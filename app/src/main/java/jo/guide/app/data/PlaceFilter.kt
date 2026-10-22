package jo.guide.app.data

/**
 * فلتر متعدد الخيارات.
 * - categories / regionIds / priceLevels / ageGroups: يكفي تطابق أي قيمة مختارة (OR داخل المجموعة).
 * - features: يجب توفر كل الميزات المختارة (AND).
 * - بين المجموعات المختلفة: AND.
 * - إذا اختار المستخدم مستوى سعر أو فئة عمرية أو ميزة، فالأماكن التي لا تتوفر عنها هذه المعلومة تُستبعد
 *   لأننا لا نفترض بيانات غير موثقة.
 */
data class PlaceFilter(
    val query: String = "",
    val categories: Set<String> = emptySet(),
    val regionIds: Set<String> = emptySet(),
    val priceLevels: Set<Int> = emptySet(),
    val ageGroups: Set<String> = emptySet(),
    val features: Set<String> = emptySet(),
    val kidsFriendlyOnly: Boolean = false,
) {
    val activeCount: Int
        get() = categories.size + regionIds.size + priceLevels.size + ageGroups.size +
            features.size + (if (kidsFriendlyOnly) 1 else 0)

    val isEmpty get() = activeCount == 0 && query.isBlank()

    fun matches(p: Place): Boolean {
        if (categories.isNotEmpty() && p.category !in categories) return false
        if (regionIds.isNotEmpty() && p.regionId !in regionIds) return false
        if (priceLevels.isNotEmpty() && p.priceLevel !in priceLevels) return false
        if (ageGroups.isNotEmpty() && p.ageGroups.none { it in ageGroups }) return false
        if (features.isNotEmpty() && !p.features.containsAll(features)) return false
        if (kidsFriendlyOnly && p.kidsFriendly != true) return false
        val q = normalizeArabic(query)
        if (q.isNotEmpty()) {
            val hay = normalizeArabic(
                listOfNotNull(p.name, p.nameEn, p.description, p.descriptionEn, p.regionName, p.regionNameEn, p.address).joinToString(" ")
            )
            if (!q.split(' ').filter { it.isNotEmpty() }.all { hay.contains(it) }) return false
        }
        return true
    }

    fun apply(places: List<Place>): List<Place> = places.filter(::matches)

    companion object {
        /** توحيد الحروف العربية لتحسين البحث: إزالة التشكيل والتطويل وتوحيد الألف والياء والتاء المربوطة. */
        fun normalizeArabic(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s.trim().lowercase()) {
                when (ch) {
                    in 'ً'..'ٟ', 'ـ', 'ٰ' -> {}
                    'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                    'ى', 'ئ' -> sb.append('ي')
                    'ة' -> sb.append('ه')
                    'ؤ' -> sb.append('و')
                    else -> sb.append(ch)
                }
            }
            return sb.toString().replace(Regex("\\s+"), " ")
        }
    }
}
