// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context
import android.util.Log
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
    private val lock = Any()
    private val presets = HashMap<String, List<Preset>>()
    private val stable = HashMap<String, GameTuningConfig>()
    private var loaded = false

    private fun key(pkg: String, mode: Int) = "$pkg|$mode"
    private fun stableKey(pkg: String, mode: Int, id: String) = "$pkg|$mode|$id"

    private fun ensureLoaded() {
        if (loaded) return
        if (!file.exists()) {
            loaded = true
            return
        }
        try {
            val root = JSONObject(file.readText())
            val games = root.optJSONObject("games") ?: JSONObject()
            for (pkg in games.keys()) {
                val modes = games.getJSONObject(pkg)
                for (m in modes.keys()) {
                    val mode = m.toIntOrNull() ?: continue
                    val entry = modes.getJSONObject(m)
                    val list = ArrayList<Preset>()
                    val arr = entry.optJSONArray("presets") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val config = configFromBase64(o.getString("config")) ?: continue
                        config.ensureArrays()
                        list.add(
                            Preset(
                                o.getString("id"), o.getString("name"), config,
                                DisplayValues(
                                    o.optDouble("res", 1.0).toFloat(), o.optInt("fps", 0),
                                    o.optBoolean("lock", false),
                                ),
                            ),
                        )
                    }
                    presets[key(pkg, mode)] = list
                    val st = entry.optJSONObject("stable")
                    if (st != null) {
                        for (id in st.keys()) {
                            configFromBase64(st.getString(id))?.let {
                                it.ensureArrays()
                                stable[stableKey(pkg, mode, id)] = it
                            }
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Preset file is damaged, starting empty", e)
            presets.clear(); stable.clear()
        }
        loaded = true
    }

    private fun save() {
        val games = JSONObject()
        val keys = (presets.keys + stable.keys.map { it.substringBeforeLast('|') }).toSet()
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
                        .put("lock", p.display.lockRefresh),
                )
            }
            entry.put("presets", arr)
            val st = JSONObject()
            for ((sk, cfg) in stable) {
                if (sk.substringBeforeLast('|') == k) st.put(sk.substringAfterLast('|'), cfg.toBase64())
            }
            entry.put("stable", st)
            game.put(mode, entry)
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("version", 1).put("games", games).toString())
        if (!tmp.renameTo(file)) throw IOException("Could not replace the preset file")
    }

    /** Readers share this instance; failed writes restore the last committed local state. */
    private fun update(block: () -> Unit) = synchronized(lock) {
        ensureLoaded()
        val previousPresets = HashMap(presets)
        val previousStable = HashMap(stable)
        try {
            block()
            save()
        } catch (e: IOException) {
            presets.clear(); presets.putAll(previousPresets)
            stable.clear(); stable.putAll(previousStable)
            throw e
        }
    }

    fun list(pkg: String, mode: Int): List<Preset> = synchronized(lock) {
        ensureLoaded()
        presets[key(pkg, mode)].orEmpty()
    }

    fun get(pkg: String, mode: Int, id: String): Preset? = list(pkg, mode).firstOrNull { it.id == id }

    fun replaceAll(pkg: String, mode: Int, list: List<Preset>) = update {
        presets[key(pkg, mode)] = list.toList()
        val ids = list.map { it.id }.toSet()
        stable.keys.removeAll { it.startsWith("$pkg|$mode|") && it.substringAfterLast('|') !in ids }
    }

    fun upsert(pkg: String, mode: Int, preset: Preset) = update {
        val old = presets[key(pkg, mode)].orEmpty()
        presets[key(pkg, mode)] =
            if (old.any { it.id == preset.id }) old.map { if (it.id == preset.id) preset else it }
            else old + preset
    }

    fun delete(pkg: String, mode: Int, id: String) = update {
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
