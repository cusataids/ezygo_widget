package com.ezygo.attendance.data.pdf

import android.util.Log
import com.ezygo.attendance.domain.model.SemesterCourse
import com.ezygo.attendance.domain.model.SemesterResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a grade card PDF.
 *
 * The documents carry a real text layer, so this is extraction rather than OCR: what
 * comes out is exactly what the university printed. Everything here is best-effort —
 * a card that cannot be read is skipped rather than guessed at, because a wrong SGPA
 * silently folded into a CGPA is worse than a missing one.
 */
/** What one grade card yielded. */
data class CardReading(
    val result: SemesterResult,
    val courses: List<SemesterCourse>,
)

@Singleton
class GradeCardReader @Inject constructor() {

    suspend fun text(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { document ->
                PDFTextStripper().getText(document)
            }
        }.onFailure {
            Log.w(TAG, "Could not read the text of ${file.name}", it)
        }.getOrNull()
    }

    /**
     * Everything one card yields: the semester's result and the courses behind it.
     *
     * [fallbackSemester] comes from the file name, which the portal's own term number
     * produced; it is used when the printed line cannot be read, and never over it.
     *
     * The course list can be empty while the result is good. The two are extracted by
     * different means - one line matched by name, a table matched by shape - and a
     * layout this reader cannot follow should cost the course breakdown, not the SGPA.
     */
    suspend fun read(file: File, fallbackSemester: Int?): CardReading? {
        val text = text(file) ?: return null
        val semester = semesterOf(text) ?: fallbackSemester ?: return null
        val sgpa = sgpaOf(text) ?: return null
        return CardReading(
            result = SemesterResult(semester = semester, sgpa = sgpa, credits = creditsOf(text)),
            courses = coursesOf(text, semester),
        )
    }

    /**
     * "Examination Semester IV , April 2026" — the semester is a Roman numeral on the
     * examination line, which is the only place it appears unambiguously. The bare word
     * "Semester" also turns up in "Semester Credits", so the numeral has to follow it
     * immediately for the match to count.
     */
    internal fun semesterOf(text: String): Int? {
        val match = SEMESTER_PATTERN.find(text) ?: return null
        return romanToInt(match.groupValues[1].uppercase())
    }

    /** "GPA : 9.50" on the results line. Also matches a card printing "SGPA". */
    internal fun sgpaOf(text: String): Double? {
        val match = GPA_PATTERN.find(text) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
    }

    /** "Semester Credits : 22", kept for display rather than for the average. */
    internal fun creditsOf(text: String): Int? =
        CREDITS_PATTERN.find(text)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The individual courses printed on a card.
     *
     * A row reads `code · title · type · grade · grade point · credit · credit point ·
     * month`, and PDFBox flattens each one to a line of text in that order. The credit
     * is the second of the three numbers, not the first, and the grade sits before them
     * rather than at the end of the line - which is what the first version of this got
     * wrong, and why it read nothing at all.
     *
     * Long titles wrap: the card prints
     *
     * ```
     * 24-813-0402 DIGITAL LOGIC AND COMPUTER
     * ORGANIZATION MAJOR S 10 4 40 Apr`26
     * ```
     *
     * so a line that opens with a course code but does not complete a row is joined
     * with the lines after it until it does, or until [MAX_WRAPPED_LINES] gives up.
     *
     * Everything that is not a course row is skipped in silence - headers, the grade
     * scale, the disclaimer. A course this cannot read contributes nothing rather than
     * a guess, on the same rule as the SGPA: a wrong credit total quietly folded into a
     * degree's worth of credits is worse than a missing one.
     */
    internal fun coursesOf(text: String, semester: Int): List<SemesterCourse> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val courses = mutableListOf<SemesterCourse>()
        var index = 0
        while (index < lines.size) {
            if (!COURSE_CODE_START.containsMatchIn(lines[index])) {
                index++
                continue
            }

            var buffer = lines[index]
            var joined = 0
            var match = COURSE_LINE_PATTERN.find(buffer)
            while (match == null && joined < MAX_WRAPPED_LINES && index + joined + 1 < lines.size) {
                joined++
                buffer = buffer + " " + lines[index + joined]
                match = COURSE_LINE_PATTERN.find(buffer)
            }

            if (match == null) {
                index++
                continue
            }

            val title = match.groupValues[2].trim()
            if (title.any { it.isLetter() }) {
                courses += SemesterCourse(
                    semester = semester,
                    code = match.groupValues[1],
                    name = title,
                    // The credit, not the grade point beside it and not the credit
                    // point after it. Guarded because a course is never worth more
                    // than a handful of credits, so a bigger number is a mis-parse.
                    credits = match.groupValues[6].toDoubleOrNull()?.takeIf { it in 0.0..MAX_CREDITS },
                    grade = match.groupValues[4],
                )
            }
            // Past the row, wrapped lines included, so a continuation cannot be read
            // a second time as a row of its own.
            index += joined + 1
        }
        // The same code twice means something other than the course table matched;
        // the first reading of each is kept rather than both.
        return courses.distinctBy { it.code }
    }

    internal fun romanToInt(roman: String): Int? {
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
        var total = 0
        var previous = 0
        for (char in roman.reversed()) {
            val value = values[char] ?: return null
            if (value < previous) total -= value else total += value
            previous = value
        }
        return total.takeIf { it in 1..20 }
    }

    private companion object {
        const val TAG = "GradeCardReader"

        val SEMESTER_PATTERN = Regex("""Semester\s+([IVX]+)\b""", RegexOption.IGNORE_CASE)
        val GPA_PATTERN = Regex("""\bS?GPA\s*:?\s*([0-9]{1,2}(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val CREDITS_PATTERN = Regex("""Semester\s+Credits\s*:?\s*([0-9]{1,3})""", RegexOption.IGNORE_CASE)

        /** A row opens with the university's course code, and nothing else does. */
        val COURSE_CODE_START = Regex("""^[0-9A-Z]{2,4}-[0-9A-Z]{2,4}-[0-9A-Z]{2,8}\s""")

        /**
         * One whole course row.
         *
         * `code title type grade gradePoint credit creditPoint month`, with the month
         * optional because it is the one column a card could plausibly omit. The title
         * is taken lazily so the columns after it are not swallowed into it, and the
         * grade alternation is anchored by the whitespace on either side so a title
         * word beginning with "A" cannot be mistaken for the grade A.
         */
        val COURSE_LINE_PATTERN = Regex(
            """^([0-9A-Z]{2,4}-[0-9A-Z]{2,4}-[0-9A-Z]{2,8})\s+(.+?)\s+""" +
                """([A-Z]{2,12})\s+(S|A\+|A|B\+|B|C|D|P|F|AB|I|W)\s+""" +
                """([0-9]{1,2}(?:\.[0-9]{1,2})?)\s+([0-9]{1,2}(?:\.[0-9]{1,2})?)\s+""" +
                """([0-9]{1,3}(?:\.[0-9]{1,2})?)(?:\s+\S+)?\s*$""",
        )

        /** A title wrapping over three lines is not a layout this card uses. */
        const val MAX_WRAPPED_LINES = 2

        /** A single course is never worth more than this; a bigger number is not credits. */
        const val MAX_CREDITS = 30.0
    }
}
