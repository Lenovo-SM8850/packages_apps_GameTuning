// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import io.github.miner7222.gametuning.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read-only view of the preset that is active for one game in one profile, for the apps that
 * hold [READ_PRESETS]. The lookup mirrors the one the app itself uses to build its library,
 * but never writes anything.
 */
class PresetProvider : ContentProvider() {
    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lookupMutex = Mutex()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        if (uri.pathSegments != listOf(PATH_ACTIVE)) return null
        val pkg = uri.getQueryParameter(PARAM_PACKAGE)?.takeIf { it.isNotEmpty() } ?: return null
        val rawMode = uri.getQueryParameter(PARAM_MODE)?.toIntOrNull() ?: return null
        val mode = if (rawMode == Modes.NONE || rawMode in Modes.ALL.toList()) rawMode else Modes.BALANCED
        val ctx = context ?: return null
        if (mode == Modes.NONE) {
            return MatrixCursor(arrayOf(COLUMN_MODE_LABEL, COLUMN_PRESET_LABEL), 1).apply {
                addRow(arrayOf(ctx.modeName(mode), ""))
            }
        }
        // At most one outstanding daemon lookup, even if a live Binder call stops answering.
        if (!lookupMutex.tryLock()) return null
        val lookup = lookups.async {
            try {
                val tuning = GameTuningRepository(ctx).tunings()
                    .firstOrNull { it.packageName == pkg && it.mode == mode && it.enabled }
                if (tuning == null) {
                    ctx.getString(R.string.builtin_setting)
                } else {
                    val id = tuning.variantId.orEmpty()
                    tuning.variantName.orEmpty().ifEmpty {
                        (if (id.isNotEmpty()) PresetStore.getInstance(ctx).get(pkg, mode, id)?.name else null)
                            ?: ctx.getString(R.string.current_setting)
                    }
                }
            } finally {
                lookupMutex.unlock()
            }
        }
        val presetName = try {
            // The lookup has its own scope so timeout never waits for synchronous Binder IO.
            runBlocking { withTimeoutOrNull(1500L) { lookup.await() } } ?: return null
        } catch (e: Exception) {
            Log.w(TAG, "Could not look up the active preset", e)
            return null
        }
        return MatrixCursor(arrayOf(COLUMN_MODE_LABEL, COLUMN_PRESET_LABEL), 1).apply {
            addRow(arrayOf(ctx.modeName(mode), presetName))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.active"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    companion object {
        private const val TAG = "GameTuning"
        const val AUTHORITY = "io.github.miner7222.gametuning.presets"
        const val PATH_ACTIVE = "active"
        const val PARAM_PACKAGE = "package"
        const val PARAM_MODE = "mode"
        const val COLUMN_MODE_LABEL = "mode_label"
        const val COLUMN_PRESET_LABEL = "preset_label"
    }
}
