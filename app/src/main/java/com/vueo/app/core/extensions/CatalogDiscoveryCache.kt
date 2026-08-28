package com.vueo.app.core.extensions

import android.content.Context
import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object CatalogDiscoveryCache {
    private const val HOME_TTL_MS =
        10 * 60_000L

    private const val DISK_HOME_MAX_AGE_MS =
        48 * 60 * 60_000L

    private const val SEARCH_TTL_MS =
        5 * 60_000L

    private const val MAX_SEARCH_ENTRIES =
        20

    private const val MAX_DISK_ROWS =
        12

    private const val MAX_DISK_ITEMS_PER_ROW =
        50

    private const val PREFS_NAME =
        "vueo_catalog_cache"

    private const val KEY_HOME =
        "home_v1"

    private var homeRows:
        List<CatalogRow> =
        emptyList()

    private var homeUpdatedAt:
        Long = 0L

    private val searches =
        object :
            LinkedHashMap<
                String,
                SearchEntry
            >(
                24,
                0.75f,
                true,
            ) {
            override fun removeEldestEntry(
                eldest:
                    MutableMap.MutableEntry<
                        String,
                        SearchEntry
                    >?,
            ): Boolean =
                size >
                    MAX_SEARCH_ENTRIES
        }

    @Synchronized
    fun home(
        allowStale: Boolean = false,
    ): List<CatalogRow>? {
        if (homeRows.isEmpty()) {
            return null
        }

        val age =
            System.currentTimeMillis() -
                homeUpdatedAt

        if (
            !allowStale &&
            age > HOME_TTL_MS
        ) {
            return null
        }

        return homeRows
    }

    @Synchronized
    fun putHome(
        rows: List<CatalogRow>,
    ) {
        if (rows.isEmpty()) {
            return
        }

        homeRows = rows
        homeUpdatedAt =
            System.currentTimeMillis()
    }

    suspend fun restoreHome(
        context: Context,
    ): List<CatalogRow> =
        withContext(
            Dispatchers.IO
        ) {
            val prefs =
                context
                    .applicationContext
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )

            val raw =
                prefs.getString(
                    KEY_HOME,
                    null,
                )
                    ?: return@withContext
                        emptyList()

            val decoded =
                runCatching {
                    val root =
                        JSONObject(raw)

                    val updatedAt =
                        root.optLong(
                            "updatedAt",
                            0L,
                        )

                    val age =
                        System
                            .currentTimeMillis() -
                            updatedAt

                    if (
                        updatedAt <= 0L ||
                        age >
                            DISK_HOME_MAX_AGE_MS
                    ) {
                        prefs.edit()
                            .remove(
                                KEY_HOME
                            )
                            .apply()

                        return@runCatching
                            emptyList<
                                CatalogRow
                            >()
                    }

                    val rows =
                        root.optJSONArray(
                            "rows"
                        ).toCatalogRows()

                    if (
                        rows.isNotEmpty()
                    ) {
                        synchronized(this@CatalogDiscoveryCache) {
                            homeRows =
                                rows

                            homeUpdatedAt =
                                updatedAt
                        }
                    }

                    rows
                }.getOrElse {
                    prefs.edit()
                        .remove(KEY_HOME)
                        .apply()

                    emptyList()
                }

            decoded
        }

    suspend fun persistHome(
        context: Context,
        rows: List<CatalogRow>,
    ) {
        if (rows.isEmpty()) {
            return
        }

        val snapshot =
            rows
                .take(
                    MAX_DISK_ROWS
                )
                .map {
                    row ->

                    row.copy(
                        items =
                            row.items.take(
                                MAX_DISK_ITEMS_PER_ROW
                            )
                    )
                }

        withContext(
            Dispatchers.IO
        ) {
            val root =
                JSONObject()
                    .put(
                        "updatedAt",
                        System
                            .currentTimeMillis(),
                    )
                    .put(
                        "rows",
                        snapshot.toJson(),
                    )

            context
                .applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .edit()
                .putString(
                    KEY_HOME,
                    root.toString(),
                )
                .apply()
        }
    }

    @Synchronized
    fun invalidateHomeMemory() {
        homeUpdatedAt = 0L
    }

    @Synchronized
    fun clearMemory() {
        homeRows =
            emptyList()

        homeUpdatedAt = 0L
        searches.clear()
    }

    suspend fun clearAll(
        context: Context,
    ) {
        clearMemory()

        withContext(
            Dispatchers.IO
        ) {
            context
                .applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .edit()
                .clear()
                .apply()
        }
    }

    @Synchronized
    fun search(
        query: String,
    ): List<MediaItem>? {
        val key =
            normalizeQuery(query)

        val entry =
            searches[key]
                ?: return null

        val age =
            System.currentTimeMillis() -
                entry.updatedAt

        if (
            age > SEARCH_TTL_MS
        ) {
            searches.remove(key)
            return null
        }

        return entry.items
    }

    @Synchronized
    fun putSearch(
        query: String,
        items: List<MediaItem>,
    ) {
        val key =
            normalizeQuery(query)

        if (
            key.isBlank() ||
            items.isEmpty()
        ) {
            return
        }

        searches[key] =
            SearchEntry(
                items = items,
                updatedAt =
                    System
                        .currentTimeMillis(),
            )
    }

    @Synchronized
    fun searchLocal(
        query: String,
        limit: Int = 60,
    ): List<MediaItem> {
        val needle =
            normalizeQuery(query)

        if (
            needle.length < 2
        ) {
            return emptyList()
        }

        return allCachedItems()
            .asSequence()
            .filter { item ->
                searchableText(item)
                    .contains(needle)
            }
            .distinctBy {
                "${it.type}:${it.id}"
            }
            .take(limit)
            .toList()
    }

    @Synchronized
    fun related(
        item: MediaItem,
        limit: Int = 16,
    ): List<MediaItem> {
        val targetGenres =
            item.genres
                .map {
                    it.lowercase()
                }
                .toSet()

        return allCachedItems()
            .asSequence()
            .filter {
                it.id != item.id &&
                    it.type == item.type
            }
            .map { candidate ->
                val overlap =
                    candidate.genres
                        .count {
                            it.lowercase() in
                                targetGenres
                        }

                candidate to overlap
            }
            .sortedWith(
                compareByDescending<
                    Pair<
                        MediaItem,
                        Int
                    >
                > {
                    it.second
                }.thenBy {
                    it.first.name
                }
            )
            .map {
                it.first
            }
            .distinctBy {
                "${it.type}:${it.id}"
            }
            .take(limit)
            .toList()
    }

    @Synchronized
    private fun allCachedItems():
        List<MediaItem> =
        buildList {
            homeRows.forEach {
                addAll(it.items)
            }

            searches.values.forEach {
                addAll(it.items)
            }
        }

    private fun searchableText(
        item: MediaItem,
    ): String =
        buildString {
            append(item.name)
            append(' ')
            append(
                item.releaseInfo
                    .orEmpty()
            )
            append(' ')
            append(
                item.genres
                    .joinToString(" ")
            )
        }.lowercase()

    private fun normalizeQuery(
        query: String,
    ): String =
        query.trim()
            .lowercase()

    private data class SearchEntry(
        val items: List<MediaItem>,
        val updatedAt: Long,
    )
}

private fun List<CatalogRow>
    .toJson(): JSONArray =
    JSONArray().also {
        array ->

        forEach { row ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        row.id,
                    )
                    .put(
                        "title",
                        row.title,
                    )
                    .put(
                        "providerName",
                        row.providerName,
                    )
                    .put(
                        "items",
                        row.items
                            .toJson(),
                    )
            )
        }
    }

private fun List<MediaItem>
    .toJson(): JSONArray =
    JSONArray().also {
        array ->

        forEach { item ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        item.id,
                    )
                    .put(
                        "type",
                        item.type,
                    )
                    .put(
                        "name",
                        item.name,
                    )
                    .put(
                        "poster",
                        item.poster,
                    )
                    .put(
                        "background",
                        item.background,
                    )
                    .put(
                        "description",
                        item.description,
                    )
                    .put(
                        "releaseInfo",
                        item.releaseInfo,
                    )
                    .put(
                        "genres",
                        JSONArray(
                            item.genres
                        ),
                    )
                    .put(
                        "sourceExtensionId",
                        item.sourceExtensionId,
                    )
            )
        }
    }

private fun JSONArray?
    .toCatalogRows():
    List<CatalogRow> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val row =
                optJSONObject(index)
                    ?: continue

            val id =
                row.optString("id")
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: continue

            val title =
                row.optString(
                    "title"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            val providerName =
                row.optString(
                    "providerName",
                    "Addon",
                )

            val items =
                row.optJSONArray(
                    "items"
                ).toMediaItems()

            if (
                items.isNotEmpty()
            ) {
                add(
                    CatalogRow(
                        id = id,
                        title = title,
                        providerName =
                            providerName,
                        items = items,
                    )
                )
            }
        }
    }
}

private fun JSONArray?
    .toMediaItems():
    List<MediaItem> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val json =
                optJSONObject(index)
                    ?: continue

            val id =
                json.optString(
                    "id"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            val name =
                json.optString(
                    "name"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            add(
                MediaItem(
                    id = id,
                    type =
                        json.optString(
                            "type",
                            "movie",
                        ),
                    name = name,
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
                        json.optJSONArray(
                            "genres"
                        ).toStringList(),
                    sourceExtensionId =
                        json.optNullableString(
                            "sourceExtensionId"
                        ),
                )
            )
        }
    }
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

    return optString(key)
        .takeIf {
            it.isNotBlank() &&
                it != "null"
        }
}

private fun JSONArray?
    .toStringList():
    List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            optString(index)
                .takeIf {
                    it.isNotBlank()
                }
                ?.let(::add)
        }
    }
}
