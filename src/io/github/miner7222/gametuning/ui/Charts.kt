// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalLayoutApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.AutoEvent
import io.github.miner7222.gametuning.data.Axis
import io.github.miner7222.gametuning.data.Bucketed
import io.github.miner7222.gametuning.data.formatClock

/** One line of a chart. Series differ in width and dash as well as colour. */
class ChartSeries(
    val label: String, val color: Color, val width: Dp, val dashed: Boolean, val data: Bucketed,
)

class ChartSpec(
    val description: String,
    val series: List<ChartSeries>,
    val axis: Axis,
    /** Label of a grid value; the second argument is true for the highest grid line. */
    val tickLabel: (Float, Boolean) -> String,
    /** Value of the dashed target line, or null. */
    val target: Float? = null,
    /** Per-point target drawn as a dashed step line instead of the flat [target]; NaN marks a gap. */
    val targetSteps: Bucketed? = null,
    val targetLegend: String? = null,
    /** Shown instead of the lines when every series is empty. */
    val emptyNote: String? = null,
)

private val AXIS_WIDTH = 46.dp
private val AXIS_GAP = 6.dp

/** Line chart on a Compose canvas: y-axis labels at the grid lines, event markers on the time axis. */
@Composable
fun RecordChart(
    spec: ChartSpec, durationMs: Int, events: List<AutoEvent>, height: Dp, modifier: Modifier = Modifier,
    originMs: Int = 0, keepBounds: Pair<Int, Int>? = null,
) {
    val cs = MaterialTheme.colorScheme
    val grid = cs.outlineVariant
    val targetColor = cs.outline
    val markerColor = cs.tertiary
    val surface = cs.surfaceContainer
    val axis = spec.axis
    val span = (axis.max - axis.min).coerceAtLeast(0.0001f)
    val duration = durationMs.coerceAtLeast(1)
    val empty = spec.series.all { s -> s.data.values.all { it.isNaN() } }
    val eventsText = events.joinToString { formatClock(it.timeMs) }
    val description = if (events.isEmpty()) spec.description
    else spec.description + " " + stringResource(R.string.chart_events_marked, eventsText)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(AXIS_WIDTH).height(height)) {
                spec.axis.ticks.forEachIndexed { i, t ->
                    val y = height * (1f - (t - axis.min) / span)
                    Text(
                        spec.tickLabel(t, i == spec.axis.ticks.lastIndex),
                        Modifier.width(AXIS_WIDTH).offset(y = y - 8.dp),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                        textAlign = TextAlign.End, maxLines = 1,
                    )
                }
            }
            Box(Modifier.padding(start = AXIS_GAP).weight(1f).height(height)) {
                Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
                    val w = size.width
                    val h = size.height
                    fun yOf(v: Float) = h - (v - axis.min) / span * h
                    fun xOf(ms: Int) = (ms - originMs).toFloat() / duration * w
                    axis.ticks.forEach { drawLine(grid, Offset(0f, yOf(it)), Offset(w, yOf(it)), 1.dp.toPx()) }
                    drawLine(grid, Offset(0f, h), Offset(w, h), 1.dp.toPx())
                    val steps = spec.targetSteps
                    if (steps != null) {
                        drawTargetSteps(steps, duration + originMs, targetColor, ::xOf, ::yOf, axis.min, axis.max)
                    } else spec.target?.let { t ->
                        if (t in axis.min..axis.max) {
                            drawLine(
                                targetColor, Offset(0f, yOf(t)), Offset(w, yOf(t)), 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                    }
                    for (s in spec.series) drawSeries(s, ::xOf, ::yOf)
                    keepBounds?.let { (start, end) ->
                        drawRect(surface.copy(alpha = .72f), Offset.Zero, androidx.compose.ui.geometry.Size(xOf(start).coerceAtLeast(0f), h))
                        drawRect(surface.copy(alpha = .72f), Offset(xOf(end), 0f), androidx.compose.ui.geometry.Size((w - xOf(end)).coerceAtLeast(0f), h))
                    }
                }
                if (empty && spec.emptyNote != null) {
                    Text(
                        spec.emptyNote, Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        // Time axis with a marker where an automatic change acted.
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(AXIS_WIDTH + AXIS_GAP))
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val w = size.width
                val cy = size.height / 2
                drawLine(grid, Offset(0f, cy), Offset(w, cy), 2.dp.toPx(), cap = StrokeCap.Round)
                for (e in events) {
                    val cx = (e.timeMs.toFloat() / duration * w).coerceIn(6.dp.toPx(), w - 6.dp.toPx())
                    drawCircle(surface, 7.dp.toPx(), Offset(cx, cy))
                    drawCircle(markerColor, 5.dp.toPx(), Offset(cx, cy))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(AXIS_WIDTH + AXIS_GAP))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val style = MaterialTheme.typography.labelMedium
                Text("0:00", style = style, color = cs.onSurfaceVariant)
                Text(formatClock(durationMs / 2), style = style, color = cs.onSurfaceVariant)
                Text(formatClock(durationMs), style = style, color = cs.onSurfaceVariant)
            }
        }
        FlowRow(
            Modifier.padding(start = AXIS_WIDTH + AXIS_GAP),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            spec.series.filter { it.label.isNotEmpty() }.forEach { LegendItem(it.label, it.color, it.width, it.dashed) }
            if ((spec.target != null || spec.targetSteps != null) && spec.targetLegend != null) LegendItem(spec.targetLegend, targetColor, 1.5.dp, true, short = true)
            if (events.isNotEmpty()) LegendDot(stringResource(R.string.chart_auto_change), markerColor)
        }
    }
}

/**
 * The dashed target as a step line: each point holds its value from halfway to the previous point
 * to halfway to the next (the first from the start, the last to the end), and neighbours are joined
 * by a vertical riser. A NaN point leaves a gap.
 */
private fun DrawScope.drawTargetSteps(
    d: Bucketed, durationMs: Int, color: Color, xOf: (Int) -> Float, yOf: (Float) -> Float, lo: Float, hi: Float,
) {
    val n = d.values.size
    if (n == 0) return
    val path = Path()
    var joined = false
    for (i in 0 until n) {
        val v = d.values[i]
        if (v.isNaN()) {
            joined = false
            continue
        }
        val left = if (i == 0) 0f else xOf((d.times[i - 1] + d.times[i]) / 2)
        val right = if (i == n - 1) xOf(durationMs) else xOf((d.times[i] + d.times[i + 1]) / 2)
        val y = yOf(v.coerceIn(lo, hi))
        if (joined) path.lineTo(left, y) else path.moveTo(left, y)
        path.lineTo(right, y)
        joined = true
    }
    drawPath(
        path, color,
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}

private fun DrawScope.drawSeries(s: ChartSeries, xOf: (Int) -> Float, yOf: (Float) -> Float) {
    val d = s.data
    if (d.values.isEmpty()) return
    val stroke = Stroke(
        width = s.width.toPx(), cap = StrokeCap.Round,
        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx())) else null,
    )
    val path = Path()
    var open = false
    var runLength = 0
    var lastX = 0f
    var lastY = 0f
    fun flushDot() {
        if (open && runLength == 1) drawCircle(s.color, s.width.toPx() / 2f, Offset(lastX, lastY))
    }
    for (i in d.values.indices) {
        val v = d.values[i]
        if (v.isNaN()) {
            flushDot()
            open = false
            runLength = 0
            continue
        }
        val x = xOf(d.times[i])
        val y = yOf(v)
        if (!open) {
            path.moveTo(x, y)
            open = true
            runLength = 1
        } else {
            path.lineTo(x, y)
            runLength++
        }
        lastX = x
        lastY = y
    }
    flushDot()
    drawPath(path, s.color, style = stroke)
}

@Composable
private fun LegendItem(text: String, color: Color, width: Dp, dashed: Boolean, short: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(if (short) 18.dp else 24.dp).height(10.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), width.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color, size.minDimension / 2) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
