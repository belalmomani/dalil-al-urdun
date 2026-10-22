package jo.guide.app.auth

import jo.guide.app.i18n.tr
import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import jo.guide.app.data.FirebaseStatus
import jo.guide.app.data.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(
    val configured: Boolean = false,
    val uid: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val claims: Map<String, Any?> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val messageOk: Boolean = false,
) {
    val signedIn get() = uid != null
    val status: AccessStatus get() = AdminAccess.evaluate(configured, signedIn, emailVerified, claims)
}

/**
 * غلاف Firebase Authentication. الحساب اختياري: التطبيق كله يعمل للزوار.
 * الصلاحيات تحسبها دالة الخادم من رمز الدخول الموثّق ولا يمكن للمستخدم تعديلها.
 */
class AuthManager(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val configured = FirebaseStatus.isConfigured(context)
    private val _state = MutableStateFlow(AuthState(configured = configured))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val auth get() = FirebaseAuth.getInstance()

    init {
        if (configured) auth.addAuthStateListener { scope.launch { refresh(it.currentUser, force = false) } }
    }

    private suspend fun refresh(user: FirebaseUser?, force: Boolean) {
        if (user == null) {
            _state.value = AuthState(configured = true)
            return
        }
        if (force) runCatching { user.getIdToken(true).await() } // يحدّث email_verified داخل الرمز
        val claims = roleClaims(user)
        _state.value = _state.value.copy(
            configured = true, uid = user.uid, email = user.email,
            emailVerified = user.isEmailVerified, claims = claims,
        )
    }

    /**
     * الدور تحسبه دالة الخادم admin-api (whoami) بعد التحقق من رمز Firebase وتأكيد البريد:
     * المالك = بريد مطابق لـ ADMIN_EMAIL في الخادم؛ غيره من جدول staff الذي يعدّله المالك فقط.
     * التطبيق هنا يعرض النتيجة فقط، ولا يمنح شيئًا. فشل الاتصال = بلا صلاحيات.
     */
    private suspend fun roleClaims(user: FirebaseUser): Map<String, Any?> {
        if (!SupabaseApi.configured || !user.isEmailVerified) return emptyMap()
        return try {
            val o = SupabaseApi.api("whoami")
            val role = if (o.isNull("role")) null else o.optString("role")
            mapOf(
                AdminAccess.CLAIM_ADMIN to (role == "owner" || role == "admin"),
                AdminAccess.CLAIM_EDITOR to (role == "editor"),
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null, messageOk = false) }

    private fun launchOp(okMessage: String? = null, block: suspend () -> Unit) {
        if (!configured) { _state.value = _state.value.copy(message = notConfigured()); return }
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null, messageOk = false)
            try {
                block()
                _state.value = _state.value.copy(busy = false, message = okMessage, messageOk = okMessage != null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, message = friendlyError(e))
            }
        }
    }

    fun signIn(email: String, password: String) = launchOp {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun register(email: String, password: String) = launchOp(tr("تم إنشاء الحساب. أرسلنا رسالة تأكيد إلى بريدك.", "Account created. We sent a verification email.")) {
        val r = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        r.user?.sendEmailVerification()?.await()
    }

    fun resetPassword(email: String) = launchOp(tr("إن كان البريد مسجلًا فقد أرسلنا رابط استعادة كلمة المرور.", "If the email is registered, we sent a password reset link.")) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun resendVerification() = launchOp(tr("أُرسلت رسالة التأكيد من جديد.", "Verification email sent again.")) {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** بعد الضغط على رابط التأكيد في البريد: يحدّث حالة الحساب والـ claims. */
    fun reload() = launchOp {
        val u = auth.currentUser ?: return@launchOp
        u.reload().await()
        refresh(auth.currentUser, force = true)
    }

    /**
     * حذف الحساب: 1) إعادة التحقق بكلمة المرور (يشترطها Firebase للعمليات الحساسة)
     * 2) حذف بيانات المستخدم من الخادم (المفضلة والبلاغات والاقتراحات) 3) حذف حساب Firebase.
     */
    fun deleteAccount(password: String, onDone: (Boolean) -> Unit) {
        if (!configured) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null, messageOk = false)
            try {
                val u = auth.currentUser ?: throw IllegalStateException(tr("لا يوجد مستخدم مسجّل.", "No signed-in user."))
                val mail = u.email ?: throw IllegalStateException(tr("حساب بلا بريد.", "Account has no email."))
                u.reauthenticate(EmailAuthProvider.getCredential(mail, password)).await()
                if (SupabaseApi.configured) SupabaseApi.api("delete_my_data")
                u.delete().await()
                _state.value = AuthState(configured = true, message = tr("تم حذف حسابك وبياناتك.", "Your account and data were deleted."), messageOk = true)
                onDone(true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, message = friendlyError(e))
                onDone(false)
            }
        }
    }

    fun signOut() {
        if (configured) auth.signOut()
    }

    /** إعادة فحص الصلاحية من الخادم (بعد تأكيد البريد أو بعد أن يمنحك المالك دورًا). */
    fun recheckAccess() = launchOp(tr("تم تحديث حالة الصلاحية.", "Access status updated.")) {
        val u = auth.currentUser ?: return@launchOp
        u.reload().await()
        refresh(auth.currentUser, force = true)
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is FirebaseNetworkException -> tr("لا يوجد اتصال بالإنترنت.", "No internet connection.")
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> tr("صيغة البريد الإلكتروني غير صحيحة.", "Invalid email format.")
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> tr("البريد أو كلمة المرور غير صحيحة.", "Incorrect email or password.")
            "ERROR_EMAIL_ALREADY_IN_USE" -> tr("هذا البريد مسجّل مسبقًا.", "This email is already registered.")
            "ERROR_WEAK_PASSWORD" -> tr("كلمة المرور ضعيفة (٦ أحرف على الأقل).", "Weak password (at least 6 characters).")
            "ERROR_TOO_MANY_REQUESTS" -> tr("محاولات كثيرة. حاول لاحقًا.", "Too many attempts. Try again later.")
            "ERROR_USER_DISABLED" -> tr("هذا الحساب معطّل.", "This account is disabled.")
            else -> tr("تعذّرت العملية (${e.errorCode}).", "Operation failed (${e.errorCode}).")
        }
        else -> e.message?.takeIf { it.isNotBlank() } ?: tr("حدث خطأ غير متوقع.", "An unexpected error occurred.")
    }

    companion object {
        fun notConfigured() = tr("لم يُربط التطبيق بمشروع Firebase بعد. راجع README لإضافة google-services.json.", "The app is not connected to a Firebase project yet. See the README to add google-services.json.")
    }
}
