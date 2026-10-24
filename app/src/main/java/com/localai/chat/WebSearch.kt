package com.localai.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLDecoder

data class Source(val index: Int, val title: String, val url: String, val text: String)

/**
 * 联网搜索模块（独立封装，方便以后换成 SearXNG 等）。
 * 只做两件事：搜索 + 抓正文。回答仍然由本地模型生成。
 */
object WebSearch {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    suspend fun search(query: String, fetchPages: Int = 2): List<Source> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/")
            .userAgent(UA).timeout(10_000).data("q", query).post()

        val hits = doc.select("div.result").mapNotNull { el ->
            val a = el.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = resolve(a.attr("href"))
            if (!url.startsWith("http") || "duckduckgo.com" in url) return@mapNotNull null
            Triple(a.text(), url, el.selectFirst(".result__snippet")?.text().orEmpty())
        }.take(4)

        coroutineScope {
            hits.mapIndexed { i, (title, url, snippet) ->
                async {
                    val body = if (i < fetchPages) runCatching { fetchText(url) }.getOrNull() else null
                    Source(i + 1, title, url, (body?.takeIf { it.length > 80 } ?: snippet).take(700))
                }
            }.awaitAll()
        }.filter { it.text.isNotBlank() }
            .mapIndexed { i, s -> s.copy(index = i + 1) }
    }

    /** 把资料和问题合成给小模型的提示；资料总长度受限，否则小模型会变慢甚至崩溃。 */
    fun wrap(question: String, sources: List<Source>): String {
        val sb = StringBuilder("请只根据下面的资料回答问题，并在句末用 [1]、[2] 标注来源；资料里没有答案就直接说没有找到。\n\n资料：\n")
        var budget = 1800
        for (s in sources) {
            val t = s.text.take(budget)
            if (t.isEmpty()) break
            sb.append("[${s.index}] ${s.title}\n$t\n\n")
            budget -= t.length
        }
        sb.append("问题：").append(question)
        return sb.toString()
    }

    private fun resolve(raw: String): String {
        val m = Regex("[?&]uddg=([^&]+)").find(raw)
        val u = if (m != null) URLDecoder.decode(m.groupValues[1], "UTF-8") else raw
        return if (u.startsWith("//")) "https:$u" else u
    }

    private fun fetchText(url: String): String {
        val d = Jsoup.connect(url).userAgent(UA).timeout(8_000).maxBodySize(500_000).get()
        d.select("script,style,nav,footer,header,aside,form").remove()
        val paras = d.select("p").map { it.text().trim() }.filter { it.length > 30 }
        return paras.joinToString(" ").ifBlank { d.body().text() }
    }
}
