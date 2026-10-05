package com.nezzar.nfcattendance.ui

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import com.nezzar.nfcattendance.data.VisualStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.rotate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.DocumentsExport
import com.nezzar.nfcattendance.data.PlayingCards
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Section
import kotlinx.coroutines.launch
import com.nezzar.nfcattendance.data.Student

@Composable
fun SectionsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    // Sections only: adding a class lives on its own page, reached from the button
    // beside the "Sections: n" label.
    val pagerState = rememberPagerState(pageCount = { state.sections.size })
    val selected = state.selectedSection
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = "Class sections",
                subtitle = "One roster per class. Register students, then scan attendance.",
                listState = listState,
            )
        }

        if (state.sections.isEmpty()) {
            item(key = "first-run") {
                EmptyState(
                    icon = R.drawable.ic_add,
                    title = "No sections yet",
                    body = "A section is one class roster. Create the first one and register its students.",
                    actionLabel = "Create your first section",
                    onAction = { state.openNewSection() },
                )
            }
        }

        if (state.sections.isNotEmpty()) {
            item(key = "shelf-label") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Sections: " + state.sections.size)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { state.openNewSection() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("New section")
                    }
                }
            }

            item(key = "shelf") {
                LaunchedEffect(pagerState.settledPage) {
                    val page = pagerState.settledPage
                    if (page in state.sections.indices) {
                        state.selectSection(state.sections[page].name)
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // The shelf's cards are the page, so they are dealt bigger than
                    // the page hero below and take the room it would leave empty.
                    val cardWidth = ShelfCardWidth
                    val side = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
                    HorizontalPager(
                        state = pagerState,
                        pageSize = PageSize.Fixed(cardWidth),
                        pageSpacing = 12.dp,
                        contentPadding = PaddingValues(horizontal = side),
                    ) { page ->
                        val distance = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction)
                        val closeness = (1f - kotlin.math.abs(distance)).coerceIn(0f, 1f)
                        val scale = 0.84f + 0.16f * closeness
                        // A plain Box, not a Surface: a Surface clips its content to
                        // its own rectangle, and that cut the chosen card's glow off
                        // at the card's edge.
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        ) {
                            val section = state.sections[page]
                            val onSelect: () -> Unit = {
                                state.selectSection(section.name)
                                // The selected card knows where it is, so the tab it
                                // opens onto can start from exactly here.
                                state.armCardFlight()
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                            val selected = section.name == state.selectedName
                            // Only the chosen card reports its place: that is the
                            // rectangle the tab card flies from.
                            val placed = if (selected) {
                                Modifier.onGloballyPositioned { coords ->
                                    state.shelfCardRect = coords.boundsInRoot()
                                }
                            } else {
                                Modifier
                            }
                            // The chosen card is the way into its own page, where the
                            // class is renamed, given a subject, or deleted.
                            val onManage: () -> Unit = {
                                state.selectSection(section.name)
                                state.openSection()
                            }
                            if (state.visualStyle == VisualStyle.PLAIN) {
                                PlainSectionCard(
                                    section = section,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
                                    onManage = onManage,
                                    glow = state.cardGlow,
                                    cardWidth = ShelfCardWidth,
                                    cardHeight = ShelfCardHeight,
                                )
                            } else if (state.visualStyle == VisualStyle.SOLIDS) {
                                SolidSectionCard(
                                    section = section,
                                    index = page,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
                                    onManage = onManage,
                                    glow = state.cardGlow,
                                    cardWidth = ShelfCardWidth,
                                    cardHeight = ShelfCardHeight,
                                )
                            } else {
                                SectionCard(
                                    section = section,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
                                    onManage = onManage,
                                    glow = state.cardGlow,
                                    cardWidth = ShelfCardWidth,
                                    cardHeight = ShelfCardHeight,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected != null) {
            // One quiet row, not two cards: the list has its own page and that page
            // carries the export, so there is no second button to explain up here.
            item(key = "roster-open") {
                val rosterInteraction = remember { MutableInteractionSource() }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(rosterInteraction)
                        .cardClick(rosterInteraction, onClick = { state.openRoster() }),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (selected.students.size) {
                                0 -> "No students registered yet"
                                1 -> "1 student in " + selected.name
                                else -> selected.students.size.toString() + " students in " + selected.name
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = "Open all registered students",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A card face's own proportions: a playing card's 1 : 1.43. */
private val FaceWidth = 200.dp
private val FaceHeight = 286.dp

/**
 * The shelf is where the classes live, so its cards are dealt this much bigger -
 * they take the room the shelf would otherwise leave empty above the tabs.
 */
private val ShelfCardWidth = 260.dp
private val ShelfCardHeight = 372.dp

/**
 * A section card, retextured as a real playing card: ivory stock, the black or red
 * corner index in two opposite corners, and a faint suit watermark behind the class
 * details. Selection is the accent border and the lift, not a repaint.
 *
 * The chosen card carries one chip: Manage, along the bottom edge.
 */
@Composable
fun SectionCard(
    section: Section,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onManage: (() -> Unit)? = null,
    glow: CardGlow = CardGlow.Idle,
    cardWidth: Dp = FaceWidth,
    cardHeight: Dp = FaceHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val ink = accent
    val face = PlayingCards.rank(section.card)
    val suit = PlayingCards.suit(section.card)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            // The glow is the accent colour, not grey: a chosen class lights up.
            .shadow(
                // Damped: a chosen class sits in a hint of light, not a halo.
                elevation = if (selected) 10.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = accent.copy(alpha = 0.35f),
                spotColor = accent.copy(alpha = 0.5f),
            )
            // While the app is listening for cards the chosen card breathes, and
            // every card that lands makes the glow swell.
            .cardGlow(accent = accent, glow = glow, enabled = selected)
            .pressScale(interaction)
            .cardClick(interaction, onClick = onSelect),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(cardWidth * 0.85f)
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                            ),
                            RoundedCornerShape(cardWidth * 0.425f),
                        ),
                )
            }
            if (suit.isNotEmpty()) {
                // A quiet echo of the suit in the opposite corner, not a centrepiece.
                Text(
                    text = suit,
                    fontSize = 44.sp,
                    color = ink.copy(alpha = if (selected) 0.30f else 0.10f),
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            if (section.card.isNotBlank()) {
                CardIndex(
                    face = face,
                    suit = suit,
                    ink = ink.copy(alpha = if (selected) 1f else 0.55f),
                    rotation = 0f,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                CardIndex(
                    face = face,
                    suit = suit,
                    ink = ink.copy(alpha = if (selected) 0.55f else 0.22f),
                    rotation = 180f,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = section.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.subject.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = section.subject,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = section.students.size.toString() + " registered",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = "SELECTED",
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (selected && onManage != null) {
                CardChip(
                    label = "Manage",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onManage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * The default texture: a civil engineering drawing sheet. A drafting grid, a truss
 * mark where a card keeps its index, and a title block along the bottom. The chosen
 * sheet carries a Manage chip along its bottom edge.
 */
@Composable
fun PlainSectionCard(
    section: Section,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onManage: (() -> Unit)? = null,
    glow: CardGlow = CardGlow.Idle,
    cardWidth: Dp = FaceWidth,
    cardHeight: Dp = FaceHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        shadowElevation = if (selected) 12.dp else 2.dp,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .cardGlow(
                accent = MaterialTheme.colorScheme.primary,
                glow = glow,
                enabled = selected,
            )
            .pressScale(interaction)
            .cardClick(interaction, onClick = onSelect),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 18.dp.toPx()
                val line = ink.copy(alpha = 0.05f)
                var x = step
                while (x < size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(28.dp, 18.dp)) {
                        val stroke = 1.6.dp.toPx()
                        val bottom = size.height
                        drawLine(ink, Offset(0f, bottom), Offset(size.width / 2f, 0f), stroke)
                        drawLine(ink, Offset(size.width / 2f, 0f), Offset(size.width, bottom), stroke)
                        drawLine(ink, Offset(0f, bottom), Offset(size.width, bottom), stroke)
                        drawLine(ink, Offset(size.width / 2f, 0f), Offset(size.width / 2f, bottom), stroke)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (selected) "SELECTED" else "SHEET",
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = section.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.subject.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = section.subject,
                        fontSize = 13.sp,
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ink.copy(alpha = 0.22f)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = section.students.size.toString() + " registered",
                    fontSize = 11.sp,
                    color = ink.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 11.sp,
                    color = ink.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Room for the Manage chip that sits over the bottom edge of the sheet.
                Spacer(Modifier.height(26.dp))
            }
            if (selected && onManage != null) {
                CardChip(
                    label = "Manage",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onManage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * The polyhedron texture: a dark card whose face is a wireframe solid, drawn in the
 * accent colour and lit up when the class is the chosen one. The chosen card carries
 * a Manage chip along its bottom edge.
 */
@Composable
fun SolidSectionCard(
    section: Section,
    index: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onManage: (() -> Unit)? = null,
    glow: CardGlow = CardGlow.Idle,
    cardWidth: Dp = FaceWidth,
    cardHeight: Dp = FaceHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val shape = Solids.of(index)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .shadow(
                // Damped: a chosen class sits in a hint of light, not a halo.
                elevation = if (selected) 10.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = accent.copy(alpha = 0.35f),
                spotColor = accent.copy(alpha = 0.5f),
            )
            .cardGlow(accent = accent, glow = glow, enabled = selected)
            .pressScale(interaction)
            .cardClick(interaction, onClick = onSelect),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(cardWidth * 0.6f)
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                            ),
                            RoundedCornerShape(cardWidth * 0.3f),
                        ),
                )
            }
            Text(
                text = shape.uppercase(),
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent.copy(alpha = if (selected) 1f else 0.7f),
                modifier = Modifier.align(Alignment.TopStart),
            )
            // The solid is a corner mark, not the subject of the card: small, dim,
            // and only fully lit when the class is the chosen one.
            SolidGlyph(
                name = shape,
                size = 34.dp,
                colour = accent.copy(alpha = if (selected) 0.85f else 0.35f),
                stroke = 1.1.dp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 30.dp)) {
                Text(
                    text = section.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.subject.isNotBlank()) {
                    Text(
                        text = section.subject,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = section.students.size.toString() + " registered  ·  updated " +
                        ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (selected && onManage != null) {
                CardChip(
                    label = "Manage",
                    accent = accent,
                    onClick = onManage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** One corner index: rank over suit, mirrored into the far corner by rotation. */
@Composable
private fun CardIndex(
    face: String,
    suit: String,
    ink: Color,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.rotate(rotation),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = face,
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
        Text(text = suit, fontSize = 16.sp, color = ink)
    }
}

/**
 * The action a chosen class card carries, drawn on the card itself: Manage opens
 * the class's own page. Only the chosen card shows it, so the shelf stays quiet
 * until a class is picked.
 */
@Composable
private fun CardChip(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color = accent.copy(alpha = 0.18f),
        contentColor = accent,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        modifier = modifier
            .height(28.dp)
            .pressScale(interaction, pressed = 0.94f)
            .cardClick(interaction, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

