// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.AppPrefs
import io.github.miner7222.gametuning.data.Change
import io.github.miner7222.gametuning.data.ImportPreview
import io.github.miner7222.gametuning.data.importStepChanges
import io.github.miner7222.gametuning.data.DisplayControl
import io.github.miner7222.gametuning.data.DisplayValues
import io.github.miner7222.gametuning.data.GameAllowances
import io.github.miner7222.gametuning.data.GENERIC_PROFILE
import io.github.miner7222.gametuning.data.GameLibrary
import io.github.miner7222.gametuning.data.GameTuningRepository
import io.github.miner7222.gametuning.data.KEEP
import io.github.miner7222.gametuning.data.MAX_PRESETS
import io.github.miner7222.gametuning.data.customCount
import io.github.miner7222.gametuning.data.RecordExport
import io.github.miner7222.gametuning.data.ModeLibrary
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.data.Preset
import io.github.miner7222.gametuning.data.GraphicsValues
import io.github.miner7222.gametuning.data.PresetStore
import io.github.miner7222.gametuning.data.Resource
import io.github.miner7222.gametuning.data.TemperatureFormat
import io.github.miner7222.gametuning.data.TuningException
import io.github.miner7222.gametuning.data.Freq
import io.github.miner7222.gametuning.data.indexAtOrBelow
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.onOff
import io.github.miner7222.gametuning.data.signedSteps
import io.github.miner7222.gametuning.data.coolRange
import io.github.miner7222.gametuning.data.deepCopy
import io.github.miner7222.gametuning.data.defaultConfig
import io.github.miner7222.gametuning.data.detailChanges
import io.github.miner7222.gametuning.data.importChanges
import io.github.miner7222.gametuning.data.ensureArrays
import io.github.miner7222.gametuning.data.builtinName
import io.github.miner7222.gametuning.data.heatName
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.newPresetId
import io.github.miner7222.gametuning.data.resourceName
import io.github.miner7222.gametuning.data.retarget
import io.github.miner7222.gametuning.data.shortSummary
import io.github.miner7222.gametuning.data.toBase64
import io.github.miner7222.gametuning.data.capFpsLimit
import io.github.miner7222.gametuning.data.restoreIndependently
import io.github.miner7222.gametuning.data.displayAffectsProbe
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vendor.lenovo.hardware.gametuning.GameTuningConfig
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.PolicyProfile
import vendor.lenovo.hardware.gametuning.ProfileSource
import vendor.lenovo.hardware.gametuning.IGameTuning
import vendor.lenovo.hardware.gametuning.Recommendation
import vendor.lenovo.hardware.gametuning.RecordingInfo
import vendor.lenovo.hardware.gametuning.RecordingSample
import vendor.lenovo.hardware.gametuning.TuningChange
import vendor.lenovo.hardware.gametuning.TuningProposal

/** One card of the game list. */
data class GameCard(
    val pkg: String, val label: String, val mode: Int, val presetName: String?,
    val adjusted: Boolean, val recording: Boolean,
    /** Whether the built-in preset is the game's own profile rather than the generic one. */
    val dedicated: Boolean,
)

/** Everything the profile screen and the preset screens show for one game. */
class ProfileState(
    val pkg: String,
    val label: String,
    val mode: Int,
    val libs: Map<Int, ModeLibrary>,
    val builtins: Map<Int, PolicyProfile>,
    val effective: PolicyProfile?,
    val limits: PolicyLimits,
    val recordings: List<RecordingInfo>,
    val armed: Boolean,
    val notice: String?,
    /** Whether the built-in preset is the game's own profile rather than the generic one. */
    val dedicated: Boolean,
) {
    val tuningEnabled: Boolean get() = mode != Modes.NONE
}

/** The preset in use for one profile of one game, with the resolved values the editor needs. */
class TuningState(
    val pkg: String,
    val mode: Int,
    val label: String,
    val library: ModeLibrary,
    val config: GameTuningConfig,
    val display: DisplayValues,
    val limits: PolicyLimits,
    val builtin: PolicyProfile,
    val base: PolicyProfile,
    val effective: PolicyProfile,
    val allow: GameAllowances,
    val changes: List<Change>,
    val hasStable: Boolean,
    val globalsLowBattery: Int,
    val scenesSupported: Boolean,
    val graphicsAvailable: Boolean,
) {
    val preset: Preset? get() = library.inUse
    val dedicated: Boolean get() = builtin.profileKey != GENERIC_PROFILE
    val builtinDisplay: DisplayValues get() = DisplayValues.builtin(mode)
}

/** A message for the snackbar, optionally undoable. */
class UiMessage(val text: String, val undo: (suspend () -> Unit)? = null)

/** An item of the import list. */
sealed class ImportItem {
    abstract val key: String
    abstract val title: String
    abstract val subtitle: String

    class Mine(
        val pkg: String, val mode: Int, val preset: Preset, val gameLabel: String,
        override val title: String, override val subtitle: String,
    ) : ImportItem() {
        override val key get() = "mine:$pkg:$mode:${preset.id}"
    }

    class BuiltIn(
        val source: ProfileSource, override val title: String, override val subtitle: String,
    ) : ImportItem() {
        override val key get() = "built:${source.profileKey}"
    }
}

class ImportSources(val mine: List<ImportItem.Mine>, val builtIn: List<ImportItem.BuiltIn>)

/** Result of a daemon analysis call: a value, or the daemon's error code. */
sealed class Outcome<out T> {
    class Ok<T>(val value: T) : Outcome<T>()
    class Failed(val code: Int) : Outcome<Nothing>()
}

/** One suggested setting: where it comes from, what it changes and why. */
class Suggestion(
    val name: String, val yours: Boolean, val changes: List<String>, val reason: String,
    val item: ImportItem, val baseMode: Int, val includeExpert: Boolean,
    val context: AdviceContext, val fpsLimit: Int?,
)

/** One line of the single change an automatic adjustment proposes. */
class ChangeLine(val label: String, val before: String, val after: String, val delta: String, val raise: Boolean)

data class AdviceContext(val revision: Long, val settings: String, val display: DisplayValues)
class AutoPlan(val proposal: TuningProposal, val lines: List<ChangeLine>, val observation: String,
               val context: AdviceContext, val fpsLimit: Int?)

class CopyTarget(val pkg: String, val mode: Int)

class CopyRequest(
    val srcPkg: String, val srcMode: Int, val presetId: String,
    val targets: List<CopyTarget>, val replace: Set<String>,
    val startUsing: Boolean, val expertToOthers: Boolean,
)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val repo = GameTuningRepository(app)
    val games = GameLibrary(app)
    val store = PresetStore.getInstance(app)
    val displayControl = DisplayControl(app)
    val prefs = AppPrefs(app)
    val tf = TemperatureFormat(app)
    private val userId = app.userId
    private val mutex = Mutex()

    /** null while the first check runs. */
    var available by mutableStateOf<Boolean?>(null)
        private set
    var cards by mutableStateOf<List<GameCard>>(emptyList())
        private set
    var tipDismissed by mutableStateOf(prefs.tipDismissed)
        private set
    var profile by mutableStateOf<ProfileState?>(null)
        private set
    var tuning by mutableStateOf<TuningState?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var lowBatteryPct by mutableStateOf(20)
        private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    private var profilePkg: String? = null
    private var tuningKey: Pair<String, Int>? = null

    private val gameListObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (available == true) reload()
        }
    }

    init {
        app.contentResolver.registerContentObserver(
            Settings.System.getUriFor(GameLibrary.LIST_KEY), false, gameListObserver, userId,
        )
    }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(gameListObserver)
        super.onCleared()
    }

    private fun say(text: String, undo: (suspend () -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, undo))
    }

    private fun s(id: Int, vararg args: Any) = app.getString(id, *args)

    private class NoneModeException : RuntimeException()

    private suspend fun requireTuningEnabled(pkg: String) {
        if (displayControl.clearRefreshIfNone(pkg)) throw NoneModeException()
    }

    /** Runs [block] off the main thread, one at a time, and turns daemon errors into a message. */
    private fun launchLocked(showBusy: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (showBusy) busy = true
                try {
                    handleFailure(block)
                } finally {
                    if (showBusy) busy = false
                }
            }
        }
    }

    private suspend fun handleFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoneModeException) {
            runCatching { reloadLocked() }
            say(s(R.string.tuning_off_none))
        } catch (e: TuningException) {
            say(s(R.string.save_failed))
            runCatching { reloadLocked() }
        } catch (e: IOException) {
            android.util.Log.w("GameTuning", "Preset persistence failed", e)
            say(s(R.string.save_failed))
            runCatching { reloadLocked() }
        } catch (e: RuntimeException) {
            android.util.Log.w("GameTuning", "Operation failed", e)
            say(s(R.string.save_failed))
            runCatching { reloadLocked() }
        }
    }

    // region loading

    fun start() {
        launchLocked {
            available = repo.isAvailable()
            if (available == true) {
                try { repo.syncBuiltins() } catch (e: Exception) {
                    android.util.Log.w("GameTuning", "Could not synchronize built-in history", e)
                }
                lowBatteryPct = repo.globals().lowBatteryPercent
                reloadLocked()
            }
        }
    }

    fun reload() = launchLocked { reloadLocked() }

    fun showProfile(pkg: String?) {
        profilePkg = pkg
        if (pkg == null) profile = null else launchLocked { loadProfileLocked(pkg) }
    }

    fun showTuning(pkg: String?, mode: Int = 0) {
        tuningKey = if (pkg == null) null else pkg to mode
        if (pkg == null) tuning = null else launchLocked { loadTuningLocked(pkg, mode) }
    }

    private suspend fun reloadLocked() {
        loadCardsLocked()
        profilePkg?.let { loadProfileLocked(it) }
        tuningKey?.let { (p, m) -> loadTuningLocked(p, m) }
    }

    private suspend fun loadCardsLocked() {
        val list = games.games()
        val tunings = repo.tunings()
        val armed = repo.armedPackages()
        val dedicated = repo.dedicatedPackages()
        cards = list.map { g ->
            val enabled = g.mode != Modes.NONE
            val lib = if (enabled) buildLibrary(g.packageName, g.mode, tunings) else null
            GameCard(
                g.packageName, g.label, g.mode, lib?.inUse?.name,
                enabled && tunings.any { it.packageName == g.packageName && it.enabled },
                enabled && g.packageName in armed,
                g.packageName in dedicated,
            )
        }
    }

    /** Joins the preset library with the daemon's stored tuning of one profile. */
    private suspend fun buildLibrary(pkg: String, mode: Int, tunings: List<GameTuningConfig>): ModeLibrary {
        var list = store.list(pkg, mode)
        val t = tunings.firstOrNull { it.packageName == pkg && it.mode == mode && it.enabled }
            ?: return ModeLibrary(list, null)
        // Reading a None game's state must not migrate its daemon configuration or local presets.
        if (games.isNoneMode(pkg, userId)) return ModeLibrary(list, t.variantId?.takeIf { it.isNotEmpty() })
        val id = t.variantId.orEmpty().ifEmpty { newPresetId() }
        val old = list.firstOrNull { it.id == id }
        val name = t.variantName.orEmpty().ifEmpty { old?.name ?: uniqueName(list, s(R.string.current_setting)) }
        val cfg = t.deepCopy().also {
            it.ensureArrays()
            it.variantId = id
            it.variantName = name
        }
        if (t.variantId.isNullOrEmpty()) repo.setTuning(cfg)
        // The daemon may have committed a change whose local file write failed, even for this ID.
        val current = Preset(id, name, cfg, old?.display ?: DisplayValues.builtin(mode), old?.archiveOrigin)
        if (old == null || old.name != name || old.config.toBase64() != cfg.toBase64()) {
            try {
                store.upsert(pkg, mode, current)
            } catch (e: IOException) {
                say(s(R.string.save_failed))
            }
        }
        // Still use the daemon's configuration when the filesystem remains unwritable.
        list = if (old == null) list + current else list.map { if (it.id == id) current else it }
        return ModeLibrary(list, id)
    }

    private suspend fun loadProfileLocked(pkg: String) {
        val label = games.label(pkg)
        val mode = games.currentMode(pkg) ?: Modes.BALANCED
        val tunings = repo.tunings()
        val libs = Modes.ALL.associateWith { buildLibrary(pkg, it, tunings) }
        val enabled = mode != Modes.NONE
        val builtins = if (enabled) Modes.ALL.associateWith { repo.profile(pkg, it, false) } else emptyMap()
        val limits = repo.limits()
        val recs = repo.recordings(pkg)
        scenesSupported(pkg, recs)
        val armed = enabled && pkg in repo.armedPackages()
        val effective = if (enabled) repo.profile(pkg, mode, true) else null
        val notice = if (enabled) recs.firstOrNull { it.mode == mode }?.let { computeNotice(it, limits) }
            else s(R.string.tuning_off_none)
        if (profilePkg == pkg) profile = ProfileState(pkg, label, mode, libs, builtins, effective, limits, recs, armed, notice, pkg in repo.dedicatedPackages())
    }

    private fun scenesSupported(pkg: String, recordings: List<RecordingInfo>): Boolean =
        prefs.scenesSupported(pkg, recordings.any { record ->
            record.userId == userId && record.packageName == pkg &&
                record.summary?.scenesSeen?.any { it in 0..100000 } == true
        })

    private suspend fun loadTuningLocked(pkg: String, mode: Int) {
        if (mode !in Modes.ALL || games.isNoneMode(pkg, userId)) {
            tuning = null
            return
        }
        val tunings = repo.tunings()
        val lib = buildLibrary(pkg, mode, tunings)
        val limits = repo.limits()
        val builtin = repo.profile(pkg, mode, false)
        val preset = lib.inUse
        val cfg = preset?.config?.deepCopy() ?: defaultConfig(userId, pkg, mode)
        cfg.ensureArrays()
        val base: PolicyProfile
        val effective: PolicyProfile
        if (preset == null) {
            base = builtin
            effective = builtin
        } else {
            val baseCfg = defaultConfig(userId, pkg, mode).also {
                it.baseProfile = cfg.baseProfile
                it.baseMode = cfg.baseMode
            }
            base = repo.preview(baseCfg)
            effective = repo.preview(cfg)
        }
        val display = preset?.display ?: DisplayValues.builtin(mode)
        val allow = games.allowances(pkg)
        val changes = if (preset == null) emptyList() else
            app.detailChanges(limits, builtin, effective, display, DisplayValues.builtin(mode), tf)
        val st = TuningState(
            pkg, mode, games.label(pkg), lib, cfg, display, limits, builtin, base, effective, allow,
            changes, preset != null && store.stableConfig(pkg, mode, preset.id) != null, lowBatteryPct,
            scenesSupported(pkg, repo.recordings(pkg)),
            tuning?.takeIf { it.pkg == pkg && it.mode == mode }?.graphicsAvailable ?: (repo.graphicsOptions(pkg) != null),
        )
        if (tuningKey == pkg to mode) tuning = st
    }

    // endregion

    // region profile and presets

    fun dismissTip() {
        prefs.tipDismissed = true
        tipDismissed = true
    }

    fun setMode(pkg: String, mode: Int) = launchLocked {
        requireTuningEnabled(pkg)
        games.setMode(pkg, mode)
        if (mode in Modes.ALL) {
            val preset = buildLibrary(pkg, mode, repo.tunings()).inUse
            applyGraphics(pkg, mode, preset?.display?.graphics ?: GraphicsValues())
        }
        displayControl.applyRefresh(pkg, mode)
        reloadLocked()
    }

    fun setLowBattery(percent: Int) = launchLocked {
        repo.setGlobals(repo.globals().also { it.lowBatteryPercent = percent })
        lowBatteryPct = percent
        tuning?.let { loadTuningLocked(it.pkg, it.mode) }
    }

    fun setArmed(pkg: String, armed: Boolean) = launchLocked {
        requireTuningEnabled(pkg)
        repo.setArmed(pkg, armed)
        reloadLocked()
    }

    private class Snap(val pkg: String, val mode: Int, val presets: List<Preset>, val inUseId: String?)

    private suspend fun snapshot(pkg: String, mode: Int): Snap {
        val lib = buildLibrary(pkg, mode, repo.tunings())
        return Snap(pkg, mode, lib.presets, lib.inUseId)
    }

    private suspend fun restore(snaps: List<Snap>) {
        mutex.withLock {
            handleFailure {
                var noneSkipped = false
                for (sn in snaps) {
                    if (displayControl.clearRefreshIfNone(sn.pkg)) {
                        noneSkipped = true
                        continue
                    }
                    store.replaceAll(sn.pkg, sn.mode, sn.presets)
                    activate(sn.pkg, sn.mode, sn.presets.firstOrNull { it.id == sn.inUseId })
                }
                reloadLocked()
                if (noneSkipped) say(s(R.string.tuning_off_none))
            }
        }
    }

    private fun undoFor(vararg snaps: Snap): suspend () -> Unit = { restore(snaps.toList()) }

    private suspend fun applyGraphics(pkg: String, mode: Int, values: GraphicsValues) {
        if (games.currentMode(pkg) == mode && repo.graphicsOptions(pkg) != null)
            repo.setGraphicsOptions(pkg, values.toOptions())
    }

    private suspend fun applyDisplay(pkg: String, mode: Int, d: DisplayValues, allow: GameAllowances) {
        requireTuningEnabled(pkg)
        if (displayAffectsProbe(displayControl.readFpsLimit(pkg, mode), displayControl.readResolution(pkg, mode),
                displayControl.readRefreshRate(pkg), d, displayControl.refreshTarget(d, allow.fpsOverride)))
            repo.invalidateDisplayProbe(pkg, mode)
        val ok = displayControl.applyGameManager(pkg, mode, d, allow)
        displayControl.applyRefresh(pkg, mode)
        if (!ok) say(s(R.string.display_apply_failed))
        applyGraphics(pkg, mode, d.graphics)
    }

    /** Makes [preset] (or the built-in setting when null) the one in use for a profile. */
    private suspend fun activate(pkg: String, mode: Int, preset: Preset?) {
        requireTuningEnabled(pkg)
        val allow = games.allowances(pkg)
        if (preset == null) {
            repo.clearTuning(pkg, mode)
            applyDisplay(pkg, mode, DisplayValues.builtin(mode), allow)
            return
        }
        val cfg = preset.config.deepCopy().also {
            it.ensureArrays()
            it.userId = userId
            it.packageName = pkg
            it.mode = mode
            it.enabled = true
            it.variantId = preset.id
            it.variantName = preset.name
        }
        val normalized = repo.setTuning(cfg)
        normalized.ensureArrays()
        store.upsert(pkg, mode, Preset(preset.id, preset.name, normalized, preset.display, preset.archiveOrigin))
        applyDisplay(pkg, mode, preset.display, allow)
    }

    fun usePreset(pkg: String, mode: Int, id: String?, andThen: (() -> Unit)? = null) = launchLocked {
        requireTuningEnabled(pkg)
        val before = snapshot(pkg, mode)
        val preset = before.presets.firstOrNull { it.id == id }
        activate(pkg, mode, preset)
        reloadLocked()
        say(
            s(R.string.now_using, preset?.name ?: app.builtinName(pkg in repo.dedicatedPackages()), s(modeLabelRes(mode))),
            undoFor(before),
        )
        andThen?.invoke()
    }

    fun uniqueName(existing: List<Preset>, wanted: String): String {
        val taken = existing.map { it.name }.toSet() + s(R.string.builtin_setting)
        if (wanted !in taken) return wanted
        var n = 2
        while ("$wanted $n" in taken) n++
        return "$wanted $n"
    }

    fun canAddPreset(pkg: String, mode: Int) = store.list(pkg, mode).customCount() + 1 < MAX_PRESETS

    fun createPreset(pkg: String, mode: Int, name: String, fromBuiltin: Boolean) = launchLocked {
        requireTuningEnabled(pkg)
        val lib = buildLibrary(pkg, mode, repo.tunings())
        if (lib.presets.customCount() + 1 >= MAX_PRESETS) {
            say(s(R.string.preset_limit))
            return@launchLocked
        }
        val before = Snap(pkg, mode, lib.presets, lib.inUseId)
        val inUse = lib.inUse
        val cfg = if (fromBuiltin || inUse == null) defaultConfig(userId, pkg, mode) else inUse.config.deepCopy()
        val disp = if (fromBuiltin || inUse == null) DisplayValues.builtin(mode) else inUse.display
        val finalName = uniqueName(lib.presets, name.trim().take(64).ifEmpty { s(R.string.new_preset) })
        val p = Preset(newPresetId(), finalName, cfg, disp)
        store.upsert(pkg, mode, p)
        activate(pkg, mode, p)
        reloadLocked()
        say(s(R.string.preset_created, finalName), undoFor(before))
    }

    fun duplicatePreset(pkg: String, mode: Int, id: String) = launchLocked {
        requireTuningEnabled(pkg)
        val lib = buildLibrary(pkg, mode, repo.tunings())
        val src = lib.presets.firstOrNull { it.id == id } ?: return@launchLocked
        if (lib.presets.customCount() + 1 >= MAX_PRESETS) {
            say(s(R.string.preset_limit))
            return@launchLocked
        }
        val before = Snap(pkg, mode, lib.presets, lib.inUseId)
        val name = uniqueName(lib.presets, s(R.string.name_copy, src.name).take(64))
        val p = Preset(newPresetId(), name, src.config.deepCopy(), src.display)
        store.upsert(pkg, mode, p)
        activate(pkg, mode, p)
        reloadLocked()
        say(s(R.string.preset_created, name), undoFor(before))
    }

    fun renamePreset(pkg: String, mode: Int, id: String, name: String) = launchLocked {
        requireTuningEnabled(pkg)
        val lib = buildLibrary(pkg, mode, repo.tunings())
        val src = lib.presets.firstOrNull { it.id == id } ?: return@launchLocked
        val clean = name.trim().take(64)
        if (clean.isEmpty() || clean == src.name) return@launchLocked
        val before = Snap(pkg, mode, lib.presets, lib.inUseId)
        val finalName = uniqueName(lib.presets.filter { it.id != id }, clean)
        val renamed = Preset(id, finalName, src.config.deepCopy().also { it.variantName = finalName }, src.display, src.archiveOrigin)
        store.upsert(pkg, mode, renamed)
        if (lib.inUseId == id) activate(pkg, mode, renamed)
        reloadLocked()
        say(s(R.string.renamed_to, finalName), undoFor(before))
    }

    fun deletePreset(pkg: String, mode: Int, id: String) = launchLocked {
        requireTuningEnabled(pkg)
        val lib = buildLibrary(pkg, mode, repo.tunings())
        val before = Snap(pkg, mode, lib.presets, lib.inUseId)
        if (lib.inUseId == id) activate(pkg, mode, null)
        store.delete(pkg, mode, id)
        reloadLocked()
        say(s(R.string.preset_deleted), undoFor(before))
    }

    // endregion

    // region editing

    /**
     * Applies one edit to the preset in use and saves it. Editing while the built-in setting is in
     * use first creates a preset of its own, since the built-in setting never changes.
     */
    fun edit(
        description: String,
        showBusy: Boolean = true,
        change: (cfg: GameTuningConfig, display: DisplayValues, st: TuningState) -> DisplayValues,
    ) {
        val st = tuning ?: return
        launchLocked(showBusy) {
            requireTuningEnabled(st.pkg)
            val before = snapshot(st.pkg, st.mode)
            val lib = buildLibrary(st.pkg, st.mode, repo.tunings())
            val cfg = (lib.inUse?.config?.deepCopy() ?: defaultConfig(userId, st.pkg, st.mode)).also { it.ensureArrays() }
            val oldDisplay = lib.inUse?.display ?: DisplayValues.builtin(st.mode)
            val newDisplay = change(cfg, oldDisplay, st)
            val existing = lib.inUse
            if (existing == null && lib.presets.customCount() + 1 >= MAX_PRESETS) {
                say(s(R.string.preset_limit))
                return@launchLocked
            }
            val name = existing?.name ?: uniqueName(lib.presets, s(R.string.my_preset))
            val id = existing?.id ?: newPresetId()
            cfg.userId = userId
            cfg.packageName = st.pkg
            cfg.mode = st.mode
            cfg.enabled = true
            cfg.variantId = id
            cfg.variantName = name
            val normalized = repo.setTuning(cfg)
            normalized.ensureArrays()
            store.upsert(st.pkg, st.mode, Preset(id, name, normalized, newDisplay, existing?.archiveOrigin))
            if (newDisplay.copy(graphics = oldDisplay.graphics) != oldDisplay || existing == null) {
                applyDisplay(st.pkg, st.mode, newDisplay, st.allow)
            } else if (newDisplay.graphics != oldDisplay.graphics) {
                applyGraphics(st.pkg, st.mode, newDisplay.graphics)
            }
            if (showBusy) reloadLocked() else loadTuningLocked(st.pkg, st.mode)
            say(s(R.string.saved_to, name, description), undoFor(before))
        }
    }

    fun goBackToStable() {
        val st = tuning ?: return
        val preset = st.preset ?: return
        launchLocked {
            requireTuningEnabled(st.pkg)
            val stable = store.stableConfig(st.pkg, st.mode, preset.id) ?: return@launchLocked
            val before = snapshot(st.pkg, st.mode)
            val restored = Preset(preset.id, preset.name, stable, preset.display, preset.archiveOrigin)
            store.upsert(st.pkg, st.mode, restored)
            activate(st.pkg, st.mode, restored)
            reloadLocked()
            say(s(R.string.went_back_stable), undoFor(before))
        }
    }

    fun resetPreset() {
        val st = tuning ?: return
        val preset = st.preset ?: return
        launchLocked {
            requireTuningEnabled(st.pkg)
            val before = snapshot(st.pkg, st.mode)
            val config = defaultConfig(userId, st.pkg, st.mode).also {
                preset.archiveOrigin?.let { origin -> it.baseProfile = origin }
            }
            val fresh = Preset(preset.id, preset.name, config, DisplayValues.builtin(st.mode), preset.archiveOrigin)
            store.upsert(st.pkg, st.mode, fresh)
            activate(st.pkg, st.mode, fresh)
            reloadLocked()
            say(s(R.string.preset_reset), undoFor(before))
        }
    }

    fun editGraphics(label: Int, change: (GraphicsValues) -> GraphicsValues) {
        edit(s(label), showBusy = false) { _, display, _ -> display.copy(graphics = change(display.graphics)) }
    }

    fun resetGame(pkg: String) = launchLocked {
        requireTuningEnabled(pkg)
        val befores = Modes.ALL.map { snapshot(pkg, it) }
        for (m in Modes.ALL) activate(pkg, m, null)
        store.clearGame(pkg)
        reloadLocked()
        say(s(R.string.game_reset), undoFor(*befores.toTypedArray()))
    }

    // endregion

    // region copy and import

    private fun stripExpert(c: GameTuningConfig) {
        c.sceneMode = KEEP
        c.scenes = emptyArray()
        c.customThreads = emptyArray()
        if (c.threadRules == 3) c.threadRules = 1
    }

    private fun keepBothName(existing: List<Preset>, name: String): String {
        var n = 2
        while (existing.any { it.name == "$name $n" }) n++
        return "$name $n"
    }

    /** Copies a preset to several profiles and games. Returns how many copies were added. */
    fun copyPreset(req: CopyRequest) = launchLocked {
        requireTuningEnabled(req.srcPkg)
        val srcLib = buildLibrary(req.srcPkg, req.srcMode, repo.tunings())
        val src = srcLib.presets.firstOrNull { it.id == req.presetId } ?: return@launchLocked
        val resolvedKey = repo.preview(src.config.deepCopy().also {
            it.userId = userId; it.packageName = req.srcPkg; it.mode = req.srcMode; it.enabled = true
        }).profileKey
        val befores = ArrayList<Snap>()
        var added = 0
        var skipped = 0
        var noneSkipped = false
        for (t in req.targets) {
            if (displayControl.clearRefreshIfNone(t.pkg)) {
                skipped++
                noneSkipped = true
                continue
            }
            val sameGame = t.pkg == req.srcPkg
            val before = snapshot(t.pkg, t.mode)
            befores.add(before)
            val list = before.presets
            val cfg = src.config.retarget(userId, t.pkg, t.mode, resolvedKey)
            if (!sameGame && !req.expertToOthers) stripExpert(cfg)
            val existing = list.firstOrNull { it.name == src.name }
            val replacing = existing != null && "${t.pkg}|${t.mode}" in req.replace
            val target: Preset
            if (replacing) {
                target = Preset(existing!!.id, existing.name, cfg, src.display, existing.archiveOrigin)
            } else {
                if (list.customCount() + 1 >= MAX_PRESETS) {
                    skipped++
                    continue
                }
                val name = if (existing != null) keepBothName(list, src.name) else src.name
                target = Preset(newPresetId(), name, cfg, src.display)
            }
            store.upsert(t.pkg, t.mode, target)
            added++
            val inUseThere = before.inUseId == target.id
            if (req.startUsing || inUseThere) activate(t.pkg, t.mode, target)
        }
        reloadLocked()
        if (noneSkipped) say(s(R.string.tuning_off_none), undoFor(*befores.toTypedArray()))
        else say(if (skipped > 0) s(R.string.copy_partial, added, skipped) else s(R.string.copy_done, added), undoFor(*befores.toTypedArray()))
    }

    private fun builtInItem(src: ProfileSource): ImportItem.BuiltIn {
        val name = src.displayName.orEmpty().ifBlank {
            if (src.profileKey == "default") s(R.string.common_setting)
            else src.packages?.firstOrNull()?.let { games.label(it) } ?: src.profileKey
        }
        val count = src.baseModes?.size ?: 0
        return ImportItem.BuiltIn(src, name, s(R.string.builtin_points, count))
    }

    suspend fun importSources(pkg: String): ImportSources {
        val mine = ArrayList<ImportItem.Mine>()
        for ((p, m, preset) in store.others(pkg)) {
            val g = games.label(p)
            mine.add(ImportItem.Mine(p, m, preset, g, preset.name, s(R.string.import_where, g, s(modeLabelRes(m)))))
        }
        val builtIn = distinctBuiltIns(repo.profileSources().distinctBy { it.profileKey }.map { builtInItem(it) })
            .sortedBy { if (it.source.profileKey == "default") "" else it.title.lowercase() }
        return ImportSources(mine.sortedBy { it.title.lowercase() }, builtIn)
    }

    /**
     * Entries that share a title: one of them when they are the same entry, otherwise each gets its
     * first package after the starting-point count so they can be told apart.
     */
    private fun distinctBuiltIns(items: List<ImportItem.BuiltIn>): List<ImportItem.BuiltIn> {
        fun identity(i: ImportItem.BuiltIn) =
            i.source.packages.orEmpty().toSortedSet().toString() + i.source.baseModes?.sorted().orEmpty()
        return items.groupBy { it.title }.values.flatMap { group ->
            val unique = group.distinctBy { identity(it) }
            if (unique.size < 2) unique
            else unique.map {
                ImportItem.BuiltIn(it.source, it.title, s(R.string.import_where, it.subtitle, it.source.profileKey))
            }
        }
    }

    private suspend fun prepareImport(
        item: ImportItem, baseMode: Int, targetPkg: String, targetMode: Int, includeExpert: Boolean,
    ): Pair<GameTuningConfig, DisplayValues> = when (item) {
        is ImportItem.Mine -> {
            val key = repo.preview(item.preset.config.deepCopy().also {
                it.userId = userId; it.packageName = item.pkg; it.mode = item.mode; it.enabled = true
            }).profileKey
            val cfg = item.preset.config.retarget(userId, targetPkg, targetMode, key)
            if (!includeExpert) stripExpert(cfg)
            cfg to item.preset.display
        }
        is ImportItem.BuiltIn -> {
            val cfg = defaultConfig(userId, targetPkg, targetMode).also {
                it.baseProfile = item.source.profileKey
                it.baseMode = baseMode
                it.threadRules = if (includeExpert) 2 else 1
            }
            cfg to DisplayValues.builtin(targetMode)
        }
    }

    /** Compare the exact import configuration with the target mode's current settings. */
    suspend fun importPreview(
        item: ImportItem, baseMode: Int, targetPkg: String, targetMode: Int, includeExpert: Boolean,
    ): List<Change> = resolveImportPreview(item, baseMode, targetPkg, targetMode, includeExpert).changes

    internal suspend fun importPreviewDetails(
        item: ImportItem, baseMode: Int, targetPkg: String, targetMode: Int, includeExpert: Boolean,
    ): ImportPreview {
        val preview = resolveImportPreview(item, baseMode, targetPkg, targetMode, includeExpert)
        return preview.copy(changes = preview.changes.filterNot { it.label == s(R.string.steps_title) })
    }

    private suspend fun resolveImportPreview(
        item: ImportItem, baseMode: Int, targetPkg: String, targetMode: Int, includeExpert: Boolean,
    ): ImportPreview {
        val (cfg, display) = prepareImport(item, baseMode, targetPkg, targetMode, includeExpert)
        val current = repo.tunings().firstOrNull {
            it.packageName == targetPkg && it.mode == targetMode && it.enabled
        }
        val before = if (current != null) repo.preview(current) else repo.profile(targetPkg, targetMode, false)
        val beforeDisplay = current?.variantId?.let { store.get(targetPkg, targetMode, it)?.display }
            ?: DisplayValues.builtin(targetMode)
        val limits = repo.limits()
        val after = repo.preview(cfg)
        return ImportPreview(
            app.importChanges(limits, before, after, display, beforeDisplay, tf),
            app.importStepChanges(limits, before, after, tf),
        )
    }

    fun doImport(
        item: ImportItem, baseMode: Int, targetPkg: String, targetMode: Int,
        replaceInUse: Boolean, includeExpert: Boolean, nameOverride: String? = null,
        replaceId: String? = null, adviceInfo: RecordingInfo? = null, adviceContext: AdviceContext? = null, adviceFps: Int? = null,
    ) = launchLocked {
        requireTuningEnabled(targetPkg)
        if (adviceInfo != null && adviceContext != null) validateAdvice(adviceInfo, adviceContext)
        val lib = buildLibrary(targetPkg, targetMode, repo.tunings())
        val before = Snap(targetPkg, targetMode, lib.presets, lib.inUseId)
        val replaced = if (replaceId != null) lib.presets.firstOrNull { it.id == replaceId } else if (replaceInUse) lib.inUse else null
        val replacing = replaced != null
        if (!replacing && lib.presets.customCount() + 1 >= MAX_PRESETS) {
            say(s(R.string.preset_limit))
            return@launchLocked
        }
        val (cfg, disp) = prepareImport(item, baseMode, targetPkg, targetMode, includeExpert)
        val wanted = when (item) {
            is ImportItem.Mine -> item.preset.name
            is ImportItem.BuiltIn -> item.title
        }
        val targetDisplay = if (adviceContext != null) adviceContext.display.copy(fpsLimit = adviceFps ?: adviceContext.display.fpsLimit) else disp
        val p = if (replaced != null) {
            Preset(replaced.id, replaced.name, cfg, targetDisplay, replaced.archiveOrigin)
        } else {
            val chosen = nameOverride?.trim()?.takeIf { it.isNotEmpty() } ?: wanted
            Preset(newPresetId(), uniqueName(lib.presets, chosen.take(64)), cfg, targetDisplay)
        }
        if (adviceContext != null) {
            val displayBefore = applyAdvice(before, p, adviceFps != null)
            reloadLocked()
            say(s(R.string.imported_as, p.name), adviceUndo(before, displayBefore))
        } else {
            store.upsert(targetPkg, targetMode, p)
            activate(targetPkg, targetMode, p)
            reloadLocked()
            say(s(R.string.imported_as, p.name), undoFor(before))
        }
    }

    // endregion

    /** What a preset changes from the game's own setting, resolved by the daemon. */
    suspend fun presetChanges(pkg: String, mode: Int, preset: Preset?): List<Change> {
        if (preset == null) return emptyList()
        return try {
            val limits = repo.limits()
            val builtin = repo.profile(pkg, mode, false)
            val eff = repo.preview(preset.config.deepCopy().also {
                it.ensureArrays(); it.userId = userId; it.packageName = pkg; it.mode = mode; it.enabled = true
            })
            app.detailChanges(limits, builtin, eff, preset.display, DisplayValues.builtin(mode), tf)
        } catch (_: TuningException) {
            emptyList()
        }
    }

    /** The preset a record was made with, if it still exists; null for the built-in setting. */
    fun recordPreset(ps: ProfileState, info: RecordingInfo): Preset? =
        recordPresetIn(ps.libs[info.mode]?.presets.orEmpty(), info)

    private fun recordPresetIn(presets: List<Preset>, info: RecordingInfo): Preset? {
        val id = info.profile?.variantId.orEmpty()
        return if (id.isEmpty()) null else presets.firstOrNull { it.id == id }
    }

    /** Records of one profile made with one preset (null id is the built-in setting). */
    fun recordsWith(ps: ProfileState, mode: Int, presetId: String?): List<RecordingInfo> =
        ps.recordings.filter { it.mode == mode && (it.profile?.variantId.orEmpty()) == presetId.orEmpty() }

    // region records, suggestions and automatic adjustment

    private val sampleCache = object : LinkedHashMap<String, List<RecordingSample>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RecordingSample>>?) = size > 3
    }

    /** Every sample of a record, read in pages of 600 and kept for the next screen. */
    suspend fun samplesFor(id: String, revision: Long? = null): List<RecordingSample> {
        val expected = revision ?: repo.recording(id).rangeRevision
        val key = "$id:$expected"
        synchronized(sampleCache) { sampleCache[key] }?.let { return it }
        val list = repo.samples(id, expected)
        if (repo.recording(id).rangeRevision != expected) throw TuningException(IGameTuning.ERROR_INVALID, "Stale range revision")
        synchronized(sampleCache) { sampleCache[key] = list }
        return list
    }

    suspend fun rawSamplesFor(info: RecordingInfo): List<RecordingSample> = repo.samples(info.id, info.rangeRevision, raw = true)

    fun keepRange(info: RecordingInfo, startMs: Int, endMs: Int) = launchLocked {
        val saved = repo.setRange(info, startMs, endMs)
        synchronized(sampleCache) { sampleCache.clear() }
        reloadLocked()
        say(s(R.string.range_saved)) {
            mutex.withLock {
                handleFailure {
                    val current = repo.recording(info.id)
                    if (current.rangeRevision == saved.rangeRevision) {
                        repo.setRange(current, info.keepStartMs, info.keepEndMs)
                        synchronized(sampleCache) { sampleCache.clear() }
                        reloadLocked()
                    }
                }
            }
        }
    }

    /** The record being written to a CSV file now, if any. */
    var exportingId by mutableStateOf<String?>(null)
        private set

    /** The name the file picker starts with for [info]. */
    fun exportFileName(info: RecordingInfo): String = RecordExport.defaultName(games.label(info.packageName), info)

    /**
     * Writes [info] to the file the user picked, in the background. It does not take the operation
     * lock, so the rest of the app stays usable; the result comes back as a message.
     */
    fun exportRecord(info: RecordingInfo, uri: Uri) {
        if (exportingId != null) return
        exportingId = info.id
        viewModelScope.launch {
            var ok = false
            try {
                RecordExport.write(app, repo, info, games.label(info.packageName), uri)
                ok = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("GameTuning", "Export failed", e)
            } finally {
                exportingId = null
            }
            say(s(if (ok) R.string.export_done else R.string.export_failed))
        }
    }

    fun deleteRecord(id: String) = launchLocked {
        repo.deleteRecording(id)
        synchronized(sampleCache) { sampleCache.keys.removeAll { it.startsWith("$id:") } }
        reloadLocked()
        say(s(R.string.record_deleted))
    }

    private fun baseModeName(baseMode: Int) = if (baseMode == 4) s(R.string.mode_maximum) else app.modeName(baseMode)

    private fun unitless(resource: Int, value: Int) =
        if (resource == Resource.GPU) Freq.mhzNumber(value) else Freq.ghzNumber(value)

    /** Up to three settings that fit a record, with their concrete changes and a reason each. */
    suspend fun suggestionsFor(info: RecordingInfo, goal: Int): Outcome<List<Suggestion>> {
        return try {
            val context = adviceContext(info)
            val recs = repo.recommend(info.id, goal)
            if (recs.any { it.rangeRevision != info.rangeRevision }) throw TuningException(IGameTuning.ERROR_INVALID, "Stale range revision")
            val limits = repo.limits()
            val sources = repo.profileSources()
            val tunings = repo.tunings()
            validateAdvice(info, context)
            Outcome.Ok(recs.mapNotNull { suggestionFrom(info, it, limits, sources, tunings, context) })
        } catch (e: TuningException) {
            Outcome.Failed(e.code)
        }
    }

    private fun suggestionFrom(
        info: RecordingInfo, rec: Recommendation, limits: PolicyLimits, sources: List<ProfileSource>,
        tunings: List<GameTuningConfig>, context: AdviceContext,
    ): Suggestion? {
        val tunedPkg = rec.tunedPackage.orEmpty()
        val item: ImportItem
        val name: String
        if (tunedPkg.isNotEmpty()) {
            val cfg = tunings.firstOrNull { it.packageName == tunedPkg && it.mode == rec.tunedMode && it.enabled }
                ?: return null
            val vid = cfg.variantId.orEmpty()
            val local = if (vid.isNotEmpty()) store.get(tunedPkg, rec.tunedMode, vid) else null
            val preset = Preset(
                vid.ifEmpty { newPresetId() },
                cfg.variantName.orEmpty().ifEmpty { local?.name ?: s(R.string.current_setting) },
                cfg.deepCopy().also { it.ensureArrays() }, local?.display ?: DisplayValues.builtin(rec.tunedMode),
            )
            val game = games.label(tunedPkg)
            name = s(R.string.suggest_name, game, preset.name)
            item = ImportItem.Mine(
                tunedPkg, rec.tunedMode, preset, game, preset.name,
                s(R.string.import_where, game, s(modeLabelRes(rec.tunedMode))),
            )
        } else {
            val src = sources.firstOrNull { it.profileKey == rec.profileKey } ?: return null
            val built = builtInItem(src)
            name = s(R.string.suggest_name, built.title, baseModeName(rec.baseMode))
            item = built
        }
        val recorded = info.profile
        val candidate = rec.profile
        val chips = ArrayList<String>()
        for (r in Resource.GOLD..Resource.GPU) {
            val d = rec.directions?.getOrNull(r) ?: 0
            if (d == 0) continue
            val ladder = limits.ladder(r)
            if (ladder.isEmpty()) continue
            var text: String? = null
            if (recorded != null && candidate != null) {
                val from = recorded.coolRange(limits, r).second
                val to = candidate.coolRange(limits, r).second
                if (to != from && (to > from) == (d > 0)) {
                    text = s(R.string.suggest_dir_steps, app.resourceName(r), app.signedSteps(to - from), unitless(r, ladder[from]), Freq.one(r, ladder[to]))
                }
            }
            chips.add(text ?: s(if (d > 0) R.string.suggest_dir_higher else R.string.suggest_dir_lower, app.resourceName(r)))
        }
        if (recorded != null && candidate != null && candidate.thermalCase != recorded.thermalCase) {
            chips.add(s(R.string.suggest_dir_heat, app.heatName(recorded.thermalCase), app.heatName(candidate.thermalCase)))
        }
        val fpsLimit = capFpsLimit(rec.confirmedCap, rec.ceilingFps, context.display.fpsLimit)
        if (fpsLimit != null) chips.add(s(R.string.cap_fps_limit) + " " + fpsLabel(context.display.fpsLimit) + " → " + fpsLabel(fpsLimit))
        if (chips.isEmpty()) chips.add(s(R.string.suggest_dir_other))
        return Suggestion(
            name, tunedPkg.isNotEmpty(), chips, reasonFor(info, rec), item, rec.baseMode,
            includeExpert = tunedPkg == info.packageName, context = context, fpsLimit = fpsLimit,
        )
    }

    /** One short line from the daemon's reason bits that fits what the candidate changes. */
    private fun reasonFor(info: RecordingInfo, rec: Recommendation): String {
        val bits = rec.reasons
        fun has(b: Int) = (bits and b) != 0
        val raise = rec.directions?.any { it > 0 } == true
        val lower = rec.directions?.any { it < 0 } == true
        val target = info.summary.analysisTargetFps.takeIf { it > 0 } ?: info.summary.targetFps
        val hot = tf.absolute(info.summary.maxSkinC.toDouble())
        val out = ArrayList<String>()
        if (raise) {
            if (has(IGameTuning.REASON_THERMAL_LIMITED)) out.add(s(R.string.reason_thermal))
            if (has(IGameTuning.REASON_GPU_BOUND)) out.add(s(R.string.reason_gpu_bound))
            if (has(IGameTuning.REASON_CPU_BOUND)) out.add(s(R.string.reason_cpu_bound))
            if (has(IGameTuning.REASON_FPS_SHORT)) out.add(s(R.string.reason_fps_short, target))
        }
        if (lower) {
            if (has(IGameTuning.REASON_OVERPROVISIONED)) out.add(s(R.string.reason_over))
            if (has(IGameTuning.REASON_HOT)) out.add(s(R.string.reason_hot, hot))
        }
        if (out.isEmpty()) {
            if (has(IGameTuning.REASON_TARGET_MET)) out.add(s(R.string.reason_target_met, target))
            if (has(IGameTuning.REASON_FPS_SHORT)) out.add(s(R.string.reason_fps_short, target))
            if (has(IGameTuning.REASON_HOT)) out.add(s(R.string.reason_hot, hot))
            if (has(IGameTuning.REASON_NO_POWER)) out.add(s(R.string.reason_no_power))
        }
        return if (out.isEmpty()) s(R.string.reason_generic) else out.take(2).joinToString(" ")
    }

    /** The single change the daemon proposes for a record and goal, with an observation line. */
    suspend fun autoPlanFor(info: RecordingInfo, goal: Int): Outcome<AutoPlan> {
        return try {
            val context = adviceContext(info)
            val p = repo.autoTune(info.id, goal)
            if (p.rangeRevision != info.rangeRevision) throw TuningException(IGameTuning.ERROR_INVALID, "Stale range revision")
            val fpsLimit = capFpsLimit(p.confirmedCap, p.ceilingFps, context.display.fpsLimit)
            val limits = repo.limits()
            val lines = p.changes.orEmpty().mapNotNull { changeLine(limits, it) }.toMutableList()
            if (fpsLimit != null) lines.add(0, ChangeLine(s(R.string.cap_fps_limit), fpsLabel(context.display.fpsLimit), fpsLabel(fpsLimit), "", false))
            validateAdvice(info, context)
            Outcome.Ok(AutoPlan(p, lines, observationFor(info, p), context, fpsLimit))
        } catch (e: TuningException) {
            Outcome.Failed(e.code)
        }
    }

    private fun changeLine(limits: PolicyLimits, ch: TuningChange): ChangeLine? {
        val res = app.resources
        when (ch.kind) {
            IGameTuning.CHANGE_MAX_FREQ, IGameTuning.CHANGE_MIN_FREQ -> {
                val r = ch.resource
                val ladder = limits.ladder(r)
                if (ladder.isEmpty()) return null
                val isMax = ch.kind == IGameTuning.CHANGE_MAX_FREQ
                val show = { v: Int -> if (v <= 0) s(R.string.no_minimum) else Freq.one(r, v) }
                val steps = ladder.indexAtOrBelow(ch.after.coerceAtLeast(0)) - ladder.indexAtOrBelow(ch.before.coerceAtLeast(0))
                val n = kotlin.math.abs(steps)
                val delta = when {
                    steps > 0 -> res.getQuantityString(R.plurals.steps_higher, n, n)
                    steps < 0 -> res.getQuantityString(R.plurals.steps_lower, n, n)
                    else -> ""
                }
                return ChangeLine(
                    s(if (isMax) R.string.change_top else R.string.change_low, app.resourceName(r)),
                    show(ch.before), show(ch.after), delta, steps > 0,
                )
            }
            IGameTuning.CHANGE_THERMAL_CASE -> {
                val levels = (ch.after - ch.before) / 100
                val n = kotlin.math.abs(levels)
                val delta = when {
                    levels > 0 -> res.getQuantityString(R.plurals.levels_higher, n, n)
                    levels < 0 -> res.getQuantityString(R.plurals.levels_lower, n, n)
                    else -> ""
                }
                return ChangeLine(s(R.string.heat_limit), app.heatName(ch.before), app.heatName(ch.after), delta, levels > 0)
            }
            IGameTuning.CHANGE_FRAME_BOOST -> {
                val on = ch.after == 1
                return ChangeLine(
                    s(R.string.ramp_up), app.onOff(ch.before == 1), app.onOff(on),
                    s(if (on) R.string.chip_turned_on else R.string.chip_turned_off), on,
                )
            }
        }
        return null
    }

    private fun observationFor(info: RecordingInfo, p: TuningProposal): String {
        fun has(b: Int) = (p.reasons and b) != 0
        val change = p.changes?.firstOrNull()
        val lowering = change != null && change.after < change.before
        val target = info.summary.targetFps
        return when {
            change != null && (change.kind == IGameTuning.CHANGE_THERMAL_CASE || (lowering && has(IGameTuning.REASON_HOT))) ->
                s(R.string.obs_thermal)
            has(IGameTuning.REASON_FPS_SHORT) -> when {
                has(IGameTuning.REASON_GPU_BOUND) -> s(R.string.obs_short_gpu)
                has(IGameTuning.REASON_CPU_BOUND) -> s(R.string.obs_short_cpu)
                else -> s(R.string.obs_short, target)
            }
            change != null && lowering && change.resource >= 0 -> s(R.string.obs_unused, app.resourceName(change.resource))
            has(IGameTuning.REASON_TARGET_MET) -> s(R.string.obs_met, target)
            else -> s(R.string.obs_none)
        }
    }

    /**
     * Applies an automatic adjustment with the proposal's tuning unchanged, so the daemon can tell
     * it from the proposal. Changing the record's preset keeps the previous tuning as its last stable
     * setting; a built-in setting is never changed, only saved as a new preset.
     */
    fun applyAutoTune(info: RecordingInfo, plan: AutoPlan, asNew: Boolean, name: String) = launchLocked {
        val pkg = info.packageName
        requireTuningEnabled(pkg)
        validateAdvice(info, plan.context)
        val mode = info.mode
        val lib = buildLibrary(pkg, mode, repo.tunings())
        val recorded = recordPresetIn(lib.presets, info)
        val before = Snap(pkg, mode, lib.presets, lib.inUseId)
        if ((asNew || recorded == null) && lib.presets.customCount() + 1 >= MAX_PRESETS) {
            say(s(R.string.preset_limit)); return@launchLocked
        }
        val replacing = !asNew && recorded != null
        val id = if (replacing) recorded!!.id else newPresetId()
        val finalName = if (replacing) recorded!!.name else uniqueName(lib.presets, name.trim().take(64).ifEmpty { s(R.string.new_preset) })
        val cfg = plan.proposal.tuning.deepCopy().also { it.variantId = id; it.variantName = finalName }
        val display = plan.context.display.copy(fpsLimit = plan.fpsLimit ?: plan.context.display.fpsLimit)
        val preset = Preset(id, finalName, cfg, display, if (asNew) null else recorded?.archiveOrigin)
        val displayBefore = applyAdvice(before, preset, plan.fpsLimit != null)
        if (replacing) store.setStable(pkg, mode, recorded!!.id, recorded.config)
        reloadLocked()
        say(s(if (replacing) R.string.auto_changed else R.string.preset_created, finalName), adviceUndo(before, displayBefore))
    }

    private fun fpsLabel(limit: Int) = if (limit == 0) s(R.string.off) else limit.toString()

    private suspend fun adviceContext(info: RecordingInfo): AdviceContext {
        val current = repo.recording(info.id)
        if (current.rangeRevision != info.rangeRevision) throw TuningException(IGameTuning.ERROR_INVALID, "Stale range revision")
        val tunings = repo.tunings()
        val lib = buildLibrary(info.packageName, info.mode, tunings)
        val target = recordPresetIn(lib.presets, info) ?: lib.inUse
        val active = tunings.firstOrNull { it.packageName == info.packageName && it.mode == info.mode }
        val signature = buildString {
            append(active?.toBase64()); append('|'); append(lib.inUseId)
            for (p in lib.presets) { append('|'); append(p.id); append(p.name); append(p.config.toBase64()); append(p.display) }
            append(games.currentMode(info.packageName)); append(games.allowances(info.packageName))
            append(displayControl.readResolution(info.packageName, info.mode))
            append(displayControl.readRefreshRate(info.packageName)); append(displayControl.readFpsLimit(info.packageName, info.mode))
        }
        val targetDisplay = target?.display ?: DisplayValues.builtin(info.mode)
        val limits = listOfNotNull(targetDisplay.fpsLimit.takeIf { it > 0 }, lib.inUse?.display?.fpsLimit?.takeIf { it > 0 },
            displayControl.readFpsLimit(info.packageName, info.mode)?.takeIf { it > 0 })
        val conservativeDisplay = targetDisplay.copy(fpsLimit = limits.minOrNull() ?: 0)
        return AdviceContext(info.rangeRevision, signature, conservativeDisplay)
    }

    private suspend fun validateAdvice(info: RecordingInfo, expected: AdviceContext) {
        if (adviceContext(info) != expected) throw TuningException(IGameTuning.ERROR_INVALID, "Stale proposal")
    }

    /** Hardware and app display fields form one transaction; a required FPS intervention must succeed. */
    private class AdviceDisplaySnapshot(val display: DisplayValues, val refresh: DisplayControl.RefreshSnapshot, val appliedRefresh: Int)

    private suspend fun applyAdvice(before: Snap, preset: Preset, fpsChanged: Boolean): AdviceDisplaySnapshot {
        val pkg = before.pkg; val mode = before.mode
        val previous = before.presets.firstOrNull { it.id == before.inUseId }
        var applied: GameTuningConfig? = null
        val originalFps = displayControl.readFpsLimit(pkg, mode)
        val originalScale = displayControl.readResolution(pkg, mode)
        val refreshBefore = displayControl.snapshotRefresh(pkg)
        val originalRefresh = refreshBefore.rate
        val displayBefore = (previous?.display ?: DisplayValues.builtin(mode)).copy(
            resolution = originalScale, fpsLimit = originalFps ?: previous?.display?.fpsLimit ?: 0)
        try {
            if (displayAffectsProbe(originalFps, originalScale, originalRefresh, preset.display,
                    displayControl.refreshTarget(preset.display, games.allowances(pkg).fpsOverride)))
                repo.invalidateDisplayProbe(pkg, mode)
            val cfg = preset.config.deepCopy().also {
                it.userId = userId; it.packageName = pkg; it.mode = mode; it.enabled = true
                it.variantId = preset.id; it.variantName = preset.name
            }
            applied = repo.setTuning(cfg).also { it.ensureArrays() }
            val stored = Preset(preset.id, preset.name, applied, preset.display, preset.archiveOrigin)
            store.upsert(pkg, mode, stored)
            if (!displayControl.applyGameManager(pkg, mode, preset.display, games.allowances(pkg), requireFps = fpsChanged))
                throw TuningException(IGameTuning.ERROR_INVALID, "Display apply failed")
            displayControl.applyRefresh(pkg, mode)
            applyGraphics(pkg, mode, preset.display.graphics)
            val expectedRefresh = displayControl.refreshTarget(preset.display, true)
            if (fpsChanged && games.currentMode(pkg) == mode && expectedRefresh > 0 &&
                displayControl.readRefreshRate(pkg) != expectedRefresh)
                throw TuningException(IGameTuning.ERROR_INVALID, "Display apply failed")
            return AdviceDisplaySnapshot(displayBefore, refreshBefore, displayControl.readRefreshRate(pkg))
        } catch (e: Exception) {
            withContext(NonCancellable) {
                val failures = restoreIndependently(
                    { applyGraphics(pkg, mode, displayBefore.graphics) },
                    {
                        val live = repo.tunings().firstOrNull { it.packageName == pkg && it.mode == mode }
                        if (applied != null && live?.toBase64() == applied.toBase64()) {
                            if (previous == null) repo.clearTuning(pkg, mode) else repo.setTuning(previous.config)
                        }
                    },
                    {
                        val nowFps = displayControl.readFpsLimit(pkg, mode)
                        val nowScale = displayControl.readResolution(pkg, mode)
                        if ((nowFps == preset.display.fpsLimit && kotlin.math.abs(nowScale - preset.display.resolution) < .01f) ||
                            (nowFps == originalFps && kotlin.math.abs(nowScale - originalScale) < .01f)) {
                            if (!displayControl.applyGameManager(pkg, mode, displayBefore, games.allowances(pkg)))
                                throw TuningException(IGameTuning.ERROR_INVALID, "Display apply failed")
                        }
                    },
                    {
                        val refresh = displayControl.readRefreshRate(pkg)
                        if (refresh == originalRefresh || refresh == displayControl.refreshTarget(preset.display, true))
                            displayControl.restoreRefresh(pkg, refreshBefore, refresh)
                    },
                    {
                        val local = store.list(pkg, mode)
                        val stillOurs = local.any { it.id == preset.id && it.display == preset.display &&
                            it.config.toBase64() == applied?.toBase64() }
                        if (stillOurs || local == before.presets) store.replaceAll(pkg, mode, before.presets)
                    },
                )
                failures.forEach { if (it !== e) e.addSuppressed(it) }
            }
            throw e
        }
    }

    private suspend fun adviceUndo(before: Snap, displayBefore: AdviceDisplaySnapshot): suspend () -> Unit {
        val expected = adviceState(before.pkg, before.mode)
        return {
            mutex.withLock {
                handleFailure {
                    if (adviceState(before.pkg, before.mode) == expected) {
                        val previous = before.presets.firstOrNull { it.id == before.inUseId }
                        val failures = withContext(NonCancellable) {
                            restoreIndependently(
                                { repo.invalidateDisplayProbe(before.pkg, before.mode) },
                                { if (previous == null) repo.clearTuning(before.pkg, before.mode) else repo.setTuning(previous.config) },
                                {
                                    if (!displayControl.applyGameManager(before.pkg, before.mode, displayBefore.display, games.allowances(before.pkg)))
                                        throw TuningException(IGameTuning.ERROR_INVALID, "Display apply failed")
                                },
                                { applyGraphics(before.pkg, before.mode, displayBefore.display.graphics) },
                                { displayControl.restoreRefresh(before.pkg, displayBefore.refresh, displayBefore.appliedRefresh) },
                                { store.replaceAll(before.pkg, before.mode, before.presets) },
                            )
                        }
                        if (failures.isNotEmpty()) {
                            failures.drop(1).forEach { failures.first().addSuppressed(it) }
                            throw failures.first()
                        }
                        reloadLocked()
                    }
                }
            }
        }
    }

    private suspend fun adviceState(pkg: String, mode: Int): String {
        val tunings = repo.tunings()
        val library = buildLibrary(pkg, mode, tunings)
        return tunings.firstOrNull { it.packageName == pkg && it.mode == mode }?.toBase64().orEmpty() +
            library.presets.joinToString { it.id + it.config.toBase64() + it.display } +
            displayControl.readResolution(pkg, mode) + ":" + displayControl.readRefreshRate(pkg) + ":" + displayControl.readFpsLimit(pkg, mode) + ":" + games.currentMode(pkg)
    }

    // endregion

    // region notice

    private suspend fun computeNotice(info: RecordingInfo, limits: PolicyLimits): String? {
        val samples = try { repo.samples(info.id, info.rangeRevision) } catch (_: TuningException) { return null }
        if (samples.isEmpty()) return null
        var bestRes = -1
        var bestCount = 0
        var bestTemp = Int.MIN_VALUE
        for (r in Resource.GOLD..Resource.GPU) {
            val caps = samples.map {
                when (r) {
                    Resource.GOLD -> it.goldCapKHz
                    Resource.PRIME -> it.primeCapKHz
                    else -> it.gpuCapHz
                }
            }
            val top = caps.filter { it > 0 }.maxOrNull() ?: continue
            var count = 0
            var temp = Int.MIN_VALUE
            samples.forEachIndexed { i, smp ->
                if (caps[i] in 1 until top) {
                    count++
                    if (smp.skinMilliC > vendor.lenovo.hardware.gametuning.IGameTuning.TEMPERATURE_UNAVAILABLE) temp = maxOf(temp, smp.skinMilliC)
                }
            }
            if (count > bestCount) {
                bestCount = count; bestRes = r; bestTemp = temp
            }
        }
        if (bestRes < 0 || bestCount < 60 || bestTemp == Int.MIN_VALUE) return null
        val minutes = (bestCount / 60).coerceAtLeast(1)
        val id = when (bestRes) {
            Resource.GOLD -> R.string.notice_lowered_cpu
            Resource.PRIME -> R.string.notice_lowered_fastest
            else -> R.string.notice_lowered_graphics
        }
        return s(id, minutes, tf.absoluteMilli(bestTemp))
    }

    // endregion

    companion object {
        fun modeLabelRes(mode: Int) = when (mode) {
            Modes.NONE -> R.string.mode_none
            Modes.PERFORMANCE -> R.string.mode_performance
            Modes.BATTERY -> R.string.mode_battery
            else -> R.string.mode_balanced
        }
    }
}
