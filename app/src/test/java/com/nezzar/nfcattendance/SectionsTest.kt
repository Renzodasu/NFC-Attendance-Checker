package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section store rules: create / select / rename / delete, duplicate-UID updates and the date stamp. */
class SectionsTest {

    private fun twoSections(): List<Section> = listOf(
        Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3"))),
        Section("BSCE-5A", emptyList()),
    )

    @Test
    fun createAddsANamedSectionAndSelectFindsIt() {
        val created = Sections.create(emptyList(), " BSCE-4B ")
        assertEquals("", created.error)
        assertEquals(listOf("BSCE-4B"), created.sections.map { it.name })
        assertEquals("Section BSCE-4B created.", created.message)

        val found = Sections.find(created.sections, "bsce-4b")
        assertEquals("BSCE-4B", found?.name)
        assertNull(Sections.find(created.sections, "BSCE-9Z"))
    }

    @Test
    fun createRefusesABlankNameAndADuplicateName() {
        val blank = Sections.create(twoSections(), "   ")
        assertTrue(blank.error.isNotEmpty())
        assertEquals(2, blank.sections.size)

        val duplicate = Sections.create(twoSections(), "bsce-5a")
        assertEquals("A section named bsce-5a already exists.", duplicate.error)
        assertEquals(2, duplicate.sections.size)
    }

    @Test
    fun renameChangesTheNameAndRefusesClashes() {
        val renamed = Sections.rename(twoSections(), "BSCE-4B", "BSCE-4C")
        assertEquals("", renamed.error)
        assertEquals(listOf("BSCE-4C", "BSCE-5A"), renamed.sections.map { it.name })
        assertEquals(1, renamed.sections[0].students.size)

        val clash = Sections.rename(twoSections(), "BSCE-4B", "BSCE-5A")
        assertTrue(clash.error.isNotEmpty())
        assertEquals(listOf("BSCE-4B", "BSCE-5A"), clash.sections.map { it.name })

        val blank = Sections.rename(twoSections(), "BSCE-4B", " ")
        assertTrue(blank.error.isNotEmpty())

        val unknown = Sections.rename(twoSections(), "NOPE", "X")
        assertTrue(unknown.error.isNotEmpty())
    }

    @Test
    fun deleteDropsOnlyTheNamedSection() {
        val deleted = Sections.delete(twoSections(), "BSCE-4B")
        assertEquals("", deleted.error)
        assertEquals(listOf("BSCE-5A"), deleted.sections.map { it.name })

        val unknown = Sections.delete(twoSections(), "NOPE")
        assertTrue(unknown.error.isNotEmpty())
        assertEquals(2, unknown.sections.size)
    }

    @Test
    fun registerAppendsANewRowWithNameAndUidOnly() {
        val edit = Sections.register(twoSections(), "BSCE-4B", "04d4e5f6", "Ben Cruz")
        assertEquals("", edit.error)
        val students = edit.sections.first { it.name == "BSCE-4B" }.students
        assertEquals(2, students.size)
        assertEquals(Student("Ben Cruz", "04D4E5F6"), students[1])
        assertEquals("Registered Ben Cruz (04D4E5F6) in BSCE-4B.", edit.message)
    }

    @Test
    fun registerOfAnExistingUidUpdatesTheNameAndNeverAddsASecondRow() {
        val edit = Sections.register(twoSections(), "BSCE-4B", "04A1B2C3", "Ana R. Reyes")
        assertEquals("", edit.error)
        val students = edit.sections.first { it.name == "BSCE-4B" }.students
        assertEquals(1, students.size)
        assertEquals("Ana R. Reyes", students[0].name)
        assertEquals("04A1B2C3", students[0].uid)

        assertEquals("Ana R. Reyes", Sections.registeredName(edit.sections[0], "04a1b2c3"))
        assertNull(Sections.registeredName(edit.sections[0], "04FFFFFF"))
    }

    @Test
    fun registerRefusesABlankNameAndAMissingSection() {
        val blank = Sections.register(twoSections(), "BSCE-4B", "04D4E5F6", "   ")
        assertTrue(blank.error.isNotEmpty())
        assertEquals(1, blank.sections.first { it.name == "BSCE-4B" }.students.size)

        val missing = Sections.register(twoSections(), "NOPE", "04D4E5F6", "Ben Cruz")
        assertEquals("Select a section first.", missing.error)
        assertEquals(2, missing.sections.size)
    }

    @Test
    fun aStudentCanBeRenamedAndRemoved() {
        val renamed = Sections.renameStudent(twoSections(), "BSCE-4B", "04A1B2C3", "Ana Reyes-Lim")
        assertEquals("Ana Reyes-Lim", renamed.sections[0].students[0].name)

        val removed = Sections.removeStudent(twoSections(), "BSCE-4B", "04a1b2c3")
        assertEquals(0, removed.sections[0].students.size)
        assertEquals("only the named section is touched", listOf("BSCE-5A"), removed.sections.drop(1).map { it.name })

        val nothing = Sections.removeStudent(twoSections(), "BSCE-4B", "04FFFFFF")
        assertTrue(nothing.error.isNotEmpty())
    }

    // ------------------------------------------------------------ date updated

    @Test
    fun creatingASectionStampsItButRenamingTheSectionDoesNot() {
        val created = Sections.create(emptyList(), "BSCE-4B", 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, created.sections[0].updatedAt)

        val renamed = Sections.rename(created.sections, "BSCE-4B", "BSCE-4C")
        assertEquals("BSCE-4C", renamed.sections[0].name)
        assertEquals("renaming a section does not change its roster", 1_700_000_000_000L,
            renamed.sections[0].updatedAt)
    }

    @Test
    fun rosterEditsStampTheDate() {
        val start = listOf(Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")), 1000L))

        val registered = Sections.register(start, "BSCE-4B", "04D4E5F6", "Ben Cruz", 2000L)
        assertEquals(2000L, registered.sections[0].updatedAt)

        val renamed = Sections.renameStudent(start, "BSCE-4B", "04A1B2C3", "Ana R. Reyes", 3000L)
        assertEquals(3000L, renamed.sections[0].updatedAt)

        val removed = Sections.removeStudent(start, "BSCE-4B", "04A1B2C3", 4000L)
        assertEquals(4000L, removed.sections[0].updatedAt)
    }

    @Test
    fun aSaveThatChangesNothingLeavesTheDateAlone() {
        val start = listOf(Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")), 1000L))

        val sameRegistration = Sections.register(start, "BSCE-4B", "04a1b2c3", "Ana Reyes", 9000L)
        assertEquals(1000L, sameRegistration.sections[0].updatedAt)

        val sameRename = Sections.renameStudent(start, "BSCE-4B", "04A1B2C3", "Ana Reyes", 9000L)
        assertEquals(1000L, sameRename.sections[0].updatedAt)

        val refused = Sections.register(start, "BSCE-4B", "04D4E5F6", "   ", 9000L)
        assertTrue(refused.error.isNotEmpty())
        assertEquals(1000L, refused.sections[0].updatedAt)
    }
}
