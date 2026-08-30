package com.vueo.app.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.Locale
import kotlin.math.roundToInt

internal data class PlayerSubtitleStyleState(
    val fontSizeSp: Int = 20,
    val bold: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val outlineEnabled: Boolean = true,
    val outlineColor: Int = 0xFF000000.toInt(),
    val bottomPaddingPercent: Int = 22,
)

private data class SubtitleLanguageGroup(
    val code: String,
    val label: String,
    val tracks: List<PlayerTrackChoice>,
)

private val SubtitleAccent = Color(0xFFB9FF3A)
private val SubtitleItemShape = RoundedCornerShape(13.dp)

@Composable
internal fun PlayerSubtitleWorkspace(
    tracks: List<PlayerTrackChoice>,
    subtitlesDisabled: Boolean,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    subtitleDelayMs: Int,
    style: PlayerSubtitleStyleState,
    onDisable: () -> Unit,
    onSelect: (PlayerTrackChoice) -> Unit,
    onOpenStyle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember(
        tracks,
        preferredLanguageCode,
        secondaryLanguageCode,
    ) {
        buildSubtitleLanguageGroups(
            tracks = tracks,
            preferredLanguageCode = preferredLanguageCode,
            secondaryLanguageCode = secondaryLanguageCode,
        )
    }
    val selectedTrack = tracks.firstOrNull { it.selected }
    var activeLanguageCode by remember(groups, selectedTrack?.selectionId) {
        mutableStateOf(
            selectedTrack?.language
                ?.let(::canonicalSubtitleLanguage)
                ?: preferredLanguageCode
                    ?.let(::canonicalSubtitleLanguage)
                    ?.takeIf { preferred ->
                        groups.any { it.code == preferred }
                    }
                ?: groups.firstOrNull()?.code
        )
    }
    val visibleTracks = groups
        .firstOrNull { it.code == activeLanguageCode }
        ?.tracks
        .orEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepSubtitleDialogImmersive()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .28f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .96f),
                        .46f to Color.Black.copy(alpha = .70f),
                        1f to Color.Black.copy(alpha = .12f),
                    )
                )
                .padding(start = 34.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).size(38.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close subtitles",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(Modifier.fillMaxHeight()) {
                Text(
                    "Subtitles",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SubtitleColumn("Languages", 142.dp) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            item {
                                LanguageRow(
                                    label = "Off",
                                    count = null,
                                    selected = subtitlesDisabled,
                                    onClick = onDisable,
                                )
                            }
                            items(groups, key = { it.code }) { group ->
                                LanguageRow(
                                    label = group.label,
                                    count = group.tracks.size,
                                    selected = !subtitlesDisabled &&
                                        group.code == activeLanguageCode,
                                    onClick = { activeLanguageCode = group.code },
                                )
                            }
                        }
                    }

                    SubtitleColumn("Subtitles", 292.dp) {
                        when {
                            visibleTracks.isNotEmpty() -> {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(visibleTracks, key = { it.key }) { track ->
                                        SubtitleTrackRow(
                                            track = track,
                                            selected = !subtitlesDisabled && track.selected,
                                            onClick = { onSelect(track) },
                                        )
                                    }
                                }
                            }
                            groups.isEmpty() -> SubtitleEmptyState(
                                "No subtitles available. Try another source or install a subtitle addon."
                            )
                            else -> SubtitleEmptyState(
                                "Choose a language to view its subtitles."
                            )
                        }
                    }

                    if (selectedTrack != null && !subtitlesDisabled) {
                        SubtitleStyleLauncherColumn(
                            subtitleDelayMs = subtitleDelayMs,
                            style = style,
                            onOpenStyle = onOpenStyle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleColumn(
    title: String,
    width: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Column(Modifier.width(width).fillMaxHeight()) {
        Text(
            title,
            color = Color.White.copy(alpha = .74f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(7.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) SubtitleAccent.copy(alpha = .15f) else Color.Transparent,
                SubtitleItemShape,
            )
            .border(
                1.dp,
                if (selected) SubtitleAccent.copy(alpha = .58f) else Color.Transparent,
                SubtitleItemShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = if (selected) .98f else .86f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        count?.let {
            Surface(
                color = if (selected) SubtitleAccent.copy(alpha = .14f)
                else Color.White.copy(alpha = .90f),
                shape = CircleShape,
            ) {
                Text(
                    it.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    color = if (selected) SubtitleAccent else Color(0xFF202124),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SubtitleTrackRow(
    track: PlayerTrackChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val foreground = Color.White.copy(alpha = if (selected) .98f else .86f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) SubtitleAccent.copy(alpha = .15f) else Color.Black.copy(alpha = .20f),
                SubtitleItemShape,
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) SubtitleAccent.copy(alpha = .58f) else Color.White.copy(alpha = .08f),
                ),
                SubtitleItemShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderBadge(track.sourceLabel, selected)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = SubtitleAccent,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            track.label,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${track.metadata ?: friendlySubtitleLanguageName(track.language)} (${track.sourceLabel})",
            color = foreground.copy(alpha = .58f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProviderBadge(label: String, selected: Boolean) {
    Surface(
        color = if (selected) SubtitleAccent.copy(alpha = .12f)
        else Color.White.copy(alpha = .11f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (selected) SubtitleAccent.copy(alpha = .24f)
            else Color.White.copy(alpha = .12f),
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            color = if (selected) SubtitleAccent.copy(alpha = .90f)
            else Color.White.copy(alpha = .68f),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubtitleStyleLauncherColumn(
    subtitleDelayMs: Int,
    style: PlayerSubtitleStyleState,
    onOpenStyle: () -> Unit,
) {
    SubtitleColumn("Style", 232.dp) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenStyle),
            shape = SubtitleItemShape,
            color = SubtitleAccent.copy(alpha = .10f),
            border = BorderStroke(
                1.dp,
                SubtitleAccent.copy(alpha = .36f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "Live style editor",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOf(
                        "${style.fontSizeSp}sp",
                        if (style.bold) "Bold" else "Regular",
                        formatSubtitleDelay(subtitleDelayMs),
                    ).joinToString(" • "),
                    color = Color.White.copy(alpha = .56f),
                    fontSize = 9.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Tap to open floating editor",
                    color = SubtitleAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun PlayerSubtitleStyleOverlay(
    subtitleDelayMs: Int,
    style: PlayerSubtitleStyleState,
    onSubtitleDelayChange: (Int) -> Unit,
    onStyleChange: (PlayerSubtitleStyleState) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        KeepSubtitleDialogImmersive()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        ) {
            val density = LocalDensity.current
            val outerPaddingPx = with(density) { 18.dp.roundToPx() }
            var containerSize by remember { mutableStateOf(IntSize.Zero) }
            var panelSize by remember { mutableStateOf(IntSize.Zero) }
            var panelOffset by remember { mutableStateOf(IntOffset.Zero) }
            var positionInitialised by remember { mutableStateOf(false) }

            LaunchedEffect(containerSize, panelSize) {
                if (
                    !positionInitialised &&
                    containerSize.width > 0 &&
                    containerSize.height > 0 &&
                    panelSize.width > 0 &&
                    panelSize.height > 0
                ) {
                    panelOffset = IntOffset(
                        x = (containerSize.width - panelSize.width - outerPaddingPx)
                            .coerceAtLeast(0),
                        y = outerPaddingPx.coerceAtMost(
                            (containerSize.height - panelSize.height).coerceAtLeast(0)
                        ),
                    )
                    positionInitialised = true
                }
            }

            val maxPanelHeight = (maxHeight - 24.dp).coerceAtLeast(220.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it },
            ) {
                Surface(
                    modifier = Modifier
                        .width(292.dp)
                        .heightIn(max = maxPanelHeight)
                        .offset { panelOffset }
                        .onSizeChanged { panelSize = it }
                        .clickable(onClick = {})
                        .pointerInput(containerSize, panelSize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val maxX = (containerSize.width - panelSize.width)
                                    .coerceAtLeast(0)
                                val maxY = (containerSize.height - panelSize.height)
                                    .coerceAtLeast(0)
                                panelOffset = IntOffset(
                                    x = (panelOffset.x + dragAmount.x.roundToInt())
                                        .coerceIn(0, maxX),
                                    y = (panelOffset.y + dragAmount.y.roundToInt())
                                        .coerceIn(0, maxY),
                                )
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF016181A),
                    border = BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = .13f),
                    ),
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Subtitle Style",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Drag panel • tap outside to close",
                                    color = Color.White.copy(alpha = .46f),
                                    fontSize = 9.sp,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SubtitleAccent, CircleShape),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SubtitleStyleControls(
                            subtitleDelayMs = subtitleDelayMs,
                            style = style,
                            onSubtitleDelayChange = onSubtitleDelayChange,
                            onStyleChange = onStyleChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleStyleControls(
    subtitleDelayMs: Int,
    style: PlayerSubtitleStyleState,
    onSubtitleDelayChange: (Int) -> Unit,
    onStyleChange: (PlayerSubtitleStyleState) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            StyleStepper(
                label = "Sync",
                value = formatSubtitleDelay(subtitleDelayMs),
                onDecrease = {
                    onSubtitleDelayChange(
                        (subtitleDelayMs - 250).coerceAtLeast(-60_000)
                    )
                },
                onIncrease = {
                    onSubtitleDelayChange(
                        (subtitleDelayMs + 250).coerceAtMost(60_000)
                    )
                },
            )
        }
        item {
            StyleStepper(
                label = "Font Size",
                value = "${style.fontSizeSp}sp",
                onDecrease = {
                    onStyleChange(
                        style.copy(
                            fontSizeSp = (style.fontSizeSp - 2).coerceAtLeast(12)
                        )
                    )
                },
                onIncrease = {
                    onStyleChange(
                        style.copy(
                            fontSizeSp = (style.fontSizeSp + 2).coerceAtMost(40)
                        )
                    )
                },
            )
        }
        item {
            StyleToggle("Bold", style.bold) {
                onStyleChange(style.copy(bold = !style.bold))
            }
        }
        item {
            StyleColorPicker(
                label = "Text Color",
                selected = style.textColor,
                colours = listOf(
                    0xFFFFFFFF.toInt(),
                    0xFFFFFF66.toInt(),
                    0xFF66E7FF.toInt(),
                    0xFFB9FF3A.toInt(),
                    0xFFFF6577.toInt(),
                ),
                onSelect = { colour ->
                    val alpha = style.textColor ushr 24
                    onStyleChange(
                        style.copy(
                            textColor = (alpha shl 24) or
                                (colour and 0x00FFFFFF)
                        )
                    )
                },
            )
        }
        item {
            val opacity = ((style.textColor ushr 24) * 100 + 127) / 255
            StyleStepper(
                label = "Text Opacity",
                value = "$opacity%",
                onDecrease = {
                    onStyleChange(
                        style.copy(
                            textColor = withAlpha(
                                style.textColor,
                                (opacity - 10).coerceAtLeast(30),
                            )
                        )
                    )
                },
                onIncrease = {
                    onStyleChange(
                        style.copy(
                            textColor = withAlpha(
                                style.textColor,
                                (opacity + 10).coerceAtMost(100),
                            )
                        )
                    )
                },
            )
        }
        item {
            StyleToggle("Outline", style.outlineEnabled) {
                onStyleChange(
                    style.copy(outlineEnabled = !style.outlineEnabled)
                )
            }
        }
        if (style.outlineEnabled) {
            item {
                StyleColorPicker(
                    label = "Outline Color",
                    selected = style.outlineColor,
                    colours = listOf(
                        0xFF000000.toInt(),
                        0xFFFFFFFF.toInt(),
                        0xFF38E8F2.toInt(),
                        0xFFFF6577.toInt(),
                    ),
                    onSelect = {
                        onStyleChange(style.copy(outlineColor = it))
                    },
                )
            }
        }
        item {
            StyleStepper(
                label = "Bottom Offset",
                value = "${style.bottomPaddingPercent}%",
                onDecrease = {
                    onStyleChange(
                        style.copy(
                            bottomPaddingPercent =
                                (style.bottomPaddingPercent - 5)
                                    .coerceAtLeast(5)
                        )
                    )
                },
                onIncrease = {
                    onStyleChange(
                        style.copy(
                            bottomPaddingPercent =
                                (style.bottomPaddingPercent + 5)
                                    .coerceAtMost(40)
                        )
                    )
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .clickable {
                        onStyleChange(PlayerSubtitleStyleState())
                    }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = SubtitleAccent.copy(alpha = .86f),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Reset Style",
                    color = Color.White.copy(alpha = .78f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun formatSubtitleDelay(delayMs: Int): String {
    val absoluteMs = kotlin.math.abs(delayMs)
    val sign = when {
        delayMs > 0 -> "+"
        delayMs < 0 -> "-"
        else -> ""
    }
    val hundredths = (absoluteMs % 1_000) / 10
    return "$sign${absoluteMs / 1_000}.${hundredths.toString().padStart(2, '0')}s"
}

@Composable
private fun StyleStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column {
        Text(label, color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            StepButton("−", onDecrease)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(Color.White.copy(alpha = .10f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(value, color = Color.White, fontSize = 11.sp)
            }
            StepButton("+", onIncrease)
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color.White.copy(alpha = .10f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
private fun StyleToggle(label: String, enabled: Boolean, onClick: () -> Unit) {
    Column {
        Text(label, color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Surface(
            modifier = Modifier.clickable(onClick = onClick),
            color = if (enabled) SubtitleAccent.copy(alpha = .16f) else Color.White.copy(alpha = .10f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                if (enabled) "On" else "Off",
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                color = if (enabled) SubtitleAccent else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StyleColorPicker(
    label: String,
    selected: Int,
    colours: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(label, color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colours.forEach { colour ->
                val isSelected =
                    (selected and 0x00FFFFFF) == (colour and 0x00FFFFFF)
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) SubtitleAccent else Color.White.copy(alpha = .55f),
                            CircleShape,
                        )
                        .padding(3.dp)
                        .background(Color(colour), CircleShape)
                        .clickable { onSelect(colour) }
                )
            }
        }
    }
}

@Composable
private fun SubtitleEmptyState(message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = .48f),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun KeepSubtitleDialogImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val decor = window?.decorView
        val previousFlags = decor?.systemUiVisibility ?: 0

        window?.setDimAmount(0f)
        if (Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            decor?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        onDispose {
            if (Build.VERSION.SDK_INT < 30) {
                decor?.systemUiVisibility = previousFlags
            }
        }
    }
}

private fun buildSubtitleLanguageGroups(
    tracks: List<PlayerTrackChoice>,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
): List<SubtitleLanguageGroup> {
    val preferred = preferredLanguageCode?.let(::canonicalSubtitleLanguage)
    val secondary = secondaryLanguageCode?.let(::canonicalSubtitleLanguage)

    return tracks
        .groupBy { canonicalSubtitleLanguage(it.language) }
        .map { (code, groupedTracks) ->
            SubtitleLanguageGroup(
                code = code,
                label = friendlySubtitleLanguageName(code),
                tracks = groupedTracks,
            )
        }
        .sortedWith(
            compareBy<SubtitleLanguageGroup> {
                when (it.code) {
                    preferred -> 0
                    secondary -> 1
                    "und" -> 3
                    else -> 2
                }
            }.thenBy { it.label }
        )
}

internal fun canonicalSubtitleLanguage(value: String?): String {
    val normalized = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace('_', '-')
        ?.takeIf { it.isNotBlank() }
        ?: return "und"

    val language = normalized.substringBefore('-')
    return when (language) {
        "ind", "idn" -> "id"
        "may", "msa", "zsm" -> "ms"
        "eng" -> "en"
        "spa" -> "es"
        "por" -> "pt"
        "fre", "fra" -> "fr"
        "ger", "deu" -> "de"
        "ita" -> "it"
        "dut", "nld" -> "nl"
        "chi", "zho" -> "zh"
        "jpn" -> "ja"
        "kor" -> "ko"
        "ara" -> "ar"
        "hin" -> "hi"
        "tam" -> "ta"
        "mac", "mkd" -> "mk"
        "per", "fas" -> "fa"
        else -> language.ifBlank { "und" }
    }
}

internal fun friendlySubtitleLanguageName(value: String?): String {
    val code = canonicalSubtitleLanguage(value)
    if (code == "und") return "Unknown"

    return Locale(code)
        .getDisplayLanguage(Locale.ENGLISH)
        .takeIf { it.isNotBlank() && !it.equals(code, true) }
        ?.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        ?: code.uppercase(Locale.ROOT)
}

private fun withAlpha(colour: Int, opacityPercent: Int): Int {
    val alpha = opacityPercent.coerceIn(0, 100) * 255 / 100
    return (alpha shl 24) or (colour and 0x00FFFFFF)
}
