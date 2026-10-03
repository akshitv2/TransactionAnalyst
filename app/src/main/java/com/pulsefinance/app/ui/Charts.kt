package com.pulsefinance.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsefinance.app.core.LabeledValue
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private fun niceMax(v: Double): Double {
    if (v <= 0.0) return 1.0
    val base = 10.0.pow(floor(log10(v)))
    val f = v / base
    val nf = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return nf * base
}

/**
 * Smooth area/line chart. Tap or drag across it to read a value.
 * Works for both the daily (monthly view) and per-month (all-time view) series.
 */
@Composable
fun TrendChart(points: List<LabeledValue>, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val leftPad = with(density) { 44.dp.toPx() }
    val rightPad = with(density) { 8.dp.toPx() }
    val topPad = with(density) { 8.dp.toPx() }
    val bottomPad = with(density) { 20.dp.toPx() }

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    fun indexAt(x: Float, width: Float): Int? {
        if (points.isEmpty()) return null
        if (points.size == 1) return 0
        val plotW = width - leftPad - rightPad
        val frac = ((x - leftPad) / plotW).coerceIn(0f, 1f)
        return (frac * (points.size - 1)).roundToInt()
    }

    Column(modifier) {
        val sel = selected?.let { points.getOrNull(it) }
        Text(
            text = if (sel != null) "${sel.label}: ${formatInr(sel.value)}" else "Touch the chart to inspect a point",
            color = if (sel != null) Pulse.Text else Pulse.TextFaint,
            fontSize = 12.sp,
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset -> selected = indexAt(offset.x, size.width.toFloat()) }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = indexAt(change.position.x, size.width.toFloat())
                    }
                }
        ) {
            val plotW = size.width - leftPad - rightPad
            val plotH = size.height - topPad - bottomPad
            val n = points.size
            val maxValue = niceMax(points.maxOfOrNull { it.value } ?: 0.0)
            val labelStyle = TextStyle(fontSize = 10.sp, color = Pulse.TextMuted)

            // Grid + y labels
            for (i in 0..4) {
                val y = topPad + plotH * (1f - i / 4f)
                drawLine(Color(0x4D334155), Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1f)
                val layout = measurer.measure(formatCompactInr(maxValue * i / 4.0), labelStyle)
                drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
            }
            if (n == 0) return@Canvas

            fun px(i: Int): Float = leftPad + if (n == 1) plotW / 2f else plotW * i / (n - 1)
            fun py(v: Double): Float = topPad + plotH * (1f - (v / maxValue).toFloat())

            // x labels (about 5, evenly spaced)
            val step = max(1, (n - 1) / 4)
            var i = 0
            while (i < n) {
                val layout = measurer.measure(points[i].label, labelStyle)
                val x = (px(i) - layout.size.width / 2f).coerceIn(leftPad, size.width - layout.size.width.toFloat())
                drawText(layout, topLeft = Offset(x, size.height - bottomPad + 4f))
                i += step
            }

            // Line + fill. Horizontal tangents keep the curve from dipping below zero.
            val line = Path()
            line.moveTo(px(0), py(points[0].value))
            for (k in 1 until n) {
                val x0 = px(k - 1); val y0 = py(points[k - 1].value)
                val x1 = px(k); val y1 = py(points[k].value)
                val mid = (x0 + x1) / 2f
                line.cubicTo(mid, y0, mid, y1, x1, y1)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(px(n - 1), topPad + plotH)
                lineTo(px(0), topPad + plotH)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(Pulse.Blue.copy(alpha = 0.35f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH,
                ),
            )
            drawPath(
                line,
                Pulse.Blue,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Selection marker
            selected?.let { s ->
                if (s in 0 until n) {
                    val x = px(s)
                    drawLine(Pulse.TextFaint, Offset(x, topPad), Offset(x, topPad + plotH), strokeWidth = 1.dp.toPx())
                    drawCircle(Color.White, 6.dp.toPx(), Offset(x, py(points[s].value)))
                    drawCircle(Pulse.BlueDeep, 4.dp.toPx(), Offset(x, py(points[s].value)))
                }
            }
        }
    }
}

/** Doughnut chart; [centerContent] is drawn in the hole. */
@Composable
fun DonutChart(
    slices: List<LabeledValue>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    diameter: Dp = 170.dp,
    centerContent: @Composable () -> Unit = {},
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = 26.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val total = slices.sumOf { it.value }

            if (total <= 0.0) {
                drawArc(
                    color = Color(0xFF334155), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(strokePx),
                )
                return@Canvas
            }
            val gap = if (slices.size > 1) 1.5f else 0f
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = (slice.value / total * 360.0).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = start + gap / 2f,
                    sweepAngle = max(sweep - gap, 0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx),
                )
                start += sweep
            }
        }
        centerContent()
    }
}

/** Fraction helper used by the bar rows. */
fun fractionOf(value: Double, max: Double): Float =
    if (max <= 0.0) 0f else min(1f, (value / max).toFloat())
