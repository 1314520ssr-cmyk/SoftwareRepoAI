#include <algorithm>
#include "jni_state.h"

// 返回 s 中由完整 UTF-8 字符组成的最长前缀长度
static size_t utf8_complete_len(const std::string &s) {
    size_t n = s.size(), i = n;
    int back = 0;
    while (i > 0 && back < 4) {
        i--;
        unsigned char c = (unsigned char) s[i];
        if ((c & 0xC0) == 0x80) { back++; continue; }
        size_t need = c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : c >= 0xC0 ? 2 : 1;
        return (n - i >= need) ? n : i;
    }
    return n;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_localai_chat_LlamaBridge_nativeGenerate(JNIEnv *env, jobject, jbyteArray jprompt, jint maxTokens,
                                                  jfloat temp, jfloat topP, jint topK, jfloat repeatPenalty,
                                                  jobject callback) {
    EngineState &g = g_engine;
    if (!g.model || !g.ctx) return -1;
    g_stop = false;

    const llama_vocab *vocab = llama_model_get_vocab(g.model);
    std::vector<llama_token> tokens = tokenize(vocab, bytes_to_string(env, jprompt));
    if (tokens.empty()) return -2;
    if ((int) tokens.size() >= g.n_ctx - 8) return -3;

    jclass cls = env->FindClass("com/localai/chat/TokenCallback");
    jmethodID mid = cls ? env->GetMethodID(cls, "onToken", "([B)Z") : nullptr;
    if (!mid) return -4;

    llama_memory_clear(llama_get_memory(g.ctx), true);
    for (size_t i = 0; i < tokens.size(); i += (size_t) g.n_batch) {
        if (g_stop) return 0;
        int cnt = (int) std::min<size_t>((size_t) g.n_batch, tokens.size() - i);
        llama_batch b = llama_batch_get_one(tokens.data() + i, cnt);
        if (llama_decode(g.ctx, b) != 0) return -5;
    }

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temp <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    auto emit = [&](const std::string &s) -> bool {
        jbyteArray arr = env->NewByteArray((jsize) s.size());
        env->SetByteArrayRegion(arr, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
        jboolean go = env->CallBooleanMethod(callback, mid, arr);
        env->DeleteLocalRef(arr);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
        return go == JNI_TRUE;
    };

    std::string pending;
    int n_cur = (int) tokens.size(), generated = 0;
    bool keep = true;
    while (keep && generated < maxTokens && n_cur < g.n_ctx - 1 && !g_stop) {
        llama_token tok = llama_sampler_sample(smpl, g.ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) pending.append(buf, (size_t) len);
        size_t ok = utf8_complete_len(pending);
        if (ok > 0) {
            keep = emit(pending.substr(0, ok));
            pending.erase(0, ok);
        }
        generated++;
        n_cur++;
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(g.ctx, b) != 0) break;
    }
    llama_sampler_free(smpl);
    return generated;
}
