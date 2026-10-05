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
import androidx.compose.animation.animateContentSize
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
    var renameDraft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var managingSection by remember { mutableStateOf(false) }
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
                subtitle = "One roster per class. Register cards, then scan attendance.",
                listState = listState,
            )
        }

        if (state.sections.isEmpty()) {
            item(key = "first-run") {
                EmptyState(
                    icon = R.drawable.ic_add,
                    title = "No sections yet",
                    body = "A section is one class roster. Create the first one and register its cards.",
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
                    val cardWidth = 200.dp
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
                        Surface(
                            color = Color.Transparent,
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
                            if (state.visualStyle == VisualStyle.PLAIN) {
                                PlainSectionCard(
                                    section = section,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
                                )
                            } else if (state.visualStyle == VisualStyle.SOLIDS) {
                                SolidSectionCard(
                                    section = section,
                                    index = page,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
                                )
                            } else {
                                SectionCard(
                                    section = section,
                                    selected = selected,
                                    modifier = placed,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected != null) {
            // One section on screen: the shelf card above already carries the name
            // and the updated date, so the renaming and deleting controls stay folded
            // away until somebody asks for them.
            item(key = "manage") {
                SelectedSectionCard(
                    state = state,
                    selected = selected,
                    renameDraft = renameDraft,
                    onRenameDraft = { renameDraft = it },
                    confirmDelete = confirmDelete,
                    onConfirmDelete = { confirmDelete = it },
                    expanded = managingSection,
                    onToggle = {
                        managingSection = !managingSection
                        confirmDelete = false
                    },
                )
            }

            // One button instead of a long inline roster: the list has its own page,
            // which is also where a student is edited.
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
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionLabel("Registered students")
                            Text(
                                text = if (selected.students.isEmpty()) {
                                    "Nobody registered yet"
                                } else {
                                    selected.students.size.toString() + " card(s) in " + selected.name
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Note("Open the full list to edit a name, remove a card, or export it.")
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = "Open all registered students",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "export") { RosterExportCard(state, selected.students.isNotEmpty()) }
        }
    }
}

@Composable
private fun ImportPreview(state: AppState, plan: RosterImporter.Plan, parsed: RosterImporter.Parsed) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(200)),
    ) {
        BrandCard {
            SectionLabel("Import preview")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("File", state.importSourceName)
            KeyValueRow(
                label = "Section in the file",
                value = if (parsed.sectionName.isBlank()) "(not named in the file)" else parsed.sectionName,
            )
            KeyValueRow("Updated in the file", ReportBuilder.dateUpdatedText(parsed.updatedAt))
            KeyValueRow(
                label = "Student rows",
                value = parsed.rows.size.toString() + "  ·  skipped " + parsed.skipped.size,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.importTargetName,
                onValueChange = { state.importSectionNameChanged(it) },
                label = { Text("Section name to import into") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            if (plan.error.isNotEmpty()) {
                Text(
                    text = "Refused: " + plan.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = if (plan.mergeIntoExisting) {
                        "Merge into " + plan.targetName + " - nothing already there is removed."
                    } else {
                        "Create " + plan.targetName + " from these students."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Note(
                    plan.added.size.toString() + " added, " + plan.renamed.size + " renamed, " +
                        plan.unchanged.size + " unchanged, " + plan.keptLocally.size + " kept, " +
                        plan.skipped.size + " skipped"
                )
                Spacer(Modifier.height(10.dp))
                var index = 0
                for (row in plan.added) {
                    StaggeredRow(index++) {
                        Text(
                            text = "add " + row.name + "   " + row.uid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                for ((row, previous) in plan.renamed) {
                    StaggeredRow(index++) {
                        Text(
                            text = "name update " + row.uid + ": " + previous + " -> " + row.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                for (student in plan.keptLocally) {
                    StaggeredRow(index++) {
                        Text(
                            text = "kept (not in the file) " + student.name + "   " + student.uid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                for (skip in plan.skipped) {
                    StaggeredRow(index++) {
                        Text(
                            text = "skipped row " + skip.rowNumber + ": " + skip.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow(
                    label = "Updated after import",
                    value = ReportBuilder.dateUpdatedText(plan.resultingUpdatedAt),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = { state.confirmImport() }) { Text("Import") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { state.cancelImport() }) { Text("Cancel") }
            }
        }
    }
}

/**
 * A section card, retextured as a real playing card: ivory stock, the black or red
 * corner index in two opposite corners, and a faint suit watermark behind the class
 * details. Selection is the accent border and the lift, not a repaint.
 */
@Composable
fun SectionCard(
    section: Section,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
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
            .width(200.dp)
            .height(286.dp)
            // The glow is the accent colour, not grey: a chosen class lights up.
            .shadow(
                elevation = if (selected) 18.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = accent,
                spotColor = accent,
            )
            .pressScale(interaction)
            .cardClick(interaction, onClick = onSelect),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(170.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                            ),
                            RoundedCornerShape(85.dp),
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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = section.students.size.toString() + " registered",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Updated " + ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
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
        }
    }
}

/**
 * The default texture: a civil engineering drawing sheet. A drafting grid, a truss
 * mark where a card keeps its index, and a title block along the bottom.
 */
@Composable
fun PlainSectionCard(
    section: Section,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
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
            .width(200.dp)
            .height(286.dp)
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
                    text = "Updated " + ReportBuilder.dateUpdatedText(section.updatedAt),
                    fontSize = 11.sp,
                    color = ink.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The polyhedron texture: a dark card whose face is a wireframe solid, drawn in the
 * accent colour and lit up when the class is the chosen one.
 */
@Composable
fun SolidSectionCard(
    section: Section,
    index: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
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
            .width(200.dp)
            .height(286.dp)
            .shadow(
                elevation = if (selected) 18.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = accent,
                spotColor = accent,
            )
            .pressScale(interaction)
            .cardClick(interaction, onClick = onSelect),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                            ),
                            RoundedCornerShape(60.dp),
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
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
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

@Composable
private fun NewSectionTile(
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    val ring by animateColorAsState(
        targetValue = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
        label = "tileRing",
    )
    val thickness by animateDpAsState(
        targetValue = if (emphasised) 2.dp else 1.dp,
        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
        label = "tileThickness",
    )
    val lift by animateDpAsState(
        targetValue = if (emphasised) 16.dp else 0.dp,
        animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
        label = "tileLift",
    )
    val glow by animateFloatAsState(
        targetValue = if (emphasised) 1f else 0f,
        animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
        label = "tileGlow",
    )
    val breathing = rememberInfiniteTransition(label = "tileBreathe")
    val breathe by breathing.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                width = thickness,
                color = if (emphasised) ring.copy(alpha = glow * breathe) else ring,
            ),
            modifier = Modifier
                // Exactly the page slot, the same as a section card, so the carousel's
                // own zoom makes this the biggest card on screen when it is focused.
                .fillMaxWidth()
                .shadow(
                elevation = lift,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            )
            .pressScale(interaction)
            .cardClick(interaction, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "New section",
                style = MaterialTheme.typography.titleSmall,
                color = if (emphasised) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(2.dp))
            Note("Adds a class roster")
        }
    }
}
}

@Composable
private fun SelectedSectionCard(
    state: AppState,
    selected: Section,
    renameDraft: String,
    onRenameDraft: (String) -> Unit,
    confirmDelete: Boolean,
    onConfirmDelete: (Boolean) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    var subject by remember(selected.name) { mutableStateOf(selected.subject) }
    BrandCard(modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Section")
                Text(text = selected.name, style = MaterialTheme.typography.titleMedium)
                Note(
                    selected.students.size.toString() + " registered  ·  updated " +
                        ReportBuilder.dateUpdatedText(selected.updatedAt)
                )
            }
            TextButton(onClick = onToggle) { Text(if (expanded) "Done" else "Manage") }
        }
        if (!expanded) return@BrandCard
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Subject (e.g. Surveying)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { state.useSubject(subject) }) { Text("Save subject") }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = renameDraft,
            onValueChange = onRenameDraft,
            label = { Text("Rename section to") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Button(onClick = {
                state.renameSelectedSection(renameDraft)
                if (state.sectionsError.isEmpty()) onRenameDraft("")
            }) {
                Text("Rename")
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { onConfirmDelete(true) }) { Text("Delete section") }
        }
        if (confirmDelete) {
            Spacer(Modifier.height(12.dp))
            Note(
                "Delete " + selected.name + " and its " + selected.students.size +
                    " registered student(s)? Registered cards cannot be recovered."
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = {
                        onConfirmDelete(false)
                        state.deleteSection(selected.name)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Yes, delete")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { onConfirmDelete(false) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun RosterExportCard(state: AppState, hasStudents: Boolean) {
    BrandCard(modifier = Modifier.animateContentSize()) {
        SectionLabel("Export")
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { state.exportRoster() },
            enabled = hasStudents,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_export),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Export section roster .xlsx")
        }
        if (!hasStudents) {
            Spacer(Modifier.height(8.dp))
            Note("Register at least one card and this becomes available.")
        }
        ExportResult(
            path = state.rosterPath,
            note = state.rosterError,
            onShare = state.rosterUri?.let { uri -> { state.shareFile(uri) } },
        )
    }
}