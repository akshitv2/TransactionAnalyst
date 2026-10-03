package com.pulsefinance.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette lifted from the original Tailwind dashboard (slate + blue). */
object Pulse {
    val Bg = Color(0xFF080D1A)        // slate-950
    val Card = Color(0xFF0F172A)      // slate-900
    val Raised = Color(0xFF1E293B)    // slate-800
    val Border = Color(0xFF1E293B)
    val Border2 = Color(0xFF334155)   // slate-700

    val Text = Color(0xFFF1F5F9)      // slate-100
    val TextSoft = Color(0xFFCBD5E1)  // slate-300
    val TextMuted = Color(0xFF94A3B8) // slate-400
    val TextFaint = Color(0xFF64748B) // slate-500

    val Blue = Color(0xFF3B82F6)
    val BlueDeep = Color(0xFF2563EB)
    val Rose = Color(0xFFFB7185)
    val Emerald = Color(0xFF34D399)
    val Amber = Color(0xFFFCD34D)
    val AmberDeep = Color(0xFFF59E0B)
    val Indigo = Color(0xFF818CF8)

    val CategoryPalette = listOf(
        Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
        Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFFF43F5E),
        Color(0xFF64748B),
    )

    /** Accent per payment channel (template name); unknown templates fall back to grey. */
    fun methodColor(template: String): Color = when (template) {
        "hdfc_card_spend" -> Color(0xFF6366F1)
        "icici_card_spend" -> Color(0xFF8B5CF6)
        "kotak_upi_transfer" -> Color(0xFF06B6D4)
        "hdfc_upi_transfer" -> Color(0xFF0EA5E9)
        "hdfc_ac_autodebit" -> Color(0xFFF59E0B)
        "hdfc_card_refund" -> Color(0xFF10B981)
        else -> Color(0xFF64748B)
    }
}

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pulse.Blue,
            onPrimary = Color.White,
            background = Pulse.Bg,
            onBackground = Pulse.Text,
            surface = Pulse.Card,
            onSurface = Pulse.Text,
            surfaceVariant = Pulse.Raised,
            onSurfaceVariant = Pulse.TextMuted,
            outline = Pulse.Border2,
        ),
        content = content,
    )
}
