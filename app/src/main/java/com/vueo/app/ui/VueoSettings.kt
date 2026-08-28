package com.vueo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.BuildConfig
import com.vueo.app.core.extensions.CatalogDiscoveryCache
import com.vueo.app.core.extensions.SourceDiscoveryCache
import com.vueo.app.core.extensions.UnifiedMediaEngine
import com.vueo.app.core.enrichment.MdblistClient
import com.vueo.app.core.enrichment.TmdbEnhancementClient
import com.vueo.app.core.plugin.PluginStore
import com.vueo.app.core.storage.LibraryStore
import com.vueo.app.core.storage.PreferredQuality
import com.vueo.app.core.storage.SettingsStore
import com.vueo.app.core.storage.SubtitleLanguage
import com.vueo.app.core.storage.SubtitleSize
import kotlinx.coroutines.launch

@Composable
internal fun VueoSettingsHub(
    engine: UnifiedMediaEngine,
    settingsStore: SettingsStore,
    onContentManager: () -> Unit,
    onEnhancements: () -> Unit,
    onPlayback: () -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onAppearance: () -> Unit,
    onDataStorage: () -> Unit,
    onUpdates: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }

    val addons = engine.stremioAddons()
    val repositories = pluginStore.repositories()
    val providers = pluginStore.totalProviderCount()
    val tmdbConfigured = pluginStore.tmdbApiKey().isNotBlank()
    val mdblistConfigured = settingsStore.mdblistApiKey().isNotBlank()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            VueoSettingsTitle(
                title = "Settings",
                subtitle = "Manage VUEO without mixing content sources, optional enhancements, playback, subtitles, and app data.",
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Content Manager",
                subtitle = "Addons, repositories, providers, health, and diagnostics.",
                status = "${addons.size} addons • ${repositories.size} repos • $providers providers",
                icon = Icons.Default.Extension,
                onClick = onContentManager,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Enhancements",
                subtitle = "Optional services that make VUEO richer and more informative.",
                status = buildString {
                    append("TMDB ")
                    append(if (tmdbConfigured) "configured" else "optional")
                    append(" • MDBList ")
                    append(if (mdblistConfigured) "configured" else "optional")
                },
                icon = Icons.Default.SettingsInputComponent,
                onClick = onEnhancements,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Playback",
                subtitle = "Resume behavior and preferred playback quality.",
                status = "${if (settingsStore.resumePlaybackEnabled()) "Resume on" else "Resume off"} • ${settingsStore.preferredQuality().label}",
                icon = Icons.Default.PlayArrow,
                onClick = onPlayback,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Subtitles",
                subtitle = "Language, automatic selection, default state, and display size.",
                status = "${settingsStore.preferredSubtitleLanguage().label} • ${if (settingsStore.subtitlesOnByDefault()) "Default on" else "Default off"}",
                icon = Icons.Default.VideoLibrary,
                onClick = onSubtitles,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Sources",
                subtitle = "Smart ranking and source information preferences.",
                status = if (settingsStore.showSourceTechnicalDetails()) {
                    "Technical details on"
                } else {
                    "Technical details off"
                },
                icon = Icons.Default.SettingsInputComponent,
                onClick = onSources,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Appearance",
                subtitle = "VUEO visual identity and interface preferences.",
                status = "VUEO Dark • Lime accent",
                icon = Icons.Default.Settings,
                onClick = onAppearance,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Data & Storage",
                subtitle = "Cache, Continue Watching, history, and local data controls.",
                status = "Local device data",
                icon = Icons.Default.VideoLibrary,
                onClick = onDataStorage,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "Updates",
                subtitle = "Version information and update preferences.",
                status = "VUEO ${BuildConfig.VERSION_NAME}",
                icon = Icons.Default.Refresh,
                onClick = onUpdates,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "About VUEO",
                subtitle = "Build information, architecture, and privacy notes.",
                status = "Version ${BuildConfig.VERSION_NAME}",
                icon = Icons.Default.Settings,
                onClick = onAbout,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun EnhancementsSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onTmdb: () -> Unit,
    onMdblist: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }

    VueoSettingsPage(
        title = "Enhancements",
        subtitle = "Optional services only. VUEO core remains usable without them.",
        onBack = onBack,
    ) {
        item {
            VueoInfoCard(
                title = "Optional by design",
                text = "TMDB and MDBList enrich VUEO. They are not required for Home, Search, Content Manager, source discovery, playback, or Library.",
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "TMDB",
                subtitle = "Richer metadata, discovery, recommendations, similar titles, and artwork.",
                status = if (pluginStore.tmdbApiKey().isNotBlank()) {
                    "Configured"
                } else {
                    "Not configured"
                },
                icon = Icons.Default.SettingsInputComponent,
                onClick = onTmdb,
            )
        }

        item {
            VueoSettingsNavigationCard(
                title = "MDBList",
                subtitle = "Optional rating and score enrichment for title details.",
                status = if (settingsStore.mdblistApiKey().isNotBlank()) {
                    "Configured"
                } else {
                    "Not configured"
                },
                icon = Icons.Default.SettingsInputComponent,
                onClick = onMdblist,
            )
        }
    }
}

@Composable
internal fun TmdbEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }

    var apiKey by remember {
        mutableStateOf(pluginStore.tmdbApiKey())
    }
    var saved by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    var testing by remember {
        mutableStateOf(false)
    }
    var connectionStatus by remember {
        mutableStateOf<String?>(null)
    }
    var metadata by remember {
        mutableStateOf(settingsStore.tmdbMetadataEnrichmentEnabled())
    }
    var recommendations by remember {
        mutableStateOf(settingsStore.tmdbRecommendationsEnabled())
    }
    var similar by remember {
        mutableStateOf(settingsStore.tmdbSimilarTitlesEnabled())
    }
    var artwork by remember {
        mutableStateOf(settingsStore.tmdbArtworkEnrichmentEnabled())
    }

    VueoSettingsPage(
        title = "TMDB",
        subtitle = "Optional metadata and discovery enhancement.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value = connectionStatus
                    ?: if (apiKey.trim().isNotEmpty()) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                text = "The key is stored locally on this device. VUEO core does not depend on TMDB.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.Surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("TMDB v3 API Key") },
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                pluginStore
                                    .setTmdbApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus = null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                val key = apiKey.trim()

                                if (key.isBlank()) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val ok =
                                            TmdbEnhancementClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            if (ok) {
                                                "Connected"
                                            } else {
                                                "Connection failed"
                                            }

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (testing) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            "TMDB configuration saved locally.",
                            color = VueoPalette.Neon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel("FEATURES")
        }

        item {
            VueoSettingsToggleCard(
                title = "Metadata Enrichment",
                subtitle = "Allow richer title information when TMDB enrichment is available.",
                checked = metadata,
                onCheckedChange = {
                    metadata = it
                    settingsStore.setTmdbMetadataEnrichmentEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Recommendations",
                subtitle = "Use TMDB recommendations for discovery when configured.",
                checked = recommendations,
                onCheckedChange = {
                    recommendations = it
                    settingsStore.setTmdbRecommendationsEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Similar Titles",
                subtitle = "Use TMDB similar titles as an additional discovery signal.",
                checked = similar,
                onCheckedChange = {
                    similar = it
                    settingsStore.setTmdbSimilarTitlesEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Artwork Enrichment",
                subtitle = "Allow better poster and backdrop fallback when available.",
                checked = artwork,
                onCheckedChange = {
                    artwork = it
                    settingsStore.setTmdbArtworkEnrichmentEnabled(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Discovery connection",
                text = "TMDB now enriches Details metadata and powers More Like This with Recommendations, Similar titles, and the VUEO catalog fallback.",
            )
        }
    }
}

@Composable
internal fun MdblistEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var apiKey by remember {
        mutableStateOf(settingsStore.mdblistApiKey())
    }
    var saved by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    var testing by remember {
        mutableStateOf(false)
    }
    var connectionStatus by remember {
        mutableStateOf<String?>(null)
    }
    var ratings by remember {
        mutableStateOf(settingsStore.mdblistRatingsEnabled())
    }
    var imdb by remember {
        mutableStateOf(settingsStore.mdblistImdbEnabled())
    }
    var rt by remember {
        mutableStateOf(settingsStore.mdblistRottenTomatoesEnabled())
    }
    var metacritic by remember {
        mutableStateOf(settingsStore.mdblistMetacriticEnabled())
    }
    var tmdb by remember {
        mutableStateOf(settingsStore.mdblistTmdbRatingEnabled())
    }
    var trakt by remember {
        mutableStateOf(settingsStore.mdblistTraktEnabled())
    }

    VueoSettingsPage(
        title = "MDBList",
        subtitle = "Optional ratings and score enrichment.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value = connectionStatus
                    ?: if (apiKey.trim().isNotEmpty()) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                text = "MDBList is optional. Without it, VUEO simply shows the information available from core metadata sources.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.Surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("MDBList API Key") },
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                settingsStore
                                    .setMdblistApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus = null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                val key = apiKey.trim()

                                if (key.isBlank()) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val ok =
                                            MdblistClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            if (ok) {
                                                "Connected"
                                            } else {
                                                "Connection failed"
                                            }

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (testing) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            "MDBList configuration saved locally.",
                            color = VueoPalette.Neon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel("RATINGS")
        }

        item {
            VueoSettingsToggleCard(
                title = "Ratings Enrichment",
                subtitle = "Master switch for MDBList rating information.",
                checked = ratings,
                onCheckedChange = {
                    ratings = it
                    settingsStore.setMdblistRatingsEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "IMDb",
                subtitle = "Show IMDb score when available.",
                checked = imdb,
                onCheckedChange = {
                    imdb = it
                    settingsStore.setMdblistImdbEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Rotten Tomatoes",
                subtitle = "Show Rotten Tomatoes score when available.",
                checked = rt,
                onCheckedChange = {
                    rt = it
                    settingsStore.setMdblistRottenTomatoesEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Metacritic",
                subtitle = "Show Metacritic score when available.",
                checked = metacritic,
                onCheckedChange = {
                    metacritic = it
                    settingsStore.setMdblistMetacriticEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "TMDB Rating",
                subtitle = "Show TMDB rating through MDBList when available.",
                checked = tmdb,
                onCheckedChange = {
                    tmdb = it
                    settingsStore.setMdblistTmdbRatingEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Trakt",
                subtitle = "Show Trakt score when available.",
                checked = trakt,
                onCheckedChange = {
                    trakt = it
                    settingsStore.setMdblistTraktEnabled(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Ratings connection",
                text = "MDBList ratings now appear on Details when configured. VUEO fetches one rating bundle and shows only the rating sources you enable.",
            )
        }
    }
}

@Composable
internal fun PlaybackSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var resume by remember {
        mutableStateOf(settingsStore.resumePlaybackEnabled())
    }
    var quality by remember {
        mutableStateOf(settingsStore.preferredQuality())
    }
    var showQualityDialog by remember {
        mutableStateOf(false)
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Preferred Quality") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PreferredQuality.values().forEach { option ->
                        VueoChoiceRow(
                            label = option.label,
                            selected = quality == option,
                            onClick = {
                                quality = option
                                settingsStore.setPreferredQuality(option)
                                showQualityDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    VueoSettingsPage(
        title = "Playback",
        subtitle = "Player behavior and quality preference.",
        onBack = onBack,
    ) {
        item {
            VueoSettingsToggleCard(
                title = "Resume Playback",
                subtitle = "Ask to continue from a saved position when reopening a title.",
                checked = resume,
                onCheckedChange = {
                    resume = it
                    settingsStore.setResumePlaybackEnabled(it)
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Preferred Quality",
                subtitle = "Boost this resolution in Smart Source ranking without hiding alternatives.",
                value = quality.label,
                onClick = { showQualityDialog = true },
            )
        }

        item {
            VueoInfoCard(
                title = "Smart preference",
                text = "Preferred Quality is a ranking boost, not a hard filter. VUEO can still choose another source when it is more reliable or playable.",
            )
        }
    }
}

@Composable
internal fun SubtitleSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var preferred by remember {
        mutableStateOf(settingsStore.preferredSubtitleLanguage())
    }
    var secondary by remember {
        mutableStateOf(settingsStore.secondarySubtitleLanguage())
    }
    var defaultOn by remember {
        mutableStateOf(settingsStore.subtitlesOnByDefault())
    }
    var autoSelect by remember {
        mutableStateOf(settingsStore.autoSelectPreferredSubtitle())
    }
    var embeddedPriority by remember {
        mutableStateOf(settingsStore.embeddedSubtitlePriority())
    }
    var size by remember {
        mutableStateOf(settingsStore.subtitleSize())
    }
    var languageDialog by remember {
        mutableStateOf<SubtitleLanguageTarget?>(null)
    }
    var showSizeDialog by remember {
        mutableStateOf(false)
    }

    languageDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { languageDialog = null },
            title = {
                Text(
                    if (target == SubtitleLanguageTarget.PRIMARY) {
                        "Preferred Language"
                    } else {
                        "Secondary Language"
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    SubtitleLanguage.values().forEach { option ->
                        val selected = if (target == SubtitleLanguageTarget.PRIMARY) {
                            preferred == option
                        } else {
                            secondary == option
                        }

                        VueoChoiceRow(
                            label = option.label,
                            selected = selected,
                            onClick = {
                                if (target == SubtitleLanguageTarget.PRIMARY) {
                                    preferred = option
                                    settingsStore.setPreferredSubtitleLanguage(option)
                                } else {
                                    secondary = option
                                    settingsStore.setSecondarySubtitleLanguage(option)
                                }
                                languageDialog = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = null }) {
                    Text("Close")
                }
            },
        )
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            title = { Text("Subtitle Size") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SubtitleSize.values().forEach { option ->
                        VueoChoiceRow(
                            label = option.label,
                            selected = size == option,
                            onClick = {
                                size = option
                                settingsStore.setSubtitleSize(option)
                                showSizeDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSizeDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    VueoSettingsPage(
        title = "Subtitles",
        subtitle = "Subtitle behavior is separate from subtitle providers in Content Manager.",
        onBack = onBack,
    ) {
        item {
            VueoSettingsValueCard(
                title = "Preferred Language",
                subtitle = "First language VUEO should prefer when subtitle tracks are available.",
                value = preferred.label,
                onClick = {
                    languageDialog = SubtitleLanguageTarget.PRIMARY
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Secondary Language",
                subtitle = "Fallback language when the preferred language is unavailable.",
                value = secondary.label,
                onClick = {
                    languageDialog = SubtitleLanguageTarget.SECONDARY
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Subtitles On by Default",
                subtitle = "Prefer showing subtitles automatically when a suitable track exists.",
                checked = defaultOn,
                onCheckedChange = {
                    defaultOn = it
                    settingsStore.setSubtitlesOnByDefault(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Auto Select Preferred Language",
                subtitle = "Prioritize your preferred subtitle language automatically.",
                checked = autoSelect,
                onCheckedChange = {
                    autoSelect = it
                    settingsStore.setAutoSelectPreferredSubtitle(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Embedded Subtitle Priority",
                subtitle = "Prefer subtitle tracks already included in the stream before external tracks when possible.",
                checked = embeddedPriority,
                onCheckedChange = {
                    embeddedPriority = it
                    settingsStore.setEmbeddedSubtitlePriority(it)
                },
            )
        }

        item {
            VueoSettingsValueCard(
                title = "Subtitle Size",
                subtitle = "Saved display size preference for the VUEO player.",
                value = size.label,
                onClick = { showSizeDialog = true },
            )
        }

        item {
            VueoInfoCard(
                title = "Subtitle sources",
                text = "OpenSubtitles and other subtitle addons remain in Content Manager. This page only controls how VUEO chooses and displays discovered subtitle tracks.",
            )
        }
    }
}

@Composable
internal fun SourceSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var technicalDetails by remember {
        mutableStateOf(settingsStore.showSourceTechnicalDetails())
    }

    VueoSettingsPage(
        title = "Sources",
        subtitle = "Discovery and Smart Source behavior.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Smart Source Ranking",
                value = "Active",
                text = "VUEO ranks direct playability, resolution, HDR, codec information, provider health, response latency, and your preferred quality.",
            )
        }

        item {
            VueoStatusCard(
                title = "Provider Health Influence",
                value = "Active",
                text = "Healthy and responsive providers receive a ranking advantage without blocking other available sources.",
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Technical Source Details",
                subtitle = "Show codec, HDR, and audio information on Source Picker cards.",
                checked = technicalDetails,
                onCheckedChange = {
                    technicalDetails = it
                    settingsStore.setShowSourceTechnicalDetails(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Progressive discovery",
                text = "The Source Picker opens immediately and updates while providers continue searching. Slow providers do not need to block fast ones.",
            )
        }
    }
}

@Composable
internal fun AppearanceSettingsScreen(
    onBack: () -> Unit,
) {
    VueoSettingsPage(
        title = "Appearance",
        subtitle = "VUEO visual identity.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Theme",
                value = "VUEO Dark",
                text = "The current interface uses the VUEO dark visual system for consistent media viewing.",
            )
        }

        item {
            VueoStatusCard(
                title = "Accent",
                value = "Lime",
                text = "Lime is used selectively for focus, state, and important actions rather than across the whole interface.",
            )
        }

        item {
            VueoInfoCard(
                title = "More appearance controls later",
                text = "Theme variants, density, and animation preferences can be added after the core Settings and discovery architecture are stable.",
            )
        }
    }
}

@Composable
internal fun DataStorageSettingsScreen(
    libraryStore: LibraryStore,
    onLibraryChanged: () -> Unit,
    onCatalogCacheCleared: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmAction by remember {
        mutableStateOf<DataClearAction?>(null)
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            DataClearAction.CATALOG_CACHE -> {
                                scope.launch {
                                    CatalogDiscoveryCache.clearAll(
                                        context.applicationContext
                                    )
                                    onCatalogCacheCleared()
                                    feedback = "Catalog and search cache cleared."
                                }
                            }

                            DataClearAction.SOURCE_CACHE -> {
                                SourceDiscoveryCache.clearAll()
                                feedback = "Recent source cache cleared."
                            }

                            DataClearAction.CONTINUE_WATCHING -> {
                                libraryStore.clearContinueWatching()
                                onLibraryChanged()
                                feedback = "Continue Watching cleared."
                            }

                            DataClearAction.WATCH_HISTORY -> {
                                libraryStore.clearHistory()
                                onLibraryChanged()
                                feedback = "Watch History cleared."
                            }
                        }
                        confirmAction = null
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    VueoSettingsPage(
        title = "Data & Storage",
        subtitle = "Local cache and playback data controls.",
        onBack = onBack,
    ) {
        feedback?.let { message ->
            item {
                VueoInfoCard(
                    title = "Done",
                    text = message,
                )
            }
        }

        item {
            VueoSettingsActionCard(
                title = "Catalog & Search Cache",
                subtitle = "Clear the persistent Home snapshot and in-memory search cache.",
                action = "Clear",
                onClick = {
                    confirmAction = DataClearAction.CATALOG_CACHE
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Recent Source Cache",
                subtitle = "Discard short-lived source results used to speed up repeat searches.",
                action = "Clear",
                onClick = {
                    confirmAction = DataClearAction.SOURCE_CACHE
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Continue Watching",
                subtitle = "Remove unfinished playback entries from Continue Watching.",
                action = "Clear",
                onClick = {
                    confirmAction = DataClearAction.CONTINUE_WATCHING
                },
            )
        }

        item {
            VueoSettingsActionCard(
                title = "Watch History",
                subtitle = "Clear playback history without changing My List.",
                action = "Clear",
                onClick = {
                    confirmAction = DataClearAction.WATCH_HISTORY
                },
            )
        }

        item {
            VueoStatusCard(
                title = "Backup & Restore",
                value = "Next milestone",
                text = "The Settings location is reserved now. Export and restore will be implemented in the Distribution & Data patch.",
            )
        }
    }
}

@Composable
internal fun UpdatesSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var automaticChecks by remember {
        mutableStateOf(settingsStore.automaticUpdateChecksEnabled())
    }

    VueoSettingsPage(
        title = "Updates",
        subtitle = "Version and future APK update behavior.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Current Version",
                value = BuildConfig.VERSION_NAME,
                text = "Build ${BuildConfig.VERSION_CODE}. Direct APK distribution remains the target release method.",
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Automatic Update Checks",
                subtitle = "Save whether VUEO should check for a newer APK when the update service is connected.",
                checked = automaticChecks,
                onCheckedChange = {
                    automaticChecks = it
                    settingsStore.setAutomaticUpdateChecksEnabled(it)
                },
            )
        }

        item {
            VueoStatusCard(
                title = "Check for Updates",
                value = "Foundation ready",
                text = "Actual update checking and Telegram APK delivery are scheduled for the Distribution & Data milestone.",
            )
        }
    }
}

@Composable
internal fun AboutVueoSettingsScreen(
    onBack: () -> Unit,
) {
    VueoSettingsPage(
        title = "About VUEO",
        subtitle = "App and architecture information.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "VUEO",
                value = BuildConfig.VERSION_NAME,
                text = "A universal media frontend built around open content sources, progressive source discovery, and direct playback.",
            )
        }

        item {
            VueoInfoCard(
                title = "Architecture",
                text = "Built-in VUEO features, Stremio Addons, JavaScript Provider Plugins, Unified Source Engine, Smart Source Ranking, and Media3 playback.",
            )
        }

        item {
            VueoInfoCard(
                title = "Privacy",
                text = "Settings and API keys configured in this build are stored locally on the device. Optional enhancement services only run when configured and used by their feature layer.",
            )
        }

        item {
            VueoInfoCard(
                title = "TMDB Attribution",
                text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
            )
        }
    }
}

private enum class SubtitleLanguageTarget {
    PRIMARY,
    SECONDARY,
}

private enum class DataClearAction(
    val title: String,
    val message: String,
) {
    CATALOG_CACHE(
        title = "Clear catalog cache?",
        message = "Home and Search will fetch fresh catalog data again.",
    ),
    SOURCE_CACHE(
        title = "Clear source cache?",
        message = "Recent source results will be discarded. The next Watch action will perform a fresh source search.",
    ),
    CONTINUE_WATCHING(
        title = "Clear Continue Watching?",
        message = "All unfinished playback entries will be removed from Continue Watching.",
    ),
    WATCH_HISTORY(
        title = "Clear Watch History?",
        message = "Previously watched playback history will be removed. My List remains unchanged.",
    ),
}

@Composable
private fun VueoSettingsPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    VueoSettingsTitle(
                        title = title,
                        subtitle = subtitle,
                    )
                }
            }
        }

        content()

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VueoSettingsTitle(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "VUEO",
            color = VueoPalette.Neon,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
        )

        Text(
            title,
            color = Color.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Black,
        )

        Text(
            subtitle,
            color = VueoPalette.Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun VueoSettingsNavigationCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.SurfaceElevated,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VueoPalette.SurfaceStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = VueoPalette.Neon,
                )
            }

            Spacer(Modifier.width(13.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )

                Text(
                    status,
                    color = VueoPalette.Neon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                "›",
                color = VueoPalette.Muted,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun VueoSettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun VueoSettingsValueCard(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = VueoPalette.Neon.copy(alpha = .10f),
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp,
                    ),
                    color = VueoPalette.Neon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun VueoSettingsActionCard(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(10.dp))

            TextButton(onClick = onClick) {
                Text(
                    action,
                    color = VueoPalette.Neon,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun VueoInfoCard(
    title: String,
    text: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.SurfaceElevated,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )

            Text(
                text,
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VueoStatusCard(
    title: String,
    value: String,
    text: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )

                Text(
                    value,
                    color = VueoPalette.Neon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }

            Text(
                text,
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VueoChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )

        Spacer(Modifier.width(8.dp))

        Text(label)
    }
}

@Composable
private fun VueoSectionLabel(
    label: String,
) {
    Text(
        label,
        color = VueoPalette.Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
    )
}
