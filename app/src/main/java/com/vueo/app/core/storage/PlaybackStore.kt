package com.vueo.app.core.storage

import android.content.Context

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun positionMs(mediaKey: String): Long =
        prefs.getLong(positionKey(mediaKey), 0L)

    fun savePositionMs(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 5_000L) {
            prefs.edit().remove(positionKey(mediaKey)).apply()
            return
        }

        if (durationMs > 0L && positionMs >= durationMs - 20_000L) {
            prefs.edit().remove(positionKey(mediaKey)).apply()
            return
        }

        prefs.edit()
            .putLong(positionKey(mediaKey), positionMs)
            .apply()
    }

    private fun positionKey(mediaKey: String): String =
        "position:$mediaKey"

    companion object {
        private const val PREFS_NAME = "vueo_playback"
    }
}
