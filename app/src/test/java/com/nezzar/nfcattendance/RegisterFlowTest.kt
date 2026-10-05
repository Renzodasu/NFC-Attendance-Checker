package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.RegisterFlow
import com.nezzar.nfcattendance.data.RegisterState
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The register mode state machine: idle -> UID captured -> name -> saved -> re-armed. */
class RegisterFlowTest {

    private val section = Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")))

    @Test
    fun theFullHappyPathEndsReArmedForTheNextCard() {
        var state = RegisterState()
        assertTrue(!state.armed)
        assertNull(state.pendingUid)

        state = RegisterFlow.arm(state, section)
        assertTrue(state.armed)
        assertTrue(state.status.contains("BSCE-4B"))

        state = RegisterFlow.onTap(state, "04D4E5F6", section)
        assertEquals("04D4E5F6", state.pendingUid)
        assertNull(state.duplicateOf)
        assertTrue(state.message.contains("Type the student's name"))

        val (afterSave, sections) = RegisterFlow.save(state, "Ben Cruz", listOf(section), "BSCE-4B")
        assertNull("re-armed: the pending slot must be empty again", afterSave.pendingUid)
        assertTrue("still armed for the next card", afterSave.armed)
        assertEquals(1, afterSave.savedCount)
        assertEquals("Ben Cruz  04D4E5F6", afterSave.lastSaved)
        assertEquals(2, sections[0].students.size)
        assertEquals(Student("Ben Cruz", "04D4E5F6"), sections[0].students[1])
    }

    @Test
    fun aBlankNameIsRefusedAndNothingIsWritten() {
        val armed = RegisterFlow.arm(RegisterState(), section)
        val tapped = RegisterFlow.onTap(armed, "04D4E5F6", section)

        val (refused, sections) = RegisterFlow.save(tapped, "   ", listOf(section), "BSCE-4B")
        assertEquals(1, sections[0].students.size)
        assertTrue(refused.message.contains("blank"))
        assertEquals("the card stays pending so the operator can retry", "04D4E5F6", refused.pendingUid)
        assertEquals(0, refused.savedCount)
    }

    @Test
    fun aDuplicateCardIsFlaggedAndSavingUpdatesThatRowInsteadOfAddingOne() {
        val armed = RegisterFlow.arm(RegisterState(), section)
        val tapped = RegisterFlow.onTap(armed, "04a1b2c3", section)

        assertEquals("04A1B2C3", tapped.pendingUid)
        assertEquals("Ana Reyes", tapped.duplicateOf)
        assertTrue(tapped.message.contains("already registered as Ana Reyes"))

        val (saved, sections) = RegisterFlow.save(tapped, "Ana Reyes-Lim", listOf(section), "BSCE-4B")
        assertEquals(1, sections[0].students.size)
        assertEquals("Ana Reyes-Lim", sections[0].students[0].name)
        assertTrue(saved.message.contains("Updated"))
    }

    @Test
    fun tapsAreIgnoredWhenNotArmedAndWhileNoSectionIsSelected() {
        val idle = RegisterFlow.onTap(RegisterState(), "04D4E5F6", section)
        assertNull(idle.pendingUid)
        assertTrue(idle.message.contains("Not registering"))

        val noSection = RegisterFlow.arm(RegisterState(), null)
        assertTrue(!noSection.armed)
        assertEquals("Create a section first.", noSection.message)

        val armed = RegisterFlow.arm(RegisterState(), section)
        val nowhere = RegisterFlow.onTap(armed, "04D4E5F6", null)
        assertNull(nowhere.pendingUid)
        assertTrue(nowhere.message.contains("Select a section"))
    }

    @Test
    fun savingWithNoCardTappedAndDisarmingBothBehave() {
        val armed = RegisterFlow.arm(RegisterState(), section)
        val (nothing, sections) = RegisterFlow.save(armed, "Ben Cruz", listOf(section), "BSCE-4B")
        assertEquals("No card tapped yet.", nothing.message)
        assertEquals(1, sections[0].students.size)

        val tapped = RegisterFlow.onTap(armed, "04D4E5F6", section)
        val skipped = RegisterFlow.cancelPending(tapped)
        assertNull(skipped.pendingUid)
        assertTrue(skipped.message.contains("skipped"))

        val off = RegisterFlow.disarm(tapped)
        assertTrue(!off.armed)
        assertNull(off.pendingUid)
    }

    @Test
    fun registerModeNeverTouchesAnythingButTheSectionRoster() {
        var sections = listOf(section)
        var state = RegisterFlow.arm(RegisterState(), section)
        state = RegisterFlow.onTap(state, "04D4E5F6", section)
        val saved = RegisterFlow.save(state, "Ben Cruz", sections, "BSCE-4B")
        sections = saved.second
        assertEquals(2, Sections.find(sections, "BSCE-4B")!!.students.size)
    }
}
