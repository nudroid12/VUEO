package com.vueo.app.core.storage

import android.content.Context

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun positionMs(mediaKey: String): Long =
        prefs.getLong(
            positionKey(mediaKey),
            0L,
        )

    fun durationMs(mediaKey: String): Long =
        prefs.getLong(
            durationKey(mediaKey),
            0L,
        )

    fun clearPosition(
        mediaKey: String,
    ) {
        prefs.edit()
            .remove(positionKey(mediaKey))
            .remove(durationKey(mediaKey))
            .apply()
    }

    fun savePositionMs(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 5_000L) {
            clearPosition(mediaKey)
            return
        }

        if (
            durationMs > 0L &&
            positionMs >=
                durationMs - 20_000L
        ) {
            clearPosition(mediaKey)
            return
        }

        prefs.edit()
            .putLong(
                positionKey(mediaKey),
                positionMs,
            )
            .putLong(
                durationKey(mediaKey),
                durationMs.coerceAtLeast(0L),
            )
            .apply()
    }

    private fun positionKey(
        mediaKey: String,
    ): String =
        "position:$mediaKey"

    private fun durationKey(
        mediaKey: String,
    ): String =
        "duration:$mediaKey"

    companion object {
        private const val PREFS_NAME = "vueo_playback"
    }
}
