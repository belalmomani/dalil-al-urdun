package jo.guide.app.auth

import android.content.Context
import com.google.firebase.FirebaseNetworkException
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

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    private fun launchOp(okMessage: String? = null, block: suspend () -> Unit) {
        if (!configured) { _state.value = _state.value.copy(message = NOT_CONFIGURED); return }
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            try {
                block()
                _state.value = _state.value.copy(busy = false, message = okMessage)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, message = arabicError(e))
            }
        }
    }

    fun signIn(email: String, password: String) = launchOp {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun register(email: String, password: String) = launchOp("تم إنشاء الحساب. أرسلنا رسالة تأكيد إلى بريدك.") {
        val r = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        r.user?.sendEmailVerification()?.await()
    }

    fun resetPassword(email: String) = launchOp("إن كان البريد مسجلًا فقد أرسلنا رابط استعادة كلمة المرور.") {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun resendVerification() = launchOp("أُرسلت رسالة التأكيد من جديد.") {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** بعد الضغط على رابط التأكيد في البريد: يحدّث حالة الحساب والـ claims. */
    fun reload() = launchOp {
        val u = auth.currentUser ?: return@launchOp
        u.reload().await()
        refresh(auth.currentUser, force = true)
    }

    fun signOut() {
        if (configured) auth.signOut()
    }

    /** إعادة فحص الصلاحية من الخادم (بعد تأكيد البريد أو بعد أن يمنحك المالك دورًا). */
    fun recheckAccess() = launchOp("تم تحديث حالة الصلاحية.") {
        val u = auth.currentUser ?: return@launchOp
        u.reload().await()
        refresh(auth.currentUser, force = true)
    }

    private fun arabicError(e: Exception): String = when (e) {
        is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "صيغة البريد الإلكتروني غير صحيحة."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "البريد أو كلمة المرور غير صحيحة."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "هذا البريد مسجّل مسبقًا."
            "ERROR_WEAK_PASSWORD" -> "كلمة المرور ضعيفة (٦ أحرف على الأقل)."
            "ERROR_TOO_MANY_REQUESTS" -> "محاولات كثيرة. حاول لاحقًا."
            "ERROR_USER_DISABLED" -> "هذا الحساب معطّل."
            else -> "تعذّرت العملية (${e.errorCode})."
        }
        else -> e.message?.takeIf { it.isNotBlank() } ?: "حدث خطأ غير متوقع."
    }

    companion object {
        const val NOT_CONFIGURED = "لم يُربط التطبيق بمشروع Firebase بعد. راجع README لإضافة google-services.json."
    }
}
