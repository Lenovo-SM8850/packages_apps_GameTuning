// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.Freq
import io.github.miner7222.gametuning.data.highIndex
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.lowIndex
import io.github.miner7222.gametuning.data.range
import io.github.miner7222.gametuning.data.rangeFromIndices
import io.github.miner7222.gametuning.data.resourceName
import vendor.lenovo.hardware.gametuning.PolicyStep
import kotlin.math.roundToInt

private data class StepVal(val temp: Float, val lo: List<Int>, val hi: List<Int>)

/** Edit by temperature: up to four steps, each with its own speed ranges. No landscape board exists, so both layouts centre one column. */
@Composable
fun StepsScreen(vm: AppViewModel, nav: Nav, pkg: String, mode: Int, wide: Boolean) {
    val st = vm.tuning?.takeIf { it.pkg == pkg && it.mode == mode }
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ctx = LocalContext.current
    val limits = st.limits
    val steps = remember(pkg, mode) {
        mutableStateListOf<StepVal>().also { list ->
            val src = st.effective.steps?.toList().orEmpty()
            src.forEachIndexed { i, s ->
                val lo = ArrayList<Int>(); val hi = ArrayList<Int>()
                for (r in 0..2) {
                    val ladder = limits.ladder(r)
                    lo.add(ladder.lowIndex(s.range(r))); hi.add(ladder.highIndex(s.range(r)))
                }
                list.add(StepVal(if (i == 0) 0f else s.temperatureC, lo, hi))
            }
        }
    }
    var picker by remember { mutableStateOf(Pair(-1, -1)) }
    val increasing = (1 until steps.size).all { steps[it].temp > steps[it - 1].temp }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.steps_title), st.label, { nav.pop() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Muted(stringResource(R.string.steps_intro), Modifier.padding(horizontal = 8.dp), MaterialTheme.typography.bodyMedium)
                steps.forEachIndexed { i, s ->
                    TonalCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (i == 0) stringResource(R.string.step_first) else stringResource(R.string.step_row, i + 1, vm.tf.absolute(s.temp.toDouble())),
                                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                )
                                if (steps.size > 1 && i > 0) {
                                    TextButton(onClick = { steps.removeAt(i) }, Modifier.heightIn(min = 48.dp)) {
                                        Icon(Icons.Rounded.Delete, null); Text(stringResource(R.string.step_remove), Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                            if (i > 0) {
                                SliderField(
                                    stringResource(R.string.steps_temp_title), s.temp, 20f..60f, 79, { vm.tf.absolute(it.toDouble()) },
                                    { v -> steps[i] = s.copy(temp = (v * 2).roundToInt() / 2f) },
                                )
                            }
                            for (r in 0..2) {
                                val ladder = limits.ladder(r)
                                if (ladder.isEmpty()) continue
                                val label = ctx.resourceName(r)
                                SpeedRange(
                                    label, ladder.lastIndex, s.lo[r], s.hi[r], { a, b -> Freq.range(r, ladder[a], ladder[b]) },
                                    { a, b -> steps[i] = s.copy(lo = s.lo.toMutableList().also { it[r] = a }, hi = s.hi.toMutableList().also { it[r] = b }) },
                                    { picker = i to r }, null, {},
                                )
                                if (picker == i to r) {
                                    ExactStepDialog(
                                        stringResource(R.string.exact_steps_for, label), ladder.map { Freq.one(r, it) }, s.lo[r], s.hi[r],
                                        { a, b -> steps[i] = s.copy(lo = s.lo.toMutableList().also { it[r] = a }, hi = s.hi.toMutableList().also { it[r] = b }) },
                                        { picker = -1 to -1 },
                                    )
                                }
                            }
                        }
                    }
                }
                if (steps.size < 4 && steps.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        val last = steps.last()
                        steps.add(last.copy(temp = (last.temp.coerceAtLeast(30f) + 2f).coerceAtMost(60f)))
                    }, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.step_add), Modifier.padding(start = 8.dp)) }
                }
                if (!increasing) Text(stringResource(R.string.steps_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        vm.edit(ctx.getString(R.string.saved_steps_cleared)) { cfg, d, _ -> cfg.steps = emptyArray(); d }
                        nav.pop()
                    }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.steps_use_base)) }
                    Button(onClick = {
                        val built = steps.mapIndexed { i, s ->
                            PolicyStep().also {
                                it.temperatureC = if (i == 0) 0f else s.temp
                                it.gold = rangeFromIndices(limits.ladder(0), s.lo[0], s.hi[0])
                                it.prime = rangeFromIndices(limits.ladder(1), s.lo[1], s.hi[1])
                                it.gpu = rangeFromIndices(limits.ladder(2), s.lo[2], s.hi[2])
                            }
                        }.toTypedArray()
                        vm.edit(ctx.getString(R.string.saved_steps, built.size)) { cfg, d, _ ->
                            cfg.steps = built; cfg.maxOffsets = intArrayOf(0, 0, 0); cfg.minOffsets = intArrayOf(0, 0, 0); d
                        }
                        nav.pop()
                    }, enabled = increasing && steps.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
