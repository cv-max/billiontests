package com.billiontests.app.model

import org.opencv.core.Point

data class QrRegion(
    val vertical: VerticalRegion,
    val horizontal: HorizontalRegion
)

enum class VerticalRegion { TOP, CENTER, BOTTOM }
enum class HorizontalRegion { LEFT, CENTER, RIGHT }

