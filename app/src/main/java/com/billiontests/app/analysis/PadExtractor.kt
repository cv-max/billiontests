package com.billiontests.app.analysis

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * Turns segmentation masks into per-pad mean colours, ported from the strip-extraction loop in
 * `app.py` (`urinalysis_improved` / `urinalysis_improved_mission`).
 *
 * Per pad: mask -> resize to canvas -> external contours -> bounding rect -> inset crop of the
 * masked image -> mean BGR; plus the contour centroid used to order pads top-to-bottom.
 */
object PadExtractor {

    /** A pad after extraction. [meanBgr] is null when the inset crop came out empty. */
    class Pad(val meanBgr: DoubleArray?, val bounds: Rect)

    /**
     * Extracts pads for [detections] filtered to class 0, ordered top-to-bottom by centroid y.
     *
     * app.py appends to `Color_box` for every contoured mask but to `Color_center` only when the
     * contour has non-zero area, then indexes `Color_box` with positions derived from
     * `Color_center` — so the two lists must stay the same length for the pairing to be valid.
     * That invariant is preserved here by dropping a pad from both lists together; in practice a
     * non-empty external contour always has m00 > 0, so this never triggers.
     */
    fun extract(warped: Mat, detections: List<SegDecoder.Detection>): List<Pad> {
        val pads = ArrayList<Pad>()
        val centroidY = ArrayList<Int>()

        for (det in detections) {
            if (det.classId != SegDecoder.CLASS_PAD) continue

            val canvasMask = SegDecoder.maskToCanvas(det.mask)
            try {
                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(
                    canvasMask, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
                )
                hierarchy.release()
                if (contours.isEmpty()) continue

                // app.py uses contours[0] only, not the largest contour.
                val contour = contours[0]
                val moments = Imgproc.moments(contour)
                if (moments.m00 == 0.0) {
                    contours.forEach { it.release() }
                    continue
                }

                val masked = Mat()
                Core.bitwise_and(warped, warped, masked, canvasMask)
                try {
                    val bounds = Imgproc.boundingRect(contour)
                    pads.add(Pad(insetMean(masked, bounds), bounds))
                    centroidY.add((moments.m01 / moments.m00).toInt())
                } finally {
                    masked.release()
                }
                contours.forEach { it.release() }
            } finally {
                canvasMask.release()
            }
        }

        // sorted(enumerate(Color_center), key=lambda x: x[1][1]) — stable, ascending centroid y
        return pads.indices
            .sortedBy { centroidY[it] }
            .map { pads[it] }
    }

    /**
     * `ker2[int(y+h/5):int(y+h-h/5), int(x+w/5):int(x+w-w/5)]` — a 20% inset on each side.
     *
     * The mean deliberately includes the pixels the mask zeroed out, exactly as the Python does:
     * the crop is a rectangle over the masked image, not over the mask's own area.
     *
     * Integer conversion must truncate (numpy `int()`), not round.
     */
    private fun insetMean(masked: Mat, b: Rect): DoubleArray? {
        val y0 = (b.y + b.height / 5.0).toInt()
        val y1 = (b.y + b.height - b.height / 5.0).toInt()
        val x0 = (b.x + b.width / 5.0).toInt()
        val x1 = (b.x + b.width - b.width / 5.0).toInt()

        val cx0 = x0.coerceIn(0, masked.cols())
        val cx1 = x1.coerceIn(0, masked.cols())
        val cy0 = y0.coerceIn(0, masked.rows())
        val cy1 = y1.coerceIn(0, masked.rows())
        if (cx1 <= cx0 || cy1 <= cy0) return null

        val crop = masked.submat(Rect(cx0, cy0, cx1 - cx0, cy1 - cy0))
        try {
            return PatchSampler.mean(crop)
        } finally {
            crop.release()
        }
    }
}
