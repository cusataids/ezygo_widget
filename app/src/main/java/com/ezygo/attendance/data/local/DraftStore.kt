package com.ezygo.attendance.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Half-finished work in the two estimators, kept across a trip out of the app.
 *
 * Both were scratchpads held only in memory, on the reasoning that an estimate should
 * not still be full of last week's guesses. That turned out to be the wrong trade in
 * practice: the data is laborious to type, and leaving the screen to check a mark on
 * another tab threw all of it away. Reset is now the only thing that clears it, which
 * makes discarding deliberate rather than accidental.
 *
 * Stored as opaque JSON rather than as typed preference keys. The shapes are lists of
 * records that change whenever either screen gains a field, and a key per field would
 * mean a migration every time. Each screen owns its own format and is responsible for
 * reading it back defensively — see the drafts beside each view model.
 */
class DraftStore(private val context: Context) {

    private val whatIfKey = stringPreferencesKey("draft_what_if")
    private val sgpaKey = stringPreferencesKey("draft_sgpa")

    val whatIf: Flow<String?> = context.preferencesFlow.map { it[whatIfKey] }

    val sgpa: Flow<String?> = context.preferencesFlow.map { it[sgpaKey] }

    suspend fun currentWhatIf(): String? = whatIf.first()

    suspend fun currentSgpa(): String? = sgpa.first()

    suspend fun setWhatIf(json: String) {
        context.appDataStore.edit { it[whatIfKey] = json }
    }

    suspend fun setSgpa(json: String) {
        context.appDataStore.edit { it[sgpaKey] = json }
    }

    /**
     * Both drafts belong to the signed-in account and go when it does.
     *
     * The only remover here. Reset on either screen does not delete the key — it
     * empties the screen, and the save that follows stores the emptied state — so a
     * per-screen clear would be a second way to do the same thing.
     */
    suspend fun clear() {
        context.appDataStore.edit {
            it.remove(whatIfKey)
            it.remove(sgpaKey)
        }
    }
}
