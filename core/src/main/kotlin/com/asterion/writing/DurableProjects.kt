package com.asterion.writing

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64

/** A durable metadata projection. The append-only journal is authoritative, not an in-memory catalog. */
data class ProjectSnapshot(val catalog: StoryCatalog, val canon: CanonLedger, val eventCount: Long)

class DurableProjects(private val journal: ProjectJournal) {
    fun snapshot(): ProjectSnapshot = reconstruct(journal.read())

    fun putUniverse(value: Universe) = commit("universe.put", listOf(value.id, value.title, value.premise)) { it.catalog.putUniverse(value) }
    fun putSeries(value: Series) = commit("series.put", listOf(value.id, value.universeId, value.title)) { it.catalog.putSeries(value) }
    fun putVolume(value: Volume) = commit("volume.put", listOf(value.id, value.seriesId, value.number.toString(), value.title)) { it.catalog.putVolume(value) }
    fun putChapter(value: Chapter) = commit("chapter.put", listOf(value.id, value.volumeId, value.number.toString(), value.title, value.nexusContentId.orEmpty())) { it.catalog.putChapter(value) }
    fun linkChapter(chapterId: String, nexusContentId: String) = commit("chapter.link", listOf(chapterId, nexusContentId)) { it.catalog.linkChapter(chapterId, nexusContentId) }
    fun establish(fact: CanonFact) = commit("canon.establish", factFields(fact)) { it.canon.establish(fact) }
    fun transition(fact: CanonFact, expectedPreviousValue: String) = commit("canon.transition", factFields(fact) + expectedPreviousValue) {
        it.canon.transition(fact, expectedPreviousValue)
    }
    fun reveal(value: Reveal) = commit("canon.reveal", listOf(value.entityId, value.factAttribute, value.characterId, value.atTick.toString(), value.knownValue)) {
        it.canon.recordReveal(value)
    }

    /** Journal mutation is allowed only after validating a fresh replay against the candidate event. */
    private fun commit(type: String, fields: List<String>, check: (ProjectSnapshot) -> Unit) {
        val before = snapshot()
        check(before)
        journal.append(type, encode(fields))
    }

    private fun reconstruct(read: JournalRead): ProjectSnapshot {
        if (read.truncatedTail) throw IOException("Incomplete project journal. Explicit recovery required.")
        val catalog = StoryCatalog()
        val canon = CanonLedger()
        for (record in read.records) {
            val fields = decode(record.payload)
            when (record.type) {
                "universe.put" -> { expect(fields, 3); catalog.putUniverse(Universe(fields[0], fields[1], fields[2])) }
                "series.put" -> { expect(fields, 3); catalog.putSeries(Series(fields[0], fields[1], fields[2])) }
                "volume.put" -> { expect(fields, 4); catalog.putVolume(Volume(fields[0], fields[1], fields[2].toInt(), fields[3])) }
                "chapter.put" -> { expect(fields, 5); catalog.putChapter(Chapter(fields[0], fields[1], fields[2].toInt(), fields[3], fields[4].ifEmpty { null })) }
                "chapter.link" -> { expect(fields, 2); catalog.linkChapter(fields[0], fields[1]) }
                "canon.establish" -> { expect(fields, 6); canon.establish(readFact(fields)) }
                "canon.transition" -> { expect(fields, 7); canon.transition(readFact(fields), fields[6]) }
                "canon.reveal" -> { expect(fields, 5); canon.recordReveal(Reveal(fields[0], fields[1], fields[2], fields[3].toLong(), fields[4])) }
                else -> throw IOException("Unknown authoritative event type ${record.type} at ${record.sequence}")
            }
        }
        return ProjectSnapshot(catalog, canon, read.records.size.toLong())
    }

    private fun factFields(f: CanonFact): List<String> = listOf(f.entityId, f.attribute, f.value, f.fromTick.toString(), f.reason, f.introducedAtChapter)
    private fun readFact(f: List<String>) = CanonFact(f[0], f[1], f[2], f[3].toLong(), f[4], f[5])
    private fun expect(fields: List<String>, length: Int) { if (fields.size != length) throw IOException("Invalid authoritative event field count") }

    /** Versioned URL-safe base64 field framing; no third-party JSON parser or delimiter ambiguity. */
    private fun encode(fields: List<String>): String = "v1:" + fields.joinToString(".") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
    }
    private fun decode(payload: String): List<String> {
        if (!payload.startsWith("v1:")) throw IOException("Unsupported event schema version")
        return try {
            payload.removePrefix("v1:").split('.').map { String(Base64.getUrlDecoder().decode(it), StandardCharsets.UTF_8) }
        } catch (e: IllegalArgumentException) { throw IOException("Malformed event encoding", e) }
    }
}
