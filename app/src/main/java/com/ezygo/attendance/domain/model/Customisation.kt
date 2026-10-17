package com.ezygo.attendance.domain.model

import java.time.DayOfWeek

/**
 * A course and the short code the user gave it.
 *
 * [courseKey] is the portal's course code where there is one, so the mapping follows
 * the course rather than the semester it happened to be taken in — which is what makes
 * it come back on its own after a switch away and back.
 */
data class CourseShortCode(
    val courseKey: String,
    val courseName: String,
    val shortCode: String,
)

/** A class the user placed on the grid themselves, standing every week. */
data class TimetableOverride(
    val dayOfWeek: DayOfWeek,
    val sessionId: Int,
    val courseId: Int?,
    val courseName: String,
    val courseCode: String?,
    /** What the grid cell says, when the user gave the class a short code. */
    val shortCode: String?,
    val instructor: String?,
    val deliveryMethod: String?,
    val classType: String?,
)

/** One course as printed on a grade card. */
data class SemesterCourse(
    val semester: Int,
    val code: String,
    val name: String,
    val credits: Double?,
    val grade: String?,
)

/**
 * One semester's courses, with what they were worth.
 *
 * [creditsEarned] counts only what the card actually printed a credit value for, so a
 * semester with one unreadable line understates rather than invents. The screen says
 * how many lines it read, which is what lets the difference be seen.
 */
data class SemesterCourses(
    val semester: Int,
    val courses: List<SemesterCourse>,
) {
    val creditsEarned: Double get() = courses.sumOf { it.credits ?: 0.0 }
}

/**
 * A course the app has been told about once.
 *
 * Everything a placed class needs, so choosing one from the list fills the whole form
 * rather than only its name.
 */
data class SavedCourse(
    val key: String,
    val name: String,
    val code: String?,
    val shortCode: String?,
    val teacher: String?,
    /**
     * True for one the user described themselves, and so one they can undescribe.
     *
     * A course the portal published is not theirs to remove from the list — it would
     * simply come back with the next refresh, so offering the cross would be a lie.
     */
    val stored: Boolean = false,
)
