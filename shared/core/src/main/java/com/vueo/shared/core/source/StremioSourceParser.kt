package com.vueo.shared.core.source

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

object StremioSourceParser {
    fun supportsStream(
        manifestJson: String,
        mediaType: String,
    ): Boolean {
        val manifest = JSONObject(manifestJson)
        return manifest.supportsResource("stream") && manifest.supportsType(mediaType)
    }

    fun supportsSubtitles(
        manifestJson: String,
        mediaType: String,
    ): Boolean {
        val manifest = JSONObject(manifestJson)
        return manifest.supportsResource("subtitles") && manifest.supportsType(mediaType)
    }

    fun buildStreamUrl(
        manifestUrl: String,
        mediaType: String,
        encodedVideoId: String,
    ): String {
        val base = manifestUrl.removeSuffix("/manifest.json").removeSuffix("/")
        return "$base/stream/$mediaType/$encodedVideoId.json"
    }

    fun buildSubtitleUrl(
        manifestUrl: String,
        mediaType: String,
        encodedVideoId: String,
    ): String {
        val base = manifestUrl.removeSuffix("/manifest.json").removeSuffix("/")
        return "$base/subtitles/$mediaType/$encodedVideoId.json"
    }

    fun parseStreams(
        manifestJson: String,
        streamResponseJson: String,
        manifestUrl: String,
        providerBoost: Int = 0,
    ): List<SourceCandidate> {
        val manifest = JSONObject(manifestJson)
        val providerName = manifest.optString("name").trim().ifBlank { fallbackName(manifestUrl) }
        val providerId = manifest.optString("id").trim().ifBlank { manifestUrl }
        val streams = JSONObject(streamResponseJson).optJSONArray("streams") ?: JSONArray()

        return (0 until streams.length()).mapNotNull { index ->
            val item = streams.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").trim().takeIf { it.isNotBlank() }
            val infoHash = item.optString("infoHash").trim().takeIf { it.isNotBlank() }
            if (url == null && infoHash == null) return@mapNotNull null

            val title = item.optString("title").trim()
                .ifBlank { item.optString("name").trim() }
                .ifBlank { providerName }
            val behaviorHints = item.optJSONObject("behaviorHints")
            val headers = behaviorHints
                ?.optJSONObject("proxyHeaders")
                ?.optJSONObject("request")
                .toStringMap()

            SourceCandidate(
                id = "$providerId:$index:${(url ?: infoHash).hashCode()}",
                name = title,
                url = url,
                infoHash = infoHash,
                fileIndex = item.optInt("fileIdx", -1).takeIf { it >= 0 },
                providerId = providerId,
                providerName = providerName,
                quality = inferQuality(title, url),
                codec = inferCodec(title, url),
                audio = item.optString("audio").trim().takeIf { it.isNotBlank() } ?: inferAudio(title),
                language = listOf("language", "lang", "audioLanguage", "audio_language")
                    .firstNotNullOfOrNull { field ->
                        item.optString(field).trim().takeIf { it.isNotBlank() }
                    } ?: inferLanguage(title),
                headers = headers,
                rankBoost = providerBoost,
            )
        }
    }

    fun parseSubtitles(
        manifestJson: String,
        subtitleResponseJson: String,
        manifestUrl: String,
    ): List<SubtitleCandidate> {
        val manifest = JSONObject(manifestJson)
        val providerName = manifest.optString("name").trim().ifBlank { fallbackName(manifestUrl) }
        val providerId = manifest.optString("id").trim().ifBlank { manifestUrl }
        val subtitles = JSONObject(subtitleResponseJson).optJSONArray("subtitles") ?: JSONArray()

        return (0 until subtitles.length()).mapNotNull { index ->
            val item = subtitles.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").trim().takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            val language = listOf("lang", "language", "languageCode", "locale", "label")
                .firstNotNullOfOrNull { field ->
                    item.optString(field).trim().takeIf { it.isNotBlank() }
                } ?: "und"
            val rawId = item.optString("id").trim().ifBlank { "$language:$index" }

            SubtitleCandidate(
                id = "$providerId:$rawId",
                language = language,
                url = url,
                providerId = providerId,
                providerName = providerName,
                name = item.optString("title").trim()
                    .ifBlank { item.optString("name").trim() }
                    .takeIf { it.isNotBlank() },
            )
        }
    }

    private fun JSONObject.supportsResource(name: String): Boolean {
        val resources = optJSONArray("resources") ?: return false
        for (index in 0 until resources.length()) {
            when (val value = resources.opt(index)) {
                is String -> if (value.equals(name, ignoreCase = true)) return true
                is JSONObject -> if (value.optString("name").equals(name, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun JSONObject.supportsType(type: String): Boolean {
        val types = optJSONArray("types") ?: return true
        if (types.length() == 0) return true
        for (index in 0 until types.length()) {
            if (types.optString(index).equals(type, ignoreCase = true)) return true
        }
        return false
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { key ->
            optString(key).trim().takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
    }

    private fun inferQuality(title: String, url: String?): String? {
        val value = "$title ${url.orEmpty()}".lowercase()
        return when {
            "2160" in value || "4k" in value || "uhd" in value -> "4K"
            "1080" in value -> "1080p"
            "720" in value -> "720p"
            "576" in value -> "576p"
            "540" in value -> "540p"
            "480" in value -> "480p"
            "360" in value -> "360p"
            "240" in value -> "240p"
            "adaptive" in value || " auto" in value -> "Auto"
            else -> null
        }
    }

    private fun inferCodec(title: String, url: String?): String? {
        val value = "$title ${url.orEmpty()}".lowercase()
        return when {
            "h264" in value || "h.264" in value || "avc" in value -> "H.264"
            "hevc" in value || "h265" in value || "h.265" in value -> "HEVC"
            "av1" in value -> "AV1"
            else -> null
        }
    }

    private fun inferAudio(title: String): String? {
        val value = title.lowercase()
        return when {
            "dual audio" in value -> "Dual Audio"
            "multi audio" in value || "multi-audio" in value -> "Multi Audio"
            "dub" in value -> "Dub"
            else -> null
        }
    }

    private fun inferLanguage(title: String): String? {
        val value = title.lowercase()
        return when {
            Regex("\\benglish\\b|\\beng\\b").containsMatchIn(value) -> "en"
            Regex("\\bmalay\\b|\\bmsa\\b").containsMatchIn(value) -> "ms"
            Regex("\\bindonesian\\b|\\bind\\b").containsMatchIn(value) -> "id"
            Regex("\\bjapanese\\b|\\bjpn\\b").containsMatchIn(value) -> "ja"
            Regex("\\bkorean\\b|\\bkor\\b").containsMatchIn(value) -> "ko"
            Regex("\\bhindi\\b|\\bhin\\b").containsMatchIn(value) -> "hi"
            Regex("\\btamil\\b|\\btam\\b").containsMatchIn(value) -> "ta"
            Regex("\\btelugu\\b|\\btel\\b").containsMatchIn(value) -> "te"
            Regex("\\barabic\\b|\\bara\\b").containsMatchIn(value) -> "ar"
            else -> null
        }
    }

    private fun fallbackName(url: String): String =
        runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("Addon")
}
