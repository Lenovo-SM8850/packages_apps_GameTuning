// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import android.icu.text.ListFormatter
import io.github.miner7222.gametuning.R
import vendor.lenovo.hardware.gametuning.IGameTuning
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.RecordingInfo
import vendor.lenovo.hardware.gametuning.RecordingSample
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure helpers behind the records, result and chart screens. */

const val CHART_MAX_POINTS = 600
private const val TEMP_NONE = IGameTuning.TEMPERATURE_UNAVAILABLE

/** A record that can be opened: it holds at least one usable sample. */
fun RecordingInfo.hasData(): Boolean = sampleCount > 0 && summary != null && summary.validSamples > 0

fun RecordingInfo.presetName(): String = profile?.variantName.orEmpty()

/** The label records are grouped and filtered by. */
fun Context.presetLabel(info: RecordingInfo): String {
    val name = info.presetName()
    return when {
        name.isNotEmpty() -> name
        info.profile?.tuned == true -> getString(R.string.unnamed_preset)
        else -> builtinName(info.profile?.profileKey?.let { it != GENERIC_PROFILE } ?: false)
    }
}

/** "24:10", or "1:02:03" past an hour. */
fun formatClock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val locale = Locale.getDefault()
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
}

fun hasTemp(milliC: Int) = milliC > TEMP_NONE

/** The samples the daemon counts: ready, with a frame rate, past the first 10 seconds. */
fun List<RecordingSample>.validOnly(): List<RecordingSample> =
    filter { (it.flags and IGameTuning.FLAG_READY) != 0 && it.fps >= 1f && it.timeMs >= 10_000 }

/** Share of valid samples at or above 90 % of [target], 0 to 100. */
fun hitPercent(samples: List<RecordingSample>, target: Int): Int {
    if (target <= 0) return 0
    val valid = samples.validOnly()
    if (valid.isEmpty()) return 0
    return (valid.count { it.fps >= 0.9f * target } * 100.0 / valid.size).roundToInt()
}

fun RecordingSample.capOf(resource: Int): Int = when (resource) {
    Resource.GOLD -> goldCapKHz
    Resource.PRIME -> primeCapKHz
    else -> gpuCapHz
}

fun RecordingSample.curOf(resource: Int): Int = when (resource) {
    Resource.GOLD -> goldKHz
    Resource.PRIME -> primeKHz
    else -> gpuHz
}

/** A stretch in which the daemon held a resource below the highest ceiling the record saw. */
data class AutoEvent(val timeMs: Int, val resource: Int, val durationMs: Int, val skinMilliC: Int)

private const val EVENT_MERGE_MS = 10_000
private const val EVENT_MIN_MS = 5_000
private const val EVENT_LIMIT = 12

/** Automatic adjustments, found from the changes in the recorded ceilings. */
fun deriveAutoEvents(samples: List<RecordingSample>): List<AutoEvent> {
    if (samples.isEmpty()) return emptyList()
    val n = samples.size
    val out = ArrayList<AutoEvent>()
    for (r in Resource.GOLD..Resource.GPU) {
        val top = samples.maxOf { it.capOf(r) }
        if (top <= 0) continue
        val spans = ArrayList<IntArray>()
        var i = 0
        while (i < n) {
            if (samples[i].capOf(r) in 1 until top) {
                var j = i
                while (j < n && samples[j].capOf(r) in 1 until top) j++
                spans.add(intArrayOf(i, j))
                i = j
            } else {
                i++
            }
        }
        val merged = ArrayList<IntArray>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && samples[s[0]].timeMs - endOf(samples, last[1]) < EVENT_MERGE_MS) last[1] = s[1] else merged.add(s)
        }
        for (s in merged) {
            val start = samples[s[0]].timeMs
            val duration = endOf(samples, s[1]) - start
            if (duration < EVENT_MIN_MS) continue
            var skin = TEMP_NONE
            for (k in s[0] until minOf(s[1], s[0] + 5)) if (hasTemp(samples[k].skinMilliC)) { skin = samples[k].skinMilliC; break }
            out.add(AutoEvent(start, r, duration, skin))
        }
    }
    return out.sortedBy { it.timeMs }.take(EVENT_LIMIT)
}

private fun endOf(samples: List<RecordingSample>, exclusive: Int): Int =
    if (exclusive < samples.size) samples[exclusive].timeMs else samples.last().timeMs + 1000

/** One chart series reduced to at most [CHART_MAX_POINTS] points; NaN marks a gap. */
class Bucketed(val times: IntArray, val values: FloatArray)

fun bucketAverage(samples: List<RecordingSample>, value: (RecordingSample) -> Float): Bucketed =
    bucketed(samples) { from, to ->
        var sum = 0.0
        var count = 0
        for (k in from until to) {
            val v = value(samples[k])
            if (!v.isNaN()) { sum += v; count++ }
        }
        if (count == 0) Float.NaN else (sum / count).toFloat()
    }

/**
 * Buckets like [bucketAverage] (same points and times), but each point is the value that the most
 * samples of the bucket carry, the later one on a tie; NaN when no sample has a value.
 */
fun bucketHeldLongest(samples: List<RecordingSample>, value: (RecordingSample) -> Float): Bucketed =
    bucketed(samples) { from, to ->
        var best = Float.NaN
        var bestCount = 0
        for (k in from until to) {
            val v = value(samples[k])
            if (v.isNaN()) continue
            var count = 0
            for (j in from until to) if (value(samples[j]) == v) count++
            if (count >= bestCount) { best = v; bestCount = count }
        }
        best
    }

private inline fun bucketed(samples: List<RecordingSample>, reduce: (from: Int, to: Int) -> Float): Bucketed {
    val n = samples.size
    if (n == 0) return Bucketed(IntArray(0), FloatArray(0))
    val size = ceil(n / CHART_MAX_POINTS.toDouble()).toInt().coerceAtLeast(1)
    val points = (n + size - 1) / size
    val times = IntArray(points)
    val values = FloatArray(points)
    for (p in 0 until points) {
        val from = p * size
        val to = minOf(n, from + size)
        times[p] = samples[(from + to - 1) / 2].timeMs
        values[p] = reduce(from, to)
    }
    return Bucketed(times, values)
}

/** Axis with its grid values, in the unit the labels use. */
class Axis(val min: Float, val max: Float, val ticks: List<Float>)

private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val exp = floor(kotlin.math.log10(raw))
    val base = 10.0.pow(exp)
    for (m in doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)) if (m * base >= raw) return m * base
    return 10.0 * base
}

/** An axis that starts at [forceMin] when given, otherwise just below the data. */
fun niceAxis(dataMin: Float, dataMax: Float, forceMin: Float? = null): Axis {
    val hiData = if (dataMax.isNaN() || dataMax <= 0f) 1f else dataMax
    val loData = forceMin ?: if (dataMin.isNaN()) 0f else dataMin
    val span = (hiData - loData).coerceAtLeast(1f)
    val step = niceStep(span / 3.5)
    val lo = if (forceMin != null) forceMin else (floor(loData / step) * step).toFloat()
    var hi = (ceil(hiData / step) * step).toFloat()
    if (hi - lo < 3 * step) hi = (lo + 3 * step).toFloat()
    val ticks = ArrayList<Float>()
    var t = lo + step
    while (t <= hi + step * 0.001) { ticks.add(t.toFloat()); t += step }
    return Axis(lo.toFloat(), hi, ticks)
}

/** Resource names joined for a sentence: "CPU and prime core". */
fun Context.joinNames(names: List<String>): String = ListFormatter.getInstance().format(names)

fun fractionOfTop(limits: PolicyLimits, resource: Int, value: Int): Float {
    val top = limits.ladder(resource).lastOrNull() ?: return 0f
    return if (top > 0) value.toFloat() / top else 0f
}

fun formatPercent(v: Int): String = String.format(Locale.getDefault(), "%d%%", v)

fun formatSigned(v: Double, digits: Int = 1): String =
    String.format(Locale.getDefault(), "%+." + digits + "f", v)
