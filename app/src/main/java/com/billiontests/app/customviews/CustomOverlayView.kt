package com.billiontests.app.customviews

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CustomOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#A6000000") // Semi-transparent black
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#FF9800") // Orange border color
        style = Paint.Style.STROKE
        strokeWidth = 6f // Adjust stroke width if needed
        pathEffect = DashPathEffect(floatArrayOf(10f, 20f), 0f) // Dotted effect
    }

    private var cutoutRect = RectF() // Define cutoutRect but leave it to be set dynamically
    private val cornerRadius = 40f // Rounded corners

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw the semi-transparent black background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw the cutout (transparent area)
        val clearPaint = clearPaint()
        canvas.drawRoundRect(cutoutRect, cornerRadius, cornerRadius, clearPaint)

        // Draw the dotted border around the cutout
        canvas.drawRoundRect(cutoutRect, cornerRadius, cornerRadius, borderPaint)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        // Dynamically calculate the center-cutout rectangle
        val rectWidth = width * 0.5f // 70% of the screen width
        val rectHeight = height * 0.7f // 40% of the screen height

        val left = (width - rectWidth) / 2
        val top = (height - rectHeight) / 2
        val right = left + rectWidth
        val bottom = top + rectHeight

        // Set the cutoutRect dynamically
        cutoutRect.set(left, top, right, bottom)

        // Invalidate the view to trigger a redraw with new rectangle
        invalidate()
    }

    // Clear paint used to erase the cutout area
    private fun clearPaint(): Paint {
        return Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }
}
