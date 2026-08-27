package com.vueo.app.ui

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
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.core.extensions.ExtensionInstaller
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.MediaExtension
import com.vueo.app.core.extensions.UnifiedMediaEngine
import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.storage.AddonStore
import com.vueo.app.ui.components.NetworkImage
import kotlinx.coroutines.launch

private enum class AppTab {
    HOME,
    SEARCH,
    LIBRARY,
    CONTENT_MANAGER,
}

private enum class ContentPage {
    ROOT,
    ADDONS,
    PLUGINS,
}

@Composable
fun VueoApp() {
    val context = LocalContext.current
    val engine = remember { UnifiedMediaEngine() }
    val store = remember { AddonStore(context.applicationContext) }

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var contentPage by remember { mutableStateOf(ContentPage.ROOT) }
    var contentVersion by remember { mutableIntStateOf(0) }
    var booting by remember { mutableStateOf(true) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(Unit) {
        store.manifestUrls().forEach { manifestUrl ->
            runCatching {
                ExtensionInstaller.installStremioAddon(manifestUrl)
            }.onSuccess(engine::install)
        }
        booting = false
        contentVersion++
    }

    if (selectedMedia != null) {
        MediaDetailsScreen(
            engine = engine,
            initialItem = selectedMedia!!,
            onBack = { selectedMedia = null },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B1114)) {
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
                    tab = AppTab.CONTENT_MANAGER,
                    selected = selectedTab,
                    icon = Icons.Default.SettingsInputComponent,
                    label = "Content",
                ) {
                    selectedTab = it
                    contentPage = ContentPage.ROOT
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    engine = engine,
                    contentVersion = contentVersion,
                    booting = booting,
                    onOpenContentManager = {
                        selectedTab = AppTab.CONTENT_MANAGER
                        contentPage = ContentPage.ROOT
                    },
                    onMediaClick = { selectedMedia = it },
                )

                AppTab.SEARCH -> PlaceholderScreen(
                    title = "Search",
                    subtitle = "Universal search is the next discovery milestone.",
                )

                AppTab.LIBRARY -> PlaceholderScreen(
                    title = "Library",
                    subtitle = "Watchlist and Continue Watching will be stored here.",
                )

                AppTab.CONTENT_MANAGER -> when (contentPage) {
                    ContentPage.ROOT -> ContentManagerScreen(
                        engine = engine,
                        onAddons = { contentPage = ContentPage.ADDONS },
                        onPlugins = { contentPage = ContentPage.PLUGINS },
                    )

                    ContentPage.ADDONS -> AddonsScreen(
                        engine = engine,
                        store = store,
                        contentVersion = contentVersion,
                        onContentChanged = { contentVersion++ },
                        onBack = { contentPage = ContentPage.ROOT },
                    )

                    ContentPage.PLUGINS -> PluginsScreen(
                        onBack = { contentPage = ContentPage.ROOT },
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
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

@Composable
private fun HomeScreen(
    engine: UnifiedMediaEngine,
    contentVersion: Int,
    booting: Boolean,
    onOpenContentManager: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    var rows by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contentVersion) {
        if (booting) return@LaunchedEffect

        loading = true
        error = null

        runCatching {
            engine.loadCatalogRows()
        }.onSuccess {
            rows = it
        }.onFailure {
            rows = emptyList()
            error = it.message
        }

        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            VueoHeader()
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
            val hero = rows.first().items.firstOrNull()
            if (hero != null) {
                item {
                    HeroMediaCard(
                        item = hero,
                        onClick = { onMediaClick(hero) },
                    )
                }
            }

            items(rows, key = { it.id }) { row ->
                CatalogSection(
                    row = row,
                    onMediaClick = onMediaClick,
                )
            }
        }
    }
}

@Composable
private fun VueoHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF081006),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "VUEO",
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                letterSpacing = 5.sp,
            )
        }

        Icon(Icons.Default.Search, contentDescription = "Search")
    }
}

@Composable
private fun EmptyHomeCard(
    hasAddons: Boolean,
    error: String?,
    onOpenContentManager: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (hasAddons) "No catalog loaded" else "Connect your first content source",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )

            Text(
                when {
                    error != null -> "VUEO could not load a catalog right now. Open Content Manager to review your addon."
                    hasAddons -> "The installed addon does not expose a catalog that can load without extra filters."
                    else -> "Install a Stremio addon in Content Manager. Its available catalogs will appear here automatically."
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
            )

            Spacer(Modifier.height(4.dp))

            Button(onClick = onOpenContentManager) {
                Text("Open Content Manager")
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
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(235.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        NetworkImage(
            url = item.background ?: item.poster,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallbackText = item.name,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .48f)),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp),
        ) {
            Text(
                item.name.uppercase(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val metaLine = listOfNotNull(
                item.releaseInfo,
                item.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(" • "),
            ).joinToString("  •  ")

            if (metaLine.isNotBlank()) {
                Text(
                    metaLine,
                    color = Color.White.copy(alpha = .78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(onClick = onClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("View")
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    row.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    row.providerName,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 12.sp,
                )
            }

            Text(
                "${row.items.size} titles",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = row.items,
                key = { "${row.id}:${it.id}" },
            ) { item ->
                MediaPoster(
                    item = item,
                    onClick = { onMediaClick(item) },
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
    Column(
        modifier = Modifier
            .width(126.dp)
            .clickable(onClick = onClick),
    ) {
        NetworkImage(
            url = item.poster,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .clip(RoundedCornerShape(14.dp)),
            fallbackText = item.name,
        )

        Spacer(Modifier.height(7.dp))

        Text(
            item.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )

        item.releaseInfo?.let {
            Text(
                it,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ContentManagerScreen(
    engine: UnifiedMediaEngine,
    onAddons: () -> Unit,
    onPlugins: () -> Unit,
) {
    val addons = engine.stremioAddons()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    "Content Manager",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Manage where VUEO gets content and stream sources.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                )
            }
        }

        item {
            Text(
                "SOURCES",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
            )
        }

        item {
            ContentManagerCard(
                title = "Addons",
                subtitle = "Install and manage Stremio addons.",
                status = "${addons.size} installed",
                icon = Icons.Default.Extension,
                onClick = onAddons,
            )
        }

        item {
            ContentManagerCard(
                title = "Plugins",
                subtitle = "JavaScript provider repositories for stream discovery.",
                status = "Provider engine next",
                icon = Icons.Default.SettingsInputComponent,
                onClick = onPlugins,
            )
        }
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.width(15.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    status,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }

            Text("›", fontSize = 30.sp)
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
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    installed,
                    key = { it.descriptor.id },
                ) { addon ->
                    AddonCard(
                        addon = addon,
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
                    Text(
                        "v${addon.descriptor.version}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                        fontSize = 12.sp,
                    )
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
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScreenHeader(
            title = "Plugins",
            subtitle = "JavaScript provider repositories",
            onBack = onBack,
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Provider plugin engine",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "This section is reserved for the Nuvio-style JavaScript provider repository system we agreed on. It is intentionally not using the old generic VUEO extension format.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                )
                Text(
                    "Next: repository manifest, provider enable/disable, JavaScript sandbox, and unified stream results.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun MediaDetailsScreen(
    engine: UnifiedMediaEngine,
    initialItem: MediaItem,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var item by remember(initialItem) { mutableStateOf(initialItem) }
    var loadingMeta by remember { mutableStateOf(true) }
    var loadingStreams by remember { mutableStateOf(false) }
    var streams by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var sourceStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialItem.id, initialItem.sourceExtensionId) {
        loadingMeta = true
        item = engine.loadMeta(initialItem)
        loadingMeta = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
            ) {
                NetworkImage(
                    url = item.background ?: item.poster,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackText = item.name,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = .5f)),
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(22.dp),
                ) {
                    Text(
                        item.name,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Text(
                        listOfNotNull(
                            item.releaseInfo,
                            item.genres.take(3)
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(" • "),
                        ).joinToString("  •  "),
                        color = Color.White.copy(alpha = .78f),
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

        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item.description?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f),
                    )
                }

                Button(
                    enabled = !loadingStreams,
                    onClick = {
                        scope.launch {
                            loadingStreams = true
                            sourceStatus = null

                            streams = runCatching {
                                engine.resolveStreams(item.type, item.id)
                            }.getOrElse {
                                sourceStatus = it.message ?: "Unable to discover streams."
                                emptyList()
                            }

                            if (streams.isEmpty() && sourceStatus == null) {
                                sourceStatus = if (item.type == "series") {
                                    "Series episode selection is not implemented yet. Movie stream discovery is available in v0.2."
                                } else {
                                    "No streams were returned by installed stream addons."
                                }
                            }

                            loadingStreams = false
                        }
                    },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loadingStreams) "Finding Sources..." else "Find Sources")
                }

                sourceStatus?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    )
                }
            }
        }

        if (streams.isNotEmpty()) {
            item {
                Text(
                    "Sources (${streams.size})",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            items(streams.take(40)) { source ->
                StreamSourceCard(source)
            }
        }
    }
}

@Composable
private fun StreamSourceCard(
    source: StreamSource,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    source.quality ?: "Source",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (source.url != null) "Direct" else "Torrent",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 12.sp,
                )
            }

            Text(
                source.name,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )

            Text(
                listOfNotNull(
                    source.codec,
                    source.hdr,
                    source.audio,
                    source.providerName,
                ).joinToString("  •  "),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                fontSize = 11.sp,
            )
        }
    }
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
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        Spacer(Modifier.width(5.dp))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 13.sp,
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
