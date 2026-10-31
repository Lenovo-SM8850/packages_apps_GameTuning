// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.ImportPreview
import io.github.miner7222.gametuning.data.StepChange
import io.github.miner7222.gametuning.data.GameNames
import io.github.miner7222.gametuning.data.GENERIC_PROFILE
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.data.builtinName
import io.github.miner7222.gametuning.data.modeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SOURCE_MODES = Modes.ALL.toList() + 4

@Composable
fun ImportScreen(vm: AppViewModel, nav: Nav, pkg: String, mode: Int, wide: Boolean) {
    val ctx = LocalContext.current
    val sources by produceState<ImportSources?>(null, pkg, LocalConfiguration.current.locales.toLanguageTags()) {
        value = withContext(Dispatchers.IO) { try { vm.importSources(pkg) } catch (_: Exception) { ImportSources(emptyList(), emptyList()) } }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    var selKey by rememberSaveable { mutableStateOf<String?>(null) }
    val src = sources
    val label = vm.games.label(pkg)

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.import_title), label, { nav.pop() }, Modifier.padding(horizontal = if (wide) 20.dp else 0.dp))
        if (src == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val mine = src.mine.filter { it.title.contains(query, true) || it.gameLabel.contains(query, true) }
        val built = src.builtIn.filter { it.title.contains(query, true) }
        val allItems: List<ImportItem> = mine + built
        val selected = allItems.firstOrNull { it.key == selKey } ?: if (wide) allItems.firstOrNull() else null

        @Composable
        fun search() {
            SearchField(query, { query = it })
        }

        @Composable
        fun itemRow(item: ImportItem, i: Int, n: Int, isSel: Boolean) {
            val itemShape = if (isSel && wide) RoundedCornerShape(24.dp) else groupShape(i, n, 24.dp)
            Surface(
                shape = itemShape,
                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(isSel, role = Role.Button) { selKey = item.key },
            ) {
                Row(Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val packages = when (item) {
                        is ImportItem.Mine -> listOf(item.pkg)
                        is ImportItem.BuiltIn -> (listOf(item.source.profileKey) + item.source.packages.orEmpty()).distinct()
                    }
                    val iconPkg = remember(item.key) {
                        packages.firstOrNull { pkg ->
                            try { ctx.packageManager.getApplicationInfo(pkg, 0); true }
                            catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
                        } ?: packages.firstOrNull().orEmpty()
                    }
                    ImportSourceIcon(vm, iconPkg, if (item is ImportItem.Mine) item.gameLabel else item.title, isSel)
                    Column(Modifier.weight(1f)) {
                        val regions = packages.mapNotNull { GameNames.region(it) }.distinct()
                        if (item is ImportItem.Mine) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            RegionName(item.gameLabel, regions, MaterialTheme.typography.bodySmall)
                            Muted(ctx.modeName(item.mode))
                        } else {
                            RegionName(item.title, regions, MaterialTheme.typography.bodyLarge)
                            Muted(item.subtitle)
                        }
                    }
                }
            }
        }

        @Composable
        fun mineList() {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (mine.isEmpty()) EmptyState(Icons.Rounded.Download, stringResource(R.string.no_presets_elsewhere), Modifier.fillMaxWidth())
                mine.forEachIndexed { i, it -> itemRow(it, i, mine.size, it.key == selected?.key) }
            }
        }

        @Composable
        fun builtList() {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (built.isEmpty() && query.isNotBlank()) EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.no_matches), Modifier.fillMaxWidth())
                built.forEachIndexed { i, it -> itemRow(it, i, built.size, it.key == selected?.key) }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    search()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stringResource(R.string.your_presets_elsewhere), Modifier.padding(top = 8.dp))
                        mineList()
                        SectionLabel(stringResource(R.string.builtin_settings), Modifier.padding(top = 8.dp))
                        builtList()
                    }
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    if (selected != null) ImportDetail(vm, nav, pkg, mode, selected, true)
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    search()
                    ConnectedChoice(
                        listOf(0 to stringResource(R.string.your_presets), 1 to stringResource(R.string.builtin_settings)), tab, { tab = it },
                        shortOf = { shortLabelResource(if (it == 0) R.string.your_presets_short else R.string.builtin_settings_short) },
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = navBottomPadding() + 16.dp)) {
                        if (tab == 0) mineList() else builtList()
                    }
                }
            }
            val s = selected
            if (s != null) {
                ModalBottomSheet(onDismissRequest = { selKey = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                        ImportDetail(vm, nav, pkg, mode, s, false) { selKey = null }
                    }
                }
            }
        }
    }
}

/**
 * The preview and options of one import: used in the portrait sheet and the landscape pane. In the pane
 * the starting values and the target profile sit side by side and Cancel and Add stay pinned at the bottom.
 */
@Composable
private fun ImportDetail(vm: AppViewModel, nav: Nav, pkg: String, mode: Int, item: ImportItem, pane: Boolean, onClose: () -> Unit = {}) {
    val ctx = LocalContext.current
    var baseMode by rememberSaveable(item.key) { mutableStateOf(mode) }
    var into by rememberSaveable(item.key) { mutableStateOf(mode) }
    var replace by rememberSaveable(item.key) { mutableStateOf(false) }
    var expert by rememberSaveable(item.key) { mutableStateOf(false) }
    val ps = vm.profile?.takeIf { it.pkg == pkg }
    val inUse = ps?.libs?.get(into)?.inUse
    val canReplace = inUse != null
    var rows by remember(item.key, baseMode, pkg, into, expert, ps) { mutableStateOf<ImportPreview?>(null) }
    var previewFailed by remember(item.key, baseMode, pkg, into, expert, ps) { mutableStateOf(false) }
    LaunchedEffect(item.key, baseMode, pkg, into, expert, ps) {
        try {
            rows = withContext(Dispatchers.IO) { vm.importPreviewDetails(item, baseMode, pkg, into, expert) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            previewFailed = true
        }
    }

    @Composable
    fun startFromBlock(modifier: Modifier, style: androidx.compose.ui.text.TextStyle) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.start_from_its), Modifier.padding(0.dp))
            ConnectedChoice(
                SOURCE_MODES.map { it to (if (it == 4) stringResource(R.string.mode_maximum) else ctx.modeName(it)) }, baseMode, { baseMode = it },
                if (pane) Modifier.fillMaxWidth() else Modifier.widthIn(max = 640.dp), labelStyle = style,
                shortOf = { modeShortLabel(it) },
            )
            if (baseMode == 4) Muted(stringResource(R.string.maximum_hint))
        }
    }

    @Composable
    fun intoBlock(modifier: Modifier, style: androidx.compose.ui.text.TextStyle) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.into_profile_label), Modifier.padding(0.dp))
            ConnectedChoice(
                Modes.ALL.map { it to ctx.modeName(it) }, into, { into = it },
                if (pane) Modifier.fillMaxWidth() else Modifier.widthIn(max = 640.dp), labelStyle = style,
                shortOf = { modeShortLabel(it) },
            )
        }
    }

    @Composable
    fun changesBlock() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.what_it_changes), Modifier.padding(0.dp))
            val preview = rows
            if (previewFailed) Muted(stringResource(R.string.save_failed))
            else if (preview == null) CircularProgressIndicator(Modifier.size(24.dp))
            else if (preview.changes.isEmpty() && preview.steps.isEmpty()) Muted(stringResource(R.string.nothing_changed), style = MaterialTheme.typography.bodyMedium)
            else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    preview.changes.forEach {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(it.label, style = MaterialTheme.typography.labelMedium)
                                Text(it.before + " → " + it.after, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (preview.steps.isNotEmpty()) {
                    androidx.compose.runtime.key(item.key, baseMode, pkg, into, expert, ps) { StepChanges(preview.steps) }
                }
            }
        }
    }

    @Composable
    fun body() {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (pane) item.title else stringResource(R.string.use_quoted, item.title),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            val where = when (item) {
                is ImportItem.Mine -> stringResource(R.string.from_where, item.subtitle)
                is ImportItem.BuiltIn -> ctx.builtinName(item.source.profileKey != GENERIC_PROFILE)
            }
            Muted(where, style = MaterialTheme.typography.bodyMedium)
        }
        val compact = MaterialTheme.typography.labelMedium
        if (pane) {
            if (item is ImportItem.BuiltIn) {
                startFromBlock(Modifier.fillMaxWidth(), compact)
                intoBlock(Modifier.fillMaxWidth(), compact)
            } else {
                intoBlock(Modifier.fillMaxWidth(), MaterialTheme.typography.labelLarge)
            }
            changesBlock()
        } else {
            if (item is ImportItem.BuiltIn) startFromBlock(Modifier, MaterialTheme.typography.labelLarge)
            changesBlock()
            intoBlock(Modifier, MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.selectableGroup()) {
            SectionLabel(stringResource(R.string.save_as), Modifier.padding(0.dp).padding(bottom = 4.dp))
            SaveAsRow(!replace || !canReplace, stringResource(R.string.new_preset_use_named, item.title)) { replace = false }
            SaveAsRow(replace && canReplace, stringResource(R.string.replace_named, inUse?.name ?: stringResource(R.string.preset_in_use_word)), enabled = canReplace) { replace = true }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            stringResource(R.string.also_expert), stringResource(R.string.also_expert_note), expert, { expert = it },
            minHeight = 56.dp, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        )
    }

    @Composable
    fun footer(modifier: Modifier) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { if (pane) nav.pop() else onClose() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
            Button(onClick = {
                vm.doImport(item, baseMode, pkg, into, replace && canReplace, expert)
                nav.pop()
            }, Modifier.heightIn(min = 48.dp), enabled = rows != null && !previewFailed && ps?.tuningEnabled == true) { Text(stringResource(R.string.add)) }
        }
    }

    if (pane) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) { body() }
            footer(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = navBottomPadding() + 16.dp))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            body()
            footer(Modifier)
        }
    }
}

@Composable
private fun RegionName(name: String, regions: List<String>, style: androidx.compose.ui.text.TextStyle) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, Modifier.weight(1f, fill = false), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        regions.forEach { Tag(it) }
    }
}

@Composable
private fun ImportSourceIcon(vm: AppViewModel, pkg: String, label: String, selected: Boolean) {
    val size = 40.dp
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg, px) { value = vm.games.icon(pkg, px) }
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(size), shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primary else cs.secondaryContainer,
        contentColor = if (selected) cs.onPrimary else cs.onSecondaryContainer,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon!!, null, Modifier.fillMaxSize())
            else Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepChanges(steps: List<StepChange>) {
    var expanded by remember { mutableStateOf(false) }
    val collapsible = steps.size > 1 || steps.sumOf { it.changes.size } > 3
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .then(if (collapsible) Modifier.clickable(role = Role.Button) { expanded = !expanded } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleSmall)
                    Muted(pluralStringResource(R.plurals.steps_count, steps.size, steps.size))
                }
                if (collapsible) Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    stringResource(if (expanded) R.string.show_less else R.string.show_details),
                )
            }
            if (!collapsible || expanded) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    steps.forEachIndexed { i, step ->
                        Surface(shape = groupShape(i, steps.size, 12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(step.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                step.changes.forEach {
                                    Text(it.label + " " + it.before + " → " + it.after, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveAsRow(selected: Boolean, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
