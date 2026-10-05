package com.nezzar.nfcattendance.data

import kotlin.random.Random

/**
 * A complete 52-card deck, used to give every class section its own card face.
 * Pure Kotlin, so the picking rule is unit-testable.
 */
object PlayingCards {

    val SUITS = listOf("\u2660", "\u2665", "\u2666", "\u2663")
    val RANKS = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

    /** All 52 faces, rank then suit: "A♠" through "K♣". */
    val DECK: List<String> = SUITS.flatMap { suit -> RANKS.map { rank -> rank + suit } }

    /** Hearts and diamonds print red; spades and clubs print in the text colour. */
    fun isRed(card: String): Boolean = card.endsWith("\u2665") || card.endsWith("\u2666")

    /** Just the rank, for the corner of the card. */
    fun rank(card: String): String = card.dropLast(1)

    /** Just the suit. */
    fun suit(card: String): String = if (card.isEmpty()) "" else card.takeLast(1)

    /**
     * A face no other section is holding. With all 52 taken it starts over with a
     * random one, because a class list longer than a deck still needs a card.
     */
    fun pick(taken: Set<String>, random: Random = Random.Default): String {
        val free = DECK.filterNot { it in taken }
        return if (free.isEmpty()) DECK.random(random) else free.random(random)
    }
}
