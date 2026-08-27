package com.vueo.app.core.storage

import android.content.Context

class AddonStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun manifestUrls(): List<String> =
        prefs.getStringSet(KEY_MANIFEST_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

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

    companion object {
        private const val PREFS_NAME = "vueo_content_manager"
        private const val KEY_MANIFEST_URLS = "stremio_manifest_urls"
    }
}
