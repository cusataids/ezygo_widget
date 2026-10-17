package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One row of Laravel's `notifications` table, as the portal returns it.
 *
 * [data] is a plain string in every captured response, but that column is a JSON
 * object in Laravel's own schema and the portal is only flattening it on the way
 * out. It is declared loosely so a server that starts sending the object form
 * degrades to reading a field out of it rather than failing the whole list.
 */
@Serializable
data class NotificationDto(
    val id: String? = null,
    /** Fully qualified notification class, e.g. `App\Notifications\AttendanceAbsentMarked`. */
    val type: String? = null,
    val data: JsonElement? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
