package jo.guide.app.ui.screens

import jo.guide.app.i18n.tr
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
import jo.guide.app.ui.sharePlaceText
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) { EmptyState(tr("المكان غير متاح", "Place unavailable"), tr("ربما حُذف أو لم يعد منشورًا.", "It may have been removed or unpublished.")); BigButton(tr("رجوع", "Back"), onClick = onBack) }
        }
        return
    }
    val media = mediaMap[place.id].orEmpty()
    val images = media.filter { !it.isVideo }
    val videos = media.filter { it.isVideo }
    val cover = place.coverUrl ?: images.firstOrNull { it.isCover }?.url ?: images.firstOrNull()?.url

    Column(Modifier.fillMaxSize().background(JG.Surface).verticalScroll(rememberScrollState())) {
        Box {
            PlaceImage(cover, place.coverAlt ?: place.displayName, place.category, Modifier.fillMaxWidth().height(260.dp))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleIcon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back"), onClick = onBack)
                CircleIcon(if (place.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, tr("المفضلة", "Favorites")) { vm.toggleFavorite(place.id) }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(place.displayName, style = MaterialTheme.typography.headlineMedium, color = JG.NavyDark)
            Text(listOfNotNull(Category.fromId(place.category)?.label, place.displayRegion).joinToString(" · "), color = JG.Muted, style = MaterialTheme.typography.bodyLarge)

            // حالة التوثيق: واضحة دائمًا
            if (place.isSample) InfoBanner(tr("هذه بيانات تجريبية غير موثّقة. لا تعتمد عليها قبل التحقق من المصدر الرسمي.", "This is unverified sample data. Do not rely on it before checking the official source."))
            else if (place.verifiedAt == null) InfoBanner(tr("معلومات هذا المكان لم تُوثّق بعد وقد تحتاج إلى تحديث.", "This place's information has not been verified yet and may need updating."))
            else StatusBadge(tr("موثّق بتاريخ", "Verified on") + " " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(place.verifiedAt)), JG.Green, Icons.Filled.Verified)
            if (catalog.fromCache) InfoBanner(tr("المعلومات محفوظة على جهازك وقد تكون قديمة.", "This information is saved on your device and may be outdated."), icon = Icons.Filled.CloudOff)

            // أزرار الإجراءات: اتصال / خريطة / مشاركة / موقع
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (place.phone != null) BigButton(tr("اتصال", "Call"), Icons.Filled.Call, Modifier.weight(1f)) { open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}"))) }
                if (place.lat != null && place.lng != null || place.address != null)
                    BigButton(tr("الخريطة", "Map"), Icons.Filled.Map, Modifier.weight(1f), outlined = true) {
                        val q = if (place.lat != null && place.lng != null) "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.displayName)})"
                        else "geo:0,0?q=${Uri.encode(place.name + " " + place.address.orEmpty())}"
                        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(q)))
                    }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(tr("مشاركة", "Share"), Icons.Filled.Share, Modifier.weight(1f), outlined = true) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, place.displayName)
                        putExtra(Intent.EXTRA_TEXT, sharePlaceText(place))
                    }
                    open(ctx, Intent.createChooser(send, tr("مشاركة المكان", "Share place")))
                }
                place.website?.let { w -> safeWebUri(w)?.let { u -> BigButton(tr("الموقع", "Website"), Icons.Filled.Language, Modifier.weight(1f), outlined = true) { open(ctx, Intent(Intent.ACTION_VIEW, u)) } } }
            }

            if (place.displayDescription.isNotBlank()) Text(place.displayDescription, style = MaterialTheme.typography.bodyLarge)

            // معلومات تظهر فقط عند توفرها
            val rows = buildList {
                place.phone?.let { add(Icons.Filled.Call to tr("الهاتف: $it", "Phone: $it")) }
                place.address?.let { add(Icons.Filled.LocationOn to tr("العنوان: $it", "Address: $it")) }
                place.openingHours?.let { add(Icons.Filled.Schedule to tr("ساعات العمل: $it", "Opening hours: $it")) }
                place.priceLevel?.let { lv -> add(Icons.Filled.Payments to tr("السعر", "Price") + ": ${PriceLevels.all.firstOrNull { it.first == lv }?.second ?: ""}" + (place.priceNote?.let { " — $it" } ?: "")) }
                place.kidsFriendly?.let { add(Icons.Filled.ChildCare to if (it) tr("مناسب للأطفال", "Suitable for children") else tr("غير مخصص للأطفال", "Not designed for children")) }
                if (place.ageGroups.isNotEmpty()) add(Icons.Filled.Groups to tr("الأعمار: ", "Ages: ") + place.ageGroups.joinToString(tr("، ", ", ")) { id -> AgeGroups.all.firstOrNull { it.first == id }?.second ?: id })
            }
            if (rows.isNotEmpty()) Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { (ic, t) -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(ic, null, tint = JG.Red); Text(t, style = MaterialTheme.typography.bodyLarge) } }
                }
            }
            if (place.features.isNotEmpty()) { Text(tr("المرافق", "Facilities"), style = MaterialTheme.typography.titleMedium); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { place.features.forEach { id -> SuggestionChip({}, { Text(Features.all.firstOrNull { it.first == id }?.second ?: id) }) } } }
            if (place.activities.isNotEmpty()) { Text(tr("الأنشطة", "Activities"), style = MaterialTheme.typography.titleMedium); place.activities.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) } }
            if (place.alerts.isNotEmpty()) place.alerts.forEach { InfoBanner(it, Color(0xFFB3261E), icon = Icons.Filled.Warning) }

            // معرض الصور
            if (images.isNotEmpty()) {
                Text(tr("معرض الصور", "Photo gallery"), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images.size) { i -> PlaceImage(images[i].thumbUrl ?: images[i].url, images[i].alt ?: images[i].title, place.category, Modifier.size(120.dp).clickable { viewer = i }) }
                }
            }
            // الفيديوهات: لا تُحمَّل إلا عند الضغط على التشغيل
            videos.forEach { v -> VideoCard(v, place.category) }

            // المصدر
            Text(
                tr("المصدر: ", "Source: ") + (place.sourceName ?: tr("غير محدد", "Not specified")) + (place.sourceUrl?.let { "\n$it" } ?: ""),
                color = JG.Muted, style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { showReport = true }) { Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp)); Text(tr("الإبلاغ عن معلومة خاطئة أو رابط لا يعمل", "Report wrong info or a broken link")) }
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
            IconButton(onClose, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) { Icon(Icons.Filled.Close, tr("إغلاق", "Close"), tint = Color.White) }
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
                FilledIconButton(onClick = { started = true; error = null }, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.PlayArrow, tr("تشغيل الفيديو", "Play video"), Modifier.size(36.dp)) }
                v.durationSec?.let { Text("%d:%02d".format(it / 60, it % 60), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) }
            } else {
                val ctx = LocalContext.current
                val player = remember(v.id) { ExoPlayer.Builder(ctx).build() }
                DisposableEffect(player) {
                    val l = object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = tr("تعذّر تشغيل الفيديو. تحقق من الاتصال وأعد المحاولة.", "Could not play the video. Check your connection and try again.") } }
                    player.addListener(l)
                    player.setMediaItem(MediaItem.fromUri(v.url)); player.prepare(); player.playWhenReady = true // بطلب من المستخدم فقط
                    onDispose { player.removeListener(l); player.release() }
                }
                AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
                error?.let { Column(Modifier.background(Color(0xCC000000)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(it, color = Color.White); BigButton(tr("إعادة المحاولة", "Retry"), onClick = { error = null; player.prepare(); player.playWhenReady = true }) } }
            }
        }
    }
    listOfNotNull(v.title, v.description).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" — "), color = JG.Muted) }
}

private val reportReasons = listOf(
    // (الوسم العربي الذي يراه فريق الإدارة, الإنجليزي للعرض)
    "رقم هاتف خاطئ" to "Wrong phone number",
    "عنوان أو موقع خاطئ" to "Wrong address or location",
    "المكان مغلق أو لم يعد موجودًا" to "Closed or no longer exists",
    "رابط لا يعمل" to "Broken link",
    "معلومات قديمة أو غير دقيقة" to "Outdated or inaccurate info",
    "محتوى غير مناسب" to "Inappropriate content",
    "أخرى" to "Other",
)

@Composable
private fun ReportDialog(vm: AppViewModel, placeId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val auth by vm.auth.state.collectAsState()
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val canSend = auth.signedIn && reason != null && !busy && (reason != "أخرى" || details.isNotBlank())
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("إبلاغ عن مشكلة", "Report a problem")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!auth.signedIn) InfoBanner(tr("سجّل الدخول من تبويب «حسابي» لإرسال البلاغ. هذا يمنع الإساءة ويتيح لنا متابعة البلاغ.", "Sign in from the Account tab to send a report. This prevents abuse and lets us follow up."))
                reportReasons.forEach { (ar, en) ->
                    Row(Modifier.fillMaxWidth().clickable { reason = ar }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == ar, onClick = { reason = ar })
                        Text(tr(ar, en))
                    }
                }
                OutlinedTextField(details, { details = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text(tr("تفاصيل (اختياري)", "Details (optional)")) }, minLines = 3)
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = canSend, onClick = {
                busy = true; msg = null
                vm.submitReport(placeId, reason.orEmpty(), details) { r ->
                    busy = false
                    if (r.isSuccess) {
                        android.widget.Toast.makeText(ctx, tr("شكرًا، وصل بلاغك وسنراجعه.", "Thanks, your report was received and will be reviewed."), android.widget.Toast.LENGTH_LONG).show()
                        onClose()
                    } else msg = r.exceptionOrNull()?.message ?: tr("تعذّر الإرسال.", "Could not send.")
                }
            }) { Text(if (busy) tr("جارٍ الإرسال…", "Sending…") else tr("إرسال", "Send")) }
        },
        dismissButton = { TextButton(onClose) { Text(tr("إلغاء", "Cancel")) } },
    )
}
