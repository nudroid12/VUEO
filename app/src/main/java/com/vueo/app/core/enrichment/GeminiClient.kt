package com.vueo.app.core.enrichment

import com.vueo.app.core.dna.UserDnaSnapshot
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.plugin.PluginHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional Gemini enhancement for VUEO.
 *
 * Calls happen only after an explicit user action.
 * Raw History, My List and playback records are never sent.
 */
object GeminiClient {
    const val DEFAULT_MODEL =
        "gemini-3.7-flash"

    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1/interactions"

    private val jsonMediaType =
        "application/json; charset=utf-8"
            .toMediaType()

    private val client by lazy {
        PluginHttp.client
            .newBuilder()
            .readTimeout(
                35,
                TimeUnit.SECONDS,
            )
            .callTimeout(
                40,
                TimeUnit.SECONDS,
            )
            .build()
    }

    suspend fun testConnection(
        apiKey: String,
        model: String = DEFAULT_MODEL,
    ): Boolean {
        if (apiKey.isBlank()) {
            return false
        }

        return runCatching {
            interact(
                apiKey = apiKey,
                model = model,
                input = "Reply with exactly VUEO_OK.",
                systemInstruction =
                    "Follow the user's instruction exactly.",
            )
                .trim()
                .contains(
                    "VUEO_OK",
                    ignoreCase = true,
                )
        }.getOrDefault(false)
    }

    suspend fun titleInsight(
        media: MediaItem,
        dna: UserDnaSnapshot?,
        dnaMatchPercent: Int?,
        apiKey: String,
        model: String = DEFAULT_MODEL,
    ): String {
        require(apiKey.isNotBlank()) {
            "Gemini API key is required."
        }

        val dnaContext =
            dna
                ?.takeIf {
                    it.topGenres.isNotEmpty()
                }
                ?.let { snapshot ->
                    buildString {
                        append("Viewer taste context:\n")
                        append("- Top genres: ")
                        append(
                            snapshot.topGenres
                                .take(5)
                                .joinToString(", ") {
                                    affinity ->
                                    "${affinity.name} ${affinity.percent}%"
                                }
                        )
                        append('\n')

                        if (
                            snapshot.tasteTags
                                .isNotEmpty()
                        ) {
                            append("- Taste tags: ")
                            append(
                                snapshot.tasteTags
                                    .take(5)
                                    .joinToString(", ")
                            )
                            append('\n')
                        }

                        append("- DNA confidence: ")
                        append(snapshot.confidencePercent)
                        append("%\n")

                        dnaMatchPercent
                            ?.let { match ->
                                append("- Visible DNA Match: ")
                                append(match)
                                append("%\n")
                            }
                    }
                }
                .orEmpty()

        val mediaContext =
            buildString {
                append("Selected title:\n")
                append("- Name: ${media.name}\n")
                append("- Type: ${media.type}\n")

                media.releaseInfo
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        append("- Release: $it\n")
                    }

                if (
                    media.genres
                        .isNotEmpty()
                ) {
                    append("- Genres: ")
                    append(
                        media.genres
                            .take(8)
                            .joinToString(", ")
                    )
                    append('\n')
                }

                media.runtimeMinutes
                    ?.takeIf {
                        it > 0
                    }
                    ?.let {
                        append("- Runtime: ${it} minutes\n")
                    }

                media.imdbRating
                    ?.takeIf {
                        it.isFinite() &&
                            it > 0.0
                    }
                    ?.let {
                        append("- IMDb rating: $it\n")
                    }

                media.tmdbRating
                    ?.takeIf {
                        it.isFinite() &&
                            it > 0.0
                    }
                    ?.let {
                        append("- TMDB rating: $it\n")
                    }

                media.description
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.take(1400)
                    ?.let {
                        append("- Overview: $it\n")
                    }
            }

        val input =
            buildString {
                append(mediaContext)

                if (
                    dnaContext.isNotBlank()
                ) {
                    append('\n')
                    append(dnaContext)
                }

                append(
                    "\nGive a short, useful and spoiler-free insight for this title."
                )

                if (
                    dnaContext.isNotBlank()
                ) {
                    append(
                        " Explain why it may fit this viewer's taste using only the supplied taste context."
                    )
                } else {
                    append(
                        " Focus on what kind of viewing experience the title appears to offer."
                    )
                }
            }

        return interact(
            apiKey = apiKey,
            model = model,
            input = input,
            systemInstruction =
                "You are VUEO's optional movie and series assistant. " +
                    "Write 2 to 4 concise sentences in clear English. " +
                    "Stay spoiler-free. Use only facts supplied by VUEO. " +
                    "Do not invent plot details, ratings, cast, awards or availability. " +
                    "If viewer taste context is supplied, explain the fit naturally without claiming certainty.",
        )
    }

    private suspend fun interact(
        apiKey: String,
        model: String,
        input: String,
        systemInstruction: String,
    ): String =
        withContext(Dispatchers.IO) {
            val generationConfig =
                JSONObject()
                    .put(
                        "max_output_tokens",
                        256,
                    )
                    .put(
                        "thinking_level",
                        "low",
                    )

            val payload =
                JSONObject()
                    .put("model", model)
                    .put("store", false)
                    .put("input", input)
                    .put(
                        "system_instruction",
                        systemInstruction,
                    )
                    .put(
                        "generation_config",
                        generationConfig,
                    )

            val request =
                Request.Builder()
                    .url(API_URL)
                    .header(
                        "x-goog-api-key",
                        apiKey.trim(),
                    )
                    .header(
                        "Accept",
                        "application/json",
                    )
                    .header(
                        "User-Agent",
                        "VUEO/0.9.6",
                    )
                    .post(
                        payload
                            .toString()
                            .toRequestBody(
                                jsonMediaType
                            )
                    )
                    .build()

            client
                .newCall(request)
                .execute()
                .use { response ->
                    val body =
                        response.body.string()

                    if (
                        !response
                            .isSuccessful
                    ) {
                        val message =
                            runCatching {
                                JSONObject(body)
                                    .optJSONObject("error")
                                    ?.optString("message")
                            }
                                .getOrNull()
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "Request failed"

                        error(
                            "Gemini HTTP ${response.code}: $message"
                        )
                    }

                    extractText(
                        JSONObject(body)
                    )
                }
        }

    private fun extractText(
        json: JSONObject,
    ): String {
        val steps =
            json.optJSONArray("steps")
                ?: error(
                    "Gemini returned no output."
                )

        for (
            stepIndex in
            steps.length() - 1
                downTo 0
        ) {
            val step =
                steps.optJSONObject(
                    stepIndex
                )
                    ?: continue

            if (
                step.optString("type") !=
                "model_output"
            ) {
                continue
            }

            val content =
                step.optJSONArray("content")
                    ?: continue

            val parts =
                mutableListOf<String>()

            for (
                contentIndex in
                0 until content.length()
            ) {
                val block =
                    content.optJSONObject(
                        contentIndex
                    )
                        ?: continue

                if (
                    block.optString("type") !=
                    "text"
                ) {
                    continue
                }

                block.optString("text")
                    .trim()
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.let(
                        parts::add
                    )
            }

            if (
                parts.isNotEmpty()
            ) {
                return parts
                    .joinToString("\n")
                    .trim()
            }
        }

        error(
            "Gemini returned no text output."
        )
    }
}
