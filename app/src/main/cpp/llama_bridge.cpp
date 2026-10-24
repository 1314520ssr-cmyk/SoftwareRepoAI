#include <android/log.h>
#include "jni_state.h"

EngineState g_engine;
std::atomic<bool> g_stop{false};
static bool g_ready = false;

static void log_cb(ggml_log_level level, const char *text, void *) {
    int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
             : level == GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    __android_log_write(prio, "LocalAI", text);
}

static void free_all() {
    if (g_engine.ctx) { llama_free(g_engine.ctx); g_engine.ctx = nullptr; }
    if (g_engine.model) { llama_model_free(g_engine.model); g_engine.model = nullptr; }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_localai_chat_LlamaBridge_nativeLoad(JNIEnv *env, jobject, jstring jpath, jint nCtx, jint nThreads) {
    if (!g_ready) {
        llama_log_set(log_cb, nullptr);
        llama_backend_init();
        g_ready = true;
    }
    free_all();
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.use_mmap = true;
    mp.n_gpu_layers = 0;
    g_engine.model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_engine.model) return JNI_FALSE;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nCtx;
    cp.n_batch = (uint32_t) g_engine.n_batch;
    cp.n_ubatch = (uint32_t) g_engine.n_batch;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;
    g_engine.ctx = llama_init_from_model(g_engine.model, cp);
    if (!g_engine.ctx) { free_all(); return JNI_FALSE; }
    g_engine.n_ctx = (int) llama_n_ctx(g_engine.ctx);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_localai_chat_LlamaBridge_nativeUnload(JNIEnv *, jobject) { free_all(); }

JNIEXPORT void JNICALL
Java_com_localai_chat_LlamaBridge_nativeStop(JNIEnv *, jobject) { g_stop = true; }

JNIEXPORT jint JNICALL
Java_com_localai_chat_LlamaBridge_nativeCountTokens(JNIEnv *env, jobject, jbyteArray jtext) {
    if (!g_engine.model) return 0;
    return (jint) tokenize(llama_model_get_vocab(g_engine.model), bytes_to_string(env, jtext)).size();
}

}  // extern "C"
