// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import io.github.miner7222.gametuning.R
import vendor.lenovo.hardware.gametuning.GameTuningConfig
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.PolicyProfile
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of "what this preset changes". */
data class Change(val label: String, val before: String, val after: String)

internal data class StepChange(val title: String, val changes: List<Change>)
internal data class ImportPreview(val changes: List<Change>, val steps: List<StepChange>)

internal fun Context.importStepChanges(
    limits: PolicyLimits, current: PolicyProfile, imported: PolicyProfile, tf: TemperatureFormat,
): List<StepChange> {
    val before = current.steps.orEmpty()
    val after = imported.steps.orEmpty()
    return (0 until maxOf(before.size, after.size)).mapNotNull step@{ i ->
        val old = before.getOrNull(i)
        val new = after.getOrNull(i)
        val thresholdChanged = (old == null) != (new == null) || i > 0 && old?.temperatureC != new?.temperatureC
        val values = (0..2).mapNotNull resource@{ r ->
            val ladder = limits.ladder(r)
            if (ladder.isEmpty()) return@resource null
            fun range(step: vendor.lenovo.hardware.gametuning.PolicyStep?): String {
                if (step == null) return "—"
                val range = step.range(r)
                return Freq.range(r, ladder[ladder.lowIndex(range)], ladder[ladder.highIndex(range)])
            }
            val from = range(old)
            val to = range(new)
            if (from == to) null else Change(resourceName(r), from, to)
        }
        if (!thresholdChanged && values.isEmpty()) return@step null
        fun threshold(step: vendor.lenovo.hardware.gametuning.PolicyStep?) =
            if (step == null) "—" else tf.absolute(step.temperatureC.toDouble())
        val title = if (i == 0) (if (new != null) after.getOrNull(1) else before.getOrNull(1))?.let {
            getString(R.string.step_first_below, tf.absolute(it.temperatureC.toDouble()))
        } ?: getString(R.string.step_first) else {
            val from = threshold(old)
            val to = threshold(new)
            getString(R.string.step_row, i + 1, if (from == to) to else "$from → $to")
        }
        StepChange(title, values)
    }
}


fun Context.modeName(mode: Int): String = getString(
    when (mode) {
        Modes.NONE -> R.string.mode_none
        Modes.PERFORMANCE -> R.string.mode_performance
        Modes.BATTERY -> R.string.mode_battery
        else -> R.string.mode_balanced
    },
)

fun Context.resourceName(resource: Int): String = getString(
    when (resource) {
        Resource.GOLD -> R.string.res_cpu
        Resource.PRIME -> R.string.res_fastest
        else -> R.string.res_graphics
    },
)

/** The built-in preset: the game's own profile, or the generic one it falls back to. */
fun Context.builtinName(dedicated: Boolean): String =
    getString(if (dedicated) R.string.builtin_game else R.string.builtin_generic)

fun Context.heatName(thermalCase: Int): String = getString(
    when {
        thermalCase <= 100 -> R.string.heat_keep_cool
        thermalCase == 200 -> R.string.heat_normal
        thermalCase == 300 -> R.string.heat_warmer
        else -> R.string.heat_hottest
    },
)

/** The display's highest supported refresh rate in Hz. */
fun Context.peakRefreshRate(): Int = try {
    display?.supportedModes?.maxOfOrNull { it.refreshRate }?.roundToInt()?.coerceAtLeast(30) ?: 60
} catch (_: RuntimeException) {
    60
}

/** The frame rate a game runs at: its limit, or the display's peak rate when there is none. */
fun Context.frameRateName(fpsLimit: Int): String =
    getString(R.string.hz_value, if (fpsLimit > 0) fpsLimit else peakRefreshRate())

fun Context.fpsName(fps: Int): String = if (fps <= 0) getString(R.string.off) else fps.toString()

fun Context.signedSteps(n: Int): String {
    val signed = (if (n > 0) "+" else "−") + abs(n)
    return resources.getQuantityString(R.plurals.steps_delta, abs(n), signed)
}

/**
 * A short, offline summary of what a preset changes, built from the stored tuning alone, for
 * list rows. The detailed before and after values come from [detailChanges].
 */
fun Context.shortSummary(
    config: GameTuningConfig, display: DisplayValues, builtinThermal: Int, builtinDisplay: DisplayValues,
    tf: TemperatureFormat,
): String {
    val parts = ArrayList<String>()
    config.ensureArrays()
    for (r in 0..2) {
        val up = config.maxOffsets[r]
        if (up != 0) parts.add(getString(R.string.diff_top, resourceName(r), signedSteps(up)))
        val low = config.minOffsets[r]
        if (low != 0) parts.add(getString(R.string.diff_low, resourceName(r), signedSteps(low)))
    }
    if (config.steps.isNotEmpty()) parts.add(getString(R.string.diff_steps))
    if (config.thermalCase != KEEP && config.thermalCase != builtinThermal) {
        parts.add(
            when {
                config.thermalCase <= 100 -> getString(R.string.heat_limit) + " " + heatName(config.thermalCase)
                config.thermalCase > builtinThermal -> getString(R.string.diff_heat_higher)
                else -> getString(R.string.diff_heat_lower)
            },
        )
    }
    if (config.skinTargetMilliC != KEEP) {
        parts.add(
            if (config.skinTargetMilliC < 0) getString(R.string.diff_temp_target_off)
            else getString(R.string.diff_temp_target, tf.absoluteMilli(config.skinTargetMilliC)),
        )
    }
    if (config.powerTargetMw != KEEP) {
        parts.add(
            if (config.powerTargetMw < 0) getString(R.string.diff_power_target_off)
            else getString(R.string.diff_power_target, formatPower(config.powerTargetMw)),
        )
    }
    if (config.frameBoost != KEEP || config.frameBoostSteps.isNotEmpty()) parts.add(getString(R.string.diff_rampup))
    if (config.uclampMin != KEEP) parts.add(getString(R.string.diff_hold))
    if (config.jankBoost != KEEP) parts.add(getString(R.string.diff_stutter))
    if (config.keepCoresOnline != KEEP) parts.add(getString(R.string.diff_cores))
    if (config.gpuDefaultHz != KEEP) parts.add(getString(R.string.diff_gpu_idle))
    if (config.startDelayMs != KEEP) parts.add(getString(R.string.diff_wait))
    if (config.sceneMode != KEEP || config.scenes.isNotEmpty() || config.customThreads.isNotEmpty() ||
        config.boostLengthMs != KEEP || config.dropSensitivity != KEEP || config.hysteresisDeciC != KEEP
    ) {
        parts.add(getString(R.string.diff_expert))
    }
    if (display.fpsLimit > 0) parts.add(getString(R.string.diff_fps, display.fpsLimit))
    if (!sameFloat(display.resolution, builtinDisplay.resolution)) {
        parts.add(getString(R.string.diff_render, formatScale(display.resolution)))
    }
    if (display.lockRefresh != builtinDisplay.lockRefresh) parts.add(getString(R.string.diff_refresh))
    if (parts.isEmpty()) return getString(R.string.diff_none)
    return if (parts.size > 3) {
        parts.take(3).joinToString(" · ") + " · " + getString(R.string.diff_more, parts.size - 3)
    } else {
        parts.joinToString(" · ")
    }
}

/** Before and after values of everything that differs from the game's own setting. */
fun Context.detailChanges(
    limits: PolicyLimits, builtin: PolicyProfile, effective: PolicyProfile,
    display: DisplayValues, builtinDisplay: DisplayValues, tf: TemperatureFormat,
): List<Change> = describeChanges(limits, builtin, effective, display, builtinDisplay, tf, false)

internal fun Context.importChanges(
    limits: PolicyLimits, current: PolicyProfile, imported: PolicyProfile,
    display: DisplayValues, currentDisplay: DisplayValues, tf: TemperatureFormat,
): List<Change> = describeChanges(limits, current, imported, display, currentDisplay, tf, true)

private fun Context.describeChanges(
    limits: PolicyLimits, builtin: PolicyProfile, effective: PolicyProfile,
    display: DisplayValues, builtinDisplay: DisplayValues, tf: TemperatureFormat, complete: Boolean,
): List<Change> {
    val out = ArrayList<Change>()
    for (r in 0..2) {
        val ladder = limits.ladder(r)
        if (ladder.isEmpty()) continue
        val (bLo, bHi) = builtin.coolRange(limits, r)
        val (eLo, eHi) = effective.coolRange(limits, r)
        if (bHi != eHi) {
            out.add(Change(getString(R.string.change_top, resourceName(r)), Freq.one(r, ladder[bHi]), Freq.one(r, ladder[eHi])))
        }
        if (bLo != eLo) {
            out.add(Change(getString(R.string.change_low, resourceName(r)), Freq.one(r, ladder[bLo]), Freq.one(r, ladder[eLo])))
        }
    }
    if (effective.thermalCase != builtin.thermalCase) {
        out.add(Change(getString(R.string.heat_limit), heatName(builtin.thermalCase), heatName(effective.thermalCase)))
    }
    if (effective.skinTargetMilliC != builtin.skinTargetMilliC) {
        out.add(Change(getString(R.string.temp_target), targetText(builtin.skinTargetMilliC, tf, false), targetText(effective.skinTargetMilliC, tf, false)))
    }
    if (effective.powerTargetMw != builtin.powerTargetMw) {
        out.add(Change(getString(R.string.power_target), targetText(builtin.powerTargetMw, tf, true), targetText(effective.powerTargetMw, tf, true)))
    }
    if (complete) {
        fun boostText(steps: IntArray?): String = (0..2).joinToString(" · ") { r ->
            resourceName(r) + " " + (steps?.getOrNull(r) ?: 0)
        }
        if (!(builtin.frameBoostSteps ?: IntArray(0)).contentEquals((effective.frameBoostSteps ?: IntArray(0)))) {
            out.add(Change(getString(R.string.ramp_up), boostText(builtin.frameBoostSteps), boostText(effective.frameBoostSteps)))
        }
        if (effective.uclampMin != builtin.uclampMin) {
            out.add(Change(getString(R.string.hold_strength), percentText(builtin.uclampMin), percentText(effective.uclampMin)))
        }
        if (!(builtin.jankBoostSteps ?: IntArray(0)).contentEquals((effective.jankBoostSteps ?: IntArray(0)))) {
            out.add(Change(getString(R.string.stutter_boost), boostText(builtin.jankBoostSteps), boostText(effective.jankBoostSteps)))
        }
    } else {
        val bRamp = builtin.frameBoostSteps?.maxOrNull() ?: 0
        val eRamp = effective.frameBoostSteps?.maxOrNull() ?: 0
        if (bRamp != eRamp) out.add(Change(getString(R.string.ramp_up), bRamp.toString(), eRamp.toString()))
        if (effective.uclampMin != builtin.uclampMin) {
            out.add(Change(getString(R.string.hold_strength), percentText(builtin.uclampMin), percentText(effective.uclampMin)))
        }
        val bJank = builtin.jankBoostSteps?.isNotEmpty() == true
        val eJank = effective.jankBoostSteps?.isNotEmpty() == true
        if (bJank != eJank) out.add(Change(getString(R.string.stutter_boost), onOff(bJank), onOff(eJank)))
    }
    if (effective.keepCoresOnline != builtin.keepCoresOnline) {
        out.add(Change(getString(R.string.cores_awake), onOff(builtin.keepCoresOnline), onOff(effective.keepCoresOnline)))
    }
    if (effective.gpuDefaultHz != builtin.gpuDefaultHz) {
        val f = { v: Int -> if (v < 0) getString(R.string.off) else Freq.mhz(v) }
        out.add(Change(getString(R.string.gpu_idle), f(builtin.gpuDefaultHz), f(effective.gpuDefaultHz)))
    }
    if (effective.startDelayMs != builtin.startDelayMs) {
        out.add(Change(getString(R.string.wait_after_launch), formatSeconds(builtin.startDelayMs), formatSeconds(effective.startDelayMs)))
    }
    if (complete) {
        fun threadText(p: PolicyProfile): String {
            val policy = getString(when (p.threadRules) {
                0 -> R.string.threads_off
                2 -> R.string.threads_base
                3 -> R.string.threads_custom
                else -> R.string.threads_own
            })
            val rules = p.threads.orEmpty().map { rule ->
                val cores = (0 until limits.goldCores + limits.primeCores).filter { (rule.cpuMask shr it) and 1L == 1L }
                rule.name + " · " + getString(R.string.cores_n, cores.joinToString(", ")) + " · " +
                    if (rule.rtPriority >= 90) getString(R.string.rt_value, rule.rtPriority) else getString(R.string.normal_priority)
            }
            return (listOf(policy) + rules).joinToString("; ")
        }
        val beforeThreads = threadText(builtin)
        val afterThreads = threadText(effective)
        if (beforeThreads != afterThreads) out.add(Change(getString(R.string.thread_rules), beforeThreads, afterThreads))
        fun sceneText(p: PolicyProfile): String = (listOf(onOff(p.sceneMode)) + p.scenes.orEmpty().map { row ->
            val ranges = (0..2).mapNotNull { r ->
                val ladder = limits.ladder(r)
                if (ladder.isEmpty()) null else {
                    val range = when (r) { 0 -> row.gold; 1 -> row.prime; else -> row.gpu }
                    resourceName(r) + " " + Freq.range(r, ladder[ladder.lowIndex(range)], ladder[ladder.highIndex(range)])
                }
            }
            (listOf(getString(R.string.scene_n, row.sceneId), onOff(row.enabled), formatSeconds(row.holdMs)) + ranges).joinToString(" · ")
        }).joinToString("; ")
        val beforeScenes = sceneText(builtin)
        val afterScenes = sceneText(effective)
        if (beforeScenes != afterScenes) out.add(Change(getString(R.string.scene_rows), beforeScenes, afterScenes))
        fun stepText(p: PolicyProfile): String = p.steps.orEmpty().mapIndexed { i, step ->
            val title = if (i == 0) p.steps.orEmpty().getOrNull(1)?.let {
                getString(R.string.step_first_below, tf.absolute(it.temperatureC.toDouble()))
            } ?: getString(R.string.step_first) else getString(R.string.step_row, i + 1, tf.absolute(step.temperatureC.toDouble()))
            val ranges = (0..2).mapNotNull { r ->
                val ladder = limits.ladder(r)
                if (ladder.isEmpty()) null else {
                    val range = step.range(r)
                    resourceName(r) + " " + Freq.range(r, ladder[ladder.lowIndex(range)], ladder[ladder.highIndex(range)])
                }
            }
            (listOf(title) + ranges).joinToString(" · ")
        }.joinToString("; ")
        val beforeSteps = stepText(builtin)
        val afterSteps = stepText(effective)
        if (beforeSteps != afterSteps) out.add(Change(getString(R.string.steps_title), beforeSteps, afterSteps))
    } else {
        if (effective.threadRules != builtin.threadRules || effective.threadRuleCount != builtin.threadRuleCount) {
            out.add(Change(getString(R.string.thread_rules), builtin.threadRuleCount.toString(), effective.threadRuleCount.toString()))
        }
        if (effective.sceneCount != builtin.sceneCount || effective.sceneMode != builtin.sceneMode) {
            out.add(Change(getString(R.string.scene_rows), builtin.sceneCount.toString(), effective.sceneCount.toString()))
        }
    }
    if (effective.boostLengthMs != builtin.boostLengthMs) {
        out.add(Change(getString(R.string.boost_length), "${builtin.boostLengthMs} ms", "${effective.boostLengthMs} ms"))
    }
    if (effective.dropSensitivity != builtin.dropSensitivity) {
        out.add(Change(getString(R.string.drop_sensitivity), sensitivityName(builtin.dropSensitivity), sensitivityName(effective.dropSensitivity)))
    }
    if (effective.hysteresisDeciC != builtin.hysteresisDeciC) {
        out.add(Change(getString(R.string.temp_smoothing), tf.delta(builtin.hysteresisDeciC / 10.0), tf.delta(effective.hysteresisDeciC / 10.0)))
    }
    if (display.fpsLimit != builtinDisplay.fpsLimit) {
        out.add(Change(getString(R.string.fps_limit), fpsName(builtinDisplay.fpsLimit), fpsName(display.fpsLimit)))
    }
    if (!sameFloat(display.resolution, builtinDisplay.resolution)) {
        out.add(Change(getString(R.string.render_resolution), formatScale(builtinDisplay.resolution), formatScale(display.resolution)))
    }
    if (display.lockRefresh != builtinDisplay.lockRefresh) {
        out.add(Change(getString(R.string.lock_refresh), onOff(builtinDisplay.lockRefresh), onOff(display.lockRefresh)))
    }
    if (complete) {
        val beforeGraphics = builtinDisplay.graphics
        val afterGraphics = display.graphics
        fun graphics(label: Int, before: String, after: String, format: (String) -> String = { it }) {
            if (before != after) {
                fun value(v: String) = if (v.isEmpty()) getString(R.string.graphics_default) else format(v)
                out.add(Change(getString(label), value(before), value(after)))
            }
        }
        graphics(R.string.graphics_texture_filtering, beforeGraphics.textureFiltering, afterGraphics.textureFiltering) {
            when (it) { "Balanced" -> modeName(Modes.BALANCED); "Fastest" -> modeName(Modes.PERFORMANCE); else -> it }
        }
        graphics(R.string.graphics_anisotropic_filtering, beforeGraphics.anisotropicFiltering, afterGraphics.anisotropicFiltering) {
            it.removeSuffix(".0") + "x"
        }
        graphics(R.string.graphics_mipmap_lod_bias, beforeGraphics.mipmapLodBias, afterGraphics.mipmapLodBias) { it.removeSuffix(".0") }
        graphics(R.string.graphics_msaa_limit, beforeGraphics.msaaLimit, afterGraphics.msaaLimit) { it + "x" }
    }
    return out
}

fun Context.onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)
fun Context.onOff(v: Int) = onOff(v == 1)
fun Context.percentText(v: Int) = if (v < 0) getString(R.string.off) else getString(R.string.percent, v)

fun Context.targetText(v: Int, tf: TemperatureFormat, power: Boolean) =
    if (v < 0) getString(R.string.off) else if (power) formatPower(v) else tf.absoluteMilli(v)

fun Context.sensitivityName(v: Int) = getString(
    when (v) {
        1 -> R.string.sensitivity_low
        3 -> R.string.sensitivity_high
        else -> R.string.sensitivity_medium
    },
)

/** Compact "CPU a-b" style parts of a resolved profile, for import previews. */
fun Context.profileLine(limits: PolicyLimits, p: PolicyProfile): List<String> {
    val out = ArrayList<String>()
    for (r in 0..2) {
        val ladder = limits.ladder(r)
        if (ladder.isEmpty()) continue
        val (lo, hi) = p.coolRange(limits, r)
        out.add(resourceName(r) + " " + Freq.range(r, ladder[lo], ladder[hi]))
    }
    out.add(heatName(p.thermalCase))
    return out
}
