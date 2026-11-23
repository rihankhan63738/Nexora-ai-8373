package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AiModelCatalog
import com.example.data.ai.AiRequestConfig
import com.example.data.ai.MultiModelAiEngine
import com.example.data.model.AppNotification
import com.example.data.model.AttachmentPayload
import com.example.data.model.ChatFolder
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMode
import com.example.data.model.Conversation
import com.example.data.model.CustomAssistant
import com.example.data.model.GeneratedImageRecord
import com.example.data.model.MemoryRecord
import com.example.data.model.QueuedPrompt
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.network.ConnectivityMonitor
import com.example.data.preferences.LocalPreferencesStore
import com.example.data.repository.NexoraFirestoreRepository
import com.example.util.ExportAndShareUtils
import com.example.util.VoiceEngine
import com.example.util.VoiceUiState
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val VM_TAG = "NexoraViewModel"

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val localPath: String, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

enum class NexoraRoute(val label: String) {
    CHAT("Chat"),
    HISTORY("History"),
    MODELS("Models"),
    IMAGE_STUDIO("Image Studio"),
    FILE_ANALYSIS("File Analysis"),
    WEB_RESEARCH("Web Research"),
    ASSISTANTS("Assistants"),
    MEMORY("Memory"),
    PROFILE("Profile"),
    SETTINGS("Settings"),
    NOTIFICATIONS("Notifications"),
    HELP("Help & Feedback")
}

@OptIn(ExperimentalCoroutinesApi::class)
class NexoraViewModel(
    private val repository: NexoraFirestoreRepository,
    private val aiEngine: MultiModelAiEngine,
    private val prefsStore: LocalPreferencesStore,
    private val connectivityMonitor: ConnectivityMonitor,
    val voiceEngine: VoiceEngine,
    private val currentUserId: String,
    private val firebaseUser: FirebaseUser
) : ViewModel() {

    private fun <T> Flow<T>.toUiState(errorFallback: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(VM_TAG, errorFallback, root)
                emit(UiState.Error(root.localizedMessage ?: errorFallback))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiState("Failed to load profile")

    val foldersState: StateFlow<UiState<List<ChatFolder>>> =
        repository.observeFolders(currentUserId).toUiState("Failed to load folders")

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId).toUiState("Failed to load conversations")

    val assistantsState: StateFlow<UiState<List<CustomAssistant>>> =
        repository.observeAssistants(currentUserId).toUiState("Failed to load assistants")

    val memoriesState: StateFlow<UiState<List<MemoryRecord>>> =
        repository.observeMemories(currentUserId).toUiState("Failed to load memory records")

    val generatedImagesState: StateFlow<UiState<List<GeneratedImageRecord>>> =
        repository.observeGeneratedImages(currentUserId).toUiState("Failed to load generated images")

    val notificationsState: StateFlow<UiState<List<AppNotification>>> =
        repository.observeNotifications(currentUserId).toUiState("Failed to load notifications")

    val isOnline: StateFlow<Boolean> = connectivityMonitor.observeOnlineStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), connectivityMonitor.isCurrentlyOnline())

    val queuedPrompts: StateFlow<List<QueuedPrompt>> = prefsStore.queuedPrompts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val voiceState: StateFlow<VoiceUiState> = voiceEngine.state

    private val _currentRoute = MutableStateFlow(NexoraRoute.CHAT)
    val currentRoute: StateFlow<NexoraRoute> = _currentRoute.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).localizedMessage ?: "Failed to load messages"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    private val _selectedModelId = MutableStateFlow(AiModelCatalog.DEFAULT_CHAT_MODEL_ID)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _activeMode = MutableStateFlow(ChatMode.CHAT)
    val activeMode: StateFlow<ChatMode> = _activeMode.asStateFlow()

    private val _activeAssistantId = MutableStateFlow<String?>(null)
    val activeAssistantId: StateFlow<String?> = _activeAssistantId.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _streamingSources = MutableStateFlow<List<String>>(emptyList())
    val streamingSources: StateFlow<List<String>> = _streamingSources.asStateFlow()

    private val _followUpSuggestions = MutableStateFlow<List<String>>(emptyList())
    val followUpSuggestions: StateFlow<List<String>> = _followUpSuggestions.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<AttachmentPayload?>(null)
    val pendingAttachment: StateFlow<AttachmentPayload?> = _pendingAttachment.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _researchReport = MutableStateFlow("")
    val researchReport: StateFlow<String> = _researchReport.asStateFlow()

    private val _researchSources = MutableStateFlow<List<String>>(emptyList())
    val researchSources: StateFlow<List<String>> = _researchSources.asStateFlow()

    private val _isResearching = MutableStateFlow(false)
    val isResearching: StateFlow<Boolean> = _isResearching.asStateFlow()

    private val _researchError = MutableStateFlow<String?>(null)
    val researchError: StateFlow<String?> = _researchError.asStateFlow()

    private var activeChatJob: Job? = null
    private var activeResearchJob: Job? = null
    private var hasSyncedDefaultModelFromProfile = false

    init {
        viewModelScope.launch {
            repository.ensureUserProfileAndDefaults(firebaseUser)
        }
        viewModelScope.launch {
            userProfileState.collect { state ->
                if (state is UiState.Success && state.data != null) {
                    val profile = state.data
                    prefsStore.setThemeMode(profile.themeMode)
                    voiceEngine.applyVoiceSettings(
                        profile.voiceName,
                        profile.voiceRate.toFloat(),
                        profile.voicePitch.toFloat()
                    )
                    if (!hasSyncedDefaultModelFromProfile) {
                        hasSyncedDefaultModelFromProfile = true
                        _selectedModelId.value = profile.preferredModel
                    }
                }
            }
        }
        // Automatically flush queued prompts when connectivity returns
        viewModelScope.launch {
            combine(isOnline, queuedPrompts) { online, queue -> online to queue }
                .distinctUntilChanged()
                .collect { (online, queue) ->
                    if (online && queue.isNotEmpty() && !_isGenerating.value) {
                        flushNextQueuedPrompt(queue.first())
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeChatJob?.cancel()
        activeResearchJob?.cancel()
    }

    fun navigateTo(route: NexoraRoute) {
        _currentRoute.value = route
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
    }

    fun selectMode(mode: ChatMode) {
        _activeMode.value = mode
        if (mode == ChatMode.RESEARCH) {
            _webSearchEnabled.value = true
        }
    }

    fun toggleWebSearch(enabled: Boolean) {
        _webSearchEnabled.value = enabled
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun attachUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            ExportAndShareUtils.readAttachmentFromUri(context, uri)
                .onSuccess { payload ->
                    _pendingAttachment.value = payload
                    _snackbarMessage.value = "Attached ${payload.name}"
                }
                .onFailure { err ->
                    _chatError.value = err.localizedMessage ?: "Failed to read attachment"
                }
        }
    }

    fun startNewConversation(
        mode: ChatMode = ChatMode.CHAT,
        assistant: CustomAssistant? = null,
        starterPrompt: String? = null
    ) {
        stopGeneration()
        _activeConversationId.value = null
        _activeMode.value = mode
        _activeAssistantId.value = assistant?.id
        if (assistant != null) {
            _selectedModelId.value = assistant.modelId
        }
        _webSearchEnabled.value = mode == ChatMode.RESEARCH
        _followUpSuggestions.value = emptyList()
        _chatError.value = null
        _currentRoute.value = NexoraRoute.CHAT
        if (!starterPrompt.isNullOrBlank()) {
            sendChatMessage(starterPrompt)
        }
    }

    fun openConversation(conversation: Conversation) {
        stopGeneration()
        _activeConversationId.value = conversation.id
        _selectedModelId.value = conversation.modelId
        _activeMode.value = ChatMode.fromRaw(conversation.mode)
        _activeAssistantId.value = conversation.assistantId
        _followUpSuggestions.value = emptyList()
        _chatError.value = null
        _currentRoute.value = NexoraRoute.CHAT
    }

    fun stopGeneration() {
        activeChatJob?.cancel()
        activeChatJob = null
        if (_isGenerating.value && _streamingText.value.isNotBlank()) {
            val convId = _activeConversationId.value
            val partial = _streamingText.value
            val modelId = _selectedModelId.value
            if (!convId.isNullOrBlank()) {
                viewModelScope.launch {
                    repository.addMessage(
                        conversationId = convId,
                        role = "assistant",
                        content = "$partial\n\n_(Generation stopped)_",
                        modelId = modelId
                    )
                }
            }
        }
        _isGenerating.value = false
        _streamingText.value = ""
        _streamingSources.value = emptyList()
    }

    fun sendChatMessage(
        rawPrompt: String,
        overrideMode: ChatMode? = null,
        forceWebSearch: Boolean? = null
    ) {
        val prompt = rawPrompt.trim()
        val attachment = _pendingAttachment.value
        if (prompt.isEmpty() && attachment == null) return
        val effectivePrompt = prompt.ifEmpty { "Please analyze the attached file `${attachment?.name}`." }
        val mode = overrideMode ?: _activeMode.value
        val modelId = _selectedModelId.value

        if (!isOnline.value) {
            viewModelScope.launch {
                val queued = QueuedPrompt(
                    id = UUID.randomUUID().toString(),
                    conversationId = _activeConversationId.value,
                    prompt = effectivePrompt,
                    modelId = modelId,
                    mode = mode.name,
                    createdAtMillis = System.currentTimeMillis()
                )
                prefsStore.enqueuePrompt(queued)
                _pendingAttachment.value = null
                _snackbarMessage.value = "Offline: Prompt queued and will send automatically when connected."
            }
            return
        }

        val spec = AiModelCatalog.findById(modelId)
        if (!AiModelCatalog.isProviderConfigured(spec.provider)) {
            _chatError.value = "${spec.provider.displayName} is not configured. Add its API key in the AI Studio Secrets panel or switch to a connected Google Gemini model."
            return
        }

        _pendingAttachment.value = null
        _chatError.value = null
        _followUpSuggestions.value = emptyList()

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val assistants = (assistantsState.value as? UiState.Success)?.data.orEmpty()
            val assistant = assistants.firstOrNull { it.id == _activeAssistantId.value }
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
            val existingMessages = (messagesState.value as? UiState.Success)?.data.orEmpty()

            var convId = _activeConversationId.value
            if (convId.isNullOrBlank()) {
                val autoTitle = effectivePrompt.replace("\n", " ").take(48)
                val created = repository.createConversation(
                    title = autoTitle,
                    modelId = modelId,
                    mode = mode.name,
                    assistantId = assistant?.id,
                    assistantName = assistant?.name,
                    lastMessagePreview = effectivePrompt
                )
                convId = created.getOrElse { err ->
                    _chatError.value = err.localizedMessage ?: "Could not create conversation."
                    return@launch
                }
                _activeConversationId.value = convId
            }

            repository.addMessage(
                conversationId = convId,
                role = "user",
                content = effectivePrompt,
                attachmentName = attachment?.name,
                attachmentMime = attachment?.mimeType
            ).onFailure { err ->
                _chatError.value = err.localizedMessage ?: "Failed to save message."
                return@launch
            }

            val autoTitleIfFirst = if (existingMessages.isEmpty()) effectivePrompt.take(48) else null
            repository.updateConversationPreview(
                conversationId = convId,
                preview = effectivePrompt,
                incrementCountBy = 1L,
                newTitle = autoTitleIfFirst,
                modelId = modelId
            )

            runAssistantInference(
                conversationId = convId,
                history = existingMessages,
                userPrompt = effectivePrompt,
                attachment = attachment,
                mode = mode,
                modelId = modelId,
                profile = profile,
                assistant = assistant,
                memories = memories,
                useWebSearch = forceWebSearch ?: (_webSearchEnabled.value || mode == ChatMode.RESEARCH)
            )
        }
    }

    private suspend fun flushNextQueuedPrompt(item: QueuedPrompt) {
        prefsStore.removeQueuedPrompt(item.id)
        if (!item.conversationId.isNullOrBlank()) {
            _activeConversationId.value = item.conversationId
        }
        _selectedModelId.value = item.modelId
        _activeMode.value = ChatMode.fromRaw(item.mode)
        _snackbarMessage.value = "Back online — sending queued prompt…"
        sendChatMessage(item.prompt, overrideMode = ChatMode.fromRaw(item.mode))
    }

    fun removeQueuedPrompt(id: String) {
        viewModelScope.launch {
            prefsStore.removeQueuedPrompt(id)
        }
    }

    fun regenerateLastResponse() {
        val convId = _activeConversationId.value ?: return
        val messages = (messagesState.value as? UiState.Success)?.data ?: return
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return
        val lastUserMsg = messages[lastUserIdx]
        val keepIds = messages.take(lastUserIdx + 1).map { it.id }.toSet()

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            repository.deleteMessagesAfter(convId, keepIds)
            val profile = (userProfileState.value as? UiState.Success)?.data
            val assistants = (assistantsState.value as? UiState.Success)?.data.orEmpty()
            val assistant = assistants.firstOrNull { it.id == _activeAssistantId.value }
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()

            runAssistantInference(
                conversationId = convId,
                history = messages.take(lastUserIdx),
                userPrompt = lastUserMsg.content,
                attachment = null,
                mode = _activeMode.value,
                modelId = _selectedModelId.value,
                profile = profile,
                assistant = assistant,
                memories = memories,
                useWebSearch = _webSearchEnabled.value || _activeMode.value == ChatMode.RESEARCH
            )
        }
    }

    fun editAndResendMessage(message: ChatMessage, updatedPrompt: String) {
        val convId = _activeConversationId.value ?: return
        val trimmed = updatedPrompt.trim()
        if (trimmed.isEmpty()) return
        val messages = (messagesState.value as? UiState.Success)?.data ?: return
        val idx = messages.indexOfFirst { it.id == message.id }
        if (idx < 0) return
        val keepIds = messages.take(idx + 1).map { it.id }.toSet()

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            repository.updateMessageContent(convId, message.id, trimmed, edited = true)
            repository.deleteMessagesAfter(convId, keepIds)

            val profile = (userProfileState.value as? UiState.Success)?.data
            val assistants = (assistantsState.value as? UiState.Success)?.data.orEmpty()
            val assistant = assistants.firstOrNull { it.id == _activeAssistantId.value }
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()

            runAssistantInference(
                conversationId = convId,
                history = messages.take(idx),
                userPrompt = trimmed,
                attachment = null,
                mode = _activeMode.value,
                modelId = _selectedModelId.value,
                profile = profile,
                assistant = assistant,
                memories = memories,
                useWebSearch = _webSearchEnabled.value || _activeMode.value == ChatMode.RESEARCH
            )
        }
    }

    private suspend fun runAssistantInference(
        conversationId: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: AttachmentPayload?,
        mode: ChatMode,
        modelId: String,
        profile: UserProfile?,
        assistant: CustomAssistant?,
        memories: List<MemoryRecord>,
        useWebSearch: Boolean
    ) {
        _isGenerating.value = true
        _streamingText.value = ""
        _streamingSources.value = emptyList()

        val systemPrompt = buildSystemPrompt(mode, profile, assistant, memories)
        val config = AiRequestConfig(
            modelId = modelId,
            systemPrompt = systemPrompt,
            temperature = profile?.temperature ?: 0.7,
            maxOutputTokens = (profile?.maxOutputTokens ?: 4096L).toInt(),
            enableWebSearch = useWebSearch
        )

        var finalReply = ""
        var finalSources = emptyList<String>()
        var promptTokens = 0L
        var outputTokens = 0L

        try {
            aiEngine.streamChat(config, history, userPrompt, attachment).collect { chunk ->
                _streamingText.value = chunk.fullText
                if (chunk.sources.isNotEmpty()) {
                    _streamingSources.value = chunk.sources
                    finalSources = chunk.sources
                }
                if (chunk.promptTokens > 0L) promptTokens = chunk.promptTokens
                if (chunk.outputTokens > 0L) outputTokens = chunk.outputTokens
                if (chunk.isComplete) {
                    finalReply = chunk.fullText
                }
            }

            if (finalReply.isBlank()) {
                finalReply = _streamingText.value
            }

            if (finalReply.isNotBlank()) {
                repository.addMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = finalReply,
                    modelId = modelId,
                    sources = finalSources,
                    promptTokens = promptTokens,
                    outputTokens = outputTokens
                )
                repository.updateConversationPreview(
                    conversationId = conversationId,
                    preview = finalReply,
                    incrementCountBy = 1L,
                    modelId = modelId
                )
                repository.recordTokenUsage(promptTokens, outputTokens)

                if (profile?.voiceEnabled == true && profile.voiceAutoSpeak) {
                    voiceEngine.speak(finalReply)
                }

                // Fetch context-aware follow-up questions in background
                viewModelScope.launch {
                    val suggestions = aiEngine.generateFollowUpSuggestions(userPrompt, finalReply)
                    _followUpSuggestions.value = suggestions
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(VM_TAG, "Inference failed", e)
            _chatError.value = e.localizedMessage ?: "AI request failed. Please check your connection and try again."
        } finally {
            _isGenerating.value = false
            _streamingText.value = ""
            _streamingSources.value = emptyList()
        }
    }

    private fun buildSystemPrompt(
        mode: ChatMode,
        profile: UserProfile?,
        assistant: CustomAssistant?,
        memories: List<MemoryRecord>
    ): String {
        val sb = StringBuilder()
        if (assistant != null) {
            sb.appendLine("You are ${assistant.name} (${assistant.personality}).")
            sb.appendLine(assistant.systemInstructions)
        } else {
            sb.appendLine(mode.systemHint)
        }
        val lang = profile?.language?.takeIf { it.isNotBlank() && it != "English" }
        if (lang != null) {
            sb.appendLine("Respond in $lang unless the user explicitly asks for another language.")
        }
        if (profile?.memoryEnabled != false) {
            val activeMemories = memories.filter { it.active && it.consentConfirmed }.take(15)
            if (activeMemories.isNotEmpty()) {
                sb.appendLine("\nUser-approved memory context (respect these preferences naturally):")
                activeMemories.forEach { mem ->
                    sb.appendLine("- [${mem.category}] ${mem.content}")
                }
            }
        }
        return sb.toString().trim()
    }

    // --- Web Research Workflow ---

    fun runDeepWebResearch(topic: String, depth: String) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isEmpty()) return
        if (!isOnline.value) {
            _researchError.value = "Internet connection is required for live Web Research."
            return
        }
        activeResearchJob?.cancel()
        _isResearching.value = true
        _researchReport.value = ""
        _researchSources.value = emptyList()
        _researchError.value = null

        activeResearchJob = viewModelScope.launch {
            val prompt = """
                Research Topic: $cleanTopic
                Depth: $depth
                
                Deliver a structured, verified research report using Markdown:
                1. Executive Summary
                2. Key Findings & Data Points
                3. Comparative Analysis / Perspectives
                4. Risks, Gaps & Open Questions
                5. Actionable Recommendations
            """.trimIndent()
            val config = AiRequestConfig(
                modelId = AiModelCatalog.DEFAULT_CHAT_MODEL_ID,
                systemPrompt = ChatMode.RESEARCH.systemHint,
                temperature = 0.4,
                maxOutputTokens = 4096,
                enableWebSearch = true
            )
            try {
                var pTokens = 0L
                var oTokens = 0L
                aiEngine.streamChat(config, emptyList(), prompt, null).collect { chunk ->
                    _researchReport.value = chunk.fullText
                    if (chunk.sources.isNotEmpty()) {
                        _researchSources.value = chunk.sources
                    }
                    if (chunk.promptTokens > 0L) pTokens = chunk.promptTokens
                    if (chunk.outputTokens > 0L) oTokens = chunk.outputTokens
                }
                repository.recordTokenUsage(pTokens, oTokens)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Web research failed", e)
                _researchError.value = e.localizedMessage ?: "Web research failed."
            } finally {
                _isResearching.value = false
            }
        }
    }

    fun stopWebResearch() {
        activeResearchJob?.cancel()
        _isResearching.value = false
    }

    fun saveResearchAsConversation(topic: String) {
        val report = _researchReport.value
        val sources = _researchSources.value
        if (report.isBlank()) return
        viewModelScope.launch {
            val convRes = repository.createConversation(
                title = "Research: ${topic.take(40)}",
                modelId = AiModelCatalog.DEFAULT_CHAT_MODEL_ID,
                mode = ChatMode.RESEARCH.name,
                lastMessagePreview = report.take(120)
            )
            convRes.onSuccess { convId ->
                repository.addMessage(convId, "user", "Deep Web Research: $topic")
                repository.addMessage(
                    conversationId = convId,
                    role = "assistant",
                    content = report,
                    modelId = AiModelCatalog.DEFAULT_CHAT_MODEL_ID,
                    sources = sources
                )
                repository.updateConversationPreview(convId, report.take(120), incrementCountBy = 2L)
                _activeConversationId.value = convId
                _activeMode.value = ChatMode.RESEARCH
                _currentRoute.value = NexoraRoute.CHAT
                _snackbarMessage.value = "Research saved to chat history"
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to save research"
            }
        }
    }

    // --- Image Generation Studio ---

    fun generateAiImage(
        context: Context,
        prompt: String,
        style: String,
        aspectRatio: String,
        modelId: String
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) {
            _imageGenState.value = ImageGenUiState.Error("Please enter an image description prompt.")
            return
        }
        if (!isOnline.value) {
            _imageGenState.value = ImageGenUiState.Error("Internet connection is required to generate images.")
            return
        }
        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiEngine.generateImage(clean, style, aspectRatio, modelId)
                val file = ExportAndShareUtils.saveGeneratedBitmapInternally(context, bitmap)
                repository.saveGeneratedImageRecord(
                    prompt = clean,
                    style = style,
                    aspectRatio = aspectRatio,
                    modelId = modelId,
                    localFilePath = file.absolutePath
                )
                _imageGenState.value = ImageGenUiState.Success(bitmap, file.absolutePath, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Image gen failed", e)
                _imageGenState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun deleteGeneratedImage(record: GeneratedImageRecord) {
        viewModelScope.launch {
            runCatching { File(record.localFilePath).delete() }
            repository.deleteGeneratedImageRecord(record.id)
                .onSuccess { _snackbarMessage.value = "Image removed from history" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to delete image" }
        }
    }

    // --- History & Folder Management ---

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationMetadata(conversationId, title = newTitle)
                .onSuccess { _snackbarMessage.value = "Conversation renamed" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Rename failed" }
        }
    }

    fun togglePinConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.updateConversationMetadata(conversation.id, pinned = !conversation.pinned)
        }
    }

    fun assignConversationFolder(conversationId: String, folderId: String?) {
        viewModelScope.launch {
            repository.updateConversationMetadata(
                conversationId = conversationId,
                folderId = folderId,
                clearFolder = folderId == null
            ).onSuccess {
                _snackbarMessage.value = if (folderId == null) "Removed from folder" else "Moved to folder"
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
                .onSuccess {
                    if (_activeConversationId.value == conversationId) {
                        _activeConversationId.value = null
                    }
                    _snackbarMessage.value = "Conversation deleted"
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun createFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.createFolder(name, colorHex)
                .onSuccess { _snackbarMessage.value = "Folder created" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to create folder" }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
                .onSuccess { _snackbarMessage.value = "Folder deleted" }
        }
    }

    fun createShareReference(conversation: Conversation, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val code = conversation.shareCode ?: "nx_${UUID.randomUUID().toString().take(8)}"
            repository.updateConversationMetadata(conversation.id, shareCode = code)
            onReady("https://nexora.ai/share/$code")
        }
    }

    // --- Assistants ---

    fun saveAssistant(
        existingId: String?,
        name: String,
        tagline: String,
        personality: String,
        instructions: String,
        modelId: String,
        iconName: String,
        starterPrompt: String
    ) {
        viewModelScope.launch {
            repository.saveAssistant(
                existingId, name, tagline, personality, instructions, modelId, iconName, starterPrompt
            ).onSuccess {
                _snackbarMessage.value = if (existingId == null) "Assistant created" else "Assistant updated"
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to save assistant"
            }
        }
    }

    fun deleteAssistant(assistantId: String) {
        viewModelScope.launch {
            repository.deleteAssistant(assistantId)
                .onSuccess { _snackbarMessage.value = "Assistant deleted" }
        }
    }

    // --- Memory ---

    fun saveMemory(existingId: String?, category: String, content: String, consentConfirmed: Boolean) {
        viewModelScope.launch {
            repository.saveMemory(existingId, category, content, active = true, consentConfirmed = consentConfirmed)
                .onSuccess { _snackbarMessage.value = "Memory saved" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not save memory" }
        }
    }

    fun toggleMemoryActive(memory: MemoryRecord) {
        viewModelScope.launch {
            repository.toggleMemoryActive(memory.id, !memory.active)
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
                .onSuccess { _snackbarMessage.value = "Memory deleted" }
        }
    }

    // --- Profile & Settings ---

    fun updateProfileDetails(displayName: String, bio: String) {
        viewModelScope.launch {
            repository.updateUserProfile(
                mapOf(
                    "displayName" to displayName.trim().ifBlank { "Nexora User" }.take(100),
                    "bio" to bio.trim().take(400)
                )
            ).onSuccess { _snackbarMessage.value = "Profile updated" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun updateThemeMode(themeMode: String) {
        viewModelScope.launch {
            prefsStore.setThemeMode(themeMode)
            repository.updateUserProfile(mapOf("themeMode" to themeMode))
        }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            repository.updateUserProfile(mapOf("language" to language))
                .onSuccess { _snackbarMessage.value = "Language set to $language" }
        }
    }

    fun updatePreferredModel(modelId: String) {
        _selectedModelId.value = modelId
        viewModelScope.launch {
            repository.updateUserProfile(mapOf("preferredModel" to modelId))
                .onSuccess { _snackbarMessage.value = "Default model updated" }
        }
    }

    fun updateModelTuning(temperature: Double, maxOutputTokens: Long) {
        viewModelScope.launch {
            repository.updateUserProfile(
                mapOf(
                    "temperature" to temperature.coerceIn(0.0, 2.0),
                    "maxOutputTokens" to maxOutputTokens.coerceIn(128L, 16384L)
                )
            ).onSuccess { _snackbarMessage.value = "Model settings saved" }
        }
    }

    fun updateMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserProfile(mapOf("memoryEnabled" to enabled))
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserProfile(mapOf("notificationsEnabled" to enabled))
        }
    }

    fun updateVoicePreferences(
        voiceEnabled: Boolean,
        autoSpeak: Boolean,
        voiceName: String?,
        rate: Double,
        pitch: Double
    ) {
        voiceEngine.applyVoiceSettings(voiceName, rate.toFloat(), pitch.toFloat())
        viewModelScope.launch {
            repository.updateUserProfile(
                mapOf(
                    "voiceEnabled" to voiceEnabled,
                    "voiceAutoSpeak" to autoSpeak,
                    "voiceName" to voiceName,
                    "voiceRate" to rate.coerceIn(0.5, 2.0),
                    "voicePitch" to pitch.coerceIn(0.5, 2.0)
                )
            )
        }
    }

    fun markNotificationRead(id: String, read: Boolean = true) {
        viewModelScope.launch {
            repository.markNotificationRead(id, read)
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun submitFeedback(category: String, subject: String, message: String, rating: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.submitFeedback(category, subject, message, rating)
                .onSuccess {
                    _snackbarMessage.value = "Thank you! Your feedback has been recorded."
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to submit feedback."
                }
        }
    }

    fun deleteAllUserData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAllUserData()
                .onSuccess {
                    _activeConversationId.value = null
                    _snackbarMessage.value = "All personal data erased."
                    onDeleted()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to delete user data."
                }
        }
    }

    companion object {
        fun provideFactory(
            context: Context,
            currentUserId: String,
            firebaseUser: FirebaseUser,
            prefsStore: LocalPreferencesStore,
            voiceEngine: VoiceEngine
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                NexoraViewModel(
                    repository = NexoraFirestoreRepository(appContext),
                    aiEngine = MultiModelAiEngine(),
                    prefsStore = prefsStore,
                    connectivityMonitor = ConnectivityMonitor(appContext),
                    voiceEngine = voiceEngine,
                    currentUserId = currentUserId,
                    firebaseUser = firebaseUser
                )
            }
        }
    }
}
