// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.DisplayValues
import io.github.miner7222.gametuning.data.Freq
import io.github.miner7222.gametuning.data.ModeLibrary
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.data.Preset
import io.github.miner7222.gametuning.data.Resource
import io.github.miner7222.gametuning.data.coolRange
import io.github.miner7222.gametuning.data.heatName
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.shortSummary

/** What a preset row says under its name. */
@Composable
fun presetDiff(ps: ProfileState, vm: AppViewModel, mode: Int, preset: Preset?): String {
    val ctx = LocalContext.current
    if (preset == null) return stringResource(R.string.builtin_diff)
    val thermal = ps.builtins[mode]?.thermalCase ?: 200
    return ctx.shortSummary(preset.config, preset.display, thermal, DisplayValues.builtin(mode), vm.tf)
}

@Composable
fun GameIcon(vm: AppViewModel, pkg: String, label: String, size: Dp, selected: Boolean = false) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg) { value = vm.games.icon(pkg, px) }
    GameAvatar(label, icon, size, selected)
}

// region game list

@Composable
fun GameListPane(
    vm: AppViewModel, wide: Boolean, selectedPkg: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    val filtered = vm.cards.filter { c ->
        c.label.contains(query.trim(), ignoreCase = true) &&
            when (filter) { 1 -> c.adjusted; 2 -> c.recording; else -> true }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(Modifier.padding(start = 8.dp, top = if (wide) 16.dp else 40.dp, bottom = 16.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_name), style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_games)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(R.string.filter_all, R.string.filter_adjusted, R.string.filter_recording)
                    labels.forEachIndexed { i, r ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(stringResource(r)) })
                    }
                }
            }
        }
        if (vm.cards.isEmpty()) {
            item {
                TonalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.no_games), style = MaterialTheme.typography.titleMedium)
                        Muted(stringResource(R.string.no_games_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            item { Muted(stringResource(R.string.no_matches), Modifier.padding(16.dp), MaterialTheme.typography.bodyMedium) }
        }
        itemsIndexed(filtered, key = { _, c -> c.pkg }) { i, c ->
            GameCardRow(vm, c, groupShape(i, filtered.size), wide, c.pkg == selectedPkg) { onPick(c.pkg) }
        }
        item {
            val intent = remember { vm.games.gameSpaceIntent() }
            if (intent != null) {
                TextButton(
                    onClick = { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.manage_games), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (!wide && !vm.tipDismissed && vm.cards.isNotEmpty()) {
            item {
                TonalCard(Modifier.padding(top = 8.dp).fillMaxWidth(), RoundedCornerShape(20.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.tip_title), style = MaterialTheme.typography.titleSmall)
                            Muted(stringResource(R.string.tip_body), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { vm.dismissTip() }, Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.dismiss_tip))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCardRow(
    vm: AppViewModel, c: GameCard, shape: androidx.compose.ui.graphics.Shape, wide: Boolean, selected: Boolean, onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val container = if (wide) (if (selected) cs.primaryContainer else Color.Transparent) else cs.surfaceContainer
    val preset = c.presetName ?: stringResource(R.string.builtin_setting)
    Surface(
        shape = if (wide) RoundedCornerShape(if (selected) 28.dp else 16.dp) else shape, color = container,
        contentColor = if (wide && selected) cs.onPrimaryContainer else cs.onSurface,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected && wide, role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameIcon(vm, c.pkg, c.label, if (wide) 48.dp else 56.dp, selected && wide)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chip = stringResource(R.string.game_chip, ctx.modeName(c.mode), preset)
                    if (wide) Muted(chip, style = MaterialTheme.typography.bodySmall)
                    else Tag(chip, Modifier.weight(1f, fill = false))
                    if (c.recording && !wide) {
                        Tag(stringResource(R.string.recording_on), container = cs.tertiaryContainer, content = cs.onTertiaryContainer, leadingDot = true)
                    }
                }
            }
            if (wide && c.recording) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cs.tertiary))
            }
            if (!wide) Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

// endregion

// region profile

@Composable
fun ProfilePane(
    vm: AppViewModel, pkg: String, wide: Boolean, nav: Nav, modifier: Modifier = Modifier,
) {
    val ps = vm.profile?.takeIf { it.pkg == pkg }
    if (ps == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val lib = ps.libs[ps.mode]!!
    val current = lib.inUse
    var expanded by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun header() {
        Row(Modifier.padding(horizontal = if (wide) 0.dp else 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameIcon(vm, pkg, ps.label, if (wide) 56.dp else 64.dp, selected = true)
            Column(Modifier.weight(1f)) {
                Text(ps.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    fun modeCards() {
        Row(Modifier.selectableGroup().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Modes.ALL) {
                val on = m == ps.mode
                val radius by animateDpAsState(if (on) (if (wide) 28.dp else 32.dp) else (if (wide) 16.dp else 20.dp), label = "radius")
                Surface(
                    shape = RoundedCornerShape(radius),
                    color = if (on) cs.primary else cs.surfaceContainer,
                    contentColor = if (on) cs.onPrimary else cs.onSurface,
                    modifier = Modifier.weight(1f).fillMaxSize().selectable(selected = on, role = Role.RadioButton) { vm.setMode(pkg, m) },
                ) {
                    Column(Modifier.padding(if (wide) 14.dp else 16.dp).heightIn(min = if (wide) 88.dp else 104.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ctx.modeName(m), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (m == Modes.PERFORMANCE) Text(stringResource(R.string.mode_performance_hint), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Tag(
                            ps.libs[m]!!.inUse?.name ?: stringResource(R.string.builtin_setting),
                            container = if (on) cs.primaryContainer else cs.surfaceContainerHigh,
                            content = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun presetRadioList() {
        val all: List<Preset?> = listOf<Preset?>(null) + lib.presets
        Column(Modifier.selectableGroup().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            all.forEach { p ->
                val on = (p?.id) == lib.inUseId
                Surface(
                    shape = RoundedCornerShape(20.dp), color = if (on) cs.primaryContainer else cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().selectable(selected = on, role = Role.RadioButton) {
                        if (!on) vm.usePreset(pkg, ps.mode, p?.id)
                    },
                ) {
                    Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = on, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(p?.name ?: stringResource(R.string.builtin_setting), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Muted(presetDiff(ps, vm, ps.mode, p), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun presetCard() {
        TonalCard(Modifier.fillMaxWidth()) {
            if (wide) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.preset_in_use_label, ctx.modeName(ps.mode)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = cs.primary)
                    TextButton(onClick = { nav.push(Route.Presets(pkg, ps.mode)) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manage)) }
                }
                presetRadioList()
                Spacer(Modifier.height(8.dp))
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable { expanded = !expanded }.padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Muted(stringResource(R.string.preset_in_use_label, ctx.modeName(ps.mode)))
                        Text(current?.name ?: stringResource(R.string.builtin_setting), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Muted(presetDiff(ps, vm, ps.mode, current), style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                        Row(Modifier.height(40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge)
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                        }
                    }
                }
                if (expanded) {
                    presetRadioList()
                    TextButton(onClick = { nav.push(Route.Presets(pkg, ps.mode)) }, Modifier.padding(start = 12.dp, bottom = 8.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.manage_presets))
                    }
                }
            }
        }
    }

    @Composable
    fun nowCard() {
        val limits = ps.limits
        val eff = ps.effective
        fun range(r: Int): String {
            val ladder = limits.ladder(r)
            if (ladder.isEmpty()) return "-"
            val (lo, hi) = eff.coolRange(limits, r)
            return Freq.range(r, ladder[lo], ladder[hi])
        }
        val fps = current?.display?.fpsLimit ?: 0
        val heat = ctx.heatName(eff.thermalCase) + " · " + if (fps > 0) stringResource(R.string.fps_value, fps) else stringResource(R.string.no_limit)
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp), if (wide) cs.surfaceContainer else cs.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = if (wide) 20.dp else 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.now_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(24.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer,
                        modifier = Modifier.clickable { nav.push(Route.Tuning(pkg, ps.mode)) },
                    ) {
                        Row(Modifier.height(48.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.edit_tuning), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pair(stringResource(R.string.res_cpu), range(Resource.GOLD)).let { ValueCell(it.first, it.second) }
                        Pair(stringResource(R.string.res_graphics), range(Resource.GPU)).let { ValueCell(it.first, it.second) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ValueCell(stringResource(R.string.res_fastest), range(Resource.PRIME))
                        ValueCell(stringResource(if (wide) R.string.heat_frame_short else R.string.heat_frame), heat)
                    }
                }
                if (!wide) Muted(stringResource(R.string.now_note))
            }
        }
    }

    @Composable
    fun notice() {
        val text = ps.notice ?: return
        Surface(shape = RoundedCornerShape(20.dp), color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(text, Modifier.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }

    @Composable
    fun moreRows() {
        val recCount = ps.recordings.size
        val countText = pluralStringResource(R.plurals.records_count, recCount, recCount)
        val recText = when {
            ps.armed -> stringResource(if (wide) R.string.recording_on_count else R.string.next_play_count, countText)
            else -> countText
        }
        val rows = buildList<@Composable (Dp) -> Unit> {
            if (!wide) add { h ->
                NavRow(stringResource(R.string.tuning_details), null, { nav.push(Route.Tuning(pkg, ps.mode)) }, icon = Icons.Rounded.Tune, minHeight = h)
            }
            add { h -> NavRow(stringResource(R.string.records_title), recText, { nav.push(Route.Records(pkg)) }, icon = if (wide) null else Icons.Rounded.BarChart, minHeight = h) }
            add { h -> NavRow(stringResource(R.string.import_title), null, { nav.push(Route.Import(pkg, ps.mode)) }, icon = if (wide) null else Icons.Rounded.Download, minHeight = h) }
            add { h -> NavRow(stringResource(R.string.expert_title), stringResource(R.string.expert_sub), { nav.push(Route.Expert(pkg, ps.mode)) }, icon = if (wide) null else Icons.Rounded.Code, minHeight = h) }
        }
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
                row(56.dp)
            }
        }
    }

    if (wide) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            header()
            modeCards()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) { presetCard() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    nowCard(); notice(); moreRows()
                }
            }
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header()
            SectionLabel(stringResource(R.string.profile))
            modeCards()
            presetCard()
            notice()
            nowCard()
            moreRows()
        }
    }
}

@Composable
private fun ValueCell(label: String, value: String) {
    Column {
        Muted(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

// endregion
