package org.agora.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Tablets (like the web from 769px): the page uses the whole width with a margin that grows with the screen, and
 * content goes into columns instead of one stretched list. Phones (below 600dp) stay exactly as they are.
 */

/** Width of the window in dp (follows rotation and split screen). */
@Composable
fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/** From 600dp: tablet layout (page margin, card grids). */
@Composable
fun isTablet(): Boolean = screenWidthDp() >= 600

/** From 840dp: two panes side by side (start page duties / messages, finances, settings). */
@Composable
fun isWide(): Boolean = screenWidthDp() >= 840

/** Side margin of a page: 20dp on phones, growing with the screen on tablets (web: clamp(20px, 2.8vw, 64px)). */
@Composable
fun pageGutter(): Dp {
    val width = screenWidthDp()
    return if (width < 600) 20.dp else (width * 0.028f).coerceIn(24f, 56f).dp
}

/** List padding with the page margin left and right. */
@Composable
fun pagePadding(top: Dp, bottom: Dp): PaddingValues {
    val gutter = pageGutter()
    return PaddingValues(start = gutter, end = gutter, top = top, bottom = bottom)
}

/** Reading pages (event, editor, chat) keep a readable column on tablets, centred in the window. */
@Composable
fun readingPadding(top: Dp, bottom: Dp, maxWidth: Dp = 760.dp): PaddingValues {
    val side = ((screenWidthDp().dp - maxWidth) / 2).coerceAtLeast(20.dp)
    return PaddingValues(start = side, end = side, top = top, bottom = bottom)
}

/** How many cards of at least [minWidth] fit next to each other within the page margins. */
@Composable
fun columnCount(minWidth: Dp, gap: Dp = 12.dp): Int {
    val available = screenWidthDp().dp - pageGutter() * 2
    return ((available + gap) / (minWidth + gap)).toInt().coerceAtLeast(1)
}

/**
 * [items] as rows of [columns] equally wide cells inside a lazy list, so a card grid scrolls together with the
 * rest of the page. One column is the plain list. Cells of a row share the height of the tallest one.
 */
fun <T> LazyListScope.gridItems(
    items: List<T>,
    columns: Int,
    key: (T) -> Any,
    gap: Dp = 12.dp,
    content: @Composable (item: T, modifier: Modifier) -> Unit
) {
    if (columns <= 1) {
        items(items, key = key) { content(it, Modifier) }
        return
    }
    items(items.chunked(columns), key = { row -> "row-" + key(row.first()) }) { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
            row.forEach { item -> Box(Modifier.weight(1f)) { content(item, Modifier.fillMaxHeight()) } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Two columns next to each other on wide screens, otherwise one below the other. */
@Composable
fun TwoPane(
    wide: Boolean,
    modifier: Modifier = Modifier,
    gap: Dp = 16.dp,
    leftWeight: Float = 1f,
    rightWeight: Float = 1f,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    if (wide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(leftWeight), verticalArrangement = Arrangement.spacedBy(gap), content = left)
            Column(Modifier.weight(rightWeight), verticalArrangement = Arrangement.spacedBy(gap), content = right)
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            left()
            right()
        }
    }
}
