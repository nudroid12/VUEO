package com.vueo.app.core.model

data class MediaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val genres: List<String> = emptyList(),
    val sourceExtensionId: String? = null,
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
)

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
