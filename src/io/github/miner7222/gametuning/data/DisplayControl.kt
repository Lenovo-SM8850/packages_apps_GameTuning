// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

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

    /**
     * Applies the refresh rate part of a preset through the ROM's per-app refresh rate setting: the
     * lock uses the highest mode, a frame rate limit uses the smallest mode that is not below it,
     * so a limit above the system peak rate still gets its rate. Nothing asked removes the entry.
     */
    suspend fun applyRefresh(pkg: String, mode: Int) =
        withContext(Dispatchers.IO) {
            displayMutex.withLock {
                try {
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
                    val current = manager.getRefreshRateForPackage(pkg)
                    if (target > 0) {
                        if (current != target) manager.setRefreshRateForPackage(pkg, target)
                    } else if (current > 0) {
                        manager.unsetRefreshRateForPackage(pkg)
                    }
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
