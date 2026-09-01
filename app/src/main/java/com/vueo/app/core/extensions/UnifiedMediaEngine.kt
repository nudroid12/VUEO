package com.vueo.app.core.extensions

import com.vueo.app.core.model.CatalogRow
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import com.vueo.app.core.player.PlayerSourcePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

class UnifiedMediaEngine {
    private val extensions = CopyOnWriteArrayList<MediaExtension>()
    private val disabledExtensionIds =
        CopyOnWriteArraySet<String>()

    fun install(extension: MediaExtension) {
        extensions.removeAll { it.descriptor.id == extension.descriptor.id }
        extensions += extension
    }

    fun uninstall(id: String) {
        extensions.removeAll { it.descriptor.id == id }
        disabledExtensionIds.remove(id)
    }

    fun setExtensionEnabled(
        id: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            disabledExtensionIds.remove(id)
        } else {
            disabledExtensionIds.add(id)
        }
    }

    fun isExtensionEnabled(
        id: String,
    ): Boolean =
        id !in disabledExtensionIds

    fun installed(): List<MediaExtension> = extensions.toList()

    fun stremioAddons(): List<MediaExtension> =
        extensions.filter { it.descriptor.kind == ExtensionKind.STREMIO_ADDON }

    fun activeStremioAddons(): List<MediaExtension> =
        stremioAddons().filter {
            isExtensionEnabled(
                it.descriptor.id
            )
        }

    fun extension(id: String?): MediaExtension? =
        id?.let { target -> extensions.firstOrNull { it.descriptor.id == target } }

    suspend fun loadCatalogRows(
        maxRows: Int = 10,
        forceRefresh: Boolean = false,
        catalogOrder: List<String> = emptyList(),
    ): List<CatalogRow> = coroutineScope {
        if (!forceRefresh) {
            CatalogDiscoveryCache
                .home()
                ?.let {
                    return@coroutineScope orderCatalogRows(
                        rows = it,
                        catalogOrder = catalogOrder,
                    ).take(maxRows)
                }
        }

        val orderIndex =
            catalogOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }

        val candidates =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter {
                            it.canLoadWithoutExtras
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .sortedBy {
                    (extension, catalog) ->
                    orderIndex[
                        catalogKey(
                            extensionId =
                                extension.descriptor.id,
                            type = catalog.type,
                            catalogId = catalog.id,
                        )
                    ] ?: Int.MAX_VALUE
                }
                .take(maxRows)

        val rows =
            candidates
                .map {
                    (extension, catalog) ->
                    async {
                        runCatching {
                            val page =
                                withTimeoutOrNull(
                                    ADDON_REQUEST_TIMEOUT_MS
                                ) {
                                    extension.catalog(
                                        catalog.type,
                                        catalog.id,
                                    )
                                }
                                    ?: return@runCatching null

                            CatalogRow(
                                id =
                                    catalogKey(
                                        extensionId =
                                            extension.descriptor.id,
                                        type =
                                            catalog.type,
                                        catalogId =
                                            catalog.id,
                                    ),
                                title =
                                    catalog.name
                                        ?: "${extension.descriptor.name} " +
                                            catalog.type
                                                .replaceFirstChar {
                                                    it.uppercase()
                                                },
                                providerName =
                                    extension
                                        .descriptor
                                        .name,
                                items =
                                    page.items,
                            )
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
                .filter {
                    it.items.isNotEmpty()
                }
                .let {
                    orderCatalogRows(
                        rows = it,
                        catalogOrder = catalogOrder,
                    )
                }

        CatalogDiscoveryCache.putHome(
            rows
        )

        rows
    }

    private fun catalogKey(
        extensionId: String,
        type: String,
        catalogId: String,
    ): String =
        "$extensionId:$type:$catalogId"

    private fun orderCatalogRows(
        rows: List<CatalogRow>,
        catalogOrder: List<String>,
    ): List<CatalogRow> {
        if (catalogOrder.isEmpty()) {
            return rows
        }

        val index =
            catalogOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }

        return rows.sortedBy {
            index[it.id] ?: Int.MAX_VALUE
        }
    }

    suspend fun search(
        query: String,
        maxCatalogs: Int = 12,
        maxResults: Int = 80,
        onPartial: ((List<MediaItem>) -> Unit)? = null,
    ): List<MediaItem> = coroutineScope {
        val normalized = query.trim()

        if (normalized.length < 2) {
            return@coroutineScope emptyList<MediaItem>()
        }

        CatalogDiscoveryCache
            .search(normalized)
            ?.let { cached ->
                return@coroutineScope rankSearchResults(
                    items = cached,
                    query = normalized,
                ).take(maxResults)
            }

        val searchableCatalogs =
            activeStremioAddons()
                .flatMap { extension ->
                    extension.descriptor.catalogs
                        .filter { catalog ->
                            val hasSearch =
                                catalog.extras.any {
                                    it.name == "search"
                                }

                            val requiredSupported =
                                catalog.extras
                                    .filter {
                                        it.isRequired
                                    }
                                    .all {
                                        it.name == "search"
                                    }

                            hasSearch &&
                                requiredSupported
                        }
                        .map { catalog ->
                            extension to catalog
                        }
                }
                .take(maxCatalogs)

        val local =
            CatalogDiscoveryCache
                .searchLocal(
                    normalized,
                    limit = maxResults,
                )

        val collectedRemote =
            mutableListOf<MediaItem>()
        val mergeMutex =
            Mutex()

        val remote =
            searchableCatalogs
                .map {
                    (extension, catalog) ->

                    async {
                        val result =
                            try {
                                withTimeoutOrNull(
                                    ADDON_REQUEST_TIMEOUT_MS
                                ) {
                                    extension.catalog(
                                        type =
                                            catalog.type,
                                        catalogId =
                                            catalog.id,
                                        extras =
                                            mapOf(
                                                "search" to
                                                    normalized
                                            ),
                                    ).items
                                }
                                    ?: emptyList()
                            } catch (
                                cancelled:
                                    CancellationException
                            ) {
                                throw cancelled
                            } catch (
                                _: Throwable
                            ) {
                                emptyList()
                            }

                        if (result.isNotEmpty()) {
                            mergeMutex.withLock {
                                collectedRemote +=
                                    result

                                onPartial?.invoke(
                                    rankSearchResults(
                                        items =
                                            collectedRemote +
                                                local,
                                        query =
                                            normalized,
                                    ).take(
                                        maxResults
                                    )
                                )
                            }
                        }

                        result
                    }
                }
                .awaitAll()
                .flatten()

        val combined =
            rankSearchResults(
                items = remote + local,
                query = normalized,
            ).take(maxResults)

        CatalogDiscoveryCache.putSearch(
            normalized,
            combined,
        )

        combined
    }

    private fun rankSearchResults(
        items: List<MediaItem>,
        query: String,
    ): List<MediaItem> {
        val normalizedQuery =
            normalizeSearchText(query)

        return items
            .groupBy {
                searchIdentityKey(it)
            }
            .values
            .mapNotNull { duplicates ->
                duplicates.maxByOrNull { item ->
                    searchRelevanceScore(
                        item = item,
                        query = normalizedQuery,
                    ) * 100 +
                        searchMetadataScore(item)
                }
            }
            .sortedWith(
                compareByDescending<MediaItem> {
                    searchRelevanceScore(
                        item = it,
                        query = normalizedQuery,
                    )
                }.thenByDescending {
                    searchMetadataScore(it)
                }.thenByDescending {
                    it.imdbRating
                        ?: it.tmdbRating
                        ?: 0.0
                }
            )
    }

    private fun searchIdentityKey(
        item: MediaItem,
    ): String {
        val title =
            normalizeSearchText(item.name)
        val year =
            searchReleaseYear(item)

        return if (title.isNotBlank()) {
            "${item.type.lowercase()}|$title|${year.takeIf { it > 0 } ?: 0}"
        } else {
            "${item.type}:${item.id}"
        }
    }

    private fun searchRelevanceScore(
        item: MediaItem,
        query: String,
    ): Int {
        val normalizedQuery =
            normalizeSearchText(query)
        val title =
            normalizeSearchText(item.name)

        if (
            normalizedQuery.isBlank() ||
            title.isBlank()
        ) {
            return 0
        }

        val queryTokens =
            normalizedQuery
                .split(' ')
                .filter { it.isNotBlank() }
        val titleTokens =
            title
                .split(' ')
                .filter { it.isNotBlank() }

        var score =
            when {
                title == normalizedQuery ->
                    100_000

                title.startsWith("$normalizedQuery ") ->
                    82_000

                title.contains(" $normalizedQuery ") ||
                    title.endsWith(" $normalizedQuery") ->
                    72_000

                title.contains(normalizedQuery) ->
                    64_000

                queryTokens.all { it in titleTokens } ->
                    52_000

                queryTokens.all { token ->
                    titleTokens.any {
                        it.startsWith(token)
                    }
                } ->
                    44_000

                else -> 0
            }

        if (score == 0) {
            score +=
                queryTokens.count { token ->
                    titleTokens.any {
                        it.startsWith(token) ||
                            token.startsWith(it)
                    }
                } * 4_000
        }

        val queryYear =
            Regex(
                """\b(19|20)\d{2}\b"""
            )
                .find(normalizedQuery)
                ?.value
                ?.toIntOrNull()

        if (queryYear != null) {
            score +=
                if (searchReleaseYear(item) == queryYear) {
                    9_000
                } else {
                    -2_000
                }
        }

        return score
    }

    private fun searchMetadataScore(
        item: MediaItem,
    ): Int {
        var score = 0

        if (!item.poster.isNullOrBlank()) score += 80
        if (!item.background.isNullOrBlank()) score += 35
        if (!item.description.isNullOrBlank()) score += 30
        if (!item.releaseInfo.isNullOrBlank()) score += 20
        if (item.genres.isNotEmpty()) score += 15

        score +=
            (((
                item.imdbRating
                    ?: item.tmdbRating
                    ?: 0.0
            ) * 10.0).toInt())

        return score
    }

    private fun searchReleaseYear(
        item: MediaItem,
    ): Int =
        item.releaseInfo
            ?.let {
                Regex(
                    """\b(19|20)\d{2}\b"""
                )
                    .find(it)
                    ?.value
                    ?.toIntOrNull()
            }
            ?: 0

    private fun normalizeSearchText(
        value: String,
    ): String =
        value
            .lowercase()
            .replace(
                Regex(
                    """[^a-z0-9]+"""
                ),
                " ",
            )
            .trim()
            .replace(
                Regex(
                    """\s+"""
                ),
                " ",
            )

    suspend fun loadMeta(
        item: MediaItem,
    ): MediaItem = coroutineScope {
        val providers =
            activeStremioAddons()
                .filter { extension ->
                    "meta" in extension.descriptor.resources &&
                        (
                            extension.descriptor.types.isEmpty() ||
                                item.type in extension.descriptor.types
                            )
                }
                .sortedBy { extension ->
                    if (
                        extension.descriptor.id ==
                        item.sourceExtensionId
                    ) {
                        0
                    } else {
                        1
                    }
                }

        if (providers.isEmpty()) {
            return@coroutineScope item
        }

        val primaryProvider =
            providers.firstOrNull {
                it.descriptor.id == item.sourceExtensionId
            }
        val primaryMetadata =
            primaryProvider?.let { provider ->
                runCatching {
                    withTimeoutOrNull(
                        ADDON_REQUEST_TIMEOUT_MS
                    ) {
                        provider.meta(
                            item.type,
                            item.id,
                        )
                    }
                }.getOrNull()
            }
        val primaryResult =
            primaryMetadata?.let { metadata ->
                mergeMediaMetadata(
                    current = item,
                    candidate = metadata,
                    sourceExtensionId = item.sourceExtensionId,
                )
            } ?: item

        if (!needsMetadataFallback(primaryResult)) {
            return@coroutineScope primaryResult
        }

        val fallbackMetadata =
            providers
                .filterNot {
                    it.descriptor.id == primaryProvider?.descriptor?.id
                }
                .map { provider ->
                    async {
                        runCatching {
                            withTimeoutOrNull(
                                METADATA_FALLBACK_TIMEOUT_MS
                            ) {
                                provider.meta(
                                    item.type,
                                    item.id,
                                )
                            }
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()

        fallbackMetadata.fold(primaryResult) { merged, candidate ->
            mergeMediaMetadata(
                current = merged,
                candidate = candidate,
                sourceExtensionId = item.sourceExtensionId,
            )
        }
    }

    suspend fun resolveStreams(
        type: String,
        videoId: String,
    ): List<StreamSource> =
        resolveStreamsProgressive(
            type = type,
            videoId = videoId,
            onProgress = {},
        )

    suspend fun resolveStreamsProgressive(
        type: String,
        videoId: String,
        onProgress: suspend (AddonStreamProgress) -> Unit,
    ): List<StreamSource> = coroutineScope {
        val providers =
            extensions.filter {
                isExtensionEnabled(
                    it.descriptor.id
                ) &&
                    "stream" in
                        it.descriptor.resources
            }

        if (providers.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val mutex = Mutex()
        val rawStreams =
            mutableListOf<StreamSource>()

        var completed = 0

        providers.map { extension ->
            async {
                val result =
                    runCatching {
                        withTimeoutOrNull(
                            ADDON_STREAM_TIMEOUT_MS
                        ) {
                            extension.streams(
                                type,
                                videoId,
                            )
                        }
                            ?: emptyList()
                    }.getOrDefault(
                        emptyList()
                    )

                val progress =
                    mutex.withLock {
                        rawStreams += result
                        completed++

                        AddonStreamProgress(
                            streams =
                                SourceCleaner.clean(
                                    rawStreams
                                ),
                            rawCount =
                                rawStreams.size,
                            completedAddons =
                                completed,
                            totalAddons =
                                providers.size,
                        )
                    }

                onProgress(progress)
            }
        }.awaitAll()

        mutex.withLock {
            SourceCleaner.clean(
                rawStreams
            )
        }
    }

    suspend fun resolveSubtitles(
        type: String,
        videoId: String,
    ): List<SubtitleTrack> = coroutineScope {
        extensions
            .filter {
                isExtensionEnabled(
                    it.descriptor.id
                ) &&
                    "subtitles" in
                        it.descriptor.resources
            }
            .map { extension ->
                async {
                    runCatching {
                        withTimeoutOrNull(
                            ADDON_REQUEST_TIMEOUT_MS
                        ) {
                            extension.subtitles(
                                type,
                                videoId,
                            )
                        }
                            ?: emptyList()
                    }.getOrDefault(
                        emptyList()
                    )
                }
            }
            .awaitAll()
            .flatten()
            .filter { it.url.startsWith("https://") }
            .distinctBy { it.url }
    }
    companion object {
        private const val ADDON_REQUEST_TIMEOUT_MS =
            8_000L

        private const val METADATA_FALLBACK_TIMEOUT_MS =
            4_000L

        private const val ADDON_STREAM_TIMEOUT_MS =
            10_000L
    }
}

private fun mergeMediaMetadata(
    current: MediaItem,
    candidate: MediaItem,
    sourceExtensionId: String?,
): MediaItem =
    current.copy(
        name = current.name.ifBlank { candidate.name },
        poster = current.poster ?: candidate.poster,
        background = current.background ?: candidate.background,
        description = richerMetadataText(
            current.description,
            candidate.description,
        ),
        releaseInfo = current.releaseInfo ?: candidate.releaseInfo,
        genres = (current.genres + candidate.genres).distinct(),
        episodes = mergeMetadataEpisodes(
            current.episodes,
            candidate.episodes,
        ),
        sourceExtensionId = sourceExtensionId,
        imdbRating = current.imdbRating ?: candidate.imdbRating,
        tmdbRating = current.tmdbRating ?: candidate.tmdbRating,
        runtimeMinutes = current.runtimeMinutes ?: candidate.runtimeMinutes,
        certification = current.certification ?: candidate.certification,
        directors = (current.directors + candidate.directors).distinct(),
        creators = (current.creators + candidate.creators).distinct(),
        writers = (current.writers + candidate.writers).distinct(),
        cast = (current.cast + candidate.cast).distinctBy { it.name.lowercase() },
        productionCompanies =
            (current.productionCompanies + candidate.productionCompanies)
                .distinctBy { it.name.lowercase() },
        networks =
            (current.networks + candidate.networks)
                .distinctBy { it.name.lowercase() },
    )

private fun needsMetadataFallback(item: MediaItem): Boolean {
    if (
        item.description.isNullOrBlank() ||
        item.poster.isNullOrBlank() ||
        item.background.isNullOrBlank()
    ) {
        return true
    }

    if (item.type != "series") return false
    if (item.episodes.isEmpty()) return true

    return item.episodes.any { episode ->
        isGenericEpisodeTitle(
            title = episode.title,
            episodeNumber = episode.episode,
        ) ||
            episode.overview.isNullOrBlank() ||
            episode.thumbnail.isNullOrBlank()
    }
}

private fun mergeMetadataEpisodes(
    current: List<EpisodeItem>,
    candidate: List<EpisodeItem>,
): List<EpisodeItem> {
    if (current.isEmpty()) return candidate
    if (candidate.isEmpty()) return current

    val candidateByPosition =
        candidate.associateBy { it.season to it.episode }
    val currentPositions =
        current.mapTo(mutableSetOf()) { it.season to it.episode }

    return (
        current.map { episode ->
            val fallback =
                candidateByPosition[episode.season to episode.episode]
                    ?: return@map episode

            episode.copy(
                title = preferredEpisodeTitle(
                    current = episode.title,
                    candidate = fallback.title,
                    episodeNumber = episode.episode,
                ),
                released = episode.released ?: fallback.released,
                overview = richerMetadataText(
                    episode.overview,
                    fallback.overview,
                ),
                thumbnail = episode.thumbnail ?: fallback.thumbnail,
            )
        } +
            candidate.filter {
                (it.season to it.episode) !in currentPositions
            }
        )
        .sortedWith(
            compareBy<EpisodeItem> { it.season }
                .thenBy { it.episode }
        )
}

private fun preferredEpisodeTitle(
    current: String,
    candidate: String,
    episodeNumber: Int,
): String {
    val currentIsGeneric =
        isGenericEpisodeTitle(current, episodeNumber)
    val candidateIsGeneric =
        isGenericEpisodeTitle(candidate, episodeNumber)

    return when {
        currentIsGeneric && !candidateIsGeneric -> candidate
        current.isBlank() && candidate.isNotBlank() -> candidate
        else -> current
    }
}

private fun isGenericEpisodeTitle(
    title: String,
    episodeNumber: Int,
): Boolean =
    title.isBlank() ||
        Regex(
            pattern = "^(?:episode|ep|e)\\s*0*$episodeNumber$",
            option = RegexOption.IGNORE_CASE,
        ).matches(title.trim())

private fun richerMetadataText(
    current: String?,
    candidate: String?,
): String? =
    when {
        current.isNullOrBlank() -> candidate?.takeIf { it.isNotBlank() }
        candidate.isNullOrBlank() -> current
        candidate.length > current.length -> candidate
        else -> current
    }

data class AddonStreamProgress(
    val streams: List<StreamSource>,
    val rawCount: Int,
    val completedAddons: Int,
    val totalAddons: Int,
)

object SourceRanker {
    fun comparator(
        preferredQuality: String? = null,
    ) = PlayerSourcePolicy.comparator(preferredQuality)
}


object SourceCleaner {
    fun clean(
        sources: List<StreamSource>,
        preferredQuality: String? = null,
    ): List<StreamSource> {
        val sorted =
            sources.sortedWith(
                SourceRanker.comparator(
                    preferredQuality
                )
            )

        val seen =
            hashSetOf<String>()

        return sorted.filter { source ->
            seen.add(
                identityKey(source)
            )
        }
    }

    fun qualityBucket(
        source: StreamSource,
    ): String {
        val value =
            (
                source.quality.orEmpty() +
                " " +
                source.name
            ).lowercase()

        return when {
            "2160" in value ||
                "4k" in value ||
                "uhd" in value ->
                "4K"

            "1080" in value ->
                "1080p"

            "720" in value ->
                "720p"

            else ->
                "Other"
        }
    }

    private fun identityKey(
        source: StreamSource,
    ): String =
        when {
            !source.url.isNullOrBlank() ->
                "url:" +
                    source.url
                        .trim()

            !source.infoHash.isNullOrBlank() ->
                "torrent:" +
                    source.infoHash
                        .lowercase() +
                    ":" +
                    (
                        source.fileIndex
                            ?: -1
                    )

            else ->
                listOf(
                    "fallback",
                    source.providerId,
                    source.name,
                    source.quality,
                ).joinToString("|")
        }
}
