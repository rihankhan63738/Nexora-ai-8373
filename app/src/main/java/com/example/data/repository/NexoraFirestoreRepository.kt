package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.ChatFolder
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.FeedbackEntry
import com.example.data.model.GeneratedImageRecord
import com.example.data.model.MemoryRecord
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NexoraFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> Flow<T>.handleObserveErrors(uid: String, path: String, op: OperationType): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    // --- User Profile & Bootstrap ---

    suspend fun ensureUserProfileAndDefaults(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = userDoc(uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", (user.displayName?.takeIf { it.isNotBlank() } ?: "Nexora User").take(100))
                    put("email", (user.email ?: "").take(200))
                    user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(1000)) }
                    put("preferredModel", "gemini-3.8-flash")
                    put("themeMode", "DARK")
                    put("language", "English")
                    put("memoryEnabled", true)
                    put("voiceEnabled", true)
                    put("voiceAutoSpeak", false)
                    put("voiceRate", 1.0)
                    put("voicePitch", 1.0)
                    put("notificationsEnabled", true)
                    put("temperature", 0.7)
                    put("maxOutputTokens", 4096L)
                    put("totalPromptTokens", 0L)
                    put("totalOutputTokens", 0L)
                    put("totalRequests", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
                seedStarterAssistants(uid)
                createNotificationInternal(
                    uid = uid,
                    title = "Welcome to Nexora AI",
                    body = "Your private multi-model workspace is connected to Gemini via Firebase AI Logic with end-to-end user isolation.",
                    category = "SYSTEM"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterAssistants(uid: String) {
        val starters = listOf(
            StarterAssistantSeed(
                id = "starter_study",
                name = "Study Assistant",
                tagline = "Active recall, clear explanations & exam prep",
                personality = "Patient, structured, encouraging tutor",
                instructions = "Break complex topics into intuitive steps, use analogies, highlight key formulas, and end with 2 quick check-your-understanding questions.",
                modelId = "gemini-3.8-flash",
                iconName = "school",
                starterPrompt = "Explain quantum entanglement intuitively, then quiz me with 2 questions."
            ),
            StarterAssistantSeed(
                id = "starter_coding",
                name = "Coding Assistant",
                tagline = "Architecture, debugging & clean production code",
                personality = "Pragmatic senior staff engineer",
                instructions = "Write clean, idiomatic, bug-free code with fenced code blocks, explain architectural trade-offs, and include time/space complexity notes.",
                modelId = "gemini-3.8-flash",
                iconName = "code",
                starterPrompt = "Help me design a thread-safe LRU cache in Kotlin with unit tests."
            ),
            StarterAssistantSeed(
                id = "starter_writing",
                name = "Writing Assistant",
                tagline = "Editorial polish, grammar & persuasive clarity",
                personality = "Sharp, warm editorial director",
                instructions = "Refine drafts for clarity, rhythm, and precision while preserving the author's voice. Provide a polished version plus a concise summary of key improvements.",
                modelId = "gemini-3.8-flash",
                iconName = "edit",
                starterPrompt = "Polish this product launch announcement to sound confident and concise."
            ),
            StarterAssistantSeed(
                id = "starter_research",
                name = "Research Assistant",
                tagline = "Deep synthesis, structured briefs & citations",
                personality = "Analytical, objective research fellow",
                instructions = "Conduct structured analysis: executive summary, core evidence, competing viewpoints, open questions, and actionable conclusions.",
                modelId = "gemini-3.8-flash",
                iconName = "science",
                starterPrompt = "Compare solid-state batteries and lithium-iron-phosphate batteries for EVs."
            )
        )
        val col = userDoc(uid).collection("assistants")
        for (seed in starters) {
            val payload = mapOf(
                "userId" to uid,
                "name" to seed.name,
                "tagline" to seed.tagline,
                "personality" to seed.personality,
                "systemInstructions" to seed.instructions,
                "modelId" to seed.modelId,
                "iconName" to seed.iconName,
                "starterPrompt" to seed.starterPrompt,
                "builtIn" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            col.document(seed.id).set(payload).await()
        }
    }

    private data class StarterAssistantSeed(
        val id: String,
        val name: String,
        val tagline: String,
        val personality: String,
        val instructions: String,
        val modelId: String,
        val iconName: String,
        val starterPrompt: String
    )

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            userDoc(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleObserveErrors(uid, path, OperationType.GET)
        )
    }

    suspend fun updateUserProfile(fields: Map<String, Any?>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val sanitized = fields.filterValues { it != null }.toMutableMap()
            sanitized["updatedAt"] = FieldValue.serverTimestamp()
            userDoc(uid).update(sanitized).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordTokenUsage(promptTokens: Long, outputTokens: Long): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            userDoc(uid).update(
                mapOf(
                    "totalPromptTokens" to FieldValue.increment(promptTokens.coerceAtLeast(0L)),
                    "totalOutputTokens" to FieldValue.increment(outputTokens.coerceAtLeast(0L)),
                    "totalRequests" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Folders ---

    fun observeFolders(uid: String): Flow<List<ChatFolder>> = flow {
        val path = "users/$uid/folders"
        emitAll(
            userDoc(uid).collection("folders")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatFolder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun createFolder(name: String, colorHex: String): Result<String> {
        val trimmed = name.trim().take(60)
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Folder name cannot be empty"))
        val uid = requireUserId()
        val folderId = "fld_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/folders/$folderId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "name" to trimmed,
                "colorHex" to colorHex.take(16),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("folders").document(folderId).set(payload).await()
            Result.success(folderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/folders/$folderId"
        return try {
            userDoc(uid).collection("folders").document(folderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Conversations ---

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "users/$uid/conversations"
        emitAll(
            userDoc(uid).collection("conversations")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<Conversation> { it.pinned }
                                .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun createConversation(
        title: String,
        modelId: String,
        mode: String = "CHAT",
        assistantId: String? = null,
        assistantName: String? = null,
        folderId: String? = null,
        lastMessagePreview: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val convId = "conv_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/conversations/$convId"
        return try {
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("title", title.trim().ifBlank { "New Chat" }.take(160))
                put("modelId", modelId.take(100))
                assistantId?.takeIf { it.isNotBlank() }?.let { put("assistantId", it.take(128)) }
                assistantName?.takeIf { it.isNotBlank() }?.let { put("assistantName", it.take(100)) }
                folderId?.takeIf { it.isNotBlank() }?.let { put("folderId", it.take(128)) }
                put("pinned", false)
                put("mode", mode)
                put("lastMessagePreview", lastMessagePreview.take(300))
                put("messageCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userDoc(uid).collection("conversations").document(convId).set(payload).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationMetadata(
        conversationId: String,
        title: String? = null,
        pinned: Boolean? = null,
        folderId: String? = null,
        clearFolder: Boolean = false,
        modelId: String? = null,
        mode: String? = null,
        shareCode: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/conversations/$conversationId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["title"] = it.take(160) }
            pinned?.let { updates["pinned"] = it }
            if (clearFolder) {
                updates["folderId"] = FieldValue.delete()
            } else if (!folderId.isNullOrBlank()) {
                updates["folderId"] = folderId.take(128)
            }
            modelId?.takeIf { it.isNotBlank() }?.let { updates["modelId"] = it.take(100) }
            mode?.takeIf { it.isNotBlank() }?.let { updates["mode"] = it }
            shareCode?.takeIf { it.isNotBlank() }?.let { updates["shareCode"] = it.take(64) }

            userDoc(uid).collection("conversations").document(conversationId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationPreview(
        conversationId: String,
        preview: String,
        incrementCountBy: Long = 1L,
        newTitle: String? = null,
        modelId: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/conversations/$conversationId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "lastMessagePreview" to preview.replace("\n", " ").trim().take(290),
                "messageCount" to FieldValue.increment(incrementCountBy),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            newTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["title"] = it.take(160) }
            modelId?.takeIf { it.isNotBlank() }?.let { updates["modelId"] = it.take(100) }
            userDoc(uid).collection("conversations").document(conversationId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/conversations/$conversationId"
        return try {
            val convRef = userDoc(uid).collection("conversations").document(conversationId)
            val msgs = convRef.collection("messages").get().await()
            for (doc in msgs.documents) {
                doc.reference.delete().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Messages ---

    fun observeMessages(uid: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            userDoc(uid).collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        modelId: String? = null,
        attachmentName: String? = null,
        attachmentMime: String? = null,
        sources: List<String> = emptyList(),
        promptTokens: Long = 0L,
        outputTokens: Long = 0L
    ): Result<String> {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/conversations/$conversationId/messages/$msgId"
        return try {
            val cleanContent = content.ifBlank { " " }.take(39500)
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("conversationId", conversationId)
                put("role", role)
                put("content", cleanContent)
                modelId?.takeIf { it.isNotBlank() }?.let { put("modelId", it.take(100)) }
                attachmentName?.takeIf { it.isNotBlank() }?.let { put("attachmentName", it.take(200)) }
                attachmentMime?.takeIf { it.isNotBlank() }?.let { put("attachmentMime", it.take(120)) }
                if (sources.isNotEmpty()) put("sources", sources.take(20))
                put("promptTokens", promptTokens.coerceAtLeast(0L))
                put("outputTokens", outputTokens.coerceAtLeast(0L))
                put("edited", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userDoc(uid).collection("conversations").document(conversationId)
                .collection("messages").document(msgId)
                .set(payload)
                .await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMessageContent(
        conversationId: String,
        messageId: String,
        newContent: String,
        edited: Boolean = true,
        sources: List<String>? = null,
        promptTokens: Long? = null,
        outputTokens: Long? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/conversations/$conversationId/messages/$messageId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "content" to newContent.ifBlank { " " }.take(39500),
                "edited" to edited,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (sources != null) updates["sources"] = sources.take(20)
            if (promptTokens != null) updates["promptTokens"] = promptTokens.coerceAtLeast(0L)
            if (outputTokens != null) updates["outputTokens"] = outputTokens.coerceAtLeast(0L)
            userDoc(uid).collection("conversations").document(conversationId)
                .collection("messages").document(messageId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessagesAfter(conversationId: String, keepIds: Set<String>): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/conversations/$conversationId/messages"
        return try {
            val col = userDoc(uid).collection("conversations").document(conversationId).collection("messages")
            val docs = col.get().await().documents
            for (doc in docs) {
                if (doc.id !in keepIds) {
                    doc.reference.delete().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Custom Assistants ---

    fun observeAssistants(uid: String): Flow<List<CustomAssistant>> = flow {
        val path = "users/$uid/assistants"
        emitAll(
            userDoc(uid).collection("assistants")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CustomAssistant::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<CustomAssistant> { !it.builtIn }
                                .thenBy { it.name.lowercase() }
                        )
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun saveAssistant(
        existingId: String?,
        name: String,
        tagline: String,
        personality: String,
        systemInstructions: String,
        modelId: String,
        iconName: String,
        starterPrompt: String
    ): Result<String> {
        val uid = requireUserId()
        val trimmedName = name.trim().take(80)
        val trimmedInstructions = systemInstructions.trim().take(4000)
        val trimmedPersonality = personality.trim().ifBlank { "Helpful & precise" }.take(240)
        if (trimmedName.isEmpty() || trimmedInstructions.isEmpty()) {
            return Result.failure(IllegalArgumentException("Name and custom instructions are required."))
        }
        val docId = existingId?.takeIf { it.isNotBlank() }
            ?: "ast_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/assistants/$docId"
        return try {
            val docRef = userDoc(uid).collection("assistants").document(docId)
            if (existingId.isNullOrBlank()) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to trimmedName,
                    "tagline" to tagline.trim().take(160),
                    "personality" to trimmedPersonality,
                    "systemInstructions" to trimmedInstructions,
                    "modelId" to modelId.take(100),
                    "iconName" to iconName.take(60),
                    "starterPrompt" to starterPrompt.trim().take(400),
                    "builtIn" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "name" to trimmedName,
                    "tagline" to tagline.trim().take(160),
                    "personality" to trimmedPersonality,
                    "systemInstructions" to trimmedInstructions,
                    "modelId" to modelId.take(100),
                    "iconName" to iconName.take(60),
                    "starterPrompt" to starterPrompt.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingId == null) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAssistant(assistantId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/assistants/$assistantId"
        return try {
            userDoc(uid).collection("assistants").document(assistantId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Memory System ---

    fun observeMemories(uid: String): Flow<List<MemoryRecord>> = flow {
        val path = "users/$uid/memories"
        emitAll(
            userDoc(uid).collection("memories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(MemoryRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun saveMemory(
        existingId: String?,
        category: String,
        content: String,
        active: Boolean = true,
        consentConfirmed: Boolean = true
    ): Result<String> {
        val uid = requireUserId()
        val trimmed = content.trim().take(600)
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Memory content cannot be empty."))
        if (!consentConfirmed) return Result.failure(IllegalArgumentException("Please confirm consent before saving personal memory."))
        val memId = existingId?.takeIf { it.isNotBlank() }
            ?: "mem_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/memories/$memId"
        return try {
            val docRef = userDoc(uid).collection("memories").document(memId)
            if (existingId.isNullOrBlank()) {
                val payload = mapOf(
                    "userId" to uid,
                    "category" to category,
                    "content" to trimmed,
                    "active" to active,
                    "consentConfirmed" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "category" to category,
                    "content" to trimmed,
                    "active" to active,
                    "consentConfirmed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(memId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingId == null) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMemoryActive(memoryId: String, active: Boolean): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/memories/$memoryId"
        return try {
            userDoc(uid).collection("memories").document(memoryId).update(
                mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/memories/$memoryId"
        return try {
            userDoc(uid).collection("memories").document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Generated Images History ---

    fun observeGeneratedImages(uid: String): Flow<List<GeneratedImageRecord>> = flow {
        val path = "users/$uid/generated_images"
        emitAll(
            userDoc(uid).collection("generated_images")
                .snapshots()
                .map { snap ->
                    snap.toObjects(GeneratedImageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun saveGeneratedImageRecord(
        prompt: String,
        style: String,
        aspectRatio: String,
        modelId: String,
        localFilePath: String
    ): Result<String> {
        val uid = requireUserId()
        val imgId = "img_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/generated_images/$imgId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "prompt" to prompt.trim().take(1200),
                "style" to style.take(60),
                "aspectRatio" to aspectRatio.take(20),
                "modelId" to modelId.take(100),
                "localFilePath" to localFilePath.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("generated_images").document(imgId).set(payload).await()
            Result.success(imgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGeneratedImageRecord(imageId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/generated_images/$imageId"
        return try {
            userDoc(uid).collection("generated_images").document(imageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Notifications ---

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        val path = "users/$uid/notifications"
        emitAll(
            userDoc(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleObserveErrors(uid, path, OperationType.LIST)
        )
    }

    suspend fun createNotification(title: String, body: String, category: String = "SYSTEM"): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            createNotificationInternal(uid, title, body, category)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/notifications")
            Result.failure(e)
        }
    }

    private suspend fun createNotificationInternal(
        uid: String,
        title: String,
        body: String,
        category: String
    ) {
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120),
            "body" to body.trim().take(500),
            "category" to category,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userDoc(uid).collection("notifications").document(notifId).set(payload).await()
    }

    suspend fun markNotificationRead(notificationId: String, read: Boolean = true): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/notifications/$notificationId"
        return try {
            userDoc(uid).collection("notifications").document(notificationId).update(
                mapOf("read" to read, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/notifications/$notificationId"
        return try {
            userDoc(uid).collection("notifications").document(notificationId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Feedback ---

    suspend fun submitFeedback(
        category: String,
        subject: String,
        message: String,
        rating: Long
    ): Result<Unit> {
        val uid = requireUserId()
        val fbId = "fb_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$uid/feedback/$fbId"
        return try {
            val trimmedSubject = subject.trim().take(140)
            val trimmedMsg = message.trim().take(2000)
            if (trimmedSubject.isEmpty() || trimmedMsg.isEmpty()) {
                return Result.failure(IllegalArgumentException("Subject and message are required."))
            }
            val payload = mapOf(
                "userId" to uid,
                "category" to category.take(60),
                "subject" to trimmedSubject,
                "message" to trimmedMsg,
                "rating" to rating.coerceIn(1L, 5L),
                "createdAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("feedback").document(fbId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Privacy & Complete Data Deletion ---

    suspend fun deleteAllUserData(): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val root = userDoc(uid)
            val conversations = root.collection("conversations").get().await().documents
            for (conv in conversations) {
                val msgs = conv.reference.collection("messages").get().await().documents
                for (m in msgs) m.reference.delete().await()
                conv.reference.delete().await()
            }
            val subCollections = listOf("folders", "assistants", "memories", "generated_images", "notifications", "feedback")
            for (sub in subCollections) {
                val docs = root.collection(sub).get().await().documents
                for (d in docs) d.reference.delete().await()
            }
            root.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
