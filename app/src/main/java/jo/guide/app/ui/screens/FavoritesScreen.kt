package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import jo.guide.app.i18n.tr
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: AppViewModel, onPlace: (String) -> Unit, onDiscover: () -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val auth by vm.auth.state.collectAsState()
    val list = catalog.places.filter { it.id in favorites }
    Column(Modifier.fillMaxSize().background(JG.Surface)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(JG.NavyDark).statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(tr("المحفوظات", "Saved places"), color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(tr("${list.size} مكان محفوظ", "${list.size} saved"), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoBanner(if (auth.signedIn) tr("محفوظاتك على جهازك وتتزامن مع حسابك.", "Your saved places are on your device and synced with your account.") else tr("محفوظاتك على هذا الجهاز فقط. سجّل الدخول (اختياريًا) لمزامنتها.", "Your saved places are on this device only. Sign in (optional) to sync them."), JG.Navy, icon = Icons.Filled.Info)
            }
            if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FallbackArt(null, Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)), iconSize = 44.dp)
                    Text(tr("لم تحفظ أي مكان بعد", "Nothing saved yet"), style = MaterialTheme.typography.titleMedium, color = JG.Ink)
                    Text(tr("اضغط على القلب في أي مكان ليظهر هنا.", "Tap the heart on any place to see it here."), color = JG.Muted, style = MaterialTheme.typography.bodyMedium)
                    BigButton(tr("اكتشف الأماكن", "Discover places"), Icons.Filled.Explore, onClick = onDiscover)
                }
            }
            items(list, key = { it.id }) { p -> PlaceCard(p, true, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }, Modifier.animateItem()) }
        }
    }
}
