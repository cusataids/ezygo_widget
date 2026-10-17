package com.ezygo.attendance.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * One preferences store for the whole app: the encrypted session token and the
 * small amount of user-tunable configuration. Attendance itself lives in Room.
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "ezygo_prefs")

/**
 * Reads preferences without the risk of a read error propagating to the caller.
 *
 * DataStore signals a failed read by throwing [IOException] into the flow, and
 * every caller is expected to handle it. Left uncaught it takes down whatever is
 * collecting — which for the home-screen widget meant no content was ever produced
 * and the loading state became permanent. Falling back to empty preferences reads
 * as "signed out, nothing configured", which is the safe interpretation.
 */
val Context.preferencesFlow: Flow<Preferences>
    get() = appDataStore.data.catch { throwable ->
        if (throwable is IOException) {
            Log.w("EzygoPrefs", "Preferences unreadable, falling back to empty", throwable)
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }
