// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.Change
import io.github.miner7222.gametuning.data.MAX_PRESETS
import io.github.miner7222.gametuning.data.customCount
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.data.Preset
import io.github.miner7222.gametuning.data.Resource
import io.github.miner7222.gametuning.data.formatFloat1
import io.github.miner7222.gametuning.data.formatPower
import io.github.miner7222.gametuning.data.builtinName
import io.github.miner7222.gametuning.data.heatName
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.resourceName
import vendor.lenovo.hardware.gametuning.RecordingInfo

private const val BUILTIN = ""

/** "Last record: 112.6 fps - 42.1 C - 7.4 W" for the newest record made with a preset. */
@Composable
private fun lastRecordText(vm: AppViewModel, ps: ProfileState, mode: Int, id: String?): String {
    val rec = vm.recordsWith(ps, mode, id).firstOrNull() ?: return stringResource(R.string.no_records_yet)
    return recordLine(vm, rec)
}

@Composable
private fun recordLine(vm: AppViewModel, rec: RecordingInfo): String {
    val sum = rec.summary
    val parts = ArrayList<String>()
    parts.add(stringResource(R.string.fps_avg_value, formatFloat1(sum.avgFps.toDouble())))
    if (sum.maxSkinC > 0f) parts.add(vm.tf.absolute(sum.maxSkinC.toDouble()))
    parts.add(if (sum.avgPowerMw >= 0) formatPower(sum.avgPowerMw) else stringResource(R.string.charging))
    return stringResource(R.string.last_record, parts.joinToString(" · "))
}

/** "In use" on the profile the game runs; "Selected" (outlined) on any other profile. */
@Composable
private fun InUseTag(onGameProfile: Boolean) {
    val cs = MaterialTheme.colorScheme
    if (onGameProfile) Tag(stringResource(R.string.in_use), container = cs.primary, content = cs.onPrimary)
    else Tag(stringResource(R.string.preset_selected), container = Color.Transparent, content = cs.onSurfaceVariant, outlined = true)
}

@Composable
fun PresetsScreen(vm: AppViewModel, nav: Nav, pkg: String, startMode: Int, wide: Boolean) {
    val ps = vm.profile?.takeIf { it.pkg == pkg }
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(startMode) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var newSheet by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    if (ps == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        return
    }
    val lib = ps.libs[mode]!!
    val entries: List<Preset?> = listOf<Preset?>(null) + lib.presets
    val count = lib.presets.customCount() + 1
    val selId = selected?.takeIf { s -> entries.any { (it?.id ?: BUILTIN) == s } } ?: (lib.inUseId ?: BUILTIN)
    val sel = entries.firstOrNull { (it?.id ?: BUILTIN) == selId }
    val tabs = Modes.ALL.map { m -> m to ctx.modeName(m) + " · " + (ps.libs[m]!!.presets.customCount() + 1) }

    val subtitle = stringResource(R.string.presets_sub, ps.label, count, MAX_PRESETS, ctx.modeName(mode))

    fun open(p: Preset?) {
        if (wide) selected = p?.id ?: BUILTIN else menuFor = p?.id ?: BUILTIN
    }

    Column(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TopBar(stringResource(R.string.presets_title), subtitle, { nav.pop() }) }
                ConnectedChoice(tabs, mode, { mode = it; selected = null }, Modifier.width(480.dp))
            }
        } else {
            TopBar(stringResource(R.string.presets_title), subtitle, { nav.pop() })
            ConnectedChoice(tabs, mode, { mode = it; selected = null }, Modifier.padding(horizontal = 16.dp).widthIn(max = 640.dp).fillMaxWidth())
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entries.forEachIndexed { i, p ->
                        val on = (p?.id ?: BUILTIN) == selId
                        val itemShape = if (on) RoundedCornerShape(28.dp) else groupShape(i, count, 24.dp)
                        Surface(
                            shape = itemShape,
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(selected = on, role = Role.Button) { open(p) },
                        ) {
                            Row(Modifier.heightIn(min = 80.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(p?.name ?: ctx.builtinName(ps.dedicated), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Muted(presetDiff(ps, vm, mode, p))
                                }
                                if ((p?.id) == lib.inUseId) InUseTag(mode == ps.mode)
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = { newSheet = true }, enabled = count < MAX_PRESETS,
                        modifier = Modifier.padding(top = 12.dp).heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.new_preset), Modifier.padding(start = 12.dp))
                    }
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    PresetDetail(vm, nav, ps, mode, sel, lib.inUseId, onDelete = { deleteId = it })
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
                        .widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entries.forEachIndexed { i, p ->
                            val on = (p?.id) == lib.inUseId
                            Surface(
                                shape = groupShape(i, count),
                                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Row(
                                        Modifier.weight(1f).heightIn(min = 96.dp)
                                            .selectable(selected = on, role = Role.RadioButton) { if (!on) vm.usePreset(pkg, mode, p?.id) }
                                            .padding(start = 20.dp, top = 16.dp, bottom = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        RadioButton(selected = on, onClick = null, modifier = Modifier.padding(top = 2.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(p?.name ?: ctx.builtinName(ps.dedicated), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                if (on) InUseTag(mode == ps.mode)
                                            }
                                            Muted(presetDiff(ps, vm, mode, p), style = MaterialTheme.typography.bodyMedium)
                                            if (p == null) builtinLine(ps, mode)?.let { Muted(it) }
                                            Muted(lastRecordText(vm, ps, mode, p?.id))
                                        }
                                    }
                                    IconButton(onClick = { open(p) }, Modifier.padding(top = 12.dp, end = 8.dp).size(48.dp)) {
                                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_for, p?.name ?: ctx.builtinName(ps.dedicated)))
                                    }
                                }
                            }
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { if (count < MAX_PRESETS) newSheet = true else Unit },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.new_preset), Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    if (newSheet) NewPresetSheet(vm, pkg, mode, lib.inUse, ps.dedicated, wide) { newSheet = false }

    val menuPreset = menuFor?.let { id -> entries.firstOrNull { (it?.id ?: BUILTIN) == id } }
    if (!wide && menuFor != null) {
        PresetActionsSheet(vm, nav, ps, mode, menuPreset, lib.inUseId, onDelete = { deleteId = it }) { menuFor = null }
    }
    val del = deleteId?.let { id -> lib.presets.firstOrNull { it.id == id } }
    if (del != null) {
        ConfirmDialog(
            stringResource(R.string.delete_preset_q, del.name),
            stringResource(if (del.id == lib.inUseId) R.string.delete_inuse_body else R.string.delete_body, ctx.modeName(mode)),
            stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.deletePreset(pkg, mode, del.id); menuFor = null; selected = null },
            onDismiss = { deleteId = null },
        )
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(64)) }, singleLine = true, enabled = enabled,
        label = { Text(stringResource(R.string.name)) }, modifier = modifier,
    )
}

@Composable
private fun NewPresetSheet(vm: AppViewModel, pkg: String, mode: Int, inUse: Preset?, dedicated: Boolean, wide: Boolean, onClose: () -> Unit) {
    var fromBuiltin by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val builtinSeed = stringResource(R.string.builtin_setting)
    val base = if (inUse == null || fromBuiltin) builtinSeed else inUse.name
    val suggested = vm.uniqueName(vm.store.list(pkg, mode), stringResource(R.string.name_copy, base))
    var name by rememberSaveable(base) { mutableStateOf(suggested) }
    val options = buildList {
        if (inUse != null) add(false to stringResource(R.string.copy_of_in_use, inUse.name))
        add(true to if (inUse == null) stringResource(R.string.builtin_in_use) else ctx.builtinName(dedicated))
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.new_preset), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SectionLabel(stringResource(R.string.start_from), Modifier.padding(0.dp))
            Column(Modifier.selectableGroup()) {
                options.forEach { (b, label) ->
                    val on = (inUse == null) || fromBuiltin == b
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(on, role = Role.RadioButton) { fromBuiltin = b },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) { RadioButton(selected = on, onClick = null); Text(label) }
                }
            }
            NameField(name, { name = it }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { vm.createPreset(pkg, mode, name, fromBuiltin || inUse == null); onClose() }, Modifier.heightIn(min = 48.dp), enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.create))
                }
            }
        }
    }
    if (wide) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 480.dp)) { body() }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.padding(bottom = 16.dp)) { body() }
        }
    }
}

/** Portrait board VariantActions: the actions of one preset in a bottom sheet. */
@Composable
private fun PresetActionsSheet(
    vm: AppViewModel, nav: Nav, ps: ProfileState, mode: Int, preset: Preset?, inUseId: String?,
    onDelete: (String) -> Unit, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val inUse = (preset?.id) == inUseId
    var name by rememberSaveable(preset?.id) { mutableStateOf(preset?.name.orEmpty()) }
    val records = vm.recordsWith(ps, mode, preset?.id).size
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(preset?.name ?: ctx.builtinName(ps.dedicated), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Muted(
                    ctx.modeName(mode) + if (inUse) " · " + stringResource(if (mode == ps.mode) R.string.in_use else R.string.preset_selected) else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (preset != null) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NameField(name, { name = it }, Modifier.weight(1f))
                    FilledTonalIconButtonSafe(stringResource(R.string.save_name), enabled = name.isNotBlank() && name != preset.name) {
                        vm.renamePreset(ps.pkg, mode, preset.id, name); onClose()
                    }
                }
            }
            ActionRow(Icons.Rounded.Tune, stringResource(if (inUse) R.string.edit_tuning else R.string.use_and_edit), null) {
                onClose()
                if (inUse) nav.push(Route.Tuning(ps.pkg, mode)) else vm.usePreset(ps.pkg, mode, preset?.id) { nav.push(Route.Tuning(ps.pkg, mode)) }
            }
            if (preset != null) {
                ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.duplicate), stringResource(R.string.duplicate_note, stringResource(R.string.name_copy, preset.name))) {
                    vm.duplicatePreset(ps.pkg, mode, preset.id); onClose()
                }
                ActionRow(Icons.Rounded.East, stringResource(R.string.copy_to), null) {
                    onClose(); nav.push(Route.Copy(ps.pkg, mode, preset.id))
                }
            }
            ActionRow(Icons.Rounded.BarChart, stringResource(R.string.records_with, records), null) {
                onClose(); nav.push(Route.Records(ps.pkg))
            }
            if (preset != null) {
                ActionRow(Icons.Rounded.Delete, stringResource(R.string.delete), null, tint = MaterialTheme.colorScheme.error) {
                    onDelete(preset.id)
                }
            }
        }
    }
}

@Composable
private fun FilledTonalIconButtonSafe(label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Rounded.Check, label)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (sub != null) Muted(sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Landscape board LVariants: the right pane for the selected preset. */
@Composable
private fun PresetDetail(
    vm: AppViewModel, nav: Nav, ps: ProfileState, mode: Int, preset: Preset?, inUseId: String?, onDelete: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val inUse = preset?.id == inUseId
    var name by rememberSaveable(preset?.id) { mutableStateOf(preset?.name.orEmpty()) }
    val changes by produceState<List<Change>?>(null, preset?.id, preset?.config, mode) {
        value = null
        value = vm.presetChanges(ps.pkg, mode, preset)
    }
    val records = vm.recordsWith(ps, mode, preset?.id)
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (preset != null) {
                NameField(name, { name = it }, Modifier.weight(1f))
                FilledTonalIconButtonSafe(stringResource(R.string.save_name), enabled = name.isNotBlank() && name != preset.name) {
                    vm.renamePreset(ps.pkg, mode, preset.id, name)
                }
            } else {
                Text(ctx.builtinName(ps.dedicated), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (inUse) {
                val onGameProfile = mode == ps.mode
                Surface(
                    shape = RoundedCornerShape(28.dp), color = if (onGameProfile) cs.surfaceContainerHigh else Color.Transparent,
                    contentColor = cs.onSurfaceVariant, border = if (onGameProfile) null else outlinedPillBorder(),
                ) {
                    Row(Modifier.height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                        Text(stringResource(if (onGameProfile) R.string.in_use else R.string.preset_selected), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Button(onClick = { vm.usePreset(ps.pkg, mode, preset?.id) }, Modifier.height(56.dp)) { Text(stringResource(R.string.use_this_preset)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (preset == null) TonalCard(Modifier.weight(1f), RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(ctx.builtinName(ps.dedicated), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    BuiltinValues(ps, mode)
                }
            } else TonalCard(Modifier.weight(1f), RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(stringResource(R.string.changes_from_builtin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    val list = changes
                    if (list != null && list.isNotEmpty()) {
                        list.forEach { c ->
                            Row(Modifier.heightIn(min = 48.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(c.before + " → ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                Text(c.after, style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                            }
                            androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
                        }
                    } else if (list != null) {
                        Muted(stringResource(R.string.nothing_changed), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TonalCard(Modifier.weight(1f), RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.recorded_with), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Muted(
                        if (records.isEmpty()) stringResource(R.string.no_records_yet) else pluralStringResource(R.plurals.records_count, records.size, records.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (records.isNotEmpty()) {
                        val valid = records.filter { it.summary.validSamples > 0 }
                        val fps = valid.map { it.summary.avgFps.toDouble() }.average()
                        val temp = valid.map { it.summary.maxSkinC.toDouble() }.maxOrNull() ?: 0.0
                        val power = valid.map { it.summary.avgPowerMw }.filter { it >= 0 }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(stringResource(R.string.average_fps), if (valid.isEmpty()) "—" else formatFloat1(fps), Modifier.weight(1f))
                            StatTile(stringResource(R.string.max_temp), if (valid.isEmpty() || temp <= 0) "—" else vm.tf.absolute(temp), Modifier.weight(1f))
                            StatTile(stringResource(R.string.power), if (power.isEmpty()) "—" else formatPower(power.average().toInt()), Modifier.weight(1f))
                        }
                        TextButton(onClick = { nav.push(Route.Records(ps.pkg)) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.see_records)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (preset != null) {
                FilledTonalButton(onClick = { vm.duplicatePreset(ps.pkg, mode, preset.id) }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp)); Text(stringResource(R.string.duplicate), Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = { nav.push(Route.Copy(ps.pkg, mode, preset.id)) }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.East, null, Modifier.size(20.dp)); Text(stringResource(R.string.copy_to), Modifier.padding(start = 8.dp))
                }
            }
            if (inUse) {
                OutlinedButton(onClick = { nav.push(Route.Tuning(ps.pkg, mode)) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit_tuning)) }
            } else {
                Button(onClick = { vm.usePreset(ps.pkg, mode, preset?.id) { nav.push(Route.Tuning(ps.pkg, mode)) } }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.use_and_edit))
                }
            }
            Spacer(Modifier.weight(1f))
            if (preset != null) {
                TextButton(onClick = { onDelete(preset.id) }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp), tint = cs.error)
                    Text(stringResource(R.string.delete), Modifier.padding(start = 8.dp), color = cs.error)
                }
            }
        }
    }
}

/** What the profile itself gives the game, on one line: CPU, Prime core, GPU and heat limit. */
@Composable
private fun builtinLine(ps: ProfileState, mode: Int): String? {
    val ctx = LocalContext.current
    val b = ps.builtins[mode] ?: return null
    val speeds = listOf(Resource.GOLD, Resource.PRIME, Resource.GPU).map { ctx.resourceName(it) + " " + speedText(ps.limits, b, it) }
    return (speeds + (stringResource(R.string.heat_limit) + " " + ctx.heatName(b.thermalCase))).joinToString(" · ")
}

/** What the profile itself gives the game, in two rows of two. */
@Composable
private fun BuiltinValues(ps: ProfileState, mode: Int) {
    val ctx = LocalContext.current
    val b = ps.builtins[mode] ?: return
    val cells = listOf(
        stringResource(R.string.res_cpu) to speedText(ps.limits, b, Resource.GOLD),
        stringResource(R.string.res_fastest) to speedText(ps.limits, b, Resource.PRIME),
        stringResource(R.string.res_graphics) to speedText(ps.limits, b, Resource.GPU),
        stringResource(R.string.heat_limit) to ctx.heatName(b.thermalCase),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Muted(label, style = MaterialTheme.typography.bodyMedium)
                        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Muted(label)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
    }
}
