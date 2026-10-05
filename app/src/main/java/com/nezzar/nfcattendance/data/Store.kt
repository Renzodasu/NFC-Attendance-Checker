package com.nezzar.nfcattendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All persistence, unchanged in spirit: plain JSON files in the app's private
 * storage plus a reports directory in the app-external files dir. No database.
 *
 * sections.json  {"sections":[{"name":"...","subject":"...","students":[{"name":..,"uid":..}]}]}
 * settings.json  {"selectedSection":"...","uidReversed":false,"theme":"dark","tutorialSeen":true}
 * sessions.json  [{"sessionId":..,"sectionName":..,"startedAt":..,"taps":[..],"roster":[..]}]
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

    private fun write(file: File, body: String) {
        if (!dir.exists()) dir.mkdirs()
        file.writeText(body, Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- sections

    fun loadSections(): List<Section> {
        val text = read(sectionsFile) ?: return emptyList()
        val out = mutableListOf<Section>()
        try {
            val root = JSONObject(text)
            val array = root.optJSONArray("sections") ?: JSONArray()
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
        } catch (t: Throwable) {
            // A damaged or legacy file must never crash the app: report nothing
            // and start from an empty list of sections.
            return emptyList()
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
        root.put("sections", array)
        write(sectionsFile, root.toString())
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
     * that; the sidebar keeps it one tap away for the rest of the app's life.
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

    /** Whether a card tap makes a short tone. */
    fun isScanSoundOn(): Boolean = settings().optBoolean("scanSound", true)

    fun setScanSound(value: Boolean) = putSetting("scanSound", value)

    // ---------------------------------------------------------------- sessions

    fun saveSession(session: AttendanceSession) {
        val array = try {
            JSONArray(read(sessionsFile) ?: "[]")
        } catch (t: Throwable) {
            JSONArray()
        }
        val obj = JSONObject()
        obj.put("sessionId", session.sessionId)
        obj.put("sectionName", session.sectionName)
        obj.put("startedAt", session.startedAtMillis)
        val taps = JSONArray()
        for (tap in session.taps) {
            val t = JSONObject()
            t.put("uid", tap.uid)
            t.put("at", tap.atMillis)
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
        array.put(obj)
        write(sessionsFile, array.toString())
    }

    // ---------------------------------------------------------------- reports

    fun reportsDir(): File {
        val base = externalRoot ?: dir
        val reports = File(base, "reports")
        if (!reports.exists()) reports.mkdirs()
        return reports
    }
}
