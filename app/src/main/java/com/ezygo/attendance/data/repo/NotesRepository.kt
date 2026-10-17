package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.entity.NoteEntity
import com.ezygo.attendance.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's notes.
 *
 * Room is the single source of truth, as everywhere else in this app: the screens
 * observe it and a save simply writes a row. Nothing here touches the network — a
 * note exists on this device and nowhere else, which is why signing out clears them
 * along with the rest of the user's own data.
 */
@Singleton
class NotesRepository @Inject constructor(
    private val database: AppDatabase,
) {
    val notes: Flow<List<Note>> =
        database.noteDao().notes().map { rows -> rows.map { it.toDomain() } }

    fun note(id: String): Flow<Note?> =
        database.noteDao().note(id).map { it?.toDomain() }

    /** A blank note that exists only in memory until something is written into it. */
    fun draft(nowEpochMs: Long): Note = Note(
        id = UUID.randomUUID().toString(),
        title = "",
        body = "",
        createdEpochMs = nowEpochMs,
        updatedEpochMs = nowEpochMs,
    )

    /**
     * Writes the note, or removes it if there is nothing left in it.
     *
     * Saving an empty note would leave an untitled blank in the list that the user
     * has to find and delete, so closing an untouched draft simply leaves nothing
     * behind.
     */
    suspend fun save(note: Note, nowEpochMs: Long) {
        if (note.isEmpty) {
            database.noteDao().delete(note.id)
            return
        }
        database.noteDao().upsert(
            NoteEntity(
                id = note.id,
                title = note.title.trim(),
                body = note.body,
                createdEpochMs = note.createdEpochMs,
                updatedEpochMs = nowEpochMs,
            ),
        )
    }

    suspend fun delete(id: String) = database.noteDao().delete(id)

    /** Signing out is an explicit "forget me", and notes are the user's own writing. */
    suspend fun clear() = database.noteDao().clearAll()

    private fun NoteEntity.toDomain() = Note(
        id = id,
        title = title,
        body = body,
        createdEpochMs = createdEpochMs,
        updatedEpochMs = updatedEpochMs,
    )
}
