package ir.barnamemali.core

/**
 * Compares the balance a bank reported in an SMS with the balance the app computed for that account at the
 * same moment, and decides how to bring the app back in line.
 */
object Reconciler {
    /** Differences up to this many rial (50,000 toman) with money missing are treated as bank fees. */
    const val FEE_LIMIT_RIAL = 500_000L

    enum class Cause {
        /** Balances match. */
        MATCH,
        /** An earlier SMS for the account is still waiting for confirmation and explains the difference. */
        WAITING_FOR_EARLIER_SMS,
        /** A recent manual entry has the same amount as the difference: probably recorded twice. */
        POSSIBLE_DUPLICATE,
        /** A small amount is missing: a bank fee that was never recorded. */
        BANK_FEE,
        /** Money arrived that the app did not know about: interest or an unrecorded deposit. */
        UNRECORDED_INCOME,
        /** Money left that the app did not know about. */
        UNRECORDED_EXPENSE,
    }

    data class Result(val cause: Cause, val differenceRial: Long) {
        /** True when the app should add a balance-correction entry of [differenceRial]. */
        val needsAdjustment get() = cause != Cause.MATCH && cause != Cause.WAITING_FOR_EARLIER_SMS

        fun reasonText(): String = when (cause) {
            Cause.MATCH -> "مانده پیامک با مانده اپ می‌خواند"
            Cause.WAITING_FOR_EARLIER_SMS -> "پیامک قبلی این حساب هنوز تأیید نشده"
            Cause.POSSIBLE_DUPLICATE -> "احتمالاً یک تراکنش دو بار ثبت شده"
            Cause.BANK_FEE -> "کارمزد بانکی ثبت‌نشده"
            Cause.UNRECORDED_INCOME -> "سود یا واریز ثبت‌نشده"
            Cause.UNRECORDED_EXPENSE -> "برداشت ثبت‌نشده"
        }
    }

    /**
     * @param appBalance the app's balance for the account including the transaction from this SMS.
     * @param smsBalance the balance printed in the SMS.
     * @param olderPendingSigned signed amounts of earlier, unconfirmed SMS for the same account.
     * @param recentManualSigned signed amounts of recent manually entered transactions for the account.
     */
    fun check(
        appBalance: Long,
        smsBalance: Long,
        olderPendingSigned: List<Long> = emptyList(),
        recentManualSigned: List<Long> = emptyList(),
    ): Result {
        val diff = smsBalance - appBalance
        if (diff == 0L) return Result(Cause.MATCH, 0)
        if (olderPendingSigned.isNotEmpty() && olderPendingSigned.sum() == diff) {
            return Result(Cause.WAITING_FOR_EARLIER_SMS, diff)
        }
        if (recentManualSigned.any { it == -diff }) return Result(Cause.POSSIBLE_DUPLICATE, diff)
        return when {
            diff < 0 && -diff <= FEE_LIMIT_RIAL -> Result(Cause.BANK_FEE, diff)
            diff > 0 -> Result(Cause.UNRECORDED_INCOME, diff)
            else -> Result(Cause.UNRECORDED_EXPENSE, diff)
        }
    }
}

/** Loan repayment schedules. */
object Installments {
    data class Item(val number: Int, val due: JalaliDate, val amountRial: Long)

    /**
     * Equal monthly payments (annuity). With [annualRatePercent] = 0 the principal is split evenly and the
     * rounding remainder goes on the last payment. Payments are rounded to whole thousand rial.
     */
    fun schedule(principalRial: Long, annualRatePercent: Double, months: Int, firstDue: JalaliDate): List<Item> {
        require(months > 0) { "months must be positive" }
        val r = annualRatePercent / 100.0 / 12.0
        val raw = if (r == 0.0) principalRial.toDouble() / months
        else principalRial * r / (1 - Math.pow(1 + r, -months.toDouble()))
        val payment = (Math.round(raw / 1000.0) * 1000L).coerceAtLeast(1)
        val total = if (r == 0.0) principalRial else payment * months
        return (1..months).map { n ->
            val amount = if (n < months) payment else total - payment * (months - 1)
            Item(n, firstDue.plusMonths(n - 1), amount)
        }
    }
}
