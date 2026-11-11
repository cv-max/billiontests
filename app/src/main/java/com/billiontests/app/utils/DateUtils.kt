package com.billiontests.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun convertDateFormat(dateString: String, outputFormat: String): String {
    return try {
        val inputFormat = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val targetFormat = SimpleDateFormat(outputFormat, Locale.getDefault())
        date?.let { targetFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

fun getCurrentDateString(format:String = "dd-MM-yyyy hh:mm a"): String {
    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
    return dateFormat.format(Date())
}

fun formatTimestamp(
    timestamp: Long,
    pattern: String,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val formatter = SimpleDateFormat(pattern, locale)
    formatter.timeZone = timeZone
    return formatter.format(Date(timestamp))
}