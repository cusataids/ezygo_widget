package com.ezygo.attendance.widget

/**
 * The two widget footprints offered in the launcher's picker.
 *
 * They are separate providers rather than one resizable widget because the
 * declared size is what the launcher uses to place a widget on drop. A single
 * provider would have to declare one default cell size for both a two-cell "next
 * class" strip and a four-by-three timetable, and one of them would always land
 * at the wrong size and need dragging.
 */
enum class WidgetFamily {
    COMPACT,
    LARGE,
    ;

    // Both derived rather than held as constructor arguments: an enum constant that
    // named a WidgetType would make the two enums initialise each other, and one of
    // them would see the other half-built. Getters are evaluated on use instead.
    val types: List<WidgetType> get() = WidgetType.entries.filter { it.family == this }

    val defaultType: WidgetType get() = types.first()
}

/**
 * What a placed widget shows. Chosen by the user when the widget is added, then
 * fixed for that widget — one home screen can carry several, each set differently.
 *
 * [recommendedSize] is shown in the chooser rather than enforced: the launcher
 * decides the real size, and every type still adapts to whatever it is given.
 */
enum class WidgetType(
    val family: WidgetFamily,
    val title: String,
    val description: String,
    val recommendedSize: String,
) {
    ATTENDANCE_STATUS(
        family = WidgetFamily.COMPACT,
        title = "Attendance status",
        description = "Your average attendance and how many subjects are in shortage.",
        recommendedSize = "3x1",
    ),
    ANALYSIS(
        family = WidgetFamily.LARGE,
        title = "Attendance analysis",
        description = "Per-subject percentages, and what it takes to hold your target.",
        recommendedSize = "3x2",
    ),
    CALENDAR(
        family = WidgetFamily.LARGE,
        title = "Calendar",
        description = "The events coming up next, from your calendar and your reminders.",
        recommendedSize = "3x2",
    ),
    TIMETABLE(
        family = WidgetFamily.LARGE,
        title = "Timetable grid",
        description = "The week as a grid, with your course short codes in the cells.",
        recommendedSize = "4x3",
    ),
    ;

    /**
     * What this widget says when there is genuinely no session.
     *
     * Per type, because a widget should only ever report on the data it draws. Every
     * widget used to say "Sign in to see your attendance" — including the calendar and
     * the timetable, which do not show attendance at all and left the user looking for
     * an attendance problem that was not the one they had.
     */
    val signedOutMessage: String
        get() = when (this) {
            ATTENDANCE_STATUS, ANALYSIS -> "Sign in to see your attendance"
            CALENDAR -> "Sign in to see your calendar"
            TIMETABLE -> "Sign in to see your timetable"
        }

    companion object {
        /**
         * Resolves a stored choice, falling back to the family's default rather than
         * failing. A widget whose stored type cannot be read must still draw
         * something — the whole point of this rewrite is that no widget is ever
         * left with nothing on it.
         */
        fun fromKey(key: String?, family: WidgetFamily): WidgetType =
            entries.firstOrNull { it.name == key && it.family == family } ?: family.defaultType
    }
}
