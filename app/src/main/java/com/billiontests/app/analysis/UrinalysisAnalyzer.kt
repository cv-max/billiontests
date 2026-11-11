package com.billiontests.app.analysis

import android.content.Context
import android.util.Log
import com.billiontests.app.R
import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.model.MissionAnalysisResponse
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs

/**
 * On-device replacement for `POST /api/analyze` and `POST /api/analyze_mission`.
 *
 * Mirrors `urinalysis_improved` / `urinalysis_improved_mission` in `billion_test/app.py`, which
 * differ only in expected pad count and lookup tables — so both run through [analyze] with a
 * different [CardConstants.CardType].
 */
object UrinalysisAnalyzer {

    private const val TAG = "UrinalysisAnalyzer"

    /**
     * Below this many detected pads a partial read is not worth surfacing and we fail into the
     * retry dialog instead. "More than 5" pads → return whatever ranks were found. Applies to both
     * the standard and mission cards.
     */
    private const val MIN_PADS_FOR_PARTIAL = 5

    sealed class Outcome {
        /**
         * @param values assay short name -> printed clinical value, e.g. "GLU" -> "Negative"
         * @param confidences parallel to the pad order, 0..100
         * @param qualityIssues non-fatal problems (marker count, lighting, pad count)
         */
        class Success(
            val values: Map<String, String>,
            val confidences: List<Double>,
            val qualityIssues: List<String>,
        ) : Outcome()

        class Failure(val reason: String, val qualityIssues: List<String>) : Outcome()
    }

    /**
     * Reads [imagePath] and analyses it. Blocking and CPU-heavy — call from a background
     * dispatcher, never the main thread.
     *
     * The file is read with [Imgcodecs.imread] so the channel order is BGR, matching `cv2.imread`
     * on the server. Decoding a Bitmap instead would yield RGBA and silently shift every colour
     * match.
     */
    fun analyze(
        context: Context,
        imagePath: String,
        cardType: CardConstants.CardType,
    ): Outcome {
        val image = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_COLOR)
        if (image.empty()) {
            image.release()
            return Outcome.Failure(context.getString(R.string.analysis_image_open_failed), emptyList())
        }
        try {
            return analyzeMat(context, image, cardType)
        } finally {
            image.release()
        }
    }

    fun analyzeMat(
        context: Context,
        image: Mat,
        cardType: CardConstants.CardType,
    ): Outcome {
        val issues = ArrayList<String>()

        // ---- 1. ArUco markers ----
        val centers = CardGeometry.markerCenters(image)
        if (centers.size != 4) {
            issues.add("Expected 4 ArUco markers, found ${centers.size}")
        }
        // Three markers still pin the card plane: CardGeometry reconstructs the missing corner.
        // Fewer than three cannot, so bail out into the retry dialog.
        val corners = CardGeometry.completeCorners(centers)
        if (corners == null) {
            return Outcome.Failure(context.getString(R.string.analysis_card_not_found), issues)
        }

        // ---- 2. perspective rectification ----
        val warped = try {
            CardGeometry.warp(image, corners)
        } catch (e: Exception) {
            Log.e(TAG, "perspective transform failed", e)
            issues.add("Perspective transform failed: ${e.message}")
            return Outcome.Failure(context.getString(R.string.analysis_card_alignment_failed), issues)
        }

        try {
            // ---- 3. lighting uniformity ----
            val lighting = LightingCheck.check(warped)
            if (!lighting.uniform) {
                issues.add("Non-uniform lighting detected at ${lighting.offendingPositions.size} positions")
            }

            // ---- 4. segmentation ----
            val detections = try {
                SegDecoder.run(ModelStore.segNet(context), warped)
            } catch (e: Exception) {
                Log.e(TAG, "segmentation failed", e)
                issues.add("Detection failed: ${e.message}")
                return Outcome.Failure(context.getString(R.string.analysis_strip_processing_failed), issues)
            }

            try {
                val padDetections = detections.filter { it.classId == SegDecoder.CLASS_PAD }
                if (padDetections.size != cardType.padCount) {
                    issues.add("Expected ${cardType.padCount} test strips, detected ${padDetections.size}")
                    // A full read needs every pad, but a partial capture that still found more than
                    // MIN_PADS_FOR_PARTIAL is worth returning: the pad loop below maps each detected
                    // pad by its top-to-bottom rank, so a short read yields values for the first N
                    // assays and simply stops. Only bail out when too few pads are found to be useful
                    // (routes into the existing retry dialog, unlike app.py's silent empty body).
                    if (padDetections.size <= MIN_PADS_FOR_PARTIAL) {
                        return Outcome.Failure(
                            context.getString(R.string.analysis_too_few_pads, padDetections.size, cardType.padCount), issues
                        )
                    }
                }

                // ---- 5. pads -> mean colour, ordered top-to-bottom ----
                val pads = PadExtractor.extract(warped, padDetections)

                // ---- 6. match each pad against its candidate swatches ----
                val values = LinkedHashMap<String, String>()
                val confidences = ArrayList<Double>()

                for (rank in pads.indices) {
                    if (rank >= cardType.names.size) break
                    val pad = pads[rank]
                    val name = cardType.names[rank]
                    val table = cardType.results[rank]

                    val padMean = pad.meanBgr
                    if (padMean == null || padMean.any { it.isNaN() }) {
                        values[name] = table.getOrElse(0) { "Negative" }
                        confidences.add(0.0)
                        continue
                    }

                    val m = ColorMatcher.match(warped, padMean, cardType.mapping[rank])
                    values[name] = if (m.confidence >= CardConstants.MIN_CONFIDENCE) {
                        table.getOrElse(m.index) { "Negative" }
                    } else {
                        table.getOrElse(0) { "Negative" }
                    }
                    confidences.add(m.confidence)
                }

                return Outcome.Success(values, confidences, issues)
            } finally {
                detections.forEach { it.mask.release() }
            }
        } finally {
            warped.release()
        }
    }
}

/** Maps analyzer output onto the response type the standard-card UI already consumes. */
fun Map<String, String>.toImageAnalysisResponse() = ImageAnalysisResponse(
    BIL = this["BIL"].orEmpty(),
    Color = this["Color"].orEmpty(),
    ERY = this["ERY/Hb"].orEmpty(),
    GLU = this["GLU"].orEmpty(),
    KET = this["KET"].orEmpty(),
    LEU = this["LEU"].orEmpty(),
    NIT = this["NIT"].orEmpty(),
    PRO = this["PRO"].orEmpty(),
    SG = this["SG"].orEmpty(),
    UBG = this["UBG"].orEmpty(),
    pH = this["pH"].orEmpty(),
)

/** Maps analyzer output onto the response type the mission-card UI already consumes. */
fun Map<String, String>.toMissionAnalysisResponse() = MissionAnalysisResponse(
    BIL = this["BIL"].orEmpty(),
    BLO = this["BLO"].orEmpty(),
    GLU = this["GLU"].orEmpty(),
    KET = this["KET"].orEmpty(),
    LEU = this["LEU"].orEmpty(),
    NIT = this["NIT"].orEmpty(),
    PRO = this["PRO"].orEmpty(),
    SG = this["SG"].orEmpty(),
    URO = this["URO"].orEmpty(),
    pH = this["pH"].orEmpty(),
)
