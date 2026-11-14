package com.asterion.writing

import java.text.Normalizer
import java.util.Locale

enum class Severity { BLOCK, REVIEW }
data class QualityFinding(val code: String, val severity: Severity, val description: String)
data class QualityPolicy(
    val minWords: Int = 250,
    val maxRepeatedParagraphs: Int = 0,
    val referenceShingleWords: Int = 10,
    val maxReferenceShingleMatches: Int = 0
) {
    init { require(minWords >= 0 && maxRepeatedParagraphs >= 0 && referenceShingleWords >= 5 && maxReferenceShingleMatches >= 0) }
}

/** Only matching hashed n-grams are retained, not the user's reference-book text. */
class ReferenceShingleIndex(private val window: Int) {
    init { require(window >= 5) }
    private val hashes = mutableSetOf<String>()
    fun addReference(text: String) { tokenWindows(text, window).forEach { hashes.add(sha256(it)) } }
    fun matches(candidate: String): Int = tokenWindows(candidate, window).count { sha256(it) in hashes }
    fun size(): Int = hashes.size
}

class QualityGate(private val policy: QualityPolicy = QualityPolicy()) {
    fun check(text: String, reference: ReferenceShingleIndex? = null): List<QualityFinding> {
        val result = mutableListOf<QualityFinding>()
        val words = tokens(text).size
        if (words < policy.minWords) result += QualityFinding("SHORT_DRAFT", Severity.BLOCK, "$words words; minimum ${policy.minWords}")
        val paragraphs = text.split(Regex("(?:\\r?\\n){2,}"))
            .map { normalize(it) }.filter { it.split(' ').size >= 12 }
        val repetitions = paragraphs.groupingBy { it }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
        if (repetitions > policy.maxRepeatedParagraphs)
            result += QualityFinding("REPEATED_PARAGRAPH", Severity.BLOCK, "$repetitions repeated substantial paragraphs")
        if (reference != null) {
            val matches = reference.matches(text)
            if (matches > policy.maxReferenceShingleMatches)
                result += QualityFinding("REFERENCE_OVERLAP", Severity.BLOCK, "$matches exact reference n-gram matches; human review required")
        }
        // Semantic quality, character motivation, plagiarism and style cannot be guaranteed by this gate.
        result += QualityFinding("EDITORIAL_REVIEW_REQUIRED", Severity.REVIEW, "Human editorial and semantic originality review required")
        return result
    }

    fun blocked(findings: List<QualityFinding>): Boolean = findings.any { it.severity == Severity.BLOCK }
}

internal fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex(" +"), " ")
internal fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }
internal fun tokenWindows(text: String, size: Int): Sequence<String> {
    val t = tokens(text)
    return if (t.size < size) emptySequence() else (0..t.size - size).asSequence().map { offset -> t.subList(offset, offset + size).joinToString(" ") }
}
