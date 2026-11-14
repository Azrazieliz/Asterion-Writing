#!/usr/bin/env python3
"""Patch only CI's pinned Asterion Art checkout; never modify upstream repo."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
def replace(path, old, new):
    p = root / path
    s = p.read_text(encoding="utf-8")
    if s.count(old) != 1:
        raise RuntimeError(f"Native writing patch anchor mismatch: {path}: {s.count(old)}")
    p.write_text(s.replace(old, new), encoding="utf-8")

replace("src/main/cpp/ai_chat.cpp",
    "constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;",
    "constexpr float DEFAULT_SAMPLER_TEMP    = 0.80f;")

native_old = """static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    return common_sampler_init(g_model, sparams);
}
"""
native_new = native_old + """
// Native writing-specific sampler. Single-threaded dispatcher owns all calls.
// This is applied only between generations, never while decode is active.
extern "C" JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_applySampler(
        JNIEnv *, jobject, jfloat temp, jfloat top_p, jfloat min_p,
        jfloat repetition, jint seed) {
    if (!g_model || !g_context || !g_sampler) return 1;
    if (!std::isfinite(temp) || temp < 0.05f || temp > 2.0f ||
        !std::isfinite(top_p) || top_p < 0.1f || top_p > 1.0f ||
        !std::isfinite(min_p) || min_p < 0.0f || min_p >= 1.0f ||
        !std::isfinite(repetition) || repetition < 1.0f || repetition > 2.0f ||
        seed < -1) return 2;
    common_params_sampling params;
    params.temp = temp;
    params.top_p = top_p;
    params.min_p = min_p;
    params.penalty_repeat = repetition;
    params.penalty_last_n = 128;
    params.n_prev = 128;
    params.seed = seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(seed);
    common_sampler * replacement = common_sampler_init(g_model, params);
    if (!replacement) return 3;
    common_sampler_free(g_sampler);
    g_sampler = replacement;
    return 0;
}
"""
replace("src/main/cpp/ai_chat.cpp", native_old, native_new)

interface_old = """    suspend fun loadAdapter(adapterPath: String)
"""
interface_new = interface_old + """
    /** Applies actual native sampling, not a UI-only setting. */
    suspend fun configureSampler(temperature: Float, topP: Float, minP: Float, repeatPenalty: Float, seed: Int)
    /** Reset the conversation and KV history between independent test trials. */
    suspend fun resetSession(systemPrompt: String)
"""
replace("src/main/java/com/arm/aichat/InferenceEngine.kt", interface_old, interface_new)

impl_old = """    @FastNative
    private external fun attachLora(adapterPath: String): Int
"""
impl_new = impl_old + """
    @FastNative
    private external fun applySampler(temperature: Float, topP: Float, minP: Float, repeatPenalty: Float, seed: Int): Int
"""
replace("src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt", impl_old, impl_new)

impl_anchor = """    override suspend fun setSystemPrompt(prompt: String) =
"""
impl_insert = """    override suspend fun configureSampler(
        temperature: Float, topP: Float, minP: Float, repeatPenalty: Float, seed: Int
    ) = withContext(llamaDispatcher) {
        check(_state.value is InferenceEngine.State.ModelReady) { "Sampler requires an idle loaded model" }
        check(applySampler(temperature, topP, minP, repeatPenalty, seed) == 0) {
            "Native sampler rejected requested parameters"
        }
    }

    override suspend fun resetSession(systemPrompt: String) = withContext(llamaDispatcher) {
        require(systemPrompt.isNotBlank())
        check(_state.value is InferenceEngine.State.ModelReady) { "Reset requires an idle loaded model" }
        check(processSystemPrompt(systemPrompt) == 0) { "Failed to reset native conversation history" }
    }

""" + impl_anchor
replace("src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt", impl_anchor, impl_insert)
print("Patched pinned Art GGUF checkout for real configurable writing sampling and clean test resets")
