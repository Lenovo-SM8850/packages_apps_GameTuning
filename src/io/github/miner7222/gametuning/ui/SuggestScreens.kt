// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.miner7222.gametuning.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.TemperatureFormat
import io.github.miner7222.gametuning.data.joinNames
import io.github.miner7222.gametuning.data.ladder
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.presetLabel
import io.github.miner7222.gametuning.data.presetName
import io.github.miner7222.gametuning.data.resourceName
import vendor.lenovo.hardware.gametuning.IGameTuning
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.RecordingInfo

/** Message for an error code of recommend() and autoTune(). */
@Composable
private fun errorText(code: Int): String = stringResource(
    when (code) {
        IGameTuning.ERROR_NOT_ENOUGH_DATA -> R.string.rec_short
        IGameTuning.ERROR_MIXED -> R.string.rec_mixed
        else -> R.string.analysis_failed
    },
)

private val GOALS = listOf(IGameTuning.GOAL_EFFICIENCY, IGameTuning.GOAL_BALANCED, IGameTuning.GOAL_FRAMES)

@Composable
private fun goalLabel(goal: Int) = stringResource(
    when (goal) {
        IGameTuning.GOAL_FRAMES -> R.string.goal_frames
        IGameTuning.GOAL_EFFICIENCY -> R.string.goal_efficiency
        else -> R.string.goal_balanced
    },
)

@Composable
private fun goalHint(goal: Int) = stringResource(
    when (goal) {
        IGameTuning.GOAL_FRAMES -> R.string.goal_frames_hint
        IGameTuning.GOAL_EFFICIENCY -> R.string.goal_efficiency_hint
        else -> R.string.goal_balanced_hint
    },
)

/** The goal selector: connected buttons in portrait, a vertical list with hints in the left pane. */
@Composable
private fun GoalPicker(goal: Int, onPick: (Int) -> Unit, wide: Boolean) {
    val cs = MaterialTheme.colorScheme
    if (!wide) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectedChoice(
                GOALS.map { it to goalLabel(it) }, goal, onPick,
                shortOf = {
                    shortLabelResource(
                        when (it) {
                            IGameTuning.GOAL_FRAMES -> R.string.goal_frames_short
                            IGameTuning.GOAL_EFFICIENCY -> R.string.goal_efficiency_short
                            else -> R.string.goal_balanced_short
                        },
                    )
                },
            )
            Muted(goalHint(goal), Modifier.padding(horizontal = 8.dp))
        }
    } else {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GOALS.forEachIndexed { i, g ->
                val on = g == goal
                val itemShape = if (on) RoundedCornerShape(28.dp) else groupShape(i, GOALS.size, 20.dp, 6.dp)
                Surface(
                    shape = itemShape,
                    color = if (on) cs.primary else cs.surfaceContainer,
                    contentColor = if (on) cs.onPrimary else cs.onSurface,
                    modifier = Modifier.fillMaxWidth().clip(itemShape).selectable(on, role = Role.RadioButton) { onPick(g) },
                ) {
                    Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                        Text(goalLabel(g), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(goalHint(g), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** "Record from Oct 30, 8:12 PM · Performance · 120 fps hot". */
private fun recordSubtitle(ctx: Context, info: RecordingInfo): String =
    ctx.getString(
        R.string.record_from,
        DateUtils.formatDateTime(ctx, info.startEpochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
        ctx.modeName(info.mode), ctx.presetLabel(info),
    )

/** Plain-language lines about the record, from its summary. */
private fun sawLines(ctx: Context, tf: TemperatureFormat, limits: PolicyLimits, info: RecordingInfo): List<String> {
    val s = info.summary
    val out = ArrayList<String>()
    if (s.targetFps > 0) {
        val pct = Math.round(s.hitRate * 100)
        out.add(ctx.getString(if (pct >= 50) R.string.saw_kept else R.string.saw_missed, s.targetFps, pct))
    }
    val pegged = s.pegged
    if (pegged != null) {
        val worst = (0..2).filter { it < pegged.size }.maxByOrNull { pegged[it] }
        if (worst != null && pegged[worst] >= 0.30f) {
            out.add(ctx.getString(R.string.saw_pegged, ctx.resourceName(worst), Math.round(pegged[worst] * 100)))
        }
    }
    val avgs = intArrayOf(s.avgGoldKHz, s.avgPrimeKHz, s.avgGpuHz)
    val low = (0..2).filter { r ->
        val top = limits.ladder(r).lastOrNull() ?: 0
        top > 0 && avgs[r] > 0 && avgs[r] < top / 2
    }
    if (low.isNotEmpty()) {
        val names = ctx.joinNames(low.map { ctx.resourceName(it) })
        out.add(ctx.getString(if (low.size > 1) R.string.saw_low_many else R.string.saw_low_one, names))
    }
    if (s.throttled >= 0.2f) out.add(ctx.getString(R.string.saw_throttled, Math.round(s.throttled * 100)))
    if (s.maxSkinC > 0f) out.add(ctx.getString(R.string.saw_reached, tf.absolute(s.maxSkinC.toDouble())))
    return out.take(4)
}

@Composable
private fun SawCard(lines: List<String>, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.what_we_saw), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun AnalysisError(code: Int, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(errorText(code), style = MaterialTheme.typography.bodyLarge)
            if (code != IGameTuning.ERROR_NOT_ENOUGH_DATA && code != IGameTuning.ERROR_MIXED) {
                OutlinedButton(onClick = onRetry, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(64) }, singleLine = true,
                    label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name); onDismiss() }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Looks up the record of a route; goes back when it is gone. */
@Composable
private fun rememberRecord(vm: AppViewModel, nav: Nav, pkg: String, id: String): Pair<ProfileState, RecordingInfo>? {
    val ps = vm.profile?.takeIf { it.pkg == pkg }
    val info = ps?.recordings?.firstOrNull { it.id == id }
    LaunchedEffect(ps != null, info == null) { if (ps != null && info == null) nav.pop() }
    return if (ps != null && info != null) ps to info else null
}

// region suggest

/** Boards Suggest and LSuggest. */
@Composable
fun SuggestScreen(vm: AppViewModel, nav: Nav, pkg: String, id: String, wide: Boolean) {
    val ctx = LocalContext.current
    val found = rememberRecord(vm, nav, pkg, id)
    Column(Modifier.fillMaxSize()) {
        TopBar(
            stringResource(R.string.suggest_title), found?.let { recordSubtitle(ctx, it.second) }, { nav.pop() },
            Modifier.padding(horizontal = if (wide) 20.dp else 0.dp),
        )
        if (found == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val (ps, info) = found
        var goal by rememberSaveable { mutableStateOf(IGameTuning.GOAL_BALANCED) }
        var attempt by rememberSaveable { mutableStateOf(0) }
        var saving by rememberSaveable { mutableStateOf<Int?>(null) }
        val result by produceState<Outcome<List<Suggestion>>?>(null, info.id, info.rangeRevision, ps.libs, goal, attempt) {
            value = null
            value = vm.suggestionsFor(info, goal)
        }
        val recorded = vm.recordPreset(ps, info)
        val saw = sawLines(ctx, vm.tf, ps.limits, info)

        fun apply(sg: Suggestion, replace: Boolean, name: String) {
            vm.doImport(sg.item, sg.baseMode, info.packageName, info.mode, false, sg.includeExpert, name, replaceId = if (replace) recorded?.id else null, adviceInfo = info, adviceContext = sg.context, adviceFps = sg.fpsLimit)
            nav.pop()
        }

        @Composable
        fun candidates() {
            when (val r = result) {
                null -> Loading()
                is Outcome.Failed -> AnalysisError(r.code) { attempt++ }
                is Outcome.Ok -> {
                    if (r.value.isEmpty()) {
                        Muted(stringResource(R.string.suggest_none), Modifier.padding(16.dp), MaterialTheme.typography.bodyLarge)
                    } else {
                        val top = r.value.first()
                        TopSuggestion(top, recorded?.name, wide, onSaveNew = { saving = 0 }, onReplace = { apply(top, true, top.name) })
                        val rest = r.value.drop(1)
                        if (wide) {
                            rest.chunked(2).forEachIndexed { rowIndex, row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEachIndexed { k, sg ->
                                        Box(Modifier.weight(1f)) { OtherSuggestion(sg, 2 + rowIndex * 2 + k, true) { saving = 1 + rowIndex * 2 + k } }
                                    }
                                    if (row.size < 2) Box(Modifier.weight(1f))
                                }
                            }
                        } else {
                            rest.forEachIndexed { k, sg -> OtherSuggestion(sg, k + 2, false) { saving = k + 1 } }
                        }
                    }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stringResource(R.string.goal_title))
                        GoalPicker(goal, { goal = it }, true)
                    }
                    SawCard(saw)
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.suggest_dialog_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        candidates()
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = navBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stringResource(R.string.goal_title))
                        GoalPicker(goal, { goal = it }, false)
                    }
                    SawCard(saw)
                    SectionLabel(stringResource(R.string.suggest_dialog_title), Modifier.padding(top = 4.dp))
                    candidates()
                }
            }
        }

        val sel = saving
        val list = (result as? Outcome.Ok<List<Suggestion>>)?.value
        if (sel != null && list != null && sel in list.indices) {
            val sg = list[sel]
            NameDialog(stringResource(R.string.save_as_preset), sg.name, { apply(sg, false, it) }, { saving = null })
        }
    }
}

@Composable
private fun ChangeChips(changes: List<String>, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        changes.forEach { Tag(it, container = container, content = content) }
    }
}

@Composable
private fun TopSuggestion(sg: Suggestion, replaceName: String?, wide: Boolean, onSaveNew: () -> Unit, onReplace: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val kind = stringResource(if (sg.yours) R.string.kind_yours else R.string.kind_builtin)
    Surface(shape = RoundedCornerShape(if (wide) 32.dp else 32.dp), color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.closest_setting, kind), style = MaterialTheme.typography.labelLarge)
                Text(sg.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ChangeChips(sg.changes, cs.secondaryContainer, cs.onSecondaryContainer)
                Text(sg.reason, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val buttons: @Composable () -> Unit = {
            Button(onClick = onSaveNew, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save_as_new_preset)) }
            if (replaceName != null) {
                OutlinedButton(onClick = onReplace, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.replace_preset_named, replaceName), maxLines = 1)
                }
            }
        }
        if (wide) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                body(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            }
        } else {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                body(Modifier)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            }
        }
    }
}

@Composable
private fun OtherSuggestion(sg: Suggestion, rank: Int, wide: Boolean, onSave: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val kind = stringResource(if (sg.yours) R.string.kind_yours else R.string.kind_builtin)
    Surface(shape = RoundedCornerShape(if (wide) 24.dp else 20.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Muted(stringResource(R.string.option_rank, rank, kind), style = MaterialTheme.typography.labelMedium)
                Text(sg.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (wide) ChangeChips(sg.changes, cs.surfaceContainerHigh, cs.onSecondaryContainer)
                else Muted(sg.changes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(sg.reason, style = MaterialTheme.typography.bodySmall, color = if (wide) cs.onSurfaceVariant else cs.onSurface)
            }
        }
        if (wide) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                text(Modifier)
                OutlinedButton(onClick = onSave, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save_as_preset)) }
            }
        } else {
            BoxWithConstraints {
                // Start 24 and end 16 dp of padding; under 480 dp inside, the button goes below the text.
                if (maxWidth - 40.dp < 480.dp) {
                    Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        text(Modifier.fillMaxWidth())
                        OutlinedButton(onClick = onSave, Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text(stringResource(R.string.save_as_preset), maxLines = 1) }
                    }
                } else {
                    Row(Modifier.heightIn(min = 104.dp).padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        text(Modifier.weight(1f))
                        OutlinedButton(onClick = onSave, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save_as_preset), maxLines = 1) }
                    }
                }
            }
        }
    }
}

// endregion

// region automatic adjustment

/** Boards AutoTune and LAutoTune. */
@Composable
fun AutoTuneScreen(vm: AppViewModel, nav: Nav, pkg: String, id: String, wide: Boolean) {
    val ctx = LocalContext.current
    val found = rememberRecord(vm, nav, pkg, id)
    Column(Modifier.fillMaxSize()) {
        TopBar(
            stringResource(R.string.auto_title), null, { nav.pop() },
            Modifier.padding(horizontal = if (wide) 20.dp else 0.dp),
        )
        if (found == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val (ps, info) = found
        var goal by rememberSaveable { mutableStateOf(IGameTuning.GOAL_BALANCED) }
        var attempt by rememberSaveable { mutableStateOf(0) }
        var asNew by rememberSaveable { mutableStateOf(false) }
        // The preset the record was made with; a built-in setting is never changed, only saved as a new preset.
        val recorded = vm.recordPreset(ps, info)
        val seedName = info.presetName().ifEmpty {
            stringResource(if (info.profile?.tuned == true) R.string.unnamed_preset else R.string.builtin_setting)
        }
        val baseName = recorded?.name ?: seedName
        val goalWord = stringResource(
            when (goal) {
                IGameTuning.GOAL_FRAMES -> R.string.goal_word_frames
                IGameTuning.GOAL_EFFICIENCY -> R.string.goal_word_efficiency
                else -> R.string.goal_word_balanced
            },
        )
        val defaultName = stringResource(R.string.auto_new_name, baseName, goalWord)
        var name by rememberSaveable(goal, recorded?.id) { mutableStateOf(defaultName) }
        val result by produceState<Outcome<AutoPlan>?>(null, info.id, info.rangeRevision, ps.libs, goal, attempt) {
            value = null
            value = vm.autoPlanFor(info, goal)
        }
        val saw = sawLines(ctx, vm.tf, ps.limits, info)
        val plan = (result as? Outcome.Ok<AutoPlan>)?.value
        val canChange = recorded != null
        val creatingNew = asNew || !canChange

        @Composable
        fun planCard() {
            val cs = MaterialTheme.colorScheme
            when (val r = result) {
                null -> Loading()
                is Outcome.Failed -> AnalysisError(r.code) { attempt++ }
                is Outcome.Ok -> {
                    val p = r.value
                    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = if (wide) 28.dp else 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                if (canChange) stringResource(R.string.auto_change_to, baseName) else stringResource(R.string.what_it_changes),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            )
                            if (p.lines.isEmpty()) {
                                Text(stringResource(R.string.auto_fits), style = MaterialTheme.typography.bodyLarge)
                            } else {
                                p.lines.forEach { ChangeRow(it, wide) }
                            }
                            Muted(p.observation, style = MaterialTheme.typography.bodyMedium)
                            if (p.lines.isNotEmpty()) {
                                androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
                                Muted(stringResource(R.string.auto_after), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun whereToApply() {
            val cs = MaterialTheme.colorScheme
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 0.dp)) {
                val rows = listOfNotNull(
                    if (canChange) false to stringResource(R.string.auto_where_change, baseName) else null,
                    true to stringResource(R.string.save_as_new_preset_long),
                )
                Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    if (wide) {
                        Row(Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rows.forEach { (isNew, text) ->
                                val on = creatingNew == isNew
                                val optionShape = RoundedCornerShape(20.dp)
                                Surface(
                                    shape = optionShape, color = if (on) cs.primaryContainer else cs.surfaceContainer,
                                    contentColor = if (on) cs.onPrimaryContainer else cs.onSurface, modifier = Modifier.weight(1f)
                                        .clip(optionShape).selectable(on, role = Role.RadioButton) { asNew = isNew },
                                ) {
                                    Row(Modifier.heightIn(min = 56.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = on, onClick = null)
                                        Text(text, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            rows.forEach { (isNew, text) ->
                                val on = creatingNew == isNew
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(on, role = Role.RadioButton) { asNew = isNew },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    RadioButton(selected = on, onClick = null)
                                    Text(text, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
            if (creatingNew) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(64) }, singleLine = true,
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.padding(top = 12.dp).then(if (wide) Modifier.widthIn(max = 480.dp) else Modifier.fillMaxWidth()),
                )
            }
        }

        val canApply = plan != null && plan.lines.isNotEmpty() && !vm.busy && (!creatingNew || name.isNotBlank())
        val apply = {
            if (plan != null) {
                vm.applyAutoTune(info, plan, creatingNew, name)
                nav.pop()
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(bottom = navBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(stringResource(R.string.goal))
                    GoalPicker(goal, { goal = it }, true)
                    SawCard(saw, Modifier.padding(top = 4.dp))
                    Muted(stringResource(R.string.auto_based_on, recordSubtitleShort(ctx, info)), Modifier.padding(horizontal = 8.dp))
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxSize().padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = navBottomPadding() + 24.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            planCard()
                            if (plan != null && plan.lines.isNotEmpty()) whereToApply()
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { nav.pop() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                            Button(onClick = { apply() }, enabled = canApply, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.apply)) }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = navBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GoalPicker(goal, { goal = it }, false)
                    planCard()
                    if (plan != null && plan.lines.isNotEmpty()) whereToApply()
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { nav.pop() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { apply() }, enabled = canApply, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.apply)) }
                    }
                }
            }
        }
    }
}

private fun recordSubtitleShort(ctx: Context, info: RecordingInfo): String =
    ctx.getString(
        R.string.record_short,
        DateUtils.formatDateTime(ctx, info.startEpochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
        ctx.presetLabel(info),
    )

@Composable
private fun ChangeRow(c: ChangeLine, wide: Boolean) {
    val cs = MaterialTheme.colorScheme
    val chip: @Composable () -> Unit = {
        if (c.delta.isNotEmpty()) {
            if (c.raise) Tag(c.delta, container = cs.tertiaryContainer, content = cs.onTertiaryContainer)
            else Tag(c.delta, container = cs.secondaryContainer, content = cs.onSecondaryContainer)
        }
    }
    val values: @Composable () -> Unit = {
        Text(c.before, style = MaterialTheme.typography.titleLarge)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        Text(c.after, style = MaterialTheme.typography.titleLarge, color = cs.primary)
        chip()
    }
    if (wide) {
        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(c.label, Modifier.widthIn(min = 140.dp, max = 200.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                values()
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(c.label, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { values() }
        }
    }
}

// endregion
