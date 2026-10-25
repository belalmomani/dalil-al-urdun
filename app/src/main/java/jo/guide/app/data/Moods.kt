package jo.guide.app.data

import jo.guide.app.i18n.tr

/**
 * «اكتشف على مزاجك»: كل مزاج يُترجَم إلى فلتر حقيقي على البيانات الموجودة (تصنيف/كلمات مفتاحية/سعر/أطفال).
 * لا توجد نتائج مختلقة: إن لم تطابق أي بيانات يعرض التطبيق رسالة `emptyNote` الصادقة.
 * لم نُضِف مزاج «أماكن للتصوير» لأنه لا يوجد في البيانات ما يدل عليه.
 */
enum class Mood(val id: String) {
    NATURE("nature"), ADVENTURE("adventure"), FAMILY("family"), HISTORY("history"),
    FOOD("food"), BUDGET("budget"), PARKS("parks");

    val title: String get() = when (this) {
        NATURE -> tr("أجواء الطبيعة", "Nature vibes")
        ADVENTURE -> tr("مغامرات", "Adventures")
        FAMILY -> tr("طلعات عائلية", "Family outings")
        HISTORY -> tr("آثار وتاريخ", "History & heritage")
        FOOD -> tr("مطاعم ومذاقات", "Food & flavors")
        BUDGET -> tr("أماكن اقتصادية", "Budget-friendly")
        PARKS -> tr("حدائق ومساحات خضراء", "Parks & green spaces")
    }

    /** رسالة تظهر عندما لا توجد بيانات كافية لهذا المزاج. */
    val emptyNote: String get() = when (this) {
        FAMILY -> tr("لا توجد بعد أماكن موثّقة كمناسبة للأطفال والعائلات في بياناتنا.", "No places are verified as kid/family-friendly in our data yet.")
        BUDGET -> tr("لا توجد بعد أماكن عليها مستوى سعر موثّق «اقتصادي».", "No places have a verified “budget” price level yet.")
        else -> tr("لا توجد أماكن مطابقة لهذا المزاج في بياناتنا حاليًا.", "No places match this mood in our data right now.")
    }

    fun filter(): PlaceFilter = when (this) {
        NATURE -> PlaceFilter(categories = setOf(Category.TOURISM.id), keywords = setOf("محمية", "طبيعة", "طبيعي", "غابة", "غابات", "وادي", "نهر", "reserve", "nature", "forest", "wadi"), moodId = id)
        ADVENTURE -> PlaceFilter(keywords = setOf("غوص", "تسلق", "مسار", "المشي", "تخييم", "مغامر", "diving", "hiking", "camping", "adventure", "trail"), moodId = id)
        FAMILY -> PlaceFilter(kidsFriendlyOnly = true, moodId = id)
        HISTORY -> PlaceFilter(keywords = setOf("أثري", "أثرية", "آثار", "تاريخ", "قلعة", "متحف", "روماني", "archaeolog", "castle", "museum", "roman", "history"), moodId = id)
        FOOD -> PlaceFilter(categories = setOf(Category.RESTAURANTS.id), moodId = id)
        BUDGET -> PlaceFilter(priceLevels = setOf(1), moodId = id)
        PARKS -> PlaceFilter(keywords = setOf("حديقة", "حدائق", "منتزه", "متنزه", "park", "garden", "ecopark"), moodId = id)
    }

    companion object {
        fun fromId(id: String?): Mood? = entries.firstOrNull { it.id == id }
    }
}
