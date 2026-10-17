package com.ezygo.attendance.domain.model

/**
 * The student record held by the Samarth portal.
 *
 * Every field is a string exactly as the portal printed it. Nothing is parsed into a
 * date or a number, because the portal's own formatting is the only thing that can be
 * checked against the site itself, and a reformatted value that disagrees with the
 * source is worse than an awkward one.
 */
data class SamarthProfile(
    val fullName: String,
    val dateOfBirth: String,
    val email: String,
    val phone: String,
    val category: String,
    val addressLine: String,
    val district: String,
    val statePincode: String,
    val enrolmentNumber: String,
    val abcId: String,
    val department: String,
    /** When this was scraped. Nothing updates it except an explicit refresh. */
    val fetchedEpochMs: Long,
) {
    val address: String
        get() = listOf(addressLine, district, statePincode)
            .filter { it.isNotBlank() && it != NOT_AVAILABLE }
            .joinToString(", ")

    companion object {
        /** What the portal itself leaves blank, kept verbatim rather than guessed at. */
        const val NOT_AVAILABLE = "N/A"
    }
}

/**
 * One grade card offered by the portal.
 *
 * The four ids are the query parameters its PDF link is built from; they are carried
 * around together because the portal will not serve the document without all of them.
 */
data class SamarthGradeCard(
    val term: String,
    val programmeId: String,
    val examSessionId: String,
    val studentId: String,
) {
    /** Stable across fetches, so a re-fetch recognises a card already on disk. */
    val fileName: String get() = "grade_card_term_$term.pdf"

    val title: String get() = "Semester $term"
}
