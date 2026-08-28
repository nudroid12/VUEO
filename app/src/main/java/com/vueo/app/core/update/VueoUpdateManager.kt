package com.vueo.app.core.update

import android.content.Context
import com.vueo.app.BuildConfig
import com.vueo.app.core.stremio.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject

data class VueoUpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val changelog: List<String>,
    val downloadUrl: String?,
    val telegramUrl: String?,
    val publishedAt: String?,
) {
    fun isNewerThanCurrent(): Boolean =
        versionCode > BuildConfig.VERSION_CODE

    fun toJson(): JSONObject = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("title", title)
        .put("changelog", JSONArray(changelog))
        .put("downloadUrl", downloadUrl)
        .put("telegramUrl", telegramUrl)
        .put("publishedAt", publishedAt)

    companion object {
        fun fromJson(json: JSONObject): VueoUpdateRelease? {
            val code = json.optInt("versionCode", -1)
            val name = json.optString("versionName")
                .takeIf { it.isNotBlank() }
                ?: return null

            if (code <= 0) {
                return null
            }

            return VueoUpdateRelease(
                versionCode = code,
                versionName = name,
                title = json.optString(
                    "title",
                    "VUEO $name",
                ),
                changelog = json.optJSONArray("changelog")
                    .toStringList(),
                downloadUrl = json.optHttpsUrl("downloadUrl"),
                telegramUrl = json.optHttpsUrl("telegramUrl"),
                publishedAt = json.optString("publishedAt")
                    .takeIf { it.isNotBlank() },
            )
        }
    }
}

data class VueoUpdateCheckResult(
    val release: VueoUpdateRelease?,
    val checkedAtEpochMs: Long,
    val fromCache: Boolean,
    val error: String? = null,
)

class VueoUpdateStore(
    context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun latestRelease(): VueoUpdateRelease? {
        val raw = prefs.getString(KEY_RELEASE_JSON, null)
            ?: return null

        return runCatching {
            VueoUpdateRelease.fromJson(JSONObject(raw))
        }.getOrNull()
    }

    fun lastCheckedAt(): Long =
        prefs.getLong(KEY_LAST_CHECKED_AT, 0L)

    fun lastError(): String? =
        prefs.getString(KEY_LAST_ERROR, null)
            ?.takeIf { it.isNotBlank() }

    fun saveSuccess(
        release: VueoUpdateRelease,
        checkedAtEpochMs: Long,
    ) {
        prefs.edit()
            .putString(
                KEY_RELEASE_JSON,
                release.toJson().toString(),
            )
            .putLong(
                KEY_LAST_CHECKED_AT,
                checkedAtEpochMs,
            )
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun saveFailure(
        error: String,
        checkedAtEpochMs: Long,
    ) {
        prefs.edit()
            .putLong(
                KEY_LAST_CHECKED_AT,
                checkedAtEpochMs,
            )
            .putString(
                KEY_LAST_ERROR,
                error.take(240),
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "vueo_update_state"
        private const val KEY_RELEASE_JSON = "latest_release_json"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val KEY_LAST_ERROR = "last_error"
    }
}

object VueoUpdateManager {
    const val DEFAULT_MANIFEST_URL =
        "https://raw.githubusercontent.com/nudroid12/VUEO/main/update.json"

    private const val MANIFEST_SCHEMA_VERSION = 1
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    suspend fun check(
        context: Context,
        force: Boolean = false,
    ): VueoUpdateCheckResult {
        val appContext = context.applicationContext
        val store = VueoUpdateStore(appContext)
        val now = System.currentTimeMillis()
        val previous = store.latestRelease()
        val lastChecked = store.lastCheckedAt()

        if (
            !force &&
            lastChecked > 0L &&
            now - lastChecked < AUTO_CHECK_INTERVAL_MS
        ) {
            return VueoUpdateCheckResult(
                release = previous,
                checkedAtEpochMs = lastChecked,
                fromCache = true,
                error = store.lastError(),
            )
        }

        return runCatching {
            val raw = SimpleHttp.getResilient(
                DEFAULT_MANIFEST_URL
            )
            val root = JSONObject(raw)
            val schema = root.optInt("schemaVersion", -1)

            require(schema == MANIFEST_SCHEMA_VERSION) {
                "Unsupported update feed schema $schema."
            }

            val release = VueoUpdateRelease.fromJson(root)
                ?: error("Update feed is missing release information.")

            store.saveSuccess(
                release = release,
                checkedAtEpochMs = now,
            )

            VueoUpdateCheckResult(
                release = release,
                checkedAtEpochMs = now,
                fromCache = false,
            )
        }.getOrElse { error ->
            val message = error.message
                ?: "Unable to check for updates."

            store.saveFailure(
                error = message,
                checkedAtEpochMs = now,
            )

            VueoUpdateCheckResult(
                release = previous,
                checkedAtEpochMs = now,
                fromCache = false,
                error = message,
            )
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (index in 0 until length()) {
            optString(index)
                .takeIf { it.isNotBlank() }
                ?.let(::add)
        }
    }
}

private fun JSONObject.optHttpsUrl(
    key: String,
): String? = optString(key)
    .trim()
    .takeIf { it.startsWith("https://") }
