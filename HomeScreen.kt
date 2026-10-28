package com.kodastream.app.ui.home

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.components.LoadingBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.PosterShelfSkeleton
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.components.SkeletonBox
import com.kodastream.app.ui.components.SkeletonPulseProvider
import com.kodastream.app.ui.components.staggeredEnter
import com.kodastream.app.ui.sources.SourceViewModel
import com.kodastream.app.ui.theme.ArtworkStatusBarIcons
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Immutable
data class HomeRow(
    val key: String,
    val title: String,
    val items: List<SearchResponse>,
    val horizontal: Boolean,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _providers = MutableStateFlow(Engine.homeProviders())
    val providers: StateFlow<List<MainAPI>> = _providers.asStateFlow()

    /** The provider currently loaded; set only by [setSource], never chosen here. */
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
    val rows: StateFlow<List<HomeRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Additional catalog pages, loaded only as the user scrolls below the original shelves. */
    private val _catalogItems = MutableStateFlow<List<SearchResponse>>(emptyList())
    val catalogItems: StateFlow<List<SearchResponse>> = _catalogItems.asStateFlow()
    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()
    private val _catalogHasMore = MutableStateFlow(false)
    val catalogHasMore: StateFlow<Boolean> = _catalogHasMore.asStateFlow()

    private data class CatalogPageSource(
        val index: Int,
        val page: MainPageData,
        val nextPage: Int,
        val stalePages: Int = 0,
    )
    private var catalogSources: List<CatalogPageSource> = emptyList()
    private var catalogJob: Job? = null
    private var requestGeneration = 0L

    /**
     * The hero comes from the first shelf that has enough posters, preferring
     * a portrait row. Derived here so composition never walks the shelves,
     * and cached eagerly because the Home screen reads it right away.
     */
    val hero: StateFlow<List<SearchResponse>> = _rows
        .map { rows ->
            rows.sortedBy { it.horizontal }
                .firstOrNull { row -> row.items.count { !it.posterUrl.isNullOrBlank() } >= 3 }
                ?.items
                .orEmpty()
                .filter { !it.posterUrl.isNullOrBlank() }
                .distinctBy { it.url }
                .take(8)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            // Runs again every time an extension is installed, updated or removed.
            PluginManager.revision.collect { _providers.value = Engine.homeProviders() }
        }
    }

    /**
     * Loads the name the source owner resolved. An equal name is a no-op, so a
     * revision that does not change the selection never cancels a running load.
     */
    fun setSource(name: String?) {
        if (name == _selected.value) return
        _selected.value = name
        val api = name?.let(Engine::provider)?.takeIf { it.hasMainPage }
        if (api == null) {
            invalidateAndClear()
            return
        }
        load(api)
    }

    fun retry() {
        val api = Engine.provider(_selected.value) ?: return
        load(api)
    }

    private fun load(api: MainAPI) {
        val generation = invalidateAndClear()
        loadJob = viewModelScope.launch {
            _loading.value = true
            val pages = api.mainPage
            val results = arrayOfNulls<List<HomeRow>>(pages.size)
            val nextSources = java.util.concurrent.ConcurrentHashMap<Int, CatalogPageSource>()
            val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val gate = Semaphore(if (api.sequentialMainPage) 1 else 3)

            coroutineScope {
                pages.forEachIndexed { index, page ->
                    launch {
                        gate.withPermit {
                            val result = Engine.mainPageWithPaging(api, page, 1)
                            result.onSuccess { response ->
                                results[index] = response.items
                                    .filter { it.list.isNotEmpty() }
                                    .mapIndexed { i, list ->
                                        HomeRow(
                                            key = "$index-$i-${list.name}",
                                            title = list.name.ifBlank { page.name },
                                            items = list.list,
                                            horizontal = list.isHorizontalImages,
                                        )
                                    }
                                // Probe page 2 for every section that returned data. Some older
                                // extensions do not populate hasNext even when pagination works.
                                if (response.items.any { it.list.isNotEmpty() }) {
                                    nextSources[index] = CatalogPageSource(index, page, 2)
                                }
                            }
                            result.onFailure { failures.add(it.readableMessage()) }
                        }
                    }
                }
            }

            if (generation != requestGeneration) return@launch
            _rows.value = results.filterNotNull().flatten()
            catalogSources = nextSources.values.sortedBy { it.index }
            _catalogHasMore.value = catalogSources.isNotEmpty()
            _loading.value = false
            if (_rows.value.isEmpty()) _error.value = failures.peek() ?: ""
        }
    }

    /** Invalidates in-flight work and clears every source-specific value synchronously. */
    private fun invalidateAndClear(): Long {
        requestGeneration += 1
        loadJob?.cancel()
        loadJob = null
        catalogJob?.cancel()
        catalogJob = null
        _rows.value = emptyList()
        _catalogItems.value = emptyList()
        _catalogHasMore.value = false
        _catalogLoading.value = false
        _loading.value = false
        _error.value = null
        catalogSources = emptyList()
        return requestGeneration
    }

    /** Fetches the next page for each homepage section that advertises more results. */
    fun loadMoreCatalog() {
        if (_catalogLoading.value || !_catalogHasMore.value || _selected.value == null) return
        val api = Engine.provider(_selected.value) ?: return
        val generation = requestGeneration
        val sources = catalogSources
        if (sources.isEmpty()) {
            _catalogHasMore.value = false
            return
        }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _catalogLoading.value = true
            val gate = Semaphore(if (api.sequentialMainPage) 1 else 3)
            val nextSources = java.util.concurrent.ConcurrentHashMap<Int, CatalogPageSource>()
            val pageResults = java.util.concurrent.ConcurrentHashMap<Int, List<SearchResponse>>()
            val alreadyKnownUrls = (_rows.value.flatMap { it.items } + _catalogItems.value)
                .mapTo(HashSet()) { it.url }

            try {
                coroutineScope {
                    sources.forEach { source ->
                        launch {
                            gate.withPermit {
                                val result = Engine.mainPageWithPaging(api, source.page, source.nextPage)
                                result.onSuccess { response ->
                                    val pageItems = response.items.flatMap { it.list }
                                    pageResults[source.index] = pageItems
                                    // Prefer the extension's hasNext signal, but also probe onward
                                    // when older extensions omit it and return genuinely new work.
                                    // Two consecutive pages with no new work stop a broken/repeating source.
                                    val containsNewWork = pageItems.any { it.url !in alreadyKnownUrls }
                                    val stalePages = if (containsNewWork) 0 else source.stalePages + 1
                                    val shouldContinue = pageItems.isNotEmpty() &&
                                        (response.hasNext || containsNewWork) &&
                                        stalePages < 2 && source.nextPage < 100
                                    if (shouldContinue) {
                                        nextSources[source.index] = source.copy(
                                            nextPage = source.nextPage + 1,
                                            stalePages = stalePages,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Flatten in source-section order, not network completion order.
                if (generation == requestGeneration) {
                    val knownUrls = (_rows.value.flatMap { it.items } + _catalogItems.value)
                        .mapTo(LinkedHashSet()) { it.url }
                    val orderedPageItems = sources.sortedBy { it.index }
                        .flatMap { pageResults[it.index].orEmpty() }
                    val fresh = orderedPageItems.filter { knownUrls.add(it.url) }
                    _catalogItems.value = _catalogItems.value + fresh
                    catalogSources = nextSources.values.sortedBy { it.index }
                    _catalogHasMore.value = catalogSources.isNotEmpty()
                }
            } finally {
                if (generation == requestGeneration) _catalogLoading.value = false
            }
        }
    }

}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenItem: (SearchResponse) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSources: () -> Unit,
    sourceViewModel: SourceViewModel,
    viewModel: HomeViewModel = viewModel(),
) {
    val providers by viewModel.providers.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val catalogItems by viewModel.catalogItems.collectAsState()
    val catalogLoading by viewModel.catalogLoading.collectAsState()
    val catalogHasMore by viewModel.catalogHasMore.collectAsState()
    val hero by viewModel.hero.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val startupDone by PluginManager.startupDone.collectAsState()
    val sourceName by sourceViewModel.home.collectAsState()

    // One direction: the source owner resolves the name, Home only loads it.
    LaunchedEffect(sourceName) { viewModel.setSource(sourceName) }

    Box(Modifier.fillMaxSize()) {
        when {
            providers.isEmpty() && !startupDone -> LoadingBox(Modifier.padding(contentPadding))

            providers.isEmpty() -> EmptyBox(
                title = stringResource(R.string.home_empty_title),
                hint = stringResource(R.string.home_empty_hint),
                modifier = Modifier.padding(contentPadding),
            ) {
                Button(onClick = onOpenExtensions) { Text(stringResource(R.string.open_extensions)) }
            }

            // The owner is still resolving the saved source; do not show a
            // different provider's shelves in the meantime.
            selected == null -> LoadingBox(Modifier.padding(contentPadding))

            else -> ExpressivePullToRefresh(
                isRefreshing = loading,
                onRefresh = viewModel::retry,
            ) {
                HomeShelves(
                    selectedSource = selected,
                    rows = rows,
                    catalogItems = catalogItems,
                    catalogLoading = catalogLoading,
                    catalogHasMore = catalogHasMore,
                    onLoadMoreCatalog = viewModel::loadMoreCatalog,
                    hero = hero,
                    loading = loading,
                    error = error,
                    contentPadding = contentPadding,
                    onOpenSources = onOpenSources,
                    onOpenItem = onOpenItem,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

/**
 * The shelves themselves, split out of the pull-to-refresh wrapper so the
 * refresh container is not recomposed on every provider switch.
 */
@Composable
private fun HomeShelves(
    selectedSource: String?,
    rows: List<HomeRow>,
    catalogItems: List<SearchResponse>,
    catalogLoading: Boolean,
    catalogHasMore: Boolean,
    onLoadMoreCatalog: () -> Unit,
    hero: List<SearchResponse>,
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    onOpenSources: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onRetry: () -> Unit,
) {
    val chooseSourceLabel = stringResource(R.string.cd_choose_source)
    val listState = rememberLazyListState()
    val topInset = with(LocalDensity.current) { contentPadding.calculateTopPadding().roundToPx() }
    val heroUnderBar by remember(listState, topInset) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "hero" }
                ?.let { it.offset < topInset } == true
        }
    }
    ArtworkStatusBarIcons(overArtwork = heroUnderBar)
    val visibleInShelves = remember(rows) { rows.flatMap { it.items }.mapTo(HashSet()) { it.url } }
    val allOtherWorks = remember(rows, catalogItems) {
        (rows.flatMap { it.items } + catalogItems)
            .distinctBy { it.url }
            .filterNot { it.url in visibleInShelves }
    }
    // Use the app's own PosterCard component, arranged three across per row.
    // This keeps its poster ratio, title typography, rating scrim and press animation.
    val allOtherWorkRows = remember(allOtherWorks) { allOtherWorks.chunked(3) }
    LaunchedEffect(listState, catalogHasMore, catalogLoading, allOtherWorks.size, rows.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(lastVisible >= info.totalItemsCount - 4, info.totalItemsCount)
        }.collect { (nearEnd, _) ->
            if (nearEnd && catalogHasMore && !catalogLoading) onLoadMoreCatalog()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                // With the chip row gone, this line names the provider and opens the sheet.
                if (selectedSource != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = chooseSourceLabel, onClick = onOpenSources),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedSource,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (hero.isNotEmpty()) {
            item(key = "hero") {
                HomeHero(items = hero, onOpen = onOpenItem)
            }
        }

        if (rows.isEmpty() && loading) {
            // One hero-sized placeholder at the carousel's real height, then a
            // shelf: the chrome is already on screen and stays put.
            item(key = "skeleton") {
                SkeletonPulseProvider {
                    Column {
                        SkeletonBox(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(480.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                        Spacer(Modifier.height(24.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(width = 160.dp, height = 24.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                        Spacer(Modifier.height(12.dp))
                        PosterShelfSkeleton()
                    }
                }
            }
        } else if (rows.isEmpty()) {
            item(key = "error") {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    ErrorBox(
                        message = error?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.nothing_found),
                        onRetry = onRetry,
                    )
                }
            }
        }

        itemsIndexed(
            items = rows,
            key = { _, row -> row.key },
            contentType = { _, _ -> "shelf" },
        ) { index, row ->
            // The first three shelves rise in once; later ones just appear, and
            // a provider switch reuses the already-settled rows.
            if (index < 3) {
                val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = entrance,
                    enter = staggeredEnter(index),
                ) {
                    Shelf(row = row, onOpenItem = onOpenItem)
                }
            } else {
                Shelf(row = row, onOpenItem = onOpenItem)
            }
        }

        // Additional vertical catalog: preserve all original shelves above, then
        // page through each extension homepage section and exclude works already shown there.
        if (allOtherWorks.isNotEmpty() || catalogHasMore || catalogLoading) {
            item(key = "all-loaded-works-header") {
                Column(Modifier.padding(top = 8.dp)) {
                    SectionHeader(stringResource(R.string.home_extra_catalog_title))
                    Text(
                        text = stringResource(R.string.home_extra_catalog_count, allOtherWorks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(
                items = allOtherWorkRows,
                key = { index, workRow -> "all-other-grid-$index-${workRow.firstOrNull()?.url.orEmpty()}" },
                contentType = { _, _ -> "all-other-grid-row" },
            ) { _, workRow ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    workRow.forEach { work ->
                        PosterCard(
                            item = work,
                            onClick = { onOpenItem(work) },
                            modifier = Modifier.weight(1f),
                            responsiveWidth = true,
                        )
                    }
                    // Keep a three-column grid on the final, incomplete row too.
                    repeat(3 - workRow.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (catalogLoading) {
                item(key = "catalog-loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(32.dp))
                    }
                }
            } else if (catalogHasMore) {
                item(key = "catalog-load-more") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = onLoadMoreCatalog) {
                            Text(stringResource(R.string.home_catalog_load_more))
                        }
                    }
                }
            }
        }

        if (rows.isNotEmpty() && loading) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    row: HomeRow,
    onOpenItem: (SearchResponse) -> Unit,
) {
    Column {
        SectionHeader(row.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // No keys here: providers sometimes return the same item twice.
            items(
                items = row.items,
                contentType = { if (row.horizontal) "horizontal" else "poster" },
            ) { item ->
                PosterCard(
                    item = item,
                    horizontal = row.horizontal,
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}
