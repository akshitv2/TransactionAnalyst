package com.pulsefinance.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsefinance.app.core.Analytics

/** Rounded slate card used for every dashboard section. */
@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Pulse.Card)
            .border(BorderStroke(1.dp, Pulse.Border), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, color = Pulse.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, color = Pulse.TextMuted, fontSize = 12.sp)
        }
    }
}

/** A small pill that opens a dropdown menu; [label] is what the pill currently shows. */
@Composable
fun <T> PillDropdown(
    label: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Pulse.Raised)
                .border(BorderStroke(1.dp, Pulse.Border2), RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = Pulse.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text("▾", color = Pulse.TextMuted, fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** Label + amount above a thin proportional bar. */
@Composable
fun BarRow(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (dimmed) Pulse.TextFaint else Pulse.TextSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                valueText,
                color = if (dimmed) Pulse.TextFaint else Pulse.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Pulse.Bg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun MethodBadge(template: String) {
    val base = Pulse.methodColor(template)
    val shape = RoundedCornerShape(6.dp)
    Text(
        Analytics.methodShortLabel(template),
        color = lerp(base, Color.White, 0.45f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(base.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, base.copy(alpha = 0.35f)), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun CategoryChip(category: String) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        category,
        color = Pulse.TextSoft,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .background(Pulse.Raised)
            .border(BorderStroke(1.dp, Pulse.Border2), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
