package ir.barnamemali.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A date in the Iranian (Jalali / Shamsi) calendar. Months are 1..12. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {

    fun toLocalDate(): LocalDate {
        val g = Jalali.toGregorian(year, month, day)
        return LocalDate.of(g[0], g[1], g[2])
    }

    fun toEpochDay(): Long = toLocalDate().toEpochDay()

    fun plusDays(days: Long): JalaliDate = Jalali.fromLocalDate(toLocalDate().plusDays(days))

    /** Same day next month(s); clamps to the month's last day (e.g. 31 Shahrivar -> 30 Mehr). */
    fun plusMonths(months: Int): JalaliDate {
        val total = (year * 12 + (month - 1)) + months
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        return JalaliDate(y, m, minOf(day, Jalali.monthLength(y, m)))
    }

    fun firstOfMonth() = JalaliDate(year, month, 1)

    fun lastOfMonth() = JalaliDate(year, month, Jalali.monthLength(year, month))

    override fun compareTo(other: JalaliDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    /** ۱۴۰۵/۰۸/۰۸ */
    fun format(): String = PersianText.toPersianDigits("%04d/%02d/%02d".format(year, month, day))

    /** ۸ آبان ۱۴۰۵ */
    fun formatLong(): String =
        PersianText.toPersianDigits("$day ") + Jalali.MONTH_NAMES[month - 1] + PersianText.toPersianDigits(" $year")

    /** ۸ آبان */
    fun formatShort(): String = PersianText.toPersianDigits("$day ") + Jalali.MONTH_NAMES[month - 1]

    fun monthTitle(): String = Jalali.MONTH_NAMES[month - 1] + PersianText.toPersianDigits(" $year")
}

object Jalali {
    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    val WEEKDAY_NAMES = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

    val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    fun fromLocalDate(d: LocalDate): JalaliDate {
        val j = toJalali(d.year, d.monthValue, d.dayOfMonth)
        return JalaliDate(j[0], j[1], j[2])
    }

    fun fromEpochDay(epochDay: Long) = fromLocalDate(LocalDate.ofEpochDay(epochDay))

    fun fromMillis(millis: Long, zone: ZoneId = TEHRAN) =
        fromLocalDate(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())

    fun today(zone: ZoneId = TEHRAN) = fromLocalDate(LocalDate.now(zone))

    fun weekdayName(d: JalaliDate): String = WEEKDAY_NAMES[d.toLocalDate().dayOfWeek.value - 1]

    fun isLeap(year: Int): Boolean {
        val g = toGregorian(year, 12, 30)
        val back = toJalali(g[0], g[1], g[2])
        return back[0] == year && back[1] == 12 && back[2] == 30
    }

    fun monthLength(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeap(year) -> 30
        else -> 29
    }

    fun isValid(year: Int, month: Int, day: Int) =
        month in 1..12 && day >= 1 && day <= monthLength(year, month)

    /** Gregorian -> Jalali, the widely used arithmetic algorithm (valid for 1300s-1400s Shamsi). */
    fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666L + 365L * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053).toInt()
        days %= 12053
        jy += 4 * (days / 1461).toInt()
        days %= 1461
        if (days > 365) {
            jy += ((days - 1) / 365).toInt()
            days = (days - 1) % 365
        }
        val d = days.toInt()
        val jm = if (d < 186) 1 + d / 31 else 7 + (d - 186) / 30
        val jd = 1 + if (d < 186) d % 31 else (d - 186) % 30
        return intArrayOf(jy, jm, jd)
    }

    /** Jalali -> Gregorian. */
    fun toGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy2 = jy + 1595
        var days = -355668L + 365L * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097).toInt()
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524).toInt()
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461).toInt()
        days %= 1461
        if (days > 365) {
            gy += ((days - 1) / 365).toInt()
            days = (days - 1) % 365
        }
        var gd = days.toInt() + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val monthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > monthDays[gm]) {
            gd -= monthDays[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }
}
