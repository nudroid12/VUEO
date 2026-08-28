package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.MediaItem

object CatalogDiscoveryCache {
    private const val HOME_TTL_MS = 10 * 60_000L
    private const val SEARCH_TTL_MS = 5 * 60_000L
    private const val MAX_SEARCH_ENTRIES = 20

    private var homeRows: List<CatalogRow> =
        emptyList()

    private var homeUpdatedAt: Long = 0L

    private val searches =
        object : LinkedHashMap<String, SearchEntry>(
            24,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, SearchEntry>?,
            ): Boolean =
                size > MAX_SEARCH_ENTRIES
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

    @Synchronized
    fun search(
        query: String,
    ): List<MediaItem>? {
        val key = normalizeQuery(query)
        val entry = searches[key]
            ?: return null

        val age =
            System.currentTimeMillis() -
                entry.updatedAt

        if (age > SEARCH_TTL_MS) {
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
        val key = normalizeQuery(query)

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
                    System.currentTimeMillis(),
            )
    }

    @Synchronized
    fun searchLocal(
        query: String,
        limit: Int = 60,
    ): List<MediaItem> {
        val needle =
            normalizeQuery(query)

        if (needle.length < 2) {
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
                .map { it.lowercase() }
                .toSet()

        return allCachedItems()
            .asSequence()
            .filter {
                it.id != item.id &&
                    it.type == item.type
            }
            .map { candidate ->
                val overlap =
                    candidate.genres.count {
                        it.lowercase() in
                            targetGenres
                    }

                candidate to overlap
            }
            .sortedWith(
                compareByDescending<
                    Pair<MediaItem, Int>
                > {
                    it.second
                }.thenBy {
                    it.first.name
                }
            )
            .map { it.first }
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
            append(item.releaseInfo.orEmpty())
            append(' ')
            append(
                item.genres.joinToString(" ")
            )
        }.lowercase()

    private fun normalizeQuery(
        query: String,
    ): String =
        query.trim().lowercase()

    private data class SearchEntry(
        val items: List<MediaItem>,
        val updatedAt: Long,
    )
}
