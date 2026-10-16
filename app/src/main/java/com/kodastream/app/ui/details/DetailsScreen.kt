package com.kodastream.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.data.LibraryStore.LibraryItem
import com.kodastream.app.data.PlayerArgs
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
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
private fun LibraryToggles(response: LoadResponse) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val revision by LibraryStore.revision.collectAsState()

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

    val title = response.name.ifBlank { fallbackTitle.orEmpty() }
    val plain = remember(response) {
        response.plot?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    }
    var plotExpanded by remember(response) { mutableStateOf(false) }
    val directData = directDataOf(response)

    fun play(data: String, subtitle: String?, episode: Episode? = null) {
        val resumeKey = response.apiName + "|" + data
        onPlay(
            PlayerArgs(
                apiName = response.apiName,
                data = data,
                title = title,
                subtitle = subtitle,
                resumeKey = resumeKey,
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

                LibraryToggles(response = response)

                if (directData != null) {
                    Button(
                        onClick = { play(directData, null) },
                        enabled = !response.comingSoon && directData.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (response.comingSoon) R.string.coming_soon else R.string.play
                            )
                        )
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
            item(key = "episodes-title") { SectionHeader(stringResource(R.string.episodes)) }
            itemsIndexed(visibleEpisodes) { index, episode ->
                // A composable call, so it is made here and not inside the click handler.
                val label = episodeLabel(episode, index)
                EpisodeRow(
                    episode = episode,
                    index = index,
                    posterHeaders = response.posterHeaders,
                    onClick = { play(episode.data, label, episode) },
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
    onClick: () -> Unit,
) {
    val number = episode.episode ?: (index + 1)
    val title = episode.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.episode_n, number)
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
                .clip(MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.play),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
