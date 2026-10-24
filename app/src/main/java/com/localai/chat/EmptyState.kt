package com.localai.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun EmptyState(model: ModelState, webSearch: Boolean, onSettings: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (model) {
            is ModelState.Loading -> {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text("正在加载模型…", color = dim)
            }
            is ModelState.Ready -> {
                Text("开始聊天", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (webSearch) "联网搜索已开启：先查资料，再由本地模型回答" else "完全离线运行，内容不会离开手机",
                    textAlign = TextAlign.Center, color = dim,
                )
            }
            else -> {
                Text("还没有可用的模型", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("到设置里导入 .gguf 模型文件，导入一次即可，之后切换不用再下载。", textAlign = TextAlign.Center, color = dim)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSettings) { Text("打开设置") }
            }
        }
    }
}
