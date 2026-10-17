package com.ezygo.attendance.domain

import com.ezygo.attendance.domain.model.SemesterResult

/**
 * The cumulative grade point average, in the one place that knows how to work it out.
 *
 * This used to exist twice — once on the grades screen and once in the SGPA estimator
 * — as two character-for-character copies of the same arithmetic. Nothing kept them in
 * step, and the estimator's whole job is to project against the figure the grades
 * screen shows, so a change to one and not the other would have produced two numbers
 * both calling themselves the CGPA.
 */
object Cgpa {

    /**
     * Whether every semester read carries a credit total.
     *
     * The weighted formula needs all of them: leaving out the one semester whose card
     * did not print its credits would drop that semester from the CGPA entirely,
     * which is a worse answer than an unweighted one.
     */
    fun isCreditWeighted(results: List<SemesterResult>): Boolean =
        results.isNotEmpty() && results.all { (it.credits ?: 0) > 0 }

    /**
     * CGPA = Σ(Cᵢ × Sᵢ) / ΣCᵢ, over the semesters actually read.
     *
     * Falls back to the plain mean of the SGPAs when a card did not yield its credit
     * total — see [isCreditWeighted], which is what the screen says out loud so the
     * figure is never quietly the other one. Null when there is nothing to average.
     */
    fun of(results: List<SemesterResult>): Double? {
        if (results.isEmpty()) return null
        if (!isCreditWeighted(results)) return results.map { it.sgpa }.average()
        val credits = totalCredits(results)
        // Unreachable while isCreditWeighted holds, which requires every credit above
        // zero. Kept because this is the one division here and a guard is cheaper
        // than trusting a caller to have asked the question first.
        if (credits <= 0.0) return null
        return results.sumOf { it.sgpa * (it.credits ?: 0).toDouble() } / credits
    }

    /**
     * The same figure with one more semester folded in, on the same formula.
     *
     * [semesterCredits] is the estimated semester's own credit total. Null when there
     * is nothing to add it to and no way to weight it.
     */
    fun with(
        results: List<SemesterResult>,
        sgpa: Double,
        semesterCredits: Double,
    ): Double? {
        if (semesterCredits <= 0.0) return null
        if (results.isEmpty()) return sgpa
        if (!isCreditWeighted(results)) return (results.map { it.sgpa } + sgpa).average()
        val pastCredits = totalCredits(results)
        val pastPoints = results.sumOf { it.sgpa * (it.credits ?: 0).toDouble() }
        return (pastPoints + sgpa * semesterCredits) / (pastCredits + semesterCredits)
    }

    /** Credits behind a weighted CGPA; zero when the results carry none. */
    fun totalCredits(results: List<SemesterResult>): Double =
        results.sumOf { (it.credits ?: 0).toDouble() }
}
