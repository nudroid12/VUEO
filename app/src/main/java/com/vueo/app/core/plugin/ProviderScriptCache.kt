package com.vueo.app.core.plugin

import android.content.Context
import java.io.File
import java.security.MessageDigest

class ProviderScriptCache(context: Context) {
    private val directory = File(
        context.cacheDir,
        "vueo-provider-scripts",
    ).apply {
        mkdirs()
    }

    fun get(
        scriptUrl: String,
        providerVersion: String,
    ): String? {
        val file = fileFor(scriptUrl, providerVersion)

        if (!file.isFile) {
            return null
        }

        return runCatching {
            file.readText(Charsets.UTF_8)
        }.getOrNull()
    }

    fun put(
        scriptUrl: String,
        providerVersion: String,
        script: String,
    ) {
        runCatching {
            trimIfNeeded()
            fileFor(scriptUrl, providerVersion)
                .writeText(script, Charsets.UTF_8)
        }
    }

    fun clear() {
        runCatching {
            directory.listFiles()
                ?.forEach(File::delete)
        }
    }

    private fun fileFor(
        scriptUrl: String,
        providerVersion: String,
    ): File {
        val key = "$scriptUrl|$providerVersion"
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte)
            }

        return File(directory, "$digest.js")
    }

    private fun trimIfNeeded() {
        val files = directory.listFiles()
            ?.filter(File::isFile)
            ?.sortedBy { it.lastModified() }
            .orEmpty()

        if (files.size < MAX_FILES) {
            return
        }

        files
            .take(files.size - KEEP_FILES)
            .forEach(File::delete)
    }

    companion object {
        private const val MAX_FILES = 140
        private const val KEEP_FILES = 100
    }
}
