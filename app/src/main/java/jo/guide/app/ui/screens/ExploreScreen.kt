package jo.guide.app.ui.screens

import jo.guide.app.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jo.guide.app.data.*
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(vm: AppViewModel, onPlace: (String) -> Unit, onRegion: (String) -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val filter by vm.filter.collectAsState()
    val results by vm.results.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // تحميل صفحة إضافية عند الاقتراب من نهاية القائمة (Pagination)
    val nearEnd by remember { derivedStateOf { val l = listState.layoutInfo; (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, catalog.hasMore) { if (nearEnd && catalog.hasMore) vm.loadMore() }

    Column(Modifier.fillMaxSize().background(JG.Surface)) {
        Column(Modifier.background(JG.NavyDark).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter.query, onValueChange = { q -> vm.updateFilter { it.copy(query = q) } },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(tr("ابحث عن مطعم أو موقع أو خدمة...", "Search for a restaurant, place or service...")) },
                    leadingIcon = { Icon(Icons.Filled.Search, tr("بحث", "Search")) },
                    trailingIcon = { if (filter.query.isNotEmpty()) IconButton({ vm.updateFilter { it.copy(query = "") } }) { Icon(Icons.Filled.Close, tr("مسح", "Clear")) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.White, unfocusedContainerColor = androidx.compose.ui.graphics.Color.White, focusedBorderColor = JG.Red, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                    FilledTonalIconButton(onClick = { showFilters = true }, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Tune, tr("الفلاتر", "Filters")) }
                }
            }
            if (filter.categories.isNotEmpty() || filter.regionIds.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filter.categories.forEach { id ->
                        InputChip(selected = true, onClick = { vm.updateFilter { it.copy(categories = it.categories - id) } }, label = { Text(Category.fromId(id)?.label ?: id) }, trailingIcon = { Icon(Icons.Filled.Close, tr("إزالة", "Remove"), Modifier.size(16.dp)) })
                    }
                    filter.regionIds.forEach { id ->
                        InputChip(selected = true, onClick = { vm.updateFilter { it.copy(regionIds = it.regionIds - id) } }, label = { Text(catalog.regions.firstOrNull { r -> r.id == id }?.displayName ?: id) }, trailingIcon = { Icon(Icons.Filled.Close, tr("إزالة", "Remove"), Modifier.size(16.dp)) })
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (catalog.fromCache) item { InfoBanner(tr("تعرض هذه الصفحة بيانات محفوظة على جهازك وقد تكون قديمة. تحقق من الاتصال لتحديثها.", "This page shows data saved on your device, which may be outdated. Check your connection to refresh."), icon = Icons.Filled.CloudOff) }
            if (catalog.isSample) item { InfoBanner(tr("بيانات تجريبية غير موثّقة إلى حين ربط قاعدة البيانات.", "Unverified sample data until the database is connected.")) }
            if (catalog.regions.isNotEmpty() && filter.isEmpty) item {
                Column {
                    Text(tr("المحافظات", "Governorates"), style = MaterialTheme.typography.titleMedium, color = JG.NavyDark)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        catalog.regions.filter { it.type == RegionType.GOVERNORATE }.forEach { r ->
                            AssistChip(onClick = { onRegion(r.id) }, label = { Text(r.displayName) }, leadingIcon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
            item { Text(tr("${results.size} نتيجة", "${results.size} results"), color = JG.Muted, style = MaterialTheme.typography.bodyMedium) }
            if (catalog.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            catalog.error?.let { err -> item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { InfoBanner(err, MaterialTheme.colorScheme.error, icon = Icons.Filled.WifiOff); BigButton(tr("إعادة المحاولة", "Retry"), Icons.Filled.Refresh, onClick = vm::refresh) } } }
            if (!catalog.loading && results.isEmpty() && catalog.error == null) item {
                Column {
                    EmptyState(tr("لا توجد نتائج", "No results"), tr("جرّب تخفيف الفلاتر أو تغيير كلمات البحث. تُستبعد الأماكن التي لا تتوفر عنها المعلومة المختارة لأننا لا نفترض بيانات غير موثقة.", "Try fewer filters or different keywords. Places missing the selected information are excluded because we never assume unverified data."))
                    if (!filter.isEmpty) BigButton(tr("مسح كل الفلاتر", "Clear all filters"), Icons.Filled.FilterAltOff, Modifier.fillMaxWidth(), outlined = true, onClick = vm::clearFilter)
                }
            }
            items(results, key = { it.id }) { p -> PlaceCard(p, p.id in favorites, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }) }
            if (catalog.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = androidx.compose.ui.graphics.Color.White) {
            FilterSheetContent(filter, catalog.regions, vm::setFilter, onClose = { showFilters = false }, resultCount = results.size)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(f: PlaceFilter, regions: List<Region>, onChange: (PlaceFilter) -> Unit, onClose: () -> Unit, resultCount: Int) {
    fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("الفلاتر", "Filters"), style = MaterialTheme.typography.titleLarge, color = JG.NavyDark)
        FilterGroup(tr("التصنيف", "Category")) { Category.entries.forEach { c -> FilterChip(c.id in f.categories, { onChange(f.copy(categories = f.categories.toggle(c.id))) }, { Text(c.label) }) } }
        if (regions.isNotEmpty()) FilterGroup(tr("المنطقة", "Region")) { regions.forEach { r -> FilterChip(r.id in f.regionIds, { onChange(f.copy(regionIds = f.regionIds.toggle(r.id))) }, { Text(r.displayName) }) } }
        FilterGroup(tr("مستوى السعر (يمكن اختيار أكثر من مستوى)", "Price level (you can pick several)")) { PriceLevels.all.forEach { (v, l) -> FilterChip(v in f.priceLevels, { onChange(f.copy(priceLevels = f.priceLevels.toggle(v))) }, { Text(l) }) } }
        FilterGroup(tr("الفئة العمرية (يمكن اختيار أكثر من فئة)", "Age group (you can pick several)")) { AgeGroups.all.forEach { (id, l) -> FilterChip(id in f.ageGroups, { onChange(f.copy(ageGroups = f.ageGroups.toggle(id))) }, { Text(l) }) } }
        FilterGroup(tr("المرافق (تُطلب كلها معًا)", "Facilities (all selected are required)")) { Features.all.forEach { (id, l) -> FilterChip(id in f.features, { onChange(f.copy(features = f.features.toggle(id))) }, { Text(l) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(f.kidsFriendlyOnly, { onChange(f.copy(kidsFriendlyOnly = it)) })
            Spacer(Modifier.width(10.dp)); Text(tr("مناسب للأطفال فقط", "Kid-friendly only"), style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(tr("مسح", "Clear"), outlined = true, modifier = Modifier.weight(1f)) { onChange(PlaceFilter(query = f.query)) }
            BigButton(tr("عرض $resultCount نتيجة", "Show $resultCount results"), modifier = Modifier.weight(2f), onClick = onClose)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = JG.Ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
