package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jo.guide.app.i18n.Lang
import jo.guide.app.i18n.tr
import jo.guide.app.ui.JG

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = JG.Surface,
        topBar = {
            TopAppBar(
                title = { Text(tr("سياسة الخصوصية", "Privacy Policy")) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JG.NavyDark, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("آخر تحديث: ${PrivacyContent.UPDATED_AR}", "Last updated: ${PrivacyContent.UPDATED_EN}"), color = JG.Muted, style = MaterialTheme.typography.bodyMedium)
            PrivacyContent.sections.forEach { s ->
                Text(if (Lang.isAr) s.titleAr else s.titleEn, style = MaterialTheme.typography.titleMedium, color = JG.NavyDark)
                (if (Lang.isAr) s.ar else s.en).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, color = JG.Ink) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
