package jo.guide.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object JG {
    val NavyDark = Color(0xFF0A2347)
    val Navy = Color(0xFF0F3566)
    val NavyCard = Color(0xFF14407A)
    val Red = Color(0xFFC8102E)
    val RedDark = Color(0xFF9E0D25)
    val Surface = Color(0xFFF4F6FA)
    val Ink = Color(0xFF111827)
    val Muted = Color(0xFF4B5563) // تباين كافٍ على الأبيض (≥ 7:1)
    val Line = Color(0xFFE3E6EC)
    val Green = Color(0xFF0B7A3E)
    val Amber = Color(0xFF8A5A00)
}

private val scheme = lightColorScheme(
    primary = JG.Red, onPrimary = Color.White,
    secondary = JG.Navy, onSecondary = Color.White,
    background = JG.Surface, onBackground = JG.Ink,
    surface = Color.White, onSurface = JG.Ink,
    surfaceVariant = Color(0xFFEDEFF5), onSurfaceVariant = JG.Muted,
    error = Color(0xFFB3261E),
)

/** نصوص أكبر قليلًا من الافتراضي لسهولة القراءة لجميع الأعمار. الخط: خط النظام العربي (Roboto/Noto). */
private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp),
)

@Composable
fun JordanGuideTheme(content: @Composable () -> Unit) {
    // الهوية ثابتة (كحلي/أبيض/أحمر) بغض النظر عن وضع الجهاز الليلي.
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
