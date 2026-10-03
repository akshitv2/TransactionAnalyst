package com.pulsefinance.app.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ₹12,34,567.50 with Indian digit grouping (3 digits, then pairs).
 * Done by hand so it behaves the same on every device and JVM.
 */
fun formatInr(value: Double): String {
    val totalPaise = Math.round(abs(value) * 100)
    val rupees = (totalPaise / 100).toString()
    val paise = (totalPaise % 100).toString().padStart(2, '0')
    val grouped = if (rupees.length <= 3) {
        rupees
    } else {
        val head = rupees.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        head + "," + rupees.takeLast(3)
    }
    val sign = if (value < 0 && totalPaise > 0) "-" else ""
    return "$sign₹$grouped.$paise"
}

/** Short axis labels: ₹800, ₹12k, ₹1.5L, ₹2Cr. */
fun formatCompactInr(value: Double): String {
    val a = abs(value)
    val sign = if (value < 0) "-" else ""
    return sign + when {
        a >= 1e7 -> "₹" + short(a / 1e7) + "Cr"
        a >= 1e5 -> "₹" + short(a / 1e5) + "L"
        a >= 1e3 -> "₹" + short(a / 1e3) + "k"
        else -> "₹" + a.roundToInt()
    }
}

private fun short(x: Double): String {
    val r = Math.round(x * 10) / 10.0
    return if (r >= 100 || r % 1.0 == 0.0) r.roundToInt().toString()
    else String.format(Locale.US, "%.1f", r)
}

private val rowDate = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun formatDateTime(dt: LocalDateTime): String = rowDate.format(dt)
fun formatClock(dt: LocalDateTime): String = clock.format(dt)
