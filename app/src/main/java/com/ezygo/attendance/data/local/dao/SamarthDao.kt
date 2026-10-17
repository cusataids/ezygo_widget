package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ezygo.attendance.data.local.entity.SamarthProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SamarthDao {

    @Query("SELECT * FROM samarth_profile LIMIT 1")
    fun profile(): Flow<SamarthProfileEntity?>

    @Upsert
    suspend fun upsert(profile: SamarthProfileEntity)

    @Query("DELETE FROM samarth_profile")
    suspend fun clear()
}
