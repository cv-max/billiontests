package com.billiontests.app.analysis

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin port of android_export/ref_decoder.py — YOLOv8n-seg decode on top of OpenCV DNN.
 *
 * The reference was validated against ultralytics on 20 real captures: MASK_MODE_84 reproduces
 * `results[0].masks.data` exactly (mask IoU 1.0000). Every constant here is derived from the fact
 * that `four_point_transform` always emits 1087x1602, which makes the letterbox deterministic.
 *
 * Detections and masks come back in LETTERBOX space (1280x896), matching `results[0].masks.data`.
 * app.py resizes those straight to 1087x1602 — see [maskToCanvas].
 */
object SegDecoder {

    const val NET_H = 1280
    const val NET_W = 896

    const val WARP_W = 1087
    const val WARP_H = 1602

    private const val NUM_CLASSES = 2
    private const val NUM_COEF = 32
    private const val NUM_CH = 4 + NUM_CLASSES + NUM_COEF   // 38

    private const val CONF_THRES = 0.5f
    private const val IOU_THRES = 0.7f
    private const val MAX_DET = 300
    private const val MAX_WH = 7680f                        // ultralytics class-offset constant

    // Verified bit-exact (maxdiff=0) against ultralytics LetterBox for a 1087x1602 input.
    private const val RESIZE_W = 869
    private const val RESIZE_H = 1280
    private const val PAD_L = 13
    private const val PAD_T = 0

    /** Upsample -> threshold -> crop at image res. Matches ultralytics 8.4.x. Validated exact. */
    const val MASK_MODE_84 = 0

    /** Crop at proto res -> upsample -> threshold. Matches ultralytics 8.3.x. */
    const val MASK_MODE_83 = 1

    private val PAD_VALUE = Scalar(114.0, 114.0, 114.0)
    private val ZERO = Scalar(0.0)

    /** Class id 0 = "color" (the reagent pads). Class id 1 = "color_strip". */
    const val CLASS_PAD = 0

    /**
     * One detection. [x1]/[y1]/[x2]/[y2] are in letterbox space (1280x896).
     * [mask] is CV_8U, 1280x896, values 0 or 255 — caller owns it and must [release].
     */
    class Detection(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val score: Float,
        val classId: Int,
        val mask: Mat,
    )

    fun loadNet(onnxPath: String): Net = Dnn.readNetFromONNX(onnxPath).apply {
        setPreferableBackend(Dnn.DNN_BACKEND_OPENCV)
        setPreferableTarget(Dnn.DNN_TARGET_CPU)
    }

    /**
     * ref_decoder.letterbox — resize 1087x1602 -> 869x1280, then pad left=13 right=14 top=0 bottom=0.
     * The horizontal padding is ASYMMETRIC; do not "simplify" it to 14/14.
     */
    fun letterbox(warped: Mat): Mat {
        require(warped.cols() == WARP_W && warped.rows() == WARP_H) {
            "expected ${WARP_W}x$WARP_H warped input, got ${warped.cols()}x${warped.rows()}"
        }
        val resized = Mat()
        Imgproc.resize(
            warped, resized, Size(RESIZE_W.toDouble(), RESIZE_H.toDouble()),
            0.0, 0.0, Imgproc.INTER_LINEAR
        )
        val out = Mat()
        Core.copyMakeBorder(
            resized, out,
            PAD_T, NET_H - RESIZE_H - PAD_T,
            PAD_L, NET_W - RESIZE_W - PAD_L,
            Core.BORDER_CONSTANT, PAD_VALUE
        )
        resized.release()
        return out
    }

    /**
     * Full pipeline: warped (BGR, 1087x1602) -> detections with masks.
     *
     * Caller must release each [Detection.mask].
     */
    fun run(net: Net, warped: Mat, maskMode: Int = MASK_MODE_84): List<Detection> {
        val lb = letterbox(warped)
        // scale 1/255, no resize (already exact), swapRB -> RGB, no crop
        val blob = Dnn.blobFromImage(lb, 1.0 / 255.0, Size(), Scalar(0.0), true, false, CvType.CV_32F)
        lb.release()

        val outs = ArrayList<Mat>()
        try {
            net.setInput(blob)
            // Must be a concrete java.util.ArrayList: OpenCV's JNI resolves ArrayList.size()/get()
            // directly and aborts on any other List (Kotlin's listOf gives Arrays$ArrayList).
            net.forward(outs, arrayListOf("output0", "output1"))
            return decode(outs[0], outs[1], maskMode)
        } finally {
            blob.release()
            outs.forEach { it.release() }
        }
    }

    /**
     * ref_decoder.decode.
     *
     * @param out0 raw net output, dims (1, 38, 23520)
     * @param out1 raw proto output, dims (1, 32, 320, 224)
     */
    fun decode(out0: Mat, out1: Mat, maskMode: Int = MASK_MODE_84): List<Detection> {
        // ---- pull output0 into a flat array laid out [channel][anchor] ----
        val pred = out0.reshape(1, NUM_CH)                 // 38 x N
        val numAnchors = pred.cols()
        val p = FloatArray(NUM_CH * numAnchors)
        pred.get(0, 0, p)
        pred.release()

        // ---- confidence filter (argmax over the 2 class channels) ----
        val cand = ArrayList<Int>()
        val confs = ArrayList<Float>()
        val cids = ArrayList<Int>()
        val c0Off = 4 * numAnchors
        val c1Off = 5 * numAnchors
        for (i in 0 until numAnchors) {
            val s0 = p[c0Off + i]
            val s1 = p[c1Off + i]
            val best: Float
            val cid: Int
            if (s0 >= s1) { best = s0; cid = 0 } else { best = s1; cid = 1 }
            if (best > CONF_THRES) {
                cand.add(i); confs.add(best); cids.add(cid)
            }
        }
        if (cand.isEmpty()) return emptyList()

        // ---- cxcywh -> xyxy ----
        val n = cand.size
        val bx = FloatArray(n * 4)
        for (k in 0 until n) {
            val i = cand[k]
            val cx = p[i]
            val cy = p[numAnchors + i]
            val w = p[2 * numAnchors + i]
            val h = p[3 * numAnchors + i]
            bx[k * 4] = cx - w / 2f
            bx[k * 4 + 1] = cy - h / 2f
            bx[k * 4 + 2] = cx + w / 2f
            bx[k * 4 + 3] = cy + h / 2f
        }

        // ---- class-aware NMS via the +class*7680 offset trick (agnostic=False) ----
        val keep = nms(bx, confs, cids, IOU_THRES).take(MAX_DET)
        if (keep.isEmpty()) return emptyList()

        // ---- mask logits: coef (k x 32) @ protos (32 x mh*mw). No sigmoid. ----
        val mh = out1.size(2)
        val mw = out1.size(3)
        val protos = out1.reshape(1, NUM_COEF)             // 32 x (mh*mw)

        val coef = Mat(keep.size, NUM_COEF, CvType.CV_32F)
        val coefRow = FloatArray(NUM_COEF)
        for ((row, k) in keep.withIndex()) {
            val i = cand[k]
            for (c in 0 until NUM_COEF) coefRow[c] = p[(4 + NUM_CLASSES + c) * numAnchors + i]
            coef.put(row, 0, coefRow)
        }
        val logits = Mat()
        Core.gemm(coef, protos, 1.0, Mat(), 0.0, logits)    // k x (mh*mw)
        coef.release()
        protos.release()

        // ---- per-detection mask assembly ----
        val result = ArrayList<Detection>(keep.size)
        for ((row, k) in keep.withIndex()) {
            val x1 = bx[k * 4]; val y1 = bx[k * 4 + 1]
            val x2 = bx[k * 4 + 2]; val y2 = bx[k * 4 + 3]

            val proto = logits.row(row).reshape(1, mh)      // mh x mw view, CV_32F
            val mask: Mat

            if (maskMode == MASK_MODE_83) {
                // crop at proto resolution -> upsample -> threshold
                val rw = mw.toFloat() / NET_W
                val rh = mh.toFloat() / NET_H
                val small = proto.clone()
                cropInPlace(small, x1 * rw, y1 * rh, x2 * rw, y2 * rh, ZERO)
                val big = Mat()
                Imgproc.resize(
                    small, big, Size(NET_W.toDouble(), NET_H.toDouble()),
                    0.0, 0.0, Imgproc.INTER_LINEAR
                )
                small.release()
                mask = Mat()
                Core.compare(big, ZERO, mask, Core.CMP_GT)  // -> CV_8U 0/255
                big.release()
            } else {
                // upsample -> threshold -> crop at image resolution  (ultralytics 8.4, validated exact)
                val big = Mat()
                Imgproc.resize(
                    proto, big, Size(NET_W.toDouble(), NET_H.toDouble()),
                    0.0, 0.0, Imgproc.INTER_LINEAR
                )
                mask = Mat()
                Core.compare(big, ZERO, mask, Core.CMP_GT)
                big.release()
                cropInPlace(mask, x1, y1, x2, y2, ZERO)
            }
            proto.release()

            // ultralytics: keep = masks.amax((-2,-1)) > 0
            if (Core.minMaxLoc(mask).maxVal > 0.0) {
                result.add(Detection(x1, y1, x2, y2, confs[k], cids[k], mask))
            } else {
                mask.release()
            }
        }
        logits.release()
        return result
    }

    /**
     * ref_decoder.crop — zero everything outside [x1,x2) x [y1,y2).
     *
     * numpy keeps column r where `r >= x1 && r < x2` for integer r, so the surviving range is
     * [ceil(x1), ceil(x2)). Using floor or round here shifts every mask edge by a pixel.
     */
    private fun cropInPlace(m: Mat, x1: Float, y1: Float, x2: Float, y2: Float, zero: Scalar) {
        val w = m.cols()
        val h = m.rows()
        val cx1 = ceil(x1.toDouble()).toInt().coerceIn(0, w)
        val cx2 = ceil(x2.toDouble()).toInt().coerceIn(0, w)
        val cy1 = ceil(y1.toDouble()).toInt().coerceIn(0, h)
        val cy2 = ceil(y2.toDouble()).toInt().coerceIn(0, h)
        if (cx2 <= cx1 || cy2 <= cy1) {
            m.setTo(zero)
            return
        }
        if (cy1 > 0) m.rowRange(0, cy1).setTo(zero)
        if (cy2 < h) m.rowRange(cy2, h).setTo(zero)
        if (cx1 > 0) m.colRange(0, cx1).setTo(zero)
        if (cx2 < w) m.colRange(cx2, w).setTo(zero)
    }

    /**
     * ref_decoder.nms_numpy — greedy IoU NMS, highest score first, class-aware via a large
     * per-class coordinate offset. Returns indices into the candidate arrays.
     */
    private fun nms(bx: FloatArray, confs: List<Float>, cids: List<Int>, iouThres: Float): List<Int> {
        val n = confs.size
        // Ties break differently from numpy's argsort()[::-1] (ascending index vs descending), which
        // is harmless: exact float32 score ties effectively do not occur and either order is valid NMS.
        val order = (0 until n).sortedByDescending { confs[it] }
        val area = FloatArray(n)
        for (i in 0 until n) {
            area[i] = (bx[i * 4 + 2] - bx[i * 4]) * (bx[i * 4 + 3] - bx[i * 4 + 1])
        }
        val suppressed = BooleanArray(n)
        val keep = ArrayList<Int>()
        // Walk by position so only lower-ranked candidates are ever considered for suppression —
        // mirrors the numpy `idx = idx[1:]` shrink. Scanning all of `order` here would let a tie
        // suppress a detection that was already kept.
        for (a in order.indices) {
            val oi = order[a]
            if (suppressed[oi]) continue
            keep.add(oi)
            val offI = cids[oi] * MAX_WH
            val ix1 = bx[oi * 4] + offI
            val iy1 = bx[oi * 4 + 1] + offI
            val ix2 = bx[oi * 4 + 2] + offI
            val iy2 = bx[oi * 4 + 3] + offI
            for (b in a + 1 until order.size) {
                val oj = order[b]
                if (suppressed[oj]) continue
                val offJ = cids[oj] * MAX_WH
                val xx1 = max(ix1, bx[oj * 4] + offJ)
                val yy1 = max(iy1, bx[oj * 4 + 1] + offJ)
                val xx2 = min(ix2, bx[oj * 4 + 2] + offJ)
                val yy2 = min(iy2, bx[oj * 4 + 3] + offJ)
                val iw = max(0f, xx2 - xx1)
                val ih = max(0f, yy2 - yy1)
                val inter = iw * ih
                val union = area[oi] + area[oj] - inter
                if (union > 0f && inter / union >= iouThres) suppressed[oj] = true
            }
        }
        return keep
    }

    /**
     * What app.py does with the mask: `cv2.resize(mask, (1087, 1602))`.
     *
     * This stretches a letterboxed 896-wide mask straight onto the 1087-wide canvas, ignoring the
     * 13/14px padding. That is geometrically wrong — but the error is +17px at x=0, ~0 at x=550 and
     * -16px at x=1087, and the reagent pads sit at x=525..587 where it is under 1px. Replicated
     * deliberately so on-device results match the server. Do not "fix" it in isolation.
     */
    fun maskToCanvas(mask: Mat): Mat {
        val out = Mat()
        Imgproc.resize(
            mask, out, Size(WARP_W.toDouble(), WARP_H.toDouble()),
            0.0, 0.0, Imgproc.INTER_LINEAR
        )
        return out
    }
}
