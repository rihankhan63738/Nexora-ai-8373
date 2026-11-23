package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiModelCatalog
import com.example.data.model.AiProvider
import com.example.data.model.ChatMode
import com.example.data.model.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app identity strings match Nexora AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Nexora AI", context.getString(R.string.app_name))
        assertEquals("One AI. Infinite Possibilities.", context.getString(R.string.app_tagline))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    }

    @Test
    fun `model catalog configures Gemini via Firebase AI Logic and checks 3P secrets honestly`() {
        assertTrue(AiModelCatalog.isProviderConfigured(AiProvider.GOOGLE_GEMINI))
        // Default third-party placeholders must not report as connected
        assertFalse(AiModelCatalog.isProviderConfigured(AiProvider.OPENAI))
        assertFalse(AiModelCatalog.isProviderConfigured(AiProvider.ANTHROPIC))
        assertFalse(AiModelCatalog.isProviderConfigured(AiProvider.OPENROUTER))

        val defaultChat = AiModelCatalog.findById(AiModelCatalog.DEFAULT_CHAT_MODEL_ID)
        assertEquals("gemini-3.8-flash", defaultChat.id)
        val defaultImage = AiModelCatalog.findById(AiModelCatalog.DEFAULT_IMAGE_MODEL_ID)
        assertEquals("gemini-nano-banana-2.1", defaultImage.id)
    }

    @Test
    fun `domain enums parse safely without throwing on unknown values`() {
        assertEquals(ChatMode.CHAT, ChatMode.fromRaw("UNKNOWN_MODE"))
        assertEquals(ChatMode.RESEARCH, ChatMode.fromRaw("RESEARCH"))
        assertEquals(MemoryCategory.PREFERENCE, MemoryCategory.fromRaw(null))
        assertEquals(MemoryCategory.WORK, MemoryCategory.fromRaw("WORK"))
    }
}
