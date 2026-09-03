package com.vueo.shared.core.source

object SourceRanker {
    fun assess(
        source: SourceCandidate,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): SourceAssessment {
        val quality = detectQuality(source)
        val audioMatch = detectAudioMatch(source, originalLanguage)
        val searchable = buildSearchableText(source)
        val preferred = preferredQuality?.trim()?.lowercase()

        val qualityScore = when (quality) {
            SourceQuality.FULL_HD -> 420
            SourceQuality.HD -> 400
            SourceQuality.AUTO -> 390
            SourceQuality.UNKNOWN -> 380
            SourceQuality.ULTRA_HD -> 360
            SourceQuality.LOW -> 0
        }

        val preferenceBoost = when {
            preferred == null -> 0
            quality.label.lowercase() == preferred -> 100
            preferred == "4k" && quality == SourceQuality.ULTRA_HD -> 100
            else -> 0
        }

        val deliveryBoost = when {
            ".m3u8" in searchable || " hls" in searchable -> 35
            ".mp4" in searchable || " mp4" in searchable -> 25
            ".mpd" in searchable || " dash" in searchable -> 20
            else -> 0
        }

        val codecBoost = when {
            "h264" in searchable || "h.264" in searchable || "avc" in searchable -> 20
            "av1" in searchable -> -20
            else -> 0
        }

        val directBoost = if (source.isDirectPlayable) 1_000 else -1_000
        val audioBoost = when (audioMatch) {
            SourceAudioMatch.ORIGINAL -> 260
            SourceAudioMatch.MULTI_WITH_ORIGINAL -> 220
            SourceAudioMatch.UNKNOWN -> 0
            SourceAudioMatch.FOREIGN_DUB -> -600
        }
        val providerBoost = source.rankBoost.coerceIn(-30, 30)
        val score = directBoost + qualityScore + preferenceBoost +
            audioBoost + deliveryBoost + codecBoost + providerBoost

        return SourceAssessment(
            quality = quality,
            score = score,
            audioMatch = audioMatch,
            summary = buildList {
                add(quality.label)
                when {
                    ".m3u8" in searchable || " hls" in searchable -> add("HLS")
                    ".mpd" in searchable || " dash" in searchable -> add("DASH")
                    ".mp4" in searchable || " mp4" in searchable -> add("MP4")
                }
                when {
                    "h264" in searchable || "h.264" in searchable || "avc" in searchable -> add("H.264")
                    "hevc" in searchable || "h265" in searchable || "h.265" in searchable -> add("HEVC")
                    "av1" in searchable -> add("AV1")
                }
            }.distinct().joinToString(" • "),
        )
    }

    fun rank(
        sources: List<SourceCandidate>,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): List<SourceCandidate> =
        sources
            .filter { it.isDirectPlayable }
            .distinctBy { canonicalUrl(it.url) }
            .sortedWith(
                compareByDescending<SourceCandidate> {
                    assess(
                        source = it,
                        preferredQuality = preferredQuality,
                        originalLanguage = originalLanguage,
                    ).score
                }.thenBy {
                    it.sizeBytes ?: Long.MAX_VALUE
                }.thenBy {
                    it.providerName.lowercase()
                }.thenBy {
                    it.name.lowercase()
                },
            )

    fun automaticRecoveryCandidates(
        rankedSources: List<SourceCandidate>,
        attemptedIds: Set<String>,
        originalLanguage: String? = null,
    ): List<SourceCandidate> = rankedSources.filter { source ->
        val assessment = assess(source, originalLanguage = originalLanguage)
        source.id !in attemptedIds &&
            assessment.quality.automaticRecoveryEligible &&
            assessment.audioMatch.recommendationEligible
    }

    fun detectQuality(source: SourceCandidate): SourceQuality {
        val explicit = source.quality.orEmpty().trim().lowercase()
        val value = listOf(source.quality, source.name).joinToString(" ").lowercase()

        return when {
            "2160" in value || "4k" in value || "uhd" in value -> SourceQuality.ULTRA_HD
            "1080" in value -> SourceQuality.FULL_HD
            "720" in value -> SourceQuality.HD
            "480" in value || "576" in value || "540" in value ||
                "360" in value || "240" in value || "144" in value || explicit == "sd" -> SourceQuality.LOW
            explicit == "auto" || "adaptive" in value -> SourceQuality.AUTO
            explicit.isBlank() || explicit == "unknown" || explicit == "other" -> SourceQuality.UNKNOWN
            else -> SourceQuality.UNKNOWN
        }
    }

    fun detectAudioMatch(
        source: SourceCandidate,
        originalLanguage: String?,
    ): SourceAudioMatch {
        val original = canonicalLanguageCode(originalLanguage) ?: return SourceAudioMatch.UNKNOWN
        val explicitText = listOfNotNull(source.language, source.audio).joinToString(" ")
        val searchable = listOf(explicitText, source.name).joinToString(" ").lowercase()
        val detectedLanguages = buildSet {
            addAll(detectLanguages(explicitText))
            canonicalLanguageCode(source.language)?.let(::add)
            canonicalLanguageCode(source.audio)?.let(::add)
            addAll(detectLanguages(source.name))
        }
        val multiAudio = AUDIO_MULTI_MARKERS.any { it.containsMatchIn(searchable) }
        val dubbed = AUDIO_DUB_MARKERS.any { it.containsMatchIn(searchable) }
        val markedOriginal = AUDIO_ORIGINAL_MARKERS.any { it.containsMatchIn(searchable) }

        return when {
            markedOriginal -> SourceAudioMatch.ORIGINAL
            original in detectedLanguages && multiAudio -> SourceAudioMatch.MULTI_WITH_ORIGINAL
            original in detectedLanguages -> SourceAudioMatch.ORIGINAL
            dubbed -> SourceAudioMatch.FOREIGN_DUB
            detectedLanguages.isNotEmpty() && !multiAudio -> SourceAudioMatch.FOREIGN_DUB
            multiAudio && detectedLanguages.isNotEmpty() -> SourceAudioMatch.FOREIGN_DUB
            else -> SourceAudioMatch.UNKNOWN
        }
    }

    private fun buildSearchableText(source: SourceCandidate): String =
        listOf(
            source.quality,
            source.name,
            source.codec,
            source.audio,
            source.language,
            source.url,
        ).joinToString(" ").lowercase()

    private fun canonicalUrl(url: String?): String =
        url.orEmpty().substringBefore('#').trim()

    private fun detectLanguages(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        val normalized = " ${value.lowercase().replace('_', ' ').replace('-', ' ')} "
        return LANGUAGE_ALIASES.mapNotNullTo(mutableSetOf()) { (alias, code) ->
            if (Regex("(^|[^a-z])${Regex.escape(alias)}([^a-z]|$)").containsMatchIn(normalized)) code else null
        }
    }

    private fun canonicalLanguageCode(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.replace('_', '-')?.takeIf { it.isNotBlank() } ?: return null
        LANGUAGE_ALIASES[normalized]?.let { return it }
        LANGUAGE_ALIASES[normalized.substringBefore('-')]?.let { return it }
        return if (normalized.length in 2..3) normalized else null
    }

    private val AUDIO_MULTI_MARKERS = listOf(
        Regex("\\bmulti[ ._-]?(audio|lang|language)?\\b"),
        Regex("\\bdual[ ._-]?audio\\b"),
        Regex("\\bmultiple[ ._-]?audio\\b"),
    )

    private val AUDIO_DUB_MARKERS = listOf(
        Regex("\\bdub(bed)?\\b"),
        Regex("\\bvoice[ ._-]?over\\b"),
    )

    private val AUDIO_ORIGINAL_MARKERS = listOf(
        Regex("\\boriginal[ ._-]?(audio|lang|language)?\\b"),
        Regex("\\borg[ ._-]?audio\\b"),
    )

    private val LANGUAGE_ALIASES = mapOf(
        "en" to "en", "eng" to "en", "english" to "en",
        "ja" to "ja", "jpn" to "ja", "japanese" to "ja",
        "ko" to "ko", "kor" to "ko", "korean" to "ko",
        "zh" to "zh", "chi" to "zh", "zho" to "zh", "chinese" to "zh", "mandarin" to "zh",
        "ms" to "ms", "may" to "ms", "msa" to "ms", "malay" to "ms",
        "id" to "id", "ind" to "id", "indonesian" to "id",
        "hi" to "hi", "hin" to "hi", "hindi" to "hi",
        "ta" to "ta", "tam" to "ta", "tamil" to "ta",
        "te" to "te", "tel" to "te", "telugu" to "te",
        "ml" to "ml", "mal" to "ml", "malayalam" to "ml",
        "ar" to "ar", "ara" to "ar", "arabic" to "ar",
        "es" to "es", "spa" to "es", "spanish" to "es",
        "fr" to "fr", "fra" to "fr", "fre" to "fr", "french" to "fr",
        "de" to "de", "deu" to "de", "ger" to "de", "german" to "de",
        "it" to "it", "ita" to "it", "italian" to "it",
        "pt" to "pt", "por" to "pt", "portuguese" to "pt",
        "ru" to "ru", "rus" to "ru", "russian" to "ru",
    )
}
