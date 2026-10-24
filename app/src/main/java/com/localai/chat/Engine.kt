package com.localai.chat

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

fun interface TokenCallback {
    fun onToken(bytes: ByteArray): Boolean
}

object LlamaBridge {
    init { System.loadLibrary("localai") }

    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Boolean
    external fun nativeUnload()
    external fun nativeStop()
    external fun nativeCountTokens(text: ByteArray): Int
    external fun nativeGenerate(
        prompt: ByteArray,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback,
    ): Int
}

data class GenParams(
    val maxTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
)

/** 所有原生调用都在同一条后台线程上串行执行，界面不会被推理卡住。 */
class ChatEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "llama") }.asCoroutineDispatcher()

    suspend fun load(path: String, nCtx: Int, threads: Int): Boolean =
        withContext(dispatcher) { LlamaBridge.nativeLoad(path, nCtx, threads) }

    suspend fun unload() = withContext(dispatcher) { LlamaBridge.nativeUnload() }

    suspend fun countTokens(text: String): Int =
        withContext(dispatcher) { LlamaBridge.nativeCountTokens(text.toByteArray(Charsets.UTF_8)) }

    fun generate(prompt: String, p: GenParams): Flow<String> = callbackFlow {
        launch(dispatcher) {
            val rc = LlamaBridge.nativeGenerate(
                prompt.toByteArray(Charsets.UTF_8),
                p.maxTokens, p.temperature, p.topP, p.topK, p.repeatPenalty,
                TokenCallback { bytes -> trySend(String(bytes, Charsets.UTF_8)).isSuccess },
            )
            close(if (rc < 0) IllegalStateException("推理失败（错误码 $rc）") else null)
        }
        awaitClose { LlamaBridge.nativeStop() }
    }.buffer(Channel.UNLIMITED)
}
