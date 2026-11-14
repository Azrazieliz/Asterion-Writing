package com.asterion.writing.preview

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ModelFile(val repo: String, val path: String, val bytes: Long, val sha256: String?) {
    val label: String get() = "${path.substringAfterLast('/')} • ${"%.2f".format(bytes / 1e9)} GB" 
}

/** Public Hugging Face metadata only. Model weights are never sent to inference providers. */
object HuggingFaceCatalog {
    val presets = linkedMapOf(
        "Creative Writer 3B" to "mradermacher/Llama3.2-3B-creative-writer-v0.1-GGUF",
        "Creative 3B" to "mradermacher/Llama-3.2-3B-Creative-GGUF",
        "Stheno 8B" to "tensorblock/L3-8B-Stheno-v3.2-GGUF",
        "Magnum 4B" to "bartowski/magnum-v2-4b-GGUF",
        "Lumimaid 8B" to "NeverSleep/Lumimaid-v0.2-8B-GGUF"
    )

    fun normalizeRepo(input: String): String {
        var s = input.trim().removePrefix("https://huggingface.co/").removePrefix("http://huggingface.co/")
            .substringBefore('?').trim('/')
        if (s.contains("/tree/")) s = s.substringBefore("/tree/")
        if (s.contains("/blob/")) s = s.substringBefore("/blob/")
        require(s.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Enter a Hugging Face repository in owner/name format" }
        return s
    }

    fun ggufs(repoInput: String): List<ModelFile> {
        val repo = normalizeRepo(repoInput)
        val url = "https://huggingface.co/api/models/${repo}/tree/main?recursive=1&expand=true"
        val data = JSONArray(readText(url))
        return (0 until data.length()).mapNotNull { index ->
            val item = data.getJSONObject(index)
            val path = item.optString("path")
            if (!path.endsWith(".gguf", true) || path.contains("..")) return@mapNotNull null
            val lfs = item.optJSONObject("lfs")
            val size = item.optLong("size", lfs?.optLong("size", 0) ?: 0)
            ModelFile(repo, path, size, lfs?.optString("sha256")?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) })
        }.sortedWith(compareBy<ModelFile> { !it.path.contains("Q4_K_M", true) }.thenBy { it.bytes })
    }

    fun search(term: String): List<String> {
        require(term.length in 2..100)
        val url = "https://huggingface.co/api/models?search=${URLEncoder.encode(term, "UTF-8")}&filter=gguf&limit=25"
        val arr = JSONArray(readText(url))
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
            .filter { it.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) }
    }

    private fun readText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "AsterionWriting/0.1")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Hugging Face HTTP $status (repository may be gated/private)")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}
