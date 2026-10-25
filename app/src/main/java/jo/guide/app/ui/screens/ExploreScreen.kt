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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jo.guide.app.data.*
import jo.guide.app.ui.AppViewModel
import jo.guide.app.ui.JG
import jo.guide.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(vm: AppViewModel, onPlace: (String) -> Unit, onRegion: (String) -> Unit, onMap: () -> Unit) {
    val catalog by vm.catalog.collectAsState()
    val filter by vm.filter.collectAsState()
    val results by vm.results.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val white = androidx.compose.ui.graphics.Color.White

    // تحميل صفحة إضافية عند الاقتراب من نهاية القائمة (Pagination)
    val nearEnd by remember { derivedStateOf { val l = listState.layoutInfo; (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, catalog.hasMore) { if (nearEnd && catalog.hasMore) vm.loadMore() }

    Column(Modifier.fillMaxSize().background(JG.Surface)) {
        // ---- رأس واضح: عنوان + عدد النتائج الفعلي + بحث + فلاتر + تبديل للخريطة
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(JG.NavyDark)
                .statusBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("استكشف", "Explore"), color = white, style = MaterialTheme.typography.titleLarge)
                    Text(if (catalog.loading) tr("جارٍ التحميل…", "Loading…") else tr("${results.size} نتيجة", "${results.size} results"), color = white.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onMap, colors = ButtonDefaults.textButtonColors(contentColor = white)) {
                    Icon(Icons.Filled.Map, null); Spacer(Modifier.width(6.dp)); Text(tr("عرض الخريطة", "Map view"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter.query, onValueChange = { q -> vm.updateFilter { it.copy(query = q) } },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(tr("وين حاب تروح؟", "Where do you want to go?")) },
                    leadingIcon = { Icon(Icons.Filled.Search, tr("بحث", "Search")) },
                    trailingIcon = { if (filter.query.isNotEmpty()) IconButton({ vm.updateFilter { it.copy(query = "") } }) { Icon(Icons.Filled.Close, tr("مسح", "Clear")) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = white, unfocusedContainerColor = white, focusedBorderColor = JG.Red, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                    FilledIconButton(onClick = { showFilters = true }, modifier = Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = JG.Red)) { Icon(Icons.Filled.Tune, tr("الفلاتر", "Filters")) }
                }
            }
        }

        // ---- شارات الفلاتر النشطة (حذف فلتر منفرد أو مسح الكل)
        if (filter.activeCount > 0) {
            FlowRow(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filter.moodId?.let { id -> Mood.fromId(id)?.let { m ->
                    RemovableChip(m.title) {
                        val base = m.filter()
                        vm.updateFilter { it.copy(
                            moodId = null, keywords = emptySet(),
                            categories = it.categories - base.categories, priceLevels = it.priceLevels - base.priceLevels,
                            kidsFriendlyOnly = if (base.kidsFriendlyOnly) false else it.kidsFriendlyOnly,
                        ) }
                    }
                } }
                filter.categories.forEach { id -> RemovableChip(Category.fromId(id)?.label ?: id) { vm.updateFilter { it.copy(categories = it.categories - id) } } }
                filter.regionIds.forEach { id -> RemovableChip(catalog.regions.firstOrNull { r -> r.id == id }?.displayName ?: id) { vm.updateFilter { it.copy(regionIds = it.regionIds - id) } } }
                filter.priceLevels.forEach { v -> if (filter.moodId != Mood.BUDGET.id) RemovableChip(PriceLevels.all.firstOrNull { it.first == v }?.second ?: "$v") { vm.updateFilter { it.copy(priceLevels = it.priceLevels - v) } } }
                filter.ageGroups.forEach { id -> RemovableChip(AgeGroups.all.firstOrNull { it.first == id }?.second ?: id) { vm.updateFilter { it.copy(ageGroups = it.ageGroups - id) } } }
                filter.features.forEach { id -> RemovableChip(Features.all.firstOrNull { it.first == id }?.second ?: id) { vm.updateFilter { it.copy(features = it.features - id) } } }
                if (filter.kidsFriendlyOnly && filter.moodId != Mood.FAMILY.id) RemovableChip(tr("مناسب للأطفال", "Kid-friendly")) { vm.updateFilter { it.copy(kidsFriendlyOnly = false) } }
                TextButton(onClick = vm::clearFilter) { Text(tr("مسح الكل", "Clear all")) }
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
            if (catalog.loading && results.isEmpty()) items(4) { SkeletonBox(Modifier.fillMaxWidth().height(116.dp), radius = 18) }
            catalog.error?.let { err -> item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { InfoBanner(err, MaterialTheme.colorScheme.error, icon = Icons.Filled.WifiOff); BigButton(tr("إعادة المحاولة", "Retry"), Icons.Filled.Refresh, onClick = vm::refresh) } } }
            if (!catalog.loading && results.isEmpty() && catalog.error == null) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val mood = Mood.fromId(filter.moodId)
                    EmptyState(tr("لا توجد نتائج", "No results"), mood?.emptyNote ?: tr("جرّب تخفيف الفلاتر أو تغيير كلمات البحث. تُستبعد الأماكن التي لا تتوفر عنها المعلومة المختارة لأننا لا نفترض بيانات غير موثقة.", "Try fewer filters or different keywords. Places missing the selected information are excluded because we never assume unverified data."))
                    if (!filter.isEmpty) BigButton(tr("مسح كل الفلاتر", "Clear all filters"), Icons.Filled.FilterAltOff, Modifier.fillMaxWidth(), outlined = true, onClick = vm::clearFilter)
                }
            }
            items(results, key = { it.id }) { p -> PlaceCard(p, p.id in favorites, { vm.toggleFavorite(p.id) }, { onPlace(p.id) }, Modifier.animateItem()) }
            if (catalog.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = androidx.compose.ui.graphics.Color.White) {
            FilterSheetContent(filter, catalog.regions, vm::setFilter, onClose = { showFilters = false }, resultCount = results.size)
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(selected = true, onClick = onRemove, label = { Text(label) }, trailingIcon = { Icon(Icons.Filled.Close, tr("إزالة", "Remove"), Modifier.size(16.dp)) })
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
        Text(tr("ملاحظة: السعر والفئة العمرية والمرافق لا تتوفر لكل الأماكن؛ عند اختيارها تُستبعد الأماكن التي لا تحمل هذه المعلومة الموثّقة.", "Note: price, age group and facilities are not available for every place; selecting them excludes places without that verified information."), style = MaterialTheme.typography.bodySmall, color = JG.Muted)
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
