package jo.guide.app.data

import com.google.firebase.auth.FirebaseAuth
import jo.guide.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** خطأ من الخادم برسالة عربية جاهزة للعرض. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * عميل Supabase بسيط (بدون مكتبات إضافية):
 *  - rest(): قراءة عامة من PostgREST بمفتاح anon. سياسات RLS تحصرها في المحتوى المنشور فقط.
 *  - api(): إجراءات تتطلب هوية، عبر دالة admin-api التي تتحقق من رمز Firebase وتحدد الدور على الخادم.
 * مفتاح anon عام بطبيعته (مثل إعدادات Firebase للويب) وليس سرًّا؛ لا يوجد هنا أي مفتاح service_role.
 */
object SupabaseApi {
    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private val base get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun idToken(forceRefresh: Boolean = false): String? = try {
        FirebaseAuth.getInstance().currentUser?.getIdToken(forceRefresh)?.await()?.token
    } catch (_: Exception) {
        null // Firebase غير مهيأ أو لا اتصال
    }

    suspend fun rest(pathAndQuery: String): JSONArray = withContext(Dispatchers.IO) {
        val c = open("$base/rest/v1/$pathAndQuery", "GET")
        try {
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            c.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            c.setRequestProperty("Accept", "application/json")
            val (code, text) = read(c)
            if (code !in 200..299) throw ApiException(code, "تعذّر جلب البيانات ($code).")
            JSONArray(text)
        } finally {
            c.disconnect()
        }
    }

    suspend fun api(action: String, body: JSONObject = JSONObject()): JSONObject {
        val token = idToken() ?: throw ApiException(401, "سجّل الدخول أولًا.")
        return withContext(Dispatchers.IO) {
            val c = open("$base/functions/v1/admin-api", "POST")
            try {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("x-firebase-token", token)
                // الدالة منشورة بدون تحقق JWT من Supabase؛ مفتاح anon يُرسل فقط ليمرّ عبر البوابة إن لزم.
                c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                val payload = JSONObject(body.toString()).put("action", action)
                c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val (code, text) = read(c)
                val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                if (code !in 200..299) {
                    val msg = if (json.isNull("error")) "رفض الخادم الطلب ($code)." else json.optString("error")
                    throw ApiException(code, msg)
                }
                json
            } finally {
                c.disconnect()
            }
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 25_000
        return c
    }

    private fun read(c: HttpURLConnection): Pair<Int, String> {
        val code = c.responseCode
        val stream = if (code >= 400) c.errorStream else c.inputStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return code to text
    }
}
