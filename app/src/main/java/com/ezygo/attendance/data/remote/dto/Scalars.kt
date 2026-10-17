package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The portal is inconsistent about scalar types across endpoints: `si_no` is a
 * number on courses but a string on user subgroups, and `view_order` is a quoted
 * string on sessions and workdays. Fields like that are declared as [JsonElement]
 * and read through these helpers, so a type change on the server side degrades to
 * a null rather than failing the whole response.
 */

internal fun JsonElement?.asIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.toIntOrNull() ?: primitive.content.toDoubleOrNull()?.toInt()
}

internal fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.takeIf { it.isNotBlank() && it != "null" }
}
