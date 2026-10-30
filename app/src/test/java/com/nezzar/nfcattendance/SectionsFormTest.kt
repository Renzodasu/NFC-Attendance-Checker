package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Sf2Header
import com.nezzar.nfcattendance.data.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Importing a school form into a class, then binding cards to the learners it brought. */
class SectionsFormTest {

    private val header = Sf2Header("123456", "Sample ES", "2026 - 2027", "Grade 5", "NARRA")

    private fun learners(): List<Student> = listOf(
        Student("Ana Cruz", "", "F"),
        Student("Ben Reyes", "", "M"),
    )

    private fun imported(now: Long = 42L): List<Section> =
        Sections.importForm(emptyList(), "Grade 5 - NARRA", learners(), header, now = now).sections

    @Test
    fun importFormCreatesAClassWithNoCardsBound() {
        val edit = Sections.importForm(emptyList(), "Grade 5 - NARRA", learners(), header, now = 42L)

        assertEquals("", edit.error)
        val created = edit.sections.single()
        assertEquals("Grade 5 - NARRA", created.name)
        assertEquals(listOf("", ""), created.students.map { it.uid })
        assertEquals(header, created.sf2)
        assertEquals(42L, created.updatedAt)
        assertEquals("Imported 2 learner(s) into Grade 5 - NARRA - no cards bound yet.", edit.message)
    }

    @Test
    fun importingTheSameFormAgainAddsOnlyTheNamesItDoesNotHave() {
        val first = imported()
        val more = learners() + Student("Cara Lim", "", "F")

        val second = Sections.importForm(first, "Grade 5 - NARRA", more, header, now = 99L)
        assertEquals("", second.error)
        assertEquals(3, second.sections.single().students.size)
        assertEquals(99L, second.sections.single().updatedAt)
        assertEquals("Added 1 learner(s) to Grade 5 - NARRA - no cards bound yet.", second.message)

        val third = Sections.importForm(second.sections, "Grade 5 - NARRA", more, header, now = 123L)
        assertEquals("Grade 5 - NARRA already has every learner from that form.", third.message)
        assertEquals(99L, third.sections.single().updatedAt)
    }

    @Test
    fun bindCardFillsTheBlankUidAndStampsTheRoster() {
        val edit = Sections.bindCard(imported(), "Grade 5 - NARRA", "Ana Cruz", "a1b2c3d4", now = 77L)

        assertEquals("", edit.error)
        val section = edit.sections.single()
        assertEquals("A1B2C3D4", section.students.first { it.name == "Ana Cruz" }.uid)
        assertEquals("", section.students.first { it.name == "Ben Reyes" }.uid)
        assertEquals(77L, section.updatedAt)
        assertEquals("Bound A1B2C3D4 to Ana Cruz.", edit.message)
    }

    @Test
    fun bindCardRefusesACardThatIsAlreadyRegistered() {
        val sections = listOf(
            Section("Grade 5 - NARRA", learners(), sf2 = header),
            Section("Grade 5 - Sampaguita", listOf(Student("Zoe Tan", "A1B2C3D4"))),
        )

        val edit = Sections.bindCard(sections, "Grade 5 - NARRA", "Ana Cruz", "A1B2C3D4")

        assertEquals("That card is already registered to Zoe Tan in Grade 5 - Sampaguita.", edit.error)
        assertEquals(sections, edit.sections)
    }

    @Test
    fun bindCardRefusesANameThatIsNotWaitingAndABrokenUid() {
        val sections = listOf(Section("Grade 5 - NARRA", learners(), sf2 = header))

        val unknown = Sections.bindCard(sections, "Grade 5 - NARRA", "Nina Roy", "A1B2C3D4")
        assertEquals("No learner called Nina Roy is waiting for a card.", unknown.error)

        val broken = Sections.bindCard(sections, "Grade 5 - NARRA", "Ana Cruz", "not-a-uid")
        assertEquals("That card did not read as a whole UID.", broken.error)
    }

    @Test
    fun registerRefusesABlankUidBecauseNoCardIsNotACard() {
        val edit = Sections.register(imported(), "Grade 5 - NARRA", "", "Zed Aquino")

        assertEquals("That card did not read as a whole UID.", edit.error)
        assertEquals(2, edit.sections.single().students.size)
    }

    @Test
    fun unboundListsOnlyTheLearnersWithoutACard() {
        val section = Section(
            "Grade 5 - NARRA",
            learners() + Student("Zoe Tan", "A1B2C3D4"),
            sf2 = header,
        )

        assertEquals(listOf("Ana Cruz", "Ben Reyes"), Sections.unbound(section).map { it.name })
        assertTrue(Sections.unbound(null).isEmpty())
    }
}
