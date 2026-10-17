package jo.guide.app.data

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class AdminStats(
    val places: Long, val published: Long, val drafts: Long, val inReview: Long,
    val users: Long, val media: Long, val openReports: Long, val pendingSuggestions: Long,
)

data class InboxItem(val id: String, val kind: String, val title: String, val details: String, val status: String)

/**
 * عمليات الإدارة داخل التطبيق (إحصائيات + صندوق البلاغات والاقتراحات).
 * كلها تمر عبر قواعد Firestore التي تتحقق من claim الإدارة؛ لو استُدعيت بحساب غير مصرّح ترفضها الخادم.
 */
class AdminRepository {
    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun count(q: Query): Long = q.count().get(AggregateSource.SERVER).await().count

    suspend fun stats(): AdminStats {
        val places = db.collection("places")
        return AdminStats(
            places = count(places),
            published = count(places.whereEqualTo("status", "published")),
            drafts = count(places.whereEqualTo("status", "draft")),
            inReview = count(places.whereEqualTo("status", "review")),
            users = count(db.collection("users")),
            media = count(db.collectionGroup("media")),
            openReports = count(db.collection("reports").whereEqualTo("status", "open")),
            pendingSuggestions = count(db.collection("suggestions").whereEqualTo("status", "pending")),
        )
    }

    suspend fun inbox(): List<InboxItem> {
        val reports = db.collection("reports").whereEqualTo("status", "open").limit(20).get().await().documents.map {
            InboxItem(it.id, "reports", "بلاغ: ${it.getString("reason") ?: ""}", it.getString("details") ?: "", "open")
        }
        val sugg = db.collection("suggestions").whereEqualTo("status", "pending").limit(20).get().await().documents.map {
            InboxItem(it.id, "suggestions", "اقتراح: ${it.getString("name") ?: ""}", it.getString("details") ?: "", "pending")
        }
        return reports + sugg
    }

    suspend fun resolve(item: InboxItem, uid: String) {
        val newStatus = if (item.kind == "reports") "resolved" else "reviewed"
        db.collection(item.kind).document(item.id).update("status", newStatus).await()
        db.collection("audit").add(
            mapOf(
                "action" to "resolve_${item.kind}", "targetPath" to "${item.kind}/${item.id}",
                "actorUid" to uid, "at" to FieldValue.serverTimestamp(),
            )
        ).await()
    }
}
