package com.nezzar.nfcattendance.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One worksheet: a name plus rows of plain string cells. */
class Sheet(val name: String, val rows: List<List<String>>)

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
        val safe = sheets.map { Sheet(sheetName(it.name), it.rows) }
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
        sb.append("<worksheet xmlns=\"").append(NS_MAIN).append("\"><sheetData>")
        var r = 0
        while (r < sheet.rows.size) {
            val row = sheet.rows[r]
            sb.append("<row r=\"").append(r + 1).append("\">")
            var c = 0
            while (c < row.size) {
                sb.append("<c r=\"").append(columnName(c)).append(r + 1)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(row[c]))
                    .append("</t></is></c>")
                c++
            }
            sb.append("</row>")
            r++
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
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

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
