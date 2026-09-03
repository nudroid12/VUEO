package com.vueotv.app.player

import android.net.Uri
import com.vueotv.app.content.TvContentManagerStore
import com.vueotv.app.data.TvMediaItem
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * TV source discovery engine.
 *
 * TV-07 intentionally consumes the same Stremio addon configuration managed by
 * TV Content Manager. Only direct HTTP(S) streams are returned to Media3.
 * Torrent-only stream entries are ignored because ExoPlayer cannot play them
 * without a torrent transport layer.
 */
class TvSourceEngine(
    private val contentStore: TvContentManagerStore,
) {
    private val cache = LinkedHashMap<String, CachedSources>()
    private val cacheMutex = Mutex()

    suspend fun discoverProgressive(
        request: TvPlaybackRequest,
        onProgress: suspend (TvSourceProgress) -> Unit = {},
    ): TvSourceDiscovery = coroutineScope {
        val effectiveRequest = resolvePlayableRequest(request)
        val key = effectiveRequest.cacheKey
        val cached = cacheMutex.withLock { cache[key] }
        if (cached != null && System.currentTimeMillis() - cached.atMs <= CACHE_TTL_MS) {
            return@coroutineScope cached.discovery.copy(fromCache = true)
        }

        val addonUrls = contentStore.enabledAddonUrls()
        if (addonUrls.isEmpty()) {
            return@coroutineScope TvSourceDiscovery(
                sources = emptyList(),
                attemptedAddons = 0,
                successfulAddons = 0,
                failedAddons = 0,
                notice = "No enabled Stremio addons. Enable a stream addon in Content Manager.",
            )
        }

        val mutex = Mutex()
        val collected = mutableListOf<TvStreamSource>()
        var completed = 0
        var successful = 0
        var failed = 0

        addonUrls.map { manifestUrl ->
            async(Dispatchers.IO) {
                val result = withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                    runCatching { discoverFromAddon(manifestUrl, effectiveRequest) }
                }

                val streams = result?.getOrNull().orEmpty()
                val didFail = result == null || result.isFailure

                val progress = mutex.withLock {
                    completed += 1
                    if (didFail) failed += 1 else successful += 1
                    collected += streams
                    val ranked = rankAndDedup(collected)
                    TvSourceProgress(
                        sources = ranked,
                        completedAddons = completed,
                        totalAddons = addonUrls.size,
                    )
                }
                onProgress(progress)
            }
        }.awaitAll()

        val ranked = mutex.withLock { rankAndDedup(collected) }
        val discovery = TvSourceDiscovery(
            sources = ranked,
            attemptedAddons = addonUrls.size,
            successfulAddons = successful,
            failedAddons = failed,
            notice = when {
                ranked.isNotEmpty() -> null
                failed == addonUrls.size -> "Source addons could not be reached."
                else -> "No direct playable source was returned for this title."
            },
        )

        if (ranked.isNotEmpty()) {
            cacheMutex.withLock {
                cache[key] = CachedSources(discovery, System.currentTimeMillis())
                while (cache.size > MAX_CACHE_ENTRIES) {
                    val first = cache.keys.firstOrNull() ?: break
                    cache.remove(first)
                }
            }
        }
        discovery
    }

    private fun resolvePlayableRequest(request: TvPlaybackRequest): TvPlaybackRequest {
        if (!request.media.type.equals("series", ignoreCase = true)) return request
        if (request.videoId != request.media.id) return request

        val resolvedId =
            runCatching {
                val url =
                    "https://v3-cinemeta.strem.io/meta/series/${Uri.encode(request.media.id)}.json"
                val root = JSONObject(httpGet(url))
                val videos = root.optJSONObject("meta")?.optJSONArray("videos")
                videos
                    ?.optJSONObject(0)
                    ?.optString("id")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

        return if (resolvedId != null) request.copy(videoId = resolvedId) else request
    }

    private fun discoverFromAddon(
        manifestUrl: String,
        request: TvPlaybackRequest,
    ): List<TvStreamSource> {
        val manifest = JSONObject(httpGet(manifestUrl))
        if (!manifest.supportsResource("stream")) return emptyList()
        if (!manifest.supportsType(request.media.type)) return emptyList()

        val addonName = manifest.optString("name").trim().ifBlank { fallbackName(manifestUrl) }
        val addonId = manifest.optString("id").trim().ifBlank { manifestUrl }
        val base = manifestUrl.removeSuffix("/manifest.json").removeSuffix("/")
        val streamUrl = "$base/stream/${Uri.encode(request.media.type)}/${Uri.encode(request.videoId)}.json"
        val root = JSONObject(httpGet(streamUrl))
        val streams = root.optJSONArray("streams") ?: JSONArray()

        return (0 until streams.length()).mapNotNull { index ->
            val item = streams.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").trim().takeIf {
                it.startsWith("https://") || it.startsWith("http://")
            } ?: return@mapNotNull null

            val title = item.optString("title").trim()
                .ifBlank { item.optString("name").trim() }
                .ifBlank { addonName }
            val quality = inferQuality(title, url)
            val delivery = inferDelivery(title, url)
            val score = sourceScore(quality, delivery, title, url)

            TvStreamSource(
                id = "$addonId:$index:${url.hashCode()}",
                name = title,
                url = url,
                providerId = addonId,
                providerName = addonName,
                quality = quality,
                delivery = delivery,
                score = score,
            )
        }
    }

    private fun rankAndDedup(input: List<TvStreamSource>): List<TvStreamSource> =
        input
            .distinctBy { canonicalUrl(it.url) }
            .sortedWith(
                compareByDescending<TvStreamSource> { it.score }
                    .thenBy { it.providerName.lowercase() }
                    .thenBy { it.name.lowercase() }
            )

    private fun sourceScore(
        quality: TvSourceQuality,
        delivery: String?,
        title: String,
        url: String,
    ): Int {
        val qualityScore = when (quality) {
            TvSourceQuality.FULL_HD -> 420
            TvSourceQuality.HD -> 400
            TvSourceQuality.AUTO -> 390
            TvSourceQuality.UNKNOWN -> 380
            TvSourceQuality.ULTRA_HD -> 360
            TvSourceQuality.LOW -> 0
        }
        val deliveryScore = when (delivery) {
            "HLS" -> 35
            "MP4" -> 25
            "DASH" -> 20
            else -> 0
        }
        val searchable = "$title $url".lowercase()
        val codecScore = when {
            "h264" in searchable || "h.264" in searchable || "avc" in searchable -> 20
            "av1" in searchable -> -20
            else -> 0
        }
        return 1_000 + qualityScore + deliveryScore + codecScore
    }

    private fun inferQuality(title: String, url: String): TvSourceQuality {
        val value = "$title $url".lowercase()
        return when {
            "2160" in value || "4k" in value || "uhd" in value -> TvSourceQuality.ULTRA_HD
            "1080" in value -> TvSourceQuality.FULL_HD
            "720" in value -> TvSourceQuality.HD
            "480" in value || "576" in value || "360" in value || "240" in value -> TvSourceQuality.LOW
            "auto" in value || "adaptive" in value -> TvSourceQuality.AUTO
            else -> TvSourceQuality.UNKNOWN
        }
    }

    private fun inferDelivery(title: String, url: String): String? {
        val value = "$title $url".lowercase()
        return when {
            ".m3u8" in value || " hls" in value -> "HLS"
            ".mpd" in value || " dash" in value -> "DASH"
            ".mp4" in value || " mp4" in value -> "MP4"
            else -> null
        }
    }

    private fun canonicalUrl(url: String): String =
        url.substringBefore("#").trim()

    private fun JSONObject.supportsResource(name: String): Boolean {
        val resources = optJSONArray("resources") ?: return false
        for (i in 0 until resources.length()) {
            when (val value = resources.opt(i)) {
                is String -> if (value == name) return true
                is JSONObject -> if (value.optString("name") == name) return true
            }
        }
        return false
    }

    private fun JSONObject.supportsType(type: String): Boolean {
        val types = optJSONArray("types") ?: return true
        if (types.length() == 0) return true
        for (i in 0 until types.length()) {
            if (types.optString(i).equals(type, ignoreCase = true)) return true
        }
        return false
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VUEO-TV-Source-Engine/0.7")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fallbackName(url: String): String =
        runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("Addon")

    private data class CachedSources(
        val discovery: TvSourceDiscovery,
        val atMs: Long,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4_500
        private const val READ_TIMEOUT_MS = 7_500
        private const val ADDON_TIMEOUT_MS = 9_000L
        private const val CACHE_TTL_MS = 120_000L
        private const val MAX_CACHE_ENTRIES = 24
    }
}

data class TvPlaybackRequest(
    val media: TvMediaItem,
    val videoId: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val displayTitle: String
        get() = episodeTitle?.let { "${media.name} • $it" } ?: media.name

    val cacheKey: String
        get() = "${media.type}|${media.id}|$videoId"
}

data class TvSourceDiscovery(
    val sources: List<TvStreamSource>,
    val attemptedAddons: Int,
    val successfulAddons: Int,
    val failedAddons: Int,
    val notice: String?,
    val fromCache: Boolean = false,
)

data class TvSourceProgress(
    val sources: List<TvStreamSource>,
    val completedAddons: Int,
    val totalAddons: Int,
)

data class TvStreamSource(
    val id: String,
    val name: String,
    val url: String,
    val providerId: String,
    val providerName: String,
    val quality: TvSourceQuality,
    val delivery: String?,
    val score: Int,
) {
    val summary: String
        get() = buildList {
            add(quality.label)
            delivery?.let(::add)
            add(providerName)
        }.distinct().joinToString(" • ")
}

enum class TvSourceQuality(val label: String) {
    ULTRA_HD("4K"),
    FULL_HD("1080p"),
    HD("720p"),
    AUTO("Auto"),
    UNKNOWN("Unknown"),
    LOW("Low"),
}
