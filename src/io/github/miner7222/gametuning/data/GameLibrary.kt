// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.app.GameManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** A game registered in Game Space with the profile it currently uses. */
data class GameInfo(val packageName: String, val label: String, val mode: Int)

/** What the game itself allows, without GameManager's intervention overrides. */
data class GameAllowances(val downscaling: Boolean, val fpsOverride: Boolean)

/** Reads and writes the game list the same way Game Space stores it. */
class GameLibrary(private val context: Context) {
    private val resolver = context.contentResolver
    private val packageManager = context.packageManager

    private fun rawEntries(userId: Int = UserHandle.USER_CURRENT): List<Pair<String, Int>> =
        Settings.System.getStringForUser(resolver, LIST_KEY, userId)
            ?.split(";")
            ?.filter { it.isNotEmpty() }
            ?.map { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: GameManager.GAME_MODE_STANDARD)
                else entry to GameManager.GAME_MODE_STANDARD
            } ?: emptyList()

    /** None is stored as zero and does not permit tuning. */
    fun isNoneMode(pkg: String, userId: Int = context.userId): Boolean =
        rawEntries(userId).any { (name, mode) -> name == pkg && mode == Modes.NONE }

    suspend fun games(): List<GameInfo> = withContext(Dispatchers.IO) {
        rawEntries().mapNotNull { (pkg, mode) ->
            val info = try {
                packageManager.getApplicationInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            GameInfo(pkg, GameNames.resolve(context, pkg, info.loadLabel(packageManager).toString(), installed = true), normalizeMode(mode))
        }.sortedBy { it.label.lowercase() }
    }

    /** Sets the profile the way Game Space does: game manager first, then the list entry. */
    suspend fun setMode(pkg: String, mode: Int) = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(GameManager::class.java)
        if (manager != null && manager.getGameMode(pkg) != mode) manager.setGameMode(pkg, mode)
        val entries = rawEntries().filter { it.first != pkg } + (pkg to mode)
        Settings.System.putStringForUser(
            resolver, LIST_KEY, entries.joinToString(";") { "${it.first}=${it.second}" },
            UserHandle.USER_CURRENT,
        )
    }

    fun currentMode(pkg: String): Int? = rawEntries().firstOrNull { it.first == pkg }?.second?.let(::normalizeMode)

    suspend fun allowances(pkg: String): GameAllowances = withContext(Dispatchers.IO) {
        try {
            val info = packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            info.loadXmlMetaData(packageManager, "android.game_mode_config")?.use { parser ->
                var type = parser.next()
                while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                    type = parser.next()
                }
                if (parser.name != "game-mode-config") return@withContext GameAllowances(true, true)
                val attrs = packageManager.getResourcesForApplication(info).obtainAttributes(
                    Xml.asAttributeSet(parser), com.android.internal.R.styleable.GameModeConfig,
                )
                try {
                    return@withContext GameAllowances(
                        attrs.getBoolean(com.android.internal.R.styleable.GameModeConfig_allowGameDownscaling, true),
                        attrs.getBoolean(com.android.internal.R.styleable.GameModeConfig_allowGameFpsOverride, true),
                    )
                } finally {
                    attrs.recycle()
                }
            }
            GameAllowances(
                info.metaData?.getBoolean("com.android.graphics.intervention.wm.allowDownscale", true) ?: true,
                true,
            )
        } catch (_: Exception) {
            // An unreadable manifest must not grant permission to override interventions.
            GameAllowances(downscaling = false, fpsOverride = false)
        }
    }

    suspend fun icon(pkg: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            toBitmap(packageManager.getApplicationIcon(pkg), sizePx).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun label(pkg: String, originalName: String = pkg): String {
        return try {
            val label = packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
            GameNames.resolve(context, pkg, label, installed = true)
        } catch (_: PackageManager.NameNotFoundException) {
            GameNames.resolve(context, pkg, originalName, installed = false)
        }
    }

    fun gameSpaceIntent(): Intent? = packageManager.getLaunchIntentForPackage(GAME_SPACE)

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }

    private fun normalizeMode(mode: Int) = if (mode == Modes.NONE || mode in Modes.ALL.toList()) mode else Modes.BALANCED

    companion object {
        const val LIST_KEY = "gamespace_game_list"
        private const val GAME_SPACE = "io.chaldeaprjkt.gamespace"
    }
}
