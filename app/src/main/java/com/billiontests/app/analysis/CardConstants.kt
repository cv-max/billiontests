package com.billiontests.app.analysis

/**
 * Calibration geometry and result lookup tables, transcribed from `billion_test/app.py`.
 *
 * All coordinates are in the warped canvas space (1087x1602) produced by [CardGeometry.warp].
 *
 * app.py also defines an `LL` table interleaving [LL1] and [LL2]; it is never read there, so it is
 * deliberately not reproduced here.
 */
object CardConstants {

    /** Reference swatch column centres — left block. */
    private val X_LEFT = intArrayOf(71, 143, 216, 289)

    /** Reference swatch column centres — right block. */
    private val X_RIGHT = intArrayOf(796, 869, 942, 1014)

    /** Reference swatch row centres, 14 rows. */
    private val Y_ROWS = intArrayOf(
        295, 350, 450, 510, 610, 670, 770, 830, 925, 985, 1085, 1145, 1245, 1300
    )

    /** Half-width of the square patch sampled at each reference point (app.py uses +/-15). */
    const val PATCH_HALF = 15

    private fun grid(xs: IntArray): Array<IntArray> =
        Array(Y_ROWS.size * xs.size) { k ->
            intArrayOf(xs[k % xs.size], Y_ROWS[k / xs.size])
        }

    /** 56 left-block reference points, row-major. Indices here are what `mappingFor` refers to. */
    val LL1: Array<IntArray> = grid(X_LEFT)

    /** 56 right-block reference points, row-major. Used only by the lighting-uniformity check. */
    val LL2: Array<IntArray> = grid(X_RIGHT)

    // ---- colour matching configuration (app.py module constants) ----

    /** Maximum distance treated as a usable match when scoring confidence. */
    const val DISTANCE_THRESHOLD = 50.0

    /**
     * app.py sets this to 0, so the confidence gate never rejects a match. Kept explicit so the
     * behaviour is visible rather than implied.
     */
    const val MIN_CONFIDENCE = 0.0

    /** Manhattan distance above which mirrored reference patches count as unevenly lit. */
    const val LIGHTING_CHECK_THRESHOLD = 300.0

    // ---- card layouts ----

    /**
     * One card variant. Pads are indexed by their top-to-bottom rank on the strip, which is also
     * the key used by all three tables.
     */
    class CardType(
        val padCount: Int,
        /** rank -> indices into [LL1] that are the candidate reference swatches for that pad */
        val mapping: Array<IntArray>,
        /** rank -> printed clinical values, parallel to [mapping] */
        val results: Array<Array<String>>,
        /** rank -> assay short name, i.e. the JSON key */
        val names: Array<String>,
    )

    /** 11-pad card, served by `POST /api/analyze` / `urinalysis_improved`. */
    val STANDARD = CardType(
        padCount = 11,
        mapping = arrayOf(
            intArrayOf(49, 50, 51, 52, 53, 54, 55),          // 0  Color
            intArrayOf(40, 41, 42, 43, 44, 45, 46, 47, 48),  // 1  ERY/Hb
            intArrayOf(36, 37, 38, 39),                      // 2  BIL
            intArrayOf(31, 32, 33, 34, 35),                  // 3  UBG
            intArrayOf(27, 28, 29, 30),                      // 4  KET
            intArrayOf(22, 23, 24, 25, 26),                  // 5  GLU
            intArrayOf(18, 19, 20, 21),                      // 6  PRO
            intArrayOf(16, 17),                              // 7  NIT
            intArrayOf(12, 13, 14, 15),                      // 8  LEU
            intArrayOf(7, 8, 9, 10, 11),                     // 9  pH
            intArrayOf(0, 1, 2, 3, 4, 5, 6),                 // 10 SG
        ),
        results = arrayOf(
            arrayOf("No Color", "Pele Yellow", "Translucent Yellow", "Dark Yellow", "Amber", "Burnt Orange", "Red"),
            arrayOf("Negative", "10", "25", "50", "250", "10", "25", "50", "250"),
            arrayOf("Negative", "1 mg/dL", "3 mg/dL", "6 mg/dL"),
            arrayOf("Normal", "1 mg/dL", "4 mg/dL", "8 mg/dL", "12 mg/dL"),
            // app.py really does list "Negative" twice here; kept so indices line up with the card.
            arrayOf("Negative", "Negative", "50 mg/dL", "150 mg/dL"),
            arrayOf("Normal", "50 mg/dL", "100 mg/dL", "300 mg/dL", "1000 mg/dL"),
            arrayOf("Negative", "30 mg/dL", "100 mg/dL", "500 mg/dL"),
            arrayOf("Negative", "Positive"),
            arrayOf("Negative", "15 Leu/muL", "75 Leu/muL", "500 Leu/muL"),
            arrayOf("5", "6", "7", "8", "9"),
            arrayOf("1.000", "1.005", "1.010", "1.015", "1.020", "1.025", "1.030"),
        ),
        names = arrayOf("Color", "ERY/Hb", "BIL", "UBG", "KET", "GLU", "PRO", "NIT", "LEU", "pH", "SG"),
    )

    /** 10-pad card, served by `POST /api/analyze_mission` / `urinalysis_improved_mission`. */
    val MISSION = CardType(
        padCount = 10,
        mapping = arrayOf(
            intArrayOf(0, 1, 2, 3, 4),                       // 0  LEU
            intArrayOf(5, 6, 7),                             // 1  NIT
            intArrayOf(8, 9, 10, 11, 12),                    // 2  URO
            intArrayOf(13, 14, 15, 16, 17, 18),              // 3  PRO
            intArrayOf(19, 20, 21, 22, 23, 24, 25),          // 4  pH
            intArrayOf(26, 27, 28, 29, 30, 31, 32),          // 5  BLO
            intArrayOf(33, 34, 35, 36, 37, 38, 39),          // 6  SG
            intArrayOf(40, 41, 42, 43, 44, 45),              // 7  KET
            intArrayOf(46, 47, 48, 49),                      // 8  BIL
            intArrayOf(50, 51, 52, 53, 54, 55),              // 9  GLU
        ),
        results = arrayOf(
            arrayOf("Negative", "Trace", "Small +", "Moderate ++", "Large +++"),
            arrayOf("Negative", "Low Positive", "High Positive"),
            arrayOf("Normal(0.2 mg/dL)", "Normal(1 mg/dL)", "2 mg/dL", "4 mg/dL", "8 mg/dL"),
            arrayOf("Negative", "Trace", "30 mg/dL", "100 mg/dL", "300 mg/dL", "2000 mg/dL"),
            arrayOf("5.0", "6.0", "6.5", "7.0", "7.5", "8.0", "8.5"),
            arrayOf(
                "Negative", "Non-Hemolyzed(Trace)", "Non-Hemolyzed(Moderate)", "Hemolyzed(Trace)",
                "Hemolyzed(Small)", "Hemolyzed(Moderate)", "Hemolyzed(Large)"
            ),
            arrayOf("1.000", "1.005", "1.010", "1.015", "1.020", "1.025", "1.030"),
            arrayOf("Negative", "Trace(5 mg/dL)", "Small(15 mg/dL)", "Moderate(40 mg/dL)", "Large(80 mg/dL)", "Large(160 mg/dL)"),
            arrayOf("Negative", "Small", "Moderate", "Large"),
            arrayOf("Negative", "100 mg/dL", "250 mg/dL", "500 mg/dL", "1000 mg/dL", "2000 mg/dL or more"),
        ),
        names = arrayOf("LEU", "NIT", "URO", "PRO", "pH", "BLO", "SG", "KET", "BIL", "GLU"),
    )
}
