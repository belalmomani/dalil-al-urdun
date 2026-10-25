package jo.guide.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jo.guide.app.data.Category
import jo.guide.app.data.Geo
import jo.guide.app.data.PlaceFilter
import jo.guide.app.data.RegionType
import jo.guide.app.i18n.tr
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * تبويب الخريطة (OpenStreetMap عبر osmdroid): تحريك وتقريب، علامات للأماكن ذات الإحداثيات الموثّقة فقط،
 * بطاقة معاينة صغيرة، لوحة محافظة، «موقعي» والأماكن القريبة (يُحسب داخل الجهاز ولا يُرسل لأي خادم).
 * الخريطة ليست الوسيلة الوحيدة: زر «القائمة» يفتح نفس النتائج في الاستكشاف مع الفلاتر نفسها.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(vm: AppViewModel, onPlace: (String) -> Unit, onList: () -> Unit) {
    val ctx = LocalContext.current
    val catalog by vm.catalog.collectAsState()
    val results by vm.results.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val reduce = rememberReduceMotion()

    val mapped = remember(results) { results.filter(Geo::hasCoords) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var govId by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val selected = selectedId?.let { id -> catalog.places.firstOrNull { it.id == id } }
    val gov = govId?.let { id -> catalog.regions.firstOrNull { it.id == id } }
    val nearby = remember(myLoc, catalog.places) { myLoc?.let { Geo.nearby(catalog.places, it.first, it.second).take(8) }.orEmpty() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            val loc = lastKnown(ctx)
            if (loc != null) { myLoc = loc.latitude to loc.longitude; note = null }
            else note = tr("تعذّر تحديد موقعك. تأكد من تفعيل خدمة الموقع ثم حاول مجددًا.", "Could not get your location. Make sure location is on and try again.")
        } else note = tr("لم يُمنح إذن الموقع. يمكنك متابعة التصفح بدونه.", "Location permission was not granted. You can keep browsing without it.")
    }

    fun moveTo(lat: Double, lng: Double, zoom: Double) {
        val mv = mapView ?: return
        if (reduce) { mv.controller.setZoom(zoom); mv.controller.setCenter(GeoPoint(lat, lng)) }
        else { mv.controller.animateTo(GeoPoint(lat, lng), zoom, 600L) }
    }
    LaunchedEffect(govId, mapView) {
        val c = govId?.let { Geo.governorateCenters[it] } ?: return@LaunchedEffect
        moveTo(c.first, c.second, 9.5)
    }
    LaunchedEffect(myLoc, mapView) { myLoc?.let { moveTo(it.first, it.second, 12.0) } }
    LaunchedEffect(selectedId) { selected?.takeIf(Geo::hasCoords)?.let { moveTo(it.lat!!, it.lng!!, 12.0) } }

    Box(Modifier.fillMaxSize().background(JG.Surface)) {
        MapContainer(
            onReady = { mapView = it },
            places = mapped, myLoc = myLoc, selectedId = selectedId,
            onMarker = { selectedId = it; govId = null },
            onEmptyTap = { selectedId = null },
        )

        // ---- اللوحة العلوية: عنوان + قائمة + المحافظات + ملاحظة العدد
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)).background(JG.NavyDark)
                    .statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("الخريطة", "Map"), color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(
                            tr("على الخريطة ${mapped.size} من ${results.size} نتيجة", "${mapped.size} of ${results.size} results on the map"),
                            color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onList, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(6.dp)); Text(tr("القائمة", "List"))
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catalog.regions.filter { it.type == RegionType.GOVERNORATE }, key = { it.id }) { r ->
                        FilterChip(
                            selected = govId == r.id, onClick = { govId = if (govId == r.id) null else r.id; selectedId = null },
                            label = { Text(r.displayName) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = Color.White, selectedContainerColor = JG.Red, selectedLabelColor = Color.White),
                        )
                    }
                }
            }
            if (!catalog.loading && mapped.isEmpty()) {
                InfoBanner(
                    tr("لا توجد بعد أماكن بإحداثيات موثّقة ضمن هذه النتائج، لذلك لا تظهر علامات. تُضاف الإحداثيات (خط العرض والطول) من لوحة الإدارة. استخدم «القائمة» لتصفّح الأماكن.", "No places in these results have verified coordinates yet, so no markers are shown. Coordinates (latitude/longitude) are added from the admin panel. Use “List” to browse places."),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // ---- اللوحة السفلية: موقعي + قريب مني + معاينة/محافظة + إسناد الخريطة
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledIconButton(
                    onClick = {
                        val l = if (hasLocationPermission(ctx)) lastKnown(ctx) else null
                        if (l != null) { myLoc = l.latitude to l.longitude; note = null }
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = JG.Red),
                ) { Icon(Icons.Filled.MyLocation, tr("موقعي", "My location")) }
            }
            note?.let { InfoBanner(it, JG.Amber) }

            when {
                selected != null -> MapPreviewCard(selected, selected.id in favorites, { vm.toggleFavorite(selected.id) }, { onPlace(selected.id) }, { selectedId = null })
                gov != null -> {
                    val inGov = catalog.places.filter { it.regionId == gov.id }
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(gov.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton({ govId = null }) { Icon(Icons.Filled.Close, tr("إغلاق", "Close")) }
                            }
                            Text(
                                tr("${inGov.size} مكان مسجّل، منها ${inGov.count(Geo::hasCoords)} بإحداثيات على الخريطة", "${inGov.size} places registered, ${inGov.count(Geo::hasCoords)} with map coordinates"),
                                color = JG.Muted, style = MaterialTheme.typography.bodyMedium,
                            )
                            BigButton(tr("عرض أماكن المحافظة", "Show governorate places"), Icons.AutoMirrored.Filled.List, Modifier.fillMaxWidth()) {
                                vm.setFilter(PlaceFilter(regionIds = setOf(gov.id))); onList()
                            }
                        }
                    }
                }
                nearby.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("قريب منك", "Near you"), color = JG.NavyDark, style = MaterialTheme.typography.labelLarge, modifier = Modifier.background(Color.White.copy(alpha = .85f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(nearby, key = { it.first.id }) { (p, km) ->
                            Surface(onClick = { selectedId = p.id }, shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(p.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(tr("%.1f كم".format(km), "%.1f km".format(km)), color = JG.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                myLoc != null -> Text(tr("لا أماكن بإحداثيات ضمن 30 كم منك.", "No places with coordinates within 30 km of you."), color = JG.NavyDark, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(Color.White.copy(alpha = .85f), RoundedCornerShape(8.dp)).padding(8.dp))
            }
            Text("© OpenStreetMap contributors", color = JG.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Start).background(Color.White.copy(alpha = .8f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** بطاقة معاينة المكان على الخريطة (النمط ٤): صغيرة كي لا تحجب الخريطة. */
@Composable
private fun MapPreviewCard(place: jo.guide.app.data.Place, favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaceImage(place.coverUrl, place.coverAlt ?: place.displayName, place.category, Modifier.size(72.dp), shape = RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(place.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(listOfNotNull(Category.fromId(place.category)?.label, place.displayRegion).joinToString(" · "), color = JG.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                VerifyBadge(place)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FavoriteButton(favorite, onFavorite)
                TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr("التفاصيل", "Details")) }
            }
            IconButton(onClose, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, tr("إغلاق", "Close"), Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun MapContainer(
    onReady: (MapView) -> Unit,
    places: List<jo.guide.app.data.Place>,
    myLoc: Pair<Double, Double>?,
    selectedId: String?,
    onMarker: (String) -> Unit,
    onEmptyTap: () -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mv = remember {
        // الكاش داخل مجلد التطبيق (بدون أذونات تخزين). User-Agent مطلوب من سياسة OSM.
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 5.0
            controller.setZoom(7.3)
            controller.setCenter(GeoPoint(31.25, 36.5)) // وسط الأردن
        }
    }
    DisposableEffect(owner, mv) {
        onReady(mv)
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_RESUME -> mv.onResume(); Lifecycle.Event.ON_PAUSE -> mv.onPause(); else -> {} }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); mv.onPause(); mv.onDetach() }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mv },
        update = { view ->
            view.overlays.clear()
            places.forEach { p ->
                val m = Marker(view)
                m.position = GeoPoint(p.lat!!, p.lng!!)
                m.title = p.displayName
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                m.alpha = if (selectedId == null || selectedId == p.id) 1f else 0.6f
                m.setOnMarkerClickListener { _, _ -> onMarker(p.id); true }
                view.overlays.add(m)
            }
            myLoc?.let { (la, ln) ->
                val me = Marker(view)
                me.position = GeoPoint(la, ln)
                me.title = tr("موقعي", "My location")
                me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                view.overlays.add(me)
            }
            view.invalidate()
        },
    )
}

private fun hasLocationPermission(ctx: Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** آخر موقع معروف من المزودات المتاحة (بدون مكتبات إضافية). يبقى على الجهاز. */
@SuppressLint("MissingPermission")
private fun lastKnown(ctx: Context): Location? {
    if (!hasLocationPermission(ctx)) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
}
