package jo.guide.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import jo.guide.app.data.Category
import jo.guide.app.data.Place
import jo.guide.app.ui.JG
import kotlin.math.cos
import kotlin.math.sin

fun categoryIcon(id: String?): ImageVector = when (id) {
    Category.TOURISM.id -> Icons.Filled.Terrain
    Category.RESTAURANTS.id -> Icons.Filled.Restaurant
    Category.GOVERNMENT.id -> Icons.Filled.AccountBalance
    Category.SHOPPING.id -> Icons.Filled.ShoppingBag
    Category.EDUCATION.id -> Icons.Filled.School
    Category.NEWS.id -> Icons.Filled.Newspaper
    Category.JOBS.id -> Icons.Filled.Work
    Category.APPS.id -> Icons.Filled.PhoneAndroid
    else -> Icons.Filled.HomeRepairService
}

/** شعار مستوحى من العلم الأردني، مرسوم برمجيًا (أصل مملوك للمشروع). */
@Composable
fun FlagLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Color.White)
            .semantics { contentDescription = "شعار دليل الأردن" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.72f).clip(RoundedCornerShape(size * 0.08f))) {
            val h = this.size.height / 3
            drawRect(Color(0xFF111111), Offset(0f, 0f), Size(this.size.width, h))
            drawRect(Color.White, Offset(0f, h), Size(this.size.width, h))
            drawRect(Color(0xFF007A3D), Offset(0f, 2 * h), Size(this.size.width, h))
            val tri = Path().apply {
                moveTo(0f, 0f); lineTo(this@Canvas.size.width * 0.55f, this@Canvas.size.height / 2); lineTo(0f, this@Canvas.size.height); close()
            }
            drawPath(tri, Color(0xFFCE1126))
            // نجمة سباعية بيضاء
            val cx = this.size.width * 0.16f
            val cy = this.size.height / 2
            val r = this.size.height * 0.1f
            val star = Path()
            for (i in 0 until 14) {
                val rad = if (i % 2 == 0) r else r * 0.45f
                val a = Math.PI / 7 * i - Math.PI / 2
                val x = cx + (rad * cos(a)).toFloat()
                val y = cy + (rad * sin(a)).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, Color.White)
        }
    }
}

@Composable
fun CategoryCard(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = JG.Red, modifier = Modifier.size(34.dp))
            Text(label, color = JG.NavyDark, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null, onDark: Boolean = false) {
    val c = if (onDark) Color.White else JG.NavyDark
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = JG.Red, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = if (onDark) Color.White.copy(alpha = .25f) else JG.Line)
    }
}

/** صورة بتحميل تدريجي وصورة بديلة عند الفشل؛ لا تمدّد النسبة (Crop). فشل الصورة لا يؤثر على الصفحة. */
@Composable
fun PlaceImage(url: String?, alt: String?, categoryId: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Color(0xFFDDE3EE)), contentAlignment = Alignment.Center) {
            Icon(categoryIcon(categoryId), null, tint = JG.Navy.copy(alpha = .55f), modifier = Modifier.size(36.dp))
        }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        if (url.isNullOrBlank()) placeholder() else SubcomposeAsyncImage(
            model = url, contentDescription = alt, contentScale = contentScale, modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize().background(Color(0xFFE9EDF4)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } },
            error = { placeholder() },
        )
    }
}

@Composable
fun StatusBadge(text: String, color: Color, icon: ImageVector? = null) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .35f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, color = color, fontSize = 12.sp)
        }
    }
}

@Composable
fun PlaceCard(place: Place, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaceImage(place.coverUrl, place.coverAlt ?: place.name, place.category, Modifier.size(92.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, color = JG.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val cat = Category.fromId(place.category)?.label ?: ""
                Text(listOfNotNull(cat, place.regionName).joinToString(" · "), color = JG.Muted, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (place.isSample) StatusBadge("تجريبي", JG.Amber)
                    else if (place.verifiedAt == null) StatusBadge("يحتاج توثيقًا", JG.Amber)
                    if (place.kidsFriendly == true) StatusBadge("للأطفال", JG.Green, Icons.Filled.ChildCare)
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = JG.Red,
                )
            }
        }
    }
}

@Composable
fun InfoBanner(text: String, color: Color = JG.Amber, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Info) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = .1f), border = BorderStroke(1.dp, color.copy(alpha = .3f))) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(text, color = JG.Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.SearchOff, null, tint = JG.Navy.copy(alpha = .5f), modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = JG.Ink)
        Text(body, color = JG.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val m = modifier.heightIn(min = 52.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    if (outlined) OutlinedButton(onClick, m, enabled, shape, content = content)
    else Button(onClick, m, enabled, shape, content = content)
}

@Composable
fun CircleIcon(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.size(48.dp).clickable(onClick = onClick), shape = CircleShape, color = Color.White.copy(alpha = .9f), shadowElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = JG.NavyDark) }
    }
}
