package com.localai.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val chats by vm.chats.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, "返回") }
            Text("历史对话", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (chats.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("清空全部") }
        }
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有历史对话", color = dim) }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(chats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openChat(c.id); onBack() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(fmt.format(Date(c.updated)), style = MaterialTheme.typography.bodySmall, color = dim)
                        }
                        IconButton(onClick = { vm.deleteChat(c.id) }) { Icon(Ic.Trash, "删除", tint = dim) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("清空全部历史对话？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = { TextButton(onClick = { vm.deleteAllChats(); confirmAll = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("取消") } },
        )
    }
}
