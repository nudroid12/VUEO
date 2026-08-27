package com.vueo.app.core.storage

import android.content.Context

class AddonStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun manifestUrls(): List<String> =
        prefs.getStringSet(KEY_MANIFEST_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    /**
     * Seeds development defaults once per seed revision.
     *
     * A user can still remove one afterwards and it will not be forced back
     * on every launch. Increment DEV_DEFAULTS_REVISION only when we
     * intentionally want to seed a new development default set.
     */
    fun seedDevelopmentDefaultsIfNeeded(): Boolean {
        val currentRevision = prefs.getInt(KEY_DEV_DEFAULTS_REVISION, 0)

        if (currentRevision >= DEV_DEFAULTS_REVISION) {
            return false
        }

        val next = manifestUrls().toMutableSet()
        next += DEVELOPMENT_DEFAULT_MANIFESTS

        prefs.edit()
            .putStringSet(KEY_MANIFEST_URLS, next)
            .putInt(KEY_DEV_DEFAULTS_REVISION, DEV_DEFAULTS_REVISION)
            .apply()

        return true
    }

    fun add(manifestUrl: String) {
        val next = manifestUrls().toMutableSet()
        next += manifestUrl
        prefs.edit().putStringSet(KEY_MANIFEST_URLS, next).apply()
    }

    fun remove(manifestUrl: String) {
        val next = manifestUrls().toMutableSet()
        next -= manifestUrl
        prefs.edit().putStringSet(KEY_MANIFEST_URLS, next).apply()
    }

    fun isDevelopmentDefault(manifestUrl: String): Boolean =
        manifestUrl in DEVELOPMENT_DEFAULT_MANIFESTS

    companion object {
        private const val PREFS_NAME = "vueo_content_manager"
        private const val KEY_MANIFEST_URLS = "stremio_manifest_urls"
        private const val KEY_DEV_DEFAULTS_REVISION = "dev_defaults_revision"

        private const val DEV_DEFAULTS_REVISION = 1

        val DEVELOPMENT_DEFAULT_MANIFESTS = setOf(
            "https://yastream.tamthai.de/manifest.json",
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
        )
    }
}
