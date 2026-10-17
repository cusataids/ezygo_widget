package com.ezygo.attendance.ui.miniapps.whatif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.DraftStore
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.ClaimDay
import com.ezygo.attendance.domain.model.ClaimSessionType
import com.ezygo.attendance.domain.model.ClaimedSession
import com.ezygo.attendance.domain.model.countByCourse
import com.ezygo.attendance.domain.model.resolveClaimedSessions
import com.ezygo.attendance.domain.model.sessionsOn
import java.time.LocalDate
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
 * One subject in the simulation.
 *
 * [attend] and [miss] are classes that have not happened. Everything derived from
 * them is arithmetic on the recorded counts, not a prediction: attending a class
 * adds one to both the attended and the total, missing one adds only to the total.
 */
data class WhatIfSubject(
    val key: String,
    val name: String,
    /** Classes counted in the student's favour so far: present plus duty leave. */
    val attended: Int,
    val total: Int,
    val attend: Int = 0,
    val miss: Int = 0,
    /**
     * Recorded absences this subject would get back if the claims held.
     *
     * Added to the attended count and to nothing else, which is the whole difference
     * between a claim and [attend]: an attended future class is one more class that
     * happened, so it raises the total too, while a claim re-marks a class already in
     * the total. Adding it to both would invent a class that was never scheduled.
     */
    val claimed: Int = 0,
    /** True for a subject typed in by hand rather than taken from the portal. */
    val isManual: Boolean = false,
    /**
     * True when this row is in the list because a claim reaches it, rather than
     * because the user added it.
     *
     * Only affects two things: the information indicator on the card, and whether
     * the row disappears again when the claim does. Everything else about it — the
     * counts, the steppers, the projection — is an ordinary subject.
     */
    val fromClaim: Boolean = false,
) {
    val currentPercent: Double? get() = percentOf(attended, total)

    val projectedPercent: Double?
        get() = percentOf(attended + attend + claimed, total + attend + miss)

    /** Whole points of movement, or null when there was nothing to move from. */
    val deltaPoints: Double?
        get() {
            val from = currentPercent ?: return null
            val to = projectedPercent ?: return null
            return to - from
        }

    val hasChanges: Boolean get() = attend > 0 || miss > 0 || claimed > 0

    private fun percentOf(good: Int, all: Int): Double? =
        if (all <= 0) null else good * 100.0 / all
}

/**
 * A course the picker can offer.
 *
 * Flattened from two sources so both look the same to the screen: courses with
 * recorded classes carry their counts, and courses the student is enrolled in but has
 * never been marked for start at nothing. Leaving the second kind out would hide
 * exactly the subjects a simulation is most useful for — the ones just starting.
 */
data class WhatIfCandidate(
    val courseId: Int,
    val name: String,
    val attended: Int,
    val total: Int,
) {
    val percent: Double? get() = percentOf(attended, total)
}

data class WhatIfUiState(
    val subjects: List<WhatIfSubject> = emptyList(),
    /** Courses from the portal that have not been added yet. */
    val available: List<WhatIfCandidate> = emptyList(),
    /**
     * Every course in this semester, as recorded, whether or not it is in the
     * simulation — so the average covers the whole semester rather than the handful
     * of subjects added below.
     */
    val semesterCourses: List<WhatIfCandidate> = emptyList(),
    /** The days being claimed, in the order they were added. */
    val claims: List<ClaimDay> = emptyList(),
    /**
     * What those days actually cover, resolved against recorded attendance.
     *
     * Held rather than recomputed per read: the card, the subject list and the average
     * all need it, and resolving it once means they cannot disagree about which
     * sessions a claim reaches.
     */
    val claimedSessions: Set<ClaimedSession> = emptySet(),
) {
    /** Claimed sessions per course, which is all the projection needs from them. */
    val claimedByCourse: Map<Int, Int> get() = claimedSessions.countByCourse()
    /**
     * Every subject the average is taken over, each with whatever is planned for it.
     *
     * The semester as the portal records it, plus anything entered by hand. A
     * hand-entered subject used to be left out of this entirely, on the reasoning that
     * it is not part of the semester — which meant planning classes on one moved its
     * own card and nothing else, and the figure at the top of the screen sat still
     * while the screen's whole question was what that figure would do.
     *
     * Both averages are taken over this one list, which is the part that matters: a
     * "before -> after" pair computed over two different sets of subjects would differ
     * by the set as well as by the plan, and the arrow between them would be a lie.
     */
    private val averaged: List<Planned>
        get() {
            val claimed = claimedByCourse
            return semesterCourses.map { course ->
                val simulated = subjects.firstOrNull {
                    it.key == COURSE_KEY_PREFIX + course.courseId
                }
                Planned(
                    attended = course.attended,
                    total = course.total,
                    attend = simulated?.attend ?: 0,
                    miss = simulated?.miss ?: 0,
                    // Read from the claim set rather than from the subject, so a claim
                    // moves the average whether or not that subject was added below.
                    // A claim is about absences that already happened; it does not
                    // need the student to have picked the subject out first.
                    claimed = claimed[course.courseId] ?: 0,
                )
            } + subjects.filter { it.isManual }.map {
                Planned(attended = it.attended, total = it.total, attend = it.attend, miss = it.miss)
            }
        }

    /**
     * Average attendance as things stand.
     *
     * Unweighted mean over every subject, each counting once whatever its class count,
     * subjects with nothing recorded left out rather than counted as zero — the same
     * rule the home screen uses. With no hand-entered subjects this is exactly the
     * figure shown everywhere else in the app; with them it is that figure plus the
     * subjects the portal does not know about, which is why the card says so.
     */
    val currentAverage: Double?
        get() = averaged.mapNotNull { percentOf(it.attended, it.total) }.averageOrNull()

    /** The same average with the planned classes and the claims applied. */
    val projectedAverage: Double?
        get() = averaged.mapNotNull {
            percentOf(it.attended + it.attend + it.claimed, it.total + it.attend + it.miss)
        }.averageOrNull()

    val averageDelta: Double?
        get() {
            val from = currentAverage ?: return null
            val to = projectedAverage ?: return null
            return to - from
        }

    val hasChanges: Boolean get() = subjects.any { it.hasChanges } || claimedSessions.isNotEmpty()

    /** Subjects in the simulation the portal has no record of. */
    val manualSubjects: Int get() = subjects.count { it.isManual }

    /** How many subjects the average covers, hand-entered ones included. */
    val averagedSubjects: Int get() = semesterCourses.size + manualSubjects

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    /** One subject's recorded counts and whatever is planned on top of them. */
    private data class Planned(
        val attended: Int,
        val total: Int,
        val attend: Int,
        val miss: Int,
        val claimed: Int = 0,
    )
}

/** Shared by the state and the view model, so the two can never key differently. */
internal const val COURSE_KEY_PREFIX = "course:"

private fun percentOf(good: Int, all: Int): Double? =
    if (all <= 0) null else good * 100.0 / all

/**
 * The attendance simulator.
 *
 * Starts empty on purpose: it answers a question about a handful of subjects the
 * student is actually worried about, and pre-loading every course would bury that in
 * a list they then have to prune.
 *
 * What the user builds here is kept across visits. It used to live only in memory on
 * the reasoning that a scratchpad should not still hold last week's guesses — but the
 * cost of that landed on the wrong person: stepping out to check a figure on another
 * screen discarded the whole simulation. Reset is now the only thing that clears it.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class WhatIfViewModel @Inject constructor(
    private val dashboardStore: DashboardStore,
    private val drafts: DraftStore,
) : ViewModel() {

    private val _state = MutableStateFlow(WhatIfUiState())
    val state: StateFlow<WhatIfUiState> = _state.asStateFlow()

    /** Nothing is written back until the stored draft has been read; see SgpaViewModel. */
    private var restored = false

    init {
        refreshAvailable()
        viewModelScope.launch {
            val saved = runCatching { drafts.currentWhatIf()?.let(WhatIfDraft::decode) }
                .getOrNull()
            if (saved != null) {
                _state.update { current ->
                    withClaims(
                        current.copy(
                            subjects = saved.whatIfSubjects,
                            claims = saved.claimDays,
                        ),
                    )
                }
                // The picker must not offer a subject the restored draft already holds.
                refreshAvailable()
            }
            restored = true
            // Debounced, and only when the encoded draft actually differs: the
            // state changes on every keystroke in a marks field and on every
            // dashboard refresh, and a disk write for each of those would be a lot
            // of work to store something nobody has finished typing.
            state.debounce(DRAFT_WRITE_DELAY_MS)
                .map { WhatIfDraft.encode(it.subjects, it.claims) }
                .distinctUntilChanged()
                .collect { draft -> runCatching { drafts.setWhatIf(draft) } }
        }
    }

    /**
     * The semester as recorded, and what of it is still free to add.
     *
     * Both come from the same read: the average is over the whole semester and the
     * picker over what is left of it, and computing them apart would let them drift.
     */
    private fun refreshAvailable() = _state.update { current ->
        val taken = current.subjects.map { it.key }.toSet()
        val dashboard = dashboardStore.dashboard.value
        val recorded = dashboard?.courses.orEmpty().map {
            WhatIfCandidate(
                courseId = it.courseId,
                name = it.courseName,
                attended = it.stats.positive,
                total = it.stats.total,
            )
        }
        val unrecorded = dashboard?.coursesWithoutRecords.orEmpty().map {
            WhatIfCandidate(courseId = it.courseId, name = it.name, attended = 0, total = 0)
        }
        val semester = (recorded + unrecorded).sortedBy { it.name }
        // Through withClaims because this is also the point the dashboard is re-read:
        // a refresh that lands while claims are on screen has to re-resolve them
        // against the new records, or a claim keeps pointing at an absence that has
        // since been marked present.
        withClaims(
            current.copy(
                semesterCourses = semester,
                available = semester.filterNot { courseKey(it.courseId) in taken },
            ),
        )
    }

    fun addCourse(course: WhatIfCandidate) {
        _state.update { current ->
            val key = courseKey(course.courseId)
            if (current.subjects.any { it.key == key }) return@update current
            withClaims(
                current.copy(
                    subjects = current.subjects + WhatIfSubject(
                        key = key,
                        name = course.name,
                        attended = course.attended,
                        total = course.total,
                    ),
                ),
            )
        }
        refreshAvailable()
    }

    /**
     * A subject the portal does not know about, with counts the user supplies.
     *
     * Zero of zero is allowed: a course that has not met yet is a perfectly good thing
     * to simulate, and refusing it would mean the only subjects you can plan around
     * are the ones already partly decided.
     */
    fun addManual(name: String, attended: Int, total: Int) {
        val cleanTotal = total.coerceAtLeast(0)
        val cleanAttended = attended.coerceIn(0, cleanTotal)
        _state.update { current ->
            current.copy(
                subjects = current.subjects + WhatIfSubject(
                    key = "manual:" + UUID.randomUUID(),
                    name = name.trim().ifBlank { "Other subject" },
                    attended = cleanAttended,
                    total = cleanTotal,
                    isManual = true,
                ),
            )
        }
    }

    fun remove(key: String) {
        _state.update { it.copy(subjects = it.subjects.filterNot { s -> s.key == key }) }
        refreshAvailable()
    }

    fun setAttend(key: String, value: Int) = updateSubject(key) { it.copy(attend = value.coerceIn(0, MAX_CLASSES)) }

    fun setMiss(key: String, value: Int) = updateSubject(key) { it.copy(miss = value.coerceIn(0, MAX_CLASSES)) }

    /**
     * Empties the simulation, and the only thing that discards the saved draft.
     *
     * Everything goes — the subjects as well as the numbers on them — to match the
     * SGPA estimator's Reset, which returns that screen to a blank semester. Zeroing
     * the counters while leaving a list of subjects behind would be a third behaviour
     * for a button that reads the same in both places.
     */
    fun resetAll() = _state.update { current ->
        withClaims(current.copy(subjects = emptyList(), claims = emptyList()))
    }.also { refreshAvailable() }

    // ---- claims ----------------------------------------------------------

    /**
     * Adds a day to the claim, or leaves it alone if it is already there.
     *
     * Silent about the duplicate rather than refusing loudly: the calendar can be
     * tapped twice on the same date by accident, and the honest result of "claim this
     * day" when the day is already claimed is that it stays claimed.
     */
    fun addClaimDate(date: LocalDate) = _state.update { current ->
        if (current.claims.any { it.date == date }) return@update current
        withClaims(current.copy(claims = (current.claims + ClaimDay(date)).sortedBy { it.date }))
    }

    fun removeClaimDate(date: LocalDate) = _state.update { current ->
        withClaims(current.copy(claims = current.claims.filterNot { it.date == date }))
    }

    /**
     * Drops every claim, and nothing else.
     *
     * The narrower sibling of [resetAll]: the subjects and the numbers on them survive,
     * which is the whole reason it exists — the days being claimed are the part a
     * student tries several versions of, and starting those again should not cost them
     * the list they built first.
     *
     * Routed through [withClaims] like every other claim edit, so the subjects the
     * claims added of their own accord leave with them.
     */
    fun clearClaims() = _state.update { current ->
        withClaims(current.copy(claims = emptyList()))
    }

    fun setClaimType(date: LocalDate, type: ClaimSessionType) = _state.update { current ->
        withClaims(
            current.copy(
                claims = current.claims.map { if (it.date == date) it.copy(type = type) else it },
            ),
        )
    }

    /** Ticks one period on or off for a day that is being claimed session by session. */
    fun toggleClaimSession(date: LocalDate, sessionId: Int) = _state.update { current ->
        withClaims(
            current.copy(
                claims = current.claims.map { day ->
                    if (day.date != date) {
                        day
                    } else {
                        val next = if (sessionId in day.customSessionIds) {
                            day.customSessionIds - sessionId
                        } else {
                            day.customSessionIds + sessionId
                        }
                        day.copy(type = ClaimSessionType.CUSTOM, customSessionIds = next)
                    }
                },
            ),
        )
    }

    /**
     * The subject-wise route: claim these exact recorded absences.
     *
     * Folded into the same day list the calendar builds rather than kept apart, so the
     * two ways in cannot double-count. A date already claimed as a whole day stays a
     * whole day — it already covers the sessions being added.
     */
    fun claimRecords(records: List<AttendanceRecord>) = _state.update { current ->
        var claims = current.claims
        records.groupBy { it.dateKey }.forEach { (dateKey, dayRecords) ->
            val date = parseDateKey(dateKey) ?: return@forEach
            val sessionIds = dayRecords.map { it.sessionId }.toSet()
            val existing = claims.firstOrNull { it.date == date }
            claims = when {
                existing == null ->
                    claims + ClaimDay(date, ClaimSessionType.CUSTOM, sessionIds)

                existing.type == ClaimSessionType.CUSTOM ->
                    claims.map {
                        if (it.date == date) {
                            it.copy(customSessionIds = it.customSessionIds + sessionIds)
                        } else {
                            it
                        }
                    }

                // Already claimed more broadly than this; nothing to widen.
                else -> claims
            }
        }
        withClaims(current.copy(claims = claims.sortedBy { it.date }))
    }

    /**
     * Days with at least one absence on them, which is what the calendar can offer.
     *
     * A day with nothing to claim is still drawn, just not selectable — see the grid.
     */
    fun absenceDates(): Set<LocalDate> =
        dashboardStore.dashboard.value?.days.orEmpty()
            .filter { day -> day.records.any { !it.isPositive } }
            .mapNotNullTo(mutableSetOf()) { it.date }

    /**
     * How many absent sessions one claimed day actually covers.
     *
     * Resolved through the same function the projection uses, so the number the dialog
     * shows and the number the average is computed from cannot drift apart.
     */
    fun coveredBy(claim: ClaimDay): Int {
        val dashboard = dashboardStore.dashboard.value ?: return 0
        return resolveClaimedSessions(dashboard.days, dashboard.sessions, listOf(claim)).size
    }

    /**
     * Courses that have something to claim, with how much.
     *
     * Courses with no absence are left out rather than listed and then found empty:
     * the picker exists to get to an absence, and an entry that leads to none is a
     * dead end dressed up as a choice.
     */
    fun coursesWithAbsences(): List<ClaimCourse> {
        val absencesByCourse = dashboardStore.dashboard.value?.days.orEmpty()
            .flatMap { it.records }
            .filterNot { it.isPositive }
            .groupingBy { it.courseId }
            .eachCount()
        return _state.value.semesterCourses.mapNotNull { course ->
            val count = absencesByCourse[course.courseId] ?: return@mapNotNull null
            ClaimCourse(course.courseId, course.name, count)
        }
    }

    /**
     * The absences one claimed day actually converts, for its details view.
     *
     * Resolved through the same function the projection uses, so what the details
     * list shows and what the average is computed from cannot disagree. Read-only:
     * nothing here writes, and viewing a claim never touches a record.
     */
    fun sessionsClaimedOn(claim: ClaimDay): List<AttendanceRecord> {
        val dashboard = dashboardStore.dashboard.value ?: return emptyList()
        val resolved = resolveClaimedSessions(dashboard.days, dashboard.sessions, listOf(claim))
        val wanted = resolved.map { it.dateKey to it.sessionId }.toSet()
        return dashboard.days
            .flatMap { it.records }
            .filter { (it.dateKey to it.sessionId) in wanted }
            .sortedBy { it.sessionId }
    }

    /** Every recorded absence for one course, newest first, for the subject-wise picker. */
    fun absencesFor(courseId: Int): List<AttendanceRecord> =
        dashboardStore.dashboard.value?.days.orEmpty()
            .flatMap { day -> day.records }
            .filter { it.courseId == courseId && !it.isPositive }
            .sortedByDescending { it.sortableDate }

    /** The periods recorded on one day, for the custom-session picker. */
    fun sessionsOnDate(date: LocalDate): List<AttendanceRecord> =
        sessionsOn(dashboardStore.dashboard.value?.days.orEmpty(), ClaimDay(date).dateKey)

    /**
     * Re-resolves the claim days against recorded attendance.
     *
     * Run on every change to the claim list rather than lazily at read time, because
     * resolution needs the dashboard and the state object has no access to it. Keeping
     * the resolved set in the state is also what stops the same session being counted
     * twice from the two entry routes.
     */
    private fun withClaims(state: WhatIfUiState): WhatIfUiState {
        val dashboard = dashboardStore.dashboard.value
        val resolved = state.copy(
            claimedSessions = resolveClaimedSessions(
                days = dashboard?.days.orEmpty(),
                sessions = dashboard?.sessions.orEmpty(),
                claims = state.claims,
            ),
        )
        val byCourse = resolved.claimedSessions.countByCourse()

        // A claim is about a subject, so the subject belongs in the list — as an
        // ordinary row, not a second kind of thing. Without this the claim moved the
        // average while the subject it belonged to was nowhere on screen, and there
        // was no way to see what had changed or to plan around it.
        //
        // Keyed the same way as one added by hand, so the existing guard against
        // adding a course twice covers this route too: a subject already in the list
        // is updated, never duplicated.
        val existing = resolved.subjects.map { it.key }.toSet()
        val added = byCourse.keys
            .filterNot { COURSE_KEY_PREFIX + it in existing }
            .mapNotNull { courseId ->
                val course = resolved.semesterCourses.firstOrNull { it.courseId == courseId }
                    ?: return@mapNotNull null
                WhatIfSubject(
                    key = COURSE_KEY_PREFIX + courseId,
                    name = course.name,
                    attended = course.attended,
                    total = course.total,
                    // Marks the row as having arrived through a claim, which is what
                    // the information indicator reads and what tells removal below
                    // that this row is the claim's rather than the user's.
                    fromClaim = true,
                )
            }

        return resolved.copy(
            subjects = (resolved.subjects + added)
                // A subject the claim no longer reaches, and that the user never
                // added themselves, goes with it. One it added by hand stays.
                .filterNot { subject ->
                    val courseId = subject.key.removePrefix(COURSE_KEY_PREFIX).toIntOrNull()
                    subject.fromClaim && (courseId == null || byCourse[courseId] == null)
                }
                .map { subject ->
                    val courseId = subject.key.removePrefix(COURSE_KEY_PREFIX).toIntOrNull()
                    subject.copy(claimed = if (courseId == null) 0 else byCourse[courseId] ?: 0)
                },
        )
    }

    private fun parseDateKey(dateKey: String): LocalDate? {
        if (dateKey.length != 8) return null
        return runCatching {
            LocalDate.of(
                dateKey.substring(0, 4).toInt(),
                dateKey.substring(4, 6).toInt(),
                dateKey.substring(6, 8).toInt(),
            )
        }.getOrNull()
    }

    private fun updateSubject(key: String, change: (WhatIfSubject) -> WhatIfSubject) =
        _state.update { current ->
            current.copy(
                subjects = current.subjects.map { if (it.key == key) change(it) else it },
            )
        }

    private fun courseKey(courseId: Int) = COURSE_KEY_PREFIX + courseId

    private companion object {
        /** Far beyond a semester's remaining classes; a guard, not a policy. */
        const val MAX_CLASSES = 99

        /** Long enough to cover a run of taps, short enough to survive leaving fast. */
        const val DRAFT_WRITE_DELAY_MS = 400L
    }
}
