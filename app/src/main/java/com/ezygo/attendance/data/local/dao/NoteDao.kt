package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ezygo.attendance.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Most recently touched first, which is the order a notes list is read in. */
    @Query("SELECT * FROM notes ORDER BY updatedEpochMs DESC")
    fun notes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun note(id: String): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes")
    suspend fun clearAll()
}
