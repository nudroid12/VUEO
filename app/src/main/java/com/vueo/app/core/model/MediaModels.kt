package com.vueo.app.core.model

data class EpisodeItem(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
)

data class MediaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val sourceExtensionId: String? = null,
)

data class CatalogRow(
    val id: String,
    val title: String,
    val providerName: String,
    val items: List<MediaItem>,
)

data class StreamSource(
    val name: String,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val quality: String? = null,
    val codec: String? = null,
    val hdr: String? = null,
    val audio: String? = null,
    val language: String? = null,
    val sizeBytes: Long? = null,
    val providerId: String,
    val providerName: String,
) {
    val isDirectPlayable: Boolean
        get() = url?.startsWith("https://") == true
}

data class SubtitleTrack(
    val id: String,
    val language: String,
    val url: String,
    val providerId: String,
)

data class CatalogPage(
    val items: List<MediaItem>,
    val hasMore: Boolean = false,
)
