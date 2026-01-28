package com.billiontests.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.model.MissionAnalysisResponse
import com.billiontests.app.model.UrineTestResult
import org.opencv.core.Mat
import org.opencv.core.Point
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun Image.getNV21() : ByteArray {
    val y = this.planes[0].buffer
    val u = this.planes[1].buffer
    val v = this.planes[2].buffer
    val width = this.width
    val height = this.height
    val rowStride = this.planes[0].rowStride
    val uPixelStride = this.planes[1].pixelStride
    val vPixelStride = this.planes[2].pixelStride
    val nv21 = ByteArray(width * height * 3 / 2)
    for (row in 0 until height) {
        y.position(row * rowStride)
        y.get(nv21, row * width, width)
    }

    var dst = 0
    val vRowBuffer = ByteArray(rowStride)
    val uRowBuffer = ByteArray(rowStride)
    for (row in 0 until (height / 2 - 1)) {
        v.position(row * rowStride)
        v.get(vRowBuffer, 0, rowStride)
        u.position(row * rowStride)
        u.get(uRowBuffer, 0, rowStride)
        dst = width * (height + row)
        for (col in 0 until (width / 2)) {
            nv21[dst++] = vRowBuffer[col * vPixelStride]
            nv21[dst++] = uRowBuffer[col * uPixelStride]
        }
    }
    //last line , data length less than rowStride
    v.position((height / 2 - 1) * rowStride)
    u.position((height / 2 - 1) * rowStride)
    for (col in 0 until (width / 2)) {
        nv21[dst++] = vRowBuffer[col * vPixelStride]
        nv21[dst++] = uRowBuffer[col * uPixelStride]
    }
    return nv21
}

fun extractPoints(cornersMat: Mat): List<Point> {
    val pts = ArrayList<Point>()

    val data = FloatArray(2) // because CV_32F

    for (i in 0 until cornersMat.rows()) {
        // cornersMat is 4x2 (4 points, x & y as float)
        cornersMat.get(i, 0, data)
        val x = data[0].toDouble()
        val y = data[1].toDouble()
        pts.add(Point(x, y))
    }

    return pts
}


fun imageProxyToBitmap(nv21:ByteArray, width:Int, height:Int): Bitmap? {

    val yuvImage = YuvImage(nv21, ImageFormat.NV21,width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun generateScanId(): String {
    val uniqueId = UUID.randomUUID().toString().substring(0, 5) // Generate a short unique ID
    return uniqueId.uppercase()
}

fun generateTimeBasedUniqueScanId(userId: String): String {
    // Get current timestamp in the format yyyyMMddHHmmss
    val sdf = SimpleDateFormat("yyyyMMddHHmmss")
    val timestamp = sdf.format(Date())

    // Convert timestamp to base36
    val base36Time = timestamp.toLong().toString(36)

    // User prefix, truncate and replace dash, and convert to lowercase
    val userPrefix = (userId.replace("-", "").substring(0, 5).lowercase(Locale.getDefault()))

    // Build the final ID
    val finalId = userPrefix + base36Time

    return finalId.uppercase(Locale.getDefault())
}

fun getCurrentDateString(): String {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
    return dateFormat.format(Date())
}

fun convertMillisToDate(millis: Long, pattern: String = "dd/MM/yyyy"): String {
    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    val date = Date(millis)
    return dateFormat.format(date)
}

fun ImageAnalysisResponse.toUrineTestResults(): List<UrineTestResult> {
    val mapping = mapOf(
        "BIL" to "Bilirubin",
        "Color" to "Color",
        "ERY" to "Erythrocytes",
        "GLU" to "Glucose",
        "KET" to "Ketone",
        "LEU" to "Leukocyte Esterase",
        "NIT" to "Nitrite",
        "PRO" to "Protein",
        "SG"  to "Specific Gravity",
        "UBG" to "Urobilinogen",
        "pH"  to "pH"
    )

    val normalRanges = mapOf(
        "BIL" to "Negative",
        "Color" to "white",
        "ERY" to "Negative",
        "GLU" to "Negative",
        "KET" to "Negative / Trace",
        "LEU" to "Negative",
        "NIT" to "Negative",
        "PRO" to "Negative",
        "SG"  to "1.000 – 1.030",
        "UBG" to "1 – 12 mg/dL",
        "pH"  to "5.0 – 9.0"
    )

    // Keep a fixed order list
    val order = listOf("BIL","Color","ERY","GLU","KET","LEU","NIT","PRO","SG","UBG","pH")

    // Read values from the response by key
    fun valueFor(key: String) = when (key) {
        "BIL" -> BIL
        "Color" -> Color
        "ERY" -> ERY
        "GLU" -> GLU
        "KET" -> KET
        "LEU" -> LEU
        "NIT" -> NIT
        "PRO" -> PRO
        "SG"  -> SG
        "UBG" -> UBG
        "pH"  -> pH
        else -> ""
    }.orEmpty().trim()

    // Build the list without filtering
    return order.map { key ->
        UrineTestResult(
            parameter = mapping.getValue(key),
            result = valueFor(key).ifBlank { "—" },
            normalRange = normalRanges.getValue(key),
        )
    }

}

fun MissionAnalysisResponse.toUrineTestResults(): List<UrineTestResult> {
    val mapping = mapOf(
        "BIL" to "Bilirubin",
        "BLO" to "Blood",
        "GLU" to "Glucose",
        "KET" to "Ketone",
        "LEU" to "Leukocyte Esterase",
        "NIT" to "Nitrite",
        "PRO" to "Protein",
        "SG"  to "Specific Gravity",
        "URO" to "Urobilinogen",
        "pH"  to "pH"
    )

    val normalRanges = mapOf(
        "BIL" to "Negative",
        "BLO" to "Negative",
        "GLU" to "Negative",
        "KET" to "Negative / Trace",
        "LEU" to "Negative",
        "NIT" to "Negative",
        "PRO" to "Negative",
        "SG"  to "1.000 – 1.030",
        "URO" to "1 – 12 mg/dL",
        "pH"  to "5.0 – 9.0"
    )

    // Keep a fixed order list
    val order = listOf("BIL","BLO","GLU","KET","LEU","NIT","PRO","SG","URO","pH")

    // Read values from the response by key
    fun valueFor(key: String) = when (key) {
        "BIL" -> BIL
        "BLO" -> BLO
        "GLU" -> GLU
        "KET" -> KET
        "LEU" -> LEU
        "NIT" -> NIT
        "PRO" -> PRO
        "SG"  -> SG
        "URO" -> URO
        "pH"  -> pH
        else -> ""
    }.orEmpty().trim()

    // Build the list without filtering
    return order.map { key ->
        UrineTestResult(
            parameter = mapping.getValue(key),
            result = valueFor(key).ifBlank { "—" },
            normalRange = normalRanges.getValue(key),
        )
    }

}



