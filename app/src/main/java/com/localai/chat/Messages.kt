package com.localai.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MessageItem(msg: UiMessage) {
    if (!msg.isUser) { AssistantMessage(msg); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            SelectionContainer {
                Text(
                    msg.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(msg: UiMessage) {
    var open by remember(msg.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth()) {
        if (msg.thinking.isNotBlank()) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = if (msg.streaming && msg.text.isEmpty()) "思考中…" else "思考过程"
                Text(label, style = MaterialTheme.typography.labelLarge, color = dim)
                Icon(Ic.Chevron, null, Modifier.size(18.dp).rotate(if (open) 180f else 0f), tint = dim)
            }
            if (open) {
                Text(
                    msg.thinking, Modifier.padding(start = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = dim,
                )
            }
        }
        if (msg.text.isNotEmpty()) {
            SelectionContainer { Text(msg.text, style = MaterialTheme.typography.bodyLarge) }
        } else if (msg.streaming && msg.thinking.isBlank()) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (msg.sources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("来源", style = MaterialTheme.typography.labelSmall, color = dim)
            msg.sources.forEach { src ->
                Text(
                    "[${src.index}] ${src.title}",
                    Modifier.clickable { runCatching { uri.openUri(src.url) } }.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!msg.streaming && msg.text.isNotEmpty()) {
            IconButton(onClick = { clipboard.setText(AnnotatedString(msg.text)) }, Modifier.size(36.dp)) {
                Icon(Ic.Copy, "复制", Modifier.size(18.dp), tint = dim)
            }
        }
    }
}
