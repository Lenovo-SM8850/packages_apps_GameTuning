// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.os.Parcel
import android.util.Base64
import vendor.lenovo.hardware.gametuning.FrequencyRange
import vendor.lenovo.hardware.gametuning.GameTuningConfig
import vendor.lenovo.hardware.gametuning.PolicyProfile
import vendor.lenovo.hardware.gametuning.SceneRow
import vendor.lenovo.hardware.gametuning.ThreadRule
import java.security.SecureRandom

object Modes {
    const val NONE = 0
    const val BALANCED = 1
    const val PERFORMANCE = 2
    const val BATTERY = 3
    /** Modes that support tuning; None must never be used for a tuning configuration. */
    val ALL = intArrayOf(BATTERY, BALANCED, PERFORMANCE)
}

const val KEEP = GameTuningConfig.KEEP
/** Key of the built-in profile that games without one of their own fall back to. */
const val GENERIC_PROFILE = "default"
const val MAX_PRESETS = 10
val FPS_LIMITS = intArrayOf(0, 30, 45, 60, 90, 120, 144, 165)

/** Smallest supported step above a confirmed historical ceiling; never raises an existing limit. */
fun capFpsLimit(confirmed: Boolean, ceilingFps: Int, currentLimit: Int): Int? {
    if (!confirmed || ceilingFps <= 0) return null
    val limit = FPS_LIMITS.firstOrNull { it > 0 && it >= ceilingFps } ?: return null
    return limit.takeIf { currentLimit == 0 || currentLimit > it }
}

/** Each rollback is attempted even when another storage or service is unavailable. */
suspend fun restoreIndependently(vararg steps: suspend () -> Unit): List<Exception> {
    val failures = ArrayList<Exception>()
    for (step in steps) try { step() } catch (e: Exception) { failures.add(e) }
    return failures
}

/** Display changes make a hardware-only probe incomparable; unknown FPS is also unsafe. */
fun displayAffectsProbe(beforeFps: Int?, beforeScale: Float, beforeRefresh: Int,
                       after: DisplayValues, afterRefresh: Int): Boolean =
    beforeFps == null || beforeFps != after.fpsLimit || !beforeScale.isFinite() || beforeScale <= 0 ||
        kotlin.math.abs(beforeScale - after.resolution) >= .0001f || beforeRefresh != afterRefresh

/** App-side values that belong to a preset but are not part of the daemon's tuning. */
data class DisplayValues(
    val resolution: Float = 1.0f,
    val fpsLimit: Int = 0,
    val lockRefresh: Boolean = false,
) {
    companion object {
        /** What a profile uses when nothing is adjusted. */
        fun builtin(mode: Int) = DisplayValues(if (mode == Modes.BATTERY) 0.70f else 1.0f, 0, false)
    }
}

/** One saved preset. The built-in setting is not a [Preset]; it is represented by null. */
class Preset(
    val id: String,
    val name: String,
    val config: GameTuningConfig,
    val display: DisplayValues,
    /** Immutable daemon transition key; user-created copies have no archive origin. */
    val archiveOrigin: String? = null,
)

fun List<Preset>.customCount(): Int = count { it.archiveOrigin == null }

data class BuiltinTransition(val key: String, val generation: Long, val version: String)
data class BuiltinCheckpoint(val generation: Long, val display: DisplayValues)
data class BuiltinSyncUpdate(val pkg: String, val mode: Int, val transitions: List<BuiltinTransition>)

/** Names are limited by the daemon in UTF-8 bytes, including the collision suffix. */
fun archivePresetName(label: String, existing: List<Preset>): String {
    val names = existing.map { it.name }.toSet()
    var n = 1
    while (true) {
        val suffix = if (n == 1) "" else " $n"
        var stem = label.filter { it >= ' ' && it != '\u007f' }
        while ((stem + suffix).toByteArray(Charsets.UTF_8).size > 64)
            stem = stem.dropLast(if (stem.last().isLowSurrogate() && stem.length > 1) 2 else 1)
        val name = stem + suffix
        if (name !in names) return name
        n++
    }
}

/** Everything the app keeps for one (game, profile). */
class ModeLibrary(
    val presets: List<Preset>,
    /** The preset the daemon is running now; null is the built-in setting. */
    val inUseId: String?,
) {
    val inUse: Preset? get() = presets.firstOrNull { it.id == inUseId }
}

fun newPresetId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    return buildString { repeat(16) { append(chars[random.nextInt(chars.length)]) } }
}

fun GameTuningConfig.deepCopy(): GameTuningConfig {
    val p = Parcel.obtain()
    try {
        p.writeTypedObject(this, 0)
        p.setDataPosition(0)
        return p.readTypedObject(GameTuningConfig.CREATOR)!!
    } finally {
        p.recycle()
    }
}

fun GameTuningConfig.toBase64(): String {
    val p = Parcel.obtain()
    try {
        p.writeTypedObject(this, 0)
        return Base64.encodeToString(p.marshall(), Base64.NO_WRAP)
    } finally {
        p.recycle()
    }
}

fun configFromBase64(text: String): GameTuningConfig? {
    val bytes = try { Base64.decode(text, Base64.NO_WRAP) } catch (_: IllegalArgumentException) { return null }
    val p = Parcel.obtain()
    try {
        p.unmarshall(bytes, 0, bytes.size)
        p.setDataPosition(0)
        return p.readTypedObject(GameTuningConfig.CREATOR)
    } catch (_: RuntimeException) {
        return null
    } finally {
        p.recycle()
    }
}

/** A fresh tuning that changes nothing: the game's own profile with zero offsets. */
fun defaultConfig(userId: Int, pkg: String, mode: Int): GameTuningConfig = GameTuningConfig().apply {
    this.userId = userId
    packageName = pkg
    this.mode = mode
    enabled = true
    baseProfile = ""
    baseMode = mode
    maxOffsets = intArrayOf(0, 0, 0)
    minOffsets = intArrayOf(0, 0, 0)
    steps = emptyArray()
    frameBoostSteps = IntArray(0)
    jankBoostSteps = IntArray(0)
    scenes = emptyArray()
    customThreads = emptyArray()
    variantId = ""
    variantName = ""
}

fun GameTuningConfig.ensureArrays() {
    if (maxOffsets == null || maxOffsets.size != 3) maxOffsets = intArrayOf(0, 0, 0)
    if (minOffsets == null || minOffsets.size != 3) minOffsets = intArrayOf(0, 0, 0)
    if (steps == null) steps = emptyArray()
    if (frameBoostSteps == null) frameBoostSteps = IntArray(0)
    if (jankBoostSteps == null) jankBoostSteps = IntArray(0)
    if (scenes == null) scenes = emptyArray()
    if (customThreads == null) customThreads = emptyArray()
    if (variantId == null) variantId = ""
    if (variantName == null) variantName = ""
    if (baseProfile == null) baseProfile = ""
}

/** Config retargeted to another (game, profile), keeping a self-contained base profile. */
fun GameTuningConfig.retarget(userId: Int, pkg: String, mode: Int, resolvedBaseKey: String?): GameTuningConfig {
    val c = deepCopy()
    c.ensureArrays()
    if (c.baseProfile.isNullOrEmpty() && !resolvedBaseKey.isNullOrEmpty()) c.baseProfile = resolvedBaseKey
    c.userId = userId
    c.packageName = pkg
    c.mode = mode
    return c
}

fun FrequencyRange.copyRange(): FrequencyRange = FrequencyRange().also { it.min = min; it.max = max }

fun SceneRow.copyRow(): SceneRow = SceneRow().also {
    it.sceneId = sceneId; it.enabled = enabled; it.holdMs = holdMs
    it.gold = gold?.copyRange() ?: FrequencyRange()
    it.prime = prime?.copyRange() ?: FrequencyRange()
    it.gpu = gpu?.copyRange() ?: FrequencyRange()
}

fun ThreadRule.copyRule(): ThreadRule = ThreadRule().also {
    it.name = name; it.cpuMask = cpuMask; it.rtPriority = rtPriority
}

/** Whether this config has expert content that a plain copy would carry along. */
fun GameTuningConfig.hasExpertContent(): Boolean =
    (scenes?.isNotEmpty() == true) || sceneMode != KEEP || (customThreads?.isNotEmpty() == true) ||
        threadRules == 3

fun PolicyProfile.firstStep() = steps?.firstOrNull()
