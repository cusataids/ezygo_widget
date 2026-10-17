package com.ezygo.attendance

import com.ezygo.attendance.data.repo.CLASS_COUNTS_MAX_AGE_MS
import com.ezygo.attendance.data.repo.classCountsAreFresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the cohort report is allowed to be re-read.
 *
 * This rule is the whole reason the monitoring endpoint stopped being called on every
 * app start: get it wrong in one direction and a term of data is fetched on every
 * open, get it wrong in the other and the class screens quote yesterday's register
 * indefinitely. Neither failure is visible by looking at the screen, so it is tested.
 */
class ClassCountsFreshnessTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `never fetched is never fresh`() {
        assertFalse(classCountsAreFresh(fetchedAtEpochMs = null, nowMs = now))
    }

    @Test
    fun `just fetched is fresh`() {
        assertTrue(classCountsAreFresh(fetchedAtEpochMs = now, nowMs = now))
    }

    @Test
    fun `an hour old is fresh`() {
        assertTrue(classCountsAreFresh(now - 60 * 60 * 1000L, now))
    }

    @Test
    fun `a minute short of the limit is still fresh`() {
        assertTrue(classCountsAreFresh(now - CLASS_COUNTS_MAX_AGE_MS + 60_000L, now))
    }

    @Test
    fun `exactly at the limit is stale`() {
        // The boundary belongs to stale: twelve hours old means twelve hours have
        // passed, and the point of the window is that it then ends.
        assertFalse(classCountsAreFresh(now - CLASS_COUNTS_MAX_AGE_MS, now))
    }

    @Test
    fun `a day old is stale`() {
        assertFalse(classCountsAreFresh(now - 24 * 60 * 60 * 1000L, now))
    }

    @Test
    fun `a timestamp from the future is treated as fresh`() {
        // A clock that has moved backwards is not a reason to re-fetch a term of
        // data; the reading corrects itself as soon as the window passes.
        assertTrue(classCountsAreFresh(now + 60 * 60 * 1000L, now))
    }

    @Test
    fun `the window is twelve hours`() {
        assertEquals(12L * 60 * 60 * 1000, CLASS_COUNTS_MAX_AGE_MS)
    }
}
