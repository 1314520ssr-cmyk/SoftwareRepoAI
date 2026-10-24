package com.localai.chat

data class UiMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val thinking: String = "",
    val sources: List<Source> = emptyList(),
    val streaming: Boolean = false,
)

sealed interface ModelState {
    data object None : ModelState
    data class Loading(val name: String) : ModelState
    data class Ready(val name: String) : ModelState
    data class Failed(val reason: String) : ModelState
}

data class UiState(
    val messages: List<UiMessage> = emptyList(),
    val generating: Boolean = false,
    val model: ModelState = ModelState.None,
    val status: String? = null,
)
