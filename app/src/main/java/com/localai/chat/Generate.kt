package com.localai.chat

import kotlinx.coroutines.CancellationException

/** 一次完整的生成流程：可选联网搜索 -> 拼提示词并裁剪历史 -> 流式生成。 */
suspend fun runGeneration(
    engine: ChatEngine,
    q: String,
    past: List<Turn>,
    family: Family,
    s: Settings,
    onStatus: (String?) -> Unit,
    onSources: (List<Source>) -> Unit,
    onText: (thinking: String, answer: String) -> Unit,
) {
    var sources = emptyList<Source>()
    if (s.webSearch) {
        onStatus("正在联网搜索…")
        try {
            sources = WebSearch.search(q)
            if (sources.isEmpty()) onStatus("没有搜到结果，已改为离线回答")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onStatus("联网失败，已改为离线回答")
        }
        if (sources.isNotEmpty()) onSources(sources)
    }

    val think = s.thinking && family.supportsThinking
    val maxTok = (if (think) s.maxTokens * 3 else s.maxTokens).coerceAtMost(s.nCtx / 2)
    val budget = s.nCtx - maxTok
    val userText = if (sources.isNotEmpty()) WebSearch.wrap(q, sources) else q

    var turns = past + Turn(true, userText)
    var prompt = PromptBuilder.build(family, s.systemPrompt, turns, think)
    // 上下文放不下时，从最早的对话开始丢
    while (turns.size > 1 && (!turns.first().user || engine.countTokens(prompt) > budget)) {
        turns = turns.drop(1)
        prompt = PromptBuilder.build(family, s.systemPrompt, turns, think)
    }
    if (engine.countTokens(prompt) > budget && sources.isNotEmpty()) {
        prompt = PromptBuilder.build(family, s.systemPrompt, listOf(Turn(true, q)), think)
    }
    if (engine.countTokens(prompt) > budget) {
        onText("", "输入太长，请缩短，或在设置里调大上下文长度。")
        return
    }

    onStatus("正在思考…")
    val raw = StringBuilder()
    val startsInThink = think && family == Family.DEEPSEEK
    val params = GenParams(maxTok, s.temperature, s.topP, s.topK, s.repeatPenalty)
    engine.generate(prompt, params).collect { piece ->
        raw.append(piece)
        val (t, a) = ThinkParser.split(raw.toString(), startsInThink)
        onText(t, a)
        onStatus(null)
    }
}
