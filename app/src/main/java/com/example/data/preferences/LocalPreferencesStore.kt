package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.QueuedPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.nexoraDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexora_prefs")

class LocalPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val QUEUED_PROMPTS = stringPreferencesKey("queued_prompts_json")
    }

    val themeMode: Flow<String> = context.nexoraDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "DARK"
    }

    val onboardingCompleted: Flow<Boolean> = context.nexoraDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val queuedPrompts: Flow<List<QueuedPrompt>> = context.nexoraDataStore.data.map { prefs ->
        val raw = prefs[Keys.QUEUED_PROMPTS] ?: "[]"
        parseQueue(raw)
    }

    suspend fun setThemeMode(mode: String) {
        context.nexoraDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.nexoraDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun enqueuePrompt(item: QueuedPrompt) {
        context.nexoraDataStore.edit { prefs ->
            val current = parseQueue(prefs[Keys.QUEUED_PROMPTS] ?: "[]").toMutableList()
            current.add(item)
            prefs[Keys.QUEUED_PROMPTS] = serializeQueue(current)
        }
    }

    suspend fun removeQueuedPrompt(id: String) {
        context.nexoraDataStore.edit { prefs ->
            val updated = parseQueue(prefs[Keys.QUEUED_PROMPTS] ?: "[]").filterNot { it.id == id }
            prefs[Keys.QUEUED_PROMPTS] = serializeQueue(updated)
        }
    }

    suspend fun clearQueuedPrompts() {
        context.nexoraDataStore.edit { it[Keys.QUEUED_PROMPTS] = "[]" }
    }

    private fun parseQueue(json: String): List<QueuedPrompt> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                QueuedPrompt(
                    id = obj.optString("id"),
                    conversationId = obj.optString("conversationId").takeIf { it.isNotBlank() },
                    prompt = obj.optString("prompt"),
                    modelId = obj.optString("modelId", "gemini-3.8-flash"),
                    mode = obj.optString("mode", "CHAT"),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeQueue(items: List<QueuedPrompt>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("conversationId", item.conversationId ?: "")
                    put("prompt", item.prompt)
                    put("modelId", item.modelId)
                    put("mode", item.mode)
                    put("createdAtMillis", item.createdAtMillis)
                }
            )
        }
        return arr.toString()
    }
}
