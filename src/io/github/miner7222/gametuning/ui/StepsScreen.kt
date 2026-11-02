// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

/**
 * Edit by temperature: up to four steps, each with its own speed ranges. Wide screens put the steps in a
 * rail on the left, the picked step on the right, and Save in the top bar; narrow screens centre one column.
 */
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
    val initial = remember(pkg, mode) { steps.toList() }
    var picker by remember { mutableStateOf(Pair(-1, -1)) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }
    val increasing = (1 until steps.size).all { steps[it].temp > steps[it - 1].temp }
    val dirty = steps.toList() != initial
    val sel = selected.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val canSave = increasing && steps.isNotEmpty()

    // Leaving with edits that were not saved asks first.
    val leave: () -> Unit = { if (dirty) confirmLeave = true else nav.pop() }
    BackHandler(enabled = dirty) { confirmLeave = true }

    val useBase: () -> Unit = {
        vm.edit(ctx.getString(R.string.saved_steps_cleared)) { cfg, d, _ -> cfg.steps = emptyArray(); d }
        nav.pop()
    }
    val save: () -> Unit = {
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
    }

    @Composable
    fun stepTitle(i: Int, s: StepVal): String =
        if (i == 0) steps.getOrNull(1)?.let { stringResource(R.string.step_first_below, vm.tf.absolute(it.temp.toDouble())) }
            ?: stringResource(R.string.step_first)
        else stringResource(R.string.step_row, i + 1, vm.tf.absolute(s.temp.toDouble()))

    @Composable
    fun addStep() {
        OutlinedButton(onClick = {
            val last = steps.last()
            steps.add(last.copy(temp = (last.temp.coerceAtLeast(30f) + 2f).coerceAtMost(60f)))
            selected = steps.lastIndex
        }, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.step_add), Modifier.padding(start = 8.dp)) }
    }

    /** The title, the start temperature and the three speed ranges of step [i]. */
    @Composable
    fun stepBody(i: Int, s: StepVal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stepTitle(i, s), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                label, ladder.lastIndex, s.lo[r], s.hi[r], { a, b -> Freq.range(r, ladder[a], ladder[b]) }, { a -> Freq.one(r, ladder[a]) },
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

    Column(Modifier.fillMaxSize()) {
        if (wide) {
            TopBar(
                stringResource(R.string.steps_title), st.label, leave, Modifier.padding(horizontal = 20.dp),
                actions = {
                    TextButton(onClick = useBase, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.steps_use_base)) }
                    Button(onClick = save, enabled = canSave, modifier = Modifier.heightIn(min = 40.dp)) { Text(stringResource(R.string.save)) }
                    Spacer(Modifier.width(8.dp))
                },
            )
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    steps.forEachIndexed { i, s ->
                        val on = i == sel
                        val itemShape = if (on) RoundedCornerShape(28.dp) else groupShape(i, steps.size, 24.dp)
                        Surface(
                            shape = itemShape,
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(on, role = Role.Button) { selected = i },
                        ) {
                            Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                Text(stepTitle(i, s), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (steps.size < 4 && steps.isNotEmpty()) Box(Modifier.padding(top = 12.dp)) { addStep() }
                    if (!increasing) Text(stringResource(R.string.steps_invalid), Modifier.padding(top = 8.dp, start = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    if (steps.isNotEmpty()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stepBody(sel, steps[sel])
                        }
                    }
                }
            }
        } else {
            TopBar(stringResource(R.string.steps_title), st.label, leave)
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 720.dp).verticalScroll(scroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Muted(stringResource(R.string.steps_intro), Modifier.padding(horizontal = 8.dp), MaterialTheme.typography.bodyMedium)
                    steps.forEachIndexed { i, s ->
                        TonalCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                stepBody(i, s)
                            }
                        }
                    }
                    if (steps.size < 4 && steps.isNotEmpty()) addStep()
                    if (!increasing) Text(stringResource(R.string.steps_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Pinned actions; the system bars are already kept clear by the root layout. The divider shows
            // while steps continue behind the bar.
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    HorizontalDivider(color = if (scroll.canScrollForward) MaterialTheme.colorScheme.outlineVariant else Color.Transparent)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Row(
                            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(onClick = useBase, Modifier.weight(1f, fill = false).heightIn(min = 48.dp)) { Text(stringResource(R.string.steps_use_base)) }
                            Button(onClick = save, enabled = canSave, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            stringResource(R.string.discard_changes_q), null, stringResource(R.string.discard), destructive = true,
            onConfirm = { nav.pop() }, onDismiss = { confirmLeave = false },
        )
    }
}
