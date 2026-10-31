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
    ): Edit {
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "A section name cannot be blank.")
        if (find(sections, name) != null) {
            return Edit(sections, error = "A section named " + name + " already exists.")
        }
        val created = Section(name, emptyList(), now, subject.trim())
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
        // A blank UID is "no card yet": it must never match the first unbound row.
        if (canonical.isEmpty()) return null
        return target.students.firstOrNull { Uid.normalize(it.uid) == canonical }?.name
    }

    /** The learners still waiting for a card, in roster order. */
    fun unbound(section: Section?): List<Student> =
        section?.students?.filter { it.uid.isBlank() } ?: emptyList()

    /**
     * Binds one tapped card to a learner who arrived without one - the state a
     * class read out of a school form starts in. The learner keeps their name and
     * sex; only the UID is filled in, so the roster that was read from the form
     * stays recognisably the form's roster.
     */
    fun bindCard(
        sections: List<Section>,
        sectionName: String,
        studentName: String,
        uid: String,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        val canonical = Uid.normalize(uid)
        if (!Uid.isValid(canonical)) {
            return Edit(sections, error = "That card did not read as a whole UID.")
        }
        for (section in sections) {
            val holder = section.students.firstOrNull { Uid.normalize(it.uid) == canonical } ?: continue
            return Edit(
                sections,
                error = "That card is already registered to " + holder.name + " in " + section.name + ".",
            )
        }
        val wanted = studentName.trim()
        if (wanted.isEmpty()) return Edit(sections, error = "Choose who this card belongs to.")
        val learner = target.students.firstOrNull {
            it.uid.isBlank() && it.name.equals(wanted, ignoreCase = true)
        } ?: return Edit(sections, error = "No learner called " + wanted + " is waiting for a card.")
        if (learner.name.isBlank()) return Edit(sections, error = "That learner has no name.")
        val students = target.students.map {
            if (it.uid.isBlank() && it.name.equals(wanted, ignoreCase = true)) it.copy(uid = canonical) else it
        }
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = now) else it
        }
        return Edit(updated, message = "Bound " + canonical + " to " + learner.name + ".")
    }

    /**
     * A class read out of a school form: its learners arrive with a name and a
     * sex but no card, so they start in the "no card yet" state. Re-importing the
     * same form into the same class only adds the names it does not already have,
     * and never touches a card that is already bound.
     */
    fun importForm(
        sections: List<Section>,
        rawName: String,
        students: List<Student>,
        header: Sf2Header?,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "A class name cannot be blank.")
        if (students.isEmpty()) return Edit(sections, error = "That school form has no learners in it.")
        val target = find(sections, name)
        if (target == null) {
            val created = Section(name, students.map { it.copy(uid = "") }, now, "", header)
            return Edit(
                sections + created,
                message = "Imported " + created.students.size + " learner(s) into " + name +
                    " - no cards bound yet.",
            )
        }
        val known = target.students.map { it.name.trim().uppercase() }.toSet()
        val added = students
            .filter { it.name.trim().uppercase() !in known }
            .map { it.copy(uid = "") }
        val takenHeader = target.sf2 ?: header
        if (added.isEmpty() && takenHeader == target.sf2) {
            return Edit(sections, message = name + " already has every learner from that form.")
        }
        val updated = sections.map {
            if (it.name == target.name) {
                target.copy(
                    students = target.students + added,
                    updatedAt = if (added.isEmpty()) target.updatedAt else now,
                    sf2 = takenHeader,
                )
            } else {
                it
            }
        }
        val message = if (added.isEmpty()) {
            "Took the school form details into " + name + "."
        } else {
            "Added " + added.size + " learner(s) to " + name + " - no cards bound yet."
        }
        return Edit(updated, message = message)
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
        if (!Uid.isValid(canonical)) return Edit(sections, error = "That card did not read as a whole UID.")
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
        if (canonical.isEmpty()) return Edit(sections, error = "That learner has no card yet, so there is nothing to rename.")
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
        if (canonical.isEmpty()) {
            return Edit(sections, error = "That learner has no card yet, so there is no card to remove.")
        }
        val students = target.students.filter { Uid.normalize(it.uid) != canonical }
        if (students.size == target.students.size) {
            return Edit(sections, error = "No student with UID " + canonical + " in " + target.name + ".")
        }
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = now) else it
        }
        return Edit(updated, message = "Removed " + canonical + " from " + target.name + ".")
    }

    /**
     * Rename by position, not by card. A class read out of the school form has no
     * UIDs at all, and an empty UID must never be read as "the first unbound
     * learner" - that is how a correction lands on the wrong child.
     */
    fun renameStudentAt(
        sections: List<Section>,
        sectionName: String,
        index: Int,
        rawName: String,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        if (index !in target.students.indices) {
            return Edit(sections, error = "That learner is no longer on the roster.")
        }
        val name = rawName.trim()
        if (name.isEmpty()) return Edit(sections, error = "A student name cannot be blank.")
        val learner = target.students[index]
        if (learner.name == name) return Edit(sections, message = "Student name unchanged.")
        val students = target.students.toMutableList()
        students[index] = learner.copy(name = name)
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = now) else it
        }
        return Edit(updated, message = "Renamed " + learner.name + " to " + name + ".")
    }

    /** Remove by position, for the same reason [renameStudentAt] exists. */
    fun removeStudentAt(
        sections: List<Section>,
        sectionName: String,
        index: Int,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        if (index !in target.students.indices) {
            return Edit(sections, error = "That learner is no longer on the roster.")
        }
        val learner = target.students[index]
        val students = target.students.toMutableList().also { it.removeAt(index) }
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = now) else it
        }
        return Edit(updated, message = "Removed " + learner.name + " from " + target.name + ".")
    }

    /**
     * Bind a card to one row of the roster, chosen by position. [bindCard] matches
     * by name, which cannot tell two namesakes apart; this is what the Register
     * picker calls, so the row the teacher pointed at is the row that changes.
     */
    fun bindCardAt(
        sections: List<Section>,
        sectionName: String,
        index: Int,
        uid: String,
        now: Long = System.currentTimeMillis(),
    ): Edit {
        val target = find(sections, sectionName) ?: return Edit(sections, error = "Select a section first.")
        if (index !in target.students.indices) {
            return Edit(sections, error = "That learner is no longer on the roster.")
        }
        val canonical = Uid.normalize(uid)
        if (!Uid.isValid(canonical)) {
            return Edit(sections, error = "That card did not read as a whole UID.")
        }
        for (section in sections) {
            val holder = section.students.firstOrNull { Uid.normalize(it.uid) == canonical } ?: continue
            return Edit(
                sections,
                error = "That card is already registered to " + holder.name + " in " + section.name + ".",
            )
        }
        val learner = target.students[index]
        if (learner.name.isBlank()) return Edit(sections, error = "That learner has no name.")
        val students = target.students.toMutableList()
        students[index] = learner.copy(uid = canonical)
        val updated = sections.map {
            if (it.name == target.name) target.copy(students = students, updatedAt = now) else it
        }
        return Edit(updated, message = "Bound " + canonical + " to " + learner.name + ".")
    }
}
