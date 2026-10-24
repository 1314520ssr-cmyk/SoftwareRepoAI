package com.localai.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun ModelSection(vm: ChatViewModel) {
    val models by vm.models.collectAsState()
    val progress by vm.importProgress.collectAsState()
    val ui by vm.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val active = when (val m = ui.model) {
        is ModelState.Ready -> m.name
        is ModelState.Loading -> m.name
        else -> ""
    }

    SectionTitle("模型")
    Text(
        "本机内存约 %.1f GB。模型导入后保存在应用内，切换时不需要再下载。".format(vm.totalRamBytes / 1e9),
        color = dim, style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "推荐：Qwen3-1.7B（普通手机）；DeepSeek-R1-Distill-Qwen-7B（12GB 以上内存）。",
        color = dim, style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    if (models.isEmpty()) Text("还没有模型，点下面的按钮选择手机里的 .gguf 文件。", color = dim)

    models.forEach { m ->
        val tooBig = m.sizeBytes > vm.totalRamBytes * 0.6
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.loadModel(m) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.displayName, maxLines = 2)
                Text(
                    "%.1f GB · %s%s".format(m.sizeBytes / 1e9, m.family.label, if (tooBig) " · 可能内存不足" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooBig) MaterialTheme.colorScheme.error else dim,
                )
            }
            if (m.name == active) Icon(Ic.Check, "当前模型", tint = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { vm.deleteModel(m) }) { Icon(Ic.Trash, "删除", tint = dim) }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = progress == null) {
        Icon(Ic.Import, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("导入模型 (.gguf)")
    }
    progress?.let {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth())
        Text("正在导入 ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = dim)
    }
    ui.status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
