package com.billiontests.app.analysis

import org.opencv.core.Mat

/**
 * `check_lighting_uniformity` from `app.py`.
 *
 * The card prints the same reference swatches on both sides, mirrored. Comparing `LL1[i]` against
 * `LL2[n-1-i]` therefore compares two patches that should be identical; a large difference means
 * the capture was lit unevenly across the card.
 */
object LightingCheck {

    class Result(val uniform: Boolean, val offendingPositions: List<Int>)

    fun check(
        warped: Mat,
        threshold: Double = CardConstants.LIGHTING_CHECK_THRESHOLD,
    ): Result {
        val ll1 = CardConstants.LL1
        val ll2 = CardConstants.LL2
        val offenders = ArrayList<Int>()

        for (i in ll1.indices) {
            val a = PatchSampler.patchMean(warped, ll1[i][0], ll1[i][1]) ?: continue
            val mirrored = ll2[ll1.size - i - 1]
            val b = PatchSampler.patchMean(warped, mirrored[0], mirrored[1]) ?: continue
            if (PatchSampler.manhattan(a, b) > threshold) offenders.add(i)
        }
        return Result(offenders.isEmpty(), offenders)
    }
}
