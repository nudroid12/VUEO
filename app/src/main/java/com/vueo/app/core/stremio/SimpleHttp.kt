package com.vueo.app.core.stremio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object SimpleHttp {
    suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            getBlocking(url)
        }

    /**
     * Resilient transport for repository manifests and provider scripts.
     *
     * Some mobile DNS/network configurations cannot resolve
     * raw.githubusercontent.com reliably. For public GitHub raw URLs,
     * VUEO tries the jsDelivr GitHub CDN representation first, then the
     * original raw URL. Non-GitHub URLs are requested normally.
     */
    suspend fun getResilient(url: String): String =
        withContext(Dispatchers.IO) {
            val candidates = candidateUrls(url)
            val failures = mutableListOf<String>()

            for (candidate in candidates) {
                val result = runCatching {
                    getBlocking(candidate)
                }

                result.getOrNull()?.let {
                    return@withContext it
                }

                val error = result.exceptionOrNull()
                val host = runCatching {
                    URL(candidate).host
                }.getOrDefault(candidate)

                failures += "$host: ${error?.message ?: "request failed"}"
            }

            error(
                "Unable to download resource. " +
                    failures.joinToString(" | ")
            )
        }

    fun candidateUrls(url: String): List<String> {
        val mirror = githubJsDelivrMirror(url)

        return if (mirror == null) {
            listOf(url)
        } else {
            // Prefer the CDN on mobile networks where raw.githubusercontent.com
            // may be DNS-blocked, while keeping raw GitHub as a fallback.
            listOf(mirror, url).distinct()
        }
    }

    private fun getBlocking(url: String): String {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", "VUEO/0.3.3")
            }

        try {
            val code = connection.responseCode

            if (code !in 200..299) {
                error("HTTP $code from ${URL(url).host}")
            }

            return connection.inputStream
                .bufferedReader()
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun githubJsDelivrMirror(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl) }.getOrNull()
            ?: return null

        if (!uri.host.equals("raw.githubusercontent.com", ignoreCase = true)) {
            return null
        }

        val parts = uri.path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }

        if (parts.size < 4) {
            return null
        }

        val owner = parts[0]
        val repo = parts[1]

        val branch: String
        val fileStart: Int

        if (
            parts.size >= 6 &&
            parts[2] == "refs" &&
            parts[3] == "heads"
        ) {
            branch = parts[4]
            fileStart = 5
        } else {
            branch = parts[2]
            fileStart = 3
        }

        if (fileStart >= parts.size) {
            return null
        }

        val filePath = parts
            .drop(fileStart)
            .joinToString("/")

        return "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$filePath"
    }
}
