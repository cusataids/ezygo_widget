package com.ezygo.attendance.ui.miniapps.notes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Notes are Markdown, stored as the text the user typed.
 *
 * Not a rich-text document with styling attached to ranges: Compose's text fields edit
 * plain text, so spans do not survive typing into them, and a note held as anything
 * else would need a schema, a serialiser and a migration to say "bold". Markers in the
 * text cost none of that, survive every edit, and are still legible if nothing ever
 * renders them again.
 *
 * The subset is what a student actually reaches for — bold, italic, strikethrough,
 * inline code, headings, bullets and numbers. Anything not recognised is left exactly
 * as written rather than swallowed.
 */
object NoteMarkdown {

    const val BOLD = "**"
    const val ITALIC = "*"
    const val STRIKE = "~~"
    const val CODE = "`"

    /**
     * Underline.
     *
     * CommonMark reads `__x__` as bold; this dialect reads it as underline, because
     * underline is one of the four marks a writer actually reaches for and Markdown
     * has no notation of its own for it. Nothing here consumes anyone else's Markdown,
     * so the only cost is that a note pasted out of here into a strict renderer comes
     * out bold rather than underlined.
     */
    const val UNDERLINE = "__"

    const val BULLET = "- "
    const val HEADING = "# "

    /** What a numbered line starts with, and how one is recognised again. */
    private val NUMBERED = Regex("""^(\d+)\. """)

    /**
     * The note, rendered.
     *
     * Line by line, because bullets and headings are properties of a line, then inline
     * within each — which is also the order the markers were written in.
     */
    fun render(text: String, baseSize: TextUnit = 16.sp): AnnotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")
            when {
                line.startsWith(HEADING) -> {
                    withStyleOf(
                        SpanStyle(fontSize = baseSize * 1.35f, fontWeight = FontWeight.Bold),
                    ) { appendInline(line.removePrefix(HEADING)) }
                }

                line.startsWith(BULLET) || line.startsWith("* ") -> {
                    // A real bullet character rather than the hyphen that stands for
                    // it, since this is the rendered form and nobody writes "-" meaning
                    // a hyphen at the start of a list line.
                    append("•  ")
                    appendInline(line.drop(2))
                }

                NUMBERED.containsMatchIn(line) -> {
                    val match = NUMBERED.find(line)!!
                    append(match.groupValues[1] + ".  ")
                    appendInline(line.removeRange(match.range))
                }

                else -> appendInline(line)
            }
        }
    }

    /**
     * The note with its markers taken off, for a list row that has two lines to spend.
     *
     * A preview showing `**Exam** on ~~Friday~~` spends its two lines on punctuation.
     */
    fun plain(text: String): String = text
        .split("\n")
        .joinToString("\n") { line ->
            val body = when {
                line.startsWith(HEADING) -> line.removePrefix(HEADING)
                line.startsWith(BULLET) || line.startsWith("* ") -> line.drop(2)
                else -> NUMBERED.find(line)?.let { line.removeRange(it.range) } ?: line
            }
            body.replace(BOLD, "")
                .replace(UNDERLINE, "")
                .replace(STRIKE, "")
                .replace(ITALIC, "")
                .replace(CODE, "")
        }

    // ---- editing ---------------------------------------------------------

    /**
     * Wraps the selection in [marker], or unwraps it if it is already wrapped.
     *
     * With nothing selected the markers are inserted and the cursor put between them,
     * so pressing bold and typing does what pressing bold in any editor does.
     */
    fun toggleInline(value: TextFieldValue, marker: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val text = value.text

        if (start == end) {
            val inserted = text.substring(0, start) + marker + marker + text.substring(start)
            return value.copy(
                text = inserted,
                selection = TextRange(start + marker.length),
            )
        }

        val selected = text.substring(start, end)
        // Already wrapped, either inside the selection or immediately around it.
        val insideWrapped = selected.length >= marker.length * 2 &&
            selected.startsWith(marker) && selected.endsWith(marker)
        if (insideWrapped) {
            val stripped = selected.substring(marker.length, selected.length - marker.length)
            return value.copy(
                text = text.substring(0, start) + stripped + text.substring(end),
                selection = TextRange(start, start + stripped.length),
            )
        }
        val outsideWrapped = start >= marker.length &&
            end + marker.length <= text.length &&
            text.substring(start - marker.length, start) == marker &&
            text.substring(end, end + marker.length) == marker
        if (outsideWrapped) {
            return value.copy(
                text = text.substring(0, start - marker.length) + selected +
                    text.substring(end + marker.length),
                selection = TextRange(start - marker.length, end - marker.length),
            )
        }

        val wrapped = text.substring(0, start) + marker + selected + marker + text.substring(end)
        return value.copy(
            text = wrapped,
            selection = TextRange(start + marker.length, end + marker.length),
        )
    }

    /**
     * Turns every line the selection touches into a list, or back out of one.
     *
     * Toggled on the whole run rather than per line: a selection that was half bulleted
     * becomes fully bulleted, which is what pressing the button on it means.
     */
    fun toggleBullet(value: TextFieldValue): TextFieldValue =
        toggleLines(value) { lines ->
            val allBulleted = lines.all { it.isBlank() || it.startsWith(BULLET) || it.startsWith("* ") }
            lines.map { line ->
                when {
                    line.isBlank() -> line
                    allBulleted -> line.removePrefix(BULLET).removePrefix("* ")
                    line.startsWith(BULLET) || line.startsWith("* ") -> line
                    else -> BULLET + line
                }
            }
        }

    fun toggleNumbered(value: TextFieldValue): TextFieldValue =
        toggleLines(value) { lines ->
            val allNumbered = lines.all { it.isBlank() || NUMBERED.containsMatchIn(it) }
            var next = 1
            lines.map { line ->
                when {
                    line.isBlank() -> line
                    allNumbered -> NUMBERED.find(line)?.let { line.removeRange(it.range) } ?: line
                    // Renumbered from one rather than kept, so a list built out of order
                    // still reads 1, 2, 3 down the page.
                    else -> {
                        val body = NUMBERED.find(line)?.let { line.removeRange(it.range) } ?: line
                        "${next++}. $body"
                    }
                }
            }
        }

    fun toggleHeading(value: TextFieldValue): TextFieldValue =
        toggleLines(value) { lines ->
            val allHeadings = lines.all { it.isBlank() || it.startsWith(HEADING) }
            lines.map { line ->
                when {
                    line.isBlank() -> line
                    allHeadings -> line.removePrefix(HEADING)
                    else -> HEADING + line
                }
            }
        }

    /**
     * Rewrites the lines the selection covers, and leaves the caret sitting in them.
     *
     * Deliberately collapsed rather than left selecting the block: a selection left
     * over the line means the next character typed replaces everything that was just
     * bulleted, which is a way to lose a paragraph by pressing a formatting button.
     *
     * The caret lands where it was, shifted by however much the markers moved the text
     * in front of it, so pressing the bullet button mid-sentence leaves you mid-
     * sentence rather than at the end of the paragraph.
     */
    private fun toggleLines(
        value: TextFieldValue,
        change: (List<String>) -> List<String>,
    ): TextFieldValue {
        val text = value.text
        val caret = value.selection.max
        val lineStart = text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(0))
            .let { if (it == -1 || value.selection.min == 0) 0 else it + 1 }
        val lineEndIndex = text.indexOf('\n', value.selection.max)
        val lineEnd = if (lineEndIndex == -1) text.length else lineEndIndex

        val block = text.substring(lineStart, lineEnd)
        val lines = block.split("\n")
        val replacedLines = change(lines)
        val replaced = replacedLines.joinToString("\n")

        // How far the caret's own line moved: every line before it contributes its own
        // change in length, and the caret's line contributes whatever grew before it.
        var consumed = lineStart
        var shift = 0
        for ((index, line) in lines.withIndex()) {
            val delta = replacedLines[index].length - line.length
            if (caret <= consumed + line.length) {
                shift += delta
                break
            }
            consumed += line.length + 1
            shift += delta
        }

        val moved = (caret + shift).coerceIn(lineStart, lineStart + replaced.length)
        return value.copy(
            text = text.substring(0, lineStart) + replaced + text.substring(lineEnd),
            selection = TextRange(moved),
        )
    }

    /**
     * Carries a list on to the next line, or ends it.
     *
     * Called on every edit, and does nothing to any edit that is not exactly one
     * newline typed at the caret — a paste, a deletion or a character all pass through
     * untouched, so there is no way for this to rewrite text the writer did not just
     * type.
     *
     * Pressing Enter on `- milk` opens `- ` beneath it. Pressing it again on that empty
     * item removes the marker instead, which is how every list in every editor is
     * ended: you stop by pressing Enter twice.
     */
    fun continueList(before: TextFieldValue, after: TextFieldValue): TextFieldValue {
        val caret = after.selection.max
        val onlyANewlineWasTyped = after.selection.collapsed &&
            after.text.length == before.text.length + 1 &&
            caret > 0 &&
            after.text[caret - 1] == '\n' &&
            after.text.removeRange(caret - 1, caret) == before.text
        if (!onlyANewlineWasTyped) return after

        val previousLine = after.text.substring(
            after.text.lastIndexOf('\n', caret - 2).let { if (it == -1) 0 else it + 1 },
            caret - 1,
        )

        val marker = when {
            previousLine.startsWith(BULLET) || previousLine.startsWith("* ") -> BULLET
            else -> NUMBERED.find(previousLine)?.let { match ->
                val number = match.groupValues[1].toIntOrNull() ?: return after
                "${number + 1}. "
            }
        } ?: return after

        // The line that was left behind carries a marker and nothing else, so the
        // writer has ended the list rather than added an empty item to it.
        val markerOnly = previousLine.length == BULLET.length && marker == BULLET ||
            NUMBERED.find(previousLine)?.range?.last == previousLine.lastIndex
        if (markerOnly) {
            // The marker and the newline that was just typed both go: ending a list
            // leaves the caret on the empty line where the marker was, not on a fresh
            // line below it. Keeping the newline would push a blank line into the note
            // every time a list was finished.
            val lineStart = caret - 1 - previousLine.length
            return after.copy(
                text = after.text.removeRange(lineStart, caret),
                selection = TextRange(lineStart),
            )
        }

        return after.copy(
            text = after.text.substring(0, caret) + marker + after.text.substring(caret),
            selection = TextRange(caret + marker.length),
        )
    }

    // ---- inline ----------------------------------------------------------

    /**
     * One line's inline markers.
     *
     * Longest markers first, so `**bold**` is not read as two italics. An unclosed
     * marker is left in the text as written: it is more likely to be an asterisk the
     * writer meant than a formatting instruction they abandoned.
     */
    private fun AnnotatedString.Builder.appendInline(line: String) {
        var index = 0
        while (index < line.length) {
            val marker = MARKERS.firstOrNull { line.startsWith(it.token, index) }
            if (marker == null) {
                append(line[index])
                index++
                continue
            }
            val contentStart = index + marker.token.length
            val close = line.indexOf(marker.token, contentStart)
            if (close == -1) {
                append(line[index])
                index++
                continue
            }
            withStyleOf(marker.style) { appendInline(line.substring(contentStart, close)) }
            index = close + marker.token.length
        }
    }

    private inline fun AnnotatedString.Builder.withStyleOf(
        style: SpanStyle,
        block: AnnotatedString.Builder.() -> Unit,
    ) {
        val handle = pushStyle(style)
        block()
        pop(handle)
    }

    private class Marker(val token: String, val style: SpanStyle)

    private val MARKERS = listOf(
        Marker(BOLD, SpanStyle(fontWeight = FontWeight.Bold)),
        Marker(UNDERLINE, SpanStyle(textDecoration = TextDecoration.Underline)),
        Marker(STRIKE, SpanStyle(textDecoration = TextDecoration.LineThrough)),
        Marker(ITALIC, SpanStyle(fontStyle = FontStyle.Italic)),
        Marker(CODE, SpanStyle(fontFamily = FontFamily.Monospace)),
    )
}
