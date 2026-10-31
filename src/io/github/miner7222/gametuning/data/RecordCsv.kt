// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Plain-Kotlin CSV writer for one benchmark record. It imports nothing from Android or the daemon
 * interface so it can be compiled and tested on a JVM; RecordExport.kt maps the daemon's types to
 * the two classes below.
 */

/** What the leading comment lines say about the record. Unknown numbers use the same sentinels as the daemon. */
class CsvMeta(
    val packageName: String,
    val gameLabel: String,
    val modeLabel: String,
    /** "<profile> · <preset>", as the record cards show it. */
    val presetLabel: String,
    val startEpochMs: Long,
    val durationMs: Int,
    /** A fixed word such as "stopped"; see [RecordCsv.endReasonName]. */
    val endReason: String,
    val averageFps: Float,
    val steadyFloorFps: Float,
    /** Tail of the longest frames; the longest-frames figure is 1 + this, unknown when negative. */
    val tail95: Float,
    val stutterPer1000: Float,
    /** Maximum surface temperature in degrees Celsius, not above 0 when unknown. */
    val maxTemperatureC: Float,
    /** Mean power in milliwatts, negative when it was not measured. */
    val averagePowerMw: Int,
    val targetFps: Int,
)

/** One sample in the daemon's own units; [RecordCsv.row] converts it to the file's fixed units. */
class CsvSample(
    val timeMs: Int,
    val fps: Float,
    val fpsMin: Float,
    /** Frame-rate target the daemon judged that second against, not above 0 when unknown. */
    val targetFps: Float,
    val jankFrames: Int,
    val frames: Int,
    val maxFrameTimeUs: Int,
    val goldKHz: Int,
    val primeKHz: Int,
    val gpuHz: Int,
    val goldCapKHz: Int,
    val primeCapKHz: Int,
    val gpuCapHz: Int,
    /** 0 to 1000, negative when unknown. */
    val goldUtilPermille: Int,
    val primeUtilPermille: Int,
    val gpuBusyPercent: Int,
    /** Milli-degrees Celsius; [RecordCsv.TEMPERATURE_UNAVAILABLE] or lower when unreadable. */
    val skinMilliC: Int,
    val cpuMilliC: Int,
    val gpuMilliC: Int,
    val batteryMilliC: Int,
    val powerMw: Int,
    val batteryPercent: Int,
    val band: Int,
    val thermalCase: Int,
    val sceneId: Int,
    val profileGeneration: Int,
)

object RecordCsv {
    /** The daemon's marker for an unreadable temperature. */
    const val TEMPERATURE_UNAVAILABLE = -273000

    /** Column names, fixed machine units whatever the display settings are. */
    val COLUMNS: List<String> = listOf(
        "time_s", "fps", "fps_min", "target_fps", "jank_frames", "frames", "max_frame_ms",
        "gold_mhz", "prime_mhz", "gpu_mhz", "gold_cap_mhz", "prime_cap_mhz", "gpu_cap_mhz",
        "gold_util_pct", "prime_util_pct", "gpu_busy_pct",
        "skin_c", "cpu_c", "gpu_c", "battery_c", "power_mw", "battery_pct",
        "band", "thermal_case", "scene_id", "profile_generation",
    )

    private const val EOL = "\n"
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)
    private val INVALID_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]")

    /** The comment lines (each ends with a line feed), then the column header row. */
    fun preamble(meta: CsvMeta, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = Instant.ofEpochMilli(meta.startEpochMs).atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS)
        val lines = listOf(
            "package" to meta.packageName,
            "game" to meta.gameLabel,
            "mode" to meta.modeLabel,
            "preset" to meta.presetLabel,
            "start" to STAMP.format(start),
            "length_s" to (if (meta.durationMs >= 0) scaled(meta.durationMs.toLong(), 1000, 3) else ""),
            "end_reason" to meta.endReason,
            "average_fps" to decimal(meta.averageFps, 1),
            "steady_floor_fps" to decimal(meta.steadyFloorFps, 1),
            "longest_frames_x" to (if (meta.tail95 >= 0f) decimal(1f + meta.tail95, 2) else ""),
            "stutter_per_1000" to decimal(meta.stutterPer1000, 1),
            "max_temperature_c" to (if (meta.maxTemperatureC > 0f) decimal(meta.maxTemperatureC, 1) else ""),
            "average_power_mw" to (if (meta.averagePowerMw >= 0) meta.averagePowerMw.toString() else ""),
            "target_fps" to (if (meta.targetFps > 0) meta.targetFps.toString() else ""),
        )
        return buildString {
            for ((key, value) in lines) {
                append("# ").append(key).append(':')
                val text = comment(value)
                if (text.isNotEmpty()) append(' ').append(text)
                append(EOL)
            }
            append(COLUMNS.joinToString(",") { field(it) }).append(EOL)
        }
    }

    /** One data row (ends with a line feed). Unknown values are empty cells. */
    fun row(s: CsvSample): String {
        val cells = arrayOf(
            scaled(s.timeMs.toLong(), 1000, 3),
            if (s.fps >= 0f) decimal(s.fps, 2) else "",
            if (s.fpsMin >= 0f) decimal(s.fpsMin, 2) else "",
            if (s.targetFps > 0f) trimmed(s.targetFps, 2) else "",
            nonNegative(s.jankFrames),
            nonNegative(s.frames),
            if (s.maxFrameTimeUs >= 0) scaled(s.maxFrameTimeUs.toLong(), 1000, 2) else "",
            if (s.goldKHz > 0) scaled(s.goldKHz.toLong(), 1000, 3) else "",
            if (s.primeKHz > 0) scaled(s.primeKHz.toLong(), 1000, 3) else "",
            if (s.gpuHz > 0) scaled(s.gpuHz.toLong(), 1_000_000, 3) else "",
            if (s.goldCapKHz > 0) scaled(s.goldCapKHz.toLong(), 1000, 3) else "",
            if (s.primeCapKHz > 0) scaled(s.primeCapKHz.toLong(), 1000, 3) else "",
            if (s.gpuCapHz > 0) scaled(s.gpuCapHz.toLong(), 1_000_000, 3) else "",
            if (s.goldUtilPermille >= 0) scaled(s.goldUtilPermille.toLong(), 10, 1) else "",
            if (s.primeUtilPermille >= 0) scaled(s.primeUtilPermille.toLong(), 10, 1) else "",
            nonNegative(s.gpuBusyPercent),
            celsius(s.skinMilliC),
            celsius(s.cpuMilliC),
            celsius(s.gpuMilliC),
            celsius(s.batteryMilliC),
            nonNegative(s.powerMw),
            nonNegative(s.batteryPercent),
            nonNegative(s.band),
            nonNegative(s.thermalCase),
            nonNegative(s.sceneId),
            nonNegative(s.profileGeneration),
        )
        return cells.joinToString(",") { field(it) } + EOL
    }

    /** Writes the whole file: [preamble], then one [row] per sample. */
    fun write(meta: CsvMeta, samples: Iterable<CsvSample>, out: Appendable, zone: ZoneId = ZoneId.systemDefault()) {
        out.append(preamble(meta, zone))
        for (s in samples) out.append(row(s))
    }

    /** `<game label>_<yyyyMMdd-HHmm>.csv`, with characters that file names cannot hold replaced by `_`. */
    fun fileName(gameLabel: String, startEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val label = INVALID_NAME_CHARS.replace(gameLabel.trim(), "_").take(80).ifEmpty { "record" }
        val stamp = FILE_STAMP.format(Instant.ofEpochMilli(startEpochMs).atZone(zone))
        return label + "_" + stamp + ".csv"
    }

    /** The file's word for how a recording ended; the argument is the daemon's `END_*` value. */
    fun endReasonName(code: Int): String = when (code) {
        1 -> "stopped"
        2 -> "exited"
        3 -> "limit"
        4 -> "interrupted"
        else -> ""
    }

    /** A field with RFC 4180 quoting: quoted when it holds a comma, a quote or a line break. */
    fun field(text: String): String {
        val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    /** A comment value on one line: line breaks and other control characters become spaces. */
    private fun comment(text: String): String =
        text.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()

    private fun nonNegative(v: Int): String = if (v >= 0) v.toString() else ""

    private fun celsius(milliC: Int): String =
        if (milliC > TEMPERATURE_UNAVAILABLE) scaled(milliC.toLong(), 1000, 1, trim = false) else ""

    private fun decimal(v: Float, digits: Int): String =
        if (v.isNaN() || v.isInfinite()) "" else
            BigDecimal(v.toString()).setScale(digits, RoundingMode.HALF_UP).toPlainString()

    /** [decimal] without trailing zeros: 60 stays "60", 59.94 stays "59.94". */
    private fun trimmed(v: Float, digits: Int): String =
        if (v.isNaN() || v.isInfinite()) "" else
            BigDecimal(v.toString()).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** [value] / [divisor] with at most [digits] decimals, rounded half up, in plain notation. */
    private fun scaled(value: Long, divisor: Long, digits: Int, trim: Boolean = true): String {
        val q = BigDecimal(value).divide(BigDecimal(divisor), digits, RoundingMode.HALF_UP)
        return (if (trim) q.stripTrailingZeros() else q).toPlainString()
    }
}
