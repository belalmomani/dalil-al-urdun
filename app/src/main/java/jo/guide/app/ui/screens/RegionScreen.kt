package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

/** صفحة محافظة/مدينة/قرية/حي: الأماكن التابعة + المناطق الفرعية. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegionScreen(vm: AppViewModel, regionId: String, onBack: () -> Unit, onPlace: (String) -> Unit, onRegion: (String) -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val region = catalog.regions.firstOrNull { it.id == regionId }
    val children = catalog.regions.filter { it.parentId == regionId }
    val ids = setOf(regionId) + children.map { it.id }
    val places = catalog.places.filter { it.regionId in ids }

    Scaffold(
        containerColor = JG.Surface,
        topBar = {
            TopAppBar(
                title = { Text(region?.let { "${it.type.label} ${it.name}" } ?: "المنطقة") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JG.NavyDark, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (children.isNotEmpty()) item {
                Column {
                    Text("المناطق التابعة", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { children.forEach { c -> AssistChip({ onRegion(c.id) }, { Text(c.name) }) } }
                }
            }
            if (places.isEmpty()) item { EmptyState("لا توجد أماكن منشورة بعد", "لم تُضف أماكن موثّقة لهذه المنطقة. يمكنك اقتراح مكان من صفحة الحساب.") }
            items(places, key = { it.id }) { p -> PlaceCard(p, p.id in favorites, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }) }
        }
    }
}
