package ir.barnamemali.core.sms

/**
 * Iranian banks and credit institutions, used to pick a bank for an account and to tell which bank an SMS
 * came from. [keywords] are matched against the SMS text; [cardPrefixes] are the first 6 digits of card numbers.
 */
data class Bank(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val cardPrefixes: List<String> = emptyList(),
)

object Banks {
    val ALL: List<Bank> = listOf(
        Bank("melli", "ملی ایران", listOf("بانک ملی", "ملی ایران", "bmi"), listOf("603799")),
        Bank("sepah", "سپه", listOf("سپه", "sepah"), listOf("589210")),
        Bank("mellat", "ملت", listOf("ملت", "mellat"), listOf("610433", "991975")),
        Bank("tejarat", "تجارت", listOf("تجارت", "tejarat"), listOf("627353", "585983")),
        Bank("saderat", "صادرات ایران", listOf("صادرات ایران", "بانک صادرات", "bsi"), listOf("603769")),
        Bank("keshavarzi", "کشاورزی", listOf("کشاورزی", "keshavarzi", "bki"), listOf("603770", "639217")),
        Bank("maskan", "مسکن", listOf("مسکن", "maskan"), listOf("628023")),
        Bank("refah", "رفاه کارگران", listOf("رفاه", "refah"), listOf("589463")),
        Bank("edbi", "توسعه صادرات", listOf("توسعه صادرات", "edbi"), listOf("627648", "207177")),
        Bank("sanatmadan", "صنعت و معدن", listOf("صنعت و معدن", "صنعت ومعدن"), listOf("627961")),
        Bank("postbank", "پست بانک", listOf("پست بانک", "پستبانک", "postbank"), listOf("627760")),
        Bank("tosee_taavon", "توسعه تعاون", listOf("توسعه تعاون"), listOf("502908")),
        Bank("eghtesad_novin", "اقتصاد نوین", listOf("اقتصاد نوین", "اقتصادنوین", "enbank"), listOf("627412")),
        Bank("parsian", "پارسیان", listOf("پارسیان", "parsian"), listOf("622106", "639194", "627884")),
        Bank("pasargad", "پاسارگاد", listOf("پاسارگاد", "pasargad", "bpi", "ویپاد"), listOf("502229", "639347")),
        Bank("saman", "سامان", listOf("سامان", "saman", "بلو", "blu"), listOf("621986")),
        Bank("sina", "سینا", listOf("بانک سینا"), listOf("639346")),
        Bank("karafarin", "کارآفرین", listOf("کارآفرین", "کارافرین", "karafarin"), listOf("627488", "502910")),
        Bank("sarmayeh", "سرمایه", listOf("بانک سرمایه"), listOf("639607")),
        Bank("shahr", "شهر", listOf("بانک شهر", "shahr"), listOf("502806", "504706")),
        Bank("day", "دی", listOf("بانک دی", "day bank"), listOf("502938")),
        Bank("khavarmianeh", "خاورمیانه", listOf("خاورمیانه", "بانکینو", "middle east"), listOf("585947")),
        Bank("iranzamin", "ایران زمین", listOf("ایران زمین", "ایرانزمین"), listOf("505785")),
        Bank("ayandeh", "آینده", listOf("بانک آینده", "ayandeh"), listOf("636214")),
        Bank("gardeshgari", "گردشگری", listOf("گردشگری", "tourism"), listOf("505416")),
        Bank("mehr_iran", "قرض‌الحسنه مهر ایران", listOf("مهر ایران", "مهرایران", "qmb"), listOf("606373")),
        Bank("resalat", "قرض‌الحسنه رسالت", listOf("رسالت", "resalat"), listOf("504172")),
        Bank("melal", "مؤسسه اعتباری ملل", listOf("اعتباری ملل", "melal"), listOf("606256")),
        Bank("noor", "مؤسسه اعتباری نور", listOf("اعتباری نور"), listOf("507677")),
        Bank("hekmat", "حکمت ایرانیان", listOf("حکمت"), listOf("636949")),
        Bank("ansar", "انصار", listOf("انصار"), listOf("627381")),
        Bank("ghavamin", "قوامین", listOf("قوامین"), listOf("639599")),
        Bank("kosar", "کوثر", listOf("کوثر"), listOf("505801")),
        Bank("mehr_eghtesad", "مهر اقتصاد", listOf("مهر اقتصاد"), listOf("639370")),
        Bank("tosee", "توسعه", listOf("موسسه توسعه", "بانک توسعه"), listOf("628157")),
        Bank("other", "سایر", emptyList()),
    )

    fun byId(id: String?): Bank? = ALL.firstOrNull { it.id == id }

    /** Best guess of the bank an SMS is from, by card prefix first and then by name. */
    fun detect(text: String): Bank? {
        val normalized = text.lowercase()
        val digitRuns = Regex("\\d{6,}").findAll(normalized.replace("-", "")).map { it.value }.toList()
        ALL.firstOrNull { b -> b.cardPrefixes.any { p -> digitRuns.any { it.startsWith(p) } } }?.let { return it }
        // Longer keywords first so "توسعه صادرات" wins over "صادرات".
        val candidates = ALL.flatMap { b -> b.keywords.map { k -> k to b } }.sortedByDescending { it.first.length }
        return candidates.firstOrNull { (k, _) -> normalized.contains(k.lowercase()) }?.second
    }
}
