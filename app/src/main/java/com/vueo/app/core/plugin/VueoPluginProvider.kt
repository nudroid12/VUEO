package com.vueo.app.core.plugin

import android.net.Uri
import com.vueo.app.core.extensions.ExtensionDescriptor
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.MediaExtension
import com.vueo.app.core.model.CatalogPage
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import com.vueo.app.core.stremio.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject

/**
 * VUEO Plugin V1 is deliberately declarative and remote.
 * Plugins expose JSON endpoints defined in their manifest and are never loaded as executable APK/JAR code.
 */
class VueoPluginProvider private constructor(
    override val descriptor: ExtensionDescriptor,
    private val routes: Map<String, String>,
) : MediaExtension {

    override suspend fun catalog(type: String, catalogId: String, extras: Map<String, String>): CatalogPage {
        val route = routes["catalog"] ?: return CatalogPage(emptyList())
        val url = route
            .replace("{type}", Uri.encode(type))
            .replace("{id}", Uri.encode(catalogId))
        val json = JSONObject(SimpleHttp.get(url))
        return CatalogPage(json.optJSONArray("items").toMediaItems(descriptor.id))
    }

    override suspend fun meta(type: String, id: String): MediaItem? {
        val route = routes["meta"] ?: return null
        val url = route.replace("{type}", Uri.encode(type)).replace("{id}", Uri.encode(id))
        val json = JSONObject(SimpleHttp.get(url))
        return json.optJSONObject("item")?.toMediaItem(descriptor.id)
    }

    override suspend fun streams(type: String, videoId: String): List<StreamSource> {
        val route = routes["stream"] ?: return emptyList()
        val url = route.replace("{type}", Uri.encode(type)).replace("{id}", Uri.encode(videoId))
        val json = JSONObject(SimpleHttp.get(url))
        val array = json.optJSONArray("streams") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val streamUrl = item.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            StreamSource(
                name = item.optString("name", descriptor.name),
                url = streamUrl,
                quality = item.optString("quality").takeIf { it.isNotBlank() },
                codec = item.optString("codec").takeIf { it.isNotBlank() },
                hdr = item.optString("hdr").takeIf { it.isNotBlank() },
                audio = item.optString("audio").takeIf { it.isNotBlank() },
                language = item.optString("language").takeIf { it.isNotBlank() },
                sizeBytes = item.optLong("sizeBytes", -1).takeIf { it >= 0 },
                providerId = descriptor.id,
                providerName = descriptor.name,
            )
        }
    }

    override suspend fun subtitles(type: String, id: String, extras: Map<String, String>): List<SubtitleTrack> {
        val route = routes["subtitles"] ?: return emptyList()
        val url = route.replace("{type}", Uri.encode(type)).replace("{id}", Uri.encode(id))
        val json = JSONObject(SimpleHttp.get(url))
        val array = json.optJSONArray("subtitles") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val subtitleUrl = item.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            SubtitleTrack(
                id = item.optString("id", "$index"),
                language = item.optString("language", "und"),
                url = subtitleUrl,
                providerId = descriptor.id,
            )
        }
    }

    companion object {
        suspend fun fromManifestUrl(manifestUrl: String): VueoPluginProvider {
            require(manifestUrl.startsWith("https://")) { "VUEO requires HTTPS plugin manifests." }
            val json = JSONObject(SimpleHttp.get(manifestUrl))
            require(json.optString("schema") == "vueo-plugin-v1") { "Not a VUEO plugin manifest." }

            val routeJson = json.optJSONObject("routes") ?: JSONObject()
            val routes = buildMap {
                listOf("catalog", "meta", "stream", "subtitles").forEach { name ->
                    routeJson.optString(name).takeIf { it.startsWith("https://") }?.let { put(name, it) }
                }
            }
            val permissions = json.optJSONArray("permissions").toStringSet()
            val resources = routes.keys.toSet()
            return VueoPluginProvider(
                descriptor = ExtensionDescriptor(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    version = json.optString("version", "0.0.0"),
                    kind = ExtensionKind.VUEO_PLUGIN,
                    baseUrl = manifestUrl,
                    description = json.optString("description").takeIf { it.isNotBlank() },
                    resources = resources,
                    types = json.optJSONArray("types").toStringSet(),
                    permissions = permissions,
                ),
                routes = routes,
            )
        }
    }
}

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
}

private fun JSONArray?.toMediaItems(sourceId: String): List<MediaItem> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.toMediaItem(sourceId) }
}

private fun JSONObject.toMediaItem(sourceId: String): MediaItem? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").takeIf { it.isNotBlank() } ?: return null
    return MediaItem(
        id = id,
        type = optString("type", "movie"),
        name = name,
        poster = optString("poster").takeIf { it.isNotBlank() },
        background = optString("background").takeIf { it.isNotBlank() },
        description = optString("description").takeIf { it.isNotBlank() },
        releaseInfo = optString("releaseInfo").takeIf { it.isNotBlank() },
        genres = emptyList(),
        sourceExtensionId = sourceId,
    )
}
