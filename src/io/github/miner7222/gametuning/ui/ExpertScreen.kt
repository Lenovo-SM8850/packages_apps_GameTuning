// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.Freq
import io.github.miner7222.gametuning.data.KEEP
import io.github.miner7222.gametuning.data.Resource
import io.github.miner7222.gametuning.data.copyRow
import io.github.miner7222.gametuning.data.copyRule
import io.github.miner7222.gametuning.data.formatSeconds
import io.github.miner7222.gametuning.data.highIndex
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.lowIndex
import io.github.miner7222.gametuning.data.coolRange
import io.github.miner7222.gametuning.data.rangeFromIndices
import io.github.miner7222.gametuning.data.builtinName
import io.github.miner7222.gametuning.data.resourceName
import vendor.lenovo.hardware.gametuning.FrequencyRange
import vendor.lenovo.hardware.gametuning.SceneRow
import vendor.lenovo.hardware.gametuning.ThreadRule
import kotlin.math.roundToInt

private const val MAX_SCENES = 32
private const val MAX_RULES = 16
private const val MAX_RT_RULES = 8

private enum class ExpertSec { THREADS, SCENES }

private fun sceneRows(st: TuningState): List<SceneRow> =
    (if (st.config.scenes.isNotEmpty()) st.config.scenes else st.effective.scenes ?: emptyArray()).toList()

private fun threadRules(st: TuningState): List<ThreadRule> =
    (if (st.config.threadRules == 3 && st.config.customThreads.isNotEmpty()) st.config.customThreads else st.effective.threads ?: emptyArray()).toList()

private fun editScenes(vm: AppViewModel, desc: String, mutate: (MutableList<SceneRow>) -> Unit) {
    vm.edit(desc) { cfg, d, s ->
        val list = sceneRows(s).map { it.copyRow() }.toMutableList()
        mutate(list)
        cfg.scenes = list.toTypedArray()
        if (cfg.sceneMode == KEEP || cfg.sceneMode == 0 && list.isNotEmpty() && s.effective.sceneMode == 1) cfg.sceneMode = 1
        d
    }
}

private fun editThreads(vm: AppViewModel, desc: String, mutate: (MutableList<ThreadRule>) -> Unit) {
    vm.edit(desc) { cfg, d, s ->
        val list = threadRules(s).map { it.copyRule() }.toMutableList()
        mutate(list)
        cfg.customThreads = list.toTypedArray()
        cfg.threadRules = 3
        d
    }
}

@Composable
fun ExpertScreen(vm: AppViewModel, nav: Nav, pkg: String, mode: Int, wide: Boolean) {
    val st = vm.tuning?.takeIf { it.pkg == pkg && it.mode == mode }
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ctx = LocalContext.current
    var sec by rememberSaveable { mutableStateOf(ExpertSec.THREADS.name) }
    var selScene by rememberSaveable { mutableStateOf(-1) }
    var selRule by rememberSaveable { mutableStateOf(0) }
    var sceneSheet by rememberSaveable { mutableStateOf(false) }
    var addScene by rememberSaveable { mutableStateOf(false) }
    var addRule by rememberSaveable { mutableStateOf(false) }
    val scenes = sceneRows(st)
    val rules = threadRules(st)
    val current = scenes.firstOrNull { it.sceneId == selScene } ?: scenes.firstOrNull()
    val presetName = st.preset?.name ?: ctx.builtinName(st.dedicated)
    val subtitle = stringResource(R.string.tuning_sub, st.label, stringResource(AppViewModel.modeLabelRes(mode)), presetName)
    val section = ExpertSec.values().firstOrNull { it.name == sec } ?: ExpertSec.THREADS
    val secs = listOf(
        ExpertSec.THREADS to stringResource(R.string.thread_rules_n, rules.size),
        ExpertSec.SCENES to stringResource(R.string.scene_rows_n, scenes.size),
    )
    val reset: @Composable () -> Unit = {
        TextButton(onClick = {
            vm.edit(ctx.getString(R.string.saved_expert_reset)) { cfg, d, _ ->
                cfg.sceneMode = KEEP; cfg.scenes = emptyArray(); cfg.customThreads = emptyArray()
                if (cfg.threadRules == 3) cfg.threadRules = 1
                cfg.boostLengthMs = KEEP; cfg.dropSensitivity = KEEP; cfg.hysteresisDeciC = KEEP
                d
            }
        }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reset_expert)) }
    }
    val seen = vm.profile?.takeIf { it.pkg == pkg }?.recordings?.firstOrNull { it.mode == mode && it.summary?.scenesSeen?.isNotEmpty() == true }?.summary?.scenesSeen

    Column(Modifier.fillMaxSize()) {
        if (wide) {
            TopBar(stringResource(R.string.expert_title), subtitle, { nav.pop() }, Modifier.padding(horizontal = 24.dp))
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    secs.forEachIndexed { i, (s, title) ->
                        val on = s == section
                        val itemShape = if (on) RoundedCornerShape(28.dp) else groupShape(i, secs.size, 24.dp)
                        Surface(
                            shape = itemShape,
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(on, role = Role.Button) { sec = s.name },
                        ) {
                            Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                Text(title.substringBefore(" · "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text(expertSummary(vm, st, s, scenes.size, rules.size), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    reset()
                }
                when (section) {
                    ExpertSec.SCENES -> if (!st.scenesSupported) {
                        Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                            UnsupportedScenes()
                        }
                    } else if (scenes.isEmpty()) {
                        // Nothing to list or edit yet: one pane with the switch and the empty state.
                        Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SceneHeader(vm, st, seen, warning = true)
                                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                    EmptyState(Icons.Rounded.Layers, stringResource(R.string.no_scene_rows)) {
                                        AddButton(R.string.add_scene, scenes.size < MAX_SCENES) { addScene = true }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SceneHeader(vm, st, seen, warning = true)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                scenes.forEachIndexed { i, r ->
                                    val on = r.sceneId == current?.sceneId
                                    val itemShape = if (on) RoundedCornerShape(28.dp) else groupShape(i, scenes.size, 24.dp)
                                    Surface(
                                        shape = itemShape,
                                        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(on, role = Role.Button) { selScene = r.sceneId },
                                    ) {
                                        Row(Modifier.heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            SceneBadge(r.sceneId, 44)
                                            Column {
                                                Text(stringResource(R.string.scene_n, r.sceneId), style = MaterialTheme.typography.bodyLarge)
                                                Muted(stringResource(R.string.scene_status, stringResource(if (r.enabled) R.string.on else R.string.off), formatSeconds(r.holdMs)))
                                            }
                                        }
                                    }
                                }
                            }
                            AddButton(R.string.add_scene, scenes.size < MAX_SCENES) { addScene = true }
                        }
                        Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                            if (current != null) {
                                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp)) { SceneEditor(vm, st, current, true) }
                            }
                        }
                    }
                    ExpertSec.THREADS -> if (rules.isEmpty()) {
                        Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ExpertWarning()
                                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                    EmptyThreadRules { addRule = true }
                                }
                            }
                        }
                    } else {
                        val sel = selRule.coerceIn(0, (rules.size - 1).coerceAtLeast(0))
                        Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                rules.forEachIndexed { i, ru ->
                                    val on = i == sel
                                    val itemShape = if (on) RoundedCornerShape(28.dp) else groupShape(i, rules.size, 24.dp)
                                    Surface(
                                        shape = itemShape,
                                        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(on, role = Role.Button) { selRule = i },
                                    ) {
                                        Column(Modifier.heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                            Text(ru.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                            Text(ruleSummary(st, ru), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            AddButton(R.string.add_rule, rules.size < MAX_RULES) { addRule = true }
                        }
                        Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ExpertWarning()
                                RuleEditor(vm, st, sel, rules, true)
                            }
                        }
                    }

                }
            }
        } else {
            TopBar(stringResource(R.string.expert_title), subtitle, { nav.pop() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = navBottomPadding() + 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Warning, null, Modifier.padding(top = 2.dp).size(20.dp))
                            Text(stringResource(R.string.expert_warning), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // The scene tab stays selectable: the notice shows only inside it, never on the other tabs.
                    val tab = section
                    ConnectedChoice(
                        secs.map { it.first to it.second }, tab, { sec = it.name },
                        shortOf = { s ->
                            when (s) {
                                ExpertSec.THREADS -> shortLabelResource(R.string.thread_rules_n_short, rules.size)
                                ExpertSec.SCENES -> shortLabelResource(R.string.scene_rows_n_short, scenes.size)
                            }
                        },
                    )
                    when (tab) {
                        ExpertSec.SCENES -> if (!st.scenesSupported) {
                            UnsupportedScenes()
                        } else {
                            SceneHeader(vm, st, seen)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                scenes.forEachIndexed { i, r ->
                                    Surface(shape = groupShape(i, scenes.size, 24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        Row(
                                            Modifier.heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            SceneBadge(r.sceneId, 48)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(stringResource(R.string.scene_hold, r.sceneId, formatSeconds(r.holdMs)), style = MaterialTheme.typography.bodyLarge)
                                                Muted(sceneRanges(st, r))
                                            }
                                            TextButton(onClick = { selScene = r.sceneId; sceneSheet = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) }
                                            Switch(
                                                checked = r.enabled,
                                                onCheckedChange = { v -> editScenes(vm, ctx.getString(R.string.saved_scene, r.sceneId)) { l -> l.first { it.sceneId == r.sceneId }.enabled = v } },
                                                modifier = Modifier.semantics { contentDescription = ctx.getString(R.string.use_scene_n, r.sceneId) },
                                            )
                                        }
                                    }
                                }
                            }
                            AddButton(R.string.add_scene, scenes.size < MAX_SCENES) { addScene = true }
                        }
                        ExpertSec.THREADS -> {
                            CoreLegend(st, Modifier.padding(horizontal = 8.dp))
                            Muted(stringResource(R.string.thread_intro), Modifier.padding(horizontal = 8.dp), MaterialTheme.typography.bodyMedium)
                            if (rules.isEmpty()) EmptyThreadRules { addRule = true }
                            rules.forEachIndexed { i, _ ->
                                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { RuleEditor(vm, st, i, rules, false) }
                                }
                            }
                            if (rules.isNotEmpty()) AddButton(R.string.add_rule, rules.size < MAX_RULES) { addRule = true }
                        }

                    }
                    reset()
                    androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
                }
            }
        }
    }

    if (st.scenesSupported && !wide && sceneSheet && current != null) {
        ModalBottomSheet(onDismissRequest = { sceneSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                SceneEditor(vm, st, current, false) { sceneSheet = false }
            }
        }
    }
    if (st.scenesSupported && addScene) AddSceneDialog(vm, st, scenes, onDismiss = { addScene = false }) { selScene = it }
    if (addRule) AddRuleDialog(vm, st, rules, onDismiss = { addRule = false }) { selRule = it }
}

@Composable
private fun CoreLegend(st: TuningState, modifier: Modifier = Modifier) {
    Muted(stringResource(R.string.cores_legend, st.limits.goldCores - 1, st.limits.goldCores, st.limits.goldCores + st.limits.primeCores - 1), modifier)
}

@Composable
private fun UnsupportedScenes() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        EmptyState(Icons.Rounded.Layers, stringResource(R.string.scene_support_note), Modifier.fillMaxWidth())
    }
}

@Composable
private fun EmptyThreadRules(onAdd: () -> Unit) {
    EmptyState(Icons.Rounded.Memory, stringResource(R.string.no_thread_rules), Modifier.fillMaxWidth()) {
        AddButton(R.string.add_rule, true, onAdd)
    }
}

/** One line that stays inside the first card of a pane. */
@Composable
fun ExpertWarning(modifier: Modifier = Modifier) {
    Surface(modifier, RoundedCornerShape(12.dp), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp))
            Text(stringResource(R.string.expert_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun expertSummary(vm: AppViewModel, st: TuningState, s: ExpertSec, nScenes: Int, nRules: Int): String = when (s) {
    ExpertSec.SCENES -> stringResource(R.string.scene_sum, stringResource(if (st.effective.sceneMode == 1) R.string.on else R.string.off), nScenes)
    ExpertSec.THREADS -> stringResource(R.string.rules_sum, nRules, threadRules(st).count { it.rtPriority >= 90 })
}

private fun sensLabel(v: Int) = when (v) { 1 -> R.string.sensitivity_low; 3 -> R.string.sensitivity_high; else -> R.string.sensitivity_medium }

@Composable
private fun SceneHeader(vm: AppViewModel, st: TuningState, seen: IntArray?, warning: Boolean = false) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            if (warning) ExpertWarning(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
            SwitchRow(
                stringResource(R.string.use_scene_settings), stringResource(R.string.use_scene_desc), st.effective.sceneMode == 1,
                { v -> vm.edit(ctx.getString(R.string.saved_scene_mode, ctx.getString(if (v) R.string.on else R.string.off))) { cfg, d, _ -> cfg.sceneMode = if (v) 1 else 0; d } },
                minHeight = 64.dp, padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
    Muted(stringResource(R.string.scene_support_note), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), MaterialTheme.typography.bodyMedium)
    if (seen != null && seen.isNotEmpty()) {
        Muted(stringResource(R.string.scenes_seen, seen.take(8).joinToString(" · ")), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SceneBadge(id: Int, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(id.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddButton(label: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); Text(stringResource(label), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun sceneRanges(st: TuningState, r: SceneRow): String {
    val ctx = LocalContext.current
    val parts = ArrayList<String>()
    for (res in 0..2) {
        val ladder = st.limits.ladder(res)
        if (ladder.isEmpty()) continue
        val fr = rangeOf(r, res)
        parts.add(ctx.resourceName(res) + " " + Freq.range(res, ladder[ladder.lowIndex(fr)], ladder[ladder.highIndex(fr)]))
    }
    return parts.joinToString(" · ")
}

private fun rangeOf(r: SceneRow, res: Int): FrequencyRange = when (res) {
    Resource.GOLD -> r.gold; Resource.PRIME -> r.prime; else -> r.gpu
} ?: FrequencyRange()

@Composable
private fun AddSceneDialog(vm: AppViewModel, st: TuningState, scenes: List<SceneRow>, onDismiss: () -> Unit, onAdded: (Int) -> Unit) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val id = text.toIntOrNull()
    val valid = id != null && id in 0..100000 && scenes.none { it.sceneId == id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_scene_row)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit).take(6) }, singleLine = true,
                label = { Text(stringResource(R.string.scene_number)) },
                isError = text.isNotEmpty() && !valid,
                supportingText = { Text(stringResource(if (text.isNotEmpty() && !valid) R.string.scene_number_bad else R.string.scene_number_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, colors = disabledReadable(), onClick = {
                val sid = id!!
                editScenes(vm, ctx.getString(R.string.saved_scene, sid)) { l ->
                    val row = SceneRow()
                    row.sceneId = sid; row.enabled = true; row.holdMs = 1000
                    val rs = arrayOf(FrequencyRange(), FrequencyRange(), FrequencyRange())
                    for (res in 0..2) {
                        val ladder = st.limits.ladder(res)
                        if (ladder.isEmpty()) continue
                        val (lo, hi) = st.effective.coolRange(st.limits, res)
                        rs[res] = rangeFromIndices(ladder, lo, hi)
                    }
                    row.gold = rs[0]; row.prime = rs[1]; row.gpu = rs[2]
                    l.add(row); l.sortBy { it.sceneId }
                }
                onAdded(sid); onDismiss()
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A text button whose disabled label stays readable on a dialog. */
@Composable
private fun disabledReadable() = ButtonDefaults.textButtonColors(
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
)

/** Draft of a thread rule: name, cores and real-time priority are chosen before it is saved. */
@Composable
private fun AddRuleDialog(vm: AppViewModel, st: TuningState, rules: List<ThreadRule>, onDismiss: () -> Unit, onAdded: (Int) -> Unit) {
    val ctx = LocalContext.current
    val seed = remember { newRule(st, rules) }
    var name by rememberSaveable { mutableStateOf(seed.name) }
    var mask by rememberSaveable { mutableStateOf(seed.cpuMask) }
    var rt by rememberSaveable { mutableStateOf(seed.rtPriority) }
    val total = coreCount(st)
    val fastStart = total - st.limits.primeCores
    val rtCount = rules.count { it.rtPriority >= 90 }
    val valid = name.isNotBlank() && mask != 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.add_rule)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(15) }, singleLine = true,
                    label = { Text(stringResource(R.string.name)) }, isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.cores_this_thread), style = MaterialTheme.typography.bodyLarge)
                val columns = total.coerceAtMost(8)
                for (rowStart in 0 until total step columns) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in rowStart until minOf(rowStart + columns, total)) {
                            CoreToggle(c, (mask shr c) and 1L == 1L, c >= fastStart, Modifier.weight(1f)) {
                                val next = mask xor (1L shl c)
                                if (next != 0L) mask = next
                            }
                        }
                    }
                }
                val rtOn = rt >= 90
                SwitchRow(
                    stringResource(R.string.rt_priority), null, rtOn, { v -> rt = if (v) 90 else -1 },
                    enabled = rtOn || rtCount < MAX_RT_RULES, minHeight = 56.dp, padding = PaddingValues(0.dp),
                )
                if (rtOn) {
                    SliderField(
                        stringResource(R.string.priority_value), rt.toFloat(), 90f..99f, 8, { it.roundToInt().toString() },
                        { v -> rt = v.roundToInt() }, minLabel = "90", maxLabel = "99",
                    )
                } else if (rtCount >= MAX_RT_RULES) {
                    Muted(stringResource(R.string.rt_limit))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, colors = disabledReadable(), onClick = {
                val rule = ThreadRule().also { it.name = name; it.cpuMask = mask; it.rtPriority = rt }
                editThreads(vm, ctx.getString(R.string.saved_rule_added)) { it.add(rule) }
                onAdded(rules.size); onDismiss()
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** One scene row editor, used in the bottom sheet (portrait) and the right pane (landscape). */
@Composable
private fun SceneEditor(vm: AppViewModel, st: TuningState, row: SceneRow, pane: Boolean, onDeleted: () -> Unit = {}) {
    val ctx = LocalContext.current
    var picker by remember { mutableStateOf(-1) }
    val id = row.sceneId
    fun update(desc: String, f: (SceneRow) -> Unit) = editScenes(vm, desc) { l -> l.first { it.sceneId == id }.let(f) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scene_n, id), Modifier.weight(1f), style = if (pane) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (pane) {
                Text(stringResource(R.string.use_this_scene), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = row.enabled, onCheckedChange = { v -> update(ctx.getString(R.string.saved_scene, id)) { it.enabled = v } }, modifier = Modifier.padding(horizontal = 12.dp))
                IconButton(onClick = { editScenes(vm, ctx.getString(R.string.saved_scene_removed, id)) { l -> l.removeAll { it.sceneId == id } }; onDeleted() }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.delete_scene_row))
                }
            }
        }
        if (!pane) {
            SwitchRow(stringResource(R.string.use_this_scene), null, row.enabled, { v -> update(ctx.getString(R.string.saved_scene, id)) { it.enabled = v } }, minHeight = 56.dp, padding = PaddingValues(0.dp))
        }
        SliderField(
            stringResource(R.string.hold_for), row.holdMs / 1000f, 0.1f..10f, 98, { formatSeconds((it * 10).roundToInt() * 100) },
            { v -> val ms = (v * 10).roundToInt() * 100; update(ctx.getString(R.string.saved_scene, id)) { it.holdMs = ms } },
        )
        for (res in 0..2) {
            val ladder = st.limits.ladder(res)
            if (ladder.isEmpty()) continue
            val fr = rangeOf(row, res)
            val lo = ladder.lowIndex(fr); val hi = ladder.highIndex(fr)
            val label = ctx.resourceName(res)
            fun set(a: Int, b: Int) = update(ctx.getString(R.string.saved_scene, id)) {
                val nr = rangeFromIndices(ladder, a, b)
                when (res) { Resource.GOLD -> it.gold = nr; Resource.PRIME -> it.prime = nr; else -> it.gpu = nr }
            }
            SpeedRange(label, ladder.lastIndex, lo, hi, { a, b -> Freq.range(res, ladder[a], ladder[b]) }, { a -> Freq.one(res, ladder[a]) }, ::set, { picker = res }, null, {})
            if (picker == res) {
                ExactStepDialog(stringResource(R.string.exact_steps_for, label), ladder.map { Freq.one(res, it) }, lo, hi, { a, b -> set(a, b) }, { picker = -1 })
            }
        }
        if (!pane) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { editScenes(vm, ctx.getString(R.string.saved_scene_removed, id)) { l -> l.removeAll { it.sceneId == id } }; onDeleted() }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delete_scene_row), color = MaterialTheme.colorScheme.error)
                }
                androidx.compose.material3.Button(onClick = onDeleted, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

private fun coreCount(st: TuningState) = (st.limits.goldCores + st.limits.primeCores).coerceIn(1, 16)

private fun newRule(st: TuningState, existing: List<ThreadRule>): ThreadRule {
    var n = 1
    while (existing.any { it.name == "Thread$n" }) n++
    return ThreadRule().also {
        it.name = "Thread$n"
        it.cpuMask = (1L shl st.limits.goldCores.coerceAtLeast(1)) - 1
        it.rtPriority = -1
    }
}

@Composable
private fun ruleSummary(st: TuningState, r: ThreadRule): String {
    val cores = (0 until coreCount(st)).filter { (r.cpuMask shr it) and 1L == 1L }
    val out = ArrayList<String>()
    var i = 0
    while (i < cores.size) {
        var j = i
        while (j + 1 < cores.size && cores[j + 1] == cores[j] + 1) j++
        out.add(if (j > i) "${cores[i]}–${cores[j]}" else "${cores[i]}")
        i = j + 1
    }
    val text = stringResource(if (cores.size == 1) R.string.core_n else R.string.cores_n, out.joinToString(", "))
    return text + " · " + if (r.rtPriority >= 90) stringResource(R.string.rt_value, r.rtPriority) else stringResource(R.string.normal_priority)
}

@Composable
private fun CommitName(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(15) }, singleLine = true,
        label = { Text(stringResource(R.string.name)) }, isError = text.isBlank(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && text != value) onCommit(text) }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text.isNotBlank() && text != value) onCommit(text) },
    )
}

@Composable
private fun RuleEditor(vm: AppViewModel, st: TuningState, index: Int, rules: List<ThreadRule>, pane: Boolean) {
    val ctx = LocalContext.current
    val rule = rules.getOrNull(index) ?: return
    val total = coreCount(st)
    val fastStart = total - st.limits.primeCores
    val rtCount = rules.count { it.rtPriority >= 90 }
    fun update(desc: String, f: (ThreadRule) -> Unit) = editThreads(vm, desc) { l -> if (index < l.size) f(l[index]) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pane) Text(stringResource(R.string.thread_rule), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            else Text(rule.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            IconButton(onClick = { editThreads(vm, ctx.getString(R.string.saved_rule_removed)) { l -> if (index < l.size) l.removeAt(index) } }, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.delete_rule_n, rule.name))
            }
        }
        if (pane) CommitName(rule.name) { n -> update(ctx.getString(R.string.saved_rule, n)) { it.name = n } }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.cores_this_thread), style = MaterialTheme.typography.bodyLarge)
            if (pane) Muted(stringResource(R.string.fastest_cores_hint, fastStart, total - 1))
        }
        val columns = total.coerceAtMost(8)
        for (rowStart in 0 until total step columns) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (pane) 8.dp else 4.dp)) {
                for (c in rowStart until minOf(rowStart + columns, total)) {
                    val on = (rule.cpuMask shr c) and 1L == 1L
                    val fast = c >= fastStart
                    CoreToggle(c, on, fast, Modifier.weight(1f)) {
                        val mask = rule.cpuMask xor (1L shl c)
                        if (mask != 0L) update(ctx.getString(R.string.saved_rule, rule.name)) { it.cpuMask = mask }
                    }
                }
            }
        }
        val rtOn = rule.rtPriority >= 90
        SwitchRow(
            stringResource(R.string.rt_priority), stringResource(if (pane) R.string.rt_desc else R.string.rt_range), rtOn,
            { v -> update(ctx.getString(R.string.saved_rule, rule.name)) { it.rtPriority = if (v) 90 else -1 } },
            enabled = rtOn || rtCount < MAX_RT_RULES, minHeight = 56.dp, padding = PaddingValues(0.dp),
        )
        if (rtOn) {
            SliderField(
                stringResource(R.string.priority_value), rule.rtPriority.toFloat(), 90f..99f, 8, { it.roundToInt().toString() },
                { v -> update(ctx.getString(R.string.saved_rule, rule.name)) { it.rtPriority = v.roundToInt() } },
                minLabel = "90", maxLabel = "99",
            )
        } else if (rtCount >= MAX_RT_RULES) {
            Muted(stringResource(R.string.rt_limit))
        }
    }
}

@Composable
private fun CoreToggle(core: Int, on: Boolean, fast: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val container = if (!on) androidx.compose.ui.graphics.Color.Transparent else if (fast) cs.tertiary else cs.primary
    val content = if (!on) cs.onSurfaceVariant else if (fast) cs.onTertiary else cs.onPrimary
    val desc = stringResource(if (fast) R.string.core_fastest_cd else R.string.core_cd, core)
    val chipShape = RoundedCornerShape(12.dp)
    Surface(
        shape = chipShape, color = container, contentColor = content,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = desc }
            .clip(chipShape).selectable(on, role = Role.Checkbox, onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(core.toString(), style = MaterialTheme.typography.labelLarge)
            if (fast) Icon(Icons.Rounded.Bolt, null, Modifier.size(14.dp))
        }
    }
}

@Composable
fun BoostBehavior(vm: AppViewModel, st: TuningState) {
    val ctx = LocalContext.current
    val e = st.effective
    SliderField(
        stringResource(R.string.boost_length), e.boostLengthMs.coerceIn(100, 2000).toFloat(), 100f..2000f, 18, { "${(it / 100).roundToInt() * 100} ms" },
        { v -> val ms = (v / 100).roundToInt() * 100; vm.edit(ctx.getString(R.string.saved_boost_length, ms)) { cfg, d, _ -> cfg.boostLengthMs = ms; d } },
        minLabel = "100 ms", maxLabel = "2000 ms",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.drop_sensitivity), style = MaterialTheme.typography.bodyLarge)
        ConnectedChoice(
            listOf(1, 2, 3).map { it to stringResource(sensLabel(it)) }, e.dropSensitivity.coerceIn(1, 3),
            { v -> vm.edit(ctx.getString(R.string.saved_sensitivity)) { cfg, d, _ -> cfg.dropSensitivity = v; d } },
            Modifier.widthIn(max = 420.dp),
        )
        Muted(stringResource(R.string.drop_sensitivity_desc))
    }
    SliderField(
        stringResource(R.string.temp_smoothing), e.hysteresisDeciC.coerceIn(0, 30).toFloat(), 0f..30f, 29, { vm.tf.delta(it.roundToInt() / 10.0) },
        { v -> val dc = v.roundToInt(); vm.edit(ctx.getString(R.string.saved_smoothing)) { cfg, d, _ -> cfg.hysteresisDeciC = dc; d } },
        description = stringResource(R.string.smoothing_desc), minLabel = vm.tf.delta(0.0), maxLabel = vm.tf.delta(3.0),
    )
}
