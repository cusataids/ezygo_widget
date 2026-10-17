package com.ezygo.attendance.domain.model

/**
 * How the app decides between its light and dark palettes.
 *
 * Three states rather than a boolean, because "follow the phone" is a genuinely
 * different answer from either fixed choice and cannot be represented by one: a user
 * on System wants the app to change when the phone does, which a stored true/false
 * cannot express.
 *
 * The keys are stored, so they never change.
 */
enum class ThemeChoice(val key: String, val label: String, val description: String) {
    DARK("dark", "Dark", "Always dark, whatever the phone is set to"),
    LIGHT("light", "Light", "Always light, whatever the phone is set to"),
    SYSTEM("system", "System default", "Follow the phone's theme"),
    ;

    companion object {
        /**
         * Dark, deliberately — not System.
         *
         * This is also the branch taken when the preference cannot be read at all:
         * [com.ezygo.attendance.data.local.preferencesFlow] answers an unreadable
         * store with empty preferences, so "never chosen" and "read failed" arrive
         * here as the same thing and have to land on the same answer.
         */
        val DEFAULT = DARK

        /**
         * Unlike [FeedSource.fromKey] this never returns null. There is no caller
         * position where "no theme" is a usable answer — something has to be drawn —
         * so the default is applied here rather than at each call site.
         */
        fun fromKey(key: String?): ThemeChoice =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
