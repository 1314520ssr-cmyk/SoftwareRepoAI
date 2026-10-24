#pragma once
#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include "llama.h"

struct EngineState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    int n_ctx = 0;
    int n_batch = 512;
};
extern EngineState g_engine;
extern std::atomic<bool> g_stop;

inline std::string bytes_to_string(JNIEnv *env, jbyteArray arr) {
    jsize n = env->GetArrayLength(arr);
    std::string s((size_t) n, '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(&s[0]));
    return s;
}

inline std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text) {
    int n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, false, true);
    if (n <= 0) return {};
    std::vector<llama_token> out((size_t) n);
    int r = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), out.data(), n, false, true);
    if (r < 0) return {};
    out.resize((size_t) r);
    return out;
}
