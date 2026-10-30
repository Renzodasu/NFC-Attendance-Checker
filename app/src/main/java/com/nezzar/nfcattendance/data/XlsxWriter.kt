package com.nezzar.nfcattendance.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One worksheet: a name plus rows of plain string cells.
 *
 * The four extra fields are optional and default to empty, so a report that only
 * needs text keeps writing `Sheet(name, rows)`. A school form does need them: it
 * has fixed column widths, ruled cells and merged title rows.
 *
 * [styles] is keyed "r,c" with 0-based coordinates and the value is one of the
 * [Style] ids. A styled empty cell is still written, which is what draws a box
 * around a blank attendance square.
 */
data class Sheet(
    val name: String,
    val rows: List<List<String>>,
    /** Column widths in Excel character units; index 0 is column A. */
    val widths: List<Double> = emptyList(),
    /** Cell style ids keyed by 0-based "row,column". */
    val styles: Map<String, Int> = emptyMap(),
    /** Merged ranges in A1 notation, e.g. "A1:R1". */
    val merges: List<String> = emptyList(),
    /** Row heights in points, keyed by 0-based row index. */
    val rowHeights: Map<Int, Double> = emptyMap(),
)

/** The cell formats [XlsxWriter] knows how to write. */
object Style {
    const val NORMAL = 0
    const val BOLD = 1
    const val TITLE = 2
    const val BOXED = 3
    const val BOXED_BOLD = 4
    const val BOXED_CENTER = 5
    const val BOXED_BOLD_CENTER = 6
    const val FOOTNOTE = 7
}

/**
 * Minimal Office Open XML (.xlsx) writer built on java.util.zip only.
 * Apache POI is deliberately NOT used: it needs javax.xml bootstrapping that is
 * unreliable on Android. Strings are written inline, so no sharedStrings part
 * is needed.
 */
object XlsxWriter {

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    fun write(out: OutputStream, sheets: List<Sheet>) {
        require(sheets.isNotEmpty()) { "at least one sheet is required" }
        val safe = sheets.map { it.copy(name = sheetName(it.name)) }
        val zip = ZipOutputStream(out)
        try {
            put(zip, "[Content_Types].xml", contentTypes(safe.size))
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(safe))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(safe.size))
            put(zip, "xl/styles.xml", STYLES)
            var i = 0
            while (i < safe.size) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(safe[i]))
                i++
            }
        } finally {
            zip.finish()
            zip.close()
        }
    }

    private fun put(zip: ZipOutputStream, path: String, body: String) {
        val entry = ZipEntry(path)
        entry.time = 1700000000000L
        zip.putNextEntry(entry)
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun sheetName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/*?:\\[\\]]"), " ").trim()
        val name = if (cleaned.isEmpty()) "Sheet" else cleaned
        return if (name.length <= 31) name else name.substring(0, 31)
    }

    private fun contentTypes(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append(XML_HEADER)
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        var i = 0
        while (i < sheetCount) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            i++
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun rootRels(): String =
        XML_HEADER +
            "<Relationships xmlns=\"" + NS_PKG_REL + "\">" +
            "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(sheets: List<Sheet>): String {
        val sb = StringBuilder()
        sb.append(XML_HEADER)
        sb.append("<workbook xmlns=\"").append(NS_MAIN).append("\" xmlns:r=\"").append(NS_REL).append("\">")
        sb.append("<sheets>")
        var i = 0
        while (i < sheets.size) {
            sb.append("<sheet name=\"").append(escape(sheets[i].name))
                .append("\" sheetId=\"").append(i + 1)
                .append("\" r:id=\"rId").append(i + 1).append("\"/>")
            i++
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append(XML_HEADER)
        sb.append("<Relationships xmlns=\"").append(NS_PKG_REL).append("\">")
        var i = 0
        while (i < sheetCount) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                .append("\" Type=\"").append(NS_REL).append("/worksheet\" Target=\"worksheets/sheet")
                .append(i + 1).append(".xml\"/>")
            i++
        }
        sb.append("<Relationship Id=\"rId").append(sheetCount + 1)
            .append("\" Type=\"").append(NS_REL).append("/styles\" Target=\"styles.xml\"/>")
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append(XML_HEADER)
        sb.append("<worksheet xmlns=\"").append(NS_MAIN).append("\">")
        // A form only prints on one page if it is told to fit there.
        sb.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
        if (sheet.widths.isNotEmpty()) {
            sb.append("<cols>")
            for (c in sheet.widths.indices) {
                sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(sheet.widths[c])
                    .append("\" customWidth=\"1\"/>")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        var r = 0
        while (r < sheet.rows.size) {
            val row = sheet.rows[r]
            val height = sheet.rowHeights[r]
            sb.append("<row r=\"").append(r + 1).append("\"")
            if (height != null) {
                sb.append(" ht=\"").append(height).append("\" customHeight=\"1\"")
            }
            sb.append(">")
            val lastStyled = styledColumns(sheet, r)
            val columns = maxOf(row.size, lastStyled + 1)
            var c = 0
            while (c < columns) {
                val style = sheet.styles[r.toString() + "," + c]
                val text = if (c < row.size) row[c] else ""
                if (style == null) {
                    if (text.isEmpty()) {
                        c++
                        continue
                    }
                    sb.append("<c r=\"").append(columnName(c)).append(r + 1)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(text))
                        .append("</t></is></c>")
                } else {
                    sb.append("<c r=\"").append(columnName(c)).append(r + 1)
                        .append("\" s=\"").append(style).append("\"")
                    if (text.isEmpty()) {
                        sb.append("/>")
                    } else {
                        sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(text))
                            .append("</t></is></c>")
                    }
                }
                c++
            }
            sb.append("</row>")
            r++
        }
        sb.append("</sheetData>")
        if (sheet.merges.isNotEmpty()) {
            sb.append("<mergeCells count=\"").append(sheet.merges.size).append("\">")
            for (range in sheet.merges) {
                sb.append("<mergeCell ref=\"").append(range).append("\"/>")
            }
            sb.append("</mergeCells>")
        }
        sb.append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.3\" footer=\"0.3\"/>")
        sb.append("<pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    /** Highest column index carrying a style on row [r], or -1 when there is none. */
    private fun styledColumns(sheet: Sheet, r: Int): Int {
        var last = -1
        for (key in sheet.styles.keys) {
            val comma = key.indexOf(',')
            if (comma <= 0) continue
            if (key.substring(0, comma).toIntOrNull() != r) continue
            val c = key.substring(comma + 1).toIntOrNull() ?: continue
            if (c > last) last = c
        }
        return last
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun escape(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * The style table. Index order is the [Style] constants; do not reorder.
     * 0 normal, 1 bold, 2 title (16pt bold), 3 boxed, 4 boxed bold,
     * 5 boxed centred, 6 boxed bold centred, 7 footnote (9pt italic).
     */
    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"4\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font>" +
        "<font><i/><sz val=\"9\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
        "<borders count=\"2\">" +
        "<border><left/><right/><top/><bottom/><diagonal/></border>" +
        "<border><left style=\"thin\"/><right style=\"thin\"/><top style=\"thin\"/>" +
        "<bottom style=\"thin\"/><diagonal/></border>" +
        "</borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"8\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\">" +
        "<alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
        "<alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
