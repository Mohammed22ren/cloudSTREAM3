@file:OptIn(UnstableApi::class)

package com.kodastream.app.ui.player

import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.SubtitleStyleStore
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
import java.io.File
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

/** Draws [style] on the player's subtitle view, overriding embedded caption styling. */
private fun applySubtitleStyle(context: android.content.Context, view: PlayerView, style: SubtitleStyleStore.SubtitleStyle) {
    val subtitleView = view.subtitleView ?: return
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.fontSize)
    val typeface = style.fontFile
        ?.let { File(File(context.filesDir, "subtitle_fonts"), it) }
        ?.takeIf { it.exists() }
        ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
        ?: Typeface.SANS_SERIF
    subtitleView.setStyle(
        CaptionStyleCompat(
            style.textColor.toInt(),
            style.bgColor.toInt(),
            android.graphics.Color.TRANSPARENT,
            style.edgeType,
            style.edgeColor.toInt(),
            typeface,
        )
    )
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
    var showStyleSheet by remember { mutableStateOf(false) }
    var playbackFailed by remember { mutableStateOf(false) }

    var resizeMode by remember { mutableStateOf(PlayerView.RESIZE_MODE_FIT) }
    var speed by remember { mutableStateOf(AppPrefs.playbackSpeed(context)) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val subtitleStyle by SubtitleStyleStore.style.collectAsState(
        initial = remember { SubtitleStyleStore.current(context) }
    )

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val resumeKey = viewModel.args.resumeKey

    // The subtitle style is applied the moment it changes - also while the
    // style sheet is open, so moving a slider shows on the video behind it.
    LaunchedEffect(subtitleStyle) {
        playerViewRef?.let { applySubtitleStyle(context, it, subtitleStyle) }
    }
    LaunchedEffect(resizeMode) {
        playerViewRef?.resizeMode = resizeMode
    }
    LaunchedEffect(speed) {
        exoPlayer.setPlaybackSpeed(speed)
    }

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
                    resizeMode = PlayerView.RESIZE_MODE_FIT
                    controllerShowTimeoutMs = 3_000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                        }
                    )
                    applySubtitleStyle(ctx, this, subtitleStyle)
                    playerViewRef = this
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
                IconButton(onClick = {
                    resizeMode = when (resizeMode) {
                        PlayerView.RESIZE_MODE_FIT -> PlayerView.RESIZE_MODE_ZOOM
                        PlayerView.RESIZE_MODE_ZOOM -> PlayerView.RESIZE_MODE_FILL
                        else -> PlayerView.RESIZE_MODE_FIT
                    }
                }) {
                    Icon(
                        Icons.Rounded.AspectRatio,
                        contentDescription = stringResource(R.string.cd_resize),
                        tint = Color.White,
                    )
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
            speed = speed,
            onSpeed = {
                speed = it
                AppPrefs.setPlaybackSpeed(context, it)
            },
            onSelectLink = {
                viewModel.selectLink(it)
                showSources = false
            },
            onSelectSubtitle = {
                viewModel.selectSubtitle(it)
                showSources = false
            },
            onOpenStyle = {
                showSources = false
                showStyleSheet = true
            },
            onDismiss = { showSources = false },
        )
    }

    if (showStyleSheet) {
        SubtitleStyleSheet(onDismiss = { showStyleSheet = false })
    }
}

@Composable
private fun SourcesSheet(
    links: List<ExtractorLink>,
    subtitles: List<SubtitleFile>,
    selectedLink: ExtractorLink?,
    selectedSubtitle: SubtitleFile?,
    resolving: Boolean,
    speed: Float,
    onSpeed: (Float) -> Unit,
    onSelectLink: (ExtractorLink) -> Unit,
    onSelectSubtitle: (SubtitleFile?) -> Unit,
    onOpenStyle: () -> Unit,
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
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    text = stringResource(R.string.playback_speed),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { value ->
                        FilterChip(
                            selected = speed == value,
                            onClick = { onSpeed(value) },
                            label = { Text(if (value == 1f) "1x" else "${value}x") },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStyle)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.subtitles_style),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
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

/**
 * The subtitle look, edited live: the preview mirrors the style, and every
 * change lands on the video behind the sheet the moment it is made. The
 * same knobs CloudStream's player exposes: size, text and background
 * colour, edge, and font - including fonts imported from the device.
 */
@Composable
private fun SubtitleStyleSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val style by SubtitleStyleStore.style.collectAsState(
        initial = remember { SubtitleStyleStore.current(context) }
    )
    val fonts = remember(style) { SubtitleStyleStore.fonts(context) }

    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            SubtitleStyleStore.importFont(context, uri)?.let { name ->
                SubtitleStyleStore.update(context) { it.copy(fontFile = name) }
            }
        }
    }

    // Local slider state so dragging does not hammer the store on every frame.
    var pendingSize by remember(style) { mutableStateOf(style.fontSize) }

    val previewTypeface = style.fontFile
        ?.let { File(File(context.filesDir, "subtitle_fonts"), it) }
        ?.takeIf { it.exists() }
        ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.subtitles_style),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            // ---- preview ----
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(0xFF202020))
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val shadow = when (style.edgeType) {
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        CaptionStyleCompat.EDGE_TYPE_RAISED,
                        -> Shadow(
                            color = Color(style.edgeColor.toInt()),
                            elevation = if (style.edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED) 8f else 4f,
                        )

                        else -> Shadow(Color.Transparent, 0f)
                    }
                    Text(
                        text = stringResource(R.string.subtitle_preview),
                        color = Color(style.textColor.toInt()),
                        fontSize = (16 * style.fontSize).sp,
                        fontFamily = previewTypeface?.let { FontFamily(it) },
                        style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = shadow),
                        modifier = if ((style.bgColor.toInt() and 0xFF000000.toInt()) != 0) {
                            Modifier.background(Color(style.bgColor.toInt()))
                        } else {
                            Modifier
                        },
                    )
                }
            }

            // ---- size ----
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.subtitle_size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(pendingSize * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = pendingSize,
                        onValueChange = { pendingSize = it },
                        onValueChangeFinished = {
                            val rounded = (pendingSize * 20f).toInt() / 20f
                            SubtitleStyleStore.update(context) { it.copy(fontSize = rounded) }
                        },
                        valueRange = 0.5f..2f,
                    )
                }
            }

            // ---- colors ----
            item {
                SwatchRow(
                    label = stringResource(R.string.subtitle_text_color),
                    swatches = listOf(0xFFFFFFFF, 0xFFFFFF00, 0xFF00FFFF, 0xFF00FF00, 0xFFFF6060, 0xFF000000),
                    selected = style.textColor,
                ) { color -> SubtitleStyleStore.update(context) { it.copy(textColor = color) } }
            }
            item {
                SwatchRow(
                    label = stringResource(R.string.subtitle_bg_color),
                    swatches = listOf(0x00000000, 0x66000000, 0xFF000000, 0xFFFFFFFF),
                    selected = style.bgColor,
                ) { color -> SubtitleStyleStore.update(context) { it.copy(bgColor = color) } }
            }
            item {
                SwatchRow(
                    label = stringResource(R.string.subtitle_edge_color),
                    swatches = listOf(0xFF000000, 0xFFFFFFFF, 0xFFFFFF00),
                    selected = style.edgeColor,
                ) { color -> SubtitleStyleStore.update(context) { it.copy(edgeColor = color) } }
            }

            // ---- edge ----
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.subtitle_edge),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_NONE,
                                onClick = { SubtitleStyleStore.update(context) { it.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_NONE) } },
                                label = { Text(stringResource(R.string.edge_none)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                onClick = { SubtitleStyleStore.update(context) { it.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE) } },
                                label = { Text(stringResource(R.string.edge_outline)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                onClick = { SubtitleStyleStore.update(context) { it.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) } },
                                label = { Text(stringResource(R.string.edge_shadow)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED,
                                onClick = { SubtitleStyleStore.update(context) { it.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_RAISED) } },
                                label = { Text(stringResource(R.string.edge_raised)) },
                            )
                        }
                    }
                }
            }

            // ---- font ----
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.subtitle_font),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = style.fontFile == null,
                                onClick = { SubtitleStyleStore.update(context) { it.copy(fontFile = null) } },
                                label = { Text(stringResource(R.string.font_default)) },
                            )
                        }
                        items(fonts) { name ->
                            FilterChip(
                                selected = style.fontFile == name,
                                onClick = { SubtitleStyleStore.update(context) { it.copy(fontFile = name) } },
                                label = { Text(name.removeSuffix(".ttf").removeSuffix(".otf").substringBeforeLast('_')) },
                            )
                        }
                    }
                }
            }
            item {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { importFont.launch(arrayOf("font/*", "application/octet-stream")) },
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.Add,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.font_import))
                }
            }
            item {
                androidx.compose.material3.TextButton(
                    onClick = { SubtitleStyleStore.reset(context) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.subtitle_reset))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** A label plus a row of colour circles, one of which is selected. */
@Composable
private fun SwatchRow(
    label: String,
    swatches: List<Long>,
    selected: Long,
    onPick: (Long) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(swatches) { color ->
                val isSelected = color == selected
                val isTransparent = (color.toInt() and 0xFF000000.toInt()) == 0
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(if (isTransparent) Color.Transparent else Color(color.toInt()))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isTransparent) {
                        Text(
                            text = stringResource(R.string.none),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
