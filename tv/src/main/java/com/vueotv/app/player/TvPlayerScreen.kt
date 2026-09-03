package com.vueotv.app.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRanker
import com.vueo.shared.core.source.SubtitleCandidate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val PlayerBlack = Color(0xFF030403)
private val PlayerPanel = Color(0xF20A0D0B)
private val PlayerGreen = Color(0xFF84E100)
private val PlayerYellow = Color(0xFFD6FF00)
private val PlayerMuted = Color(0xFFAAB2AD)
private val PlayerDanger = Color(0xFFFFB4AB)

private enum class PlayerSidePanel {
    SOURCES,
    AUDIO,
    SUBTITLES,
}

@Composable
fun TvPlayerScreen(
    request: TvPlaybackRequest,
    initialSource: SourceCandidate,
    externalSubtitles: List<SubtitleCandidate>,
    sourceEngine: TvSourceEngine,
    playbackStore: TvPlaybackStore,
    onPlayRequest: (TvPlaybackRequest) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val httpFactory = remember(context, request.cacheKey) {
        DefaultHttpDataSource.Factory()
            .setUserAgent("VUEO-TV/0.7")
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember(context, request.cacheKey, httpFactory) {
        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    var sources by remember(request.cacheKey) { mutableStateOf<List<SourceCandidate>>(emptyList()) }
    var allSources by remember(request.cacheKey) { mutableStateOf<List<SourceCandidate>>(emptyList()) }
    var currentSource by remember(request.cacheKey) { mutableStateOf<SourceCandidate?>(null) }
    var sourceLoading by remember(request.cacheKey) { mutableStateOf(true) }
    var sourceNotice by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var sourceProgress by remember(request.cacheKey) { mutableStateOf("Finding sources…") }
    var playerError by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var isPlaying by remember(request.cacheKey) { mutableStateOf(false) }
    var isBuffering by remember(request.cacheKey) { mutableStateOf(false) }
    var positionMs by remember(request.cacheKey) { mutableLongStateOf(0L) }
    var durationMs by remember(request.cacheKey) { mutableLongStateOf(0L) }
    var controlsVisible by remember(request.cacheKey) { mutableStateOf(true) }
    var sidePanel by remember(request.cacheKey) { mutableStateOf<PlayerSidePanel?>(null) }
    var interactionToken by remember(request.cacheKey) { mutableIntStateOf(0) }
    var hasStartedPlayback by remember(request.cacheKey) { mutableStateOf(false) }
    var waitingForRecovery by remember(request.cacheKey) { mutableStateOf(false) }
    var audioLabel by remember(request.cacheKey) { mutableStateOf("Audio") }
    var subtitleLabel by remember(request.cacheKey) { mutableStateOf("Subtitles") }
    val failedSourceIds = remember(request.cacheKey) { mutableSetOf<String>() }

    val rewindRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val forwardRequester = remember { FocusRequester() }
    val audioRequester = remember { FocusRequester() }
    val subtitleRequester = remember { FocusRequester() }
    val sourcesRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val firstPanelRequester = remember { FocusRequester() }
    val problemRequester = remember { FocusRequester() }

    fun touchControls() {
        controlsVisible = true
        interactionToken += 1
    }

    fun playSource(
        source: SourceCandidate,
        resumeMs: Long = positionMs,
    ) {
        val sourceUrl = source.url ?: return
        httpFactory.setDefaultRequestProperties(source.headers)
        currentSource = source
        playerError = null
        waitingForRecovery = false
        player.setMediaItem(
            buildPlayerMediaItem(
                sourceUrl = sourceUrl,
                subtitles = externalSubtitles,
            ),
        )
        player.prepare()
        if (resumeMs > 0L) player.seekTo(resumeMs)
        player.playWhenReady = true
        hasStartedPlayback = true
        touchControls()
    }

    fun nextRecoveryCandidate(): SourceCandidate? =
        SourceRanker.automaticRecoveryCandidates(
            rankedSources = sources,
            attemptedIds = failedSourceIds,
            originalLanguage = request.originalLanguage,
        ).firstOrNull()

    fun recoverOrWait(message: String?) {
        currentSource?.let { failedSourceIds += it.id }
        val next = nextRecoveryCandidate()
        when {
            next != null -> playSource(next, positionMs)
            sourceLoading -> {
                waitingForRecovery = true
                playerError = null
                sourceProgress = "Trying another source…"
                touchControls()
            }
            else -> {
                waitingForRecovery = false
                playerError = message ?: "Playback failed and no alternate source is available."
                touchControls()
            }
        }
    }

    fun saveProgress() {
        playbackStore.save(
            request = request,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    playbackStore.clear(request)
                    controlsVisible = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioLabel = selectedTrackLabel(tracks, C.TRACK_TYPE_AUDIO, "Audio")
                subtitleLabel = selectedTrackLabel(tracks, C.TRACK_TYPE_TEXT, "Subtitles")
            }

            override fun onPlayerError(error: PlaybackException) {
                recoverOrWait(error.message)
            }
        }

        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler {
        if (sidePanel != null) {
            sidePanel = null
            touchControls()
        } else {
            saveProgress()
            onBack()
        }
    }

    LaunchedEffect(request.cacheKey) {
        player.stop()
        sourceLoading = true
        sourceNotice = null
        sourceProgress = "Finding sources…"
        sources = emptyList()
        allSources = emptyList()
        currentSource = null
        failedSourceIds.clear()
        hasStartedPlayback = false
        waitingForRecovery = false
        playerError = null
        positionMs = 0L
        durationMs = 0L
        sidePanel = null
        controlsVisible = true

        if (initialSource.isDirectPlayable) {
            sources = listOf(initialSource)
            allSources = listOf(initialSource)
            playSource(initialSource, playbackStore.resumePositionMs(request))
        }

        val discovery = sourceEngine.discoverProgressive(request) { progress ->
            withContext(Dispatchers.Main.immediate) {
                sources = mergePlayableSources(initialSource, progress.sources)
                allSources = mergeAllSources(initialSource, progress.allSources)
                sourceProgress =
                    "Sources ${progress.completedAddons}/${progress.totalAddons} • ${progress.sources.size} playable"

                val candidate = when {
                    !hasStartedPlayback -> progress.sources.firstOrNull()
                    waitingForRecovery -> nextRecoveryCandidate()
                    else -> null
                }

                if (candidate != null) {
                    val resume = if (!hasStartedPlayback) playbackStore.resumePositionMs(request) else positionMs
                    playSource(candidate, resume)
                }
            }
        }

        sources = mergePlayableSources(initialSource, discovery.sources)
        allSources = mergeAllSources(initialSource, discovery.allSources)
        sourceNotice = discovery.notice
        sourceLoading = false
        sourceProgress =
            if (discovery.sources.isNotEmpty()) {
                "${discovery.sources.size} sources • ${discovery.successfulAddons}/${discovery.attemptedAddons} addons"
            } else {
                discovery.notice ?: "No playable source"
            }

        if (!hasStartedPlayback && sources.isNotEmpty()) {
            playSource(sources.first(), playbackStore.resumePositionMs(request))
        } else if (waitingForRecovery) {
            val next = nextRecoveryCandidate()
            if (next != null) {
                playSource(next, positionMs)
            } else {
                waitingForRecovery = false
                playerError = "Playback failed and no alternate source is available."
            }
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            if (positionMs > 0L) saveProgress()
            delay(1_000)
        }
    }

    LaunchedEffect(controlsVisible, sidePanel, interactionToken, isPlaying, playerError) {
        if (controlsVisible && sidePanel == null && isPlaying && playerError == null) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, sidePanel, playerError) {
        delay(90)
        when {
            sidePanel != null -> runCatching { firstPanelRequester.requestFocus() }
            playerError != null -> runCatching { problemRequester.requestFocus() }
            controlsVisible -> runCatching { playRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PlayerBlack)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (event.key) {
                        Key.MediaPlayPause -> {
                            if (player.isPlaying) player.pause() else player.play()
                            touchControls()
                            true
                        }

                        Key.MediaRewind -> {
                            player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                            touchControls()
                            true
                        }

                        Key.MediaFastForward -> {
                            val target = if (durationMs > 0L) {
                                (player.currentPosition + SEEK_STEP_MS).coerceAtMost(durationMs)
                            } else {
                                player.currentPosition + SEEK_STEP_MS
                            }
                            player.seekTo(target)
                            touchControls()
                            true
                        }

                        Key.DirectionLeft -> {
                            if (!controlsVisible && sidePanel == null) {
                                player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                                touchControls()
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (!controlsVisible && sidePanel == null) {
                                val target = if (durationMs > 0L) {
                                    (player.currentPosition + SEEK_STEP_MS).coerceAtMost(durationMs)
                                } else {
                                    player.currentPosition + SEEK_STEP_MS
                                }
                                player.seekTo(target)
                                touchControls()
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            if (!controlsVisible && sidePanel == null) {
                                touchControls()
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (!hasStartedPlayback && sourceLoading) {
            LoadingPlayerState(
                title = request.displayTitle,
                message = sourceProgress,
            )
        }

        if (!sourceLoading && sources.isEmpty()) {
            EmptyPlayerState(
                title = request.displayTitle,
                message = sourceNotice ?: "No direct playable source was found.",
                requester = problemRequester,
                onBack = onBack,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && sidePanel == null && sources.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(
                request = request,
                currentSource = currentSource,
                sourceProgress = sourceProgress,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                waitingForRecovery = waitingForRecovery,
                positionMs = positionMs,
                durationMs = durationMs,
                rewindRequester = rewindRequester,
                playRequester = playRequester,
                forwardRequester = forwardRequester,
                audioRequester = audioRequester,
                subtitleRequester = subtitleRequester,
                sourcesRequester = sourcesRequester,
                nextRequester = nextRequester,
                audioLabel = audioLabel,
                subtitleLabel = subtitleLabel,
                onRewind = {
                    player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                    touchControls()
                },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                    touchControls()
                },
                onForward = {
                    val target = if (durationMs > 0L) {
                        (player.currentPosition + SEEK_STEP_MS).coerceAtMost(durationMs)
                    } else {
                        player.currentPosition + SEEK_STEP_MS
                    }
                    player.seekTo(target)
                    touchControls()
                },
                onAudio = {
                    sidePanel = PlayerSidePanel.AUDIO
                    interactionToken += 1
                },
                onSubtitles = {
                    sidePanel = PlayerSidePanel.SUBTITLES
                    interactionToken += 1
                },
                onSources = {
                    sidePanel = PlayerSidePanel.SOURCES
                    interactionToken += 1
                },
                onNext = request.nextRequest()?.let { next ->
                    {
                        saveProgress()
                        onPlayRequest(next)
                    }
                },
            )
        }

        when (sidePanel) {
            PlayerSidePanel.SOURCES ->
                SourcePickerPanel(
                    sources = allSources,
                    selected = currentSource,
                    firstRequester = firstPanelRequester,
                    originalLanguage = request.originalLanguage,
                    onSelect = { source ->
                        failedSourceIds.remove(source.id)
                        playSource(source, positionMs)
                        sidePanel = null
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            PlayerSidePanel.AUDIO ->
                TrackPickerPanel(
                    title = "Audio",
                    options = trackOptions(player.currentTracks, C.TRACK_TYPE_AUDIO, allowOff = false),
                    firstRequester = firstPanelRequester,
                    onSelect = { option ->
                        option.choice?.let { applyTrackChoice(player, C.TRACK_TYPE_AUDIO, it) }
                        audioLabel = selectedTrackLabel(player.currentTracks, C.TRACK_TYPE_AUDIO, "Audio")
                        sidePanel = null
                        touchControls()
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            PlayerSidePanel.SUBTITLES ->
                TrackPickerPanel(
                    title = "Subtitles",
                    options = trackOptions(player.currentTracks, C.TRACK_TYPE_TEXT, allowOff = true),
                    firstRequester = firstPanelRequester,
                    onSelect = { option ->
                        if (option.choice == null) {
                            disableTrackType(player, C.TRACK_TYPE_TEXT)
                        } else {
                            applyTrackChoice(player, C.TRACK_TYPE_TEXT, option.choice)
                        }
                        subtitleLabel = selectedTrackLabel(player.currentTracks, C.TRACK_TYPE_TEXT, "Subtitles")
                        sidePanel = null
                        touchControls()
                    },
                    onClose = {
                        sidePanel = null
                        touchControls()
                    },
                )

            null -> Unit
        }

        if (playerError != null && sidePanel == null && sources.isNotEmpty()) {
            PlaybackProblemPanel(
                message = playerError ?: "Playback problem",
                requester = problemRequester,
                onRetry = {
                    val retry = currentSource ?: sources.firstOrNull()
                    if (retry != null) {
                        failedSourceIds.remove(retry.id)
                        playSource(retry, positionMs)
                    }
                },
                onSources = {
                    sidePanel = PlayerSidePanel.SOURCES
                    interactionToken += 1
                },
            )
        }
    }
}

@Composable
private fun LoadingPlayerState(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PlayerGreen)
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = PlayerMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PlayerControls(
    request: TvPlaybackRequest,
    currentSource: SourceCandidate?,
    sourceProgress: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    waitingForRecovery: Boolean,
    positionMs: Long,
    durationMs: Long,
    rewindRequester: FocusRequester,
    playRequester: FocusRequester,
    forwardRequester: FocusRequester,
    audioRequester: FocusRequester,
    subtitleRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    nextRequester: FocusRequester,
    audioLabel: String,
    subtitleLabel: String,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onNext: (() -> Unit)?,
) {
    val assessment = currentSource?.let { SourceRanker.assess(it, originalLanguage = request.originalLanguage) }
    val sourceLine = when {
        waitingForRecovery -> "Trying another source…"
        assessment != null -> "${assessment.summary} • ${currentSource.providerName}"
        else -> sourceProgress
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.70f),
                            Color.Black.copy(alpha = 0.96f),
                        ),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.width(780.dp)) {
                    Text(
                        text = request.displayTitle,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sourceLine,
                        color = PlayerMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${formatTime(positionMs)}  /  ${formatTime(durationMs)}",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(14.dp))
            PlayerSeekBar(positionMs = positionMs, durationMs = durationMs)
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerButton("↶ 10", rewindRequester, onRewind)
                PlayerButton(
                    text = when {
                        isBuffering -> "Buffering…"
                        isPlaying -> "Pause"
                        else -> "Play"
                    },
                    requester = playRequester,
                    onClick = onPlayPause,
                    primary = true,
                )
                PlayerButton("10 ↷", forwardRequester, onForward)
                Spacer(Modifier.width(10.dp))
                PlayerButton(audioLabel, audioRequester, onAudio)
                PlayerButton(subtitleLabel, subtitleRequester, onSubtitles)
                PlayerButton("Sources", sourcesRequester, onSources)
                if (onNext != null) {
                    PlayerButton("Next", nextRequester, onNext)
                }
            }
        }
    }
}

@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
) {
    val fraction =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .background(PlayerGreen, RoundedCornerShape(999.dp)),
        )
        if (durationMs > 0L) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(Color.White, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PlayerButton(
    text: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) PlayerYellow else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.13f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourcePickerPanel(
    sources: List<SourceCandidate>,
    selected: SourceCandidate?,
    firstRequester: FocusRequester,
    originalLanguage: String?,
    onSelect: (SourceCandidate) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    RightPanel(title = "Sources", subtitle = "VUEO ranked for fast direct playback") {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                var focused by remember(source.id) { mutableStateOf(false) }
                val active = selected?.id == source.id
                val assessment = SourceRanker.assess(source, originalLanguage = originalLanguage)

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstRequester) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .background(
                                when {
                                    focused -> Color.White.copy(alpha = 0.16f)
                                    active -> PlayerGreen.copy(alpha = 0.10f)
                                    else -> Color.White.copy(alpha = 0.055f)
                                },
                                RoundedCornerShape(11.dp),
                            )
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = when {
                                    focused -> PlayerYellow
                                    active -> PlayerGreen.copy(alpha = 0.75f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(11.dp),
                            )
                            .then(
                                if (source.isDirectPlayable) {
                                    Modifier.clickable { onSelect(source) }.focusable()
                                } else {
                                    Modifier
                                },
                            )
                            .padding(14.dp),
                ) {
                    val sourceSummary = when {
                        source.isDirectPlayable -> assessment.summary
                        source.isTorrent -> "Torrent • debrid playback comes later"
                        source.url?.startsWith("http://") == true -> "HTTP • not direct-playable"
                        else -> "Not direct-playable"
                    }
                    Text(
                        text = if (index == 0 && source.isDirectPlayable) "Recommended • $sourceSummary" else sourceSummary,
                        color = if (source.isDirectPlayable) Color.White else PlayerMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = source.providerName,
                        color = PlayerGreen.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = source.name,
                        color = PlayerMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class TrackChoice(
    val group: Tracks.Group,
    val trackIndex: Int,
)

private data class TrackOption(
    val label: String,
    val secondary: String?,
    val selected: Boolean,
    val choice: TrackChoice?,
)

@Composable
private fun TrackPickerPanel(
    title: String,
    options: List<TrackOption>,
    firstRequester: FocusRequester,
    onSelect: (TrackOption) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    RightPanel(
        title = title,
        subtitle = if (options.isEmpty()) "No tracks available" else "Select with your remote",
    ) {
        if (options.isEmpty()) {
            Text(
                text = "No $title tracks are available in this source.",
                color = PlayerMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    var focused by remember(index, option.label) { mutableStateOf(false) }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstRequester) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .background(
                                    when {
                                        focused -> Color.White.copy(alpha = 0.16f)
                                        option.selected -> PlayerGreen.copy(alpha = 0.10f)
                                        else -> Color.White.copy(alpha = 0.055f)
                                    },
                                    RoundedCornerShape(11.dp),
                                )
                                .border(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = when {
                                        focused -> PlayerYellow
                                        option.selected -> PlayerGreen.copy(alpha = 0.75f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(11.dp),
                                )
                                .clickable { onSelect(option) }
                                .focusable()
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                    ) {
                        Text(
                            text = if (option.selected) "✓  ${option.label}" else option.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        option.secondary?.let { secondary ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = secondary,
                                color = PlayerMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RightPanel(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .width(470.dp)
                    .fillMaxHeight()
                    .background(PlayerPanel)
                    .padding(top = 36.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = subtitle,
                color = PlayerMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PlaybackProblemPanel(
    message: String,
    requester: FocusRequester,
    onRetry: () -> Unit,
    onSources: () -> Unit,
) {
    val sourcesRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(end = 34.dp)
                    .width(390.dp)
                    .background(PlayerPanel, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(22.dp),
        ) {
            Text(
                text = "Playback problem",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = PlayerMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerButton("Retry", requester, onRetry, primary = true)
                PlayerButton("Sources", sourcesRequester, onSources)
            }
        }
    }
}

@Composable
private fun EmptyPlayerState(
    title: String,
    message: String,
    requester: FocusRequester,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(90)
        runCatching { requester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(PlayerBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(720.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = PlayerMuted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(22.dp))
            PlayerButton("Back to details", requester, onBack, primary = true)
        }
    }
}

private fun trackChoices(
    tracks: Tracks,
    type: Int,
): List<TrackChoice> =
    tracks.groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length).map { index -> TrackChoice(group, index) }
        }

private fun trackOptions(
    tracks: Tracks,
    type: Int,
    allowOff: Boolean,
): List<TrackOption> {
    val choices = trackChoices(tracks, type)
    val result = mutableListOf<TrackOption>()

    if (allowOff) {
        val anySelected = choices.any { it.group.isTrackSelected(it.trackIndex) }
        result += TrackOption(
            label = "Off",
            secondary = null,
            selected = !anySelected,
            choice = null,
        )
    }

    choices.forEachIndexed { index, choice ->
        val format = choice.group.getTrackFormat(choice.trackIndex)
        val label =
            format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.takeIf { it.isNotBlank() }
                ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${index + 1}" else "Subtitle ${index + 1}"
        val secondary = buildList {
            format.language?.takeIf { it.isNotBlank() }?.let(::add)
            format.codecs?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct().joinToString(" • ").takeIf { it.isNotBlank() }

        result += TrackOption(
            label = label,
            secondary = secondary,
            selected = choice.group.isTrackSelected(choice.trackIndex),
            choice = choice,
        )
    }

    return result
}

private fun applyTrackChoice(
    player: Player,
    type: Int,
    choice: TrackChoice,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(
                TrackSelectionOverride(
                    choice.group.mediaTrackGroup,
                    choice.trackIndex,
                ),
            )
            .build()
}

private fun disableTrackType(
    player: Player,
    type: Int,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, true)
            .build()
}

private fun selectedTrackLabel(
    tracks: Tracks,
    type: Int,
    fallback: String,
): String {
    val selected = trackChoices(tracks, type).firstOrNull { choice ->
        choice.group.isTrackSelected(choice.trackIndex)
    } ?: return if (type == C.TRACK_TYPE_TEXT) "Subtitles Off" else fallback

    val format = selected.group.getTrackFormat(selected.trackIndex)
    val raw =
        format.label?.takeIf { it.isNotBlank() }
            ?: format.language?.takeIf { it.isNotBlank() }
            ?: fallback

    return if (type == C.TRACK_TYPE_AUDIO) "Audio • $raw" else "Subs • $raw"
}

private fun mergePlayableSources(
    initialSource: SourceCandidate,
    discovered: List<SourceCandidate>,
): List<SourceCandidate> =
    (listOf(initialSource) + discovered)
        .filter { it.isDirectPlayable }
        .distinctBy { it.url }

private fun mergeAllSources(
    initialSource: SourceCandidate,
    discovered: List<SourceCandidate>,
): List<SourceCandidate> =
    (listOf(initialSource) + discovered)
        .distinctBy { it.id }

private fun buildPlayerMediaItem(
    sourceUrl: String,
    subtitles: List<SubtitleCandidate>,
): MediaItem {
    val subtitleConfigurations =
        subtitles
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
            .map { subtitle ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setId(subtitle.id)
                    .setLabel(subtitle.name ?: subtitle.language.uppercase())
                    .setLanguage(subtitle.language)
                    .setMimeType(subtitleMimeType(subtitle.url))
                    .build()
            }

    return MediaItem.Builder()
        .setUri(Uri.parse(sourceUrl))
        .setSubtitleConfigurations(subtitleConfigurations)
        .build()
}

private fun subtitleMimeType(url: String): String =
    when (
        url.substringBefore("?")
            .substringAfterLast(".", "")
            .lowercase()
    ) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        "ttml", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private const val SEEK_STEP_MS = 10_000L
