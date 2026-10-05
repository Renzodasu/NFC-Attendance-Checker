package com.nezzar.nfcattendance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import kotlin.math.abs
import kotlinx.coroutines.launch

private data class GuideCard(
    val icon: Int,
    val step: String,
    val title: String,
    val body: String,
)

private val Deck = listOf(
    GuideCard(
        R.drawable.ic_nav_sections, "1", "Create a section",
        "Name the class - BSCE-4B, for example. One section is one roster, and everything else hangs off it.",
    ),
    GuideCard(
        R.drawable.ic_nav_register, "2", "Register each student card",
        "On Register, press Start registering, hold the ID card to the phone, then type the name and Save. " +
            "One tap per student; no student number is ever asked for.",
    ),
    GuideCard(
        R.drawable.ic_nav_scan, "3", "Scan the class",
        "On Scan, press Start session, then tap every ID as the class walks in. Press Pause when the door is " +
            "quiet - the session stays open, so late arrivals are still counted.",
    ),
    GuideCard(
        R.drawable.ic_check, "4", "Late arrivals",
        "A card read more than 15 minutes after the start is counted as LATE, not present. Change that window " +
            "under Settings, and the report lists late arrivals with their time.",
    ),
    GuideCard(
        R.drawable.ic_export, "5", "Export the result",
        "On Report, press Export .xlsx report, then Share. The file lands in Documents/NFC Attendance with the " +
            "date and time of every scan.",
    ),
    GuideCard(
        R.drawable.ic_share, "6", "Unmatched cards and privacy",
        "A card that is not on the roster is flagged, never dropped - if it happens, the UID byte-order switch " +
            "under Settings is the fix. A name joined to a UID is personal data under RA 10173, and it never " +
            "leaves the phone.",
    ),
)

/**
 * The guide as a deck of cards: the top card follows the finger, tilts with it,
 * and is thrown off the stack on a swipe. Under it the next cards sit slightly
 * smaller and rotated, so the stack reads as a stack before anything moves.
 */
@Composable
fun TutorialScreen(state: AppState, modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val finished = index > Deck.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (finished) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("That is the whole app", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Note("Four steps and two habits. The same guide stays under How to use in the sidebar.")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { state.closeOverlay() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open the app")
                    }
                }
            }
            return@Column
        }

        Text(
            text = "How to use",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Note("Swipe the card away to see the next one. " + (Deck.size - index) + " left.")

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            contentAlignment = Alignment.Center,
        ) {
            for (depth in 2 downTo 0) {
                val cardIndex = index + depth
                if (cardIndex > Deck.lastIndex) continue
                val card = Deck[cardIndex]

                if (depth == 0) {
                    SwipeableCard(
                        card = card,
                        offset = offset.value,
                        onDrag = { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                        onRelease = { width ->
                            val thrown = abs(offset.value) > width * 0.28f
                            scope.launch {
                                if (thrown) {
                                    offset.animateTo(
                                        targetValue = if (offset.value > 0) width * 1.8f else -width * 1.8f,
                                        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                                    )
                                    index += 1
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.55f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth(1f - depth * 0.05f)
                            .height((420 - depth * 14).dp)
                            .graphicsLayer {
                                rotationZ = depth * -3.5f
                                translationY = depth * -10f
                                alpha = 0.55f
                            },
                    ) {}
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Deck.size) { dot ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (dot == index) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (dot <= index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { index = (index + 1).coerceAtMost(Deck.size) }) {
                Text("Skip this card")
            }
            TextButton(onClick = { state.closeOverlay() }) { Text("Close the guide") }
        }
    }
}

/** The top card: tilts with the finger and is thrown by a flick. */
@Composable
private fun SwipeableCard(
    card: GuideCard,
    offset: Float,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer {
                translationX = offset
                rotationZ = (offset / 55f).coerceIn(-14f, 14f)
            }
            .pointerInput(card) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onRelease(size.width.toFloat()) },
                    onDragCancel = { onRelease(size.width.toFloat()) },
                )
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(card.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "STEP " + card.step,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = card.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Note("Swipe me away")
            }
        }
    }
}
