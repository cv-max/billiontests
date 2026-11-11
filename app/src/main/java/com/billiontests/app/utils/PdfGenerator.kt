package com.billiontests.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import com.billiontests.app.R
import com.billiontests.app.model.PatientInfo
import com.billiontests.app.model.UrineTestResult
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Generates an A4 PDF similar to the screenshot and returns a sharable Uri.
 * The file is saved under: context.getExternalFilesDir(DIRECTORY_DOCUMENTS)/BillionTests/<fileName>.pdf
 */


fun generateUrineReportPdf(
    context: Context,
    appName: String = "BillionTests",
    patient: PatientInfo,
    results: List<UrineTestResult>,
    fileName: String = "Urine_Test_Report.pdf",
    @DrawableRes logoResId: Int? = null,            // <- pass your logo drawable here (e.g., R.drawable.ic_billiontests)
    topMarginBeforeNamePx: Float = 12f,
    isMission: Boolean,// <- extra space before "Name:"
    directoryPath: String,
    volume: String,
    colorM: String,
    appearance: String,
): Uri {
    val pageWidth = 595
    val pageHeight = 842

    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    val headerBlue = Color.parseColor("#0A1E4A")
    val lightGray = Color.parseColor("#F2F4F7")
    val lineGray = Color.parseColor("#D4D7DE")
    val textDark = Color.parseColor("#222222")
    val textMuted = Color.parseColor("#6B7280")
    val titleGray = Color.parseColor("#444444")

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textDark }

    val margin = 24
    var y = 0f

    // Header background
    paint.style = Paint.Style.FILL
    paint.color = headerBlue
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 60f, paint)

    // Optional logo in header (left side)
    var appNameStartX = margin.toFloat()
    logoResId?.let { resId ->
        loadBitmapCompat(context, resId)?.let { raw ->
            val targetH = 36f               // header logo height in px
            val scale = targetH / raw.height.toFloat()
            val logoW = raw.width * scale
            val logoH = raw.height * scale

            val logo = Bitmap.createScaledBitmap(raw, logoW.toInt(), logoH.toInt(), true)
            // draw a white backdrop (optional) to make it pop on dark header
            // paint.color = Color.WHITE; canvas.drawRoundRect(margin.toFloat(), 12f, margin + logoW + 8f, 12f + logoH + 8f, 6f, 6f, paint)

            canvas.drawBitmap(logo, margin.toFloat(), 12f, null)
            appNameStartX = margin + logoW + 10f // push app name right of logo
        }
    }

    // App name (left) and "Test Report" (right)
    text.color = Color.WHITE
    text.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    text.textSize = 14f
    canvas.drawText(appName, appNameStartX, 38f, text)

    text.textAlign = Paint.Align.RIGHT
    canvas.drawText("Test Report", (pageWidth - margin).toFloat(), 38f, text)
    text.textAlign = Paint.Align.LEFT
    y = 70f

    // >>> Extra margin before "Name:"
    y += topMarginBeforeNamePx

    // Patient/Report info
    text.color = textDark
    text.typeface = Typeface.DEFAULT_BOLD
    text.textSize = 12f
    canvas.drawText("Name:", margin.toFloat(), y, text)
    canvas.drawText("Report ID:", (pageWidth / 2f) + 10f, y, text)
    text.typeface = Typeface.DEFAULT
    canvas.drawText(patient.name, margin + 55f, y, text)
    canvas.drawText(patient.reportId, (pageWidth / 2f) + 85f, y, text)
    y += 18f

    text.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText("Age:", margin.toFloat(), y, text)
    canvas.drawText("Sample type:", (pageWidth / 2f) + 10f, y, text)
    text.typeface = Typeface.DEFAULT
    canvas.drawText("${patient.ageYears} ", margin + 55f, y, text)
    canvas.drawText(patient.sampleType, (pageWidth / 2f) + 85f, y, text)
    y += 18f

    text.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText("Gender:", margin.toFloat(), y, text)
    canvas.drawText("Sample collected on:", (pageWidth / 2f) + 10f, y, text)
    text.typeface = Typeface.DEFAULT
    canvas.drawText(patient.gender.uppercase(Locale.getDefault()), margin + 55f, y, text)
    canvas.drawText(patient.collectedOn, (pageWidth / 2f) + 130f, y, text)
    y += 18f

    text.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText("Reference:", margin.toFloat(), y, text)
    canvas.drawText("Reported generated on:", (pageWidth / 2f) + 10f, y, text)
    text.typeface = Typeface.DEFAULT
    canvas.drawText(patient.reference, margin + 75f, y, text)
    canvas.drawText(patient.reportedOn, (pageWidth / 2f) + 160f, y, text)
    y += 20f

    // Divider
    paint.color = lineGray
    paint.strokeWidth = 1f
    canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, paint)
    y += 22f

    // Title
    text.color = titleGray
    text.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    text.textSize = 16f
    text.textAlign = Paint.Align.CENTER
    canvas.drawText("Urine Test Strip Analysis Report", (pageWidth / 2f), y, text)
    text.textAlign = Paint.Align.LEFT
    y += 18f

    // Subtitle
    text.color = textMuted
    text.typeface = Typeface.DEFAULT_BOLD
    text.textSize = 10f
    canvas.drawText("Urine Examination", margin.toFloat(), y, text)
    y += 14f
    text.typeface = Typeface.DEFAULT
    text.textSize = 9f
    canvas.drawText("CHEMICAL EXAMINATION", margin.toFloat(), y, text)
    y += 10f

    // ===== Table (NO UNIT COLUMN) =====
    val colX = floatArrayOf(
        margin.toFloat(),               // Investigation
        (margin + 260).toFloat(),       // Observed Value
        (pageWidth - margin).toFloat()  // Reference range (end X)
    )
    val rowHeight = 22f

    // Header background
    paint.color = lightGray
    canvas.drawRect(colX[0], y, colX[2], y + rowHeight, paint)

    // Header text
    text.color = textDark
    text.typeface = Typeface.DEFAULT_BOLD
    text.textSize = 11f
    val headerY = y + 15f
    canvas.drawText("Investigation", colX[0] + 8f, headerY, text)
    canvas.drawText("Observed Value", colX[1] + 8f, headerY, text)
    canvas.drawText("Reference range", colX[1] + 160f, headerY, text)
    y += rowHeight

    // Row drawer
    text.typeface = Typeface.DEFAULT
    text.textSize = 11f
    paint.color = lineGray
    paint.strokeWidth = 1f

    fun drawRow(inv: String, obs: String, ref: String) {
        canvas.drawLine(colX[0], y + rowHeight, colX[2], y + rowHeight, paint)
        val ty = y + 15f
        text.color = textDark
        canvas.drawText(inv, colX[0] + 8f, ty, text)
        canvas.drawText(obs, colX[1] + 8f, ty, text)
        text.color = if (ref.equals("Negative", true)) textMuted else textDark
        canvas.drawText(ref, colX[1] + 160f, ty, text)
        y += rowHeight
    }

    fun findRes(nameStartsWith: String): UrineTestResult? =
        results.firstOrNull { it.parameter.startsWith(nameStartsWith, ignoreCase = true) }

    drawRow(
        "Specific gravity (SG)",
        findRes("Specific Gravity")?.result ?: "-",
        findRes("Specific Gravity")?.normalRange ?: "1.005–1.025"
    )
    drawRow(
        "pH",
        findRes("pH")?.result ?: "-",
        findRes("pH")?.normalRange ?: "5.0 - 9.0"
    )
    drawRow(
        "Leukocytes (LEU)",
        findRes("Leukocyte")?.result ?: "-",
        findRes("Leukocyte")?.normalRange ?: "Negative"
    )
    drawRow(
        "Nitrite (NIT)",
        findRes("Nitrite")?.result ?: "-",
        findRes("Nitrite")?.normalRange ?: "Negative"
    )
    drawRow(
        "Protein (Albumin) (PRO)",
        findRes("Protein")?.result ?: "-",
        findRes("Protein")?.normalRange ?: "Negative"
    )
    drawRow(
        "Glucose (Urine) (GLU)",
        findRes("Glucose")?.result ?: "-",
        findRes("Glucose")?.normalRange ?: "Negative"
    )
    drawRow(
        "Ketone (KET)",
        findRes("Ketone")?.result ?: "-",
        findRes("Ketone")?.normalRange ?: "Negative"
    )
    if (isMission) {
        drawRow(
            "Urobilinogen (URO)",
            findRes("Urobilinogen")?.result ?: "-",
            findRes("Urobilinogen")?.normalRange ?: "1 - 12 mg/dL"
        )
    } else {
        drawRow(
            "Urobilinogen (UBG)",
            findRes("Urobilinogen")?.result ?: "-",
            findRes("Urobilinogen")?.normalRange ?: "1 - 12 mg/dL"
        )
    }
    drawRow(
        "Bilirubin (BIL)",
        findRes("Bilirubin")?.result ?: "-",
        findRes("Bilirubin")?.normalRange ?: "Negative"
    )
    if (isMission.not()) {
        drawRow(
            "Erythrocytes/Hb (ERY/Hb)",
            findRes("Erythrocytes")?.result ?: "-",
            findRes("Erythrocytes")?.normalRange ?: "Negative"
        )
        drawRow(
            "Color (COL)",
            findRes("Color")?.result ?: "-",
            findRes("Color")?.normalRange ?: "-"
        )
    } else {
        drawRow(
            "Blood (BLO)",
            findRes("Blood")?.result ?: "-",
            findRes("Blood")?.normalRange ?: "Negative"
        )
    }

    val hasMetaData = volume.isNotBlank() || colorM.isNotBlank() || appearance.isNotBlank()

    if (hasMetaData) {
        y += 20f

        text.color = titleGray
        text.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        text.textSize = 13f
        canvas.drawText("Physical Examination", margin.toFloat(), y, text)
        y += 10f

        val metaColX = floatArrayOf(
            margin.toFloat(),
            (margin + 260).toFloat(),
            (pageWidth - margin).toFloat()
        )

        paint.color = lightGray
        canvas.drawRect(metaColX[0], y, metaColX[2], y + rowHeight, paint)

        text.color = textDark
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 11f
        canvas.drawText("Parameter", metaColX[0] + 8f, y + 15f, text)
        canvas.drawText("Value", metaColX[1] + 8f, y + 15f, text)
        y += rowHeight

        text.typeface = Typeface.DEFAULT
        text.textSize = 11f
        paint.color = lineGray
        paint.strokeWidth = 1f

        fun drawMetaRow(label: String, value: String) {
            if (value.isBlank()) return

            canvas.drawLine(metaColX[0], y + rowHeight, metaColX[2], y + rowHeight, paint)

            text.color = textDark
            canvas.drawText(label, metaColX[0] + 8f, y + 15f, text)
            canvas.drawText(value, metaColX[1] + 8f, y + 15f, text)

            y += rowHeight
        }

        drawMetaRow("Volume", volume)
        drawMetaRow("Color", colorM)
        drawMetaRow("Appearance", appearance)
    }

    y += 20f

    // Notes
    text.color = textMuted
    text.textSize = 9f
    val notes = listOf(
        "• The urine routine is a screening test.",
        "• Pre-test: first-void, mid-stream urine in a clean sterile container to avoid contamination.",
        "• Negative nitrite does not exclude UTI; interpret with clinical picture.",
        "• Trace proteinuria can occur with exercise, high protein diet, etc.",
        "• False positives for bile pigments, proteins, glucose and nitrites may occur due to certain drugs/dyes."
    )
    for (n in notes) {
        canvas.drawText(n, margin.toFloat(), y, text)
        y += 12f
    }

    doc.finishPage(page)

//    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), appName)
    val dir = File(directoryPath)
    if (!dir.exists()) dir.mkdirs()
    val outFile = File(dir, fileName)
    Log.e("PdfGenerator", "Pdf saved to: ${outFile.absolutePath}")
    FileOutputStream(outFile).use { fos -> doc.writeTo(fos) }
    doc.close()

    return FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        outFile
    )
}


/** Loads bitmap from drawable (works with bitmap & vector drawables) */
private fun loadBitmapCompat(context: Context, @DrawableRes resId: Int): Bitmap? {
    val d = AppCompatResources.getDrawable(context, resId) ?: return null
    return when (d) {
        is BitmapDrawable -> d.bitmap
        else -> {
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 200
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 200
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            bmp
        }
    }
}


