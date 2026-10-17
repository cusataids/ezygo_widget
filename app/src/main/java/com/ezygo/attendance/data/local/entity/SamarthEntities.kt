package com.ezygo.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The cached Samarth profile.
 *
 * Exactly one row, because there is exactly one student. Unlike the EzyGo caches this
 * one is never refreshed on its own schedule: it is written when the user asks for it
 * and at no other time, so the date on screen is always the date they last pressed
 * Refresh rather than something that quietly moved underneath them.
 */
@Entity(tableName = "samarth_profile")
data class SamarthProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val fullName: String,
    val dateOfBirth: String,
    val email: String,
    val phone: String,
    val category: String,
    val addressLine: String,
    val district: String,
    val statePincode: String,
    val enrolmentNumber: String,
    val abcId: String,
    val department: String,
    val fetchedEpochMs: Long,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}
