// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vendor.lenovo.hardware.gametuning.RecordingInfo
import vendor.lenovo.hardware.gametuning.RecordingSample
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter

/** Saves one benchmark record as a CSV file the user picked with the system file picker. */
object RecordExport {
    /** Whether the record can be exported: the same test that lets it be opened. */
    fun available(info: RecordingInfo): Boolean = info.hasData()

    /** The file name the picker starts with. */
    fun defaultName(gameLabel: String, info: RecordingInfo): String =
        RecordCsv.fileName(gameLabel, info.startEpochMs)

    /** Writes the whole record to [uri] on the IO dispatcher; a half-written file is removed on failure. */
    suspend fun write(ctx: Context, repo: GameTuningRepository, info: RecordingInfo, gameLabel: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            val meta = metaOf(ctx, info, gameLabel)
            val resolver = ctx.contentResolver
            try {
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { out ->
                    out.append(RecordCsv.preamble(meta))
                    repo.forEachSamplePage(info.id) { page ->
                        for (s in page) out.append(RecordCsv.row(sampleOf(s)))
                    }
                }
            } catch (e: Exception) {
                try {
                    DocumentsContract.deleteDocument(resolver, uri)
                } catch (_: Exception) {
                    // The provider may not allow it; the failure message still tells the user.
                }
                throw e
            }
        }

    fun metaOf(ctx: Context, info: RecordingInfo, gameLabel: String): CsvMeta {
        val s = info.summary
        val mode = ctx.modeName(info.mode)
        return CsvMeta(
            packageName = info.packageName.orEmpty(),
            gameLabel = gameLabel,
            modeLabel = mode,
            presetLabel = mode + " · " + ctx.presetLabel(info),
            startEpochMs = info.startEpochMs,
            durationMs = info.durationMs,
            endReason = RecordCsv.endReasonName(info.endReason),
            averageFps = s.avgFps,
            steadyFloorFps = s.floor5Fps,
            tail95 = s.tail95,
            stutterPer1000 = s.jankPer1000,
            maxTemperatureC = s.maxSkinC,
            averagePowerMw = s.avgPowerMw,
            targetFps = s.targetFps,
        )
    }

    fun sampleOf(s: RecordingSample): CsvSample {
        val util = s.cpuUtilPermille
        return CsvSample(
            timeMs = s.timeMs, fps = s.fps, fpsMin = s.fpsMin, targetFps = s.targetFps, jankFrames = s.jankFrames, frames = s.frames,
            maxFrameTimeUs = s.maxFrameTimeUs,
            goldKHz = s.goldKHz, primeKHz = s.primeKHz, gpuHz = s.gpuHz,
            goldCapKHz = s.goldCapKHz, primeCapKHz = s.primeCapKHz, gpuCapHz = s.gpuCapHz,
            goldUtilPermille = util?.getOrNull(0) ?: -1, primeUtilPermille = util?.getOrNull(1) ?: -1,
            gpuBusyPercent = s.gpuBusyPercent,
            skinMilliC = s.skinMilliC, cpuMilliC = s.cpuMilliC, gpuMilliC = s.gpuMilliC, batteryMilliC = s.batteryMilliC,
            powerMw = s.powerMw, batteryPercent = s.batteryPercent,
            band = s.band, thermalCase = s.thermalCase, sceneId = s.sceneId, profileGeneration = s.profileGeneration,
        )
    }
}
