package com.asterion.writing

import java.io.Closeable
import java.io.File

enum class Accelerator { QNN, VULKAN, CPU }
/** No model names are hardcoded: the user selects a checksum-pinned trained writer. */
data class WriterModel(
    val id: String,
    val checksumSha256: String,
    val preferredAccelerators: List<Accelerator>,
    val minimumAvailableRamBytes: Long,
    val contextTokens: Int
) {
    init {
        require(id.isNotBlank() && checksumSha256.matches(Regex("[0-9a-fA-F]{64}")))
        require(preferredAccelerators.isNotEmpty() && preferredAccelerators.distinct() == preferredAccelerators)
        require(minimumAvailableRamBytes >= 0 && contextTokens > 0)
    }
}

/** Rounder resolves or stages a checksum-pinned model from its existing store; it must never stream weights lazily while decoding. */
interface RounderModelCache {
    fun availableLocalModel(modelId: String): File? // null means not currently staged
    fun requestStaging(modelId: String) // integration implementation, not implicit network inference
}
interface DeviceMemory { fun availableBytes(): Long }
interface NativeWriterRuntime {
    fun supports(model: WriterModel, accelerator: Accelerator): Boolean
    fun open(model: WriterModel, weights: File, accelerator: Accelerator): WritingModelSession
}
interface WritingModelSession : Closeable {
    /** Exact native tokenizer count, never a whitespace-based guess. */
    fun countTokens(text: String): Int
    fun generate(prompt: String, maxNewTokens: Int): String
}

/** At most one author model is active. No hidden fallback to another runtime. */
class SingleModelHost(
    private val rounder: RounderModelCache,
    private val memory: DeviceMemory,
    private val runtime: NativeWriterRuntime
) : Closeable {
    private var activeModel: WriterModel? = null
    private var session: WritingModelSession? = null
    private var activeBackend: Accelerator? = null

    @Synchronized fun load(model: WriterModel): Accelerator {
        if (activeModel == model && session != null) return checkNotNull(activeBackend)
        close()
        require(memory.availableBytes() >= model.minimumAvailableRamBytes) { "Insufficient available RAM; no model loaded" }
        val weights = rounder.availableLocalModel(model.id) ?: run {
            rounder.requestStaging(model.id)
            error("Model is not staged locally; use Rounder to stage before loading")
        }
        require(weights.isFile && weights.canRead()) { "Model file is unavailable" }
        require(sha256(weights).equals(model.checksumSha256, ignoreCase = true)) { "Model checksum mismatch" }
        val backend = model.preferredAccelerators.firstOrNull { runtime.supports(model, it) }
            ?: error("No verified compatible backend for this model")
        session = runtime.open(model, weights, backend)
        activeModel = model
        activeBackend = backend
        return backend
    }

    @Synchronized fun generate(model: WriterModel, prompt: String, maxNewTokens: Int): String {
        require(prompt.isNotBlank() && maxNewTokens in 1..model.contextTokens)
        load(model)
        val current = checkNotNull(session)
        val promptTokens = current.countTokens(prompt)
        require(promptTokens > 0 && promptTokens.toLong() + maxNewTokens <= model.contextTokens) { "Native tokenizer reports context overflow" }
        return current.generate(prompt, maxNewTokens)
    }
    @Synchronized override fun close() {
        try { session?.close() } finally { session = null; activeModel = null; activeBackend = null }
    }
}

/** These boundaries are adapters to existing apps, not new backends or duplicated readers. */
interface NexusWritingStore {
    fun saveDraft(universeId: String, seriesId: String, chapterId: String, text: String, findings: List<QualityFinding>): String
    fun publishApprovedChapter(draftId: String): String
}
