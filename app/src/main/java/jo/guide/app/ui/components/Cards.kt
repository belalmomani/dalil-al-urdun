package jo.guide.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jo.guide.app.data.Category
import jo.guide.app.data.Place
import jo.guide.app.i18n.tr
import jo.guide.app.ui.JG

private fun Place.meta(): String = listOfNotNull(Category.fromId(category)?.label, displayRegion).joinToString(" · ")

/** حالة التوثيق: أيقونة + نص (لا نعتمد على اللون وحده). */
@Composable
fun VerifyBadge(place: Place, onDark: Boolean = false) {
    when {
        place.isSample -> StatusBadge(tr("تجريبي", "Sample"), if (onDark) Color(0xFFFFD27A) else JG.Amber, Icons.Filled.Info)
        place.verifiedAt == null -> StatusBadge(tr("يحتاج توثيقًا", "Needs verification"), if (onDark) Color(0xFFFFD27A) else JG.Amber, Icons.Filled.PendingActions)
        else -> StatusBadge(tr("موثّق", "Verified"), if (onDark) Color(0xFF7FE0A8) else JG.Green, Icons.Filled.Verified)
    }
}

/** زر القلب: نبضة صغيرة عند الإضافة (تُعطَّل مع تقليل الحركة). */
@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = JG.Red, background: Color? = null) {
    val reduce = rememberReduceMotion()
    var bump by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bump && !reduce) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { bump = false }, label = "heart",
    )
    IconButton(onClick = { if (!favorite) bump = true; onClick() }, modifier = modifier.size(48.dp).then(if (background != null) Modifier.background(background, androidx.compose.foundation.shape.CircleShape) else Modifier)) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (favorite) tr("إزالة من المحفوظات", "Remove from saved") else tr("حفظ المكان", "Save place"),
            tint = tint, modifier = Modifier.scale(scale),
        )
    }
}

/**
 * النمط ١: بطاقة سينمائية كبيرة. الصورة هي الأهم، والنص فوق تدرّج داكن خفيف لضمان وضوحه.
 * عند غياب صورة موثوقة: بديل أنيق (لا صورة مختلقة).
 */
@Composable
fun PlaceHeroCard(place: Place, favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 380.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))) {
        PlaceImage(place.coverUrl, place.coverAlt ?: place.displayName, place.category, Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to JG.NavyDark.copy(alpha = 0.92f))))
        Row(Modifier.align(Alignment.TopStart).padding(12.dp)) { VerifyBadge(place, onDark = true) }
        FavoriteButton(favorite, onFavorite, Modifier.align(Alignment.TopEnd).padding(8.dp), tint = Color.White, background = Color.Black.copy(alpha = .35f))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(place.meta(), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium)
            Text(place.displayName, color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (place.displayDescription.isNotBlank())
                Text(place.displayDescription, color = Color.White.copy(alpha = .92f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = JG.Red), shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(tr("افتح التفاصيل", "Open details"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** النمط ٢: صف تحريري أفقي (صورة + اسم + تصنيف + توثيق). بدون ظل ولا حدود. */
@Composable
fun PlaceCard(place: Place, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hasImage = !place.coverUrl.isNullOrBlank()
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
        if (hasImage) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaceImage(place.coverUrl, place.coverAlt ?: place.displayName, place.category, Modifier.size(96.dp), shape = RoundedCornerShape(16.dp))
                RowText(place, Modifier.weight(1f))
                FavoriteButton(favorite, onFavorite)
            }
        } else {
            // النمط ٥: بلا صورة (لا صورة موثوقة) → بطاقة نصية بشريط أحمر وأيقونة التصنيف
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(JG.Red))
                Icon(categoryIcon(place.category), null, tint = JG.Navy, modifier = Modifier.padding(start = 14.dp, end = 4.dp).size(28.dp))
                RowText(place, Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 8.dp))
                FavoriteButton(favorite, onFavorite)
            }
        }
    }
}

@Composable
private fun RowText(place: Place, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(place.displayName, style = MaterialTheme.typography.titleMedium, color = JG.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(place.meta(), color = JG.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VerifyBadge(place)
            if (place.kidsFriendly == true) StatusBadge(tr("للأطفال", "Kids"), JG.Green, Icons.Filled.ChildCare)
        }
    }
}

/** النمط ٣: بطاقة صغيرة للقوائم الأفقية: صورة ثم نص مباشرة على الخلفية (بلا صندوق). */
@Composable
fun PlaceMiniCard(place: Place, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 160.dp, imageHeight: androidx.compose.ui.unit.Dp = 120.dp, onDark: Boolean = false) {
    val ink = if (onDark) Color.White else JG.Ink
    val sub = if (onDark) Color.White.copy(alpha = .8f) else JG.Muted
    Column(modifier.width(width).semantics { contentDescription = place.displayName }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
            PlaceImage(place.coverUrl, place.coverAlt ?: place.displayName, place.category, Modifier.fillMaxWidth().height(imageHeight), shape = RoundedCornerShape(16.dp))
        }
        Text(place.displayName, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(place.meta(), color = sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
