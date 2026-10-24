package com.localai.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Family(val label: String, val supportsThinking: Boolean) {
    QWEN3("Qwen3", true),
    QWEN("Qwen", false),
    DEEPSEEK("DeepSeek", true),
    CHATML("通用", false),
}

fun detectFamily(fileName: String): Family {
    val n = fileName.lowercase()
    return when {
        "deepseek" in n -> Family.DEEPSEEK
        "qwen3" in n || "qwen-3" in n -> Family.QWEN3
        "qwen" in n -> Family.QWEN
        else -> Family.CHATML
    }
}

data class ModelInfo(val file: File) {
    val name: String get() = file.name
    val displayName: String get() = file.nameWithoutExtension
    val sizeBytes: Long get() = file.length()
    val family: Family get() = detectFamily(file.name)
}

/** 模型全部保存在应用私有目录，导入一次后切换不需要再下载。 */
class ModelStore(private val context: Context) {
    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    fun list(): List<ModelInfo> =
        dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
            ?.sortedBy { it.name }
            ?.map { ModelInfo(it) }
            .orEmpty()

    fun delete(info: ModelInfo) = info.file.delete()

    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "model.gguf"
            var size = -1L
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            if (!name.endsWith(".gguf", true)) error("请选择 .gguf 格式的模型文件")
            name = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (size > 0 && dir.usableSpace < size + 200L * 1024 * 1024) error("手机存储空间不足")

            val target = File(dir, name)
            val tmp = File(dir, "$name.part")
            val input = resolver.openInputStream(uri) ?: error("无法读取所选文件")
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (size > 0) {
                            val pct = (done * 100 / size).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) error("保存模型失败")
            target
        }
    }
}
