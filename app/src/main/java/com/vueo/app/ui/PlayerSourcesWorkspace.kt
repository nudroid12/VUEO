package com.vueo.app.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.app.core.model.StreamSource
import java.util.Locale

private val SourceAccent = Color(0xFFB9FF3A)
private val SourceCardShape = RoundedCornerShape(14.dp)

@Composable
internal fun PlayerSourcesWorkspace(
    title: String,
    sources: List<StreamSource>,
    currentSource: StreamSource,
    currentPlaybackFailed: Boolean,
    onSelect: (StreamSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val qualityFilters = remember(sources) {
        sources
            .map(::sourceQualityBucket)
            .distinct()
            .sortedBy(::sourceQualityOrder)
    }
    var activeFilter by remember(sources) {
        mutableStateOf<String?>(null)
    }
    val visibleSources = sources.filter {
        activeFilter == null || sourceQualityBucket(it) == activeFilter
    }
    val recommended = remember(sources, currentSource.url, currentPlaybackFailed) {
        if (currentPlaybackFailed) {
            sources.firstOrNull { it.url != currentSource.url }
                ?: sources.firstOrNull()
        } else {
            sources.firstOrNull()
        }
    }
    val bestQualityOrder = sources
        .minOfOrNull { sourceQualityOrder(sourceQualityBucket(it)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepSourcesDialogImmersive()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .24f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .98f),
                        .62f to Color.Black.copy(alpha = .84f),
                        1f to Color.Black.copy(alpha = .20f),
                    )
                )
                .padding(start = 34.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close sources",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            val workspaceWidth = minOf(maxWidth * .64f, 720.dp)
            Column(
                modifier = Modifier
                    .width(workspaceWidth)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = "Sources",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$title • ${sources.size} playable",
                    color = Color.White.copy(alpha = .52f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )

                recommended?.let { candidate ->
                    Spacer(Modifier.height(10.dp))
                    RecommendedSourceCard(
                        source = candidate,
                        current = candidate.url == currentSource.url,
                        recoverySuggestion = currentPlaybackFailed &&
                            candidate.url != currentSource.url,
                        onSelect = { onSelect(candidate) },
                    )
                }

                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SourceFilterChip(
                        label = "All",
                        selected = activeFilter == null,
                        onClick = { activeFilter = null },
                    )
                    qualityFilters.forEach { quality ->
                        SourceFilterChip(
                            label = quality,
                            selected = activeFilter == quality,
                            onClick = { activeFilter = quality },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = visibleSources,
                        key = { it.url ?: "${it.providerId}:${it.name}" },
                    ) { candidate ->
                        val current = candidate.url == currentSource.url
                        SourceListRow(
                            source = candidate,
                            current = current,
                            recommended = candidate.url == recommended?.url,
                            bestQuality = sourceQualityOrder(
                                sourceQualityBucket(candidate)
                            ) == bestQualityOrder,
                            playbackFailed = current && currentPlaybackFailed,
                            onClick = {
                                if (!current) onSelect(candidate)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedSourceCard(
    source: StreamSource,
    current: Boolean,
    recoverySuggestion: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF181B17),
        border = BorderStroke(1.dp, SourceAccent.copy(alpha = .42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (recoverySuggestion) {
                        "SUGGESTED ALTERNATIVE"
                    } else {
                        "VUEO RECOMMENDS"
                    },
                    color = SourceAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .7.sp,
                )
                Text(
                    text = "${sourceQualityBucket(source)} • ${source.providerName}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = sourceDetailLine(source),
                    color = Color.White.copy(alpha = .54f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (current) {
                SourceBadge("Playing", accent = true)
            } else {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("Switch", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceListRow(
    source: StreamSource,
    current: Boolean,
    recommended: Boolean,
    bestQuality: Boolean,
    playbackFailed: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !current, onClick = onClick),
        shape = SourceCardShape,
        color = if (current) {
            SourceAccent.copy(alpha = .12f)
        } else {
            Color.White.copy(alpha = .04f)
        },
        border = BorderStroke(
            1.dp,
            if (current) {
                SourceAccent.copy(alpha = .48f)
            } else {
                Color.White.copy(alpha = .07f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        Color.White.copy(alpha = .055f),
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = if (current) SourceAccent else Color.White.copy(alpha = .66f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = sourceQualityBucket(source),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = source.providerName,
                        color = Color.White.copy(alpha = .64f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = sourceDetailLine(source),
                    color = Color.White.copy(alpha = .46f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    playbackFailed -> SourceBadge("Problem")
                    current -> SourceBadge("Current", accent = true)
                    recommended -> SourceBadge("Recommended", accent = true)
                    bestQuality -> SourceBadge("Best quality")
                    else -> SourceBadge("Direct")
                }
            }
            if (current) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Current source",
                    tint = SourceAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(
    label: String,
    accent: Boolean = false,
) {
    Text(
        text = label,
        color = if (accent) SourceAccent else Color.White.copy(alpha = .62f),
        fontSize = 8.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (accent) {
                    SourceAccent.copy(alpha = .10f)
                } else {
                    Color.White.copy(alpha = .06f)
                },
                RoundedCornerShape(50),
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun SourceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color(0xFF161A14) else Color.White.copy(alpha = .72f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (selected) SourceAccent else Color.White.copy(alpha = .06f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

private fun sourceQualityBucket(source: StreamSource): String {
    val value = "${source.quality.orEmpty()} ${source.name}".lowercase()
    return when {
        "2160" in value || "4k" in value || "uhd" in value -> "4K"
        "1080" in value -> "1080p"
        "720" in value -> "720p"
        "480" in value -> "480p"
        else -> source.quality?.takeIf { it.isNotBlank() } ?: "Auto"
    }
}

private fun sourceQualityOrder(value: String): Int =
    when (value.lowercase()) {
        "4k", "2160p", "uhd" -> 0
        "1080p" -> 1
        "720p" -> 2
        "480p" -> 3
        else -> 4
    }

private fun sourceDetailLine(source: StreamSource): String =
    buildList {
        source.codec?.takeIf { it.isNotBlank() }?.let(::add)
        source.hdr?.takeIf { it.isNotBlank() }?.let(::add)
        source.audio?.takeIf { it.isNotBlank() }?.let(::add)
        source.language?.takeIf { it.isNotBlank() }?.let(::add)
        source.sizeBytes?.takeIf { it > 0L }?.let {
            add(formatSourceSize(it))
        }
        add("Direct")
    }.distinct().joinToString(" • ")

private fun formatSourceSize(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GB", gib)
    } else {
        String.format(Locale.US, "%.0f MB", mib)
    }
}

@Composable
private fun KeepSourcesDialogImmersive() {
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
