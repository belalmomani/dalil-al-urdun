package jo.guide.app.ui

import jo.guide.app.i18n.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jo.guide.app.auth.AuthManager
import jo.guide.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CatalogState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val places: List<Place> = emptyList(),
    val regions: List<Region> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
    /** البيانات من الكاش المحلي (قد تكون قديمة). */
    val fromCache: Boolean = false,
    val isSample: Boolean = true,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val auth = AuthManager(app)
    val network = NetworkMonitor(app)

    private val repo: PlaceRepository =
        if (SupabaseApi.configured) SupabasePlaceRepository(app) else SeedPlaceRepository()
    private val favStore = FavoritesStore(app)
    private var cursor: Int? = null

    private val _catalog = MutableStateFlow(CatalogState(isSample = repo.isSample))
    val catalog = _catalog.asStateFlow()

    private val _filter = MutableStateFlow(PlaceFilter())
    val filter = _filter.asStateFlow()

    private val _favorites = MutableStateFlow(favStore.load())
    val favorites = _favorites.asStateFlow()

    private val _adsRemote = MutableStateFlow(true)
    val adsRemoteEnabled = _adsRemote.asStateFlow()

    private val _media = MutableStateFlow<Map<String, List<PlaceMedia>>>(emptyMap())
    val media = _media.asStateFlow()

    val results: StateFlow<List<Place>> = combine(_catalog, _filter) { c, f -> f.apply(c.places) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        viewModelScope.launch { _adsRemote.value = repo.adsRemoteEnabled() }
        // مزامنة المفضلة عند تسجيل الدخول فقط
        viewModelScope.launch {
            auth.state.map { it.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) runCatching {
                    val remote = repo.loadFavorites(uid)
                    val merged = _favorites.value + remote
                    setFavorites(merged, push = merged != remote)
                }
            }
        }
    }

    fun refresh() {
        cursor = null
        viewModelScope.launch {
            _catalog.update { it.copy(loading = true, error = null) }
            try {
                val regions = repo.loadRegions()
                val page = repo.loadPlaces(null)
                cursor = page.next
                _catalog.value = CatalogState(
                    loading = false, places = page.places, regions = regions, hasMore = page.next != null,
                    fromCache = page.fromCache, isSample = repo.isSample,
                )
            } catch (e: Exception) {
                _catalog.update { it.copy(loading = false, error = tr("تعذّر تحميل البيانات. تحقق من الاتصال ثم أعد المحاولة.", "Could not load data. Check your connection and try again.")) }
            }
        }
    }

    fun loadMore() {
        val c = cursor ?: return
        if (_catalog.value.loadingMore) return
        viewModelScope.launch {
            _catalog.update { it.copy(loadingMore = true) }
            try {
                val page = repo.loadPlaces(c)
                cursor = page.next
                _catalog.update { it.copy(places = it.places + page.places, loadingMore = false, hasMore = page.next != null) }
            } catch (_: Exception) {
                _catalog.update { it.copy(loadingMore = false, error = tr("تعذّر تحميل المزيد.", "Could not load more.")) }
            }
        }
    }

    fun setFilter(f: PlaceFilter) { _filter.value = f }
    fun updateFilter(block: (PlaceFilter) -> PlaceFilter) { _filter.update(block) }
    fun clearFilter() { _filter.value = PlaceFilter() }

    fun place(id: String): Place? = _catalog.value.places.firstOrNull { it.id == id }

    fun loadMedia(placeId: String) {
        if (_media.value.containsKey(placeId)) return
        viewModelScope.launch {
            // فشل الوسائط لا يمنع صفحة المكان: نخزن قائمة فارغة.
            val list = runCatching { repo.loadMedia(placeId) }.getOrDefault(emptyList())
            _media.update { it + (placeId to list) }
        }
    }

    fun toggleFavorite(id: String) {
        val cur = _favorites.value
        setFavorites(if (id in cur) cur - id else cur + id, push = true)
    }

    private fun setFavorites(ids: Set<String>, push: Boolean) {
        _favorites.value = ids
        favStore.save(ids)
        val uid = auth.state.value.uid
        if (push && uid != null) viewModelScope.launch { runCatching { repo.saveFavorites(uid, ids) } }
    }

    fun submitReport(placeId: String, reason: String, details: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repo.submitReport(placeId, reason, details)) }
    }

    fun submitSuggestion(name: String, category: String, region: String, details: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repo.submitSuggestion(name, category, region, details)) }
    }
}
