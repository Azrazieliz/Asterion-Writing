package com.asterion.writing.preview

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Pilot-local manuscript workspace. Nexus remains the authority when its adapter is available. */
data class WriterWorkspace(
    val id: String,
    val universe: String,
    val series: String,
    val volume: String,
    val chapter: String,
    val premise: String,
    val cast: String,
    val canon: String,
    val sceneObjective: String,
    val chapterPlan: String,
    val profile: String,
    val parentChapterId: String = "",
    val chapterIndex: Int = 1,
    val acceptedOutcome: String = "",
    val approved: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
class WriterWorkspaceStore(private val context: Context) {
    private val root = File(context.filesDir, "writing-workspaces").apply { mkdirs() }
    private val index = File(root, "workspaces.json")
    private fun safeId(id: String): String {
        require(id.matches(Regex("[a-zA-Z0-9-]{8,70}"))) { "Invalid workspace identity" }
        return id
    }
    private fun workspaceDir(id: String): File =
        File(root, safeId(id)).apply { mkdirs() }
    private fun draftFile(id: String) = File(workspaceDir(id), "chapter-draft.txt")
    private fun atomicallyWrite(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".new")
        FileOutputStream(temp).use { out -> out.write(bytes); out.fd.sync() }
        check(temp.renameTo(file)) { "Could not persist writing state" }
    }
    private fun json(w: WriterWorkspace): JSONObject = JSONObject()
        .put("id", w.id).put("universe", w.universe).put("series", w.series)
        .put("volume", w.volume).put("chapter", w.chapter).put("premise", w.premise)
        .put("cast", w.cast).put("canon", w.canon).put("sceneObjective", w.sceneObjective)
        .put("chapterPlan", w.chapterPlan).put("profile", w.profile)
        .put("parentChapterId", w.parentChapterId)
        .put("chapterIndex", w.chapterIndex).put("acceptedOutcome", w.acceptedOutcome)
        .put("approved", w.approved).put("updatedAt", w.updatedAt)
    private fun parse(j: JSONObject): WriterWorkspace = WriterWorkspace(
        j.getString("id"), j.optString("universe"), j.optString("series"),
        j.optString("volume"), j.optString("chapter"), j.optString("premise"),
        j.optString("cast"), j.optString("canon"), j.optString("sceneObjective"),
        j.optString("chapterPlan"), j.optString("profile"),
        j.optString("parentChapterId"), j.optInt("chapterIndex", 1),
        j.optString("acceptedOutcome"),
        j.optBoolean("approved"), j.optLong("updatedAt"))
    @Synchronized fun list(): List<WriterWorkspace> {
        if (!index.isFile) return emptyList()
        val j = JSONArray(index.readText(Charsets.UTF_8))
        return (0 until j.length()).map { parse(j.getJSONObject(it)) }
            .sortedByDescending { it.updatedAt }
    }
    @Synchronized fun get(id: String): WriterWorkspace? = list().firstOrNull { it.id == id }
    @Synchronized fun create(
        universe: String, series: String, volume: String, chapter: String,
        premise: String, cast: String, canon: String, objective: String,
        plan: String = "", profile: String = "",
        parentChapterId: String = "", chapterIndex: Int = 1
    ): WriterWorkspace {
        require(universe.isNotBlank() && chapter.isNotBlank() && premise.isNotBlank()) {
            "Enter a universe, chapter title and original premise"
        }
        require(chapterIndex >= 1)
        val w = WriterWorkspace(UUID.randomUUID().toString(), universe, series, volume, chapter,
            premise, cast, canon, objective, plan, profile, parentChapterId, chapterIndex)
        val others = list().toMutableList()
        others += w
        atomicallyWrite(index, JSONArray(others.map { json(it) }).toString().toByteArray(Charsets.UTF_8))
        return w
    }
    @Synchronized fun update(w: WriterWorkspace): WriterWorkspace {
        val previous = get(w.id) ?: error("Workspace no longer exists")
        require(w.universe.isNotBlank() && w.chapter.isNotBlank() && w.premise.isNotBlank())
        // Accepted chapters cannot silently change their authoritative metadata or prose.
        require(!previous.approved || previous.copy(updatedAt = w.updatedAt) == w) {
            "Accepted chapter is immutable; create a new chapter revision"
        }
        val next = w.copy(updatedAt = System.currentTimeMillis())
        atomicallyWrite(index, JSONArray(list().map { if (it.id == w.id) json(next) else json(it) })
            .toString().toByteArray(Charsets.UTF_8))
        return next
    }
    @Synchronized fun draft(id: String): String {
        requireNotNull(get(id))
        val file = draftFile(id)
        return if (file.isFile) file.readText(Charsets.UTF_8) else ""
    }
    @Synchronized fun autosave(id: String, text: String) {
        val w = requireNotNull(get(id)) { "Workspace no longer exists" }
        require(!w.approved) { "Accepted chapter cannot be changed" }
        atomicallyWrite(draftFile(id), text.toByteArray(Charsets.UTF_8))
    }
    @Synchronized fun saveRevision(id: String, text: String): String {
        val w = requireNotNull(get(id))
        require(!w.approved) { "Accepted chapter cannot be changed" }
        val old = draft(id)
        if (old == text) return "Unchanged"
        val revisions = File(workspaceDir(id), "revisions").apply { mkdirs() }
        if (old.isNotBlank()) {
            val hash = MessageDigest.getInstance("SHA-256").digest(old.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(12)
            val backup = File(revisions, System.currentTimeMillis().toString() + "-" + hash + ".txt")
            atomicallyWrite(backup, old.toByteArray(Charsets.UTF_8))
        }
        autosave(id, text)
        return "Saved"
    }
    @Synchronized fun revisions(id: String): List<File> {
        requireNotNull(get(id))
        return File(workspaceDir(id), "revisions").listFiles()
            ?.filter { it.isFile && it.extension == "txt" }?.sortedByDescending { it.name } ?: emptyList()
    }
    /** Explicit local editorial lock. Publication to Nexus requires a separate real adapter and receipt. */
    @Synchronized fun approve(id: String, acceptedOutcome: String): WriterWorkspace {
        val w = requireNotNull(get(id))
        require(draft(id).trim().isNotEmpty()) { "Cannot approve an empty chapter" }
        require(acceptedOutcome.isNotBlank()) { "Record the accepted chapter outcome first" }
        return update(w.copy(approved = true, acceptedOutcome = acceptedOutcome))
    }
    /** Returns only explicitly accepted prior outcomes. Detects broken chains and cycles. */
    @Synchronized fun acceptedHistory(id: String): List<String> {
        val seen = mutableSetOf<String>()
        val outcomes = mutableListOf<String>()
        var current = get(id)
        while (current?.parentChapterId?.isNotBlank() == true) {
            require(seen.add(current.id)) { "Chapter chain contains a cycle" }
            val parent = requireNotNull(get(current.parentChapterId)) { "Missing previous chapter" }
            require(parent.approved && parent.acceptedOutcome.isNotBlank()) {
                "Previous chapter needs accepted editorial outcome"
            }
            outcomes += "Chapter ${parent.chapterIndex} — ${parent.acceptedOutcome}"
            current = parent
        }
        return outcomes.asReversed()
    }
    @Synchronized fun nextChapter(id: String): WriterWorkspace {
        val w = requireNotNull(get(id))
        require(w.approved && w.acceptedOutcome.isNotBlank()) {
            "Approve the previous chapter and its consequences before continuing the series"
        }
        val next = create(w.universe, w.series, w.volume,
            "Chapter ${w.chapterIndex + 1}", w.premise, w.cast,
            w.canon + "\nAccepted chapter ${w.chapterIndex} consequence: " + w.acceptedOutcome,
            "Advance the next conflict", w.chapterPlan, w.profile, w.id, w.chapterIndex + 1)
        return next
    }
    @Synchronized fun fork(id: String): WriterWorkspace {
        val original = requireNotNull(get(id))
        val next = create(original.universe, original.series, original.volume,
            original.chapter + " — revision", original.premise, original.cast,
            original.canon, original.sceneObjective, original.chapterPlan, original.profile,
            original.parentChapterId, original.chapterIndex)
        autosave(next.id, draft(id))
        return next
    }
}
