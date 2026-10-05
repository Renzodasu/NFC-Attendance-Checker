package com.nezzar.nfcattendance.ui

import com.nezzar.nfcattendance.ui.theme.isBrandDark
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezzar.nfcattendance.R
import kotlin.math.abs
import kotlinx.coroutines.launch

private data class GuideCard(
    val icon: Int,
    val face: String,
    val suit: String,
    val title: String,
    val body: String,
)

/**
 * The guide as one hand of cards: six cards, each one a real card face in the
 * same style as a class card. The corner index counts the step, so the order is
 * legible even with the card in a stack.
 *
 * Every card is short on purpose - one idea, one or two sentences.
 */
private val Deck = listOf(
    GuideCard(
        R.drawable.ic_nav_sections, "1", "\u2660", "Make a class",
        "Tap New section, type the class name, then save. One class keeps one list of students.",
    ),
    GuideCard(
        R.drawable.ic_nav_register, "2", "\u2665", "Add each student",
        "On Register, tap Start registering, hold an ID card to the phone, then type the name.",
    ),
    GuideCard(
        R.drawable.ic_nav_scan, "3", "\u2666", "Take attendance",
        "On Scan, tap Start session and hold each ID to the phone as students walk in. Tap Pause when the door is quiet.",
    ),
    GuideCard(
        R.drawable.ic_check, "4", "\u2663", "Late arrivals",
        "A card read more than 15 minutes after the start counts as LATE. You can change that window in Settings.",
    ),
    GuideCard(
        R.drawable.ic_export, "5", "\u2660", "Export the list",
        "On Report, tap Export .xlsx report, then Share. The file opens in Excel or Google Sheets.",
    ),
    GuideCard(
        R.drawable.ic_share, "6", "\u2665", "If a card is unknown",
        "A card that is not on the list is flagged, never dropped. Names and IDs never leave your phone.",
    ),
)

/** The card's own proportions, so the guide's cards look like the real thing. */
private const val CardAspect = 0.7f

/**
 * How to use the app, dealt as a hand of playing cards.
 *
 * Three ways forward, because people reach for different ones: swipe the card
 * away, tap Next card, or tap the dots. Back is there so nobody has to lose
 * their place, and the last card opens the app.
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
            FinishCard(state, onReplay = { index = 0 })
            return@Column
        }

        Text(
            text = "How to use",
            style = MaterialTheme.typography.headlineMedium,
        )
        Note("Card " + (index + 1) + " of " + Deck.size + " - swipe it away, or tap Next card.")

        Spacer(Modifier.height(14.dp))

        Box(
            // A guide card, not a fixed slab: on a short phone the deck shrinks
            // with the room left for it, so the buttons below never get pushed
            // off the screen.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp),
            contentAlignment = Alignment.Center,
        ) {
            for (depth in 2 downTo 0) {
                val cardIndex = index + depth
                if (cardIndex > Deck.lastIndex) continue
                if (depth == 0) {
                    SwipeableCard(
                        card = Deck[cardIndex],
                        step = cardIndex + 1,
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
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .fillMaxHeight(1f - depth * 0.06f)
                            .aspectRatio(CardAspect)
                            .graphicsLayer {
                                rotationZ = depth * -3.5f
                                translationY = depth * -10f
                                alpha = 0.5f
                            },
                    ) {}
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Deck.size) { dot ->
                Surface(
                    color = if (dot <= index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (dot == index) 10.dp else 8.dp),
                ) {}
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { if (index > 0) index -= 1 },
                enabled = index > 0,
                modifier = Modifier.height(54.dp),
            ) {
                Text("Back")
            }
            Button(
                onClick = {
                    index += 1
                    scope.launch { offset.snapTo(0f) }
                },
                modifier = Modifier.weight(1f).height(54.dp),
            ) {
                Text(if (index == Deck.lastIndex) "Finish" else "Next card")
            }
        }

        TextButton(
            onClick = { state.closeOverlay() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Close the guide")
        }
    }
}

/** The card on top: it tilts with the finger and is thrown by a flick. */
@Composable
private fun SwipeableCard(
    card: GuideCard,
    step: Int,
    offset: Float,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
) {
    GuideFace(
        card = card,
        step = step,
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(CardAspect)
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
    )
}

/**
 * A guide card drawn as a playing card: the ivory face, the step number and suit
 * in two opposite corners, a faint echo of the suit behind the words, and the
 * step's icon where a face card would carry its picture.
 */
@Composable
private fun GuideFace(card: GuideCard, step: Int, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    // A wash on black, a hint of tint on white.
    val wash = if (isBrandDark()) 0.16f else 0.06f

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(width = 2.dp, color = accent),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = wash), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Text(
                text = card.suit,
                fontSize = 52.sp,
                color = accent.copy(alpha = 0.13f),
                modifier = Modifier.align(Alignment.BottomStart),
            )
            CardIndex(
                face = card.face,
                suit = card.suit,
                ink = accent,
                modifier = Modifier.align(Alignment.TopStart),
                rankSize = 26.sp,
                suitSize = 18.sp,
            )
            CardIndex(
                face = card.face,
                suit = card.suit,
                ink = accent.copy(alpha = 0.55f),
                rotation = 180f,
                modifier = Modifier.align(Alignment.BottomEnd),
                rankSize = 26.sp,
                suitSize = 18.sp,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(card.icon),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "STEP $step OF ${Deck.size}",
                    fontSize = 12.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = card.title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = card.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Note("Swipe or tap Next")
            }
        }
    }
}

/** The hand is dealt: what is left is to open the app. */
@Composable
private fun FinishCard(state: AppState, onReplay: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(width = 2.dp, color = accent),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = "\u2660",
                fontSize = 52.sp,
                color = accent.copy(alpha = 0.13f),
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "You are ready",
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Note("Start with a class, then register the students in it. This guide stays under How to use.")
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { state.closeOverlay() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("Start using the app")
                }
                TextButton(onClick = onReplay) {
                    Text("Play the guide again")
                }
            }
        }
    }
}
