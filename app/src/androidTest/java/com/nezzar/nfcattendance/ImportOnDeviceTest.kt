package com.nezzar.nfcattendance

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Store
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.XlsxWriter
import com.nezzar.nfcattendance.ui.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the very same import path the Import button uses - AppState.stageImport
 * then AppState.confirmImport - against a real file on the device, and proves the
 * merge rules hold with the app's own storage.
 */
@RunWith(AndroidJUnit4::class)
class ImportOnDeviceTest {

    private val stamp = 1_767_225_600_000L

    /** What another phone would have exported and handed over. */
    private fun sharedFile(name: String): File {
        val buffer = java.io.ByteArrayOutputStream()
        XlsxWriter.write(
            buffer,
            ReportBuilder.rosterSheets(
                Section(
                    "BSCE-4B",
                    listOf(
                        Student("Ana Reyes", "04A1B2C3"),
                        Student("Ben Cruz", "04D4E5F6"),
                    ),
                    stamp,
                )
            ),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        file.writeBytes(buffer.toByteArray())
        return file
    }

    @Test
    fun aSharedWorkbookImportsThroughTheSameCodePathTheButtonUses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Store(context).saveSections(emptyList())
        val file = sharedFile("shared-roster-" + System.currentTimeMillis() + ".xlsx")
        val state = AppState(context)

        state.stageImport(Uri.fromFile(file))
        assertEquals("", state.importError)
        assertNotNull(state.importedFile)
        assertEquals("BSCE-4B", state.importTargetName)

        val plan = state.importPlan!!
        assertEquals("", plan.error)
        assertEquals(2, plan.added.size)
        assertTrue(plan.keptLocally.isEmpty())

        state.confirmImport()
        assertEquals("", state.importError)
        assertEquals(
            "Imported into BSCE-4B: added 2, names updated 0, unchanged 0, kept (not in file) 0, skipped rows 0.",
            state.sectionsMessage,
        )

        val stored = Store(context).loadSections()
        val section = Sections.find(stored, "BSCE-4B")
        assertNotNull("the imported section must be persisted", section)
        assertEquals(listOf("Ana Reyes", "Ben Cruz"), section!!.students.map { it.name })
        assertEquals(listOf("04A1B2C3", "04D4E5F6"), section.students.map { it.uid })
        assertEquals(stamp, section.updatedAt)
    }

    @Test
    fun importingTheSameFileTwiceChangesNothingTheSecondTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Store(context).saveSections(emptyList())
        val file = sharedFile("shared-again-" + System.currentTimeMillis() + ".xlsx")

        val first = AppState(context)
        first.stageImport(Uri.fromFile(file))
        first.confirmImport()
        assertEquals("", first.importError)

        val second = AppState(context)
        second.stageImport(Uri.fromFile(file))
        val plan = second.importPlan!!
        assertEquals("", plan.error)
        assertTrue("nothing new the second time", plan.added.isEmpty())
        assertEquals(2, plan.unchanged.size)
        second.confirmImport()

        val section = Sections.find(Store(context).loadSections(), "BSCE-4B")
        assertEquals(2, section!!.students.size)
        assertEquals("the file's date still wins - nothing newer exists", stamp, section.updatedAt)
        assertEquals(
            "Imported into BSCE-4B: added 0, names updated 0, unchanged 2, kept (not in file) 0, skipped rows 0.",
            second.sectionsMessage,
        )
    }
}
