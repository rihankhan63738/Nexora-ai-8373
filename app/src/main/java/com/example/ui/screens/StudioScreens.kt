package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AiModelCatalog
import com.example.data.model.ChatMode
import com.example.data.model.UiState
import com.example.ui.ImageGenUiState
import com.example.ui.NexoraViewModel
import com.example.ui.components.NexoraMarkdownText
import com.example.util.ExportAndShareUtils
import java.io.File

@Composable
fun ImageStudioScreen(viewModel: NexoraViewModel) {
    val context = LocalContext.current
    val imageGenState by viewModel.imageGenState.collectAsStateWithLifecycle()
    val historyState by viewModel.generatedImagesState.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Photorealistic") }
    var selectedAspect by remember { mutableStateOf("1:1") }
    var selectedModel by remember { mutableStateOf(AiModelCatalog.DEFAULT_IMAGE_MODEL_ID) }

    val styles = listOf("Photorealistic", "3D Render", "Cyberpunk", "Watercolor", "Minimalist Vector", "Natural / None")
    val aspects = listOf("1:1", "16:9", "9:16", "4:3", "3:4")
    val history = (historyState as? UiState.Success)?.data.orEmpty()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_nexora_studio_banner_1795412553800),
                            contentDescription = "Nexora Image Studio Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xE6090D18), Color(0x40090D18))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Nexora Image Studio",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Powered by Gemini Nano Banana 2.1 & 3 Pro Image",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe the image you want to create") },
                    placeholder = { Text("e.g., Isometric glass workstation glowing in soft morning light…") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )
            }

            item {
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiModelCatalog.imageModels.forEach { spec ->
                        FilterChip(
                            selected = selectedModel == spec.id,
                            onClick = { selectedModel = spec.id },
                            label = { Text(spec.displayName) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Style Preset", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    aspects.forEach { ratio ->
                        FilterChip(
                            selected = selectedAspect == ratio,
                            onClick = { selectedAspect = ratio },
                            label = { Text(ratio) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.generateAiImage(context, prompt, selectedStyle, selectedAspect, selectedModel)
                    },
                    enabled = imageGenState !is ImageGenUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_image_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageGenState is ImageGenUiState.Loading) "Synthesizing Image…" else "Generate Image")
                }
            }

            item {
                when (val state = imageGenState) {
                    is ImageGenUiState.Idle -> Unit
                    is ImageGenUiState.Loading -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("Generating artwork (may take 20–60s)…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    is ImageGenUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    is ImageGenUiState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = state.prompt,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(state.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            ExportAndShareUtils.saveImageToGallery(context, state.bitmap, state.prompt)
                                                .onSuccess { viewModel.showMessage(it) }
                                                .onFailure { viewModel.showMessage(it.localizedMessage ?: "Save failed") }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save")
                                    }
                                    Button(
                                        onClick = {
                                            ExportAndShareUtils.shareImageFile(context, File(state.localPath))
                                        }
                                    ) {
                                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Text("Generated History", style = MaterialTheme.typography.titleMedium)
                }
                items(history, key = { it.id }) { item ->
                    val bmp = remember(item.localFilePath) {
                        val f = File(item.localFilePath)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = item.prompt,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Image, null)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${item.style} · ${item.aspectRatio} · ${item.modelId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (bmp != null) {
                                IconButton(onClick = {
                                    ExportAndShareUtils.shareImageFile(context, File(item.localFilePath))
                                }) {
                                    Icon(Icons.Filled.Share, contentDescription = "Share image")
                                }
                            }
                            IconButton(onClick = { viewModel.deleteGeneratedImage(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete image")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileAnalysisScreen(viewModel: NexoraViewModel) {
    val context = LocalContext.current
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("Summarize the key insights, extract important details, and highlight action items.") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachUri(context, uri)
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.attachUri(context, uri)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("File & Image Analysis", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Upload an image via Android Photo Picker or select a PDF, Markdown, CSV, or source code file for multimodal Gemini analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pick_image_analysis_button")
                    ) {
                        Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Image")
                    }
                    OutlinedButton(
                        onClick = {
                            docPickerLauncher.launch(arrayOf("application/pdf", "text/*", "application/json"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pick_document_analysis_button")
                    ) {
                        Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick PDF / Doc")
                    }
                }
            }

            item {
                pendingAttachment?.let { att ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(att.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${(att.bytes.size / 1024).coerceAtLeast(1)} KB · ${att.mimeType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.clearAttachment() }) { Text("Clear") }
                        }
                    }
                } ?: Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No file selected yet. Pick an image or PDF/document above to analyze.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                Text("Quick Analysis Presets", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Executive Summary" to "Summarize this document with bullet points and key takeaways.",
                        "Extract Data" to "Extract all structured metrics, dates, names, and tables from this file.",
                        "Explain Visual / Diagram" to "Describe everything visible in this image and explain any diagrams or text.",
                        "Code & Security Review" to "Review this code file for bugs, edge cases, and architectural improvements."
                    ).forEach { (label, prompt) ->
                        FilterChip(
                            selected = question == prompt,
                            onClick = { question = prompt },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Analysis instructions") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.startNewConversation(mode = ChatMode.FILE)
                        viewModel.sendChatMessage(question, overrideMode = ChatMode.FILE)
                    },
                    enabled = pendingAttachment != null && question.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("analyze_file_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze in Chat")
                }
            }
        }
    }
}

@Composable
fun WebResearchScreen(viewModel: NexoraViewModel) {
    val report by viewModel.researchReport.collectAsStateWithLifecycle()
    val sources by viewModel.researchSources.collectAsStateWithLifecycle()
    val isResearching by viewModel.isResearching.collectAsStateWithLifecycle()
    val researchError by viewModel.researchError.collectAsStateWithLifecycle()

    var topic by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf("Deep Brief") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Web Research", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Live Google Search grounding via Gemini 3.8 Flash. Synthesizes up-to-date answers with verified web citations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Research question or topic") },
                    placeholder = { Text("e.g., Latest Kotlin Multiplatform Compose stable releases and benchmarks") },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("research_topic_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Quick Snapshot", "Deep Brief", "Comparative Matrix").forEach { option ->
                        FilterChip(
                            selected = depth == option,
                            onClick = { depth = option },
                            label = { Text(option) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isResearching) {
                        OutlinedButton(
                            onClick = { viewModel.stopWebResearch() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop Research")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.runDeepWebResearch(topic, depth) },
                            enabled = topic.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("start_research_button")
                        ) {
                            Icon(Icons.Filled.Public, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Grounded Research")
                        }
                    }
                    if (report.isNotBlank() && !isResearching) {
                        OutlinedButton(
                            onClick = { viewModel.saveResearchAsConversation(topic) },
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Filled.BookmarkAdd, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Chat")
                        }
                    }
                }
            }

            if (!researchError.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = researchError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (isResearching || report.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (isResearching) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Searching web & synthesizing…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            if (report.isNotBlank()) {
                                NexoraMarkdownText(
                                    markdown = report,
                                    onCopied = { viewModel.showMessage(it) }
                                )
                            }
                            if (sources.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Verified Sources", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                sources.forEach { src ->
                                    Text("• $src", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
