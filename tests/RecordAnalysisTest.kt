// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning

import io.github.miner7222.gametuning.data.*
import io.github.miner7222.gametuning.ui.AdviceContext
import vendor.lenovo.hardware.gametuning.RecordingSample
import java.time.ZoneId
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Standalone JVM checks; Android Binder and device display changes are verified on device. */
fun main() {
    check(capFpsLimit(true, 60, 120) == 60)
    check(capFpsLimit(true, 40, 60) == 45)
    check(capFpsLimit(true, 60, 0) == 60)
    check(capFpsLimit(true, 60, 45) == null)
    check(capFpsLimit(true, 60, 60) == null)
    check(capFpsLimit(false, 60, 120) == null)
    check(capFpsLimit(true, 200, 0) == null)
    val display = DisplayValues(.85f, 120, true)
    val lowered = display.copy(fpsLimit = capFpsLimit(true, 40, display.fpsLimit)!!)
    check(lowered == DisplayValues(.85f, 45, true))
    val proposal = AdviceContext(7, "preset-a", display)
    check(proposal != proposal.copy(revision = 8))
    check(proposal != proposal.copy(settings = "preset-b"))
    check(proposal != proposal.copy(display = lowered))

    // A failing local upsert/restore must not strand the applied hardware or display state.
    fun runSuspending(block: suspend () -> Unit) {
        var completed = false
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); completed = true }
        })
        check(completed)
    }
    for (failingStep in 0..3) {
        var hardware = "proposal"
        var displayState = "proposal"
        var refresh = "proposal"
        var local = "proposal"
        val attempted = ArrayList<Int>()
        runSuspending {
            val failures = restoreIndependently(
                { attempted.add(0); if (failingStep == 0) throw IOException("daemon"); hardware = "previous" },
                { attempted.add(1); if (failingStep == 1) throw IOException("display"); displayState = "previous" },
                { attempted.add(2); if (failingStep == 2) throw IOException("refresh"); refresh = "previous" },
                { attempted.add(3); if (failingStep == 3) throw IOException("local storage"); local = "previous" },
            )
            check(failures.size == 1 && attempted == listOf(0,1,2,3))
            check((hardware == "previous") == (failingStep != 0))
            check((displayState == "previous") == (failingStep != 1))
            check((refresh == "previous") == (failingStep != 2))
            check((local == "previous") == (failingStep != 3))
        }
    }
    runSuspending {
        var hardware = "previous"; var displayState = "previous"
        val upsertError = IOException("upsert failed after setTuning")
        val restoreError = IOException("replaceAll failed on the same storage")
        try {
            hardware = "proposal"
            throw upsertError
        } catch (failure: IOException) {
            val errors = restoreIndependently(
                { hardware = "previous" },
                { displayState = "previous" },
                { throw restoreError },
            )
            errors.forEach { failure.addSuppressed(it) }
            check(failure === upsertError && failure.suppressed.single() === restoreError)
        }
        check(hardware == "previous" && displayState == "previous")
    }
    println("F1_INDEPENDENT_ROLLBACK_PASS local-storage daemon display refresh failures")
    check(displayAffectsProbe(60, .85f, 120, DisplayValues(1f, 60, true), 120))
    check(displayAffectsProbe(60, .85f, 120, DisplayValues(.86f, 60, true), 120))
    check(displayAffectsProbe(60, .85f, 120, DisplayValues(.85f, 60, false), 60))
    check(displayAffectsProbe(120, .85f, 120, DisplayValues(.85f, 60, true), 120))
    check(displayAffectsProbe(null, .85f, 120, DisplayValues(.85f, 60, true), 120))
    check(!displayAffectsProbe(60, .85f, 120, DisplayValues(.85f, 60, true), 120))
    println("F6_DISPLAY_INVALIDATION_PASS resolution-only refresh-only fps unknown unchanged")

    val samples = (0..20).map { second -> RecordingSample().also {
        it.timeMs = 20_000 + second * 1000
        it.goldCapKHz = if (second == 0) 3000 else 2000
        it.primeCapKHz = 3000; it.gpuCapHz = 300
    } }
    val events = keptAutoEvents(samples, 20_000, 40_000)
    check(events.size == 1 && events[0].timeMs == 1000 && events[0].durationMs == 19_000)
    val meta = CsvMeta("game.pkg", "Game", "Balanced", "Preset", 1_020_000, 20_000,
        "stopped", 60f, 59f, .2f, 1f, 38f, 4000, 120,
        originalStartEpochMs = 1_000_000, keepStartMs = 20_000, keepEndMs = 40_000, rangeRevision = 7)
    val csv = RecordCsv.preamble(meta, ZoneId.of("UTC"))
    check(csv.contains("original_start_epoch_ms") && csv.contains("1000000"))
    check(csv.contains("keep_start_ms") && csv.contains("20000") && csv.contains("range_revision"))
    check(RecordCsv.COLUMNS.first() == "time_s")
    println("APP_RECORD_ANALYSIS_PASS cap-steps off lower-limit uncertainty scale-lock stale-context clipped-events csv-provenance")
}
