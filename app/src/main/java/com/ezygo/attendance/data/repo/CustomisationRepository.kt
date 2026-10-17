package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.dao.HiddenKeys
import com.ezygo.attendance.data.local.dao.courseShortCodeKey
import com.ezygo.attendance.data.local.dao.savedCourseKey
import com.ezygo.attendance.data.local.entity.CourseShortCodeEntity
import com.ezygo.attendance.data.local.entity.HiddenFromHomeEntity
import com.ezygo.attendance.data.local.entity.SavedCourseEntity
import com.ezygo.attendance.domain.model.CourseShortCode
import com.ezygo.attendance.domain.model.SavedCourse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The short codes the user gave their courses.
 *
 * Kept apart from the timetable cache on purpose: the cache is refetched and replaced
 * whole on every refresh, while these are the user's own work and must survive that,
 * a semester switch, and a switch back.
 */
@Singleton
class CourseShortCodeRepository @Inject constructor(
    private val database: AppDatabase,
) {
    /** Keyed by course key, which is what a timetable row is matched against. */
    val codes: Flow<Map<String, CourseShortCode>> =
        database.courseShortCodeDao().all().map { rows -> rows.associate { it.courseKey to it.toDomain() } }

    suspend fun codesOnce(): Map<String, CourseShortCode> =
        database.courseShortCodeDao().allOnce().associate { it.courseKey to it.toDomain() }

    /**
     * Sets or clears one course's short code.
     *
     * A blank code deletes the row rather than storing an empty string, so "no code"
     * has exactly one representation and the timetable falls back to the full name.
     *
     * A code already spoken for by another course is refused. Two courses sharing one
     * code would make the grid ambiguous in exactly the place it is meant to be read
     * quickly — a cell saying "OS" that could be either of two subjects is worse than
     * a cell saying the whole name. Compared without regard to case, because "os" and
     * "OS" are the same code to anyone reading the grid.
     *
     * Enforced here rather than by a unique index, because this is the only writer and
     * an index would have to be introduced over data that may already hold duplicates
     * — which would mean deleting some of the user's mappings to install a guarantee.
     */
    suspend fun set(
        courseCode: String?,
        courseId: Int?,
        courseName: String,
        shortCode: String,
    ): ShortCodeResult {
        val key = courseShortCodeKey(courseCode, courseId) ?: return ShortCodeResult.NoKey
        val clean = shortCode.trim().take(MAX_LENGTH)
        if (clean.isEmpty()) {
            database.courseShortCodeDao().delete(key)
            return ShortCodeResult.Cleared
        }
        val clash = database.courseShortCodeDao().allOnce().firstOrNull {
            it.courseKey != key && it.shortCode.equals(clean, ignoreCase = true)
        }
        if (clash != null) return ShortCodeResult.Taken(clash.courseName)

        database.courseShortCodeDao().upsert(
            CourseShortCodeEntity(
                courseKey = key,
                courseName = courseName,
                shortCode = clean,
                updatedEpochMs = System.currentTimeMillis(),
            ),
        )
        return ShortCodeResult.Saved
    }

    suspend fun clear() = database.courseShortCodeDao().clearAll()

    private fun CourseShortCodeEntity.toDomain() =
        CourseShortCode(courseKey = courseKey, courseName = courseName, shortCode = shortCode)

    companion object {
        /** Long enough for "MATH", short enough to fit a grid cell. */
        const val MAX_LENGTH = 8
    }
}

/** What became of an attempt to set a short code. */
sealed interface ShortCodeResult {
    data object Saved : ShortCodeResult
    data object Cleared : ShortCodeResult

    /** Another course already answers to this code, and is named so it can be found. */
    data class Taken(val courseName: String) : ShortCodeResult

    /** The course has neither a code nor an id, so nothing can be filed against it. */
    data object NoKey : ShortCodeResult
}

/**
 * What the user has asked the home screen to stop showing.
 *
 * Hiding is a home-screen decision and nothing more: the item stays in its own app,
 * still syncs, and still raises its reminder. Reversing it is a tap on the same screen,
 * which is why nothing here is destructive — and why each entry keeps its own name, so
 * it can be restored on its own rather than only as part of the whole set.
 */
@Singleton
class HomeVisibilityRepository @Inject constructor(
    private val database: AppDatabase,
) {
    val hidden: Flow<List<HiddenItem>> =
        database.hiddenFromHomeDao().all().map { rows ->
            rows.map {
                HiddenItem(
                    key = it.id,
                    // Rows hidden before the title column existed have none. Naming
                    // them by kind keeps them identifiable enough to restore.
                    title = it.title.ifBlank {
                        if (HiddenKeys.isEvent(it.id)) "Hidden event" else "Hidden task"
                    },
                    isEvent = HiddenKeys.isEvent(it.id),
                )
            }
        }

    val hiddenIds: Flow<Set<String>> =
        database.hiddenFromHomeDao().all().map { rows -> rows.map { it.id }.toSet() }

    suspend fun hideEvent(id: String, title: String) = hide(HiddenKeys.event(id), title)

    /** [pendingId] is already prefixed — see PendingItem, which carries its own kind. */
    suspend fun hidePending(pendingId: String, title: String) = hide(pendingId, title)

    suspend fun show(key: String) = database.hiddenFromHomeDao().show(key)

    suspend fun showAll() = database.hiddenFromHomeDao().clearAll()

    suspend fun clear() = database.hiddenFromHomeDao().clearAll()

    private suspend fun hide(key: String, title: String) =
        database.hiddenFromHomeDao().hide(
            HiddenFromHomeEntity(
                id = key,
                title = title.trim(),
                hiddenEpochMs = System.currentTimeMillis(),
            ),
        )
}

/** One entry of the home screen's hidden list. */
data class HiddenItem(
    val key: String,
    val title: String,
    val isEvent: Boolean,
)

/**
 * The courses the user has described.
 *
 * Written whenever a class is placed on the timetable, which is the only screen that
 * asks for a course's details. Nothing here is ever deleted by removing a class: the
 * point of the list is that a course described once stays described.
 */
@Singleton
class SavedCourseRepository @Inject constructor(
    private val database: AppDatabase,
) {
    val courses: Flow<List<SavedCourse>> =
        database.savedCourseDao().all().map { rows ->
            rows.map {
                SavedCourse(
                    key = it.courseKey,
                    name = it.name,
                    code = it.code,
                    shortCode = it.shortCode,
                    teacher = it.teacher,
                    stored = true,
                )
            }
        }

    suspend fun remember(name: String, code: String?, shortCode: String?, teacher: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        database.savedCourseDao().upsert(
            SavedCourseEntity(
                courseKey = savedCourseKey(code, clean),
                name = clean,
                code = code?.trim()?.takeIf { it.isNotEmpty() },
                shortCode = shortCode?.trim()?.takeIf { it.isNotEmpty() },
                teacher = teacher?.trim()?.takeIf { it.isNotEmpty() },
                updatedEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun forget(key: String) = database.savedCourseDao().delete(key)

    suspend fun clear() = database.savedCourseDao().clearAll()
}
