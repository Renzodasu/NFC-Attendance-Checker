package com.nezzar.nfcattendance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

private data class GuideStep(
    val icon: Int,
    val title: String,
    val body: String,
)

/**
 * The guide as six numbered steps on one card.
 *
 * It used to be a hand of playing cards, with the suits doubling as the step
 * numbers. Nothing in the app is a playing card any more, so the steps say
 * what they are: a number, then the words.
 */
private val Steps = listOf(
    GuideStep(
        R.drawable.ic_nav_sections, "Make a class",
        "Tap New class, type the class name, then Save. One class keeps one list of students.",
    ),
    GuideStep(
        R.drawable.ic_nav_register, "Add each student",
        "On Register, tap Start registering, hold an ID card to the phone, then type the name.",
    ),
    GuideStep(
        R.drawable.ic_nav_scan, "Take attendance",
        "On Scan, tap Start session and hold each ID to the phone as students walk in. " +
            "Tap Pause when the door is quiet.",
    ),
    GuideStep(
        R.drawable.ic_check, "Late arrivals",
        "A card read more than 15 minutes after the start counts as LATE. " +
            "You can change that window in Settings.",
    ),
    GuideStep(
        R.drawable.ic_export, "Export the list",
        "On Report, tap Export .xlsx report, then Share. The file opens in Excel or Google Sheets.",
    ),
    GuideStep(
        R.drawable.ic_share, "If a card is unknown",
        "A card that is not on the list is flagged, never dropped. Names and IDs never leave your phone.",
    ),
)

/**
 * How to use the app, one step at a time.
 *
 * Three ways forward, because people reach for different ones: swipe the step
 * away, tap Next step, or tap the dots. Back is there so nobody has to lose
 * their place, and the last step opens the app.
 */
@Composable
fun TutorialScreen(state: AppState, modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val finished = index > Steps.lastIndex

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
        Note("Step " + (index + 1) + " of " + Steps.size + " - swipe it away, or tap Next step.")

        Spacer(Modifier.height(14.dp))

        Box(
            // A step, not a fixed slab: on a short phone it shrinks with the room
            // left for it, so the buttons below never get pushed off the screen.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp),
            contentAlignment = Alignment.Center,
        ) {
            SwipeableStep(
                step = Steps[index],
                number = index + 1,
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
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Steps.size) { dot ->
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
                Text(if (index == Steps.lastIndex) "Finish" else "Next step")
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

/** The step on top: it tilts with the finger and is thrown by a flick. */
@Composable
private fun SwipeableStep(
    step: GuideStep,
    number: Int,
    offset: Float,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
) {
    StepFace(
        step = step,
        number = number,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .graphicsLayer {
                translationX = offset
                rotationZ = (offset / 55f).coerceIn(-8f, 8f)
            }
            .pointerInput(step) {
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

/** One step: the count, the icon, the name of the step, then the sentence. */
@Composable
private fun StepFace(step: GuideStep, number: Int, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(width = 1.5.dp, color = accent),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent, shape = CircleShape) {
                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = number.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                SectionLabel("Step " + number + " of " + Steps.size)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(step.icon),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = step.title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Note("Swipe or tap Next step")
            }
        }
    }
}

/** Six steps walked: what is left is to open the app. */
@Composable
private fun FinishCard(state: AppState, onReplay: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(width = 1.5.dp, color = accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(color = accent, shape = CircleShape) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "You are ready",
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Start with a class, then register the students in it. " +
                    "This guide stays under How to use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
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
