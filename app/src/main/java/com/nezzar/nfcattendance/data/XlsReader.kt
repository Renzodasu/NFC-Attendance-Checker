package com.nezzar.nfcattendance.data

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.MathContext
import java.nio.charset.Charset

/**
 * Reads a legacy .xls workbook: the binary format every Excel up to 2003 wrote,
 * and the one school offices still hand out.
 *
 * The file is an OLE2 compound document ("a filesystem inside one file"). Inside
 * it is a "Workbook" stream (or "Book" in the older BIFF5 shape) made of
 * records: one page of shared strings, one boundsheet per sheet, then the cell
 * records of each sheet. No Apache POI, no Android classes - plain Kotlin over
 * ByteArrays, so the whole reader is unit-testable on the JVM.
 *
 * What it reads: text, numbers, RK numbers, cached formula results, and the
 * sheet names. What it does NOT read: date formatting (a date cell arrives as
 * its serial number, and a roster import treats that as "Not recorded" rather
 * than inventing a day), styles, merged cells, or anything past column 64.
 */
object XlsReader {

    /** A file bigger than this is not a class list; refuse before allocating. */
    const val MAX_BYTES = 24 * 1024 * 1024
    const val MAX_ROWS = 20000
    const val MAX_COLUMNS = 64

    /** The eight bytes every OLE2 compound file starts with. */
    private val OLE2_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    /** True when these bytes are an OLE2 file, which is what an .xls starts with. */
    fun isXls(bytes: ByteArray): Boolean {
        if (bytes.size < 512) return false
        for (i in OLE2_MAGIC.indices) if (bytes[i] != OLE2_MAGIC[i]) return false
        return true
    }

    @Throws(IllegalArgumentException::class)
    fun read(bytes: ByteArray): ReadBook {
        if (bytes.size > MAX_BYTES) {
            throw IllegalArgumentException(
                "That file is too big for a class list (" + bytes.size / 1024 + " KB)."
            )
        }
        if (!isXls(bytes)) {
            throw IllegalArgumentException("This file is not an .xls workbook.")
        }
        val stream = Ole2(bytes).stream("Workbook") ?: Ole2(bytes).stream("Book")
        ?: throw IllegalArgumentException(
            "This .xls file holds no Workbook stream, so there is nothing to read."
        )
        return parse(stream)
    }

    // ------------------------------------------------------------------ OLE2

    /** The compound-document container: a FAT, a directory, and sector chains. */
    private class Ole2(private val bytes: ByteArray) {

        private val sink: Int = 1 shl u16(30)
        private val miniSink: Int = 1 shl u16(32)
        private val miniCutoff: Int = u32(56).toInt()

        private val fat: IntArray = run {
            val sectors = ArrayList<Int>()
            var i = 0
            while (i < 109) {
                val value = i32(76 + i * 4)
                if (value >= 0) sectors += value
                i++
            }
            var difat = i32(68)
            var guard = 0
            while (difat >= 0 && guard < 4096) {
                val base = offset(difat)
                var entry = 0
                while (entry < sink / 4 - 1) {
                    val value = i32(base + entry * 4)
                    if (value >= 0) sectors += value
                    entry++
                }
                difat = i32(base + sink - 4)
                guard++
            }
            val table = IntArray(sectors.size * (sink / 4))
            sectors.forEachIndexed { index, sector ->
                val at = offset(sector)
                var word = 0
                while (word < sink / 4) {
                    table[index * (sink / 4) + word] = i32(at + word * 4)
                    word++
                }
            }
            table
        }

        private val entries: List<DirEntry> = run {
            val out = ArrayList<DirEntry>()
            val data = chain(i32(48), maxSectors = 4096)
            var at = 0
            while (at + 128 <= data.size) {
                val nameLength = u16(data, at + 64)
                val builder = StringBuilder()
                var char = 0
                while (char < nameLength / 2 - 1) {
                    val code = u16(data, at + char * 2)
                    if (code != 0) builder.append(code.toChar())
                    char++
                }
                out += DirEntry(
                    name = builder.toString(),
                    type = data[at + 66].toInt() and 0xFF,
                    start = i32(data, at + 116),
                    size = u32(data, at + 120).toInt(),
                )
                at += 128
            }
            out
        }

        fun stream(name: String): ByteArray? {
            val entry = entries.firstOrNull {
                it.type == 2 && it.name.equals(name, ignoreCase = true)
            } ?: return null
            if (entry.size <= 0 || entry.size > MAX_BYTES) return null
            if (entry.size < miniCutoff) return miniChain(entry.start, entry.size)
            return chain(entry.start, entry.size, maxSectors = (MAX_BYTES / sink) + 16)
        }

        private fun miniChain(start: Int, size: Int): ByteArray {
            val root = entries.firstOrNull { it.type == 5 } ?: return ByteArray(0)
            val mini = chain(i32(60), maxSectors = 4096)
            val fat = IntArray(mini.size / 4) { i32(mini, it * 4) }
            val data = chain(root.start, root.size)
            val out = ByteArrayOutputStream(size)
            var sector = start
            var guard = 0
            while (sector in 0 until fat.size && out.size() < size && guard < fat.size) {
                val base = sector * miniSink
                var i = 0
                while (i < miniSink && out.size() < size && base + i < data.size) {
                    out.write(data[base + i].toInt())
                    i++
                }
                sector = fat[sector]
                guard++
            }
            return out.toByteArray()
        }

        /** Walks one FAT chain and returns at most [size] bytes. */
        private fun chain(start: Int, size: Int = -1, maxSectors: Int = 65536): ByteArray {
            val out = ByteArrayOutputStream()
            var sector = start
            var guard = 0
            while (sector in 0 until fat.size && guard < maxSectors) {
                if (size >= 0 && out.size() >= size) break
                val base = offset(sector)
                var i = 0
                while (i < sink && base + i < bytes.size) {
                    if (size >= 0 && out.size() >= size) break
                    out.write(bytes[base + i].toInt())
                    i++
                }
                sector = fat[sector]
                guard++
            }
            return out.toByteArray()
        }

        private fun offset(sector: Int): Int = (sector + 1) * sink

        private fun u16(at: Int): Int = u16(bytes, at)

        private fun u32(at: Int): Long = u32(bytes, at)

        private fun i32(at: Int): Int = i32(bytes, at)

        private fun u16(data: ByteArray, at: Int): Int =
            (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)

        private fun u32(data: ByteArray, at: Int): Long =
            (data[at].toLong() and 0xFF) or
                ((data[at + 1].toLong() and 0xFF) shl 8) or
                ((data[at + 2].toLong() and 0xFF) shl 16) or
                ((data[at + 3].toLong() and 0xFF) shl 24)

        private fun i32(data: ByteArray, at: Int): Int {
            val value = u32(data, at)
            return if (value >= 0xFFFFFFFC.toLong()) -1 else value.toInt()
        }
    }

    private class DirEntry(val name: String, val type: Int, val start: Int, val size: Int)

    // ------------------------------------------------------------------ BIFF

    private class Record(val type: Int, val at: Int, val length: Int)

    private fun parse(stream: ByteArray): ReadBook {
        val records = records(stream)
        if (records.isEmpty()) throw IllegalArgumentException("That Workbook stream is empty.")

        var version = 8
        records.firstOrNull()?.let { first ->
            if (first.type == BOF && first.length >= 4) {
                version = if (u16(stream, first.at + 2) >= 0x0600) 8 else 5
            }
        }
        val encoding: Charset = if (version >= 8) Charsets.UTF_16LE else cp1252()

        // Shared strings: the SST record and every CONTINUE that follows it.
        val sst = run {
            val at = records.indexOfFirst { it.type == SST }
            if (at < 0) emptyList() else {
                val chunks = ArrayList<ByteArray>()
                chunks += body(stream, records[at])
                var next = at + 1
                while (next < records.size && records[next].type == CONTINUE) {
                    chunks += body(stream, records[next])
                    next++
                }
                SstCursor(chunks, encoding).readAll()
            }
        }

        val sheets = ArrayList<Pair<String, Int>>()
        for (record in records) {
            if (record.type != BOUNDSHEET || record.length < 8) continue
            // lbPlyPos (4), sheet type (2), name length (1), name flags (1), name.
            val start = i32(stream, record.at)
            val kind = stream[record.at + 4].toInt() and 0xFF
            val nameLength = stream[record.at + 6].toInt() and 0xFF
            val flags = stream[record.at + 7].toInt() and 0xFF
            val wide = (flags and 0x01) != 0
            val name = nameAt(stream, record.at + 8, nameLength, wide)
            if (kind == 0) sheets += name to start
        }
        if (sheets.isEmpty()) {
            throw IllegalArgumentException("That workbook has no worksheet, so there is nothing to read.")
        }

        val names = ArrayList<String>()
        val tables = ArrayList<List<List<String>>>()
        for ((name, start) in sheets) {
            names += name
            tables += sheet(stream, start, sst, version)
        }
        return ReadBook(names, tables)
    }

    private fun sheet(stream: ByteArray, start: Int, sst: List<String>, version: Int): List<List<String>> {
        val cells = HashMap<Int, HashMap<Int, String>>()
        var maxRow = -1
        var maxColumn = -1

        fun put(row: Int, column: Int, text: String) {
            if (row < 0 || column < 0 || row >= MAX_ROWS || column >= MAX_COLUMNS) return
            if (text.isEmpty()) return
            cells.getOrPut(row) { HashMap() }[column] = text
            if (row > maxRow) maxRow = row
            if (column > maxColumn) maxColumn = column
        }

        var at = start
        var pendingFormulaRow = -1
        var pendingFormulaColumn = -1
        var guard = 0
        while (at + 4 <= stream.size && guard < 400000) {
            val type = u16(stream, at)
            val length = u16(stream, at + 2)
            val data = at + 4
            if (data + length > stream.size) break
            when (type) {
                EOF -> break
                LABELSST -> {
                    val row = u16(stream, data)
                    val column = u16(stream, data + 2)
                    val index = u32(stream, data + 6).toInt()
                    put(row, column, sst.getOrElse(index) { "" })
                }
                LABEL -> {
                    val row = u16(stream, data)
                    val column = u16(stream, data + 2)
                    put(row, column, label(stream, data + 6, length - 6))
                }
                NUMBER -> {
                    val row = u16(stream, data)
                    val column = u16(stream, data + 2)
                    val value = doubleAt(stream, data + 6)
                    put(row, column, number(value))
                }
                RK -> {
                    val row = u16(stream, data)
                    val column = u16(stream, data + 2)
                    put(row, column, number(rk(u32(stream, data + 6))))
                }
                MULRK -> {
                    val row = u16(stream, data)
                    val first = u16(stream, data + 2)
                    val count = (length - 6) / 6
                    var i = 0
                    while (i < count) {
                        val value = rk(u32(stream, data + 4 + i * 6 + 2))
                        put(row, first + i, number(value))
                        i++
                    }
                }
                FORMULA -> {
                    val row = u16(stream, data)
                    val column = u16(stream, data + 2)
                    if (length >= 14 && u16(stream, data + 12) == 0xFFFF) {
                        when (stream[data + 6].toInt() and 0xFF) {
                            0 -> {
                                pendingFormulaRow = row
                                pendingFormulaColumn = column
                            }
                            3 -> {}
                            else -> put(row, column, "")
                        }
                    } else if (length >= 14) {
                        put(row, column, number(doubleAt(stream, data + 6)))
                    }
                }
                STRING -> {
                    if (pendingFormulaRow >= 0) {
                        put(pendingFormulaRow, pendingFormulaColumn, label(stream, data, length))
                        pendingFormulaRow = -1
                        pendingFormulaColumn = -1
                    }
                }
            }
            at = data + length
            guard++
        }

        val rows = ArrayList<List<String>>(maxRow + 1)
        var row = 0
        while (row <= maxRow) {
            val line = cells[row]
            val values = ArrayList<String>(maxColumn + 1)
            var column = 0
            while (column <= maxColumn) {
                values += line?.get(column) ?: ""
                column++
            }
            rows += values
            row++
        }
        return rows
    }

    /** The shared-string table, with the CONTINUE/width rule handled. */
    private class SstCursor(private val chunks: List<ByteArray>, private val encoding: Charset) {
        private var chunk = 0
        private var position = 0

        fun readAll(): List<String> {
            val out = ArrayList<String>()
            if (chunks.isEmpty()) return out
            skip(8) // total and unique counts
            var i = 0
            val unique = if (chunks[0].size >= 8) u16(chunks[0], 4) else 0
            while (i < unique && !atEnd()) {
                out += next()
                i++
            }
            return out
        }

        private fun next(): String {
            val count = u16()
            val flags = u8()
            val wide = (flags and 0x01) != 0
            val rich = (flags and 0x08) != 0
            val farEast = (flags and 0x04) != 0
            val runs = if (rich) u16() else 0
            val extra = if (farEast) u32().toInt() else 0
            val text = chars(count, wide)
            if (runs > 0) skip(runs * 4)
            if (extra > 0) skip(extra)
            return text
        }

        private fun atEnd(): Boolean {
            advance()
            return chunk >= chunks.size
        }

        private fun advance() {
            while (chunk < chunks.size && position >= chunks[chunk].size) {
                chunk++
                position = 0
            }
        }

        private fun u8(): Int {
            advance()
            if (chunk >= chunks.size) return 0
            val value = chunks[chunk][position].toInt() and 0xFF
            position++
            return value
        }

        private fun u16(): Int = u8() or (u8() shl 8)

        private fun u32(): Long = (u16().toLong()) or (u16().toLong() shl 16)

        private fun skip(count: Int) {
            var left = count
            while (left > 0) {
                advance()
                if (chunk >= chunks.size) return
                val take = minOf(left, chunks[chunk].size - position)
                position += take
                left -= take
            }
        }

        private fun chars(count: Int, wide: Boolean): String {
            val builder = StringBuilder()
            var left = count
            var isWide = wide
            while (left > 0) {
                if (chunk >= chunks.size) break
                val data = chunks[chunk]
                if (position >= data.size) {
                    if (!nextChunk()) break
                    isWide = chunkIsWide()
                    continue
                }
                if (isWide && position + 1 >= data.size) {
                    // A wide character cannot straddle the record edge: the
                    // remainder continues in the next record.
                    if (!nextChunk()) break
                    isWide = chunkIsWide()
                    continue
                }
                if (isWide) {
                    builder.append(u16(data, position).toChar())
                    position += 2
                } else {
                    builder.append((data[position].toInt() and 0xFF).toChar())
                    position++
                }
                left--
            }
            return builder.toString()
        }

        /** Moves to the next record body and consumes its width flag byte. */
        private fun nextChunk(): Boolean {
            chunk++
            position = 0
            return chunk < chunks.size
        }

        private fun chunkIsWide(): Boolean {
            val flag = chunks[chunk][position].toInt() and 0x01
            position++
            return flag != 0
        }

        private fun u16(data: ByteArray, at: Int): Int =
            (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
    }

    // ------------------------------------------------------------- cell rules

    /** A LABEL/STRING body: 2-byte count, 1-byte flags, then the characters. */
    private fun label(stream: ByteArray, data: Int, length: Int): String {
        if (length < 3) return ""
        val count = u16(stream, data)
        val wide = (stream[data + 2].toInt() and 0x01) != 0
        var at = data + 3
        val builder = StringBuilder(count)
        var i = 0
        while (i < count) {
            if (at + 1 >= stream.size) break
            if (wide) {
                builder.append(u16(stream, at).toChar())
                at += 2
            } else {
                builder.append((stream[at].toInt() and 0xFF).toChar())
                at++
            }
            i++
        }
        return builder.toString()
    }

    private fun nameAt(stream: ByteArray, data: Int, length: Int, wide: Boolean): String {
        val builder = StringBuilder(length)
        var at = data
        var i = 0
        while (i < length) {
            if (at + 1 >= stream.size) break
            if (wide) {
                builder.append(u16(stream, at).toChar())
                at += 2
            } else {
                builder.append((stream[at].toInt() and 0xFF).toChar())
                at++
            }
            i++
        }
        return builder.toString()
    }

    /** RK: bit 1 says integer, bit 0 says the value was divided by 100. */
    private fun rk(raw: Long): Double {
        val value = raw.toInt()
        val integer = (value and 0x02) != 0
        val scaled = (value and 0x01) != 0
        var result: Double
        if (integer) {
            result = (value shr 2).toDouble()
        } else {
            val bits = (value.toLong() and 0xFFFFFFFCL) shl 32
            result = Double.fromBits(bits)
        }
        if (scaled) result /= 100.0
        return result
    }

    /** "42" for whole numbers, "12.5" for the rest - never scientific notation. */
    private fun number(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        return BigDecimal(value).round(MathContext(15)).stripTrailingZeros().toPlainString()
    }

    private fun cp1252(): Charset = try {
        Charset.forName("windows-1252")
    } catch (t: Throwable) {
        Charsets.ISO_8859_1
    }

    // ------------------------------------------------------------- byte helpers

    private fun records(stream: ByteArray): List<Record> {
        val out = ArrayList<Record>()
        var at = 0
        var guard = 0
        while (at + 4 <= stream.size && guard < 400000) {
            val type = u16(stream, at)
            val length = u16(stream, at + 2)
            if (at + 4 + length > stream.size) break
            out += Record(type, at + 4, length)
            if (type == EOF) break
            at += 4 + length
            guard++
        }
        return out
    }

    private fun body(stream: ByteArray, record: Record): ByteArray =
        stream.copyOfRange(record.at, record.at + record.length)

    private fun doubleAt(stream: ByteArray, at: Int): Double {
        var bits = 0L
        var i = 7
        while (i >= 0) {
            bits = (bits shl 8) or (stream[at + i].toLong() and 0xFF)
            i--
        }
        return Double.fromBits(bits)
    }

    private fun u16(stream: ByteArray, at: Int): Int =
        (stream[at].toInt() and 0xFF) or ((stream[at + 1].toInt() and 0xFF) shl 8)

    private fun i32(stream: ByteArray, at: Int): Int = u32(stream, at).toInt()

    private fun u32(stream: ByteArray, at: Int): Long =
        (stream[at].toLong() and 0xFF) or
            ((stream[at + 1].toLong() and 0xFF) shl 8) or
            ((stream[at + 2].toLong() and 0xFF) shl 16) or
            ((stream[at + 3].toLong() and 0xFF) shl 24)

    // BIFF record numbers this reader understands.
    private const val BOF = 0x0809
    private const val EOF = 0x000A
    private const val BOUNDSHEET = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABELSST = 0x00FD
    private const val LABEL = 0x0204
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val FORMULA = 0x0006
    private const val STRING = 0x0207
}
