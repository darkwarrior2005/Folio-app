package app.folio.core.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round

/**
 * Recognizes the volume and chapter number of chapters from their names. Ported, logic
 * unchanged, from Mihon's `tachiyomi.domain.chapter.service.ChapterRecognition` (Apache-2.0),
 * adapted to take a lightweight [RecognitionChapter] instead of Mihon's own chapter model.
 *
 * Each name is first read on its own: the volume or season is taken out, a part becomes the
 * letter of a sub-chapter, and the chapter number is the number after "ch." or else the first one
 * left. Reading the whole chapter list then fills in what single names don't tell, see
 * [parseChapterNumbers].
 *
 * -R> = regex conversion.
 */
object ChapterRecognition {

    data class RecognitionChapter(val name: String, val chapterNumber: Double)

    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")
    private val number = Regex(NUMBER_PATTERN)
    private val unwanted = Regex("""\b(?:ver|version)[^a-z]?[0-9]+""")
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")
    private val volume = Regex("""\b(?:(?:vol(?:ume)?|season)[^a-z0-9]? *|[vs]\.?)([0-9]+(?:\.[0-9]+)?)\b""")
    private val part = Regex("""\bpart\.? *([0-9]+|i{1,3}|iv|vi{0,3}|ix|x)\b""")
    private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")
    private val anyNumber = Regex("""[0-9]+(?:\.[0-9]+)?""")
    private val chapterPrefix = Regex("""(?:\b(?:ch|chap|chapter|ep|episode)\.? *|#)$""")
    private val bonus = Regex("""\b(?:extra|bonus|omake|special|side ?story|afterword|interlude)""")
    private val prologue = Regex("""\b(?:prologue|one.?shot|pilot)\b""")

    private val formatter = DecimalFormat("#.###", DecimalFormatSymbols(Locale.ROOT))

    data class ChapterNumbering(val volume: String?, val chapter: String?)

    private data class RecognizedNumber(val value: Double, val letter: Char? = null) {
        override fun toString(): String = formatter.format(value) + (letter?.toString() ?: "")
    }

    fun parseChapterNumber(
        mangaTitle: String,
        chapterName: String,
        chapterNumber: Double? = null,
    ): Double {
        if (chapterNumber != null && chapterNumber.isKnown()) return chapterNumber
        return parseName(cleanName(mangaTitle, chapterName))?.let { toDouble(it.toString()) } ?: chapterNumber ?: -1.0
    }

    fun parseChapterNumbers(mangaTitle: String, chapters: List<RecognitionChapter>): List<ChapterNumbering> {
        val names = chapters.map { cleanName(mangaTitle, it.name) }
        val sharedNumbers = findSharedNumbers(names, chapters)
        val chapterNumbers = Array(chapters.size) { index ->
            chapters[index].chapterNumber.takeIf { it.isKnown() }?.let(::RecognizedNumber)
                ?: parseName(names[index].removeRanges(sharedNumbers[index]))
        }
        val volumes = Array(chapters.size) { volume.find(names[it])?.groupValues?.get(1)?.toDouble() }

        fillChapterNumbers(names, chapterNumbers, volumes)
        return chapters.indices.map {
            ChapterNumbering(volumes[it]?.let(formatter::format), chapterNumbers[it]?.toString())
        }
    }

    fun toDouble(number: String): Double {
        val letter = number.last().takeIf { it.isLetter() }
        val value = number.removeSuffix(letter?.toString() ?: "").toDouble()
        val tenths = letter?.let { it - 'a' + 1 }
        return if (tenths != null && tenths < 10 && '.' !in number) value + tenths / 10.0 else value
    }

    private fun Double.isKnown(): Boolean = this == -2.0 || this > -1.0

    private fun cleanName(mangaTitle: String, chapterName: String): String {
        return chapterName.lowercase().replace(mangaTitle.lowercase(), "")
    }

    private fun String.removeRanges(ranges: List<IntRange>): String {
        return ranges.sortedByDescending { it.first }.fold(this) { name, range -> name.replaceRange(range, " ") }
    }

    private fun findSharedNumbers(names: List<String>, chapters: List<RecognitionChapter>): List<List<IntRange>> {
        val matches = names.map { anyNumber.findAll(it).toList() }
        val sharedNumbers = MutableList(names.size) { emptyList<IntRange>() }
        names.indices
            .filterNot { chapters[it].chapterNumber.isKnown() }
            .groupBy { names[it].replace(anyNumber, "#") }
            .values
            .filter { it.size >= 3 }
            .forEach { group ->
                val first = matches[group.first()]
                val (sharedSlots, otherSlots) = first.indices.partition { slot ->
                    group.all { matches[it][slot].value == first[slot].value }
                }
                if (otherSlots.isEmpty()) return@forEach
                val removableSlots = sharedSlots.filterNot { slot ->
                    chapterPrefix.containsMatchIn(names[group.first()].substring(0, first[slot].range.first))
                }
                group.forEach { index -> sharedNumbers[index] = removableSlots.map { matches[index][it].range } }
            }
        return sharedNumbers
    }

    private fun fillChapterNumbers(
        names: List<String>,
        chapterNumbers: Array<RecognizedNumber?>,
        volumes: Array<Double?>,
    ) {
        chapterNumbers.nullRuns().forEach { run ->
            val newer = chapterNumbers.getOrNull(run.first - 1)?.takeIf { it.value >= 0 }
            val older = chapterNumbers.getOrNull(run.last + 1)?.takeIf { it.value >= 0 }
            val olderVolume = volumes.getOrNull(run.last + 1)
            val bonuses = run.count { bonus.containsMatchIn(names[it]) }
            when {
                newer != null && older != null -> {
                    val isSameVolume = volumes[run.first - 1] == olderVolume && volumes.isWithinVolume(run, olderVolume)
                    if (newer.value <= older.value || !isSameVolume) return@forEach
                    val isWhole = listOf(newer, older).all { it.letter == null && it.value % 1.0 == 0.0 }
                    if (bonuses == 0 && isWhole && newer.value - older.value - 1 == run.count().toDouble()) {
                        run.forEach { chapterNumbers[it] = RecognizedNumber(older.value + (run.last + 1 - it)) }
                    } else {
                        chapterNumbers.fillBetween(run, older.value, minOf(newer.value, floor(older.value) + 1))
                    }
                }
                older != null -> {
                    if (bonuses < run.count() || !volumes.isWithinVolume(run, olderVolume)) return@forEach
                    chapterNumbers.fillBetween(run, older.value, floor(older.value) + 1)
                }
                newer != null && newer.value > 0 -> {
                    run.filter { prologue.containsMatchIn(names[it]) }.forEach {
                        chapterNumbers[it] = RecognizedNumber(0.0)
                        if (volumes[it] == null && volumes[run.first - 1] != null) volumes[it] = 0.0
                    }
                }
            }
        }
    }

    private fun Array<RecognizedNumber?>.nullRuns(): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = 0
        while (start < size) {
            if (this[start] != null) {
                start++
                continue
            }
            val end = (start until size).firstOrNull { this[it] != null } ?: size
            runs.add(start until end)
            start = end
        }
        return runs
    }

    private fun Array<Double?>.isWithinVolume(run: IntRange, volume: Double?): Boolean {
        return run.all { this[it] == null || this[it] == volume }
    }

    private fun Array<RecognizedNumber?>.fillBetween(run: IntRange, lower: Double, upper: Double) {
        val tenths = (5..9).map { floor(lower) + it / 10.0 }.filter { it > lower && it < upper }
        if (tenths.size >= run.count()) {
            run.reversed().forEachIndexed { index, chapter ->
                this[chapter] = RecognizedNumber(round(tenths[index] * 10) / 10)
            }
            return
        }
        val step = (upper - lower) / (run.count() + 1)
        run.forEach { this[it] = RecognizedNumber(round((lower + step * (run.last + 1 - it)) * 1000) / 1000) }
    }

    private fun parseName(name: String): RecognizedNumber? {
        val withoutVolume = volume.replace(name, " ")
        val partMatch = part.find(withoutVolume)
        val partLetter = partMatch?.groupValues?.get(1)?.let(::parsePart)?.takeIf { it in 1..26 }?.let { 'a' + it - 1 }
        if (partMatch != null && partLetter != null) {
            parseNumber(withoutVolume.removeRange(partMatch.range))?.let { number ->
                return if (number.letter == null) number.copy(letter = partLetter) else number
            }
        }
        return parseNumber(withoutVolume)
    }

    private fun parsePart(part: String): Int = part.toIntOrNull() ?: (romanNumerals.indexOf(part) + 1)

    private fun parseNumber(name: String): RecognizedNumber? {
        val cleanName = name
            .trim()
            .replace(',', '.')
            .replace('-', '.')
            .replace(unwantedWhiteSpace, "")

        val numbers = number.findAll(cleanName).toList()
        if (numbers.size > 1) {
            val withoutTags = unwanted.replace(cleanName, "")
            (basic.find(withoutTags) ?: number.find(withoutTags))?.let { return it.toRecognizedNumber() }
        }
        return numbers.firstOrNull()?.toRecognizedNumber()
    }

    private fun MatchResult.toRecognizedNumber(): RecognizedNumber {
        val (whole, decimal, alpha) = destructured
        val value = whole.toDouble() + (decimal.toDoubleOrNull() ?: 0.0)
        return when {
            decimal.isEmpty() && "extra" in alpha -> RecognizedNumber(value + 0.99)
            decimal.isEmpty() && "omake" in alpha -> RecognizedNumber(value + 0.98)
            decimal.isEmpty() && "special" in alpha -> RecognizedNumber(value + 0.97)
            else -> RecognizedNumber(value, alpha.trimStart('.').singleOrNull()?.takeIf { it in 'a'..'i' })
        }
    }
}
