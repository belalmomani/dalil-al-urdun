package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jo.guide.app.data.Category
import jo.guide.app.data.Mood
import jo.guide.app.data.Place
import jo.guide.app.data.Region
import jo.guide.app.data.RegionType
import jo.guide.app.i18n.tr
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

private fun moodIcon(m: Mood): ImageVector = when (m) {
    Mood.NATURE -> Icons.Filled.Forest
    Mood.ADVENTURE -> Icons.Filled.Hiking
    Mood.FAMILY -> Icons.Filled.FamilyRestroom
    Mood.HISTORY -> Icons.Filled.Castle
    Mood.FOOD -> Icons.Filled.Restaurant
    Mood.BUDGET -> Icons.Filled.Savings
    Mood.PARKS -> Icons.Filled.Park
}

private val categoryOrder = listOf(
    Category.TOURISM, Category.RESTAURANTS, Category.GOVERNMENT, Category.SHOPPING, Category.EDUCATION,
    Category.NEWS, Category.APPS, Category.JOBS, Category.SERVICES,
)

@Composable
private fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.width(28.dp).height(3.dp).background(JG.Red))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = JG.NavyDark)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = JG.Muted)
        }
        if (action != null && onAction != null) TextButton(onAction) { Text(action) }
    }
}

/**
 * الرئيسية («أطلس الأردن»): ترويسة كحلية بالهوية والبحث وشريط تصنيفات سريع، ثم وجهة سينمائية، ثم المزاجات،
 * ثم المحافظات (مع عدد الأماكن الفعلي)، ثم أماكن مختارة بأحجام متنوعة، ثم «وُثّق حديثًا».
 * كل الأقسام تُبنى من البيانات الفعلية؛ لا محتوى ثابت يوحي بأنه يتجدد.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenSearch: () -> Unit,
    onCategory: (Category) -> Unit,
    onMood: (Mood) -> Unit,
    onRegion: (String) -> Unit,
    onPlace: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    val catalog by vm.catalog.collectAsState()
    val favorites by vm.favorites.collectAsState()

    val places = catalog.places
    val hero: Place? = remember(places) {
        places.filter { it.featured }.sortedByDescending { it.verifiedAt ?: 0L }.firstOrNull()
            ?: places.firstOrNull { !it.coverUrl.isNullOrBlank() } ?: places.firstOrNull()
    }
    val picks: List<Place> = remember(places, hero) {
        val featured = places.filter { it.featured && it.id != hero?.id }
        (if (featured.size >= 3) featured else featured + places.filter { !it.featured && it.id != hero?.id }.sortedByDescending { !it.coverUrl.isNullOrBlank() }).take(6)
    }
    val recent: List<Place> = remember(places) {
        places.filter { !it.isSample && it.verifiedAt != null }.sortedByDescending { it.verifiedAt }.take(6)
    }
    val governorates: List<Region> = remember(catalog.regions) { catalog.regions.filter { it.type == RegionType.GOVERNORATE } }

    LazyColumn(
        Modifier.fillMaxSize().background(JG.Surface),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        // ---- الترويسة: الهوية + البحث + التصنيفات
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(JG.NavyDark, JG.Navy)))
                    .statusBarsPadding().padding(top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlagLogo(44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr("دليل الأردن", "Jordan Guide"), color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(tr("كل الأردن بين إيديك", "All of Jordan at your fingertips"), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(
                    onClick = onOpenSearch, shape = RoundedCornerShape(30.dp), color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Search, tr("بحث", "Search"), tint = JG.Red)
                        Text(tr("وين حاب تروح؟", "Where do you want to go?"), color = JG.Muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Tune, null, tint = JG.Navy)
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(categoryOrder) { c ->
                        Column(
                            Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onCategory(c) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                Icon(categoryIcon(c.id), null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Text(c.label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (catalog.isSample) item {
            InfoBanner(tr("بيانات تجريبية: لم يُربط التطبيق بقاعدة البيانات بعد. الأسماء عامة وغير موثّقة، ولا تتضمن أرقام هواتف أو أسعارًا.", "Sample data: the app is not connected to the database yet. Names are generic and unverified, with no phone numbers or prices."), modifier = Modifier.padding(horizontal = 20.dp))
        }

        // ---- الوجهة المختارة
        if (catalog.loading && hero == null) item { SkeletonBox(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(380.dp), radius = 24) }
        hero?.let { h ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(tr("وجهة مختارة", "Featured destination"))
                    PlaceHeroCard(h, h.id in favorites, { vm.toggleFavorite(h.id) }, { onPlace(h.id) }, Modifier.padding(horizontal = 20.dp))
                }
            }
        }

        catalog.error?.let { err ->
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoBanner(err, Color(0xFFB3261E), icon = Icons.Filled.WifiOff)
                    BigButton(tr("إعادة المحاولة", "Retry"), Icons.Filled.Refresh, onClick = vm::refresh)
                }
            }
        }

        // ---- اكتشف على مزاجك
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(tr("اكتشف على مزاجك", "Discover by mood"), tr("اختر مزاجًا لنعرض لك الأماكن المناسبة من بياناتنا", "Pick a mood and we’ll filter our real listings"))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Mood.entries) { m ->
                        Surface(onClick = { onMood(m) }, shape = RoundedCornerShape(26.dp), color = Color.White, modifier = Modifier.heightIn(min = 52.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(moodIcon(m), null, tint = JG.Red, modifier = Modifier.size(22.dp))
                                Text(m.title, style = MaterialTheme.typography.labelLarge, color = JG.NavyDark)
                            }
                        }
                    }
                }
            }
        }

        // ---- المحافظات (تخطيط غير متماثل)
        if (governorates.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(tr("اكتشف محافظات الأردن", "Explore Jordan’s governorates"))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    items(governorates.size) { i ->
                        val g = governorates[i]
                        val inGov = places.filter { it.regionId == g.id }
                        GovernorateTile(
                            name = g.displayName,
                            count = if (catalog.hasMore) null else inGov.size,
                            coverUrl = inGov.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl,
                            tall = i % 2 == 0,
                            onClick = { onRegion(g.id) },
                        )
                    }
                }
            }
        }

        // ---- أماكن تستحق الاكتشاف (أحجام متنوعة)
        if (picks.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(tr("أماكن تستحق الاكتشاف", "Places worth discovering"), action = tr("عرض الكل", "See all"), onAction = onSeeAll)
                val big = picks.first()
                PlaceHeroCard(big, big.id in favorites, { vm.toggleFavorite(big.id) }, { onPlace(big.id) }, height = 260.dp)
                val pair = picks.drop(1).take(2)
                if (pair.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { p -> PlaceMiniCard(p, { onPlace(p.id) }, Modifier.weight(1f), width = Dp.Unspecified, imageHeight = 130.dp) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                picks.drop(3).forEach { p -> PlaceCard(p, p.id in favorites, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }) }
            }
        }

        // ---- وُثّق حديثًا (من بيانات حقيقية: تاريخ التوثيق)
        if (recent.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(tr("وُثّق حديثًا", "Recently verified"), tr("أحدث الأماكن التي راجعها فريق التحرير", "The latest places our editors reviewed"))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent, key = { it.id }) { p -> PlaceMiniCard(p, { onPlace(p.id) }) }
                }
            }
        }

        item {
            BigButton(tr("تصفّح كل الأماكن", "Browse all places"), Icons.Filled.Explore, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), outlined = true, onClick = onSeeAll)
        }
    }
}

@Composable
private fun GovernorateTile(name: String, count: Int?, coverUrl: String?, tall: Boolean, onClick: () -> Unit) {
    val h = if (tall) 200.dp else 160.dp
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = JG.NavyDark, modifier = Modifier.width(150.dp).height(h)) {
        Box {
            PlaceImage(coverUrl, null, Category.TOURISM.id, Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to JG.NavyDark.copy(alpha = .9f))))
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count != null) Text(
                    if (count == 0) tr("لا أماكن بعد", "No places yet") else tr("$count مكان", if (count == 1) "1 place" else "$count places"),
                    color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
