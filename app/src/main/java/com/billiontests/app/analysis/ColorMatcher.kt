package com.billiontests.app.analysis

import org.opencv.core.Mat

/**
 * Matches an extracted pad colour against the card's printed reference swatches,
 * ported from the inner matching loop of `app.py`.
 */
object ColorMatcher {

    /** [index] indexes into the pad's candidate list, i.e. it is already the result-table index. */
    class Match(val index: Int, val confidence: Double, val distance: Double)

    /**
     * Nearest reference swatch to [padMean] among [candidates] (indices into [CardConstants.LL1]).
     *
     * Defaults to index 0 with zero confidence when nothing usable is found — app.py's
     * `best_match = 0` fallback, which for most assays reads as "Negative".
     */
    fun match(warped: Mat, padMean: DoubleArray, candidates: IntArray): Match {
        var bestIndex = 0
        var bestConfidence = 0.0
        var bestDistance = Double.MAX_VALUE

        for ((j, t) in candidates.withIndex()) {
            if (t >= CardConstants.LL1.size) continue
            val ref = CardConstants.LL1[t]
            val refMean = PatchSampler.patchMean(warped, ref[0], ref[1]) ?: continue

            val distance = PatchSampler.euclidean(padMean, refMean)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = j
                bestConfidence = if (distance <= CardConstants.DISTANCE_THRESHOLD) {
                    (100.0 * (1.0 - distance / CardConstants.DISTANCE_THRESHOLD)).coerceAtLeast(0.0)
                } else {
                    0.0
                }
            }
        }
        return Match(bestIndex, bestConfidence, bestDistance)
    }
}
