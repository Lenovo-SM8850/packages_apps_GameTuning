// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.data.modeName

/** Selection state of the copy flow, kept as strings so it survives rotation. */
private class CopyForm(
    modes: androidx.compose.runtime.MutableState<String>,
    games: androidx.compose.runtime.MutableState<String>,
    into: androidx.compose.runtime.MutableState<String>,
    replace: androidx.compose.runtime.MutableState<String>,
    start: androidx.compose.runtime.MutableState<Boolean>,
    expert: androidx.compose.runtime.MutableState<Boolean>,
) {
    private var m by modes
    private var g by games
    private var i by into
    private var r by replace
    var startUsing by start
    var expertToOthers by expert

    private fun set(text: String) = text.split(",").filter { it.isNotEmpty() }.toSet()
    val modes: Set<Int> get() = set(m).map { it.toInt() }.toSet()
    val games: Set<String> get() = set(g)
    val replace: Set<String> get() = set(r)
    fun intoFor(pkg: String, default: Int): Int =
        i.split(";").firstOrNull { it.startsWith("$pkg=") }?.substringAfter('=')?.toIntOrNull() ?: default

    fun toggleMode(mode: Int) { m = (if (mode in modes) modes - mode else modes + mode).joinToString(",") }
    fun toggleGame(pkg: String) { g = (if (pkg in games) games - pkg else games + pkg).joinToString(",") }
    fun selectAll(pkgs: List<String>) { g = pkgs.joinToString(",") }
    fun setInto(pkg: String, mode: Int) {
        i = (i.split(";").filter { it.isNotEmpty() && !it.startsWith("$pkg=") } + "$pkg=$mode").joinToString(";")
    }
    fun setReplace(key: String, on: Boolean) { r = (if (on) replace + key else replace - key).joinToString(",") }
}

@Composable
private fun rememberCopyForm() = CopyForm(
    rememberSaveable { mutableStateOf("") }, rememberSaveable { mutableStateOf("") },
    rememberSaveable { mutableStateOf("") }, rememberSaveable { mutableStateOf("") },
    rememberSaveable { mutableStateOf(false) }, rememberSaveable { mutableStateOf(false) },
)

private fun CopyForm.targets(src: Route.Copy, srcMode: Int): List<CopyTarget> =
    modes.filter { it != srcMode }.map { CopyTarget(src.pkg, it) } + games.map { CopyTarget(it, intoFor(it, srcMode)) }

@Composable
private fun CopyBody(vm: AppViewModel, route: Route.Copy, form: CopyForm, wide: Boolean) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val preset = vm.store.get(route.pkg, route.mode, route.presetId)
    val name = preset?.name.orEmpty()
    val gameLabel = vm.games.label(route.pkg)
    val others = vm.cards.filter { it.pkg != route.pkg && it.mode != Modes.NONE }

    fun conflict(pkg: String, mode: Int) = vm.store.list(pkg, mode).any { it.name == name }

    @Composable
    fun conflictRow(pkg: String, mode: Int, who: String) {
        val key = "$pkg|$mode"
        val replace = key in form.replace
        ConnectedChoice(
            listOf(false to stringResource(R.string.keep_both, vm.uniqueName(vm.store.list(pkg, mode), name)), true to stringResource(R.string.replace)),
            replace, { form.setReplace(key, it) },
            Modifier.fillMaxWidth().padding(start = if (wide) 46.dp else 58.dp, end = 16.dp, bottom = 12.dp),
        )
    }

    @Composable
    fun thisGame() {
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp), if (wide) cs.surfaceContainerHigh else cs.surfaceContainer) {
            Column(Modifier.padding(horizontal = if (wide) 20.dp else 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.other_profiles_of, gameLabel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in Modes.ALL) {
                        val src = m == route.mode
                        val on = m in form.modes && !src
                        FilterChip(
                            selected = on, enabled = !src, onClick = { form.toggleMode(m) },
                            label = { Text(if (src) stringResource(R.string.this_one, ctx.modeName(m)) else ctx.modeName(m)) },
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                for (m in Modes.ALL) if (m in form.modes && m != route.mode && conflict(route.pkg, m)) {
                    Muted(stringResource(R.string.already_has, ctx.modeName(m), name), style = MaterialTheme.typography.bodyMedium)
                    conflictRow(route.pkg, m, ctx.modeName(m))
                }
            }
        }
    }

    @Composable
    fun otherGames() {
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp), if (wide) cs.surfaceContainerHigh else cs.surfaceContainer) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.other_games), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { form.selectAll(others.map { it.pkg }) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.select_all)) }
                }
                if (others.isEmpty()) Muted(stringResource(R.string.no_other_games), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), MaterialTheme.typography.bodyMedium)
                others.forEach { g ->
                    val on = g.pkg in form.games
                    val into = form.intoFor(g.pkg, route.mode)
                    val hasSame = conflict(g.pkg, into)
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.padding(start = 8.dp, end = 16.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Checkbox) { form.toggleGame(g.pkg) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Column {
                                Text(g.label, style = MaterialTheme.typography.bodyLarge)
                                Muted(
                                    when {
                                        hasSame -> stringResource(R.string.has_same_name)
                                        g.presetName == null -> stringResource(R.string.builtin_in_use)
                                        else -> stringResource(R.string.presets_in, vm.store.list(g.pkg, into).size, ctx.modeName(into))
                                    },
                                )
                            }
                        }
                        Box {
                            OutlinedButton(onClick = { menu = true }, enabled = on, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.into_profile, ctx.modeName(into)))
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                for (m in Modes.ALL) DropdownMenuItem(text = { Text(ctx.modeName(m)) }, onClick = { form.setInto(g.pkg, m); menu = false })
                            }
                        }
                    }
                    if (on && hasSame) conflictRow(g.pkg, into, g.label)
                }
            }
        }
    }

    @Composable
    fun options() {
        TonalCard(Modifier.fillMaxWidth(), RoundedCornerShape(if (wide) 24.dp else 28.dp), if (wide) cs.surfaceContainerHigh else cs.surfaceContainer) {
            SwitchRow(stringResource(R.string.start_using_there), null, form.startUsing, { form.startUsing = it }, minHeight = 56.dp, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (wide) 20.dp else 24.dp, vertical = 12.dp))
            androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
            SwitchRow(stringResource(R.string.copy_expert_title), stringResource(R.string.copy_expert_note_short), form.expertToOthers, { form.expertToOthers = it }, minHeight = 72.dp, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (wide) 20.dp else 24.dp, vertical = 12.dp))
        }
    }

    val gn = form.games.size
    val mn = form.modes.filter { it != route.mode }.size
    val summary = stringResource(R.string.copy_summary, name, pluralStringResource(R.plurals.profiles_count, mn, mn), pluralStringResource(R.plurals.games_count, gn, gn))

    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { thisGame(); options() }
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) { otherGames() }
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            thisGame(); otherGames(); options()
            Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

private fun anyReplace(vm: AppViewModel, route: Route.Copy, form: CopyForm): Boolean {
    val name = vm.store.get(route.pkg, route.mode, route.presetId)?.name ?: return false
    return form.targets(route, route.mode).any { t ->
        "${t.pkg}|${t.mode}" in form.replace && vm.store.list(t.pkg, t.mode).any { it.name == name }
    }
}

private fun doCopy(vm: AppViewModel, route: Route.Copy, form: CopyForm) {
    val t = form.targets(route, route.mode)
    vm.copyPreset(
        CopyRequest(route.pkg, route.mode, route.presetId, t, form.replace, form.startUsing, form.expertToOthers),
    )
}

/** Portrait board CopyTo. */
@Composable
fun CopyScreen(vm: AppViewModel, nav: Nav, route: Route.Copy) {
    val form = rememberCopyForm()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val name = vm.store.get(route.pkg, route.mode, route.presetId)?.name.orEmpty()
    val enabled = form.targets(route, route.mode).isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TopBar(stringResource(R.string.copy_title, name), null, null, actions = {})
            }
            IconButton(onClick = { nav.pop() }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
            Button(onClick = {
                if (anyReplace(vm, route, form)) confirm = true else { doCopy(vm, route, form); nav.pop() }
            }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy)) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                CopyBody(vm, route, form, false)
            }
        }
    }
    if (confirm) {
        val replaced = form.targets(route, route.mode).filter { "${it.pkg}|${it.mode}" in form.replace }.map { vm.games.label(it.pkg) }.distinct().joinToString()
        ConfirmDialog(
            stringResource(R.string.replace_q, name, replaced), stringResource(R.string.replace_body), stringResource(R.string.replace),
            onConfirm = { doCopy(vm, route, form); nav.pop() }, onDismiss = { confirm = false },
        )
    }
}

/** Landscape board LCopyTo: a centered dialog over the presets screen. */
@Composable
fun CopyDialog(vm: AppViewModel, nav: Nav, route: Route.Copy) {
    val form = rememberCopyForm()
    val name = vm.store.get(route.pkg, route.mode, route.presetId)?.name.orEmpty()
    val enabled = form.targets(route, route.mode).isNotEmpty()
    Dialog(onDismissRequest = { nav.pop() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 960.dp).fillMaxWidth(0.9f), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.copy_title, name), style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f, fill = false).padding(top = 16.dp).verticalScroll(rememberScrollState())) { CopyBody(vm, route, form, true) }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { nav.pop() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { doCopy(vm, route, form); nav.pop() }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy)) }
                }
            }
        }
    }
}
