package com.vamp.haron.common.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vamp.haron.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun Long.toFileSize(): String = toFileSize(LocalContext.current)

fun Long.toFileSize(context: Context): String {
    if (this <= 0) return "0 ${context.getString(R.string.size_bytes)}"
    val units = arrayOf(
        context.getString(R.string.size_bytes),
        context.getString(R.string.size_kb),
        context.getString(R.string.size_mb),
        context.getString(R.string.size_gb),
        context.getString(R.string.size_tb)
    )
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceAtMost(units.lastIndex)
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        this / Math.pow(1024.0, index.toDouble()),
        units[index]
    )
}

fun Long.toDurationString(): String {
    val totalSec = this / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
fun Long.toRelativeDate(): String = toRelativeDate(LocalContext.current)

fun Long.toRelativeDate(context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> context.getString(R.string.time_just_now)
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff).toInt()
            context.getString(R.string.time_minutes_ago, minutes)
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff).toInt()
            context.getString(R.string.time_hours_ago, hours)
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
            context.getString(R.string.time_days_ago, days)
        }
        else -> {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(this))
        }
    }
}
