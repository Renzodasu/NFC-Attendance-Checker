package com.nezzar.nfcattendance.data

import java.io.InputStream
import java.util.zip.ZipInputStream

/** A read workbook: sheet names in workbook order plus their rows of text. */
class XlsxBook(
    val sheetNames: List<String>,
    private val sheets: List<List<List<String>>>,
) {
    /** Rows of the named sheet, or null when there is no such sheet. Case-insensitive. */
    fun sheet(name: String): List<List<String>>? {
        val index = sheetNames.indexOfFirst { it.equals(name.trim(), ignoreCase = true) }
        return if (index < 0) null else sheets[index]
    }
}

/**
 * Reads .xlsx files with java.util.zip plus the project's own [XmlLite]
 * scanner. No POI, no DocumentBuilderFactory, no android.util.Xml - plain
 * Kotlin, so the reader is unit-testable on the JVM.
 *
 * It understands both shapes this app meets:
 *  - our own writer: <c t="inlineStr"><is><t>...</t></is></c>;
 *  - a file reopened and re-saved in Excel or Google Sheets: strings in
 *    xl/sharedStrings.xml referenced by <c t="s"><v>index</v></c>.
 */
object XlsxReader {

    const val MAX_PART_BYTES = 8 * 1024 * 1024
    const val MAX_ROWS = 20000
    const val MAX_COLUMNS = 64

    @Throws(IllegalArgumentException::class)
    fun read(input: InputStream): XlsxBook {
        val parts = LinkedHashMap<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/')
                if (!entry.isDirectory && wanted(name) && !parts.containsKey(name)) {
                    parts[name] = readPart(name, zip)
                }
                zip.closeEntry()
            }
        }
        val workbook = findPart(parts, "xl/workbook.xml")
            ?: throw IllegalArgumentException(
                "This file is not an .xlsx workbook - xl/workbook.xml is missing."
            )
        val rels = relationshipTargets(findPart(parts, "xl/_rels/workbook.xml.rels") ?: "")
        val shared = sharedStrings(findPart(parts, "xl/sharedStrings.xml") ?: "")

        val names = mutableListOf<String>()
        val tables = mutableListOf<List<List<String>>>()
        val entries = sheetEntries(workbook)
        var index = 0
        while (index < entries.size) {
            val (sheetName, rid) = entries[index]
            val target = rels[rid] ?: ("xl/worksheets/sheet" + (index + 1) + ".xml")
            val xml = findPart(parts, target) ?: findPart(parts, "xl/worksheets/sheet" + (index + 1) + ".xml")
            if (xml != null) {
                names += if (sheetName.isEmpty()) "Sheet" + (index + 1) else sheetName
                tables += rows(xml, shared)
            }
            index++
        }
        if (names.isEmpty()) {
            throw IllegalArgumentException("This workbook has no worksheets to read.")
        }
        return XlsxBook(names, tables)
    }

    /** Element order of <sheets><sheet name="Roster" r:id="rId1"/>... */
    fun sheetEntries(workbookXml: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val scanner = XmlLite(workbookXml)
        while (true) {
            val event = scanner.next()
            if (event is XmlLite.Event.Eof) break
            if (event is XmlLite.Event.Start && event.name == "sheet") {
                val name = event.attributes["name"] ?: ""
                if (name.isEmpty()) continue
                out += name to (event.attributes["id"] ?: "")
            }
        }
        return out
    }

    /** rId -> part name, e.g. "rId1" -> "xl/worksheets/sheet1.xml". */
    fun relationshipTargets(relsXml: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (relsXml.isEmpty()) return out
        val scanner = XmlLite(relsXml)
        while (true) {
            val event = scanner.next()
            if (event is XmlLite.Event.Eof) break
            if (event is XmlLite.Event.Start && event.name == "relationship") {
                val id = event.attributes["id"] ?: continue
                val target = event.attributes["target"] ?: continue
                out[id] = normalizeTarget(target)
            }
        }
        return out
    }

    /** Every <si> in xl/sharedStrings.xml, with its <t> runs concatenated. */
    fun sharedStrings(xml: String): List<String> {
        val out = mutableListOf<String>()
        if (xml.isEmpty()) return out
        val scanner = XmlLite(xml)
        var inItem = false
        var inText = false
        val text = StringBuilder()
        while (true) {
            val event = scanner.next()
            if (event is XmlLite.Event.Eof) break
            when (event) {
                is XmlLite.Event.Start -> when (event.name) {
                    "si" -> {
                        inItem = true
                        text.setLength(0)
                    }
                    "t" -> inText = true
                    else -> {}
                }
                is XmlLite.Event.End -> when (event.name) {
                    "t" -> inText = false
                    "si" -> {
                        inItem = false
                        out += text.toString()
                    }
                    else -> {}
                }
                is XmlLite.Event.Text -> if (inItem && inText) text.append(event.text)
                else -> {}
            }
        }
        return out
    }

    /**
     * One worksheet as rows of text. A row's position in the result matches the
     * spreadsheet's own row number, so a reported "row 7" is really row 7.
     */
    fun rows(xml: String, shared: List<String>): List<List<String>> {
        val out = mutableListOf<List<String>>()
        val scanner = XmlLite(xml)
        var cells: MutableMap<Int, String>? = null
        var column = -1
        var type = ""
        var inValue = false
        var inText = false
        val value = StringBuilder()
        val text = StringBuilder()
        while (true) {
            val event = scanner.next()
            if (event is XmlLite.Event.Eof) break
            when (event) {
                is XmlLite.Event.Start -> when (event.name) {
                    "row" -> {
                        val declared = event.attributes["r"]?.trim()?.toIntOrNull()
                        if (declared != null) {
                            while (out.size < declared - 1 && out.size < MAX_ROWS) out.add(emptyList())
                        }
                        cells = LinkedHashMap()
                        column = -1
                    }
                    "c" -> {
                        val ref = event.attributes["r"]
                        column = if (ref == null) column + 1 else columnIndex(ref)
                        type = event.attributes["t"] ?: ""
                        value.setLength(0)
                        text.setLength(0)
                    }
                    "v" -> {
                        inValue = true
                        value.setLength(0)
                    }
                    "t" -> inText = true
                    else -> {}
                }
                is XmlLite.Event.End -> when (event.name) {
                    "v" -> inValue = false
                    "t" -> inText = false
                    "c" -> {
                        val raw = value.toString()
                        val cell = when (type) {
                            "inlineStr" -> text.toString()
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            else -> raw
                        }
                        cells?.let { if (column >= 0 && column < MAX_COLUMNS) it[column] = cell }
                    }
                    "row" -> {
                        val row = cells
                        if (row != null && out.size < MAX_ROWS) {
                            val width = if (row.isEmpty()) 0 else row.keys.max() + 1
                            out.add((0 until width).map { row[it] ?: "" })
                        }
                        cells = null
                    }
                    else -> {}
                }
                is XmlLite.Event.Text -> if (inValue) value.append(event.text) else if (inText) text.append(event.text)
                else -> {}
            }
        }
        return out
    }

    /** "B3" -> 1. Cells without a reference are read left to right. */
    fun columnIndex(ref: String): Int {
        var value = 0
        var seen = 0
        for (ch in ref) {
            val upper = ch.uppercaseChar()
            if (upper < 'A' || upper > 'Z') break
            value = value * 26 + (upper - 'A' + 1)
            seen++
        }
        return if (seen == 0) -1 else value - 1
    }

    private fun wanted(name: String): Boolean =
        name.endsWith("workbook.xml") ||
            name.endsWith("workbook.xml.rels") ||
            name.endsWith("sharedStrings.xml") ||
            (name.contains("worksheets/") && name.endsWith(".xml"))

    private fun findPart(parts: Map<String, String>, name: String): String? {
        parts[name]?.let { return it }
        val tail = name.substringAfterLast('/')
        var found: String? = null
        for ((key, value) in parts) {
            if (key.substringAfterLast('/') == tail) {
                found = value
                break
            }
        }
        return found
    }

    private fun normalizeTarget(raw: String): String {
        val path = raw.substringBefore('#').trim()
        if (path.startsWith("/")) return path.trimStart('/')
        if (path.startsWith("xl/")) return path
        return "xl/" + path
    }

    private fun readPart(name: String, zip: ZipInputStream): String {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            if (out.size() + read > MAX_PART_BYTES) {
                throw IllegalArgumentException(name + " is too large to be a spreadsheet part.")
            }
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }
}
