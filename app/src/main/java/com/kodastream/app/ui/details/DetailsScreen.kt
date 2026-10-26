package com.kodastream.app.ui.details

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.EpisodeQueues
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.data.LibraryStore.LibraryItem
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.downloads.DownloadService
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.components.SkeletonBox
import com.kodastream.app.ui.components.SkeletonPulseProvider
import com.kodastream.app.ui.components.bouncySpring
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
            DetailsSkeleton()
            TopBar(
                title = args.title.orEmpty(),
                collapsed = true,
                onBack = onBack,
                onShare = null,
            )
        }

        is DetailsState.Failed -> Box(Modifier.fillMaxSize()) {
            ErrorBox(message = current.message, onRetry = viewModel::load)
            TopBar(
                title = args.title.orEmpty(),
                collapsed = true,
                onBack = onBack,
                onShare = null,
            )
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
private fun TopBar(
    title: String,
    collapsed: Boolean,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
) {
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "topBarAlpha")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f * barAlpha))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .graphicsLayer { alpha = barAlpha },
        )
        if (onShare != null) {
            FilledTonalIconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.cd_share))
            }
        } else {
            Spacer(Modifier.size(48.dp))
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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val title = response.name.ifBlank { fallbackTitle.orEmpty() }
    val isMovie = response !is TvSeriesLoadResponse && response !is AnimeLoadResponse
    val directData = directDataOf(response)?.takeIf { it.isNotBlank() }

    val episodesByDub = remember(response) { episodesOf(response) }
    val dubOptions = remember(episodesByDub) { episodesByDub.keys.toList() }
    var selectedDub by remember(response) { mutableStateOf(dubOptions.firstOrNull()) }
    val allEpisodes = episodesByDub[selectedDub].orEmpty()
    val allEpisodesAnyDub = remember(episodesByDub) { episodesByDub.values.flatten() }

    val seasonNames = remember(response) { (response as? EpisodeResponse)?.seasonNames.orEmpty() }
    val seasons = remember(allEpisodes) { allEpisodes.mapNotNull { it.season }.distinct().sorted() }
    var selectedSeason by remember(response, selectedDub) { mutableStateOf(seasons.firstOrNull()) }
    val visibleEpisodes = if (selectedSeason == null) {
        allEpisodes
    } else {
        allEpisodes.filter { it.season == selectedSeason || it.season == null }
    }

    // Watched and history are read once for the screen; both only change after
    // an explicit action or when playback returns, never per download tick.
    var watchedKeys by remember { mutableStateOf(WatchedStore.current(context)) }
    LaunchedEffect(Unit) {
        WatchedStore.keys.collect { updated -> updated?.let { watchedKeys = it } }
    }
    var history by remember { mutableStateOf(HistoryStore.current(context)) }
    LaunchedEffect(Unit) {
        HistoryStore.entries.collect { updated -> updated?.let { history = it } }
    }
    var hideWatched by rememberSaveable { mutableStateOf(false) }

    // Library state is derived once per revision, not once per toggle button.
    val revision by LibraryStore.revision.collectAsState()
    val libraryItem = remember(response) {
        LibraryItem(
            apiName = response.apiName,
            url = response.url,
            name = response.name,
            posterUrl = response.posterUrl,
            posterHeaders = response.posterHeaders,
            score10 = response.score?.toFloat(10),
        )
    }
    val isFavorite = remember(revision, libraryItem) {
        LibraryStore.isFavorite(context, libraryItem.apiName, libraryItem.url)
    }
    val isWatchlisted = remember(revision, libraryItem) {
        LibraryStore.isWatchlisted(context, libraryItem.apiName, libraryItem.url)
    }

    // Only lifecycle changes reach the screen; byte progress is never read, so
    // a progress tick cannot invalidate the whole page.
    val initialDownloadStates = remember(context) {
        DownloadStore.current(context).associate { it.id to it.state }
    }
    val downloadStates = remember(context) {
        DownloadStore.entries
            .map { list -> list.orEmpty().associate { it.id to it.state } }
            .distinctUntilChanged()
    }.collectAsState(initial = initialDownloadStates)

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

    val plain = remember(response) {
        response.plot?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    }
    val historyKey = response.apiName + "|" + response.url
    val resume = remember(history, response) {
        history.firstOrNull { it.key == historyKey && !it.isComplete && it.data.isNotBlank() }
    }
    val resumeEpisode = remember(resume, allEpisodesAnyDub) {
        resume?.let { entry ->
            allEpisodesAnyDub.firstOrNull { it.data == entry.data }
                ?: allEpisodesAnyDub.firstOrNull { it.season == entry.season && it.episode == entry.episode }
        }
    }
    val resumeFraction = resume
        ?.takeIf { it.durationMs > 0L }
        ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        ?: 0f
    val resumeMinutes = resume
        ?.takeIf { it.durationMs > it.positionMs }
        ?.let { ((it.durationMs - it.positionMs) / 60_000L).coerceAtLeast(1L) }
    val firstPlayable = allEpisodes.firstOrNull() ?: allEpisodesAnyDub.firstOrNull()
    val firstPlayableLabel = if (firstPlayable != null) episodeLabel(firstPlayable, 0) else null

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var downloadSheet by remember { mutableStateOf<DownloadTarget?>(null) }
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
            DownloadStore.Download(
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
                if (DownloadStore.current(context).any { it.id == id && it.state == DownloadStore.STATE_DONE }) continue
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
                    key = historyKey,
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

    val movieKey = if (isMovie) WatchedStore.keyOf(response.apiName, response.url, null, null) else null
    val movieWatched = movieKey != null && movieKey in watchedKeys
    val movieDownloadId = if (isMovie && directData != null) {
        DownloadStore.idOf(response.apiName, response.url, null, null)
    } else {
        null
    }

    val share: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, title, response.url))
        runCatching {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
        }
    }

    val primaryTrailerUrl = remember(response) {
        response.trailers.firstNotNullOfOrNull(::trailerUrl)
    }
    val playLabel = when {
        response.comingSoon -> stringResource(R.string.coming_soon)
        resume != null -> stringResource(R.string.resume)
        else -> stringResource(R.string.play)
    }
    val playSubtitle = if (resume != null && resumeMinutes != null) {
        stringResource(R.string.minutes_left, resumeMinutes)
    } else {
        null
    }
    val playEnabled = !response.comingSoon && (directData != null || firstPlayable != null)
    val playPrimary: () -> Unit = {
        when {
            resume != null -> play(resume.data, resume.subtitle, resumeEpisode)
            directData != null -> play(directData, null)
            firstPlayable != null -> play(firstPlayable.data, firstPlayableLabel, firstPlayable)
        }
    }

    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val tags = remember(response) { response.tags.orEmpty().filter { it.isNotBlank() } }
    val recommendations = remember(response) {
        response.recommendations.orEmpty().distinctBy { it.apiName to it.url }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "hero") {
                DetailsHero(
                    response = response,
                    title = title,
                    parallax = {
                        if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset / 2f
                        } else {
                            0f
                        }
                    },
                )
            }

            if (tags.isNotEmpty()) {
                item(key = "tags") { TagsRow(tags) }
            }

            item(key = "actions") {
                val downloadState = movieDownloadId?.let { downloadStates.value[it] }
                PrimaryActions(
                    playLabel = playLabel,
                    playSubtitle = playSubtitle,
                    progress = resumeFraction,
                    playEnabled = playEnabled,
                    onPlay = playPrimary,
                    favorite = isFavorite,
                    onFavorite = { LibraryStore.toggleFavorite(context, libraryItem) },
                    watchlisted = isWatchlisted,
                    onWatchlist = { LibraryStore.toggleWatchlist(context, libraryItem) },
                    showDownload = isMovie && directData != null,
                    downloadState = downloadState,
                    onDownload = {
                        when (downloadState) {
                            null -> if (directData != null) {
                                downloadSheet = DownloadTarget.Single(directData, null, null, null)
                            }

                            DownloadStore.STATE_DOWNLOADING -> movieDownloadId?.let { DownloadService.pause(context, it) }
                            DownloadStore.STATE_PAUSED -> movieDownloadId?.let { DownloadService.resume(context, it) }
                            DownloadStore.STATE_FAILED -> movieDownloadId?.let { DownloadService.retry(context, it) }
                            DownloadStore.STATE_QUEUED -> movieDownloadId?.let { DownloadService.cancel(context, it) }
                            DownloadStore.STATE_DONE -> Unit
                        }
                    },
                    watched = if (isMovie) movieWatched else null,
                    onToggleWatched = {
                        if (movieKey != null) {
                            WatchedStore.setWatched(
                                context = context,
                                apiName = response.apiName,
                                url = response.url,
                                season = null,
                                episode = null,
                                resumeKey = null,
                                watched = !movieWatched,
                            )
                        }
                    },
                    trailerUrl = primaryTrailerUrl,
                    onTrailer = {
                        primaryTrailerUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
                    },
                    onShare = share,
                )
            }

            if (!plain.isNullOrBlank()) {
                item(key = "overview") { Overview(plain) }
            }

            if (directData == null && episodesByDub.isEmpty()) {
                item(key = "unsupported") {
                    Text(
                        text = stringResource(
                            if (response.comingSoon) R.string.coming_soon else R.string.type_not_supported
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }

            if (dubOptions.size > 1) {
                item(key = "dubs") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = dubOptions,
                            key = { it.name },
                            contentType = { "dub" },
                        ) { dub ->
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
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = seasons,
                            key = { it },
                            contentType = { "season" },
                        ) { season ->
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
                        SectionTitle(stringResource(R.string.episodes), Modifier.weight(1f))
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
                itemsIndexed(
                    items = visibleEpisodes,
                    key = { index, episode ->
                        "${episode.season}:${episode.episode ?: index}:${episode.data.hashCode()}"
                    },
                    contentType = { _, _ -> "episode" },
                ) { index, episode ->
                    val watched = episodeKey(episode, index) in watchedKeys
                    // Hidden watched episodes keep their place in the index math,
                    // they just draw nothing while the toggle is on.
                    if (watched && hideWatched) return@itemsIndexed
                    // A composable call, so it is made here and not inside the click handler.
                    val label = episodeLabel(episode, index)
                    val downloadId = DownloadStore.idOf(
                        response.apiName, response.url,
                        episode.season ?: selectedSeason, episode.episode ?: index + 1,
                    )
                    val downloadState = downloadStates.value[downloadId]
                    EpisodeRow(
                        episode = episode,
                        index = index,
                        posterHeaders = response.posterHeaders,
                        watched = watched,
                        downloadState = downloadState,
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
                            when (downloadState) {
                                null -> downloadSheet = DownloadTarget.Single(
                                    episode.data,
                                    episode.season ?: selectedSeason,
                                    episode.episode ?: index + 1,
                                    episode.name,
                                )

                                DownloadStore.STATE_DOWNLOADING -> DownloadService.pause(context, downloadId)
                                DownloadStore.STATE_PAUSED -> DownloadService.resume(context, downloadId)
                                DownloadStore.STATE_FAILED -> DownloadService.retry(context, downloadId)
                                DownloadStore.STATE_QUEUED -> DownloadService.cancel(context, downloadId)
                                DownloadStore.STATE_DONE -> Unit
                            }
                        },
                    )
                }
            }

            val actors = response.actors.orEmpty()
            if (actors.isNotEmpty()) {
                item(key = "cast") {
                    SectionTitle(stringResource(R.string.cast_title))
                    CastRail(actors.take(20), response.posterHeaders)
                }
            }

            if (response.trailers.isNotEmpty()) {
                item(key = "trailers") {
                    SectionTitle(stringResource(R.string.trailers_title))
                    TrailerRail(response.trailers)
                }
            }

            if (recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    Column {
                        SectionTitle(stringResource(R.string.recommended))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                items = recommendations,
                                key = { _, item -> "${item.apiName}|${item.url}" },
                                contentType = { _, _ -> "poster" },
                            ) { _, item ->
                                PosterCard(item = item, onClick = { onOpenItem(item) })
                            }
                        }
                    }
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.navigationBarsPadding()) }
        }

        TopBar(
            title = title,
            collapsed = collapsed,
            onBack = onBack,
            onShare = share,
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
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

@Composable
private fun DetailsHero(
    response: LoadResponse,
    title: String,
    parallax: () -> Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            // The parallax shifts the artwork down; without clipping its bottom edge shows below the hero.
            .clipToBounds()
    ) {
        RemoteImage(
            url = response.backgroundPosterUrl ?: response.posterUrl,
            headers = response.posterHeaders,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = parallax() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.62f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.background,
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                shadowElevation = 12.dp,
                modifier = Modifier.size(112.dp, 166.dp),
            ) {
                RemoteImage(
                    url = response.posterUrl,
                    headers = response.posterHeaders,
                    contentDescription = stringResource(R.string.cd_poster, title),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                if (response.comingSoon) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.coming_soon).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
                DetailsMetaLine(response, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun DetailsMetaLine(response: LoadResponse, modifier: Modifier = Modifier) {
    val minutes = response.duration?.takeIf { it > 0 }?.let { stringResource(R.string.minutes_short, it) }
    val parts = listOfNotNull(
        response.year?.toString(),
        minutes,
        response.contentRating?.takeIf { it.isNotBlank() },
    )
    val score = response.score?.toFloat(10)?.takeIf { it > 0f }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (score != null) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = " %.1f".format(score) + if (parts.isNotEmpty()) "  ·  " else "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
    ) {
        tags.take(4).forEach { tag ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private data class ActionTileSpec(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val busy: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun PrimaryActions(
    playLabel: String,
    playSubtitle: String?,
    progress: Float,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    watchlisted: Boolean,
    onWatchlist: () -> Unit,
    showDownload: Boolean,
    downloadState: String?,
    onDownload: () -> Unit,
    watched: Boolean?,
    onToggleWatched: () -> Unit,
    trailerUrl: String?,
    onTrailer: () -> Unit,
    onShare: () -> Unit,
) {
    val favoriteLabel = stringResource(R.string.action_favorite)
    val watchlistLabel = stringResource(R.string.action_watchlist)
    val downloadLabel = stringResource(R.string.download)
    val queuedLabel = stringResource(R.string.download_queued)
    val downloadingLabel = stringResource(R.string.downloading)
    val pausedLabel = stringResource(R.string.download_paused)
    val failedLabel = stringResource(R.string.download_failed)
    val downloadedLabel = stringResource(R.string.downloaded)
    val watchedLabel = stringResource(R.string.cd_watched)
    val markWatchedLabel = stringResource(R.string.mark_watched)
    val trailerLabel = stringResource(R.string.action_trailer)
    val shareLabel = stringResource(R.string.action_share)

    val tiles = buildList {
        add(
            ActionTileSpec(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = favoriteLabel,
                active = favorite,
                onClick = onFavorite,
            )
        )
        add(
            ActionTileSpec(
                icon = if (watchlisted) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = watchlistLabel,
                active = watchlisted,
                onClick = onWatchlist,
            )
        )
        if (showDownload) {
            add(
                when (downloadState) {
                    null -> ActionTileSpec(Icons.Rounded.Download, downloadLabel, onClick = onDownload)
                    DownloadStore.STATE_QUEUED -> ActionTileSpec(
                        Icons.Rounded.Schedule, queuedLabel, active = true, onClick = onDownload,
                    )
                    DownloadStore.STATE_DOWNLOADING -> ActionTileSpec(
                        Icons.Rounded.Downloading, downloadingLabel, busy = true, onClick = onDownload,
                    )
                    DownloadStore.STATE_PAUSED -> ActionTileSpec(
                        Icons.Rounded.PauseCircle, pausedLabel, active = true, onClick = onDownload,
                    )
                    DownloadStore.STATE_FAILED -> ActionTileSpec(
                        Icons.Rounded.ErrorOutline, failedLabel, onClick = onDownload,
                    )
                    DownloadStore.STATE_DONE -> ActionTileSpec(
                        Icons.Rounded.DownloadDone, downloadedLabel, active = true, onClick = onDownload,
                    )
                    else -> ActionTileSpec(Icons.Rounded.Download, downloadLabel, onClick = onDownload)
                }
            )
        }
        if (watched != null) {
            add(
                ActionTileSpec(
                    icon = if (watched) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                    label = if (watched) watchedLabel else markWatchedLabel,
                    active = watched,
                    onClick = onToggleWatched,
                )
            )
        }
        if (trailerUrl != null) {
            add(ActionTileSpec(Icons.Rounded.Theaters, trailerLabel, onClick = onTrailer))
        }
        add(ActionTileSpec(Icons.Rounded.Share, shareLabel, onClick = onShare))
    }

    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Button(
            onClick = onPlay,
            enabled = playEnabled,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = playLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playSubtitle != null) {
                    Text(
                        text = playSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (progress > 0f) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        tiles.chunked(3).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (rowIndex == 0) 12.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTiles.forEach { tile ->
                    ActionTile(tile = tile, modifier = Modifier.weight(1f))
                }
                repeat(3 - rowTiles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    tile: ActionTileSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (tile.active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (tile.active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = tile.onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tile.busy) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                AnimatedContent(
                    targetState = tile.icon,
                    transitionSpec = {
                        (scaleIn(bouncySpring()) + fadeIn()).togetherWith(scaleOut() + fadeOut())
                    },
                    label = "actionTileIcon",
                ) { icon ->
                    Icon(icon, contentDescription = null)
                }
            }
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Overview(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .animateContentSize()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.length > 220) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(
                    stringResource(
                        if (expanded) R.string.show_less else R.string.more
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 14.dp)
            .semantics { heading() },
    )
}

@Composable
private fun CastRail(actors: List<ActorData>, posterHeaders: Map<String, String>?) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = actors,
            key = { index, person -> "${person.actor.name}-$index" },
            contentType = { _, _ -> "cast" },
        ) { _, person ->
            Column(
                modifier = Modifier.width(88.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RemoteImage(
                    url = person.actor.image,
                    headers = posterHeaders,
                    contentDescription = person.actor.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                )
                Text(
                    text = person.actor.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val role = person.roleString ?: person.role?.name
                if (!role.isNullOrBlank()) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerRail(trailers: List<TrailerData>) {
    val uriHandler = LocalUriHandler.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = trailers,
            key = { index, trailer -> "${trailer.extractorUrl}-$index" },
            contentType = { _, _ -> "trailer" },
        ) { index, trailer ->
            val url = trailerUrl(trailer)
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        onClickLabel = stringResource(R.string.cd_trailer, index + 1),
                    ) {
                        if (url != null) runCatching { uriHandler.openUri(url) }
                    }
                    .padding(bottom = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.trailer_n, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
                )
            }
        }
    }
}

private fun trailerUrl(trailer: TrailerData): String? {
    val raw = trailer.extractorUrl.takeIf { it.isNotBlank() } ?: return null
    return if (raw.startsWith("http")) raw else "https://www.youtube.com/watch?v=$raw"
}

/** The download affordance: one icon that reads the download state and acts on tap. */
@Composable
private fun DownloadStateIcon(
    downloadState: String?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, description) = when (downloadState) {
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
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onDownload)
            .padding(12.dp)
            .size(24.dp),
    )
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
    downloadState: String?,
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
                .graphicsLayer { alpha = dim },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = dim },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
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
        DownloadStateIcon(downloadState = downloadState, onDownload = onDownload)
        ToggleIconButton(
            filledIcon = Icons.Rounded.CheckCircle,
            outlinedIcon = Icons.Outlined.CheckCircle,
            active = watched,
            label = stringResource(R.string.cd_mark_watched),
            onToggle = onToggleWatched,
        )
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A toggle button whose icon springs between its filled and outlined form. */
@Composable
private fun ToggleIconButton(
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
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

/** Mirrors the title page: artwork, poster and title, play button, action tiles, overview. */
@Composable
private fun DetailsSkeleton() {
    SkeletonPulseProvider {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(540.dp)) {
                SkeletonBox(modifier = Modifier.fillMaxSize(), shape = null)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(112.dp, 166.dp),
                        shape = MaterialTheme.shapes.large,
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(190.dp, 30.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                        SkeletonBox(
                            modifier = Modifier.size(130.dp, 16.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = MaterialTheme.shapes.large,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        SkeletonBox(
                            modifier = Modifier.weight(1f).height(72.dp),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
                repeat(3) { index ->
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 2) 0.6f else 1f)
                            .height(16.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
    }
}
