package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.ManualKey
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A class read out of the school form arrives with names but no cards, so every
 * learner carries a blank UID. These are the rules that stop a blank UID from
 * being read as "the first unbound learner" - the crash on a duplicate list key
 * and the correction that lands on the wrong child.
 */
class BlankUidTest {

    private fun formRoster() = listOf(
        Student("Ana Cruz", ""),
        Student("Ana Cruz", ""),
        Student("Ben Cruz", ""),
        Student("Cara Lim", "04A1B2C5"),
    )

    private fun section() = Section("Grade 5 - NARRA", formRoster())

    @Test
    fun aNameKeyTellsNamesakesApartAndReadsTheSameTwice() {
        val roster = formRoster()
        val first = ManualKey.of(0, roster)
        val second = ManualKey.of(1, roster)
        assertEquals("name:ana cruz#0", first)
        assertEquals("name:ana cruz#1", second)
        assertEquals(first, ManualKey.of(0, roster))
        assertNotEquals(first, second)
        assertTrue(ManualKey.isManual(first))
        assertFalse(ManualKey.isManual("04A1B2C5"))
    }

    @Test
    fun aKeyDoesNotMoveWhenALaterLearnerIsAdded() {
        val before = listOf(Student("Ana Cruz", ""), Student("Ben Cruz", ""))
        val after = before + Student("Cara Lim", "")
        assertEquals(ManualKey.of(1, before), ManualKey.of(1, after))
    }

    @Test
    fun aHandPresenceForTheSecondNamesakeMarksOnlyThatRow() {
        val roster = formRoster()
        val taps = listOf(Tap(ManualKey.of(1, roster), 5_000L))
        val resolved = AttendanceResolver.resolve(roster, taps, startedAtMillis = 0L)
        assertEquals(1, resolved.present.size)
        assertTrue("the second Ana, not either other row", resolved.present[0].student === roster[1])
        assertEquals(3, resolved.absent.size)
        assertTrue(resolved.unmatched.isEmpty())
    }

    @Test
    fun aManualKeyForNobodyIsUnmatchedAndNeverMarksALearner() {
        val roster = formRoster()
        val stranger = Tap("name:nobody#0", 5_000L)
        val resolved = AttendanceResolver.resolve(roster, listOf(stranger), startedAtMillis = 0L)
        assertEquals(0, resolved.present.size)
        assertEquals(4, resolved.absent.size)
        assertEquals(1, resolved.unmatched.size)
    }

    @Test
    fun aCardStillMatchesTheLearnerWhoHasOne() {
        val roster = formRoster()
        val resolved = AttendanceResolver.resolve(
            roster,
            listOf(Tap("04A1B2C5", 5_000L)),
            startedAtMillis = 0L,
        )
        assertTrue(resolved.present[0].student === roster[3])
        assertEquals(3, resolved.absent.size)
        assertTrue(resolved.unmatched.isEmpty())
    }

    @Test
    fun bindingACardAtARowBindsThatNamesakeOnly() {
        val edit = Sections.bindCardAt(listOf(section()), section().name, 1, "A1B2C3D4")
        assertEquals("", edit.error)
        val students = edit.sections[0].students
        assertEquals("", students[0].uid)
        assertEquals("A1B2C3D4", students[1].uid)
        assertEquals("", students[2].uid)
        assertEquals("04A1B2C5", students[3].uid)
    }

    @Test
    fun aBlankUidLearnerCanBeRenamedAndRemovedByPosition() {
        val sections = listOf(section())
        val renamed = Sections.renameStudentAt(sections, section().name, 0, "Ana Reyes")
        assertEquals("", renamed.error)
        assertEquals("Ana Reyes", renamed.sections[0].students[0].name)
        assertEquals("Ana Cruz", renamed.sections[0].students[1].name)

        // The old by-UID path is what refused this exact learner.
        val refused = Sections.renameStudent(sections, section().name, "", "X")
        assertTrue(refused.error.isNotEmpty())

        val removed = Sections.removeStudentAt(sections, section().name, 3)
        assertEquals("", removed.error)
        assertEquals(3, removed.sections[0].students.size)
    }

    @Test
    fun aRowThatIsGoneCannotBeEdited() {
        val sections = listOf(section())
        assertTrue(Sections.renameStudentAt(sections, section().name, 9, "X").error.isNotEmpty())
        assertTrue(Sections.removeStudentAt(sections, section().name, 9).error.isNotEmpty())
        assertTrue(Sections.bindCardAt(sections, section().name, 9, "A1B2C3D4").error.isNotEmpty())
    }
}
