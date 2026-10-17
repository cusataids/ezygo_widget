package com.ezygo.attendance.domain.model

/** Someone enrolled in a course alongside the student. */
data class Classmate(
    val institutionUserId: Long,
    val name: String,
    val enrollStatus: String?,
    /** Course role id: 1 is staff, 2 is a student, per the portal's own data. */
    val roleId: Int?,
) {
    val isStaff: Boolean get() = roleId == STAFF_ROLE_ID

    companion object {
        const val STAFF_ROLE_ID = 1
    }
}

/** A course as it appears in the student's course list. */
data class CourseSummary(
    val courseId: Int,
    val name: String,
    val code: String?,
    val academicYear: String?,
    val academicSemester: String?,
    val subgroupName: String?,
    val programmeName: String?,
    val people: List<Classmate>,
) {
    val students: List<Classmate> get() = people.filterNot { it.isStaff }
    val staff: List<Classmate> get() = people.filter { it.isStaff }
}

data class CourseModuleInfo(
    val id: Int,
    val order: Int?,
    val title: String,
    val lessons: String?,
    val contactHours: String?,
)

data class CourseOutcomeInfo(
    val id: Int,
    val code: String?,
    val outcome: String,
)

/** The syllabus property block. Every field is optional in the source data. */
data class CoursePropertyInfo(
    val ltpCredits: String?,
    val textBooks: String?,
    val referenceDocs: String?,
    val preRequisites: String?,
    val preamble: String?,
    val aims: String?,
    val objectives: String?,
    val industrialRelevance: String?,
    val contentBeyondSyllabus: String?,
    val learningMaterials: String?,
) {
    val isEmpty: Boolean
        get() = listOf(
            ltpCredits, textBooks, referenceDocs, preRequisites, preamble,
            aims, objectives, industrialRelevance, contentBeyondSyllabus, learningMaterials,
        ).all { it.isNullOrBlank() }
}

/** Everything shown on the course info screen. */
data class CourseInfo(
    val courseId: Int,
    val name: String,
    val code: String?,
    val type: String?,
    val subgroupName: String?,
    val programmeName: String?,
    val property: CoursePropertyInfo?,
    val modules: List<CourseModuleInfo>,
    val outcomes: List<CourseOutcomeInfo>,
    val people: List<Classmate>,
) {
    val students: List<Classmate> get() = people.filterNot { it.isStaff }
    val staff: List<Classmate> get() = people.filter { it.isStaff }
}
