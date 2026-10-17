package com.ezygo.attendance.ui.miniapps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.HorizontalScrollbar

/**
 * The small self-contained tools that live alongside the attendance data.
 *
 * One entry per tool, and adding another is one constant here plus one `composable`
 * registration in AppNavigation — the home row, the routes and the ordering all
 * follow from this list rather than repeating it.
 *
 * They are deliberately separate from the tabs: a tab is somewhere the app lives,
 * while these are things it can do on request and does not need to be showing.
 */
enum class MiniApp(
    val route: String,
    val title: String,
    /** One dry line under the title, so a tile says what it is for. */
    val description: String,
    val icon: ImageVector,
) {
    GRADE_CARDS(
        route = "miniapp/gradecards",
        title = "Credits",
        description = "The damage, itemised",
        icon = Icons.AutoMirrored.Filled.Article,
    ),
    WHAT_IF(
        route = "miniapp/whatif",
        title = "What if",
        description = "Skip-day arithmetic",
        icon = Icons.Default.QueryStats,
    ),
    TASKS(
        route = "miniapp/tasks",
        title = "Tasks",
        description = "Later never comes",
        icon = Icons.Default.Checklist,
    ),
    NOTES(
        route = "miniapp/notes",
        title = "Notes",
        description = "Write it, forget it",
        icon = Icons.AutoMirrored.Filled.StickyNote2,
    ),
}

/**
 * The home screen's row of mini apps.
 *
 * A [LazyRow] rather than a fixed row of weighted cards: three fit across a phone
 * today, and the fourth should scroll rather than squeeze the other three.
 */
@Composable
fun MiniAppsRow(
    onOpen: (MiniApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    // The row is wider than the screen by design, and a tile cut off at the edge can
    // read as a layout accident rather than as an invitation. A short bar underneath
    // says there is more, how much more, and where in it you are; it disappears by
    // itself if the tiles ever do fit.
    Column(modifier) {
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(MiniApp.entries, key = { it.name }) { app ->
                MiniAppCard(app = app, onClick = { onOpen(app) })
            }
        }
        Gap(6)
        HorizontalScrollbar(
            state = state,
            // Not the full width: a bar as wide as the row reads as a divider under
            // it. Centred and short, it reads as a control.
            modifier = Modifier.fillMaxWidth(0.34f).align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun MiniAppCard(app: MiniApp, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = app.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    // Back to the smaller size it had before. With a title and a line
                    // of description under it, the icon is the tile's label rather
                    // than its subject, and at 28dp it was crowding both.
                    modifier = Modifier.padding(7.dp).size(20.dp),
                )
            }
            Text(
                app.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                app.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Two lines of headroom rather than one with an ellipsis: the copy
                // fits on one line at normal scales, and at large accessibility
                // scales a tile that grows reads better than one that truncates.
                maxLines = 2,
            )
        }
    }
}
