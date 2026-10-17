package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: AppViewModel, onPlace: (String) -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val auth by vm.auth.state.collectAsState()
    val list = catalog.places.filter { it.id in favorites }
    Column(Modifier.fillMaxSize().background(JG.Surface)) {
        Box(Modifier.fillMaxWidth().background(JG.NavyDark).statusBarsPadding().padding(16.dp)) { Text("المفضلة", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoBanner(if (auth.signedIn) "مفضلتك محفوظة على جهازك وتتزامن مع حسابك." else "مفضلتك محفوظة على هذا الجهاز فقط. سجّل الدخول (اختياريًا) لمزامنتها.", JG.Navy, icon = Icons.Filled.Info)
            }
            if (list.isEmpty()) item { EmptyState("لا توجد أماكن مفضلة", "اضغط على القلب في أي مكان لإضافته هنا.") }
            items(list, key = { it.id }) { p -> PlaceCard(p, true, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }) }
        }
    }
}
