package jo.guide.app

import android.app.Application
import com.startapp.sdk.adsbase.StartAppSDK

class JordanGuideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initAds()
    }

    /** يُهيّأ SDK الأصلي فقط إذا مُرّر STARTIO_APP_ID وقت البناء. فشل الإعلان لا يُسقط التطبيق أبدًا. */
    private fun initAds() {
        if (!BuildConfig.ADS_CONFIGURED) return
        try {
            // الوسيط الأخير false = تعطيل «Return Ads» (إعلانات تظهر عند العودة للتطبيق) حفاظًا على التجربة.
            StartAppSDK.init(this, BuildConfig.STARTIO_APP_ID, false)
            StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
        } catch (t: Throwable) {
            android.util.Log.w("JordanGuide", "Start.io init failed: ${t.message}")
        }
    }
}
