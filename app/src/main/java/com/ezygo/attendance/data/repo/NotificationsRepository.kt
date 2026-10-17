package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.remote.EzygoApi
import com.ezygo.attendance.data.remote.dto.NotificationDto
import com.ezygo.attendance.data.remote.dto.asStringOrNull
import com.ezygo.attendance.domain.model.NotificationKind
import com.ezygo.attendance.domain.model.UserNotification
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The portal's message list.
 *
 * Held in memory for the life of the process rather than in Room, for the same
 * reason as the course catalogue: it is something the student opens occasionally,
 * not something the app has to render on a cold offline start. Nothing fetches
 * this in the background either — the list is only loaded when the screen asks
 * for it, so the icon in the app bar costs no requests until it is tapped.
 */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: EzygoApi,
) {

    private val mutex = Mutex()
    private var cached: List<UserNotification>? = null

    /** Newest first. Served from memory unless [forceRefresh] is set. */
    suspend fun notifications(forceRefresh: Boolean = false): List<UserNotification> =
        mutex.withLock {
            val existing = cached
            if (existing != null && !forceRefresh) return@withLock existing
            apiCall { api.notifications() }
                .mapNotNull { it.toDomain() }
                // The API already returns newest first, but the order is not part of
                // any contract and the list is meaningless in another one.
                .sortedByDescending { it.createdAt ?: Instant.EPOCH }
                .also { cached = it }
        }

    /** Drops everything on sign-out; these messages belong to the signed-in user. */
    suspend fun clear() = mutex.withLock { cached = null }

    // ---- mapping ---------------------------------------------------------

    private fun NotificationDto.toDomain(): UserNotification? {
        val id = id?.takeIf { it.isNotBlank() } ?: return null
        // A message with no text is not worth a row.
        val message = data.readMessage() ?: return null
        val className = type?.substringAfterLast(NAMESPACE_SEPARATOR)?.takeIf { it.isNotBlank() }
        return UserNotification(
            id = id,
            title = className?.let(::humanise) ?: "Notification",
            message = message,
            kind = kindOf(className),
            createdAt = parseTimestamp(createdAt),
            isRead = !readAt.isNullOrBlank(),
        )
    }

    /**
     * The message text, whether the portal flattened it to a string or left it as
     * the JSON object Laravel actually stores.
     */
    private fun JsonElement?.readMessage(): String? = when (this) {
        is JsonObject -> MESSAGE_KEYS.firstNotNullOfOrNull { key -> this[key].asStringOrNull() }
        is JsonPrimitive -> asStringOrNull()
        else -> null
    }

    private fun kindOf(className: String?): NotificationKind = when {
        className == null -> NotificationKind.OTHER
        className.startsWith("Attendance") -> NotificationKind.ATTENDANCE
        className.contains("Enroll") || className.contains("Institution") ->
            NotificationKind.ENROLMENT
        else -> NotificationKind.OTHER
    }

    /**
     * "AttendanceAbsentMarked" -> "Attendance absent marked".
     *
     * Derived from the class name rather than matched against a fixed list, so a
     * notification type this app has never seen still gets a readable heading
     * instead of being dropped or labelled "Unknown".
     */
    private fun humanise(className: String): String {
        val words = CAMEL_CASE_BOUNDARY.split(className).filter { it.isNotBlank() }
        if (words.isEmpty()) return className
        return words.mapIndexed { index, word ->
            if (index == 0) word.replaceFirstChar { it.uppercase() } else word.lowercase()
        }.joinToString(" ")
    }

    /**
     * Laravel emits ISO-8601 with microseconds; some of its versions emit a plain
     * "yyyy-MM-dd HH:mm:ss" in UTC instead. An unparseable value costs the row its
     * timestamp, not its place in the list.
     */
    private fun parseTimestamp(raw: String?): Instant? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
            }.getOrNull()
    }

    private companion object {
        const val NAMESPACE_SEPARATOR = '\\'
        val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
        val MESSAGE_KEYS = listOf("message", "body", "text", "data", "title")
    }
}
