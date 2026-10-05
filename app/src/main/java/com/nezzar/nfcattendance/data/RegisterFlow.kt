package com.nezzar.nfcattendance.data

/**
 * The Register tab's state machine: idle -> card captured -> name required ->
 * saved -> re-armed for the next card. Pure Kotlin (no Android, no Compose) so
 * every transition is unit-testable.
 */
data class RegisterState(
    val armed: Boolean = false,
    val pendingUid: String? = null,
    val duplicateOf: String? = null,
    val savedCount: Int = 0,
    val lastSaved: String? = null,
    val status: String = "Idle - not registering.",
    val message: String = "",
)

object RegisterFlow {

    fun arm(state: RegisterState, section: Section?): RegisterState {
        val target = section
        if (target == null) {
            return state.copy(armed = false, status = "Idle - not registering.", message = "Create a section first.")
        }
        return state.copy(
            armed = true,
            pendingUid = null,
            duplicateOf = null,
            status = "Registering into " + target.name + " - tap a student ID.",
            message = "",
        )
    }

    fun disarm(state: RegisterState): RegisterState = state.copy(
        armed = false,
        pendingUid = null,
        duplicateOf = null,
        status = "Idle - not registering.",
        message = "",
    )

    /** A card arrived in register mode. It only ever touches the pending slot. */
    fun onTap(state: RegisterState, uid: String, section: Section?): RegisterState {
        if (!state.armed) {
            return state.copy(message = "Not registering - press Start registering. Tap of " + uid + " ignored.")
        }
        if (section == null) {
            return state.copy(message = "Select a section on the Sections tab first.")
        }
        val canonical = Uid.normalize(uid)
        val known = Sections.registeredName(section, canonical)
        val message = if (known != null) {
            "Card " + canonical + " is already registered as " + known + "."
        } else {
            "Card read: " + canonical + ". Type the student's name and save."
        }
        return state.copy(pendingUid = canonical, duplicateOf = known, message = message)
    }

    fun cancelPending(state: RegisterState): RegisterState = state.copy(
        pendingUid = null,
        duplicateOf = null,
        message = "Card skipped - nothing was saved.",
    )

    /**
     * Save the typed name for the pending card. A blank name is refused with a
     * visible reason and nothing is written.
     */
    fun save(
        state: RegisterState,
        rawName: String,
        sections: List<Section>,
        sectionName: String,
    ): Pair<RegisterState, List<Section>> {
        if (!state.armed) {
            return state.copy(message = "Not registering - press Start registering.") to sections
        }
        val uid = state.pendingUid
            ?: return state.copy(message = "No card tapped yet.") to sections
        val name = rawName.trim()
        if (name.isEmpty()) {
            return state.copy(
                message = "Refused: the name is blank. Type the student's name, or skip this card.",
            ) to sections
        }
        val edit = Sections.register(sections, sectionName, uid, name)
        if (edit.error.isNotEmpty()) {
            return state.copy(message = edit.error) to sections
        }
        val saved = state.copy(
            pendingUid = null,
            duplicateOf = null,
            savedCount = state.savedCount + 1,
            lastSaved = name + "  " + uid,
            message = edit.message,
        )
        return saved to edit.sections
    }
}
