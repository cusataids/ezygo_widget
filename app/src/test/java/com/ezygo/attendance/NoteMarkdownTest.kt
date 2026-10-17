package com.ezygo.attendance

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ezygo.attendance.ui.miniapps.notes.NoteMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The note formatting, as the toolbar buttons drive it.
 *
 * These are the parts with no UI to try them in: what the markers come to as text, and
 * where the caret lands afterwards — which is what decides whether pressing bold and
 * typing does the obvious thing.
 */
class NoteMarkdownTest {

    // ---- inline ----------------------------------------------------------

    @Test
    fun `wraps a selection and keeps it selected`() {
        val value = TextFieldValue("read chapter four", TextRange(5, 12))
        val bold = NoteMarkdown.toggleInline(value, NoteMarkdown.BOLD)

        assertEquals("read **chapter** four", bold.text)
        // Still over the same word, so a second press can unwrap it.
        assertEquals("chapter", bold.text.substring(bold.selection.min, bold.selection.max))
    }

    @Test
    fun `a second press unwraps what the first wrapped`() {
        val value = TextFieldValue("read chapter four", TextRange(5, 12))
        val bold = NoteMarkdown.toggleInline(value, NoteMarkdown.BOLD)
        val plain = NoteMarkdown.toggleInline(bold, NoteMarkdown.BOLD)

        assertEquals("read chapter four", plain.text)
        assertEquals("chapter", plain.text.substring(plain.selection.min, plain.selection.max))
    }

    /** The markers sit either side of the selection rather than inside it. */
    @Test
    fun `unwraps markers that surround the selection`() {
        val value = TextFieldValue("read **chapter** four", TextRange(7, 14))
        val plain = NoteMarkdown.toggleInline(value, NoteMarkdown.BOLD)

        assertEquals("read chapter four", plain.text)
    }

    @Test
    fun `with nothing selected it leaves the caret between the markers`() {
        val value = TextFieldValue("note: ", TextRange(6))
        val bold = NoteMarkdown.toggleInline(value, NoteMarkdown.BOLD)

        assertEquals("note: ****", bold.text)
        assertEquals(8, bold.selection.start)
        assertEquals(8, bold.selection.end)
    }

    // ---- lines -----------------------------------------------------------

    @Test
    fun `underline is its own mark`() {
        val value = TextFieldValue("read this", TextRange(5, 9))
        val underlined = NoteMarkdown.toggleInline(value, NoteMarkdown.UNDERLINE)

        assertEquals("read __this__", underlined.text)
        assertEquals("read this", NoteMarkdown.render(underlined.text).text)
        assertEquals("read this", NoteMarkdown.plain(underlined.text))
    }

    // ---- carrying a list on --------------------------------------------

    @Test
    fun `enter on a bullet opens another one`() {
        val before = TextFieldValue("- milk", TextRange(6))
        val after = TextFieldValue("- milk\n", TextRange(7))

        val continued = NoteMarkdown.continueList(before, after)
        assertEquals("- milk\n- ", continued.text)
        assertEquals(9, continued.selection.start)
    }

    @Test
    fun `enter on a numbered line counts on`() {
        val before = TextFieldValue("1. wake\n2. run", TextRange(14))
        val after = TextFieldValue("1. wake\n2. run\n", TextRange(15))

        assertEquals("1. wake\n2. run\n3. ", NoteMarkdown.continueList(before, after).text)
    }

    /** Enter on an item with nothing in it is how every editor ends a list. */
    @Test
    fun `enter twice ends the list`() {
        val before = TextFieldValue("- milk\n- ", TextRange(9))
        val after = TextFieldValue("- milk\n- \n", TextRange(10))

        val ended = NoteMarkdown.continueList(before, after)
        assertEquals("- milk\n", ended.text)
        assertEquals(7, ended.selection.start)
    }

    @Test
    fun `a numbered item with nothing in it ends the list too`() {
        val before = TextFieldValue("1. wake\n2. ", TextRange(11))
        val after = TextFieldValue("1. wake\n2. \n", TextRange(12))

        assertEquals("1. wake\n", NoteMarkdown.continueList(before, after).text)
    }

    @Test
    fun `enter on an ordinary line does nothing`() {
        val before = TextFieldValue("just words", TextRange(10))
        val after = TextFieldValue("just words\n", TextRange(11))

        assertEquals("just words\n", NoteMarkdown.continueList(before, after).text)
    }

    /**
     * Only a newline typed at the caret is a list continuation. A paste, a deletion or
     * an ordinary character must pass through untouched, or this would rewrite text the
     * writer never typed.
     */
    @Test
    fun `leaves every other kind of edit alone`() {
        val before = TextFieldValue("- milk", TextRange(6))

        val typed = TextFieldValue("- milks", TextRange(7))
        assertEquals(typed, NoteMarkdown.continueList(before, typed))

        val pasted = TextFieldValue("- milk\neggs", TextRange(11))
        assertEquals(pasted, NoteMarkdown.continueList(before, pasted))

        val deleted = TextFieldValue("- mil", TextRange(5))
        assertEquals(deleted, NoteMarkdown.continueList(before, deleted))
    }

    // ---- lines -----------------------------------------------------------

    @Test
    fun `bullets every line the selection touches`() {
        val value = TextFieldValue("milk\neggs\nbread", TextRange(2, 12))
        val listed = NoteMarkdown.toggleBullet(value)

        assertEquals("- milk\n- eggs\n- bread", listed.text)
    }

    @Test
    fun `a half-bulleted selection becomes fully bulleted, then plain`() {
        val value = TextFieldValue("- milk\neggs", TextRange(0, 11))
        val listed = NoteMarkdown.toggleBullet(value)
        assertEquals("- milk\n- eggs", listed.text)

        // Re-selected rather than chained: a toggle now leaves a caret, so pressing the
        // button again would only reach the line the caret is on.
        val plain = NoteMarkdown.toggleBullet(listed.copy(selection = TextRange(0, 13)))
        assertEquals("milk\neggs", plain.text)
    }

    /** Built out of order or not, a numbered list reads 1, 2, 3 down the page. */
    @Test
    fun `numbers a list from one`() {
        val value = TextFieldValue("wake\nrun\neat", TextRange(0, 12))
        val numbered = NoteMarkdown.toggleNumbered(value)

        assertEquals("1. wake\n2. run\n3. eat", numbered.text)
        assertEquals(
            "wake\nrun\neat",
            NoteMarkdown.toggleNumbered(numbered.copy(selection = TextRange(0, 20))).text,
        )
    }

    @Test
    fun `acts on the whole line even when only part of it is selected`() {
        val value = TextFieldValue("a plain line", TextRange(4, 6))
        assertEquals("# a plain line", NoteMarkdown.toggleHeading(value).text)
    }

    /**
     * A selection left over the line means the next character typed replaces the whole
     * paragraph, which is a way to lose work by pressing a formatting button.
     */
    @Test
    fun `a list format leaves a caret, not a selection`() {
        val value = TextFieldValue("milk\neggs", TextRange(0, 9))
        val listed = NoteMarkdown.toggleBullet(value)

        assertEquals(listed.selection.start, listed.selection.end)
    }

    /** A caret on one line formats that line, and stays where it was in the words. */
    @Test
    fun `the caret keeps its place through the markers`() {
        // Caret after "mi" on the second line.
        val value = TextFieldValue("eggs\nmilk", TextRange(7))
        val listed = NoteMarkdown.toggleBullet(value)

        assertEquals("eggs\n- milk", listed.text)
        assertEquals("eggs\n- mi", listed.text.take(listed.selection.start))
    }

    /** And over several lines, the caret still lands among the same words. */
    @Test
    fun `the caret survives a multi-line toggle`() {
        val value = TextFieldValue("eggs\nmilk", TextRange(0, 7))
        val listed = NoteMarkdown.toggleBullet(value)

        assertEquals("- eggs\n- milk", listed.text)
        assertEquals("- eggs\n- mi", listed.text.take(listed.selection.start))
    }

    @Test
    fun `leaves blank lines alone`() {
        val value = TextFieldValue("milk\n\neggs", TextRange(0, 10))
        assertEquals("- milk\n\n- eggs", NoteMarkdown.toggleBullet(value).text)
    }

    // ---- rendering -------------------------------------------------------

    @Test
    fun `renders markers away and keeps the words`() {
        val rendered = NoteMarkdown.render("**Exam** on ~~Friday~~ *now* Monday")
        assertEquals("Exam on Friday now Monday", rendered.text)
    }

    @Test
    fun `renders a bullet and a number as their marks`() {
        assertEquals("•  milk\n•  eggs", NoteMarkdown.render("- milk\n- eggs").text)
        assertEquals("1.  wake\n2.  run", NoteMarkdown.render("1. wake\n2. run").text)
        assertEquals("Chapter one", NoteMarkdown.render("# Chapter one").text)
    }

    /**
     * An asterisk with no partner is far likelier to be an asterisk the writer meant
     * than a marker they abandoned, so it survives untouched.
     */
    @Test
    fun `leaves an unclosed marker as written`() {
        assertEquals("2 * 3 = 6", NoteMarkdown.render("2 * 3 = 6").text)
        assertEquals("a **b", NoteMarkdown.render("a **b").text)
    }

    @Test
    fun `strips markers for a list preview`() {
        assertEquals(
            "Exam Friday\nmilk\nChapter one",
            NoteMarkdown.plain("**Exam** ~~Friday~~\n- milk\n# Chapter one"),
        )
    }
}
