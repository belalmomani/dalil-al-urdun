package jo.guide.app.data

import org.json.JSONObject
import java.time.OffsetDateTime

// ملاحظة: في org.json قيمة JSON null تعيد "null" نصًّا من optString، لذلك نتحقق بـ isNull أولًا دائمًا.
internal fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
internal fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
internal fun JSONObject.intv(k: String): Int? = if (isNull(k)) null else optInt(k)
internal fun JSONObject.bool(k: String): Boolean? = if (isNull(k)) null else optBoolean(k)
internal fun JSONObject.strList(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).mapNotNull { i -> if (a.isNull(i)) null else a.optString(i).takeIf { it.isNotBlank() } }
}

internal fun parseTime(s: String?): Long? =
    if (s == null) null else try { OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) { null }

fun regionFromJson(o: JSONObject): Region? {
    val id = o.str("id") ?: return null
    val name = o.str("name") ?: return null
    return Region(id, name, RegionType.fromId(o.str("type")), o.str("parent_id"), o.str("name_en"))
}

fun placeFromJson(o: JSONObject): Place? {
    val id = o.str("id") ?: return null
    val name = o.str("name") ?: return null
    return Place(
        id = id,
        name = name,
        category = o.str("category") ?: Category.SERVICES.id,
        regionId = o.str("region_id"),
        regionName = o.str("region_name"),
        description = o.str("description") ?: "",
        nameEn = o.str("name_en"),
        descriptionEn = o.str("description_en"),
        regionNameEn = o.str("region_name_en"),
        phone = o.str("phone"),
        address = o.str("address"),
        lat = o.dbl("lat"),
        lng = o.dbl("lng"),
        website = o.str("website"),
        priceLevel = o.intv("price_level"),
        priceNote = o.str("price_note"),
        openingHours = o.str("opening_hours"),
        kidsFriendly = o.bool("kids_friendly"),
        ageGroups = o.strList("age_groups"),
        features = o.strList("features"),
        activities = o.strList("activities"),
        alerts = o.strList("alerts"),
        sourceName = o.str("source_name"),
        sourceUrl = o.str("source_url"),
        verifiedAt = parseTime(o.str("verified_at")),
        status = o.str("status") ?: "published",
        featured = o.bool("featured") ?: false,
        coverUrl = o.str("cover_url"),
        coverAlt = o.str("cover_alt"),
    )
}

fun mediaFromJson(o: JSONObject): PlaceMedia? {
    val id = o.str("id") ?: return null
    val url = o.str("url") ?: return null
    return PlaceMedia(
        id = id,
        type = o.str("type") ?: "image",
        url = url,
        thumbUrl = o.str("thumb_url"),
        title = o.str("title"),
        description = o.str("description"),
        alt = o.str("alt"),
        order = o.intv("sort_order") ?: 0,
        isCover = o.bool("is_cover") ?: false,
        durationSec = o.intv("duration_sec"),
        source = o.str("source"),
        license = o.str("license"),
    )
}
