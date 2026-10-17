package jo.guide.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jo.guide.app.data.Category
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

private val homeOrder = listOf(
    Category.RESTAURANTS, Category.TOURISM, Category.SHOPPING, Category.GOVERNMENT,
    Category.NEWS, Category.EDUCATION, Category.APPS, Category.JOBS,
)

@Composable
fun HomeScreen(vm: AppViewModel, onSearch: (String) -> Unit, onCategory: (Category) -> Unit, onPlace: (String) -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var query by remember { mutableStateOf("") }
    val featured = remember(catalog.places) { catalog.places.filter { it.featured } }

    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(JG.NavyDark, JG.Navy))).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagLogo(64.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("دليل الأردن", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("كل الأردن بين إيديك", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("ابحث عن مطعم أو موقع أو خدمة...") },
                leadingIcon = { Icon(Icons.Filled.Search, "بحث") },
                singleLine = true, shape = RoundedCornerShape(32.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = JG.Red, unfocusedBorderColor = Color.Transparent,
                ),
            )
        }
        if (catalog.isSample) item {
            InfoBanner(
                "بيانات تجريبية: لم يُربط التطبيق بقاعدة البيانات بعد. الأسماء عامة وغير موثّقة، ولا تتضمن أرقام هواتف أو أسعارًا.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(homeOrder.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { c -> CategoryCard(c.label, categoryIcon(c.id), Modifier.weight(1f)) { onCategory(c) } }
            }
        }
        item {
            CategoryCard(Category.SERVICES.label, categoryIcon(Category.SERVICES.id), Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { onCategory(Category.SERVICES) }
        }
        item { SectionTitle("اكتشف الأردن", Icons.Filled.Terrain, onDark = true) }
        item {
            Surface(
                onClick = { onCategory(Category.TOURISM) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(150.dp),
                shape = RoundedCornerShape(20.dp), color = JG.NavyCard,
            ) {
                Box(Modifier.background(Brush.horizontalGradient(listOf(JG.NavyCard, JG.Red.copy(alpha = .55f))))) {
                    Column(Modifier.align(Alignment.CenterStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("وجهات تستحق الزيارة", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("أجمل الأماكن السياحية في الأردن", color = Color.White.copy(alpha = .9f))
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { onCategory(Category.TOURISM) }, colors = ButtonDefaults.buttonColors(containerColor = JG.RedDark)) { Text("اكتشف الآن") }
                    }
                }
            }
        }
        if (featured.isNotEmpty()) {
            item { SectionTitle("أماكن مميزة", Icons.Filled.Star, onDark = true) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(featured, key = { it.id }) { p ->
                        Surface(onClick = { onPlace(p.id) }, shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.width(200.dp)) {
                            Column {
                                PlaceImage(p.coverUrl, p.coverAlt ?: p.name, p.category, Modifier.fillMaxWidth().height(110.dp))
                                Column(Modifier.padding(10.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = JG.Ink, maxLines = 1)
                                    Text(p.regionName ?: "", color = JG.Muted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (catalog.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
        catalog.error?.let { err ->
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoBanner(err, Color(0xFFFFB4AB), icon = Icons.Filled.WifiOff)
                    BigButton("إعادة المحاولة", Icons.Filled.Refresh, onClick = vm::refresh)
                }
            }
        }
    }
}
