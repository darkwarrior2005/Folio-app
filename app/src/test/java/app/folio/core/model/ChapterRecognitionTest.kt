package app.folio.core.model

import app.folio.core.model.ChapterRecognition.ChapterNumbering
import app.folio.core.model.ChapterRecognition.RecognitionChapter
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ported from Mihon's ChapterRecognitionTest (Apache-2.0), case for case. */
class ChapterRecognitionTest {

    @Test
    fun basicChPrefix() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation" to ChapterNumbering("1", "4"),
        )
    }

    @Test
    fun basicChPrefixWithSpaceAfterPeriod() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation" to ChapterNumbering("1", "4"),
        )
    }

    @Test
    fun basicChPrefixWithDecimal() {
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation" to ChapterNumbering("1", "4.1"))
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation" to ChapterNumbering("1", "4.4"))
    }

    @Test
    fun basicChPrefixWithAlphaPostfix() {
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation" to ChapterNumbering("1", "4a"))
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.b: Misrepresentation" to ChapterNumbering("1", "4b"))
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation" to ChapterNumbering("1", "4.99"))
    }

    @Test
    fun nameContainingOneNumber() {
        assertNumbers("Bleach", "Bleach 567 Down With Snowwhite" to ChapterNumbering(null, "567"))
    }

    @Test
    fun nameContainingOneNumberAndDecimal() {
        assertNumbers("Bleach", "Bleach 567.1 Down With Snowwhite" to ChapterNumbering(null, "567.1"))
        assertNumbers("Bleach", "Bleach 567.4 Down With Snowwhite" to ChapterNumbering(null, "567.4"))
    }

    @Test
    fun nameContainingOneNumberAndAlpha() {
        assertNumbers("Bleach", "Bleach 567.a Down With Snowwhite" to ChapterNumbering(null, "567a"))
        assertNumbers("Bleach", "Bleach 567.b Down With Snowwhite" to ChapterNumbering(null, "567b"))
        assertNumbers("Bleach", "Bleach 567.extra Down With Snowwhite" to ChapterNumbering(null, "567.99"))
    }

    @Test
    fun chapterContainingMangaTitleAndNumber() {
        assertNumbers("Solanin", "Solanin 028 Vol. 2" to ChapterNumbering("2", "28"))
    }

    @Test
    fun chapterContainingMangaTitleAndNumberDecimal() {
        assertNumbers("Solanin", "Solanin 028.1 Vol. 2" to ChapterNumbering("2", "28.1"))
        assertNumbers("Solanin", "Solanin 028.4 Vol. 2" to ChapterNumbering("2", "28.4"))
    }

    @Test
    fun chapterContainingMangaTitleAndNumberAlpha() {
        assertNumbers("Solanin", "Solanin 028.a Vol. 2" to ChapterNumbering("2", "28a"))
        assertNumbers("Solanin", "Solanin 028.b Vol. 2" to ChapterNumbering("2", "28b"))
        assertNumbers("Solanin", "Solanin 028.extra Vol. 2" to ChapterNumbering("2", "28.99"))
    }

    @Test
    fun extremeCase() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028" to ChapterNumbering(null, "28"))
    }

    @Test
    fun extremeCaseWithDecimal() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.1" to ChapterNumbering(null, "28.1"))
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.4" to ChapterNumbering(null, "28.4"))
    }

    @Test
    fun extremeCaseWithAlpha() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.a" to ChapterNumbering(null, "28a"))
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.b" to ChapterNumbering(null, "28b"))
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.extra" to ChapterNumbering(null, "28.99"))
    }

    @Test
    fun chapterContainingDotV2() {
        assertNumbers("random", "Vol.1 Ch.5v.2: Alones" to ChapterNumbering("1", "5"))
    }

    @Test
    fun numberInMangaTitle() {
        assertNumbers("Ayame 14", "Ayame 14 1 - The summer of 14" to ChapterNumbering(null, "1"))
    }

    @Test
    fun spaceBetweenChX() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation" to ChapterNumbering("1", "4"),
        )
    }

    @Test
    fun chapterTitleWithChSubstring() {
        assertNumbers("Ayame 14", "Vol.1 Ch.1: March 25 (First Day Cohabiting)" to ChapterNumbering("1", "1"))
    }

    @Test
    fun chapterContainingMultipleZeros() {
        assertNumbers("random", "Vol.001 Ch.003: Kaguya Doesn't Know Much" to ChapterNumbering("1", "3"))
    }

    @Test
    fun chapterWithVersionBeforeNumber() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]" to ChapterNumbering(null, "86"))
    }

    @Test
    fun versionAttachedToChapterNumber() {
        assertNumbers("Ansatsu Kyoushitsu", "Ansatsu Kyoushitsu 011v002: Assembly Time" to ChapterNumbering(null, "11"))
    }

    @Test
    fun numberAfterMangaTitleWithChapterInChapterTitleCase() {
        assertNumbers("Tokyo ESP", "Tokyo ESP 027: Part 002: Chapter 001" to ChapterNumbering(null, "27b"))
    }

    @Test
    fun numberAfterUnwantedTag() {
        assertNumbers("One-punch Man", "Mag Version 195.5" to ChapterNumbering(null, "195.5"))
    }

    @Test
    fun unparseableChapter() {
        assertNumbers("random", "Foo" to ChapterNumbering(null, null))
    }

    @Test
    fun chapterWithTimeInTitle() {
        assertNumbers("random", "Fairy Tail 404: 00:00" to ChapterNumbering(null, "404"))
    }

    @Test
    fun chapterWithAlphaWithoutDot() {
        assertNumbers("random", "Asu No Yoichi 19a" to ChapterNumbering(null, "19a"))
    }

    @Test
    fun chapterTitleContainingExtraAndVol() {
        assertNumbers("Fairy Tail", "Fairy Tail 404.extravol002" to ChapterNumbering(null, "404.99"))
        assertNumbers("Fairy Tail", "Fairy Tail 404 extravol002" to ChapterNumbering(null, "404.99"))
    }

    @Test
    fun chapterTitleContainingOmakeAndVol() {
        assertNumbers("Fairy Tail", "Fairy Tail 404.omakevol002" to ChapterNumbering(null, "404.98"))
        assertNumbers("Fairy Tail", "Fairy Tail 404 omakevol002" to ChapterNumbering(null, "404.98"))
    }

    @Test
    fun chapterTitleContainingSpecialAndVol() {
        assertNumbers("Fairy Tail", "Fairy Tail 404.specialvol002" to ChapterNumbering(null, "404.97"))
        assertNumbers("Fairy Tail", "Fairy Tail 404 specialvol002" to ChapterNumbering(null, "404.97"))
    }

    @Test
    fun chapterTitleContainingCommas() {
        assertNumbers("One Piece", "One Piece 300,a" to ChapterNumbering(null, "300a"))
        assertNumbers("One Piece", "One Piece Ch,123,extra" to ChapterNumbering(null, "123.99"))
        assertNumbers("One Piece", "One Piece the sunny, goes swimming 024,005" to ChapterNumbering(null, "24.005"))
    }

    @Test
    fun chapterTitleContainingHyphens() {
        assertNumbers("Solo Leveling", "ch 122-a" to ChapterNumbering(null, "122a"))
        assertNumbers("Solo Leveling", "Solo Leveling Ch.123-extra" to ChapterNumbering(null, "123.99"))
        assertNumbers("Solo Leveling", "Solo Leveling, 024-005" to ChapterNumbering(null, "24.005"))
        assertNumbers("Solo Leveling", "Ch.191-200 Read Online" to ChapterNumbering(null, "191.2"))
    }

    @Test
    fun chaptersContainingSeason() {
        assertNumbers("D.I.C.E", "D.I.C.E[Season 001] Ep. 007" to ChapterNumbering("1", "7"))
    }

    @Test
    fun chaptersInFormatSxChapterXx() {
        assertNumbers("The Gamer", "S3 - Chapter 20" to ChapterNumbering("3", "20"))
    }

    @Test
    fun chaptersEndingWithS() {
        assertNumbers("One Outs", "One Outs 001" to ChapterNumbering(null, "1"))
    }

    @Test
    fun chaptersContainingOrdinals() {
        val mangaTitle = "The Sister of the Woods with a Thousand Young"
        assertNumbers(mangaTitle, "The 1st Night" to ChapterNumbering(null, "1"))
        assertNumbers(mangaTitle, "The 2nd Night" to ChapterNumbering(null, "2"))
        assertNumbers(mangaTitle, "The 3rd Night" to ChapterNumbering(null, "3"))
        assertNumbers(mangaTitle, "The 4th Night" to ChapterNumbering(null, "4"))
    }

    @Test
    fun partsAreLetters() {
        assertNumbers("Chapter 1.5 Part I" to ChapterNumbering(null, "1.5a"))
        assertNumbers("Chapter 1.5 Part II" to ChapterNumbering(null, "1.5b"))
        assertNumbers("Chapter 12 Part 3" to ChapterNumbering(null, "12c"))
        assertNumbers("Vol.2 Chapter 7 - Part IV" to ChapterNumbering("2", "7d"))
        assertNumbers("Part 3" to ChapterNumbering(null, "3"))
    }

    @Test
    fun numbersAreTurnedIntoDoubles() {
        assertEquals(12.0, ChapterRecognition.toDouble("12"), 0.0)
        assertEquals(10.5, ChapterRecognition.toDouble("10.5"), 0.0)
        assertEquals(19.1, ChapterRecognition.toDouble("19a"), 0.0)
        assertEquals(19.9, ChapterRecognition.toDouble("19i"), 0.0)
        assertEquals(1.5, ChapterRecognition.toDouble("1.5a"), 0.0)
        assertEquals(-2.0, ChapterRecognition.toDouble("-2"), 0.0)
    }

    @Test
    fun volumeIsNotTakenAsTheChapterNumber() {
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.3" to ChapterNumbering("3", null))
        assertNumbers("Mokushiroku Alice", "Vol.3 12" to ChapterNumbering("3", "12"))
    }

    @Test
    fun seasonsAreVolumes() {
        assertNumbers("Vol-3 Chapter 2" to ChapterNumbering("3", "2"))
        assertNumbers("Mob's 100" to ChapterNumbering(null, "100"))
    }

    @Test
    fun volumesAreOnlyTakenFromTheName() {
        assertNumbers(
            "Vol.2 Ch.5" to ChapterNumbering("2", "5"),
            "Ch.4" to ChapterNumbering(null, "4"),
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
        )
    }

    @Test
    fun numbersEverySimilarNameSharesAreIgnored() {
        assertNumbers(
            "Group 2 Chapter 387" to ChapterNumbering(null, "387"),
            "Group 2 Chapter 386" to ChapterNumbering(null, "386"),
            "Group 2 Chapter 385" to ChapterNumbering(null, "385"),
        )
    }

    @Test
    fun sharedNumbersAfterAChapterPrefixAreKept() {
        assertNumbers(
            "Chapter 5 Part 3" to ChapterNumbering(null, "5c"),
            "Chapter 5 Part 2" to ChapterNumbering(null, "5b"),
            "Chapter 5 Part 1" to ChapterNumbering(null, "5a"),
        )
    }

    @Test
    fun chaptersWithoutANumberFillTheMissingWholeNumbers() {
        assertNumbers(
            "Chapter 4" to ChapterNumbering(null, "4"),
            "The Return" to ChapterNumbering(null, "3"),
            "Chapter 2" to ChapterNumbering(null, "2"),
        )
        assertNumbers(
            "Chapter 5" to ChapterNumbering(null, "5"),
            "Rain" to ChapterNumbering(null, "4"),
            "Snow" to ChapterNumbering(null, "3"),
            "Chapter 2" to ChapterNumbering(null, "2"),
        )
    }

    @Test
    fun chaptersWithoutANumberGoBetweenTheChaptersAroundThem() {
        assertNumbers(
            "Chapter 11" to ChapterNumbering(null, "11"),
            "Bonus: Beach Episode" to ChapterNumbering(null, "10.5"),
            "Chapter 10" to ChapterNumbering(null, "10"),
        )
        assertNumbers(
            "Chapter 13" to ChapterNumbering(null, "13"),
            "Intermission" to ChapterNumbering(null, "10.5"),
            "Chapter 10" to ChapterNumbering(null, "10"),
        )
        assertNumbers(
            "Chapter 11" to ChapterNumbering(null, "11"),
            "Rain" to ChapterNumbering(null, "10.6"),
            "Snow" to ChapterNumbering(null, "10.5"),
            "Chapter 10" to ChapterNumbering(null, "10"),
        )
        assertNumbers(
            "Chapter 4" to ChapterNumbering(null, "4"),
            "Side Story" to ChapterNumbering(null, "2.5"),
            "Chapter 2" to ChapterNumbering(null, "2"),
        )
        assertNumbers(
            "Chapter 3" to ChapterNumbering(null, "3"),
            "Extra" to ChapterNumbering(null, "2.6"),
            "Chapter 2.5" to ChapterNumbering(null, "2.5"),
        )
        assertNumbers(
            "Chapter 3" to ChapterNumbering(null, "3"),
            "Extra" to ChapterNumbering(null, "2.95"),
            "Chapter 2.9" to ChapterNumbering(null, "2.9"),
        )
    }

    @Test
    fun chaptersWithoutANumberAtTheEnds() {
        assertNumbers(
            "Afterword" to ChapterNumbering(null, "100.5"),
            "Chapter 100" to ChapterNumbering(null, "100"),
        )
        assertNumbers(
            "Hiatus Announcement" to ChapterNumbering(null, null),
            "Chapter 100" to ChapterNumbering(null, "100"),
        )
        assertNumbers(
            "Chapter 1" to ChapterNumbering(null, "1"),
            "Prologue" to ChapterNumbering(null, "0"),
        )
        assertNumbers(
            "Chapter 1" to ChapterNumbering(null, "1"),
            "Character Profiles" to ChapterNumbering(null, null),
        )
    }

    @Test
    fun aPrologueBeforeChaptersInVolumesIsInVolumeZero() {
        assertNumbers(
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
            "Prologue" to ChapterNumbering("0", "0"),
        )
        assertNumbers(
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
            "Vol.2 Prologue" to ChapterNumbering("2", "0"),
        )
    }

    @Test
    fun chaptersWithoutANumberAreOnlyNumberedWithinAVolume() {
        assertNumbers(
            "Vol.1 Ch.3" to ChapterNumbering("1", "3"),
            "Rain" to ChapterNumbering(null, "2"),
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
        )
        assertNumbers(
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
            "Rain" to ChapterNumbering(null, null),
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
        )
        assertNumbers(
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
            "Vol.2 Rain" to ChapterNumbering("2", null),
            "Ch.1" to ChapterNumbering(null, "1"),
        )
        assertNumbers(
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
            "Vol.1 Rain" to ChapterNumbering("1", null),
            "Vol.2 Ch.1" to ChapterNumbering("2", "1"),
        )
        assertNumbers(
            "Extra" to ChapterNumbering(null, "10.5"),
            "Vol.2 Ch.10" to ChapterNumbering("2", "10"),
        )
        assertNumbers(
            "Vol.2 Extra" to ChapterNumbering("2", "10.5"),
            "Vol.2 Ch.10" to ChapterNumbering("2", "10"),
        )
        assertNumbers(
            "Vol.3 Extra" to ChapterNumbering("3", null),
            "Vol.2 Ch.10" to ChapterNumbering("2", "10"),
        )
    }

    @Test
    fun numbersFromTheSourceAreKept() {
        val chapters = listOf(
            RecognitionChapter(name = "Vol.1 Chapter 3", chapterNumber = 7.0),
            RecognitionChapter(name = "Chapter 2", chapterNumber = -1.0),
        )
        assertEquals(
            listOf(ChapterNumbering("1", "7"), ChapterNumbering(null, "2")),
            ChapterRecognition.parseChapterNumbers("", chapters),
        )
    }

    private fun assertNumbers(vararg expected: Pair<String, ChapterNumbering>) = assertNumbers("", *expected)

    private fun assertNumbers(mangaTitle: String, vararg expected: Pair<String, ChapterNumbering>) {
        val chapters = expected.map { (name, _) -> RecognitionChapter(name = name, chapterNumber = -1.0) }
        assertEquals(expected.map { it.second }, ChapterRecognition.parseChapterNumbers(mangaTitle, chapters))
    }
}
