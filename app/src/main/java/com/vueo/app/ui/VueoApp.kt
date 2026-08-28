package com.vueo.app.ui

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MediaItem as Media3MediaItem
import com.vueo.app.core.extensions.AddonCategory
import com.vueo.app.core.extensions.ExtensionInstaller
import com.vueo.app.core.extensions.primaryAddonCategory
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.MediaExtension
import com.vueo.app.core.extensions.UnifiedMediaEngine
import com.vueo.app.core.extensions.SourceRanker
import com.vueo.app.core.extensions.SourceCleaner
import com.vueo.app.core.extensions.SourceDiscoveryCache
import com.vueo.app.core.extensions.CatalogDiscoveryCache
import com.vueo.app.core.enrichment.MdblistClient
import com.vueo.app.core.enrichment.MediaRating
import com.vueo.app.core.enrichment.TmdbEnhancementClient
import com.vueo.app.core.model.CatalogRow
import com.vueo.app.BuildConfig
import com.vueo.app.core.storage.PlaybackStore
import com.vueo.app.core.storage.LibraryStore
import com.vueo.app.core.storage.ProfileStore
import com.vueo.app.core.storage.VueoProfile
import com.vueo.app.core.storage.LibraryPlaybackEntry
import com.vueo.app.core.storage.PreferredQuality
import com.vueo.app.core.storage.SettingsStore
import com.vueo.app.core.storage.VueoDataMigration
import com.vueo.app.core.update.VueoUpdateManager
import com.vueo.app.core.model.SubtitleTrack
import com.vueo.app.core.plugin.PluginStore
import com.vueo.app.core.plugin.ProviderCodeSyncManager
import com.vueo.app.core.plugin.ProviderCodeStore
import com.vueo.app.core.plugin.ProviderHealthStatus
import com.vueo.app.core.plugin.ProviderHealthRecord
import com.vueo.app.core.plugin.PluginHealthStore
import com.vueo.app.core.plugin.TmdbResolver
import com.vueo.app.core.plugin.PluginSourceEngine
import com.vueo.app.core.plugin.PluginRepositoryDescriptor
import com.vueo.app.core.plugin.PluginRepositoryClient
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.storage.AddonStore
import com.vueo.app.ui.components.NetworkImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private enum class AppTab {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
}

private enum class SettingsPage {
    ROOT,
    PROFILES,
    CONTENT_MANAGER,
    ADDONS,
    PLUGINS,
    ENHANCEMENTS,
    TMDB,
    MDBLIST,
    PLAYBACK,
    SUBTITLES,
    SOURCES,
    APPEARANCE,
    DATA_STORAGE,
    UPDATES,
    ABOUT,
}

private enum class HomeMediaFilter(
    val label: String,
) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
}

@Composable
fun VueoApp() {
    val context = LocalContext.current
    val engine = remember { UnifiedMediaEngine() }
    val store = remember {
        AddonStore(context.applicationContext)
    }
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }
    val libraryStore = remember {
        LibraryStore(
            context.applicationContext
        )
    }
    val profileStore = remember {
        ProfileStore(
            context.applicationContext
        )
    }
    val settingsStore = remember {
        SettingsStore(
            context.applicationContext
        )
    }

    LaunchedEffect(settingsStore) {
        VueoPalette.applyAccent(
            settingsStore.appAccent()
        )
    }
    val providerCodeSync = remember {
        ProviderCodeSyncManager(
            context.applicationContext
        )
    }

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var settingsPage by remember { mutableStateOf(SettingsPage.ROOT) }
    var contentVersion by remember { mutableIntStateOf(0) }
    var booting by remember {
        mutableStateOf(true)
    }
    var selectedMedia by remember {
        mutableStateOf<MediaItem?>(null)
    }
    var mediaBackStack by remember {
        mutableStateOf<List<MediaItem>>(
            emptyList()
        )
    }
    var libraryVersion by remember {
        mutableIntStateOf(0)
    }
    var profileVersion by remember {
        mutableIntStateOf(0)
    }
    var showProfilePicker by remember {
        mutableStateOf(
            profileStore
                .shouldShowPickerOnStartup()
        )
    }
    var selectedLibraryEntry by remember {
        mutableStateOf<
            LibraryPlaybackEntry?
        >(null)
    }

    LaunchedEffect(Unit) {
        VueoDataMigration.migrateIfNeeded(
            context.applicationContext
        )

        profileStore.ensureDefaultProfile()
        showProfilePicker =
            profileStore
                .shouldShowPickerOnStartup()
        profileVersion++

        CatalogDiscoveryCache
            .restoreHome(
                context.applicationContext
            )

        SourceDiscoveryCache
            .clearExpired()

        if (
            settingsStore
                .automaticUpdateChecksEnabled()
        ) {
            launch {
                VueoUpdateManager.check(
                    context = context.applicationContext,
                    force = false,
                )
            }
        }

        contentVersion++

        store.seedDevelopmentDefaultsIfNeeded()
        pluginStore.seedDevelopmentDefaultsIfNeeded()

        launch {
            providerCodeSync.syncMissing(
                pluginStore.repositories()
            )
        }

        store.manifestUrls().forEach { manifestUrl ->
            runCatching {
                ExtensionInstaller.installStremioAddon(manifestUrl)
            }.onSuccess(engine::install)
        }

        booting = false
        contentVersion++
    }

    if (
        !booting &&
        showProfilePicker
    ) {
        WhosWatchingScreen(
            profileStore =
                profileStore,
            profileVersion =
                profileVersion,
            onProfileSelected = {
                selectedMedia = null
                selectedLibraryEntry =
                    null
                mediaBackStack =
                    emptyList()
                selectedTab =
                    AppTab.HOME
                settingsPage =
                    SettingsPage.ROOT
                libraryVersion++
                profileVersion++
                showProfilePicker =
                    false
            },
            onProfilesChanged = {
                profileVersion++
                libraryVersion++
            },
        )
        return
    }

    if (selectedMedia != null) {
        MediaDetailsScreen(
            engine = engine,
            settingsStore =
                settingsStore,
            initialItem = selectedMedia!!,
            initialLibraryEntry =
                selectedLibraryEntry,
            onLibraryChanged = {
                libraryVersion++
            },
            onBack = {
                val previous =
                    mediaBackStack
                        .lastOrNull()

                if (previous == null) {
                    selectedMedia = null
                    selectedLibraryEntry =
                        null
                } else {
                    selectedMedia =
                        previous
                    mediaBackStack =
                        mediaBackStack
                            .dropLast(1)
                }
            },
            onMediaClick = { next ->
                selectedMedia?.let {
                    current ->

                    mediaBackStack =
                        mediaBackStack +
                            current
                }

                selectedLibraryEntry =
                    null
                selectedMedia = next
            },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor =
                    VueoPalette.Nav,
                tonalElevation = 0.dp,
            ) {
                BottomTab(
                    tab = AppTab.HOME,
                    selected = selectedTab,
                    icon = Icons.Default.Home,
                    label = "Home",
                ) { selectedTab = it }

                BottomTab(
                    tab = AppTab.SEARCH,
                    selected = selectedTab,
                    icon = Icons.Default.Search,
                    label = "Search",
                ) { selectedTab = it }

                BottomTab(
                    tab = AppTab.LIBRARY,
                    selected = selectedTab,
                    icon = Icons.Default.VideoLibrary,
                    label = "Library",
                ) { selectedTab = it }

                BottomTab(
                    tab = AppTab.SETTINGS,
                    selected = selectedTab,
                    icon = Icons.Default.Settings,
                    label = "Settings",
                ) {
                    selectedTab = it
                    settingsPage =
                        SettingsPage.ROOT
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                )
                .padding(padding),
        ) {
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    engine = engine,
                    contentVersion = contentVersion,
                    booting = booting,
                    activeProfile =
                        profileStore.activeProfile(),
                    onProfileClick = {
                        showProfilePicker =
                            true
                    },
                    onOpenContentManager = {
                        selectedTab =
                            AppTab.SETTINGS
                        settingsPage =
                            SettingsPage
                                .CONTENT_MANAGER
                    },
                    onSearch = {
                        selectedTab =
                            AppTab.SEARCH
                    },
                    onMediaClick = {
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            null
                        selectedMedia = it
                    },
                )

                AppTab.SEARCH -> SearchScreen(
                    engine = engine,
                    contentVersion =
                        contentVersion,
                    booting = booting,
                    onMediaClick = {
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            null
                        selectedMedia = it
                    },
                )

                AppTab.LIBRARY -> LibraryScreen(
                    store =
                        libraryStore,
                    version =
                        libraryVersion,
                    onVersionChanged = {
                        libraryVersion++
                    },
                    onMediaClick = {
                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            null
                        selectedMedia = it
                    },
                    onPlaybackClick = {
                        entry ->

                        mediaBackStack =
                            emptyList()
                        selectedLibraryEntry =
                            entry
                        selectedMedia =
                            entry.media
                    },
                )

                AppTab.SETTINGS -> when (
                    settingsPage
                ) {
                    SettingsPage.ROOT ->
                        VueoSettingsHub(
                            engine = engine,
                            settingsStore = settingsStore,
                            profileStore = profileStore,
                            profileVersion = profileVersion,
                            onProfiles = {
                                settingsPage = SettingsPage.PROFILES
                            },
                            onContentManager = {
                                settingsPage = SettingsPage.CONTENT_MANAGER
                            },
                            onEnhancements = {
                                settingsPage = SettingsPage.ENHANCEMENTS
                            },
                            onPlayback = {
                                settingsPage = SettingsPage.PLAYBACK
                            },
                            onSubtitles = {
                                settingsPage = SettingsPage.SUBTITLES
                            },
                            onSources = {
                                settingsPage = SettingsPage.SOURCES
                            },
                            onAppearance = {
                                settingsPage = SettingsPage.APPEARANCE
                            },
                            onDataStorage = {
                                settingsPage = SettingsPage.DATA_STORAGE
                            },
                            onUpdates = {
                                settingsPage = SettingsPage.UPDATES
                            },
                            onAbout = {
                                settingsPage = SettingsPage.ABOUT
                            },
                        )

                    SettingsPage.PROFILES ->
                        ProfileSettingsScreen(
                            profileStore =
                                profileStore,
                            profileVersion =
                                profileVersion,
                            onProfilesChanged = {
                                profileVersion++
                            },
                            onActiveProfileChanged = {
                                selectedLibraryEntry =
                                    null
                                mediaBackStack =
                                    emptyList()
                                libraryVersion++
                                profileVersion++
                            },
                            onBack = {
                                settingsPage =
                                    SettingsPage.ROOT
                            },
                        )

                    SettingsPage.CONTENT_MANAGER ->
                        ContentManagerScreen(
                            engine = engine,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                            onAddons = {
                                settingsPage = SettingsPage.ADDONS
                            },
                            onPlugins = {
                                settingsPage = SettingsPage.PLUGINS
                            },
                        )

                    SettingsPage.ADDONS ->
                        AddonsScreen(
                            engine = engine,
                            store = store,
                            contentVersion = contentVersion,
                            onContentChanged = {
                                contentVersion++
                            },
                            onBack = {
                                settingsPage = SettingsPage.CONTENT_MANAGER
                            },
                        )

                    SettingsPage.PLUGINS ->
                        PluginsScreen(
                            onBack = {
                                settingsPage = SettingsPage.CONTENT_MANAGER
                            },
                        )

                    SettingsPage.ENHANCEMENTS ->
                        EnhancementsSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                            onTmdb = {
                                settingsPage = SettingsPage.TMDB
                            },
                            onMdblist = {
                                settingsPage = SettingsPage.MDBLIST
                            },
                        )

                    SettingsPage.TMDB ->
                        TmdbEnhancementSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ENHANCEMENTS
                            },
                        )

                    SettingsPage.MDBLIST ->
                        MdblistEnhancementSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ENHANCEMENTS
                            },
                        )

                    SettingsPage.PLAYBACK ->
                        PlaybackSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.SUBTITLES ->
                        SubtitleSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.SOURCES ->
                        SourceSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.APPEARANCE ->
                        AppearanceSettingsScreen(
                            settingsStore =
                                settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.DATA_STORAGE ->
                        DataStorageSettingsScreen(
                            libraryStore = libraryStore,
                            settingsStore = settingsStore,
                            onLibraryChanged = {
                                libraryVersion++
                            },
                            onCatalogCacheCleared = {
                                contentVersion++
                            },
                            onPersistentDataChanged = {
                                engine
                                    .stremioAddons()
                                    .forEach {
                                        engine.uninstall(
                                            it.descriptor.id
                                        )
                                    }

                                store.seedDevelopmentDefaultsIfNeeded()
                                pluginStore.seedDevelopmentDefaultsIfNeeded()

                                store.manifestUrls()
                                    .forEach { manifestUrl ->
                                        runCatching {
                                            ExtensionInstaller
                                                .installStremioAddon(
                                                    manifestUrl
                                                )
                                        }.onSuccess(
                                            engine::install
                                        )
                                    }

                                providerCodeSync.syncMissing(
                                    pluginStore.repositories()
                                )

                                profileStore.ensureDefaultProfile()
                                VueoPalette.applyAccent(
                                    settingsStore.appAccent()
                                )
                                selectedLibraryEntry = null
                                contentVersion++
                                libraryVersion++
                                profileVersion++
                            },
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.UPDATES ->
                        UpdatesSettingsScreen(
                            settingsStore = settingsStore,
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )

                    SettingsPage.ABOUT ->
                        AboutVueoSettingsScreen(
                            onBack = {
                                settingsPage = SettingsPage.ROOT
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomTab(
    tab: AppTab,
    selected: AppTab,
    icon: ImageVector,
    label: String,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = {
            onSelect(tab)
        },
        icon = {
            Icon(
                icon,
                contentDescription =
                    label,
            )
        },
        label = {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight =
                    if (
                        selected == tab
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
            )
        },
        colors =
            NavigationBarItemDefaults
                .colors(
                    selectedIconColor =
                        VueoPalette.Accent,
                    selectedTextColor =
                        Color.White,
                    indicatorColor =
                        VueoPalette.Accent
                            .copy(
                                alpha = .12f
                            ),
                    unselectedIconColor =
                        VueoPalette.Muted,
                    unselectedTextColor =
                        VueoPalette.Muted,
                ),
    )
}

@Composable
private fun HomeScreen(
    engine: UnifiedMediaEngine,
    contentVersion: Int,
    booting: Boolean,
    activeProfile: VueoProfile,
    onProfileClick: () -> Unit,
    onOpenContentManager: () -> Unit,
    onSearch: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    val context =
        LocalContext.current

    var rows by remember {
        mutableStateOf(
            CatalogDiscoveryCache
                .home(
                    allowStale = true
                )
                .orEmpty()
        )
    }
    var loading by remember {
        mutableStateOf(false)
    }
    var error by remember {
        mutableStateOf<String?>(null)
    }
    var mediaFilter by remember {
        mutableStateOf(
            HomeMediaFilter.ALL
        )
    }

    LaunchedEffect(contentVersion) {
        CatalogDiscoveryCache
            .home(
                allowStale = true
            )
            ?.takeIf {
                it.isNotEmpty()
            }
            ?.let {
                rows = it
            }

        if (booting) {
            loading = false
            return@LaunchedEffect
        }

        loading =
            rows.isEmpty()

        error = null

        runCatching {
            engine.loadCatalogRows(
                forceRefresh =
                    rows.isNotEmpty(),
            )
        }.onSuccess {
            fresh ->

            if (
                fresh.isNotEmpty()
            ) {
                rows = fresh

                CatalogDiscoveryCache
                    .persistHome(
                        context =
                            context
                                .applicationContext,
                        rows = fresh,
                    )
            }
        }.onFailure {
            failure ->

            error =
                failure.message

            if (
                rows.isEmpty()
            ) {
                rows =
                    CatalogDiscoveryCache
                        .home(
                            allowStale =
                                true
                        )
                        .orEmpty()
            }
        }

        loading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                VueoPalette.Background
            ),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            VueoHeader(
                profile =
                    activeProfile,
                onProfileClick =
                    onProfileClick,
                onSearch = onSearch,
            )
        }

        if (rows.isNotEmpty()) {
            item {
                HomeFilterRow(
                    selected =
                        mediaFilter,
                    onSelect = {
                        mediaFilter = it
                    },
                )
            }
        }

        if (booting || loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }

        if (!booting && !loading && rows.isEmpty()) {
            item {
                EmptyHomeCard(
                    hasAddons = engine.stremioAddons().isNotEmpty(),
                    error = error,
                    onOpenContentManager = onOpenContentManager,
                )
            }
        }

        if (rows.isNotEmpty()) {
            val filteredRows =
                rows.mapNotNull { row ->
                    val filteredItems =
                        row.items.filter {
                            item ->

                            when (mediaFilter) {
                                HomeMediaFilter.ALL ->
                                    true

                                HomeMediaFilter.MOVIES ->
                                    item.type == "movie"

                                HomeMediaFilter.SERIES ->
                                    item.type == "series"
                            }
                        }

                    if (
                        filteredItems.isEmpty()
                    ) {
                        null
                    } else {
                        row.copy(
                            items =
                                filteredItems
                        )
                    }
                }

            val hero =
                filteredRows
                    .asSequence()
                    .flatMap {
                        it.items.asSequence()
                    }
                    .firstOrNull {
                        !it.background.isNullOrBlank()
                    }
                    ?: filteredRows
                        .firstOrNull()
                        ?.items
                        ?.firstOrNull()

            if (hero != null) {
                item {
                    HeroMediaCard(
                        item = hero,
                        onClick = {
                            onMediaClick(hero)
                        },
                    )
                }
            }

            items(
                filteredRows,
                key = { it.id },
            ) { row ->
                CatalogSection(
                    row = row,
                    onMediaClick =
                        onMediaClick,
                )
            }
        }
    }
}

@Composable
private fun VueoHeader(
    profile: VueoProfile,
    onProfileClick: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 18.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier.weight(1f),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(
                        RoundedCornerShape(
                            12.dp
                        )
                    )
                    .background(
                        VueoPalette
                            .SurfaceElevated
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                VueoBrandMark(
                    modifier =
                        Modifier.size(
                            27.dp
                        ),
                )
            }

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        1.dp
                    ),
            ) {
                Text(
                    "VUEO",
                    color =
                        VueoPalette.BrandLime,
                    fontWeight =
                        FontWeight.Black,
                    fontSize = 23.sp,
                    letterSpacing = 4.sp,
                )

                Text(
                    "Your media. Your sources.",
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                )
            }
        }

        Surface(
            modifier =
                Modifier
                    .padding(
                        end = 8.dp
                    )
                    .clickable(
                        onClick =
                            onProfileClick
                    ),
            shape =
                RoundedCornerShape(
                    50
                ),
            color =
                VueoPalette
                    .SurfaceElevated,
        ) {
            Box(
                modifier =
                    Modifier.size(
                        48.dp
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    text =
                        profile.avatar,
                    fontSize = 20.sp,
                )
            }
        }

        Surface(
            shape =
                RoundedCornerShape(
                    50
                ),
            color =
                VueoPalette
                    .SurfaceElevated,
        ) {
            IconButton(
                onClick = onSearch,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription =
                        "Search",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun HomeFilterRow(
    selected: HomeMediaFilter,
    onSelect: (HomeMediaFilter) -> Unit,
) {
    LazyRow(
        contentPadding =
            PaddingValues(
                horizontal = 20.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        items(
            HomeMediaFilter.values().toList()
        ) { filter ->
            FilterChip(
                selected =
                    selected == filter,
                onClick = {
                    onSelect(filter)
                },
                label = {
                    Text(
                        filter.label,
                        fontWeight =
                            if (
                                selected ==
                                filter
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            },
                    )
                },
            )
        }
    }
}

@Composable
private fun EmptyHomeCard(
    hasAddons: Boolean,
    error: String?,
    onOpenContentManager: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            ),
        shape =
            RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(22.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
        ) {
            Text(
                "CONTENT",
                color =
                    VueoPalette.Accent,
                fontWeight =
                    FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
            )

            Text(
                if (hasAddons) {
                    "No catalog loaded"
                } else {
                    "Connect your first content source"
                },
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                when {
                    error != null ->
                        "VUEO could not load a catalog right now. Open Content Manager to review your addon."

                    hasAddons ->
                        "The installed addon does not expose a catalog that can load without extra filters."

                    else ->
                        "Install a Stremio addon in Content Manager. Available catalogs will appear here automatically."
                },
                color =
                    VueoPalette.Muted,
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Button(
                onClick =
                    onOpenContentManager,
            ) {
                Text(
                    "Open Content Manager"
                )
            }
        }
    }
}

@Composable
private fun HeroMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(
                horizontal = 20.dp
            )
            .fillMaxWidth()
            .height(305.dp)
            .clip(
                RoundedCornerShape(
                    26.dp
                )
            )
            .clickable(
                onClick = onClick
            ),
    ) {
        NetworkImage(
            url =
                item.background
                    ?: item.poster,
            contentDescription =
                item.name,
            modifier =
                Modifier.fillMaxSize(),
            contentScale =
                ContentScale.Crop,
            fallbackText =
                item.name,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black
                                        .copy(
                                            alpha = .08f
                                        ),
                                    Color.Black
                                        .copy(
                                            alpha = .22f
                                        ),
                                    Color.Black
                                        .copy(
                                            alpha = .94f
                                        ),
                                ),
                        )
                ),
        )

        Surface(
            modifier = Modifier
                .align(
                    Alignment.TopStart
                )
                .padding(18.dp),
            shape =
                RoundedCornerShape(
                    50
                ),
            color =
                Color.Black.copy(
                    alpha = .54f
                ),
        ) {
            Text(
                "FEATURED",
                modifier =
                    Modifier.padding(
                        horizontal = 11.dp,
                        vertical = 6.dp,
                    ),
                color =
                    VueoPalette.Accent,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
        }

        Column(
            modifier = Modifier
                .align(
                    Alignment.BottomStart
                )
                .padding(22.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    7.dp
                ),
        ) {
            Text(
                item.name,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight =
                    FontWeight.Black,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
            )

            val metaLine =
                listOfNotNull(
                    item.type
                        .replaceFirstChar {
                            it.uppercase()
                        },
                    item.releaseInfo,
                    item.genres
                        .take(2)
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.joinToString(" • "),
                ).joinToString("  •  ")

            if (
                metaLine.isNotBlank()
            ) {
                Text(
                    metaLine,
                    color =
                        Color.White.copy(
                            alpha = .72f
                        ),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }

            item.description
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Text(
                        it,
                        color =
                            Color.White.copy(
                                alpha = .68f
                            ),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }

            Spacer(
                Modifier.height(3.dp)
            )

            Button(
                onClick = onClick,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription =
                        null,
                )
                Spacer(
                    Modifier.width(7.dp)
                )
                Text("View Details")
            }
        }
    }
}

@Composable
private fun CatalogSection(
    row: CatalogRow,
    onMediaClick: (MediaItem) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                11.dp
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        2.dp
                    ),
            ) {
                Text(
                    row.title,
                    color = Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    row.providerName,
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape =
                    RoundedCornerShape(
                        50
                    ),
                color =
                    VueoPalette
                        .SurfaceElevated,
            ) {
                Text(
                    "${row.items.size}",
                    modifier =
                        Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp,
                        ),
                    color =
                        VueoPalette.Accent,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                ),
        ) {
            items(
                items = row.items,
                key = {
                    "${row.id}:${it.id}"
                },
            ) { item ->
                MediaPoster(
                    item = item,
                    onClick = {
                        onMediaClick(
                            item
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaPoster(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(138.dp)
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                17.dp
            ),
        color =
            VueoPalette.Surface,
    ) {
        Column {
            NetworkImage(
                url = item.poster,
                contentDescription =
                    item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    item.name,
            )

            Column(
                modifier =
                    Modifier.padding(
                        10.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        3.dp
                    ),
            ) {
                Text(
                    item.name,
                    color = Color.White,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis,
                    fontWeight =
                        FontWeight.SemiBold,
                    fontSize = 13.sp,
                )

                Text(
                    listOfNotNull(
                        item.releaseInfo,
                        item.type
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.replaceFirstChar {
                                it.uppercase()
                            },
                    ).joinToString(" • "),
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(
    engine: UnifiedMediaEngine,
    contentVersion: Int,
    booting: Boolean,
    onMediaClick: (MediaItem) -> Unit,
) {
    var query by remember {
        mutableStateOf("")
    }
    var results by remember {
        mutableStateOf<
            List<MediaItem>
        >(emptyList())
    }
    var searching by remember {
        mutableStateOf(false)
    }
    var status by remember {
        mutableStateOf(
            "Search movies and series across compatible catalogs."
        )
    }

    LaunchedEffect(
        query,
        contentVersion,
        booting,
    ) {
        val normalized =
            query.trim()

        if (
            booting ||
            normalized.length < 2
        ) {
            searching = false
            results =
                if (
                    normalized.length >= 2
                ) {
                    CatalogDiscoveryCache
                        .searchLocal(
                            normalized
                        )
                } else {
                    emptyList()
                }

            status =
                if (
                    normalized.isBlank()
                ) {
                    "Search movies and series across compatible catalogs."
                } else {
                    "Type at least 2 characters."
                }

            return@LaunchedEffect
        }

        results =
            CatalogDiscoveryCache
                .searchLocal(
                    normalized
                )

        status =
            if (results.isEmpty()) {
                "Searching connected catalogs…"
            } else {
                "${results.size} cached matches • checking connected catalogs…"
            }

        searching = true

        delay(450)

        val remote =
            runCatching {
                engine.search(
                    normalized
                )
            }.getOrElse {
                emptyList()
            }

        results =
            if (remote.isNotEmpty()) {
                remote
            } else {
                results
            }

        searching = false

        status =
            when {
                results.isEmpty() ->
                    "No results for \"$normalized\"."

                else ->
                    "${results.size} results"
            }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                bottom = 28.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 18.dp,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "DISCOVER",
                    color =
                        VueoPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )

                Text(
                    "Find something to watch",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight =
                        FontWeight.Black,
                )

                Text(
                    "Search across the catalogs connected to VUEO.",
                    color =
                        VueoPalette.Muted,
                    fontSize = 12.sp,
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Movies or series"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription =
                                null,
                        )
                    },
                    singleLine = true,
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                )

                if (searching) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }

                Text(
                    status,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .58f),
                    fontSize = 12.sp,
                )
            }
        }

        items(
            results,
            key = {
                "${it.sourceExtensionId}:" +
                    "${it.type}:${it.id}"
            },
        ) { item ->
            SearchResultCard(
                item = item,
                onClick = {
                    onMediaClick(item)
                },
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            )
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(11.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            NetworkImage(
                url = item.poster,
                contentDescription =
                    item.name,
                modifier = Modifier
                    .width(78.dp)
                    .height(116.dp)
                    .clip(
                        RoundedCornerShape(
                            12.dp
                        )
                    ),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    item.name,
            )

            Spacer(
                Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    ),
            ) {
                Text(
                    item.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    listOfNotNull(
                        item.type
                            .replaceFirstChar {
                                it.uppercase()
                            },
                        item.releaseInfo,
                    ).joinToString(" • "),
                    color =
                        VueoPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Bold,
                )

                if (
                    item.genres.isNotEmpty()
                ) {
                    Text(
                        item.genres
                            .take(3)
                            .joinToString(" • "),
                        color =
                            VueoPalette.Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }

                item.description
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        Text(
                            it,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                            color =
                                Color.White.copy(
                                    alpha = .58f
                                ),
                            fontSize = 11.sp,
                        )
                    }
            }

            Text(
                "›",
                color =
                    VueoPalette.Muted,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    store: LibraryStore,
    version: Int,
    onVersionChanged: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlaybackClick:
        (LibraryPlaybackEntry) -> Unit,
) {
    var continueWatching by remember(
        version
    ) {
        mutableStateOf(
            store.continueWatching()
        )
    }

    var watchlist by remember(
        version
    ) {
        mutableStateOf(
            store.watchlist()
        )
    }

    var history by remember(
        version
    ) {
        mutableStateOf(
            store.history()
        )
    }

    fun refresh() {
        continueWatching =
            store.continueWatching()

        watchlist =
            store.watchlist()

        history =
            store.history()
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                20.dp
            ),
    ) {
        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 18.dp,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        5.dp
                    ),
            ) {
                Text(
                    "YOUR SPACE",
                    color =
                        VueoPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )

                Text(
                    "Library",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight =
                        FontWeight.Black,
                )

                Text(
                    "Continue where you left off, keep a watchlist, and revisit your history.",
                    color =
                        VueoPalette.Muted,
                    fontSize = 12.sp,
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),
                ) {
                    LibraryMetricPill(
                        label = "Continue",
                        count =
                            continueWatching.size,
                    )

                    LibraryMetricPill(
                        label = "My List",
                        count =
                            watchlist.size,
                    )

                    LibraryMetricPill(
                        label = "History",
                        count =
                            history.size,
                    )
                }
            }
        }

        if (
            continueWatching
                .isNotEmpty()
        ) {
            item {
                LibrarySectionHeader(
                    title =
                        "Continue Watching",
                    count =
                        continueWatching.size,
                    actionLabel =
                        "Clear",
                    onAction = {
                        store
                            .clearContinueWatching()
                        refresh()
                        onVersionChanged()
                    },
                )
            }

            item {
                LazyRow(
                    contentPadding =
                        PaddingValues(
                            horizontal = 20.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                ) {
                    items(
                        continueWatching,
                        key = {
                            it.mediaKey
                        },
                    ) { entry ->
                        ContinueWatchingCard(
                            entry = entry,
                            onClick = {
                                onPlaybackClick(
                                    entry
                                )
                            },
                            onRemove = {
                                store.removeHistory(
                                    entry.mediaKey
                                )
                                refresh()
                                onVersionChanged()
                            },
                        )
                    }
                }
            }
        }

        if (
            watchlist.isNotEmpty()
        ) {
            item {
                LibrarySectionHeader(
                    title = "My List",
                    count =
                        watchlist.size,
                )
            }

            item {
                LazyRow(
                    contentPadding =
                        PaddingValues(
                            horizontal = 20.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                ) {
                    items(
                        watchlist,
                        key = {
                            "${it.type}:${it.id}"
                        },
                    ) { media ->
                        LibraryPosterCard(
                            media = media,
                            onClick = {
                                onMediaClick(
                                    media
                                )
                            },
                            onRemove = {
                                store
                                    .removeWatchlist(
                                        media
                                    )
                                refresh()
                                onVersionChanged()
                            },
                        )
                    }
                }
            }
        }

        if (
            history.isNotEmpty()
        ) {
            item {
                LibrarySectionHeader(
                    title = "History",
                    count =
                        history.size,
                    actionLabel =
                        "Clear",
                    onAction = {
                        store.clearHistory()
                        refresh()
                        onVersionChanged()
                    },
                )
            }

            items(
                history.take(60),
                key = {
                    "history:" +
                        it.mediaKey
                },
            ) { entry ->
                HistoryRow(
                    entry = entry,
                    onClick = {
                        onPlaybackClick(
                            entry
                        )
                    },
                    onRemove = {
                        store.removeHistory(
                            entry.mediaKey
                        )
                        refresh()
                        onVersionChanged()
                    },
                )
            }
        }

        if (
            continueWatching
                .isEmpty() &&
            watchlist.isEmpty() &&
            history.isEmpty()
        ) {
            item {
                ElevatedCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    20.dp
                            ),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                22.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        Icon(
                            Icons.Default
                                .VideoLibrary,
                            contentDescription =
                                null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                        )

                        Text(
                            "Your Library is ready",
                            fontSize = 19.sp,
                            fontWeight =
                                FontWeight.Black,
                        )

                        Text(
                            "Add titles to My List or start watching something. VUEO will keep Continue Watching and History here automatically.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                                    .copy(
                                        alpha = .62f
                                    ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp
                ),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                "$count items",
                color =
                    VueoPalette.Muted,
                fontSize = 10.sp,
            )
        }

        if (
            actionLabel != null &&
            onAction != null
        ) {
            TextButton(
                onClick = onAction,
            ) {
                Text(
                    actionLabel,
                    color =
                        VueoPalette.Accent,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LibraryMetricPill(
    label: String,
    count: Int,
) {
    Surface(
        shape =
            RoundedCornerShape(
                50
            ),
        color =
            VueoPalette
                .SurfaceElevated,
    ) {
        Text(
            "$count $label",
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 6.dp,
                ),
            color =
                if (
                    count > 0
                ) {
                    Color.White
                } else {
                    VueoPalette.Muted
                },
            fontSize = 10.sp,
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: LibraryPlaybackEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(250.dp)
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp),
            ) {
                NetworkImage(
                    url =
                        entry.media
                            .background
                            ?: entry.media
                                .poster,
                    contentDescription =
                        entry.media.name,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop,
                    fallbackText =
                        entry.media.name,
                )

                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                Color.Black
                                    .copy(
                                        alpha = .24f
                                    )
                            ),
                )

                FilledIconButton(
                    onClick = onClick,
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),
                ) {
                    Icon(
                        Icons.Default
                            .PlayArrow,
                        contentDescription =
                            "Continue",
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier =
                        Modifier.align(
                            Alignment.TopEnd
                        ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Remove",
                        tint = Color.White,
                    )
                }
            }

            LinearProgressIndicator(
                progress = {
                    entry.progressFraction
                },
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Column(
                modifier =
                    Modifier.padding(
                        12.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {
                Text(
                    entry.media.name,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    fontWeight =
                        FontWeight.Bold,
                )

                Text(
                    playbackEntrySubtitle(
                        entry
                    ),
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .58f),
                    fontSize = 11.sp,
                )

                Text(
                    "${formatPlaybackTime(entry.positionMs)} of " +
                        if (
                            entry.durationMs >
                                0L
                        ) {
                            formatPlaybackTime(
                                entry.durationMs
                            )
                        } else {
                            "unknown"
                        },
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    media: MediaItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier.width(
                132.dp
            ),
    ) {
        MediaPoster(
            item = media,
            onClick = onClick,
        )

        FilledIconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .padding(4.dp)
                    .size(30.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription =
                    "Remove from My List",
                modifier =
                    Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: LibraryPlaybackEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            )
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                16.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            NetworkImage(
                url =
                    entry.media.poster
                        ?: entry.media
                            .background,
                contentDescription =
                    entry.media.name,
                modifier = Modifier
                    .width(66.dp)
                    .height(92.dp)
                    .clip(
                        RoundedCornerShape(
                            9.dp
                        )
                    ),
                contentScale =
                    ContentScale.Crop,
                fallbackText =
                    entry.media.name,
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {
                Text(
                    entry.media.name,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    playbackEntrySubtitle(
                        entry
                    ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .58f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    when {
                        entry.isCompleted ->
                            "Completed"

                        entry.positionMs >
                            5_000L ->
                            "Stopped at " +
                                formatPlaybackTime(
                                    entry.positionMs
                                )

                        else ->
                            "Opened"
                    },
                    color =
                        if (
                            entry.isCompleted
                        ) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(
                                    alpha = .5f
                                )
                        },
                    fontSize = 11.sp,
                )
            }

            IconButton(
                onClick = onRemove,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription =
                        "Remove from History",
                )
            }
        }
    }
}

private fun playbackEntrySubtitle(
    entry: LibraryPlaybackEntry,
): String =
    when {
        entry.season != null &&
            entry.episode != null ->
            "S${entry.season} E${entry.episode}" +
                entry.episodeTitle
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        " • $it"
                    }
                    .orEmpty()

        else ->
            entry.media.type
                .replaceFirstChar {
                    it.uppercase()
                }
    }


@Composable
private fun ContentManagerScreen(
    engine: UnifiedMediaEngine,
    onBack: () -> Unit,
    onAddons: () -> Unit,
    onPlugins: () -> Unit,
) {
    val context =
        LocalContext.current

    val pluginStore =
        remember {
            PluginStore(
                context.applicationContext
            )
        }

    val addons =
        engine.stremioAddons()

    val repositoryCount =
        pluginStore
            .repositories()
            .size

    val providerCount =
        pluginStore
            .totalProviderCount()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                VueoPalette.Background
            ),
        contentPadding =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.Top,
            ) {
                IconButton(
                    onClick = onBack,
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription =
                            "Back to Settings",
                        tint =
                            Color.White,
                    )
                }

                Spacer(
                    Modifier.width(
                        4.dp
                    )
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            5.dp
                        ),
                ) {
                    Text(
                        "CONTROL CENTER",
                        color =
                            VueoPalette.Accent,
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Black,
                        letterSpacing = 1.4.sp,
                    )

                    Text(
                        "Content Manager",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight =
                            FontWeight.Black,
                    )

                    Text(
                        "Manage the catalogs, stream addons, and JavaScript providers that power VUEO.",
                        color =
                            VueoPalette.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            Card(
                shape =
                    RoundedCornerShape(
                        20.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            VueoPalette
                                .SurfaceElevated
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            addons.size
                                .toString(),
                        label = "Addons",
                    )

                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            repositoryCount
                                .toString(),
                        label = "Repos",
                    )

                    ContentMetric(
                        modifier =
                            Modifier.weight(1f),
                        value =
                            providerCount
                                .toString(),
                        label = "Providers",
                    )
                }
            }
        }

        item {
            Text(
                "SOURCES",
                fontWeight =
                    FontWeight.Black,
                color =
                    VueoPalette.Muted,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
            )
        }

        item {
            ContentManagerCard(
                title = "Addons",
                subtitle =
                    "Stremio-compatible catalogs, metadata, streams, and subtitles.",
                status =
                    "${addons.size} installed",
                icon =
                    Icons.Default.Extension,
                onClick = onAddons,
            )
        }

        item {
            ContentManagerCard(
                title = "Plugins",
                subtitle =
                    "QuickJS provider repositories used by the Smart Source Engine.",
                status =
                    "$repositoryCount repos • $providerCount providers",
                icon =
                    Icons.Default
                        .SettingsInputComponent,
                onClick = onPlugins,
            )
        }
    }
}

@Composable
private fun ContentMetric(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                2.dp
            ),
    ) {
        Text(
            value,
            color =
                VueoPalette.Accent,
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Black,
        )

        Text(
            label,
            color =
                VueoPalette.Muted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ContentManagerCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    17.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .background(
                        VueoPalette
                            .SurfaceStrong
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription =
                        null,
                    tint =
                        VueoPalette.Accent,
                )
            }

            Spacer(
                Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        3.dp
                    ),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color =
                        VueoPalette.Muted,
                    fontSize = 11.sp,
                )

                Text(
                    status,
                    color =
                        VueoPalette.Accent,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }

            Text(
                "›",
                color =
                    VueoPalette.Muted,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun AddonsScreen(
    engine: UnifiedMediaEngine,
    store: AddonStore,
    contentVersion: Int,
    onContentChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var installed by remember(contentVersion) {
        mutableStateOf(engine.stremioAddons())
    }
    var showInstallDialog by remember { mutableStateOf(false) }
    var manifestUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshingId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScreenHeader(
            title = "Addons",
            subtitle = "Stremio compatible content sources",
            onBack = onBack,
            action = {
                FilledIconButton(onClick = { showInstallDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add addon")
                }
            },
        )

        if (installed.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(28.dp),
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "No addons installed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                    Text(
                        "Install an HTTPS Stremio manifest URL. Catalogs exposed by the addon can then populate Home.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showInstallDialog = true }) {
                        Text("Add Stremio Addon")
                    }
                }
            }
        } else {
            val groupedAddons = AddonCategory.values().toList()
                .mapNotNull { category ->
                    val addons = installed.filter {
                        it.descriptor.primaryAddonCategory() == category
                    }
                    if (addons.isEmpty()) null else category to addons
                }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                groupedAddons.forEach { (category, addons) ->
                    item(key = "header:${category.name}") {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                category.label.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                                fontSize = 12.sp,
                                letterSpacing = 1.3.sp,
                            )

                            Text(
                                when (category) {
                                    AddonCategory.CATALOG_METADATA ->
                                        "Discovery, catalogs and title information"
                                    AddonCategory.STREAMS ->
                                        "Playback source providers"
                                    AddonCategory.SUBTITLES ->
                                        "Subtitle providers"
                                    AddonCategory.MULTI_PURPOSE ->
                                        "Addons with more than one content capability"
                                    AddonCategory.OTHER ->
                                        "Other Stremio addon resources"
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    items(
                        addons,
                        key = { it.descriptor.id },
                    ) { addon ->
                        AddonCard(
                            addon = addon,
                            isDevelopmentDefault = store.isDevelopmentDefault(
                                addon.descriptor.baseUrl
                            ),
                            refreshing = refreshingId == addon.descriptor.id,
                            onRefresh = {
                                scope.launch {
                                    refreshingId = addon.descriptor.id

                                    runCatching {
                                        ExtensionInstaller.installStremioAddon(
                                            addon.descriptor.baseUrl
                                        )
                                    }.onSuccess {
                                        engine.install(it)
                                        installed = engine.stremioAddons()
                                        onContentChanged()
                                    }

                                    refreshingId = null
                                }
                            },
                            onDelete = {
                                engine.uninstall(addon.descriptor.id)
                                store.remove(addon.descriptor.baseUrl)
                                installed = engine.stremioAddons()
                                onContentChanged()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showInstallDialog = false
                    status = null
                }
            },
            title = { Text("Install Stremio Addon") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Paste the addon's HTTPS manifest URL. VUEO will inspect its resources and catalogs before installing it."
                    )

                    OutlinedTextField(
                        value = manifestUrl,
                        onValueChange = {
                            manifestUrl = it
                            status = null
                        },
                        label = { Text("Manifest URL") },
                        placeholder = { Text("https://.../manifest.json") },
                        enabled = !busy,
                        singleLine = true,
                    )

                    status?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }

                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = manifestUrl.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null

                            runCatching {
                                ExtensionInstaller.installStremioAddon(
                                    manifestUrl.trim()
                                )
                            }.onSuccess { addon ->
                                engine.install(addon)
                                store.add(addon.descriptor.baseUrl)
                                installed = engine.stremioAddons()
                                manifestUrl = ""
                                showInstallDialog = false
                                onContentChanged()
                            }.onFailure {
                                status = it.message ?: "Unable to install addon."
                            }

                            busy = false
                        }
                    },
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showInstallDialog = false
                        status = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AddonCard(
    addon: MediaExtension,
    isDevelopmentDefault: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "S",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        addon.descriptor.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "v${addon.descriptor.version}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            fontSize = 12.sp,
                        )

                        if (isDevelopmentDefault) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                            ) {
                                Text(
                                    "DEV DEFAULT",
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 3.dp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            addon.descriptor.description?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider()

            Text(
                "${addon.descriptor.catalogs.size} catalogs  •  " +
                    "${addon.descriptor.resources.size} resources",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
            )

            if (addon.descriptor.resources.isNotEmpty()) {
                Text(
                    addon.descriptor.resources.sorted().joinToString("  •  "),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PluginsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember {
        PluginStore(
            context.applicationContext
        )
    }
    val scope = rememberCoroutineScope()
    val healthStore = remember {
        PluginHealthStore(context.applicationContext)
    }
    val codeStore = remember {
        ProviderCodeStore(context.applicationContext)
    }
    val codeSync = remember {
        ProviderCodeSyncManager(
            context.applicationContext
        )
    }
    var healthRevision by remember {
        mutableIntStateOf(0)
    }
    var codeRevision by remember {
        mutableIntStateOf(0)
    }

    var repositories by remember {
        mutableStateOf(
            store.repositories()
        )
    }
    var pluginsEnabled by remember {
        mutableStateOf(
            store.pluginsEnabled()
        )
    }
    var showAddDialog by remember {
        mutableStateOf(false)
    }
    var repositoryUrl by remember {
        mutableStateOf("")
    }
    var busy by remember {
        mutableStateOf(false)
    }
    var message by remember {
        mutableStateOf<String?>(null)
    }
    var refreshingUrl by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        store.seedDevelopmentDefaultsIfNeeded()
        repositories =
            store.repositories()

        codeSync.syncMissing(
            repositories
        )
        codeRevision++
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScreenHeader(
            title = "Plugins",
            subtitle =
                "JavaScript provider repositories",
            onBack = onBack,
            action = {
                FilledIconButton(
                    onClick = {
                        showAddDialog = true
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription =
                            "Add repository",
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp,
                    vertical = 8.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            item {
                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier.padding(18.dp),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f),
                        ) {
                            Text(
                                "Plugin providers",
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                            Text(
                                "${repositories.size} repos • " +
                                    "${store.enabledProviderCount()} enabled providers",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = .6f),
                                fontSize = 12.sp,
                            )
                        }

                        Switch(
                            checked =
                                pluginsEnabled,
                            onCheckedChange = {
                                pluginsEnabled = it
                                store.setPluginsEnabled(
                                    it
                                )
                            },
                        )
                    }
                }
            }

            item {
                val summary = healthStore.summary(
                    repositories = repositories,
                    pluginStore = store,
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Provider Health",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                                Text(
                                    "Updated whenever VUEO runs source discovery.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                                    fontSize = 11.sp,
                                )
                            }

                            IconButton(
                                onClick = { healthRevision++ },
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh health",
                                )
                            }
                        }

                        Text(
                            "${summary.online} online • ${summary.slow} slow • " +
                                "${summary.noResults} no results",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )

                        if (
                            summary.needsSetup > 0 ||
                            summary.unavailable > 0 ||
                            summary.blocked > 0 ||
                            summary.timeout > 0 ||
                            summary.failed > 0
                        ) {
                            Text(
                                "${summary.needsSetup} setup • " +
                                    "${summary.unavailable} unavailable • " +
                                    "${summary.blocked} blocked • " +
                                    "${summary.timeout} timeout • " +
                                    "${summary.failed} failed",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                                fontSize = 11.sp,
                            )
                        }

                        if (summary.unknown > 0 || summary.disabled > 0) {
                            Text(
                                "${summary.unknown} unknown • ${summary.disabled} disabled",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }


            if (repositories.isEmpty()) {
                item {
                    ElevatedCard(
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(20.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                ),
                        ) {
                            Text(
                                "No plugin repositories",
                                fontSize = 21.sp,
                                fontWeight =
                                    FontWeight.Black,
                            )
                            Text(
                                "Add a Nuvio-style provider repository URL.",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(
                                            alpha = .68f
                                        ),
                            )
                            Button(
                                onClick = {
                                    showAddDialog =
                                        true
                                },
                            ) {
                                Text(
                                    "Add Repository"
                                )
                            }
                        }
                    }
                }
            }

            items(
                repositories,
                key = {
                    it.manifestUrl
                },
            ) { repository ->
                PluginRepositoryCard(
                    repository = repository,
                    store = store,
                    healthStore = healthStore,
                    healthRevision = healthRevision,
                    codeStore = codeStore,
                    codeRevision = codeRevision,
                    isDevelopmentDefault =
                        store.isDevelopmentDefault(
                            repository.manifestUrl
                        ),
                    refreshing =
                        refreshingUrl ==
                        repository.manifestUrl,
                    onRefresh = {
                        scope.launch {
                            refreshingUrl =
                                repository
                                    .manifestUrl

                            runCatching {
                                PluginRepositoryClient
                                    .fetch(
                                        repository
                                            .manifestUrl
                                    )
                            }.onSuccess {
                                refreshed ->

                                store.upsert(
                                    refreshed
                                )

                                val syncResult =
                                    codeSync.syncRepository(
                                        repository =
                                            refreshed,
                                        force =
                                            true,
                                    )

                                repositories =
                                    store.repositories()
                                codeRevision++

                                message =
                                    "Provider code ready " +
                                    "${syncResult.readyProviders}/" +
                                    "${refreshed.providers.size}"
                            }.onFailure {
                                message =
                                    it.message
                            }

                            refreshingUrl = null
                        }
                    },
                    onDelete = {
                        healthStore.removeRepository(
                            repository.manifestUrl
                        )
                        store.remove(
                            repository.manifestUrl
                        )
                        repositories =
                            store.repositories()
                        healthRevision++
                    },
                    onProviderChanged = {
                        repositories =
                            store.repositories()
                        healthRevision++
                    },
                )
            }

            item {
                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            ),
                    ) {
                        Text(
                            "Runtime status",
                            fontWeight =
                                FontWeight.Bold,
                        )
                        Text(
                            "Provider runtime ACTIVE. " +
                                "VUEO executes locally stored provider code " +
                                "inside QuickJS. Plugin fetch() uses native OkHttp " +
                                "with system DNS plus DNS-over-HTTPS fallback.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showAddDialog = false
                    message = null
                }
            },
            title = {
                Text(
                    "Add Plugin Repository"
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Paste a repository base URL or direct manifest.json URL."
                    )

                    OutlinedTextField(
                        value = repositoryUrl,
                        onValueChange = {
                            repositoryUrl = it
                            message = null
                        },
                        label = {
                            Text(
                                "Repository URL"
                            )
                        },
                        placeholder = {
                            Text(
                                "https://.../manifest.json"
                            )
                        },
                        enabled = !busy,
                        singleLine = true,
                    )

                    message?.let {
                        Text(
                            it,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                            fontSize = 12.sp,
                        )
                    }

                    if (busy) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        repositoryUrl.isNotBlank() &&
                        !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null

                            runCatching {
                                PluginRepositoryClient
                                    .fetch(
                                        repositoryUrl
                                    )
                            }.onSuccess {
                                repository ->

                                store.upsert(
                                    repository
                                )

                                val syncResult =
                                    codeSync.syncRepository(
                                        repository =
                                            repository,
                                        force =
                                            true,
                                    )

                                repositories =
                                    store.repositories()
                                codeRevision++
                                repositoryUrl = ""
                                showAddDialog = false

                                message =
                                    "Installed ${repository.name}. " +
                                    "Provider code ready " +
                                    "${syncResult.readyProviders}/" +
                                    "${repository.providers.size}"
                            }.onFailure {
                                message =
                                    it.message
                                    ?: "Unable to install repository."
                            }

                            busy = false
                        }
                    },
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showAddDialog = false
                        message = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PluginRepositoryCard(
    repository: PluginRepositoryDescriptor,
    store: PluginStore,
    healthStore: PluginHealthStore,
    healthRevision: Int,
    codeStore: ProviderCodeStore,
    codeRevision: Int,
    isDevelopmentDefault: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onProviderChanged: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(
                            RoundedCornerShape(
                                12.dp
                            )
                        )
                        .background(
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                        ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Text(
                        "JS",
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        fontWeight =
                            FontWeight.Black,
                    )
                }

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Text(
                            repository.name,
                            fontWeight =
                                FontWeight.Black,
                            fontSize = 18.sp,
                        )

                        if (
                            isDevelopmentDefault
                        ) {
                            Spacer(
                                Modifier.width(8.dp)
                            )
                            Surface(
                                shape =
                                    RoundedCornerShape(
                                        50
                                    ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                        .copy(
                                            alpha =
                                                .14f
                                        ),
                            ) {
                                Text(
                                    "DEV DEFAULT",
                                    modifier =
                                        Modifier.padding(
                                            horizontal =
                                                7.dp,
                                            vertical =
                                                3.dp,
                                        ),
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    fontSize =
                                        9.sp,
                                    fontWeight =
                                        FontWeight.Black,
                                )
                            }
                        }
                    }

                    Text(
                        "v${repository.version} • " +
                            "${repository.providers.size} providers",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = .55f),
                        fontSize = 12.sp,
                    )

                    val readyProviderCode =
                        remember(
                            repository.manifestUrl,
                            repository.version,
                            codeRevision,
                        ) {
                            codeStore.readyCount(
                                repository
                            )
                        }

                    Text(
                        "Provider code $readyProviderCode/" +
                            "${repository.providers.size} ready locally",
                        color =
                            if (
                                readyProviderCode ==
                                repository.providers.size
                            ) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                                    .copy(alpha = .55f)
                            },
                        fontSize = 11.sp,
                    )
                }

                IconButton(
                    enabled = !refreshing,
                    onClick = onRefresh,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription =
                            "Refresh",
                    )
                }

                IconButton(
                    onClick = onDelete,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Delete",
                        tint =
                            MaterialTheme
                                .colorScheme
                                .error,
                    )
                }
            }

            if (refreshing) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth()
                )
            }

            repository.description?.let {
                Text(
                    it,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .65f),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider()

            repository.providers.forEach { provider ->
                val enabled = store.isProviderEnabled(
                    repository,
                    provider,
                )

                val health = if (healthRevision >= 0) {
                    healthStore.record(
                        repositoryManifestUrl = repository.manifestUrl,
                        providerId = provider.id,
                    )
                } else {
                    null
                }

                ProviderHealthRow(
                    repository = repository,
                    provider = provider,
                    health = health,
                    enabled = enabled,
                    onEnabledChanged = { next ->
                        store.setProviderEnabled(
                            repository,
                            provider,
                            next,
                        )
                        onProviderChanged()
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderHealthRow(
    repository: PluginRepositoryDescriptor,
    provider: com.vueo.app.core.plugin.PluginProviderDescriptor,
    health: ProviderHealthRecord?,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val effectiveStatus = if (!enabled) {
        "Disabled"
    } else {
        health?.status?.label ?: ProviderHealthStatus.UNKNOWN.label
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    provider.name,
                    fontWeight = FontWeight.Bold,
                )

                val details = buildList {
                    if (provider.supportedTypes.isNotEmpty()) {
                        add(provider.supportedTypes.sorted().joinToString("/"))
                    }
                    if (provider.formats.isNotEmpty()) {
                        add(provider.formats.take(3).joinToString(", "))
                    }
                    if (provider.limited) add("limited")
                }.joinToString(" • ")

                if (details.isNotBlank()) {
                    Text(
                        details,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                        fontSize = 11.sp,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    effectiveStatus,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)
                        health?.status in setOf(
                            ProviderHealthStatus.FAILED,
                            ProviderHealthStatus.BLOCKED,
                            ProviderHealthStatus.TIMEOUT,
                            ProviderHealthStatus.UNAVAILABLE,
                        ) -> MaterialTheme.colorScheme.error

                        health?.status == ProviderHealthStatus.NEEDS_SETUP ->
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)

                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )

                health?.responseMs?.let { responseMs ->
                    Text(
                        "${responseMs} ms",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }

        if (
            enabled &&
            health?.status in setOf(
                ProviderHealthStatus.FAILED,
                ProviderHealthStatus.NEEDS_SETUP,
                ProviderHealthStatus.UNAVAILABLE,
                ProviderHealthStatus.BLOCKED,
                ProviderHealthStatus.TIMEOUT,
            )
        ) {
            health?.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error.copy(alpha = .85f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (enabled && health?.status == ProviderHealthStatus.NO_RESULTS) {
            Text(
                "Runtime completed but this title returned no sources.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
                fontSize = 10.sp,
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
        )
    }
}

@Composable
private fun MediaDetailsScreen(
    engine: UnifiedMediaEngine,
    settingsStore: SettingsStore,
    initialItem: MediaItem,
    initialLibraryEntry:
        LibraryPlaybackEntry?,
    onLibraryChanged: () -> Unit,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }
    val libraryStore = remember {
        LibraryStore(
            context.applicationContext
        )
    }
    val pluginEngine = remember {
        PluginSourceEngine(
            context = context,
            store = pluginStore,
        )
    }

    val preferredSourceQuality =
        settingsStore
            .preferredQuality()
            .rankKey

    val showSourceTechnicalDetails =
        settingsStore
            .showSourceTechnicalDetails()

    var item by remember(initialItem) { mutableStateOf(initialItem) }
    var loadingMeta by remember { mutableStateOf(true) }
    var loadingStreams by remember {
        mutableStateOf(false)
    }
    var sourceStatus by remember {
        mutableStateOf<String?>(null)
    }
    var relatedItems by remember {
        mutableStateOf<
            List<MediaItem>
        >(emptyList())
    }
    var relatedUsesTmdb by remember {
        mutableStateOf(false)
    }
    var ratings by remember {
        mutableStateOf<
            List<MediaRating>
        >(emptyList())
    }
    var inWatchlist by remember(
        initialItem.id,
        initialItem.type,
    ) {
        mutableStateOf(
            libraryStore
                .isWatchlisted(
                    initialItem
                )
        )
    }

    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var selectedEpisode by remember { mutableStateOf<EpisodeItem?>(null) }

    var sourcePickerStreams by remember {
        mutableStateOf<List<StreamSource>?>(null)
    }
    var sourcePickerSubtitles by remember {
        mutableStateOf<List<SubtitleTrack>>(emptyList())
    }
    var sourcePickerNotice by remember {
        mutableStateOf<String?>(null)
    }
    var sourcePickerRawCount by remember {
        mutableIntStateOf(0)
    }
    var sourcePickerSearching by remember {
        mutableStateOf(false)
    }
    var sourcePickerProgress by remember {
        mutableStateOf("Ready")
    }
    var sourcePickerFirstResultMs by remember {
        mutableStateOf<Long?>(null)
    }
    var sourceDiscoveryJob by remember {
        mutableStateOf<Job?>(null)
    }
    var selectedPlaybackSource by remember {
        mutableStateOf<StreamSource?>(null)
    }
    var selectedPlaybackVideoId by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(
        initialItem.id,
        initialItem.type,
        initialItem.sourceExtensionId,
    ) {
        loadingMeta = true
        relatedItems = emptyList()
        relatedUsesTmdb = false
        ratings = emptyList()

        val tmdbKey =
            pluginStore
                .tmdbApiKey()

        val preparedItem =
            if (
                tmdbKey.isNotBlank() &&
                initialItem.id
                    .startsWith(
                        "tmdb:"
                    )
            ) {
                runCatching {
                    TmdbEnhancementClient
                        .prepareForCore(
                            item =
                                initialItem,
                            apiKey =
                                tmdbKey,
                        )
                }.getOrDefault(
                    initialItem
                )
            } else {
                initialItem
            }

        val coreItem =
            engine.loadMeta(
                preparedItem
            )

        item =
            if (
                tmdbKey.isNotBlank() &&
                (
                    settingsStore
                        .tmdbMetadataEnrichmentEnabled() ||
                        settingsStore
                            .tmdbArtworkEnrichmentEnabled()
                )
            ) {
                runCatching {
                    TmdbEnhancementClient
                        .enrich(
                            item = coreItem,
                            apiKey =
                                tmdbKey,
                            metadataEnabled =
                                settingsStore
                                    .tmdbMetadataEnrichmentEnabled(),
                            artworkEnabled =
                                settingsStore
                                    .tmdbArtworkEnrichmentEnabled(),
                        )
                }.getOrDefault(
                    coreItem
                )
            } else {
                coreItem
            }

        if (
            item.type == "series" &&
            item.episodes.isNotEmpty()
        ) {
            val requestedSeason =
                initialLibraryEntry
                    ?.season

            val requestedEpisode =
                initialLibraryEntry
                    ?.episode

            val matched =
                if (
                    requestedSeason != null &&
                    requestedEpisode != null
                ) {
                    item.episodes
                        .firstOrNull {
                            it.season ==
                                requestedSeason &&
                                it.episode ==
                                requestedEpisode
                        }
                } else {
                    null
                }

            val firstSeason =
                matched?.season
                    ?: item.episodes
                        .map {
                            it.season
                        }
                        .distinct()
                        .sorted()
                        .firstOrNull()

            selectedSeason =
                firstSeason

            selectedEpisode =
                matched
                    ?: item.episodes
                        .firstOrNull {
                            it.season ==
                                firstSeason
                        }
        } else {
            selectedSeason = null
            selectedEpisode = null
        }

        inWatchlist =
            libraryStore
                .isWatchlisted(
                    item
                )

        val resolvedItem =
            item

        val localRelated =
            CatalogDiscoveryCache
                .related(
                    resolvedItem,
                    limit = 18,
                )

        relatedItems =
            localRelated

        loadingMeta = false

        launch {
            if (
                tmdbKey.isBlank() ||
                (
                    !settingsStore
                        .tmdbRecommendationsEnabled() &&
                        !settingsStore
                            .tmdbSimilarTitlesEnabled()
                )
            ) {
                return@launch
            }

            val tmdbRelated =
                runCatching {
                    TmdbEnhancementClient
                        .moreLikeThis(
                            item =
                                resolvedItem,
                            apiKey =
                                tmdbKey,
                            recommendationsEnabled =
                                settingsStore
                                    .tmdbRecommendationsEnabled(),
                            similarEnabled =
                                settingsStore
                                    .tmdbSimilarTitlesEnabled(),
                            limit = 18,
                        )
                }.getOrDefault(
                    emptyList()
                )

            if (tmdbRelated.isNotEmpty()) {
                relatedUsesTmdb = true
                relatedItems =
                    (
                        tmdbRelated +
                            localRelated
                    )
                        .distinctBy {
                            "${it.type}:${it.id}"
                        }
                        .take(18)
            }
        }

        launch {
            val mdblistKey =
                settingsStore
                    .mdblistApiKey()

            if (
                mdblistKey.isBlank() ||
                !settingsStore
                    .mdblistRatingsEnabled()
            ) {
                return@launch
            }

            val fetched =
                runCatching {
                    MdblistClient
                        .ratings(
                            media =
                                resolvedItem,
                            apiKey =
                                mdblistKey,
                        )
                }.getOrDefault(
                    emptyList()
                )

            ratings =
                fetched.filter {
                    rating ->
                    when (rating.source) {
                        "imdb" ->
                            settingsStore
                                .mdblistImdbEnabled()

                        "tomatoes" ->
                            settingsStore
                                .mdblistRottenTomatoesEnabled()

                        "metacritic" ->
                            settingsStore
                                .mdblistMetacriticEnabled()

                        "tmdb" ->
                            settingsStore
                                .mdblistTmdbRatingEnabled()

                        "trakt" ->
                            settingsStore
                                .mdblistTraktEnabled()

                        else -> false
                    }
                }
        }
    }

    val playbackSource = selectedPlaybackSource
    val playbackVideoId = selectedPlaybackVideoId

    if (playbackSource != null && playbackVideoId != null) {
        PlayerScreen(
            settingsStore =
                settingsStore,
            title = playbackTitle(
                media = item,
                episode = selectedEpisode,
            ),
            mediaKey =
                "${item.type}:${item.id}:$playbackVideoId",
            media = item,
            videoId =
                playbackVideoId,
            episode =
                selectedEpisode,
            source = playbackSource,
            subtitles =
                sourcePickerSubtitles,
            onLibraryChanged =
                onLibraryChanged,
            onBack = {
                selectedPlaybackSource = null
                selectedPlaybackVideoId = null
            },
        )
        return
    }

    sourcePickerStreams?.let { streams ->
        SourcePickerScreen(
            mediaTitle = playbackTitle(
                media = item,
                episode = selectedEpisode,
            ),
            streams = streams,
            rawCount = sourcePickerRawCount,
            notice = sourcePickerNotice,
            searching = sourcePickerSearching,
            progressText = sourcePickerProgress,
            firstResultMs = sourcePickerFirstResultMs,
            showTechnicalDetails =
                showSourceTechnicalDetails,
            onBack = {
                sourceDiscoveryJob?.cancel()
                sourceDiscoveryJob = null
                sourcePickerSearching = false
                loadingStreams = false
                sourcePickerStreams = null
            },
            onPlay = { source ->
                sourceDiscoveryJob?.cancel()
                sourceDiscoveryJob = null
                sourcePickerSearching = false

                val videoId = selectedVideoId(item, selectedEpisode)

                if (videoId != null && source.isDirectPlayable) {
                    selectedPlaybackVideoId = videoId
                    selectedPlaybackSource = source
                }
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                VueoPalette.Background
            ),
        contentPadding =
            PaddingValues(
                bottom = 32.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                18.dp
            ),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp),
            ) {
                NetworkImage(
                    url =
                        item.background
                            ?: item.poster,
                    contentDescription =
                        item.name,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop,
                    fallbackText =
                        item.name,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black
                                                .copy(
                                                    alpha = .12f
                                                ),
                                            Color.Black
                                                .copy(
                                                    alpha = .30f
                                                ),
                                            VueoPalette
                                                .Background
                                                .copy(
                                                    alpha = .98f
                                                ),
                                        ),
                                )
                        ),
                )

                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(
                            Alignment.TopStart
                        ),
                    shape =
                        RoundedCornerShape(
                            50
                        ),
                    color =
                        Color.Black.copy(
                            alpha = .52f
                        ),
                ) {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.Default
                                .ArrowBack,
                            contentDescription =
                                "Back",
                            tint = Color.White,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(
                            Alignment.BottomStart
                        )
                        .padding(
                            horizontal = 20.dp,
                            vertical = 18.dp,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        ),
                ) {
                    Surface(
                        shape =
                            RoundedCornerShape(
                                50
                            ),
                        color =
                            VueoPalette.Accent
                                .copy(
                                    alpha = .15f
                                ),
                    ) {
                        Text(
                            item.type
                                .replaceFirstChar {
                                    it.uppercase()
                                },
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        10.dp,
                                    vertical =
                                        5.dp,
                                ),
                            color =
                                VueoPalette.Accent,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Black,
                        )
                    }

                    Text(
                        item.name,
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight =
                            FontWeight.Black,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    Text(
                        listOfNotNull(
                            item.releaseInfo,
                            item.genres
                                .take(3)
                                .takeIf {
                                    it.isNotEmpty()
                                }
                                ?.joinToString(
                                    " • "
                                ),
                        ).joinToString(
                            "  •  "
                        ),
                        color =
                            Color.White.copy(
                                alpha = .68f
                            ),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (loadingMeta) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }

        if (
            item.genres.isNotEmpty()
        ) {
            item {
                LazyRow(
                    contentPadding =
                        PaddingValues(
                            horizontal = 20.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        item.genres.take(8)
                    ) { genre ->
                        Surface(
                            shape =
                                RoundedCornerShape(
                                    50
                                ),
                            color =
                                VueoPalette
                                    .SurfaceStrong,
                        ) {
                            Text(
                                genre,
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            11.dp,
                                        vertical =
                                            6.dp,
                                    ),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        if (ratings.isNotEmpty()) {
            item {
                MediaRatingsStrip(
                    ratings = ratings
                )
            }
        }

        item.description?.let {
            description ->

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp
                        ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                VueoPalette
                                    .Surface
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                17.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            ),
                    ) {
                        Text(
                            "OVERVIEW",
                            color =
                                VueoPalette.Accent,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Black,
                            letterSpacing =
                                1.2.sp,
                        )

                        Text(
                            description,
                            color =
                                Color.White
                                    .copy(
                                        alpha = .72f
                                    ),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (item.type == "series" && item.episodes.isNotEmpty()) {
            item {
                SeasonSelector(
                    episodes = item.episodes,
                    selectedSeason = selectedSeason,
                    onSelectSeason = { season ->
                        selectedSeason = season
                        selectedEpisode = item.episodes
                            .firstOrNull { it.season == season }
                        sourceStatus = null
                    },
                )
            }

            item {
                EpisodeSelector(
                    episodes = item.episodes.filter {
                        it.season == selectedSeason
                    },
                    selectedEpisode = selectedEpisode,
                    onSelectEpisode = {
                        selectedEpisode = it
                        sourceStatus = null
                    },
                )
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val videoId = selectedVideoId(item, selectedEpisode)
                val seriesNeedsEpisode =
                    item.type == "series" && item.episodes.isNotEmpty()

                Button(
                    enabled = !loadingStreams &&
                        videoId != null &&
                        (!seriesNeedsEpisode || selectedEpisode != null),
onClick = {
    val targetVideoId =
        videoId
            ?: return@Button

    sourceDiscoveryJob
        ?.cancel()

    val cacheKey =
        SourceDiscoveryCache.key(
            mediaType =
                item.type,
            mediaId =
                item.id,
            videoId =
                targetVideoId,
        )

    val cached =
        SourceDiscoveryCache
            .get(cacheKey)

    sourcePickerStreams =
        cached?.streams
            ?: emptyList()

    sourcePickerRawCount =
        cached?.rawCount
            ?: 0

    sourcePickerNotice =
        cached?.notice

    sourcePickerSearching =
        true

    sourcePickerFirstResultMs =
        null

    sourcePickerProgress =
        if (cached != null) {
            "Recent sources loaded instantly • refreshing in background"
        } else {
            "Starting source discovery…"
        }

    loadingStreams = true
    sourceStatus = null

    sourceDiscoveryJob =
        scope.launch {
            val startedAt =
                System.nanoTime()

            val cachedStreams =
                cached?.streams
                    .orEmpty()

            var freshAddonStreams =
                emptyList<StreamSource>()

            var freshPluginStreams =
                emptyList<StreamSource>()

            var addonRawCount = 0
            var pluginRawCount = 0

            var addonCompleted = 0
            var addonTotal = 0

            var pluginCompleted = 0
            var pluginTotal = 0

            fun elapsedMs(): Long =
                (
                    System.nanoTime() -
                        startedAt
                ) / 1_000_000L

            fun publish(
                progress: String,
            ) {
                val fresh =
                    SourceCleaner.clean(
                        sources =
                            freshAddonStreams +
                                freshPluginStreams,
                        preferredQuality =
                            preferredSourceQuality,
                    )

                val display =
                    if (
                        sourcePickerSearching
                    ) {
                        SourceCleaner.clean(
                            sources =
                                cachedStreams +
                                    fresh,
                            preferredQuality =
                                preferredSourceQuality,
                        )
                    } else {
                        fresh
                    }

                if (
                    sourcePickerFirstResultMs ==
                    null &&
                    display.isNotEmpty() &&
                    cachedStreams.isEmpty()
                ) {
                    sourcePickerFirstResultMs =
                        elapsedMs()
                }

                sourcePickerStreams =
                    display

                sourcePickerRawCount =
                    maxOf(
                        cached?.rawCount
                            ?: 0,
                        addonRawCount +
                            pluginRawCount,
                    )

                sourcePickerProgress =
                    progress
            }

            val subtitlesDeferred =
                async {
                    runCatching {
                        engine.resolveSubtitles(
                            type =
                                item.type,
                            videoId =
                                targetVideoId,
                        )
                    }.getOrDefault(
                        emptyList()
                    )
                }

            val addonDeferred =
                async {
                    runCatching {
                        engine
                            .resolveStreamsProgressive(
                                type =
                                    item.type,
                                videoId =
                                    targetVideoId,
                            ) { progress ->
                                freshAddonStreams =
                                    progress.streams

                                addonRawCount =
                                    progress.rawCount

                                addonCompleted =
                                    progress.completedAddons

                                addonTotal =
                                    progress.totalAddons

                                publish(
                                    "Searching • Addons " +
                                        "$addonCompleted/$addonTotal • " +
                                        "Plugins $pluginCompleted/$pluginTotal"
                                )
                            }
                    }.getOrElse {
                        emptyList()
                    }
                }

            val pluginDeferred =
                async {
                    if (
                        !pluginStore
                            .pluginsEnabled() ||
                        pluginStore
                            .repositories()
                            .isEmpty()
                    ) {
                        return@async null
                    }

                    val tmdbId =
                        runCatching {
                            TmdbResolver.resolve(
                                media =
                                    item,
                                apiKey =
                                    pluginStore
                                        .tmdbApiKey(),
                            )
                        }.getOrNull()

                    if (tmdbId == null) {
                        sourcePickerNotice =
                            "Plugin providers skipped: VUEO could not resolve a TMDB ID. Add your TMDB API key in Settings > Enhancements > TMDB."

                        return@async null
                    }

                    val mediaType =
                        if (
                            item.type ==
                            "series"
                        ) {
                            "tv"
                        } else {
                            "movie"
                        }

                    runCatching {
                        pluginEngine
                            .discoverProgressive(
                                tmdbId =
                                    tmdbId,
                                mediaType =
                                    mediaType,
                                season =
                                    selectedEpisode
                                        ?.season,
                                episode =
                                    selectedEpisode
                                        ?.episode,
                            ) { progress ->
                                freshPluginStreams =
                                    progress
                                        .result
                                        .streams

                                pluginRawCount =
                                    freshPluginStreams
                                        .size

                                pluginCompleted =
                                    progress
                                        .completedProviders

                                pluginTotal =
                                    progress
                                        .totalProviders

                                publish(
                                    "Searching • Addons " +
                                        "$addonCompleted/$addonTotal • " +
                                        "Plugins $pluginCompleted/$pluginTotal • " +
                                        "${
                                            SourceCleaner.clean(
                                                sources =
                                                    freshAddonStreams +
                                                        freshPluginStreams,
                                                preferredQuality =
                                                    preferredSourceQuality,
                                            ).size
                                        } fresh sources"
                                )
                            }
                    }.getOrNull()
                }

            val finalAddonStreams =
                addonDeferred.await()

            val pluginResult =
                pluginDeferred.await()

            sourcePickerSubtitles =
                subtitlesDeferred.await()

            freshAddonStreams =
                finalAddonStreams

            if (pluginResult != null) {
                freshPluginStreams =
                    pluginResult.streams

                pluginRawCount =
                    pluginResult.streams.size

                sourcePickerNotice =
                    "Plugins: ${pluginResult.attemptedProviders} checked • " +
                        "${pluginResult.successfulProviders} online • " +
                        "${pluginResult.slowProviders} slow • " +
                        "${pluginResult.noResultProviders} no results • " +
                        "${pluginResult.needsSetupProviders} setup • " +
                        "${pluginResult.unavailableProviders} unavailable • " +
                        "${pluginResult.blockedProviders} blocked • " +
                        "${pluginResult.timeoutProviders} timeout • " +
                        "${pluginResult.failedProviders} failed."
            }

            val freshFinal =
                SourceCleaner.clean(
                    sources =
                        freshAddonStreams +
                            freshPluginStreams,
                    preferredQuality =
                        preferredSourceQuality,
                )

            val finalStreams =
                if (
                    freshFinal.isNotEmpty()
                ) {
                    freshFinal
                } else {
                    cachedStreams
                }

            sourcePickerStreams =
                finalStreams

            sourcePickerRawCount =
                maxOf(
                    cached?.rawCount
                        ?: 0,
                    addonRawCount +
                        pluginRawCount,
                )

            sourcePickerSearching =
                false

            loadingStreams = false

            sourcePickerProgress =
                if (
                    finalStreams.isEmpty()
                ) {
                    "Search complete • no sources found"
                } else {
                    "Search complete • ${finalStreams.size} unique sources"
                }

            if (
                finalStreams.isNotEmpty()
            ) {
                SourceDiscoveryCache.put(
                    key =
                        cacheKey,
                    streams =
                        finalStreams,
                    rawCount =
                        sourcePickerRawCount,
                    notice =
                        sourcePickerNotice,
                )
            }
        }
},
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (loadingStreams) {
                            "Finding Sources…"
                        } else {
                            "Watch"
                        }
                    )
                }

                OutlinedButton(
                    modifier =
                        Modifier.fillMaxWidth(),
                    onClick = {
                        inWatchlist =
                            libraryStore
                                .toggleWatchlist(
                                    item
                                )

                        onLibraryChanged()
                    },
                ) {
                    Icon(
                        if (inWatchlist) {
                            Icons.Default.VideoLibrary
                        } else {
                            Icons.Default.Add
                        },
                        contentDescription =
                            null,
                    )

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Text(
                        if (inWatchlist) {
                            "In My List"
                        } else {
                            "Add to My List"
                        }
                    )
                }

                Text(
                    "VUEO will find and rank the best available sources.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .5f),
                    fontSize = 11.sp,
                )

                sourceStatus?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    )
                }

                if (item.type == "series" && item.episodes.isEmpty() && !loadingMeta) {
                    Text(
                        "This metadata provider did not return episode video IDs, so VUEO cannot request episode streams yet.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (
            relatedItems.isNotEmpty()
        ) {
            item {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "More Like This",
                        color = Color.White,
                        modifier =
                            Modifier.padding(
                                horizontal = 20.dp
                            ),
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Black,
                    )

                    Text(
                        if (relatedUsesTmdb) {
                            "TMDB enhanced • VUEO fallback ready"
                        } else {
                            "From your VUEO catalog"
                        },
                        color =
                            Color.White.copy(
                                alpha = .48f
                            ),
                        modifier =
                            Modifier.padding(
                                horizontal = 20.dp
                            ),
                        fontSize = 11.sp,
                    )

                    LazyRow(
                        contentPadding =
                            PaddingValues(
                                horizontal = 20.dp
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            ),
                    ) {
                        items(
                            relatedItems,
                            key = {
                                "${it.type}:${it.id}"
                            },
                        ) { related ->
                            MediaPoster(
                                item =
                                    related,
                                onClick = {
                                    onMediaClick(
                                        related
                                    )
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
private fun MediaRatingsStrip(
    ratings: List<MediaRating>,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 20.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                "RATINGS",
                color =
                    VueoPalette.Accent,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Black,
                letterSpacing = 1.2.sp,
            )

            Spacer(
                Modifier.width(8.dp)
            )

            Text(
                "via MDBList",
                color =
                    Color.White.copy(
                        alpha = .45f
                    ),
                fontSize = 10.sp,
            )
        }

        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            items(
                ratings,
                key = {
                    it.source
                },
            ) { rating ->
                Surface(
                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),
                    color =
                        VueoPalette
                            .SurfaceStrong,
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 9.dp,
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            rating.compactLabel,
                            color =
                                Color.White.copy(
                                    alpha = .62f
                                ),
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Bold,
                        )

                        Text(
                            rating.displayValue(),
                            color =
                                Color.White,
                            fontSize = 13.sp,
                            fontWeight =
                                FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonSelector(
    episodes: List<EpisodeItem>,
    selectedSeason: Int?,
    onSelectSeason: (Int) -> Unit,
) {
    val seasons = episodes
        .map { it.season }
        .distinct()
        .sorted()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Seasons",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seasons) { season ->
                if (season == selectedSeason) {
                    Button(onClick = { onSelectSeason(season) }) {
                        Text("Season $season")
                    }
                } else {
                    OutlinedButton(onClick = { onSelectSeason(season) }) {
                        Text("Season $season")
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSelector(
    episodes: List<EpisodeItem>,
    selectedEpisode: EpisodeItem?,
    onSelectEpisode: (EpisodeItem) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Episodes",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                episodes,
                key = { it.id },
            ) { episode ->
                val selected = episode.id == selectedEpisode?.id

                ElevatedCard(
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onSelectEpisode(episode) },
                ) {
                    Column {
                        NetworkImage(
                            url = episode.thumbnail,
                            contentDescription = episode.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp),
                            fallbackText = episode.title,
                        )

                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "E${episode.episode} • ${episode.title}",
                                fontWeight = if (selected) {
                                    FontWeight.Black
                                } else {
                                    FontWeight.Bold
                                },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            episode.overview?.let {
                                Text(
                                    it,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = .58f
                                    ),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerScreen(
    mediaTitle: String,
    streams: List<StreamSource>,
    rawCount: Int,
    notice: String?,
    searching: Boolean,
    progressText: String,
    firstResultMs: Long?,
    showTechnicalDetails: Boolean,
    onBack: () -> Unit,
    onPlay: (StreamSource) -> Unit,
) {
    val playable = streams.filter { it.isDirectPlayable }
    val best = playable.firstOrNull()

    val qualityGroups =
        listOf(
            "4K",
            "1080p",
            "720p",
            "Other",
        ).mapNotNull { bucket ->
            val matches =
                streams.filter {
                    SourceCleaner
                        .qualityBucket(it) ==
                        bucket
                }

            if (matches.isEmpty()) {
                null
            } else {
                bucket to matches
            }
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                VueoPalette.Background
            ),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Choose a Source",
                subtitle = mediaTitle,
                onBack = onBack,
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            VueoPalette
                                .SurfaceElevated
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Text(
                            if (searching) {
                                "SMART SOURCE ENGINE"
                            } else {
                                "SOURCE ENGINE"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontWeight =
                                FontWeight.Black,
                            fontSize = 11.sp,
                            modifier =
                                Modifier.weight(1f),
                        )

                        Text(
                            if (searching) {
                                "LIVE"
                            } else {
                                "READY"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Bold,
                        )
                    }

                    if (searching) {
                        LinearProgressIndicator(
                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        progressText,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = .68f),
                        fontSize = 12.sp,
                    )

                    firstResultMs?.let {
                        Text(
                            "First source in ${it} ms",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                                    .copy(alpha = .5f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        if (!notice.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp
                        ),
                    shape =
                        RoundedCornerShape(
                            16.dp
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                VueoPalette
                                    .Surface
                        ),
                ) {
                    Text(
                        notice,
                        modifier =
                            Modifier.padding(14.dp),
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = .65f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (best != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp
                        ),
                    shape =
                        RoundedCornerShape(
                            22.dp
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                VueoPalette
                                    .SurfaceElevated
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            "VUEO RECOMMENDS",
                            color = VueoPalette.Accent,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                        )

                        Text(
                            best.quality ?: "Direct stream",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Text(
                            listOfNotNull(
                                best.codec,
                                best.hdr,
                                best.audio,
                                best.providerName,
                            ).joinToString(" • "),
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = .66f
                            ),
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPlay(best) },
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Play Best")
                        }
                    }
                }
            }
        } else if (!searching) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        if (streams.isEmpty()) {
                            "No sources were returned for this title."
                        } else {
                            "Sources were found, but none are direct HTTPS/HLS/DASH streams that the current VUEO player can play. Torrent/debrid playback is a later layer."
                        },
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    )
                }
            }
        }

        if (streams.isEmpty() && searching) {
            item {
                Text(
                    "Sources will appear here as soon as a provider responds.",
                    modifier =
                        Modifier.padding(
                            horizontal = 20.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .5f),
                    fontSize = 12.sp,
                )
            }
        }

        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Unique Sources (${streams.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )

                if (
                    rawCount > streams.size
                ) {
                    Text(
                        "$rawCount raw results analysed • " +
                            "${rawCount - streams.size} exact duplicates removed",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = .5f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        qualityGroups.forEach {
            (bucket, sources) ->

            item(
                key = "group:$bucket"
            ) {
                Text(
                    "$bucket • ${sources.size}",
                    modifier =
                        Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 3.dp,
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = .62f),
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }

            items(
                sources,
                key = {
                    listOf(
                        bucket,
                        it.url,
                        it.infoHash,
                        it.fileIndex,
                        it.providerId,
                        it.name,
                    ).joinToString(":")
                },
            ) { source ->
                StreamSourceCard(
                    source = source,
                    showTechnicalDetails =
                        showTechnicalDetails,
                    onClick =
                        if (
                            source.isDirectPlayable
                        ) {
                            {
                                onPlay(source)
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun StreamSourceCard(
    source: StreamSource,
    showTechnicalDetails: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape =
            RoundedCornerShape(
                16.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette.Surface
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    6.dp
                ),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    shape =
                        RoundedCornerShape(
                            50
                        ),
                    color =
                        VueoPalette.Accent
                            .copy(
                                alpha = .12f
                            ),
                ) {
                    Text(
                        source.quality
                            ?: "Source",
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    9.dp,
                                vertical =
                                    5.dp,
                            ),
                        color =
                            VueoPalette.Accent,
                        fontWeight =
                            FontWeight.Black,
                        fontSize = 11.sp,
                    )
                }

                Spacer(
                    Modifier.width(8.dp)
                )

                Text(
                    source.providerName,
                    modifier =
                        Modifier.weight(1f),
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    when {
                        source
                            .isDirectPlayable ->
                            "READY"

                        source.infoHash !=
                            null ->
                            "TORRENT"

                        else ->
                            "OTHER"
                    },
                    color =
                        if (
                            source
                                .isDirectPlayable
                        ) {
                            VueoPalette.Success
                        } else {
                            VueoPalette.Muted
                        },
                    fontSize = 9.sp,
                    fontWeight =
                        FontWeight.Black,
                    letterSpacing = .8.sp,
                )
            }

            Text(
                source.name,
                color =
                    Color.White.copy(
                        alpha = .86f
                    ),
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )

            val tags =
                listOfNotNull(
                    source.codec,
                    source.hdr,
                    source.audio,
                ).joinToString(" • ")

            if (
                showTechnicalDetails &&
                tags.isNotBlank()
            ) {
                Text(
                    tags,
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    settingsStore: SettingsStore,
    title: String,
    mediaKey: String,
    media: MediaItem,
    videoId: String,
    episode: EpisodeItem?,
    source: StreamSource,
    subtitles: List<SubtitleTrack>,
    onLibraryChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context =
        LocalContext.current

    val playbackStore =
        remember {
            PlaybackStore(
                context.applicationContext
            )
        }

    val libraryStore =
        remember {
            LibraryStore(
                context.applicationContext
            )
        }

    val savedPositionMs =
        remember(mediaKey) {
            playbackStore.positionMs(
                mediaKey
            )
        }

    val resumePlaybackEnabled =
        remember(mediaKey) {
            settingsStore
                .resumePlaybackEnabled()
        }

    val initialPlaybackPositionMs =
        if (
            resumePlaybackEnabled
        ) {
            savedPositionMs
        } else {
            0L
        }

    var resumePromptVisible by remember(
        mediaKey
    ) {
        mutableStateOf(
            resumePlaybackEnabled &&
                savedPositionMs >
                    5_000L
        )
    }

    var playbackError by remember {
        mutableStateOf<String?>(null)
    }

    var isBuffering by remember {
        mutableStateOf(false)
    }

    var audioTracks by remember {
        mutableStateOf<
            List<PlayerTrackChoice>
        >(emptyList())
    }

    var textTracks by remember {
        mutableStateOf<
            List<PlayerTrackChoice>
        >(emptyList())
    }

    var showAudioDialog by remember {
        mutableStateOf(false)
    }

    var showSubtitleDialog by remember {
        mutableStateOf(false)
    }

    val player = remember(
        source.url,
        source.headers,
        mediaKey,
    ) {
        val httpFactory =
            DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "VUEO/${BuildConfig.VERSION_NAME}"
                )
                .setAllowCrossProtocolRedirects(
                    true
                )
                .setDefaultRequestProperties(
                    source.headers
                )

        val mediaSourceFactory =
            DefaultMediaSourceFactory(
                context
            ).setDataSourceFactory(
                httpFactory
            )

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                mediaSourceFactory
            )
            .build()
            .apply {
                val mediaItem =
                    buildPlayerMediaItem(
                        sourceUrl =
                            requireNotNull(
                                source.url
                            ),
                        subtitles =
                            subtitles,
                        preferredLanguageCode =
                            settingsStore
                                .preferredSubtitleLanguage()
                                .languageCode,
                        secondaryLanguageCode =
                            settingsStore
                                .secondarySubtitleLanguage()
                                .languageCode,
                        subtitlesOnByDefault =
                            settingsStore
                                .subtitlesOnByDefault(),
                        autoSelectPreferred =
                            settingsStore
                                .autoSelectPreferredSubtitle(),
                        embeddedPriority =
                            settingsStore
                                .embeddedSubtitlePriority(),
                    )

                setMediaItem(mediaItem)

                trackSelectionParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_TEXT,
                            !settingsStore
                                .subtitlesOnByDefault(),
                        )
                        .build()

                prepare()

                playWhenReady =
                    !resumePlaybackEnabled ||
                        savedPositionMs <=
                            5_000L
            }
    }

    fun recordLibraryProgress() {
        libraryStore.recordPlayback(
            media = media,
            videoId = videoId,
            episodeTitle =
                episode?.title,
            season =
                episode?.season,
            episode =
                episode?.episode,
            positionMs =
                player.currentPosition,
            durationMs =
                player.duration,
        )
    }

    LaunchedEffect(
        mediaKey,
    ) {
        libraryStore.recordPlayback(
            media = media,
            videoId = videoId,
            episodeTitle =
                episode?.title,
            season =
                episode?.season,
            episode =
                episode?.episode,
            positionMs =
                initialPlaybackPositionMs,
            durationMs =
                playbackStore.durationMs(
                    mediaKey
                ),
        )
    }

    fun refreshTrackChoices(
        tracks: Tracks =
            player.currentTracks,
    ) {
        audioTracks =
            playerTrackChoices(
                tracks = tracks,
                trackType =
                    C.TRACK_TYPE_AUDIO,
            )

        textTracks =
            playerTrackChoices(
                tracks = tracks,
                trackType =
                    C.TRACK_TYPE_TEXT,
            )
    }

    DisposableEffect(
        player,
        mediaKey,
    ) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(
                    error:
                        PlaybackException,
                ) {
                    playbackError =
                        friendlyPlaybackError(
                            error
                        )

                    isBuffering = false
                }

                override fun onTracksChanged(
                    tracks: Tracks,
                ) {
                    refreshTrackChoices(
                        tracks
                    )
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int,
                ) {
                    isBuffering =
                        playbackState ==
                            Player.STATE_BUFFERING

                    if (
                        playbackState ==
                        Player.STATE_READY
                    ) {
                        playbackError = null
                    }

                    if (
                        playbackState ==
                        Player.STATE_ENDED
                    ) {
                        playbackStore
                            .clearPosition(
                                mediaKey
                            )

                        libraryStore
                            .recordPlayback(
                                media = media,
                                videoId =
                                    videoId,
                                episodeTitle =
                                    episode?.title,
                                season =
                                    episode?.season,
                                episode =
                                    episode?.episode,
                                positionMs =
                                    player.duration
                                        .coerceAtLeast(
                                            0L
                                        ),
                                durationMs =
                                    player.duration
                                        .coerceAtLeast(
                                            0L
                                        ),
                            )

                        onLibraryChanged()
                    }
                }

                override fun onIsPlayingChanged(
                    isPlaying: Boolean,
                ) {
                    if (!isPlaying) {
                        playbackStore
                            .savePositionMs(
                                mediaKey =
                                    mediaKey,
                                positionMs =
                                    player
                                        .currentPosition,
                                durationMs =
                                    player.duration,
                            )

                        recordLibraryProgress()
                    }
                }
            }

        player.addListener(
            listener
        )

        refreshTrackChoices()

        onDispose {
            player.removeListener(
                listener
            )

            playbackStore
                .savePositionMs(
                    mediaKey =
                        mediaKey,
                    positionMs =
                        player
                            .currentPosition,
                    durationMs =
                        player.duration,
                )

            recordLibraryProgress()
            onLibraryChanged()

            player.release()
        }
    }

    LaunchedEffect(
        player,
        mediaKey,
    ) {
        var librarySaveTicks = 0

        while (true) {
            delay(10_000L)

            playbackStore
                .savePositionMs(
                    mediaKey =
                        mediaKey,
                    positionMs =
                        player.currentPosition,
                    durationMs =
                        player.duration,
                )

            librarySaveTicks++

            if (
                librarySaveTicks >= 3
            ) {
                recordLibraryProgress()
                librarySaveTicks = 0
            }
        }
    }

    PlayerFullscreenEffect(
        context = context,
    )

    if (resumePromptVisible) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("Resume playback?")
            },
            text = {
                Text(
                    "Continue from " +
                        formatPlaybackTime(
                            savedPositionMs
                        ) +
                        " or start from the beginning."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        player.seekTo(
                            savedPositionMs
                        )
                        player.playWhenReady =
                            true
                        resumePromptVisible =
                            false
                    },
                ) {
                    Text(
                        "Resume " +
                            formatPlaybackTime(
                                savedPositionMs
                            )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        playbackStore
                            .clearPosition(
                                mediaKey
                            )
                        player.seekTo(0L)
                        player.playWhenReady =
                            true
                        resumePromptVisible =
                            false
                    },
                ) {
                    Text("Start Over")
                }
            },
        )
    }

    if (showAudioDialog) {
        PlayerTrackDialog(
            title = "Audio",
            tracks = audioTracks,
            automaticLabel = "Auto",
            offLabel = null,
            onAutomatic = {
                clearTrackOverride(
                    player =
                        player,
                    trackType =
                        C.TRACK_TYPE_AUDIO,
                    disable = false,
                )
                showAudioDialog = false
            },
            onOff = null,
            onSelect = {
                choice ->

                applyTrackChoice(
                    player =
                        player,
                    trackType =
                        C.TRACK_TYPE_AUDIO,
                    choice =
                        choice,
                )
                showAudioDialog = false
            },
            onDismiss = {
                showAudioDialog = false
            },
        )
    }

    if (showSubtitleDialog) {
        PlayerTrackDialog(
            title = "Subtitles",
            tracks = textTracks,
            automaticLabel = "Auto",
            offLabel = "Off",
            onAutomatic = {
                clearTrackOverride(
                    player =
                        player,
                    trackType =
                        C.TRACK_TYPE_TEXT,
                    disable = false,
                )
                showSubtitleDialog = false
            },
            onOff = {
                clearTrackOverride(
                    player =
                        player,
                    trackType =
                        C.TRACK_TYPE_TEXT,
                    disable = true,
                )
                showSubtitleDialog = false
            },
            onSelect = {
                choice ->

                applyTrackChoice(
                    player =
                        player,
                    trackType =
                        C.TRACK_TYPE_TEXT,
                    choice =
                        choice,
                )
                showSubtitleDialog = false
            },
            onDismiss = {
                showSubtitleDialog = false
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier =
                Modifier.fillMaxSize(),
            factory = {
                playerContext ->

                PlayerView(
                    playerContext
                ).apply {
                    this.player =
                        player
                    useController = true
                    keepScreenOn = true
                }
            },
            update = {
                view ->

                view.player = player
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(
                    Alignment.TopCenter
                )
                .background(
                    Color.Black.copy(
                        alpha = .58f
                    )
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription =
                        "Back",
                    tint = Color.White,
                )
            }

            Column(
                modifier =
                    Modifier.weight(1f),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )

                Text(
                    source.providerName +
                        " • " +
                        (
                            source.quality
                                ?: "Auto"
                        ),
                    color =
                        Color.White.copy(
                            alpha = .62f
                        ),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }

            if (isBuffering) {
                Text(
                    "BUFFERING",
                    color =
                        Color.White.copy(
                            alpha = .72f
                        ),
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(
                    Alignment.BottomCenter
                )
                .background(
                    Color.Black.copy(
                        alpha = .62f
                    )
                )
                .padding(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            playbackError?.let {
                error ->

                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                    ) {
                        Text(
                            "Playback problem",
                            fontWeight =
                                FontWeight.Black,
                        )

                        Text(
                            error,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                                    .copy(
                                        alpha = .72f
                                    ),
                            fontSize = 12.sp,
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                ),
                        ) {
                            Button(
                                onClick = {
                                    playbackError =
                                        null
                                    player.prepare()
                                    player.playWhenReady =
                                        true
                                },
                            ) {
                                Text("Retry")
                            }

                            OutlinedButton(
                                onClick = onBack,
                            ) {
                                Text(
                                    "Other Source"
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    ),
            ) {
                OutlinedButton(
                    modifier =
                        Modifier.weight(1f),
                    enabled =
                        audioTracks
                            .isNotEmpty(),
                    onClick = {
                        showAudioDialog =
                            true
                    },
                ) {
                    Text(
                        if (
                            audioTracks
                                .isEmpty()
                        ) {
                            "Audio"
                        } else {
                            "Audio " +
                                audioTracks.size
                        }
                    )
                }

                OutlinedButton(
                    modifier =
                        Modifier.weight(1f),
                    enabled =
                        textTracks
                            .isNotEmpty() ||
                        subtitles
                            .isNotEmpty(),
                    onClick = {
                        showSubtitleDialog =
                            true
                    },
                ) {
                    Text(
                        "Subtitles " +
                            maxOf(
                                textTracks.size,
                                subtitles.size,
                            )
                    )
                }
            }

            Text(
                buildString {
                    append(
                        source.quality
                            ?: "Direct stream"
                    )

                    source.codec
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            append(" • ")
                            append(it)
                        }

                    source.hdr
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            append(" • ")
                            append(it)
                        }

                    append(" • ")
                    append(
                        source.providerName
                    )
                },
                color =
                    Color.White.copy(
                        alpha = .62f
                    ),
                fontSize = 11.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerFullscreenEffect(
    context: android.content.Context,
) {
    DisposableEffect(context) {
        val activity =
            context as? Activity

        val window =
            activity?.window

        val decor =
            window?.decorView

        val previousFlags =
            decor?.systemUiVisibility
                ?: 0

        if (
            Build.VERSION.SDK_INT >= 30
        ) {
            window
                ?.insetsController
                ?.apply {
                    hide(
                        WindowInsets.Type
                            .systemBars()
                    )

                    systemBarsBehavior =
                        WindowInsetsController
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            if (
                Build.VERSION.SDK_INT >= 30
            ) {
                window
                    ?.insetsController
                    ?.show(
                        WindowInsets.Type
                            .systemBars()
                    )
            } else {
                decor?.systemUiVisibility =
                    previousFlags
            }
        }
    }
}

private data class PlayerTrackChoice(
    val key: String,
    val label: String,
    val override:
        TrackSelectionOverride,
    val selected: Boolean,
)

private fun playerTrackChoices(
    tracks: Tracks,
    trackType: Int,
): List<PlayerTrackChoice> {
    val result =
        mutableListOf<
            PlayerTrackChoice
        >()

    tracks.groups.forEachIndexed {
        groupIndex,
        group ->

        if (
            group.type !=
            trackType
        ) {
            return@forEachIndexed
        }

        for (
            trackIndex in
            0 until group.length
        ) {
            if (
                !group.isTrackSupported(
                    trackIndex
                )
            ) {
                continue
            }

            val format =
                group.getTrackFormat(
                    trackIndex
                )

            val label =
                buildTrackLabel(
                    trackType =
                        trackType,
                    formatLabel =
                        format.label,
                    language =
                        format.language,
                    channelCount =
                        format.channelCount,
                    sampleMimeType =
                        format.sampleMimeType,
                    fallbackIndex =
                        result.size + 1,
                )

            result +=
                PlayerTrackChoice(
                    key =
                        "$groupIndex:" +
                            trackIndex,
                    label = label,
                    override =
                        TrackSelectionOverride(
                            group.mediaTrackGroup,
                            trackIndex,
                        ),
                    selected =
                        group
                            .isTrackSelected(
                                trackIndex
                            ),
                )
        }
    }

    return result
}

private fun buildTrackLabel(
    trackType: Int,
    formatLabel: String?,
    language: String?,
    channelCount: Int,
    sampleMimeType: String?,
    fallbackIndex: Int,
): String {
    val primary =
        formatLabel
            ?.takeIf {
                it.isNotBlank()
            }
            ?: language
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.uppercase()
            ?: if (
                trackType ==
                C.TRACK_TYPE_AUDIO
            ) {
                "Audio $fallbackIndex"
            } else {
                "Subtitle $fallbackIndex"
            }

    val details =
        buildList {
            if (
                trackType ==
                    C.TRACK_TYPE_AUDIO &&
                channelCount > 0
            ) {
                add(
                    "${channelCount}ch"
                )
            }

            sampleMimeType
                ?.substringAfterLast(
                    "/"
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    add(
                        it.uppercase()
                    )
                }
        }

    return if (
        details.isEmpty()
    ) {
        primary
    } else {
        "$primary • " +
            details.joinToString(" • ")
    }
}

@Composable
private fun PlayerTrackDialog(
    title: String,
    tracks: List<PlayerTrackChoice>,
    automaticLabel: String,
    offLabel: String?,
    onAutomatic: () -> Unit,
    onOff: (() -> Unit)?,
    onSelect:
        (PlayerTrackChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {
            Text(title)
        },
        text = {
            LazyColumn(
                modifier =
                    Modifier.height(
                        360.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    ),
            ) {
                item {
                    PlayerTrackDialogRow(
                        label =
                            automaticLabel,
                        selected =
                            tracks.none {
                                it.selected
                            },
                        onClick =
                            onAutomatic,
                    )
                }

                if (
                    offLabel != null &&
                    onOff != null
                ) {
                    item {
                        PlayerTrackDialogRow(
                            label =
                                offLabel,
                            selected = false,
                            onClick =
                                onOff,
                        )
                    }
                }

                items(
                    tracks,
                    key = {
                        it.key
                    },
                ) { track ->
                    PlayerTrackDialogRow(
                        label =
                            track.label,
                        selected =
                            track.selected,
                        onClick = {
                            onSelect(track)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun PlayerTrackDialogRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
            .padding(
                vertical = 6.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )

        Spacer(
            Modifier.width(8.dp)
        )

        Text(
            label,
            maxLines = 2,
            overflow =
                TextOverflow.Ellipsis,
        )
    }
}

private fun applyTrackChoice(
    player: ExoPlayer,
    trackType: Int,
    choice: PlayerTrackChoice,
) {
    player.trackSelectionParameters =
        player
            .trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(
                trackType,
                false,
            )
            .clearOverridesOfType(
                trackType
            )
            .setOverrideForType(
                choice.override
            )
            .build()
}

private fun clearTrackOverride(
    player: ExoPlayer,
    trackType: Int,
    disable: Boolean,
) {
    player.trackSelectionParameters =
        player
            .trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(
                trackType
            )
            .setTrackTypeDisabled(
                trackType,
                disable,
            )
            .build()
}

private fun friendlyPlaybackError(
    error: PlaybackException,
): String {
    val message =
        error.message
            ?.takeIf {
                it.isNotBlank()
            }

    return when {
        message
            ?.contains(
                "403",
                ignoreCase = true,
            ) == true ->
            "The stream server rejected this request. Try another source."

        message
            ?.contains(
                "404",
                ignoreCase = true,
            ) == true ->
            "This stream is no longer available. Try another source."

        message
            ?.contains(
                "timeout",
                ignoreCase = true,
            ) == true ->
            "The stream took too long to respond. Retry or choose another source."

        message
            ?.contains(
                "decoder",
                ignoreCase = true,
            ) == true ||
            message
                ?.contains(
                    "codec",
                    ignoreCase = true,
                ) == true ->
            "This device may not support the stream codec. Try another source."

        else ->
            message
                ?: "Playback failed. Retry or choose another source."
    }
}

private fun formatPlaybackTime(
    positionMs: Long,
): String {
    val totalSeconds =
        (
            positionMs /
                1_000L
        ).coerceAtLeast(0L)

    val hours =
        totalSeconds / 3_600L

    val minutes =
        (
            totalSeconds %
                3_600L
        ) / 60L

    val seconds =
        totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(
            hours,
            minutes,
            seconds,
        )
    } else {
        "%d:%02d".format(
            minutes,
            seconds,
        )
    }
}

private fun buildPlayerMediaItem(
    sourceUrl: String,
    subtitles: List<SubtitleTrack>,
    preferredLanguageCode: String?,
    secondaryLanguageCode: String?,
    subtitlesOnByDefault: Boolean,
    autoSelectPreferred: Boolean,
    embeddedPriority: Boolean,
): Media3MediaItem {
    val normalizedPreferred =
        preferredLanguageCode
            ?.lowercase()

    val normalizedSecondary =
        secondaryLanguageCode
            ?.lowercase()

    val orderedSubtitles =
        subtitles
            .filter {
                it.url.startsWith(
                    "https://"
                )
            }
            .distinctBy {
                it.url
            }
            .sortedBy {
                subtitle ->

                subtitleLanguagePriority(
                    language =
                        subtitle.language,
                    preferred =
                        normalizedPreferred,
                    secondary =
                        normalizedSecondary,
                )
            }

    val subtitleConfigurations =
        orderedSubtitles
            .mapIndexed {
                index,
                subtitle ->

                val builder =
                    Media3MediaItem
                        .SubtitleConfiguration
                        .Builder(
                            Uri.parse(
                                subtitle.url
                            )
                        )
                        .setId(
                            subtitle.id
                        )
                        .setLabel(
                            subtitle.language
                        )
                        .setLanguage(
                            subtitle.language
                        )
                        .setMimeType(
                            subtitleMimeType(
                                subtitle.url
                            )
                        )

                val shouldMarkDefault =
                    subtitlesOnByDefault &&
                        autoSelectPreferred &&
                        !embeddedPriority &&
                        (
                            subtitleLanguagePriority(
                                language =
                                    subtitle.language,
                                preferred =
                                    normalizedPreferred,
                                secondary =
                                    normalizedSecondary,
                            ) == 0 ||
                                (
                                    normalizedPreferred == null &&
                                        index == 0
                                )
                        )

                if (shouldMarkDefault) {
                    builder.setSelectionFlags(
                        C.SELECTION_FLAG_DEFAULT
                    )
                }

                builder.build()
            }

    return Media3MediaItem
        .Builder()
        .setUri(
            Uri.parse(sourceUrl)
        )
        .setSubtitleConfigurations(
            subtitleConfigurations
        )
        .build()
}

private fun subtitleLanguagePriority(
    language: String,
    preferred: String?,
    secondary: String?,
): Int {
    val normalized =
        language
            .trim()
            .lowercase()

    return when {
        preferred != null &&
            (
                normalized == preferred ||
                    normalized.startsWith(
                        "$preferred-"
                    )
            ) -> 0

        secondary != null &&
            (
                normalized == secondary ||
                    normalized.startsWith(
                        "$secondary-"
                    )
            ) -> 1

        else -> 2
    }
}

private fun subtitleMimeType(
    url: String,
): String =
    when (
        url.substringBefore("?")
            .substringAfterLast(
                ".",
                "",
            )
            .lowercase()
    ) {
        "vtt" ->
            MimeTypes.TEXT_VTT

        "ssa",
        "ass" ->
            MimeTypes.TEXT_SSA

        "ttml",
        "xml" ->
            MimeTypes.APPLICATION_TTML

        else ->
            MimeTypes.APPLICATION_SUBRIP
    }

private fun selectedVideoId(
    media: MediaItem,
    episode: EpisodeItem?,
): String? =
    if (media.type == "series") {
        episode?.id
    } else {
        media.id
    }

private fun playbackTitle(
    media: MediaItem,
    episode: EpisodeItem?,
): String =
    if (episode == null) {
        media.name
    } else {
        "${media.name} • S${episode.season}E${episode.episode} • ${episode.title}"
    }

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Surface(
            shape =
                RoundedCornerShape(
                    50
                ),
            color =
                VueoPalette
                    .SurfaceElevated,
        ) {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription =
                        "Back",
                    tint = Color.White,
                )
            }
        }

        Spacer(
            Modifier.width(12.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp
                ),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight =
                    FontWeight.Black,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Text(
                subtitle,
                color =
                    VueoPalette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        }

        action?.invoke()
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text(
                title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            )
        }
    }
}
