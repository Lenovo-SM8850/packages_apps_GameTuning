// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.DisplayValues
import io.github.miner7222.gametuning.data.Freq
import io.github.miner7222.gametuning.data.GraphicsValues
import io.github.miner7222.gametuning.data.ModeLibrary
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.data.Preset
import io.github.miner7222.gametuning.data.Resource
import io.github.miner7222.gametuning.data.coolRange
import io.github.miner7222.gametuning.data.builtinName
import io.github.miner7222.gametuning.data.heatName
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.shortSummary
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.PolicyProfile

private val MODE_ICON_SIZE = 18.dp
private val MODE_ICON_GAP = 8.dp

/** The glyphs Game Space uses for the same three profiles. */
private fun modeIcon(mode: Int) = when (mode) {
    Modes.BATTERY -> Icons.Rounded.EnergySavingsLeaf
    Modes.PERFORMANCE -> Icons.Rounded.ElectricBolt
    else -> Icons.Rounded.SportsEsports
}

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

/** One speed range of a profile, such as "0.79–2.00 GHz", or "-" when the device has no such step. */
fun speedText(limits: PolicyLimits, profile: PolicyProfile, r: Int): String {
    val ladder = limits.ladder(r)
    if (ladder.isEmpty()) return "-"
    val (lo, hi) = profile.coolRange(limits, r)
    return Freq.range(r, ladder[lo], ladder[hi])
}

/** The game list after the search text and the filter chip (0 all, 1 adjusted, 2 recording on). */
fun filterCards(cards: List<GameCard>, query: String, filter: Int): List<GameCard> = cards.filter { c ->
    c.label.contains(query.trim(), ignoreCase = true) &&
        when (filter) { 1 -> c.adjusted; 2 -> c.recording; else -> true }
}

// region game list

@Composable
fun GameListPane(
    vm: AppViewModel, wide: Boolean, selectedPkg: String?, onPick: (String) -> Unit,
    query: String, onQuery: (String) -> Unit, filter: Int, onFilter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val filtered = filterCards(vm.cards, query, filter)
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = navBottomPadding() + if (wide) 32.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(Modifier.padding(start = 8.dp, top = if (wide) 16.dp else 40.dp, bottom = 16.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wide) Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                else Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                SearchField(query, onQuery)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(
                        R.string.filter_all to 0, R.string.filter_adjusted to R.string.filter_adjusted_short,
                        R.string.filter_recording to R.string.filter_recording_short,
                    )
                    labels.forEachIndexed { i, (r, short) ->
                        FilterChip(selected = filter == i, onClick = { onFilter(i) }, label = { FitLabel(stringResource(r), shortLabelResource(short)) })
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
            item {
                if (wide) {
                    TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                        Muted(stringResource(R.string.no_matches), Modifier.padding(20.dp), MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.no_matches), Modifier.fillMaxWidth())
                    }
                }
            }
        }
        itemsIndexed(filtered, key = { _, c -> c.pkg }) { i, c ->
            GameCardRow(vm, c, groupShape(i, filtered.size), wide, c.pkg == selectedPkg) { onPick(c.pkg) }
        }
        item {
            val intent = remember { vm.games.gameSpaceIntent() }
            if (intent != null) {
                TextButton(
                    // Reuse the library already open in its task instead of stacking another one.
                    onClick = {
                        context.startActivity(Intent(intent).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    },
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
    val preset = c.presetName ?: ctx.builtinName(c.dedicated)
    val cardShape = if (wide) RoundedCornerShape(if (selected) 28.dp else 16.dp) else shape
    Surface(
        shape = cardShape, color = container,
        contentColor = if (wide && selected) cs.onPrimaryContainer else cs.onSurface,
        modifier = Modifier.fillMaxWidth().clip(cardShape).selectable(selected = selected && wide, role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameIcon(vm, c.pkg, c.label, if (wide) 48.dp else 56.dp, selected && wide)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(ctx.modeName(c.mode))
                    if (c.mode != Modes.NONE) {
                        Text(preset, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = if (wide && selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
    val lib = ps.libs[ps.mode] ?: ModeLibrary(emptyList(), null)
    val current = lib.inUse
    val activeDisplay = current?.display ?: DisplayValues.builtin(ps.mode)
    val fpsState = if (activeDisplay.fpsLimit > 0) stringResource(R.string.fps_value, activeDisplay.fpsLimit) else stringResource(R.string.no_limit)
    val graphicsState = stringResource(if (activeDisplay.graphics == GraphicsValues()) R.string.graphics_default else R.string.filter_adjusted)
    var expanded by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun header() {
        Row(Modifier.padding(horizontal = if (wide) 0.dp else 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameIcon(vm, pkg, ps.label, if (wide) 56.dp else 64.dp, selected = true)
            Column(Modifier.weight(1f)) {
                Text(ps.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    fun modeCards() {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Icons alone only on a screen under 340 dp wide (this row sits inside two 16 dp gutters) or with large text.
            val iconOnly = maxWidth + 32.dp < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in Modes.ALL) {
                    val on = m == ps.mode
                    val name = ctx.modeName(m)
                    val radius by animateDpAsState(if (on) 28.dp else 16.dp, label = "radius")
                    val cardShape = RoundedCornerShape(radius)
                    Surface(
                        shape = cardShape,
                        color = if (on) cs.primary else cs.surfaceContainer,
                        contentColor = if (on) cs.onPrimary else cs.onSurface,
                        modifier = Modifier.weight(1f).clip(cardShape).selectable(selected = on, enabled = ps.tuningEnabled, role = Role.RadioButton) { vm.setMode(pkg, m) },
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp).alpha(if (ps.tuningEnabled) 1f else 0.38f),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(modeIcon(m), if (iconOnly) name else null, Modifier.size(MODE_ICON_SIZE))
                            if (!iconOnly) {
                                Spacer(Modifier.width(MODE_ICON_GAP))
                                FitLabel(
                                    name, modeShortLabel(m), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
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
                val rowShape = RoundedCornerShape(20.dp)
                Surface(
                    shape = rowShape, color = if (on) cs.primaryContainer else cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clip(rowShape).selectable(selected = on, role = Role.RadioButton) {
                        if (!on) vm.usePreset(pkg, ps.mode, p?.id)
                    },
                ) {
                    Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = on, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(p?.name ?: ctx.builtinName(ps.dedicated), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(current?.name ?: ctx.builtinName(ps.dedicated), Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
                        Muted(stringResource(R.string.preset_in_use_label))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(current?.name ?: ctx.builtinName(ps.dedicated), Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
        val eff = ps.effective ?: return
        fun range(r: Int): String {
            val ladder = limits.ladder(r)
            if (ladder.isEmpty()) return "-"
            val (lo, hi) = eff.coolRange(limits, r)
            return Freq.range(r, ladder[lo], ladder[hi])
        }
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp), if (wide) cs.surfaceContainer else cs.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = if (wide) 20.dp else 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.now_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val pillShape = RoundedCornerShape(24.dp)
                    Surface(
                        shape = pillShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer,
                        modifier = Modifier.clip(pillShape).clickable { nav.push(Route.Tuning(pkg, ps.mode)) },
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
                        ValueCell(stringResource(R.string.graphics), graphicsState)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ValueCell(stringResource(R.string.res_fastest), range(Resource.PRIME))
                        ValueCell(stringResource(R.string.heat_limit), ctx.heatName(eff.thermalCase))
                        ValueCell(stringResource(R.string.fps_limit), fpsState)
                    }
                }
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

    val recCount = ps.recordings.size
    val recText = stringResource(R.string.game_chip, stringResource(if (ps.armed) R.string.on else R.string.off), recCount.toString())

    @Composable
    fun moreRows() {
        val rows = buildList<@Composable (Dp) -> Unit> {
            if (!wide && ps.tuningEnabled) add { h ->
                NavRow(stringResource(R.string.tuning_details), null, { nav.push(Route.Tuning(pkg, ps.mode)) }, icon = Icons.Rounded.Tune, minHeight = h)
            }
            add { h -> NavRow(stringResource(R.string.records_title), recText, { nav.push(Route.Records(pkg)) }, icon = if (wide) null else Icons.Rounded.BarChart, minHeight = h) }
            if (ps.tuningEnabled) {
                add { h -> NavRow(stringResource(R.string.import_title), null, { nav.push(Route.Import(pkg, ps.mode)) }, icon = if (wide) null else Icons.Rounded.Download, minHeight = h) }
            }
        }
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
                row(56.dp)
            }
        }
    }

    /** Stacked detail: the game, with the preset list button at its right. */
    @Composable
    fun stackedHeader() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameIcon(vm, pkg, ps.label, 48.dp, selected = true)
            Column(Modifier.weight(1f)) {
                Text(
                    ps.label, style = MaterialTheme.typography.titleLarge, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = { nav.push(Route.Presets(pkg, ps.mode)) }, Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.manage_presets))
            }
        }
    }

    /** Stacked detail: the preset in use and what the game gets now, in one card. */
    @Composable
    fun presetAndNowCard() {
        val eff = ps.effective
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current?.name ?: ctx.builtinName(ps.dedicated), Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (ps.tuningEnabled) TextButton(onClick = { nav.push(Route.Tuning(pkg, ps.mode)) }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.edit_tuning), Modifier.padding(start = 8.dp))
                }
            }
            if (eff != null) {
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = cs.outlineVariant)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValueCell(stringResource(R.string.res_cpu), speedText(ps.limits, eff, Resource.GOLD), Modifier.weight(1f))
                    ValueCell(stringResource(R.string.res_fastest), speedText(ps.limits, eff, Resource.PRIME), Modifier.weight(1f), shortLabelResource(R.string.res_fastest_short))
                    ValueCell(stringResource(R.string.res_graphics), speedText(ps.limits, eff, Resource.GPU), Modifier.weight(1f))
                }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ValueCell(
                        stringResource(R.string.heat_limit), ctx.heatName(eff.thermalCase), Modifier.weight(1f),
                        shortLabelResource(R.string.heat_limit_short), heatShortLabel(eff.thermalCase),
                    )
                    ValueCell(stringResource(R.string.graphics), graphicsState, Modifier.weight(1f))
                    ValueCell(stringResource(R.string.fps_limit), fpsState, Modifier.weight(1f))
                }
            }
        }
    }

    /** Stacked detail: records and import side by side. */
    @Composable
    fun navTiles() {
        val tiles = buildList<Triple<String, String?, () -> Unit>> {
            add(Triple(stringResource(R.string.records_title), recText) { nav.push(Route.Records(pkg)) })
            if (ps.tuningEnabled) {
                add(Triple(stringResource(R.string.import_title), null) { nav.push(Route.Import(pkg, ps.mode)) })
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEachIndexed { i, (title, sub, open) ->
                val start = if (i == 0) 20.dp else 8.dp
                val end = if (i == tiles.lastIndex) 20.dp else 8.dp
                val tileShape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
                Surface(
                    shape = tileShape,
                    color = cs.surfaceContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(tileShape).clickable(onClick = open),
                ) {
                    Column(Modifier.heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (sub != null) Muted(sub, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier) {
        // A detail pane under 840 dp stacks its parts instead of using two columns.
        val stacked = wide && maxWidth < 840.dp
        if (stacked) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                stackedHeader()
                modeCards()
                if (ps.tuningEnabled) presetAndNowCard()
                notice()
                navTiles()
            }
        } else if (wide) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                header()
                modeCards()
                if (!ps.tuningEnabled) {
                    notice()
                    moreRows()
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.weight(1f)) { presetCard() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        nowCard(); notice(); moreRows()
                    }
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = navBottomPadding() + 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                SectionLabel(stringResource(R.string.profile))
                modeCards()
                if (ps.tuningEnabled) presetCard()
                notice()
                nowCard()
                moreRows()
            }
        }
    }
}

@Composable
private fun ValueCell(label: String, value: String, modifier: Modifier = Modifier, shortLabel: String? = null, shortValue: String? = null) {
    Column(modifier) {
        if (shortLabel != null) FitLabel(label, shortLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        else Muted(label, style = MaterialTheme.typography.bodyMedium)
        if (shortValue != null) FitLabel(value, shortValue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2)
        else Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

// endregion
