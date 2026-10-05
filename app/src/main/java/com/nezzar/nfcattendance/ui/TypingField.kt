package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Keeps the field being typed in front of the keyboard.
 *
 * The page already shrinks with `imePadding`, but a list only scrolls a focused
 * field into view when that field was laid out first - the editor at the bottom of
 * a short list never is, so the keyboard opens on top of it.
 *
 * This lifts the item just clear of the shortened viewport, and only by as much as
 * is covered: a field that is already visible does not move, so nothing jumps under
 * the finger while the keyboard is still settling.
 *
 * [index] is the list item holding the field, or null when this page has nothing to
 * reveal; [active] says whether that field is on the page at all. It runs when the
 * keyboard opens, which is the moment the viewport actually gets shorter.
 */
@Composable
fun RevealTypingField(listState: LazyListState, index: Int?, active: Boolean) {
    val density = LocalDensity.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(active, keyboardOpen, index) {
        if (!active || !keyboardOpen || index == null) return@LaunchedEffect
        // A frame or two, so the shorter viewport and the focused field both settle
        // before we measure where the field ended up.
        withFrameNanos { }
        withFrameNanos { }
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        // How far the field hangs past the part of the list the keyboard leaves.
        val covered = item?.let {
            it.offset + it.size - layout.viewportEndOffset + layout.afterContentPadding
        }
        // A page that shrinks under the finger can be shorter than the scroll we ask
        // for: a reveal that cannot happen is not worth a crash.
        runCatching {
            when {
                item == null -> listState.animateScrollToItem(index)
                covered != null && covered > 0 ->
                    listState.animateScrollBy(covered.toFloat() + with(density) { 12.dp.toPx() })
            }
        }
    }
}
