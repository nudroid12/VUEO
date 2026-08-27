package com.vueo.app.core.stremio

import android.net.Uri
import com.vueo.app.core.extensions.ExtensionDescriptor
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.MediaExtension
import com.vueo.app.core.model.CatalogPage
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject

class StremioAddonProvider private constructor(
    override val descriptor: ExtensionDescriptor,
) : MediaExtension {

    private val base = descriptor.baseUrl.removeSuffix("/manifest.json").removeSuffix("/")

    override suspend fun catalog(type: String, catalogId: String, extras: Map<String, String>): CatalogPage {
        val suffix = encodeExtras(extras)
        val json = JSONObject(SimpleHttp.get("$base/catalog/$type/$catalogId$suffix.json"))
        val metas = json.optJSONArray("metas") ?: JSONArray()
        return CatalogPage((0 until metas.length()).mapNotNull { metas.optJSONObject(it)?.toMediaItem(descriptor.id) })
    }

    override suspend fun meta(type: String, id: String): MediaItem? {
        val json = JSONObject(SimpleHttp.get("$base/meta/$type/${Uri.encode(id)}.json"))
        return json.optJSONObject("meta")?.toMediaItem(descriptor.id)
    }

    override suspend fun streams(type: String, videoId: String): List<StreamSource> {
        val json = JSONObject(SimpleHttp.get("$base/stream/$type/${Uri.encode(videoId)}.json"))
        val streams = json.optJSONArray("streams") ?: JSONArray()
        return (0 until streams.length()).mapNotNull { index ->
            val item = streams.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.isNotBlank() }
            val infoHash = item.optString("infoHash").takeIf { it.isNotBlank() }
            if (url == null && infoHash == null) return@mapNotNull null
            val title = item.optString("title", item.optString("name", descriptor.name))
            StreamSource(
                name = title,
                url = url,
                infoHash = infoHash,
                fileIndex = item.optInt("fileIdx", -1).takeIf { it >= 0 },
                quality = inferQuality(title),
                codec = inferCodec(title),
                hdr = inferHdr(title),
                providerId = descriptor.id,
                providerName = descriptor.name,
            )
        }
    }

    override suspend fun subtitles(type: String, id: String, extras: Map<String, String>): List<SubtitleTrack> {
        val suffix = encodeExtras(extras)
        val json = JSONObject(SimpleHttp.get("$base/subtitles/$type/${Uri.encode(id)}$suffix.json"))
        val subtitles = json.optJSONArray("subtitles") ?: JSONArray()
        return (0 until subtitles.length()).mapNotNull { index ->
            val item = subtitles.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SubtitleTrack(
                id = "$index-${item.optString("id", item.optString("lang", "und"))}",
                language = item.optString("lang", "und"),
                url = url,
                providerId = descriptor.id,
            )
        }
    }

    companion object {
        suspend fun fromManifestUrl(manifestUrl: String): StremioAddonProvider {
            require(manifestUrl.startsWith("https://")) { "VUEO requires HTTPS extension URLs." }
            val json = JSONObject(SimpleHttp.get(manifestUrl))
            val id = json.getString("id")
            val name = json.getString("name")
            val version = json.optString("version", "0.0.0")
            val resources = parseResources(json.optJSONArray("resources"))
            val types = json.optJSONArray("types").toStringSet()
            return StremioAddonProvider(
                ExtensionDescriptor(
                    id = id,
                    name = name,
                    version = version,
                    kind = ExtensionKind.STREMIO_ADDON,
                    baseUrl = manifestUrl,
                    description = json.optString("description").takeIf { it.isNotBlank() },
                    resources = resources,
                    types = types,
                )
            )
        }

        private fun parseResources(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()
            return buildSet {
                for (i in 0 until array.length()) {
                    when (val value = array.opt(i)) {
                        is String -> add(value)
                        is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        }
    }
}

private fun JSONObject.toMediaItem(sourceId: String): MediaItem? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    val type = optString("type", "movie")
    val name = optString("name").takeIf { it.isNotBlank() } ?: return null
    return MediaItem(
        id = id,
        type = type,
        name = name,
        poster = optString("poster").takeIf { it.isNotBlank() },
        background = optString("background").takeIf { it.isNotBlank() },
        description = optString("description").takeIf { it.isNotBlank() },
        releaseInfo = optString("releaseInfo").takeIf { it.isNotBlank() },
        genres = optJSONArray("genres").toStringList(),
        sourceExtensionId = sourceId,
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}

private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

private fun encodeExtras(extras: Map<String, String>): String {
    if (extras.isEmpty()) return ""
    val encoded = extras.entries.joinToString("&") { "${Uri.encode(it.key)}=${Uri.encode(it.value)}" }
    return "/$encoded"
}

private fun inferQuality(text: String): String? {
    val t = text.lowercase()
    return when {
        "2160" in t || "4k" in t -> "4K"
        "1080" in t -> "1080p"
        "720" in t -> "720p"
        "480" in t -> "480p"
        else -> null
    }
}

private fun inferCodec(text: String): String? {
    val t = text.lowercase()
    return when {
        "av1" in t -> "AV1"
        "hevc" in t || "h265" in t || "x265" in t -> "HEVC"
        "h264" in t || "x264" in t -> "H.264"
        else -> null
    }
}

private fun inferHdr(text: String): String? {
    val t = text.lowercase()
    return when {
        "dolby vision" in t || " dovi" in t || " dv " in t -> "Dolby Vision"
        "hdr10+" in t -> "HDR10+"
        "hdr" in t -> "HDR"
        else -> null
    }
}
