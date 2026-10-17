package com.ezygo.attendance.ui.samarth

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.data.repo.StoredGradeCard
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.eventTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * The grade cards the Samarth portal holds.
 *
 * Anything already downloaded is listed straight away, with no network and no sign-in.
 * Fetching is what reaches the portal, and it is the only thing that does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeCardsScreen(
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenEstimator: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: GradeCardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isBusy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Gap(12)
                    } else if (state.cards.isNotEmpty()) {
                        // Only once there is something to refresh. Before that the
                        // fetch is the page's one job and it lives in the page, not
                        // in a corner of the bar.
                        IconButton(onClick = { viewModel.fetch(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Fetch again")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { message ->
                item { InlineError(message, onRetry = { viewModel.fetch() }) }
            }

            if (state.results.isNotEmpty()) {
                item { CgpaCard(state) }
            }

            // The estimator belongs beside the results rather than on its own icon:
            // it is the same question about the semester that has not been graded yet.
            item { EstimatorRow(onClick = onOpenEstimator) }

            item {
                LinkRow(
                    title = "Credit history",
                    subtitle = "Every completed course by semester, with its credits",
                    onClick = onOpenHistory,
                )
            }

            // The documents themselves, under a heading of their own. Everything above
            // is read out of them; these are the sources, and they belong together
            // rather than trailing off the end of the summaries.
            item {
                Gap(4)
                SectionHeader(
                    "Grade cards",
                    trailing = state.cards.size.takeIf { it > 0 }?.toString(),
                )
            }

            if (state.cards.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            title = "No grade cards yet",
                            body = if (state.hasCredentials) {
                                "They are read from Samarth with the credentials you saved."
                            } else {
                                "You will be asked to sign in to Samarth once. " +
                                    "After that they are fetched automatically."
                            },
                        )
                        Gap(4)
                        Button(
                            onClick = { viewModel.fetch() },
                            enabled = !state.isBusy,
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Fetch your grade cards")
                        }
                    }
                }
            } else {
                items(state.cards, key = { it.card.term }) { stored ->
                    GradeCardRow(stored = stored, onClick = { onOpenCard(stored.card.term) })
                }
            }
        }
    }

    if (state.askForCredentials) {
        SamarthCredentialsDialog(
            onSave = viewModel::saveCredentials,
            onDismiss = viewModel::dismissCredentials,
            error = state.credentialsError,
        )
    }
}

/**
 * The CGPA, over the semesters actually read.
 *
 * Says how many cards it is averaging, because a CGPA over three semesters and one
 * over eight are different claims and the number alone cannot tell them apart.
 */
@Composable
private fun CgpaCard(state: GradeCardsUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "CGPA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(2)
            Text(
                state.cgpa?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(2)
            Text(
                buildString {
                    append(state.results.size)
                    append(if (state.results.size == 1) " semester" else " semesters")
                    state.totalCredits?.let { append(" · $it credits") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(10)
            state.results.forEach { result ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        "Semester ${result.semester}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    result.credits?.let {
                        Text(
                            "$it credits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                    Text(
                        String.format(Locale.getDefault(), "%.2f", result.sgpa),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun EstimatorRow(onClick: () -> Unit) {
    LinkRow(
        title = "SGPA estimator",
        subtitle = "Enter this semester's marks and see where the CGPA lands",
        onClick = onClick,
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradeCardRow(stored: StoredGradeCard, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stored.card.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Downloaded " + eventTime(stored.downloadedEpochMs).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- the card itself ------------------------------------------------------

/**
 * One grade card, rendered from the downloaded PDF.
 *
 * Drawn with the platform's own [PdfRenderer] rather than a PDF library: the documents
 * are one or two pages of a table, and a rendered page is all this screen needs to be.
 *
 * It is a page to read, not to manipulate. Anything beyond scrolling - zooming,
 * selecting, copying - is what a real PDF viewer is for, and every phone has one, so
 * "Open with" hands the file over rather than half-reimplementing it here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeCardViewerScreen(
    term: String,
    onBack: () -> Unit,
    viewModel: GradeCardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val file = remember(term, state.cards) { viewModel.fileForTerm(term) }
    var pages by remember(file) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var failed by remember(file) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(file) {
        val target = file ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) { runCatching { renderPdf(target) }.getOrNull() }
        if (rendered == null) failed = true else pages = rendered
    }

    // Confirmed where the file went, in the words the user will look for in their file
    // manager. Cleared straight away so it cannot fire a second time on a rotation.
    LaunchedEffect(state.savedTo) {
        if (state.savedTo != null) {
            Toast.makeText(context, "Saved to Downloads.", Toast.LENGTH_SHORT).show()
            viewModel.dismissSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Semester $term") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { file?.let { openExternally(context, it) } },
                        enabled = file != null,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open with another app",
                        )
                    }
                    IconButton(onClick = { viewModel.saveToDevice(term) }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Save to Downloads")
                    }
                },
            )
        },
    ) { padding ->
        when {
            // The stored list is read from disk asynchronously, so "no file yet" is
            // indistinguishable from "still looking" until it has arrived.
            !failed && file == null && state.cards.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            file == null || failed -> EmptyState(
                title = "Could not open this card",
                body = "The downloaded file is missing or unreadable. Fetch the grade " +
                    "cards again from the previous screen.",
                modifier = Modifier.padding(padding),
            )

            pages.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pages.forEachIndexed { index, page ->
                    Image(
                        bitmap = page,
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        // The page is a white document; it keeps its own background
                        // rather than borrowing the app's, in dark mode included.
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

/**
 * Hands the file to whatever PDF viewer the user already has.
 *
 * Through a content URI from the app's own FileProvider, with read permission granted
 * for this Intent alone: the receiving app can read this one card and nothing else,
 * and the grant dies with the Intent.
 */
private fun openExternally(context: Context, file: File) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(context, "Could not open this card.", Toast.LENGTH_SHORT).show()
        return
    }
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // No PDF viewer installed is a perfectly ordinary state, and it must not crash.
    runCatching { context.startActivity(Intent.createChooser(view, "Open grade card")) }
        .onFailure {
            Toast.makeText(
                context,
                "No app on this device can open a PDF.",
                Toast.LENGTH_SHORT,
            ).show()
        }
}

/** Renders every page to a bitmap wide enough to read on a phone. */
private fun renderPdf(file: File, targetWidth: Int = 1400): List<ImageBitmap> =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val height = (page.height * (targetWidth.toFloat() / page.width))
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                    // PdfRenderer draws only ink; without this the paper is transparent
                    // and the text lands on whatever is behind it.
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    }
