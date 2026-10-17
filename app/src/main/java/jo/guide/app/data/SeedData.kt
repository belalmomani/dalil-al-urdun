package jo.guide.app.data

/**
 * بيانات أولية **تجريبية** لتشغيل التطبيق قبل ربط Firebase.
 *
 * - تحتوي فقط على معلومات عامة معروفة (الأسماء والمحافظات ووصف عام).
 * - لا أرقام هواتف، لا أسعار، لا تقييمات، لا ساعات عمل، لا إحداثيات: لم يتم التحقق منها.
 * - كل سجل isSample = true ويعرض التطبيق تنبيهًا بذلك. تُستبدل بالبيانات الحقيقية من لوحة الإدارة.
 * - لا توجد صور مضمّنة: الصور تُرفع من لوحة الإدارة إلى Firebase Storage بمصدر وترخيص موثقين.
 */
object SeedData {

    private const val SRC = "معلومة عامة — لم يتم التحقق منها بعد"

    val regions: List<Region> = listOf(
        "amman" to "عمّان", "irbid" to "إربد", "zarqa" to "الزرقاء", "balqa" to "البلقاء",
        "mafraq" to "المفرق", "jerash" to "جرش", "ajloun" to "عجلون", "madaba" to "مادبا",
        "karak" to "الكرك", "tafilah" to "الطفيلة", "maan" to "معان", "aqaba" to "العقبة",
    ).map { (id, name) -> Region(id, name, RegionType.GOVERNORATE) }

    private fun regionName(id: String) = regions.first { it.id == id }.name

    private fun sample(
        id: String, name: String, category: Category, regionId: String, description: String,
        featured: Boolean = false, website: String? = null, activities: List<String> = emptyList(),
    ) = Place(
        id = "sample-$id", name = name, category = category.id, regionId = regionId,
        regionName = regionName(regionId), description = description, website = website,
        activities = activities, sourceName = SRC, verifiedAt = null, featured = featured, isSample = true,
    )

    val places: List<Place> = listOf(
        sample("petra", "البتراء", Category.TOURISM, "maan",
            "مدينة أثرية نبطية منحوتة في الصخر، من أشهر المواقع السياحية في الأردن.", featured = true,
            activities = listOf("المشي في السيق", "زيارة الخزنة")),
        sample("wadi-rum", "وادي رم", Category.TOURISM, "aqaba",
            "وادٍ صحراوي واسع بجباله الرملية والجرانيتية، معروف بالرحلات الصحراوية والتخييم.", featured = true,
            activities = listOf("جولات الجيب", "التخييم")),
        sample("dead-sea", "البحر الميت", Category.TOURISM, "balqa",
            "أخفض منطقة على سطح الأرض، تشتهر بمياهها شديدة الملوحة وطينها.", featured = true),
        sample("jerash", "جرش الأثرية", Category.TOURISM, "jerash",
            "مدينة أثرية رومانية قديمة تضم أعمدة ومسارح وساحات.", featured = true),
        sample("ajloun-castle", "قلعة عجلون", Category.TOURISM, "ajloun",
            "قلعة إسلامية تطل على غور الأردن وتقع في محافظة عجلون."),
        sample("ajloun-forest", "محمية غابات عجلون", Category.TOURISM, "ajloun",
            "محمية طبيعية حرجية في شمال الأردن مخصصة للمشي والتنزه.", featured = true,
            activities = listOf("المشي", "مراقبة الطبيعة")),
        sample("mount-nebo", "جبل نيبو", Category.TOURISM, "madaba",
            "موقع ديني وأثري قرب مادبا يُطل على وادي الأردن."),
        sample("amman-citadel", "جبل القلعة", Category.TOURISM, "amman",
            "موقع أثري على أحد جبال عمّان يضم بقايا من عصور متعددة."),
        sample("roman-theatre", "المدرج الروماني", Category.TOURISM, "amman",
            "مدرج روماني قديم في وسط عمّان."),
        sample("aqaba-red-sea", "شاطئ العقبة على البحر الأحمر", Category.TOURISM, "aqaba",
            "مدينة ساحلية جنوبية تُعرف بالغوص والأنشطة البحرية."),
        sample("hashem", "مطعم هاشم", Category.RESTAURANTS, "amman",
            "مطعم شعبي معروف في وسط البلد بعمّان. (العنوان والهاتف وساعات العمل تحتاج إلى توثيق.)", featured = true),
        sample("gov-portal", "البوابة الحكومية الرسمية", Category.GOVERNMENT, "amman",
            "الموقع الحكومي الرسمي للخدمات والمعلومات.", website = "https://jordan.gov.jo"),
        sample("visit-jordan", "Visit Jordan — السياحة في الأردن", Category.TOURISM, "amman",
            "الموقع الرسمي للتعريف بالسياحة في الأردن.", website = "https://www.visitjordan.com"),
    )
}
