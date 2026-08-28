package com.vueo.app.core.storage

import android.content.Context

enum class PreferredQuality(
    val label: String,
    val rankKey: String?,
) {
    AUTO(
        label = "Auto",
        rankKey = null,
    ),
    FOUR_K(
        label = "4K",
        rankKey = "4K",
    ),
    FULL_HD(
        label = "1080p",
        rankKey = "1080p",
    ),
    HD(
        label = "720p",
        rankKey = "720p",
    ),
}

enum class SubtitleLanguage(
    val label: String,
    val languageCode: String?,
) {
    AUTO("Auto", null),
    ENGLISH("English", "en"),
    MALAY("Malay", "ms"),
    INDONESIAN("Indonesian", "id"),
    CHINESE("Chinese", "zh"),
    TAMIL("Tamil", "ta"),
    HINDI("Hindi", "hi"),
    ARABIC("Arabic", "ar"),
    JAPANESE("Japanese", "ja"),
    KOREAN("Korean", "ko"),
}

enum class SubtitleSize(
    val label: String,
) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large"),
}

class SettingsStore(
    context: Context,
) {
    private val prefs =
        context
            .applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun resumePlaybackEnabled(): Boolean =
        prefs.getBoolean(
            KEY_RESUME_PLAYBACK,
            true,
        )

    fun setResumePlaybackEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_RESUME_PLAYBACK,
                enabled,
            )
            .apply()
    }

    fun preferredQuality(): PreferredQuality =
        enumValue(
            key = KEY_PREFERRED_QUALITY,
            default = PreferredQuality.AUTO,
        )

    fun setPreferredQuality(
        value: PreferredQuality,
    ) {
        prefs.edit()
            .putString(
                KEY_PREFERRED_QUALITY,
                value.name,
            )
            .apply()
    }

    fun showSourceTechnicalDetails(): Boolean =
        prefs.getBoolean(
            KEY_SOURCE_TECHNICAL_DETAILS,
            true,
        )

    fun setShowSourceTechnicalDetails(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_SOURCE_TECHNICAL_DETAILS,
                enabled,
            )
            .apply()
    }

    fun preferredSubtitleLanguage(): SubtitleLanguage =
        enumValue(
            key = KEY_SUBTITLE_LANGUAGE,
            default = SubtitleLanguage.ENGLISH,
        )

    fun setPreferredSubtitleLanguage(
        value: SubtitleLanguage,
    ) {
        prefs.edit()
            .putString(
                KEY_SUBTITLE_LANGUAGE,
                value.name,
            )
            .apply()
    }

    fun secondarySubtitleLanguage(): SubtitleLanguage =
        enumValue(
            key = KEY_SECONDARY_SUBTITLE_LANGUAGE,
            default = SubtitleLanguage.AUTO,
        )

    fun setSecondarySubtitleLanguage(
        value: SubtitleLanguage,
    ) {
        prefs.edit()
            .putString(
                KEY_SECONDARY_SUBTITLE_LANGUAGE,
                value.name,
            )
            .apply()
    }

    fun subtitlesOnByDefault(): Boolean =
        prefs.getBoolean(
            KEY_SUBTITLES_ON_BY_DEFAULT,
            true,
        )

    fun setSubtitlesOnByDefault(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_SUBTITLES_ON_BY_DEFAULT,
                enabled,
            )
            .apply()
    }

    fun autoSelectPreferredSubtitle(): Boolean =
        prefs.getBoolean(
            KEY_AUTO_SELECT_SUBTITLE,
            true,
        )

    fun setAutoSelectPreferredSubtitle(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_AUTO_SELECT_SUBTITLE,
                enabled,
            )
            .apply()
    }

    fun embeddedSubtitlePriority(): Boolean =
        prefs.getBoolean(
            KEY_EMBEDDED_SUBTITLE_PRIORITY,
            true,
        )

    fun setEmbeddedSubtitlePriority(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_EMBEDDED_SUBTITLE_PRIORITY,
                enabled,
            )
            .apply()
    }

    fun subtitleSize(): SubtitleSize =
        enumValue(
            key = KEY_SUBTITLE_SIZE,
            default = SubtitleSize.MEDIUM,
        )

    fun setSubtitleSize(
        value: SubtitleSize,
    ) {
        prefs.edit()
            .putString(
                KEY_SUBTITLE_SIZE,
                value.name,
            )
            .apply()
    }

    fun tmdbMetadataEnrichmentEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_METADATA,
            true,
        )

    fun setTmdbMetadataEnrichmentEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_METADATA,
                enabled,
            )
            .apply()
    }

    fun tmdbRecommendationsEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_RECOMMENDATIONS,
            true,
        )

    fun setTmdbRecommendationsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_RECOMMENDATIONS,
                enabled,
            )
            .apply()
    }

    fun tmdbSimilarTitlesEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_SIMILAR,
            true,
        )

    fun setTmdbSimilarTitlesEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_SIMILAR,
                enabled,
            )
            .apply()
    }

    fun tmdbArtworkEnrichmentEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_ARTWORK,
            true,
        )

    fun setTmdbArtworkEnrichmentEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_ARTWORK,
                enabled,
            )
            .apply()
    }

    fun mdblistApiKey(): String =
        prefs.getString(
            KEY_MDBLIST_API_KEY,
            "",
        ).orEmpty().trim()

    fun setMdblistApiKey(
        apiKey: String,
    ) {
        prefs.edit()
            .putString(
                KEY_MDBLIST_API_KEY,
                apiKey.trim(),
            )
            .apply()
    }

    fun mdblistRatingsEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_RATINGS,
            true,
        )

    fun setMdblistRatingsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_RATINGS,
                enabled,
            )
            .apply()
    }

    fun mdblistImdbEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_IMDB,
            true,
        )

    fun setMdblistImdbEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_IMDB,
                enabled,
            )
            .apply()
    }

    fun mdblistRottenTomatoesEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_RT,
            true,
        )

    fun setMdblistRottenTomatoesEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_RT,
                enabled,
            )
            .apply()
    }

    fun mdblistMetacriticEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_METACRITIC,
            true,
        )

    fun setMdblistMetacriticEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_METACRITIC,
                enabled,
            )
            .apply()
    }

    fun mdblistTmdbRatingEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_TMDB,
            true,
        )

    fun setMdblistTmdbRatingEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_TMDB,
                enabled,
            )
            .apply()
    }

    fun mdblistTraktEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_TRAKT,
            true,
        )

    fun setMdblistTraktEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_TRAKT,
                enabled,
            )
            .apply()
    }

    fun automaticUpdateChecksEnabled(): Boolean =
        prefs.getBoolean(
            KEY_AUTO_UPDATE_CHECKS,
            true,
        )

    fun setAutomaticUpdateChecksEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_AUTO_UPDATE_CHECKS,
                enabled,
            )
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(
        key: String,
        default: T,
    ): T {
        val stored =
            prefs.getString(
                key,
                default.name,
            )

        return runCatching {
            enumValueOf<T>(
                stored ?: default.name
            )
        }.getOrDefault(default)
    }

    companion object {
        private const val PREFS_NAME =
            "vueo_settings"

        private const val KEY_RESUME_PLAYBACK =
            "resume_playback"

        private const val KEY_PREFERRED_QUALITY =
            "preferred_quality"

        private const val KEY_SOURCE_TECHNICAL_DETAILS =
            "source_technical_details"

        private const val KEY_SUBTITLE_LANGUAGE =
            "subtitle_language"

        private const val KEY_SECONDARY_SUBTITLE_LANGUAGE =
            "secondary_subtitle_language"

        private const val KEY_SUBTITLES_ON_BY_DEFAULT =
            "subtitles_on_by_default"

        private const val KEY_AUTO_SELECT_SUBTITLE =
            "auto_select_preferred_subtitle"

        private const val KEY_EMBEDDED_SUBTITLE_PRIORITY =
            "embedded_subtitle_priority"

        private const val KEY_SUBTITLE_SIZE =
            "subtitle_size"

        private const val KEY_TMDB_METADATA =
            "tmdb_metadata_enrichment"

        private const val KEY_TMDB_RECOMMENDATIONS =
            "tmdb_recommendations"

        private const val KEY_TMDB_SIMILAR =
            "tmdb_similar_titles"

        private const val KEY_TMDB_ARTWORK =
            "tmdb_artwork_enrichment"

        private const val KEY_MDBLIST_API_KEY =
            "mdblist_api_key"

        private const val KEY_MDBLIST_RATINGS =
            "mdblist_ratings"

        private const val KEY_MDBLIST_IMDB =
            "mdblist_imdb"

        private const val KEY_MDBLIST_RT =
            "mdblist_rotten_tomatoes"

        private const val KEY_MDBLIST_METACRITIC =
            "mdblist_metacritic"

        private const val KEY_MDBLIST_TMDB =
            "mdblist_tmdb"

        private const val KEY_MDBLIST_TRAKT =
            "mdblist_trakt"

        private const val KEY_AUTO_UPDATE_CHECKS =
            "automatic_update_checks"
    }
}
