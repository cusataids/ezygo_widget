package com.ezygo.attendance.domain

import com.ezygo.attendance.domain.model.CourseStats

/** How many more classes one subject has to be attended, and what that does to it. */
data class CourseNeed(
    val courseId: Int,
    val courseName: String,
    val classes: Int,
)

/**
 * The shortest route back to the target, as a count and as a plan.
 *
 * [classesNeeded] is the headline. [perCourse] is the same number broken down, and
 * always sums to it — a bare "attend 7 more classes" is not actionable without
 * knowing which seven.
 */
data class AttendancePlan(
    val targetPercent: Int,
    val classesNeeded: Int,
    val perCourse: List<CourseNeed>,
    /** True when the average is already at or above the target; nothing to do. */
    val alreadyMet: Boolean,
    /** False when the target could not be reached within [AttendancePlanner.MAX_STEPS]. */
    val reachable: Boolean,
    /** Courses the plan is averaged over: those with at least one recorded class. */
    val courseCount: Int,
) {
    val hasPlan: Boolean get() = !alreadyMet && reachable && classesNeeded > 0
}

/**
 * Works out the fewest additional classes that lift the equal-weight average to the
 * target, and which subjects they should be.
 *
 * Greedy: simulate attending one class, every time in whichever subject that single
 * class moves the average furthest, then recount and go again. The subject with the
 * fewest recorded classes usually wins early — one class is worth more out of eleven
 * than out of forty — so the plan naturally concentrates where it does most good.
 *
 * **Greedy is genuinely optimal here, not merely a heuristic.** Each subject's
 * percentage after k extra attended classes is (aᵢ+k)/(tᵢ+k), which is increasing and
 * concave in k, so each additional class in the same subject is worth strictly less
 * than the one before. Maximising a sum of concave functions one unit at a time
 * always lands on the true optimum, so the count this returns is the minimum rather
 * than just a good answer.
 *
 * Subjects with no recorded classes are excluded throughout. They are not in the
 * equal-weight average to begin with — [com.ezygo.attendance.domain.model.AttendanceStats.percentage]
 * is null for them — and attending one would *add* a subject to the average rather
 * than move it, which is a different question from the one being asked.
 */
object AttendancePlanner {

    /**
     * A ceiling on the simulation, not a policy.
     *
     * The loop terminates on its own: every subject below 100% gains from another
     * class, and driving them all to 100% would put the average at 100. This only
     * stops a pathological input from spinning, and tripping it is reported as
     * "not reachable" rather than as a plan.
     */
    const val MAX_STEPS = 5_000

    /** Doubles do not land on 75.0 exactly; this stops one class being added for dust. */
    private const val EPSILON = 1e-9

    fun plan(courses: List<CourseStats>, targetPercent: Int): AttendancePlan {
        // Zero-class subjects are dropped here and never looked at again.
        val eligible = courses.filter { it.stats.total > 0 }
        if (eligible.isEmpty()) {
            return AttendancePlan(
                targetPercent = targetPercent,
                classesNeeded = 0,
                perCourse = emptyList(),
                alreadyMet = false,
                reachable = false,
                courseCount = 0,
            )
        }

        val attended = IntArray(eligible.size) { eligible[it].stats.positive }
        val total = IntArray(eligible.size) { eligible[it].stats.total }
        val added = IntArray(eligible.size)

        fun average(): Double =
            eligible.indices.sumOf { attended[it] * 100.0 / total[it] } / eligible.size

        if (average() >= targetPercent - EPSILON) {
            return AttendancePlan(
                targetPercent = targetPercent,
                classesNeeded = 0,
                perCourse = emptyList(),
                alreadyMet = true,
                reachable = true,
                courseCount = eligible.size,
            )
        }

        var steps = 0
        while (average() < targetPercent - EPSILON && steps < MAX_STEPS) {
            var best = -1
            var bestGain = 0.0
            for (i in eligible.indices) {
                // The gain to the average is this subject's gain divided by the
                // subject count, and that divisor is the same for every candidate —
                // so comparing the subject's own movement picks the same winner.
                val now = attended[i] * 100.0 / total[i]
                val next = (attended[i] + 1) * 100.0 / (total[i] + 1)
                val gain = next - now
                if (gain > bestGain) {
                    bestGain = gain
                    best = i
                }
            }
            // Only reachable if every subject is already at 100%, in which case the
            // average is 100 and the loop above would not have run.
            if (best < 0) break

            attended[best]++
            total[best]++
            added[best]++
            steps++
        }

        val reached = average() >= targetPercent - EPSILON
        return AttendancePlan(
            targetPercent = targetPercent,
            classesNeeded = steps,
            perCourse = eligible.indices
                .filter { added[it] > 0 }
                .map { i ->
                    CourseNeed(
                        courseId = eligible[i].courseId,
                        courseName = eligible[i].courseName,
                        classes = added[i],
                    )
                }
                .sortedByDescending { it.classes },
            alreadyMet = false,
            reachable = reached,
            courseCount = eligible.size,
        )
    }
}
