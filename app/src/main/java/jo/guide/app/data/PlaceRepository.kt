package jo.guide.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object FirebaseStatus {
    /** Firebase مهيّأ فقط إذا وُجد google-services.json أثناء البناء. */
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}

data class PlacePage(val places: List<Place>, val fromCache: Boolean, val next: DocumentSnapshot?)

interface PlaceRepository {
    /** true عندما تكون البيانات تجريبية (Firebase غير مربوط). */
    val isSample: Boolean
    suspend fun loadRegions(): List<Region>
    suspend fun loadPlaces(cursor: DocumentSnapshot?): PlacePage
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
    override suspend fun loadPlaces(cursor: DocumentSnapshot?) = PlacePage(SeedData.places, false, null)
    override suspend fun loadMedia(placeId: String) = emptyList<PlaceMedia>()
    override suspend fun adsRemoteEnabled() = true
    override suspend fun submitReport(placeId: String, reason: String, details: String) =
        Result.failure<Unit>(IllegalStateException("لم يتم ربط Firebase بعد، لا يمكن إرسال البلاغات."))
    override suspend fun submitSuggestion(name: String, category: String, regionName: String, details: String) =
        Result.failure<Unit>(IllegalStateException("لم يتم ربط Firebase بعد، لا يمكن إرسال الاقتراحات."))
    override suspend fun saveFavorites(uid: String, ids: Set<String>) = Unit
    override suspend fun loadFavorites(uid: String) = emptySet<String>()
}

class FirestorePlaceRepository : PlaceRepository {
    override val isSample = false
    private val db get() = FirebaseFirestore.getInstance()

    override suspend fun loadRegions(): List<Region> {
        val snap = db.collection("regions").whereEqualTo("status", "published").get().await()
        return snap.documents.mapNotNull { d ->
            val name = d.getString("name") ?: return@mapNotNull null
            Region(d.id, name, RegionType.fromId(d.getString("type")), d.getString("parentId"))
        }.sortedBy { it.name }
    }

    /** يقرأ المنشور فقط، على صفحات (Pagination). الفهرس المطلوب في firestore.indexes.json. */
    override suspend fun loadPlaces(cursor: DocumentSnapshot?): PlacePage {
        var q: Query = db.collection("places")
            .whereEqualTo("status", "published")
            .orderBy("name")
            .limit(PAGE_SIZE)
        if (cursor != null) q = q.startAfter(cursor)
        val snap = q.get().await()
        val places = snap.documents.mapNotNull { d -> d.data?.let { Place.fromMap(d.id, it) } }
        val next = if (snap.size() >= PAGE_SIZE) snap.documents.lastOrNull() else null
        return PlacePage(places, snap.metadata.isFromCache, next)
    }

    override suspend fun loadMedia(placeId: String): List<PlaceMedia> {
        val snap = db.collection("places").document(placeId).collection("media")
            .orderBy("order").limit(60).get().await()
        return snap.documents.mapNotNull { d -> d.data?.let { PlaceMedia.fromMap(d.id, it) } }
    }

    override suspend fun adsRemoteEnabled(): Boolean = try {
        val d = db.collection("settings").document("public").get().await()
        d.getBoolean("adsEnabled") ?: true
    } catch (_: Exception) {
        true
    }

    override suspend fun submitReport(placeId: String, reason: String, details: String): Result<Unit> = runCatching {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("سجّل الدخول لإرسال بلاغ.")
        db.collection("reports").add(
            mapOf(
                "placeId" to placeId, "reason" to reason.take(80), "details" to details.take(1000),
                "reporterUid" to uid, "status" to "open", "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }

    override suspend fun submitSuggestion(name: String, category: String, regionName: String, details: String): Result<Unit> =
        runCatching {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("سجّل الدخول لإرسال اقتراح.")
            db.collection("suggestions").add(
                mapOf(
                    "name" to name.take(120), "category" to category, "regionName" to regionName.take(80),
                    "details" to details.take(1000), "suggestedByUid" to uid, "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Unit
        }

    override suspend fun saveFavorites(uid: String, ids: Set<String>) {
        db.collection("users").document(uid).set(
            mapOf("favorites" to ids.toList().take(500), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    override suspend fun loadFavorites(uid: String): Set<String> {
        val d = db.collection("users").document(uid).get().await()
        return (d.get("favorites") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
    }

    companion object {
        const val PAGE_SIZE = 30L
    }
}
