package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AiModelCatalog
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMode
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.ui.NexoraViewModel
import com.example.ui.components.NexoraMarkdownText
import com.example.ui.theme.EmeraldConnected
import com.example.util.ExportAndShareUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: NexoraViewModel,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val activeMode by viewModel.activeMode.collectAsStateWithLifecycle()
    val activeAssistantId by viewModel.activeAssistantId.collectAsStateWithLifecycle()
    val assistantsState by viewModel.assistantsState.collectAsStateWithLifecycle()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingSources by viewModel.streamingSources.collectAsStateWithLifecycle()
    val followUpSuggestions by viewModel.followUpSuggestions.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val queuedPrompts by viewModel.queuedPrompts.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showVoiceModal by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editedText by remember { mutableStateOf("") }
    var voiceLanguageTag by remember { mutableStateOf(Locale.getDefault().toLanguageTag()) }

    val activeConversation: Conversation? = remember(conversationsState, activeConversationId) {
        (conversationsState as? UiState.Success)?.data?.firstOrNull { it.id == activeConversationId }
    }
    val activeAssistant = remember(assistantsState, activeAssistantId) {
        (assistantsState as? UiState.Success)?.data?.firstOrNull { it.id == activeAssistantId }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUri(context, uri)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachUri(context, uri)
        }
    }

    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingVoiceAction?.invoke()
        } else {
            viewModel.showMessage("Microphone permission is needed for voice dictation.")
        }
        pendingVoiceAction = null
    }

    fun requestMicAndRun(action: () -> Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            action()
        } else {
            pendingVoiceAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val listState = rememberLazyListState()
    val messageList = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messageList.size, isGenerating, streamingText.length) {
        val total = messageList.size + (if (isGenerating) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem((total - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDrawerButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("chat_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_open_drawer)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showModelSheet = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeAssistant?.name ?: activeConversation?.title ?: "Nexora AI",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = AiModelCatalog.findById(selectedModelId).displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "${activeMode.displayName} mode · Tap to switch model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = { showVoiceModal = true },
                    modifier = Modifier.testTag("open_voice_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = stringResource(R.string.cd_voice_mode),
                        tint = if (voiceState.isListening || voiceState.isSpeaking) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                IconButton(
                    onClick = { viewModel.startNewConversation() },
                    modifier = Modifier.testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_new_chat)
                    )
                }

                if (activeConversation != null) {
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier.testTag("chat_more_menu_button")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Conversation actions")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export as TXT") },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                onClick = {
                                    showExportMenu = false
                                    ExportAndShareUtils.exportConversationToTxtAndShare(
                                        context,
                                        activeConversation,
                                        messageList
                                    ).onFailure { viewModel.showMessage(it.localizedMessage ?: "Export failed") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as PDF") },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                onClick = {
                                    showExportMenu = false
                                    ExportAndShareUtils.exportConversationToPdfAndShare(
                                        context,
                                        activeConversation,
                                        messageList
                                    ).onFailure { viewModel.showMessage(it.localizedMessage ?: "PDF export failed") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Share Link") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.createShareReference(activeConversation) { url ->
                                        ExportAndShareUtils.copyToClipboard(context, "Nexora Share", url)
                                        viewModel.showMessage("Share link copied: $url")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Offline / Queue Banner
        AnimatedVisibility(visible = !isOnline || queuedPrompts.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (!isOnline) {
                            stringResource(R.string.offline_banner) +
                                if (queuedPrompts.isNotEmpty()) " (${queuedPrompts.size} queued)" else ""
                        } else {
                            "Syncing ${queuedPrompts.size} queued prompt(s)…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Error Banner
        AnimatedVisibility(visible = !chatError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearChatError() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Main Chat Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                messagesState is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                messageList.isEmpty() && !isGenerating -> {
                    ChatEmptyState(
                        activeMode = activeMode,
                        assistantName = activeAssistant?.name,
                        starterPrompt = activeAssistant?.starterPrompt,
                        onSelectMode = { viewModel.selectMode(it) },
                        onUsePrompt = { prompt -> viewModel.sendChatMessage(prompt) }
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messageList, key = { it.id }) { msg ->
                            val isLastAssistant = msg.role == "assistant" &&
                                msg.id == messageList.lastOrNull { it.role == "assistant" }?.id
                            MessageBubble(
                                message = msg,
                                isLastAssistant = isLastAssistant && !isGenerating,
                                isSpeaking = voiceState.isSpeaking,
                                onCopy = {
                                    ExportAndShareUtils.copyToClipboard(context, "Nexora Response", msg.content)
                                    viewModel.showMessage("Copied to clipboard")
                                },
                                onEditUserMessage = {
                                    editingMessage = msg
                                    editedText = msg.content
                                },
                                onRegenerate = { viewModel.regenerateLastResponse() },
                                onSpeakToggle = {
                                    if (voiceState.isSpeaking) {
                                        viewModel.voiceEngine.stopSpeaking()
                                    } else {
                                        viewModel.voiceEngine.speak(msg.content)
                                    }
                                },
                                onShare = {
                                    ExportAndShareUtils.shareText(context, "Nexora AI Response", msg.content)
                                },
                                onCopiedCode = { viewModel.showMessage(it) }
                            )
                        }

                        if (isGenerating) {
                            item(key = "streaming_indicator") {
                                StreamingAssistantBubble(
                                    modelId = selectedModelId,
                                    streamingText = streamingText,
                                    sources = streamingSources,
                                    onStop = { viewModel.stopGeneration() }
                                )
                            }
                        }

                        if (!isGenerating && followUpSuggestions.isNotEmpty()) {
                            item(key = "followups") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Suggested follow-ups",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        followUpSuggestions.forEach { suggestion ->
                                            AssistChip(
                                                onClick = { viewModel.sendChatMessage(suggestion) },
                                                label = { Text(suggestion, maxLines = 1) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Mode & Web Search quick strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = webSearchEnabled,
                        onClick = { viewModel.toggleWebSearch(!webSearchEnabled) },
                        label = { Text("Web Search") },
                        leadingIcon = {
                            Icon(Icons.Filled.Public, null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("toggle_web_search_chip")
                    )
                    ChatMode.entries.forEach { mode ->
                        FilterChip(
                            selected = activeMode == mode,
                            onClick = { viewModel.selectMode(mode) },
                            label = { Text(mode.displayName) }
                        )
                    }
                }

                // Pending attachment preview
                AnimatedVisibility(visible = pendingAttachment != null) {
                    pendingAttachment?.let { att ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (att.isImage) {
                                    val bmp = remember(att.bytes) {
                                        BitmapFactory.decodeByteArray(att.bytes, 0, att.bytes.size)
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = att.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                } else {
                                    Icon(Icons.Filled.Description, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${(att.bytes.size / 1024).coerceAtLeast(1)} KB · ${att.mimeType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearAttachment() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Live voice dictation indicator
                AnimatedVisibility(visible = voiceState.isListening) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = voiceState.partialTranscript.ifBlank { "Listening… speak now" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.voiceEngine.stopListening() }) {
                                Text("Stop")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("attach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = stringResource(R.string.cd_attach_file)
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Photo / Image (Photo Picker)") },
                                leadingIcon = { Icon(Icons.Filled.Image, null) },
                                onClick = {
                                    showAttachMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("PDF / Text / Code File") },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                onClick = {
                                    showAttachMenu = false
                                    docPickerLauncher.launch(
                                        arrayOf("application/pdf", "text/*", "application/json")
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                        maxLines = 5,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (voiceState.isListening) {
                                viewModel.voiceEngine.stopListening()
                            } else {
                                requestMicAndRun {
                                    viewModel.voiceEngine.startListening(
                                        languageTag = voiceLanguageTag,
                                        onFinalResult = { spoken ->
                                            inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mic_dictation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.cd_voice_input),
                            tint = if (voiceState.isListening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (isGenerating) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("stop_generation_button")
                                .clickable { viewModel.stopGeneration() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = stringResource(R.string.stop_generating),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else {
                        val canSend = inputText.isNotBlank() || pendingAttachment != null
                        Surface(
                            color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_message_button")
                                .clickable(enabled = canSend) {
                                    val prompt = inputText
                                    inputText = ""
                                    viewModel.sendChatMessage(prompt)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.cd_send_message),
                                    tint = if (canSend) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Model Switcher Bottom Sheet
    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select AI Model", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Switch models anytime inside the active conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                AiModelCatalog.chatModels.forEach { spec ->
                    val configured = AiModelCatalog.isProviderConfigured(spec.provider)
                    val selected = spec.id == selectedModelId
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectModel(spec.id)
                                showModelSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = if (configured) EmeraldConnected.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (configured) "Connected" else "Key Required",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (configured) EmeraldConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    spec.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Hands-Free Voice Conversation Sheet
    if (showVoiceModal) {
        ModalBottomSheet(onDismissRequest = {
            viewModel.voiceEngine.stopListening()
            showVoiceModal = false
        }) {
            val pulseScale by rememberInfiniteTransition(label = "voicePulse").animateFloat(
                initialValue = 1f,
                targetValue = if (voiceState.isListening) 1.18f else 1.04f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulse"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Nexora Voice Conversation", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Powered by Android SpeechRecognizer & Text-to-Speech. Speak naturally and Nexora will reply and read aloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("en-US" to "English", "hi-IN" to "Hindi", "es-ES" to "Spanish", "fr-FR" to "French", "ar-SA" to "Arabic").forEach { (tag, label) ->
                        FilterChip(
                            selected = voiceLanguageTag == tag,
                            onClick = { voiceLanguageTag = tag },
                            label = { Text(label) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (voiceState.isListening) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .clickable {
                            if (voiceState.isListening) {
                                viewModel.voiceEngine.stopListening()
                            } else {
                                requestMicAndRun {
                                    viewModel.voiceEngine.startListening(
                                        languageTag = voiceLanguageTag,
                                        onFinalResult = { spoken ->
                                            viewModel.sendChatMessage(spoken)
                                        }
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Tap to speak",
                        tint = if (voiceState.isListening) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = when {
                        voiceState.isListening -> voiceState.partialTranscript.ifBlank { "Listening… speak now" }
                        isGenerating -> "Nexora is thinking…"
                        voiceState.isSpeaking -> "Speaking response…"
                        else -> "Tap microphone to ask a question"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!voiceState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = voiceState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (voiceState.isSpeaking) {
                        Button(onClick = { viewModel.voiceEngine.stopSpeaking() }) {
                            Icon(Icons.Filled.VolumeOff, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop Audio")
                        }
                    } else {
                        val lastReply = messageList.lastOrNull { it.role == "assistant" }?.content
                        if (!lastReply.isNullOrBlank()) {
                            Button(onClick = { viewModel.voiceEngine.speak(lastReply) }) {
                                Icon(Icons.Filled.VolumeUp, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Speak Last Reply")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Edit & Resend User Message Dialog
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit & Resend Prompt") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6
                )
            },
            confirmButton = {
                Button(onClick = {
                    val textToSend = editedText
                    editingMessage = null
                    viewModel.editAndResendMessage(msg, textToSend)
                }) {
                    Text("Resend")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatEmptyState(
    activeMode: ChatMode,
    assistantName: String?,
    starterPrompt: String?,
    onSelectMode: (ChatMode) -> Unit,
    onUsePrompt: (String) -> Unit
) {
    val quickActions = listOf(
        Triple(ChatMode.CODE, Icons.Filled.Code, "Design a thread-safe Kotlin Coroutine Flow cache with tests"),
        Triple(ChatMode.MATH, Icons.Filled.Functions, "Solve step by step: integral of x * e^(2x) dx"),
        Triple(ChatMode.RESEARCH, Icons.Filled.Public, "Compare the latest solid-state battery breakthroughs in 2026"),
        Triple(ChatMode.TRANSLATE, Icons.Filled.Translate, "Translate into natural Spanish & Japanese: 'Welcome to the future of intelligent tools.'"),
        Triple(ChatMode.WRITING, Icons.Filled.Edit, "Proofread and elevate this executive summary for clarity and impact")
    )

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = assistantName ?: "How can Nexora AI help today?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Active mode: ${activeMode.displayName}. Choose a capability or type your prompt below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))

        if (!starterPrompt.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUsePrompt(starterPrompt) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(starterPrompt, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickActions.forEach { (mode, icon, sample) ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectMode(mode)
                            onUsePrompt(sample)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(sample, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLastAssistant: Boolean,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onEditUserMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onSpeakToggle: () -> Unit,
    onShare: () -> Unit,
    onCopiedCode: (String) -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (!message.attachmentName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AttachFile, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(message.attachmentName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (message.edited) {
                        Text(
                            text = "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = AiModelCatalog.findById(message.modelId ?: AiModelCatalog.DEFAULT_CHAT_MODEL_ID).displayName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.outputTokens > 0L) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${message.outputTokens} tok",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    NexoraMarkdownText(
                        markdown = message.content,
                        onCopied = onCopiedCode
                    )

                    if (message.sources.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Grounded Web Sources",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        message.sources.forEach { src ->
                            Text(
                                text = "• $src",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Action row below message
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            if (isUser) {
                IconButton(onClick = onEditUserMessage, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit and resend",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy prompt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_text),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onSpeakToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.cd_speak),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.cd_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isLastAssistant) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_regenerate),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    modelId: String,
    streamingText: String,
    sources: List<String>,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${AiModelCatalog.findById(modelId).displayName} · Streaming",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onStop) {
                    Text("Stop")
                }
            }
            Spacer(Modifier.height(6.dp))
            if (streamingText.isBlank()) {
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                NexoraMarkdownText(markdown = streamingText)
            }
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                sources.forEach { src ->
                    Text(
                        text = "• $src",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
