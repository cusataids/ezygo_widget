package com.ezygo.attendance.domain.model

/**
 * An academic year + semester pair.
 *
 * The EzyGo attendance endpoint takes an empty body and reads the period from
 * the *server side* user settings, so a Period is never sent with a request —
 * it is written to the user's settings first and then implied by the response.
 * We still tag every cached row with it so two semesters can never blend.
 */
data class Period(
    val academicYear: String,
    val semester: String,
) {
    /** Stable primary key for the cache. */
    val key: String get() = "$academicYear|$semester"

    /**
     * True when either half is the portal's "all" setting.
     *
     * The portal offers an All option for both the year and the semester, stored as
     * the string "0". This app is built around one period at a time — its whole cache
     * is keyed by period so that two semesters can never blend — so All is a state to
     * recognise and leave, not one to adopt.
     */
    val isAll: Boolean get() = academicYear == ALL || semester == ALL

    val displayLabel: String
        get() = when {
            academicYear == ALL && semester == ALL -> "All years and semesters"
            academicYear == ALL -> "All years · ${semester.replaceFirstChar { it.uppercaseChar() }}"
            semester == ALL -> "$academicYear · All semesters"
            else -> "$academicYear · ${semester.replaceFirstChar { it.uppercaseChar() }}"
        }

    companion object {
        const val SEMESTER_ODD = "odd"
        const val SEMESTER_EVEN = "even"

        /** What the portal stores in either field for "all". */
        const val ALL = "0"

        /**
         * Academic years offered by the source portal (spec §25). No endpoint in
         * the documented contract enumerates these, so the list is static and
         * isolated here — swap this for an API call if one ever appears.
         */
        val ACADEMIC_YEARS = listOf(
            "2019-20", "2020-21", "2021-22", "2022-23",
            "2023-24", "2024-25", "2025-26", "2026-27",
        )

        val SEMESTERS = listOf(SEMESTER_ODD, SEMESTER_EVEN)

        /**
         * A concrete period built from a possibly half-chosen one.
         *
         * Choosing a year while the account is on "all semesters" has to supply a
         * semester as well, or the request writes "0" straight back and the account
         * never leaves All — which is exactly what made the setting impossible to
         * change from inside this app.
         */
        fun concrete(academicYear: String, semester: String): Period = Period(
            academicYear = academicYear.takeUnless { it == ALL } ?: ACADEMIC_YEARS.last(),
            semester = semester.takeUnless { it == ALL } ?: SEMESTER_ODD,
        )

        fun parseKey(key: String): Period? {
            val parts = key.split("|")
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                Period(parts[0], parts[1])
            } else {
                null
            }
        }
    }
}
