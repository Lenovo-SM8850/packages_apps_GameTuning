// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import vendor.lenovo.hardware.gametuning.GameTuningConfig
import java.io.File
import java.io.IOException

/**
 * The preset library: named presets per (game, profile) plus the "last stable" tuning of each
 * preset. One JSON file per user in the app's private storage.
 */
class PresetStore private constructor(context: Context) {
    private val file = File(context.filesDir, "presets-u${context.userId}.json")
    private val atomicFile = AtomicFile(file)
    private val lock = Any()
    private val presets = HashMap<String, List<Preset>>()
    private val stable = HashMap<String, GameTuningConfig>()
    private val checkpoints = HashMap<String, BuiltinCheckpoint>()
    private val processed = HashMap<String, Set<String>>()
    private var loaded = false
    private var archiveEpoch = ""
    private var archiveGeneration = 0L
    private var quarantinePending = false

    private fun quarantine() {
        if (!quarantinePending) return
        val backup = File(file.path + ".bad-" + java.util.UUID.randomUUID())
        if (file.exists() && !file.renameTo(backup)) throw IOException("Could not preserve damaged preset file")
        val directory = Os.open(file.parent!!, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        quarantinePending = false
    }

    private fun key(pkg: String, mode: Int) = "$pkg|$mode"
    private fun stableKey(pkg: String, mode: Int, id: String) = "$pkg|$mode|$id"

    private fun ensureLoaded() {
        if (loaded) return
        if (!atomicFile.exists()) {
            loaded = true
            return
        }
        try {
            val root = JSONObject(atomicFile.openRead().bufferedReader().use { it.readText() })
            archiveEpoch = root.optString("builtinEpoch")
            archiveGeneration = root.optLong("builtinGeneration", 0)
            val games = root.optJSONObject("games") ?: throw JSONException("Missing preset games")
            for (pkg in games.keys()) {
                val modes = games.optJSONObject(pkg)
                if (modes == null) { quarantinePending = true; continue }
                for (m in modes.keys()) {
                    val mode = m.toIntOrNull() ?: continue
                    val entry = modes.optJSONObject(m)
                    if (entry == null) { quarantinePending = true; continue }
                    val list = ArrayList<Preset>()
                    val arr = entry.optJSONArray("presets") ?: JSONArray()
                    if (entry.has("presets") && entry.optJSONArray("presets") == null) quarantinePending = true
                    presets[key(pkg, mode)] = list
                    for (i in 0 until arr.length()) {
                        try {
                            val o = arr.getJSONObject(i)
                            val config = configFromBase64(o.getString("config"))
                            if (config == null) { quarantinePending = true; continue }
                            config.ensureArrays()
                            list.add(
                                Preset(
                                    o.getString("id"), o.getString("name"), config,
                                    DisplayValues(
                                        o.optDouble("res", 1.0).toFloat(), o.optInt("fps", 0),
                                        o.optBoolean("lock", false), GraphicsValues.fromJson(o.optJSONObject("graphics")),
                                    ),
                                    o.optString("archiveOrigin").takeIf { it.startsWith("@builtin/") },
                                ),
                            )
                        } catch (_: Exception) { quarantinePending = true }
                    }
                    presets[key(pkg, mode)] = list
                    val checkpoint = entry.optJSONObject("builtinCheckpoint")
                    if (checkpoint != null && checkpoint.optLong("generation", 0) > 0) {
                        checkpoints[key(pkg, mode)] = BuiltinCheckpoint(checkpoint.optLong("generation"),
                            DisplayValues(checkpoint.optDouble("res", DisplayValues.builtin(mode).resolution.toDouble()).toFloat(),
                                checkpoint.optInt("fps", 0), checkpoint.optBoolean("lock", false)))
                    }
                    val seen = entry.optJSONArray("builtinProcessed")
                    if (seen != null) processed[key(pkg, mode)] = (0 until seen.length())
                        .mapNotNull { seen.optString(it).takeIf { value -> value.startsWith("@builtin/") } }.toSet()
                    val st = entry.optJSONObject("stable")
                    if (st != null) {
                        for (id in st.keys()) {
                            val config = configFromBase64(st.optString(id))
                            if (config == null) { quarantinePending = true; continue }
                            config.let {
                                it.ensureArrays()
                                stable[stableKey(pkg, mode, id)] = it
                            }
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Preset file is damaged", e)
            quarantinePending = true
        }
        quarantine()
        loaded = true
    }

    private fun save() {
        quarantine()
        val games = JSONObject()
        val keys = (presets.keys + stable.keys.map { it.substringBeforeLast('|') } + checkpoints.keys + processed.keys).toSet()
        for (k in keys) {
            val pkg = k.substringBefore('|')
            val mode = k.substringAfter('|')
            val game = games.optJSONObject(pkg) ?: JSONObject().also { games.put(pkg, it) }
            val entry = JSONObject()
            val arr = JSONArray()
            for (p in presets[k].orEmpty()) {
                arr.put(
                    JSONObject().put("id", p.id).put("name", p.name).put("config", p.config.toBase64())
                        .put("res", p.display.resolution.toDouble()).put("fps", p.display.fpsLimit)
                        .put("lock", p.display.lockRefresh).put("graphics", p.display.graphics.toJson()),
                )
                if (p.archiveOrigin != null) arr.getJSONObject(arr.length() - 1).put("archiveOrigin", p.archiveOrigin)
            }
            entry.put("presets", arr)
            val st = JSONObject()
            for ((sk, cfg) in stable) {
                if (sk.substringBeforeLast('|') == k) st.put(sk.substringAfterLast('|'), cfg.toBase64())
            }
            entry.put("stable", st)
            checkpoints[k]?.let { c -> entry.put("builtinCheckpoint", JSONObject()
                .put("generation", c.generation).put("res", c.display.resolution.toDouble())
                .put("fps", c.display.fpsLimit).put("lock", c.display.lockRefresh)) }
            entry.put("builtinProcessed", JSONArray(processed[k].orEmpty().toList()))
            game.put(mode, entry)
        }
        val bytes = JSONObject().put("version", 3).put("games", games)
            .put("builtinEpoch", archiveEpoch).put("builtinGeneration", archiveGeneration)
            .toString().toByteArray(Charsets.UTF_8)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            if (!file.readBytes().contentEquals(bytes)) throw IOException("Could not replace the preset file")
            val directory = Os.open(file.parent!!, OsConstants.O_RDONLY, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw IOException("Could not replace the preset file", e)
        }
    }

    /** Readers share this instance; failed writes restore the last committed local state. */
    private fun update(block: () -> Unit) = synchronized(lock) {
        ensureLoaded()
        val previousPresets = HashMap(presets)
        val previousStable = HashMap(stable)
        val previousCheckpoints = HashMap(checkpoints)
        val previousProcessed = HashMap(processed)
        val previousEpoch = archiveEpoch
        val previousGeneration = archiveGeneration
        try {
            block()
            save()
        } catch (e: Exception) {
            presets.clear(); presets.putAll(previousPresets)
            stable.clear(); stable.putAll(previousStable)
            checkpoints.clear(); checkpoints.putAll(previousCheckpoints)
            processed.clear(); processed.putAll(previousProcessed)
            archiveEpoch = previousEpoch; archiveGeneration = previousGeneration
            throw e
        }
    }

    fun list(pkg: String, mode: Int): List<Preset> = synchronized(lock) {
        ensureLoaded()
        presets[key(pkg, mode)].orEmpty()
    }

    fun get(pkg: String, mode: Int, id: String): Preset? = list(pkg, mode).firstOrNull { it.id == id }

    fun builtinCheckpoint(pkg: String, mode: Int): BuiltinCheckpoint? = synchronized(lock) {
        ensureLoaded(); checkpoints[key(pkg, mode)]
    }

    fun builtinEpoch(): String = synchronized(lock) { ensureLoaded(); archiveEpoch }
    fun builtinGeneration(): Long = synchronized(lock) { ensureLoaded(); archiveGeneration }

    /** Insertion and suppression/checkpoint records commit together, without changing active tuning. */
    fun syncBuiltins(pkg: String, mode: Int, generation: Long, transitions: List<BuiltinTransition>,
                     userId: Int, label: (String) -> String) =
        syncBuiltinsBatch("00000000000000000000000000000000", generation,
            listOf(BuiltinSyncUpdate(pkg, mode, transitions)), userId, builtinEpoch(), label)

    /** One sync pass commits every eligible game/profile, epoch and checkpoint together. */
    fun syncBuiltinsBatch(epoch: String, generation: Long, updates: List<BuiltinSyncUpdate>, userId: Int,
                          expectedEpoch: String, label: (String) -> String) = update {
        require(epoch.matches(Regex("[0-9a-f]{32}")) && generation > 0)
        require(archiveEpoch == expectedEpoch || archiveEpoch == epoch) { "Archive epoch changed during synchronization" }
        if (archiveEpoch == epoch) require(generation >= archiveGeneration) { "Archive generation regressed" }
        if (archiveEpoch != epoch) {
            checkpoints.clear(); processed.clear(); archiveEpoch = epoch; archiveGeneration = 0
        }
        for ((pkg, mode, transitions) in updates) {
            require(mode in Modes.ALL && generation > 0)
            val k = key(pkg, mode)
            val checkpoint = checkpoints[k]
            if (checkpoint != null && generation > checkpoint.generation) {
                var list = presets[k].orEmpty()
                val seen = processed[k].orEmpty().toMutableSet()
                for (t in transitions.sortedBy { it.generation }) {
                    require(t.generation <= generation)
                    if (t.generation <= checkpoint.generation) continue
                    if (!seen.add(t.key)) continue
                    if (list.any { it.archiveOrigin == t.key }) continue
                    val config = defaultConfig(userId, pkg, mode).also { it.baseProfile = t.key }
                    val name = archivePresetName(label(t.version), list)
                    list = list + Preset(newPresetId(), name, config, checkpoint.display, t.key)
                }
                presets[k] = list
                processed[k] = seen
            }
            // A newly encountered game seeds its baseline rather than importing earlier history.
            if (checkpoint == null || generation >= checkpoint.generation)
                checkpoints[k] = BuiltinCheckpoint(generation, DisplayValues.builtin(mode))
        }
        archiveGeneration = generation
    }

    fun replaceAll(pkg: String, mode: Int, list: List<Preset>) = update {
        // Undo snapshots may predate a concurrent archive synchronization.
        val retained = presets[key(pkg, mode)].orEmpty().filter { p ->
            p.archiveOrigin != null && list.none { it.id == p.id }
        }
        val merged = list.toList() + retained
        presets[key(pkg, mode)] = merged
        val ids = merged.map { it.id }.toSet()
        stable.keys.removeAll { it.startsWith("$pkg|$mode|") && it.substringAfterLast('|') !in ids }
    }

    fun upsert(pkg: String, mode: Int, preset: Preset) = update {
        val old = presets[key(pkg, mode)].orEmpty()
        val origin = old.firstOrNull { it.id == preset.id }?.archiveOrigin ?: preset.archiveOrigin
        val saved = if (origin == preset.archiveOrigin) preset else Preset(preset.id, preset.name, preset.config, preset.display, origin)
        presets[key(pkg, mode)] =
            if (old.any { it.id == saved.id }) old.map { if (it.id == saved.id) saved else it }
            else old + saved
    }

    fun delete(pkg: String, mode: Int, id: String) = update {
        presets[key(pkg, mode)].orEmpty().firstOrNull { it.id == id }?.archiveOrigin?.let {
            processed[key(pkg, mode)] = processed[key(pkg, mode)].orEmpty() + it
        }
        presets[key(pkg, mode)] = presets[key(pkg, mode)].orEmpty().filter { it.id != id }
        stable.remove(stableKey(pkg, mode, id))
    }

    /** The tuning saved before the latest automatic change of this preset. */
    fun stableConfig(pkg: String, mode: Int, id: String): GameTuningConfig? = synchronized(lock) {
        ensureLoaded()
        stable[stableKey(pkg, mode, id)]?.deepCopy()
    }

    fun setStable(pkg: String, mode: Int, id: String, config: GameTuningConfig) = update {
        stable[stableKey(pkg, mode, id)] = config.deepCopy()
    }

    fun clearGame(pkg: String) = update {
        presets.keys.removeAll { it.substringBefore('|') == pkg }
        stable.keys.removeAll { it.substringBefore('|') == pkg }
    }

    /** Every preset of every other game, for the import list. */
    fun others(excludePkg: String): List<Triple<String, Int, Preset>> = synchronized(lock) {
        ensureLoaded()
        presets.flatMap { (k, v) ->
            val pkg = k.substringBefore('|')
            val mode = k.substringAfter('|').toInt()
            if (pkg == excludePkg) emptyList() else v.map { Triple(pkg, mode, it) }
        }
    }

    companion object {
        private const val TAG = "GameTuning"
        private val instances = HashMap<String, PresetStore>()

        fun getInstance(context: Context): PresetStore = synchronized(instances) {
            val app = context.applicationContext
            val path = File(app.filesDir, "presets-u${app.userId}.json").absolutePath
            instances.getOrPut(path) { PresetStore(app) }
        }
    }
}
