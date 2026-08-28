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

    fun resumePlaybackEnabled():
        Boolean =
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

    fun preferredQuality():
        PreferredQuality {
        val stored =
            prefs.getString(
                KEY_PREFERRED_QUALITY,
                PreferredQuality
                    .AUTO
                    .name,
            )

        return runCatching {
            PreferredQuality.valueOf(
                stored
                    ?: PreferredQuality
                        .AUTO
                        .name
            )
        }.getOrDefault(
            PreferredQuality.AUTO
        )
    }

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

    fun showSourceTechnicalDetails():
        Boolean =
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

    companion object {
        private const val PREFS_NAME =
            "vueo_settings"

        private const val KEY_RESUME_PLAYBACK =
            "resume_playback"

        private const val KEY_PREFERRED_QUALITY =
            "preferred_quality"

        private const val KEY_SOURCE_TECHNICAL_DETAILS =
            "source_technical_details"
    }
}
