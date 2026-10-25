package jo.guide.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import jo.guide.app.R

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

/** خط Tajawal (ترخيص OFL، الملفات في res/font) يدعم العربية واللاتينية؛ أحجام مريحة للقراءة لجميع الأعمار. */
val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

private fun t(w: FontWeight, size: Int, line: Int) = TextStyle(fontFamily = Tajawal, fontWeight = w, fontSize = size.sp, lineHeight = line.sp)

private val typography = Typography(
    displaySmall = t(FontWeight.Bold, 34, 44),
    headlineMedium = t(FontWeight.Bold, 28, 38),
    headlineSmall = t(FontWeight.Bold, 24, 34),
    titleLarge = t(FontWeight.Bold, 22, 30),
    titleMedium = t(FontWeight.Medium, 18, 26),
    titleSmall = t(FontWeight.Medium, 16, 24),
    bodyLarge = t(FontWeight.Normal, 17, 27),
    bodyMedium = t(FontWeight.Normal, 15, 24),
    bodySmall = t(FontWeight.Normal, 13, 20),
    labelLarge = t(FontWeight.Medium, 16, 22),
    labelMedium = t(FontWeight.Medium, 13, 18),
    labelSmall = t(FontWeight.Medium, 11, 16),
)

@Composable
fun JordanGuideTheme(content: @Composable () -> Unit) {
    // الهوية ثابتة (كحلي/أبيض/أحمر) بغض النظر عن وضع الجهاز الليلي.
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
