package com.ezygo.attendance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A short code the user gave a course, so the timetable grid can name it in a cell.
 *
 * Deliberately **not** scoped by period, unlike the timetable cache it feeds. A course
 * keeps its code across semesters, so keying on the course rather than on the semester
 * is what makes a short code come back on its own when the student switches away to
 * another semester and later switches back. The course name is stored beside it so the
 * mapping screen can still say what "FDS" was, even for a semester not currently loaded.
 */
@Entity(tableName = "course_short_codes")
data class CourseShortCodeEntity(
    /** The portal's course code where there is one, else `id:<courseId>`. */
    @PrimaryKey val courseKey: String,
    val courseName: String,
    val shortCode: String,
    val updatedEpochMs: Long,
)

/**
 * A class the user placed themselves, on top of what the portal published.
 *
 * Keyed by day of the week rather than by date: an override is a standing change to
 * the weekly timetable, so it applies to that period on that weekday every week, which
 * is what was asked for. It is scoped by period because a semester's timetable is its
 * own — carrying one semester's overrides into the next would put last term's classes
 * on this term's grid.
 *
 * The portal's own row for the period is replaced, not merged: an override exists
 * precisely because the published one is wrong.
 */
@Entity(tableName = "timetable_overrides", primaryKeys = ["periodKey", "dayOfWeek", "sessionId"])
data class TimetableOverrideEntity(
    val periodKey: String,
    /** `java.time.DayOfWeek.value`: Monday is 1, Sunday is 7. */
    val dayOfWeek: Int,
    val sessionId: Int,
    /** Set when the override names a course the portal knows; null for a typed one. */
    val courseId: Int?,
    val courseName: String,
    val courseCode: String?,
    /**
     * The short code for this class, if the user gave it one.
     *
     * Held on the override rather than in the shared course-code table, because an
     * override often names a course the portal has never heard of: there may be no
     * course code to file a mapping under, and a made-up key would then outlive the
     * override that invented it.
     */
    val shortCode: String?,
    val instructor: String?,
    val deliveryMethod: String?,
    val classType: String?,
)

/**
 * One thing the user asked the home screen to stop showing.
 *
 * A table of its own rather than a column on the event or task, because both of those
 * are replaced wholesale by their sync: a column would be wiped every time the feed
 * refreshed, and the item would silently come back. The id is prefixed by kind
 * (`event:`, `task:`) so one table covers both without them colliding.
 *
 * [title] is stored rather than looked up. The hidden list has to name what it holds
 * so each entry can be restored on its own, and joining back to the task or event
 * would leave anything since deleted upstream as a blank row that could not be
 * identified — which is precisely the row a reader would most want to get rid of.
 */
@Entity(tableName = "hidden_from_home")
data class HiddenFromHomeEntity(
    @PrimaryKey val id: String,
    /**
     * The defaults are declared here as well as in the migration that added the
     * columns. Room compares them, and a column added with a default the entity does
     * not also declare fails validation on the next open.
     */
    @ColumnInfo(defaultValue = "") val title: String,
    @ColumnInfo(defaultValue = "0") val hiddenEpochMs: Long,
)

/**
 * One course as printed on a grade card, with what it was worth and what it earned.
 *
 * Keyed by semester and course code so re-reading a card rewrites its rows rather than
 * doubling them, exactly as [SemesterResultEntity] does for the semester as a whole.
 */
@Entity(tableName = "semester_courses", primaryKeys = ["semester", "code"])
data class SemesterCourseEntity(
    val semester: Int,
    val code: String,
    val name: String,
    /** Null when the card printed a course without a readable credit value. */
    val credits: Double?,
    /** The letter grade as printed. Null when the line had none this reader could find. */
    val grade: String?,
)

/**
 * A course the user has described once, kept so they never describe it twice.
 *
 * Filled from every class they place on the timetable, so a custom course invented for
 * one period is offered as a choice the next time one is added. Deliberately outlives
 * the override that created it: deleting a Tuesday class should not make the course
 * itself unknown to the app again.
 *
 * Not scoped by semester. A course is a course; which term it was taken in is the
 * timetable's business, not this list's.
 */
@Entity(tableName = "saved_courses")
data class SavedCourseEntity(
    /** The course code where there is one, else the name folded to lower case. */
    @PrimaryKey val courseKey: String,
    val name: String,
    val code: String?,
    val shortCode: String?,
    val teacher: String?,
    val updatedEpochMs: Long,
)
