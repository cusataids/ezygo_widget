package com.ezygo.attendance.domain.model

/**
 * Where a dated thing came from, as far as the user's settings are concerned.
 *
 * One list for both questions the settings ask — what may raise a notification, and
 * what may appear on the home screen — because they are the same set of sources and a
 * second enum would let the two drift apart.
 *
 * The keys are stored, so they never change. "google" is still the batch calendar's
 * key and "tasks" still the synced feed's, both from earlier versions.
 */
enum class FeedSource(val key: String, val label: String, val description: String) {
    BATCH("google", "IMSc 24-29 Batch Calendar", "The batch's shared calendar"),
    ACADEMIC("academic", "Academic calendar", "The institution's published dates"),
    LOCAL("local", "On this device", "Events you added yourself"),
    TASKS_SYNCED("tasks", "Synced tasks", "Deadlines from the tasks calendar"),
    TASKS_OWN("tasks_own", "Your tasks", "Tasks you wrote yourself"),
    ;

    /** True for the three that produce calendar events rather than tasks. */
    val isCalendar: Boolean get() = this == BATCH || this == ACADEMIC || this == LOCAL

    companion object {
        fun fromKey(key: String?): FeedSource? = entries.firstOrNull { it.key == key }

        /**
         * What notifies by default: everything but the academic calendar, which is a
         * long reference list of institutional dates and would notify constantly.
         */
        val DEFAULT_NOTIFY: Set<String> =
            entries.filterNot { it == ACADEMIC }.map { it.key }.toSet()

        /**
         * What the home screen shows by default.
         *
         * The academic calendar is off for the same reason it does not notify: it is
         * reference material, and it would bury the handful of dates a student actually
         * needs at a glance. It is a choice now rather than a rule, which is the point —
         * it used to be excluded in code with no way to ask for it.
         */
        val DEFAULT_HOME: Set<String> =
            entries.filterNot { it == ACADEMIC }.map { it.key }.toSet()
    }
}
