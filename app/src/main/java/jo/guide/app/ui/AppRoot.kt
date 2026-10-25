package jo.guide.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import jo.guide.app.ui.components.rememberReduceMotion
import jo.guide.app.ui.components.starPath
import jo.guide.app.ui.screens.*
import jo.guide.app.ui.components.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private fun tabs() = listOf(
    Tab("home", tr("اكتشف", "Discover"), Icons.Filled.Explore),
    Tab("map", tr("الخريطة", "Map"), Icons.Filled.Map),
    Tab("favorites", tr("المحفوظات", "Saved"), Icons.Filled.Favorite),
    Tab("account", tr("حسابي", "Account"), Icons.Filled.Person),
)

/**
 * الهيكل الأساسي: عمود رأسي من ثلاث مناطق منفصلة ثابتة (لا عنصر عائم فوق المحتوى):
 *  1) المحتوى القابل للتمرير (weight 1)  2) شريط التنقل السفلي  3) مساحة إعلان Start.io.
 * عند ظهور لوحة المفاتيح: imePadding يرفع كل شيء، والإعلان يُخفى كي لا يقترب من حقول الإدخال.
 * قواعد ظهور الإعلان لم تتغير: تُخفى في الحساب والإدارة والخصوصية.
 */
@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val online by vm.network.online.collectAsState()
    val remote by vm.adsRemoteEnabled.collectAsState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val barRoutes = tabs().map { it.route } + "explore"
    val showBar = route in barRoutes
    val decision = AdPolicy.decide(
        appIdConfigured = BuildConfig.ADS_CONFIGURED, remoteEnabled = remote, online = online,
        keyboardOpen = imeVisible, sensitiveScreen = route == "account" || route == "admin" || route == "privacy",
    )
    val reduce = rememberReduceMotion()
    fun enter(): EnterTransition = if (reduce) EnterTransition.None else fadeIn(tween(180))
    fun exit(): ExitTransition = if (reduce) ExitTransition.None else fadeOut(tween(120))

    CompositionLocalProvider(LocalLayoutDirection provides if (Lang.isAr) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        JordanGuideTheme {
            Column(Modifier.fillMaxSize().background(JG.NavyDark).imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().then(if (!showBar && !decision.show) Modifier.navigationBarsPadding() else Modifier)) {
                    NavHost(nav, startDestination = "home",
                        enterTransition = { enter() }, exitTransition = { exit() },
                        popEnterTransition = { enter() }, popExitTransition = { exit() }) {
                        composable("home") {
                            HomeScreen(vm,
                                onOpenSearch = { vm.clearFilter(); goTab(nav, "explore") },
                                onCategory = { c -> vm.setFilter(PlaceFilter(categories = setOf(c.id))); goTab(nav, "explore") },
                                onMood = { m -> vm.setFilter(m.filter()); goTab(nav, "explore") },
                                onRegion = { nav.navigate("region/$it") },
                                onPlace = { nav.navigate("place/$it") },
                                onSeeAll = { vm.clearFilter(); goTab(nav, "explore") })
                        }
                        composable("explore") { ExploreScreen(vm, { nav.navigate("place/$it") }, { nav.navigate("region/$it") }, onMap = { goTab(nav, "map") }) }
                        composable("map") { MapScreen(vm, onPlace = { nav.navigate("place/$it") }, onList = { goTab(nav, "explore") }) }
                        composable("favorites") { FavoritesScreen(vm, onPlace = { nav.navigate("place/$it") }, onDiscover = { goTab(nav, "home") }) }
                        composable("account") { AccountScreen(vm, onAdmin = { nav.navigate("admin") }, onPrivacy = { nav.navigate("privacy") }) }
                        composable("privacy") { PrivacyScreen { nav.popBackStack() } }
                        composable("admin") { AdminScreen(vm) { nav.popBackStack() } }
                        composable("place/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            PlaceScreen(vm, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
                        }
                        composable("region/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            RegionScreen(vm, e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { nav.navigate("place/$it") }, { nav.navigate("region/$it") },
                                onMap = { goTab(nav, "map") }, onList = { id -> vm.setFilter(PlaceFilter(regionIds = setOf(id))); goTab(nav, "explore") })
                        }
                    }
                }
                if (showBar) BottomBar(if (route == "explore") "home" else route, !decision.show) { goTab(nav, it) }
                AdSlot(decision)
            }
        }
    }
}

private fun goTab(nav: androidx.navigation.NavController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = route != "explore" && route != "map" // نبقي الفلتر/المزاج المختار حديثًا
    }
}

/** شريط سفلي كحلي مستقل عن المحتوى. التبويب المختار: حبّة حمراء + نجمة سباعية صغيرة (تفصيل واحد فقط). */
@Composable
private fun BottomBar(current: String?, padForNav: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(JG.NavyDark).then(if (padForNav) Modifier.navigationBarsPadding() else Modifier).padding(top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs().forEach { t ->
            val sel = current == t.route
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Tab) { onSelect(t.route) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Canvas(Modifier.size(8.dp)) { if (sel) drawPath(starPath(size.width / 2, size.height / 2, size.width / 2), Color.White) }
                Box(Modifier.width(60.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (sel) JG.Red else Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(t.label, color = if (sel) Color.White else Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
