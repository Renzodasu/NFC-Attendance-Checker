package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.ClassMark
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two-letter mark that replaced the playing-card face.
 *
 * It is derived, not chosen, so the only rules that matter are: a class with a
 * grade number keeps it, a class without one still gets letters, and nothing a
 * teacher types can make the mark blank.
 */
class ClassMarkTest {

    @Test
    fun aGradeAndADashNameKeepsTheGradeAndTheInitial() {
        assertEquals("3R", ClassMark.monogram("Grade 3 - Rizal"))
        assertEquals("3S", ClassMark.monogram("Grade 3 - Sampaguita"))
        assertEquals("5N", ClassMark.monogram("Grade 5 - NARRA"))
    }

    @Test
    fun aNameWithoutAGradeUsesItsFirstTwoLetters() {
        assertEquals("NA", ClassMark.monogram("Narra"))
        assertEquals("MO", ClassMark.monogram("Molave"))
    }

    @Test
    fun aLongGradeNumberKeepsOnlyItsLastTwoDigits() {
        assertEquals("12M", ClassMark.monogram("Grade 12 - Mabini"))
    }

    @Test
    fun aNounThatStartsWithADigitStillReadsItsLetter() {
        // Regressions: the badge painted the grade twice ("33") because the initial
        // was taken from the first alphanumeric character of the noun.
        assertEquals("3A", ClassMark.monogram("BSCE-3A"))
        assertEquals("4B", ClassMark.monogram("BSCE-4Babcdefgh"))
        assertEquals("5A", ClassMark.monogram("BSCE-5A"))
    }

    @Test
    fun theMarkIsNeverBlank() {
        assertEquals("?", ClassMark.monogram(""))
        assertEquals("?", ClassMark.monogram("   "))
        assertEquals("?", ClassMark.monogram(" - "))
    }

    @Test
    fun aTrailingDashFallsBackToTheWordBeforeIt() {
        assertEquals("3R", ClassMark.monogram("Grade 3 - Rizal -"))
        assertEquals("5G", ClassMark.monogram("Grade 5 -   "))
    }

    @Test
    fun theNounIsThePartAfterTheDash() {
        assertEquals("Rizal", ClassMark.noun("Grade 3 - Rizal"))
        assertEquals("Narra", ClassMark.noun("Narra"))
    }
}
