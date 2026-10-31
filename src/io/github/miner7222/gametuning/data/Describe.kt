// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import io.github.miner7222.gametuning.R
import vendor.lenovo.hardware.gametuning.GameTuningConfig
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.PolicyProfile
import kotlin.math.abs

/** One line of "what this preset changes". */
data class Change(val label: String, val before: String, val after: String)

fun Context.modeName(mode: Int): String = getString(
    when (mode) {
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

fun Context.heatName(thermalCase: Int): String = getString(
    when {
        thermalCase <= 100 -> R.string.heat_keep_cool
        thermalCase == 200 -> R.string.heat_normal
        thermalCase == 300 -> R.string.heat_warmer
        else -> R.string.heat_hottest
    },
)

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
                config.thermalCase <= 100 -> getString(R.string.heat_keep_cool)
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
    val bRamp = builtin.frameBoostSteps?.maxOrNull() ?: 0
    val eRamp = effective.frameBoostSteps?.maxOrNull() ?: 0
    if (bRamp != eRamp) out.add(Change(getString(R.string.ramp_up), bRamp.toString(), eRamp.toString()))
    if (effective.uclampMin != builtin.uclampMin) {
        out.add(Change(getString(R.string.hold_strength), percentText(builtin.uclampMin), percentText(effective.uclampMin)))
    }
    val bJank = builtin.jankBoostSteps?.isNotEmpty() == true
    val eJank = effective.jankBoostSteps?.isNotEmpty() == true
    if (bJank != eJank) out.add(Change(getString(R.string.stutter_boost), onOff(bJank), onOff(eJank)))
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
    if (effective.threadRules != builtin.threadRules || effective.threadRuleCount != builtin.threadRuleCount) {
        out.add(Change(getString(R.string.thread_rules), builtin.threadRuleCount.toString(), effective.threadRuleCount.toString()))
    }
    if (effective.sceneCount != builtin.sceneCount || effective.sceneMode != builtin.sceneMode) {
        out.add(Change(getString(R.string.scene_rows), builtin.sceneCount.toString(), effective.sceneCount.toString()))
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
