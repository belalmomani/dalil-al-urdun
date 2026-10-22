package jo.guide.app.ads

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** حالة آخر طلب إعلان (للتشخيص في شاشة الحساب بنسخة debug فقط). */
object AdStatus {
    private val _text = MutableStateFlow("لم يُطلب إعلان بعد")
    val text: StateFlow<String> = _text
    fun set(s: String) { _text.value = s }
}

/**
 * مساحة الإعلان: صف ثابت أسفل الشاشة بارتفاع ثابت (50dp) ما دام القرار "عرض".
 * السبب: الـ Banner في Start.io لا يطلب الإعلان إن كان ارتفاعه صفرًا/1dp (لا يُقاس له حجم)،
 * وهذا كان سبب عدم ظهور الإعلانات سابقًا. لذلك الحجم الآن ثابت ولا يتغير بعد وصول الإعلان.
 */
@Composable
fun AdSlot(decision: AdDecision, modifier: Modifier = Modifier) {
    if (!decision.show) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = Color(0xFFE3E6EC))
        AndroidView(
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(vertical = 2.dp),
            factory = { ctx ->
                try {
                    Banner(ctx, object : BannerListener {
                        override fun onReceiveAd(view: View) { AdStatus.set("وصل إعلان ✓") }
                        override fun onFailedToReceiveAd(view: View) { AdStatus.set("فشل جلب الإعلان (لا يوجد إعلان متاح أو المعرّف غير مفعّل بعد)") }
                        override fun onImpression(view: View) { AdStatus.set("تم عرض الإعلان ✓") }
                        override fun onClick(view: View) {}
                    })
                } catch (t: Throwable) {
                    AdStatus.set("خطأ SDK: ${t.javaClass.simpleName}")
                    View(ctx)
                }
            },
        )
    }
}
