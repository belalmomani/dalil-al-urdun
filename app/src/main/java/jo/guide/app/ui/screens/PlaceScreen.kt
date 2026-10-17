package jo.guide.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import jo.guide.app.data.*
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun open(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { /* لا يوجد تطبيق مناسب */ }
}

private fun safeWebUri(url: String): Uri? =
    Uri.parse(url).takeIf { it.scheme == "https" || it.scheme == "http" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceScreen(vm: AppViewModel, placeId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val catalog by vm.catalog.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val mediaMap by vm.media.collectAsState()
    val place = catalog.places.firstOrNull { it.id == placeId }
    LaunchedEffect(placeId) { if (!placeId.startsWith("sample-")) vm.loadMedia(placeId) }
    var viewer by remember { mutableIntStateOf(-1) }
    var showReport by remember { mutableStateOf(false) }

    if (place == null) {
        Box(Modifier.fillMaxSize().background(JG.Surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { EmptyState("المكان غير متاح", "ربما حُذف أو لم يعد منشورًا."); BigButton("رجوع", onClick = onBack) }
        }
        return
    }
    val media = mediaMap[place.id].orEmpty()
    val images = media.filter { !it.isVideo }
    val videos = media.filter { it.isVideo }
    val cover = place.coverUrl ?: images.firstOrNull { it.isCover }?.url ?: images.firstOrNull()?.url

    Column(Modifier.fillMaxSize().background(JG.Surface).verticalScroll(rememberScrollState())) {
        Box {
            PlaceImage(cover, place.coverAlt ?: place.name, place.category, Modifier.fillMaxWidth().height(260.dp))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleIcon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onClick = onBack)
                CircleIcon(if (place.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "المفضلة") { vm.toggleFavorite(place.id) }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(place.name, style = MaterialTheme.typography.headlineMedium, color = JG.NavyDark)
            Text(listOfNotNull(Category.fromId(place.category)?.label, place.regionName).joinToString(" · "), color = JG.Muted, style = MaterialTheme.typography.bodyLarge)

            // حالة التوثيق: واضحة دائمًا
            if (place.isSample) InfoBanner("هذه بيانات تجريبية غير موثّقة. لا تعتمد عليها قبل التحقق من المصدر الرسمي.")
            else if (place.verifiedAt == null) InfoBanner("معلومات هذا المكان لم تُوثّق بعد وقد تحتاج إلى تحديث.")
            else StatusBadge("موثّق بتاريخ ${SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(place.verifiedAt))}", JG.Green, Icons.Filled.Verified)
            if (catalog.fromCache) InfoBanner("المعلومات محفوظة على جهازك وقد تكون قديمة.", icon = Icons.Filled.CloudOff)

            // أزرار الإجراءات: اتصال / خريطة / مشاركة / موقع
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (place.phone != null) BigButton("اتصال", Icons.Filled.Call, Modifier.weight(1f)) { open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}"))) }
                if (place.lat != null && place.lng != null || place.address != null)
                    BigButton("الخريطة", Icons.Filled.Map, Modifier.weight(1f), outlined = true) {
                        val q = if (place.lat != null && place.lng != null) "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.name)})"
                        else "geo:0,0?q=${Uri.encode(place.name + " " + place.address.orEmpty())}"
                        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(q)))
                    }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("مشاركة", Icons.Filled.Share, Modifier.weight(1f), outlined = true) {
                    val text = listOfNotNull(place.name, place.address, place.website).joinToString("\n") + "\n— عبر تطبيق دليل الأردن"
                    open(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "مشاركة"))
                }
                place.website?.let { w -> safeWebUri(w)?.let { u -> BigButton("الموقع", Icons.Filled.Language, Modifier.weight(1f), outlined = true) { open(ctx, Intent(Intent.ACTION_VIEW, u)) } } }
            }

            if (place.description.isNotBlank()) Text(place.description, style = MaterialTheme.typography.bodyLarge)

            // معلومات تظهر فقط عند توفرها
            val rows = buildList {
                place.phone?.let { add(Icons.Filled.Call to "الهاتف: $it") }
                place.address?.let { add(Icons.Filled.LocationOn to "العنوان: $it") }
                place.openingHours?.let { add(Icons.Filled.Schedule to "ساعات العمل: $it") }
                place.priceLevel?.let { lv -> add(Icons.Filled.Payments to "السعر: ${PriceLevels.all.firstOrNull { it.first == lv }?.second ?: ""}" + (place.priceNote?.let { " — $it" } ?: "")) }
                place.kidsFriendly?.let { add(Icons.Filled.ChildCare to if (it) "مناسب للأطفال" else "غير مخصص للأطفال") }
                if (place.ageGroups.isNotEmpty()) add(Icons.Filled.Groups to "الأعمار: " + place.ageGroups.joinToString("، ") { id -> AgeGroups.all.firstOrNull { it.first == id }?.second ?: id })
            }
            if (rows.isNotEmpty()) Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { (ic, t) -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(ic, null, tint = JG.Red); Text(t, style = MaterialTheme.typography.bodyLarge) } }
                }
            }
            if (place.features.isNotEmpty()) { Text("المرافق", style = MaterialTheme.typography.titleMedium); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { place.features.forEach { id -> SuggestionChip({}, { Text(Features.all.firstOrNull { it.first == id }?.second ?: id) }) } } }
            if (place.activities.isNotEmpty()) { Text("الأنشطة", style = MaterialTheme.typography.titleMedium); place.activities.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) } }
            if (place.alerts.isNotEmpty()) place.alerts.forEach { InfoBanner(it, Color(0xFFB3261E), icon = Icons.Filled.Warning) }

            // معرض الصور
            if (images.isNotEmpty()) {
                Text("معرض الصور", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images.size) { i -> PlaceImage(images[i].thumbUrl ?: images[i].url, images[i].alt ?: images[i].title, place.category, Modifier.size(120.dp).clickable { viewer = i }) }
                }
            }
            // الفيديوهات: لا تُحمَّل إلا عند الضغط على التشغيل
            videos.forEach { v -> VideoCard(v, place.category) }

            // المصدر
            Text(
                "المصدر: ${place.sourceName ?: "غير محدد"}" + (place.sourceUrl?.let { "\n$it" } ?: ""),
                color = JG.Muted, style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { showReport = true }) { Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp)); Text("الإبلاغ عن معلومة خاطئة أو رابط لا يعمل") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (viewer >= 0) ImageViewer(images, viewer, place.category) { viewer = -1 }
    if (showReport) ReportDialog(vm, place.id) { showReport = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageViewer(images: List<PlaceMedia>, start: Int, category: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start) { images.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    PlaceImage(images[i].url, images[i].alt ?: images[i].title, category, Modifier.fillMaxWidth().weight(1f), ContentScale.Fit)
                    val cap = listOfNotNull(images[i].title, images[i].description).joinToString(" — ")
                    if (cap.isNotBlank()) Text(cap, color = Color.White, modifier = Modifier.padding(16.dp).navigationBarsPadding())
                }
            }
            IconButton(onClose, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
        }
    }
}

@Composable
private fun VideoCard(v: PlaceMedia, category: String) {
    var started by remember(v.id) { mutableStateOf(false) }
    var error by remember(v.id) { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.Black) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            if (!started) {
                PlaceImage(v.thumbUrl, v.alt ?: v.title, category, Modifier.fillMaxSize())
                FilledIconButton(onClick = { started = true; error = null }, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.PlayArrow, "تشغيل الفيديو", Modifier.size(36.dp)) }
                v.durationSec?.let { Text("%d:%02d".format(it / 60, it % 60), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) }
            } else {
                val ctx = LocalContext.current
                val player = remember(v.id) { ExoPlayer.Builder(ctx).build() }
                DisposableEffect(player) {
                    val l = object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = "تعذّر تشغيل الفيديو. تحقق من الاتصال وأعد المحاولة." } }
                    player.addListener(l)
                    player.setMediaItem(MediaItem.fromUri(v.url)); player.prepare(); player.playWhenReady = true // بطلب من المستخدم فقط
                    onDispose { player.removeListener(l); player.release() }
                }
                AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
                error?.let { Column(Modifier.background(Color(0xCC000000)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(it, color = Color.White); BigButton("إعادة المحاولة", onClick = { error = null; player.prepare(); player.playWhenReady = true }) } }
            }
        }
    }
    listOfNotNull(v.title, v.description).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" — "), color = JG.Muted) }
}

@Composable
private fun ReportDialog(vm: AppViewModel, placeId: String, onClose: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("إبلاغ عن مشكلة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reason, { reason = it }, label = { Text("نوع المشكلة (مثال: رقم هاتف خاطئ)") }, singleLine = true)
                OutlinedTextField(details, { details = it }, label = { Text("التفاصيل") }, minLines = 3)
                msg?.let { Text(it, color = JG.Muted) }
            }
        },
        confirmButton = { TextButton(enabled = reason.isNotBlank(), onClick = { vm.submitReport(placeId, reason, details) { r -> if (r.isSuccess) onClose() else msg = r.exceptionOrNull()?.message } }) { Text("إرسال") } },
        dismissButton = { TextButton(onClose) { Text("إلغاء") } },
    )
}
