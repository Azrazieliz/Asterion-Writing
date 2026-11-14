package com.asterion.writing

/** In-memory editing index. Chapter bodies and EPUBs belong to Nexus, not this module. */
data class Universe(val id: String, val title: String, val premise: String)
data class Series(val id: String, val universeId: String, val title: String)
data class Volume(val id: String, val seriesId: String, val number: Int, val title: String)
data class Chapter(val id: String, val volumeId: String, val number: Int, val title: String, val nexusContentId: String?)

class StoryCatalog {
    private val worlds = linkedMapOf<String, Universe>()
    private val series = linkedMapOf<String, Series>()
    private val volumes = linkedMapOf<String, Volume>()
    private val chapters = linkedMapOf<String, Chapter>()

    fun putUniverse(value: Universe) {
        validateId(value.id)
        require(value.title.isNotBlank() && value.premise.isNotBlank())
        val previous = worlds[value.id]
        require(previous == null || previous == value) { "Universe ID already used" }
        worlds[value.id] = value
    }
    fun putSeries(value: Series) {
        validateId(value.id)
        require(value.title.isNotBlank() && worlds.containsKey(value.universeId)) { "Missing universe" }
        val previous = series[value.id]
        require(previous == null || previous == value) { "Series ID already used" }
        series[value.id] = value
    }
    fun putVolume(value: Volume) {
        validateId(value.id)
        require(value.number > 0 && value.title.isNotBlank() && series.containsKey(value.seriesId))
        require(volumes.values.none { it.seriesId == value.seriesId && it.number == value.number && it.id != value.id }) { "Duplicate volume number" }
        val previous = volumes[value.id]
        require(previous == null || previous == value) { "Volume ID already used" }
        volumes[value.id] = value
    }
    fun putChapter(value: Chapter) {
        validateId(value.id)
        require(value.number > 0 && value.title.isNotBlank() && volumes.containsKey(value.volumeId))
        require(chapters.values.none { it.volumeId == value.volumeId && it.number == value.number && it.id != value.id }) { "Duplicate chapter number" }
        val previous = chapters[value.id]
        require(previous == null || previous == value) { "Chapter ID already used; use draft revisions instead" }
        chapters[value.id] = value
    }
    fun linkChapter(chapterId: String, nexusContentId: String) {
        require(nexusContentId.isNotBlank())
        val chapter = requireNotNull(chapters[chapterId]) { "Missing chapter" }
        require(chapter.nexusContentId == null || chapter.nexusContentId == nexusContentId) { "Cannot silently replace published content" }
        chapters[chapterId] = chapter.copy(nexusContentId = nexusContentId)
    }
    fun getUniverse(id: String): Universe? = worlds[id]
    fun getSeries(id: String): Series? = series[id]
    fun getVolume(id: String): Volume? = volumes[id]
    fun getChapter(id: String): Chapter? = chapters[id]
    fun universeSeries(id: String): List<Series> = series.values.filter { it.universeId == id }
    fun seriesVolumes(id: String): List<Volume> = volumes.values.filter { it.seriesId == id }.sortedBy { it.number }
    fun volumeChapters(id: String): List<Chapter> = chapters.values.filter { it.volumeId == id }.sortedBy { it.number }
    fun allUniverses(): List<Universe> = worlds.values.toList()
    private fun validateId(id: String) {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) { "Invalid stable ID" }
    }
}
