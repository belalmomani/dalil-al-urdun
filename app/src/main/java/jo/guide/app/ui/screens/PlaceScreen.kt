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
import androidx.compose.ui.graphics.Brush
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

@Composable
private fun Module(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(4.dp).height(18.dp).background(JG.Red))
            Text(title, style = MaterialTheme.typography.titleMedium, color = JG.NavyDark)
        }
        content()
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = JG.Red, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = JG.Ink)
    }
}

/**
 * صفحة المكان («دليل متخصص»): صورة واسعة بنص فوق تدرّج داكن، ثم حالة التوثيق، ثم إجراءات تظهر فقط عند توفر بياناتها الصالحة،
 * ثم وحدات تختلف بحسب نوع المكان (أثري/سياحي، مطعم، خدمة حكومية/رقمية، عام). لا أقسام فارغة ولا معلومات وهمية.
 */
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
    val cat = Category.fromId(place.category)
    val isVisit = cat == Category.TOURISM
    val isFood = cat == Category.RESTAURANTS
    val isService = cat in setOf(Category.GOVERNMENT, Category.SERVICES, Category.APPS, Category.EDUCATION, Category.JOBS, Category.NEWS)
    val webUri = place.website?.let { safeWebUri(it) }
    val hasMap = (place.lat != null && place.lng != null) || place.address != null

    Column(Modifier.fillMaxSize().background(JG.Surface).verticalScroll(rememberScrollState())) {
        Box {
            PlaceImage(cover, place.coverAlt ?: place.displayName, place.category, Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to JG.NavyDark.copy(alpha = .85f)), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleIcon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back"), onClick = onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIcon(Icons.Filled.Share, tr("مشاركة المكان", "Share place")) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, place.displayName)
                            putExtra(Intent.EXTRA_TEXT, sharePlaceText(place))
                        }
                        open(ctx, Intent.createChooser(send, tr("مشاركة المكان", "Share place")))
                    }
                    CircleIcon(if (place.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (place.id in favorites) tr("إزالة من المحفوظات", "Remove from saved") else tr("حفظ المكان", "Save place")) { vm.toggleFavorite(place.id) }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listOfNotNull(cat?.label, place.displayRegion).joinToString(" · "), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium)
                Text(place.displayName, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            // حالة التوثيق (دائمًا واضحة)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VerifyBadge(place)
                    place.verifiedAt?.let { Text(tr("بتاريخ", "on") + " " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(it)), color = JG.Muted, style = MaterialTheme.typography.bodySmall) }
                }
                if (place.isSample) InfoBanner(tr("هذه بيانات تجريبية غير موثّقة. لا تعتمد عليها قبل التحقق من المصدر الرسمي.", "This is unverified sample data. Do not rely on it before checking the official source."))
                else if (place.verifiedAt == null) InfoBanner(tr("معلومات هذا المكان لم تُوثّق بعد وقد تحتاج إلى تحديث.", "This place's information has not been verified yet and may need updating."))
                if (catalog.fromCache) InfoBanner(tr("المعلومات محفوظة على جهازك وقد تكون قديمة.", "This information is saved on your device and may be outdated."), icon = Icons.Filled.CloudOff)
            }

            // الإجراءات: لا يظهر إلا ما له بيانات صالحة
            val actions = (if (Geo.validPhone(place.phone)) 1 else 0) + (if (hasMap) 1 else 0) + (if (webUri != null) 1 else 0)
            if (actions > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (Geo.validPhone(place.phone)) BigButton(tr("اتصال", "Call"), Icons.Filled.Call) { open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone!!.trim()}"))) }
                if (hasMap) BigButton(tr("الخريطة", "Map"), Icons.Filled.Map, outlined = true) {
                    val q = if (place.lat != null && place.lng != null) "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.displayName)})"
                    else "geo:0,0?q=${Uri.encode(place.displayName + " " + place.address.orEmpty())}"
                    open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(q)))
                }
                if (webUri != null) BigButton(if (isService) tr("الرابط الرسمي", "Official link") else tr("الموقع الإلكتروني", "Website"), Icons.Filled.Language, outlined = true) { open(ctx, Intent(Intent.ACTION_VIEW, webUri)) }
            }

            // وحدة التعريف: عنوانها بحسب النوع
            if (place.displayDescription.isNotBlank()) Module(
                when { isVisit -> tr("نبذة", "About"); isFood -> tr("عن المكان", "About"); isService -> tr("وصف الخدمة", "About this service"); else -> tr("نبذة", "About") }
            ) { Text(place.displayDescription, style = MaterialTheme.typography.bodyLarge, color = JG.Ink) }

            // تنبيهات (الزيارة/الخدمة)
            place.alerts.forEach { InfoBanner(it, Color(0xFFB3261E), icon = Icons.Filled.Warning) }

            // معلومات تظهر فقط عند توفرها، بعنوان يناسب النوع
            val rows = buildList {
                if (Geo.validPhone(place.phone)) add(Icons.Filled.Call to tr("الهاتف: ${place.phone}", "Phone: ${place.phone}"))
                place.address?.let { add(Icons.Filled.LocationOn to tr("العنوان: $it", "Address: $it")) }
                place.openingHours?.let { add(Icons.Filled.Schedule to tr("ساعات العمل: $it", "Opening hours: $it")) }
                place.priceLevel?.let { lv -> add(Icons.Filled.Payments to tr("السعر", "Price") + ": ${PriceLevels.all.firstOrNull { it.first == lv }?.second ?: ""}" + (place.priceNote?.let { " — $it" } ?: "")) }
                place.kidsFriendly?.let { add(Icons.Filled.ChildCare to if (it) tr("مناسب للأطفال", "Suitable for children") else tr("غير مخصص للأطفال", "Not designed for children")) }
                if (place.ageGroups.isNotEmpty()) add(Icons.Filled.Groups to tr("الأعمار: ", "Ages: ") + place.ageGroups.joinToString(tr("، ", ", ")) { id -> AgeGroups.all.firstOrNull { it.first == id }?.second ?: id })
            }
            if (rows.isNotEmpty()) Module(
                when { isVisit -> tr("معلومات الزيارة", "Visitor information"); isFood -> tr("معلومات المطعم", "Restaurant info"); isService -> tr("معلومات التواصل والخدمة", "Contact & service info"); else -> tr("معلومات", "Information") }
            ) { rows.forEach { (ic, t) -> InfoRow(ic, t) } }

            if (place.activities.isNotEmpty()) Module(if (isVisit) tr("أهم المعالم والأنشطة", "Highlights & activities") else tr("الأنشطة", "Activities")) {
                place.activities.forEach { InfoRow(Icons.Filled.Check, it) }
            }
            if (place.features.isNotEmpty()) Module(tr("المرافق", "Facilities")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    place.features.forEach { id -> SuggestionChip({}, { Text(Features.all.firstOrNull { it.first == id }?.second ?: id) }) }
                }
            }

            // معرض الصور والفيديو
            if (images.isNotEmpty()) Module(tr("معرض الصور", "Photo gallery")) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images.size) { i -> PlaceImage(images[i].thumbUrl ?: images[i].url, images[i].alt ?: images[i].title, place.category, Modifier.size(width = 150.dp, height = 110.dp).clickable { viewer = i }, shape = RoundedCornerShape(14.dp)) }
                }
            }
            if (videos.isNotEmpty()) Module(tr("فيديو", "Video")) { videos.forEach { v -> VideoCard(v, place.category) } }

            // المصدر + الإبلاغ
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr("المصدر: ", "Source: ") + (place.sourceName ?: tr("غير محدد", "Not specified")) + (place.sourceUrl?.let { "\n$it" } ?: ""),
                    color = JG.Muted, style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { showReport = true }) { Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp)); Text(tr("الإبلاغ عن معلومة خاطئة أو رابط لا يعمل", "Report wrong info or a broken link")) }
            }
            Spacer(Modifier.height(16.dp))
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
