package com.example.data.ai

import com.example.BuildConfig
import com.example.data.model.AiModelSpec
import com.example.data.model.AiProvider

object AiModelCatalog {

    const val DEFAULT_CHAT_MODEL_ID = "gemini-3.8-flash"
    const val DEFAULT_IMAGE_MODEL_ID = "gemini-nano-banana-2.1"

    val chatModels: List<AiModelSpec> = listOf(
        AiModelSpec(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            provider = AiProvider.GOOGLE_GEMINI,
            tagline = "Versatile frontier model for everyday reasoning, vision, coding & web research",
            bestFor = listOf("General Chat", "Vision & PDF", "Web Research", "Coding"),
            supportsVision = true,
            supportsDocuments = true,
            supportsWebSearch = true,
            supportsStreaming = true,
            contextWindowLabel = "1M tokens"
        ),
        AiModelSpec(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            provider = AiProvider.GOOGLE_GEMINI,
            tagline = "Ultra-fast low-latency model for quick answers, translation & grammar",
            bestFor = listOf("Fast Replies", "Translation", "Summarization", "Grammar"),
            supportsVision = true,
            supportsDocuments = true,
            supportsWebSearch = true,
            supportsStreaming = true,
            contextWindowLabel = "1M tokens"
        ),
        AiModelSpec(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            provider = AiProvider.GOOGLE_GEMINI,
            tagline = "Deep reasoning powerhouse for advanced math, STEM & complex software architecture",
            bestFor = listOf("Deep Reasoning", "Advanced Math", "Architecture", "Research"),
            supportsVision = true,
            supportsDocuments = true,
            supportsWebSearch = true,
            supportsStreaming = true,
            contextWindowLabel = "1M tokens"
        ),
        AiModelSpec(
            id = "gpt-4o",
            displayName = "GPT-4o",
            provider = AiProvider.OPENAI,
            tagline = "OpenAI flagship multimodal chat & coding model (requires OPENAI_API_KEY)",
            bestFor = listOf("General Chat", "Coding", "Writing"),
            supportsVision = true,
            supportsStreaming = true,
            contextWindowLabel = "128K tokens"
        ),
        AiModelSpec(
            id = "o3-mini",
            displayName = "OpenAI o3-mini",
            provider = AiProvider.OPENAI,
            tagline = "STEM and algorithmic reasoning model from OpenAI (requires OPENAI_API_KEY)",
            bestFor = listOf("Math", "Logic", "Coding"),
            supportsStreaming = true,
            contextWindowLabel = "200K tokens"
        ),
        AiModelSpec(
            id = "claude-sonnet-4-20250514",
            displayName = "Claude Sonnet 4",
            provider = AiProvider.ANTHROPIC,
            tagline = "Anthropic balanced intelligence for nuanced writing & code (requires ANTHROPIC_API_KEY)",
            bestFor = listOf("Writing", "Code Review", "Analysis"),
            supportsStreaming = true,
            contextWindowLabel = "200K tokens"
        ),
        AiModelSpec(
            id = "claude-3-5-haiku-latest",
            displayName = "Claude 3.5 Haiku",
            provider = AiProvider.ANTHROPIC,
            tagline = "Fast Anthropic model for rapid iteration (requires ANTHROPIC_API_KEY)",
            bestFor = listOf("Fast Edits", "Summaries"),
            supportsStreaming = true,
            contextWindowLabel = "200K tokens"
        ),
        AiModelSpec(
            id = "deepseek/deepseek-r1",
            displayName = "DeepSeek R1 (OpenRouter)",
            provider = AiProvider.OPENROUTER,
            tagline = "Open-weights reasoning model via OpenRouter (requires OPENROUTER_API_KEY)",
            bestFor = listOf("Reasoning", "Math", "Research"),
            supportsStreaming = true,
            contextWindowLabel = "128K tokens"
        ),
        AiModelSpec(
            id = "meta-llama/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B (OpenRouter)",
            provider = AiProvider.OPENROUTER,
            tagline = "Open multilingual instruct model via OpenRouter (requires OPENROUTER_API_KEY)",
            bestFor = listOf("Multilingual", "General"),
            supportsStreaming = true,
            contextWindowLabel = "128K tokens"
        )
    )

    val imageModels: List<AiModelSpec> = listOf(
        AiModelSpec(
            id = "gemini-nano-banana-2.1",
            displayName = "Gemini Nano Banana 2.1",
            provider = AiProvider.GOOGLE_GEMINI,
            tagline = "Fast high-efficiency text-to-image synthesis via Firebase AI Logic",
            bestFor = listOf("Fast Image Gen", "Illustrations", "Concepts"),
            supportsImageGen = true,
            supportsStreaming = false,
            contextWindowLabel = "1K / 2K"
        ),
        AiModelSpec(
            id = "gemini-3-pro-image",
            displayName = "Gemini 3 Pro Image",
            provider = AiProvider.GOOGLE_GEMINI,
            tagline = "Studio-grade contextual image generation & crisp typography",
            bestFor = listOf("Studio Assets", "Photorealism", "Editorial Art"),
            supportsImageGen = true,
            supportsStreaming = false,
            contextWindowLabel = "1K / 2K"
        )
    )

    val allModels: List<AiModelSpec> = chatModels + imageModels

    fun findById(id: String): AiModelSpec =
        allModels.firstOrNull { it.id == id } ?: chatModels.first()

    fun isProviderConfigured(provider: AiProvider): Boolean = when (provider) {
        AiProvider.GOOGLE_GEMINI -> true
        AiProvider.OPENAI -> isRealSecret(BuildConfig.OPENAI_API_KEY)
        AiProvider.ANTHROPIC -> isRealSecret(BuildConfig.ANTHROPIC_API_KEY)
        AiProvider.OPENROUTER -> isRealSecret(BuildConfig.OPENROUTER_API_KEY)
    }

    fun apiKeyFor(provider: AiProvider): String? = when (provider) {
        AiProvider.GOOGLE_GEMINI -> null
        AiProvider.OPENAI -> BuildConfig.OPENAI_API_KEY.takeIf { isRealSecret(it) }
        AiProvider.ANTHROPIC -> BuildConfig.ANTHROPIC_API_KEY.takeIf { isRealSecret(it) }
        AiProvider.OPENROUTER -> BuildConfig.OPENROUTER_API_KEY.takeIf { isRealSecret(it) }
    }

    fun providerStatusLabel(provider: AiProvider): String = when (provider) {
        AiProvider.GOOGLE_GEMINI -> "Connected · Firebase AI Logic"
        AiProvider.OPENAI -> if (isProviderConfigured(provider)) "Connected · OpenAI API" else "Not configured · Set OPENAI_API_KEY"
        AiProvider.ANTHROPIC -> if (isProviderConfigured(provider)) "Connected · Anthropic API" else "Not configured · Set ANTHROPIC_API_KEY"
        AiProvider.OPENROUTER -> if (isProviderConfigured(provider)) "Connected · OpenRouter API" else "Not configured · Set OPENROUTER_API_KEY"
    }

    private fun isRealSecret(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        return !trimmed.startsWith("YOUR_") && !trimmed.startsWith("MY_") && trimmed.length >= 10
    }
}
