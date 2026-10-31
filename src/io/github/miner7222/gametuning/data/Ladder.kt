// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import vendor.lenovo.hardware.gametuning.FrequencyRange
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.PolicyProfile
import vendor.lenovo.hardware.gametuning.PolicyStep

/** Ascending list of the speeds this device allows for resource 0 gold, 1 prime, 2 graphics. */
fun PolicyLimits.ladder(resource: Int): IntArray = when (resource) {
    Resource.GOLD -> goldKHz
    Resource.PRIME -> primeKHz
    else -> gpuHz
} ?: IntArray(0)

/** The largest ladder index whose value is at or below [value]; 0 when none is. */
fun IntArray.indexAtOrBelow(value: Int): Int {
    var best = 0
    for (i in indices) if (this[i] <= value) best = i else break
    return best
}

fun PolicyStep.range(resource: Int): FrequencyRange = when (resource) {
    Resource.GOLD -> gold
    Resource.PRIME -> prime
    else -> gpu
} ?: FrequencyRange()

/** Index of the lowest speed of a step: no minimum counts as the bottom of the ladder. */
fun IntArray.lowIndex(range: FrequencyRange): Int = if (isEmpty() || range.min < 0) 0 else indexAtOrBelow(range.min)

/** Index of the highest speed of a step: no maximum counts as the top of the ladder. */
fun IntArray.highIndex(range: FrequencyRange): Int =
    if (isEmpty()) 0 else if (range.max < 0) lastIndex else indexAtOrBelow(range.max)

/** The range a profile uses when the device is cool, as ladder indices. */
fun PolicyProfile.coolRange(limits: PolicyLimits, resource: Int): Pair<Int, Int> {
    val ladder = limits.ladder(resource)
    val step = steps?.firstOrNull() ?: return 0 to ladder.lastIndex.coerceAtLeast(0)
    val r = step.range(resource)
    return ladder.lowIndex(r) to ladder.highIndex(r)
}

fun FrequencyRange.of(min: Int, max: Int): FrequencyRange = FrequencyRange().also { it.min = min; it.max = max }

/** Wrapper that stores a ladder range as explicit frequencies; the bottom index means no minimum. */
fun rangeFromIndices(ladder: IntArray, lo: Int, hi: Int): FrequencyRange {
    if (ladder.isEmpty()) return FrequencyRange()
    val h = hi.coerceIn(0, ladder.lastIndex)
    val l = lo.coerceIn(0, h)
    return FrequencyRange().of(if (l == 0) -1 else ladder[l], ladder[h])
}
