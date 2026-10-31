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
}
