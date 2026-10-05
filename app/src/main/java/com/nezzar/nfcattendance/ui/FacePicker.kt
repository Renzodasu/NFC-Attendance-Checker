package com.nezzar.nfcattendance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.PlayingCards

/**
 * A card face, asked for in one field. Tapping the field reveals a box holding
 * every symbol and every number together, so a whole face is picked in place
 * instead of hunting through two separate lists - and the box stays open while
 * both halves are chosen.
 *
 * Leaving it on "Any" lets the app deal a face no other class is holding.
 */
@Composable
fun FacePicker(
    suit: String,
    rank: String,
    onSuit: (String) -> Unit,
    onRank: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        FaceField(
            label = "Face",
            value = faceText(suit, rank),
            onClick = { open = true },
        )
        AnimatedVisibility(visible = open) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayingCards.SUITS.forEach { glyph ->
                            FaceChoice(
                                label = glyph,
                                selected = glyph == suit,
                                onClick = { onSuit(glyph) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    PlayingCards.RANKS.chunked(7).forEach { line ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            line.forEach { value ->
                                FaceChoice(
                                    label = value,
                                    selected = value == rank,
                                    onClick = { onRank(value) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(7 - line.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Note(
                            text = if (suit.isEmpty() || rank.isEmpty()) {
                                "Pick a symbol and a number."
                            } else {
                                rank + suit
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { open = false }) { Text("Done") }
                    }
                }
            }
        }
    }
}

/** "Any" until something is picked, then the face itself: "9♥". */
private fun faceText(suit: String, rank: String): String = when {
    suit.isEmpty() && rank.isEmpty() -> "Any"
    suit.isEmpty() -> rank
    rank.isEmpty() -> suitText(suit)
    else -> rank + suit
}

/** "♠  Spades" when a symbol is held, "Any" when the face is still open. */
private fun suitText(suit: String): String {
    if (suit.isEmpty()) return "Any"
    val index = PlayingCards.SUITS.indexOf(suit)
    val name = if (index >= 0) PlayingCards.SUIT_NAMES[index] else ""
    return if (name.isEmpty()) suit else suit + "  " + name
}

@Composable
private fun FaceField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** One pick inside the revealed box: a symbol or a number. */
@Composable
private fun FaceChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier
            .height(40.dp)
            .pressScale(interaction, pressed = 0.94f)
            .cardClick(interaction, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
