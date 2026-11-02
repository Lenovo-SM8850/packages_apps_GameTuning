// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning

import android.content.Context
import io.github.miner7222.gametuning.data.*
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import vendor.lenovo.hardware.gametuning.PolicyProfile

/** Exercises the production repository loop against the daemon's real v1 protocol decoder. */
fun repositoryArchiveTests(dir: File, command: List<String>) {
    dir.mkdirs()
    val context = Context::class.java.getConstructor(File::class.java, Int::class.javaPrimitiveType).newInstance(dir, 0)
    val store = PresetStore.getInstance(context)
    val process = ProcessBuilder(command + File(dir, "daemon").path).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val input = process.outputStream.bufferedWriter()
    val output = process.inputStream.bufferedReader()
    fun request(s: String): String {
        input.write(s); input.newLine(); input.flush()
        return output.readLine() ?: error("Archive protocol process ended")
    }
    fun run(block: suspend () -> Unit) {
        var result: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<Unit>) { result = value }
        })
        check(result != null); result!!.getOrThrow()
    }
    val label: (String) -> String = { "Built-in · $it" }
    var calls = 0
    val source: suspend (String, Int) -> PolicyProfile = { key, mode ->
        calls++
        val response = request("$key $mode")
        check(!response.startsWith("ERROR")) { response }
        val fields = response.split('\t')
        PolicyProfile().apply { profileKey = fields[0]; variantId = fields[1]; variantName = fields[2] }
    }
    val games = listOf("test.game" to Modes.BALANCED, "test.fallback" to Modes.BALANCED, "test.none" to Modes.NONE)
    fun sync(marker: String) = run {
        GameTuningRepository.syncBuiltinHistory(store, 0, marker, games, { it != "test.none" }, source, label)
    }
    fun writes(): Int = Class.forName("android.util.AtomicFile").getField("writes").getInt(null)
    try {
        val a = request("A")
        var count = writes(); sync(a)
        check(writes() == count + 1 && calls == 0)
        check(store.list("test.game", Modes.BALANCED).isEmpty())
        request("B")
        val c = request("C")
        val file = File(dir, "presets-u0.json")
        val before = file.readText()
        var failed = false
        try { run {
            var n = 0
            GameTuningRepository.syncBuiltinHistory(store, 0, c, games, { true }, { key, mode ->
                if (++n == 2) error("Injected history failure") else source(key, mode)
            }, label)
        } } catch (_: IllegalStateException) { failed = true }
        check(failed && file.readText() == before)
        count = writes(); sync(c)
        check(writes() == count + 1)
        for (mode in Modes.ALL) {
            check(store.list("test.game", mode).map { it.name } == listOf("Built-in · A", "Built-in · B"))
            check(store.list("test.fallback", mode).isEmpty())
            check(store.builtinCheckpoint("test.none", mode) == null)
        }
        val existing = store.list("test.game", Modes.BALANCED)
        val oldEpoch = store.builtinEpoch()
        count = writes(); sync(c); check(writes() == count)
        val regressed = "@builtin/current/$oldEpoch/1"
        failed = false
        try { sync(regressed) } catch (_: IllegalArgumentException) { failed = true }
        check(failed && writes() == count)
        val reset = request("RESET")
        sync(reset)
        check(store.builtinEpoch() != oldEpoch && store.builtinGeneration() == 1L)
        check(store.list("test.game", Modes.BALANCED).map { it.id } == existing.map { it.id })
        check(request("@builtin/history/$oldEpoch/test.game/0 1").startsWith("ERROR"))
        sync(request("A"))
        val after = store.list("test.game", Modes.BALANCED)
        check(after.size == 3 && after.last().name == "Built-in · C")
        check(after.last().archiveOrigin!!.startsWith("@builtin/${store.builtinEpoch()}/"))
        check(after.last().archiveOrigin !in existing.map { it.archiveOrigin })
        println("BUILTIN_REPOSITORY_PROTOCOL_PASS batch offline-history empty-end epoch-change regression no-activation")
    } finally {
        input.close(); check(process.waitFor() == 0)
    }
}
