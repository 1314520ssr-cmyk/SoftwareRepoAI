package com.localai.chat

data class Turn(val user: Boolean, val text: String)

/** 按模型家族拼提示词。Qwen 用 ChatML；DeepSeek-R1 蒸馏版用它自己的 User/Assistant 标记。 */
object PromptBuilder {
    private const val BAR = "\uFF5C"   // 全角竖线
    private const val SP = "\u2581"    // 下划线块
    private val DS_BOS = "<${BAR}begin${SP}of${SP}sentence${BAR}>"
    private val DS_EOS = "<${BAR}end${SP}of${SP}sentence${BAR}>"
    private val DS_USER = "<${BAR}User${BAR}>"
    private val DS_ASSISTANT = "<${BAR}Assistant${BAR}>"
    private const val EMPTY_THINK = "<think>\n\n</think>\n\n"

    fun build(family: Family, system: String, turns: List<Turn>, thinking: Boolean): String {
        val sb = StringBuilder()
        if (family == Family.DEEPSEEK) {
            sb.append(DS_BOS)
            turns.forEachIndexed { i, t ->
                if (t.user) {
                    // DeepSeek 建议不用 system 提示，指令并入第一条用户消息
                    val text = if (i == 0 && system.isNotBlank()) system.trim() + "\n\n" + t.text else t.text
                    sb.append(DS_USER).append(text)
                } else {
                    sb.append(DS_ASSISTANT).append(t.text).append(DS_EOS)
                }
            }
            sb.append(DS_ASSISTANT)
            sb.append(if (thinking) "<think>\n" else EMPTY_THINK)
        } else {
            if (system.isNotBlank()) sb.append("<|im_start|>system\n").append(system.trim()).append("<|im_end|>\n")
            turns.forEach { t ->
                sb.append(if (t.user) "<|im_start|>user\n" else "<|im_start|>assistant\n")
                    .append(t.text).append("<|im_end|>\n")
            }
            sb.append("<|im_start|>assistant\n")
            if (family == Family.QWEN3 && !thinking) sb.append(EMPTY_THINK)
        }
        return sb.toString()
    }
}

/** 把流式输出拆成「思考过程」和「正式回答」。 */
object ThinkParser {
    private const val OPEN = "<think>"
    private const val CLOSE = "</think>"

    fun split(raw: String, startsInThink: Boolean): Pair<String, String> {
        val close = raw.indexOf(CLOSE)
        if (close >= 0) {
            val thinking = raw.substring(0, close).removePrefix(OPEN).trim()
            val answer = raw.substring(close + CLOSE.length).trimStart()
            return thinking to answer
        }
        val t = raw.trimStart()
        if (t.length < OPEN.length && OPEN.startsWith(t)) return "" to ""   // 标签还没输出完整
        if (startsInThink || t.startsWith(OPEN)) return t.removePrefix(OPEN).trim() to ""
        return "" to raw
    }
}
