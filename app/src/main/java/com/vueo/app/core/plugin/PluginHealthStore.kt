package com.vueo.app.core.plugin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ProviderHealthStatus(
    val label: String,
) {
    ONLINE("Online"),
    SLOW("Slow"),
    NO_RESULTS("No Results"),
    NEEDS_SETUP("Needs Setup"),
    UNAVAILABLE("Unavailable"),
    BLOCKED("Blocked"),
    TIMEOUT("Timeout"),
    FAILED("Failed"),
    UNKNOWN("Unknown"),
}

data class ProviderHealthRecord(
    val repositoryManifestUrl: String,
    val repositoryName: String,
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus,
    val responseMs: Long? = null,
    val streamCount: Int = 0,
    val error: String? = null,
    val logs: List<String> = emptyList(),
    val lastCheckedEpochMs: Long,
)

class PluginHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun records(): List<ProviderHealthRecord> {
        val raw = prefs.getString(KEY_RECORDS, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index ->
                    array.optJSONObject(index)?.toRecord()
                }
        }.getOrDefault(emptyList())
    }

    fun record(
        repositoryManifestUrl: String,
        providerId: String,
    ): ProviderHealthRecord? =
        records().firstOrNull {
            it.repositoryManifestUrl == repositoryManifestUrl &&
                it.providerId == providerId
        }

    fun save(record: ProviderHealthRecord) {
        val next = records()
            .filterNot {
                it.repositoryManifestUrl == record.repositoryManifestUrl &&
                    it.providerId == record.providerId
            }
            .toMutableList()

        next += record
        saveRecords(next)
    }

    fun removeRepository(manifestUrl: String) {
        saveRecords(
            records().filterNot {
                it.repositoryManifestUrl == manifestUrl
            }
        )
    }

    fun summary(
        repositories: List<PluginRepositoryDescriptor>,
        pluginStore: PluginStore,
    ): ProviderHealthSummary {
        val known = records().associateBy {
            it.repositoryManifestUrl to it.providerId
        }

        var online = 0
        var slow = 0
        var noResults = 0
        var needsSetup = 0
        var unavailable = 0
        var blocked = 0
        var timeout = 0
        var failed = 0
        var unknown = 0
        var disabled = 0

        repositories.forEach { repository ->
            repository.providers.forEach { provider ->
                if (!pluginStore.isProviderEnabled(repository, provider)) {
                    disabled++
                    return@forEach
                }

                when (
                    known[repository.manifestUrl to provider.id]?.status
                        ?: ProviderHealthStatus.UNKNOWN
                ) {
                    ProviderHealthStatus.ONLINE -> online++
                    ProviderHealthStatus.SLOW -> slow++
                    ProviderHealthStatus.NO_RESULTS -> noResults++
                    ProviderHealthStatus.NEEDS_SETUP -> needsSetup++
                    ProviderHealthStatus.UNAVAILABLE -> unavailable++
                    ProviderHealthStatus.BLOCKED -> blocked++
                    ProviderHealthStatus.TIMEOUT -> timeout++
                    ProviderHealthStatus.FAILED -> failed++
                    ProviderHealthStatus.UNKNOWN -> unknown++
                }
            }
        }

        return ProviderHealthSummary(
            online = online,
            slow = slow,
            noResults = noResults,
            needsSetup = needsSetup,
            unavailable = unavailable,
            blocked = blocked,
            timeout = timeout,
            failed = failed,
            unknown = unknown,
            disabled = disabled,
        )
    }

    private fun saveRecords(records: List<ProviderHealthRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "vueo_plugin_health"
        private const val KEY_RECORDS = "provider_health_records"
    }
}

data class ProviderHealthSummary(
    val online: Int,
    val slow: Int,
    val noResults: Int,
    val needsSetup: Int,
    val unavailable: Int,
    val blocked: Int,
    val timeout: Int,
    val failed: Int,
    val unknown: Int,
    val disabled: Int,
)

private fun ProviderHealthRecord.toJson(): JSONObject =
    JSONObject().apply {
        put("repositoryManifestUrl", repositoryManifestUrl)
        put("repositoryName", repositoryName)
        put("providerId", providerId)
        put("providerName", providerName)
        put("status", status.name)
        responseMs?.let { put("responseMs", it) }
        put("streamCount", streamCount)
        put("error", error)
        put("logs", JSONArray(logs))
        put("lastCheckedEpochMs", lastCheckedEpochMs)
    }

private fun JSONObject.toRecord(): ProviderHealthRecord? {
    val repositoryManifestUrl = optString("repositoryManifestUrl")
        .takeIf { it.isNotBlank() } ?: return null
    val providerId = optString("providerId")
        .takeIf { it.isNotBlank() } ?: return null

    return ProviderHealthRecord(
        repositoryManifestUrl = repositoryManifestUrl,
        repositoryName = optString("repositoryName", "Repository"),
        providerId = providerId,
        providerName = optString("providerName", providerId),
        status = runCatching {
            ProviderHealthStatus.valueOf(
                optString("status", ProviderHealthStatus.UNKNOWN.name)
            )
        }.getOrDefault(ProviderHealthStatus.UNKNOWN),
        responseMs = if (has("responseMs")) optLong("responseMs") else null,
        streamCount = optInt("streamCount", 0),
        error = optString("error").takeIf { it.isNotBlank() },
        logs = optJSONArray("logs").toStringList(),
        lastCheckedEpochMs = optLong("lastCheckedEpochMs", 0L),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
