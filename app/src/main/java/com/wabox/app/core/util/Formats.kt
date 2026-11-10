package com.wabox.app.core.util

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

object Formats {
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, if (mb < 10) "%.1f MB" else "%.0f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun duration(ms: Long): String {
        val total = max(0L, (ms + 500) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = max(0L, now - timestamp)
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun dateTime(timestamp: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

    /** Section title for the saved gallery: Today / Yesterday / weekday / Month year. */
    fun daySection(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val day = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        fun dayIndex(c: Calendar) = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
        val delta = dayIndex(today) - dayIndex(day)
        return when {
            delta <= 0 -> "Today"
            delta == 1 -> "Yesterday"
            delta < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
            day.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(timestamp))
            else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

object Hashing {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).toHex()

    /** Fingerprint from head + tail chunks and the size: cheap, and stable across renames. */
    fun partialFingerprint(head: ByteArray, tail: ByteArray, size: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(head)
        md.update(tail)
        md.update(size.toString().toByteArray())
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
