package com.ezygo.attendance.ui.miniapps.whatif

import com.ezygo.attendance.domain.model.ClaimDay
import com.ezygo.attendance.domain.model.ClaimSessionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The simulation as it is stored between visits.
 *
 * A file format rather than the screen's own model, for the same reason as the SGPA
 * draft: what is on disk has to outlive the build that wrote it. Dates are ISO
 * strings and enums are names, both read back leniently — an entry that no longer
 * makes sense is dropped rather than taking the whole draft down with it.
 *
 * Only what the user typed is kept. The semester's courses, the resolved claim
 * sessions and the averages are all derived from the dashboard on open, so storing
 * them would just be a second copy to go stale.
 */
@Serializable
data class WhatIfDraft(
    val subjects: List<SubjectRow> = emptyList(),
    val claims: List<ClaimRow> = emptyList(),
) {

    @Serializable
    data class SubjectRow(
        val key: String,
        val name: String,
        val attended: Int = 0,
        val total: Int = 0,
        val attend: Int = 0,
        val miss: Int = 0,
        val isManual: Boolean = false,
    )

    @Serializable
    data class ClaimRow(
        val date: String,
        val type: String,
        val sessionIds: List<Int> = emptyList(),
    )

    /** The subjects as the screen holds them. Claimed counts are recomputed on load. */
    val whatIfSubjects: List<WhatIfSubject>
        get() = subjects.map {
            WhatIfSubject(
                key = it.key,
                name = it.name,
                attended = it.attended,
                total = it.total,
                attend = it.attend,
                miss = it.miss,
                isManual = it.isManual,
            )
        }

    /** The claimed days, skipping any whose date no longer parses. */
    val claimDays: List<ClaimDay>
        get() = claims.mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
                ?: return@mapNotNull null
            ClaimDay(
                date = date,
                type = ClaimSessionType.entries.firstOrNull { it.name == row.type }
                    ?: ClaimSessionType.FULL_DAY,
                customSessionIds = row.sessionIds.toSet(),
            )
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(subjects: List<WhatIfSubject>, claims: List<ClaimDay>): String =
            json.encodeToString(
                WhatIfDraft(
                    subjects = subjects.map {
                        SubjectRow(
                            key = it.key,
                            name = it.name,
                            attended = it.attended,
                            total = it.total,
                            attend = it.attend,
                            miss = it.miss,
                            isManual = it.isManual,
                        )
                    },
                    claims = claims.map {
                        ClaimRow(
                            date = it.date.toString(),
                            type = it.type.name,
                            sessionIds = it.customSessionIds.sorted(),
                        )
                    },
                ),
            )

        /** Null when the draft cannot be read; the screen then opens empty, as before. */
        fun decode(raw: String): WhatIfDraft? = runCatching {
            json.decodeFromString<WhatIfDraft>(raw)
        }.getOrNull()
    }
}
