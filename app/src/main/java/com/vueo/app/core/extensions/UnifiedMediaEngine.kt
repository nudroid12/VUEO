package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

class UnifiedMediaEngine {
    private val extensions = CopyOnWriteArrayList<MediaExtension>()

    fun install(extension: MediaExtension) {
        extensions.removeAll { it.descriptor.id == extension.descriptor.id }
        extensions += extension
    }

    fun uninstall(id: String) {
        extensions.removeAll { it.descriptor.id == id }
    }

    fun installed(): List<MediaExtension> = extensions.toList()

    fun stremioAddons(): List<MediaExtension> =
        extensions.filter { it.descriptor.kind == ExtensionKind.STREMIO_ADDON }

    fun extension(id: String?): MediaExtension? =
        id?.let { target -> extensions.firstOrNull { it.descriptor.id == target } }

    suspend fun loadCatalogRows(
        maxRows: Int = 10,
        forceRefresh: Boolean = false,
    ): List<CatalogRow> = coroutineScope {
        if (!forceRefresh) {
            CatalogDiscoveryCache
                .home()
                ?.let {
                    return@coroutineScope it.take(maxRows)
                }
        }

        val rows =
            stremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter {
                            it.canLoadWithoutExtras
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .take(maxRows)
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
                                    ?: return@runCatching
                                        null

                            CatalogRow(
                                id =
                                    "${extension.descriptor.id}:" +
                                    "${catalog.type}:" +
                                    catalog.id,
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

        CatalogDiscoveryCache.putHome(
            rows
        )

        rows
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
            stremioAddons()
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
            .filter { "subtitles" in it.descriptor.resources }
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
    private fun score(
        source: StreamSource,
        preferredQuality: String? = null,
    ): Int {
        val q =
            source.quality
                .orEmpty()
                .lowercase()

        val hdr =
            source.hdr
                .orEmpty()
                .lowercase()

        val codec =
            source.codec
                .orEmpty()
                .lowercase()

        val detectedQuality =
            when {
                "2160" in q ||
                    "4k" in q ||
                    "uhd" in q ->
                    "4K"

                "1080" in q ->
                    "1080p"

                "720" in q ->
                    "720p"

                else ->
                    "Other"
            }

        val preferenceBoost =
            if (
                preferredQuality != null &&
                detectedQuality ==
                    preferredQuality
            ) {
                55
            } else {
                0
            }

        return source.rankBoost +
            preferenceBoost +
            (
                if (
                    source.isDirectPlayable
                ) {
                    100
                } else {
                    0
                }
            ) +
            when (
                detectedQuality
            ) {
                "4K" -> 40
                "1080p" -> 30
                "720p" -> 20
                else -> 10
            } +
            when {
                "dolby vision" in hdr ||
                    hdr == "dv" ->
                    15

                "hdr" in hdr ->
                    10

                else ->
                    0
            } +
            when {
                "hevc" in codec ||
                    "h265" in codec ||
                    "av1" in codec ->
                    8

                else ->
                    0
            }
    }

    fun comparator(
        preferredQuality: String? = null,
    ) =
        compareByDescending<
            StreamSource
        > {
            score(
                source = it,
                preferredQuality =
                    preferredQuality,
            )
        }.thenBy {
            it.sizeBytes
                ?: Long.MAX_VALUE
        }
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
