package com.ezygo.attendance.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/*
 * Scrollbars.
 *
 * A short track with a thumb on it, beside or beneath the thing that scrolls. It says
 * three things at once and needs no explaining: that there is more, how much more, and
 * where in it you are. An arrow pointing off the edge only ever said the first.
 *
 * None of these are draggable. A 4dp target is a worse way to move through a list than
 * the flick that is already there, and a thumb that looks draggable and is not is worse
 * than one that plainly is not.
 */

/** How thick the track is, and how short a thumb is allowed to get. */
private val BAR_THICKNESS = 4.dp
private const val MIN_THUMB = 0.12f

/** Always legible, brighter while the gesture is happening. */
private const val IDLE_ALPHA = 0.28f
private const val ACTIVE_ALPHA = 0.7f

/**
 * Where the thumb goes and how long it is, as two fractions of the track.
 *
 * [extent] is the thumb's length over the track's; [progress] is how far along the
 * remaining travel it sits. Null when there is nothing to scroll, which is the signal
 * to draw no bar at all.
 */
private data class BarMetrics(val extent: Float, val progress: Float)

/**
 * Lazy lists know their items, not their pixels, so this estimates.
 *
 * The estimate is the mean size of what is currently on screen, extended over the
 * whole list. With rows of similar height it is exact; with a mix it breathes a little
 * as you scroll, which is why the extent is animated rather than applied raw — the
 * number is approximate, so it should not twitch as though it were not.
 *
 * Crucially it adds [LazyListState.firstVisibleItemScrollOffset] into the position, so
 * the thumb glides with the content instead of hopping one item at a time. That hop
 * was what made the old bar look broken on lists of uneven rows.
 */
private fun LazyListState.barMetrics(): BarMetrics? {
    val info = layoutInfo
    val onScreen = info.visibleItemsInfo
    if (onScreen.isEmpty()) return null
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) return null

    val first = onScreen.first()
    val last = onScreen.last()
    val spanned = (last.offset + last.size - first.offset).toFloat()
    val mean = spanned / onScreen.size
    if (mean <= 0f) return null

    val content = mean * info.totalItemsCount
    val travel = content - viewport
    if (travel <= 1f) return null

    val scrolled = firstVisibleItemIndex * mean + firstVisibleItemScrollOffset
    return BarMetrics(
        extent = (viewport / content).coerceIn(MIN_THUMB, 1f),
        progress = (scrolled / travel).coerceIn(0f, 1f),
    )
}

/** A plain scrolling container knows its pixels exactly, so this does not estimate. */
private fun ScrollState.barMetrics(): BarMetrics? {
    if (maxValue <= 0 || maxValue == Int.MAX_VALUE) return null
    val viewport = viewportSize.toFloat()
    if (viewport <= 0f) return null
    val content = viewport + maxValue
    return BarMetrics(
        extent = (viewport / content).coerceIn(MIN_THUMB, 1f),
        progress = (value.toFloat() / maxValue).coerceIn(0f, 1f),
    )
}

/**
 * A scrollbar under a horizontal list.
 *
 * Under rather than over: the tiles it belongs to are the content, and a bar laid
 * across their bottom edge would be covering the thing it is describing. It wants a
 * [Box] or a [androidx.compose.foundation.layout.Column] that has left room for it.
 */
@Composable
fun HorizontalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember { derivedStateOf { state.barMetrics() } }
    val bar = metrics ?: return
    val extent by animateFloatAsState(bar.extent, tween(180), label = "bar-extent")
    val alpha by animateFloatAsState(
        if (state.isScrollInProgress) ACTIVE_ALPHA else IDLE_ALPHA,
        label = "bar-alpha",
    )
    Track(modifier.fillMaxWidth().height(BAR_THICKNESS)) {
        Box(
            Modifier
                .fillMaxWidth(extent)
                .fillMaxHeight()
                .align(BiasAlignment(bar.progress * 2f - 1f, 0f))
                .clip(RoundedCornerShape(BAR_THICKNESS / 2))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
        )
    }
}

/**
 * A scrollbar down the side of a scrolling column, for dialogs.
 *
 * Laid over the right edge of the [Box] it is given, so the caller leaves a little
 * end padding on the content rather than this taking a column of its own — in a
 * dialog every point of width is spoken for, and a strip reserved for something
 * four points wide is a strip of wasted line length on every row above it.
 */
@Composable
fun BoxScope.VerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember { derivedStateOf { state.barMetrics() } }
    val bar = metrics ?: return
    val alpha by animateFloatAsState(
        if (state.isScrollInProgress) ACTIVE_ALPHA else IDLE_ALPHA,
        label = "bar-alpha",
    )
    Box(modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
        Track(Modifier.fillMaxHeight().width(BAR_THICKNESS)) {
            Box(
                Modifier
                    .fillMaxHeight(bar.extent)
                    .fillMaxWidth()
                    .align(BiasAlignment(0f, bar.progress * 2f - 1f))
                    .clip(RoundedCornerShape(BAR_THICKNESS / 2))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

/**
 * The same bar, overlaid on the right edge of a full-screen list.
 *
 * Overlaid here, unlike in a dialog, because a screen-wide list has a margin to spare
 * and giving the bar its own column would shift every row left by four points for the
 * sake of something that is usually invisible.
 */
@Composable
fun BoxScope.ListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember { derivedStateOf { state.barMetrics() } }
    val bar = metrics ?: return
    val extent by animateFloatAsState(bar.extent, tween(180), label = "list-bar-extent")
    val alpha by animateFloatAsState(
        if (state.isScrollInProgress) ACTIVE_ALPHA else IDLE_ALPHA,
        label = "list-bar-alpha",
    )
    Box(
        modifier.matchParentSize().padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Track(Modifier.fillMaxHeight().width(BAR_THICKNESS)) {
            Box(
                Modifier
                    .fillMaxHeight(extent)
                    .fillMaxWidth()
                    .align(BiasAlignment(0f, bar.progress * 2f - 1f))
                    .clip(RoundedCornerShape(BAR_THICKNESS / 2))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

/**
 * The groove the thumb runs in.
 *
 * Faint, but drawn: without it the thumb is a floating mark with no scale behind it,
 * and the length of the track is half of what the bar is saying.
 */
@Composable
private fun Track(modifier: Modifier, thumb: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(BAR_THICKNESS / 2))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
        content = thumb,
    )
}

/**
 * The way back to the top of a long list, once there is a way back worth having.
 *
 * Appears only after a few screens of scrolling: offered any earlier it would be
 * covering content to save a gesture nobody minds making. Deliberately small and
 * tucked into the corner rather than a full floating action button — the screen's
 * actions belong to the screen, and this is a shortcut, not one of them.
 */
@Composable
fun BoxScope.ScrollToTopButton(
    state: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appearAfterItems: Int = 6,
) {
    val show by remember(appearAfterItems) {
        derivedStateOf { state.firstVisibleItemIndex >= appearAfterItems }
    }
    val alpha by animateFloatAsState(if (show) 1f else 0f, label = "scroll-to-top")
    if (alpha <= 0.01f) return

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 3.dp,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "Back to top",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(8.dp).size(22.dp),
        )
    }
}
