package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The corner of a playing card: the rank over the suit, both inked in the
 * caller's colour. Every face draws it twice, rotated 180 degrees against each
 * other, and that pairing is what makes a plain rectangle read as a card.
 *
 * Shared by the class cards on the shelf and the guide's cards, so the two can
 * never drift apart.
 */
@Composable
fun CardIndex(
    face: String,
    suit: String,
    ink: Color,
    rotation: Float = 0f,
    modifier: Modifier = Modifier,
    rankSize: TextUnit = 22.sp,
    suitSize: TextUnit = 16.sp,
) {
    Column(
        modifier = modifier.rotate(rotation),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = face,
            fontFamily = FontFamily.Serif,
            fontSize = rankSize,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
        Text(text = suit, fontSize = suitSize, color = ink)
    }
}
