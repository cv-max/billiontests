package com.billiontests.app.analysis

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect

/**
 * ArUco marker detection and the perspective rectification, ported from `app.py`
 * (`ordered_points` / `four_point_transform`).
 *
 * Verified on-device against the Python reference: marker centres match within +/-1 px.
 */
object CardGeometry {

    /** Destination inset used by `four_point_transform`. */
    private const val INSET_X = 114.0
    private const val INSET_Y = 99.0

    private val detector: ArucoDetector by lazy {
        ArucoDetector(
            Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50),
            DetectorParameters()
        )
    }

    /**
     * Marker centres, computed exactly as app.py does: the midpoint of corner 0 (top-left) and
     * corner 2 (bottom-right), truncated to int.
     *
     * Returns them in detection order — [warp] does the ordering.
     */
    fun markerCenters(image: Mat): List<Point> {
        val corners = ArrayList<Mat>()
        val ids = Mat()
        try {
            detector.detectMarkers(image, corners, ids)
            return corners.map { mc ->
                val pts = FloatArray(8)
                mc.reshape(2, 4).get(0, 0, pts)
                Point(
                    ((pts[0] + pts[4]) / 2.0f).toInt().toDouble(),
                    ((pts[1] + pts[5]) / 2.0f).toInt().toDouble()
                )
            }
        } finally {
            corners.forEach { it.release() }
            ids.release()
        }
    }

    /**
     * Guarantees the four corner centres [warp] needs.
     *
     * With four (or more) markers the detected centres are returned unchanged. With exactly three,
     * the missing corner is reconstructed geometrically: the two farthest-apart centres form one
     * diagonal, and the fourth corner is the remaining centre reflected through that diagonal's
     * midpoint — a rectangle's diagonals bisect each other, so `missing = 2*midpoint - third`.
     * Fewer than three centres cannot define the card plane, so `null` is returned.
     *
     * The reconstruction assumes the markers sit at the corners of a rectangle (they do); mild
     * perspective skew leaves the estimate close enough for the fixed-inset [warp].
     */
    fun completeCorners(centers: List<Point>): List<Point>? = when {
        centers.size >= 4 -> centers
        centers.size == 3 -> {
            val (a, b) = farthestPair(centers)
            val third = centers.first { it !== a && it !== b }
            val midX = (a.x + b.x) / 2.0
            val midY = (a.y + b.y) / 2.0
            val missing = Point(2.0 * midX - third.x, 2.0 * midY - third.y)
            listOf(a, b, third, missing)
        }
        else -> null
    }

    /** The two points with the greatest separation — the diagonal of the marker rectangle. */
    private fun farthestPair(points: List<Point>): Pair<Point, Point> {
        var best = points[0] to points[1]
        var bestDist = -1.0
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                val dist = dx * dx + dy * dy
                if (dist > bestDist) {
                    bestDist = dist
                    best = points[i] to points[j]
                }
            }
        }
        return best
    }

    /**
     * `ordered_points` — top-left is min(x+y), bottom-right max(x+y),
     * top-right min(x-y) [numpy's `diff` is y-x, hence min there], bottom-left max.
     */
    private fun ordered(points: List<Point>): Array<Point> {
        val tl = points.minByOrNull { it.x + it.y }!!
        val br = points.maxByOrNull { it.x + it.y }!!
        val tr = points.minByOrNull { it.y - it.x }!!
        val bl = points.maxByOrNull { it.y - it.x }!!
        return arrayOf(tl, tr, br, bl)
    }

    /**
     * `four_point_transform` — rectifies onto the fixed 1087x1602 canvas.
     *
     * The output size is what makes [SegDecoder]'s letterbox constants valid, so it must not change.
     */
    fun warp(image: Mat, centers: List<Point>): Mat {
        val rect = ordered(centers)
        val src = MatOfPoint2f(*rect)
        val dst = MatOfPoint2f(
            Point(INSET_X, INSET_Y),
            Point(SegDecoder.WARP_W - INSET_X, INSET_Y),
            Point(SegDecoder.WARP_W - INSET_X, SegDecoder.WARP_H - INSET_Y),
            Point(INSET_X, SegDecoder.WARP_H - INSET_Y),
        )
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        try {
            Imgproc.warpPerspective(
                image, warped, m,
                Size(SegDecoder.WARP_W.toDouble(), SegDecoder.WARP_H.toDouble())
            )
        } finally {
            src.release(); dst.release(); m.release()
        }
        return warped
    }
}
