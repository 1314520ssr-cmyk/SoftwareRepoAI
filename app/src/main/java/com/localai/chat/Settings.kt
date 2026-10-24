package com.localai.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val maxTokens: Int,
    val nCtx: Int,
    val threads: Int,
    val systemPrompt: String,
    val webSearch: Boolean,
    val thinking: Boolean,
    val activeModel: String,
)

const val DEFAULT_SYSTEM_PROMPT =
    "你是一个在手机本地运行的 AI 助手。请用简洁的中文回答；不确定的内容直接说不知道，不要编造。"

class AppSettings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    // 不要开满所有核心：手机会因发热降频，反而更慢
    private val defaultThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    private fun read() = Settings(
        temperature = sp.getFloat("temperature", 0.5f),
        topP = sp.getFloat("topP", 0.9f),
        topK = sp.getInt("topK", 40),
        repeatPenalty = sp.getFloat("repeatPenalty", 1.1f),
        maxTokens = sp.getInt("maxTokens", 512),
        nCtx = sp.getInt("nCtx", 2048),
        threads = sp.getInt("threads", defaultThreads),
        systemPrompt = sp.getString("systemPrompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT,
        webSearch = sp.getBoolean("webSearch", false),
        thinking = sp.getBoolean("thinking", false),
        activeModel = sp.getString("activeModel", "") ?: "",
    )

    fun update(block: (Settings) -> Settings) {
        val v = block(_state.value)
        _state.value = v
        sp.edit()
            .putFloat("temperature", v.temperature)
            .putFloat("topP", v.topP)
            .putInt("topK", v.topK)
            .putFloat("repeatPenalty", v.repeatPenalty)
            .putInt("maxTokens", v.maxTokens)
            .putInt("nCtx", v.nCtx)
            .putInt("threads", v.threads)
            .putString("systemPrompt", v.systemPrompt)
            .putBoolean("webSearch", v.webSearch)
            .putBoolean("thinking", v.thinking)
            .putString("activeModel", v.activeModel)
            .apply()
    }
}
