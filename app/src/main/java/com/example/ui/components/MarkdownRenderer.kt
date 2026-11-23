package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBackgroundDark
import com.example.ui.theme.CodeBlockBackgroundLight
import com.example.ui.theme.CodeCommentColor
import com.example.ui.theme.CodeKeywordColor
import com.example.ui.theme.CodeNumberColor
import com.example.ui.theme.CodeStringColor
import com.example.ui.theme.CodeTypeColor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.ExportAndShareUtils

private sealed interface MdBlock {
    data class TextBlock(val lines: List<String>) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class MathBlock(val expression: String) : MdBlock
}

@Composable
fun NexoraMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onCopied: (String) -> Unit = {}
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.CodeBlock -> {
                        CodeBlockCard(
                            language = block.language,
                            code = block.code,
                            isDark = isDark,
                            onCopy = {
                                ExportAndShareUtils.copyToClipboard(context, "Code", block.code)
                                onCopied("Code copied to clipboard")
                            }
                        )
                    }
                    is MdBlock.MathBlock -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = block.expression,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                    is MdBlock.TableBlock -> {
                        MarkdownTable(headers = block.headers, rows = block.rows, textColor = textColor)
                    }
                    is MdBlock.TextBlock -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.lines.forEach { line ->
                                RenderTextLine(line = line, textColor = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderTextLine(line: String, textColor: Color) {
    val trimmed = line.trimStart()
    when {
        trimmed.startsWith("### ") -> {
            Text(
                text = formatInlineMarkdown(trimmed.removePrefix("### "), MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        trimmed.startsWith("## ") -> {
            Text(
                text = formatInlineMarkdown(trimmed.removePrefix("## "), MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        trimmed.startsWith("# ") -> {
            Text(
                text = formatInlineMarkdown(trimmed.removePrefix("# "), MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        trimmed.startsWith("> ") -> {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatInlineMarkdown(trimmed.removePrefix("> "), MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = formatInlineMarkdown(trimmed.drop(2), MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }
        Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
            val dotIdx = trimmed.indexOf('.')
            val prefix = trimmed.substring(0, dotIdx + 1)
            val rest = trimmed.substring(dotIdx + 1).trimStart()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = formatInlineMarkdown(rest, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }
        trimmed == "---" || trimmed == "***" -> {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        trimmed.isEmpty() -> {
            Spacer(modifier = Modifier.height(2.dp))
        }
        else -> {
            Text(
                text = formatInlineMarkdown(line, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    isDark: Boolean,
    onCopy: () -> Unit
) {
    val bgColor = if (isDark) CodeBlockBackgroundDark else CodeBlockBackgroundLight
    val highlighted = remember(code) { highlightCodeSyntax(code) }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CodeKeywordColor
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(IntrinsicSize.Max)
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .width(140.dp)
                            .padding(end = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            rows.forEachIndexed { idx, row ->
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    headers.indices.forEach { colIdx ->
                        Text(
                            text = row.getOrElse(colIdx) { "" },
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            modifier = Modifier
                                .width(140.dp)
                                .padding(end = 8.dp)
                        )
                    }
                }
                if (idx < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.lines()
    val blocks = mutableListOf<MdBlock>()
    val textBuffer = mutableListOf<String>()
    var i = 0

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            blocks.add(MdBlock.TextBlock(textBuffer.toList()))
            textBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushText()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.startsWith("$$")) {
            flushText()
            val exprLines = mutableListOf<String>()
            val sameLine = trimmed.removePrefix("$$")
            if (sameLine.endsWith("$$") && sameLine.length > 2) {
                blocks.add(MdBlock.MathBlock(sameLine.removeSuffix("$$").trim()))
                i++
                continue
            } else if (sameLine.isNotBlank()) {
                exprLines.add(sameLine)
            }
            i++
            while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                exprLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                val endLine = lines[i].trim().removeSuffix("$$").trim()
                if (endLine.isNotBlank()) exprLines.add(endLine)
            }
            blocks.add(MdBlock.MathBlock(exprLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size &&
            lines[i + 1].trim().matches(Regex("^\\|[\\s:\\-|]+\\|$"))
        ) {
            flushText()
            val headers = trimmed.trim('|').split('|').map { it.trim() }
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(lines[i].trim().trim('|').split('|').map { it.trim() })
                i++
            }
            blocks.add(MdBlock.TableBlock(headers, rows))
            continue
        }

        textBuffer.add(line)
        i++
    }
    flushText()
    return blocks
}

private fun formatInlineMarkdown(text: String, accentColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            when {
                text.startsWith("**", idx) -> {
                    val end = text.indexOf("**", idx + 2)
                    if (end > idx + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(idx + 2, end))
                        }
                        idx = end + 2
                    } else {
                        append("**")
                        idx += 2
                    }
                }
                text[idx] == '`' -> {
                    val end = text.indexOf('`', idx + 1)
                    if (end > idx + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = accentColor,
                                background = accentColor.copy(alpha = 0.12f),
                                fontSize = 13.sp
                            )
                        ) {
                            append(" ${text.substring(idx + 1, end)} ")
                        }
                        idx = end + 1
                    } else {
                        append('`')
                        idx++
                    }
                }
                text[idx] == '*' && (idx + 1 >= text.length || text[idx + 1] != '*') -> {
                    val end = text.indexOf('*', idx + 1)
                    if (end > idx + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(idx + 1, end))
                        }
                        idx = end + 1
                    } else {
                        append('*')
                        idx++
                    }
                }
                else -> {
                    append(text[idx])
                    idx++
                }
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum",
    "if", "else", "when", "for", "while", "return", "suspend", "private", "public",
    "override", "import", "package", "def", "async", "await", "const", "let",
    "function", "export", "from", "true", "false", "null", "try", "catch", "throw",
    "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE"
)

private fun highlightCodeSyntax(code: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val commentIdx = line.indexOf("//").takeIf { it >= 0 }
                ?: line.indexOf("# ").takeIf { it >= 0 }
            val codePart = if (commentIdx != null) line.substring(0, commentIdx) else line
            val commentPart = if (commentIdx != null) line.substring(commentIdx) else null

            val tokenRegex = Regex("(\"[^\"]*\"|'[^']*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Z][A-Za-z0-9_]*\\b|\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)")
            var cursor = 0
            for (match in tokenRegex.findAll(codePart)) {
                if (match.range.first > cursor) {
                    append(codePart.substring(cursor, match.range.first))
                }
                val token = match.value
                val style = when {
                    token.startsWith("\"") || token.startsWith("'") -> SpanStyle(color = CodeStringColor)
                    token.first().isDigit() -> SpanStyle(color = CodeNumberColor)
                    token in CODE_KEYWORDS -> SpanStyle(color = CodeKeywordColor, fontWeight = FontWeight.SemiBold)
                    token.first().isUpperCase() -> SpanStyle(color = CodeTypeColor)
                    else -> null
                }
                if (style != null) {
                    withStyle(style) { append(token) }
                } else {
                    append(token)
                }
                cursor = match.range.last + 1
            }
            if (cursor < codePart.length) {
                append(codePart.substring(cursor))
            }
            if (commentPart != null) {
                withStyle(SpanStyle(color = CodeCommentColor, fontStyle = FontStyle.Italic)) {
                    append(commentPart)
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}
