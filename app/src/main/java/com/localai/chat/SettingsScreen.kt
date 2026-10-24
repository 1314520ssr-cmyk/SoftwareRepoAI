package com.localai.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val s by vm.settings.state.collectAsState()

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Back, "返回") }
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ModelSection(vm)

            SectionTitle("功能")
            SwitchRow("联网搜索", "先搜索资料再由本地模型回答；关闭则完全离线", s.webSearch) { v ->
                vm.settings.update { it.copy(webSearch = v) }
            }
            SwitchRow("深度思考", "仅 Qwen3 / DeepSeek 支持，回答更慢但更严谨", s.thinking) { v ->
                vm.settings.update { it.copy(thinking = v) }
            }

            SectionTitle("生成参数")
            SliderRow("温度（越低越稳）", "%.2f".format(s.temperature), s.temperature, 0f..1.5f) { v ->
                vm.settings.update { it.copy(temperature = v) }
            }
            SliderRow("Top P", "%.2f".format(s.topP), s.topP, 0.5f..1f) { v ->
                vm.settings.update { it.copy(topP = v) }
            }
            SliderRow("防复读", "%.2f".format(s.repeatPenalty), s.repeatPenalty, 1f..1.3f) { v ->
                vm.settings.update { it.copy(repeatPenalty = v) }
            }
            SliderRow("最大回答长度", "${s.maxTokens}", s.maxTokens.toFloat(), 128f..2048f) { v ->
                vm.settings.update { it.copy(maxTokens = (v / 64).toInt() * 64) }
            }

            SectionTitle("性能（修改后需重新加载模型）")
            SliderRow("上下文长度", "${s.nCtx}", s.nCtx.toFloat(), 512f..4096f) { v ->
                vm.settings.update { it.copy(nCtx = (v / 256).toInt() * 256) }
            }
            SliderRow("线程数", "${s.threads}", s.threads.toFloat(), 1f..8f) { v ->
                vm.settings.update { it.copy(threads = v.toInt()) }
            }
            Button(onClick = vm::reloadModel, enabled = s.activeModel.isNotEmpty()) { Text("重新加载模型") }

            SectionTitle("系统提示词")
            OutlinedTextField(
                value = s.systemPrompt,
                onValueChange = { v -> vm.settings.update { it.copy(systemPrompt = v) } },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
            )
            OutlinedButton(onClick = { vm.settings.update { it.copy(systemPrompt = DEFAULT_SYSTEM_PROMPT) } }) {
                Text("恢复默认")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
