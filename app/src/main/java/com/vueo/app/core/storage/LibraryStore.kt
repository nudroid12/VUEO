package com.vueo.app.core.storage

import android.content.Context
import com.vueo.app.core.model.MediaItem
import org.json.JSONArray
import org.json.JSONObject

data class LibraryPlaybackEntry(
    val media: MediaItem,
    val videoId: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedEpochMs: Long = 0L,
) {
    val mediaKey: String
        get() =
            "${media.type}:${media.id}:$videoId"

    val progressFraction: Float
        get() =
            if (
                durationMs > 0L
            ) {
                (
                    positionMs.toDouble() /
                        durationMs.toDouble()
                )
                    .coerceIn(
                        0.0,
                        1.0,
                    )
                    .toFloat()
            } else {
                0f
            }

    val isCompleted: Boolean
        get() =
            durationMs > 0L &&
                (
                    positionMs >=
                        durationMs - 20_000L ||
                    progressFraction >= 0.95f
                )
}

class LibraryStore(
    context: Context,
) {
    private val prefs =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun watchlist(): List<MediaItem> =
        readWatchlist()
            .sortedByDescending {
                watchlistTimestamp(
                    it
                )
            }
            .map {
                mediaFromJson(
                    it.getJSONObject(
                        "media"
                    )
                )
            }

    @Synchronized
    fun isWatchlisted(
        media: MediaItem,
    ): Boolean =
        readWatchlist().any {
            val stored =
                it.optJSONObject(
                    "media"
                )
                    ?: return@any false

            stored.optString(
                "id"
            ) == media.id &&
                stored.optString(
                    "type"
                ) == media.type
        }

    @Synchronized
    fun toggleWatchlist(
        media: MediaItem,
    ): Boolean {
        val entries =
            readWatchlist()
                .toMutableList()

        val index =
            entries.indexOfFirst {
                val stored =
                    it.optJSONObject(
                        "media"
                    )

                stored?.optString(
                    "id"
                ) == media.id &&
                    stored.optString(
                        "type"
                    ) == media.type
            }

        val added =
            if (index >= 0) {
                entries.removeAt(
                    index
                )
                false
            } else {
                entries +=
                    JSONObject()
                        .put(
                            "media",
                            mediaToJson(
                                media
                            ),
                        )
                        .put(
                            "addedAt",
                            System
                                .currentTimeMillis(),
                        )
                true
            }

        writeArray(
            KEY_WATCHLIST,
            entries,
        )

        return added
    }

    @Synchronized
    fun removeWatchlist(
        media: MediaItem,
    ) {
        val entries =
            readWatchlist()
                .filterNot {
                    val stored =
                        it.optJSONObject(
                            "media"
                        )

                    stored?.optString(
                        "id"
                    ) == media.id &&
                        stored.optString(
                            "type"
                        ) == media.type
                }

        writeArray(
            KEY_WATCHLIST,
            entries,
        )
    }

    @Synchronized
    fun history(): List<LibraryPlaybackEntry> =
        readHistory()
            .sortedByDescending {
                it.lastWatchedEpochMs
            }

    @Synchronized
    fun continueWatching():
        List<LibraryPlaybackEntry> =
        history()
            .filter {
                it.positionMs > 5_000L &&
                    !it.isCompleted
            }

    @Synchronized
    fun recordPlayback(
        media: MediaItem,
        videoId: String,
        episodeTitle: String?,
        season: Int?,
        episode: Int?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val key =
            "${media.type}:${media.id}:$videoId"

        val entries =
            readHistory()
                .toMutableList()

        entries.removeAll {
            it.mediaKey == key
        }

        entries.add(
            0,
            LibraryPlaybackEntry(
                media = media,
                videoId = videoId,
                episodeTitle =
                    episodeTitle,
                season = season,
                episode = episode,
                positionMs =
                    positionMs
                        .coerceAtLeast(
                            0L
                        ),
                durationMs =
                    durationMs
                        .coerceAtLeast(
                            0L
                        ),
                lastWatchedEpochMs =
                    System
                        .currentTimeMillis(),
            ),
        )

        writeHistory(
            entries.take(
                MAX_HISTORY
            )
        )
    }

    @Synchronized
    fun removeHistory(
        mediaKey: String,
    ) {
        writeHistory(
            readHistory()
                .filterNot {
                    it.mediaKey ==
                        mediaKey
                }
        )
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit()
            .remove(
                KEY_HISTORY
            )
            .apply()
    }

    @Synchronized
    fun clearContinueWatching() {
        val completedOnly =
            readHistory()
                .filter {
                    it.isCompleted ||
                        it.positionMs <=
                            5_000L
                }

        writeHistory(
            completedOnly
        )
    }

    private fun readWatchlist():
        List<JSONObject> =
        readObjectArray(
            KEY_WATCHLIST
        )

    private fun watchlistTimestamp(
        entry: JSONObject,
    ): Long =
        entry.optLong(
            "addedAt",
            0L,
        )

    private fun readHistory():
        List<LibraryPlaybackEntry> =
        readObjectArray(
            KEY_HISTORY
        ).mapNotNull {
            runCatching {
                playbackFromJson(
                    it
                )
            }.getOrNull()
        }

    private fun writeHistory(
        entries:
            List<LibraryPlaybackEntry>,
    ) {
        writeArray(
            KEY_HISTORY,
            entries.map {
                playbackToJson(
                    it
                )
            },
        )
    }

    private fun readObjectArray(
        key: String,
    ): List<JSONObject> {
        val raw =
            prefs.getString(
                key,
                null,
            )
                ?: return emptyList()

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    index in
                    0 until
                        array.length()
                ) {
                    array.optJSONObject(
                        index
                    )?.let(::add)
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    private fun writeArray(
        key: String,
        entries: List<JSONObject>,
    ) {
        val array =
            JSONArray()

        entries.forEach {
            array.put(it)
        }

        prefs.edit()
            .putString(
                key,
                array.toString(),
            )
            .apply()
    }

    private fun playbackToJson(
        entry: LibraryPlaybackEntry,
    ): JSONObject =
        JSONObject()
            .put(
                "media",
                mediaToJson(
                    entry.media
                ),
            )
            .put(
                "videoId",
                entry.videoId,
            )
            .put(
                "episodeTitle",
                entry.episodeTitle,
            )
            .put(
                "season",
                entry.season,
            )
            .put(
                "episode",
                entry.episode,
            )
            .put(
                "positionMs",
                entry.positionMs,
            )
            .put(
                "durationMs",
                entry.durationMs,
            )
            .put(
                "lastWatchedEpochMs",
                entry.lastWatchedEpochMs,
            )

    private fun playbackFromJson(
        json: JSONObject,
    ): LibraryPlaybackEntry =
        LibraryPlaybackEntry(
            media =
                mediaFromJson(
                    json.getJSONObject(
                        "media"
                    )
                ),
            videoId =
                json.getString(
                    "videoId"
                ),
            episodeTitle =
                json.optString(
                    "episodeTitle",
                ).takeIf {
                    it.isNotBlank()
                },
            season =
                json.optIntOrNull(
                    "season"
                ),
            episode =
                json.optIntOrNull(
                    "episode"
                ),
            positionMs =
                json.optLong(
                    "positionMs",
                    0L,
                ),
            durationMs =
                json.optLong(
                    "durationMs",
                    0L,
                ),
            lastWatchedEpochMs =
                json.optLong(
                    "lastWatchedEpochMs",
                    0L,
                ),
        )

    private fun mediaToJson(
        media: MediaItem,
    ): JSONObject =
        JSONObject()
            .put(
                "id",
                media.id,
            )
            .put(
                "type",
                media.type,
            )
            .put(
                "name",
                media.name,
            )
            .put(
                "poster",
                media.poster,
            )
            .put(
                "background",
                media.background,
            )
            .put(
                "description",
                media.description,
            )
            .put(
                "releaseInfo",
                media.releaseInfo,
            )
            .put(
                "genres",
                JSONArray(
                    media.genres
                ),
            )
            .put(
                "sourceExtensionId",
                media.sourceExtensionId,
            )

    private fun mediaFromJson(
        json: JSONObject,
    ): MediaItem {
        val genresJson =
            json.optJSONArray(
                "genres"
            )

        val genres =
            buildList {
                if (
                    genresJson != null
                ) {
                    for (
                        index in
                        0 until
                            genresJson.length()
                    ) {
                        genresJson
                            .optString(
                                index
                            )
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let(::add)
                    }
                }
            }

        return MediaItem(
            id =
                json.getString(
                    "id"
                ),
            type =
                json.getString(
                    "type"
                ),
            name =
                json.getString(
                    "name"
                ),
            poster =
                json.optNullableString(
                    "poster"
                ),
            background =
                json.optNullableString(
                    "background"
                ),
            description =
                json.optNullableString(
                    "description"
                ),
            releaseInfo =
                json.optNullableString(
                    "releaseInfo"
                ),
            genres =
                genres,
            sourceExtensionId =
                json.optNullableString(
                    "sourceExtensionId"
                ),
        )
    }

    private fun JSONObject
        .optNullableString(
            key: String,
        ): String? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optString(
            key
        ).takeIf {
            it.isNotBlank() &&
                it != "null"
        }
    }

    private fun JSONObject
        .optIntOrNull(
            key: String,
        ): Int? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optInt(key)
    }

    companion object {
        private const val PREFS_NAME =
            "vueo_library"

        private const val KEY_WATCHLIST =
            "watchlist"

        private const val KEY_HISTORY =
            "history"

        private const val MAX_HISTORY =
            150
    }
}
