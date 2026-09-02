package com.vueotv.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VueoBlack = Color(0xFF050706)
private val VueoPanel = Color(0xFF101412)
private val VueoGreen = Color(0xFF84E100)
private val VueoYellow = Color(0xFFD6FF00)
private val VueoMuted = Color(0xFFAAB2AD)

private data class TvCard(
    val title: String,
    val meta: String,
)

private val continueWatching = listOf(
    TvCard("Reacher", "S2 E4 • 31m left"),
    TvCard("Shōgun", "S1 E6 • 22m left"),
    TvCard("The Last of Us", "S2 E2 • 44m left"),
    TvCard("Fallout", "S1 E5 • 18m left"),
    TvCard("Severance", "S2 E1 • 39m left"),
    TvCard("Silo", "S2 E7 • 27m left"),
)

private val popular = listOf(
    TvCard("Dune: Part Two", "Movie • 2024"),
    TvCard("The Penguin", "Series • 2024"),
    TvCard("Andor", "Series • 2022"),
    TvCard("The Bear", "Series • 2022"),
    TvCard("The Batman", "Movie • 2022"),
    TvCard("Slow Horses", "Series • 2022"),
)

private val newest = listOf(
    TvCard("Paradise", "Series • New"),
    TvCard("The Studio", "Series • New"),
    TvCard("Mickey 17", "Movie • New"),
    TvCard("Black Bag", "Movie • New"),
    TvCard("MobLand", "Series • New"),
    TvCard("The Gorge", "Movie • New"),
)

@Composable
fun VueoTvApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VueoBlack,
        ) {
            VueoTvHome()
        }
    }
}

@Composable
private fun VueoTvHome() {
    val firstAction = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstAction.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VueoBlack),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 76.dp),
            contentPadding = PaddingValues(bottom = 34.dp),
        ) {
            item { Hero(firstAction) }
            item { TvRail("Continue Watching", continueWatching, showProgress = true) }
            item { TvRail("Popular", popular) }
            item { TvRail("Recently Added", newest) }
        }

        TvTopNav()
    }
}

@Composable
private fun TvTopNav() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 42.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VUEO",
                color = VueoYellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(44.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvNavItem("Home", selected = true)
                TvNavItem("Search")
                TvNavItem("Library")
                TvNavItem("Content Manager")
            }
        }

        TvNavItem("Luckez")
    }
}

@Composable
private fun TvNavItem(
    label: String,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        if (focused || selected) Color.White else VueoMuted,
        label = "navColor",
    )

    Box(
        modifier =
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .background(
                    color = if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                )
                .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun Hero(firstAction: FocusRequester) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF111A15),
                            Color(0xFF0A0E0C),
                            VueoBlack,
                        ),
                    ),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VueoGreen.copy(alpha = 0.17f),
                                Color.Transparent,
                            ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 58.dp, end = 40.dp)
                    .fillMaxWidth(0.52f),
        ) {
            Text(
                text = "REACHER",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "2022  •  4 Seasons  •  IMDb ★ 8.0",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "A former military investigator enters a small town and uncovers a conspiracy much larger than anyone expected.",
                color = VueoMuted,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvHeroButton(
                    text = "▶  Play",
                    primary = true,
                    modifier = Modifier.focusRequester(firstAction),
                )
                TvHeroButton(text = "+  My List")
            }
        }
    }
}

@Composable
private fun TvHeroButton(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "heroButtonScale")

    Button(
        onClick = { },
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .scale(scale),
        shape = RoundedCornerShape(9.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (primary) Color.White
                    else Color.White.copy(alpha = if (focused) 0.20f else 0.12f),
                contentColor = if (primary) Color.Black else Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 23.dp, vertical = 12.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvRail(
    title: String,
    cards: List<TvCard>,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier.padding(top = 14.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 58.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            itemsIndexed(cards) { index, card ->
                TvPosterCard(
                    card = card,
                    index = index,
                    showProgress = showProgress,
                )
            }
        }
    }
}

@Composable
private fun TvPosterCard(
    card: TvCard,
    index: Int,
    showProgress: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cardScale")
    val borderColor by animateColorAsState(
        if (focused) VueoYellow else Color.Transparent,
        label = "cardBorder",
    )

    Column(
        modifier =
            Modifier
                .width(154.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .focusable(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(205.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(10.dp))
                    .background(
                        brush =
                            Brush.linearGradient(
                                listOf(
                                    VueoPanel,
                                    Color(0xFF1A211D),
                                    if (index % 2 == 0) {
                                        VueoGreen.copy(alpha = 0.22f)
                                    } else {
                                        Color(0xFF2B302D)
                                    },
                                ),
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ),
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = Color.White.copy(alpha = 0.12f),
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )

            if (showProgress) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth((0.24f + index * 0.11f).coerceAtMost(0.82f))
                                .fillMaxHeight()
                                .background(VueoYellow),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.meta,
            color = VueoMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
