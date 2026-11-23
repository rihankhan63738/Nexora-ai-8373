package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiModelCatalog
import com.example.data.model.ChatMode
import com.example.data.model.CustomAssistant
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryRecord
import com.example.data.model.UiState
import com.example.ui.NexoraViewModel

@Composable
fun AssistantsScreen(viewModel: NexoraViewModel) {
    val assistantsState by viewModel.assistantsState.collectAsStateWithLifecycle()
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingAssistant by remember { mutableStateOf<CustomAssistant?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Custom Assistants", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Create specialized AI personas with custom instructions and preferred models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingAssistant = null
                        showEditorDialog = true
                    },
                    modifier = Modifier.testTag("create_assistant_button")
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create")
                }
            }

            Spacer(Modifier.height(14.dp))

            when (val state = assistantsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.data, key = { it.id }) { assistant ->
                            AssistantCard(
                                assistant = assistant,
                                onStartChat = {
                                    viewModel.startNewConversation(
                                        mode = ChatMode.CHAT,
                                        assistant = assistant
                                    )
                                },
                                onEdit = {
                                    editingAssistant = assistant
                                    showEditorDialog = true
                                },
                                onDelete = { viewModel.deleteAssistant(assistant.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        AssistantEditorDialog(
            initial = editingAssistant,
            onDismiss = { showEditorDialog = false },
            onSave = { name, tagline, personality, instructions, modelId, starterPrompt ->
                viewModel.saveAssistant(
                    existingId = editingAssistant?.id,
                    name = name,
                    tagline = tagline,
                    personality = personality,
                    instructions = instructions,
                    modelId = modelId,
                    iconName = editingAssistant?.iconName ?: "sparkles",
                    starterPrompt = starterPrompt
                )
                showEditorDialog = false
            }
        )
    }
}

@Composable
private fun AssistantCard(
    assistant: CustomAssistant,
    onStartChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (assistant.iconName) {
        "school" -> Icons.Filled.School
        "code" -> Icons.Filled.Code
        "edit" -> Icons.Filled.Edit
        "science" -> Icons.Filled.Science
        else -> Icons.Filled.AutoAwesome
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(assistant.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        assistant.tagline.ifBlank { assistant.personality },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit assistant")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete assistant")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = assistant.systemInstructions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AiModelCatalog.findById(assistant.modelId).displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = onStartChat) {
                    Text("Start Chat")
                }
            }
        }
    }
}

@Composable
private fun AssistantEditorDialog(
    initial: CustomAssistant?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var tagline by remember { mutableStateOf(initial?.tagline ?: "") }
    var personality by remember { mutableStateOf(initial?.personality ?: "Insightful & concise") }
    var instructions by remember { mutableStateOf(initial?.systemInstructions ?: "") }
    var modelId by remember { mutableStateOf(initial?.modelId ?: AiModelCatalog.DEFAULT_CHAT_MODEL_ID) }
    var starterPrompt by remember { mutableStateOf(initial?.starterPrompt ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Custom Assistant" else "Edit Assistant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it },
                    label = { Text("Tagline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text("Personality / Tone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Custom System Instructions") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiModelCatalog.chatModels.filter { AiModelCatalog.isProviderConfigured(it.provider) }.forEach { spec ->
                        FilterChip(
                            selected = modelId == spec.id,
                            onClick = { modelId = spec.id },
                            label = { Text(spec.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = starterPrompt,
                    onValueChange = { starterPrompt = it },
                    label = { Text("Optional Starter Prompt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, tagline, personality, instructions, modelId, starterPrompt) },
                enabled = name.isNotBlank() && instructions.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MemoryScreen(viewModel: NexoraViewModel) {
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()

    val memoryEnabled = (profileState as? UiState.Success)?.data?.memoryEnabled ?: true
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryRecord?>(null) }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memory System", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "User-controlled preferences injected into AI context only with your consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingMemory = null
                        showAddDialog = true
                    },
                    modifier = Modifier.testTag("add_memory_button")
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Memory Across Chats", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Never save passwords or sensitive credentials. Toggle off anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = { viewModel.updateMemoryEnabled(it) },
                        modifier = Modifier.testTag("global_memory_switch")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
                MemoryCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when (val state = memoriesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    val items = state.data.filter {
                        selectedCategory == null || it.category == selectedCategory?.name
                    }
                    if (items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No memory items saved yet. Tap Add to store a preference.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items, key = { it.id }) { mem ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = MemoryCategory.fromRaw(mem.category).label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Switch(
                                            checked = mem.active,
                                            onCheckedChange = { viewModel.toggleMemoryActive(mem) }
                                        )
                                        IconButton(onClick = {
                                            editingMemory = mem
                                            showAddDialog = true
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit memory")
                                        }
                                        IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete memory")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            initial = editingMemory,
            onDismiss = { showAddDialog = false },
            onSave = { cat, content, consent ->
                viewModel.saveMemory(editingMemory?.id, cat.name, content, consent)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    initial: MemoryRecord?,
    onDismiss: () -> Unit,
    onSave: (MemoryCategory, String, Boolean) -> Unit
) {
    var category by remember { mutableStateOf(MemoryCategory.fromRaw(initial?.category)) }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var consentChecked by remember { mutableStateOf(initial != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Save Memory" else "Edit Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What should Nexora remember?") },
                    placeholder = { Text("e.g., I prefer Kotlin examples with Jetpack Compose.") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it }
                    )
                    Text(
                        text = "I consent to storing this preference privately in my account (no passwords or sensitive PII).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, content, consentChecked) },
                enabled = content.isNotBlank() && consentChecked
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
