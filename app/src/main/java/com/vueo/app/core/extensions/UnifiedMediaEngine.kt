package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import com.vueo.app.core.player.PlayerSourcePolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

class UnifiedMediaEngine {
    private val extensions = CopyOnWriteArrayList<MediaExtension>()
    private val disabledExtensionIds =
        CopyOnWriteArraySet<String>()

    fun install(extension: MediaExtension) {
        extensions.removeAll { it.descriptor.id == extension.descriptor.id }
        extensions += extension
    }

    fun uninstall(id: String) {
        extensions.removeAll { it.descriptor.id == id }
        disabledExtensionIds.remove(id)
    }

    fun setExtensionEnabled(
        id: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            disabledExtensionIds.remove(id)
        } else {
            disabledExtensionIds.add(id)
        }
    }

    fun isExtensionEnabled(
        id: String,
    ): Boolean =
        id !in disabledExtensionIds

    fun installed(): List<MediaExtension> = extensions.toList()

    fun stremioAddons(): List<MediaExtension> =
        extensions.filter { it.descriptor.kind == ExtensionKind.STREMIO_ADDON }

    fun activeStremioAddons(): List<MediaExtension> =
        stremioAddons().filter {
            isExtensionEnabled(
                it.descriptor.id
            )
        }

    fun extension(id: String?): MediaExtension? =
        id?.let { target -> extensions.firstOrNull { it.descriptor.id == target } }

    suspend fun loadCatalogRows(
        maxRows: Int = 10,
        forceRefresh: Boolean = false,
        catalogOrder: List<String> = emptyList(),
    ): List<CatalogRow> = coroutineScope {
        if (!forceRefresh) {
            CatalogDiscoveryCache
                .home()
                ?.let {
                    return@coroutineScope orderCatalogRows(
                        rows = it,
                        catalogOrder = catalogOrder,
                    ).take(maxRows)
                }
        }

        val orderIndex =
            catalogOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }

        val candidates =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter {
                            it.canLoadWithoutExtras
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .sortedBy {
                    (extension, catalog) ->
                    orderIndex[
                        catalogKey(
                            extensionId =
                                extension.descriptor.id,
                            type = catalog.type,
                            catalogId = catalog.id,
                        )
                    ] ?: Int.MAX_VALUE
                }
                .take(maxRows)

        val rows =
            candidates
                .map {
                    (extension, catalog) ->
                    async {
                        runCatching {
                            val page =
                                withTimeoutOrNull(
                                    ADDON_REQUEST_TIMEOUT_MS
                                ) {
                                    extension.catalog(
                                        catalog.type,
                                        catalog.id,
                                    )
                                }
                                    ?: return@runCatching null

                            CatalogRow(
                                id =
                                    catalogKey(
                                        extensionId =
                                            extension.descriptor.id,
                                        type =
                                            catalog.type,
                                        catalogId =
                                            catalog.id,
                                    ),
                                title =
                                    catalog.name
                                        ?: "${extension.descriptor.name} " +
                                            catalog.type
                                                .replaceFirstChar {
                                                    it.uppercase()
                                                },
                                providerName =
                                    extension
                                        .descriptor
                                        .name,
                                items =
                                    page.items,
                            )
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
                .filter {
                    it.items.isNotEmpty()
                }
                .let {
                    orderCatalogRows(
                        rows = it,
                        catalogOrder = catalogOrder,
                    )
                }

        CatalogDiscoveryCache.putHome(
            rows
        )

        rows
    }

    private fun catalogKey(
        extensionId: String,
        type: String,
        catalogId: String,
    ): String =
        "$extensionId:$type:$catalogId"

    private fun orderCatalogRows(
        rows: List<CatalogRow>,
        catalogOrder: List<String>,
    ): List<CatalogRow> {
        if (catalogOrder.isEmpty()) {
            return rows
        }

        val index =
            catalogOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }

        return rows.sortedBy {
            index[it.id] ?: Int.MAX_VALUE
        }
    }

    suspend fun search(
        query: String,
        maxCatalogs: Int = 12,
        maxResults: Int = 80,
    ): List<MediaItem> = coroutineScope {
        val normalized = query.trim()

        if (normalized.length < 2) {
            return@coroutineScope emptyList<MediaItem>()
        }

        CatalogDiscoveryCache
            .search(normalized)
            ?.let {
                return@coroutineScope it.take(maxResults)
            }

        val searchableCatalogs =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter { catalog ->
                            val hasSearch =
                                catalog.extras.any {
                                    it.name == "search"
                                }

                            val requiredSupported =
                                catalog.extras
                                    .filter {
                                        it.isRequired
                                    }
                                    .all {
                                        it.name == "search"
                                    }

                            hasSearch &&
                                requiredSupported
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .take(maxCatalogs)

        val remote =
            searchableCatalogs
                .map {
                    (extension, catalog) ->

                    async {
                        runCatching {
                            withTimeoutOrNull(
                                ADDON_REQUEST_TIMEOUT_MS
                            ) {
                                extension.catalog(
                                    type =
                                        catalog.type,
                                    catalogId =
                                        catalog.id,
                                    extras =
                                        mapOf(
                                            "search" to
                                                normalized
                                        ),
                                ).items
                            }
                                ?: emptyList()
                        }.getOrDefault(
                            emptyList()
                        )
                    }
                }
                .awaitAll()
                .flatten()

        val local =
            CatalogDiscoveryCache
                .searchLocal(
                    normalized,
                    limit = maxResults,
                )

        val combined =
            (
                remote +
                    local
            )
                .distinctBy {
                    "${it.type}:${it.id}"
                }
                .take(maxResults)

        CatalogDiscoveryCache.putSearch(
            normalized,
            combined,
        )

        combined
    }

    suspend fun loadMeta(
        item: MediaItem,
    ): MediaItem {
        val provider =
            extension(
                item.sourceExtensionId
            )
                ?.takeIf {
                    isExtensionEnabled(
                        it.descriptor.id
                    )
                }
                ?: return item

        return runCatching {
            withTimeoutOrNull(
                ADDON_REQUEST_TIMEOUT_MS
            ) {
                provider.meta(
                    item.type,
                    item.id,
                )
            }
        }.getOrNull()
            ?: item
    }

    suspend fun resolveStreams(
        type: String,
        videoId: String,
    ): List<StreamSource> =
        resolveStreamsProgressive(
            type = type,
            videoId = videoId,
            onProgress = {},
        )

    suspend fun resolveStreamsProgressive(
        type: String,
        videoId: String,
        onProgress: suspend (AddonStreamProgress) -> Unit,
    ): List<StreamSource> = coroutineScope {
        val providers =
            extensions.filter {
                isExtensionEnabled(
                    it.descriptor.id
                ) &&
                    "stream" in
                        it.descriptor.resources
            }

        if (providers.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val mutex = Mutex()
        val rawStreams =
            mutableListOf<StreamSource>()

        var completed = 0

        providers.map { extension ->
            async {
                val result =
                    runCatching {
                        withTimeoutOrNull(
                            ADDON_STREAM_TIMEOUT_MS
                        ) {
                            extension.streams(
                                type,
                                videoId,
                            )
                        }
                            ?: emptyList()
                    }.getOrDefault(
                        emptyList()
                    )

                val progress =
                    mutex.withLock {
                        rawStreams += result
                        completed++

                        AddonStreamProgress(
                            streams =
                                SourceCleaner.clean(
                                    rawStreams
                                ),
                            rawCount =
                                rawStreams.size,
                            completedAddons =
                                completed,
                            totalAddons =
                                providers.size,
                        )
                    }

                onProgress(progress)
            }
        }.awaitAll()

        mutex.withLock {
            SourceCleaner.clean(
                rawStreams
            )
        }
    }

    suspend fun resolveSubtitles(
        type: String,
        videoId: String,
    ): List<SubtitleTrack> = coroutineScope {
        extensions
            .filter {
                isExtensionEnabled(
                    it.descriptor.id
                ) &&
                    "subtitles" in
                        it.descriptor.resources
            }
            .map { extension ->
                async {
                    runCatching {
                        withTimeoutOrNull(
                            ADDON_REQUEST_TIMEOUT_MS
                        ) {
                            extension.subtitles(
                                type,
                                videoId,
                            )
                        }
                            ?: emptyList()
                    }.getOrDefault(
                        emptyList()
                    )
                }
            }
            .awaitAll()
            .flatten()
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
    }
    companion object {
        private const val ADDON_REQUEST_TIMEOUT_MS =
            8_000L

        private const val ADDON_STREAM_TIMEOUT_MS =
            10_000L
    }
}

data class AddonStreamProgress(
    val streams: List<StreamSource>,
    val rawCount: Int,
    val completedAddons: Int,
    val totalAddons: Int,
)

object SourceRanker {
    fun comparator(
        preferredQuality: String? = null,
    ) = PlayerSourcePolicy.comparator(preferredQuality)
}


object SourceCleaner {
    fun clean(
        sources: List<StreamSource>,
        preferredQuality: String? = null,
    ): List<StreamSource> {
        val sorted =
            sources.sortedWith(
                SourceRanker.comparator(
                    preferredQuality
                )
            )

        val seen =
            hashSetOf<String>()

        return sorted.filter { source ->
            seen.add(
                identityKey(source)
            )
        }
    }

    fun qualityBucket(
        source: StreamSource,
    ): String {
        val value =
            (
                source.quality.orEmpty() +
                " " +
                source.name
            ).lowercase()

        return when {
            "2160" in value ||
                "4k" in value ||
                "uhd" in value ->
                "4K"

            "1080" in value ->
                "1080p"

            "720" in value ->
                "720p"

            else ->
                "Other"
        }
    }

    private fun identityKey(
        source: StreamSource,
    ): String =
        when {
            !source.url.isNullOrBlank() ->
                "url:" +
                    source.url
                        .trim()

            !source.infoHash.isNullOrBlank() ->
                "torrent:" +
                    source.infoHash
                        .lowercase() +
                    ":" +
                    (
                        source.fileIndex
                            ?: -1
                    )

            else ->
                listOf(
                    "fallback",
                    source.providerId,
                    source.name,
                    source.quality,
                ).joinToString("|")
        }
}
