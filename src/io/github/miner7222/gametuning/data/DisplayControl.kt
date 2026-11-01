// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.app.ActivityManager
import android.app.IGameManagerService
import android.content.Context
import android.os.ServiceManager
import android.util.Log
import com.android.internal.util.evolution.DisplayRefreshRateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.evolution.display.RefreshRateManager

/**
 * Applies the app-side values of a preset: render resolution and frame rate limit through the
 * game manager, and the refresh rate lock through the per-app refresh rate setting.
 */
class DisplayControl(private val context: Context) {
    private val userId = context.userId
    private val games = GameLibrary(context)
    private val refreshWrites = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("refresh_writes_u$userId", Context.MODE_PRIVATE)

    private fun gameService(): IGameManagerService? = try {
        IGameManagerService.Stub.asInterface(ServiceManager.getService(Context.GAME_SERVICE))
    } catch (_: RuntimeException) {
        null
    }

    /** The values the game manager holds now; used to avoid needless writes. */
    suspend fun readResolution(pkg: String, mode: Int): Float = withContext(Dispatchers.IO) {
        try {
            val f = gameService()?.getResolutionScalingFactor(pkg, mode, userId) ?: -1f
            if (f <= 0f) 1.0f else f
        } catch (_: Exception) {
            1.0f
        }
    }

    /**
     * Writes the render resolution and the frame rate limit of one profile through the game
     * manager's hidden `setGameModeConfigOverride(pkg, user, mode, fps, scaling)`. The service
     * switches the game to that profile as a side effect, so the previous profile is restored
     * afterwards. fps "0" means no override and scaling "-1" removes the scaling override (the
     * tokens the service's own shell command passes for "disable"). Games opting out of either
     * intervention are left alone: creating any override would enable both allowance flags.
     * When the method is missing (older framework), the render resolution is written
     * through `updateResolutionScalingFactor` and the frame rate is left to the refresh rate.
     * Returns false when something could not be applied.
     */
    suspend fun applyGameManager(
        pkg: String, mode: Int, display: DisplayValues, allow: GameAllowances,
    ): Boolean = withContext(Dispatchers.IO) {
        displayMutex.withLock {
            if (games.isNoneMode(pkg, userId)) {
                clearRefreshLocked(pkg)
                return@withLock true
            }
            if (!allow.downscaling || !allow.fpsOverride) return@withLock true
            val service = gameService() ?: return@withContext false
            val scaling = if (display.resolution >= 0.9999f) "-1"
            else String.format(java.util.Locale.US, "%.2f", display.resolution)
            val fps = display.fpsLimit.coerceAtLeast(0).toString()
            try {
                val previous = service.getGameMode(pkg, userId)
                var ok = true
                try {
                    service.setGameModeConfigOverride(pkg, userId, mode, fps, scaling)
                } catch (e: NoSuchMethodError) {
                    ok = legacyResolution(service, pkg, mode, display, allow)
                } catch (e: AbstractMethodError) {
                    ok = legacyResolution(service, pkg, mode, display, allow)
                }
                if (service.getGameMode(pkg, userId) != previous) service.setGameMode(pkg, previous, userId)
                ok
            } catch (e: Exception) {
                Log.w(TAG, "Could not set the game manager override", e)
                false
            }
        }
    }

    private fun legacyResolution(
        service: IGameManagerService, pkg: String, mode: Int, display: DisplayValues, allow: GameAllowances,
    ): Boolean {
        if (!allow.downscaling) return true
        val factor = if (display.resolution >= 0.9999f) -1f else display.resolution
        service.updateResolutionScalingFactor(pkg, mode, factor, userId)
        return true
    }

    /** True when this game has a per-app refresh rate (key refresh_rate_config_custom). */
    fun isRefreshLocked(pkg: String): Boolean = try {
        (context.getSystemService(RefreshRateManager::class.java)?.getRefreshRateForPackage(pkg) ?: -1) > 0
    } catch (_: Exception) {
        false
    }

    /** The per-app refresh rate a preset asks for, or -1 for none. */
    fun refreshTarget(display: DisplayValues, fpsAllowed: Boolean): Int {
        val rates = try {
            DisplayRefreshRateHelper.getInstance(context).supportedRefreshRateList.sorted()
        } catch (_: Exception) {
            emptyList()
        }
        if (rates.isEmpty()) return -1
        if (display.lockRefresh) return rates.last()
        if (display.fpsLimit > 0 && fpsAllowed) return rates.firstOrNull { it >= display.fpsLimit } ?: rates.last()
        return -1
    }

    /** Releases only a recorded write that still matches an applied or pending value. */
    private fun clearRefreshLocked(pkg: String) {
        if (!refreshWrites.contains("$pkg.applied")) return
        try {
            // The refresh manager operates on the foreground user's map.
            if (ActivityManager.getCurrentUser() != userId) return
            val manager = context.getSystemService(RefreshRateManager::class.java) ?: return
            val current = manager.getRefreshRateForPackage(pkg).let { if (it > 0) it else -1 }
            if (current == refreshWrites.getInt("$pkg.applied", -1) ||
                (refreshWrites.contains("$pkg.pending") && current == refreshWrites.getInt("$pkg.pending", -1))) {
                val previous = refreshWrites.getInt("$pkg.previous", -1)
                if (previous != current) {
                    if (previous > 0) manager.setRefreshRateForPackage(pkg, previous)
                    else if (current > 0) manager.unsetRefreshRateForPackage(pkg)
                    val restored = manager.getRefreshRateForPackage(pkg).let { if (it > 0) it else -1 }
                    if (restored != previous) return
                }
            }
            // A later edit by another owner wins, even when we still have a saved value.
            refreshWrites.edit().remove("$pkg.previous").remove("$pkg.applied")
                .remove("$pkg.pending").commit()
        } catch (e: Exception) {
            Log.w(TAG, "Could not release the per-app refresh rate", e)
        }
    }

    /** Checks None before a UI operation and releases any refresh override owned by this app. */
    suspend fun clearRefreshIfNone(pkg: String): Boolean = withContext(Dispatchers.IO) {
        displayMutex.withLock {
            if (!games.isNoneMode(pkg, userId)) return@withLock false
            clearRefreshLocked(pkg)
            true
        }
    }

    /** Also runs at service startup so persistent writes are handled after a restart. */
    suspend fun clearNoneRefresh() {
        for (key in refreshWrites.all.keys.filter { it.endsWith(".applied") }) {
            clearRefreshIfNone(key.removeSuffix(".applied"))
        }
    }

    /**
     * Applies the refresh rate part of a preset through the ROM's per-app refresh rate setting: the
     * lock uses the highest mode, a frame rate limit uses the smallest mode that is not below it,
     * so a limit above the system peak rate still gets its rate. No target releases only our write.
     */
    suspend fun applyRefresh(pkg: String, mode: Int) =
        withContext(Dispatchers.IO) {
            displayMutex.withLock {
                try {
                    if (games.isNoneMode(pkg, userId)) {
                        clearRefreshLocked(pkg)
                        return@withLock
                    }
                    if (ActivityManager.getCurrentUser() != userId) return@withLock
                    // Refresh settings are package-wide. Serialize with temporary mode switches above.
                    if (gameService()?.getGameMode(pkg, userId) != mode) return@withLock
                    val tuning = GameTuningRepository(context).tunings().firstOrNull {
                        it.packageName == pkg && it.mode == mode && it.enabled
                    }
                    val display = tuning?.variantId?.let {
                        PresetStore.getInstance(context).get(pkg, mode, it)?.display
                    } ?: DisplayValues.builtin(mode)
                    val fpsAllowed = GameLibrary(context).allowances(pkg).fpsOverride
                    val manager = context.getSystemService(RefreshRateManager::class.java) ?: return@withContext
                    val target = refreshTarget(display, fpsAllowed)
                    if (gameService()?.getGameMode(pkg, userId) != mode) return@withLock
                    if (games.isNoneMode(pkg, userId)) {
                        clearRefreshLocked(pkg)
                        return@withLock
                    }
                    if (target <= 0) {
                        clearRefreshLocked(pkg)
                        return@withLock
                    }
                    val current = manager.getRefreshRateForPackage(pkg).let { if (it > 0) it else -1 }
                    if (current == target) return@withLock
                    val owned = refreshWrites.contains("$pkg.applied") &&
                        (refreshWrites.getInt("$pkg.applied", -1) == current ||
                            (refreshWrites.contains("$pkg.pending") && refreshWrites.getInt("$pkg.pending", -1) == current))
                    val previous = if (owned) refreshWrites.getInt("$pkg.previous", -1) else current
                    // Keep both sides of an unfinished write so a restart can release either value.
                    if (!refreshWrites.edit().putInt("$pkg.previous", previous)
                            .putInt("$pkg.applied", current).putInt("$pkg.pending", target).commit()) return@withLock
                    manager.setRefreshRateForPackage(pkg, target)
                    refreshWrites.edit().putInt("$pkg.applied", target).remove("$pkg.pending").commit()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not change the per-app refresh rate", e)
                }
            }
        }

    companion object {
        private const val TAG = "GameTuning"
        private val displayMutex = Mutex()
    }
}
