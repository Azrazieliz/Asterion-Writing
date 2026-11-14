package com.asterion.writing

/** Canon is intentionally separate from draft prose: only reviewed facts can become authoritative. */
data class CanonFact(
    val entityId: String,
    val attribute: String,
    val value: String,
    val fromTick: Long,
    val reason: String,
    val introducedAtChapter: String
)

data class Reveal(val entityId: String, val factAttribute: String, val characterId: String, val atTick: Long, val knownValue: String)

class CanonLedger {
    private val changes = mutableListOf<CanonFact>()
    private val reveals = mutableSetOf<Reveal>()

    fun establish(fact: CanonFact) {
        require(fact.entityId.isNotBlank() && fact.attribute.isNotBlank() && fact.value.isNotBlank())
        require(fact.fromTick >= 0 && fact.introducedAtChapter.isNotBlank())
        require(changes.none { it.entityId == fact.entityId && it.attribute == fact.attribute }) { "Canon attribute already established; use transition" }
        changes += fact
    }

    fun transition(fact: CanonFact, expectedPreviousValue: String) {
        require(fact.reason.isNotBlank()) { "Canon change must have a narrative reason" }
        val previous = valueAt(fact.entityId, fact.attribute, fact.fromTick)
        require(previous == expectedPreviousValue) { "Incorrect previous canon value" }
        require(fact.value != previous) { "Transition must change the value" }
        require(changes.none { it.entityId == fact.entityId && it.attribute == fact.attribute && it.fromTick == fact.fromTick }) { "Same-tick conflict" }
        changes += fact
    }

    fun valueAt(entityId: String, attribute: String, tick: Long): String? =
        changes.asSequence().filter { it.entityId == entityId && it.attribute == attribute && it.fromTick <= tick }
            .maxByOrNull { it.fromTick }?.value

    fun recordReveal(reveal: Reveal) {
        require(reveal.atTick >= 0 && reveal.characterId.isNotBlank())
        require(reveal.knownValue.isNotBlank() && valueAt(reveal.entityId, reveal.factAttribute, reveal.atTick) == reveal.knownValue) { "Cannot reveal an unverified or obsolete fact" }
        reveals += reveal
    }

    fun characterKnows(characterId: String, entityId: String, attribute: String, atTick: Long): Boolean =
        reveals.any { it.characterId == characterId && it.entityId == entityId && it.factAttribute == attribute && it.atTick <= atTick && it.knownValue == valueAt(entityId, attribute, atTick) }

    fun history(entityId: String): List<CanonFact> = changes.filter { it.entityId == entityId }.sortedBy { it.fromTick }
}
