package com.nezzar.nfcattendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What a read of sections.json produced. A damaged file is reported as an error
 * rather than silently becoming "no classes": the app that reads "no classes"
 * from a corrupt file will overwrite the real one on the next edit.
 */
data class SectionsLoad(
    val sections: List<Section> = emptyList(),
    /** Non-null when the file exists but could not be understood. Nothing was changed. */
    val error: String? = null,
    /** True when there is no file at all - a genuinely fresh install. */
    val missing: Boolean = false,
)

/** A session that was still running the last time the app stopped. */
data class LiveSession(val session: AttendanceSession, val paused: Boolean)

/**
 * All persistence: plain JSON files in the app's private storage plus a reports
 * directory in the app-external files dir. No database.
 *
 * Every write lands through a temporary file and a rename, so a process killed
 * mid-write leaves the previous file intact instead of a half-written one.
 *
 * sections.json  {"version":2,"sections":[{"name":"...","subject":"...","students":[{"name":..,"uid":..}]}]}
 * settings.json  {"selectedSection":"...","uidReversed":false,"theme":"dark","tutorialSeen":true}
 * sessions.json  {"version":2,"sessions":[{"sessionId":..,"sectionName":..,"startedAt":..,
 *                  "taps":[..],"roster":[..],"lateAfterMinutes":15,"live":true,"paused":false}]}
 *
 * A file written by an older install is still read: an unversioned object for
 * sections, a bare array for sessions, and neither carries the newer keys.
 */
class Store(context: Context) {

    private val dir = File(context.filesDir, "nfc-attendance")
    private val sectionsFile = File(dir, "sections.json")
    private val settingsFile = File(dir, "settings.json")
    private val sessionsFile = File(dir, "sessions.json")
    private val externalRoot = context.getExternalFilesDir(null)

    init {
        if (!dir.exists()) dir.mkdirs()
    }

    private fun read(file: File): String? = try {
        if (file.isFile) file.readText(Charsets.UTF_8) else null
    } catch (t: Throwable) {
        null
    }

    /**
     * Write-then-rename. On this filesystem rename replaces the target in one step,
     * so a reader either sees the whole old file or the whole new one. The two
     * fallbacks exist only for a filesystem that refuses the replace.
     */
    private fun write(file: File, body: String) {
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(body, Charsets.UTF_8)
            if (tmp.renameTo(file)) return
            if (file.delete() && tmp.renameTo(file)) return
        } catch (ignored: Throwable) {
            // fall through to the direct write
        }
        try {
            file.writeText(body, Charsets.UTF_8)
        } finally {
            tmp.delete()
        }
    }

    // ---------------------------------------------------------------- sections

    fun loadSections(): SectionsLoad {
        if (!sectionsFile.isFile) return SectionsLoad(missing = true)
        val text = read(sectionsFile)
            ?: return SectionsLoad(error = "The class list file could not be opened.")
        return try {
            SectionsLoad(sections = parseSections(text))
        } catch (t: Throwable) {
            // The parser's own words are not for a teacher: the file is named, the
            // file is kept, and the two ways forward are on the card.
            SectionsLoad(error = "The file is damaged or was only half-written.")
        }
    }

    private fun parseSections(text: String): List<Section> {
        val root = JSONObject(text)
        val array = root.optJSONArray("sections") ?: JSONArray()
        val out = mutableListOf<Section>()
        var i = 0
        while (i < array.length()) {
            val obj = array.optJSONObject(i)
            i++
            if (obj == null) continue
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) continue
            val students = mutableListOf<Student>()
            val list = obj.optJSONArray("students")
            if (list != null) {
                var j = 0
                while (j < list.length()) {
                    val s = list.optJSONObject(j)
                    j++
                    if (s == null) continue
                    val studentName = s.optString("name", "").trim()
                    val uid = Uid.normalize(s.optString("uid", ""))
                    if (studentName.isEmpty() || uid.isEmpty()) continue
                    students += Student(studentName, uid)
                }
            }
            // A legacy file has no updatedAt; 0 means "never recorded".
            out += Section(
                name = name,
                students = students,
                updatedAt = obj.optLong("updatedAt", 0L),
                subject = obj.optString("subject", "").trim(),
                card = obj.optString("card", "").trim(),
            )
        }
        return out
    }

    fun saveSections(sections: List<Section>) {
        val array = JSONArray()
        for (section in sections) {
            val obj = JSONObject()
            obj.put("name", section.name)
            val students = JSONArray()
            for (student in section.students) {
                val s = JSONObject()
                s.put("name", student.name)
                s.put("uid", student.uid)
                students.put(s)
            }
            obj.put("students", students)
            obj.put("updatedAt", section.updatedAt)
            if (section.subject.isNotBlank()) obj.put("subject", section.subject)
            if (section.card.isNotBlank()) obj.put("card", section.card)
            array.put(obj)
        }
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("sections", array)
        write(sectionsFile, root.toString())
    }

    /**
     * Moves a file that could not be read out of the way instead of deleting it, so
     * starting fresh is never the same as destroying whatever was there.
     * Returns where it went, or null when there was nothing to move.
     */
    fun setAsideUnreadableSections(): File? {
        if (!sectionsFile.isFile) return null
        val target = File(dir, "sections.json.unreadable-" + System.currentTimeMillis())
        return try {
            if (sectionsFile.renameTo(target)) target else null
        } catch (t: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------- settings

    private fun settings(): JSONObject = try {
        JSONObject(read(settingsFile) ?: "{}")
    } catch (t: Throwable) {
        JSONObject()
    }

    private fun putSetting(key: String, value: Any) {
        val obj = settings()
        obj.put(key, value)
        write(settingsFile, obj.toString())
    }

    fun selectedSection(): String = settings().optString("selectedSection", "")

    fun setSelectedSection(name: String) = putSetting("selectedSection", name)

    fun isUidReversed(): Boolean = settings().optBoolean("uidReversed", false)

    fun setUidReversed(value: Boolean) = putSetting("uidReversed", value)

    /** "system", "light" or "dark" - added later, so an older file just has no key. */
    fun themeMode(): String = settings().optString("theme", "dark")

    fun setThemeMode(value: String) = putSetting("theme", value)

    /**
     * The guide opens by itself on the very first launch and never again after
     * that; the header keeps it one tap away for the rest of the app's life.
     */
    fun hasSeenTutorial(): Boolean = settings().optBoolean("tutorialSeen", false)

    fun setTutorialSeen(value: Boolean = true) = putSetting("tutorialSeen", value)

    /** Minutes after the session start that still count as on time. */
    fun lateAfterMinutes(): Int =
        settings().optInt("lateAfterMinutes", AttendanceResolver.DEFAULT_LATE_AFTER_MINUTES)

    fun setLateAfterMinutes(value: Int) = putSetting("lateAfterMinutes", value)

    /** How hard a card tap buzzes: "off", "light", "normal" or "strong". */
    fun hapticStrength(): String = settings().optString("haptics", "normal")

    fun setHapticStrength(value: String) = putSetting("haptics", value)

    /** "plain" (engineering sheet) or "cards" (playing deck). */
    fun visualStyle(): String = settings().optString("visualStyle", "cards")

    fun setVisualStyle(value: String) = putSetting("visualStyle", value)

    /** "pie", "line" or "bar" - the chart the Session report draws. */
    fun chartKind(): String = settings().optString("chart", "pie")

    fun setChartKind(value: String) = putSetting("chart", value)

    /** "nfc" or "qr" - which reader the Scan tab opens with. */
    fun readerMode(): String = settings().optString("readerMode", "nfc")

    fun setReaderMode(value: String) = putSetting("readerMode", value)

    /** Whether a card tap makes a short tone. */
    fun isScanSoundOn(): Boolean = settings().optBoolean("scanSound", true)

    fun setScanSound(value: Boolean) = putSetting("scanSound", value)

    // ---------------------------------------------------------------- sessions

    private fun sessionEntries(): JSONArray {
        val text = read(sessionsFile) ?: return JSONArray()
        return try {
            val trimmed = text.trim()
            // A file written before versioning is a bare array of sessions.
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("sessions") ?: JSONArray()
        } catch (t: Throwable) {
            JSONArray()
        }
    }

    private fun jsonOf(session: AttendanceSession, live: Boolean, paused: Boolean): JSONObject {
        val obj = JSONObject()
        obj.put("sessionId", session.sessionId)
        obj.put("sectionName", session.sectionName)
        obj.put("startedAt", session.startedAtMillis)
        obj.put("lateAfterMinutes", session.lateAfterMinutes)
        obj.put("live", live)
        obj.put("paused", paused)
        val taps = JSONArray()
        for (tap in session.taps) {
            val t = JSONObject()
            t.put("uid", tap.uid)
            t.put("at", tap.atMillis)
            t.put("method", tap.method.name)
            taps.put(t)
        }
        obj.put("taps", taps)
        val roster = JSONArray()
        for (student in session.roster) {
            val s = JSONObject()
            s.put("name", student.name)
            s.put("uid", student.uid)
            roster.put(s)
        }
        obj.put("roster", roster)
        return obj
    }

    private fun sessionOf(obj: JSONObject): AttendanceSession? {
        val id = obj.optString("sessionId", "").trim()
        if (id.isEmpty()) return null
        val taps = mutableListOf<Tap>()
        obj.optJSONArray("taps")?.let { array ->
            var i = 0
            while (i < array.length()) {
                val t = array.optJSONObject(i)
                i++
                if (t == null) continue
                val uid = Uid.normalize(t.optString("uid", ""))
                if (uid.isEmpty()) continue
                // "method" is the current key; "source" is what 0.0.4 wrote, and its
                // CARD means an NFC tap. A file from either version reads correctly.
                val raw = t.optString("method").ifEmpty { t.optString("source", "NFC") }
                val method = when (raw.uppercase()) {
                    "QR" -> AttendanceMethod.QR
                    "MANUAL" -> AttendanceMethod.MANUAL
                    else -> AttendanceMethod.NFC
                }
                taps += Tap(uid, t.optLong("at", 0L), method)
            }
        }
        val roster = mutableListOf<Student>()
        obj.optJSONArray("roster")?.let { array ->
            var i = 0
            while (i < array.length()) {
                val s = array.optJSONObject(i)
                i++
                if (s == null) continue
                val name = s.optString("name", "").trim()
                val uid = Uid.normalize(s.optString("uid", ""))
                if (name.isEmpty() || uid.isEmpty()) continue
                roster += Student(name, uid)
            }
        }
        return AttendanceSession(
            sessionId = id,
            sectionName = obj.optString("sectionName", ""),
            startedAtMillis = obj.optLong("startedAt", 0L),
            taps = taps,
            roster = roster,
            lateAfterMinutes = obj.optInt("lateAfterMinutes", 0),
        )
    }

    /**
     * Creates or replaces this session's row. Called on every tap, which is what
     * makes a running session survive the app being killed; the file stays small
     * because a session is only ever its roster and its taps.
     */
    fun saveSession(session: AttendanceSession, live: Boolean = false, paused: Boolean = false) {
        val entries = sessionEntries()
        val out = JSONArray()
        var replaced = false
        var i = 0
        while (i < entries.length()) {
            val obj = entries.optJSONObject(i)
            i++
            if (obj == null) continue
            if (obj.optString("sessionId") == session.sessionId) {
                out.put(jsonOf(session, live, paused))
                replaced = true
            } else {
                out.put(obj)
            }
        }
        if (!replaced) out.put(jsonOf(session, live, paused))
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("sessions", prune(out))
        write(sessionsFile, root.toString())
    }

    /** The session that was still running when the app last stopped, if any. */
    fun liveSession(): LiveSession? {
        val entries = sessionEntries()
        var i = entries.length() - 1
        while (i >= 0) {
            val obj = entries.optJSONObject(i)
            i--
            if (obj == null || !obj.optBoolean("live", false)) continue
            val session = sessionOf(obj) ?: continue
            return LiveSession(session, obj.optBoolean("paused", false))
        }
        return null
    }

    /** Marks a session as no longer running, keeping its record. */
    fun endSession(sessionId: String) {
        val entries = sessionEntries()
        val out = JSONArray()
        var i = 0
        while (i < entries.length()) {
            val obj = entries.optJSONObject(i)
            i++
            if (obj == null) continue
            if (obj.optString("sessionId") == sessionId) obj.put("live", false)
            out.put(obj)
        }
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("sessions", out)
        write(sessionsFile, root.toString())
    }

    /** Keep the file bounded: the most recent [keep] sessions are the useful ones. */
    private fun prune(entries: JSONArray, keep: Int = 50): JSONArray {
        if (entries.length() <= keep) return entries
        val out = JSONArray()
        for (i in entries.length() - keep until entries.length()) {
            entries.optJSONObject(i)?.let { out.put(it) }
        }
        return out
    }

    // ---------------------------------------------------------------- reports

    fun reportsDir(): File {
        val base = externalRoot ?: dir
        val reports = File(base, "reports")
        if (!reports.exists()) reports.mkdirs()
        return reports
    }

    companion object {
        /** The format this build writes. Older files have no version key and are read as-is. */
        const val SCHEMA_VERSION = 3
    }
}
