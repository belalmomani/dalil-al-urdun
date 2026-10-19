package jo.guide.app.auth

/**
 * منطق نقي (بدون Android) لتحديد حالة صلاحية الحساب من واجهة التطبيق.
 *
 * مهم: هذا للتجربة فقط (إظهار/إخفاء الشاشات). الحماية الحقيقية تُفرض في
 * دالة Supabase admin-api التي تتحقق من رمز Firebase وتحسب الدور في الخادم.
 * هنا نستقبل نتيجة whoami فقط (claims مبنية منها). لا يوجد أي اعتماد على نص البريد الإلكتروني.
 */
enum class AccessStatus {
    FIREBASE_NOT_CONFIGURED, SIGNED_OUT, EMAIL_NOT_VERIFIED, USER, EDITOR, ADMIN
}

object AdminAccess {
    const val CLAIM_ADMIN = "admin"
    const val CLAIM_EDITOR = "editor"

    fun evaluate(
        configured: Boolean,
        signedIn: Boolean,
        emailVerified: Boolean,
        claims: Map<String, Any?>,
    ): AccessStatus = when {
        !configured -> AccessStatus.FIREBASE_NOT_CONFIGURED
        !signedIn -> AccessStatus.SIGNED_OUT
        !emailVerified -> AccessStatus.EMAIL_NOT_VERIFIED
        claims[CLAIM_ADMIN] == true -> AccessStatus.ADMIN
        claims[CLAIM_EDITOR] == true -> AccessStatus.EDITOR
        else -> AccessStatus.USER
    }

    fun canSeeAdminDashboard(s: AccessStatus) = s == AccessStatus.ADMIN
}
