package jo.guide.app.data

import jo.guide.app.i18n.tr

/** تصنيفات الدليل. الترتيب هو ترتيب العرض في الشاشة الرئيسية. */
enum class Category(val id: String, private val ar: String, private val en: String) {
    TOURISM("tourism", "سياحة", "Tourism"),
    RESTAURANTS("restaurants", "مطاعم", "Restaurants"),
    GOVERNMENT("government", "حكومي", "Government"),
    SHOPPING("shopping", "تسوّق", "Shopping"),
    EDUCATION("education", "تعليم", "Education"),
    NEWS("news", "أخبار", "News"),
    JOBS("jobs", "وظائف", "Jobs"),
    APPS("apps", "تطبيقات", "Apps"),
    SERVICES("services", "خدمات", "Services");

    val label: String get() = tr(ar, en)

    companion object {
        fun fromId(id: String?): Category? = entries.firstOrNull { it.id == id }
    }
}

enum class RegionType(val id: String, private val ar: String, private val en: String) {
    GOVERNORATE("governorate", "محافظة", "Governorate"),
    CITY("city", "مدينة", "City"),
    TOWN("town", "بلدة", "Town"),
    VILLAGE("village", "قرية", "Village"),
    NEIGHBORHOOD("neighborhood", "حي", "Neighborhood");

    val label: String get() = tr(ar, en)

    companion object {
        fun fromId(id: String?): RegionType = entries.firstOrNull { it.id == id } ?: CITY
    }
}

data class Region(
    val id: String,
    val name: String,
    val type: RegionType,
    val parentId: String? = null,
    val nameEn: String? = null,
) {
    /** الاسم بلغة التطبيق؛ يرجع للعربي إن لم تتوفر ترجمة. */
    val displayName: String get() = if (!jo.guide.app.i18n.Lang.isAr && !nameEn.isNullOrBlank()) nameEn else name
}

/** فئات الأعمار المتاحة في الفلاتر. */
object AgeGroups {
    val all: List<Pair<String, String>> get() = listOf(
        "toddlers" to tr("رُضّع وصغار (0–3)", "Babies & toddlers (0–3)"),
        "kids" to tr("أطفال (4–12)", "Children (4–12)"),
        "teens" to tr("مراهقون (13–17)", "Teens (13–17)"),
        "adults" to tr("بالغون", "Adults"),
        "seniors" to tr("كبار السن", "Seniors"),
    )
}

/** الميزات والمرافق المتاحة في الفلاتر. */
object Features {
    val all: List<Pair<String, String>> get() = listOf(
        "parking" to tr("مواقف سيارات", "Parking"),
        "kids_area" to tr("ركن أطفال", "Kids' area"),
        "wheelchair" to tr("وصول لذوي الإعاقة", "Wheelchair access"),
        "outdoor" to tr("جلسات خارجية", "Outdoor seating"),
        "wifi" to tr("إنترنت", "Wi-Fi"),
        "prayer_room" to tr("مصلّى", "Prayer room"),
    )
}

object PriceLevels {
    val all: List<Pair<Int, String>> get() = listOf(1 to tr("اقتصادي", "Budget"), 2 to tr("متوسط", "Moderate"), 3 to tr("مرتفع", "High-end"))
}

/**
 * سجل مكان. أي حقل غير معروف يبقى null ولا يُعرض — لا نخترع بيانات.
 * verifiedAt = null يعني «غير موثّق / يحتاج تحديثًا».
 */
data class Place(
    val id: String,
    val name: String,
    val category: String,
    val regionId: String? = null,
    val regionName: String? = null,
    val description: String = "",
    val nameEn: String? = null,
    val descriptionEn: String? = null,
    val regionNameEn: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val website: String? = null,
    val priceLevel: Int? = null,
    val priceNote: String? = null,
    val openingHours: String? = null,
    val kidsFriendly: Boolean? = null,
    val ageGroups: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val alerts: List<String> = emptyList(),
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: Long? = null,
    val status: String = "published",
    val featured: Boolean = false,
    val coverUrl: String? = null,
    val coverAlt: String? = null,
    /** true للبيانات التجريبية المضمّنة في التطبيق. */
    val isSample: Boolean = false,
) {
    private val useEn get() = !jo.guide.app.i18n.Lang.isAr
    /** الاسم/الوصف/المنطقة بلغة التطبيق مع رجوع للعربية عند غياب الترجمة. */
    val displayName: String get() = if (useEn && !nameEn.isNullOrBlank()) nameEn else name
    val displayDescription: String get() = if (useEn && !descriptionEn.isNullOrBlank()) descriptionEn else description
    val displayRegion: String? get() = if (useEn && !regionNameEn.isNullOrBlank()) regionNameEn else regionName
}

/** وسيط (صورة/فيديو) مرتبط بمكان. الملفات نفسها في Supabase Storage. */
data class PlaceMedia(
    val id: String,
    val type: String, // "image" | "video"
    val url: String,
    val thumbUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val alt: String? = null,
    val order: Int = 0,
    val isCover: Boolean = false,
    val durationSec: Int? = null,
    val source: String? = null,
    val license: String? = null,
) {
    val isVideo get() = type == "video"

}
