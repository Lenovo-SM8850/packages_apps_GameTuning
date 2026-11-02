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
import vendor.lenovo.hardware.gametuning.GraphicsOptions
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
class GameTuningRepository(private val context: Context) {
    private val userId: Int = context.userId
    @Volatile private var service: IGameTuning? = null
    @Volatile private var limitsCache: PolicyLimits? = null
    @Volatile private var dedicatedCache: Set<String>? = null

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
        if (l.goldKHz?.isNotEmpty() == true && l.primeKHz?.isNotEmpty() == true &&
            l.gpuHz?.isNotEmpty() == true && l.goldCores > 0 && l.primeCores > 0) limitsCache = l
        return l
    }

    private suspend fun allProfileSources(): List<ProfileSource> = call { it.profileSources }.toList()

    suspend fun profileSources(): List<ProfileSource> = allProfileSources()
        .filterNot { it.profileKey.startsWith("@builtin/") }.map { source ->
        if (source.profileKey != GENERIC_PROFILE) {
            source.displayName = GameLibrary(context).label(source.profileKey, source.displayName.orEmpty())
        }
        source
    }

    /** Reads one transition per Binder call without changing tuning or display settings. */
    suspend fun syncBuiltins() = withContext(Dispatchers.IO) {
        val sources = allProfileSources()
        if (sources.any { it.profileKey == "@builtin/pending" })
            throw TuningException(TuningException.UNAVAILABLE, "Built-in snapshot pending")
        val marker = sources.firstOrNull { it.profileKey.startsWith("@builtin/current/") }
            ?.profileKey ?: return@withContext
        val store = PresetStore.getInstance(context)
        val games = GameLibrary(context)
        syncBuiltinHistory(store, userId, marker, games.gamesForUser(userId).map { it.packageName to it.mode },
            { !games.isNoneMode(it, userId) }, { key, mode -> sourceProfile(key, mode) }) {
                context.getString(io.github.miner7222.gametuning.R.string.builtin_archived, it)
            }
    }

    /** Packages that have a built-in profile of their own; every other game uses the generic one. */
    suspend fun dedicatedPackages(): Set<String> {
        dedicatedCache?.let { return it }
        val set = profileSources().filter { it.profileKey != GENERIC_PROFILE }
            .flatMap { listOf(it.profileKey) + it.packages.orEmpty() }.toSet()
        dedicatedCache = set
        return set
    }

    suspend fun profile(pkg: String, mode: Int, tuned: Boolean): PolicyProfile =
        call { it.getProfile(userId, pkg, mode, tuned) }

    suspend fun sourceProfile(key: String, baseMode: Int): PolicyProfile =
        call { it.getSourceProfile(key, baseMode) }

    suspend fun tunings(): List<GameTuningConfig> = call { it.getTunings(userId) }.toList()

    suspend fun preview(config: GameTuningConfig): PolicyProfile = call { it.previewTuning(config) }

    suspend fun setTuning(config: GameTuningConfig): GameTuningConfig = call { it.setTuning(config) }

    suspend fun clearTuning(pkg: String, mode: Int) = call { it.clearTuning(userId, pkg, mode) }

    suspend fun graphicsOptions(pkg: String): GraphicsOptions? = try {
        call { if (it.graphicsSupport) it.getGraphicsOptions(pkg) else null }
    } catch (_: TuningException) {
        null
    }

    suspend fun setGraphicsOptions(pkg: String, options: GraphicsOptions) = call { it.setGraphicsOptions(pkg, options) }

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
    suspend fun setRange(info: RecordingInfo, startMs: Int, endMs: Int): RecordingInfo =
        call { it.setRecordingRange(userId, info.id, startMs, endMs, info.rangeRevision) }

    suspend fun invalidateDisplayProbe(pkg: String, mode: Int) = call { it.invalidateDisplayProbe(userId, pkg, mode) }

    suspend fun recording(id: String): RecordingInfo = allRecordings().firstOrNull { it.id == id }
        ?: throw TuningException(IGameTuning.ERROR_NOT_FOUND, "No such record")

    suspend fun samples(id: String, revision: Long? = null, raw: Boolean = false): List<RecordingSample> {
        val expected = revision ?: recording(id).rangeRevision
        val all = ArrayList<RecordingSample>()
        var offset = 0
        while (offset < MAX_SAMPLES) {
            val part = call {
                if (raw) it.getRawRecordingSamples(userId, id, offset, expected)
                else it.getEffectiveRecordingSamples(userId, id, offset, expected)
            }
            if (part.isEmpty()) break
            all.addAll(part)
            offset += part.size
            if (part.size < PAGE) break
        }
        return all
    }

    /** Hands a record to [onPage] one page of [PAGE] samples at a time, with no cap on its length. */
    suspend fun forEachSamplePage(id: String, revision: Long, onPage: (List<RecordingSample>) -> Unit) {
        var offset = 0
        while (offset < EXPORT_LIMIT) {
            val part = call { it.getEffectiveRecordingSamples(userId, id, offset, revision) }
            if (part.isEmpty()) break
            onPage(part.asList())
            offset += part.size
            if (part.size < PAGE) break
        }
    }

    companion object {
        /** Shared by the Binder-backed sync pass and the host protocol integration check. */
        suspend fun syncBuiltinHistory(store: PresetStore, userId: Int, marker: String,
            games: List<Pair<String, Int>>, enabled: (String) -> Boolean,
            source: suspend (String, Int) -> PolicyProfile, label: (String) -> String) {
            val parts = marker.split('/')
            require(parts.size == 4 && parts[0] == "@builtin" && parts[1] == "current" &&
                parts[2].matches(Regex("[0-9a-f]{32}"))) { "Invalid archive epoch" }
            val epoch = parts[2]
            val generation = parts[3].toLongOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Invalid archive generation")
            val previousEpoch = store.builtinEpoch()
            if (previousEpoch == epoch) require(generation >= store.builtinGeneration()) { "Archive generation regressed" }
            val updates = ArrayList<BuiltinSyncUpdate>()
            for ((pkg, gameMode) in games) {
                if (gameMode == Modes.NONE || !enabled(pkg)) continue
                for (mode in Modes.ALL) {
                    val checkpoint = if (epoch == previousEpoch) store.builtinCheckpoint(pkg, mode) else null
                    if (checkpoint != null && checkpoint.generation == generation) continue
                    if (checkpoint != null) require(checkpoint.generation < generation) { "Archive generation regressed" }
                    val transitions = ArrayList<BuiltinTransition>()
                    if (checkpoint != null) {
                        var after = checkpoint.generation
                        while (after < generation) {
                            val next = source("@builtin/history/$epoch/$pkg/$after", mode)
                            if (next.profileKey.isNullOrEmpty()) break
                            val g = next.variantId?.toLongOrNull()
                                ?: throw IllegalArgumentException("Invalid archive generation")
                            require(g > after && g <= generation && next.profileKey.startsWith("@builtin/$epoch/$g/")) {
                                "Invalid archive transition"
                            }
                            transitions.add(BuiltinTransition(next.profileKey, g, next.variantName.orEmpty()))
                            after = g
                        }
                    }
                    updates.add(BuiltinSyncUpdate(pkg, mode, transitions))
                }
            }
            val eligible = updates.filter { enabled(it.pkg) }
            if (eligible.isNotEmpty() || epoch != previousEpoch || generation != store.builtinGeneration())
                store.syncBuiltinsBatch(epoch, generation, eligible, userId, previousEpoch, label)
        }
        const val NAME = "vendor.lenovo.hardware.gametuning.IGameTuning/default"
        private const val TAG = "GameTuning"
        private const val PAGE = 600
        private const val MAX_SAMPLES = 7200
        private const val EXPORT_LIMIT = 1_000_000
    }
}
