package com.ezygo.attendance.widget

import android.content.Context
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.TokenStore
import com.ezygo.attendance.data.repo.AttendanceRepository
import com.ezygo.attendance.data.repo.AttendanceSnapshot
import com.ezygo.attendance.data.repo.AuthRepository
import com.ezygo.attendance.data.repo.CalendarRepository
import com.ezygo.attendance.data.repo.TaskRepository
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.TimetableRepository
import com.ezygo.attendance.domain.AttendanceCalculator
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.CalendarEvent
import com.ezygo.attendance.domain.model.EventSource
import com.ezygo.attendance.domain.model.FeedSource
import com.ezygo.attendance.domain.model.Period
import com.ezygo.attendance.domain.model.Task
import com.ezygo.attendance.domain.model.TaskSource
import com.ezygo.attendance.domain.model.RiskBand
import com.ezygo.attendance.domain.model.UpcomingTimetable
import com.ezygo.attendance.domain.model.gridDays
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One subject, trimmed to what a widget cell can show. */
data class WidgetSubject(
    val courseId: Int,
    val name: String,
    val percent: Int?,
    /** Classes to attend to reach the target; null when unreachable. */
    val classesNeeded: Int?,
    /** Classes that can still be missed while staying at target; null when unbounded. */
    val classesMissable: Int?,
    /**
     * Carried rather than reduced to a boolean, because a widget colours by degree:
     * a subject a class or two from the threshold has to look different from one
     * that is comfortably clear of it, not just from one already short.
     */
    val band: RiskBand,
) {
    val atRisk: Boolean get() = band == RiskBand.AT_RISK
}

/** One calendar entry, as a widget row. */
data class WidgetEvent(
    /** Carried so a tapped row can open this event rather than merely its day. */
    val id: String,
    val title: String,
    /** "Today", "Tomorrow", or "Mon 8". */
    val dayLabel: String,
    /** "09:00", or null for an all-day event. */
    val timeLabel: String?,
    val isToday: Boolean,
    /**
     * Which feed it came from, so the agenda can colour its stripe by source and each
     * placed widget can keep only the feeds it was configured with.
     *
     * [FeedSource] rather than [EventSource] because a calendar widget now carries
     * task lists alongside calendars, and only one of the two enums can name both.
     */
    val source: FeedSource,
    /**
     * Tasks open the app rather than a calendar entry: there is no event behind the
     * id for the calendar screen to find.
     */
    val isTask: Boolean = false,
)

/**
 * One cell of the timetable grid.
 *
 * [label] is null for a free period. It is the course's short code where the user gave
 * it one and the full name where they did not, resolved by the timetable repository so
 * a widget cell and a grid cell can never disagree about what a course is called.
 */
data class WidgetSlot(
    val label: String?,
    val cancelled: Boolean,
)

/** One row of the grid: a weekday and its periods. */
data class WidgetTimetableDay(
    val label: String,
    val isToday: Boolean,
    val slots: List<WidgetSlot>,
)

/** The week, as the timetable widget draws it. */
data class WidgetTimetable(
    val periodNames: List<String>,
    val days: List<WidgetTimetableDay>,
) {
    val isEmpty: Boolean get() = days.none { day -> day.slots.any { it.label != null } }
}

/**
 * Everything the widgets render, read straight from the Room cache.
 *
 * No widget calls the API on its own — they draw whatever the app last stored, so
 * having them on the home screen costs nothing. Only the refresh button causes a
 * network request.
 */
/**
 * Why a widget has nothing to show, when it has nothing to show.
 *
 * Three states rather than a signed-in boolean, because "not signed in" and "not
 * loaded yet" look identical from the widget's side and read as completely different
 * things to the user. Opening the app with an expired token used to flip every widget
 * on the home screen to "Sign in to see your attendance" for the length of a silent
 * re-authentication the user was never asked to take part in.
 */
enum class WidgetStatus {
    /** Data is present, or at least a period is known and a fetch is under way. */
    READY,

    /** Signing in, or signed in with nothing fetched yet. Say nothing alarming. */
    LOADING,

    /** No token and no stored credentials: only a person can fix this. */
    SIGNED_OUT,
}

data class WidgetSnapshot(
    val status: WidgetStatus,
    val hasData: Boolean,
    val periodLabel: String,
    val targetPercent: Int,
    /** Kept fractional: a widget has room for one decimal and it reads as precise. */
    val averagePercent: Double?,
    val band: RiskBand,
    val shortageCount: Int,
    val subjectCount: Int,
    /**
     * The aggregate counts behind the average.
     *
     * Pooled across every subject, unlike [averagePercent], which is the mean of the
     * per-subject percentages. They answer different questions and a wide widget has
     * room for both: how you are doing, and how many classes that is out of.
     */
    val attended: Int,
    val absent: Int,
    val totalClasses: Int,
    /** Every subject, worst attendance first. */
    val subjects: List<WidgetSubject>,
    /** The next few events, soonest first. Empty is a normal state, not an error. */
    val events: List<WidgetEvent>,
    val lastUpdatedEpochMs: Long?,
    /** The calendar syncs separately from attendance, so it dates separately too. */
    val calendarSyncEpochMs: Long?,
    /** Null when no timetable has been fetched for this period yet. */
    val timetable: WidgetTimetable?,
    /** The timetable is its own fetch, so it carries its own date as well. */
    val timetableUpdatedEpochMs: Long?,
) {
    /** The subjects a shortage widget leads with. */
    val atRiskSubjects: List<WidgetSubject> get() = subjects.filter { it.atRisk }

    companion object {
        val SIGNED_OUT = of(WidgetStatus.SIGNED_OUT)

        /** Nothing to show yet, but nothing wrong either. */
        val LOADING = of(WidgetStatus.LOADING)

        private fun of(status: WidgetStatus) = WidgetSnapshot(
            status = status,
            hasData = false,
            periodLabel = "",
            targetPercent = AttendanceSettings.DEFAULT_TARGET,
            averagePercent = null,
            band = RiskBand.NO_DATA,
            shortageCount = 0,
            subjectCount = 0,
            attended = 0,
            absent = 0,
            totalClasses = 0,
            subjects = emptyList(),
            events = emptyList(),
            lastUpdatedEpochMs = null,
            calendarSyncEpochMs = null,
            timetable = null,
            timetableUpdatedEpochMs = null,
        )
    }
}

/**
 * Hilt reaches the widget through an entry point: a [android.appwidget.AppWidgetProvider]
 * is instantiated by the framework, so it cannot be injected directly.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun preferencesStore(): PreferencesStore
    fun attendanceRepository(): AttendanceRepository
    fun calendarRepository(): CalendarRepository
    fun tokenStore(): TokenStore
    fun dashboardStore(): DashboardStore
    fun timetableRepository(): TimetableRepository
    fun authRepository(): AuthRepository
    fun taskRepository(): TaskRepository
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** More rows than the tallest widget can show, so no size is ever short of them. */
private const val MAX_EVENTS = 10


/**
 * What a missing token actually means.
 *
 * A blank token is not the same as a signed-out user: the app drops its token the
 * moment one 401s and signs back in from stored credentials behind the loading state.
 * A widget that read "no token" as "signed out" announced it on every home screen for
 * the length of that round trip, which is exactly when the user is most likely to be
 * looking at one — they just tapped it to open the app.
 */
private suspend fun WidgetEntryPoint.statusWithoutToken(): WidgetStatus =
    if (runCatching { authRepository().savedCredentials() }.getOrNull() != null) {
        WidgetStatus.LOADING
    } else {
        WidgetStatus.SIGNED_OUT
    }

/**
 * The widgets' view of the cache, read once.
 *
 * This is what draws a widget's first frame: one round of reads lands faster than
 * waiting for several observed Flows to each emit, and nothing has had a chance to
 * go stale in between. [widgetSnapshotFlow] keeps it current from there.
 */
suspend fun loadWidgetSnapshot(context: Context): WidgetSnapshot {
    val entryPoint = context.widgetEntryPoint()

    if (entryPoint.tokenStore().currentToken().isNullOrBlank()) {
        return when (entryPoint.statusWithoutToken()) {
            WidgetStatus.LOADING -> WidgetSnapshot.LOADING
            else -> WidgetSnapshot.SIGNED_OUT
        }
    }

    val preferences = entryPoint.preferencesStore()
    // Signed in, but no period picked yet: the first refresh has not landed. That is
    // loading, not signed out, and must not be reported as the latter.
    val period = preferences.currentPeriod() ?: return WidgetSnapshot.LOADING
    val settings: AttendanceSettings = preferences.currentSettings()
    val attendance = entryPoint.attendanceRepository().snapshotOnce(period)

    val calendar = entryPoint.calendarRepository()
    // Every calendar and every task list, unfiltered.
    //
    // Which of them a given widget shows is now that widget's own setting, chosen when
    // it was added and stored against its id — so the filtering happens in the
    // composition, where the widget's own configuration is readable. Filtering here
    // would apply one answer to every widget on the home screen, which is the thing
    // per-widget configuration exists to stop.
    val events = runCatching {
        calendar.upcomingForHome(MAX_EVENTS, CALENDAR_KEYS).first()
    }.getOrDefault(emptyList())
    val tasks = runCatching { entryPoint.taskRepository().tasks.first() }
        .getOrDefault(emptyList())
    val synced = runCatching { calendar.lastSyncEpochMs.first() }.getOrNull()
    val timetable = runCatching {
        entryPoint.timetableRepository().upcomingOnce(period, LocalDate.now())
    }.getOrNull()

    return buildWidgetSnapshot(period, settings, attendance, events, tasks, synced, timetable)
}

/**
 * The same view, rebuilt every time the cache underneath it changes.
 *
 * Collected inside the widget's composition, which is the only place it can follow
 * the app from. `provideGlance` runs once per Glance session and `provideContent`
 * never returns, so anything read before it is frozen for that whole session: an app
 * refresh landing while a session was alive redrew the widget with the data — and
 * the "last updated" time — from *before* the refresh, and then left it there.
 *
 * Observing Room instead means a refresh reaches a live widget the moment it is
 * written, with no push involved at all.
 */
fun widgetSnapshotFlow(context: Context): Flow<WidgetSnapshot> = flow {
    val entryPoint = context.widgetEntryPoint()
    if (entryPoint.tokenStore().currentToken().isNullOrBlank()) {
        emit(
            when (entryPoint.statusWithoutToken()) {
                WidgetStatus.LOADING -> WidgetSnapshot.LOADING
                else -> WidgetSnapshot.SIGNED_OUT
            },
        )
        return@flow
    }

    val preferences = entryPoint.preferencesStore()
    val period = preferences.currentPeriod()
    if (period == null) {
        emit(WidgetSnapshot.LOADING)
        return@flow
    }

    val calendar = entryPoint.calendarRepository()

    // The target percentage is observed too, so changing it in the app moves the
    // widget's risk colours with it rather than waiting for the next refresh.
    // Calendars and task lists arrive as one slot rather than two, because combine's
    // typed overloads stop at five and the alternative loses every type in the lambda.
    val agenda = combine(
        calendar.upcomingForHome(MAX_EVENTS, CALENDAR_KEYS),
        entryPoint.taskRepository().tasks,
    ) { events, tasks -> events to tasks }

    emitAll(
        combine(
            preferences.settings,
            entryPoint.attendanceRepository().observe(period),
            agenda,
            calendar.lastSyncEpochMs,
            // Observed like the rest, so renaming a course to a short code redraws the
            // grid on the home screen without waiting for anything to be refetched.
            entryPoint.timetableRepository().observeUpcoming(period, LocalDate.now()),
        ) { settings, attendance, (events, tasks), synced, timetable ->
            buildWidgetSnapshot(period, settings, attendance, events, tasks, synced, timetable)
        },
    )
}


/** Only the calendar half of [FeedSource]; the task half is read from the task table. */
private val CALENDAR_KEYS: List<String> =
    FeedSource.entries.filter { it.isCalendar }.map { it.key }

/**
 * Calendars and task deadlines as one list, soonest first.
 *
 * Merged here rather than by the widget so both kinds sort together: a task due at
 * 09:00 belongs above a lecture at 11:00, not in a separate block underneath it.
 * Completed tasks are dropped — an agenda is what is still coming.
 */
private fun buildAgenda(
    events: List<CalendarEvent>,
    tasks: List<Task>,
    today: LocalDate,
): List<WidgetEvent> {
    val zone = ZoneId.systemDefault()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()

    // Paired with the instant each one happens at, so the two kinds can be ordered
    // against each other. Sorting the mapped rows instead would only have the day
    // label to go on, which cannot separate 09:00 from 11:00 on the same day.
    val calendarRows = events.map { it.start.toEpochMilli() to it.toWidgetEvent(today) }
    val taskRows = tasks.asSequence()
        .filterNot { it.done }
        .mapNotNull { task -> task.dueEpochMs?.let { due -> due to task } }
        .filter { (due, _) -> due >= startOfToday }
        .mapNotNull { (due, task) -> task.toWidgetEvent(today, zone)?.let { due to it } }

    return (calendarRows + taskRows)
        .sortedBy { (at, _) -> at }
        .map { (_, row) -> row }
        .take(MAX_EVENTS)
}

/**
 * Assembles one snapshot.
 *
 * Uses the same [AttendanceCalculator] the app screens use, so a widget can never
 * quote a different percentage from the one inside the app.
 */
private fun buildWidgetSnapshot(
    period: Period,
    settings: AttendanceSettings,
    attendance: AttendanceSnapshot,
    events: List<CalendarEvent>,
    tasks: List<Task>,
    calendarSyncEpochMs: Long?,
    timetable: UpcomingTimetable?,
): WidgetSnapshot {
    val courses = AttendanceCalculator.courseStats(attendance.records, settings)
    val overall = AttendanceCalculator.statsFor(attendance.records, settings)

    val average = courses.mapNotNull { it.stats.percentage }
        .takeIf { it.isNotEmpty() }
        ?.average()

    // Already ordered worst first by courseStats, which is the order a glanceable
    // list wants: the subjects in trouble are the ones that fit in a small widget.
    val subjects = courses.map { course ->
        WidgetSubject(
            courseId = course.courseId,
            name = course.courseName,
            percent = course.stats.percentage?.let { Math.round(it).toInt() },
            classesNeeded = course.stats.classesNeededForTarget,
            classesMissable = course.stats.classesMissableAtTarget,
            band = course.stats.band,
        )
    }

    val today = LocalDate.now()
    return WidgetSnapshot(
        status = WidgetStatus.READY,
        hasData = overall.hasData,
        periodLabel = period.displayLabel,
        targetPercent = settings.targetPercent,
        averagePercent = average,
        band = AttendanceCalculator.bandFor(average, settings),
        shortageCount = subjects.count { it.atRisk },
        subjectCount = courses.size,
        attended = overall.positive,
        absent = overall.absent,
        totalClasses = overall.total,
        subjects = subjects,
        events = buildAgenda(events, tasks, today),
        lastUpdatedEpochMs = attendance.lastUpdatedEpochMs,
        calendarSyncEpochMs = calendarSyncEpochMs,
        timetable = timetable?.toWidgetTimetable(today),
        timetableUpdatedEpochMs = timetable?.lastUpdatedEpochMs,
    )
}

// ---- timetable -> widget grid --------------------------------------------

private fun UpcomingTimetable.toWidgetTimetable(today: LocalDate): WidgetTimetable {
    // The same weekdays the app's grid shows, from the same rule, so the two cannot
    // disagree about what a week is.
    val week = days.gridDays()
    // Every day carries the same period list, so the first with any names the columns.
    val periodNames = week.firstOrNull { it.slots.isNotEmpty() }?.slots.orEmpty().map { it.sessionName }
    return WidgetTimetable(
        periodNames = periodNames,
        days = week.map { day ->
            WidgetTimetableDay(
                // Three letters is what a row header can hold beside six cells.
                label = day.weekdayName.take(3),
                isToday = day.date == today,
                slots = day.slots.map { slot ->
                    WidgetSlot(label = slot.displayName, cancelled = slot.isCancelled)
                },
            )
        },
    )
}

// ---- calendar -> widget rows ---------------------------------------------

private fun CalendarEvent.toWidgetEvent(today: LocalDate): WidgetEvent {
    val date = startDate()
    return WidgetEvent(
        id = id,
        title = title,
        dayLabel = date.toDayLabel(today),
        timeLabel = startTime()?.toClockLabel(),
        isToday = date == today,
        source = source.toFeedSource(),
    )
}

private fun EventSource.toFeedSource(): FeedSource = when (this) {
    EventSource.BATCH -> FeedSource.BATCH
    EventSource.ACADEMIC -> FeedSource.ACADEMIC
    EventSource.LOCAL -> FeedSource.LOCAL
}

/**
 * A dated task, as an agenda row.
 *
 * Undated ones are dropped rather than listed at the end: an agenda is ordered by when,
 * and something with no when has nowhere to sit in it.
 */
private fun Task.toWidgetEvent(today: LocalDate, zone: ZoneId): WidgetEvent? {
    val due = dueEpochMs ?: return null
    val moment = Instant.ofEpochMilli(due).atZone(zone)
    val date = moment.toLocalDate()
    return WidgetEvent(
        id = id,
        title = title,
        dayLabel = date.toDayLabel(today),
        timeLabel = if (isAllDay) null else moment.toLocalTime().toClockLabel(),
        isToday = date == today,
        source = when (source) {
            TaskSource.OWN -> FeedSource.TASKS_OWN
            TaskSource.SYNCED -> FeedSource.TASKS_SYNCED
        },
        isTask = true,
    )
}

private fun LocalTime.toClockLabel(): String = "%02d:%02d".format(hour, minute)

private fun LocalDate.toDayLabel(today: LocalDate): String = when (this) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    // Deliberately not localised: this is three fixed letters plus a number in a
    // space where a long localised weekday would be cut off anyway.
    else -> dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } +
        " " + dayOfMonth
}
