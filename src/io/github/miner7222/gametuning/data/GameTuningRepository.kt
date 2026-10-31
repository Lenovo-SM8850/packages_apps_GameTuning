// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vendor.lenovo.hardware.gametuning.GameTuningConfig
import vendor.lenovo.hardware.gametuning.IGameTuning
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.PolicyProfile
import vendor.lenovo.hardware.gametuning.ProfileSource
import vendor.lenovo.hardware.gametuning.Recommendation
import vendor.lenovo.hardware.gametuning.RecordingInfo
import vendor.lenovo.hardware.gametuning.RecordingSample
import vendor.lenovo.hardware.gametuning.TuningGlobals
import vendor.lenovo.hardware.gametuning.TuningProposal

/** Thrown when the daemon refuses a value or is not reachable. */
class TuningException(val code: Int, message: String?, cause: Throwable? = null) :
    Exception(message, cause) {
    companion object {
        const val UNAVAILABLE = -1
    }
}

/** Wraps the vendor tuning service. Every call runs off the main thread. */
class GameTuningRepository(context: Context) {
    private val userId: Int = context.userId
    @Volatile private var service: IGameTuning? = null
    @Volatile private var limitsCache: PolicyLimits? = null

    /** False when the device does not declare the service. */
    val declared: Boolean = try {
        ServiceManager.isDeclared(NAME)
    } catch (_: RuntimeException) {
        false
    }

    private fun connect(): IGameTuning? {
        service?.let { return it }
        if (!declared) return null
        val binder: IBinder? = ServiceManager.checkService(NAME)
        val svc = binder?.let { IGameTuning.Stub.asInterface(it) }
        service = svc
        return svc
    }

    private suspend fun <T> call(block: (IGameTuning) -> T): T = withContext(Dispatchers.IO) {
        var lastError: TuningException? = null
        for (attempt in 0..1) {
            val svc = connect() ?: throw TuningException(TuningException.UNAVAILABLE, "Service not available")
            try {
                return@withContext block(svc)
            } catch (e: DeadObjectException) {
                service = null
                lastError = TuningException(TuningException.UNAVAILABLE, "Service died", e)
            } catch (e: ServiceSpecificException) {
                throw TuningException(e.errorCode, e.message, e)
            } catch (e: RemoteException) {
                service = null
                throw TuningException(TuningException.UNAVAILABLE, e.message, e)
            } catch (e: SecurityException) {
                throw TuningException(TuningException.UNAVAILABLE, e.message, e)
            }
        }
        throw lastError ?: TuningException(TuningException.UNAVAILABLE, "Service not available")
    }

    /** True when the service answers. */
    suspend fun isAvailable(): Boolean = try {
        limits()
        true
    } catch (e: TuningException) {
        Log.w(TAG, "Tuning service unavailable: ${e.message}")
        false
    }

    suspend fun limits(): PolicyLimits {
        limitsCache?.let { return it }
        val l = call { it.limits }
        limitsCache = l
        return l
    }

    suspend fun profileSources(): List<ProfileSource> = call { it.profileSources }.toList()

    suspend fun profile(pkg: String, mode: Int, tuned: Boolean): PolicyProfile =
        call { it.getProfile(userId, pkg, mode, tuned) }

    suspend fun sourceProfile(key: String, baseMode: Int): PolicyProfile =
        call { it.getSourceProfile(key, baseMode) }

    suspend fun tunings(): List<GameTuningConfig> = call { it.getTunings(userId) }.toList()

    suspend fun preview(config: GameTuningConfig): PolicyProfile = call { it.previewTuning(config) }

    suspend fun setTuning(config: GameTuningConfig): GameTuningConfig = call { it.setTuning(config) }

    suspend fun clearTuning(pkg: String, mode: Int) = call { it.clearTuning(userId, pkg, mode) }

    suspend fun globals(): TuningGlobals = call { it.globals }

    suspend fun setGlobals(globals: TuningGlobals) = call { it.setGlobals(globals) }

    suspend fun armedPackages(): Set<String> = call { it.getArmedPackages(userId) }.toSet()

    suspend fun setArmed(pkg: String, armed: Boolean) = call { it.setRecordingArmed(userId, pkg, armed) }

    suspend fun recordings(pkg: String): List<RecordingInfo> =
        call { it.getRecordings(userId, pkg) }.toList()

    suspend fun allRecordings(): List<RecordingInfo> = call { it.getRecordings(userId, "") }.toList()

    suspend fun deleteRecording(id: String) = call { it.deleteRecording(userId, id) }

    suspend fun recommend(id: String, goal: Int): List<Recommendation> =
        call { it.recommend(userId, id, goal) }.toList()

    suspend fun autoTune(id: String, goal: Int): TuningProposal = call { it.autoTune(userId, id, goal) }

    /** Reads a record in pages of [PAGE] samples. */
    suspend fun samples(id: String): List<RecordingSample> {
        val all = ArrayList<RecordingSample>()
        var offset = 0
        while (offset < MAX_SAMPLES) {
            val part = call { it.getRecordingSamples(userId, id, offset) }
            if (part.isEmpty()) break
            all.addAll(part)
            offset += part.size
            if (part.size < PAGE) break
        }
        return all
    }

    /** Hands a record to [onPage] one page of [PAGE] samples at a time, with no cap on its length. */
    suspend fun forEachSamplePage(id: String, onPage: (List<RecordingSample>) -> Unit) {
        var offset = 0
        while (offset < EXPORT_LIMIT) {
            val part = call { it.getRecordingSamples(userId, id, offset) }
            if (part.isEmpty()) break
            onPage(part.asList())
            offset += part.size
            if (part.size < PAGE) break
        }
    }

    companion object {
        const val NAME = "vendor.lenovo.hardware.gametuning.IGameTuning/default"
        private const val TAG = "GameTuning"
        private const val PAGE = 600
        private const val MAX_SAMPLES = 7200
        private const val EXPORT_LIMIT = 1_000_000
    }
}
