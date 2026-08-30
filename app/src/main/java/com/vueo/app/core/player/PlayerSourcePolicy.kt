package com.vueo.app.core.player

import com.vueo.app.core.model.StreamSource

enum class PlayerSourceQuality(
    val label: String,
    val automaticRecoveryEligible: Boolean,
) {
    FULL_HD("1080p", true),
    HD("720p", true),
    AUTO("Auto", true),
    UNKNOWN("Unknown", true),
    ULTRA_HD("4K", true),
    LOW("Below 720p", false),
}

data class PlayerSourceAssessment(
    val quality: PlayerSourceQuality,
    val score: Int,
    val summary: String,
)

/**
 * Deterministic source policy. It uses only metadata available for the current
 * title and never learns from viewing history.
 */
object PlayerSourcePolicy {
    fun assess(
        source: StreamSource,
        preferredQuality: String? = null,
    ): PlayerSourceAssessment {
        val quality = detectQuality(source)
        val searchable = buildSearchableText(source)
        val preferred = preferredQuality
            ?.trim()
            ?.lowercase()

        val qualityScore = when (quality) {
            PlayerSourceQuality.FULL_HD -> 420
            PlayerSourceQuality.HD -> 400
            PlayerSourceQuality.AUTO -> 390
            PlayerSourceQuality.UNKNOWN -> 380
            PlayerSourceQuality.ULTRA_HD -> 360
            PlayerSourceQuality.LOW -> 0
        }

        val preferenceBoost = when {
            preferred == null -> 0
            quality.label.lowercase() == preferred -> 100
            preferred == "4k" && quality == PlayerSourceQuality.ULTRA_HD -> 100
            else -> 0
        }

        val deliveryBoost = when {
            ".m3u8" in searchable || " hls" in searchable -> 35
            ".mp4" in searchable || " mp4" in searchable -> 25
            else -> 0
        }

        val codecBoost = when {
            "h264" in searchable ||
                "h.264" in searchable ||
                "avc" in searchable -> 20

            "av1" in searchable -> -20
            else -> 0
        }

        val directBoost = if (source.isDirectPlayable) 1_000 else -1_000
        val providerBoost = source.rankBoost.coerceIn(-30, 30)
        val score = directBoost + qualityScore + preferenceBoost +
            deliveryBoost + codecBoost + providerBoost

        return PlayerSourceAssessment(
            quality = quality,
            score = score,
            summary = buildList {
                add(quality.label)
                when {
                    ".m3u8" in searchable || " hls" in searchable ->
                        add("HLS")

                    ".mp4" in searchable || " mp4" in searchable ->
                        add("MP4")
                }
                when {
                    "h264" in searchable ||
                        "h.264" in searchable ||
                        "avc" in searchable -> add("H.264")

                    "hevc" in searchable || "h265" in searchable ->
                        add("HEVC")

                    "av1" in searchable -> add("AV1")
                }
            }.distinct().joinToString(" • "),
        )
    }

    fun comparator(
        preferredQuality: String? = null,
    ): Comparator<StreamSource> =
        compareByDescending<StreamSource> {
            assess(it, preferredQuality).score
        }.thenBy {
            it.sizeBytes ?: Long.MAX_VALUE
        }.thenBy {
            it.providerName.lowercase()
        }

    fun automaticRecoveryCandidates(
        rankedSources: List<StreamSource>,
        attemptedUrls: Set<String>,
    ): List<StreamSource> = rankedSources.filter { source ->
        val url = source.url
        url != null &&
            url !in attemptedUrls &&
            assess(source).quality.automaticRecoveryEligible
    }

    fun detectQuality(
        source: StreamSource,
    ): PlayerSourceQuality {
        val explicit = source.quality
            .orEmpty()
            .trim()
            .lowercase()
        val value = listOf(
            source.quality,
            source.name,
        ).joinToString(" ")
            .lowercase()

        return when {
            "2160" in value || "4k" in value || "uhd" in value ->
                PlayerSourceQuality.ULTRA_HD

            "1080" in value -> PlayerSourceQuality.FULL_HD
            "720" in value -> PlayerSourceQuality.HD
            "480" in value ||
                "576" in value ||
                "540" in value ||
                "360" in value ||
                "240" in value ||
                "144" in value ||
                explicit == "sd" -> PlayerSourceQuality.LOW

            explicit == "auto" || "adaptive" in value ->
                PlayerSourceQuality.AUTO

            explicit.isBlank() ||
                explicit == "unknown" ||
                explicit == "other" -> PlayerSourceQuality.UNKNOWN

            else -> PlayerSourceQuality.UNKNOWN
        }
    }

    private fun buildSearchableText(
        source: StreamSource,
    ): String = listOf(
        source.quality,
        source.name,
        source.codec,
        source.url,
    ).joinToString(" ")
        .lowercase()
}

class PlayerSourceRecoverySession(
    private val maximumAutomaticSwitches: Int = 2,
) {
    private val attemptedUrls = linkedSetOf<String>()
    private val failedUrls = linkedSetOf<String>()
    private var automaticSwitches = 0

    fun begin(source: StreamSource) {
        source.url?.let(attemptedUrls::add)
    }

    fun markFailed(source: StreamSource) {
        source.url?.let {
            attemptedUrls += it
            failedUrls += it
        }
    }

    fun allowRetry(source: StreamSource) {
        source.url?.let {
            attemptedUrls -= it
            failedUrls -= it
        }
    }

    fun next(
        rankedSources: List<StreamSource>,
    ): StreamSource? {
        if (automaticSwitches >= maximumAutomaticSwitches) {
            return null
        }
        val candidate = PlayerSourcePolicy
            .automaticRecoveryCandidates(
                rankedSources = rankedSources,
                attemptedUrls = attemptedUrls,
            )
            .firstOrNull()
            ?: return null

        automaticSwitches++
        candidate.url?.let(attemptedUrls::add)
        return candidate
    }

    fun failedSourceUrls(): Set<String> = failedUrls.toSet()
}

enum class PlayerPlaybackPhase {
    LOADING,
    BUFFERING,
    RECOVERING,
    READY,
    FAILED,
}

const val PLAYER_STARTUP_TIMEOUT_MS = 15_000L
const val PLAYER_REBUFFER_TIMEOUT_MS = 25_000L
