package com.vueo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.core.extensions.ExtensionInstaller
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.UnifiedMediaEngine
import kotlinx.coroutines.launch

enum class AppTab { HOME, SEARCH, LIBRARY, ADDONS }

@Composable
fun VueoApp() {
    val engine = remember { UnifiedMediaEngine() }
    var selected by remember { mutableStateOf(AppTab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B1114)) {
                TabItem(AppTab.HOME, selected, Icons.Default.Home, "Home") { selected = it }
                TabItem(AppTab.SEARCH, selected, Icons.Default.Search, "Search") { selected = it }
                TabItem(AppTab.LIBRARY, selected, Icons.Default.VideoLibrary, "Library") { selected = it }
                TabItem(AppTab.ADDONS, selected, Icons.Default.Extension, "Extensions") { selected = it }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selected) {
                AppTab.HOME -> HomeScreen()
                AppTab.SEARCH -> PlaceholderScreen("Search", "Universal search arrives in the next milestone.")
                AppTab.LIBRARY -> PlaceholderScreen("Library", "Watchlist and Continue Watching will live here.")
                AppTab.ADDONS -> ExtensionsScreen(engine)
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: AppTab, selected: AppTab, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (AppTab) -> Unit) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

@Composable
private fun HomeScreen() {
    val continueWatching = listOf("Dune: Part Two", "Shogun", "Fallout")
    val trending = listOf("Kingdom", "Godzilla", "The Fall Guy", "Oppenheimer", "Arrival")
    val series = listOf("The Last of Us", "The Boys", "Loki", "Severance")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Text("▶", color = Color(0xFF081006), fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text("VUEO", fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 4.sp)
                }
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
        item { HeroCard() }
        item { MediaSection("Continue Watching", continueWatching, wide = true) }
        item { MediaSection("Trending", trending) }
        item { MediaSection("Popular Series", series) }
    }
}

@Composable
private fun HeroCard() {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF26362D), Color(0xFF10171A), Color(0xFF080C0E))
                )
            )
            .padding(22.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("INTERSTELLAR", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("2014  •  Sci-Fi  •  Adventure", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
            Spacer(Modifier.height(14.dp))
            Button(onClick = {}) { Text("▶  Watch") }
        }
    }
}

@Composable
private fun MediaSection(title: String, labels: List<String>, wide: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("See all", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(labels) { label -> MediaCard(label, wide) }
        }
    }
}

@Composable
private fun MediaCard(label: String, wide: Boolean) {
    Column(Modifier.width(if (wide) 190.dp else 125.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (wide) 112.dp else 178.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF31483A), Color(0xFF172024), Color(0xFF0D1215))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1), fontSize = 42.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary.copy(alpha = .7f))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExtensionsScreen(engine: UnifiedMediaEngine) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(engine.installed()) }
    var showDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Extensions", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Stremio addons + VUEO plugins", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
            }
            FilledIconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Install extension")
            }
        }

        if (installed.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Text("No extensions installed", fontWeight = FontWeight.Bold)
                    Text("Add a Stremio manifest or VUEO plugin URL.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showDialog = true }) { Text("Install Extension") }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(installed) { extension ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (extension.descriptor.kind == ExtensionKind.STREMIO_ADDON) "S" else "V", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(extension.descriptor.name, fontWeight = FontWeight.Bold)
                                Text(
                                    if (extension.descriptor.kind == ExtensionKind.STREMIO_ADDON) "Stremio Addon" else "VUEO Plugin",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                                    fontSize = 13.sp,
                                )
                            }
                            Text("Installed", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDialog = false },
            title = { Text("Install Extension") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Paste an HTTPS Stremio manifest URL or VUEO plugin manifest URL.")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; status = null },
                        label = { Text("Manifest URL") },
                        singleLine = true,
                        enabled = !busy,
                    )
                    status?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null
                            runCatching { ExtensionInstaller.inspectAndCreate(url.trim()) }
                                .onSuccess {
                                    engine.install(it)
                                    installed = engine.installed()
                                    url = ""
                                    showDialog = false
                                }
                                .onFailure { status = it.message ?: "Unable to install extension." }
                            busy = false
                        }
                    }
                ) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }, enabled = !busy) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        }
    }
}
