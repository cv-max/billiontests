package com.billiontests.app.analysis

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * Mean-BGR sampling helpers shared by the lighting check and the colour matcher.
 *
 * app.py reads reference swatches with numpy slicing (`warped[cy-15:cy+15, cx-15:cx+15]`), which
 * silently clamps out-of-range indices; OpenCV's submat throws instead, so the rect is clamped here
 * explicitly. With the shipped [CardConstants] tables no clamping is ever needed — every patch sits
 * well inside the 1087x1602 canvas — but a malformed table would otherwise crash rather than degrade.
 */
object PatchSampler {

    /** Mean B, G, R over the square patch centred on ([cx], [cy]). Null if the patch is empty. */
    fun patchMean(warped: Mat, cx: Int, cy: Int, half: Int = CardConstants.PATCH_HALF): DoubleArray? {
        val x0 = (cx - half).coerceIn(0, warped.cols())
        val x1 = (cx + half).coerceIn(0, warped.cols())
        val y0 = (cy - half).coerceIn(0, warped.rows())
        val y1 = (cy + half).coerceIn(0, warped.rows())
        if (x1 <= x0 || y1 <= y0) return null
        val sub = warped.submat(Rect(x0, y0, x1 - x0, y1 - y0))
        try {
            return mean(sub)
        } finally {
            sub.release()
        }
    }

    /** Mean B, G, R over every pixel of [region] — matches numpy's `.mean(axis=(0, 1))`. */
    fun mean(region: Mat): DoubleArray {
        val m = Core.mean(region)
        return doubleArrayOf(m.`val`[0], m.`val`[1], m.`val`[2])
    }

    /** `calculate_color_distance(..., method='euclidean')` — the method app.py actually selects. */
    fun euclidean(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in 0..2) {
            val d = a[i] - b[i]
            s += d * d
        }
        return Math.sqrt(s)
    }

    /** `manhattan` — used only by the lighting-uniformity check. */
    fun manhattan(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in 0..2) s += Math.abs(a[i] - b[i])
        return s
    }
}
