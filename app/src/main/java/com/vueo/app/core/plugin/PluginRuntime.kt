package com.vueo.app.core.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.stremio.SimpleHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.coroutines.resume

data class PluginDiscoveryResult(
    val streams: List<StreamSource>,
    val attemptedProviders: Int,
    val successfulProviders: Int,
    val failedProviders: Int,
)

class PluginSourceEngine(
    private val context: Context,
    private val store: PluginStore,
) {
    private val concurrency = Semaphore(3)

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
                        failedProviders = 0,
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
                            provider.supportedTypes.isEmpty() ||
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

            val results = targets.map {
                (repository, provider) ->

                async {
                    concurrency.withPermit {
                        val streams =
                            withTimeoutOrNull(
                                PROVIDER_TIMEOUT_MS
                            ) {
                                runCatching {
                                    executeProvider(
                                        repository =
                                            repository,
                                        provider =
                                            provider,
                                        tmdbId = tmdbId,
                                        mediaType =
                                            mediaType,
                                        season = season,
                                        episode = episode,
                                    )
                                }.getOrDefault(
                                    emptyList()
                                )
                            } ?: emptyList()

                        ProviderRun(
                            streams = streams,
                            succeeded =
                                streams.isNotEmpty(),
                        )
                    }
                }
            }.awaitAll()

            PluginDiscoveryResult(
                streams = results
                    .flatMap { it.streams }
                    .distinctBy {
                        listOf(
                            it.url,
                            it.providerId,
                            it.name,
                        )
                    },
                attemptedProviders =
                    targets.size,
                successfulProviders =
                    results.count { it.succeeded },
                failedProviders =
                    results.count {
                        !it.succeeded
                    },
            )
        }

    private suspend fun executeProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): List<StreamSource> {
        val scriptUrl =
            PluginRepositoryClient
                .providerScriptUrl(
                    repository,
                    provider,
                )

        require(
            scriptUrl.startsWith("https://")
        ) {
            "Only HTTPS provider scripts are allowed."
        }

        val script = SimpleHttp.get(scriptUrl)

        return executeInWebView(
            repository = repository,
            provider = provider,
            script = script,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun executeInWebView(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        script: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): List<StreamSource> =
        withContext(Dispatchers.Main.immediate) {

            suspendCancellableCoroutine {
                continuation ->

                val webView = WebView(context)

                webView.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false

                    // Remote providers may only reach
                    // the network through VueoBridge.fetch().
                    blockNetworkLoads = true
                }

                var completed = false

                fun finish(
                    streams: List<StreamSource>,
                ) {
                    if (completed) {
                        return
                    }

                    completed = true

                    webView.post {
                        runCatching {
                            webView.removeJavascriptInterface(
                                "VueoBridge"
                            )
                            webView.stopLoading()
                            webView.destroy()
                        }

                        if (continuation.isActive) {
                            continuation.resume(streams)
                        }
                    }
                }

                val bridge = ProviderBridge(
                    repository = repository,
                    provider = provider,
                    onComplete = ::finish,
                    onFailure = {
                        finish(emptyList())
                    },
                )

                webView.addJavascriptInterface(
                    bridge,
                    "VueoBridge",
                )

                webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                            super.onPageFinished(
                                view,
                                url,
                            )

                            val javascript =
                                buildRuntimeScript(
                                    providerScript =
                                        script,
                                    tmdbId = tmdbId,
                                    mediaType =
                                        mediaType,
                                    season = season,
                                    episode = episode,
                                )

                            view.evaluateJavascript(
                                javascript,
                                null,
                            )
                        }
                    }

                continuation.invokeOnCancellation {
                    webView.post {
                        runCatching {
                            webView.destroy()
                        }
                    }
                }

                webView.loadDataWithBaseURL(
                    "https://vueo.local/",
                    "<html><head></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null,
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
            JSONObject.quote(tmdbId)

        val safeMediaType =
            JSONObject.quote(mediaType)

        val seasonValue =
            season?.toString() ?: "null"

        val episodeValue =
            episode?.toString() ?: "null"

        return """
            (function () {
              "use strict";

              globalThis.SCRAPER_SETTINGS =
                globalThis.SCRAPER_SETTINGS || {};

              var module = { exports: {} };
              var exports = module.exports;

              function vueoHeaders(raw) {
                var normalized = {};
                if (raw && typeof raw === "object") {
                  Object.keys(raw).forEach(function (key) {
                    normalized[String(key)] =
                      String(raw[key]);
                  });
                }
                return normalized;
              }

              globalThis.fetch = function (
                input,
                init
              ) {
                init = init || {};

                return new Promise(
                  function (resolve, reject) {
                    try {
                      var request = {
                        url: String(input),
                        method:
                          String(
                            init.method || "GET"
                          ).toUpperCase(),
                        headers:
                          vueoHeaders(init.headers),
                        body:
                          init.body == null
                            ? null
                            : String(init.body)
                      };

                      var raw =
                        VueoBridge.fetch(
                          JSON.stringify(request)
                        );

                      var response =
                        JSON.parse(raw);

                      if (response.error) {
                        reject(
                          new Error(response.error)
                        );
                        return;
                      }

                      var responseHeaders =
                        response.headers || {};

                      var result = {
                        ok:
                          response.status >= 200 &&
                          response.status < 300,
                        status:
                          response.status || 0,
                        statusText:
                          response.statusText || "",
                        url:
                          response.url ||
                          request.url,
                        headers: {
                          get: function (name) {
                            if (!name) return null;
                            var lower =
                              String(name)
                                .toLowerCase();

                            var keys =
                              Object.keys(
                                responseHeaders
                              );

                            for (
                              var i = 0;
                              i < keys.length;
                              i++
                            ) {
                              if (
                                keys[i]
                                  .toLowerCase() ===
                                lower
                              ) {
                                return String(
                                  responseHeaders[
                                    keys[i]
                                  ]
                                );
                              }
                            }
                            return null;
                          }
                        },
                        text: function () {
                          return Promise.resolve(
                            response.body || ""
                          );
                        },
                        json: function () {
                          return Promise.resolve(
                            JSON.parse(
                              response.body || "null"
                            )
                          );
                        }
                      };

                      resolve(result);
                    } catch (error) {
                      reject(error);
                    }
                  }
                );
              };

              globalThis.require =
                globalThis.require ||
                function (name) {
                  throw new Error(
                    "Unsupported runtime require(): " +
                    name
                  );
                };

              try {
                ${providerScript}

                var getStreams =
                  module &&
                  module.exports &&
                  typeof module.exports.getStreams
                    === "function"
                    ? module.exports.getStreams
                    : (
                        typeof globalThis.getStreams
                          === "function"
                          ? globalThis.getStreams
                          : null
                      );

                if (!getStreams) {
                  throw new Error(
                    "Provider does not export getStreams"
                  );
                }

                Promise.resolve(
                  getStreams(
                    ${safeTmdbId},
                    ${safeMediaType},
                    ${seasonValue},
                    ${episodeValue}
                  )
                )
                  .then(function (streams) {
                    VueoBridge.complete(
                      JSON.stringify(
                        Array.isArray(streams)
                          ? streams
                          : []
                      )
                    );
                  })
                  .catch(function (error) {
                    VueoBridge.fail(
                      String(
                        error &&
                        error.message
                          ? error.message
                          : error
                      )
                    );
                  });
              } catch (error) {
                VueoBridge.fail(
                  String(
                    error &&
                    error.message
                      ? error.message
                      : error
                  )
                );
              }
            })();
        """.trimIndent()
    }

    private data class ProviderRun(
        val streams: List<StreamSource>,
        val succeeded: Boolean,
    )

    private class ProviderBridge(
        private val repository:
            PluginRepositoryDescriptor,
        private val provider:
            PluginProviderDescriptor,
        private val onComplete:
            (List<StreamSource>) -> Unit,
        private val onFailure:
            (String) -> Unit,
    ) {
        @JavascriptInterface
        fun fetch(requestJson: String): String =
            NativeHttpsFetch.execute(
                requestJson
            )

        @JavascriptInterface
        fun complete(resultJson: String) {
            val streams =
                parseProviderStreams(
                    repository =
                        repository,
                    provider =
                        provider,
                    resultJson =
                        resultJson,
                )

            onComplete(streams)
        }

        @JavascriptInterface
        fun fail(message: String) {
            onFailure(message)
        }
    }

    companion object {
        private const val PROVIDER_TIMEOUT_MS =
            15_000L
    }
}

private object NativeHttpsFetch {
    fun execute(requestJson: String): String {
        return runCatching {
            val request =
                JSONObject(requestJson)

            val urlString =
                request.optString("url")

            validatePublicHttpsUrl(
                urlString
            )

            val connection =
                URL(urlString)
                    .openConnection()
                    as HttpURLConnection

            try {
                val method =
                    request.optString(
                        "method",
                        "GET",
                    ).uppercase()

                connection.requestMethod =
                    method

                connection.connectTimeout =
                    8_000

                connection.readTimeout =
                    12_000

                connection.instanceFollowRedirects =
                    true

                val headers =
                    request.optJSONObject(
                        "headers"
                    )

                if (headers != null) {
                    val iterator =
                        headers.keys()

                    while (
                        iterator.hasNext()
                    ) {
                        val key =
                            iterator.next()

                        if (
                            key.lowercase() in
                            BLOCKED_REQUEST_HEADERS
                        ) {
                            continue
                        }

                        connection.setRequestProperty(
                            key,
                            headers.optString(key),
                        )
                    }
                }

                val body =
                    request.optString(
                        "body",
                        "",
                    )

                if (
                    body.isNotEmpty() &&
                    method !in
                    setOf("GET", "HEAD")
                ) {
                    connection.doOutput = true

                    connection.outputStream
                        .bufferedWriter(
                            Charsets.UTF_8
                        )
                        .use {
                            it.write(body)
                        }
                }

                val status =
                    connection.responseCode

                val stream =
                    if (status in 200..399) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val responseBody =
                    stream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                val responseHeaders =
                    JSONObject()

                connection.headerFields
                    .filterKeys {
                        it != null
                    }
                    .forEach {
                        (key, values) ->

                        responseHeaders.put(
                            key,
                            values.joinToString(", "),
                        )
                    }

                JSONObject()
                    .put(
                        "status",
                        status,
                    )
                    .put(
                        "statusText",
                        connection.responseMessage
                            .orEmpty(),
                    )
                    .put(
                        "url",
                        connection.url.toString(),
                    )
                    .put(
                        "body",
                        responseBody,
                    )
                    .put(
                        "headers",
                        responseHeaders,
                    )
                    .toString()
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            JSONObject()
                .put(
                    "error",
                    error.message
                        ?: "Native fetch failed",
                )
                .toString()
        }
    }

    private fun validatePublicHttpsUrl(
        rawUrl: String,
    ) {
        require(
            rawUrl.startsWith(
                "https://"
            )
        ) {
            "Plugin fetch only allows HTTPS."
        }

        val url = URL(rawUrl)
        val host = url.host

        require(host.isNotBlank()) {
            "Invalid plugin fetch host."
        }

        val addresses =
            InetAddress.getAllByName(host)

        require(
            addresses.none {
                address ->
                isPrivateAddress(address)
            }
        ) {
            "Plugin fetch cannot access local/private networks."
        }
    }

    private fun isPrivateAddress(
        address: InetAddress,
    ): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress
        ) {
            return true
        }

        val bytes = address.address

        if (bytes.size == 4) {
            val first =
                bytes[0].toInt() and 0xFF
            val second =
                bytes[1].toInt() and 0xFF

            // Carrier-grade NAT.
            if (
                first == 100 &&
                second in 64..127
            ) {
                return true
            }
        }

        if (bytes.size == 16) {
            val first =
                bytes[0].toInt() and 0xFF

            // IPv6 unique-local fc00::/7.
            if (
                first == 0xFC ||
                first == 0xFD
            ) {
                return true
            }
        }

        return false
    }

    private val BLOCKED_REQUEST_HEADERS =
        setOf(
            "host",
            "content-length",
            "connection",
            "accept-encoding",
        )
}

private fun parseProviderStreams(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    resultJson: String,
): List<StreamSource> {
    val array = runCatching {
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
                name = displayName,
                url = url,
                quality = quality,
                headers = headers,
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

    val iterator = keys()

    while (iterator.hasNext()) {
        val key = iterator.next()
        result[key] =
            optString(key)
    }

    return result
}
