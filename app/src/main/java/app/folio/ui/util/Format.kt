package app.folio.ui.util

import android.content.Context
import app.folio.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

object Format {

    fun fileSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun duration(context: Context, milliseconds: Long): String {
        val totalMinutes = (milliseconds / 60_000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.duration_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.duration_minutes, minutes)
        }
    }

    fun shortDuration(milliseconds: Long): String {
        val totalMinutes = (milliseconds / 60_000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun timer(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun percent(progress: Float): Int = (progress.coerceIn(0f, 1f) * 100).roundToInt()

    fun date(timestamp: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate())

    /** "Today", "Yesterday", "5 days ago", then a real date. */
    fun relativeDate(timestamp: Long?, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (timestamp == null || timestamp <= 0) return null
        val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            days < 30L -> "${days / 7} weeks ago"
            else -> date(timestamp)
        }
    }
}
