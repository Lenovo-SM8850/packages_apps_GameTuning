// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.Freq
import io.github.miner7222.gametuning.data.KEEP
import io.github.miner7222.gametuning.data.coolRange
import io.github.miner7222.gametuning.data.FPS_LIMITS
import io.github.miner7222.gametuning.data.formatPower
import io.github.miner7222.gametuning.data.formatScale
import io.github.miner7222.gametuning.data.builtinName
import io.github.miner7222.gametuning.data.heatName
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.fpsName
import io.github.miner7222.gametuning.data.resourceName
import io.github.miner7222.gametuning.data.signedSteps
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Sec { SPEED, TARGETS, BOOSTS, DISPLAY, ADVANCED, UNSAVED }

@Composable
fun TuningScreen(vm: AppViewModel, nav: Nav, pkg: String, mode: Int, wide: Boolean) {
    val st = vm.tuning?.takeIf { it.pkg == pkg && it.mode == mode }
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ctx = LocalContext.current
    var section by rememberSaveable { mutableStateOf(Sec.SPEED.name) }
    var openGroup by rememberSaveable { mutableStateOf(Sec.DISPLAY.name) }
    var menu by remember { mutableStateOf(false) }
    var confirmGame by remember { mutableStateOf(false) }
    var confirmPreset by remember { mutableStateOf(false) }
    val presetName = st.preset?.name ?: ctx.builtinName(st.dedicated)
    val subtitle = stringResource(R.string.tuning_sub, st.label, ctx.modeName(mode), presetName)
    val startsFrom = stringResource(
        if (st.config.baseProfile.isNullOrEmpty()) R.string.starts_from_builtin else R.string.starts_from_other,
        st.changes.size,
    )

    val overflow: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { menu = true }, Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (st.hasStable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.go_back_stable)) },
                        onClick = { menu = false; vm.goBackToStable() },
                    )
                }
                if (st.preset != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reset_preset)) },
                        onClick = { menu = false; confirmPreset = true },
                    )
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.reset_game)) }, onClick = { menu = false; confirmGame = true })
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TopBar(stringResource(R.string.tuning_details), "$subtitle · $startsFrom", { nav.pop() }) }
                androidx.compose.material3.FilledTonalButton(onClick = { nav.push(Route.Presets(pkg, mode)) }, Modifier.heightIn(min = 48.dp)) {
                    Text(presetName); Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                }
                overflow()
            }
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val items = Sec.values().filter { it != Sec.UNSAVED }
                    items.forEachIndexed { i, s ->
                        SectionButton(sectionTitle(s), summary(vm, st, s, true), section == s.name, groupShape(i, items.size, 24.dp)) { section = s.name }
                    }
                    SectionLabel(stringResource(R.string.not_saved_in_presets), Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp))
                    SectionButton(stringResource(R.string.low_battery), stringResource(R.string.low_battery_desc), section == Sec.UNSAVED.name, RoundedCornerShape(24.dp)) { section = Sec.UNSAVED.name }
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (Sec.valueOf(section)) {
                            Sec.SPEED -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.speed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    SpeedContent(vm, st)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.heat_limit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    HeatContent(vm, st, vertical = true)
                                    Muted(stringResource(R.string.heat_hint_wide))
                                }
                            }
                            Sec.TARGETS -> SecPane(R.string.targets) { TargetsContent(vm, st) }
                            Sec.BOOSTS -> SecPane(R.string.boosts) { BoostsContent(vm, st) }
                            Sec.DISPLAY -> SecPane(R.string.display) { DisplayContent(vm, st) }
                            Sec.ADVANCED -> SecPane(R.string.advanced, gap = 16.dp) { AdvancedContent(vm, nav, st) }
                            Sec.UNSAVED -> SecPane(R.string.low_battery, R.string.not_saved_in_presets) { UnsavedContent(vm, st, true) }
                        }
                    }
                }
            }
        } else {
            TopBar(stringResource(R.string.tuning_details), null, { nav.pop() }, actions = { overflow() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Muted(subtitle, style = MaterialTheme.typography.bodyMedium)
                        Muted(startsFrom)
                    }
                    TonalCard(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.speed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SpeedContent(vm, st)
                        }
                    }
                    TonalCard(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.heat_limit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HeatContent(vm, st, vertical = false)
                        }
                    }
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val groups = listOf(Sec.TARGETS, Sec.BOOSTS, Sec.DISPLAY, Sec.ADVANCED)
                        groups.forEachIndexed { i, g ->
                            val open = openGroup == g.name
                            Surface(shape = if (open) RoundedCornerShape(28.dp) else groupShape(i, groups.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(vertical = if (open) 6.dp else 0.dp)) {
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 72.dp).selectable(open, role = Role.Button) { openGroup = if (open) "" else g.name }
                                            .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(sectionTitle(g), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                            Muted(summary(vm, st, g, false), style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (open) {
                                        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            when (g) {
                                                Sec.TARGETS -> TargetsContent(vm, st)
                                                Sec.BOOSTS -> BoostsContent(vm, st)
                                                Sec.DISPLAY -> DisplayContent(vm, st)
                                                else -> AdvancedContent(vm, nav, st)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SectionLabel(stringResource(R.string.not_saved_in_presets), Modifier.padding(top = 20.dp, bottom = 8.dp))
                    TonalCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 8.dp)) { UnsavedContent(vm, st, false) } }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(96.dp))
                }
            }
        }
    }

    if (confirmGame) {
        ConfirmDialog(
            stringResource(R.string.reset_game_q, st.label), stringResource(R.string.reset_game_body), stringResource(R.string.reset), destructive = true,
            onConfirm = { vm.resetGame(pkg) }, onDismiss = { confirmGame = false },
        )
    }
    if (confirmPreset) {
        ConfirmDialog(
            stringResource(R.string.reset_preset_q, presetName), stringResource(R.string.reset_preset_body), stringResource(R.string.reset), destructive = true,
            onConfirm = { vm.resetPreset() }, onDismiss = { confirmPreset = false },
        )
    }
}

@Composable
private fun SecPane(title: Int, subtitle: Int? = null, gap: Dp = 20.dp, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Muted(stringResource(subtitle))
        }
        content()
    }
}

@Composable
private fun SectionButton(title: String, summary: String, current: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = if (current) RoundedCornerShape(28.dp) else shape,
        color = if (current) cs.primaryContainer else cs.surfaceContainer,
        contentColor = if (current) cs.onPrimaryContainer else cs.onSurface,
        modifier = Modifier.fillMaxWidth().selectable(current, role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = if (current) cs.onPrimaryContainer else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun sectionTitle(s: Sec) = stringResource(
    when (s) {
        Sec.SPEED -> R.string.speed_and_heat
        Sec.TARGETS -> R.string.targets
        Sec.BOOSTS -> R.string.boosts
        Sec.DISPLAY -> R.string.display
        Sec.ADVANCED -> R.string.advanced
        Sec.UNSAVED -> R.string.low_battery
    },
)

@Composable
private fun summary(vm: AppViewModel, st: TuningState, s: Sec, wide: Boolean): String {
    val ctx = LocalContext.current
    val e = st.effective
    return when (s) {
        Sec.SPEED -> {
            val parts = ArrayList<String>()
            for (r in 0..2) {
                val d = deltaTop(st, r)
                if (d != 0 && r != 1) parts.add(ctx.resourceName(r) + " " + ctx.signedSteps(d))
            }
            parts.add(ctx.heatName(e.thermalCase))
            parts.joinToString(" · ")
        }
        Sec.TARGETS -> stringResource(R.string.targets_sum, targetText(vm, e.skinTargetMilliC, false), targetText(vm, e.powerTargetMw, true))
        Sec.BOOSTS -> stringResource(
            R.string.boosts_sum, rampValue(st), holdText(st.effective.uclampMin),
            stringResource(if (e.jankBoostSteps?.isNotEmpty() == true) R.string.on else R.string.off),
        )
        Sec.DISPLAY -> {
            val d = st.display
            val fps = if (d.fpsLimit > 0) stringResource(R.string.fps_value, d.fpsLimit) else stringResource(R.string.no_limit)
            stringResource(R.string.display_sum, formatScale(d.resolution), fps, stringResource(if (d.lockRefresh) R.string.refresh_locked else R.string.refresh_free))
        }
        Sec.ADVANCED -> stringResource(if (wide) R.string.advanced_sum_wide else R.string.advanced_sum)
        Sec.UNSAVED -> ""
    }
}

@Composable
private fun targetText(vm: AppViewModel, v: Int, power: Boolean): String =
    if (v < 0) stringResource(R.string.off) else if (power) formatPower(v) else vm.tf.absoluteMilli(v)

@Composable
private fun holdText(uclamp: Int) =
    if (uclamp < 0) stringResource(R.string.off) else stringResource(R.string.percent, uclamp)

private fun rampValue(st: TuningState) = st.effective.frameBoostSteps?.maxOrNull() ?: 0
/** Position of the hold strength slider: 0 is Off (-1), then 0 to 100 percent in steps of 5. */
private fun holdPosition(st: TuningState) = if (st.effective.uclampMin < 0) 0 else st.effective.uclampMin / 5 + 1

private fun deltaTop(st: TuningState, r: Int): Int {
    val (_, bHi) = st.builtin.coolRange(st.limits, r)
    val (_, eHi) = st.effective.coolRange(st.limits, r)
    return eHi - bHi
}

// region Speed and heat

@Composable
private fun SpeedContent(vm: AppViewModel, st: TuningState) {
    val ctx = LocalContext.current
    val limits = st.limits
    var picker by remember { mutableStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0..2) {
            val ladder = limits.ladder(r)
            if (ladder.isEmpty()) continue
            val (bLo, bHi) = st.base.coolRange(limits, r)
            val (eLo, eHi) = st.effective.coolRange(limits, r)
            val (oLo, oHi) = st.builtin.coolRange(limits, r)
            val label = ctx.resourceName(r)
            val changed = st.config.maxOffsets[r] != 0 || st.config.minOffsets[r] != 0 || st.config.steps.isNotEmpty()
            fun commit(lo: Int, hi: Int) {
                vm.edit(ctx.getString(R.string.saved_speed, label, Freq.range(r, ladder[lo], ladder[hi]))) { cfg, d, _ ->
                    cfg.maxOffsets[r] = (hi - bHi).coerceIn(-10, 10)
                    cfg.minOffsets[r] = (lo - bLo).coerceIn(-10, 10)
                    d
                }
            }
            SpeedRange(
                label = label, top = ladder.lastIndex, lo = eLo, hi = eHi,
                valueText = { a, b -> Freq.range(r, ladder[a], ladder[b]) },
                pointText = { a -> Freq.one(r, ladder[a]) },
                onCommit = ::commit, onPick = { picker = r },
                onReset = if (changed) ({
                    vm.edit(ctx.getString(R.string.saved_reset, label)) { cfg, d, _ ->
                        cfg.maxOffsets[r] = 0; cfg.minOffsets[r] = 0; d
                    }
                }) else null,
                note = {
                    val same = oLo == eLo && oHi == eHi
                    Muted(
                        if (same) stringResource(R.string.same_as_builtin)
                        else stringResource(R.string.builtin_range, Freq.range(r, ladder[oLo], ladder[oHi]), ctx.signedSteps(eHi - oHi)),
                    )
                },
            )
            if (picker == r) {
                ExactStepDialog(
                    stringResource(R.string.exact_steps_for, label), ladder.map { Freq.one(r, it) }, eLo, eHi,
                    onConfirm = { a, b -> commit(a, b) }, onDismiss = { picker = -1 },
                )
            }
        }
        Muted(stringResource(R.string.speed_foot), Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HeatContent(vm: AppViewModel, st: TuningState, vertical: Boolean) {
    val ctx = LocalContext.current
    val cases = st.limits.thermalCases?.toList()?.takeIf { it.isNotEmpty() } ?: listOf(100, 200, 300, 400)
    val options = cases.map { it to ctx.heatName(it) }
    val select: (Int) -> Unit = { v ->
        vm.edit(ctx.getString(R.string.saved_heat, ctx.heatName(v))) { cfg, d, s ->
            cfg.thermalCase = if (v == s.builtin.thermalCase) KEEP else v
            d
        }
    }
    if (vertical) {
        val n = options.size
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, (v, label) ->
                val on = v == st.effective.thermalCase
                Surface(
                    shape = if (on) RoundedCornerShape(28.dp) else groupShape(i, n, 24.dp),
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().selectable(on, role = Role.RadioButton) { select(v) },
                ) { Text(label, Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp).wrapCenter(), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
            }
        }
    } else {
        ConnectedChoice(options, st.effective.thermalCase, select, height = 56.dp)
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.padding(vertical = 16.dp))

// endregion

// region Targets, boosts, display

@Composable
private fun TargetsContent(vm: AppViewModel, st: TuningState) {
    val ctx = LocalContext.current
    val skin = st.effective.skinTargetMilliC
    val skinOn = skin >= 0
    val tempLabel = stringResource(R.string.temp_target)
    SliderField(
        label = tempLabel,
        value = (if (skinOn) skin else 41000) / 1000f, range = 35f..48f, steps = 25,
        format = { vm.tf.absolute(it.toDouble()) },
        enabled = skinOn, showValue = skinOn,
        minLabel = vm.tf.absoluteRounded(35.0), maxLabel = vm.tf.absoluteRounded(48.0),
        description = stringResource(R.string.temp_target_desc),
        onCommit = { v ->
            val milli = ((v * 2).roundToInt() * 500)
            vm.edit(ctx.getString(R.string.saved_temp_target, vm.tf.absoluteMilli(milli))) { cfg, d, _ -> cfg.skinTargetMilliC = milli; d }
        },
        trailing = {
            Switch(
                checked = skinOn, modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = tempLabel },
                onCheckedChange = { on ->
                    vm.edit(ctx.getString(R.string.saved_temp_target, if (on) vm.tf.absoluteMilli(41000) else ctx.getString(R.string.off))) { cfg, d, _ ->
                        cfg.skinTargetMilliC = if (on) 41000 else -1; d
                    }
                },
            )
        },
    )
    val power = st.effective.powerTargetMw
    val powerOn = power >= 0
    val powerLabel = stringResource(R.string.power_target)
    SliderField(
        label = powerLabel,
        value = (if (powerOn) power else 6000) / 1000f, range = 2f..12f, steps = 19,
        format = { formatPower((it * 1000).roundToInt()) },
        enabled = powerOn, showValue = powerOn, minLabel = formatPower(2000), maxLabel = formatPower(12000),
        description = stringResource(R.string.power_target_desc),
        onCommit = { v ->
            val mw = ((v * 2).roundToInt() * 500)
            vm.edit(ctx.getString(R.string.saved_power_target, formatPower(mw))) { cfg, d, _ -> cfg.powerTargetMw = mw; d }
        },
        trailing = {
            Switch(
                checked = powerOn, modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = powerLabel },
                onCheckedChange = { on ->
                    vm.edit(ctx.getString(R.string.saved_power_target, if (on) formatPower(6000) else ctx.getString(R.string.off))) { cfg, d, _ ->
                        cfg.powerTargetMw = if (on) 6000 else -1; d
                    }
                },
            )
        },
    )
}

@Composable
private fun BoostsContent(vm: AppViewModel, st: TuningState) {
    val ctx = LocalContext.current
    SliderField(
        label = stringResource(R.string.ramp_up), value = rampValue(st).toFloat(), range = 0f..10f, steps = 9,
        format = { ctx.getString(R.string.of_ten, it.roundToInt()) }, minLabel = "0", maxLabel = "10",
        description = stringResource(R.string.ramp_up_desc),
        onCommit = { v ->
            val n = v.roundToInt()
            vm.edit(ctx.getString(R.string.saved_ramp, n)) { cfg, d, _ ->
                if (n == 0) { cfg.frameBoost = 0; cfg.frameBoostSteps = IntArray(0) }
                else { cfg.frameBoost = 1; cfg.frameBoostSteps = intArrayOf(n, n, n) }
                d
            }
        },
    )
    SliderField(
        label = stringResource(R.string.hold_strength), value = holdPosition(st).toFloat(), range = 0f..21f, steps = 20,
        format = { v -> val i = v.roundToInt(); if (i == 0) ctx.getString(R.string.off) else ctx.getString(R.string.percent, (i - 1) * 5) },
        minLabel = stringResource(R.string.off), maxLabel = "100%",
        description = stringResource(R.string.hold_desc),
        onCommit = { v ->
            val i = v.roundToInt()
            val p = if (i == 0) -1 else (i - 1) * 5
            vm.edit(ctx.getString(R.string.saved_hold, if (p < 0) ctx.getString(R.string.off) else ctx.getString(R.string.percent, p))) { cfg, d, _ -> cfg.uclampMin = p; d }
        },
    )
    val on = st.effective.jankBoostSteps?.isNotEmpty() == true
    SwitchRow(
        stringResource(R.string.stutter_boost), stringResource(R.string.stutter_desc), on,
        { v ->
            vm.edit(ctx.getString(R.string.saved_stutter, ctx.getString(if (v) R.string.on else R.string.off))) { cfg, d, _ ->
                cfg.jankBoost = if (v) 1 else 0
                cfg.jankBoostSteps = if (v) intArrayOf(3, 3, 3) else IntArray(0)
                d
            }
        },
        padding = PaddingValues(0.dp),
    )
}

@Composable
private fun DisplayContent(vm: AppViewModel, st: TuningState) {
    val ctx = LocalContext.current
    val d0 = st.display
    SliderField(
        label = stringResource(R.string.render_resolution), value = d0.resolution, range = 0.5f..1f, steps = 9,
        format = { formatScale((it * 20).roundToInt() / 20f) }, enabled = st.allow.downscaling,
        description = stringResource(if (st.allow.downscaling) R.string.render_desc else R.string.render_blocked),
        onCommit = { v ->
            val r = (v * 20).roundToInt() / 20f
            vm.edit(ctx.getString(R.string.saved_render, formatScale(r))) { _, d, _ -> d.copy(resolution = r) }
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fps_limit), style = MaterialTheme.typography.bodyLarge)
        ConnectedChoice(
            FPS_LIMITS.map { it to ctx.fpsName(it) }, d0.fpsLimit,
            { v -> vm.edit(ctx.getString(R.string.saved_fps, ctx.fpsName(v))) { _, d, _ -> d.copy(fpsLimit = v) } },
            enabled = st.allow.fpsOverride,
        )
        Muted(stringResource(if (st.allow.fpsOverride) R.string.fps_desc else R.string.fps_blocked))
    }
    SwitchRow(
        stringResource(R.string.lock_refresh), stringResource(R.string.lock_refresh_desc), d0.lockRefresh,
        { v -> vm.edit(ctx.getString(R.string.saved_lock, ctx.getString(if (v) R.string.on else R.string.off))) { _, d, _ -> d.copy(lockRefresh = v) } },
        padding = PaddingValues(0.dp), minHeight = 48.dp,
    )
}

// endregion

// region Advanced and not-saved

@Composable
private fun AdvancedContent(vm: AppViewModel, nav: Nav, st: TuningState) {
    val ctx = LocalContext.current
    val e = st.effective
    var dialog by remember { mutableStateOf("") }
    val noPad = PaddingValues(0.dp)
    SwitchRow(
        stringResource(R.string.cores_awake), stringResource(R.string.cores_desc), e.keepCoresOnline,
        { v ->
            vm.edit(ctx.getString(R.string.saved_cores, ctx.getString(if (v) R.string.on else R.string.off))) { cfg, d, _ -> cfg.keepCoresOnline = if (v) 1 else 0; d }
        },
        padding = noPad,
    )
    val gpuLadder = st.limits.ladder(2)
    val idle = if (e.gpuDefaultHz < 0) stringResource(R.string.off) else Freq.mhz(e.gpuDefaultHz)
    NavRow(stringResource(R.string.gpu_idle), stringResource(R.string.gpu_idle_desc), { dialog = "gpu" }, value = idle, padding = noPad)
    SliderField(
        label = stringResource(R.string.wait_after_launch), value = (e.startDelayMs / 1000f).coerceIn(0f, 10f), range = 0f..10f, steps = 9,
        format = { ctx.getString(R.string.seconds_value, it.roundToInt()) }, minLabel = "0 s", maxLabel = "10 s",
        description = stringResource(R.string.wait_desc),
        onCommit = { v ->
            val n = v.roundToInt()
            vm.edit(ctx.getString(R.string.saved_wait, n)) { cfg, d, _ -> cfg.startDelayMs = n * 1000; d }
        },
    )
    val threadName = stringResource(
        when (e.threadRules) { 0 -> R.string.threads_off; 2 -> R.string.threads_base; 3 -> R.string.threads_custom; else -> R.string.threads_own },
    )
    NavRow(stringResource(R.string.thread_placement), null, { dialog = "threads" }, value = threadName, padding = noPad, minHeight = 56.dp)
    NavRow(
        stringResource(R.string.edit_by_temp), null,
        { nav.push(Route.Steps(st.pkg, st.mode)) },
        value = pluralStringResource(R.plurals.steps_count, e.steps?.size ?: 0, e.steps?.size ?: 0), padding = noPad, minHeight = 56.dp,
    )

    if (dialog == "gpu") {
        val opts = listOf(0 to ctx.getString(R.string.off)) + gpuLadder.mapIndexed { i, hz -> (i + 1) to Freq.mhz(hz) }
        val cur = if (e.gpuDefaultHz < 0) 0 else gpuLadder.indexOfFirst { it == e.gpuDefaultHz }.let { if (it < 0) null else it + 1 }
        ChoiceDialog(stringResource(R.string.gpu_idle), opts, cur, { v ->
            val hz = if (v == 0) -1 else gpuLadder[v - 1]
            vm.edit(ctx.getString(R.string.saved_gpu_idle, if (v == 0) ctx.getString(R.string.off) else Freq.mhz(hz))) { cfg, d, _ -> cfg.gpuDefaultHz = hz; d }
        }, { dialog = "" })
    }
    if (dialog == "threads") {
        val opts = buildList {
            add(0 to ctx.getString(R.string.threads_off)); add(1 to ctx.getString(R.string.threads_own)); add(2 to ctx.getString(R.string.threads_base))
            if (st.config.threadRules == 3 || st.config.customThreads.isNotEmpty()) add(3 to ctx.getString(R.string.threads_custom))
        }
        ChoiceDialog(stringResource(R.string.thread_placement), opts, e.threadRules, { v ->
            vm.edit(ctx.getString(R.string.saved_threads)) { cfg, d, _ -> cfg.threadRules = v; d }
        }, { dialog = "" })
    }
}

@Composable
private fun UnsavedContent(vm: AppViewModel, st: TuningState, wide: Boolean) {
    if (wide) {
        // The switch and the thresholds sit right in the pane.
        val ctx = LocalContext.current
        val pct = st.globalsLowBattery
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
            SwitchRow(
                stringResource(R.string.low_battery), stringResource(R.string.low_battery_desc), pct > 0,
                { on -> vm.setLowBattery(if (on) 20 else 0) },
                minHeight = 80.dp, padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
            ConnectedChoice(
                (10..40 step 5).map { it to ctx.getString(R.string.percent, it) }, pct.takeIf { it in 10..40 },
                { vm.setLowBattery(it) }, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).fillMaxWidth(), enabled = pct > 0,
            )
        }
        return
    }
    var picker by remember { mutableStateOf(false) }
    val pad = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
    val body: @Composable () -> Unit = {
        NavRow(
            stringResource(R.string.low_battery), stringResource(R.string.low_battery_desc), { picker = true },
            value = if (st.globalsLowBattery == 0) stringResource(R.string.off) else stringResource(R.string.below_percent, st.globalsLowBattery),
            padding = pad, minHeight = 80.dp,
        )
    }
    body()
    if (picker) {
        val ctx = LocalContext.current
        val opts = listOf(0 to ctx.getString(R.string.off)) + (10..50 step 5).map { it to ctx.getString(R.string.below_percent, it) }
        ChoiceDialog(stringResource(R.string.low_battery), opts, st.globalsLowBattery, { vm.setLowBattery(it) }, { picker = false })
    }
}

// endregion
