package jo.guide.app.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * لغة التطبيق: "system" (حسب الجهاز) أو "ar" أو "en".
 * عند "system": العربية إن كانت لغة الجهاز عربية، وإلا الإنجليزية.
 * التغيير اليدوي يُحفظ ثم تُعاد إنشاء الـ Activity (انظر MainActivity.attachBaseContext).
 */
object Lang {
    const val SYSTEM = "system"
    const val AR = "ar"
    const val EN = "en"
    private const val PREFS = "settings"
    private const val KEY = "lang"

    /** اللغة الفعّالة الحالية ("ar" أو "en"). تُضبط مبكرًا في attachBaseContext. */
    @Volatile var code: String = AR
        private set

    val isAr: Boolean get() = code == AR

    fun preference(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun setPreference(ctx: Context, pref: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, pref).apply()
    }

    /** منطق نقي قابل للاختبار. */
    fun resolve(pref: String, deviceLanguage: String): String = when (pref) {
        AR -> AR
        EN -> EN
        else -> if (deviceLanguage.equals("ar", ignoreCase = true)) AR else EN
    }

    /** يطبّق اللغة على Context (يُستدعى من attachBaseContext). */
    fun wrap(base: Context): Context {
        val device = base.resources.configuration.locales.get(0)?.language ?: "en"
        code = resolve(preference(base), device)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }
}

/** نص بحسب اللغة الحالية: tr("عربي", "English"). */
fun tr(ar: String, en: String): String = if (Lang.isAr) ar else en
