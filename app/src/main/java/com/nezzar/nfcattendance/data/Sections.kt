package com.nezzar.nfcattendance.data

/**
 * Pure Class Section / roster operations. No Android imports, so every rule
 * about naming, duplicates and edits is unit-testable on the JVM.
 *
 * Every edit that actually changes a section's ROSTER stamps [Section.updatedAt].
 * Renaming the section itself does not: the roster did not change.
 */
object Sections {

    /** Outcome of one edit: the resulting list plus what to tell the operator. */
    data class Edit(
        val sections: List<Section>,
        val message: String = "",
        val error: String = "",
    )

    fun find(sections: List<Section>, name: String): Section? {
        val wanted = name.trim()
        return sections.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
    }

    fun create(
        sections: List<Section>,
        rawName: String,
        now: Long = System.currentTimeMillis(),
        subject: String = "",
        card: String = "",
    ): Edit {
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "A section name cannot be blank.")
        if (find(sections, name) != null) {
            return Edit(sections, error = "A section named " + name + " already exists.")
        }
        // A chosen face wins; otherwise deal one no other section is holding.
        val taken = sections.map { it.card }.filter { it.isNotBlank() }.toSet()
        val face = card.trim().let { if (it.isNotEmpty()) it else PlayingCards.pick(taken) }
        val created = Section(name, emptyList(), now, subject.trim(), face)
        val message = if (created.subject.isEmpty()) {
            "Section " + name + " created."
        } else {
            "Section " + name + " created for " + created.subject + "."
        }
        return Edit(sections + created, message = message)
    }

    /**
     * The subject is part of what a section IS, not of its roster, so setting it
     * leaves [Section.updatedAt] alone - the same rule rename follows.
     */
    fun setSubject(sections: List<Section>, sectionName: String, rawSubject: String): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "No section named " + sectionName + ".")
        val subject = rawSubject.trim()
        if (subject == target.subject) return Edit(sections, message = "Subject unchanged.")
        val updated = sections.map {
            if (it.name == target.name) it.copy(subject = subject) else it
        }
        val message = if (subject.isEmpty()) {
            "Subject cleared for " + target.name + "."
        } else {
            target.name + " is now " + subject + "."
        }
        return Edit(updated, message = message)
    }

    fun rename(sections: List<Section>, oldName: String, rawName: String): Edit {
        val target = find(sections, oldName) ?: return Edit(sections, error = "No section named " + oldName + ".")
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "A section name cannot be blank.")
        val clash = find(sections, name)
        if (clash != null && clash.name != target.name) {
            return Edit(sections, error = "A section named " + name + " already exists.")
        }
        if (name == target.name) return Edit(sections, message = "Section name unchanged.")
        // The roster is untouched, so updatedAt deliberately stays where it was.
        val updated = sections.map { if (it.name == target.name) it.copy(name = name) else it }
        return Edit(updated, message = "Section " + target.name + " renamed to " + name + ".")
    }

    fun delete(sections: List<Section>, name: String): Edit {
        val target = find(sections, name) ?: return Edit(sections, error = "No section named " + name + ".")
        val updated = sections.filter { it.name != target.name }
        return Edit(updated, message = "Section " + target.name + " deleted.")
    }

    /** The name already registered against this UID in this section, if any. */
    fun registeredName(section: Section?, uid: String): String? {
        val target = section ?: return null
        val canonical = Uid.normalize(uid)
        return target.students.firstOrNull { Uid.normalize(it.uid) == canonical }?.name
    }

    /**
     * Register one card. A UID that is already in the section never becomes a
     * second row: its name is updated in place instead.
     */
    fun register(
        sections: List<Section>,
        sectionName: String,
        uid: String,
        rawName: String,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "Enter the student's name before saving.")
        val canonical = Uid.normalize(uid)
        val existing = target.students.firstOrNull { Uid.normalize(it.uid) == canonical }
        val students = if (existing == null) {
            target.students + Student(name, canonical)
        } else {
            target.students.map { if (Uid.normalize(it.uid) == canonical) it.copy(name = name) else it }
        }
        val stamp = if (students != target.students) now else target.updatedAt
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = stamp) else it
        }
        val message = if (existing == null) {
            "Registered " + name + " (" + canonical + ") in " + target.name + "."
        } else {
            "Updated " + existing.name + " to " + name + " (" + canonical + ")."
        }
        return Edit(updated, message = message)
    }

    fun renameStudent(
        sections: List<Section>,
        sectionName: String,
        uid: String,
        rawName: String,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "A student name cannot be blank.")
        val canonical = Uid.normalize(uid)
        var found = false
        val students = target.students.map {
            if (Uid.normalize(it.uid) == canonical) {
                found = true
                it.copy(name = name)
            } else {
                it
            }
        }
        if (!found) return Edit(sections, error = "No student with UID " + canonical + " in " + target.name + ".")
        val stamp = if (students != target.students) now else target.updatedAt
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = stamp) else it
        }
        return Edit(updated, message = "Renamed " + canonical + " to " + name + ".")
    }

    fun removeStudent(
        sections: List<Section>,
        sectionName: String,
        uid: String,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        val canonical = Uid.normalize(uid)
        val students = target.students.filter { Uid.normalize(it.uid) != canonical }
        if (students.size == target.students.size) {
            return Edit(sections, error = "No student with UID " + canonical + " in " + target.name + ".")
        }
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = now) else it
        }
        return Edit(updated, message = "Removed " + canonical + " from " + target.name + ".")
    }
}
