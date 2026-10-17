package com.ezygo.attendance.ui.miniapps.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ezygo.attendance.ui.common.ConfirmDeleteDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * One note: a title, the writing under it, and a few marks to shape it.
 *
 * The body is Markdown — see [NoteMarkdown] for why it is text rather than a styled
 * document. The toolbar writes the markers so nobody has to know that, and the preview
 * shows what they come to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Held here rather than read back from the view model on every keystroke: the
    // toolbar needs the selection, and a field rendering a value that has been through
    // the state and back is a frame behind the keyboard, which moves the cursor.
    var body by remember { mutableStateOf(TextFieldValue()) }
    LaunchedEffect(state.isReady) {
        if (state.isReady) {
            body = TextFieldValue(state.body, TextRange(state.body.length))
            // A note that already exists opens as something to read.
            preview = state.isExisting
        }
    }

    val setBody = { value: TextFieldValue ->
        // A list carries itself on to the next line. Anything that is not exactly one
        // newline typed at the caret passes through this untouched.
        val continued = NoteMarkdown.continueList(body, value)
        body = continued
        viewModel.onBodyChange(continued.text)
    }

    val format = { transform: (TextFieldValue) -> TextFieldValue -> setBody(transform(body)) }

    val finish = {
        viewModel.save()
        onBack()
    }
    BackHandler(onBack = finish)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note") },
                navigationIcon = {
                    IconButton(onClick = finish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            if (preview) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (preview) "Edit" else "Preview",
                        )
                    }
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete note")
                    }
                    // Saves and leaves. A Save that stayed put would make the reader
                    // press back afterwards and wonder whether that saved it again.
                    TextButton(enabled = state.isReady, onClick = finish) { Text("Save") }
                },
            )
        },
    ) { padding ->
        // The keyboard is inset here rather than left to cover the field: without it
        // the caret can sit behind the keyboard on a long note with nothing the reader
        // can do about it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 8.dp),
        ) {
            TextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = { Text("Title") },
                singleLine = true,
                readOnly = preview,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                colors = borderlessColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (preview) {
                Text(
                    NoteMarkdown.render(body.text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                return@Column
            }

            FormatBar(value = body, onChange = setBody)
            HorizontalDivider(Modifier.padding(horizontal = 8.dp))

            // A page of empty room under the writing, so the last line can always be
            // scrolled clear of the keyboard even in a note only a few lines long.
            val pageHeight = LocalConfiguration.current.screenHeightDp.dp
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextField(
                    value = body,
                    onValueChange = setBody,
                    placeholder = { Text("Write something") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = borderlessColors(),
                    // Bold, italic and underline join Cut, Copy and Paste on the
                    // platform's own selection menu, which is where a reader who has
                    // just selected a word is already looking.
                    modifier = Modifier
                        .fillMaxWidth()
                        .appendTextContextMenuComponents {
                            separator()
                            item(key = BoldMenuKey, label = "Bold") {
                                close()
                                format { NoteMarkdown.toggleInline(it, NoteMarkdown.BOLD) }
                            }
                            item(key = ItalicMenuKey, label = "Italic") {
                                close()
                                format { NoteMarkdown.toggleInline(it, NoteMarkdown.ITALIC) }
                            }
                            item(key = UnderlineMenuKey, label = "Underline") {
                                close()
                                format { NoteMarkdown.toggleInline(it, NoteMarkdown.UNDERLINE) }
                            }
                            item(key = StrikeMenuKey, label = "Strikethrough") {
                                close()
                                format { NoteMarkdown.toggleInline(it, NoteMarkdown.STRIKE) }
                            }
                        },
                )
                Spacer(Modifier.height(pageHeight))
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            what = "this note",
            onConfirm = {
                viewModel.delete()
                onBack()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

// Keys for the added selection-menu items. Objects rather than strings, because the
// menu identifies items by key and two libraries picking the same string would collide.
private data object BoldMenuKey
private data object ItalicMenuKey
private data object UnderlineMenuKey
private data object StrikeMenuKey

/**
 * The marks, as buttons.
 *
 * Scrolls sideways rather than wrapping: a toolbar that grows a second row pushes the
 * writing off screen, which is the opposite of what a writing surface should do.
 */
@Composable
private fun FormatBar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        FormatButton(Icons.Default.FormatBold, "Bold") {
            onChange(NoteMarkdown.toggleInline(value, NoteMarkdown.BOLD))
        }
        FormatButton(Icons.Default.FormatItalic, "Italic") {
            onChange(NoteMarkdown.toggleInline(value, NoteMarkdown.ITALIC))
        }
        FormatButton(Icons.Default.FormatUnderlined, "Underline") {
            onChange(NoteMarkdown.toggleInline(value, NoteMarkdown.UNDERLINE))
        }
        FormatButton(Icons.Default.FormatStrikethrough, "Strikethrough") {
            onChange(NoteMarkdown.toggleInline(value, NoteMarkdown.STRIKE))
        }
        FormatButton(Icons.Default.Code, "Code") {
            onChange(NoteMarkdown.toggleInline(value, NoteMarkdown.CODE))
        }
        FormatButton(Icons.Default.Title, "Heading") {
            onChange(NoteMarkdown.toggleHeading(value))
        }
        FormatButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bulleted list") {
            onChange(NoteMarkdown.toggleBullet(value))
        }
        FormatButton(Icons.Default.FormatListNumbered, "Numbered list") {
            onChange(NoteMarkdown.toggleNumbered(value))
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A note should look like paper, not like a form. */
@Composable
private fun borderlessColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
