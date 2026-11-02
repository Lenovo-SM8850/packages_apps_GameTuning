// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.DisplayValues
import io.github.miner7222.gametuning.data.PresetStore
import io.github.miner7222.gametuning.data.DisplayControl
import io.github.miner7222.gametuning.data.GameLibrary
import io.github.miner7222.gametuning.data.GameTuningRepository
import kotlinx.coroutines.runBlocking
import vendor.lenovo.hardware.gametuning.IGameTuning
import vendor.lenovo.hardware.gametuning.ISessionListener

/**
 * Follows game sessions for the whole user. It registers a session listener with the tuning daemon,
 * shows a short notice when a session is being recorded and reapplies the refresh-rate floor on
 * session or mode changes. Started at boot, again when the app is replaced and whenever the app
 * opens; it registers again when the daemon restarts.
 */
class SessionService : Service() {
    private val worker = HandlerThread("GameTuningSession")
    private val archiveWorker = HandlerThread("GameTuningArchive", android.os.Process.THREAD_PRIORITY_BACKGROUND)
    private lateinit var archiveHandler: Handler
    private lateinit var handler: Handler
    private val main = Handler(Looper.getMainLooper())

    // Everything below belongs to the worker thread.
    private var daemon: IGameTuning? = null
    private var daemonBinder: IBinder? = null
    private var retryDelayMs = RETRY_MIN_MS
    private var noticeFor: String? = null
    private lateinit var gameListObserver: ContentObserver

    private val userSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.post { clearNoneRefresh() }
        }
    }

    private val listener = object : ISessionListener.Stub() {
        override fun onSessionChanged(userId: Int, packageName: String?, mode: Int, active: Boolean, recording: Boolean) {
            val pkg = packageName ?: return
            if (userId != this@SessionService.userId) return
            handler.post { onSession(pkg, mode, active, recording) }
        }

        override fun getInterfaceVersion(): Int = ISessionListener.VERSION

        override fun getInterfaceHash(): String = ISessionListener.HASH
    }

    private val death = IBinder.DeathRecipient {
        handler.post {
            Log.w(TAG, "The tuning daemon died, registering again")
            daemon = null
            daemonBinder = null
            noticeFor = null
            scheduleConnect(RETRY_MIN_MS)
        }
    }

    private val connectTask = Runnable { connect() }
    private val archiveTask = Runnable { syncBuiltins() }

    private fun scheduleArchiveSync() {
        archiveHandler.removeCallbacks(archiveTask)
        archiveHandler.postDelayed(archiveTask, 1000)
    }

    private fun syncBuiltins() {
        archiveHandler.removeCallbacks(archiveTask)
        try {
            runBlocking { GameTuningRepository(this@SessionService).syncBuiltins() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not synchronize built-in history", e)
            archiveHandler.postDelayed(archiveTask, RETRY_MAX_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        worker.start()
        archiveWorker.start()
        archiveHandler = Handler(archiveWorker.looper)
        handler = Handler(worker.looper)
        gameListObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                clearNoneRefresh()
                scheduleArchiveSync()
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(GameLibrary.LIST_KEY), false, gameListObserver, userId,
        )
        registerReceiver(userSwitchReceiver, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
        handler.post { clearNoneRefresh() }
        handler.post(connectTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(connectTask)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        archiveHandler.removeCallbacks(archiveTask)
        archiveWorker.quitSafely()
        contentResolver.unregisterContentObserver(gameListObserver)
        unregisterReceiver(userSwitchReceiver)
        handler.post {
            try {
                daemonBinder?.unlinkToDeath(death, 0)
                daemon?.unregisterSessionListener(listener)
            } catch (_: RuntimeException) {
            } catch (_: RemoteException) {
            }
            worker.quitSafely()
        }
        super.onDestroy()
    }

    private fun scheduleConnect(delayMs: Long) {
        handler.removeCallbacks(connectTask)
        handler.postDelayed(connectTask, delayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    /** Registers the listener; a failed attempt is retried with a growing delay. */
    private fun connect() {
        if (daemon != null) { scheduleArchiveSync(); return }
        try {
            if (!ServiceManager.isDeclared(GameTuningRepository.NAME)) {
                stopSelf()
                return
            }
            val binder = ServiceManager.checkService(GameTuningRepository.NAME)
            if (binder == null) {
                scheduleConnect(retryDelayMs)
                return
            }
            val svc = IGameTuning.Stub.asInterface(binder)
            binder.linkToDeath(death, 0)
            try {
                svc.registerSessionListener(listener)
            } catch (e: Exception) {
                runCatching { binder.unlinkToDeath(death, 0) }
                throw e
            }
            daemon = svc
            daemonBinder = binder
            retryDelayMs = RETRY_MIN_MS
            scheduleArchiveSync()
        } catch (e: RemoteException) {
            Log.w(TAG, "Cannot register the session listener: ${e.message}")
            scheduleConnect(retryDelayMs)
        } catch (e: ServiceSpecificException) {
            Log.w(TAG, "The daemon refused the session listener: ${e.message}")
            scheduleConnect(retryDelayMs)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to register the session listener: ${e.message}")
            scheduleConnect(retryDelayMs)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot reach the tuning daemon: ${e.message}")
            scheduleConnect(retryDelayMs)
        }
    }

    private fun onSession(pkg: String, mode: Int, active: Boolean, recording: Boolean) {
        if (!active) {
            try {
                runBlocking { DisplayControl(this@SessionService).clearRefreshIfNone(pkg) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not release the session refresh rate", e)
            }
            if (noticeFor == pkg) noticeFor = null
            return
        }
        applySessionRefresh(pkg, mode)
        if (recording) captureRecordingContext(pkg, mode)
        // A profile change inside a session repeats the event; the notice shows once per session.
        if (recording && noticeFor != pkg) {
            noticeFor = pkg
            main.post { Toast.makeText(applicationContext, R.string.rec_toast, Toast.LENGTH_SHORT).show() }
        } else if (!recording && noticeFor == pkg) {
            noticeFor = null
        }
    }

    private fun captureRecordingContext(pkg: String, mode: Int) {
        try {
            runBlocking {
                val config = GameTuningRepository(this@SessionService).tunings().firstOrNull {
                    it.packageName == pkg && it.mode == mode && it.enabled
                }
                val chosen = config?.variantId?.let { PresetStore.getInstance(this@SessionService).get(pkg, mode, it)?.display }
                    ?: DisplayValues.builtin(mode)
                val allowed = GameLibrary(this@SessionService).allowances(pkg).fpsOverride
                val refresh = getSystemService(android.hardware.display.DisplayManager::class.java)
                    ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: -1f
                // Off is unknown intent. Actual refresh is retained separately, never treated as a chosen FPS target.
                daemon?.setRecordingContext(userId, pkg, if (allowed && chosen.fpsLimit > 0) chosen.fpsLimit else -1,
                    DisplayControl(this@SessionService).readResolution(pkg, mode), refresh)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not capture recording context", e)
        }
    }

    private fun applySessionRefresh(pkg: String, mode: Int) {
        try {
            runBlocking {
                DisplayControl(this@SessionService).applyRefresh(pkg, mode)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply the session refresh rate", e)
        }
    }

    private fun clearNoneRefresh() {
        try {
            runBlocking { DisplayControl(this@SessionService).clearNoneRefresh() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release refresh rates for None games", e)
        }
    }

    companion object {
        private const val TAG = "GameTuningSession"
        private const val RETRY_MIN_MS = 2_000L
        private const val RETRY_MAX_MS = 60_000L

        /** Starts the service; harmless when it already runs. */
        fun start(context: Context) {
            try {
                context.startService(Intent(context, SessionService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "The service may not start in the background now", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "The service may not start now", e)
            }
        }
    }
}
