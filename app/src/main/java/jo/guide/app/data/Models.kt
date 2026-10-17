package jo.guide.app.data

/** تصنيفات الدليل. الترتيب هو ترتيب العرض في الشاشة الرئيسية. */
enum class Category(val id: String, val label: String) {
    TOURISM("tourism", "سياحة"),
    RESTAURANTS("restaurants", "مطاعم"),
    GOVERNMENT("government", "حكومي"),
    SHOPPING("shopping", "تسوّق"),
    EDUCATION("education", "تعليم"),
    NEWS("news", "أخبار"),
    JOBS("jobs", "وظائف"),
    APPS("apps", "تطبيقات"),
    SERVICES("services", "خدمات");

    companion object {
        fun fromId(id: String?): Category? = entries.firstOrNull { it.id == id }
    }
}

enum class RegionType(val id: String, val label: String) {
    GOVERNORATE("governorate", "محافظة"),
    CITY("city", "مدينة"),
    TOWN("town", "بلدة"),
    VILLAGE("village", "قرية"),
    NEIGHBORHOOD("neighborhood", "حي");

    companion object {
        fun fromId(id: String?): RegionType = entries.firstOrNull { it.id == id } ?: CITY
    }
}

data class Region(
    val id: String,
    val name: String,
    val type: RegionType,
    val parentId: String? = null,
)

/** فئات الأعمار المتاحة في الفلاتر. */
object AgeGroups {
    val all: List<Pair<String, String>> = listOf(
        "toddlers" to "رُضّع وصغار (0–3)",
        "kids" to "أطفال (4–12)",
        "teens" to "مراهقون (13–17)",
        "adults" to "بالغون",
        "seniors" to "كبار السن",
    )
}

/** الميزات والمرافق المتاحة في الفلاتر. */
object Features {
    val all: List<Pair<String, String>> = listOf(
        "parking" to "مواقف سيارات",
        "kids_area" to "ركن أطفال",
        "wheelchair" to "وصول لذوي الإعاقة",
        "outdoor" to "جلسات خارجية",
        "wifi" to "إنترنت",
        "prayer_room" to "مصلّى",
    )
}

object PriceLevels {
    val all: List<Pair<Int, String>> = listOf(1 to "اقتصادي", 2 to "متوسط", 3 to "مرتفع")
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
    companion object {
        fun fromMap(id: String, m: Map<String, Any?>): Place? {
            val name = m["name"] as? String ?: return null
            return Place(
                id = id,
                name = name,
                category = m["category"] as? String ?: Category.SERVICES.id,
                regionId = m["regionId"] as? String,
                regionName = m["regionName"] as? String,
                description = m["description"] as? String ?: "",
                phone = (m["phone"] as? String)?.takeIf { it.isNotBlank() },
                address = (m["address"] as? String)?.takeIf { it.isNotBlank() },
                lat = (m["lat"] as? Number)?.toDouble(),
                lng = (m["lng"] as? Number)?.toDouble(),
                website = (m["website"] as? String)?.takeIf { it.isNotBlank() },
                priceLevel = (m["priceLevel"] as? Number)?.toInt(),
                priceNote = m["priceNote"] as? String,
                openingHours = m["openingHours"] as? String,
                kidsFriendly = m["kidsFriendly"] as? Boolean,
                ageGroups = strList(m["ageGroups"]),
                features = strList(m["features"]),
                activities = strList(m["activities"]),
                alerts = strList(m["alerts"]),
                sourceName = m["sourceName"] as? String,
                sourceUrl = m["sourceUrl"] as? String,
                verifiedAt = (m["verifiedAt"] as? Number)?.toLong() ?: tsMillis(m["verifiedAt"]),
                status = m["status"] as? String ?: "draft",
                featured = m["featured"] as? Boolean ?: false,
                coverUrl = (m["coverUrl"] as? String)?.takeIf { it.isNotBlank() },
                coverAlt = m["coverAlt"] as? String,
            )
        }

        private fun strList(v: Any?): List<String> =
            (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        private fun tsMillis(v: Any?): Long? =
            (v as? com.google.firebase.Timestamp)?.toDate()?.time
    }
}

/** وسيط (صورة/فيديو) مرتبط بمكان. الملفات نفسها في Firebase Storage. */
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

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>): PlaceMedia? {
            val url = m["url"] as? String ?: return null
            return PlaceMedia(
                id = id,
                type = m["type"] as? String ?: "image",
                url = url,
                thumbUrl = m["thumbUrl"] as? String,
                title = m["title"] as? String,
                description = m["description"] as? String,
                alt = m["alt"] as? String,
                order = (m["order"] as? Number)?.toInt() ?: 0,
                isCover = m["isCover"] as? Boolean ?: false,
                durationSec = (m["durationSec"] as? Number)?.toInt(),
                source = m["source"] as? String,
                license = m["license"] as? String,
            )
        }
    }
}
