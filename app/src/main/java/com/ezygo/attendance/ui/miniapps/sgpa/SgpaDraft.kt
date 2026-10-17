package com.ezygo.attendance.ui.miniapps.sgpa

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The estimator's rows, in the shape they are stored in.
 *
 * A separate type from [SgpaCourse] on purpose. The screen's model is free to gain
 * and rename fields; this one is a file format, and anything stored has to survive
 * the app that wrote it being replaced by a newer one. Enums are held as their names
 * with an unknown name resolving to null rather than throwing, so a draft written by
 * a build that knew an extra grade still opens.
 */
@Serializable
data class SgpaDraft(val rows: List<Row> = emptyList()) {

    @Serializable
    data class Row(
        val id: String,
        val name: String = "",
        val customName: Boolean = false,
        val type: String? = null,
        val entry: String = "",
        val marks: String = "",
        val grade: String? = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(courses: List<SgpaCourse>): String = json.encodeToString(
            SgpaDraft(
                rows = courses.map {
                    Row(
                        id = it.id,
                        name = it.name,
                        customName = it.customName,
                        type = it.type?.name,
                        entry = it.entry.name,
                        marks = it.marks,
                        grade = it.grade?.name,
                    )
                },
            ),
        )

        /**
         * Reads a draft back, or null if it cannot be read at all.
         *
         * A stored draft is disposable: the worst outcome of an unreadable one is the
         * screen opening as it would have before this existed, which is why nothing
         * here throws.
         */
        fun decode(raw: String): SgpaDraft? = runCatching {
            json.decodeFromString<SgpaDraft>(raw)
        }.getOrNull()
    }

    /** The rows as the screen holds them. */
    val courses: List<SgpaCourse>
        get() = rows.map { row ->
            SgpaCourse(
                id = row.id,
                name = row.name,
                customName = row.customName,
                type = CourseType.entries.firstOrNull { it.name == row.type },
                entry = ScoreEntry.entries.firstOrNull { it.name == row.entry } ?: ScoreEntry.MARK,
                marks = row.marks,
                grade = Grade.entries.firstOrNull { it.name == row.grade },
            )
        }
}
