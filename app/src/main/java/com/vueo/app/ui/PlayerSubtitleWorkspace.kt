package com.vueo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

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

private val SubtitleAccent = Color(0xFFB6FF3B)
private val SubtitlePanel = Color(0xE817191D)
private val SubtitleBorder = Color.White.copy(alpha = .16f)

@Composable
internal fun PlayerSubtitleWorkspace(
    tracks: List<PlayerTrackChoice>,
    subtitlesDisabled: Boolean,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    style: PlayerSubtitleStyleState,
    onDisable: () -> Unit,
    onSelect: (PlayerTrackChoice) -> Unit,
    onStyleChange: (PlayerSubtitleStyleState) -> Unit,
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
    var activeLanguageCode by remember(groups) {
        mutableStateOf(
            selectedTrack?.language?.let(::normaliseLanguageCode)
                ?: preferredLanguageCode
                    ?.let(::normaliseLanguageCode)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .36f))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = .96f),
                            Color.Black.copy(alpha = .78f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(horizontal = 34.dp, vertical = 22.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        Color.Black.copy(alpha = .52f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close subtitles",
                    tint = Color.White,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxHeight(.88f),
            ) {
                Text(
                    "Subtitles",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitleWorkspaceSummary(
                        selectedTrack = selectedTrack,
                        subtitlesDisabled = subtitlesDisabled,
                    ),
                    color = Color.White.copy(alpha = .58f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubtitleRail(
                        title = "Languages",
                        subtitle = "${groups.size} available",
                        width = 188.dp,
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            item {
                                SubtitleWorkspaceRow(
                                    title = "Off",
                                    selected = subtitlesDisabled,
                                    onClick = onDisable,
                                )
                            }
                            items(groups, key = { it.code }) { group ->
                                SubtitleWorkspaceRow(
                                    title = group.label,
                                    detail = "${group.tracks.size}",
                                    selected = !subtitlesDisabled &&
                                        group.code == activeLanguageCode,
                                    onClick = {
                                        activeLanguageCode = group.code
                                    },
                                )
                            }
                        }
                    }

                    SubtitleRail(
                        title = "Tracks",
                        subtitle = when {
                            groups.isEmpty() -> "No tracks"
                            visibleTracks.isEmpty() -> "Choose a language"
                            else -> "${visibleTracks.size} available"
                        },
                        width = 286.dp,
                    ) {
                        if (visibleTracks.isEmpty()) {
                            SubtitleEmptyState(
                                if (groups.isEmpty()) {
                                    "No subtitle tracks were returned. Install a subtitle addon or try another source."
                                } else {
                                    "Choose a language to view its subtitle tracks."
                                }
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(visibleTracks, key = { it.key }) { track ->
                                    SubtitleWorkspaceRow(
                                        title = track.label,
                                        detail = track.sourceLabel,
                                        supporting = track.metadata,
                                        selected = !subtitlesDisabled && track.selected,
                                        onClick = { onSelect(track) },
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTrack != null && !subtitlesDisabled) {
                        SubtitleStyleRail(
                            style = style,
                            onStyleChange = onStyleChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleRail(
    title: String,
    subtitle: String,
    width: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        color = SubtitlePanel,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SubtitleBorder),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = .48f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(11.dp))
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun SubtitleStyleRail(
    style: PlayerSubtitleStyleState,
    onStyleChange: (PlayerSubtitleStyleState) -> Unit,
) {
    SubtitleRail(
        title = "Style",
        subtitle = "Live preview",
        width = 272.dp,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SubtitleStepper(
                    label = "Font size",
                    value = "${style.fontSizeSp} sp",
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
                SubtitleToggleRow(
                    label = "Bold text",
                    enabled = style.bold,
                    onClick = {
                        onStyleChange(style.copy(bold = !style.bold))
                    },
                )
            }
            item {
                SubtitleColorPicker(
                    label = "Text colour",
                    selected = style.textColor,
                    colours = listOf(
                        0xFFFFFFFF.toInt(),
                        0xFFFFFF66.toInt(),
                        0xFF66E7FF.toInt(),
                        0xFFB6FF3B.toInt(),
                        0xFFFF6B6B.toInt(),
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
                val opacity = ((style.textColor ushr 24) * 100 / 255)
                SubtitleStepper(
                    label = "Text opacity",
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
                SubtitleToggleRow(
                    label = "Outline",
                    enabled = style.outlineEnabled,
                    onClick = {
                        onStyleChange(
                            style.copy(outlineEnabled = !style.outlineEnabled)
                        )
                    },
                )
            }
            if (style.outlineEnabled) {
                item {
                    SubtitleColorPicker(
                        label = "Outline colour",
                        selected = style.outlineColor,
                        colours = listOf(
                            0xFF000000.toInt(),
                            0xFFFFFFFF.toInt(),
                        ),
                        onSelect = {
                            onStyleChange(style.copy(outlineColor = it))
                        },
                    )
                }
            }
            item {
                SubtitleStepper(
                    label = "Vertical position",
                    value = "${style.bottomPaddingPercent}%",
                    onDecrease = {
                        onStyleChange(
                            style.copy(
                                bottomPaddingPercent =
                                    (style.bottomPaddingPercent - 5).coerceAtLeast(5)
                            )
                        )
                    },
                    onIncrease = {
                        onStyleChange(
                            style.copy(
                                bottomPaddingPercent =
                                    (style.bottomPaddingPercent + 5).coerceAtMost(40)
                            )
                        )
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onStyleChange(PlayerSubtitleStyleState())
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .72f),
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Reset style",
                        color = Color.White.copy(alpha = .78f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleWorkspaceRow(
    title: String,
    detail: String? = null,
    supporting: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) SubtitleAccent.copy(alpha = .13f)
                else Color.White.copy(alpha = .035f),
                RoundedCornerShape(11.dp),
            )
            .border(
                1.dp,
                if (selected) SubtitleAccent.copy(alpha = .7f)
                else Color.Transparent,
                RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) SubtitleAccent else Color.White,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supporting?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = .42f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = Color.White.copy(alpha = .5f),
                fontSize = 9.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .widthIn(max = 78.dp),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubtitleStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column {
        Text(label, color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = .055f), RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SubtitleStepButton("−", onDecrease)
            Text(value, color = Color.White, fontSize = 11.sp)
            SubtitleStepButton("+", onIncrease)
        }
    }
}

@Composable
private fun SubtitleStepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp)
    }
}

@Composable
private fun SubtitleToggleRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White.copy(alpha = .74f), fontSize = 11.sp)
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(19.dp)
                .background(
                    if (enabled) SubtitleAccent else Color.White.copy(alpha = .16f),
                    CircleShape,
                )
                .padding(2.dp),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(15.dp)
                    .background(
                        if (enabled) Color.Black else Color.White.copy(alpha = .72f),
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun SubtitleColorPicker(
    label: String,
    selected: Int,
    colours: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(label, color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colours.forEach { colour ->
                val selectedRgb = selected and 0x00FFFFFF
                val colourRgb = colour and 0x00FFFFFF
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .border(
                            if (selectedRgb == colourRgb) 2.dp else 1.dp,
                            if (selectedRgb == colourRgb) SubtitleAccent
                            else Color.White.copy(alpha = .25f),
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
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        color = Color.White.copy(alpha = .5f),
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

private fun buildSubtitleLanguageGroups(
    tracks: List<PlayerTrackChoice>,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
): List<SubtitleLanguageGroup> {
    val preferred = preferredLanguageCode?.let(::normaliseLanguageCode)
    val secondary = secondaryLanguageCode?.let(::normaliseLanguageCode)

    return tracks
        .groupBy { normaliseLanguageCode(it.language) }
        .map { (code, groupedTracks) ->
            SubtitleLanguageGroup(
                code = code,
                label = languageDisplayName(code),
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

private fun normaliseLanguageCode(value: String?): String =
    value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?.takeIf { it.isNotBlank() }
        ?: "und"

private fun languageDisplayName(code: String): String {
    if (code == "und") return "Unknown"
    return Locale(code).getDisplayLanguage(Locale.getDefault())
        .takeIf { it.isNotBlank() && it != code }
        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        ?: code.uppercase(Locale.ROOT)
}

private fun subtitleWorkspaceSummary(
    selectedTrack: PlayerTrackChoice?,
    subtitlesDisabled: Boolean,
): String = when {
    subtitlesDisabled -> "Subtitles off"
    selectedTrack != null -> "${selectedTrack.label} · ${selectedTrack.sourceLabel}"
    else -> "Choose a language and track"
}

private fun withAlpha(colour: Int, opacityPercent: Int): Int {
    val alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100)
    return (alpha shl 24) or (colour and 0x00FFFFFF)
}
