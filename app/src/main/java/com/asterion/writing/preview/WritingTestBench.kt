package com.asterion.writing.preview

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Private pilot-only benchmarking utilities. No model inference or story library is duplicated. */
data class WritingSampling(
    val temperature: Float,
    val topP: Float,
    val minP: Float,
    val repetition: Float,
    val seed: Int
) {
    fun validate() {
        require(temperature.isFinite() && temperature in 0.05f..2.0f) { "Temperature must be 0.05–2.0" }
        require(topP.isFinite() && topP in 0.1f..1f) { "Top-p must be 0.1–1.0" }
        require(minP.isFinite() && minP >= 0f && minP < 1f) { "Min-p must be 0.0–0.99" }
        require(repetition.isFinite() && repetition in 1f..2f) { "Repeat penalty must be 1.0–2.0" }
        require(seed >= -1) { "Seed must be -1 (random) or nonnegative" }
    }

    companion object {
        fun from(p: SharedPreferences): WritingSampling = WritingSampling(
            p.getString("temperature", "0.80")!!.toFloatOrNull() ?: error("Invalid temperature"),
            p.getString("top_p", "0.90")!!.toFloatOrNull() ?: error("Invalid top-p"),
            p.getString("min_p", "0.05")!!.toFloatOrNull() ?: error("Invalid min-p"),
            p.getString("repetition", "1.12")!!.toFloatOrNull() ?: error("Invalid repetition penalty"),
            p.getString("seed", "42")!!.toIntOrNull() ?: error("Invalid seed")
        ).also { it.validate() }
    }

    fun toJson(): JSONObject = JSONObject().put("temperature", temperature.toDouble())
        .put("top_p", topP.toDouble()).put("min_p", minP.toDouble())
        .put("repeat_penalty", repetition.toDouble()).put("seed", seed)
}

object WritingTestBench {
    val STANDARD_BRIEF = "Write an original fantasy opening for adults (400–600 words). " +
        "Begin with an irreversible choice by two morally complicated characters. " +
        "Invent one unusual supernatural rule with immediate social consequences, " +
        "reveal the stakes through action and natural dialogue, and avoid generic fantasy " +
        "names, exposition, prophecies, and repetitive sentences. Write only prose."

    val SYSTEM_PROMPT = "You are a specialist original-fiction production engine. " +
        "Follow the explicitly selected authoring mode. For chapter prose, write directly in " +
        "the story with distinct characterization, causal consequences and natural dialogue. " +
        "For worldbuilding, character design or plotting, produce usable creative notes. " +
        "For continuity critique, identify contradictions without inventing canon. " +
        "Preserve original creative identity; never copy source works, and never emit generic assistant chatter."

    fun checkpoint(context: Context, text: String) {
        val directory = File(context.filesDir, "writer-drafts").apply { mkdirs() }
        val next = File(directory, "active.txt.tmp")
        next.writeText(text, Charsets.UTF_8)
        val current = File(directory, "active.txt")
        check(next.renameTo(current)) { "Could not save chapter checkpoint" }
    }

    fun readCheckpoint(context: Context): String {
        val file = File(context.filesDir, "writer-drafts/active.txt")
        return if (file.isFile) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    /** Never destroy the previous draft when starting a fresh chapter trial. */
    fun archiveDraft(context: Context, text: String) {
        if (text.isBlank()) return
        val directory = File(context.filesDir, "writer-drafts").apply { mkdirs() }
        val file = File(directory, "draft-" + System.currentTimeMillis() + ".txt")
        file.writeText(text, Charsets.UTF_8)
    }

    fun deviceMetrics(context: Context): JSONObject {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo()
        activity.getMemoryInfo(memory)
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val thermal = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return JSONObject()
            .put("available_ram_bytes", memory.availMem)
            .put("total_ram_bytes", memory.totalMem)
            .put("low_memory", memory.lowMemory)
            .put("battery_percent", battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("thermal_status", thermal.currentThermalStatus)
            .put("sampled_at_ms", System.currentTimeMillis())
    }

    /** A run is stored privately, never in a public GitHub repository or remote inference service. */
    fun saveRun(
        context: Context, model: String, brief: String, text: String, sampling: WritingSampling,
        contextTokens: Int, newTokensPerPass: Int, segments: Int, elapsedMs: Long,
        firstTextMs: Long, streamChunks: Int, status: String, findings: Int,
        startMetrics: JSONObject? = null
    ): JSONObject {
        val folder = File(context.filesDir, "writing-tests").apply { mkdirs() }
        val id = System.currentTimeMillis().toString()
        val output = File(folder, id + ".txt")
        output.writeText(text, Charsets.UTF_8)
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val obj = JSONObject()
            .put("id", id).put("model", model).put("status", status)
            .put("settings", sampling.toJson()).put("context_tokens", contextTokens)
            .put("new_tokens_per_pass", newTokensPerPass).put("segments", segments)
            .put("elapsed_ms", elapsedMs).put("first_text_ms", firstTextMs)
            .put("stream_chunks", streamChunks).put("words", words)
            .put("characters", text.length).put("structural_flags", findings)
            .put("brief_sha256", sha(brief)).put("output_sha256", sha(text))
            .put("chars_per_sec", if (elapsedMs > 0) text.length * 1000.0 / elapsedMs else 0.0)
            .put("backend", "CPU") // Native backend selected by this pilot's inherited engine
            .put("device_start", startMetrics)
            .put("device_end", deviceMetrics(context))
        File(folder, "results.jsonl").appendText(obj.toString() + "\n", Charsets.UTF_8)
        return obj
    }

    fun recent(context: Context, limit: Int = 20): List<JSONObject> {
        val file = File(File(context.filesDir, "writing-tests"), "results.jsonl")
        if (!file.isFile) return emptyList()
        return file.readLines().takeLast(limit).asReversed().mapNotNull {
            runCatching { JSONObject(it) }.getOrNull()
        }
    }

    val QUALITY_AXES = listOf(
        "Originality", "Prose", "Characterization", "Dialogue",
        "Causality", "Continuity", "Pacing", "Engagement"
    )

    /** Human assessment of actual prose, never a fabricated model quality score. */
    fun rateRun(context: Context, runId: String, scores: Map<String, Int>) {
        require(runId.matches(Regex("[0-9]{10,20}"))) { "Invalid benchmark run ID" }
        require(recent(context, 200).any { it.optString("id") == runId }) { "Unknown benchmark result" }
        require(scores.keys.containsAll(QUALITY_AXES))
        require(scores.values.all { it in 1..5 })
        val folder = File(context.filesDir, "writing-tests").apply { mkdirs() }
        val j = JSONObject().put("id", runId).put("at", System.currentTimeMillis())
        val axes = JSONObject()
        QUALITY_AXES.forEach { axes.put(it, scores[it] ?: error("Missing axis")) }
        j.put("scores", axes)
        File(folder, "human-ratings.jsonl").appendText(j.toString() + "\n", Charsets.UTF_8)
    }

    fun rating(context: Context, runId: String): JSONObject? {
        val file = File(File(context.filesDir, "writing-tests"), "human-ratings.jsonl")
        if (!file.isFile) return null
        return file.useLines { lines -> lines.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.optString("id") == runId }.lastOrNull() }
    }

    fun exportSummary(context: Context): String = buildString {
        appendLine("Asterion Writing — private device benchmark summary")
        appendLine("No output text included")
        recent(context, 30).forEach { obj ->
            appendLine(obj.optString("id") + " | " + obj.optString("model") +
                " | " + obj.optString("status") + " | " + obj.optInt("words") + " words | " +
                obj.optLong("elapsed_ms") + " ms | " +
                obj.optDouble("chars_per_sec").toInt() + " chars/s")
        }
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
