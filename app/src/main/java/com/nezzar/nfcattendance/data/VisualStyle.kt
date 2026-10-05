package com.nezzar.nfcattendance.data

/**
 * How the app draws a class section. Independent of light/dark mode: this is the
 * texture, not the lighting.
 *
 * PLAIN is the default - an engineering drawing sheet, which is what a class
 * schedule in a civil engineering department actually looks like.
 * CARDS is the playing-card deck.
 */
enum class VisualStyle {
    PLAIN,
    CARDS,
    SOLIDS;

    companion object {
        fun fromStored(value: String): VisualStyle = when (value.lowercase()) {
            "cards" -> CARDS
            "solids" -> SOLIDS
            else -> PLAIN
        }
    }
}
