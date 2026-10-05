package com.nezzar.nfcattendance.data

/**
 * A minimal XML scanner - just enough to read the parts of an .xlsx workbook
 * this app needs. It is deliberately dependency-free (no POI, no
 * DocumentBuilderFactory, no android.util.Xml) so the whole .xlsx reader stays
 * plain Kotlin and is unit-testable on the JVM.
 *
 * It tolerates namespace prefixes, comments, CDATA, self-closing tags, single
 * or double quoted attributes and the XML entities Excel writes.
 */
class XmlLite(private val text: String) {

    sealed class Event {
        /** Element start; [attributes] keys are lower-cased local names, values decoded. */
        class Start(val name: String, val attributes: Map<String, String>) : Event()
        class End(val name: String) : Event()
        class Text(val text: String) : Event()
        object Eof : Event()
    }

    private var pos = 0
    private var owedEnd: String? = null

    fun next(): Event {
        val owed = owedEnd
        if (owed != null) {
            owedEnd = null
            return Event.End(owed)
        }
        while (true) {
            if (pos >= text.length) return Event.Eof
            if (text[pos] != '<') {
                val lt = text.indexOf('<', pos)
                val end = if (lt < 0) text.length else lt
                val body = decode(text.substring(pos, end))
                pos = end
                if (body.isNotEmpty()) return Event.Text(body)
                continue
            }
            if (text.startsWith("<!--", pos)) {
                pos = after("-->", pos + 4)
                continue
            }
            if (text.startsWith("<![CDATA[", pos)) {
                val end = text.indexOf("]]>", pos + 9)
                val body = if (end < 0) text.substring(pos + 9) else text.substring(pos + 9, end)
                pos = if (end < 0) text.length else end + 3
                if (body.isNotEmpty()) return Event.Text(body)
                continue
            }
            if (text.startsWith("<?", pos)) {
                pos = after("?>", pos + 2)
                continue
            }
            if (text.startsWith("<!", pos)) {
                pos = after(">", pos + 2)
                continue
            }
            if (text.startsWith("</", pos)) {
                val end = text.indexOf('>', pos)
                if (end < 0) {
                    pos = text.length
                    return Event.Eof
                }
                val name = local(text.substring(pos + 2, end))
                pos = end + 1
                return Event.End(name)
            }
            return startTag()
        }
    }

    private fun startTag(): Event {
        var i = pos + 1
        val nameStart = i
        while (i < text.length && !endsName(text[i])) i++
        val name = local(text.substring(nameStart, i))
        val attributes = LinkedHashMap<String, String>()
        var selfClosing = false
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break
            if (text[i] == '>') {
                i++
                break
            }
            if (text[i] == '/') {
                if (i + 1 < text.length && text[i + 1] == '>') {
                    selfClosing = true
                    i += 2
                    break
                }
                i++
                continue
            }
            val keyStart = i
            while (i < text.length && text[i] != '=' && text[i] != '>' && text[i] != '/' && !text[i].isWhitespace()) i++
            val key = local(text.substring(keyStart, i))
            var value = ""
            while (i < text.length && text[i].isWhitespace()) i++
            if (i < text.length && text[i] == '=') {
                i++
                while (i < text.length && text[i].isWhitespace()) i++
                if (i < text.length && (text[i] == '"' || text[i] == '\'')) {
                    val quote = text[i]
                    i++
                    val valueStart = i
                    while (i < text.length && text[i] != quote) i++
                    value = decode(text.substring(valueStart, minOf(i, text.length)))
                    if (i < text.length) i++
                } else {
                    val valueStart = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] != '>') i++
                    value = decode(text.substring(valueStart, i))
                }
            }
            if (key.isNotEmpty()) attributes[key] = value
        }
        pos = i
        if (selfClosing) owedEnd = name
        return Event.Start(name, attributes)
    }

    private fun after(marker: String, from: Int): Int {
        val at = text.indexOf(marker, from)
        return if (at < 0) text.length else at + marker.length
    }

    private fun endsName(c: Char): Boolean = c.isWhitespace() || c == '/' || c == '>'

    /** "x:row" -> "row"; attribute keys and element names are compared lower-case. */
    private fun local(raw: String): String = raw.substringAfterLast(':').trim().lowercase()

    companion object {
        /** Decodes the five XML entities plus numeric character references. */
        fun decode(raw: String): String {
            if (raw.indexOf('&') < 0) return raw
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch != '&') {
                    sb.append(ch)
                    i++
                    continue
                }
                val semi = raw.indexOf(';', i + 1)
                if (semi < 0 || semi - i > 12) {
                    sb.append(ch)
                    i++
                    continue
                }
                val body = raw.substring(i + 1, semi)
                val decoded = when {
                    body == "amp" -> "&"
                    body == "lt" -> "<"
                    body == "gt" -> ">"
                    body == "quot" -> "\""
                    body == "apos" -> "'"
                    body.startsWith("#x") || body.startsWith("#X") ->
                        body.substring(2).toIntOrNull(16)?.let { code -> code.toChar().toString() }
                    body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { code -> code.toChar().toString() }
                    else -> null
                }
                if (decoded == null) {
                    sb.append(ch)
                    i++
                } else {
                    sb.append(decoded)
                    i = semi + 1
                }
            }
            return sb.toString()
        }
    }
}
