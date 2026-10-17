package com.ezygo.attendance.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the notes table.
 *
 * A real migration rather than the destructive fallback, because by version 3 the
 * database already holds two things the app cannot get back: the user's own calendar
 * events, and now their notes. Dropping every table to add one would take both with
 * it. This only creates, so there is nothing for it to lose.
 *
 * The statements have to match what Room generates for [NoteEntity] exactly — same
 * columns, same affinities, same nullability, same index name — or Room rejects the
 * result on the next open.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`todos` TEXT NOT NULL, " +
                "`createdEpochMs` INTEGER NOT NULL, " +
                "`updatedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notes_updatedEpochMs` " +
                "ON `notes` (`updatedEpochMs`)",
        )
    }
}

/**
 * Adds the cached Samarth profile.
 *
 * Creates only, for the same reason as [MIGRATION_3_4]: by now the database carries
 * the user's calendar events and notes, and the destructive fallback would take both.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `samarth_profile` (" +
                "`id` INTEGER NOT NULL, " +
                "`fullName` TEXT NOT NULL, " +
                "`dateOfBirth` TEXT NOT NULL, " +
                "`email` TEXT NOT NULL, " +
                "`phone` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`addressLine` TEXT NOT NULL, " +
                "`district` TEXT NOT NULL, " +
                "`statePincode` TEXT NOT NULL, " +
                "`enrolmentNumber` TEXT NOT NULL, " +
                "`abcId` TEXT NOT NULL, " +
                "`department` TEXT NOT NULL, " +
                "`fetchedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * Records what each note is for.
 *
 * Existing notes become BOTH, which is what they effectively were: an editor that
 * always offered a body and a checklist. Nothing is lost and nothing has to be
 * re-entered. (The kind column, and the checklist behind it, are gone again at 9.)
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `notes` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'BOTH'",
        )
    }
}

/**
 * Adds synced tasks and the per-semester results the CGPA is averaged from.
 *
 * Creates only, like every migration since 3.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`details` TEXT, " +
                "`dueEpochMs` INTEGER NOT NULL, " +
                "`isAllDay` INTEGER NOT NULL, " +
                "`done` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tasks_dueEpochMs` ON `tasks` (`dueEpochMs`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `semester_results` (" +
                "`semester` INTEGER NOT NULL, " +
                "`sgpa` REAL NOT NULL, " +
                "`credits` INTEGER, " +
                "`parsedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`semester`))",
        )
    }
}

/**
 * Adds the user's own customisations, and the per-course grade history.
 *
 * Creates only, like every migration since 3. Three of these four tables hold things
 * nothing can refetch - a short code the student invented, a class they placed by hand,
 * an item they asked the home screen to stop showing - so the destructive fallback
 * would be a real loss from here on.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `course_short_codes` (" +
                "`courseKey` TEXT NOT NULL, " +
                "`courseName` TEXT NOT NULL, " +
                "`shortCode` TEXT NOT NULL, " +
                "`updatedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`courseKey`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `timetable_overrides` (" +
                "`periodKey` TEXT NOT NULL, " +
                "`dayOfWeek` INTEGER NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`courseId` INTEGER, " +
                "`courseName` TEXT NOT NULL, " +
                "`courseCode` TEXT, " +
                "`instructor` TEXT, " +
                "`deliveryMethod` TEXT, " +
                "`classType` TEXT, " +
                "PRIMARY KEY(`periodKey`, `dayOfWeek`, `sessionId`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hidden_from_home` (" +
                "`id` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `semester_courses` (" +
                "`semester` INTEGER NOT NULL, " +
                "`code` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`credits` REAL, " +
                "`grade` TEXT, " +
                "PRIMARY KEY(`semester`, `code`))",
        )
    }
}

/**
 * Makes a task a real row, and a note only text.
 *
 * The one destructive migration since 3, and deliberately so. A task used to be a line
 * of JSON inside a note: it had no deadline of its own, no editor, and no existence
 * until a note had been written to hold it. Carrying those lines across would have
 * meant keeping both shapes alive indefinitely to preserve a handful of rows the user
 * explicitly said not to preserve. Both tables are dropped and rebuilt instead.
 *
 * Nothing else is touched. Local calendar events, short codes, timetable overrides and
 * the grade history all survive, which is why this drops two named tables rather than
 * falling back to dropping every one of them.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `tasks`")
        connection.execSQL("DROP TABLE IF EXISTS `notes`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (" +
                "`id` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`detail` TEXT, " +
                "`dueEpochMs` INTEGER, " +
                "`isAllDay` INTEGER NOT NULL, " +
                "`done` INTEGER NOT NULL, " +
                "`completedEpochMs` INTEGER, " +
                "`createdEpochMs` INTEGER NOT NULL, " +
                "`updatedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tasks_dueEpochMs` ON `tasks` (`dueEpochMs`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tasks_source` ON `tasks` (`source`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`createdEpochMs` INTEGER NOT NULL, " +
                "`updatedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notes_updatedEpochMs` " +
                "ON `notes` (`updatedEpochMs`)",
        )
    }
}

/**
 * Lets the hidden list say what it is holding.
 *
 * Two added columns with defaults rather than a rebuilt table, so anything already
 * hidden stays hidden. Those rows carry an empty title and a zero timestamp; the
 * screen falls back to naming them by kind, and they restore exactly like the rest.
 */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `hidden_from_home` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''",
        )
        connection.execSQL(
            "ALTER TABLE `hidden_from_home` ADD COLUMN `hiddenEpochMs` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Lets a placed class carry its own short code.
 *
 * A nullable added column, so every override already placed keeps working and simply
 * has none — the grid falls back to its course name exactly as before.
 */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `timetable_overrides` ADD COLUMN `shortCode` TEXT")
    }
}

/**
 * Remembers the courses the user describes, so they describe each one once.
 *
 * Create-only: there is nothing to carry across, and the list fills itself from the
 * next class placed on the timetable.
 */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_courses` (" +
                "`courseKey` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`code` TEXT, " +
                "`shortCode` TEXT, " +
                "`teacher` TEXT, " +
                "`updatedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`courseKey`))",
        )
    }
}

/**
 * 13: the cohort's present/absent counts per period, and the cohort's own start date.
 *
 * The counts table is a cache of a report, so it is created empty and filled by the
 * next refresh rather than back-filled here. subgroupStartDate is added nullable with
 * no default for the same reason — the next timetable refresh writes it, and until
 * then the monitoring report simply is not asked for.
 */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_attendance_counts` (" +
                "`periodKey` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`present` INTEGER NOT NULL, " +
                "`absent` INTEGER NOT NULL, " +
                "PRIMARY KEY(`periodKey`, `date`, `sessionId`))",
        )
        connection.execSQL("ALTER TABLE `timetable_meta` ADD COLUMN `subgroupStartDate` TEXT")
    }
}

/**
 * 14: when the cohort report was last read.
 *
 * Created empty. A missing row reads as "never fetched", which is exactly right for
 * an upgrade: the counts already cached were gathered by the old always-on refresh
 * and nobody knows when, so the first visit to the class screens re-reads them once
 * and the table is honest from then on.
 */
internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `class_counts_meta` (" +
                "`periodKey` TEXT NOT NULL, " +
                "`fetchedEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`periodKey`))",
        )
    }
}
