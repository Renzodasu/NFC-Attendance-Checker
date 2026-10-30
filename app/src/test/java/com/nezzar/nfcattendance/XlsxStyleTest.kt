package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.Sheet
import com.nezzar.nfcattendance.data.Style
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** The writer learns widths, merges, row heights and one boxed blank cell. */
class XlsxStyleTest {

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }

    private fun styledWorkbook(): Pair<ByteArray, Map<String, String>> {
        val sheet = Sheet(
            name = "SF2",
            rows = listOf(listOf("School Form 2", ""), listOf("", "x")),
            widths = listOf(5.0, 34.0),
            styles = mapOf("0,0" to Style.TITLE, "1,0" to Style.BOXED),
            merges = listOf("A1:B1"),
            rowHeights = mapOf(0 to 26.0),
        )
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, listOf(sheet))
        val bytes = buffer.toByteArray()
        return bytes to unzip(bytes)
    }

    @Test
    fun writesColumnWidthsRowHeightsMergesAndABoxedBlankCell() {
        val (_, entries) = styledWorkbook()
        val xml = entries["xl/worksheets/sheet1.xml"]!!

        assertTrue(xml.contains("<cols>"))
        assertTrue(xml.contains("<col min=\"1\" max=\"1\" width=\"5.0\" customWidth=\"1\"/>"))
        assertTrue(xml.contains("<col min=\"2\" max=\"2\" width=\"34.0\" customWidth=\"1\"/>"))
        assertTrue(xml.contains("ht=\"26.0\" customHeight=\"1\""))
        assertTrue(xml.contains("<mergeCells count=\"1\">"))
        assertTrue(xml.contains("<mergeCell ref=\"A1:B1\"/>"))
        assertTrue(xml.contains("<c r=\"A1\" s=\"" + Style.TITLE + "\" t=\"inlineStr\">"))
        assertTrue(xml.contains("<c r=\"A2\" s=\"" + Style.BOXED + "\"/>"))
        assertTrue(xml.contains("School Form 2"))
    }

    @Test
    fun theStyledWorkbookCanStillBeReadBackAndItsStylesAreDefined() {
        val (bytes, entries) = styledWorkbook()

        val book = XlsxReader.read(ByteArrayInputStream(bytes))
        assertEquals(listOf("SF2"), book.sheetNames)
        assertEquals("School Form 2", book.sheet("SF2")!![0][0])
        assertEquals("x", book.sheet("SF2")!![1][1])

        assertTrue(entries["xl/styles.xml"]!!.contains("cellXfs count=\"8\""))
    }
}
