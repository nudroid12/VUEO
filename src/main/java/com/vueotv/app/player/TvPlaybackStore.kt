package com.vueotv.app.player

import android.content.Context

class TvPlaybackStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun resumePositionMs(request: TvPlaybackRequest): Long =
        prefs.getLong(positionKey(request.cacheKey), 0L)
            .takeIf { it >= MIN_RESUME_MS }
            ?: 0L

    fun save(
        request: TvPlaybackRequest,
        positionMs: Long,
        durationMs: Long,
    ) {
        val completed =
            durationMs > 0L &&
                positionMs >= durationMs - COMPLETION_WINDOW_MS

        if (completed || positionMs < MIN_RESUME_MS) {
            clear(request)
            return
        }

        prefs.edit()
            .putLong(positionKey(request.cacheKey), positionMs)
            .putLong(durationKey(request.cacheKey), durationMs.coerceAtLeast(0L))
            .apply()
    }

    fun clear(request: TvPlaybackRequest) {
        prefs.edit()
            .remove(positionKey(request.cacheKey))
            .remove(durationKey(request.cacheKey))
            .apply()
    }

    private fun positionKey(key: String) = "position:$key"
    private fun durationKey(key: String) = "duration:$key"

    companion object {
        private const val PREFS_NAME = "vueo_tv_playback"
        private const val MIN_RESUME_MS = 30_000L
        private const val COMPLETION_WINDOW_MS = 60_000L
    }
}
