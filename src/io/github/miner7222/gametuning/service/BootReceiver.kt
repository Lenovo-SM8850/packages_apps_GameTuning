// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the session service after boot and after the app is replaced. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> SessionService.start(context)
        }
    }
}
