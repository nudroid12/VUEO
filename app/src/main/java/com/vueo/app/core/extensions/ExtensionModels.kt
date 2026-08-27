package com.vueo.app.core.extensions

enum class ExtensionKind {
    STREMIO_ADDON,
    VUEO_PLUGIN,
}

enum class ExtensionHealth {
    ONLINE,
    SLOW,
    OFFLINE,
    UNKNOWN,
}

data class ExtensionDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val kind: ExtensionKind,
    val baseUrl: String,
    val description: String? = null,
    val resources: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
    val health: ExtensionHealth = ExtensionHealth.UNKNOWN,
)
