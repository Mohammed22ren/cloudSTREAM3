package com.kodastream.app.ui.home

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.components.LoadingBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.PosterShelfSkeleton
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.components.SkeletonTextLine
import com.kodastream.app.ui.components.ThumbRowSkeleton
import com.kodastream.app.ui.components.staggeredEnter
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class HomeRow(
    val key: String,
    val title: String,
    val items: List<SearchResponse>,
    val horizontal: Boolean,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _providers = MutableStateFlow<List<MainAPI>>(emptyList())
    val providers: StateFlow<List<MainAPI>> = _providers.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
    val rows: StateFlow<List<HomeRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            // Runs again every time an extension is installed, updated or removed.
            PluginManager.revision.collect { refreshProviders() }
        }
    }

    private fun refreshProviders() {
        val available = Engine.homeProviders()
        _providers.value = available
        val current = _selected.value
        if (current != null && available.any { it.name == current }) return

        val saved = AppPrefs.selectedProvider(getApplication())
        val next = available.firstOrNull { it.name == saved } ?: available.firstOrNull()
        _selected.value = next?.name
        if (next == null) {
            loadJob?.cancel()
            _rows.value = emptyList()
            _loading.value = false
            _error.value = null
        } else {
            load(next)
        }
    }

    fun select(name: String) {
        if (name == _selected.value) return
        val api = Engine.provider(name) ?: return
        _selected.value = name
        AppPrefs.setSelectedProvider(getApplication(), name)
        load(api)
    }

    fun retry() {
        val api = Engine.provider(_selected.value) ?: return
        load(api)
    }

    private fun load(api: MainAPI) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _rows.value = emptyList()

            val pages = api.mainPage
            val results = arrayOfNulls<List<HomeRow>>(pages.size)
            var lastFailure: String? = null
            val gate = Semaphore(if (api.sequentialMainPage) 1 else 3)

            coroutineScope {
                pages.forEachIndexed { index, page ->
                    launch {
                        gate.withPermit {
                            val result = Engine.mainPage(api, page)
                            result.onSuccess { lists ->
                                results[index] = lists
                                    .filter { it.list.isNotEmpty() }
                                    .mapIndexed { i, list ->
                                        HomeRow(
                                            key = "$index-$i-${list.name}",
                                            title = list.name.ifBlank { page.name },
                                            items = list.list,
                                            horizontal = list.isHorizontalImages,
                                        )
                                    }
                                _rows.value = results.filterNotNull().flatten()
                            }
                            result.onFailure { lastFailure = it.readableMessage() }
                        }
                    }
                }
            }

            _loading.value = false
            if (_rows.value.isEmpty()) {
                _error.value = lastFailure ?: ""
            }
        }
    }
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenItem: (SearchResponse) -> Unit,
    onOpenExtensions: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val providers by viewModel.providers.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val startupDone by PluginManager.startupDone.collectAsState()

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

            else -> ExpressivePullToRefresh(
                isRefreshing = loading,
                onRefresh = viewModel::retry,
            ) {
                HomeShelves(
                    providers = providers,
                    selected = selected,
                    rows = rows,
                    loading = loading,
                    error = error,
                    contentPadding = contentPadding,
                    onSelectProvider = viewModel::select,
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
    providers: List<MainAPI>,
    selected: String?,
    rows: List<HomeRow>,
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    onSelectProvider: (String) -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "providers") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(providers) { api ->
                    FilterChip(
                        selected = api.name == selected,
                        onClick = { onSelectProvider(api.name) },
                        label = { Text(api.name) },
                    )
                }
            }
        }

        if (rows.isEmpty() && loading) {
            // Placeholders at the shelf's real size and shape: the chrome
            // (provider chips, headers) is already on screen and stays, only
            // the blocked regions breathe.
            item(key = "skeleton-1") {
                Column {
                    SkeletonTextLine(
                        width = 140.dp,
                        height = 20.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    PosterShelfSkeleton()
                }
            }
            item(key = "skeleton-2") {
                Column {
                    SkeletonTextLine(
                        width = 180.dp,
                        height = 20.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    ThumbRowSkeleton()
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

        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            // Each shelf enters once, a step later than the one above it;
            // the delay is capped so shelves far down the list are not
            // waiting behind shelves that are not even visible yet.
            val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = entrance,
                enter = staggeredEnter(index.coerceAtMost(8)),
            ) {
                Column {
                    SectionHeader(row.title)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // No keys here: providers sometimes return the same item twice.
                        items(row.items) { item ->
                            PosterCard(
                                item = item,
                                horizontal = row.horizontal,
                                onClick = { onOpenItem(item) },
                            )
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
