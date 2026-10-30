package ir.barnamemali.app.data

import androidx.room.withTransaction
import ir.barnamemali.app.notify.Notifier
import ir.barnamemali.core.Installments
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.PersianText
import ir.barnamemali.core.JalaliDate
import ir.barnamemali.core.Money
import ir.barnamemali.core.Reconciler
import ir.barnamemali.core.sms.Banks
import ir.barnamemali.core.sms.Direction
import ir.barnamemali.core.sms.MerchantKey
import ir.barnamemali.core.sms.SmsParser
import java.security.MessageDigest
import kotlin.math.abs

/** How a confirmed SMS is booked. */
enum class SmsBooking { EXPENSE_OR_INCOME, TRANSFER }

class Repository(
    private val db: AppDatabase,
    val settings: Settings,
    private val notifier: Notifier,
) {
    val dao = db.dao()

    // ---------- Setup ----------

    suspend fun seedDefaults() {
        if (dao.categoryCount() > 0) return
        val expense = listOf(
            "خواربار", "رستوران و بیرون", "حمل‌ونقل", "قبوض", "اجاره و شارژ", "آموزش", "درمان و سلامت", "پوشاک",
            "تفریح و سفر", "خانه و وسایل", "هدیه و مناسبت", "اینترنت و اشتراک", "کارمزد بانکی", "سایر",
        )
        val income = listOf("حقوق", "کسب‌وکار", "سود سپرده", "اجاره دریافتی", "هدیه دریافتی", "سایر درآمد")
        expense.forEachIndexed { i, n -> dao.upsertCategory(Category(name = n, kind = CategoryKind.EXPENSE, sortOrder = i)) }
        income.forEachIndexed { i, n -> dao.upsertCategory(Category(name = n, kind = CategoryKind.INCOME, sortOrder = 100 + i)) }
        if (dao.membersOnce().isEmpty()) {
            dao.upsertMember(Member(name = "مشترک خانواده", role = "خواربار، قبوض، شارژ", shared = true, sortOrder = 0))
            dao.upsertMember(Member(name = "من", role = "مدیر مالی", sortOrder = 1))
        }
    }

    // ---------- Transactions ----------

    suspend fun saveTxn(t: Txn): Long {
        val id = if (t.id == 0L) dao.insertTxn(t) else t.id.also { dao.updateTxn(t) }
        if (t.kind == TxnKind.EXPENSE) checkBudgets(t)
        return id
    }

    suspend fun deleteTxn(t: Txn) = db.withTransaction {
        t.peerTxnId?.let { dao.txn(it) }?.let { dao.deleteTxn(it) }
        dao.deleteTxn(t)
    }

    suspend fun transfer(fromId: Long, toId: Long, amount: Long, time: Long, note: String) = db.withTransaction {
        val outId = dao.insertTxn(Txn(accountId = fromId, amount = -amount, kind = TxnKind.TRANSFER_OUT, time = time, note = note))
        val inId = dao.insertTxn(
            Txn(accountId = toId, amount = amount, kind = TxnKind.TRANSFER_IN, time = time, note = note, peerTxnId = outId)
        )
        dao.updateTxn(dao.txn(outId)!!.copy(peerTxnId = inId))
    }

    private suspend fun checkBudgets(t: Txn) {
        val month = Jalali.fromMillis(t.time)
        val (from, to) = Times.monthRange(month)
        t.memberId?.let { mid ->
            val m = dao.membersOnce().firstOrNull { it.id == mid } ?: return@let
            if (m.monthlyBudget > 0) alertIfCrossed("m$mid-${month.year}-${month.month}", m.name, dao.memberExpense(mid, from, to), m.monthlyBudget)
        }
        t.categoryId?.let { cid ->
            val c = dao.categoriesOnce().firstOrNull { it.id == cid } ?: return@let
            if (c.monthlyBudget > 0) alertIfCrossed("c$cid-${month.year}-${month.month}", c.name, dao.categoryExpense(cid, from, to), c.monthlyBudget)
        }
    }

    private fun alertIfCrossed(key: String, name: String, spent: Long, budget: Long) {
        val pct = spent * 100 / budget
        val level = when {
            pct >= 100 -> 100
            pct >= 80 -> 80
            else -> return
        }
        if (settings.alertSent("$key-$level")) return
        settings.markAlertSent("$key-$level")
        val unit = settings.unit.value
        notifier.budget(
            "بودجه «$name»",
            if (level == 100) "بودجه این ماه تمام شد: ${Money.format(spent, unit)} از ${Money.format(budget, unit)}"
            else "۸۰٪ بودجه این ماه خرج شده: ${Money.format(spent, unit)} از ${Money.format(budget, unit)}",
        )
    }

    // ---------- Bank SMS ----------

    /** Reads one SMS. Returns true if it was a bank transaction and is now waiting (or was auto-confirmed). */
    private val personalNumber = Regex("^(\\+?98|0098|0)?9\\d{9}$")

    /** Banks send from short codes or names; a regular mobile number is a person, never a bank. */
    private fun fromPersonalNumber(sender: String) = personalNumber.matches(PersianText.normalize(sender).filter { it.isDigit() || it == '+' })

    private fun matchAccount(accounts: List<Account>, digits: String?, bankId: String?): Account? =
        accounts.filter { it.digits != null && it.digits == digits }
            .let { matches -> matches.firstOrNull { it.bankId == bankId } ?: matches.firstOrNull() }
            ?: accounts.filter { bankId != null && it.bankId == bankId }.singleOrNull()

    /**
     * Once the user has defined bank accounts, an SMS that matches none of them and comes from a bank they have
     * no account with is not theirs to book.
     */
    private fun isUsersBank(accounts: List<Account>, bankId: String?): Boolean {
        val bankAccounts = accounts.filter { !it.archived && (it.type == AccountType.BANK || it.type == AccountType.CARD) }
        return bankAccounts.isEmpty() || (bankId != null && bankAccounts.any { it.bankId == bankId })
    }

    /** Runs the current SMS rules over the SMS still waiting, so messages wrongly taken as bank SMS go away. */
    suspend fun recheckPendingSms() {
        val accounts = dao.accounts()
        for (sms in dao.pendingSmsOnce()) {
            val parsed = SmsParser.parse(sms.body, Jalali.fromMillis(sms.receivedAt))
            val keep = parsed != null && !fromPersonalNumber(sms.sender) &&
                (matchAccount(accounts, parsed.accountDigits, parsed.bankId ?: sms.bankId) != null ||
                    isUsersBank(accounts, parsed.bankId ?: sms.bankId))
            if (!keep) dao.updateSms(sms.copy(status = SmsStatus.IGNORED))
        }
    }

    suspend fun ingestSms(sender: String, body: String, receivedAt: Long, notify: Boolean = true): Boolean {
        val received = Jalali.fromMillis(receivedAt)
        if (fromPersonalNumber(sender)) return false
        val parsed = SmsParser.parse(body, received) ?: return false
        val accounts = dao.accounts()
        val bankId = parsed.bankId ?: Banks.detect(sender)?.id
        val account = matchAccount(accounts, parsed.accountDigits, bankId)
        if (account == null && !isUsersBank(accounts, bankId)) return false

        // A new account created today should not pull in months of older SMS.
        if (account != null && account.balanceKnown && receivedAt < account.createdAt) return false

        val time = parsed.date?.let { d ->
            val receivedDay = Jalali.fromMillis(receivedAt)
            when {
                parsed.hour != null -> Times.at(d, parsed.hour!!, parsed.minute ?: 0)
                d == receivedDay -> receivedAt
                else -> Times.at(d, 12, 0)
            }
        } ?: receivedAt

        val signed = when (parsed.direction) {
            Direction.DEBIT -> -parsed.amountRial
            Direction.CREDIT -> parsed.amountRial
            null -> 0L
        }
        val rule = MerchantKey.of(parsed.description)?.let { dao.rule(it) }
        val defaultMember = account?.ownerMemberId ?: dao.membersOnce().firstOrNull { it.shared }?.id
        val fingerprintSource = if (parsed.hour != null || parsed.date != null) body.trim() else body.trim() + (receivedAt / 3_600_000)
        val sms = PendingSms(
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            time = time,
            amount = signed,
            absAmount = parsed.amountRial,
            balance = parsed.balanceRial,
            accountId = account?.id,
            bankId = bankId,
            digits = parsed.accountDigits,
            description = parsed.description,
            suggestedCategoryId = rule?.categoryId,
            suggestedMemberId = rule?.memberId ?: defaultMember,
            fingerprint = sha1(fingerprintSource),
        )
        val id = dao.insertSms(sms)
        if (id <= 0) return false // already seen

        if (rule?.autoConfirm == true && account != null && signed != 0L) {
            val message = confirmSms(id, account.id, signed, SmsBooking.EXPENSE_OR_INCOME, rule.categoryId, rule.memberId, "", remember = false)
            if (notify) notifier.autoConfirmed(sms.description.ifBlank { "تراکنش" }, Money.format(abs(signed), settings.unit.value), message)
        } else if (notify) {
            notifier.pendingSms(dao.pendingSmsOnce().size)
        }
        return true
    }

    /**
     * Books a bank SMS as a transaction, then compares the SMS balance with the app's balance and corrects any
     * difference automatically. Returns a one-line Persian summary of what happened to the balance.
     */
    suspend fun confirmSms(
        smsId: Long,
        accountId: Long,
        signedAmount: Long,
        booking: SmsBooking,
        categoryId: Long?,
        memberId: Long?,
        note: String,
        remember: Boolean,
        autoConfirmNextTime: Boolean = false,
    ): String = db.withTransaction {
        val sms = dao.sms(smsId) ?: return@withTransaction ""
        val kind = when {
            booking == SmsBooking.TRANSFER -> if (signedAmount < 0) TxnKind.TRANSFER_OUT else TxnKind.TRANSFER_IN
            signedAmount < 0 -> TxnKind.EXPENSE
            else -> TxnKind.INCOME
        }
        var txn = Txn(
            accountId = accountId,
            amount = signedAmount,
            kind = kind,
            time = sms.time,
            categoryId = if (booking == SmsBooking.TRANSFER) null else categoryId,
            memberId = memberId,
            note = note.ifBlank { sms.description },
            source = TxnSource.SMS,
            smsBalance = sms.balance,
        )
        val txnId = dao.insertTxn(txn)
        txn = txn.copy(id = txnId)
        linkToChequeOrInstallment(txn)?.let { txn = it; dao.updateTxn(it) }
        dao.updateSms(sms.copy(status = SmsStatus.CONFIRMED, accountId = accountId, txnId = txnId))

        val key = MerchantKey.of(sms.description)
        if (remember && key != null) {
            val old = dao.rule(key)
            dao.upsertRule(MerchantRule(key, categoryId, memberId, autoConfirmNextTime, (old?.hits ?: 0) + 1))
        }
        val account = dao.account(accountId)
        if (account != null && account.digits == null && sms.digits != null) {
            dao.updateAccount(account.copy(digits = sms.digits, bankId = account.bankId ?: sms.bankId))
        }
        if (txn.kind == TxnKind.EXPENSE) checkBudgets(txn)
        reconcile(accountId, txn)
    }

    /** If an SMS amount matches a pending cheque or an unpaid installment, the SMS settles it. */
    private suspend fun linkToChequeOrInstallment(txn: Txn): Txn? {
        val day = Jalali.fromMillis(txn.time).toEpochDay()
        val amount = abs(txn.amount)
        val cheque = dao.pendingChequesUntil(day + 5).firstOrNull {
            it.amount == amount && it.dueEpochDay >= day - 5 && (it.accountId == null || it.accountId == txn.accountId) &&
                (if (it.direction == ChequeDirection.ISSUED) txn.amount < 0 else txn.amount > 0)
        }
        if (cheque != null) {
            dao.updateCheque(cheque.copy(status = ChequeStatus.CLEARED, txnId = txn.id, accountId = txn.accountId))
            return txn.copy(kind = TxnKind.CHEQUE, chequeId = cheque.id, categoryId = null)
        }
        val inst = dao.unpaidInstallmentsUntil(day + 7).firstOrNull { i ->
            i.amount == amount && i.dueEpochDay >= day - 7 && dao.loan(i.loanId)?.let { l ->
                !l.closed && (l.accountId == null || l.accountId == txn.accountId) &&
                    (if (l.direction == LoanDirection.TAKEN) txn.amount < 0 else txn.amount > 0)
            } == true
        }
        if (inst != null) {
            dao.updateInstallment(inst.copy(paidTxnId = txn.id, paidAt = txn.time))
            closeLoanIfDone(inst.loanId)
            return txn.copy(kind = TxnKind.INSTALLMENT, loanId = inst.loanId, categoryId = null)
        }
        return null
    }

    private suspend fun reconcile(accountId: Long, txn: Txn): String {
        val smsBalance = txn.smsBalance ?: return "پیامک مانده نداشت"
        val account = dao.account(accountId) ?: return ""
        if (!account.balanceKnown) {
            // Earlier SMS still waiting will be added later; leave room for them so the balance stays exact.
            val olderPending = dao.olderPendingSms(accountId, txn.time).sumOf { it.amount }
            val opening = smsBalance - dao.sumUntil(accountId, txn.time) - olderPending
            dao.updateAccount(account.copy(openingBalance = opening, balanceKnown = true))
            return "مانده حساب از روی پیامک تنظیم شد"
        }
        // A later SMS already confirmed the balance after this moment; it is the more recent truth.
        if (dao.verifiedAfter(accountId, txn.time) > 0) return "ثبت شد"
        val appBalance = account.openingBalance + dao.sumUntil(accountId, txn.time)
        val result = Reconciler.check(
            appBalance = appBalance,
            smsBalance = smsBalance,
            olderPendingSigned = dao.olderPendingSms(accountId, txn.time).map { it.amount },
            recentManualSigned = dao.recentManualAmounts(accountId, txn.time - 3 * 86_400_000L),
        )
        if (result.needsAdjustment) {
            val feeCategory = if (result.cause == Reconciler.Cause.BANK_FEE)
                dao.categoriesOnce().firstOrNull { it.name == "کارمزد بانکی" }?.id else null
            dao.insertTxn(
                Txn(
                    accountId = accountId,
                    amount = result.differenceRial,
                    kind = TxnKind.ADJUSTMENT,
                    time = txn.time,
                    categoryId = feeCategory,
                    note = result.reasonText(),
                    source = TxnSource.AUTO,
                    reviewed = false,
                )
            )
            val unit = settings.unit.value
            return "مانده اصلاح شد: ${Money.format(abs(result.differenceRial), unit)} ${result.reasonText()}"
        }
        return result.reasonText()
    }

    suspend fun ignoreSms(sms: PendingSms) = dao.updateSms(sms.copy(status = SmsStatus.IGNORED))

    /** Confirms every waiting SMS that has an account and a direction, oldest first, with the suggested values. */
    suspend fun confirmAllSms(): Int {
        var n = 0
        for (s in dao.pendingSmsOnce()) {
            val acc = s.accountId ?: continue
            if (s.amount == 0L) continue
            confirmSms(s.id, acc, s.amount, SmsBooking.EXPENSE_OR_INCOME, s.suggestedCategoryId, s.suggestedMemberId, "", remember = false)
            n++
        }
        return n
    }

    /** After the user creates an account, waiting SMS with the same digits or bank get attached to it. */
    suspend fun attachPendingSms(account: Account) {
        for (s in dao.pendingSmsOnce()) {
            if (s.accountId != null) continue
            val match = (account.digits != null && s.digits == account.digits) ||
                (s.digits == null && account.bankId != null && s.bankId == account.bankId)
            if (match) dao.updateSms(s.copy(accountId = account.id, suggestedMemberId = s.suggestedMemberId ?: account.ownerMemberId))
        }
    }

    // ---------- Debts ----------

    /** [amount] > 0: they owe us more (we lent or they took). < 0: we owe them more or they repaid. */
    suspend fun addDebt(personId: Long, amount: Long, accountId: Long?, time: Long, dueEpochDay: Long?, note: String) =
        db.withTransaction {
            val txnId = accountId?.let {
                dao.insertTxn(Txn(accountId = it, amount = -amount, kind = TxnKind.DEBT, time = time, personId = personId, note = note))
            }
            dao.insertDebt(DebtEntry(personId = personId, amount = amount, time = time, dueEpochDay = dueEpochDay, note = note, txnId = txnId))
        }

    suspend fun deleteDebt(d: DebtEntry) = db.withTransaction {
        d.txnId?.let { dao.txn(it) }?.let { dao.deleteTxn(it) }
        dao.deleteDebt(d)
    }

    // ---------- Loans ----------

    suspend fun createLoan(loan: Loan, recordDisbursement: Boolean) = db.withTransaction {
        val id = dao.insertLoan(loan)
        val schedule = Installments.schedule(loan.principal, loan.annualRate, loan.months, Jalali.fromEpochDay(loan.firstDueEpochDay))
        dao.insertInstallments(schedule.map { Installment(loanId = id, number = it.number, dueEpochDay = it.due.toEpochDay(), amount = it.amountRial) })
        if (recordDisbursement && loan.accountId != null) {
            val sign = if (loan.direction == LoanDirection.TAKEN) 1 else -1
            dao.insertTxn(
                Txn(accountId = loan.accountId, amount = sign * loan.principal, kind = TxnKind.LOAN, time = System.currentTimeMillis(), loanId = id, note = loan.title)
            )
        }
        id
    }

    suspend fun payInstallment(inst: Installment, accountId: Long?, time: Long) = db.withTransaction {
        val loan = dao.loan(inst.loanId) ?: return@withTransaction
        val txnId = accountId?.let {
            val sign = if (loan.direction == LoanDirection.TAKEN) -1 else 1
            dao.insertTxn(
                Txn(accountId = it, amount = sign * inst.amount, kind = TxnKind.INSTALLMENT, time = time, loanId = loan.id, memberId = loan.memberId, note = "قسط ${inst.number} ${loan.title}")
            )
        }
        dao.updateInstallment(inst.copy(paidTxnId = txnId ?: -1, paidAt = time))
        closeLoanIfDone(loan.id)
    }

    suspend fun unpayInstallment(inst: Installment) = db.withTransaction {
        inst.paidTxnId?.takeIf { it > 0 }?.let { dao.txn(it) }?.let { dao.deleteTxn(it) }
        dao.updateInstallment(inst.copy(paidTxnId = null, paidAt = null))
        dao.loan(inst.loanId)?.let { if (it.closed) dao.updateLoan(it.copy(closed = false)) }
    }

    private suspend fun closeLoanIfDone(loanId: Long) {
        val loan = dao.loan(loanId) ?: return
        if (dao.unpaidInstallmentsUntil(Long.MAX_VALUE).none { it.loanId == loanId }) dao.updateLoan(loan.copy(closed = true))
    }

    suspend fun deleteLoan(loan: Loan) = db.withTransaction {
        dao.deleteInstallments(loan.id)
        dao.deleteLoan(loan)
    }

    // ---------- Cheques ----------

    suspend fun setChequeStatus(c: Cheque, status: ChequeStatus, accountId: Long?) = db.withTransaction {
        var txnId = c.txnId
        if (status == ChequeStatus.CLEARED && txnId == null && accountId != null) {
            val sign = if (c.direction == ChequeDirection.ISSUED) -1 else 1
            txnId = dao.insertTxn(
                Txn(accountId = accountId, amount = sign * c.amount, kind = TxnKind.CHEQUE, time = System.currentTimeMillis(), chequeId = c.id, note = "چک ${c.counterparty}")
            )
        }
        if (status != ChequeStatus.CLEARED && txnId != null) {
            dao.txn(txnId)?.let { dao.deleteTxn(it) }
            txnId = null
        }
        dao.updateCheque(c.copy(status = status, accountId = accountId ?: c.accountId, txnId = txnId))
    }

    // ---------- Recurring ----------

    suspend fun markRecurringDone(r: Recurring, accountId: Long) = db.withTransaction {
        saveTxn(
            Txn(
                accountId = accountId, amount = r.amount, kind = if (r.amount < 0) TxnKind.EXPENSE else TxnKind.INCOME,
                time = System.currentTimeMillis(), categoryId = r.categoryId, memberId = r.memberId, note = r.title,
            )
        )
        val next = Jalali.fromEpochDay(r.nextDueEpochDay).plusMonths(1)
        dao.upsertRecurring(r.copy(nextDueEpochDay = next.toEpochDay()))
    }

    suspend fun skipRecurring(r: Recurring) =
        dao.upsertRecurring(r.copy(nextDueEpochDay = Jalali.fromEpochDay(r.nextDueEpochDay).plusMonths(1).toEpochDay()))

    // ---------- Upcoming ----------

    data class Due(val title: String, val amount: Long, val epochDay: Long, val outgoing: Boolean)

    suspend fun upcoming(days: Int): List<Due> {
        val until = Times.todayEpochDay() + days
        val out = mutableListOf<Due>()
        val loans = dao.allLoans().associateBy { it.id }
        dao.pendingChequesUntil(until).forEach {
            out += Due(
                (if (it.direction == ChequeDirection.ISSUED) "چک پرداختی به " else "چک دریافتی از ") + it.counterparty,
                it.amount, it.dueEpochDay, it.direction == ChequeDirection.ISSUED,
            )
        }
        dao.unpaidInstallmentsUntil(until).forEach { i ->
            val l = loans[i.loanId] ?: return@forEach
            if (!l.closed) out += Due("قسط ${i.number} از ${l.months} · ${l.title}", i.amount, i.dueEpochDay, l.direction == LoanDirection.TAKEN)
        }
        dao.recurringDueUntil(until).forEach { out += Due(it.title, abs(it.amount), it.nextDueEpochDay, it.amount < 0) }
        val people = dao.peopleOnce().associateBy { it.id }
        val balances = dao.allDebts().groupBy { it.personId }.mapValues { (_, l) -> l.sumOf { it.amount } }
        dao.debtEntriesWithDue().filter { d ->
            val bal = balances[d.personId] ?: 0L
            d.dueEpochDay!! <= until && bal != 0L && (bal > 0) == (d.amount > 0)
        }.forEach { d ->
            val name = people[d.personId]?.name ?: return@forEach
            out += Due(if (d.amount < 0) "بدهی به $name" else "طلب از $name", abs(d.amount), d.dueEpochDay!!, d.amount < 0)
        }
        return out.sortedBy { it.epochDay }
    }

    // ---------- Helpers ----------

    fun monthRange(d: JalaliDate) = Times.monthRange(d)

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
