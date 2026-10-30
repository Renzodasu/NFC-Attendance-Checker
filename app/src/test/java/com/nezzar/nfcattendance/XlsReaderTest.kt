package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.XlsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Builds real OLE2 containers and real BIFF streams byte by byte, so the
 * reader meets the same shape Excel writes rather than a convenient stub.
 */
class XlsReaderTest {

    @Test
    fun readsTheSheetNameAndItsCells() {
        val book = XlsReader.read(buildFile())
        assertEquals(listOf("Roster"), book.sheetNames)

        val rows = book.sheet("roster")!!
        assertEquals(listOf("Name", "", "", ""), rows[0])
        assertEquals(listOf("Ana Cruz", "8", "", ""), rows[1])
        assertEquals(listOf("", "", "3", "4"), rows[2])
        assertEquals(listOf("12.5", "", "", ""), rows[3])
        assertEquals(listOf("99", "", "", ""), rows[4])
        assertEquals(listOf("late", "", "", ""), rows[5])
    }

    @Test
    fun continuesASharedStringAcrossRecords() {
        val book = XlsReader.read(buildFile(splitAt = 5))
        assertEquals("Narra", book.sheet("Roster")!![0][0])
    }

    @Test
    fun aWholeNumberNeverGainsADecimalTail() {
        val rows = XlsReader.read(buildFile()).sheet("Roster")!!
        assertEquals("8", rows[1][1])
        assertEquals("99", rows[4][0])
        assertFalse(rows[1][1].contains("."))
    }

    @Test
    fun refusesSomethingThatIsNotAnOleFile() {
        val bytes = "Name,UID\nAna,04A1B2C3\n".toByteArray()
        assertFalse(XlsReader.isXls(bytes))
        try {
            XlsReader.read(bytes)
            fail("A text file must not parse as a workbook.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains(".xls"))
        }
    }

    @Test
    fun refusesAnOleFileWithoutAWorkbookStream() {
        val file = buildOle(ByteArray(4096), streamName = "NotAWorkbook")
        try {
            XlsReader.read(file)
            fail("A container without a Workbook stream must be refused.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Workbook"))
        }
    }

    @Test
    fun refusesAWorkbookWithoutAnyWorksheet() {
        val globals = Records()
        globals.record(BOF, bofBody(0x0005))
        globals.record(EOF, ByteArray(0))
        try {
            XlsReader.read(buildOle(pad(globals.bytes()), streamName = "Workbook"))
            fail("A workbook with no sheet must be refused.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("worksheet"))
        }
    }

    @Test
    fun readsTwoSheetsAndTheRosterSheetImports() {
        val book = XlsReader.read(buildTwoSheetFile())
        assertEquals(listOf("Roster", "Notes"), book.sheetNames)

        val roster = book.sheet("roster")!!
        val parsed = com.nezzar.nfcattendance.data.RosterImporter.parse(roster)
        assertEquals("Grade 5 - Narra", parsed.sectionName)
        assertEquals("Math", parsed.subject)
        assertEquals(1, parsed.rows.size)
        assertEquals("Ana Cruz", parsed.rows[0].name)
        assertEquals("04A1B2C3", parsed.rows[0].uid)
        assertEquals(2, parsed.skipped.size)
        assertEquals("the UID is not 8+ hex characters: not-a-uid", parsed.skipped[0].reason)
        assertEquals("the UID is blank", parsed.skipped[1].reason)
    }

    // ------------------------------------------------------------ the workbook

    private fun buildFile(splitAt: Int = 0): ByteArray {
        val sheet = sheetStream()
        val globals = Records()
        globals.record(BOF, bofBody(0x0005))
        val boundsheet = globals.record(BOUNDSHEET, boundsheetBody(0, "Roster"))
        val text = listOf("Name", "Ana Cruz", "Narra")
        if (splitAt <= 0) {
            globals.record(SST, sstBody(text))
        } else {
            val (head, tail) = splitSst(text, splitAt)
            globals.record(SST, head)
            globals.record(CONTINUE, tail)
        }
        globals.record(EOF, ByteArray(0))

        val stream = globals.bytes()
        // lbPlyPos is the worksheet substream's offset inside the stream.
        putLe32(stream, boundsheet + 4, stream.size)
        return buildOle(pad(stream + sheet), streamName = "Workbook")
    }

    private fun sheetStream(): ByteArray {
        val out = Records()
        out.record(BOF, bofBody(0x0010))
        out.record(LABELSST, labelSst(0, 0, 0))
        out.record(LABELSST, labelSst(1, 0, 1))
        out.record(RK, rkCell(1, 1, integerRk(8)))
        out.record(MULRK, mulRk(2, 2, listOf(integerRk(3), integerRk(4))))
        out.record(NUMBER, numberCell(3, 0, 12.5))
        out.record(FORMULA, formulaNumber(4, 0, 99.0))
        out.record(FORMULA, formulaString(5, 0))
        out.record(STRING, textBody("late"))
        out.record(EOF, ByteArray(0))
        return out.bytes()
    }

    private fun buildTwoSheetFile(): ByteArray {
        val strings = listOf(
            "Section name", "Grade 5 - Narra", "Subject", "Math",
            "Name", "UID", "Ana Cruz", "04A1B2C3", "Ben Cruz", "not-a-uid", "Cy", "Keep this line",
        )
        val roster = Records()
        roster.record(BOF, bofBody(0x0010))
        roster.record(LABELSST, labelSst(0, 0, 0))
        roster.record(LABELSST, labelSst(0, 1, 1))
        roster.record(LABELSST, labelSst(1, 0, 2))
        roster.record(LABELSST, labelSst(1, 1, 3))
        roster.record(LABELSST, labelSst(3, 0, 4))
        roster.record(LABELSST, labelSst(3, 1, 5))
        roster.record(LABELSST, labelSst(4, 0, 6))
        roster.record(LABELSST, labelSst(4, 1, 7))
        roster.record(LABELSST, labelSst(5, 0, 8))
        roster.record(LABELSST, labelSst(5, 1, 9))
        roster.record(LABELSST, labelSst(6, 0, 10))
        roster.record(EOF, ByteArray(0))
        val first = roster.bytes()

        val notes = Records()
        notes.record(BOF, bofBody(0x0010))
        notes.record(LABELSST, labelSst(0, 0, 11))
        notes.record(EOF, ByteArray(0))
        val second = notes.bytes()

        val globals = Records()
        globals.record(BOF, bofBody(0x0005))
        val firstSheet = globals.record(BOUNDSHEET, boundsheetBody(0, "Roster"))
        val secondSheet = globals.record(BOUNDSHEET, boundsheetBody(0, "Notes"))
        globals.record(SST, sstBody(strings))
        globals.record(EOF, ByteArray(0))

        val stream = globals.bytes()
        putLe32(stream, firstSheet + 4, stream.size)
        putLe32(stream, secondSheet + 4, stream.size + first.size)
        return buildOle(pad(stream + first + second), streamName = "Workbook")
    }

    // -------------------------------------------------------------- BIFF bits

    private fun bofBody(kind: Int): ByteArray =
        le16(0x0600) + le16(kind) + le16(0x0DBB) + le16(0x07CC) + le32(0) + le32(0)

    private fun boundsheetBody(offset: Int, name: String): ByteArray =
        le32(offset) + le16(0) + byteArrayOf(name.length.toByte(), 0x01) + utf16(name)

    private fun sstBody(strings: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(le32(strings.size))
        out.write(le32(strings.size))
        for (text in strings) {
            out.write(le16(text.length))
            out.write(byteArrayOf(0x01))
            out.write(utf16(text))
        }
        return out.toByteArray()
    }

    /** Cuts one shared string after [first] characters; the tail is a CONTINUE. */
    private fun splitSst(strings: List<String>, first: Int): Pair<ByteArray, ByteArray> {
        val head = ByteArrayOutputStream()
        head.write(le32(strings.size))
        head.write(le32(strings.size))
        val text = strings.last()
        head.write(le16(text.length))
        head.write(byteArrayOf(0x01))
        val all = utf16(text)
        head.write(all, 0, first * 2)

        val tail = ByteArrayOutputStream()
        tail.write(byteArrayOf(0x01))
        tail.write(all, first * 2, all.size - first * 2)
        return head.toByteArray() to tail.toByteArray()
    }

    private fun labelSst(row: Int, column: Int, index: Int): ByteArray =
        le16(row) + le16(column) + le16(0) + le32(index)

    private fun rkCell(row: Int, column: Int, rk: Int): ByteArray =
        le16(row) + le16(column) + le16(0) + le32(rk)

    private fun mulRk(row: Int, firstColumn: Int, values: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(le16(row))
        out.write(le16(firstColumn))
        for (value in values) {
            out.write(le16(0))
            out.write(le32(value))
        }
        out.write(le16(firstColumn + values.size - 1))
        return out.toByteArray()
    }

    private fun numberCell(row: Int, column: Int, value: Double): ByteArray =
        le16(row) + le16(column) + le16(0) + le64(value)

    private fun formulaNumber(row: Int, column: Int, value: Double): ByteArray =
        le16(row) + le16(column) + le16(0) + le64(value) + le16(0) + le32(0) + le16(0)

    private fun formulaString(row: Int, column: Int): ByteArray =
        le16(row) + le16(column) + le16(0) +
            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()) +
            le16(0) + le32(0) + le16(0)

    private fun textBody(text: String): ByteArray =
        le16(text.length) + byteArrayOf(0x01) + utf16(text)

    /** An int as Excel stores it in an RK cell: value << 2, plus the integer bit. */
    private fun integerRk(value: Int): Int = (value shl 2) or 0x02

    // -------------------------------------------------------------- OLE2 bits

    private fun buildOle(stream: ByteArray, streamName: String): ByteArray {
        val size = maxOf(4096, ((stream.size + 511) / 512) * 512)
        val padded = stream.copyOf(size)
        val sectors = padded.size / 512

        val fat = IntArray(128) { FREE }
        fat[0] = FATSECT
        fat[1] = ENDOFCHAIN
        for (index in 0 until sectors) {
            fat[2 + index] = if (index == sectors - 1) ENDOFCHAIN else 3 + index
        }

        val header = ByteArray(512)
        val magic = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        System.arraycopy(magic, 0, header, 0, 8)
        putLe16(header, 24, 0x003E)
        putLe16(header, 26, 3)
        putLe16(header, 28, 0xFFFE)
        putLe16(header, 30, 9)
        putLe16(header, 32, 6)
        putLe32(header, 44, 1)
        putLe32(header, 48, 1)
        putLe32(header, 56, 4096)
        putLe32(header, 60, ENDOFCHAIN)
        putLe32(header, 68, ENDOFCHAIN)
        putLe32(header, 76, 0)
        for (index in 1 until 109) putLe32(header, 76 + index * 4, FREE)

        val fatSector = ByteArray(512)
        for (index in 0 until 128) putLe32(fatSector, index * 4, fat[index])

        val directory = ByteArray(512)
        System.arraycopy(dirEntry("Root Entry", 5, ENDOFCHAIN, 0, child = 1), 0, directory, 0, 128)
        System.arraycopy(dirEntry(streamName, 2, 2, padded.size, child = -1), 0, directory, 128, 128)

        val out = ByteArrayOutputStream()
        out.write(header)
        out.write(fatSector)
        out.write(directory)
        out.write(padded)
        return out.toByteArray()
    }

    private fun dirEntry(name: String, type: Int, start: Int, size: Int, child: Int): ByteArray {
        val entry = ByteArray(128)
        val encoded = utf16(name)
        System.arraycopy(encoded, 0, entry, 0, minOf(encoded.size, 62))
        putLe16(entry, 64, encoded.size + 2)
        entry[66] = type.toByte()
        entry[67] = 1
        putLe32(entry, 68, FREE)
        putLe32(entry, 72, FREE)
        putLe32(entry, 76, child)
        putLe32(entry, 116, start)
        putLe32(entry, 120, size)
        return entry
    }

    private fun pad(stream: ByteArray): ByteArray {
        val size = maxOf(4096, ((stream.size + 511) / 512) * 512)
        return stream.copyOf(size)
    }

    // ------------------------------------------------------------ byte writers

    private inner class Records {
        private val out = ByteArrayOutputStream()
        fun record(type: Int, body: ByteArray): Int {
            val at = out.size()
            out.write(le16(type))
            out.write(le16(body.size))
            out.write(body)
            return at
        }
        fun bytes(): ByteArray = out.toByteArray()
    }

    private fun le16(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte())

    private fun le32(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte(),
        )

    private fun le64(value: Double): ByteArray {
        var bits = java.lang.Double.doubleToLongBits(value)
        val out = ByteArray(8)
        for (index in 0 until 8) {
            out[index] = (bits and 0xFF).toByte()
            bits = bits ushr 8
        }
        return out
    }

    private fun utf16(text: String): ByteArray {
        val out = ByteArray(text.length * 2)
        for (index in text.indices) {
            val code = text[index].code
            out[index * 2] = (code and 0xFF).toByte()
            out[index * 2 + 1] = ((code ushr 8) and 0xFF).toByte()
        }
        return out
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray =
        copyOf(size + other.size).also { System.arraycopy(other, 0, it, size, other.size) }

    private fun putLe16(target: ByteArray, at: Int, value: Int) {
        target[at] = (value and 0xFF).toByte()
        target[at + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putLe32(target: ByteArray, at: Int, value: Int) {
        target[at] = (value and 0xFF).toByte()
        target[at + 1] = ((value ushr 8) and 0xFF).toByte()
        target[at + 2] = ((value ushr 16) and 0xFF).toByte()
        target[at + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private companion object {
        const val BOF = 0x0809
        const val EOF = 0x000A
        const val BOUNDSHEET = 0x0085
        const val SST = 0x00FC
        const val CONTINUE = 0x003C
        const val LABELSST = 0x00FD
        const val NUMBER = 0x0203
        const val RK = 0x027E
        const val MULRK = 0x00BD
        const val FORMULA = 0x0006
        const val STRING = 0x0207

        const val FREE = 0xFFFFFFFF.toInt()
        const val ENDOFCHAIN = 0xFFFFFFFE.toInt()
        const val FATSECT = 0xFFFFFFFD.toInt()
    }
}
