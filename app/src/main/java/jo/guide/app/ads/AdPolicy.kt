package jo.guide.app.ads

/** سبب إخفاء الإعلان — يفيد في الاختبار والتشخيص. */
enum class AdHiddenReason { NOT_CONFIGURED, DISABLED_REMOTELY, OFFLINE, KEYBOARD_OPEN, SENSITIVE_SCREEN }

data class AdDecision(val show: Boolean, val reason: AdHiddenReason? = null)

/**
 * يقرر هل تُعرض مساحة إعلان Start.io. منطق نقي قابل للاختبار.
 * - بدون App ID صحيح (يمرَّر وقت البناء) لا يُهيَّأ SDK ولا تظهر أي مساحة.
 * - تُخفى عند ظهور لوحة المفاتيح كي لا تقترب من حقول الإدخال/البحث.
 * - تُخفى في الشاشات الحساسة (تسجيل الدخول، الدفع، لوحة الإدارة) لمنع النقر غير المقصود.
 */
object AdPolicy {
    fun decide(
        appIdConfigured: Boolean,
        remoteEnabled: Boolean,
        online: Boolean,
        keyboardOpen: Boolean,
        sensitiveScreen: Boolean,
    ): AdDecision = when {
        !appIdConfigured -> AdDecision(false, AdHiddenReason.NOT_CONFIGURED)
        !remoteEnabled -> AdDecision(false, AdHiddenReason.DISABLED_REMOTELY)
        !online -> AdDecision(false, AdHiddenReason.OFFLINE)
        keyboardOpen -> AdDecision(false, AdHiddenReason.KEYBOARD_OPEN)
        sensitiveScreen -> AdDecision(false, AdHiddenReason.SENSITIVE_SCREEN)
        else -> AdDecision(true)
    }
}
