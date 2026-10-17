package com.ezygo.attendance.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ezygo.attendance.domain.model.RiskBand
import com.ezygo.attendance.ui.theme.StatusNeutral
import com.ezygo.attendance.ui.theme.StatusNeutralDark
import com.ezygo.attendance.ui.theme.StatusRisk
import com.ezygo.attendance.ui.theme.StatusRiskDark
import com.ezygo.attendance.ui.theme.StatusSafe
import com.ezygo.attendance.ui.theme.StatusSafeDark
import com.ezygo.attendance.ui.theme.StatusWatch
import com.ezygo.attendance.ui.theme.StatusWatchDark
import com.ezygo.attendance.domain.model.FeedSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// ---- the size the launcher gave us ---------------------------------------

/**
 * How many launcher rows tall the widget is.
 *
 * The layouts are specified per row count, so the row count is what they branch on.
 * The boundaries sit between the declared responsive sizes rather than on them, so a
 * widget that lands slightly off its nominal size still resolves to the tier it was
 * designed for.
 */
private enum class Rows { ONE, TWO, MANY }

private fun rowsFor(height: Dp): Rows = when {
    height < 120.dp -> Rows.ONE
    height < 200.dp -> Rows.TWO
    else -> Rows.MANY
}

/**
 * How many launcher columns wide it is.
 *
 * Width only ever scales the type — it never changes what is shown. A wider widget
 * says the same things in a size that suits the room it was given.
 */
private enum class Span { TWO, THREE, FOUR, FIVE }

private fun spanFor(width: Dp): Span = when {
    width < 150.dp -> Span.TWO
    width < 210.dp -> Span.THREE
    width < 280.dp -> Span.FOUR
    else -> Span.FIVE
}

/** One type scale per width, so every size is deliberate rather than merely legible. */
private class Type(val span: Span) {
    /**
     * The percentage, which is the whole point of the widget and is sized like it.
     *
     * Larger than a headline usually is, and deliberately: at the smallest size it is
     * the only thing on the tile besides the risk count, so it has the room.
     */
    val headline: TextUnit = when (span) {
        Span.TWO -> 38.sp
        Span.THREE -> 40.sp
        Span.FOUR -> 44.sp
        Span.FIVE -> 48.sp
    }
    val title: TextUnit = when (span) {
        Span.TWO -> 11.sp
        Span.THREE -> 12.sp
        Span.FOUR -> 13.sp
        Span.FIVE -> 14.sp
    }
    val body: TextUnit = when (span) {
        Span.TWO -> 10.sp
        Span.THREE -> 11.sp
        Span.FOUR -> 12.sp
        Span.FIVE -> 13.sp
    }
    val caption: TextUnit = when (span) {
        Span.TWO -> 9.sp
        Span.THREE -> 9.sp
        Span.FOUR -> 10.sp
        Span.FIVE -> 11.sp
    }
    val refresh: Dp = when (span) {
        Span.TWO -> 22.dp
        Span.THREE -> 24.dp
        Span.FOUR -> 26.dp
        Span.FIVE -> 28.dp
    }

    /** What the fixed blocks cost, so a list can budget the space around them. */
    val summaryHeight: Dp = when (span) {
        Span.TWO -> 34.dp
        Span.THREE -> 38.dp
        Span.FOUR -> 44.dp
        Span.FIVE -> 50.dp
    }
    val footerHeight: Dp get() = refresh + 4.dp
}

// ---- root ----------------------------------------------------------------

/**
 * Every widget, whatever its type, is this one tree.
 *
 * The hierarchy is the same everywhere: the headline figure, then the risk summary,
 * then the detail, then the last-updated line and refresh control. Only the detail
 * changes with size, and the last two are never dropped at any size — a widget that
 * cannot tell you how stale it is, or be told to try again, is worse than no widget.
 */
@Composable
internal fun WidgetRoot(type: WidgetType, snapshot: WidgetSnapshot?, sources: Set<String>) {
    val size = LocalSize.current
    val span = spanFor(size.width)
    val rows = rowsFor(size.height)
    val scale = Type(span)
    val padding = if (span == Span.TWO) 8.dp else 10.dp
    val available = size.height - padding * 2

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(padding)
            .clickable(actionStartActivity(homeIntent(LocalContext.current))),
    ) {
        when {
            snapshot == null -> ErrorBody(scale)

            // Nothing is wrong yet — the app is signing in or has not finished its
            // first fetch. Anything more definite than this would be a guess, and the
            // guess it used to make was "signed out".
            snapshot.status == WidgetStatus.LOADING ->
                NoticeBody("Loading…", null, scale, null)

            // Each widget speaks for its own data. A calendar widget telling you to
            // sign in to see your *attendance* is reporting someone else's problem.
            snapshot.status == WidgetStatus.SIGNED_OUT ->
                NoticeBody(type.signedOutMessage, null, scale, null)

            // The calendar stands on its own data, so it renders even on an account
            // that has no attendance recorded yet.
            type == WidgetType.CALENDAR -> CalendarBody(snapshot, sources, scale)

            // Like the calendar, the timetable is its own data and does not need any
            // attendance to have been recorded before it can be drawn.
            type == WidgetType.TIMETABLE -> TimetableBody(snapshot, scale)

            !snapshot.hasData ->
                NoticeBody("No attendance data yet", "Tap refresh to load it", scale, snapshot)

            type == WidgetType.ATTENDANCE_STATUS -> StatusBody(snapshot, span, scale)

            else -> AnalysisBody(snapshot, available, rows, scale)
        }
    }
}

// ---- compact: attendance status ------------------------------------------

/**
 * The percentage, and as much as the width pays for.
 *
 * The layout does not change with width — the figure and its risk count are always the
 * left column, always the same size, always the thing your eye lands on. Width only
 * buys a second column beside them, and only ever adds to it:
 *
 * - **2 columns** — the rounded percentage and the risk count, and nothing else. This
 *   is the smallest the widget goes, and everything that would qualify the figure is
 *   dropped rather than shrunk to fit around it.
 * - **3 columns** — the classes behind the figure: attended out of total.
 * - **4 columns** — absent as well, and how far the figure sits from the target.
 * - **5 columns** — the same, with room to breathe.
 *
 * The footer is dropped at two columns for the same reason as everything else: there
 * is room for the number or for the furniture around it, not both. From three columns
 * on it returns with the refresh control.
 */
@Composable
private fun ColumnScope.StatusBody(snapshot: WidgetSnapshot, span: Span, scale: Type) {
    val tiny = span == Span.TWO

    Row(
        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // The figure and its verdict, stacked. The count sits directly under the
        // percentage rather than beside it, so the two read as one block whatever
        // else is on the tile.
        Column(
            modifier = if (tiny) GlanceModifier.defaultWeight() else GlanceModifier,
            horizontalAlignment = if (tiny) {
                Alignment.Horizontal.CenterHorizontally
            } else {
                Alignment.Horizontal.Start
            },
        ) {
            Text(
                text = averageLabel(snapshot, span),
                style = TextStyle(
                    color = bandColor(snapshot.band),
                    fontSize = scale.headline,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = riskLabel(snapshot),
                style = TextStyle(
                    color = shortageColor(snapshot),
                    fontSize = scale.body,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }

        if (tiny) return@Row

        Spacer(GlanceModifier.width(10.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = Alignment.Horizontal.End,
        ) {
            DetailLine("${snapshot.attended} of ${snapshot.totalClasses}", "attended", scale)
            if (span != Span.THREE) {
                DetailLine("${snapshot.absent}", "absent", scale)
                Text(
                    text = targetLabel(snapshot),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = scale.caption,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    // Two columns has no room for it and says so by leaving it out; the tile still
    // opens the app on a tap, which is where a stale reading gets refreshed.
    if (!tiny) Footer(snapshot.lastUpdatedEpochMs, scale)
}

/** A figure and the word for it, on one line, so the numbers stay scannable. */
@Composable
private fun DetailLine(value: String, label: String, scale: Type) {
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = scale.body,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = scale.caption,
            ),
            maxLines = 1,
        )
    }
}

/** How far above or below the target the average sits, which is the real question. */
private fun targetLabel(snapshot: WidgetSnapshot): String {
    val average = snapshot.averagePercent ?: return "Target ${snapshot.targetPercent}%"
    val delta = average - snapshot.targetPercent
    return when {
        delta >= 0.05 -> String.format(Locale.getDefault(), "+%.1f vs target", delta)
        delta <= -0.05 -> String.format(Locale.getDefault(), "%.1f vs target", delta)
        else -> "At target"
    }
}

/**
 * Whether anything needs dealing with, in as few characters as that can be said.
 *
 * Kept apart from [shortageLabel], which the large widget still uses: that one has the
 * width for a sentence and sits above a list that names the subjects anyway.
 */
private fun riskLabel(snapshot: WidgetSnapshot): String =
    if (snapshot.shortageCount > 0) "Risk · ${snapshot.shortageCount}" else "Safe"

// ---- large: attendance analysis ------------------------------------------

/**
 * The same summary at every height, over as much detail as the height pays for.
 *
 * One row tall there is only room to name the subjects actually in shortage; two
 * rows lists every subject with its percentage; three rows and up adds what each one
 * costs to hold or recover, which is the thing the numbers are for.
 */
@Composable
private fun ColumnScope.AnalysisBody(
    snapshot: WidgetSnapshot,
    available: Dp,
    rows: Rows,
    scale: Type,
) {
    Summary(snapshot, scale)

    val detailed = rows == Rows.MANY
    val listed = if (rows == Rows.ONE) snapshot.atRiskSubjects else snapshot.subjects

    if (listed.isEmpty()) {
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = if (rows == Rows.ONE) "No subject is in shortage" else "No subjects recorded",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scale.body),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
    } else {
        val rowHeight = if (detailed) 30.dp else 19.dp
        val capacity = ((available - scale.summaryHeight - scale.footerHeight) / rowHeight)
            .toInt()
            .coerceAtLeast(1)

        // The list takes the slack, and each row inside it takes an equal share of
        // that. Rows spread out instead of stacking at the top over a gap, which is
        // what turns extra height into a more readable list rather than dead space.
        Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            listed.take(capacity).forEach { subject ->
                CourseRow(
                    subject = subject,
                    scale = scale,
                    detailed = detailed,
                    target = snapshot.targetPercent,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }

    Footer(snapshot.lastUpdatedEpochMs, scale)
}

@Composable
private fun CourseRow(
    subject: WidgetSubject,
    scale: Type,
    detailed: Boolean,
    target: Int,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(actionStartActivity(subjectIntent(context, subject.courseId))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = subject.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scale.body),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = subject.percent?.let { "$it%" } ?: "--",
                style = TextStyle(
                    color = bandColor(subject.band),
                    fontSize = scale.body,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        if (detailed) {
            val advice = adviceFor(subject, target)
            if (advice != null) {
                Text(
                    text = advice,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = scale.caption,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** What this subject costs to hold, or to recover — the point of the percentage. */
private fun adviceFor(subject: WidgetSubject, target: Int): String? = when {
    subject.band == RiskBand.NO_DATA -> null
    subject.atRisk -> when (val needed = subject.classesNeeded) {
        null -> "Cannot reach $target% this semester"
        0 -> "Back at $target%"
        1 -> "Attend 1 more to reach $target%"
        else -> "Attend $needed more to reach $target%"
    }
    else -> when (val spare = subject.classesMissable) {
        null -> "Clear of $target%"
        0 -> "Cannot miss any class"
        1 -> "Can miss 1 class"
        else -> "Can miss $spare classes"
    }
}

// ---- large: calendar -----------------------------------------------------

/**
 * The next few dates, as an agenda.
 *
 * Modelled on the shape Google Calendar's widget uses, because that shape is right and
 * everyone already reads it: days are announced once as a heading, each entry gives its
 * time in a fixed column on the left, and a coloured stripe says which calendar it came
 * from. Nothing repeats the date on every row, which is what the old list did and what
 * left no width for the titles.
 *
 * A [LazyColumn] rather than a fixed handful of rows, so the list scrolls on the home
 * screen instead of stopping at whatever the height happened to fit.
 *
 * Dated by its own sync rather than the attendance refresh: the two come from different
 * servers and go stale independently, and quoting one for the other would make the
 * widget claim to be fresher than it is.
 */
@Composable
private fun ColumnScope.CalendarBody(
    snapshot: WidgetSnapshot,
    sources: Set<String>,
    scale: Type,
) {
    // The snapshot carries every calendar and task list; this widget keeps the ones it
    // was set up with. Filtering here rather than in the snapshot is what lets two
    // calendar widgets on one home screen show different things.
    val events = snapshot.events.filter { it.source.key in sources }
    if (events.isEmpty()) {
        NoticeBody(
            title = "Nothing coming up",
            subtitle = if (sources.isEmpty()) {
                "No calendars chosen for this widget"
            } else {
                "Your calendars appear here"
            },
            scale = scale,
            snapshot = snapshot,
            epochMs = snapshot.calendarSyncEpochMs,
        )
        return
    }

    // Flattened once here rather than grouped inside the list: a lazy list has no
    // "group" of its own, and a day heading is simply a row that is not an event.
    val rows = buildList {
        var lastDay: String? = null
        events.forEach { event ->
            if (event.dayLabel != lastDay) {
                add(AgendaRow.Day(event.dayLabel, event.isToday))
                lastDay = event.dayLabel
            }
            add(AgendaRow.Entry(event))
        }
    }

    LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
        items(rows.size) { index ->
            when (val row = rows[index]) {
                is AgendaRow.Day -> AgendaDayHeading(row, scale)
                is AgendaRow.Entry -> AgendaEntry(row.event, scale)
            }
        }
    }

    Footer(snapshot.calendarSyncEpochMs, scale)
}

/** A heading or an entry: the two kinds of line an agenda has. */
private sealed interface AgendaRow {
    data class Day(val label: String, val isToday: Boolean) : AgendaRow

    data class Entry(val event: WidgetEvent) : AgendaRow
}

@Composable
private fun AgendaDayHeading(day: AgendaRow.Day, scale: Type) {
    Text(
        text = day.label.uppercase(Locale.getDefault()),
        style = TextStyle(
            // Today is the one heading worth picking out; the rest are wayfinding.
            color = if (day.isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
            fontSize = scale.caption,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun AgendaEntry(event: WidgetEvent, scale: Type) {
    val context = LocalContext.current
    Row(
        // Opens this event in the app's calendar. Without it the row inherits the whole
        // widget's tap, which lands on the home screen and leaves the reader to find
        // again what they had just pointed at.
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(
                actionStartActivity(
                    // A task has no calendar entry for the event screen to find, so it
                    // opens the app rather than a row that does not exist.
                    if (event.isTask) homeIntent(context) else eventIntent(context, event.id),
                ),
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = event.timeLabel ?: "all day",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = scale.caption,
            ),
            maxLines = 1,
            modifier = GlanceModifier.width(AGENDA_TIME_COLUMN),
        )
        // The stripe, which is how a calendar widget says which calendar without
        // spending any width on saying it.
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(AGENDA_ROW_HEIGHT)
                .cornerRadius(2.dp)
                .background(event.source.stripeColor()),
            contentAlignment = Alignment.Center,
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = event.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = scale.body,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

/** The same three colours the calendar screen uses, so the two never disagree. */
@Composable
private fun FeedSource.stripeColor(): ColorProvider = when (this) {
    FeedSource.BATCH -> GlanceTheme.colors.primary
    FeedSource.ACADEMIC -> GlanceTheme.colors.secondary
    FeedSource.LOCAL -> GlanceTheme.colors.tertiary
    // The two task lists share the error colour rather than taking two more hues:
    // a deadline is a different kind of thing from an event, and the stripe is there
    // to say which kind at a glance, not to enumerate every feed.
    FeedSource.TASKS_OWN, FeedSource.TASKS_SYNCED -> GlanceTheme.colors.error
}

private val AGENDA_TIME_COLUMN = 42.dp
private val AGENDA_ROW_HEIGHT = 18.dp

// ---- large: the week as a grid -------------------------------------------

/**
 * The timetable, days down the left and periods across the top.
 *
 * Cells hold the short code the user gave a course, which is the only reason a grid
 * this size can say anything at all: a widget cell is a few characters wide, and a
 * course name is not. A course with no code falls back to its name, clipped.
 *
 * Every row is given equal weight rather than a fixed height, so the same grid fills
 * a three-row widget and a five-row one without a second layout.
 */
@Composable
private fun ColumnScope.TimetableBody(snapshot: WidgetSnapshot, scale: Type) {
    val timetable = snapshot.timetable
    if (timetable == null || timetable.isEmpty) {
        NoticeBody(
            title = "No timetable yet",
            subtitle = "Tap refresh to load this week's classes",
            scale = scale,
            snapshot = snapshot,
            epochMs = snapshot.timetableUpdatedEpochMs,
        )
        return
    }

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Spacer(GlanceModifier.width(DAY_COLUMN))
        timetable.periodNames.forEach { name ->
            Text(
                text = name.take(PERIOD_LABEL_CHARS),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = scale.caption,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
        timetable.days.forEach { day ->
            TimetableRow(day, scale, GlanceModifier.defaultWeight())
        }
    }

    Footer(snapshot.timetableUpdatedEpochMs, scale)
}

@Composable
private fun TimetableRow(day: WidgetTimetableDay, scale: Type, modifier: GlanceModifier) {
    // A marker in the margin, not a band across the row.
    //
    // The first attempt tinted the whole row. It did make today obvious, but it put a
    // second block of colour behind cells that are already colour-coded, so the row
    // fought the very content it was pointing at — worst on days that mix classes with
    // free periods, where the band showed through the gaps as stripes.
    //
    // So the day column carries the emphasis and the cells are left alone: a short
    // accent rule down the left edge, the label in the accent colour, and bold weight.
    // Three quiet signals in the margin read as clearly as one loud one across the row,
    // and neither of them touches a timetable cell.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.width(DAY_COLUMN).fillMaxHeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            // Drawn at both states rather than only for today, so every row keeps the
            // same day-column width and the grid does not shift by 3dp on the one row
            // that matters. Off days draw it in the background colour: present in the
            // layout, invisible on screen.
            Box(
                modifier = GlanceModifier
                    .width(TODAY_RULE)
                    .fillMaxHeight()
                    .cornerRadius(2.dp)
                    .background(
                        if (day.isToday) {
                            GlanceTheme.colors.primary
                        } else {
                            GlanceTheme.colors.widgetBackground
                        },
                    ),
                content = {},
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = day.label,
                style = TextStyle(
                    color = if (day.isToday) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = scale.caption,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        if (day.slots.isEmpty()) {
            Text(
                text = "—",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = scale.caption,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            return@Row
        }
        day.slots.forEach { slot ->
            TimetableCell(slot, scale, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun TimetableCell(slot: WidgetSlot, scale: Type, modifier: GlanceModifier) {
    val label = slot.label
    Box(
        modifier = modifier.fillMaxHeight().padding(horizontal = 1.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(4.dp)
                .background(
                    when {
                        label == null -> GlanceTheme.colors.widgetBackground
                        slot.cancelled -> GlanceTheme.colors.errorContainer
                        else -> GlanceTheme.colors.primaryContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // A cancelled class keeps its place and loses its claim on the day.
                // Glance has no strikethrough, so it is said in a word the cell can
                // hold rather than implied by a colour alone.
                text = label?.let { if (slot.cancelled) "off" else it } ?: "",
                style = TextStyle(
                    color = when {
                        slot.cancelled -> GlanceTheme.colors.onErrorContainer
                        else -> GlanceTheme.colors.onPrimaryContainer
                    },
                    fontSize = scale.caption,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Room for the today rule, a gap, and three letters of a weekday.
 *
 * Wider than it was because the rule and its gap now live inside this column rather
 * than beside it; the label itself keeps the space it had.
 */
private val DAY_COLUMN = 37.dp

/** The accent rule that marks today. Thin enough to read as a marker, not a block. */
private val TODAY_RULE = 3.dp

/** Period names run "Period 1"; the grid has room for the part that differs. */
private const val PERIOD_LABEL_CHARS = 2

/** The headline figure on the left, what it means on the right. */
@Composable
private fun Summary(snapshot: WidgetSnapshot, scale: Type) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = averageLabel(snapshot, scale.span),
            style = TextStyle(
                color = bandColor(snapshot.band),
                fontSize = scale.headline,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            Text(
                text = "Average attendance",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = scale.title,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = shortageLabel(snapshot),
                style = TextStyle(color = shortageColor(snapshot), fontSize = scale.body),
                maxLines = 1,
            )
        }
    }
}

/** Always rendered: the last-updated line and the refresh action are never dropped. */
@Composable
private fun Footer(epochMs: Long?, scale: Type) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = updatedLabel(epochMs, scale.span),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = scale.caption,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        RefreshButton(scale.refresh)
    }
}

/**
 * The "nothing to show" state, which still carries the footer.
 *
 * [snapshot] is null only before sign-in, when there is no timestamp to quote and
 * nothing a refresh could fetch.
 */
@Composable
private fun ColumnScope.NoticeBody(
    title: String,
    subtitle: String?,
    scale: Type,
    snapshot: WidgetSnapshot?,
    epochMs: Long? = snapshot?.lastUpdatedEpochMs,
) {
    Text(
        text = title,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = scale.title,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 2,
    )
    if (subtitle != null && scale.span != Span.TWO) {
        Text(
            text = subtitle,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scale.caption),
            maxLines = 2,
        )
    }
    Spacer(GlanceModifier.defaultWeight())
    if (snapshot != null) Footer(epochMs, scale)
}

/**
 * Shown when the cached data could not be read at all.
 *
 * The important part is the Refresh button: a widget that fails must offer a way
 * out of the failure, otherwise the only remedy is removing and re-adding it.
 */
@Composable
private fun ColumnScope.ErrorBody(scale: Type) {
    Text(
        text = "Couldn't load your data",
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = scale.title,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
    if (scale.span != Span.TWO) {
        Text(
            text = "Tap refresh to try again",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scale.caption),
            maxLines = 1,
        )
    }
    Spacer(GlanceModifier.defaultWeight())
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Spacer(GlanceModifier.defaultWeight())
        RefreshButton(scale.refresh)
    }
}

/** The only control on a widget that reaches the network, and only when tapped. */
@Composable
private fun RefreshButton(diameter: Dp) {
    Box(
        modifier = GlanceModifier
            .size(diameter)
            .cornerRadius(diameter / 2)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(actionRunCallback<RefreshWidgetAction>())
            .semantics { contentDescription = "Refresh" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "↻",
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = (diameter.value * 0.58f).sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

// ---- colours -------------------------------------------------------------

/**
 * Status colours are fixed rather than themed: the meaning of "in shortage" should
 * not shift with the launcher's wallpaper palette.
 *
 * Each pair is chosen for contrast against both the light and the dark widget
 * background — the darker tone on light, the lighter tone on dark — so the same
 * meaning survives whichever the launcher hands us.
 *
 * Taken from the app palette rather than re-typed. Glance needs day/night pairs
 * instead of a CompositionLocal, which is why the *shape* differs here — but the
 * eight hex values were previously written out a second time, so a change to the
 * app's risk colours would have left the widget quietly showing the old ones.
 */
private fun bandColor(band: RiskBand) = when (band) {
    RiskBand.SAFE -> ColorProvider(day = StatusSafe, night = StatusSafeDark)
    RiskBand.WATCH -> ColorProvider(day = StatusWatch, night = StatusWatchDark)
    RiskBand.AT_RISK -> ColorProvider(day = StatusRisk, night = StatusRiskDark)
    RiskBand.NO_DATA -> ColorProvider(day = StatusNeutral, night = StatusNeutralDark)
}

private fun shortageColor(snapshot: WidgetSnapshot) =
    if (snapshot.shortageCount > 0) bandColor(RiskBand.AT_RISK) else bandColor(RiskBand.SAFE)

// ---- labels --------------------------------------------------------------

private fun averageLabel(snapshot: WidgetSnapshot, span: Span): String {
    val value = snapshot.averagePercent ?: return "--"
    // A decimal reads as precise and costs two characters; the narrowest widget
    // drops it rather than let the headline number shrink to fit.
    return if (span == Span.TWO) {
        "${Math.round(value)}%"
    } else {
        String.format(Locale.getDefault(), "%.1f%%", value)
    }
}

/** Deliberately a count. Naming the subjects here is the course list's job. */
private fun shortageLabel(snapshot: WidgetSnapshot): String {
    val count = snapshot.shortageCount
    if (count == 0) return "All subjects on track"
    return "$count ${if (count == 1) "subject" else "subjects"} at risk"
}

/**
 * When the data was last refreshed, as a clock time rather than as an age.
 *
 * A widget cannot tick. Everything on it is baked into RemoteViews when it is drawn
 * and stays exactly that until something redraws it, so an age is only true for the
 * instant it is written: "2m ago" still read "2m ago" an hour later, while the app
 * had moved on to "1 hour ago". A clock time is true whenever it is read, which is
 * the only way the two can agree without waking the device to poll.
 */
private fun updatedLabel(epochMs: Long?, span: Span): String {
    if (epochMs == null) return if (span == Span.TWO) "--" else "Never updated"

    val moment = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val day = moment.toLocalDate()
    val today = LocalDate.now()
    val clock = "%02d:%02d".format(moment.hour, moment.minute)

    // Deliberately not localised, for the same reason as the calendar's day labels:
    // this is a handful of characters in a space a long localised month would be cut
    // off in anyway.
    val stamp = when (day) {
        today -> clock
        today.minusDays(1) -> "yesterday $clock"
        else -> "${day.dayOfMonth} " +
            day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }

    return when (span) {
        Span.TWO -> if (day == today) clock else stamp
        Span.THREE -> "Updated $stamp"
        else -> "Last updated $stamp"
    }
}
