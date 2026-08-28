package com.vueo.app.core.plugin

import android.content.Context
import android.util.Base64
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import com.vueo.app.core.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

data class ProviderDiagnostic(
    val repositoryManifestUrl: String,
    val repositoryName: String,
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus,
    val responseMs: Long,
    val streamCount: Int,
    val error: String? = null,
    val logs: List<String> = emptyList(),
)

data class PluginDiscoveryResult(
    val streams: List<StreamSource>,
    val attemptedProviders: Int,
    val successfulProviders: Int,
    val slowProviders: Int,
    val noResultProviders: Int,
    val failedProviders: Int,
    val diagnostics: List<ProviderDiagnostic>,
)

class PluginSourceEngine(
    context: Context,
    private val store: PluginStore,
) {
    private val concurrency = Semaphore(5)

    private val healthStore =
        PluginHealthStore(
            context.applicationContext
        )

    private val codeStore =
        ProviderCodeStore(
            context.applicationContext
        )

    suspend fun discover(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): PluginDiscoveryResult =
        coroutineScope {

            if (!store.pluginsEnabled()) {
                return@coroutineScope PluginDiscoveryResult(
                        streams = emptyList(),
                        attemptedProviders = 0,
                        successfulProviders = 0,
                        slowProviders = 0,
                        noResultProviders = 0,
                        failedProviders = 0,
                        diagnostics = emptyList(),
                    )
            }

            val targets = store.repositories()
                .flatMap { repository ->
                    repository.providers
                        .filter { provider ->
                            store.isProviderEnabled(
                                repository,
                                provider,
                            )
                        }
                        .filter { provider ->
                            provider.supportedTypes
                                .isEmpty() ||
                                mediaType in
                                provider.supportedTypes
                        }
                        .filter { provider ->
                            "android" !in
                                provider.disabledPlatforms
                        }
                        .map { provider ->
                            repository to provider
                        }
                }

            val runs = targets.map {
                (repository, provider) ->

                async {
                    concurrency.withPermit {
                        runProvider(
                            repository =
                                repository,
                            provider =
                                provider,
                            tmdbId =
                                tmdbId,
                            mediaType =
                                mediaType,
                            season =
                                season,
                            episode =
                                episode,
                        )
                    }
                }
            }.awaitAll()

            runs.forEach { run ->
                healthStore.save(
                    ProviderHealthRecord(
                        repositoryManifestUrl =
                            run.diagnostic
                                .repositoryManifestUrl,
                        repositoryName =
                            run.diagnostic
                                .repositoryName,
                        providerId =
                            run.diagnostic
                                .providerId,
                        providerName =
                            run.diagnostic
                                .providerName,
                        status =
                            run.diagnostic
                                .status,
                        responseMs =
                            run.diagnostic
                                .responseMs,
                        streamCount =
                            run.diagnostic
                                .streamCount,
                        error =
                            run.diagnostic
                                .error,
                        logs =
                            run.diagnostic
                                .logs
                                .takeLast(
                                    MAX_STORED_LOGS
                                ),
                        lastCheckedEpochMs =
                            System.currentTimeMillis(),
                    )
                )
            }

            val diagnostics =
                runs.map { it.diagnostic }

            PluginDiscoveryResult(
                streams =
                    runs
                        .flatMap {
                            it.streams
                        }
                        .distinctBy {
                            listOf(
                                it.url,
                                it.providerId,
                                it.name,
                            )
                        },
                attemptedProviders =
                    runs.size,
                successfulProviders =
                    diagnostics.count {
                        it.status ==
                            ProviderHealthStatus
                                .ONLINE
                    },
                slowProviders =
                    diagnostics.count {
                        it.status ==
                            ProviderHealthStatus
                                .SLOW
                    },
                noResultProviders =
                    diagnostics.count {
                        it.status ==
                            ProviderHealthStatus
                                .NO_RESULTS
                    },
                failedProviders =
                    diagnostics.count {
                        it.status ==
                            ProviderHealthStatus
                                .FAILED
                    },
                diagnostics =
                    diagnostics,
            )
        }

    private suspend fun runProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): ProviderRun {
        val started =
            System.nanoTime()

        val execution =
            withTimeoutOrNull(
                PROVIDER_TIMEOUT_MS
            ) {
                runCatching {
                    executeProvider(
                        repository =
                            repository,
                        provider =
                            provider,
                        tmdbId =
                            tmdbId,
                        mediaType =
                            mediaType,
                        season =
                            season,
                        episode =
                            episode,
                    )
                }.getOrElse { error ->
                    ProviderExecution(
                        streams =
                            emptyList(),
                        error =
                            error.message
                                ?: error::class
                                    .java
                                    .simpleName,
                        logs =
                            emptyList(),
                    )
                }
            }
                ?: ProviderExecution(
                    streams =
                        emptyList(),
                    error =
                        "Timed out after " +
                        "${PROVIDER_TIMEOUT_MS / 1000}s",
                    logs =
                        emptyList(),
                )

        val elapsedMs =
            (
                System.nanoTime() -
                started
            ) / 1_000_000L

        val consoleError =
            execution.logs
                .lastOrNull {
                    it.startsWith(
                        "ERROR: "
                    )
                }
                ?.removePrefix(
                    "ERROR: "
                )

        val error =
            execution.error
                ?: consoleError

        val status =
            when {
                execution.streams
                    .isNotEmpty() &&
                    elapsedMs >=
                    SLOW_THRESHOLD_MS ->
                    ProviderHealthStatus
                        .SLOW

                execution.streams
                    .isNotEmpty() ->
                    ProviderHealthStatus
                        .ONLINE

                error != null ->
                    ProviderHealthStatus
                        .FAILED

                else ->
                    ProviderHealthStatus
                        .NO_RESULTS
            }

        return ProviderRun(
            streams =
                execution.streams,
            diagnostic =
                ProviderDiagnostic(
                    repositoryManifestUrl =
                        repository.manifestUrl,
                    repositoryName =
                        repository.name,
                    providerId =
                        provider.id,
                    providerName =
                        provider.name,
                    status =
                        status,
                    responseMs =
                        elapsedMs,
                    streamCount =
                        execution.streams.size,
                    error =
                        error,
                    logs =
                        execution.logs
                            .takeLast(
                                MAX_STORED_LOGS
                            ),
                ),
        )
    }

    private suspend fun executeProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): ProviderExecution {
        val source =
            codeStore.read(
                repository,
                provider,
            )
                ?: return ProviderExecution(
                    streams =
                        emptyList(),
                    error =
                        "Provider code is not installed locally. " +
                        "Open Content Manager > Plugins and refresh this repository.",
                    logs =
                        emptyList(),
                )

        val logs =
            CopyOnWriteArrayList<String>()

        return try {
            val resultJson =
                quickJs {
                    evaluationTimeoutMillis =
                        PROVIDER_TIMEOUT_MS

                    define("console") {
                        function("log") { args ->
                            logs +=
                                "LOG: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }

                        function("info") { args ->
                            logs +=
                                "LOG: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }

                        function("warn") { args ->
                            logs +=
                                "LOG: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }

                        function("error") { args ->
                            logs +=
                                "ERROR: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }
                    }

                    asyncFunction<String, String>(
                        "__vueoNativeFetch"
                    ) { requestJson ->
                        PluginHttp.executeJson(
                            requestJson
                        )
                    }

                    function<String, String>(
                        "__vueoBase64"
                    ) { value ->
                        Base64.encodeToString(
                            value.toByteArray(
                                Charsets.UTF_8
                            ),
                            Base64.NO_WRAP,
                        )
                    }

                    function<String, String>(
                        "__vueoBase64Decode"
                    ) { value ->
                        String(
                            Base64.decode(
                                value,
                                Base64.DEFAULT,
                            ),
                            Charsets.UTF_8,
                        )
                    }

                    asyncFunction<Double, Boolean>(
                        "__vueoDelay"
                    ) { millis ->
                        delay(
                            millis
                                .toLong()
                                .coerceIn(
                                    0L,
                                    30_000L,
                                )
                        )
                        true
                    }

                    evaluate<String>(
                        buildRuntimeScript(
                            providerScript =
                                source,
                            tmdbId =
                                tmdbId,
                            mediaType =
                                mediaType,
                            season =
                                season,
                            episode =
                                episode,
                        ),
                        filename =
                            "${provider.id}.js",
                    )
                }

            ProviderExecution(
                streams =
                    parseProviderStreams(
                        repository =
                            repository,
                        provider =
                            provider,
                        resultJson =
                            resultJson,
                    ),
                error =
                    null,
                logs =
                    logs.toList(),
            )
        } catch (error: Throwable) {
            ProviderExecution(
                streams =
                    emptyList(),
                error =
                    buildString {
                        append(
                            error.message
                                ?: error::class
                                    .java
                                    .simpleName
                        )
                    },
                logs =
                    logs.toList(),
            )
        }
    }

    private fun buildRuntimeScript(
        providerScript: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): String {
        val safeTmdbId =
            JSONObject.quote(
                tmdbId
            )

        val safeMediaType =
            JSONObject.quote(
                mediaType
            )

        val seasonValue =
            season?.toString()
                ?: "null"

        val episodeValue =
            episode?.toString()
                ?: "null"

        return """
            globalThis.window = globalThis;
            globalThis.global = globalThis;
            globalThis.self = globalThis;
            globalThis.process = globalThis.process || { env: {} };
            globalThis.SCRAPER_SETTINGS =
              globalThis.SCRAPER_SETTINGS || {};

            function __vueoString(value) {
              try {
                if (typeof value === "string") return value;
                return JSON.stringify(value);
              } catch (_) {
                return String(value);
              }
            }

            function __vueoHeaders(raw) {
              var normalized = {};
              if (raw && typeof raw === "object") {
                Object.keys(raw).forEach(function (key) {
                  normalized[String(key)] = String(raw[key]);
                });
              }
              return normalized;
            }

            globalThis.fetch = async function (input, init) {
              init = init || {};

              var request = {
                url: String(input && input.url ? input.url : input),
                method: String(init.method || "GET").toUpperCase(),
                headers: __vueoHeaders(init.headers),
                body: init.body == null ? null : String(init.body),
                contentType:
                  init.headers &&
                  (init.headers["Content-Type"] || init.headers["content-type"])
                    ? String(
                        init.headers["Content-Type"] ||
                        init.headers["content-type"]
                      )
                    : null
              };

              var raw = await __vueoNativeFetch(
                JSON.stringify(request)
              );

              var response = JSON.parse(raw);

              if (response.error) {
                throw new Error(response.error);
              }

              var responseHeaders = response.headers || {};
              var bodyText = response.body || "";

              return {
                ok:
                  response.status >= 200 &&
                  response.status < 300,
                status: response.status || 0,
                statusText: response.statusText || "",
                url: response.url || request.url,
                headers: {
                  get: function (name) {
                    if (!name) return null;
                    var lower = String(name).toLowerCase();
                    var keys = Object.keys(responseHeaders);
                    for (var i = 0; i < keys.length; i++) {
                      if (keys[i].toLowerCase() === lower) {
                        return String(responseHeaders[keys[i]]);
                      }
                    }
                    return null;
                  },
                  has: function (name) {
                    return this.get(name) !== null;
                  }
                },
                text: async function () {
                  return bodyText;
                },
                json: async function () {
                  return JSON.parse(bodyText || "null");
                },
                clone: function () {
                  return this;
                }
              };
            };

            function __vueoAxiosRequest(config) {
              config = config || {};

              return fetch(config.url, {
                method: config.method || "GET",
                headers: config.headers || {},
                body:
                  config.data == null
                    ? null
                    : (
                        typeof config.data === "string"
                          ? config.data
                          : JSON.stringify(config.data)
                      )
              }).then(async function (response) {
                var text = await response.text();
                var data = text;

                try {
                  data = JSON.parse(text);
                } catch (_) {}

                if (!response.ok) {
                  var error =
                    new Error(
                      "Request failed with status " +
                      response.status
                    );

                  error.response = {
                    data: data,
                    status: response.status,
                    statusText: response.statusText,
                    headers: response.headers,
                    config: config
                  };

                  throw error;
                }

                return {
                  data: data,
                  status: response.status,
                  statusText: response.statusText,
                  headers: response.headers,
                  config: config
                };
              });
            }

            var axios = function (config) {
              return __vueoAxiosRequest(config);
            };

            axios.request = __vueoAxiosRequest;

            axios.get = function (url, config) {
              config = config || {};
              config.url = url;
              config.method = "GET";
              return __vueoAxiosRequest(config);
            };

            axios.post = function (url, data, config) {
              config = config || {};
              config.url = url;
              config.method = "POST";
              config.data = data;
              return __vueoAxiosRequest(config);
            };

            globalThis.axios = axios;

            globalThis.btoa = function (value) {
              return __vueoBase64(String(value));
            };

            globalThis.atob = function (value) {
              return __vueoBase64Decode(String(value));
            };

            globalThis.Buffer = globalThis.Buffer || {
              from: function (value) {
                var text = String(value);

                return {
                  toString: function (encoding) {
                    if (encoding === "base64") {
                      return __vueoBase64(text);
                    }
                    return text;
                  }
                };
              }
            };

            globalThis.setTimeout = function (callback, millis) {
              return __vueoDelay(Number(millis || 0))
                .then(function () {
                  return callback();
                });
            };

            globalThis.clearTimeout = function () {};

            globalThis.URLSearchParams =
              globalThis.URLSearchParams ||
              function (initial) {
                this._pairs = [];

                if (typeof initial === "string") {
                  var source =
                    initial.charAt(0) === "?"
                      ? initial.slice(1)
                      : initial;

                  if (source) {
                    var parts = source.split("&");
                    for (var i = 0; i < parts.length; i++) {
                      var pair = parts[i].split("=");
                      this.append(
                        decodeURIComponent(pair[0] || ""),
                        decodeURIComponent(pair.slice(1).join("=") || "")
                      );
                    }
                  }
                } else if (initial && typeof initial === "object") {
                  var keys = Object.keys(initial);
                  for (var j = 0; j < keys.length; j++) {
                    this.append(keys[j], initial[keys[j]]);
                  }
                }
              };

            URLSearchParams.prototype.append = function (key, value) {
              this._pairs.push([String(key), String(value)]);
            };

            URLSearchParams.prototype.set = function (key, value) {
              this.delete(key);
              this.append(key, value);
            };

            URLSearchParams.prototype.get = function (key) {
              key = String(key);
              for (var i = 0; i < this._pairs.length; i++) {
                if (this._pairs[i][0] === key) {
                  return this._pairs[i][1];
                }
              }
              return null;
            };

            URLSearchParams.prototype.delete = function (key) {
              key = String(key);
              this._pairs = this._pairs.filter(function (pair) {
                return pair[0] !== key;
              });
            };

            URLSearchParams.prototype.toString = function () {
              return this._pairs.map(function (pair) {
                return (
                  encodeURIComponent(pair[0]) +
                  "=" +
                  encodeURIComponent(pair[1])
                );
              }).join("&");
            };

            globalThis.require = function (name) {
              if (name === "axios") {
                return axios;
              }

              throw new Error(
                "Unsupported runtime require(): " + name
              );
            };

            var module = { exports: {} };
            var exports = module.exports;

            ${providerScript}

            var __vueoGetStreams =
              module &&
              module.exports &&
              typeof module.exports.getStreams === "function"
                ? module.exports.getStreams
                : (
                    typeof globalThis.getStreams === "function"
                      ? globalThis.getStreams
                      : null
                  );

            if (!__vueoGetStreams) {
              throw new Error(
                "Provider does not export getStreams"
              );
            }

            var __vueoStreams =
              await Promise.resolve(
                __vueoGetStreams(
                  ${safeTmdbId},
                  ${safeMediaType},
                  ${seasonValue},
                  ${episodeValue}
                )
              );

            JSON.stringify(
              Array.isArray(__vueoStreams)
                ? __vueoStreams
                : []
            );
        """.trimIndent()
    }

    private data class ProviderExecution(
        val streams: List<StreamSource>,
        val error: String?,
        val logs: List<String>,
    )

    private data class ProviderRun(
        val streams: List<StreamSource>,
        val diagnostic: ProviderDiagnostic,
    )

    companion object {
        private const val PROVIDER_TIMEOUT_MS =
            10_000L

        private const val SLOW_THRESHOLD_MS =
            3_000L

        private const val MAX_STORED_LOGS =
            6

        private const val MAX_LOG_LENGTH =
            500
    }
}

private fun parseProviderStreams(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    resultJson: String,
): List<StreamSource> {
    val array =
        runCatching {
            JSONArray(resultJson)
        }.getOrNull()
            ?: return emptyList()

    return (0 until array.length())
        .mapNotNull { index ->
            val item =
                array.optJSONObject(index)
                    ?: return@mapNotNull null

            val url =
                item.optString("url")
                    .takeIf {
                        it.startsWith(
                            "https://"
                        ) ||
                        it.startsWith(
                            "http://"
                        )
                    }
                    ?: return@mapNotNull null

            val headers =
                item.optJSONObject("headers")
                    .toStringMap()

            val quality =
                item.optString("quality")
                    .takeIf {
                        it.isNotBlank()
                    }

            val displayName =
                item.optString("title")
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: item.optString("name")
                        .takeIf {
                            it.isNotBlank()
                        }
                    ?: provider.name

            StreamSource(
                name =
                    displayName,
                url =
                    url,
                quality =
                    quality,
                headers =
                    headers,
                providerId =
                    "plugin:" +
                    repository.manifestUrl
                        .hashCode() +
                    ":" +
                    provider.id,
                providerName =
                    "${repository.name} / " +
                    provider.name,
            )
        }
}

private fun JSONObject?.toStringMap():
    Map<String, String> {

    if (this == null) {
        return emptyMap()
    }

    val result =
        linkedMapOf<String, String>()

    val iterator =
        keys()

    while (
        iterator.hasNext()
    ) {
        val key =
            iterator.next()

        result[key] =
            optString(key)
    }

    return result
}
