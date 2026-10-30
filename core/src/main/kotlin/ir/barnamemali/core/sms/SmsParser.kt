package ir.barnamemali.core.sms

import ir.barnamemali.core.Jalali
import ir.barnamemali.core.JalaliDate
import ir.barnamemali.core.PersianText

enum class Direction { DEBIT, CREDIT }

/** What was understood from one bank SMS. Amounts are in rial. */
data class ParsedSms(
    val direction: Direction?,
    val amountRial: Long,
    val balanceRial: Long?,
    val accountDigits: String?,
    val bankId: String?,
    val date: JalaliDate?,
    val hour: Int?,
    val minute: Int?,
    val description: String,
)

/**
 * A format-agnostic reader for Iranian bank SMS. Banks change their templates often, so instead of one template
 * per bank it looks for the patterns all of them share: a keyword (برداشت، واریز، مبلغ…) followed by a number,
 * a balance after مانده/موجودی, a masked account or card number, a Shamsi date and a time.
 */
object SmsParser {
    private val debitWords = listOf(
        "برداشت", "خرید", "کسر", "انتقال", "پرداخت", "قسط", "کارمزد", "بدهکار", "شارژ", "عابربانک", "خودپرداز",
    )
    private val creditWords = listOf("واریز", "سود", "بستانکار", "دریافت", "افزایش", "برگشت")
    private val neutralWords = listOf("مبلغ", "تراکنش", "مقدار")
    private val balanceWords = listOf("مانده", "موجودی", "ماند")

    private const val NUM = "([+\\-]?)(\\d{1,3}(?:,\\d{3})+|\\d+)([+\\-]?)(?![\\d/:])"
    private val amountRegex =
        Regex("(?<![\\p{L}])(${(debitWords + creditWords + neutralWords).joinToString("|")})([^\\d+\\-]{0,25}?)(?<!\\d)(?<!\\d[/:])$NUM")
    private val balanceRegex = Regex("(${balanceWords.joinToString("|")})[^\\d+\\-]{0,20}?(?<!\\d)(?<!\\d[/:])$NUM")
    private val signedLineRegex = Regex("(?m)^\\s*([+\\-])\\s?(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*$|^\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s?([+\\-])\\s*$")
    private val accountLabelRegex = Regex("(حساب|کارت|سپرده|ح\\.|ح:|ک:|از)\\s*[:.]?\\s*([0-9*xX.\\-]{4,})")
    private val maskedRegex = Regex("[0-9]*[*xX]+[.\\-]?[0-9]{3,}")
    private val cardRegex = Regex("(?<!\\d)\\d{16}(?!\\d)")
    private val fullDateRegex = Regex("(1[34]\\d{2})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})")
    private val shortYearDateRegex = Regex("(?<![\\d/])(0\\d|1\\d)/(\\d{1,2})/(\\d{1,2})(?![\\d/])")
    private val compactRegex = Regex("(?<![\\d,])(\\d{2})(\\d{2})-(\\d{1,2}):(\\d{2})(?!\\d)")
    private val monthDayRegex = Regex("(?<![\\d/,])(\\d{1,2})/(\\d{1,2})(?![\\d/])")
    private val timeRegex = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)")
    private val otpRegex = Regex("رمز ?پویا|رمز ?دوم|رمز ?یکبار|کد ?تا[ئیٔ]?ید|رمز ?عبور|OTP", RegexOption.IGNORE_CASE)
    private val accountWordsInGap = Regex("حساب|کارت|سپرده|شماره|شبا")

    /**
     * @param body the SMS text.
     * @param received the day the SMS arrived; used for the year when the SMS shows only month/day.
     * @return null when the SMS has no transaction amount (OTP codes, ads, etc.).
     */
    fun parse(body: String, received: JalaliDate = Jalali.today()): ParsedSms? {
        val text = PersianText.normalize(body).replace('\r', '\n')
        if (otpRegex.containsMatchIn(text)) return null
        val usedRanges = mutableListOf<IntRange>()

        val balanceMatch = balanceRegex.find(text)
        var balance: Long? = null
        if (balanceMatch != null) {
            balance = toLong(balanceMatch.groupValues[3])?.let {
                if (balanceMatch.groupValues[2] == "-" || balanceMatch.groupValues[4] == "-") -it else it
            }
            usedRanges += balanceMatch.range
        }

        var amount: Long? = null
        var sign: Char? = null
        var amountRange: IntRange? = null
        for (m in amountRegex.findAll(text)) {
            if (usedRanges.any { it.overlaps(m.range) }) continue
            if (accountWordsInGap.containsMatchIn(m.groupValues[2])) continue
            val value = toLong(m.groupValues[4]) ?: continue
            if (value == 0L) continue
            amount = value
            sign = when {
                m.groupValues[3] == "+" || m.groupValues[5] == "+" -> '+'
                m.groupValues[3] == "-" || m.groupValues[5] == "-" -> '-'
                else -> null
            }
            amountRange = m.range
            break
        }
        if (amount == null) {
            for (m in signedLineRegex.findAll(text)) {
                if (usedRanges.any { it.overlaps(m.range) }) continue
                val (s, n) = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2]
                else m.groupValues[4] to m.groupValues[3]
                amount = toLong(n) ?: continue
                sign = s.first()
                amountRange = m.range
                break
            }
        }
        if (amount == null || amountRange == null) return null
        usedRanges += amountRange

        val direction = when (sign) {
            '+' -> Direction.CREDIT
            '-' -> Direction.DEBIT
            else -> directionFromWords(text)
        }

        val toman = text.contains("تومان") && !text.contains("ریال")
        val factor = if (toman) 10L else 1L

        val account = findAccount(text)
        val (date, time) = findDateTime(text, received)

        return ParsedSms(
            direction = direction,
            amountRial = amount * factor,
            balanceRial = balance?.times(factor),
            accountDigits = account,
            bankId = Banks.detect(text)?.id,
            date = date,
            hour = time?.first,
            minute = time?.second,
            description = describe(text, usedRanges),
        )
    }

    private fun directionFromWords(text: String): Direction? {
        val firstDebit = debitWords.mapNotNull { w -> text.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val firstCredit = creditWords.mapNotNull { w -> text.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        return when {
            firstDebit == null && firstCredit == null -> null
            firstCredit == null -> Direction.DEBIT
            firstDebit == null -> Direction.CREDIT
            firstCredit < firstDebit -> Direction.CREDIT
            else -> Direction.DEBIT
        }
    }

    private fun findAccount(text: String): String? {
        val candidates = sequence {
            accountLabelRegex.findAll(text).forEach { yield(it.groups[2]!!.value to it.range) }
            maskedRegex.findAll(text).forEach { yield(it.value to it.range) }
            cardRegex.findAll(text).forEach { yield(it.value to it.range) }
        }
        for ((token, _) in candidates) {
            val digits = token.filter { it.isDigit() }
            if (digits.length < 3) continue
            if (token.contains(',')) continue
            return digits.takeLast(4)
        }
        return null
    }

    private fun findDateTime(text: String, received: JalaliDate): Pair<JalaliDate?, Pair<Int, Int>?> {
        var date: JalaliDate? = null
        var time: Pair<Int, Int>? = null
        fullDateRegex.find(text)?.let { m ->
            date = valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        if (date == null) shortYearDateRegex.find(text)?.let { m ->
            date = valid(1400 + m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        if (date == null) compactRegex.find(text)?.let { m ->
            date = withYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), received)
            time = validTime(m.groupValues[3].toInt(), m.groupValues[4].toInt())
        }
        if (date == null) monthDayRegex.find(text)?.let { m ->
            date = withYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), received)
        }
        if (time == null) timeRegex.find(text)?.let { m ->
            time = validTime(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        return date to time
    }

    private fun withYear(month: Int, day: Int, received: JalaliDate): JalaliDate? {
        val year = if (month > received.month) received.year - 1 else received.year
        return valid(year, month, day)
    }

    private fun valid(y: Int, m: Int, d: Int) = if (Jalali.isValid(y, m, d)) JalaliDate(y, m, d) else null

    private fun validTime(h: Int, m: Int) = if (h in 0..23 && m in 0..59) h to m else null

    private fun describe(text: String, used: List<IntRange>): String {
        var offset = 0
        val parts = mutableListOf<String>()
        for (line in text.split('\n')) {
            val range = offset until offset + line.length
            offset += line.length + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (used.any { it.overlaps(range) }) continue
            if (trimmed.contains("بانک") || trimmed.contains("حساب") || trimmed.contains("کارت")) continue
            if (trimmed.none { it.isLetter() }) continue
            if (fullDateRegex.containsMatchIn(trimmed) || timeRegex.containsMatchIn(trimmed)) {
                val rest = trimmed.replace(fullDateRegex, "").replace(timeRegex, "").trim(' ', '-', '_')
                if (rest.none { it.isLetter() }) continue
            }
            parts += trimmed.trim('-', ' ', ':')
        }
        return parts.joinToString(" - ").take(120)
    }

    private fun toLong(s: String): Long? = s.replace(",", "").takeIf { it.isNotEmpty() && it.length <= 15 }?.toLong()

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
}

/** Short key used to remember the category and member of a merchant, e.g. "فروشگاه شهروند". */
object MerchantKey {
    private val noise = Regex("[\\d,/:+\\-_*.()]|خرید|برداشت|واریز|انتقال|پرداخت|از|به|ریال|تومان")

    fun of(description: String): String? {
        val words = PersianText.normalize(description).replace(noise, " ")
            .split(' ', '\n', '\t').map { it.trim() }.filter { it.length > 1 }
        if (words.isEmpty()) return null
        return words.take(3).joinToString(" ")
    }
}
