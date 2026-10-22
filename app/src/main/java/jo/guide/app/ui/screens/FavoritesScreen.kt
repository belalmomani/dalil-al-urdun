package jo.guide.app.ui.screens

import jo.guide.app.i18n.tr
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
        Box(Modifier.fillMaxWidth().background(JG.NavyDark).statusBarsPadding().padding(16.dp)) { Text(tr("المفضلة", "Favorites"), color = Color.White, style = MaterialTheme.typography.titleLarge) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoBanner(if (auth.signedIn) tr("مفضلتك محفوظة على جهازك وتتزامن مع حسابك.", "Your favorites are saved on your device and synced with your account.") else tr("مفضلتك محفوظة على هذا الجهاز فقط. سجّل الدخول (اختياريًا) لمزامنتها.", "Your favorites are saved on this device only. Sign in (optional) to sync them."), JG.Navy, icon = Icons.Filled.Info)
            }
            if (list.isEmpty()) item { EmptyState(tr("لا توجد أماكن مفضلة", "No favorite places"), tr("اضغط على القلب في أي مكان لإضافته هنا.", "Tap the heart on any place to add it here.")) }
            items(list, key = { it.id }) { p -> PlaceCard(p, true, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }) }
        }
    }
}
