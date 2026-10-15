@file:OptIn(UnstableApi::class)

package com.kodastream.app.ui.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.LoadingBox
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(val args: PlayerArgs) : ViewModel() {
    val api: MainAPI? = Engine.provider(args.apiName)

    private val _links = MutableStateFlow<List<ExtractorLink>>(emptyList())
    val links: StateFlow<List<ExtractorLink>> = _links.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val subtitles: StateFlow<List<SubtitleFile>> = _subtitles.asStateFlow()

    private val _selectedLink = MutableStateFlow<ExtractorLink?>(null)
    val selectedLink: StateFlow<ExtractorLink?> = _selectedLink.asStateFlow()

    private val _selectedSubtitle = MutableStateFlow<SubtitleFile?>(null)
    val selectedSubtitle: StateFlow<SubtitleFile?> = _selectedSubtitle.asStateFlow()

    /** True while the provider is still looking for sources. */
    private val _resolving = MutableStateFlow(true)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    /** Null when fine, blank when the provider finished without any link, otherwise a message. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val failedUrls = HashSet<String>()
    private var job: Job? = null

    init {
        resolve()
    }

    fun resolve() {
        job?.cancel()
        _links.value = emptyList()
        _subtitles.value = emptyList()
        _selectedLink.value = null
        _selectedSubtitle.value = null
        _error.value = null
        failedUrls.clear()

        job = viewModelScope.launch {
            _resolving.value = true
            val provider = api
            if (provider == null) {
                _error.value = "Provider \"${args.apiName}\" is not loaded"
                _resolving.value = false
                return@launch
            }
            val result = Engine.guarded(provider.loadLinksTimeoutMs ?: 120_000L) {
                provider.loadLinks(
                    args.data,
                    false,
                    { subtitle -> addSubtitle(subtitle) },
                    { link -> addLink(link) },
                )
            }
            _resolving.value = false
            if (_links.value.isEmpty()) {
                _error.value = result.exceptionOrNull()?.readableMessage() ?: ""
            }
        }
    }

    // These two are called by extension code, possibly from several threads at once.
    private fun addLink(link: ExtractorLink) {
        // Torrents and magnets need a torrent engine, which this app does not have.
        if (link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) return
        _links.update { current -> if (current.any { it.url == link.url }) current else current + link }
        _selectedLink.compareAndSet(null, link)
    }

    private fun addSubtitle(subtitle: SubtitleFile) {
        _subtitles.update { current -> if (current.any { it.url == subtitle.url }) current else current + subtitle }
    }

    fun selectLink(link: ExtractorLink) {
        _selectedLink.value = link
    }

    fun selectSubtitle(subtitle: SubtitleFile?) {
        _selectedSubtitle.value = subtitle
    }

    /** The current link failed to play. Moves to the next untried one; false when none is left. */
    fun onPlaybackError(): Boolean {
        val current = _selectedLink.value ?: return false
        failedUrls.add(current.url)
        val next = _links.value.firstOrNull { it.url !in failedUrls } ?: return false
        _selectedLink.value = next
        return true
    }
}

private fun subtitleMimeType(url: String): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        path.endsWith(".ttml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.TEXT_VTT
    }
}

private fun buildMediaSource(link: ExtractorLink, api: MainAPI?, subtitle: SubtitleFile?): MediaSource {
    val client = app.baseClient.newBuilder().apply {
        // Some providers need to sign or rewrite every request the player makes.
        api?.getVideoInterceptor(link)?.let { addInterceptor(it) }
    }.build()

    val headers = link.getAllHeaders()
    val dataSourceFactory = OkHttpDataSource.Factory(client).apply {
        setDefaultRequestProperties(headers)
        if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) setUserAgent(USER_AGENT)
    }

    val item = MediaItem.Builder()
        .setUri(link.url)
        .apply {
            when (link.type) {
                ExtractorLinkType.M3U8 -> setMimeType(MimeTypes.APPLICATION_M3U8)
                ExtractorLinkType.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
                else -> Unit
            }
            if (subtitle != null) {
                setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(subtitleMimeType(subtitle.url))
                            .setLanguage(subtitle.langTag ?: subtitle.lang)
                            .setLabel(subtitle.lang)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                )
            }
        }
        .build()

    return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
}

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.getActivity() }
    val view = LocalView.current

    val links by viewModel.links.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val selectedLink by viewModel.selectedLink.collectAsState()
    val selectedSubtitle by viewModel.selectedSubtitle.collectAsState()
    val resolving by viewModel.resolving.collectAsState()
    val error by viewModel.error.collectAsState()

    var controllerVisible by remember { mutableStateOf(true) }
    var showSources by remember { mutableStateOf(false) }
    var playbackFailed by remember { mutableStateOf(false) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val resumeKey = viewModel.args.resumeKey

    // Landscape and no system bars while the player is open; everything is put back afterwards.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Pause when the app goes to the background.
    DisposableEffect(activity) {
        val lifecycle = (activity as? AppCompatActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Try the next source before giving up.
                if (!viewModel.onPlaybackError()) playbackFailed = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackFailed = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            val position = exoPlayer.currentPosition
            if (exoPlayer.mediaItemCount > 0 && position > 0) {
                AppPrefs.setPosition(context, resumeKey, position)
            }
            exoPlayer.release()
        }
    }

    // (Re)start playback whenever the chosen source or subtitle changes.
    LaunchedEffect(selectedLink, selectedSubtitle) {
        val link = selectedLink ?: return@LaunchedEffect
        playbackFailed = false
        val position = if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentPosition
        } else {
            AppPrefs.position(context, resumeKey).takeIf { it > 5_000L } ?: 0L
        }
        exoPlayer.setMediaSource(buildMediaSource(link, viewModel.api, selectedSubtitle))
        exoPlayer.prepare()
        if (position > 0L) exoPlayer.seekTo(position)
        exoPlayer.playWhenReady = true
    }

    // Remember where playback is, so closing the app does not lose the place.
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(5_000L)
            if (exoPlayer.isPlaying) AppPrefs.setPosition(context, resumeKey, exoPlayer.currentPosition)
        }
    }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 3_000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                        }
                    )
                }
            },
        )

        when {
            selectedLink == null && resolving -> LoadingBox()

            selectedLink == null -> ErrorBox(
                message = error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_sources),
                onRetry = viewModel::resolve,
            )

            playbackFailed -> ErrorBox(
                message = stringResource(R.string.playback_failed),
                onRetry = { showSources = true },
                retryLabel = stringResource(R.string.sources),
            )
        }

        AnimatedVisibility(
            visible = controllerVisible || selectedLink == null || playbackFailed,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .displayCutoutPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = viewModel.args.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    viewModel.args.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (links.isNotEmpty() || subtitles.isNotEmpty()) {
                    IconButton(onClick = { showSources = true }) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.sources),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (showSources) {
        SourcesSheet(
            links = links,
            subtitles = subtitles,
            selectedLink = selectedLink,
            selectedSubtitle = selectedSubtitle,
            resolving = resolving,
            onSelectLink = {
                viewModel.selectLink(it)
                showSources = false
            },
            onSelectSubtitle = {
                viewModel.selectSubtitle(it)
                showSources = false
            },
            onDismiss = { showSources = false },
        )
    }
}

@Composable
private fun SourcesSheet(
    links: List<ExtractorLink>,
    subtitles: List<SubtitleFile>,
    selectedLink: ExtractorLink?,
    selectedSubtitle: SubtitleFile?,
    resolving: Boolean,
    onSelectLink: (ExtractorLink) -> Unit,
    onSelectSubtitle: (SubtitleFile?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.sources),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(links.sortedByDescending { it.quality }) { link ->
                val quality = Qualities.getStringByInt(link.quality)
                ChoiceRow(
                    selected = link.url == selectedLink?.url,
                    label = if (quality.isBlank()) link.name else "${link.name}  $quality",
                    onClick = { onSelectLink(link) },
                )
            }
            if (resolving) {
                item {
                    Text(
                        text = stringResource(R.string.still_searching),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            if (subtitles.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.subtitles),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                item {
                    ChoiceRow(
                        selected = selectedSubtitle == null,
                        label = stringResource(R.string.subtitles_off),
                        onClick = { onSelectSubtitle(null) },
                    )
                }
                items(subtitles) { subtitle ->
                    ChoiceRow(
                        selected = subtitle.url == selectedSubtitle?.url,
                        label = subtitle.lang,
                        onClick = { onSelectSubtitle(subtitle) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
