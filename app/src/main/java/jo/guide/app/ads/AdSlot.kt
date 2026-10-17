package jo.guide.app.ads

import android.view.View
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener

/**
 * مساحة الإعلان: صف ثابت في تخطيط الشاشة (أسفل المحتوى وأسفل شريط التنقل)، وليس عنصرًا عائمًا.
 * - قبل وصول الإعلان: ارتفاعها 1dp فقط (لا فراغ مزعج) مع بقاء الـ Banner مرفقًا ليطلب الإعلان.
 * - عند onReceiveAd: تتمدد لارتفاع الإعلان مع فاصل وحشوة علوية تمنع النقر غير المقصود على الأزرار المجاورة.
 * - عند onFailedToReceiveAd: تنكمش من جديد. لا نستدعي loadAd() داخل الفشل (SDK يعيد المحاولة تلقائيًا).
 */
@Composable
fun AdSlot(decision: AdDecision, modifier: Modifier = Modifier) {
    if (!decision.show) return
    var loaded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (loaded) Color.White else Color.Transparent)
            .animateContentSize()
            .navigationBarsPadding(),
    ) {
        if (loaded) {
            HorizontalDivider(color = Color(0xFFE3E6EC))
            Text(
                "إعلان",
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (loaded) Modifier.heightIn(min = 50.dp).padding(top = 4.dp, bottom = 8.dp) else Modifier.height(1.dp)),
            factory = { ctx ->
                try {
                    Banner(ctx, object : BannerListener {
                        override fun onReceiveAd(view: View) { loaded = true }
                        override fun onFailedToReceiveAd(view: View) { loaded = false }
                        override fun onImpression(view: View) {}
                        override fun onClick(view: View) {}
                    })
                } catch (t: Throwable) {
                    loaded = false
                    View(ctx) // فشل SDK لا يعطّل الواجهة
                }
            },
        )
    }
}
