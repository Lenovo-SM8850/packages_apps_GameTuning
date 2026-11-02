// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning

import android.content.Context
import io.github.miner7222.gametuning.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Standalone persistence checks. The host runner supplies file-backed Android I/O shims. */
fun main(args: Array<String>) {
    val dir = File(args.first()).apply { mkdirs() }
    fun store(user: Int = 0): PresetStore {
        val context = Context::class.java.getConstructor(File::class.java, Int::class.javaPrimitiveType)
            .newInstance(dir, user)
        return PresetStore::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
            .newInstance(context)
    }
    val pkg = "test.game"
    val mode = Modes.BALANCED
    val config = defaultConfig(0, pkg, mode).apply { variantId = "original"; variantName = "Original" }
    val encoded = config.toBase64()
    val entry = JSONObject().put("presets", JSONArray().put(JSONObject()
        .put("id", "original").put("name", "Original").put("config", encoded)
        .put("res", .85).put("fps", 90).put("lock", true)))
        .put("stable", JSONObject().put("original", encoded))
    val file = File(dir, "presets-u0.json")
    file.writeText(JSONObject().put("version", 1).put("games", JSONObject()
        .put(pkg, JSONObject().put(mode.toString(), entry))).toString())
    var s = store()
    check(s.list(pkg, mode).single().id == "original")
    check(s.list(pkg, mode).single().display == DisplayValues(.85f, 90, true))
    check(s.stableConfig(pkg, mode, "original")!!.toBase64() == encoded)
    check(s.list(pkg, mode).single().display.graphics == GraphicsValues())
    check(Modes.ALL.all { DisplayValues.builtin(it).graphics == GraphicsValues() })
    check(GraphicsValues.fromJson(JSONObject().put("msaaLimit", "2")) == GraphicsValues(msaaLimit = "2"))
    val graphics = GraphicsValues("Fastest", "16.0", "-4.0", "4")
    check(GraphicsValues.fromJson(graphics.toJson()) == graphics)
    val options = graphics.toOptions()
    check(options.textureFiltering == "Fastest" && options.anisotropicFiltering == "16.0" &&
        options.mipmapLodBias == "-4.0" && options.msaaLimit == "4")
    var graphicsStore = store(20)
    val graphicsDisplay = DisplayValues(.85f, 90, true, graphics)
    graphicsStore.upsert(pkg, mode, Preset("graphics", "Graphics", config.deepCopy(), graphicsDisplay))
    graphicsStore = store(20)
    val graphicsPreset = graphicsStore.get(pkg, mode, "graphics")!!
    check(graphicsPreset.display == graphicsDisplay)
    graphicsStore.upsert("copy.game", Modes.PERFORMANCE, Preset("copy", graphicsPreset.name,
        graphicsPreset.config.retarget(20, "copy.game", Modes.PERFORMANCE, "default"), graphicsPreset.display))
    check(store(20).get("copy.game", Modes.PERFORMANCE, "copy")!!.display.graphics == graphics)
    graphicsStore.replaceAll(pkg, mode, listOf(Preset("graphics", "Graphics", config, DisplayValues())))
    check(store(20).get(pkg, mode, "graphics")!!.display.graphics == GraphicsValues())
    val label: (String) -> String = { "Built-in · $it" }
    val a = BuiltinTransition("@builtin/2/0", 2, "A")
    val b = BuiltinTransition("@builtin/3/0", 3, "B")
    // First encounter seeds rather than manufacturing personal history.
    s.syncBuiltins(pkg, mode, 1, emptyList(), 0, label)
    check(JSONObject(file.readText()).getInt("version") == 3)
    check(s.list(pkg, mode).single().config.toBase64() == encoded)
    check(s.stableConfig(pkg, mode, "original")!!.toBase64() == encoded)
    repeat(8) { i -> s.upsert(pkg, mode, Preset("custom$i", "Custom $i", config.deepCopy(), DisplayValues())) }
    check(s.list(pkg, mode).customCount() == 9)
    s.syncBuiltins(pkg, mode, 3, listOf(a, b), 0, label)
    check(s.list(pkg, mode).size == 11 && s.list(pkg, mode).customCount() == 9)
    s.replaceAll(pkg, mode, s.list(pkg, mode).filter { it.archiveOrigin == null })
    check(s.list(pkg, mode).size == 11) // A stale undo must retain concurrently inserted archives.
    val archive = s.list(pkg, mode).first { it.archiveOrigin == a.key }
    check(archive.name == "Built-in · A" && archive.config.baseProfile == a.key)
    check(archive.display == DisplayValues.builtin(mode))
    check(s.get(pkg, mode, "original")!!.config.toBase64() == encoded)
    check(s.get(pkg, mode, "original")!!.display == DisplayValues(.85f, 90, true))
    val ids = s.list(pkg, mode).map { it.id }
    // Competing/repeated synchronizations are harmless even with an older read checkpoint.
    s.syncBuiltins(pkg, mode, 3, listOf(a, b), 0, label)
    check(s.list(pkg, mode).map { it.id } == ids)
    s.upsert(pkg, mode, Preset(archive.id, "Renamed", archive.config, archive.display, archive.archiveOrigin))
    s.delete(pkg, mode, archive.id)
    s = store()
    s.syncBuiltins(pkg, mode, 3, listOf(a, b), 0, label)
    check(s.list(pkg, mode).none { it.archiveOrigin == a.key })
    check(s.list(pkg, mode).count { it.archiveOrigin == b.key } == 1)
    // Explicit tombstones still suppress copies if a checkpoint must be recovered.
    val json = JSONObject(file.readText())
    val saved = json.getJSONObject("games").getJSONObject(pkg).getJSONObject(mode.toString())
    saved.getJSONObject("builtinCheckpoint").put("generation", 1)
    file.writeText(json.toString())
    s = store(); s.syncBuiltins(pkg, mode, 3, listOf(a, b), 0, label)
    check(s.list(pkg, mode).none { it.archiveOrigin == a.key })
    check(s.list(pkg, mode).count { it.archiveOrigin == b.key } == 1)
    val before = file.readText()
    Class.forName("android.util.AtomicFile").getField("failNext").setBoolean(null, true)
    var failed = false
    try { s.syncBuiltins(pkg, mode, 4, listOf(BuiltinTransition("@builtin/4/0", 4, "C")), 0, label) }
    catch (_: IOException) { failed = true }
    check(failed && file.readText() == before && s.builtinCheckpoint(pkg, mode)!!.generation == 3L)
    s.syncBuiltins(pkg, mode, 4, listOf(BuiltinTransition("@builtin/4/0", 4, "C")), 0, label)
    check(s.list(pkg, mode).count { it.archiveOrigin == "@builtin/4/0" } == 1)
    val committed = file.readText()
    Class.forName("android.util.AtomicFile").getField("failRename").setBoolean(null, true)
    failed = false
    try { s.syncBuiltins(pkg, mode, 5, listOf(BuiltinTransition("@builtin/5/0", 5, "D")), 0, label) }
    catch (_: IOException) { failed = true }
    check(failed && file.readText() == committed && s.builtinCheckpoint(pkg, mode)!!.generation == 4L)
    var rejected = false
    try { s.syncBuiltins(pkg, Modes.NONE, 4, listOf(a), 0, label) } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected && s.list(pkg, Modes.NONE).isEmpty())
    val other = store(10)
    check(other.list(pkg, mode).isEmpty() && other.builtinCheckpoint(pkg, mode) == null)
    other.syncBuiltins(pkg, mode, 4, listOf(a, b), 10, label)
    check(other.list(pkg, mode).isEmpty())
    // Missing or malformed optional history leaves the existing v1/v2 library intact.
    val damaged = JSONObject(file.readText())
    damaged.getJSONObject("games").getJSONObject(pkg).getJSONObject(mode.toString())
        .put("builtinCheckpoint", "damaged").put("builtinProcessed", "damaged")
    file.writeText(damaged.toString())
    s = store()
    check(s.list(pkg, mode).size == 11)
    s.syncBuiltins(pkg, mode, 5, listOf(a, b), 0, label)
    check(s.list(pkg, mode).size == 11)
    val long = "Built-in · " + "😀한".repeat(30)
    val name = archivePresetName(long, emptyList())
    check(name.toByteArray().size <= 64)
    val collision = archivePresetName(long, listOf(Preset("collision", name, config, DisplayValues())))
    check(collision.endsWith(" 2") && collision.toByteArray().size <= 64)
    val duplicate = Preset("copy", archive.name, archive.config.deepCopy(), archive.display)
    check(listOf(archive, duplicate).customCount() == 1)
    val remaining = s.list(pkg, mode).first { it.archiveOrigin != null }
    s.upsert(pkg, mode, Preset(remaining.id, "Imported replacement", config, remaining.display))
    check(s.get(pkg, mode, remaining.id)!!.archiveOrigin == remaining.archiveOrigin)
    // One undecodable preset must preserve both the damaged bytes and all readable entries.
    val invalid = JSONObject(file.readText())
    invalid.getJSONObject("games").getJSONObject(pkg).getJSONObject(mode.toString()).getJSONArray("presets")
        .put(JSONObject().put("id", "broken").put("name", "Broken").put("config", "!invalid!"))
    val invalidBytes = invalid.toString(); file.writeText(invalidBytes)
    s = store()
    check(s.list(pkg, mode).size == 11)
    check(!file.exists() && dir.listFiles()!!.any { it.name.startsWith("presets-u0.json.bad-") && it.readText() == invalidBytes })
    s.setStable(pkg, mode, "original", config)
    check(s.list(pkg, mode).size == 11)
    file.writeText("{broken json")
    s = store(); check(s.list(pkg, mode).isEmpty())
    check(!file.exists() && dir.listFiles()!!.any { it.name.startsWith("presets-u0.json.bad-") && it.readText() == "{broken json" })
    if (args.size > 1) repositoryArchiveTests(File(dir, "repository"), args.drop(1))
    println("BUILTIN_APP_ARCHIVE_PASS migration atomic idempotent full deletion rename active display None multi-user names corrupt-history")
}
