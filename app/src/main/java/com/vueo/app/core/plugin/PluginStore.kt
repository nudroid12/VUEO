package com.vueo.app.core.plugin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PluginStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun pluginsEnabled(): Boolean =
        prefs.getBoolean(KEY_PLUGINS_ENABLED, true)

    fun setPluginsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_PLUGINS_ENABLED, enabled)
            .apply()
    }

    fun repositories(): List<PluginRepositoryDescriptor> {
        val raw = prefs.getString(KEY_REPOSITORIES_JSON, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)

            (0 until array.length())
                .mapNotNull { index ->
                    array.optJSONObject(index)?.toRepository()
                }
        }.getOrDefault(emptyList())
    }

    fun upsert(repository: PluginRepositoryDescriptor) {
        val next = repositories()
            .filterNot { it.manifestUrl == repository.manifestUrl }
            .toMutableList()

        next += repository
        saveRepositories(next)
    }

    fun remove(manifestUrl: String) {
        saveRepositories(
            repositories().filterNot {
                it.manifestUrl == manifestUrl
            }
        )
    }

    fun isProviderEnabled(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
    ): Boolean {
        val key = providerKey(repository, provider)

        return if (prefs.contains(key)) {
            prefs.getBoolean(key, provider.defaultEnabled)
        } else {
            provider.defaultEnabled
        }
    }

    fun setProviderEnabled(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                providerKey(repository, provider),
                enabled,
            )
            .apply()
    }

    fun totalProviderCount(): Int =
        repositories().sumOf { it.providers.size }

    fun enabledProviderCount(): Int =
        repositories().sumOf { repository ->
            repository.providers.count { provider ->
                isProviderEnabled(repository, provider)
            }
        }

    private fun saveRepositories(
        repositories: List<PluginRepositoryDescriptor>,
    ) {
        val array = JSONArray()

        repositories.forEach { repository ->
            array.put(repository.toJson())
        }

        prefs.edit()
            .putString(KEY_REPOSITORIES_JSON, array.toString())
            .apply()
    }

    private fun providerKey(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
    ): String =
        "provider:${repository.manifestUrl}:${provider.id}"

    companion object {
        private const val PREFS_NAME = "vueo_plugins"
        private const val KEY_PLUGINS_ENABLED = "plugins_enabled"
        private const val KEY_REPOSITORIES_JSON = "repositories_json"
    }
}

private fun PluginRepositoryDescriptor.toJson(): JSONObject =
    JSONObject().apply {
        put("manifestUrl", manifestUrl)
        put("baseUrl", baseUrl)
        put("name", name)
        put("version", version)
        put("description", description)

        put(
            "providers",
            JSONArray().apply {
                providers.forEach { put(it.toJson()) }
            }
        )
    }

private fun PluginProviderDescriptor.toJson(): JSONObject =
    JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("version", version)
        put("author", author)
        put("supportedTypes", JSONArray(supportedTypes.toList()))
        put("filename", filename)
        put("defaultEnabled", defaultEnabled)
        put("logo", logo)
        put("contentLanguages", JSONArray(contentLanguages))
        put("formats", JSONArray(formats))
        put("limited", limited)
        put("disabledPlatforms", JSONArray(disabledPlatforms.toList()))
        put("supportsExternalPlayer", supportsExternalPlayer)
    }

private fun JSONObject.toRepository(): PluginRepositoryDescriptor? {
    val manifestUrl = optString("manifestUrl")
        .takeIf { it.isNotBlank() } ?: return null
    val baseUrl = optString("baseUrl")
        .takeIf { it.isNotBlank() } ?: return null
    val name = optString("name")
        .takeIf { it.isNotBlank() } ?: return null

    val array = optJSONArray("providers") ?: JSONArray()

    return PluginRepositoryDescriptor(
        manifestUrl = manifestUrl,
        baseUrl = baseUrl,
        name = name,
        version = optString("version", "0.0.0"),
        description = optString("description")
            .takeIf { it.isNotBlank() },
        providers = (0 until array.length())
            .mapNotNull { index ->
                array.optJSONObject(index)?.toStoredProvider()
            },
    )
}

private fun JSONObject.toStoredProvider(): PluginProviderDescriptor? {
    val id = optString("id")
        .takeIf { it.isNotBlank() } ?: return null
    val name = optString("name")
        .takeIf { it.isNotBlank() } ?: return null
    val filename = optString("filename")
        .takeIf { it.isNotBlank() } ?: return null

    return PluginProviderDescriptor(
        id = id,
        name = name,
        description = optString("description")
            .takeIf { it.isNotBlank() },
        version = optString("version", "0.0.0"),
        author = optString("author").takeIf { it.isNotBlank() },
        supportedTypes = optJSONArray("supportedTypes").toStringSet(),
        filename = filename,
        defaultEnabled = optBoolean("defaultEnabled", true),
        logo = optString("logo").takeIf { it.isNotBlank() },
        contentLanguages = optJSONArray("contentLanguages").toStringList(),
        formats = optJSONArray("formats").toStringList(),
        limited = optBoolean("limited", false),
        disabledPlatforms = optJSONArray("disabledPlatforms").toStringSet(),
        supportsExternalPlayer = optBoolean(
            "supportsExternalPlayer",
            true,
        ),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()

    return (0 until length())
        .mapNotNull {
            optString(it).takeIf(String::isNotBlank)
        }
}

private fun JSONArray?.toStringSet(): Set<String> =
    toStringList().toSet()
