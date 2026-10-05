package com.nezzar.nfcattendance.data

/**
 * How much of a buzz a card tap gets. The platform has no amplitude API that works
 * without the VIBRATE permission, so strength maps onto the window's own haptic
 * feedback: nothing, the light tick, the long press, or the long press twice.
 */
enum class HapticStrength {
    OFF,
    LIGHT,
    NORMAL,
    STRONG;

    companion object {
        fun fromStored(value: String): HapticStrength = when (value.lowercase()) {
            "off" -> OFF
            "light" -> LIGHT
            "strong" -> STRONG
            else -> NORMAL
        }
    }
}
