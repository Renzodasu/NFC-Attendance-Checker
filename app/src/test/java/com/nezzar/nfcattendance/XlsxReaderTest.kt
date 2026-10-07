package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The .xlsx reader against both shapes it must handle: this app's own writer
 * (inline strings) and a workbook re-saved by Excel or Google Sheets
 * (sharedStrings + <c t="s"><v>index</v></c>).
 */
class XlsxReaderTest {

    private fun zipped(parts: Map<String, String>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            for ((name, body) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    /** A workbook shaped like one Excel writes: shared strings, a prefix, a number cell, a row gap. */
    private fun excelStyleWorkbook(): ByteArray {
        val contentTypes = "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>"
        val rootRels = "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        val workbook = "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
            "<x:sheet xmlns:x=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" name=\"Roster\" sheetId=\"1\" r:id=\"rId1\"/>" +
            "</sheets></workbook>"
        val rels = "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>" +
            "</Relationships>"
        val shared = "<?xml version=\"1.0\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"4\" uniqueCount=\"4\">" +
            "<si><t>REGISTERED ROSTER - BSCE-4B</t></si>" +
            "<si><t>Ana Reyes</t></si>" +
            "<si><r><t>Ben </t></r><r><t>Cruz</t></r></si>" +
            "<si><t>Reyes &amp; Co</t></si>" +
            "</sst>"
        val sheet = "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<dimension ref=\"A1:D7\"/><sheetData>" +
            "<row r=\"1\" spans=\"1:4\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>" +
            "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c><c r=\"B2\" t=\"s\"><v>3</v></c></row>" +
            "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>2</v></c><c r=\"B3\"><v>42</v></c></row>" +
            "<row r=\"4\"><c r=\"A4\"/><c r=\"C4\" t=\"inlineStr\"><is><t xml:space=\"preserve\">  spaced  </t></is></c></row>" +
            "<row r=\"7\"><c r=\"A7\" t=\"inlineStr\"><is><t><![CDATA[<raw>]]></t></is></c></row>" +
            "</sheetData></worksheet>"
        return zipped(
            mapOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "xl/workbook.xml" to workbook,
                "xl/_rels/workbook.xml.rels" to rels,
                "xl/sharedStrings.xml" to shared,
                "xl/worksheets/sheet1.xml" to sheet,
            )
        )
    }

    @Test
    fun readsAWorkbookSavedByExcelWithSharedStrings() {
        val book = XlsxReader.read(ByteArrayInputStream(excelStyleWorkbook()))

        assertEquals(listOf("Roster"), book.sheetNames)
        val rows = book.sheet("roster")
        assertNotNull(rows)
        val sheet = rows!!

        assertEquals("row 1", "REGISTERED ROSTER - BSCE-4B", sheet[0][0])
        assertEquals("shared string", "Ana Reyes", sheet[1][0])
        assertEquals("second column", "Reyes & Co", sheet[1][1])
        assertEquals("runs inside one si are concatenated", "Ben Cruz", sheet[2][0])
        assertEquals("a plain number cell stays text", "42", sheet[2][1])
        assertEquals("xml:space is preserved", "  spaced  ", sheet[3][2])
        assertEquals("an empty cell is an empty string", "", sheet[3][0])

        assertEquals("a row gap is padded so row numbers stay honest", 7, sheet.size)
        assertEquals("CDATA is read literally", "<raw>", sheet[6][0])

        assertNull(book.sheet("Absent"))
    }

    @Test
    fun readsBackWhatThisAppWrites() {
        val section = Section(
            "BSCE-4B",
            listOf(Student("Ana Reyes", "04A1B2C3"), Student("Ben & Cruz", "04D4E5F6")),
            1_767_225_600_000L,
        )
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.rosterSheets(section))

        val book = XlsxReader.read(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(listOf("Roster"), book.sheetNames)
        val rows = book.sheet("Roster")!!

        assertEquals("REGISTERED ROSTER - BSCE-4B", rows[0][0])
        assertEquals(ReportBuilder.LABEL_SECTION_NAME, rows[1][0])
        assertEquals("BSCE-4B", rows[1][1])
        assertEquals(ReportBuilder.LABEL_DATE_UPDATED, rows[4][0])
        assertEquals(ReportBuilder.LABEL_SUBJECT, rows[2][0])
        assertEquals(ReportBuilder.dateUpdatedText(1_767_225_600_000L), rows[4][1])
        // One blank spacer row sits between the header block and the table.
        assertEquals("Name", rows[6][0])
        assertEquals("UID", rows[6][1])
        assertEquals("Ana Reyes", rows[7][0])
        assertEquals("Ben & Cruz", rows[8][0])
        assertEquals("entities survive the round trip", "04D4E5F6", rows[8][1])

        val parsed = RosterImporter.parse(rows)
        assertEquals("", parsed.error)
        assertEquals("BSCE-4B", parsed.sectionName)
        assertEquals(1_767_225_600_000L, parsed.updatedAt)
        assertEquals(listOf("Ana Reyes", "Ben & Cruz"), parsed.rows.map { it.name })
    }

    @Test
    fun readsTheThreeSheetSessionReportInOrder() {
        val session = com.nezzar.nfcattendance.data.AttendanceSession(
            sessionId = "S-READER-1",
            sectionName = "BSCE-4B",
            startedAtMillis = 1_767_225_600_000L,
            roster = listOf(
                Student("Ana Reyes", "04A1B2C3"),
                Student("Ben Cruz", "04D4E5F6"),
            ),
            taps = listOf(com.nezzar.nfcattendance.data.Tap("04A1B2C3", 1_767_225_601_000L)),
        )
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.sheets(session))

        val book = XlsxReader.read(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(listOf("Absent", "Late", "Present", "Unmatched"), book.sheetNames)
        val absent = book.sheet("Absent")!!
        // The Late sheet exists even when nobody was late - the shape must not move.
        assertEquals(listOf("Name", "UID", "Date", "Time", "Method"), book.sheet("Late")!![1])
        assertEquals("ABSENT - the conclusion (roster minus present)", absent[0][0])
        assertEquals("Ben Cruz", absent[absent.size - 1][0])
        val present = book.sheet("Present")!!
        // Every scanned card carries the date and the time it was read.
        assertEquals(listOf("Name", "UID", "Date", "Time", "Method"), present[1])
        assertEquals("Ana Reyes", present[2][0])
        assertEquals(ReportBuilder.dateText(1_767_225_601_000L), present[2][2])
        assertEquals(ReportBuilder.timeText(1_767_225_601_000L), present[2][3])
        assertTrue(book.sheet("Unmatched")!![0][0].startsWith("UNMATCHED"))
    }


    @Test
    fun aCardReadAfterTheWindowLandsInItsOwnLateSheet() {
        val start = 1_700_000_000_000L
        val session = AttendanceSession(
            sessionId = "S-DEMO-LATE",
            sectionName = "BSCE-4B",
            startedAtMillis = start,
            roster = listOf(Student("Ana Reyes", "04A1B2C3"), Student("Ben Cruz", "04A1B2C4")),
            taps = listOf(
                com.nezzar.nfcattendance.data.Tap("04A1B2C3", start + 60_000L),
                com.nezzar.nfcattendance.data.Tap("04A1B2C4", start + 18L * 60_000L),
            ),
        )
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.sheets(session))
        val book = XlsxReader.read(ByteArrayInputStream(buffer.toByteArray()))

        assertEquals(listOf("Absent", "Late", "Present", "Unmatched"), book.sheetNames)
        val late = book.sheet("Late")!!
        assertEquals(listOf("Name", "UID", "Date", "Time", "Method"), late[1])
        assertEquals("Ben Cruz", late[2][0])
        assertEquals(ReportBuilder.dateText(start + 18L * 60_000L), late[2][2])
        assertEquals(ReportBuilder.timeText(start + 18L * 60_000L), late[2][3])

        // Keep the bytes on disk so the workbook can be opened and inspected.
        val outDir = File("build/nfc-artifacts").apply { mkdirs() }
        File(outDir, "attendance-S-DEMO-LATE.xlsx").writeBytes(buffer.toByteArray())
    }

    @Test
    fun aHandMadeCorrectionIsNotReportedAsAScannedCard() {
        val start = 1_700_000_000_000L
        val session = AttendanceSession(
            sessionId = "S-DEMO-MANUAL",
            sectionName = "BSCE-4B",
            startedAtMillis = start,
            roster = listOf(Student("Ana Reyes", "04A1B2C3"), Student("Ben Cruz", "04A1B2C4")),
            taps = listOf(
                com.nezzar.nfcattendance.data.Tap("04A1B2C3", start + 60_000L),
                // The card was broken, so a teacher marked this one by hand.
                com.nezzar.nfcattendance.data.Tap(
                    "04A1B2C4",
                    start + 120_000L,
                    com.nezzar.nfcattendance.data.AttendanceMethod.MANUAL,
                ),
            ),
        )
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.sheets(session))
        val book = XlsxReader.read(ByteArrayInputStream(buffer.toByteArray()))

        val present = book.sheet("Present")!!
        assertEquals("NFC", present[2][4])
        assertEquals("Manual", present[3][4])
        // And the conclusion sheet says how many presences were recorded by hand.
        assertTrue(
            book.sheet("Absent")!!.any { row -> row.size >= 2 && row[0] == "Recorded by hand" && row[1] == "1" },
        )
    }

    @Test
    fun refusesFilesThatAreNotThisKindOfWorkbook() {
        val csv = "name,uid\nAna Reyes,04A1B2C3\n".toByteArray()
        try {
            XlsxReader.read(ByteArrayInputStream(csv))
            throw AssertionError("a CSV must not be accepted as a workbook")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("not an .xlsx workbook"))
        }

        val zipWithoutWorkbook = zipped(mapOf("hello.txt" to "not a spreadsheet"))
        try {
            XlsxReader.read(ByteArrayInputStream(zipWithoutWorkbook))
            throw AssertionError("a zip without xl/workbook.xml must not be accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("not an .xlsx workbook"))
        }

        val noRosterSheet = zipped(
            mapOf(
                "xl/workbook.xml" to
                    "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
                    "<sheet name=\"Attendance\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
                "xl/worksheets/sheet1.xml" to
                    "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                    "<sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>hi</t></is></c></row></sheetData></worksheet>",
            )
        )
        val book = XlsxReader.read(ByteArrayInputStream(noRosterSheet))
        assertEquals(listOf("Attendance"), book.sheetNames)
        assertNull("a workbook with no Roster sheet cannot be imported", book.sheet(RosterImporter.SHEET_ROSTER))
    }

    @Test
    fun columnReferencesAreDecodedLikeASpreadsheet() {
        assertEquals(0, XlsxReader.columnIndex("A1"))
        assertEquals(1, XlsxReader.columnIndex("B12"))
        assertEquals(25, XlsxReader.columnIndex("Z3"))
        assertEquals(26, XlsxReader.columnIndex("AA3"))
        assertEquals(27, XlsxReader.columnIndex("AB1"))
    }

    @Test
    fun theXmlScannerDecodesEntitiesAndIgnoresComments() {
        assertEquals("Ana & Reyes", com.nezzar.nfcattendance.data.XmlLite.decode("Ana &amp; Reyes"))
        assertEquals("<b>", com.nezzar.nfcattendance.data.XmlLite.decode("&lt;b&gt;"))
        assertEquals("A", com.nezzar.nfcattendance.data.XmlLite.decode("&#65;"))
        assertEquals("B", com.nezzar.nfcattendance.data.XmlLite.decode("&#x42;"))
        assertEquals("&unknown;", com.nezzar.nfcattendance.data.XmlLite.decode("&unknown;"))

        val scanner = com.nezzar.nfcattendance.data.XmlLite("<a><!-- skip me --><b x=\"1\">t</b></a>")
        val events = mutableListOf<String>()
        while (true) {
            val event = scanner.next()
            if (event is com.nezzar.nfcattendance.data.XmlLite.Event.Eof) break
            when (event) {
                is com.nezzar.nfcattendance.data.XmlLite.Event.Start ->
                    events += "start:" + event.name + ":" + (event.attributes["x"] ?: "")
                is com.nezzar.nfcattendance.data.XmlLite.Event.End -> events += "end:" + event.name
                is com.nezzar.nfcattendance.data.XmlLite.Event.Text -> events += "text:" + event.text
                else -> {}
            }
        }
        assertEquals(listOf("start:a:", "start:b:1", "text:t", "end:b", "end:a"), events)
    }
}