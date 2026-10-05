package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The section texture survives a restart.
 *
 * Settings stores `VisualStyle.name.lowercase()`, so every enum constant has to
 * read back as itself. Plain once fell through to Cards, which meant choosing
 * it only lasted until the app was closed.
 */
class VisualStyleTest {

    @Test
    fun everyStyleRoundTripsThroughItsStoredName() {
        for (style in VisualStyle.entries) {
            assertEquals(style, VisualStyle.fromStored(style.name.lowercase()))
        }
    }

    @Test
    fun plainIsNotTheDefault() {
        assertEquals(VisualStyle.PLAIN, VisualStyle.fromStored("plain"))
        assertEquals(VisualStyle.PLAIN, VisualStyle.fromStored("PLAIN"))
    }

    @Test
    fun cardsAndSolidsReadBack() {
        assertEquals(VisualStyle.CARDS, VisualStyle.fromStored("cards"))
        assertEquals(VisualStyle.SOLIDS, VisualStyle.fromStored("solids"))
    }

    @Test
    fun anUnknownValueFallsBackToCards() {
        assertEquals(VisualStyle.CARDS, VisualStyle.fromStored(""))
        assertEquals(VisualStyle.CARDS, VisualStyle.fromStored("nonsense"))
    }
}
