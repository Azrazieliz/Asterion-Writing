package com.asterion.writing

import java.io.File
import java.io.IOException
import java.nio.file.Files

private fun expectFailure(block: () -> Unit) {
    var failed = false
    try { block() } catch (_: IllegalArgumentException) { failed = true }
      catch (_: IllegalStateException) { failed = true }
      catch (_: IOException) { failed = true }
    check(failed) { "Expected failure did not occur" }
}

fun main() {
    val catalog = StoryCatalog()
    catalog.putUniverse(Universe("u1", "First World", "An original cosmology"))
    catalog.putUniverse(Universe("u2", "Second World", "An unrelated cosmology"))
    catalog.putSeries(Series("s1", "u1", "First serial"))
    catalog.putSeries(Series("s2", "u2", "Other serial"))
    catalog.putVolume(Volume("v1", "s1", 1, "First volume"))
    catalog.putChapter(Chapter("c1", "v1", 1, "First chapter", null))
    check(catalog.universeSeries("u1").size == 1)
    check(catalog.universeSeries("u2").single().id == "s2")
    expectFailure { catalog.putVolume(Volume("v2", "s1", 1, "Duplicate")) }
    expectFailure { catalog.putSeries(Series("s3", "missing", "Orphan")) }
    expectFailure { catalog.putChapter(Chapter("c2", "v1", 1, "Duplicate" , null)) }

    val canon = CanonLedger()
    canon.establish(CanonFact("hero", "allegiance", "city", 0, "opening", "c1"))
    expectFailure { canon.establish(CanonFact("hero", "allegiance", "forest", 3, "silent change", "c2")) }
    expectFailure { canon.transition(CanonFact("hero", "allegiance", "forest", 3, "", "c2"), "city") }
    canon.transition(CanonFact("hero", "allegiance", "forest", 3, "joined forest", "c2"), "city")
    check(canon.valueAt("hero", "allegiance", 2) == "city")
    check(canon.valueAt("hero", "allegiance", 4) == "forest")
    check(!canon.characterKnows("rival", "hero", "allegiance", 4))
    canon.recordReveal(Reveal("hero", "allegiance", "rival", 6, "forest"))
    check(!canon.characterKnows("rival", "hero", "allegiance", 5))
    check(canon.characterKnows("rival", "hero", "allegiance", 6))
    canon.transition(CanonFact("hero", "allegiance", "republic", 9, "reformed", "c3"), "forest")
    check(!canon.characterKnows("rival", "hero", "allegiance", 10)) // no automatic knowledge of later change

    val repeated = "The character walked through the ruined city and carefully considered the consequences of the ancient promise."
    val gate = QualityGate(QualityPolicy(minWords = 10))
    check(gate.check("$repeated\n\n$repeated").any { it.code == "REPEATED_PARAGRAPH" })
    check(gate.check("Short words.").any { it.code == "SHORT_DRAFT" })
    val reference = ReferenceShingleIndex(10)
    reference.addReference("At the edge of the forest the travelers watched the silver tower crumble beneath a silent sky.")
    val overlap = "At the edge of the forest the travelers watched the silver tower crumble beneath a silent sky. Everything changed."
    check(gate.check(overlap, reference).any { it.code == "REFERENCE_OVERLAP" })
    check(gate.check("A completely unrelated journey begins in the distant ocean city.", reference).none { it.code == "REFERENCE_OVERLAP" })
    check(gate.check(repeated).any { it.code == "EDITORIAL_REVIEW_REQUIRED" })

    val director = NarrativeController()
    val opening = SceneObjective("opening", "c1", "The bridge", "Force an irreversible choice",
        listOf("Mara", "Sen"), chronology = 1)
    director.putPlanned(opening)
    expectFailure { director.putPlanned(opening) }
    director.markReviewed("opening")
    expectFailure { director.accept("missing", "anything") }
    director.accept("opening", "The bridge was destroyed")
    check(director.chapterScenes("c1").single().status == SceneStatus.ACCEPTED)
    expectFailure { director.makeInstruction(
        ChapterContext("World", "Serial", "Volume", "Opening", "An original city", "",
            "Mara knows the price, Sen does not", "", emptyList()), director.chapterScenes("c1").single(), "", false
    ) }
    val draftScene = SceneObjective("scene2", "c1", "Price", "Confront the consequences",
        listOf("Mara"), chronology = 2)
    val instruction = director.makeInstruction(
        ChapterContext("World", "Serial", "Volume", "Opening", "An original city",
            "Magic consumes memory", "Mara knows the price, Sen does not",
            "Consequences", listOf("The bridge was destroyed")),
        draftScene, "Mara reached the shoreline.", true)
    check("The bridge was destroyed" in instruction)
    check("Mara reached the shoreline" in instruction)
    check("Continue with only new prose" in instruction)
    check(director.reviewChapter("Short.").codes.contains("SHORT_DRAFT"))

    val temp = Files.createTempDirectory("asterion-writing-").toFile()
    try {
        val journal = ProjectJournal(File(temp, "project.journal"))
        check(journal.read().records.isEmpty())
        journal.append("universe.created", "{\"id\":\"u1\"}")
        journal.append("chapter.reviewed", "draft-1")
        check(journal.read().records.size == 2)
        File(temp, "project.journal").appendBytes(byteArrayOf(0x41, 0x57))
        check(journal.read().truncatedTail)
        expectFailure { journal.append("chapter.approved", "draft-1") }
        check(journal.recoverTruncatedTail() == 2L)
        check(File(temp, "project.journal.truncated.bak").exists())
        journal.append("chapter.approved", "draft-1")
        check(journal.read().records.size == 3)
        val persisted = DurableProjects(ProjectJournal(File(temp, "durable.journal")))
        persisted.putUniverse(Universe("orig-universe", "World | with punctuation", "A premise.\nWith newlines"))
        persisted.putSeries(Series("orig-series", "orig-universe", "Serial"))
        persisted.putVolume(Volume("orig-volume", "orig-series", 1, "Volume 1"))
        persisted.putChapter(Chapter("orig-chapter", "orig-volume", 1, "Opening", "nexus-content:1"))
        persisted.establish(CanonFact("protagonist", "faction", "independent", 0, "opening", "orig-chapter"))
        persisted.transition(CanonFact("protagonist", "faction", "coalition", 12, "negotiated", "orig-chapter"), "independent")
        persisted.reveal(Reveal("protagonist", "faction", "ally", 15, "coalition"))
        val restored = DurableProjects(ProjectJournal(File(temp, "durable.journal"))).snapshot()
        check(restored.catalog.getUniverse("orig-universe")!!.premise == "A premise.\nWith newlines")
        check(restored.catalog.getChapter("orig-chapter")!!.nexusContentId == "nexus-content:1")
        check(restored.canon.valueAt("protagonist", "faction", 13) == "coalition")
        check(restored.canon.characterKnows("ally", "protagonist", "faction", 15))
        check(restored.eventCount == 7L)
        persisted.linkChapter("orig-chapter", "nexus-content:1")
        check(persisted.snapshot().eventCount == 8L)
        expectFailure { persisted.linkChapter("orig-chapter", "nexus-content:other") }
        expectFailure { persisted.putChapter(Chapter("duplicate", "orig-volume", 1, "Duplicate", null)) }
        check(persisted.snapshot().eventCount == 8L)

        java.io.RandomAccessFile(File(temp, "project.journal"), "rw").use { raf ->
            raf.seek(55)
            val original = raf.readByte().toInt()
            raf.seek(55)
            raf.writeByte(original xor 1)
        }
        expectFailure { journal.read() }

        val modelFile = File(temp, "writer.gguf")
        modelFile.writeText("small test fixture, not a real model")
        val model = WriterModel("asterion-writer-test", sha256(modelFile), listOf(Accelerator.QNN, Accelerator.VULKAN), 1024, 4096)
        val cache = object : RounderModelCache {
            var requests = 0
            override fun availableLocalModel(modelId: String): File? = if (modelId == model.id) modelFile else null
            override fun requestStaging(modelId: String) { requests++ }
        }
        var releases = 0
        var opens = 0
        val runtime = object : NativeWriterRuntime {
            override fun supports(model: WriterModel, accelerator: Accelerator): Boolean = accelerator == Accelerator.VULKAN
            override fun open(model: WriterModel, weights: File, accelerator: Accelerator): WritingModelSession {
                check(accelerator == Accelerator.VULKAN)
                opens++
                return object : WritingModelSession {
                    override fun countTokens(text: String): Int = text.split(Regex("\\s+")).size
                    override fun generate(prompt: String, maxNewTokens: Int): String =
                        "The independent city resolved to forge a new future beyond the valley, changing its citizens forever."
                    override fun close() { releases++ }
                }
            }
        }
        val host = SingleModelHost(cache, object : DeviceMemory { override fun availableBytes() = 10_000L }, runtime)
        check(host.load(model) == Accelerator.VULKAN)
        check(host.load(model) == Accelerator.VULKAN)
        check(opens == 1)
        var saved = 0
        val nexus = object : NexusWritingStore {
            override fun saveDraft(universeId: String, seriesId: String, chapterId: String, text: String, findings: List<QualityFinding>): String {
                check(text.isNotBlank())
                saved++
                return "draft-$saved"
            }
            override fun publishApprovedChapter(draftId: String): String = error("Must not auto-publish")
        }
        val engine = DraftEngine(host, nexus, QualityGate(QualityPolicy(minWords = 5)))
        val receipt = engine.generateDraft(model, ChapterBrief("u1", "s1", "c1", "A new decision changes the polity", "City has two factions", "Complex characterization", "Do not adapt reference plots"), 128)
        check(receipt.nexusDraftId == "draft-1")
        check(!receipt.approvedForPublication)
        check(receipt.findings.any { it.code == "EDITORIAL_REVIEW_REQUIRED" })
        check(opens == 1)
        host.close()
        check(releases == 1)
        modelFile.writeText("modified")
        expectFailure { host.load(model) }
        check(cache.requests == 0)
    } finally { temp.deleteRecursively() }
    println("PASS: catalog isolation, canon transition and character knowledge, deterministic quality gates, journal recovery/integrity, single-model host, draft-no-autopublish")
}
