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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueotv.app.data.TvCatalogRow
import com.vueotv.app.data.TvHomeData
import com.vueotv.app.data.TvHomeRepository
import com.vueotv.app.data.TvMediaItem
import com.vueotv.app.ui.components.TvNetworkImage
import com.vueotv.app.update.VueoTvUpdateManager
import com.vueotv.app.update.VueoTvUpdateRelease

private val VueoBlack = Color(0xFF050706)
private val VueoPanel = Color(0xFF101412)
private val VueoGreen = Color(0xFF84E100)
private val VueoYellow = Color(0xFFD6FF00)
private val VueoMuted = Color(0xFFAAB2AD)

@Composable
fun VueoTvApp() {
    val context = LocalContext.current
    var updateRelease by remember { mutableStateOf<VueoTvUpdateRelease?>(null) }
    var updateVisible by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        VueoTvUpdateManager.check(
            context = context.applicationContext,
            force = false,
        ) { result ->
            val release = result.release
            if (release != null && release.isNewerThanCurrent()) {
                updateRelease = release
                updateVisible = true
                updateError = null
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VueoBlack,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                VueoTvHome()

                val release = updateRelease
                if (updateVisible && release != null) {
                    TvUpdateOverlay(
                        release = release,
                        downloading = updateDownloading,
                        progress = updateProgress,
                        error = updateError,
                        onLater = {
                            if (!updateDownloading) {
                                updateVisible = false
                            }
                        },
                        onUpdateNow = {
                            if (VueoTvUpdateManager.needsInstallPermission(context)) {
                                updateError =
                                    "Allow VUEO TV to install unknown apps, then return and choose Update Now again."
                                runCatching {
                                    VueoTvUpdateManager.openInstallPermissionSettings(context)
                                }.onFailure {
                                    updateError = it.message ?: "Unable to open install permission settings."
                                }
                            } else if (!updateDownloading) {
                                updateDownloading = true
                                updateProgress = 0
                                updateError = null

                                VueoTvUpdateManager.downloadAndInstall(
                                    context = context.applicationContext,
                                    release = release,
                                    onProgress = { updateProgress = it },
                                ) { result ->
                                    updateDownloading = false
                                    result.onFailure { failure ->
                                        updateError =
                                            failure.message ?: "Unable to install the TV update."
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VueoTvHome() {
    val context = LocalContext.current
    val repository =
        remember(context) {
            TvHomeRepository(context.applicationContext)
        }
    val firstAction = remember { FocusRequester() }

    var home by remember {
        mutableStateOf(repository.cached())
    }
    var selectedHero by remember {
        mutableStateOf(home?.hero)
    }
    var loading by remember {
        mutableStateOf(home == null)
    }
    var refreshError by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(home?.hero?.id) {
        if (home != null) {
            runCatching { firstAction.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            repository.refresh()
        }
            .onSuccess { fresh ->
                home = fresh
                selectedHero = fresh.hero
                refreshError = null
            }
            .onFailure {
                refreshError =
                    if (home == null) {
                        "Unable to load VUEO catalogs"
                    } else {
                        "Showing cached catalog"
                    }
            }

        loading = false
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VueoBlack),
    ) {
        when {
            home != null && selectedHero != null -> {
                HomeContent(
                    home = home!!,
                    hero = selectedHero!!,
                    firstAction = firstAction,
                    refreshError = refreshError,
                    onCardFocused = { selectedHero = it },
                )
            }

            loading -> LoadingHome()

            else -> ErrorHome(
                message = refreshError ?: "Unable to load VUEO catalogs",
            )
        }

        TvTopNav()
    }
}

@Composable
private fun HomeContent(
    home: TvHomeData,
    hero: TvMediaItem,
    firstAction: FocusRequester,
    refreshError: String?,
    onCardFocused: (TvMediaItem) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 76.dp),
        contentPadding = PaddingValues(bottom = 38.dp),
    ) {
        item {
            Hero(
                item = hero,
                firstAction = firstAction,
                providerName = home.providerName,
            )
        }

        refreshError?.let { message ->
            item {
                Text(
                    text = message,
                    color = VueoMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 58.dp, vertical = 5.dp),
                )
            }
        }

        home.rows.forEach { row ->
            item(key = row.id) {
                TvRail(
                    row = row,
                    onCardFocused = onCardFocused,
                )
            }
        }
    }
}

@Composable
private fun TvTopNav() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VueoBlack,
                            VueoBlack.copy(alpha = 0.94f),
                            Color.Transparent,
                        )
                    )
                )
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
private fun Hero(
    item: TvMediaItem,
    firstAction: FocusRequester,
    providerName: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(VueoBlack),
    ) {
        TvNetworkImage(
            url = item.background ?: item.poster,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.68f),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    VueoBlack,
                                    VueoBlack.copy(alpha = 0.96f),
                                    VueoBlack.copy(alpha = 0.56f),
                                    Color.Transparent,
                                ),
                        )
                    ),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                VueoBlack,
                            )
                        )
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 58.dp, end = 40.dp)
                    .fillMaxWidth(0.50f),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = heroMeta(item),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text =
                    item.description
                        ?: item.genres.take(3).joinToString(" • ")
                        .ifBlank { "Available from $providerName" },
                color = VueoMuted,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvHeroButton(
                    text = "▶  Play",
                    primary = true,
                    modifier = Modifier.focusRequester(firstAction),
                )
                TvHeroButton(text = "+  My List")
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = "Source • $providerName",
                color = VueoMuted.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
        }
    }
}

private fun heroMeta(item: TvMediaItem): String =
    buildList {
        add(item.displayType)
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let(::add)
        item.imdbRating?.let {
            add("IMDb ★ ${String.format("%.1f", it)}")
        }
    }.joinToString("  •  ")

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
    row: TvCatalogRow,
    onCardFocused: (TvMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 58.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.providerName,
                color = VueoMuted.copy(alpha = 0.68f),
                fontSize = 11.sp,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(
                items = row.items,
                key = { "${row.id}:${it.type}:${it.id}" },
            ) { item ->
                TvPosterCard(
                    item = item,
                    onFocused = onCardFocused,
                )
            }
        }
    }
}

@Composable
private fun TvPosterCard(
    item: TvMediaItem,
    onFocused: (TvMediaItem) -> Unit,
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
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) {
                        onFocused(item)
                    }
                }
                .focusable(),
    ) {
        TvNetworkImage(
            url = item.poster,
            contentDescription = item.name,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(222.dp)
                    .border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(10.dp),
                    ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cardMeta(item),
            color = VueoMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun cardMeta(item: TvMediaItem): String =
    listOfNotNull(
        item.displayType,
        item.releaseInfo?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

@Composable
private fun TvUpdateOverlay(
    release: VueoTvUpdateRelease,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onLater: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    val updateButtonFocus = remember { FocusRequester() }

    LaunchedEffect(release.versionCode) {
        runCatching { updateButtonFocus.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(610.dp)
                    .background(
                        color = VueoPanel,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 34.dp, vertical = 28.dp),
        ) {
            Text(
                text = "VUEO TV update available",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = release.versionName,
                color = VueoYellow,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (release.changelog.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                release.changelog.take(3).forEach { item ->
                    Text(
                        text = "•  $item",
                        color = VueoMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            if (downloading) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Downloading update… ${progress.coerceIn(0, 100)}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(99.dp),
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    (progress.coerceIn(0, 100) / 100f)
                                        .coerceAtLeast(0.01f)
                                )
                                .fillMaxHeight()
                                .background(
                                    VueoYellow,
                                    RoundedCornerShape(99.dp),
                                ),
                    )
                }
            }

            error?.let { message ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = message,
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onUpdateNow,
                    enabled = !downloading,
                    modifier = Modifier.focusRequester(updateButtonFocus),
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Update Now",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onLater,
                    enabled = !downloading,
                    shape = RoundedCornerShape(10.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Later",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingHome() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = VueoYellow,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Loading VUEO",
                color = VueoMuted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ErrorHome(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VUEO",
                color = VueoYellow,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = VueoMuted,
                fontSize = 15.sp,
            )
        }
    }
}
