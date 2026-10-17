package com.ezygo.attendance.data.repo

/**
 * What happened when something was asked to be deleted.
 *
 * Both stores already refused to delete synced rows — a task or an event that came
 * from a feed would simply come back on the next sync, so removing it locally would be
 * a lie that lasted until the next refresh. But they refused by returning quietly,
 * which from the user's side is indistinguishable from a delete that worked and then
 * undid itself. This is the refusal made visible.
 */
sealed interface DeleteOutcome {

    data object Deleted : DeleteOutcome

    /** Refused, with the reason to put in front of the user. */
    data class Refused(val message: String) : DeleteOutcome

    companion object {
        fun syncedTask() = Refused(
            "This task comes from your synced tasks calendar, so it cannot be deleted " +
                "here — it would return on the next sync. Remove it where it was " +
                "created, then sync again.",
        )

        fun syncedEvent() = Refused(
            "This event comes from a synced calendar, so it cannot be deleted here — " +
                "it would return on the next sync. Remove it in the calendar it came " +
                "from, then sync again.",
        )
    }
}
