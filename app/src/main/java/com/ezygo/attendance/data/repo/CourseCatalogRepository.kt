package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.remote.EzygoApi
import com.ezygo.attendance.data.remote.dto.CourseMemberDto
import com.ezygo.attendance.data.remote.dto.CourseWithUsersDto
import com.ezygo.attendance.data.remote.dto.asIntOrNull
import com.ezygo.attendance.domain.model.Classmate
import com.ezygo.attendance.domain.model.CourseInfo
import com.ezygo.attendance.domain.model.CourseModuleInfo
import com.ezygo.attendance.domain.model.CourseOutcomeInfo
import com.ezygo.attendance.domain.model.CoursePropertyInfo
import com.ezygo.attendance.domain.model.CourseSummary
import com.ezygo.attendance.domain.model.Period
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The course catalogue: which courses the student is in, who else is in them, and
 * the syllabus material behind each one.
 *
 * Unlike attendance, this is held in memory for the life of the process rather
 * than in Room. It is reference material the student opens occasionally, not
 * something the app must render on a cold offline start, and persisting the full
 * cohort roster for every course would be a lot of storage for little benefit.
 * The consequence is explicit: after a process death this refetches.
 */
@Singleton
class CourseCatalogRepository @Inject constructor(
    private val api: EzygoApi,
) {

    private val mutex = Mutex()
    private var cachedCourses: List<CourseSummary>? = null
    private val cachedInfo = mutableMapOf<Int, CourseInfo>()

    /**
     * The student's courses. When [period] is given, only courses belonging to that
     * academic year and semester are returned, so the list matches the rest of the
     * app rather than spanning every semester the student has ever enrolled in.
     */
    suspend fun courses(period: Period?, forceRefresh: Boolean = false): List<CourseSummary> {
        val all = mutex.withLock {
            val cached = cachedCourses
            if (cached != null && !forceRefresh) {
                cached
            } else {
                apiCall { api.coursesWithUsers() }
                    .mapNotNull { it.toSummary() }
                    .also { cachedCourses = it }
            }
        }
        if (period == null) return all
        val matching = all.filter {
            it.academicYear == period.academicYear && it.academicSemester == period.semester
        }
        // If nothing matches, the endpoint evidently does not scope by period the way
        // we assumed — show everything rather than an empty screen.
        return matching.ifEmpty { all }
    }

    /**
     * Full detail for one course. The three calls are independent, so they run
     * concurrently; modules and outcomes are optional and a failure in either leaves
     * that section empty rather than failing the whole screen.
     */
    suspend fun courseInfo(courseId: Int, forceRefresh: Boolean = false): CourseInfo {
        val cached = mutex.withLock { cachedInfo[courseId] }
        if (cached != null && !forceRefresh) return cached

        val summary = summaryFor(courseId)

        val info = coroutineScope {
            val detailTask = async { apiCall { api.courseDetail(courseId) } }
            val modulesTask = async { optionalApiCall(emptyList()) { api.courseModules(courseId) } }
            val outcomesTask = async { optionalApiCall(emptyList()) { api.courseOutcomes(courseId) } }

            val detail = detailTask.await()
            val modules = modulesTask.await()
            val outcomes = outcomesTask.await()

            CourseInfo(
                courseId = detail.id ?: courseId,
                name = detail.name ?: summary?.name ?: "Course #$courseId",
                code = detail.code ?: summary?.code,
                type = detail.type?.name,
                subgroupName = detail.usersubgroup?.name ?: summary?.subgroupName,
                programmeName = detail.usersubgroup?.usergroup?.name ?: summary?.programmeName,
                property = detail.property?.let { property ->
                    CoursePropertyInfo(
                        ltpCredits = property.ltpCredits,
                        textBooks = property.textBooks,
                        referenceDocs = property.referenceDocs,
                        preRequisites = property.preRequisites,
                        preamble = property.preamble,
                        aims = property.aims,
                        objectives = property.objectives,
                        industrialRelevance = property.industrialRelevance,
                        contentBeyondSyllabus = property.contentBeyondSyllabus,
                        learningMaterials = property.learningMaterials,
                    )
                }?.takeUnless { it.isEmpty },
                modules = modules.mapNotNull { module ->
                    val id = module.id ?: return@mapNotNull null
                    CourseModuleInfo(
                        id = id,
                        order = module.serialNo.asIntOrNull(),
                        title = module.module?.takeIf { it.isNotBlank() } ?: "Module",
                        lessons = module.lessons?.takeIf { it.isNotBlank() },
                        contactHours = module.contactHours?.let { hours ->
                            hours.asIntOrNull()?.toString()
                        },
                    )
                }.sortedBy { it.order ?: Int.MAX_VALUE },
                outcomes = outcomes.mapNotNull { outcome ->
                    val id = outcome.id ?: return@mapNotNull null
                    val text = outcome.outcome?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    CourseOutcomeInfo(id = id, code = outcome.code?.takeIf { it.isNotBlank() }, outcome = text)
                },
                // The roster comes from the course list; the detail endpoint does not
                // carry it. See [summaryFor].
                people = summary?.people.orEmpty(),
            )
        }

        mutex.withLock { cachedInfo[courseId] = info }
        return info
    }

    /**
     * The course-list entry for [courseId], loading the list if it has not been
     * fetched yet.
     *
     * The roster only exists on the list endpoint, and this screen can be opened
     * straight from a subject without the courses list ever having been visited —
     * without this, arriving that way would show a course with no classmates. A
     * failure here costs the people section, not the screen.
     */
    private suspend fun summaryFor(courseId: Int): CourseSummary? {
        val known = mutex.withLock { cachedCourses }
        if (known != null) return known.firstOrNull { it.courseId == courseId }
        return runCatching { courses(period = null) }
            .getOrNull()
            ?.firstOrNull { it.courseId == courseId }
    }

    /** Drops everything on sign-out; this cache belongs to the signed-in user. */
    suspend fun clear() = mutex.withLock {
        cachedCourses = null
        cachedInfo.clear()
    }

    // ---- mapping ---------------------------------------------------------

    private fun CourseWithUsersDto.toSummary(): CourseSummary? {
        val id = id ?: return null
        return CourseSummary(
            courseId = id,
            name = name?.takeIf { it.isNotBlank() } ?: "Course #$id",
            code = code?.takeIf { it.isNotBlank() },
            academicYear = academicYear,
            academicSemester = academicSemester,
            subgroupName = usersubgroup?.name,
            programmeName = usersubgroup?.usergroup?.name,
            people = members
                // Soft-deleted enrolments are people who have left the course.
                .filter { it.deletedAt == null && it.pivot?.deletedAt == null }
                .mapNotNull { it.toClassmate() }
                .sortedWith(compareByDescending<Classmate> { it.isStaff }.thenBy { it.name }),
        )
    }

    private fun CourseMemberDto.toClassmate(): Classmate? {
        val fullName = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() } ?: return null
        return Classmate(
            institutionUserId = pivot?.institutionUserId ?: id ?: return null,
            name = fullName,
            enrollStatus = enrollStatus,
            roleId = pivot?.courseRoleId,
        )
    }
}
