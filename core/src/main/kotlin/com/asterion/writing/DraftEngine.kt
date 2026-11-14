package com.asterion.writing

/** Generates a reviewable draft; never auto-publishes or rewrites accepted canon. */
data class ChapterBrief(
    val universeId: String,
    val seriesId: String,
    val chapterId: String,
    val plan: String,
    val canonSummary: String,
    val toneAndPreferences: String,
    val mustNotImitate: String
)
data class DraftReceipt(val nexusDraftId: String, val findings: List<QualityFinding>, val approvedForPublication: Boolean = false)

class DraftEngine(
    private val modelHost: SingleModelHost,
    private val store: NexusWritingStore,
    private val quality: QualityGate
) {
    fun generateDraft(model: WriterModel, brief: ChapterBrief, maxNewTokens: Int, reference: ReferenceShingleIndex? = null): DraftReceipt {
        require(brief.universeId.isNotBlank() && brief.seriesId.isNotBlank() && brief.chapterId.isNotBlank())
        require(brief.plan.isNotBlank() && brief.canonSummary.isNotBlank())
        val prompt = buildString {
            appendLine("Write an original web/light novel chapter. This is a private fiction-authoring project.")
            appendLine("Create distinct worldbuilding, scenes, character voices and narrative developments; do not imitate source works.")
            appendLine("Universe: ${brief.universeId}")
            appendLine("Series: ${brief.seriesId}")
            appendLine("Chapter: ${brief.chapterId}")
            appendLine("Chapter plan:\n${brief.plan}")
            appendLine("Authoritative continuity:\n${brief.canonSummary}")
            appendLine("User preferences:\n${brief.toneAndPreferences}")
            appendLine("Specific similarity risks to avoid:\n${brief.mustNotImitate}")
            appendLine("Output only the prose chapter. Preserve plot and character causality.")
        }
        val text = modelHost.generate(model, prompt, maxNewTokens)
        require(text.isNotBlank()) { "Native model returned an empty draft" }
        val findings = quality.check(text, reference)
        val id = store.saveDraft(brief.universeId, brief.seriesId, brief.chapterId, text, findings)
        require(id.isNotBlank()) { "Nexus did not return a draft ID" }
        return DraftReceipt(id, findings)
    }
}
