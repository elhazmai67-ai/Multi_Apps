package com.linkvault.app.util

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    /** "Today, 4:12 PM", "Yesterday", "12 Nov", "12 Nov 2025". */
    fun relative(timeMillis: Long, now: Long = System.currentTimeMillis()): String {
        val t = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))
        val dayDiff = dayNumber(n) - dayNumber(t)
        return when {
            dayDiff == 0 -> "Today, $time"
            dayDiff == 1 -> "Yesterday"
            t.get(Calendar.YEAR) == n.get(Calendar.YEAR) ->
                java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timeMillis))
            else -> java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timeMillis))
        }
    }

    fun full(timeMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timeMillis))

    private fun dayNumber(c: Calendar) = c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)

    fun fileStamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
}
