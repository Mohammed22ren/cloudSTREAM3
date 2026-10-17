package com.kodastream.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.EpisodeQueue
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.ui.components.RemoteImage

/**
 * The episodes panel over the video: seasons as chips, episodes as rows with
 * their watched/downloaded marks, the current episode highlighted, and a
 * smooth slide in from the end side. Pure overlay - playback never pauses.
 */
@Composable
fun EpisodesPanel(
    queue: EpisodeQueue,
    currentIndex: Int,
    watchedKeys: Set<String>,
    historyEntry: HistoryStore.HistoryEntry?,
    downloads: List<DownloadStore.Download>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dark chrome over the video, like the player's own top bar.
    val panelColor = Color.Black.copy(alpha = 0.9f)
    val contentColor = Color.White

    var selectedSeason by remember {
        mutableStateOf(
            queue.episodes.getOrNull(currentIndex)?.season ?: queue.seasons.firstOrNull() ?: -1,
        )
    }
    val seasonEpisodes = remember(queue, selectedSeason) {
        queue.episodes.filter { (it.season ?: -1) == selectedSeason }
    }
    val listState = rememberLazyListState()

    // Land on the current episode the moment the panel opens.
    LaunchedEffect(selectedSeason) {
        val inSeason = seasonEpisodes.indexOfFirst { it.index == currentIndex }
        if (inSeason >= 0) listState.scrollToItem((inSeason - 2).coerceAtLeast(0))
    }

    Column(
        modifier = modifier
            .background(panelColor)
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = queue.title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = contentColor,
                )
            }
        }

        if (queue.seasons.size > 1) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(queue.seasons) { season ->
                    FilterChip(
                        selected = season == selectedSeason,
                        onClick = { selectedSeason = season },
                        label = {
                            Text(stringResource(R.string.season_n, season.takeIf { it != -1 } ?: 1))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            labelColor = Color.White,
                            selectedContainerColor = Color.White.copy(alpha = 0.28f),
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        ) {
            itemsIndexed(seasonEpisodes, key = { _, ep -> ep.index }) { _, episode ->
                val isCurrent = episode.index == currentIndex
                val isDownloaded = downloads.any {
                    it.apiName == queue.apiName && it.url == queue.url &&
                        it.season == episode.season && it.episode == episode.number &&
                        it.state == DownloadStore.STATE_DONE && it.fileUri != null
                }
                val isWatched = WatchedStore.keyOf(
                    queue.apiName, queue.url, episode.season, episode.number,
                ) in watchedKeys
                val historyFraction = historyEntry
                    ?.takeIf { it.season == episode.season && it.episode == episode.number && !it.isComplete }
                    ?.takeIf { it.durationMs > 0 }
                    ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
                // Offline lists only offer what is actually on the device.
                val disabled = queue.offlineOnly && !isDownloaded

                PanelEpisodeRow(
                    episode = episode,
                    posterUrl = episode.posterUrl ?: queue.posterUrl,
                    posterHeaders = queue.posterHeaders,
                    isCurrent = isCurrent,
                    isWatched = isWatched,
                    isDownloaded = isDownloaded,
                    progress = historyFraction,
                    disabled = disabled,
                    onClick = {
                        if (!disabled) {
                            if (isCurrent) onDismiss() else onPick(episode.index)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PanelEpisodeRow(
    episode: com.kodastream.app.data.QueueEpisode,
    posterUrl: String?,
    posterHeaders: Map<String, String>?,
    isCurrent: Boolean,
    isWatched: Boolean,
    isDownloaded: Boolean,
    progress: Float?,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isCurrent) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.4f else 1f)
            .clickable(enabled = !disabled, onClick = onClick),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RemoteImage(
                    url = posterUrl,
                    headers = posterHeaders,
                    modifier = Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "${episode.number}. ${episode.name ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.now_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isWatched) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.cd_watched),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = stringResource(R.string.downloaded),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (progress != null && progress > 0.01f && !isWatched) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}
