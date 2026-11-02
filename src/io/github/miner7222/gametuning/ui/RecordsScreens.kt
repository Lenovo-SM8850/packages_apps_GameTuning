// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.miner7222.gametuning.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.AutoEvent
import io.github.miner7222.gametuning.data.Axis
import io.github.miner7222.gametuning.data.RecordExport
import io.github.miner7222.gametuning.data.Resource
import io.github.miner7222.gametuning.data.TemperatureFormat
import io.github.miner7222.gametuning.data.TuningException
import io.github.miner7222.gametuning.data.bucketAverage
import io.github.miner7222.gametuning.data.bucketHeldLongest
import io.github.miner7222.gametuning.data.curOf
import io.github.miner7222.gametuning.data.deriveAutoEvents
import io.github.miner7222.gametuning.data.formatClock
import io.github.miner7222.gametuning.data.formatFloat1
import io.github.miner7222.gametuning.data.formatPower
import io.github.miner7222.gametuning.data.fractionOfTop
import io.github.miner7222.gametuning.data.hasData
import io.github.miner7222.gametuning.data.hasTemp
import io.github.miner7222.gametuning.data.hitPercent
import io.github.miner7222.gametuning.data.modeName
import io.github.miner7222.gametuning.data.niceAxis
import io.github.miner7222.gametuning.data.presetLabel
import vendor.lenovo.hardware.gametuning.IGameTuning
import vendor.lenovo.hardware.gametuning.PolicyLimits
import vendor.lenovo.hardware.gametuning.RecordingInfo
import vendor.lenovo.hardware.gametuning.RecordingSample
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_SAMPLES_FOR_ADVICE = 110

// region records list

/**
 * Boards Records and LRecords. Below 840 dp this is the list of one game's records (a record opens
 * the Result screen); from 840 dp it is the list on the left and the result of the picked record on
 * the right. [openId] picks the record when the Result route is shown at that width.
 */
@Composable
fun RecordsScreen(vm: AppViewModel, nav: Nav, pkg: String, wide: Boolean, openId: String? = null) {
    val ctx = LocalContext.current
    val ps = vm.profile?.takeIf { it.pkg == pkg }
    var filter by rememberSaveable(pkg) { mutableStateOf("") }
    var picked by rememberSaveable(pkg) { mutableStateOf(openId.orEmpty()) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openId) { if (openId != null) picked = openId }

    if (ps == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.records_title), vm.games.label(pkg), { nav.pop() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    val all = ps.recordings
    val labels = all.map { ctx.presetLabel(it) }.distinct()
    val activeFilter = filter.takeIf { it in labels }.orEmpty()
    val shown = all.filter { activeFilter.isEmpty() || ctx.presetLabel(it) == activeFilter }
    val current = shown.firstOrNull { it.id == picked && it.hasData() } ?: shown.firstOrNull { it.hasData() }

    @Composable
    fun armSwitch(compact: Boolean) {
        if (compact) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.heightIn(min = 48.dp).toggleable(ps.armed, role = Role.Switch) { vm.setArmed(pkg, it) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.rec_arm_title), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ps.armed, onCheckedChange = null)
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    stringResource(R.string.rec_arm_title), stringResource(R.string.rec_arm_summary), ps.armed, { vm.setArmed(pkg, it) },
                    minHeight = 88.dp, padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }

    @Composable
    fun chips() {
        if (labels.size < 2) return
        FlowRow(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(selected = activeFilter.isEmpty(), onClick = { filter = "" }, label = { Text(stringResource(R.string.filter_all)) })
            labels.forEach { l ->
                FilterChip(selected = activeFilter == l, onClick = { filter = l }, label = { Text(l, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
    }

    @Composable
    fun list() {
        if (all.isEmpty()) {
            Muted(stringResource(R.string.rec_empty), Modifier.padding(16.dp), MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEachIndexed { i, r ->
                RecordCard(
                    vm, r, i, shown.size, wide, selected = wide && r.id == current?.id,
                    onOpen = {
                        if (wide) picked = r.id else nav.push(Route.Result(pkg, r.id))
                    },
                    onDelete = { deleteId = r.id },
                )
            }
        }
    }

    if (wide) {
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TopBar(stringResource(R.string.records_title), stringResource(R.string.records_sub_wide, ps.label), { nav.pop() })
                }
                armSwitch(true)
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips()
                    list()
                }
                Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                    if (current != null) {
                        ResultBody(vm, nav, ps, current, true, onDelete = { deleteId = current.id })
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Muted(stringResource(R.string.rec_pick_none), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            TopBar("", null, { nav.pop() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Muted(stringResource(R.string.records_sub_wide, ps.label), style = MaterialTheme.typography.bodyMedium)
                    }
                    armSwitch(false)
                    chips()
                    list()
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(
            stringResource(R.string.rec_delete_confirm), null, stringResource(R.string.delete),
            destructive = true, onConfirm = { vm.deleteRecord(id) }, onDismiss = { deleteId = null },
        )
    }
}

@Composable
private fun RecordCard(
    vm: AppViewModel, r: RecordingInfo, i: Int, n: Int, wide: Boolean, selected: Boolean,
    onOpen: () -> Unit, onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val usable = r.hasData()
    val whenText = DateUtils.formatDateTime(ctx, r.startEpochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL)
    val profileText = ctx.modeName(r.mode) + " · " + ctx.presetLabel(r)
    val shape = if (wide) (if (selected) RoundedCornerShape(24.dp) else groupShape(i, n, 20.dp, 6.dp)) else groupShape(i, n, 24.dp)
    val container = if (selected) cs.primaryContainer else cs.surfaceContainer
    val contentColor = when {
        !usable -> cs.onSurfaceVariant.copy(alpha = 0.7f)
        selected -> cs.onPrimaryContainer
        else -> cs.onSurface
    }
    Surface(shape = shape, color = container, contentColor = contentColor, modifier = Modifier.fillMaxWidth()) {
        if (usable) {
            Column(
                Modifier.fillMaxWidth().selectable(selected = selected, role = Role.Button, onClick = onOpen)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(whenText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(formatClock(r.durationMs), style = MaterialTheme.typography.bodyMedium, color = if (selected) contentColor else cs.onSurfaceVariant)
                }
                Text(recordStats(ctx, vm.tf, r), style = MaterialTheme.typography.bodyMedium)
                if (wide) Muted(profileText) else Tag(profileText)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(whenText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(if (r.endReason == IGameTuning.END_INTERRUPTED) R.string.rec_failed else R.string.rec_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (wide) Muted(profileText) else Tag(profileText, container = cs.surfaceContainerHigh, content = contentColor)
                }
                IconButton(onClick = onDelete, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.rec_delete))
                }
            }
        }
    }
}

/** "112.6 fps · 42.1 C · 7.4 W" for a record card. */
private fun recordStats(ctx: Context, tf: TemperatureFormat, r: RecordingInfo): String {
    val s = r.summary
    val parts = ArrayList<String>()
    parts.add(ctx.getString(R.string.fps_avg_value, formatFloat1(s.avgFps.toDouble())))
    if (s.maxSkinC > 0f) parts.add(tf.absolute(s.maxSkinC.toDouble()))
    parts.add(if (s.avgPowerMw >= 0) formatPower(s.avgPowerMw) else ctx.getString(R.string.charging))
    return parts.joinToString(" · ")
}

// endregion

// region result

/** Board Result: one record on its own screen (portrait). */
@Composable
fun ResultScreen(vm: AppViewModel, nav: Nav, pkg: String, id: String) {
    val ps = vm.profile?.takeIf { it.pkg == pkg }
    val info = ps?.recordings?.firstOrNull { it.id == id }
    var confirm by rememberSaveable { mutableStateOf(false) }
    // The record can disappear (deleted here or by the daemon); go back to the list then.
    LaunchedEffect(ps != null, info == null) { if (ps != null && info == null) nav.pop() }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            "", null, { nav.pop() },
            actions = {
                if (info != null) IconButton(onClick = { confirm = true }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.rec_delete))
                }
                if (info != null && RecordExport.available(info)) ExportMenu(vm, info)
            },
        )
        if (ps == null || info == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ResultBody(vm, nav, ps, info, false, onDelete = { confirm = true }, modifier = Modifier.widthIn(max = 640.dp))
            }
        }
    }
    if (confirm && info != null) {
        ConfirmDialog(
            stringResource(R.string.rec_delete_confirm), null, stringResource(R.string.delete),
            destructive = true, onConfirm = { vm.deleteRecord(info.id) }, onDismiss = { confirm = false },
        )
    }
}

/**
 * The overflow menu (more options) of a result, used in the portrait top bar and in the LRecords pane.
 * "Export as CSV" asks the system file picker where to save, then writes the record in the background;
 * while it writes, the button shows a small progress ring and the menu item is disabled.
 */
@Composable
private fun ExportMenu(vm: AppViewModel, info: RecordingInfo) {
    var open by remember { mutableStateOf(false) }
    // The record the picker was opened for, so a later pick in the list cannot change what gets saved.
    var pending by remember { mutableStateOf<RecordingInfo?>(null) }
    val exporting = vm.exportingId == info.id
    val anyExporting = vm.exportingId != null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val record = pending
        pending = null
        // Cancelling the picker gives no uri and stays silent.
        if (uri != null && record != null) vm.exportRecord(record, uri)
    }
    val working = stringResource(R.string.export_working)
    Box {
        IconButton(onClick = { open = true }, Modifier.size(48.dp)) {
            if (exporting) {
                CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = working }, strokeWidth = 2.5.dp)
            } else {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (exporting) R.string.export_working else R.string.export_csv)) },
                enabled = !anyExporting,
                leadingIcon = if (exporting) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                onClick = {
                    open = false
                    pending = info
                    launcher.launch(vm.exportFileName(info))
                },
            )
        }
    }
}

private class Tile(
    val label: String, val value: String, val hint: String?, val container: Color, val content: Color,
)

@Composable
private fun MetricTile(t: Tile, wide: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, RoundedCornerShape(if (wide) 18.dp else 20.dp), t.container, t.content) {
        Column(
            Modifier.padding(horizontal = if (wide) 12.dp else 16.dp, vertical = if (wide) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 2.dp else 4.dp),
        ) {
            Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
            Text(
                t.value, style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (t.hint != null) Text(t.hint, style = MaterialTheme.typography.labelSmall, color = t.content.copy(alpha = 0.78f), maxLines = 2)
        }
    }
}

private fun displayRate(ctx: Context): Int = try {
    (ctx.display?.refreshRate ?: 60f).roundToInt().coerceAtLeast(30)
} catch (_: RuntimeException) {
    60
}

private fun signed(text: String, value: Double): String = when {
    value > 0.0 -> "+$text"
    value < 0.0 -> "−$text"
    else -> text
}

/**
 * The result of one record: headline, metric tiles, comparison, scenes and automatic changes,
 * charts and the two follow-up actions. [wide] selects the LRecords pane layout.
 */
@Composable
private fun ResultBody(
    vm: AppViewModel, nav: Nav, ps: ProfileState, info: RecordingInfo, wide: Boolean,
    onDelete: () -> Unit, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val tf = vm.tf
    val sum = info.summary
    val samples by produceState<List<RecordingSample>?>(null, info.id) {
        value = try { vm.samplesFor(info.id) } catch (_: TuningException) { emptyList() }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showAll by rememberSaveable(info.id) { mutableStateOf(false) }
    var showWhy by rememberSaveable(info.id) { mutableStateOf(false) }
    var pickCompare by rememberSaveable(info.id) { mutableStateOf(false) }
    var compareLabel by rememberSaveable(info.id) { mutableStateOf("") }

    val preset = remember(info.id) { vm.store.get(info.packageName, info.mode, info.profile?.variantId.orEmpty()) }
    val target = remember(info.id) {
        val d = preset?.display
        when {
            d != null && d.fpsLimit > 0 -> d.fpsLimit
            // The game's own cap seen in the record; the display rate overstates it for capped games
            info.summary.targetFps > 0 -> info.summary.targetFps
            d != null && d.lockRefresh -> vm.displayControl.refreshTarget(d, true).takeIf { it > 0 } ?: displayRate(ctx)
            else -> displayRate(ctx)
        }
    }
    val loaded = samples
    val hit = remember(loaded, target) { loaded?.let { hitPercent(it, target) } }
    val events = remember(loaded) { loaded?.let { deriveAutoEvents(it) }.orEmpty() }
    val label = ctx.presetLabel(info)
    val advice = !sum.mixed && sum.validSamples >= MIN_SAMPLES_FOR_ADVICE

    // Other presets of the same profile to compare with: the latest usable record of each.
    val others = remember(ps.recordings, info.id) {
        ps.recordings.filter { it.id != info.id && it.mode == info.mode && it.hasData() && ctx.presetLabel(it) != label }
            .sortedByDescending { it.startEpochMs }.distinctBy { ctx.presetLabel(it) }
    }
    val compareTo = others.firstOrNull { ctx.presetLabel(it) == compareLabel } ?: others.firstOrNull()

    val primaryTile = cs.primaryContainer
    val primaryOn = cs.onPrimaryContainer
    val warmTile = cs.tertiaryContainer
    val warmOn = cs.onTertiaryContainer
    val plainTile = cs.surfaceContainer
    val plainOn = cs.onSurface
    val dash = "—"
    val avg = Tile(
        ctx.getString(R.string.average_frame_rate), formatFloat1(sum.avgFps.toDouble()),
        null, primaryTile, primaryOn,
    )
    val floor = Tile(
        ctx.getString(R.string.steady_floor), formatFloat1(sum.floor5Fps.toDouble()),
        ctx.getString(R.string.tile_floor_hint, 95), plainTile, plainOn,
    )
    val temp = Tile(
        ctx.getString(R.string.max_temperature), if (sum.maxSkinC > 0f) tf.absolute(sum.maxSkinC.toDouble()) else dash,
        ctx.getString(R.string.tile_temp_hint), warmTile, warmOn,
    )
    val longest = if (sum.tail95 < 0f) null else Tile(
        ctx.getString(R.string.longest_frames), String.format(Locale.getDefault(), "%.1f×", 1.0 + sum.tail95),
        ctx.getString(if (wide) R.string.tile_longest_hint_l else R.string.tile_longest_hint), plainTile, plainOn,
    )
    val stutter = Tile(
        ctx.getString(R.string.stutter), formatFloat1(sum.jankPer1000.toDouble()),
        ctx.getString(R.string.tile_stutter_hint), plainTile, plainOn,
    )
    val power = Tile(
        ctx.getString(R.string.power_use), if (sum.avgPowerMw >= 0) formatPower(sum.avgPowerMw) else ctx.getString(R.string.charging),
        ctx.getString(if (sum.avgPowerMw >= 0) R.string.tile_power_hint else R.string.tile_power_none),
        plainTile, plainOn,
    )
    val allTiles = if (wide) listOfNotNull(avg, floor, longest, stutter, temp, power) else listOfNotNull(avg, floor, temp, longest, stutter, power)

    @Composable
    fun header() {
        val meta = buildAnnotatedString {
            append(DateUtils.formatDateTime(ctx, info.startEpochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL))
            append(" · "); append(formatClock(info.durationMs)); append(" · "); append(ctx.modeName(info.mode)); append(" · ")
            withStyle(SpanStyle(color = cs.onSurface, fontWeight = FontWeight.Medium)) { append(label) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (wide) 2.dp else 8.dp)) {
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text(
                headline(ctx, target, hit, sum.avgFps, cs.primary), fontWeight = FontWeight.Bold,
                style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            )
        }
    }

    @Composable
    fun compare() {
        if (compareTo == null) {
            Muted(stringResource(R.string.compare_none, ctx.modeName(info.mode)), style = MaterialTheme.typography.bodyMedium)
            return
        }
        val otherLabel = ctx.presetLabel(compareTo)
        val parts = ArrayList<String>()
        parts.add(ctx.getString(R.string.compare_avg, signed(formatFloat1(abs(sum.avgFps - compareTo.summary.avgFps).toDouble()), (sum.avgFps - compareTo.summary.avgFps).toDouble())))
        if (sum.maxSkinC > 0f && compareTo.summary.maxSkinC > 0f) {
            val d = (sum.maxSkinC - compareTo.summary.maxSkinC).toDouble()
            parts.add(ctx.getString(R.string.compare_temp, signed(tf.delta(abs(d)), d)))
        }
        if (sum.avgPowerMw >= 0 && compareTo.summary.avgPowerMw >= 0) {
            val d = (sum.avgPowerMw - compareTo.summary.avgPowerMw) / 1000.0
            parts.add(ctx.getString(R.string.compare_power, signed(formatFloat1(abs(d)), d)))
        }
        val values = parts.joinToString(" · ")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (wide) {
                    Text(stringResource(R.string.compare_inline, otherLabel, values), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.compare_title, otherLabel), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(values, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            if (others.size > 1) {
                OutlinedButton(onClick = { pickCompare = true }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.change))
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        }
    }

    @Composable
    fun scenesAndAuto() {
        val scenes = sum.scenesSeen?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { NumberFormat.getIntegerInstance().format(it) }
        val auto = if (events.isEmpty()) stringResource(R.string.auto_none)
        else pluralStringResource(R.plurals.auto_times, events.size, events.size)
        val text = listOfNotNull(scenes?.let { stringResource(R.string.scenes_label, it) }, auto).joinToString(" · ")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (events.isNotEmpty()) {
                TextButton(onClick = { showWhy = !showWhy }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.see_why))
                    Icon(if (showWhy) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        }
    }

    @Composable
    fun why() {
        if (!showWhy || events.isEmpty()) return
        Surface(
            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
            color = if (wide) cs.tertiaryContainer else cs.surfaceContainerLow,
            contentColor = if (wide) cs.onTertiaryContainer else cs.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEach { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(10.dp).background(cs.tertiary, CircleShape))
                        Text(eventLine(ctx, tf, e), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    @Composable
    fun charts() {
        val tabs = listOf(
            0 to stringResource(R.string.tab_frames), 1 to stringResource(R.string.tab_speed),
            2 to stringResource(R.string.tab_temp), 3 to stringResource(R.string.tab_power),
        )
        Surface(shape = RoundedCornerShape(if (wide) 24.dp else 28.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = if (wide) 16.dp else 20.dp, vertical = if (wide) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectedChoice(tabs, tab, { tab = it })
                val data = loaded
                if (data == null) {
                    Box(Modifier.fillMaxWidth().height(if (wide) 150.dp else 204.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val limits = ps.limits
                    val duration = maxOf(info.durationMs, data.lastOrNull()?.timeMs ?: 0)
                    val spec = remember(data, tab, target, limits) { chartSpec(ctx, cs, tab, data, limits, tf, target) }
                    RecordChart(spec, duration, events, if (wide) 150.dp else 204.dp)
                }
            }
        }
    }

    @Composable
    fun actions() {
        val enabled = advice
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { nav.push(Route.Suggest(info.packageName, info.id)) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.suggest_title))
                }
                FilledTonalButton(onClick = { nav.push(Route.AutoTune(info.packageName, info.id)) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.auto_title))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.push(Route.Suggest(info.packageName, info.id)) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Lightbulb, null, Modifier.size(22.dp))
                    Text(stringResource(R.string.suggest_title), Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = { nav.push(Route.AutoTune(info.packageName, info.id)) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Autorenew, null, Modifier.size(22.dp))
                    Text(stringResource(R.string.auto_title), Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    fun adviceNote() {
        if (advice) return
        Muted(stringResource(if (sum.mixed) R.string.rec_mixed else R.string.rec_short), style = MaterialTheme.typography.bodyMedium)
    }

    if (wide) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { header() }
                IconButton(onClick = onDelete, Modifier.size(48.dp)) { Icon(Icons.Rounded.Delete, stringResource(R.string.rec_delete)) }
                if (RecordExport.available(info)) ExportMenu(vm, info)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTiles.forEach { MetricTile(it, true, Modifier.weight(1f)) }
                if (allTiles.size < 6) Spacer(Modifier.weight((6 - allTiles.size).toFloat()))
            }
            compare()
            charts()
            why()
            scenesAndAuto()
            adviceNote()
            actions()
        }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)) { header() }
            val rows = (if (showAll) allTiles else allTiles.take(3)).chunked(3)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { MetricTile(it, false, Modifier.weight(1f)) }
                    if (row.size < 3) Spacer(Modifier.weight((3 - row.size).toFloat()))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showAll = !showAll }, Modifier.heightIn(min = 48.dp)) {
                    Text(if (showAll) stringResource(R.string.show_less) else stringResource(R.string.show_all, allTiles.size))
                    Icon(if (showAll) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp))
                }
                Muted(stringResource(R.string.left_out_note), Modifier.padding(end = 8.dp).weight(1f, fill = false))
            }
            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { compare() }
            }
            Box(Modifier.padding(horizontal = 8.dp)) { scenesAndAuto() }
            why()
            adviceNote()
            Spacer(Modifier.size(4.dp))
            charts()
            Spacer(Modifier.size(8.dp))
            actions()
        }
    }

    if (pickCompare) {
        val opts = others.mapIndexed { i, r -> i to ctx.presetLabel(r) }
        ChoiceDialog(
            stringResource(R.string.compare_pick), opts, others.indexOfFirst { it === compareTo }.takeIf { it >= 0 },
            { compareLabel = ctx.presetLabel(others[it]) }, { pickCompare = false },
        )
    }
}

private fun headline(ctx: Context, target: Int, hit: Int?, avgFps: Float, accent: Color): AnnotatedString {
    val targetText = ctx.getString(R.string.fps_value, target)
    val text = when {
        hit == null -> ctx.getString(R.string.result_head_avg, formatFloat1(avgFps.toDouble()))
        hit >= 50 -> ctx.getString(R.string.result_head_kept, targetText, hit)
        else -> ctx.getString(R.string.result_head_missed, targetText, hit)
    }
    val at = text.indexOf(targetText)
    return buildAnnotatedString {
        append(text)
        if (at >= 0) addStyle(SpanStyle(color = accent), at, at + targetText.length)
    }
}

/** "8:31 · Graphics top speed lowered for 3 min at 42 C". */
private fun eventLine(ctx: Context, tf: TemperatureFormat, e: AutoEvent): String {
    val seconds = e.durationMs / 1000
    val duration = if (seconds >= 60) ctx.getString(R.string.dur_min, (seconds / 60.0).roundToInt().coerceAtLeast(1))
    else ctx.getString(R.string.dur_sec, seconds)
    val res = when (e.resource) {
        Resource.GOLD -> R.string.res_cpu
        Resource.PRIME -> R.string.res_fastest
        else -> R.string.res_graphics
    }
    val name = ctx.getString(res)
    return if (hasTemp(e.skinMilliC)) {
        ctx.getString(R.string.event_line, formatClock(e.timeMs), name, duration, tf.absoluteRounded(e.skinMilliC / 1000.0))
    } else {
        ctx.getString(R.string.event_line_cool, formatClock(e.timeMs), name, duration)
    }
}

private fun tickNumber(v: Float): String {
    val f = NumberFormat.getNumberInstance()
    f.maximumFractionDigits = if (abs(v - v.roundToInt()) < 0.001f) 0 else 1
    return f.format(v)
}

/** The series, axis and legend of one chart tab; every series is cut to 600 points. */
private fun chartSpec(
    ctx: Context, cs: ColorScheme, tab: Int, samples: List<RecordingSample>, limits: PolicyLimits,
    tf: TemperatureFormat, target: Int,
): ChartSpec {
    val s1 = cs.primary
    val s2 = cs.tertiary
    val s3 = cs.onSurface
    fun peak(vararg b: io.github.miner7222.gametuning.data.Bucketed): Float =
        b.flatMap { it.values.asList() }.filter { !it.isNaN() }.maxOrNull() ?: Float.NaN
    fun trough(vararg b: io.github.miner7222.gametuning.data.Bucketed): Float =
        b.flatMap { it.values.asList() }.filter { !it.isNaN() }.minOrNull() ?: Float.NaN
    return when (tab) {
        0 -> {
            val fps = bucketAverage(samples) { if (it.fps >= 0f) it.fps else Float.NaN }
            // The target each second was judged against; with none recorded, the headline target drawn flat.
            val steps = if (samples.any { it.targetFps > 0f }) {
                bucketHeldLongest(samples) { if (it.targetFps > 0f) it.targetFps else Float.NaN }
            } else null
            val highTarget = if (steps != null) peak(steps).let { if (it.isNaN()) 0f else it * 1.02f } else target * 1.02f
            val top = maxOf(peak(fps).let { if (it.isNaN()) 0f else it }, highTarget)
            // One number in the legend when the target never changes, else the plain wording.
            val legend = when {
                steps == null -> ctx.getString(R.string.chart_target_legend, target)
                trough(steps) == peak(steps) -> ctx.getString(R.string.chart_target_legend, peak(steps).roundToInt())
                else -> ctx.getString(R.string.chart_target_legend_varies)
            }
            ChartSpec(
                ctx.getString(R.string.chart_fps_desc),
                listOf(ChartSeries(ctx.getString(R.string.chart_fps_legend), s1, 2.dp, false, fps)),
                niceAxis(0f, top, 0f),
                { v, isTop -> if (isTop) ctx.getString(R.string.fps_axis_top, tickNumber(v)) else tickNumber(v) },
                target = if (steps == null) target.toFloat() else null, targetSteps = steps, targetLegend = legend,
            )
        }
        1 -> {
            fun pct(r: Int) = bucketAverage(samples) {
                val cur = it.curOf(r)
                if (cur > 0) fractionOfTop(limits, r, cur) * 100f else Float.NaN
            }
            ChartSpec(
                ctx.getString(R.string.chart_speed_desc),
                listOf(
                    ChartSeries(ctx.getString(R.string.res_cpu), s1, 2.dp, false, pct(Resource.GOLD)),
                    ChartSeries(ctx.getString(R.string.res_fastest), s2, 3.dp, false, pct(Resource.PRIME)),
                    ChartSeries(ctx.getString(R.string.chart_graphics_pct), s3, 2.dp, true, pct(Resource.GPU)),
                ),
                Axis(0f, 100f, listOf(25f, 50f, 75f)),
                { v, _ -> String.format(Locale.getDefault(), "%d%%", v.roundToInt()) },
            )
        }
        2 -> {
            fun series(read: (RecordingSample) -> Int) =
                bucketAverage(samples) { if (hasTemp(read(it))) tf.convert(read(it) / 1000.0).toFloat() else Float.NaN }
            val skin = series { it.skinMilliC }
            val cpu = series { it.cpuMilliC }
            val gpu = series { it.gpuMilliC }
            val lo = trough(skin, cpu, gpu).let { if (it.isNaN()) tf.convert(25.0).toFloat() else it }
            val hi = peak(skin, cpu, gpu).let { if (it.isNaN()) tf.convert(45.0).toFloat() else it }
            ChartSpec(
                ctx.getString(R.string.chart_temp_desc),
                listOf(
                    ChartSeries(ctx.getString(R.string.chart_surface), s1, 2.dp, false, skin),
                    ChartSeries(ctx.getString(R.string.res_cpu), s2, 3.dp, false, cpu),
                    ChartSeries(ctx.getString(R.string.chart_gpu_temp), s3, 2.dp, true, gpu),
                ),
                niceAxis(lo, hi),
                { v, _ -> tf.absoluteRounded(if (tf.fahrenheit) (v - 32.0) * 5.0 / 9.0 else v.toDouble()) },
            )
        }
        else -> {
            val power = bucketAverage(samples) { if (it.powerMw >= 0) it.powerMw / 1000f else Float.NaN }
            ChartSpec(
                ctx.getString(R.string.chart_power_desc),
                listOf(ChartSeries(ctx.getString(R.string.chart_power_legend), s1, 2.dp, false, power)),
                niceAxis(0f, peak(power).let { if (it.isNaN()) 1f else it }, 0f),
                { v, isTop -> if (isTop) ctx.getString(R.string.power_axis_top, tickNumber(v)) else tickNumber(v) },
                emptyNote = ctx.getString(R.string.chart_power_empty),
            )
        }
    }
}

// endregion
