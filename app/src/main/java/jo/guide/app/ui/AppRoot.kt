package jo.guide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jo.guide.app.BuildConfig
import jo.guide.app.i18n.Lang
import jo.guide.app.i18n.tr
import jo.guide.app.ads.AdPolicy
import jo.guide.app.ads.AdSlot
import jo.guide.app.data.Category
import jo.guide.app.data.PlaceFilter
import jo.guide.app.ui.screens.*
import jo.guide.app.ui.components.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private fun tabs() = listOf(
    Tab("home", tr("الرئيسية", "Home"), Icons.Filled.Home),
    Tab("explore", tr("استكشف", "Explore"), Icons.Filled.Explore),
    Tab("favorites", tr("المفضلة", "Favorites"), Icons.Filled.Favorite),
    Tab("account", tr("حسابي", "Account"), Icons.Filled.Person),
)

/**
 * الهيكل الأساسي: عمود رأسي من ثلاث مناطق منفصلة ثابتة (لا يوجد عنصر عائم فوق المحتوى):
 *  1) المحتوى القابل للتمرير (weight 1)  2) شريط التنقل السفلي  3) مساحة إعلان Start.io.
 * عند ظهور لوحة المفاتيح: imePadding يرفع كل شيء، والإعلان يُخفى كي لا يقترب من حقول الإدخال.
 */
@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val online by vm.network.online.collectAsState()
    val remote by vm.adsRemoteEnabled.collectAsState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showBar = route in tabs().map { it.route }
    val decision = AdPolicy.decide(
        appIdConfigured = BuildConfig.ADS_CONFIGURED, remoteEnabled = remote, online = online,
        keyboardOpen = imeVisible, sensitiveScreen = route == "account" || route == "admin" || route == "privacy",
    )

    CompositionLocalProvider(LocalLayoutDirection provides if (Lang.isAr) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        JordanGuideTheme {
            Column(Modifier.fillMaxSize().background(JG.NavyDark).imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().then(if (!showBar && !decision.show) Modifier.navigationBarsPadding() else Modifier)) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(vm,
                                onSearch = { q -> vm.setFilter(PlaceFilter(query = q)); goTab(nav, "explore") },
                                onCategory = { c -> vm.setFilter(PlaceFilter(categories = setOf(c.id))); goTab(nav, "explore") },
                                onPlace = { nav.navigate("place/$it") })
                        }
                        composable("explore") { ExploreScreen(vm, { nav.navigate("place/$it") }, { nav.navigate("region/$it") }) }
                        composable("favorites") { FavoritesScreen(vm) { nav.navigate("place/$it") } }
                        composable("account") { AccountScreen(vm, onAdmin = { nav.navigate("admin") }, onPrivacy = { nav.navigate("privacy") }) }
                        composable("privacy") { PrivacyScreen { nav.popBackStack() } }
                        composable("admin") { AdminScreen(vm) { nav.popBackStack() } }
                        composable("place/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            PlaceScreen(vm, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
                        }
                        composable("region/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            RegionScreen(vm, e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { nav.navigate("place/$it") }, { nav.navigate("region/$it") })
                        }
                    }
                }
                if (showBar) BottomBar(route, !decision.show) { goTab(nav, it) }
                AdSlot(decision)
            }
        }
    }
}

private fun goTab(nav: androidx.navigation.NavController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = route != "explore" // نبقي فلتر التصنيف المختار حديثًا
    }
}

@Composable
private fun BottomBar(current: String?, padForNav: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(JG.NavyDark).then(if (padForNav) Modifier.navigationBarsPadding() else Modifier).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs().forEach { t ->
            val sel = current == t.route
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Tab) { onSelect(t.route) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (sel) JG.Red else Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(t.icon, null, tint = Color.White)
                }
                Text(t.label, color = if (sel) Color.White else Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
        }
    }
}
