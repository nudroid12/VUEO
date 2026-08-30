package com.vueo.app.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.ui.components.NetworkImage

internal data class PlayerEpisodeProgress(
    val fraction: Float = 0f,
    val watched: Boolean = false,
)

private val EpisodeAccent = Color(0xFFB9FF3A)
private val EpisodeCardShape = RoundedCornerShape(14.dp)

@Composable
internal fun PlayerEpisodesWorkspace(
    seriesTitle: String,
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    progressByEpisodeId: Map<String, PlayerEpisodeProgress>,
    onEpisodeSelected: (EpisodeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val groupedEpisodes = remember(episodes) {
        episodes
            .sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
            .groupBy { it.season }
    }
    val seasons = remember(groupedEpisodes) { groupedEpisodes.keys.sorted() }
    var selectedSeason by remember(currentEpisode?.id, seasons) {
        mutableIntStateOf(
            currentEpisode?.season
                ?.takeIf { it in seasons }
                ?: seasons.firstOrNull()
                ?: 1
        )
    }
    val visibleEpisodes = groupedEpisodes[selectedSeason].orEmpty()
    val episodeListState = rememberLazyListState()

    LaunchedEffect(selectedSeason, currentEpisode?.id, visibleEpisodes) {
        if (visibleEpisodes.isEmpty()) {
            return@LaunchedEffect
        }
        val currentIndex = visibleEpisodes.indexOfFirst { it.id == currentEpisode?.id }
        episodeListState.scrollToItem(currentIndex.coerceAtLeast(0))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepEpisodesDialogImmersive()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .32f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .97f),
                        .58f to Color.Black.copy(alpha = .86f),
                        1f to Color.Black.copy(alpha = .16f),
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
                    contentDescription = "Close episodes",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.78f)
                    .widthIn(max = 760.dp),
            ) {
                Text(
                    "Episodes",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    seriesTitle,
                    color = Color.White.copy(alpha = .52f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(11.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(end = 10.dp),
                ) {
                    items(seasons, key = { it }) { season ->
                        SeasonChip(
                            season = season,
                            selected = season == selectedSeason,
                            onClick = { selectedSeason = season },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (visibleEpisodes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No episodes available for this season.",
                            color = Color.White.copy(alpha = .52f),
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        state = episodeListState,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        itemsIndexed(
                            items = visibleEpisodes,
                            key = { index, candidate ->
                                "${candidate.season}:${candidate.episode}:${candidate.id}:$index"
                            },
                        ) { _, candidate ->
                            EpisodeWorkspaceRow(
                                episode = candidate,
                                current = candidate.id == currentEpisode?.id,
                                progress = progressByEpisodeId[candidate.id]
                                    ?: PlayerEpisodeProgress(),
                                onClick = {
                                    if (candidate.id != currentEpisode?.id) {
                                        onEpisodeSelected(candidate)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(
    season: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = .08f),
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else Color.White.copy(alpha = .10f),
        ),
    ) {
        Text(
            if (season == 0) "Specials" else "Season $season",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) Color(0xFF202124) else Color.White.copy(alpha = .72f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EpisodeWorkspaceRow(
    episode: EpisodeItem,
    current: Boolean,
    progress: PlayerEpisodeProgress,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EpisodeCardShape)
            .clickable(enabled = !current, onClick = onClick),
        shape = EpisodeCardShape,
        color = if (current) EpisodeAccent.copy(alpha = .10f)
        else Color.White.copy(alpha = .055f),
        border = BorderStroke(
            if (current) 1.5.dp else 1.dp,
            if (current) EpisodeAccent.copy(alpha = .72f)
            else Color.White.copy(alpha = .08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = .06f)),
            ) {
                NetworkImage(
                    url = episode.thumbnail,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackText = "E${episode.episode}",
                )
                if (progress.fraction > 0f && !progress.watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = .28f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(EpisodeAccent),
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = .72f),
                ) {
                    Text(
                        "S${episode.season} E${episode.episode}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        episode.title.ifBlank { "Episode ${episode.episode}" },
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = .94f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        current -> EpisodeStatus("Playing", EpisodeAccent)
                        progress.watched -> EpisodeStatus("Watched", Color.White.copy(alpha = .70f))
                    }
                }
                episode.overview
                    ?.takeIf { it.isNotBlank() }
                    ?.let { overview ->
                        Text(
                            overview,
                            color = Color.White.copy(alpha = .48f),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                if (progress.fraction > 0f && !progress.watched && !current) {
                    Text(
                        "${(progress.fraction * 100).toInt().coerceIn(1, 99)}% watched",
                        color = EpisodeAccent.copy(alpha = .78f),
                        fontSize = 8.sp,
                    )
                }
            }

            Icon(
                if (progress.watched) Icons.Default.Check else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (current) EpisodeAccent else Color.White.copy(alpha = .48f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun EpisodeStatus(
    label: String,
    colour: Color,
) {
    Surface(
        shape = CircleShape,
        color = colour.copy(alpha = .12f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = colour,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun KeepEpisodesDialogImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                window.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                run {
                    window.decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }
            }
        }
        onDispose { }
    }
}
