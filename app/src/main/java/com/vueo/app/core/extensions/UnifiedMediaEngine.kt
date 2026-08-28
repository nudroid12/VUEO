package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun loadCatalogRows(maxRows: Int = 10): List<CatalogRow> = coroutineScope {
        stremioAddons()
            .flatMap { extension ->
                extension.descriptor.catalogs
                    .filter { it.canLoadWithoutExtras }
                    .map { catalog -> extension to catalog }
            }
            .take(maxRows)
            .map { (extension, catalog) ->
                async {
                    runCatching {
                        val page = extension.catalog(catalog.type, catalog.id)
                        CatalogRow(
                            id = "${extension.descriptor.id}:${catalog.type}:${catalog.id}",
                            title = catalog.name
                                ?: "${extension.descriptor.name} ${catalog.type.replaceFirstChar { it.uppercase() }}",
                            providerName = extension.descriptor.name,
                            items = page.items,
                        )
                    }.getOrNull()
                }
            }
            .awaitAll()
            .filterNotNull()
            .filter { it.items.isNotEmpty() }
    }

    suspend fun loadMeta(item: MediaItem): MediaItem =
        extension(item.sourceExtensionId)
            ?.let { provider ->
                runCatching { provider.meta(item.type, item.id) }.getOrNull()
            }
            ?: item

    suspend fun resolveStreams(type: String, videoId: String): List<StreamSource> = coroutineScope {
        extensions
            .filter { "stream" in it.descriptor.resources }
            .map { extension ->
                async {
                    runCatching { extension.streams(type, videoId) }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { listOf(it.url, it.infoHash, it.fileIndex, it.providerId) }
            .sortedWith(SourceRanker.comparator)
    }

    suspend fun resolveSubtitles(
        type: String,
        videoId: String,
    ): List<SubtitleTrack> = coroutineScope {
        extensions
            .filter { "subtitles" in it.descriptor.resources }
            .map { extension ->
                async {
                    runCatching { extension.subtitles(type, videoId) }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
    }
}

object SourceRanker {
    private fun score(source: StreamSource): Int {
        val q = source.quality.orEmpty().lowercase()
        val hdr = source.hdr.orEmpty().lowercase()
        val codec = source.codec.orEmpty().lowercase()

        return (if (source.isDirectPlayable) 100 else 0) + when {
            "2160" in q || "4k" in q -> 40
            "1080" in q -> 30
            "720" in q -> 20
            else -> 10
        } + when {
            "dolby vision" in hdr || hdr == "dv" -> 15
            "hdr" in hdr -> 10
            else -> 0
        } + when {
            "hevc" in codec || "h265" in codec || "av1" in codec -> 8
            else -> 0
        }
    }

    val comparator = compareByDescending<StreamSource> { score(it) }
        .thenBy { it.sizeBytes ?: Long.MAX_VALUE }
}


object SourceCleaner {
    fun clean(
        sources: List<StreamSource>,
    ): List<StreamSource> {
        val sorted =
            sources.sortedWith(
                SourceRanker.comparator
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
