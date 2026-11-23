package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ChatMode(val displayName: String, val systemHint: String) {
    CHAT(
        "General",
        "You are Nexora AI, an intelligent, concise, and helpful assistant. Format responses cleanly with Markdown, tables, lists, and fenced code blocks when helpful."
    ),
    CODE(
        "Coding",
        "You are Nexora AI Coding Assistant. Provide production-grade, well-structured code with clear language tags on fenced code blocks, complexity analysis, and edge-case notes."
    ),
    MATH(
        "Math Solver",
        "You are Nexora AI Mathematics Solver. Show clear step-by-step derivations, clearly label formulas and intermediate steps, and verify the final answer."
    ),
    WRITING(
        "Writing",
        "You are Nexora AI Writing & Grammar Coach. Improve clarity, tone, flow, and grammar while explaining key edits clearly."
    ),
    TRANSLATE(
        "Translate",
        "You are Nexora AI Multilingual Translator. Detect the source language, provide an accurate natural translation, and include brief nuance notes when helpful."
    ),
    FILE(
        "File Analysis",
        "You are Nexora AI Document & Vision Analyst. Thoroughly inspect attached images, PDFs, or text documents and answer with structured insights and extracted details."
    ),
    RESEARCH(
        "Web Research",
        "You are Nexora AI Research Analyst. Synthesize accurate, well-structured findings with clear headings, key takeaways, and source citations."
    );

    companion object {
        fun fromRaw(raw: String?): ChatMode =
            entries.firstOrNull { it.name == raw } ?: CHAT
    }
}

enum class MemoryCategory(val label: String) {
    PREFERENCE("Preference"),
    FACT("Personal Fact"),
    WORK("Work & Goals"),
    STYLE("Response Style"),
    CUSTOM("Custom");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name == raw } ?: PREFERENCE
    }
}

enum class AiProvider(val displayName: String, val badge: String) {
    GOOGLE_GEMINI("Google Gemini", "Firebase AI Logic"),
    OPENAI("OpenAI", "API Key"),
    ANTHROPIC("Anthropic Claude", "API Key"),
    OPENROUTER("OpenRouter", "API Key")
}

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val provider: AiProvider,
    val tagline: String,
    val bestFor: List<String>,
    val supportsVision: Boolean = false,
    val supportsDocuments: Boolean = false,
    val supportsWebSearch: Boolean = false,
    val supportsImageGen: Boolean = false,
    val supportsStreaming: Boolean = true,
    val contextWindowLabel: String = "1M tokens"
)

data class AttachmentPayload(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    val previewUri: String? = null
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
    val isTextLike: Boolean get() = mimeType.startsWith("text/") ||
        mimeType == "application/json" ||
        mimeType == "application/xml" ||
        name.endsWith(".md", ignoreCase = true) ||
        name.endsWith(".kt", ignoreCase = true) ||
        name.endsWith(".py", ignoreCase = true) ||
        name.endsWith(".js", ignoreCase = true) ||
        name.endsWith(".ts", ignoreCase = true) ||
        name.endsWith(".csv", ignoreCase = true)
}

data class QueuedPrompt(
    val id: String,
    val conversationId: String?,
    val prompt: String,
    val modelId: String,
    val mode: String,
    val createdAtMillis: Long
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val bio: String? = null,
    val preferredModel: String = "gemini-3.8-flash",
    val themeMode: String = "DARK",
    val language: String = "English",
    val memoryEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val voiceAutoSpeak: Boolean = false,
    val voiceName: String? = null,
    val voiceRate: Double = 1.0,
    val voicePitch: Double = 1.0,
    val notificationsEnabled: Boolean = true,
    val temperature: Double = 0.7,
    val maxOutputTokens: Long = 4096L,
    val totalPromptTokens: Long = 0L,
    val totalOutputTokens: Long = 0L,
    val totalRequests: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatFolder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#38BDF8",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val modelId: String = "gemini-3.8-flash",
    val assistantId: String? = null,
    val assistantName: String? = null,
    val folderId: String? = null,
    val pinned: Boolean = false,
    val mode: String = "CHAT",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val shareCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val content: String = "",
    val modelId: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val sources: List<String> = emptyList(),
    val promptTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val edited: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomAssistant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val tagline: String = "",
    val personality: String = "",
    val systemInstructions: String = "",
    val modelId: String = "gemini-3.8-flash",
    val iconName: String = "sparkles",
    val starterPrompt: String = "",
    val builtIn: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "PREFERENCE",
    val content: String = "",
    val active: Boolean = true,
    val consentConfirmed: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GeneratedImageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prompt: String = "",
    val style: String = "Photorealistic",
    val aspectRatio: String = "1:1",
    val modelId: String = "gemini-nano-banana-2.1",
    val localFilePath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "SYSTEM",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FeedbackEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "General",
    val subject: String = "",
    val message: String = "",
    val rating: Long = 5L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
