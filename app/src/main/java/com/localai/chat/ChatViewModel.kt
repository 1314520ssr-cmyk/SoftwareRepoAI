package com.localai.chat

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    private val store = ModelStore(app)
    private val history = HistoryStore(app)
    private val engine = ChatEngine()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    private val _models = MutableStateFlow(store.list())
    val models: StateFlow<List<ModelInfo>> = _models
    private val _chats = MutableStateFlow(history.list())
    val chats: StateFlow<List<Saved>> = _chats
    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress

    val totalRamBytes: Long = ActivityManager.MemoryInfo().also {
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }.totalMem

    private var genJob: Job? = null
    private var nextId = 1L
    private var family = Family.CHATML
    private var chatId = 0L

    init {
        val saved = settings.state.value.activeModel
        _models.value.firstOrNull { it.name == saved }?.let { loadModel(it) }
        chatId = history.currentId
        val msgs = if (chatId != 0L) history.load(chatId) else null
        if (msgs != null) {
            nextId = (msgs.maxOfOrNull { it.id } ?: 0L) + 1
            _ui.update { it.copy(messages = msgs) }
        } else chatId = 0L
    }

    // ---------- 模型 ----------

    fun loadModel(info: ModelInfo) {
        genJob?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(model = ModelState.Loading(info.name), generating = false, status = null) }
            val s = settings.state.value
            if (engine.load(info.file.absolutePath, s.nCtx, s.threads)) {
                family = info.family
                settings.update { it.copy(activeModel = info.name) }
                _ui.update { it.copy(model = ModelState.Ready(info.name)) }
            } else {
                _ui.update { it.copy(model = ModelState.Failed("加载失败：内存可能不足，或文件已损坏")) }
            }
        }
    }

    fun reloadModel() {
        val name = settings.state.value.activeModel
        _models.value.firstOrNull { it.name == name }?.let { loadModel(it) }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _importProgress.value = 0f
            val result = store.import(uri) { _importProgress.value = it }
            _importProgress.value = null
            _models.value = store.list()
            result.onFailure { e -> _ui.update { it.copy(status = "导入失败：${e.message}") } }
            result.onSuccess { f ->
                if (_ui.value.model !is ModelState.Ready) {
                    _models.value.firstOrNull { it.name == f.name }?.let { loadModel(it) }
                }
            }
        }
    }

    fun deleteModel(info: ModelInfo) {
        viewModelScope.launch {
            if (settings.state.value.activeModel == info.name) {
                genJob?.cancel()
                engine.unload()
                settings.update { it.copy(activeModel = "") }
                _ui.update { it.copy(model = ModelState.None, generating = false) }
            }
            store.delete(info)
            _models.value = store.list()
        }
    }

    // ===== 第二段从下一行开始粘贴 =====
        // ---------- 历史对话 ----------

    private fun saveChat() {
        history.save(chatId, _ui.value.messages)
        _chats.value = history.list()
    }

    private fun clearScreen() {
        chatId = 0L
        history.currentId = 0L
        _ui.update { it.copy(messages = emptyList(), generating = false, status = null) }
    }

    fun newChat() {
        genJob?.cancel()
        saveChat()
        clearScreen()
    }

    fun openChat(id: Long) {
        val msgs = history.load(id) ?: return
        genJob?.cancel()
        saveChat()
        chatId = id
        history.currentId = id
        nextId = (msgs.maxOfOrNull { it.id } ?: 0L) + 1
        _ui.update { it.copy(messages = msgs, generating = false, status = null) }
    }

    fun deleteChat(id: Long) {
        history.delete(id)
        if (id == chatId) { genJob?.cancel(); clearScreen() }
        _chats.value = history.list()
    }

    fun deleteAllChats() {
        genJob?.cancel()
        history.deleteAll()
        clearScreen()
        _chats.value = history.list()
    }

    // ---------- 对话 ----------

    fun stop() { genJob?.cancel() }

    /** 返回 true 表示消息已发出（输入框可以清空）。 */
    fun send(text: String): Boolean {
        val q = text.trim()
        if (q.isEmpty() || _ui.value.generating || _ui.value.model !is ModelState.Ready) return false
        if (chatId == 0L) { chatId = System.currentTimeMillis(); history.currentId = chatId }

        val s = settings.state.value
        val fam = family
        val past = _ui.value.messages.filter { it.text.isNotBlank() }.map { Turn(it.isUser, it.text) }
        val botId = nextId + 1
        _ui.update {
            it.copy(
                messages = it.messages + UiMessage(nextId, true, q) + UiMessage(botId, false, "", streaming = true),
                generating = true, status = null,
            )
        }
        nextId += 2
        startService()

        genJob = viewModelScope.launch {
            try {
                runGeneration(
                    engine, q, past, fam, s,
                    onStatus = { st -> if (st != null || _ui.value.status != null) _ui.update { it.copy(status = st) } },
                    onSources = { src -> updateBot(botId) { it.copy(sources = src) } },
                    onText = { t, a -> updateBot(botId) { it.copy(thinking = t, text = a) } },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateBot(botId) { it.copy(text = it.text.ifEmpty { "生成失败：${e.message}" }) }
            } finally {
                updateBot(botId) { it.copy(streaming = false) }
                _ui.update { it.copy(generating = false, status = null) }
                saveChat()
                stopService()
            }
        }
        return true
    }

    private fun updateBot(id: Long, block: (UiMessage) -> UiMessage) {
        _ui.update { st -> st.copy(messages = st.messages.map { if (it.id == id) block(it) else it }) }
    }

    private fun startService() {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, InferenceService::class.java)) }
    }

    private fun stopService() {
        runCatching { app.stopService(Intent(app, InferenceService::class.java)) }
    }
}
