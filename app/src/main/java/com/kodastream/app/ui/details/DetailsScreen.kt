package com.kodastream.app.ui.details

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.DownloadStore.Download
import com.kodastream.app.data.EpisodeQueue
import com.kodastream.app.data.EpisodeQueues
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.data.LibraryStore.LibraryItem
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.downloads.DownloadService
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.LoadingBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.components.bouncySpring
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Failed(val message: String) : DetailsState
    data class Ready(val response: LoadResponse) : DetailsState
}

class DetailsViewModel(val args: DetailsArgs) : ViewModel() {
    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DetailsState.Loading
            val api = Engine.provider(args.apiName)
            if (api == null) {
                _state.value = DetailsState.Failed("Provider \"${args.apiName}\" is not loaded")
                return@launch
            }
            Engine.load(api, args.url).fold(
                onSuccess = { _state.value = DetailsState.Ready(it) },
                onFailure = { _state.value = DetailsState.Failed(it.readableMessage()) },
            )
        }
    }
}

/** The episodes of a response, grouped by dub status. Movies and live streams have none. */
private fun episodesOf(response: LoadResponse): Map<DubStatus, List<Episode>> = when (response) {
    is AnimeLoadResponse -> response.episodes.filterValues { it.isNotEmpty() }
    is TvSeriesLoadResponse -> if (response.episodes.isEmpty()) emptyMap() else mapOf(DubStatus.None to response.episodes)
    else -> emptyMap()
}

/** The `data` string that loadLinks() wants for something playable right away. */
private fun directDataOf(response: LoadResponse): String? = when (response) {
    is MovieLoadResponse -> response.dataUrl
    is LiveStreamLoadResponse -> response.dataUrl
    else -> null
}

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onOpenDownloads: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val args = viewModel.args

    when (val current = state) {
        DetailsState.Loading -> Box(Modifier.fillMaxSize()) {
            LoadingBox()
            BackButton(onBack, Modifier.align(Alignment.TopStart))
        }

        is DetailsState.Failed -> Box(Modifier.fillMaxSize()) {
            ErrorBox(message = current.message, onRetry = viewModel::load)
            BackButton(onBack, Modifier.align(Alignment.TopStart))
        }

        is DetailsState.Ready -> DetailsContent(
            response = current.response,
            fallbackTitle = args.title,
            onBack = onBack,
            onOpenItem = onOpenItem,
            onPlay = onPlay,
            onOpenDownloads = onOpenDownloads,
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onBack,
        modifier = modifier
            .statusBarsPadding()
            .padding(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

/**
 * Favorites and watchlist, one tap each. The state is read back from the
 * store on every revision bump rather than kept locally, so every entry
 * point (this row, the library, another screen) always agrees.
 */
@Composable
private fun LibraryToggles(response: LoadResponse, isMovie: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val revision by LibraryStore.revision.collectAsState()
    var watchedKeys by remember { mutableStateOf(WatchedStore.current(context)) }
    LaunchedEffect(Unit) {
        WatchedStore.keys.collect { updated -> updated?.let { watchedKeys = it } }
    }

    val item = remember(response) {
        LibraryItem(
            apiName = response.apiName,
            url = response.url,
            name = response.name,
            posterUrl = response.posterUrl,
            posterHeaders = response.posterHeaders,
        )
    }
    val isFavorite = remember(revision) {
        LibraryStore.isFavorite(context, item.apiName, item.url)
    }
    val isWatchlisted = remember(revision) {
        LibraryStore.isWatchlisted(context, item.apiName, item.url)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleIconButton(
            filledIcon = Icons.Rounded.Favorite,
            outlinedIcon = Icons.Rounded.FavoriteBorder,
            active = isFavorite,
            label = stringResource(R.string.cd_add_favorites),
            onToggle = { LibraryStore.toggleFavorite(context, item) },
        )
        ToggleIconButton(
            filledIcon = Icons.Rounded.Bookmark,
            outlinedIcon = Icons.Rounded.BookmarkBorder,
            active = isWatchlisted,
            label = stringResource(R.string.cd_add_watchlist),
            onToggle = { LibraryStore.toggleWatchlist(context, item) },
        )
        // A movie is one unit, so its watched mark lives on the page itself;
        // episodes carry theirs in the list below.
        if (isMovie) {
            val movieKey = WatchedStore.keyOf(response.apiName, response.url, null, null)
            ToggleIconButton(
                filledIcon = Icons.Rounded.CheckCircle,
                outlinedIcon = Icons.Outlined.CheckCircle,
                active = movieKey in watchedKeys,
                label = stringResource(R.string.cd_mark_watched),
                onToggle = {
                    WatchedStore.setWatched(
                        context = context,
                        apiName = response.apiName,
                        url = response.url,
                        season = null,
                        episode = null,
                        resumeKey = null,
                        watched = movieKey !in watchedKeys,
                    )
                },
            )
        }
    }
}

/** A toggle button whose icon springs between its filled and outlined form. */
@Composable
private fun ToggleIconButton(
    filledIcon: androidx.compose.ui.graphics.vector.ImageVector,
    outlinedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    FilledTonalIconButton(onClick = onToggle) {
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                (scaleIn(bouncySpring()) + fadeIn()).togetherWith(scaleOut() + fadeOut())
            },
            label = "libraryToggle",
        ) { filled ->
            Icon(
                imageVector = if (filled) filledIcon else outlinedIcon,
                contentDescription = label,
                tint = if (filled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun DetailsContent(
    response: LoadResponse,
    fallbackTitle: String?,
    onBack: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val episodesByDub = remember(response) { episodesOf(response) }
    val dubOptions = remember(episodesByDub) { episodesByDub.keys.toList() }
    var selectedDub by remember(response) { mutableStateOf(dubOptions.firstOrNull()) }
    val allEpisodes = episodesByDub[selectedDub].orEmpty()

    val seasonNames = remember(response) { (response as? EpisodeResponse)?.seasonNames.orEmpty() }
    val seasons = remember(allEpisodes) { allEpisodes.mapNotNull { it.season }.distinct().sorted() }
    var selectedSeason by remember(response, selectedDub) { mutableStateOf(seasons.firstOrNull()) }
    val visibleEpisodes = if (selectedSeason == null) {
        allEpisodes
    } else {
        allEpisodes.filter { it.season == selectedSeason || it.season == null }
    }

    // Watched episodes live in their own store, keyed per episode.
    val context = LocalContext.current
    var watchedKeys by remember { mutableStateOf(WatchedStore.current(context)) }
    LaunchedEffect(Unit) {
        WatchedStore.keys.collect { updated -> updated?.let { watchedKeys = it } }
    }
    var hideWatched by rememberSaveable { mutableStateOf(false) }

    fun episodeKey(episode: Episode, index: Int): String =
        WatchedStore.keyOf(response.apiName, response.url, episode.season ?: selectedSeason, episode.episode ?: index + 1)

    /** Marks a batch of episodes and keeps the title's History entry in step. */
    fun setWatchedBatch(episodes: List<Pair<Episode, Int>>, watched: Boolean) {
        WatchedStore.setAll(context, episodes.map { (ep, i) -> episodeKey(ep, i) }, watched)
        val historyKey = response.apiName + "|" + response.url
        if (watched) {
            HistoryStore.setCompleted(context, historyKey, true)
        } else {
            HistoryStore.setCompleted(context, historyKey, false)
            HistoryStore.resetProgress(context, historyKey)
        }
    }


    val title = response.name.ifBlank { fallbackTitle.orEmpty() }
    val plain = remember(response) {
        response.plot?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    }
    var plotExpanded by remember(response) { mutableStateOf(false) }
    val directData = directDataOf(response)

    // ---- downloads ----
    var downloads by remember { mutableStateOf(DownloadStore.current(context)) }
    LaunchedEffect(Unit) {
        DownloadStore.entries.collect { updated -> updated?.let { downloads = it } }
    }
    val scope = rememberCoroutineScope()
    var downloadSheet by remember { mutableStateOf<DownloadTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startDownload(
        link: ExtractorLink,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        episodeData: String? = null,
        subtitles: List<SubtitleFile> = emptyList(),
    ) {
        ensureNotifPermission()
        val isHls = link.type == ExtractorLinkType.M3U8
        val sanitize: (String) -> String = { s: String ->
            s.replace(Regex("[\\/:*?\"<>|]"), "").trim().take(100)
        }
        val fileName = if (season != null || episode != null) {
            "${sanitize(title)} - S${"%02d".format(season ?: 1)}E${"%02d".format(episode ?: 1)}" +
                (episodeTitle?.takeIf { it.isNotBlank() }?.let { " - ${sanitize(it)}" } ?: "") +
                "." + (if (isHls) "ts" else "mp4")
        } else {
            sanitize(title) + (response.year?.let { " ($it)" } ?: "") + "." + (if (isHls) "ts" else "mp4")
        }
        val queued = DownloadStore.enqueue(
            context,
            Download(
                id = DownloadStore.idOf(response.apiName, response.url, season, episode),
                apiName = response.apiName,
                url = response.url,
                title = title,
                posterUrl = response.posterUrl,
                posterHeaders = response.posterHeaders,
                type = when (response) {
                    is AnimeLoadResponse -> "anime"
                    is TvSeriesLoadResponse -> "series"
                    else -> "movie"
                },
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                year = response.year,
                linkUrl = link.url,
                linkName = link.name,
                quality = link.quality,
                headers = link.getAllHeaders(),
                streamType = if (isHls) "hls" else "direct",
                data = episodeData,
                fileName = fileName,
                subLang = subtitles.firstOrNull()?.lang,
                subUrl = subtitles.firstOrNull()?.url,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (queued) DownloadService.ensure(context)
    }

    fun downloadOf(id: String): Download? = downloads.firstOrNull { it.id == id }

    /** "Download added" with a way to jump straight to the Downloads tab. */
    fun announceDownload(count: Int, fallbacks: Int) {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = if (fallbacks > 0) {
                    context.getString(R.string.download_season_fallback, count, fallbacks)
                } else {
                    context.getString(R.string.added_to_downloads)
                },
                actionLabel = context.getString(R.string.view),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                AppEvents.pendingLibrarySection.value = "DOWNLOADS"
                onOpenDownloads()
            }
        }
    }

    /** Season mode: apply the preferred server and language to every episode. */
    fun downloadSeason(episodes: List<Episode>, preferredServer: String, preferredSubLang: String?) {
        scope.launch {
            val api = Engine.provider(response.apiName) ?: return@launch
            var queued = 0
            var fallbacks = 0
            for ((index, episode) in episodes.withIndex()) {
                val season = episode.season ?: selectedSeason
                val number = episode.episode ?: index + 1
                val id = DownloadStore.idOf(response.apiName, response.url, season, number)
                if (downloads.any { it.id == id && it.state == DownloadStore.STATE_DONE }) continue
                // Thread-safe for the same reason as the download card:
                // providers fire these callbacks from parallel extractors.
                val links = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
                val subs = java.util.Collections.synchronizedList(mutableListOf<SubtitleFile>())
                try {
                    Engine.guarded(api.loadLinksTimeoutMs ?: 120_000L) {
                        api.loadLinks(
                            episode.data, false,
                            { sub -> runCatching { subs.add(sub) } },
                            { link -> runCatching { links.add(link) } },
                        )
                    }
                } catch (t: Throwable) {
                    continue
                }
                val usable = synchronized(links) { links.toList() }
                    .filter { it.type == ExtractorLinkType.VIDEO || it.type == ExtractorLinkType.M3U8 }
                val chosen = usable.firstOrNull { it.name.equals(preferredServer, ignoreCase = true) }
                    ?: usable.maxByOrNull { it.quality }
                    ?: continue
                if (!chosen.name.equals(preferredServer, ignoreCase = true)) fallbacks++
                val sub = if (preferredSubLang == null) {
                    null
                } else {
                    synchronized(subs) { subs.toList() }.firstOrNull { candidate ->
                        candidate.lang.equals(preferredSubLang, ignoreCase = true) ||
                            candidate.langTag?.startsWith(preferredSubLang, ignoreCase = true) == true
                    }
                }
                if (preferredSubLang != null && sub == null) fallbacks++
                startDownload(
                    chosen, season, number, episode.name, episode.data,
                    sub?.let { listOf(it) } ?: emptyList(),
                )
                queued++
            }
            if (queued > 0) announceDownload(queued, fallbacks)
        }
    }


    fun play(data: String, subtitle: String?, episode: Episode? = null) {
        val resumeKey = response.apiName + "|" + data
        // Episodes carry the whole series with them so the player can walk
        // between episodes without loading anything again.
        val queue = if (episode != null && allEpisodes.isNotEmpty()) {
            EpisodeQueues.build(
                apiName = response.apiName,
                url = response.url,
                title = title,
                posterUrl = response.posterUrl,
                posterHeaders = response.posterHeaders,
                type = when (response) {
                    is AnimeLoadResponse -> "anime"
                    is TvSeriesLoadResponse -> "series"
                    else -> "movie"
                },
                episodes = allEpisodes,
            )
        } else {
            null
        }
        onPlay(
            PlayerArgs(
                apiName = response.apiName,
                data = data,
                title = title,
                subtitle = subtitle,
                resumeKey = resumeKey,
                queue = queue,
                history = HistoryEntry(
                    key = response.apiName + "|" + response.url,
                    apiName = response.apiName,
                    url = response.url,
                    title = title,
                    posterUrl = response.posterUrl,
                    posterHeaders = response.posterHeaders,
                    type = when (response) {
                        is AnimeLoadResponse -> "anime"
                        is TvSeriesLoadResponse -> "series"
                        else -> "movie"
                    },
                    season = episode?.season ?: selectedSeason,
                    episode = episode?.episode,
                    episodeTitle = episode?.name,
                    data = data,
                    subtitle = subtitle,
                    resumeKey = resumeKey,
                ),
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 11f)
            ) {
                RemoteImage(
                    url = response.backgroundPosterUrl ?: response.posterUrl,
                    headers = response.posterHeaders,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background,
                            )
                        )
                )
                BackButton(onBack, Modifier.align(Alignment.TopStart))
                val isMovie = response !is TvSeriesLoadResponse && response !is AnimeLoadResponse
                if (isMovie && WatchedStore.keyOf(response.apiName, response.url, null, null) in watchedKeys) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.cd_watched),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                            .padding(6.dp)
                            .size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        item(key = "meta") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaLine(response)

                LibraryToggles(response = response, isMovie = response !is TvSeriesLoadResponse && response !is AnimeLoadResponse)

                if (directData != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { play(directData, null) },
                            enabled = !response.comingSoon && directData.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (response.comingSoon) R.string.coming_soon else R.string.play
                                )
                            )
                        }
                        val movieDownload = downloadOf(DownloadStore.idOf(response.apiName, response.url, null, null))
                        DownloadStateIcon(download = movieDownload, onDownload = {
                            downloadSheet = DownloadTarget.Single(directData, null, null, null)
                        })
                    }
                }

                if (!plain.isNullOrBlank()) {
                    Text(
                        text = plain,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (plotExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { plotExpanded = !plotExpanded },
                    )
                }

                val tags = response.tags.orEmpty().filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.joinToString("  •  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (directData == null && episodesByDub.isEmpty()) {
            item(key = "unsupported") {
                Text(
                    text = stringResource(
                        if (response.comingSoon) R.string.coming_soon else R.string.type_not_supported
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (dubOptions.size > 1) {
            item(key = "dubs") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(dubOptions) { dub ->
                        FilterChip(
                            selected = dub == selectedDub,
                            onClick = { selectedDub = dub },
                            label = {
                                Text(
                                    stringResource(
                                        when (dub) {
                                            DubStatus.Dubbed -> R.string.dub
                                            DubStatus.Subbed -> R.string.sub
                                            DubStatus.None -> R.string.episodes
                                        }
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }

        if (seasons.size > 1) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasons) { season ->
                        val data = seasonNames.firstOrNull { it.season == season }
                        val label = when {
                            data?.name != null && data.displaySeason != null ->
                                stringResource(R.string.season_n, data.displaySeason!!) + " " + data.name
                            data?.name != null -> data.name!!
                            data?.displaySeason != null -> stringResource(R.string.season_n, data.displaySeason!!)
                            else -> stringResource(R.string.season_n, season)
                        }
                        FilterChip(
                            selected = season == selectedSeason,
                            onClick = { selectedSeason = season },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }

        if (visibleEpisodes.isNotEmpty()) {
            item(key = "episodes-title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.episodes), Modifier.weight(1f))
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.episode_actions),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_season_watched)) },
                                onClick = {
                                    setWatchedBatch(visibleEpisodes.mapIndexed { i, ep -> ep to i }, true)
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_previous_watched)) },
                                onClick = {
                                    // Everything before the highest watched episode.
                                    val maxWatched = visibleEpisodes.mapIndexedNotNull { i, ep ->
                                        if (episodeKey(ep, i) in watchedKeys) (ep.episode ?: i + 1) else null
                                    }.maxOrNull()
                                    if (maxWatched != null) {
                                        setWatchedBatch(
                                            visibleEpisodes.mapIndexed { i, ep -> ep to i }
                                                .filter { (ep, i) -> (ep.episode ?: i + 1) < maxWatched },
                                            true,
                                        )
                                    }
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_season_unwatched)) },
                                onClick = {
                                    setWatchedBatch(visibleEpisodes.mapIndexed { i, ep -> ep to i }, false)
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_season)) },
                                onClick = {
                                    menuOpen = false
                                    downloadSheet = DownloadTarget.Season(visibleEpisodes.toList())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.hide_watched)) },
                                leadingIcon = {
                                    if (hideWatched) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    hideWatched = !hideWatched
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            itemsIndexed(visibleEpisodes) { index, episode ->
                val watched = episodeKey(episode, index) in watchedKeys
                // Hidden watched episodes keep their place in the index math,
                // they just draw nothing while the toggle is on.
                if (watched && hideWatched) return@itemsIndexed
                // A composable call, so it is made here and not inside the click handler.
                val label = episodeLabel(episode, index)
                val episodeDownload = downloadOf(
                    DownloadStore.idOf(
                        response.apiName, response.url,
                        episode.season ?: selectedSeason, episode.episode ?: index + 1,
                    )
                )
                EpisodeRow(
                    episode = episode,
                    index = index,
                    posterHeaders = response.posterHeaders,
                    watched = watched,
                    download = episodeDownload,
                    onClick = { play(episode.data, label, episode) },
                    onToggleWatched = {
                        WatchedStore.setWatched(
                            context = context,
                            apiName = response.apiName,
                            url = response.url,
                            season = episode.season ?: selectedSeason,
                            episode = episode.episode ?: index + 1,
                            resumeKey = response.apiName + "|" + episode.data,
                            watched = !watched,
                        )
                    },
                    onDownload = {
                        when (episodeDownload?.state) {
                            null -> downloadSheet = DownloadTarget.Single(
                                episode.data,
                                episode.season ?: selectedSeason,
                                episode.episode ?: index + 1,
                                episode.name,
                            )

                            DownloadStore.STATE_DOWNLOADING -> DownloadService.pause(context, episodeDownload.id)
                            DownloadStore.STATE_PAUSED -> DownloadService.resume(context, episodeDownload.id)
                            DownloadStore.STATE_FAILED -> DownloadService.retry(context, episodeDownload.id)
                            DownloadStore.STATE_QUEUED -> DownloadService.cancel(context, episodeDownload.id)
                            DownloadStore.STATE_DONE -> Unit
                        }
                    },
                )
            }
        }

        val recommendations = response.recommendations.orEmpty()
        if (recommendations.isNotEmpty()) {
            item(key = "recommendations") {
                Column {
                    SectionHeader(stringResource(R.string.recommended))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recommendations) { item ->
                            PosterCard(item = item, onClick = { onOpenItem(item) })
                        }
                    }
                }
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.navigationBarsPadding()) }
    }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    downloadSheet?.let { sheetTarget ->
        DownloadOptionsSheet(
            apiName = response.apiName,
            target = sheetTarget,
            onConfirm = { link, subtitle ->
                if (sheetTarget is DownloadTarget.Single) {
                    startDownload(
                        link,
                        sheetTarget.season,
                        sheetTarget.episode,
                        sheetTarget.episodeTitle,
                        sheetTarget.data,
                        subtitle?.let { listOf(it) } ?: emptyList(),
                    )
                    announceDownload(1, 0)
                }
                downloadSheet = null
            },
            onConfirmSeason = { serverName, subLang ->
                downloadSheet = null
                if (sheetTarget is DownloadTarget.Season) {
                    downloadSeason(sheetTarget.episodes, serverName, subLang)
                }
            },
            onDismiss = { downloadSheet = null },
        )
    }
}

/**
 * The download affordance: one icon that reads the download state and acts
 * on tap - start, pause, resume, retry, cancel - with a 48dp target.
 */
@Composable
private fun DownloadStateIcon(
    download: Download?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, description) = when (download?.state) {
        null -> Triple(Icons.Rounded.Download, MaterialTheme.colorScheme.onSurfaceVariant, R.string.cd_download)
        DownloadStore.STATE_QUEUED -> Triple(Icons.Rounded.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, R.string.download_queued)
        DownloadStore.STATE_DOWNLOADING -> Triple(Icons.Rounded.Downloading, MaterialTheme.colorScheme.primary, R.string.cd_download_pause)
        DownloadStore.STATE_PAUSED -> Triple(Icons.Rounded.PauseCircle, MaterialTheme.colorScheme.secondary, R.string.cd_download_resume)
        DownloadStore.STATE_FAILED -> Triple(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, R.string.cd_download_retry)
        DownloadStore.STATE_DONE -> Triple(Icons.Rounded.DownloadDone, MaterialTheme.colorScheme.primary, R.string.downloaded)
        else -> Triple(Icons.Rounded.Download, MaterialTheme.colorScheme.onSurfaceVariant, R.string.cd_download)
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = tint,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onDownload)
            .padding(12.dp)
            .size(24.dp),
    )
}

@Composable
private fun MetaLine(response: LoadResponse) {
    val minutes = response.duration?.takeIf { it > 0 }?.let { stringResource(R.string.minutes_short, it) }
    val parts = listOfNotNull(response.year?.toString(), minutes, response.type.name)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = parts.joinToString("  •  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val score = response.score?.toFloat(10)
        if (score != null && score > 0f) {
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "%.1f".format(score),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun episodeLabel(episode: Episode, index: Int): String {
    val number = episode.episode ?: (index + 1)
    val base = stringResource(R.string.episode_n, number)
    return if (episode.name.isNullOrBlank()) base else "$base • ${episode.name}"
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    index: Int,
    posterHeaders: Map<String, String>?,
    watched: Boolean,
    download: Download? = null,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownload: () -> Unit = {},
) {
    val number = episode.episode ?: (index + 1)
    val title = episode.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.episode_n, number)
    // Watched episodes dim out of the way; their check stays fully opaque.
    val dim = if (watched) 0.45f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemoteImage(
            url = episode.posterUrl,
            headers = posterHeaders,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small)
                .alpha(dim),
        )
        Column(Modifier.weight(1f).alpha(dim), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "$number. $title",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val description = episode.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DownloadStateIcon(download = download, onDownload = onDownload, modifier = Modifier)
        ToggleIconButton(
            filledIcon = Icons.Rounded.CheckCircle,
            outlinedIcon = Icons.Outlined.CheckCircle,
            active = watched,
            label = stringResource(R.string.cd_mark_watched),
            onToggle = onToggleWatched,
        )
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.play),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
