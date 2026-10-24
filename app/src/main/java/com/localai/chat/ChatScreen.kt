package com.localai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(vm: ChatViewModel, onSettings: () -> Unit, onHistory: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val s by vm.settings.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val last = ui.messages.lastOrNull()
    val ready = ui.model is ModelState.Ready
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(ui.messages.size, last?.text?.length, last?.thinking?.length) {
        if (ui.messages.isNotEmpty()) listState.scrollBy(1_000_000f)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSettings).padding(8.dp)) {
                Text("AI", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val m = ui.model) {
                        is ModelState.Ready -> m.name.removeSuffix(".gguf")
                        is ModelState.Loading -> "正在加载 ${m.name.removeSuffix(".gguf")}…"
                        is ModelState.Failed -> m.reason
                        ModelState.None -> "未选择模型"
                    },
                    style = MaterialTheme.typography.bodySmall, color = dim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onHistory) { Icon(Ic.Clock, "历史对话") }
            IconButton(onClick = vm::newChat) { Icon(Ic.Add, "新对话") }
            IconButton(onClick = onSettings) { Icon(Ic.Sliders, "设置") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ui.messages.isEmpty()) {
                EmptyState(ui.model, s.webSearch, onSettings)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(ui.messages, key = { it.id }) { MessageItem(it) }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }

        ui.status?.let {
            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = dim)
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            val on = s.webSearch
            Box(
                Modifier.padding(bottom = 4.dp).size(44.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { vm.settings.update { it.copy(webSearch = !it.webSearch) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Globe, "联网搜索", tint = if (on) MaterialTheme.colorScheme.primary else dim)
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (ready) "输入消息" else "请先选择模型") },
                maxLines = 5, shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { if (ui.generating) vm.stop() else if (vm.send(input)) input = "" },
                enabled = ui.generating || (ready && input.isNotBlank()),
                modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
            ) {
                Icon(if (ui.generating) Ic.Stop else Ic.Send, if (ui.generating) "停止" else "发送")
            }
        }
    }
}
