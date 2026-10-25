package jo.guide.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** true عندما يكون المستخدم قد عطّل الحركات في إعدادات النظام (Remove animations / مقياس الحركة 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false }
    }
}

/** كتلة هيكل تحميل (Skeleton) تنبض بلطف، وتثبت إن كانت الحركة معطّلة. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Int = 14) {
    val reduce = rememberReduceMotion()
    val a = if (reduce) 0.6f else {
        val tr = rememberInfiniteTransition(label = "sk")
        tr.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    }
    Box(modifier.clip(RoundedCornerShape(radius.dp)).alpha(a).background(Color(0xFFD9DFEA)))
}
