package jo.guide.app.data

import android.content.Context

/** المفضلة محليًا دائمًا (تعمل للزوار)، وتُزامَن مع Supabase فقط عند تسجيل الدخول. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun save(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
