package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ezygo.attendance.data.local.entity.SemesterResultEntity
import com.ezygo.attendance.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * Everything, undated last.
     *
     * The screen groups these, so the only ordering that has to be right here is that
     * a task with no deadline cannot claim a place among the dated ones.
     */
    @Query(
        "SELECT * FROM tasks ORDER BY (dueEpochMs IS NULL) ASC, dueEpochMs ASC, " +
            "createdEpochMs ASC",
    )
    fun tasks(): Flow<List<TaskEntity>>

    /**
     * Dated and unfinished, soonest first: what the home screen is asking about.
     *
     * Deliberately not bounded to the future. Something overdue is the most owed thing
     * there is, and leaving it off the summary was the one case where the home screen
     * could say "nothing pending" to a student who had missed a deadline.
     */
    @Query(
        "SELECT * FROM tasks WHERE done = 0 AND dueEpochMs IS NOT NULL " +
            "ORDER BY dueEpochMs ASC LIMIT :limit",
    )
    fun pending(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun taskOnce(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE source = :source")
    suspend fun bySourceOnce(source: String): List<TaskEntity>

    @Upsert
    suspend fun upsert(tasks: List<TaskEntity>)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query(
        "UPDATE tasks SET done = :done, completedEpochMs = :completedEpochMs, " +
            "updatedEpochMs = :nowEpochMs WHERE id = :id",
    )
    suspend fun setDone(id: String, done: Boolean, completedEpochMs: Long?, nowEpochMs: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    /** Removes synced tasks whose events are gone from the feed. */
    @Query("DELETE FROM tasks WHERE source = :source AND id NOT IN (:keep)")
    suspend fun deleteMissing(source: String, keep: List<String>)

    @Query("DELETE FROM tasks WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}

@Dao
interface SemesterResultDao {

    @Query("SELECT * FROM semester_results ORDER BY semester ASC")
    fun results(): Flow<List<SemesterResultEntity>>

    @Upsert
    suspend fun upsert(result: SemesterResultEntity)

    @Query("DELETE FROM semester_results")
    suspend fun clearAll()
}
