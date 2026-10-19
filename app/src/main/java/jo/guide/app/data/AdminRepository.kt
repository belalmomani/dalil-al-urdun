package jo.guide.app.data

import org.json.JSONObject

data class AdminStats(
    val places: Long, val published: Long, val drafts: Long, val inReview: Long,
    val regions: Long, val media: Long, val openReports: Long, val pendingSuggestions: Long,
)

data class InboxItem(val id: String, val kind: String, val title: String, val details: String, val status: String)

/**
 * عمليات الإدارة داخل التطبيق (إحصائيات + صندوق البلاغات والاقتراحات).
 * كلها تمر عبر دالة admin-api التي تتحقق في الخادم من رمز Firebase وتحسب الدور؛ تُرفض لأي حساب غير مصرّح.
 */
class AdminRepository {
    suspend fun stats(): AdminStats {
        val o = SupabaseApi.api("stats")
        return AdminStats(
            places = o.optLong("places"), published = o.optLong("published"), drafts = o.optLong("drafts"),
            inReview = o.optLong("review"), regions = o.optLong("regions"), media = o.optLong("media"),
            openReports = o.optLong("openReports"), pendingSuggestions = o.optLong("pendingSuggestions"),
        )
    }

    suspend fun inbox(): List<InboxItem> {
        val arr = SupabaseApi.api("inbox").optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            InboxItem(id, o.str("kind") ?: "reports", o.str("title") ?: "", o.str("details") ?: "", o.str("status") ?: "")
        }
    }

    suspend fun resolve(item: InboxItem, @Suppress("UNUSED_PARAMETER") uid: String) {
        SupabaseApi.api("resolve", JSONObject().put("kind", item.kind).put("id", item.id))
    }
}
