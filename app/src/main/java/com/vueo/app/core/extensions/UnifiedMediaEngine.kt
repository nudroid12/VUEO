package com.vueo.app.core.extensions

import com.vueo.app.core.model.StreamSource
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
}

object SourceRanker {
    private fun score(source: StreamSource): Int {
        val q = source.quality.orEmpty().lowercase()
        val hdr = source.hdr.orEmpty().lowercase()
        val codec = source.codec.orEmpty().lowercase()
        return when {
            "2160" in q || "4k" in q -> 40
            "1080" in q -> 30
            "720" in q -> 20
            else -> 10
        } + when {
            "dolby vision" in hdr || "dv" == hdr -> 15
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
