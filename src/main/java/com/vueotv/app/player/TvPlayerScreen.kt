package com.vueotv.app.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val PlayerBlack = Color(0xFF030403)
private val PlayerPanel = Color(0xE6111412)
private val PlayerGreen = Color(0xFF84E100)
private val PlayerYellow = Color(0xFFD6FF00)
private val PlayerMuted = Color(0xFFAAB2AD)

@Composable
fun TvPlayerScreen(
    request: TvPlaybackRequest,
    sourceEngine: TvSourceEngine,
    playbackStore: TvPlaybackStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var sources by remember(request.cacheKey) { mutableStateOf<List<TvStreamSource>>(emptyList()) }
    var currentSource by remember(request.cacheKey) { mutableStateOf<TvStreamSource?>(null) }
    var sourceLoading by remember(request.cacheKey) { mutableStateOf(true) }
    var sourceNotice by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var sourceProgress by remember(request.cacheKey) { mutableStateOf("Finding sources…") }
    var playerError by remember(request.cacheKey) { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var sourcePickerVisible by remember { mutableStateOf(false) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var hasStartedPlayback by remember(request.cacheKey) { mutableStateOf(false) }
    var audioLabel by remember { mutableStateOf("Audio") }
    var subtitleLabel by remember { mutableStateOf("Subtitles") }
    val failedSourceIds = remember(request.cacheKey) { mutableSetOf<String>() }

    val backRequester = remember { FocusRequester() }
    val rewindRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val forwardRequester = remember { FocusRequester() }
    val sourcesRequester = remember { FocusRequester() }
    val audioRequester = remember { FocusRequester() }
    val subtitleRequester = remember { FocusRequester() }
    val firstSourceRequester = remember { FocusRequester() }

    fun touchControls() {
        controlsVisible = true
        interactionToken += 1
    }

    fun playSource(source: TvStreamSource, resumeMs: Long = positionMs) {
        currentSource = source
        playerError = null
        val item = MediaItem.fromUri(source.url)
        player.setMediaItem(item)
        player.prepare()
        if (resumeMs > 0L) player.seekTo(resumeMs)
        player.playWhenReady = true
        hasStartedPlayback = true
        touchControls()
    }

    fun tryRecovery(error: String?) {
        currentSource?.let { failedSourceIds += it.id }
        val next = sources.firstOrNull { it.id !in failedSourceIds }
        if (next != null) {
            playSource(next, positionMs)
        } else {
            playerError = error ?: "Playback failed and no alternate source is available."
            controlsVisible = true
        }
    }

    DisposableEffect(player, request.cacheKey) {
        val listener =
            object : Player.Listener {
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
                    subtitleLabel = selectedTrackLabel(tracks, C.TRACK_TYPE_TEXT, "Subtitles Off")
                }

                override fun onPlayerError(error: PlaybackException) {
                    tryRecovery(error.message)
                }
            }
        player.addListener(listener)
        onDispose {
            playbackStore.save(
                request = request,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler {
        if (sourcePickerVisible) {
            sourcePickerVisible = false
            touchControls()
        } else {
            playbackStore.save(request, player.currentPosition, player.duration)
            onBack()
        }
    }

    LaunchedEffect(request.cacheKey) {
        sourceLoading = true
        sourceNotice = null
        sourceProgress = "Finding sources…"
        sources = emptyList()
        currentSource = null
        failedSourceIds.clear()
        hasStartedPlayback = false

        val discovery =
            sourceEngine.discoverProgressive(request) { progress ->
                withContext(Dispatchers.Main.immediate) {
                    sources = progress.sources
                    sourceProgress =
                        "Sources ${progress.completedAddons}/${progress.totalAddons} • ${progress.sources.size} playable"
                    if (!hasStartedPlayback && progress.sources.isNotEmpty()) {
                        val resume = playbackStore.resumePositionMs(request)
                        playSource(progress.sources.first(), resume)
                    }
                }
            }

        sources = discovery.sources
        sourceNotice = discovery.notice
        sourceLoading = false
        sourceProgress =
            if (discovery.sources.isNotEmpty()) {
                "${discovery.sources.size} sources • ${discovery.successfulAddons}/${discovery.attemptedAddons} addons"
            } else {
                discovery.notice ?: "No playable source"
            }

        if (!hasStartedPlayback && discovery.sources.isNotEmpty()) {
            playSource(discovery.sources.first(), playbackStore.resumePositionMs(request))
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            if (positionMs > 0L) {
                playbackStore.save(request, positionMs, durationMs)
            }
            delay(1_000)
        }
    }

    LaunchedEffect(controlsVisible, sourcePickerVisible, interactionToken, isPlaying) {
        if (controlsVisible && !sourcePickerVisible && isPlaying) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, sourcePickerVisible) {
        delay(80)
        when {
            sourcePickerVisible && sources.isNotEmpty() -> runCatching { firstSourceRequester.requestFocus() }
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
                        Key.DirectionLeft -> {
                            if (!controlsVisible && !sourcePickerVisible) {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                touchControls()
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!controlsVisible && !sourcePickerVisible) {
                                player.seekTo(player.currentPosition + 10_000L)
                                touchControls()
                                true
                            } else false
                        }
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            if (!controlsVisible) {
                                touchControls()
                                true
                            } else false
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
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PlayerGreen)
                    Spacer(Modifier.height(18.dp))
                    Text(request.displayTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(sourceProgress, color = PlayerMuted, fontSize = 14.sp)
                }
            }
        }

        if (!sourceLoading && sources.isEmpty()) {
            EmptyPlayerState(
                title = request.displayTitle,
                message = sourceNotice ?: "No direct playable source was found.",
                requester = backRequester,
                onBack = onBack,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !sourcePickerVisible && sources.isNotEmpty(),
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
                positionMs = positionMs,
                durationMs = durationMs,
                error = playerError,
                backRequester = backRequester,
                rewindRequester = rewindRequester,
                playRequester = playRequester,
                forwardRequester = forwardRequester,
                sourcesRequester = sourcesRequester,
                audioRequester = audioRequester,
                subtitleRequester = subtitleRequester,
                audioLabel = audioLabel,
                subtitleLabel = subtitleLabel,
                onBack = {
                    playbackStore.save(request, player.currentPosition, player.duration)
                    onBack()
                },
                onRewind = {
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    touchControls()
                },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                    touchControls()
                },
                onForward = {
                    player.seekTo(player.currentPosition + 10_000L)
                    touchControls()
                },
                onSources = {
                    sourcePickerVisible = true
                    interactionToken += 1
                },
                onCycleAudio = {
                    cycleTrack(player, C.TRACK_TYPE_AUDIO, allowOff = false)
                    audioLabel = selectedTrackLabel(player.currentTracks, C.TRACK_TYPE_AUDIO, "Audio")
                    touchControls()
                },
                onCycleSubtitles = {
                    cycleTrack(player, C.TRACK_TYPE_TEXT, allowOff = true)
                    subtitleLabel = selectedTrackLabel(player.currentTracks, C.TRACK_TYPE_TEXT, "Subtitles Off")
                    touchControls()
                },
            )
        }

        if (sourcePickerVisible) {
            SourcePicker(
                sources = sources,
                selected = currentSource,
                firstRequester = firstSourceRequester,
                onSelect = { source ->
                    failedSourceIds.remove(source.id)
                    playSource(source, positionMs)
                    sourcePickerVisible = false
                },
                onClose = {
                    sourcePickerVisible = false
                    touchControls()
                },
            )
        }
    }
}

@Composable
private fun PlayerControls(
    request: TvPlaybackRequest,
    currentSource: TvStreamSource?,
    sourceProgress: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    error: String?,
    backRequester: FocusRequester,
    rewindRequester: FocusRequester,
    playRequester: FocusRequester,
    forwardRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    audioRequester: FocusRequester,
    subtitleRequester: FocusRequester,
    audioLabel: String,
    subtitleLabel: String,
    onBack: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onSources: () -> Unit,
    onCycleAudio: () -> Unit,
    onCycleSubtitles: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PlayerPanel)
                .padding(horizontal = 44.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.width(760.dp)) {
                Text(
                    text = request.displayTitle,
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = currentSource?.summary ?: sourceProgress,
                    color = PlayerMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${formatTime(positionMs)}  /  ${formatTime(durationMs)}",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(PlayerGreen),
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = Color(0xFFFFB4AB), fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerButton("Back", backRequester, onBack)
            PlayerButton("↶ 10", rewindRequester, onRewind)
            PlayerButton(if (isBuffering) "Buffering…" else if (isPlaying) "Pause" else "Play", playRequester, onPlayPause, primary = true)
            PlayerButton("10 ↷", forwardRequester, onForward)
            PlayerButton("Sources", sourcesRequester, onSources)
            PlayerButton(audioLabel, audioRequester, onCycleAudio)
            PlayerButton(subtitleLabel, subtitleRequester, onCycleSubtitles)
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
                    1.dp,
                    if (focused) PlayerYellow else Color.Transparent,
                    RoundedCornerShape(8.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.13f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun SourcePicker(
    sources: List<TvStreamSource>,
    selected: TvStreamSource?,
    firstRequester: FocusRequester,
    onSelect: (TvStreamSource) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .width(470.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0C0F0D))
                    .padding(top = 34.dp),
        ) {
            Text(
                text = "Sources",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = "Ranked for fast direct playback",
                color = PlayerMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                    var focused by remember(source.id) { mutableStateOf(false) }
                    val active = selected?.id == source.id
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
                                        else -> Color.White.copy(alpha = 0.06f)
                                    },
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    if (focused) PlayerYellow else if (active) PlayerGreen else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onSelect(source) }
                                .focusable()
                                .padding(14.dp),
                    ) {
                        Text(
                            text = if (index == 0) "★ ${source.summary}" else source.summary,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
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
}

@Composable
private fun EmptyPlayerState(
    title: String,
    message: String,
    requester: FocusRequester,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { requester.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(PlayerBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(720.dp)) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = PlayerMuted, fontSize = 15.sp)
            Spacer(Modifier.height(22.dp))
            PlayerButton("Back to details", requester, onBack, primary = true)
        }
    }
}

private data class TrackChoice(
    val group: Tracks.Group,
    val trackIndex: Int,
)

private fun trackChoices(tracks: Tracks, type: Int): List<TrackChoice> =
    tracks.groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length).map { index -> TrackChoice(group, index) }
        }

private fun cycleTrack(
    player: Player,
    type: Int,
    allowOff: Boolean,
) {
    val choices = trackChoices(player.currentTracks, type)
    if (choices.isEmpty()) return

    val selectedIndex =
        choices.indexOfFirst { choice ->
            choice.group.isTrackSelected(choice.trackIndex)
        }
    val builder = player.trackSelectionParameters.buildUpon()

    if (allowOff && selectedIndex == choices.lastIndex) {
        player.trackSelectionParameters =
            builder
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, true)
                .build()
        return
    }

    val nextIndex =
        when {
            selectedIndex < 0 -> 0
            else -> (selectedIndex + 1).coerceAtMost(choices.lastIndex)
        }
    val next = choices[nextIndex]
    player.trackSelectionParameters =
        builder
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(
                TrackSelectionOverride(next.group.mediaTrackGroup, next.trackIndex)
            )
            .build()
}

private fun selectedTrackLabel(
    tracks: Tracks,
    type: Int,
    fallback: String,
): String {
    val selected =
        trackChoices(tracks, type).firstOrNull { choice ->
            choice.group.isTrackSelected(choice.trackIndex)
        } ?: return fallback
    val format = selected.group.getTrackFormat(selected.trackIndex)
    val raw =
        format.label
            ?.takeIf { it.isNotBlank() }
            ?: format.language?.takeIf { it.isNotBlank() }
            ?: if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles"
    return if (type == C.TRACK_TYPE_AUDIO) "Audio • $raw" else "Subs • $raw"
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
