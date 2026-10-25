package jo.guide.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** دوال جغرافية نقية (قابلة للاختبار). الموقع لا يغادر الجهاز أبدًا. */
object Geo {
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** إحداثيات صالحة داخل نطاق منطقي حول الأردن والجوار (تفادي قيم خاطئة). */
    fun hasCoords(p: Place): Boolean {
        val la = p.lat ?: return false
        val ln = p.lng ?: return false
        return la in 28.0..34.0 && ln in 33.5..40.0
    }

    /** مراكز تقريبية للمحافظات لتحريك الكاميرا فقط (ليست بيانات تُعرض كمعلومة موثّقة). */
    val governorateCenters: Map<String, Pair<Double, Double>> = mapOf(
        "amman" to (31.95 to 35.93), "irbid" to (32.55 to 35.85), "zarqa" to (32.07 to 36.09),
        "balqa" to (32.04 to 35.73), "madaba" to (31.72 to 35.79), "karak" to (31.18 to 35.70),
        "tafilah" to (30.84 to 35.60), "maan" to (30.19 to 35.73), "aqaba" to (29.53 to 35.01),
        "jerash" to (32.27 to 35.90), "ajloun" to (32.33 to 35.75), "mafraq" to (32.34 to 36.21),
    )

    /** رقم هاتف صالح للعرض كزر اتصال: أرقام وعلامات شائعة وستة أرقام على الأقل. */
    fun validPhone(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        if (!Regex("^[+0-9()\\-\\s]{6,25}$").matches(s.trim())) return false
        return s.count { it.isDigit() } >= 6
    }

    fun nearby(places: List<Place>, lat: Double, lng: Double, maxKm: Double = 30.0): List<Pair<Place, Double>> =
        places.filter(::hasCoords)
            .map { it to distanceKm(lat, lng, it.lat!!, it.lng!!) }
            .filter { it.second <= maxKm }
            .sortedBy { it.second }
}
