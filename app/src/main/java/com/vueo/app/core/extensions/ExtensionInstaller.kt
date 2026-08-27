package com.vueo.app.core.extensions

import com.vueo.app.core.plugin.VueoPluginProvider
import com.vueo.app.core.stremio.SimpleHttp
import com.vueo.app.core.stremio.StremioAddonProvider
import org.json.JSONObject

object ExtensionInstaller {
    suspend fun inspectAndCreate(url: String): MediaExtension {
        require(url.startsWith("https://")) { "Only HTTPS extension URLs are accepted." }
        val raw = SimpleHttp.get(url)
        val json = JSONObject(raw)
        return when {
            json.optString("schema") == "vueo-plugin-v1" -> VueoPluginProvider.fromManifestUrl(url)
            json.has("id") && json.has("resources") -> StremioAddonProvider.fromManifestUrl(url)
            else -> error("Unsupported extension manifest.")
        }
    }
}
