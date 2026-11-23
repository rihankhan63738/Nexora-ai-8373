package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.model.AiProvider
import com.example.data.model.AttachmentPayload
import com.example.data.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val TAG = "MultiModelAiEngine"
private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Stay safely under 20MB inline limit

data class StreamChunkResult(
    val deltaText: String = "",
    val fullText: String = "",
    val sources: List<String> = emptyList(),
    val promptTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val isComplete: Boolean = false
)

data class AiRequestConfig(
    val modelId: String,
    val systemPrompt: String,
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096,
    val enableWebSearch: Boolean = false
)

class MultiModelAiEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun streamChat(
        config: AiRequestConfig,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: AttachmentPayload? = null
    ): Flow<StreamChunkResult> {
        val spec = AiModelCatalog.findById(config.modelId)
        return when (spec.provider) {
            AiProvider.GOOGLE_GEMINI -> streamGemini(config, history, userPrompt, attachment)
            AiProvider.OPENAI -> streamOpenAiCompatible(
                endpoint = "https://api.openai.com/v1/chat/completions",
                apiKey = AiModelCatalog.apiKeyFor(AiProvider.OPENAI),
                providerName = "OpenAI",
                extraHeaders = emptyMap(),
                config = config,
                history = history,
                userPrompt = userPrompt,
                attachment = attachment
            )
            AiProvider.OPENROUTER -> streamOpenAiCompatible(
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = AiModelCatalog.apiKeyFor(AiProvider.OPENROUTER),
                providerName = "OpenRouter",
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://nexora.ai",
                    "X-Title" to "Nexora AI"
                ),
                config = config,
                history = history,
                userPrompt = userPrompt,
                attachment = attachment
            )
            AiProvider.ANTHROPIC -> streamAnthropic(
                apiKey = AiModelCatalog.apiKeyFor(AiProvider.ANTHROPIC),
                config = config,
                history = history,
                userPrompt = userPrompt,
                attachment = attachment
            )
        }
    }

    private fun streamGemini(
        config: AiRequestConfig,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: AttachmentPayload?
    ): Flow<StreamChunkResult> = flow {
        val genConfig = generationConfig {
            temperature = config.temperature.toFloat().coerceIn(0f, 2f)
            maxOutputTokens = config.maxOutputTokens.coerceIn(256, 16384)
        }

        val toolsList = if (config.enableWebSearch) {
            listOf(Tool.googleSearch())
        } else {
            null
        }

        // Initialize lazily at inference time so FirebaseApp & App Check are guaranteed ready
        val model = Firebase.ai.generativeModel(
            modelName = config.modelId,
            generationConfig = genConfig,
            systemInstruction = content { text(config.systemPrompt) },
            tools = toolsList
        )

        val geminiHistory = buildGeminiHistory(history)
        val inputContent = buildGeminiUserContent(userPrompt, attachment)

        val accumulated = StringBuilder()
        val collectedSources = LinkedHashSet<String>()
        var lastPromptTokens = 0L
        var lastOutputTokens = 0L

        try {
            withTimeout(60_000L) {
                val stream = if (geminiHistory.isNotEmpty()) {
                    val chat = model.startChat(history = geminiHistory)
                    chat.sendMessageStream(inputContent)
                } else {
                    model.generateContentStream(inputContent)
                }

                stream.collect { chunk ->
                    currentCoroutineContext().ensureActive()
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                    }

                    chunk.usageMetadata?.let { usage ->
                        lastPromptTokens = usage.promptTokenCount?.toLong() ?: lastPromptTokens
                        lastOutputTokens = usage.candidatesTokenCount?.toLong() ?: lastOutputTokens
                    }

                    chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                        val web = gChunk.web
                        if (web != null) {
                            val title = web.title?.takeIf { it.isNotBlank() } ?: web.domain ?: "Web Source"
                            val uri = web.uri?.takeIf { it.isNotBlank() }
                            if (uri != null) {
                                collectedSources.add("$title — $uri")
                            }
                        }
                    }

                    if (delta.isNotEmpty() || collectedSources.isNotEmpty()) {
                        emit(
                            StreamChunkResult(
                                deltaText = delta,
                                fullText = accumulated.toString(),
                                sources = collectedSources.toList(),
                                promptTokens = lastPromptTokens,
                                outputTokens = lastOutputTokens,
                                isComplete = false
                            )
                        )
                    }
                }
            }

            if (accumulated.isBlank()) {
                throw IllegalStateException("The model returned an empty response. Please try rephrasing your prompt.")
            }

            emit(
                StreamChunkResult(
                    deltaText = "",
                    fullText = accumulated.toString(),
                    sources = collectedSources.toList(),
                    promptTokens = lastPromptTokens,
                    outputTokens = lastOutputTokens,
                    isComplete = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            throw IllegalStateException("Generation timed out after 60 seconds. Please try again.", e)
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Gemini quota exceeded", e)
            throw IllegalStateException("Rate limit or quota reached for ${config.modelId}. Try switching to Gemini 3.5 Flash-Lite or retrying shortly.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini streaming failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private fun buildGeminiHistory(history: List<ChatMessage>): List<Content> {
        val result = mutableListOf<Content>()
        // Keep last 20 messages for strong context without blowing limits
        for (msg in history.takeLast(20)) {
            if (msg.content.isBlank()) continue
            val role = when (msg.role) {
                "assistant" -> "model"
                "user" -> "user"
                else -> continue
            }
            result.add(content(role) { text(msg.content) })
        }
        // Gemini requires history to start with user and alternate cleanly
        while (result.isNotEmpty() && result.first().role != "user") {
            result.removeAt(0)
        }
        return result
    }

    private fun buildGeminiUserContent(prompt: String, attachment: AttachmentPayload?): Content {
        return content("user") {
            if (attachment != null) {
                if (attachment.bytes.size > MAX_INLINE_BYTES) {
                    throw IllegalArgumentException("Attachment exceeds 18MB inline size limit. Please select a smaller file.")
                }
                when {
                    attachment.isImage -> {
                        val bitmap = BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size)
                        if (bitmap != null) {
                            image(bitmap)
                        } else {
                            inlineData(attachment.bytes, attachment.mimeType)
                        }
                    }
                    attachment.isPdf -> {
                        inlineData(attachment.bytes, "application/pdf")
                    }
                    attachment.isTextLike -> {
                        val decoded = attachment.bytes.toString(Charsets.UTF_8).take(28_000)
                        text("Attached file `${attachment.name}`:\n```\n$decoded\n```")
                    }
                    else -> {
                        inlineData(attachment.bytes, attachment.mimeType.ifBlank { "application/octet-stream" })
                    }
                }
            }
            text(prompt)
        }
    }

    private fun streamOpenAiCompatible(
        endpoint: String,
        apiKey: String?,
        providerName: String,
        extraHeaders: Map<String, String>,
        config: AiRequestConfig,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: AttachmentPayload?
    ): Flow<StreamChunkResult> = flow {
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException(
                "$providerName API key is not configured. Add ${providerName.uppercase()}_API_KEY in the AI Studio Secrets panel or switch to a connected Google Gemini model."
            )
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", config.systemPrompt)
        })
        history.takeLast(16).forEach { msg ->
            if (msg.role == "user" || msg.role == "assistant") {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        val finalPrompt = if (attachment != null && attachment.isTextLike) {
            val snippet = attachment.bytes.toString(Charsets.UTF_8).take(20_000)
            "Attached file `${attachment.name}`:\n```\n$snippet\n```\n\n$userPrompt"
        } else {
            userPrompt
        }

        if (attachment != null && attachment.isImage) {
            val b64 = Base64.encodeToString(attachment.bytes, Base64.NO_WRAP)
            val contentParts = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", finalPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:${attachment.mimeType};base64,$b64")
                    })
                })
            }
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", contentParts)
            })
        } else {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", finalPrompt)
            })
        }

        val bodyJson = JSONObject().apply {
            put("model", config.modelId)
            put("messages", messagesArray)
            put("stream", true)
            if (!config.modelId.startsWith("o3")) {
                put("temperature", config.temperature.coerceIn(0.0, 2.0))
                put("max_tokens", config.maxOutputTokens.coerceIn(256, 8192))
            }
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val accumulated = StringBuilder()
        var promptTokens = 0L
        var outputTokens = 0L

        withTimeout(60_000L) {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty().take(300)
                    when (response.code) {
                        401 -> throw IllegalStateException("$providerName rejected the API key (HTTP 401). Please verify your secret in AI Studio.")
                        429 -> throw IllegalStateException("$providerName rate limit exceeded (HTTP 429). Please retry shortly or switch models.")
                        else -> throw IllegalStateException("$providerName request failed (HTTP ${response.code}): $errBody")
                    }
                }
                val stream = response.body?.byteStream()
                    ?: throw IllegalStateException("$providerName returned an empty response stream.")
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        currentCoroutineContext().ensureActive()
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val obj = try { JSONObject(data) } catch (_: Exception) { continue }
                        obj.optJSONObject("usage")?.let { usage ->
                            promptTokens = usage.optLong("prompt_tokens", promptTokens)
                            outputTokens = usage.optLong("completion_tokens", outputTokens)
                        }
                        val delta = obj.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content")
                            .orEmpty()
                        if (delta.isNotEmpty()) {
                            accumulated.append(delta)
                            emit(
                                StreamChunkResult(
                                    deltaText = delta,
                                    fullText = accumulated.toString(),
                                    promptTokens = promptTokens,
                                    outputTokens = outputTokens,
                                    isComplete = false
                                )
                            )
                        }
                    }
                }
            }
        }

        if (accumulated.isBlank()) {
            throw IllegalStateException("$providerName returned an empty response.")
        }

        emit(
            StreamChunkResult(
                deltaText = "",
                fullText = accumulated.toString(),
                promptTokens = promptTokens,
                outputTokens = outputTokens,
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun streamAnthropic(
        apiKey: String?,
        config: AiRequestConfig,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: AttachmentPayload?
    ): Flow<StreamChunkResult> = flow {
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException(
                "Anthropic API key is not configured. Add ANTHROPIC_API_KEY in the AI Studio Secrets panel or switch to a connected Google Gemini model."
            )
        }

        val messagesArray = JSONArray()
        history.takeLast(16).forEach { msg ->
            if (msg.role == "user" || msg.role == "assistant") {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }
        val finalPrompt = if (attachment != null && attachment.isTextLike) {
            "Attached file `${attachment.name}`:\n```\n${attachment.bytes.toString(Charsets.UTF_8).take(20_000)}\n```\n\n$userPrompt"
        } else {
            userPrompt
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", finalPrompt)
        })

        val bodyJson = JSONObject().apply {
            put("model", config.modelId)
            put("max_tokens", config.maxOutputTokens.coerceIn(256, 8192))
            put("temperature", config.temperature.coerceIn(0.0, 1.0))
            put("system", config.systemPrompt)
            put("messages", messagesArray)
            put("stream", true)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val accumulated = StringBuilder()
        var promptTokens = 0L
        var outputTokens = 0L

        withTimeout(60_000L) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty().take(300)
                    when (response.code) {
                        401 -> throw IllegalStateException("Anthropic rejected the API key (HTTP 401).")
                        429 -> throw IllegalStateException("Anthropic rate limit exceeded (HTTP 429).")
                        else -> throw IllegalStateException("Anthropic request failed (HTTP ${response.code}): $err")
                    }
                }
                val stream = response.body?.byteStream()
                    ?: throw IllegalStateException("Anthropic returned an empty stream.")
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        currentCoroutineContext().ensureActive()
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        val obj = try { JSONObject(data) } catch (_: Exception) { continue }
                        when (obj.optString("type")) {
                            "message_start" -> {
                                obj.optJSONObject("message")?.optJSONObject("usage")?.let { u ->
                                    promptTokens = u.optLong("input_tokens", promptTokens)
                                }
                            }
                            "content_block_delta" -> {
                                val delta = obj.optJSONObject("delta")?.optString("text").orEmpty()
                                if (delta.isNotEmpty()) {
                                    accumulated.append(delta)
                                    emit(
                                        StreamChunkResult(
                                            deltaText = delta,
                                            fullText = accumulated.toString(),
                                            promptTokens = promptTokens,
                                            outputTokens = outputTokens,
                                            isComplete = false
                                        )
                                    )
                                }
                            }
                            "message_delta" -> {
                                obj.optJSONObject("usage")?.let { u ->
                                    outputTokens = u.optLong("output_tokens", outputTokens)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (accumulated.isBlank()) {
            throw IllegalStateException("Anthropic returned an empty response.")
        }

        emit(
            StreamChunkResult(
                deltaText = "",
                fullText = accumulated.toString(),
                promptTokens = promptTokens,
                outputTokens = outputTokens,
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    // --- Runtime AI Image Generation via Firebase AI Logic ---

    suspend fun generateImage(
        prompt: String,
        style: String,
        aspectRatioLabel: String,
        modelId: String = AiModelCatalog.DEFAULT_IMAGE_MODEL_ID
    ): Bitmap = withContext(Dispatchers.IO) {
        val ratio = when (aspectRatioLabel) {
            "16:9" -> AspectRatio.LANDSCAPE_16x9
            "9:16" -> AspectRatio.PORTRAIT_9x16
            "4:3" -> AspectRatio.LANDSCAPE_4x3
            "3:4" -> AspectRatio.PORTRAIT_3x4
            else -> AspectRatio.SQUARE_1x1
        }
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(ratio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }

        val styledPrompt = if (style.equals("Natural / None", ignoreCase = true)) {
            prompt.trim()
        } else {
            "${prompt.trim()}. Visual style: $style. High detail, cohesive composition."
        }

        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            generationConfig = config
        )

        try {
            withTimeout(60_000L) {
                val response = model.generateContent(styledPrompt)
                val imagePart = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                imagePart?.image
                    ?: throw IllegalStateException("No image was returned by $modelId. Try adjusting your prompt.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds. Please try again.", e)
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image generation quota exceeded", e)
            throw IllegalStateException("Image generation quota reached or requires Firebase Blaze plan.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw e
        }
    }

    // --- Context-Aware Follow-Up Suggestions ---

    suspend fun generateFollowUpSuggestions(
        lastUserPrompt: String,
        lastAssistantReply: String
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.5-flash-lite",
                generationConfig = generationConfig {
                    temperature = 0.5f
                    maxOutputTokens = 256
                }
            )
            val prompt = """
                Based on this conversation exchange, propose exactly 3 brief, insightful follow-up questions the user might ask next (each under 10 words).
                Return ONLY the 3 questions, one per line, without numbering or bullets.
                
                User: ${lastUserPrompt.take(400)}
                Assistant: ${lastAssistantReply.take(700)}
            """.trimIndent()
            withTimeout(15_000L) {
                val res = model.generateContent(prompt)
                res.text.orEmpty()
                    .lines()
                    .map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '.', ' ') }
                    .filter { it.length in 5..90 }
                    .take(3)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
