package jo.guide.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

object FirebaseStatus {
    /** Firebase (للدخول فقط) مهيّأ إذا وُجد google-services.json أثناء البناء. */
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}

/** next = إزاحة الصفحة التالية (offset)، أو null عند انتهاء النتائج. */
data class PlacePage(val places: List<Place>, val fromCache: Boolean, val next: Int?)

interface PlaceRepository {
    /** true عندما تكون البيانات تجريبية (Supabase غير مربوط). */
    val isSample: Boolean
    suspend fun loadRegions(): List<Region>
    suspend fun loadPlaces(cursor: Int?): PlacePage
    suspend fun loadMedia(placeId: String): List<PlaceMedia>
    suspend fun adsRemoteEnabled(): Boolean
    suspend fun submitReport(placeId: String, reason: String, details: String): Result<Unit>
    suspend fun submitSuggestion(name: String, category: String, regionName: String, details: String): Result<Unit>
    suspend fun saveFavorites(uid: String, ids: Set<String>)
    suspend fun loadFavorites(uid: String): Set<String>
}

class SeedPlaceRepository : PlaceRepository {
    override val isSample = true
    override suspend fun loadRegions() = SeedData.regions
    override suspend fun loadPlaces(cursor: Int?) = PlacePage(SeedData.places, false, null)
    override suspend fun loadMedia(placeId: String) = emptyList<PlaceMedia>()
    override suspend fun adsRemoteEnabled() = true
    override suspend fun submitReport(placeId: String, reason: String, details: String) =
        Result.failure<Unit>(IllegalStateException("لم يتم ربط Supabase بعد، لا يمكن إرسال البلاغات."))
    override suspend fun submitSuggestion(name: String, category: String, regionName: String, details: String) =
        Result.failure<Unit>(IllegalStateException("لم يتم ربط Supabase بعد، لا يمكن إرسال الاقتراحات."))
    override suspend fun saveFavorites(uid: String, ids: Set<String>) = Unit
    override suspend fun loadFavorites(uid: String) = emptySet<String>()
}

/**
 * يقرأ المحتوى المنشور من Supabase (PostgREST + RLS). الصفحة الأولى تُخزَّن محليًا؛
 * عند انقطاع الاتصال تُعرض النسخة المخزنة مع علامة fromCache كي يُنبَّه المستخدم أن المعلومات قد تكون قديمة.
 */
class SupabasePlaceRepository(private val context: Context) : PlaceRepository {
    override val isSample = false

    private fun cacheFile(name: String) = File(context.cacheDir, name)

    private suspend fun cachedRest(query: String, cacheName: String): Pair<JSONArray, Boolean> =
        try {
            val arr = SupabaseApi.rest(query)
            runCatching { cacheFile(cacheName).writeText(arr.toString()) }
            arr to false
        } catch (e: IOException) {
            val cached = runCatching { JSONArray(cacheFile(cacheName).readText()) }.getOrNull() ?: throw e
            cached to true
        }

    override suspend fun loadRegions(): List<Region> {
        val (arr, _) = cachedRest("regions?select=id,name,type,parent_id&status=eq.published&order=name&limit=500", "regions.json")
        return (0 until arr.length()).mapNotNull { regionFromJson(arr.getJSONObject(it)) }
    }

    /** المنشور فقط، على صفحات. أول صفحة تعمل بلا اتصال من الكاش. */
    override suspend fun loadPlaces(cursor: Int?): PlacePage {
        val offset = cursor ?: 0
        val query = "places?select=*&status=eq.published&order=name&limit=$PAGE_SIZE&offset=$offset"
        val (arr, fromCache) = if (offset == 0) cachedRest(query, "places_page0.json") else SupabaseApi.rest(query) to false
        val places = (0 until arr.length()).mapNotNull { placeFromJson(arr.getJSONObject(it)) }
        val next = if (!fromCache && arr.length() >= PAGE_SIZE) offset + PAGE_SIZE else null
        return PlacePage(places, fromCache, next)
    }

    override suspend fun loadMedia(placeId: String): List<PlaceMedia> {
        val arr = SupabaseApi.rest("media?select=*&place_id=eq.${SupabaseApi.enc(placeId)}&order=sort_order&limit=60")
        return (0 until arr.length()).mapNotNull { mediaFromJson(arr.getJSONObject(it)) }
    }

    override suspend fun adsRemoteEnabled(): Boolean = try {
        val arr = SupabaseApi.rest("settings?select=value&key=eq.public&limit=1")
        val v: JSONObject? = arr.optJSONObject(0)?.optJSONObject("value")
        v?.optBoolean("adsEnabled", true) ?: true
    } catch (_: Exception) {
        true
    }

    override suspend fun submitReport(placeId: String, reason: String, details: String): Result<Unit> = runCatching {
        SupabaseApi.api("submit_report", JSONObject().put("placeId", placeId).put("reason", reason.take(80)).put("details", details.take(1000)))
        Unit
    }

    override suspend fun submitSuggestion(name: String, category: String, regionName: String, details: String): Result<Unit> = runCatching {
        SupabaseApi.api(
            "submit_suggestion",
            JSONObject().put("name", name.take(120)).put("category", category).put("regionName", regionName.take(80)).put("details", details.take(1000)),
        )
        Unit
    }

    override suspend fun saveFavorites(uid: String, ids: Set<String>) {
        SupabaseApi.api("save_favorites", JSONObject().put("ids", JSONArray(ids.toList().take(500))))
    }

    override suspend fun loadFavorites(uid: String): Set<String> {
        val a = SupabaseApi.api("load_favorites").optJSONArray("ids") ?: return emptySet()
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
