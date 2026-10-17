package com.ezygo.attendance

import com.ezygo.attendance.domain.Cgpa
import com.ezygo.attendance.domain.model.SemesterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cumulative average, which two screens quote and therefore must agree on.
 *
 * Added with the extraction that gave them one implementation: the whole risk of
 * having had two was that they could drift, and a test is what makes a drift fail
 * rather than ship.
 */
class CgpaTest {

    private fun result(semester: Int, sgpa: Double, credits: Int?) =
        SemesterResult(semester = semester, sgpa = sgpa, credits = credits)

    // ---- weighting -------------------------------------------------------

    @Test
    fun `credits weight the average`() {
        // 8.0 over 20 credits and 9.0 over 10: the bigger semester pulls harder, so
        // the answer sits below the plain mean of 8.5.
        val results = listOf(result(1, 8.0, 20), result(2, 9.0, 10))
        assertEquals((8.0 * 20 + 9.0 * 10) / 30, Cgpa.of(results)!!, 1e-9)
        assertEquals(8.3333, Cgpa.of(results)!!, 1e-4)
    }

    @Test
    fun `one semester without credits falls back to the plain mean`() {
        // Not "drop that semester": leaving it out is a worse answer than an
        // unweighted one, which is why the fallback covers the whole list.
        val results = listOf(result(1, 8.0, 20), result(2, 9.0, null))
        assertFalse(Cgpa.isCreditWeighted(results))
        assertEquals(8.5, Cgpa.of(results)!!, 1e-9)
    }

    @Test
    fun `a zero credit total counts as missing`() {
        val results = listOf(result(1, 8.0, 0), result(2, 9.0, 10))
        assertFalse(Cgpa.isCreditWeighted(results))
        assertEquals(8.5, Cgpa.of(results)!!, 1e-9)
    }

    @Test
    fun `no semesters means no average`() {
        assertNull(Cgpa.of(emptyList()))
        assertFalse(Cgpa.isCreditWeighted(emptyList()))
    }

    @Test
    fun `a single weighted semester is its own average`() {
        val results = listOf(result(1, 7.5, 22))
        assertTrue(Cgpa.isCreditWeighted(results))
        assertEquals(7.5, Cgpa.of(results)!!, 1e-9)
    }

    // ---- projecting the next semester ------------------------------------

    @Test
    fun `projecting folds the new semester in on the same formula`() {
        val past = listOf(result(1, 8.0, 20), result(2, 9.0, 10))
        val projected = Cgpa.with(past, sgpa = 10.0, semesterCredits = 10.0)!!
        assertEquals((8.0 * 20 + 9.0 * 10 + 10.0 * 10) / 40, projected, 1e-9)
    }

    @Test
    fun `projecting onto unweighted history stays unweighted`() {
        val past = listOf(result(1, 8.0, null), result(2, 9.0, 20))
        assertEquals(9.0, Cgpa.with(past, sgpa = 10.0, semesterCredits = 10.0)!!, 1e-9)
    }

    @Test
    fun `projecting with no history is the semester itself`() {
        assertEquals(9.1, Cgpa.with(emptyList(), sgpa = 9.1, semesterCredits = 18.0)!!, 1e-9)
    }

    @Test
    fun `a semester worth no credits cannot be projected`() {
        val past = listOf(result(1, 8.0, 20))
        assertNull(Cgpa.with(past, sgpa = 9.0, semesterCredits = 0.0))
    }

    @Test
    fun `projecting the same average leaves it unmoved`() {
        // The property that catches a mis-weighted denominator: adding a semester at
        // exactly the running average must not shift it.
        val past = listOf(result(1, 8.0, 20), result(2, 8.0, 10))
        val projected = Cgpa.with(past, sgpa = 8.0, semesterCredits = 14.0)!!
        assertEquals(8.0, projected, 1e-9)
    }
}
