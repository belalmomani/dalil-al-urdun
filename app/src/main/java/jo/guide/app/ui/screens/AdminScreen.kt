package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jo.guide.app.auth.AccessStatus
import jo.guide.app.auth.AdminAccess
import jo.guide.app.data.AdminRepository
import jo.guide.app.data.AdminStats
import jo.guide.app.data.InboxItem
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*
import kotlinx.coroutines.launch

/**
 * لوحة الإدارة داخل التطبيق: تظهر فقط لحساب لديه claim الإدارة (يتحقق منه الخادم أيضًا في كل استعلام).
 * الإدارة الكاملة للمحتوى والوسائط في لوحة الويب (admin-web).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(vm: AppViewModel, onBack: () -> Unit) {
    val auth by vm.auth.state.collectAsState()
    val allowed = AdminAccess.canSeeAdminDashboard(auth.status)
    val repo = remember { AdminRepository() }
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var inbox by remember { mutableStateOf<List<InboxItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        loading = true; error = null
        try { stats = repo.stats(); inbox = repo.inbox() } catch (e: Exception) { error = "رفض الخادم الطلب أو تعذّر الاتصال: ${e.message ?: ""}" }
        loading = false
    }
    LaunchedEffect(allowed) { if (allowed) load() }

    Scaffold(
        containerColor = JG.Surface,
        topBar = { TopAppBar(title = { Text("لوحة الإدارة") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = JG.NavyDark, titleContentColor = Color.White, navigationIconContentColor = Color.White)) },
    ) { pad ->
        if (!allowed) {
            Box(Modifier.padding(pad).fillMaxSize().padding(24.dp)) {
                InfoBanner(
                    when (auth.status) {
                        AccessStatus.EMAIL_NOT_VERIFIED -> "أكّد بريدك الإلكتروني أولًا."
                        AccessStatus.SIGNED_OUT -> "سجّل الدخول بحساب الإدارة."
                        else -> "هذا الحساب لا يملك صلاحية الإدارة. الصلاحية تُمنح من الخادم فقط."
                    }, Color(0xFFB3261E), icon = Icons.Filled.Lock,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { InfoBanner("حساب المدير: ${auth.email}. الإدارة الكاملة (إضافة وتعديل الأماكن والوسائط) من لوحة الويب.", JG.Green, icon = Icons.Filled.VerifiedUser) }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { e -> item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { InfoBanner(e, Color(0xFFB3261E)); BigButton("إعادة المحاولة", onClick = { load() }) } } }
            stats?.let { s ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("الأماكن", s.places, Modifier.weight(1f)); Stat("منشور", s.published, Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("مسودات", s.drafts, Modifier.weight(1f)); Stat("قيد المراجعة", s.inReview, Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("المستخدمون", s.users, Modifier.weight(1f)); Stat("الوسائط", s.media, Modifier.weight(1f)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("بلاغات مفتوحة", s.openReports, Modifier.weight(1f)); Stat("اقتراحات", s.pendingSuggestions, Modifier.weight(1f)) }
                    }
                }
            }
            item { Text("البلاغات والاقتراحات", style = MaterialTheme.typography.titleMedium) }
            if (inbox.isEmpty() && !loading) item { EmptyState("لا شيء بانتظار المراجعة", "") }
            items(inbox, key = { it.kind + it.id }) { it2 ->
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(it2.title, style = MaterialTheme.typography.titleMedium)
                        if (it2.details.isNotBlank()) Text(it2.details, color = JG.Muted)
                        BigButton("تمت المراجعة", Icons.Filled.Check, outlined = true) {
                            scope.launch { try { repo.resolve(it2, auth.uid ?: ""); load() } catch (e: Exception) { error = "تعذّر الحفظ: ${e.message}" } }
                        }
                    }
                }
            }
            item { BigButton("تحديث", Icons.Filled.Refresh, Modifier.fillMaxWidth(), outlined = true) { load() } }
        }
    }
}

@Composable
private fun Stat(label: String, value: Long, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.White) {
        Column(Modifier.padding(14.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineMedium, color = JG.Red)
            Text(label, color = JG.Muted)
        }
    }
}
