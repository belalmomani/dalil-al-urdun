package jo.guide.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jo.guide.app.MainActivity
import jo.guide.app.R
import jo.guide.app.data.SupabaseApi
import jo.guide.app.i18n.Lang
import jo.guide.app.i18n.tr
import java.util.concurrent.TimeUnit

/**
 * إشعارات «أماكن جديدة»: اختيارية ومغلقة افتراضيًا. لا تحتاج خادم إشعارات خارجي:
 * مهمة دورية (مرة يوميًا تقريبًا، وبوجود إنترنت فقط) تقارن معرّفات الأماكن المنشورة بآخر قائمة رآها المستخدم،
 * وتُظهر إشعارًا محليًا إن ظهرت أماكن جديدة. أول تشغيل يحفظ القائمة فقط دون إشعار.
 */
object Notifications {
    const val CHANNEL_ID = "new_places"
    private const val WORK_NAME = "new_places_check"
    private const val PREFS = "notify"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_KNOWN = "known_ids"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, false)

    fun hasPermission(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
        val wm = WorkManager.getInstance(c)
        if (on) {
            ensureChannel(c)
            val req = PeriodicWorkRequestBuilder<NewPlacesWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, tr("أماكن جديدة", "New places"), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    internal fun knownIds(c: Context): Set<String>? = prefs(c).getStringSet(KEY_KNOWN, null)
    internal fun saveKnown(c: Context, ids: Set<String>) { prefs(c).edit().putStringSet(KEY_KNOWN, ids).apply() }

    /** منطق نقي: ما عدد الأماكن الجديدة؟ (null = أول تشغيل، لا إشعار). */
    fun newCount(known: Set<String>?, current: Set<String>): Int? =
        if (known == null) null else (current - known).size

    internal fun show(c: Context, count: Int) {
        if (!hasPermission(c)) return
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (Lang.isAr) "أُضيف $count مكان جديد إلى الدليل. تصفّحها الآن." else "$count new place(s) added to the guide. Take a look."
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(c).notify(1001, n) } catch (_: SecurityException) { }
    }
}

class NewPlacesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!Notifications.isEnabled(c) || !SupabaseApi.configured) return Result.success()
        return try {
            val arr = SupabaseApi.rest("places?select=id&status=eq.published&limit=2000")
            val current = (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("id").takeIf { s -> s.isNotBlank() } }.toSet()
            val n = Notifications.newCount(Notifications.knownIds(c), current)
            Notifications.saveKnown(c, current)
            if (n != null && n > 0) Notifications.show(c, n)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
