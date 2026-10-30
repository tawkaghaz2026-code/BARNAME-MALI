package ir.barnamemali.core

/** Helpers for Persian digits, letters and amount formatting. Amounts are always stored in rial. */
object PersianText {
    private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    /** Persian/Arabic digits to ASCII, Arabic ي/ك to Persian ی/ک. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val fa = FA_DIGITS.indexOf(c)
            val ar = AR_DIGITS.indexOf(c)
            sb.append(
                when {
                    fa >= 0 -> '0' + fa
                    ar >= 0 -> '0' + ar
                    c == 'ي' -> 'ی'
                    c == 'ك' -> 'ک'
                    c == '٬' -> ','
                    c == '‏' || c == '‎' -> ' '
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun toPersianDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
        return sb.toString()
    }

    /** 1234567 -> ۱٬۲۳۴٬۵۶۷ */
    fun groupDigits(value: Long): String {
        val neg = value < 0
        val digits = kotlin.math.abs(value).toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('٬')
            sb.append(c)
        }
        return (if (neg) "−" else "") + toPersianDigits(sb.toString())
    }

    /** Parses user input like "۸۵۰٬۰۰۰" or "850,000" to a number, or null. */
    fun parseNumber(input: String): Long? {
        val clean = normalize(input).filter { it.isDigit() }
        if (clean.isEmpty() || clean.length > 17) return null
        return clean.toLong()
    }
}

enum class MoneyUnit { TOMAN, RIAL }

object Money {
    fun toDisplay(rial: Long, unit: MoneyUnit): Long = if (unit == MoneyUnit.TOMAN) rial / 10 else rial

    fun fromInput(value: Long, unit: MoneyUnit): Long = if (unit == MoneyUnit.TOMAN) value * 10 else value

    fun unitLabel(unit: MoneyUnit) = if (unit == MoneyUnit.TOMAN) "تومان" else "ریال"

    /** Full amount: "۸۵۰٬۰۰۰ تومان". */
    fun format(rial: Long, unit: MoneyUnit, withUnit: Boolean = true): String {
        val s = PersianText.groupDigits(toDisplay(rial, unit))
        return if (withUnit) "$s ${unitLabel(unit)}" else s
    }

    /** Compact amount for cards: "۱۸۴٫۶ م" (million) or "۸۵۰ هزار". */
    fun compact(rial: Long, unit: MoneyUnit): String {
        val v = toDisplay(rial, unit)
        val abs = kotlin.math.abs(v)
        val sign = if (v < 0) "−" else ""
        return when {
            abs >= 1_000_000_000 -> sign + oneDecimal(abs / 1_000_000_000.0) + " میلیارد"
            abs >= 1_000_000 -> sign + oneDecimal(abs / 1_000_000.0) + " م"
            abs >= 1_000 -> sign + PersianText.toPersianDigits((abs / 1000).toString()) + " هزار"
            else -> sign + PersianText.toPersianDigits(abs.toString())
        }
    }

    private fun oneDecimal(d: Double): String {
        val r = Math.round(d * 10) / 10.0
        val text = if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', '٫')
        return PersianText.toPersianDigits(text)
    }
}
