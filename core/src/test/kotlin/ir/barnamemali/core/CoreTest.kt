package ir.barnamemali.core

import ir.barnamemali.core.sms.Direction
import ir.barnamemali.core.sms.MerchantKey
import ir.barnamemali.core.sms.SmsParser
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JalaliTest {
    @Test
    fun convertsKnownDates() {
        assertEquals(JalaliDate(1405, 8, 8), Jalali.fromLocalDate(LocalDate.of(2026, 10, 30)))
        assertEquals(JalaliDate(1403, 1, 1), Jalali.fromLocalDate(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1403, 12, 30), Jalali.fromLocalDate(LocalDate.of(2025, 3, 20)))
        assertEquals(JalaliDate(1404, 1, 1), Jalali.fromLocalDate(LocalDate.of(2025, 3, 21)))
        assertEquals(LocalDate.of(2026, 10, 30), JalaliDate(1405, 8, 8).toLocalDate())
    }

    @Test
    fun roundTripsEveryDayForTenYears() {
        var d = LocalDate.of(2020, 1, 1)
        while (d.year < 2031) {
            assertEquals(d, Jalali.fromLocalDate(d).toLocalDate())
            d = d.plusDays(1)
        }
    }

    @Test
    fun leapYearsAndMonthMath() {
        assertTrue(Jalali.isLeap(1403))
        assertFalse(Jalali.isLeap(1404))
        assertEquals(JalaliDate(1405, 7, 30), JalaliDate(1405, 6, 31).plusMonths(1))
        assertEquals(JalaliDate(1406, 1, 15), JalaliDate(1405, 12, 15).plusMonths(1))
        assertEquals("۸ آبان ۱۴۰۵", JalaliDate(1405, 8, 8).formatLong())
    }
}

class MoneyTest {
    @Test
    fun formatsAmounts() {
        assertEquals("۸۵۰٬۰۰۰ تومان", Money.format(8_500_000, MoneyUnit.TOMAN))
        assertEquals("۱۸۴٫۶ م", Money.compact(1_846_000_000, MoneyUnit.TOMAN))
        assertEquals(850_000L, PersianText.parseNumber("۸۵۰٬۰۰۰"))
    }
}

class SmsParserTest {
    private val today = JalaliDate(1405, 8, 8)

    @Test
    fun multiLineDebitWithMaskedAccount() {
        val p = SmsParser.parse(
            "بانک ملت\nحساب 4521***\nبرداشت: 8,500,000\nمانده: 124,310,000\n1405/08/08 13:42\nخرید - فروشگاه شهروند",
            today,
        )!!
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(8_500_000, p.amountRial)
        assertEquals(124_310_000, p.balanceRial)
        assertEquals("4521", p.accountDigits)
        assertEquals("mellat", p.bankId)
        assertEquals(JalaliDate(1405, 8, 8), p.date)
        assertEquals(13, p.hour)
        assertEquals("فروشگاه شهروند", MerchantKey.of(p.description))
    }

    @Test
    fun trailingMinusAndCompactDate() {
        val p = SmsParser.parse("بانک ملی ایران\nانتقال:200,000-\nحساب:0109xxxx2003\nمانده:1,250,000\n0808-09:15", today)!!
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(200_000, p.amountRial)
        assertEquals(1_250_000, p.balanceRial)
        assertEquals("2003", p.accountDigits)
        assertEquals(JalaliDate(1405, 8, 8), p.date)
        assertEquals(9, p.hour)
        assertEquals(15, p.minute)
    }

    @Test
    fun creditWithPlusSignAndPersianDigits() {
        val p = SmsParser.parse("سامان\nواریز +۴۲,۰۰۰,۰۰۰\nبه حساب 849-800-1234567-1\nمانده ۵۶,۷۰۰,۰۰۰\n1405/08/01\n10:20", today)!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(42_000_000, p.amountRial)
        assertEquals(56_700_000, p.balanceRial)
        assertEquals("5671", p.accountDigits)
        assertEquals("saman", p.bankId)
    }

    @Test
    fun singleLineSkipsAccountNumberAsAmount() {
        val p = SmsParser.parse(
            "بانک تجارت: برداشت از حساب 1234567890 مبلغ 1,500,000 ریال در تاریخ 1405/08/05 ساعت 18:30 مانده 9,800,000 ریال",
            today,
        )!!
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(1_500_000, p.amountRial)
        assertEquals(9_800_000, p.balanceRial)
        assertEquals("7890", p.accountDigits)
        assertEquals(JalaliDate(1405, 8, 5), p.date)
        assertEquals(18, p.hour)
    }

    @Test
    fun tomanAmountsAreConvertedToRial() {
        val p = SmsParser.parse("واریز 500,000 تومان به کارت 6037991234567890", today)!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(5_000_000, p.amountRial)
        assertEquals("melli", p.bankId)
        assertEquals("7890", p.accountDigits)
    }

    @Test
    fun monthFromLastYear() {
        val p = SmsParser.parse("حساب ***1234\nبرداشت 100,000\n12/25 10:00", JalaliDate(1405, 1, 3))!!
        assertEquals(JalaliDate(1404, 12, 25), p.date)
    }

    @Test
    fun ignoresOtpAndAds() {
        assertNull(SmsParser.parse("رمز پویا خرید: 482913 مبلغ: 500,000 ریال بانک ملت", today))
        assertNull(SmsParser.parse("جشنواره پاییزی! با خرید از فروشگاه ما تخفیف بگیرید", today))
    }

    @Test
    fun ignoresNonBankMessagesWithNumbers() {
        val notBank = listOf(
            "شارژ 50,000 ریالی شما با موفقیت انجام شد. موجودی: 120,000 ریال. ایرانسل",
            "مشترک گرامی، بسته اینترنت 10 گیگابایت با مبلغ 150,000 تومان فعال شد",
            "سفارش شما به مبلغ 1,250,000 تومان پرداخت شد. کد رهگیری 123456789",
            "سلام، 500 تومن برام کارت به کارت کن فردا میدم",
            "قبض برق شما به مبلغ 320,000 ریال صادر شد. شناسه پرداخت 1234567890123",
            "مبلغ 2,000,000 تومان جایزه نقدی! برای ثبت نام عدد 1 را ارسال کنید",
            "پرداخت 85,000 تومان در اسنپ انجام شد",
        )
        for (text in notBank) assertNull(SmsParser.parse(text, today), text)
    }

    @Test
    fun readsCommonBankFormats() {
        val pasargad = SmsParser.parse("-1,200,000\nاز 220.8000.1234567.1\n1405/08/08_10:20\nمانده: 3,400,000", today)!!
        assertEquals(Direction.DEBIT, pasargad.direction)
        assertEquals(1_200_000, pasargad.amountRial)
        assertEquals(3_400_000, pasargad.balanceRial)

        val sepah = SmsParser.parse("بانک سپه\nبرداشت: 750,000\nح: 1234\nمانده: 9,000,000\n1405/08/07", today)!!
        assertEquals(750_000, sepah.amountRial)
        assertEquals("1234", sepah.accountDigits)

        val keshavarzi = SmsParser.parse("بانک کشاورزی\nخرید\nمبلغ:450,000-\nحساب:...5566\nمانده:2،300،000\n0807-21:05", today)!!
        assertEquals(Direction.DEBIT, keshavarzi.direction)
        assertEquals(450_000, keshavarzi.amountRial)
        assertEquals(2_300_000, keshavarzi.balanceRial)

        val billByBank = SmsParser.parse("بانک ملت\nپرداخت قبض\nحساب 4521***\nبرداشت: 320,000\nمانده: 1,000,000", today)!!
        assertEquals(320_000, billByBank.amountRial)
    }
}

class ReconcilerTest {
    @Test
    fun decidesCause() {
        assertEquals(Reconciler.Cause.MATCH, Reconciler.check(1_000, 1_000).cause)
        val fee = Reconciler.check(1_000_000, 880_000)
        assertEquals(Reconciler.Cause.BANK_FEE, fee.cause)
        assertEquals(-120_000, fee.differenceRial)
        assertTrue(fee.needsAdjustment)
        assertEquals(
            Reconciler.Cause.WAITING_FOR_EARLIER_SMS,
            Reconciler.check(1_000_000, 700_000, olderPendingSigned = listOf(-300_000)).cause,
        )
        assertEquals(
            Reconciler.Cause.POSSIBLE_DUPLICATE,
            Reconciler.check(1_000_000, 2_000_000, recentManualSigned = listOf(-1_000_000)).cause,
        )
        assertEquals(Reconciler.Cause.UNRECORDED_INCOME, Reconciler.check(1_000, 5_000_000).cause)
        assertEquals(Reconciler.Cause.UNRECORDED_EXPENSE, Reconciler.check(50_000_000, 1_000).cause)
    }
}

class InstallmentsTest {
    @Test
    fun interestFreeSplitsEvenly() {
        val s = Installments.schedule(12_000_000, 0.0, 12, JalaliDate(1405, 8, 15))
        assertEquals(12, s.size)
        assertTrue(s.all { it.amountRial == 1_000_000L })
        assertEquals(JalaliDate(1406, 7, 15), s.last().due)
    }

    @Test
    fun annuityWithFeeRate() {
        val s = Installments.schedule(600_000_000, 4.0, 36, JalaliDate(1405, 8, 15))
        assertEquals(17_714_000, s.first().amountRial)
        assertEquals(s.first().amountRial * 36, s.sumOf { it.amountRial })
    }
}
