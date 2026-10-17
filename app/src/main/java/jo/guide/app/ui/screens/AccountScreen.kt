package jo.guide.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import jo.guide.app.BuildConfig
import jo.guide.app.auth.AccessStatus
import jo.guide.app.data.Category
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

private enum class AuthMode { LOGIN, REGISTER, RESET }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(vm: AppViewModel, onAdmin: () -> Unit) {
    val auth by vm.auth.state.collectAsState()
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showSuggest by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(JG.Surface).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(JG.NavyDark).statusBarsPadding().padding(16.dp)) { Text("حسابي", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!auth.configured) InfoBanner("لم يُربط التطبيق بمشروع Firebase بعد، لذا تسجيل الدخول غير متاح حاليًا. يمكنك تصفّح التطبيق بالكامل كزائر.")

            if (!auth.signedIn) {
                InfoBanner("الحساب اختياري: التصفح والبحث والمفضلة متاحة بدون تسجيل. الحساب يتيح مزامنة المفضلة وإرسال البلاغات والاقتراحات.", JG.Navy)
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when (mode) { AuthMode.LOGIN -> "تسجيل الدخول"; AuthMode.REGISTER -> "إنشاء حساب جديد"; AuthMode.RESET -> "استعادة كلمة المرور" }, style = MaterialTheme.typography.titleLarge, color = JG.NavyDark)
                        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        if (mode != AuthMode.RESET) OutlinedTextField(
                            pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            trailingIcon = { IconButton({ showPass = !showPass }) { Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showPass) "إخفاء" else "إظهار") } },
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        auth.message?.let { Text(it, color = if (it.startsWith("تم") || it.startsWith("إن") || it.startsWith("أرسل")) JG.Green else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        BigButton(when (mode) { AuthMode.LOGIN -> "دخول"; AuthMode.REGISTER -> "إنشاء الحساب"; AuthMode.RESET -> "إرسال رابط الاستعادة" }, enabled = !auth.busy && email.isNotBlank() && (mode == AuthMode.RESET || pass.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                            when (mode) { AuthMode.LOGIN -> vm.auth.signIn(email, pass); AuthMode.REGISTER -> vm.auth.register(email, pass); AuthMode.RESET -> vm.auth.resetPassword(email) }
                        }
                        if (auth.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (mode != AuthMode.LOGIN) TextButton({ mode = AuthMode.LOGIN; vm.auth.clearMessage() }) { Text("لديّ حساب") }
                            if (mode != AuthMode.REGISTER) TextButton({ mode = AuthMode.REGISTER; vm.auth.clearMessage() }) { Text("إنشاء حساب") }
                            if (mode != AuthMode.RESET) TextButton({ mode = AuthMode.RESET; vm.auth.clearMessage() }) { Text("نسيت كلمة المرور؟") }
                        }
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Filled.AccountCircle, null, tint = JG.Navy, modifier = Modifier.size(40.dp)); Text(auth.email ?: "", style = MaterialTheme.typography.titleMedium) }
                        val label = when (auth.status) {
                            AccessStatus.ADMIN -> "الحالة: مدير (صلاحية موثّقة من الخادم)"
                            AccessStatus.EDITOR -> "الحالة: محرر"
                            AccessStatus.EMAIL_NOT_VERIFIED -> "الحالة: البريد غير مؤكَّد"
                            else -> "الحالة: مستخدم"
                        }
                        Text(label, color = JG.Muted)
                        if (!auth.emailVerified) {
                            InfoBanner("أكّد بريدك بالضغط على الرابط المرسل إليك، ثم اضغط «تحديث الحالة». التأكيد مطلوب لأي صلاحية خاصة.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton("تحديث الحالة", Icons.Filled.Refresh, Modifier.weight(1f), enabled = !auth.busy) { vm.auth.reload() }
                                BigButton("إعادة الإرسال", outlined = true, modifier = Modifier.weight(1f), enabled = !auth.busy) { vm.auth.resendVerification() }
                            }
                        }
                        auth.message?.let { Text(it, color = JG.Muted) }
                        if (auth.status == AccessStatus.ADMIN) BigButton("لوحة الإدارة", Icons.Filled.AdminPanelSettings, Modifier.fillMaxWidth(), onClick = onAdmin)
                        if (auth.status == AccessStatus.ADMIN && BuildConfig.ADMIN_PANEL_URL.startsWith("https://"))
                            BigButton("فتح لوحة الإدارة على الويب", Icons.Filled.OpenInBrowser, Modifier.fillMaxWidth(), outlined = true) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ADMIN_PANEL_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        BigButton("تسجيل الخروج", Icons.Filled.Logout, Modifier.fillMaxWidth(), outlined = true) { vm.auth.signOut() }
                    }
                }
                if (auth.emailVerified && auth.status == AccessStatus.USER) OwnerSetup(vm, busy = auth.busy)
                BigButton("اقتراح مكان جديد", Icons.Filled.AddLocationAlt, Modifier.fillMaxWidth(), outlined = true) { showSuggest = true }
            }
            Text("الإصدار ${BuildConfig.VERSION_NAME}", color = JG.Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showSuggest) SuggestDialog(vm) { showSuggest = false }
}

/**
 * قسم «للمالك»: لا يمنح شيئًا بنفسه. يستدعي دالة الخادم bootstrapOwner التي ترفض أي حساب
 * غير مطابق لـ ADMIN_EMAIL المضبوط في الخادم، أو بريده غير مؤكَّد.
 */
@Composable
private fun OwnerSetup(vm: AppViewModel, busy: Boolean) {
    var open by remember { mutableStateOf(false) }
    TextButton({ open = !open }) { Text("هل أنت مالك المشروع؟") }
    if (open) InfoBanner("إذا كان هذا الحساب هو بريد المالك المعتمد في إعدادات الخادم، اضغط الزر لتهيئة صلاحية المدير. سيرفض الخادم الطلب لأي حساب آخر.", JG.Navy)
    if (open) BigButton("تهيئة صلاحية المالك", Icons.Filled.Key, Modifier.fillMaxWidth(), enabled = !busy) { vm.auth.bootstrapOwner() }
}

@Composable
private fun SuggestDialog(vm: AppViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Category.RESTAURANTS.id) }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("اقتراح مكان") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم المكان") }, singleLine = true)
                OutlinedTextField(region, { region = it }, label = { Text("المدينة / المنطقة") }, singleLine = true)
                OutlinedTextField(details, { details = it }, label = { Text("معلومات (عنوان، رابط، ...)") }, minLines = 3)
                msg?.let { Text(it, color = JG.Muted) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.submitSuggestion(name, cat, region, details) { r -> if (r.isSuccess) onClose() else msg = r.exceptionOrNull()?.message } }) { Text("إرسال") } },
        dismissButton = { TextButton(onClose) { Text("إلغاء") } },
    )
}
