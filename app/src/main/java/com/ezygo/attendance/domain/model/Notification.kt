package com.ezygo.attendance.domain.model

import java.time.Instant

/** What a notification is about, used to pick its icon. */
enum class NotificationKind { ATTENDANCE, ENROLMENT, OTHER }

/** One message from the portal, ready to render. */
data class UserNotification(
    val id: String,
    /** Readable form of the server's notification class, e.g. "Attendance absent marked". */
    val title: String,
    val message: String,
    val kind: NotificationKind,
    /** Null when the server sent a timestamp this app could not parse. */
    val createdAt: Instant?,
    /**
     * The portal has never been observed to set `read_at`, and exposes no endpoint
     * to set it, so nothing in the UI is driven by this yet. It is carried because
     * it is real data: the day the portal starts marking messages read, the list
     * can act on it without another round of API work.
     */
    val isRead: Boolean,
)
