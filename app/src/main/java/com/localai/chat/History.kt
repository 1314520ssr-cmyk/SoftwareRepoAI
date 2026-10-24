package com.localai.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Saved(val id: Long, val title: String, val updated: Long)

/** 历史对话：每个对话一个 json 文件，保存在应用私有目录。 */
class HistoryStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val sp = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    var currentId: Long
        get() = sp.getLong("current", 0L)
        set(v) { sp.edit().putLong("current", v).apply() }

    fun list(): List<Saved> = dir.listFiles().orEmpty().mapNotNull { f ->
        runCatching {
            val o = JSONObject(f.readText())
            Saved(o.getLong("id"), o.getString("title"), o.getLong("updated"))
        }.getOrNull()
    }.sortedByDescending { it.updated }

    fun save(id: Long, messages: List<UiMessage>) {
        val real = messages.filter { it.text.isNotBlank() }
        if (id == 0L || real.none { it.isUser }) return
        val title = real.first { it.isUser }.text.replace("\n", " ").take(24)
        val arr = JSONArray()
        real.forEach { arr.put(JSONObject().put("u", it.isUser).put("t", it.text).put("k", it.thinking)) }
        val o = JSONObject().put("id", id).put("title", title)
            .put("updated", System.currentTimeMillis()).put("msgs", arr)
        File(dir, "$id.json").writeText(o.toString())
    }

    fun load(id: Long): List<UiMessage>? = runCatching {
        val arr = JSONObject(File(dir, "$id.json").readText()).getJSONArray("msgs")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            UiMessage(i + 1L, o.getBoolean("u"), o.getString("t"), o.optString("k", ""))
        }
    }.getOrNull()

    fun delete(id: Long) { File(dir, "$id.json").delete() }

    fun deleteAll() { dir.listFiles()?.forEach { it.delete() } }
}
