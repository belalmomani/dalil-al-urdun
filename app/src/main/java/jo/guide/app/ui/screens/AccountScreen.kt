package jo.guide.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import jo.guide.app.ads.AdStatus
import jo.guide.app.i18n.Lang
import jo.guide.app.notify.Notifications
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
import jo.guide.app.i18n.tr
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

private enum class AuthMode { LOGIN, REGISTER, RESET }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(vm: AppViewModel, onAdmin: () -> Unit, onPrivacy: () -> Unit) {
    val auth by vm.auth.state.collectAsState()
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showSuggest by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(JG.Surface).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(JG.NavyDark).statusBarsPadding().padding(16.dp)) { Text(tr("حسابي", "Account"), color = Color.White, style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!auth.configured) InfoBanner(tr("لم يُربط التطبيق بمشروع Firebase بعد، لذا تسجيل الدخول غير متاح حاليًا. يمكنك تصفّح التطبيق بالكامل كزائر.", "The app is not connected to a Firebase project yet, so sign-in is unavailable. You can browse the whole app as a guest."))

            if (!auth.signedIn) {
                InfoBanner(tr("الحساب اختياري: التصفح والبحث والمفضلة متاحة بدون تسجيل. الحساب يتيح مزامنة المفضلة وإرسال البلاغات والاقتراحات.", "An account is optional: browsing, search and favorites work without signing in. An account lets you sync favorites and send reports and suggestions."), JG.Navy)
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when (mode) { AuthMode.LOGIN -> tr("تسجيل الدخول", "Sign in"); AuthMode.REGISTER -> tr("إنشاء حساب جديد", "Create a new account"); AuthMode.RESET -> tr("استعادة كلمة المرور", "Reset password") }, style = MaterialTheme.typography.titleLarge, color = JG.NavyDark)
                        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(tr("البريد الإلكتروني", "Email")) }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        if (mode != AuthMode.RESET) OutlinedTextField(
                            pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(tr("كلمة المرور", "Password")) }, singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            trailingIcon = { IconButton({ showPass = !showPass }) { Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showPass) tr("إخفاء", "Hide") else tr("إظهار", "Show")) } },
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        auth.message?.let { Text(it, color = if (auth.messageOk) JG.Green else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        BigButton(when (mode) { AuthMode.LOGIN -> tr("دخول", "Sign in"); AuthMode.REGISTER -> tr("إنشاء الحساب", "Create account"); AuthMode.RESET -> tr("إرسال رابط الاستعادة", "Send reset link") }, enabled = !auth.busy && email.isNotBlank() && (mode == AuthMode.RESET || pass.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                            when (mode) { AuthMode.LOGIN -> vm.auth.signIn(email, pass); AuthMode.REGISTER -> vm.auth.register(email, pass); AuthMode.RESET -> vm.auth.resetPassword(email) }
                        }
                        if (auth.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (mode != AuthMode.LOGIN) TextButton({ mode = AuthMode.LOGIN; vm.auth.clearMessage() }) { Text(tr("لديّ حساب", "I have an account")) }
                            if (mode != AuthMode.REGISTER) TextButton({ mode = AuthMode.REGISTER; vm.auth.clearMessage() }) { Text(tr("إنشاء حساب", "Create account")) }
                            if (mode != AuthMode.RESET) TextButton({ mode = AuthMode.RESET; vm.auth.clearMessage() }) { Text(tr("نسيت كلمة المرور؟", "Forgot password?")) }
                        }
                    }
                }
            } else {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Filled.AccountCircle, null, tint = JG.Navy, modifier = Modifier.size(40.dp)); Text(auth.email ?: "", style = MaterialTheme.typography.titleMedium) }
                        val label = when (auth.status) {
                            AccessStatus.ADMIN -> tr("الحالة: مدير (صلاحية موثّقة من الخادم)", "Status: Admin (verified by the server)")
                            AccessStatus.EDITOR -> tr("الحالة: محرر", "Status: Editor")
                            AccessStatus.EMAIL_NOT_VERIFIED -> tr("الحالة: البريد غير مؤكَّد", "Status: email not verified")
                            else -> tr("الحالة: مستخدم", "Status: User")
                        }
                        Text(label, color = JG.Muted)
                        if (!auth.emailVerified) {
                            InfoBanner(tr("أكّد بريدك بالضغط على الرابط المرسل إليك، ثم اضغط «تحديث الحالة». التأكيد مطلوب لأي صلاحية خاصة.", "Verify your email using the link we sent, then tap “Refresh status”. Verification is required for any special access."))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton(tr("تحديث الحالة", "Refresh status"), Icons.Filled.Refresh, Modifier.weight(1f), enabled = !auth.busy) { vm.auth.reload() }
                                BigButton(tr("إعادة الإرسال", "Resend"), outlined = true, modifier = Modifier.weight(1f), enabled = !auth.busy) { vm.auth.resendVerification() }
                            }
                        }
                        auth.message?.let { Text(it, color = JG.Muted) }
                        if (auth.status == AccessStatus.ADMIN) BigButton(tr("لوحة الإدارة", "Admin panel"), Icons.Filled.AdminPanelSettings, Modifier.fillMaxWidth(), onClick = onAdmin)
                        if (auth.status == AccessStatus.ADMIN && BuildConfig.ADMIN_PANEL_URL.startsWith("https://"))
                            BigButton(tr("فتح لوحة الإدارة على الويب", "Open the web admin panel"), Icons.Filled.OpenInBrowser, Modifier.fillMaxWidth(), outlined = true) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ADMIN_PANEL_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        BigButton(tr("تسجيل الخروج", "Sign out"), Icons.Filled.Logout, Modifier.fillMaxWidth(), outlined = true) { vm.auth.signOut() }
                    }
                }
                if (auth.emailVerified && auth.status == AccessStatus.USER) OwnerSetup(vm, busy = auth.busy)
                BigButton(tr("اقتراح مكان جديد", "Suggest a new place"), Icons.Filled.AddLocationAlt, Modifier.fillMaxWidth(), outlined = true) { showSuggest = true }
            }
            SettingsSection(vm, onPrivacy)
            Text(tr("الإصدار", "Version") + " ${BuildConfig.VERSION_NAME}", color = JG.Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showSuggest) SuggestDialog(vm) { showSuggest = false }
}

/**
 * قسم «للمالك»: لا يمنح شيئًا بنفسه. يطلب من الخادم إعادة حساب دورك؛ الخادم يمنح المالك فقط
 * إذا طابق بريدك ADMIN_EMAIL المضبوط عنده وكان بريدك مؤكَّدًا.
 */
@Composable
private fun OwnerSetup(vm: AppViewModel, busy: Boolean) {
    var open by remember { mutableStateOf(false) }
    TextButton({ open = !open }) { Text(tr("هل أنت مالك المشروع؟", "Are you the project owner?")) }
    if (open) InfoBanner(tr("إذا كان هذا الحساب هو بريد المالك المعتمد في إعدادات الخادم أو مُنح دورًا من المالك، اضغط الزر لإعادة فحص الصلاحية. لا يمنح الزر شيئًا لأي حساب آخر.", "If this account is the owner email set on the server, or was given a role by the owner, tap the button to re-check access. The button grants nothing to any other account."), JG.Navy)
    if (open) BigButton(tr("إعادة فحص الصلاحية", "Re-check access"), Icons.Filled.Key, Modifier.fillMaxWidth(), enabled = !busy) { vm.auth.recheckAccess() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestDialog(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Category.RESTAURANTS.id) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(tr("اقتراح مكان", "Suggest a place")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(tr("اسم المكان", "Place name")) }, singleLine = true)
                Text(tr("التصنيف", "Category"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c -> FilterChip(selected = cat == c.id, onClick = { cat = c.id }, label = { Text(c.label) }) }
                }
                OutlinedTextField(region, { region = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(tr("المدينة / المنطقة", "City / region")) }, singleLine = true)
                OutlinedTextField(details, { details = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text(tr("معلومات (عنوان، رابط، ...)", "Info (address, link, ...)")) }, minLines = 3)
                InfoBanner(tr("لا نضيف أي مكان قبل مراجعته والتحقق من مصدره.", "We never add a place before reviewing it and checking its source."), JG.Navy)
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                busy = true; msg = null
                vm.submitSuggestion(name, cat, region, details) { r ->
                    busy = false
                    if (r.isSuccess) {
                        android.widget.Toast.makeText(ctx, tr("شكرًا، وصل اقتراحك وسنراجعه.", "Thanks, your suggestion was received and will be reviewed."), android.widget.Toast.LENGTH_LONG).show()
                        onClose()
                    } else msg = r.exceptionOrNull()?.message ?: tr("تعذّر الإرسال.", "Could not send.")
                }
            }) { Text(if (busy) tr("جارٍ الإرسال…", "Sending…") else tr("إرسال", "Send")) }
        },
        dismissButton = { TextButton(onClose) { Text(tr("إلغاء", "Cancel")) } },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** اللغة + الإشعارات + الخصوصية + تشخيص الإعلان (في نسخة debug فقط). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSection(vm: AppViewModel, onPrivacy: () -> Unit) {
    val ctx = LocalContext.current
    var notif by remember { mutableStateOf(Notifications.isEnabled(ctx) && Notifications.hasPermission(ctx)) }
    var notifMsg by remember { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Notifications.setEnabled(ctx, true); notif = true; notifMsg = null }
        else { Notifications.setEnabled(ctx, false); notif = false; notifMsg = tr("لم يُمنح إذن الإشعارات. يمكنك السماح به من إعدادات أندرويد.", "Notification permission was not granted. You can allow it in Android settings.") }
    }
    val currentPref = remember { Lang.preference(ctx) }

    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("الإعدادات", "Settings"), style = MaterialTheme.typography.titleLarge, color = JG.NavyDark)

            Text(tr("اللغة", "Language"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Lang.SYSTEM to tr("حسب الجهاز", "Device default"), Lang.AR to "العربية", Lang.EN to "English").forEach { (code, label) ->
                    FilterChip(selected = currentPref == code, onClick = {
                        if (currentPref != code) {
                            Lang.setPreference(ctx, code)
                            vm.refresh()
                            ctx.findActivity()?.recreate()
                        }
                    }, label = { Text(label) })
                }
            }

            HorizontalDivider(color = JG.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("إشعارات الأماكن الجديدة", "New places notifications"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("إشعار عند إضافة أماكن جديدة للدليل (مرة يوميًا تقريبًا).", "Get notified when new places are added (about once a day)."), color = JG.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = notif, onCheckedChange = { on ->
                    if (!on) { Notifications.setEnabled(ctx, false); notif = false; notifMsg = null }
                    else if (Notifications.hasPermission(ctx)) { Notifications.setEnabled(ctx, true); notif = true; notifMsg = null }
                    else permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            notifMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider(color = JG.Line)
            TextButton(onPrivacy, Modifier.fillMaxWidth()) { Icon(Icons.Filled.PrivacyTip, null); Spacer(Modifier.width(8.dp)); Text(tr("سياسة الخصوصية", "Privacy Policy")) }

            if (BuildConfig.DEBUG) {
                val st by AdStatus.text.collectAsState()
                Text("Ads: ${if (BuildConfig.ADS_CONFIGURED) "configured" else "NOT configured (STARTIO_APP_ID missing)"} — $st", color = JG.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
