package com.ezygo.attendance.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * When the cohort figures were last read, and whether they are being read now.
 *
 * Shown on every screen that displays them, because this report is the one thing in
 * the app that is not kept up to date behind the reader's back: it is fetched when
 * they open these screens and then left alone for half a day. A figure with that
 * policy behind it has to say how old it is, or it is just a number.
 */
@Composable
fun ClassCountsStatus(
    fetchedAtEpochMs: Long?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
        }
        Text(
            when {
                loading -> "Updating class attendance…"
                fetchedAtEpochMs == null -> "Class attendance not fetched yet"
                else -> "Class attendance " + relativeTime(fetchedAtEpochMs).lowercase()
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
