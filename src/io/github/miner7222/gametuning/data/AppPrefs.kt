// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context

/** Small per-viewer settings kept in preferences. */
class AppPrefs(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    var tipDismissed: Boolean
        get() = prefs.getBoolean("tip_dismissed", false)
        set(v) = prefs.edit().putBoolean("tip_dismissed", v).apply()

    /** Keep observed support for this user even after the proving recording is deleted. */
    fun scenesSupported(pkg: String, observed: Boolean): Boolean {
        val key = "scene_support_$pkg"
        val known = prefs.getBoolean(key, false)
        if (observed && !known) prefs.edit().putBoolean(key, true).apply()
        return known || observed
    }
}
