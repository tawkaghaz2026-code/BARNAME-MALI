package ir.barnamemali.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.barnamemali.app.data.Account
import ir.barnamemali.app.data.Cheque
import ir.barnamemali.app.data.ChequeDirection
import ir.barnamemali.app.data.ChequeStatus
import ir.barnamemali.app.data.Installment
import ir.barnamemali.app.data.Loan
import ir.barnamemali.app.data.LoanDirection
import ir.barnamemali.app.data.Person
import ir.barnamemali.app.data.Times
import ir.barnamemali.core.Installments
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.PersianText
import ir.barnamemali.core.sms.Banks
import kotlinx.coroutines.launch
import kotlin.math.abs

fun chequeStatusLabel(s: ChequeStatus) = when (s) {
    ChequeStatus.PENDING -> "در جریان"
    ChequeStatus.CLEARED -> "پاس شد"
    ChequeStatus.BOUNCED -> "برگشتی"
    ChequeStatus.ENDORSED -> "خرج شد (واگذار)"
    ChequeStatus.CANCELLED -> "باطل"
}

@Composable
fun ObligationsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val r = repo()
    var tab by remember { mutableIntStateOf(0) }
    val people by r.dao.peopleWithBalance().collectAsStateWithLifecycle(emptyList())
    val loans by r.dao.loans().collectAsStateWithLifecycle(emptyList())
    val installments by r.dao.installments().collectAsStateWithLifecycle(emptyList())
    val cheques by r.dao.cheques().collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    var addPerson by remember { mutableStateOf(false) }
    var addLoan by remember { mutableStateOf(false) }
    var editCheque by remember { mutableStateOf<Cheque?>(null) }
    val today = Times.todayEpochDay()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("طلب و بدهی", "وام", "چک").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> {
                    val owed = people.filter { it.balance > 0 }.sumOf { it.balance }
                    val owe = -people.filter { it.balance < 0 }.sumOf { it.balance }
                    item {
                        SectionCard {
                            KeyValueRow("دیگران به شما", money(owed), Brand)
                            KeyValueRow("شما به دیگران", money(owe), Warn)
                        }
                    }
                    items(people, key = { it.person.id }) { p ->
                        SectionCard(onClick = { nav.navigate("person/${p.person.id}") }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.person.name, fontWeight = FontWeight.Bold)
                                    Hint(
                                        when {
                                            p.balance > 0 -> "به شما بدهکار است"
                                            p.balance < 0 -> "شما بدهکارید"
                                            else -> "تسویه"
                                        }
                                    )
                                }
                                Text(money(abs(p.balance)), fontWeight = FontWeight.Bold, color = if (p.balance >= 0) Brand else Warn)
                            }
                        }
                    }
                    item { TextButton(onClick = { addPerson = true }) { Text("+ شخص جدید") } }
                }
                1 -> {
                    items(loans, key = { it.id }) { l ->
                        val sched = installments.filter { it.loanId == l.id }
                        val paid = sched.count { it.paidTxnId != null }
                        val remaining = sched.filter { it.paidTxnId == null }.sumOf { it.amount }
                        val next = sched.firstOrNull { it.paidTxnId == null }
                        SectionCard(onClick = { nav.navigate("loan/${l.id}") }) {
                            Row {
                                Text(l.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Hint(if (l.closed) "تسویه شد" else "مانده ${money(remaining, compact = true)}")
                            }
                            Hint((if (l.direction == LoanDirection.TAKEN) "وام گرفته" else "وام داده") + " · ${fa(paid)} از ${fa(sched.size)} قسط پرداخت شده")
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { if (sched.isEmpty()) 0f else paid.toFloat() / sched.size },
                                modifier = Modifier.fillMaxWidth(),
                                color = Brand,
                            )
                            next?.let { Hint("قسط بعدی ${money(it.amount)} · ${Jalali.fromEpochDay(it.dueEpochDay).formatLong()} (${daysLabel(it.dueEpochDay, today)})") }
                        }
                    }
                    item { TextButton(onClick = { addLoan = true }) { Text("+ وام جدید") } }
                }
                else -> {
                    val pendingIssued = cheques.filter { it.status == ChequeStatus.PENDING && it.direction == ChequeDirection.ISSUED }.sumOf { it.amount }
                    val pendingReceived = cheques.filter { it.status == ChequeStatus.PENDING && it.direction == ChequeDirection.RECEIVED }.sumOf { it.amount }
                    item {
                        SectionCard {
                            KeyValueRow("چک‌های پرداختی در جریان", money(pendingIssued), Warn)
                            KeyValueRow("چک‌های دریافتی در جریان", money(pendingReceived), Brand)
                        }
                    }
                    items(cheques, key = { it.id }) { c ->
                        SectionCard(onClick = { editCheque = c }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(
                                    if (c.direction == ChequeDirection.ISSUED) "پرداختی" else "دریافتی",
                                    if (c.direction == ChequeDirection.ISSUED) Color(0xFFFFF1E6) else BrandSoft,
                                    if (c.direction == ChequeDirection.ISSUED) Color(0xFF9A3412) else Color(0xFF115E59),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(c.counterparty, fontWeight = FontWeight.Bold)
                                    Hint(Jalali.fromEpochDay(c.dueEpochDay).formatLong() + " · " + chequeStatusLabel(c.status) +
                                        if (c.status == ChequeStatus.PENDING) " · " + daysLabel(c.dueEpochDay, today) else "")
                                }
                                Text(money(c.amount, compact = true), fontWeight = FontWeight.Bold)
                            }
                            if (c.status == ChequeStatus.PENDING && c.direction == ChequeDirection.ISSUED && c.accountId != null) {
                                val bal = accounts.firstOrNull { it.account.id == c.accountId }?.balance
                                if (bal != null) {
                                    val dueBefore = cheques.filter { it.status == ChequeStatus.PENDING && it.direction == ChequeDirection.ISSUED && it.accountId == c.accountId && it.dueEpochDay <= c.dueEpochDay }.sumOf { it.amount }
                                    if (bal >= dueBefore) Text("موجودی حساب برای این چک کافی است", color = Brand, fontSize = 12.sp)
                                    else Text("کسری موجودی: ${money(dueBefore - bal)} تا سررسید", color = Danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = {
                            editCheque = Cheque(direction = ChequeDirection.ISSUED, amount = 0, dueEpochDay = today + 30, counterparty = "")
                        }) { Text("+ چک جدید") }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (addPerson) PersonDialog(Person(name = ""), onDismiss = { addPerson = false }) { p ->
        addPerson = false
        app.appScope.launch { r.dao.upsertPerson(p) }
    }
    if (addLoan) LoanDialog(accounts.map { it.account }, onDismiss = { addLoan = false }) { loan, record ->
        addLoan = false
        app.appScope.launch { r.createLoan(loan, record) }
    }
    editCheque?.let { c ->
        ChequeDialog(c, accounts.map { it.account }, onDismiss = { editCheque = null }) { saved, newStatus, accountId ->
            editCheque = null
            app.appScope.launch {
                if (saved.id == 0L) r.dao.insertCheque(saved)
                else {
                    r.dao.updateCheque(saved)
                    if (newStatus != c.status) r.setChequeStatus(saved, newStatus, accountId)
                }
            }
        }
    }
}

@Composable
fun PersonDialog(p: Person, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var phone by remember { mutableStateOf(p.phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.id == 0L) "شخص جدید" else "ویرایش") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("شماره موبایل (برای یادآوری)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(p.copy(name = name.trim(), phone = phone.trim())) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun LoanDialog(accounts: List<Account>, onDismiss: () -> Unit, onSave: (Loan, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(LoanDirection.TAKEN) }
    var principal by remember { mutableStateOf<Long?>(null) }
    var rate by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var first by remember { mutableStateOf(Jalali.today().plusMonths(1)) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var record by remember { mutableStateOf(false) }
    val m = PersianText.parseNumber(months)?.toInt()
    val rt = PersianText.normalize(rate).replace('٫', '.').replace('/', '.').toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وام جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(direction == LoanDirection.TAKEN, { direction = LoanDirection.TAKEN }, { Text("وام گرفتم") })
                    FilterChip(direction == LoanDirection.GIVEN, { direction = LoanDirection.GIVEN }, { Text("وام دادم") })
                }
                OutlinedTextField(title, { title = it }, label = { Text("عنوان (مثلاً قرض‌الحسنه رسالت)") }, singleLine = true)
                AmountField("مبلغ وام", principal, { principal = it })
                OutlinedTextField(rate, { rate = it }, label = { Text("نرخ سود/کارمزد سالانه ٪ (اختیاری)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(months, { months = it }, label = { Text("تعداد اقساط (ماه)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                JalaliDateField("سررسید اولین قسط", first, { first = it })
                Dropdown("حساب پرداخت اقساط", accounts, accounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id })
                if (accountId != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(record, { record = it })
                    Text(if (direction == LoanDirection.TAKEN) "مبلغ وام همین حالا به این حساب واریز شد" else "مبلغ وام همین حالا از این حساب برداشت شد", fontSize = 13.sp)
                }
                if (principal != null && m != null && m > 0) {
                    val s = Installments.schedule(principal!!, rt, m, first)
                    Hint("قسط ماهانه: ${money(s.first().amountRial)} · جمع بازپرداخت: ${money(s.sumOf { it.amountRial })}")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = principal != null && m != null && m in 1..600,
                onClick = {
                    onSave(
                        Loan(
                            title = title.ifBlank { "وام" }, direction = direction, principal = principal!!, annualRate = rt,
                            months = m!!, firstDueEpochDay = first.toEpochDay(), accountId = accountId,
                        ),
                        record,
                    )
                },
            ) { Text("ساخت جدول اقساط") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ChequeDialog(c: Cheque, accounts: List<Account>, onDismiss: () -> Unit, onSave: (Cheque, ChequeStatus, Long?) -> Unit) {
    var direction by remember { mutableStateOf(c.direction) }
    var amount by remember { mutableStateOf<Long?>(c.amount.takeIf { it > 0 }) }
    var due by remember { mutableStateOf(Jalali.fromEpochDay(c.dueEpochDay)) }
    var counterparty by remember { mutableStateOf(c.counterparty) }
    var bankId by remember { mutableStateOf(c.bankId) }
    var serial by remember { mutableStateOf(c.serial) }
    var accountId by remember { mutableStateOf(c.accountId) }
    var status by remember { mutableStateOf(c.status) }
    val isNew = c.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "چک جدید" else "چک ${c.counterparty}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(direction == ChequeDirection.ISSUED, { direction = ChequeDirection.ISSUED }, { Text("پرداختی (کشیدم)") })
                    FilterChip(direction == ChequeDirection.RECEIVED, { direction = ChequeDirection.RECEIVED }, { Text("دریافتی") })
                }
                AmountField("مبلغ", amount, { amount = it })
                JalaliDateField("تاریخ سررسید", due, { due = it })
                OutlinedTextField(counterparty, { counterparty = it }, label = { Text(if (direction == ChequeDirection.ISSUED) "در وجه" else "از طرف") }, singleLine = true)
                Dropdown("بانک چک", Banks.ALL, Banks.byId(bankId), { it.name }, { bankId = it.id })
                OutlinedTextField(serial, { serial = it }, label = { Text("شماره صیادی (اختیاری)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Dropdown(if (direction == ChequeDirection.ISSUED) "از حساب" else "واریز به حساب", accounts, accounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id })
                if (!isNew) {
                    Hint("وضعیت")
                    ChipPicker(ChequeStatus.entries, status, ::chequeStatusLabel, { if (it != null) status = it }, allowNone = false)
                    if (status == ChequeStatus.CLEARED && c.status != ChequeStatus.CLEARED) Hint("با «پاس شد» مبلغ در حساب انتخاب‌شده ثبت می‌شود. اگر پیامک بانکش آمد، پیامک خودش به این چک وصل می‌شود.")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && counterparty.isNotBlank(), onClick = {
                onSave(
                    c.copy(direction = direction, amount = amount!!, dueEpochDay = due.toEpochDay(), counterparty = counterparty.trim(),
                        bankId = bankId, serial = serial.trim(), accountId = accountId),
                    status, accountId,
                )
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun PersonScreen(nav: NavHostController, personId: Long) {
    val app = LocalApp.current
    val r = repo()
    val context = androidx.compose.ui.platform.LocalContext.current
    val people by r.dao.peopleWithBalance().collectAsStateWithLifecycle(emptyList())
    val entries by r.dao.debtEntries(personId).collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val p = people.firstOrNull { it.person.id == personId }
    var adding by remember { mutableStateOf<Int?>(null) } // 0 lent, 1 borrowed, 2 they repaid, 3 we repaid
    var confirmDelete by remember { mutableStateOf(false) }
    val unit by r.settings.unit.collectAsStateCompat()

    Scaffold(topBar = { BackBar(p?.person?.name ?: "", nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p != null) item {
                SectionCard {
                    Text(
                        when {
                            p.balance > 0 -> "${p.person.name} به شما ${money(p.balance)} بدهکار است"
                            p.balance < 0 -> "شما به ${p.person.name} ${money(-p.balance)} بدهکارید"
                            else -> "حساب تسویه است"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                    if (p.balance > 0) OutlinedButton(onClick = {
                        val text = "سلام ${p.person.name} عزیز، یادآوری دوستانه: مبلغ ${ir.barnamemali.core.Money.format(p.balance, unit)} از حساب قبلی‌مان باقی مانده است. ممنون"
                        val intent = if (p.person.phone.isNotBlank())
                            android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${p.person.phone}")).putExtra("sms_body", text)
                        else android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "ارسال یادآوری")) }
                    }) { Text("ارسال یادآوری مؤدبانه") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { adding = 0 }, Modifier.weight(1f)) { Text("پول دادم", fontSize = 13.sp) }
                    Button(onClick = { adding = 1 }, Modifier.weight(1f)) { Text("پول گرفتم", fontSize = 13.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { adding = 2 }, Modifier.weight(1f)) { Text("پس داد", fontSize = 13.sp) }
                    OutlinedButton(onClick = { adding = 3 }, Modifier.weight(1f)) { Text("پس دادم", fontSize = 13.sp) }
                }
            }
            items(entries, key = { it.id }) { e ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (e.amount > 0) "طلب شما بیشتر شد" else "بدهی شما بیشتر شد / طلب کم شد", fontSize = 14.sp)
                            Hint(listOfNotNull(Jalali.fromMillis(e.time).formatLong(), e.note.ifBlank { null },
                                e.dueEpochDay?.let { "سررسید " + Jalali.fromEpochDay(it).formatShort() }).joinToString(" · "))
                        }
                        Text(money(abs(e.amount), compact = true), fontWeight = FontWeight.Bold, color = if (e.amount > 0) Brand else Warn)
                        TextButton(onClick = { app.appScope.launch { r.deleteDebt(e) } }) { Text("حذف", color = Danger, fontSize = 12.sp) }
                    }
                }
            }
            item { TextButton(onClick = { confirmDelete = true }) { Text("حذف این شخص", color = Danger) } }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
    adding?.let { kind ->
        var amount by remember { mutableStateOf<Long?>(null) }
        var accountId by remember { mutableStateOf<Long?>(null) }
        var note by remember { mutableStateOf("") }
        var hasDue by remember { mutableStateOf(false) }
        var due by remember { mutableStateOf(Jalali.today().plusMonths(1)) }
        val title = listOf("پول دادم (قرض دادم)", "پول گرفتم (قرض گرفتم)", "پس داد", "پس دادم")[kind]
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("مبلغ", amount, { amount = it })
                    Dropdown("از/به حساب (اختیاری)", accounts.map { it.account }, accounts.map { it.account }.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id })
                    OutlinedTextField(note, { note = it }, label = { Text("توضیح") }, singleLine = true)
                    if (kind <= 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasDue, { hasDue = it }); Text("قرار بازپرداخت دارد", fontSize = 13.sp) }
                        if (hasDue) JalaliDateField("تاریخ بازپرداخت", due, { due = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = amount != null, onClick = {
                    val a = amount!!
                    val signed = if (kind == 0 || kind == 3) a else -a
                    app.appScope.launch {
                        r.addDebt(personId, signed, accountId, System.currentTimeMillis(), if (hasDue && kind <= 1) due.toEpochDay() else null, note)
                    }
                    adding = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("انصراف") } },
        )
    }
    if (confirmDelete && p != null) ConfirmDialog("حذف ${p.person.name}", "همه سوابق این شخص هم حذف می‌شود.", onConfirm = {
        app.appScope.launch {
            entries.forEach { r.deleteDebt(it) }
            r.dao.deletePerson(p.person)
        }
        nav.popBackStack()
    }, onDismiss = { confirmDelete = false })
}

@Composable
fun LoanScreen(nav: NavHostController, loanId: Long) {
    val app = LocalApp.current
    val r = repo()
    val loans by r.dao.loans().collectAsStateWithLifecycle(emptyList())
    val all by r.dao.installments().collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val loan = loans.firstOrNull { it.id == loanId }
    val schedule = all.filter { it.loanId == loanId }
    var paying by remember { mutableStateOf<Installment?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = Times.todayEpochDay()

    Scaffold(topBar = { BackBar(loan?.title ?: "", nav) { TextButton(onClick = { confirmDelete = true }) { Text("حذف", color = Danger) } } }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loan != null) item {
                SectionCard {
                    KeyValueRow("مبلغ وام", money(loan.principal))
                    KeyValueRow("پرداخت شده", money(schedule.filter { it.paidTxnId != null }.sumOf { it.amount }), Brand)
                    KeyValueRow("مانده", money(schedule.filter { it.paidTxnId == null }.sumOf { it.amount }), Warn)
                    Hint("قسطی که پیامک کسرش از حساب بیاید خودکار پرداخت‌شده علامت می‌خورد.")
                }
            }
            items(schedule, key = { it.id }) { i ->
                val paid = i.paidTxnId != null
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("قسط ${fa(i.number)} · ${money(i.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Hint(Jalali.fromEpochDay(i.dueEpochDay).formatLong() + if (!paid) " · " + daysLabel(i.dueEpochDay, today) else " · پرداخت شد")
                        }
                        if (paid) TextButton(onClick = { app.appScope.launch { r.unpayInstallment(i) } }) { Text("برگرداندن", fontSize = 12.sp) }
                        else Button(onClick = { paying = i }) { Text("پرداخت شد") }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
    paying?.let { inst ->
        var accountId by remember { mutableStateOf(loan?.accountId) }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("پرداخت قسط ${fa(inst.number)}") },
            text = { Dropdown("از حساب", accounts.map { it.account }, accounts.map { it.account }.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id }) },
            confirmButton = {
                TextButton(onClick = {
                    app.appScope.launch { r.payInstallment(inst, accountId, System.currentTimeMillis()) }
                    paying = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("انصراف") } },
        )
    }
    if (confirmDelete && loan != null) ConfirmDialog("حذف وام", "جدول اقساط این وام حذف می‌شود (تراکنش‌های ثبت‌شده می‌مانند).", onConfirm = {
        app.appScope.launch { r.deleteLoan(loan) }
        nav.popBackStack()
    }, onDismiss = { confirmDelete = false })
}
