package com.nezzar.nfcattendance

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nezzar.nfcattendance.data.DocumentsExport
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** The export destination on a real device: the shared Documents folder. */
@RunWith(AndroidJUnit4::class)
class DocumentsExportTest {

    private fun workbook(): ByteArray {
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(
            buffer,
            ReportBuilder.rosterSheets(
                Section(
                    "BSCE-4B",
                    listOf(Student("Ana Reyes", "04A1B2C3"), Student("Ben Cruz", "04D4E5F6")),
                    1_767_225_600_000L,
                )
            ),
        )
        return buffer.toByteArray()
    }

    @Test
    fun aWorkbookLandsInTheSharedDocumentsFolderWithoutAnyStoragePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("this device can write to Documents directly", DocumentsExport.supported())

        val fileName = "device-check-" + System.currentTimeMillis() + ".xlsx"
        val body = workbook()
        val written = DocumentsExport.save(context, fileName) { out -> out.write(body) }
        try {
            assertTrue(written.uri.toString().startsWith("content://"))
            assertEquals("/storage/emulated/0/Documents/Presencia/" + fileName, written.displayPath)

            val file = File(written.displayPath)
            assertTrue("the file must exist at " + written.displayPath, file.isFile)
            assertEquals(body.size.toLong(), file.length())

            val book = context.contentResolver.openInputStream(written.uri)!!.use { XlsxReader.read(it) }
            assertEquals(listOf("Roster"), book.sheetNames)
            assertEquals("Ana Reyes", book.sheet("Roster")!![6][0])

            context.contentResolver.query(written.uri, null, null, null, null)?.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val relative = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                assertTrue(relative >= 0)
                assertEquals(
                    Environment.DIRECTORY_DOCUMENTS + "/" + DocumentsExport.FOLDER + "/",
                    cursor.getString(relative),
                )
            }
        } finally {
            context.contentResolver.delete(written.uri, null, null)
        }
    }

    @Test
    fun reExportingTheSameFileNameReplacesItInsteadOfStackingCopies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "device-replace-" + System.currentTimeMillis() + ".xlsx"
        val body = workbook()

        val stem = fileName.substringBeforeLast('.')

        val first = DocumentsExport.save(context, fileName) { it.write(body) }
        val second = DocumentsExport.save(context, fileName) { it.write(body) }
        try {
            // The name MediaStore actually assigned must be the requested one...
            assertEquals(fileName, File(second.displayPath).name)
            assertEquals(first.displayPath, second.displayPath)

            // ...and the folder must hold exactly one row for this export, counting the
            // "(1)" names MediaStore invents when a surviving copy forces a rename.
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            var copies = 0
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(Environment.DIRECTORY_DOCUMENTS + "/" + DocumentsExport.FOLDER + "/"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: ""
                    if (name == fileName || (name.startsWith(stem + " (") && name.endsWith(".xlsx"))) {
                        copies++
                    }
                }
            }
            assertEquals("exactly one copy of " + fileName, 1, copies)
        } finally {
            context.contentResolver.delete(first.uri, null, null)
            context.contentResolver.delete(second.uri, null, null)
        }
    }
}
