package com.ezygo.attendance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ezygo.attendance.data.local.dao.AttendanceDao
import com.ezygo.attendance.data.local.dao.CalendarDao
import com.ezygo.attendance.data.local.dao.CourseShortCodeDao
import com.ezygo.attendance.data.local.dao.HiddenFromHomeDao
import com.ezygo.attendance.data.local.dao.NoteDao
import com.ezygo.attendance.data.local.dao.SamarthDao
import com.ezygo.attendance.data.local.dao.SavedCourseDao
import com.ezygo.attendance.data.local.dao.SemesterCourseDao
import com.ezygo.attendance.data.local.dao.SemesterResultDao
import com.ezygo.attendance.data.local.dao.TaskDao
import com.ezygo.attendance.data.local.dao.ProfileDao
import com.ezygo.attendance.data.local.dao.TimetableDao
import com.ezygo.attendance.data.local.dao.TimetableOverrideDao
import com.ezygo.attendance.data.local.entity.AttendanceRecordEntity
import com.ezygo.attendance.data.local.entity.CalendarEventEntity
import com.ezygo.attendance.data.local.entity.CalendarSyncEntity
import com.ezygo.attendance.data.local.entity.CourseEntity
import com.ezygo.attendance.data.local.entity.CourseShortCodeEntity
import com.ezygo.attendance.data.local.entity.HiddenFromHomeEntity
import com.ezygo.attendance.data.local.entity.EnrolledCourseEntity
import com.ezygo.attendance.data.local.entity.NoteEntity
import com.ezygo.attendance.data.local.entity.PeriodEntity
import com.ezygo.attendance.data.local.entity.ProfileEntity
import com.ezygo.attendance.data.local.entity.SamarthProfileEntity
import com.ezygo.attendance.data.local.entity.SavedCourseEntity
import com.ezygo.attendance.data.local.entity.ClassCountsMetaEntity
import com.ezygo.attendance.data.local.entity.SessionCountEntity
import com.ezygo.attendance.data.local.entity.SemesterCourseEntity
import com.ezygo.attendance.data.local.entity.SemesterResultEntity
import com.ezygo.attendance.data.local.entity.TaskEntity
import com.ezygo.attendance.data.local.entity.ScheduleEntity
import com.ezygo.attendance.data.local.entity.SessionEntity
import com.ezygo.attendance.data.local.entity.TimetableMetaEntity
import com.ezygo.attendance.data.local.entity.TimetableOverrideEntity
import com.ezygo.attendance.data.local.entity.TimetableSessionEntity

@Database(
    entities = [
        PeriodEntity::class,
        CourseEntity::class,
        EnrolledCourseEntity::class,
        SessionEntity::class,
        AttendanceRecordEntity::class,
        ProfileEntity::class,
        TimetableMetaEntity::class,
        TimetableSessionEntity::class,
        ScheduleEntity::class,
        CalendarEventEntity::class,
        CalendarSyncEntity::class,
        NoteEntity::class,
        SamarthProfileEntity::class,
        TaskEntity::class,
        SemesterResultEntity::class,
        CourseShortCodeEntity::class,
        TimetableOverrideEntity::class,
        HiddenFromHomeEntity::class,
        SemesterCourseEntity::class,
        SavedCourseEntity::class,
        SessionCountEntity::class,
        ClassCountsMetaEntity::class,
    ],
    // 2: adds the timetable cache. Everything there is a rebuildable cache, so the
    // destructive fallback simply refetches rather than needing a migration.
    //
    // 3: adds the calendar. Note this breaks the assumption above — a local event
    // is the user's own data and cannot be refetched, so the destructive fallback
    // will discard it. That is survivable only because 3 is the version that
    // introduces the table: there is nothing to lose on the way in. Any later
    // schema change to calendar_events must ship a real migration.
    // 4: adds notes. Shipped with MIGRATION_3_4 rather than the destructive
    // fallback, because by now the database holds two things nothing can refetch —
    // local calendar events and the notes themselves.
    //
    // 5: adds the cached Samarth profile, with MIGRATION_4_5 for the same reason.
    //
    // 6: records what each note is for. An added column with a default, so no
    // note has to be rewritten and none is lost.
    //
    // 7: adds synced tasks and the per-semester results behind the CGPA.
    //
    // 8: adds the user's own customisations - course short codes, timetable
    // overrides and what they have hidden from the home screen - plus the
    // per-course grade history. With MIGRATION_7_8; the first three are the
    // user's own work and nothing can fetch them back.
    //
    // 9: makes a task a row of its own instead of JSON inside a note, and
    // reduces a note to its text. MIGRATION_8_9 drops and rebuilds those two
    // tables and nothing else - the only destructive step since 3, taken on an
    // explicit instruction that the existing notes and tasks did not matter.
    //
    // 10: the hidden-from-home list remembers what it hid, so each entry can be
    // restored on its own. Two added columns with defaults, so nothing already
    // hidden is disturbed.
    //
    // 11: a timetable override carries its own short code, for a class the
    // portal has never heard of and so has no course code to file one under.
    //
    // 12: the courses the user has described, kept so a custom one invented for
    // a single period is offered again the next time a class is added.
    // 13: how many of the cohort were present and absent in each period, from the
    // monitoring report, plus the cohort's own start date so that report can be
    // asked for from day one.
    version = 14,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao
    abstract fun profileDao(): ProfileDao
    abstract fun timetableDao(): TimetableDao
    abstract fun calendarDao(): CalendarDao
    abstract fun noteDao(): NoteDao
    abstract fun samarthDao(): SamarthDao
    abstract fun taskDao(): TaskDao
    abstract fun semesterResultDao(): SemesterResultDao
    abstract fun courseShortCodeDao(): CourseShortCodeDao
    abstract fun timetableOverrideDao(): TimetableOverrideDao
    abstract fun hiddenFromHomeDao(): HiddenFromHomeDao
    abstract fun semesterCourseDao(): SemesterCourseDao
    abstract fun savedCourseDao(): SavedCourseDao

    companion object {
        const val NAME = "ezygo_attendance.db"
    }
}
