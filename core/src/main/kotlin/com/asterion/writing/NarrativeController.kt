package com.asterion.writing

/** Editorial decisions are authoritative only after explicit acceptance. */
enum class SceneStatus { PLANNED, DRAFT, REVIEW, ACCEPTED }
data class SceneObjective(
    val id: String,
    val chapterId: String,
    val title: String,
    val objective: String,
    val characters: List<String>,
    val viewpoint: String = "",
    val chronology: Long = 0L,
    val status: SceneStatus = SceneStatus.PLANNED,
    val outcome: String = ""
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
        require(chapterId.isNotBlank() && title.isNotBlank() && objective.isNotBlank())
        require(chronology >= 0)
        require(characters.size == characters.distinct().size)
        if (status == SceneStatus.ACCEPTED) require(outcome.isNotBlank())
    }
}
data class AuthoringProfile(
    val name: String = "Default",
    val style: String = "",
    val pacing: String = "",
    val viewpoint: String = "",
    val dialogue: String = "",
    val exclusions: String = ""
)
data class ChapterContext(
    val universe: String,
    val series: String,
    val volume: String,
    val chapter: String,
    val premise: String,
    val canon: String,
    val castAndKnowledge: String,
    val chapterPlan: String,
    val acceptedOutcomes: List<String>,
    val profile: AuthoringProfile = AuthoringProfile()
) {
    init { require(universe.isNotBlank() && chapter.isNotBlank() && premise.isNotBlank()) }
}
data class NarrativeReview(val codes: List<String>, val acceptedForPublication: Boolean = false)

/** Pure deterministic narrative state and prompt assembly, never a fake language model. */
class NarrativeController {
    private val scenes = linkedMapOf<String, SceneObjective>()
    fun putPlanned(scene: SceneObjective) {
        require(scene.status == SceneStatus.PLANNED) { "Use review and accept for authored scenes" }
        require(scenes[scene.id] == null) { "Scene IDs cannot be silently replaced" }
        require(scenes.values.none { it.chapterId == scene.chapterId && it.chronology == scene.chronology && it.id != scene.id }) {
            "Two scenes cannot share a timeline position"
        }
        scenes[scene.id] = scene
    }
    fun markReviewed(sceneId: String): SceneObjective {
        val prior = requireNotNull(scenes[sceneId]) { "Unknown scene" }
        require(prior.status == SceneStatus.PLANNED || prior.status == SceneStatus.DRAFT)
        return prior.copy(status = SceneStatus.REVIEW).also { scenes[sceneId] = it }
    }
    fun accept(sceneId: String, outcome: String): SceneObjective {
        val prior = requireNotNull(scenes[sceneId]) { "Unknown scene" }
        require(prior.status == SceneStatus.REVIEW) { "Review the draft before accepting its effects" }
        require(outcome.isNotBlank())
        return prior.copy(status = SceneStatus.ACCEPTED, outcome = outcome).also { scenes[sceneId] = it }
    }
    fun chapterScenes(chapterId: String): List<SceneObjective> =
        scenes.values.filter { it.chapterId == chapterId }.sortedBy { it.chronology }

    fun makeInstruction(
        context: ChapterContext,
        scene: SceneObjective,
        recentProse: String,
        continuation: Boolean,
        maxRecentChars: Int = 2800
    ): String {
        require(maxRecentChars in 0..10000)
        require(scene.status != SceneStatus.ACCEPTED) { "Accepted scenes are immutable: create a revision" }
        val authorizedOutcomes = context.acceptedOutcomes.filter { it.isNotBlank() }.takeLast(12)
        return buildString {
            appendLine("TASK: Write original novel prose for the scene below. Never provide an outline or assistant commentary.")
            appendLine("UNIVERSE: ${context.universe}")
            appendLine("SERIES / VOLUME / CHAPTER: ${context.series} / ${context.volume} / ${context.chapter}")
            appendLine("PREMISE: ${context.premise}")
            appendLine("AUTHORITATIVE CANON (do not silently change): ${context.canon.ifBlank { "No approved facts yet." }}")
            appendLine("CAST AND CHARACTER KNOWLEDGE: ${context.castAndKnowledge.ifBlank { "Establish coherently in the scene." }}")
            appendLine("CHAPTER PLAN: ${context.chapterPlan.ifBlank { "Advance the current chapter conflict." }}")
            appendLine("ACCEPTED EARLIER OUTCOMES: ${authorizedOutcomes.ifEmpty { listOf("None yet.") }.joinToString(" | ")}")
            appendLine("WRITING PROFILE: ${context.profile.name}; style=${context.profile.style}; pacing=${context.profile.pacing}; POV=${context.profile.viewpoint}; dialogue=${context.profile.dialogue}; avoid=${context.profile.exclusions}")
            appendLine("SCENE: ${scene.title}; objective=${scene.objective}; characters=${scene.characters.joinToString(", ")}; POV=${scene.viewpoint}; timeline=${scene.chronology}")
            appendLine("Reveal stakes through action and character-specific speech. Follow choices with consequences. Respect who knows what, chronology and established abilities.")
            if (continuation && recentProse.isNotBlank()) {
                appendLine("RECENT PROSE FOR CONTINUITY ONLY — DO NOT REPEAT OR SUMMARIZE:")
                appendLine(recentProse.takeLast(maxRecentChars))
                appendLine("Continue with only new prose; no recaps.")
            } else appendLine("Start directly in the scene.")
        }
    }

    fun reviewChapter(chapter: String, priorScene: String = ""): NarrativeReview {
        val findings = mutableListOf<String>()
        findings += QualityGate().check(chapter).map { it.code }
        if (priorScene.isNotBlank() && normalize(priorScene.takeLast(240)) ==
            normalize(chapter.take(240))) findings += "REPEATED_SCENE_START"
        if (chapter.contains(Regex("(?i)\\b(as an ai|here is the chapter|chapter summary:)\\b"))) findings += "META_OUTPUT"
        return NarrativeReview(findings.distinct())
    }
}
