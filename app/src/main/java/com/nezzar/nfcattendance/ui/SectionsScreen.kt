package com.nezzar.nfcattendance.ui

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import com.nezzar.nfcattendance.data.VisualStyle
import com.nezzar.nfcattendance.ui.theme.isBrandDark
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val listState = rememberLazyListState()

    // A way to a class without swiping past twenty others: a search bar that
    // unfolds where the shelf's own label sits. The shelf stays on the page while
    // it is open, so the jump it makes can be watched.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val needle = query.trim().lowercase()
    val matches = if (needle.isEmpty()) {
        emptyList()
    } else {
        state.sections.withIndex().filter { (_, section) ->
            section.name.lowercase().contains(needle) ||
                section.subject.lowercase().contains(needle) ||
                faceLabel(section).lowercase().contains(needle)
        }
    }

    // "Teleport": two cards away is a fast slide, anything further jumps outright,
    // so reaching the thirtieth class never pages through the twenty-nine before it.
    val jump: (Int) -> Unit = { page ->
        scope.launch {
            if (page in state.sections.indices) {
                state.selectSection(state.sections[page].name)
                if (kotlin.math.abs(page - pagerState.currentPage) <= 2) {
                    pagerState.animateScrollToPage(
                        page = page,
                        animationSpec = tween(MotionTouchMs * 2, easing = EmphasizedDecelerate),
                    )
                } else {
                    pagerState.scrollToPage(page)
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
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
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search classes") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            // Clears what was typed; on an empty bar it closes the search.
                            IconButton(
                                onClick = {
                                    if (query.isEmpty()) searching = false else query = ""
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Clear the search",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    )
                    LaunchedEffect(searching) {
                        searchFocus.requestFocus()
                        keyboard?.show()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Sections: " + state.sections.size)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { searching = true },
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = "Search classes",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { state.openNewSection() },
                            modifier = Modifier.height(46.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("New section")
                        }
                    }
                }
            }

            if (searching && needle.isNotEmpty()) {
                item(key = "search-results") {
                    BrandCard {
                        if (matches.isEmpty()) {
                            Note("No class matches \"" + query.trim() + "\".")
                        } else {
                            SectionLabel(matches.size.toString() + " of " +
                                state.sections.size + " classes")
                            Spacer(Modifier.height(4.dp))
                            // Four is what fits above the shelf: typing narrows the
                            // rest, and the shelf stays in sight for the jump.
                            matches.take(4).forEach { (index, section) ->
                                SearchRow(
                                    section = section,
                                    onClick = {
                                        query = ""
                                        searching = false
                                        jump(index)
                                    },
                                )
                            }
                            if (matches.size > 4) {
                                Spacer(Modifier.height(6.dp))
                                Note("Keep typing - " + (matches.size - 4) + " more classes match.")
                            }
                        }
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
                        // Barely stepped back: the bigger the cards, the less room is
                        // left for the next one to show, and that peek is how the shelf
                        // says there is more than one class.
                        val scale = 0.94f + 0.06f * closeness
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
                            // The chosen card is selected by its own tap. Its two
                            // actions - Register and Manage - are the buttons under the
                            // shelf, where there is room to read them.
                            if (state.visualStyle == VisualStyle.PLAIN) {
                                PlainSectionCard(
                                    section = section,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
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

        // No roster row under the shelf: the card already says how many students are
        // in the class, and the list has its own row on the section's own page.
        //
        // The shelf never filled the page, so the chosen class's two actions moved
        // out of the card and into the room under it - side by side, so the page
        // still ends without a scroll. The card keeps its tap: that is how it is
        // chosen.
        if (state.sections.isNotEmpty()) {
            item(key = "actions") {
                val section = state.selectedSection
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            section?.let { state.selectSection(it.name) }
                            state.openRegister()
                        },
                        enabled = section != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_register),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Register")
                    }
                    OutlinedButton(
                        onClick = {
                            section?.let { state.selectSection(it.name) }
                            state.openSection()
                        },
                        enabled = section != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Manage")
                    }
                }
            }
        }
    }
}

/**
 * The chosen card's controls, along its bottom edge: Register opens the page that
 * adds students to this class, Manage opens the class's own page.
 */
@Composable
private fun CardChipRow(
    accent: Color,
    onManage: (() -> Unit)?,
    onRegister: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onRegister != null) {
            CardChip(label = "Register", accent = accent, onClick = onRegister)
        }
        if (onManage != null) {
            CardChip(label = "Manage", accent = accent, onClick = onManage)
        }
    }
}

/** A card face's own proportions: a playing card's 1 : 1.43. */
private val FaceWidth = 228.dp
private val FaceHeight = 326.dp

/**
 * The shelf is where the classes live, so its cards are dealt this much bigger -
 * they take the room the shelf would otherwise leave empty above the tabs.
 */
private val ShelfCardWidth = 310.dp
private val ShelfCardHeight = 444.dp

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
    onRegister: (() -> Unit)? = null,
    glow: CardGlow = CardGlow.Idle,
    cardWidth: Dp = FaceWidth,
    cardHeight: Dp = FaceHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val ink = accent
    val dark = isBrandDark()
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
                                // A wash on black, a hint of tint on white.
                                listOf(
                                    accent.copy(alpha = if (dark) 0.16f else 0.06f),
                                    Color.Transparent,
                                )
                            ),
                            RoundedCornerShape(cardWidth * 0.425f),
                        ),
                )
            }
            if (suit.isNotEmpty()) {
                // A quiet echo of the suit in the opposite corner, not a centrepiece.
                Text(
                    text = suit,
                    // Scales with the card: a fixed 48 sp would swallow the date line
                    // on the shorter face card the three tabs carry.
                    fontSize = (cardHeight.value * 0.108f).sp,
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
                    fontSize = 22.sp,
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
                        fontSize = 15.sp,
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
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = "SELECTED",
                    fontSize = 12.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (selected && (onManage != null || onRegister != null)) {
                CardChipRow(
                    accent = MaterialTheme.colorScheme.primary,
                    onManage = onManage,
                    onRegister = onRegister,
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
    onRegister: (() -> Unit)? = null,
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
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = section.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.subject.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = section.subject,
                        fontSize = 15.sp,
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
                    fontSize = 13.sp,
                    color = ink.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 13.sp,
                    color = ink.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Room for the Manage chip that sits over the bottom edge of the sheet.
                Spacer(Modifier.height(26.dp))
            }
            if (selected && (onManage != null || onRegister != null)) {
                CardChipRow(
                    accent = MaterialTheme.colorScheme.primary,
                    onManage = onManage,
                    onRegister = onRegister,
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
    onRegister: (() -> Unit)? = null,
    glow: CardGlow = CardGlow.Idle,
    cardWidth: Dp = FaceWidth,
    cardHeight: Dp = FaceHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val dark = isBrandDark()
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
                                // A wash on black, a hint of tint on white.
                                listOf(
                                    accent.copy(alpha = if (dark) 0.18f else 0.07f),
                                    Color.Transparent,
                                )
                            ),
                            RoundedCornerShape(cardWidth * 0.3f),
                        ),
                )
            }
            Text(
                text = shape.uppercase(),
                fontSize = 11.sp,
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.subject.isNotBlank()) {
                    Text(
                        text = section.subject,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = section.students.size.toString() + " registered",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "updated " + ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected && (onManage != null || onRegister != null)) {
                CardChipRow(
                    accent = accent,
                    onManage = onManage,
                    onRegister = onRegister,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** One corner index: rank over suit, mirrored into the far corner by rotation. */
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
            .height(34.dp)
            .pressScale(interaction, pressed = 0.94f)
            .cardClick(interaction, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

/** The face a class was dealt, printed the way the card prints it. */
private fun faceLabel(section: Section): String = if (section.card.isBlank()) {
    ""
} else {
    PlayingCards.rank(section.card) + PlayingCards.suit(section.card)
}

/**
 * One line of a search result: the class's face, its name, its subject and its
 * roster size. Tapping the line jumps the shelf to that class.
 */
@Composable
private fun SearchRow(section: Section, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interaction, pressed = 0.98f)
            .cardClick(interaction, onClick = onClick),
    ) {
        Text(
            text = faceLabel(section).ifEmpty { "—" },
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.width(46.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.name,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    section.subject.isBlank() -> section.students.size.toString() + " registered"
                    else -> section.subject + "  ·  " + section.students.size + " registered"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

