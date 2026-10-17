package com.ezygo.attendance.ui.miniapps.sgpa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.DraftStore
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.SamarthRepository
import com.ezygo.attendance.domain.Cgpa
import com.ezygo.attendance.domain.model.SemesterResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The institution's grade scale.
 *
 * [range] is what a mark is read against; the bands are contiguous and cover 0..100,
 * so any mark in that span lands on exactly one grade.
 */
enum class Grade(
    val letter: String,
    val label: String,
    val weight: Double,
    val range: IntRange,
) {
    S("S", "Outstanding", 10.0, 90..100),
    A("A", "Excellent", 9.0, 80..89),
    B("B", "Very good", 8.0, 70..79),
    C("C", "Good", 7.0, 60..69),
    D("D", "Satisfactory", 6.0, 50..59),
    F("F", "Fail", 0.0, 0..49),
    ;

    companion object {
        /** Null for a mark outside 0..100, which is a typo rather than a grade. */
        fun forMark(mark: Int): Grade? = entries.firstOrNull { mark in it.range }
    }
}

/**
 * The course categories, each worth a fixed number of credits.
 *
 * Chosen instead of typing a credit value, because the credits are a property of the
 * category rather than of the course: picking "Major" cannot be off by one, whereas
 * typing 4 can.
 */
enum class CourseType(val label: String, val credits: Double) {
    MAJOR("Major", 4.0),
    MINOR("Minor", 4.0),
    MDC("MDC", 3.0),
    AEC("AEC", 3.0),
    SEC("SEC", 3.0),
    VAC("VAC", 3.0),
    INTERNSHIP("Internship", 2.0),
    SEMINAR("Seminar", 2.0),
    ;

    val creditLabel: String get() = "$label · ${credits.toInt()} credits"
}

/** Whether a row is being answered with a mark or straight with a grade. */
enum class ScoreEntry { MARK, GRADE }

/**
 * One course in the calculation.
 *
 * A course carries a mark or a grade, never both: [entry] decides which half is in
 * force, and switching clears the other, so what is on screen is always the whole
 * answer with nothing hidden behind it.
 */
data class SgpaCourse(
    val id: String,
    val name: String = "",
    /** True once the user chose to type a name rather than pick a known course. */
    val customName: Boolean = false,
    val type: CourseType? = null,
    val entry: ScoreEntry = ScoreEntry.MARK,
    val marks: String = "",
    val grade: Grade? = null,
) {
    val creditValue: Double? get() = type?.credits

    val markValue: Int? get() = marks.toIntOrNull()

    /** The grade in force: the one chosen, or the one the mark implies. */
    val effectiveGrade: Grade?
        get() = when (entry) {
            ScoreEntry.GRADE -> grade
            ScoreEntry.MARK -> markValue?.let { Grade.forMark(it) }
        }

    val hasBadMark: Boolean
        get() = entry == ScoreEntry.MARK && marks.isNotBlank() &&
            (markValue == null || markValue !in 0..100)

    /** Counts towards the SGPA only with both halves of the sum present. */
    val isComplete: Boolean get() = creditValue != null && effectiveGrade != null
}

data class SgpaUiState(
    val courses: List<SgpaCourse> = emptyList(),
    /** Every course the portal knows about, for the name dropdown. */
    val knownCourses: List<String> = emptyList(),
    /** Semesters already read off downloaded grade cards, for the CGPA it moves. */
    val pastResults: List<SemesterResult> = emptyList(),
) {
    private val counted: List<SgpaCourse> get() = courses.filter { it.isComplete }

    val totalCredits: Double get() = counted.sumOf { it.creditValue ?: 0.0 }

    /**
     * Σ(weightage × credit) / Σ(credits), over the courses that have both.
     *
     * Null rather than zero while the form is incomplete: a blank calculator has no
     * SGPA, and showing 0.00 would read as a result rather than as an absence.
     */
    val sgpa: Double?
        get() {
            val credits = totalCredits
            if (credits <= 0.0) return null
            val points = counted.sumOf { (it.effectiveGrade?.weight ?: 0.0) * (it.creditValue ?: 0.0) }
            return points / credits
        }

    val countedCourses: Int get() = counted.size

    /** Rows the user has begun but not finished, so the screen can say why. */
    val incompleteCourses: Int get() = courses.count { !it.isComplete }

    // ---- what it does to the CGPA ----------------------------------------

    /**
     * True when every past semester carries a credit total.
     *
     * Without them the weighted formula cannot be applied to the history, and a
     * projection built on a different formula from the one on the grades screen would
     * be a different number pretending to be the same one.
     */
    /** CGPA over the semesters already read: the figure this estimate moves from. */
    val currentCgpa: Double? get() = Cgpa.of(pastResults)

    /**
     * CGPA with this semester added, on the same formula the grades screen uses.
     *
     * Null while there is nothing to add or no history to add it to: an estimate of
     * where the CGPA lands is only meaningful once there is a CGPA to land on.
     */
    val projectedCgpa: Double?
        get() = Cgpa.with(pastResults, sgpa ?: return null, totalCredits)

    val cgpaDelta: Double?
        get() {
            val from = currentCgpa ?: return null
            val to = projectedCgpa ?: return null
            return to - from
        }
}

/**
 * The SGPA estimator, inside the grades app.
 *
 * It opens on the semester the student is actually sitting - every enrolled course,
 * already named - so the work is entering marks rather than rebuilding a list the app
 * already has. Beside the SGPA it shows where the CGPA lands, because the reason for
 * asking about this semester is almost always what it does to the whole degree.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SgpaViewModel @Inject constructor(
    dashboardStore: DashboardStore,
    samarthRepository: SamarthRepository,
    private val drafts: DraftStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SgpaUiState())
    val state: StateFlow<SgpaUiState> = _state.asStateFlow()

    /**
     * Held back until the saved draft has been read.
     *
     * Without it the prefilled blank rows written during construction would be saved
     * over the draft before it had a chance to load, and leaving the screen would
     * reliably erase the marks it was meant to restore.
     */
    private var restored = false

    init {
        // Every enrolled course, whether or not it has attendance recorded: a course
        // with no classes yet still has a grade at the end of the semester.
        val dashboard = dashboardStore.dashboard.value
        val known = (
            dashboard?.courses.orEmpty().map { it.courseName } +
                dashboard?.coursesWithoutRecords.orEmpty().map { it.name }
            ).distinct().sorted()

        _state.value = SgpaUiState(
            // Prefilled with this semester rather than starting blank: the courses are
            // known, so the only thing left to enter is what is not - the marks.
            courses = freshCourses(known),
            knownCourses = known,
        )

        viewModelScope.launch {
            val saved = runCatching { drafts.currentSgpa()?.let(SgpaDraft::decode) }.getOrNull()
            if (saved != null && saved.courses.isNotEmpty()) {
                _state.update { it.copy(courses = saved.courses) }
            }
            restored = true
            // Only now does anything the user does get written back.
            // Debounced, and only when the encoded draft actually differs: the
            // state changes on every keystroke in a marks field and on every
            // dashboard refresh, and a disk write for each of those would be a lot
            // of work to store something nobody has finished typing.
            state.debounce(DRAFT_WRITE_DELAY_MS)
                .map { SgpaDraft.encode(it.courses) }
                .distinctUntilChanged()
                .collect { draft -> runCatching { drafts.setSgpa(draft) } }
        }

        viewModelScope.launch {
            samarthRepository.semesterResults.collect { results ->
                _state.update { it.copy(pastResults = results) }
            }
        }
    }

    private fun freshCourses(known: List<String>): List<SgpaCourse> =
        known.map { SgpaCourse(id = UUID.randomUUID().toString(), name = it) }
            .ifEmpty { listOf(SgpaCourse(id = UUID.randomUUID().toString())) }

    fun addCourse() = _state.update {
        it.copy(courses = it.courses + SgpaCourse(id = UUID.randomUUID().toString()))
    }

    fun removeCourse(id: String) = _state.update {
        it.copy(courses = it.courses.filterNot { course -> course.id == id })
    }

    /**
     * Picking a known course, which also leaves custom-name mode.
     *
     * A name already on another row is refused outright. Two rows for one course would
     * count its credits twice, and an SGPA over a course list that is not the semester
     * is not an SGPA — the screen offers only free names, and this is the guard behind
     * that so no other path can get around it.
     */
    fun setName(id: String, value: String) = _state.update { current ->
        if (current.courses.any { it.id != id && it.name.equals(value, ignoreCase = true) }) {
            return@update current
        }
        current.copy(
            courses = current.courses.map {
                if (it.id == id) it.copy(name = value, customName = false) else it
            },
        )
    }

    /** Switching to a typed name, for a course the portal has never heard of. */
    fun setCustomName(id: String, custom: Boolean) = update(id) {
        it.copy(customName = custom, name = if (custom) it.name else "")
    }

    fun setTypedName(id: String, value: String) = update(id) { it.copy(name = value) }

    /** Course names not already spoken for by another row. */
    fun availableNames(course: SgpaCourse): List<String> {
        val taken = _state.value.courses
            .filter { it.id != course.id }
            .map { it.name.lowercase() }
            .toSet()
        return _state.value.knownCourses.filterNot { it.lowercase() in taken }
    }

    fun setType(id: String, type: CourseType?) = update(id) { it.copy(type = type) }

    fun setEntry(id: String, entry: ScoreEntry) = update(id) {
        // The half being left behind is cleared rather than kept out of sight, so the
        // row can never hold a second answer that disagrees with the one on screen.
        when (entry) {
            ScoreEntry.MARK -> it.copy(entry = entry, grade = null)
            ScoreEntry.GRADE -> it.copy(entry = entry, marks = "")
        }
    }

    fun setMarks(id: String, value: String) = update(id) {
        it.copy(marks = value.filter(Char::isDigit).take(3), entry = ScoreEntry.MARK, grade = null)
    }

    fun setGrade(id: String, grade: Grade?) = update(id) {
        it.copy(grade = grade, entry = ScoreEntry.GRADE, marks = "")
    }

    /** Back to this semester's courses with nothing entered against them. */
    /**
     * Back to an empty semester, and the only thing that discards the saved draft.
     *
     * The collector above writes the emptied state straight back, so the stored copy
     * is replaced rather than left behind to reappear on the next visit.
     */
    fun clearAll() = _state.update { current ->
        current.copy(courses = freshCourses(current.knownCourses))
    }

    private fun update(id: String, change: (SgpaCourse) -> SgpaCourse) = _state.update { current ->
        current.copy(courses = current.courses.map { if (it.id == id) change(it) else it })
    }

    private companion object {
        /** Long enough to cover typing a mark, short enough to survive leaving fast. */
        const val DRAFT_WRITE_DELAY_MS = 400L
    }
}
