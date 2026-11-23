package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ai.AiModelCatalog
import com.example.data.model.UiState
import com.example.ui.NexoraViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: NexoraViewModel,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data
    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("User Profile", style = MaterialTheme.typography.headlineMedium)
            }

            if (profileState is UiState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (profile != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!profile.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile.photoUrl,
                                    contentDescription = "Profile avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    profile.email.ifBlank { "Google Authenticated Account" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "UID: ${profile.userId.take(12)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Real Usage Telemetry from Firestore
                item {
                    Text("Real AI Usage", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        UsageStatCard("Requests", profile.totalRequests.toString(), Modifier.weight(1f))
                        UsageStatCard("Prompt Tokens", profile.totalPromptTokens.toString(), Modifier.weight(1f))
                        UsageStatCard("Output Tokens", profile.totalOutputTokens.toString(), Modifier.weight(1f))
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Edit Profile", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Display name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Bio / Role") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { viewModel.updateProfileDetails(displayName, bio) },
                                modifier = Modifier.testTag("save_profile_button")
                            ) {
                                Text("Save Profile")
                            }
                        }
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Account & Security", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sign Out")
                            }
                            Button(
                                onClick = { showDeleteAllDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("delete_all_data_button")
                            ) {
                                Icon(Icons.Filled.DeleteForever, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete All Account Data")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Personal Data?") },
            text = {
                Text("This permanently erases all your conversations, messages, folders, custom assistants, memories, and profile settings from Firestore.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllUserData(onDeleted = onSignOut)
                    }
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun UsageStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(viewModel: NexoraViewModel) {
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data

    val themeMode = profile?.themeMode ?: "DARK"
    val language = profile?.language ?: "English"
    val defaultModel = profile?.preferredModel ?: AiModelCatalog.DEFAULT_CHAT_MODEL_ID
    var temp by remember(profile?.temperature) { mutableStateOf((profile?.temperature ?: 0.7).toFloat()) }
    var maxTokens by remember(profile?.maxOutputTokens) { mutableStateOf((profile?.maxOutputTokens ?: 4096L).toFloat()) }
    var voiceRate by remember(profile?.voiceRate) { mutableStateOf((profile?.voiceRate ?: 1.0).toFloat()) }
    var voicePitch by remember(profile?.voicePitch) { mutableStateOf((profile?.voicePitch ?: 1.0).toFloat()) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Settings & Personalization", style = MaterialTheme.typography.headlineMedium)
            }

            // Appearance
            item {
                SettingsSectionCard(title = "Appearance") {
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light").forEach { (key, label) ->
                            FilterChip(
                                selected = themeMode.equals(key, ignoreCase = true),
                                onClick = { viewModel.updateThemeMode(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Response Language", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("English", "Spanish", "French", "German", "Hindi", "Arabic", "Japanese").forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { viewModel.updateLanguage(lang) },
                                label = { Text(lang) }
                            )
                        }
                    }
                }
            }

            // AI Model Settings
            item {
                SettingsSectionCard(title = "Default AI Model & Tuning") {
                    Text("Default Model", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiModelCatalog.chatModels.filter { AiModelCatalog.isProviderConfigured(it.provider) }.forEach { spec ->
                            FilterChip(
                                selected = defaultModel == spec.id,
                                onClick = { viewModel.updatePreferredModel(spec.id) },
                                label = { Text(spec.displayName) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Temperature: ${String.format(Locale.US, "%.2f", temp)}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = temp,
                        onValueChange = { temp = it },
                        valueRange = 0f..1.5f
                    )
                    Text("Max Output Tokens: ${maxTokens.toInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        valueRange = 512f..8192f
                    )
                    Button(onClick = { viewModel.updateModelTuning(temp.toDouble(), maxTokens.toLong()) }) {
                        Text("Save Model Parameters")
                    }
                }
            }

            // Voice Settings
            item {
                SettingsSectionCard(title = "Voice & Speech") {
                    val voiceEnabled = profile?.voiceEnabled ?: true
                    val autoSpeak = profile?.voiceAutoSpeak ?: false
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Text-to-Speech", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = voiceEnabled,
                            onCheckedChange = {
                                viewModel.updateVoicePreferences(it, autoSpeak, profile?.voiceName, voiceRate.toDouble(), voicePitch.toDouble())
                            }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-Speak Assistant Replies", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = autoSpeak,
                            onCheckedChange = {
                                viewModel.updateVoicePreferences(voiceEnabled, it, profile?.voiceName, voiceRate.toDouble(), voicePitch.toDouble())
                            }
                        )
                    }
                    Text("Speech Rate: ${String.format(Locale.US, "%.2fx", voiceRate)}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = voiceRate,
                        onValueChange = { voiceRate = it },
                        valueRange = 0.6f..1.8f
                    )
                    Text("Pitch: ${String.format(Locale.US, "%.2f", voicePitch)}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = voicePitch,
                        onValueChange = { voicePitch = it },
                        valueRange = 0.6f..1.6f
                    )
                    if (voiceState.availableVoices.isNotEmpty()) {
                        Text("Device TTS Voice", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            voiceState.availableVoices.take(8).forEach { v ->
                                FilterChip(
                                    selected = profile?.voiceName == v.name,
                                    onClick = {
                                        viewModel.updateVoicePreferences(voiceEnabled, autoSpeak, v.name, voiceRate.toDouble(), voicePitch.toDouble())
                                    },
                                    label = { Text(v.displayLabel) }
                                )
                            }
                        }
                    }
                    Button(onClick = {
                        viewModel.updateVoicePreferences(voiceEnabled, autoSpeak, profile?.voiceName, voiceRate.toDouble(), voicePitch.toDouble())
                        viewModel.voiceEngine.speak("Hello, this is Nexora AI voice preview.")
                    }) {
                        Text("Save & Test Voice")
                    }
                }
            }

            // Privacy & Notifications
            item {
                SettingsSectionCard(title = "Privacy & Notifications") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cross-Conversation Memory", style = MaterialTheme.typography.bodyMedium)
                            Text("Allow saved memories to personalize prompts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = profile?.memoryEnabled ?: true,
                            onCheckedChange = { viewModel.updateMemoryEnabled(it) }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("In-App Notifications", style = MaterialTheme.typography.bodyMedium)
                            Text("Receive system and workspace alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = profile?.notificationsEnabled ?: true,
                            onCheckedChange = { viewModel.updateNotificationsEnabled(it) }
                        )
                    }
                }
            }

            // App Info
            item {
                SettingsSectionCard(title = "About Nexora AI") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Nexora AI · v1.0", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "One AI. Infinite Possibilities. Built with Jetpack Compose, Firebase AI Logic, App Check, and per-user Firestore rules.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun NotificationsScreen(viewModel: NexoraViewModel) {
    val state by viewModel.notificationsState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Notifications", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            when (val res = state) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> Text(res.message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    if (res.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("You're all caught up.", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(res.data, key = { it.id }) { notif ->
                                val dateStr = notif.createdAt?.toDate()?.let {
                                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it)
                                } ?: ""
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!notif.read) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                            Spacer(Modifier.height(2.dp))
                                            Text(notif.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (dateStr.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        if (!notif.read) {
                                            IconButton(onClick = { viewModel.markNotificationRead(notif.id, true) }) {
                                                Icon(Icons.Filled.DoneAll, contentDescription = "Mark read")
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteNotification(notif.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete notification")
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
}

@Composable
fun HelpAndFeedbackScreen(viewModel: NexoraViewModel) {
    var category by remember { mutableStateOf("Feedback") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var rating by remember { mutableLongStateOf(5L) }
    var expandedFaq by remember { mutableStateOf<Int?>(0) }

    val faqs = listOf(
        "Which AI models work immediately without extra setup?" to
            "Google Gemini 3.8 Flash, Gemini 3.5 Flash-Lite, Gemini 4 Argon, and Nano Banana image models run through Firebase AI Logic with App Check attestation.",
        "How do I enable OpenAI, Claude, or OpenRouter models?" to
            "Configure OPENAI_API_KEY, ANTHROPIC_API_KEY, or OPENROUTER_API_KEY in the AI Studio Secrets panel and rebuild the app.",
        "How does offline mode work?" to
            "Your saved conversations and history remain readable offline from Firestore's local cache. Any prompt sent offline is queued locally in DataStore and dispatched automatically when connectivity returns."
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Help & Feedback", style = MaterialTheme.typography.headlineMedium)
            }

            faqs.forEachIndexed { idx, (q, a) ->
                item {
                    val isOpen = expandedFaq == idx
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedFaq = if (isOpen) null else idx }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(q, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                            }
                            if (isOpen) {
                                Spacer(Modifier.height(6.dp))
                                Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Send Feedback", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Feedback", "Bug Report", "Feature Request").forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rating: ", style = MaterialTheme.typography.labelLarge)
                            (1L..5L).forEach { star ->
                                IconButton(onClick = { rating = star }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Rate $star stars",
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Subject") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Details") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.submitFeedback(category, subject, message, rating) {
                                    subject = ""
                                    message = ""
                                }
                            },
                            enabled = subject.isNotBlank() && message.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_feedback_button")
                        ) {
                            Text("Submit Feedback")
                        }
                    }
                }
            }
        }
    }
}
