// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.miner7222.gametuning.R
import kotlin.math.roundToInt

/** Corner shape for item [i] of [n] in a grouped list: large outside, small between neighbours. */
fun groupShape(i: Int, n: Int, big: Dp = 28.dp, small: Dp = 8.dp): Shape =
    if (n <= 1) RoundedCornerShape(big) else when (i) {
        0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomEnd = small, bottomStart = small)
        n - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = big, bottomStart = big)
        else -> RoundedCornerShape(small)
    }

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor) {
        Column(content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(horizontal = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall) {
    Text(text, modifier = modifier, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Centered empty state: an icon, a short title and optional actions, on a tonal card. */
@Composable
fun EmptyState(
    icon: ImageVector, title: String, modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    TonalCard(modifier.widthIn(max = 420.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium)
            actions()
        }
    }
}

/** The top bar used by every sub screen: back arrow, title, optional subtitle and actions. */
@Composable
fun TopBar(
    title: String, subtitle: String? = null, onBack: (() -> Unit)?, modifier: Modifier = Modifier,
    subtitleMaxLines: Int = Int.MAX_VALUE, actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
fun PageTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) Muted(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Connected button group with one selected button: 2 dp apart, 24 dp outer and 6 dp inner corners,
 * and the selected button becomes a full pill. With [wrapColumns] set, a group narrower than
 * [wrapBelow] becomes a connected grid with that many buttons per row.
 */
@Composable
fun <T> ConnectedChoice(
    options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, height: Dp = 48.dp, enabled: Boolean = true,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    optionEnabled: (T) -> Boolean = { true },
    wrapColumns: Int = 0, wrapBelow: Dp = 480.dp,
) {
    if (wrapColumns <= 0 || wrapColumns >= options.size) {
        ChoiceGrid(options, selected, onSelect, modifier, height, enabled, labelStyle, optionEnabled, options.size)
    } else {
        BoxWithConstraints(modifier) {
            val columns = if (maxWidth < wrapBelow) wrapColumns else options.size
            ChoiceGrid(options, selected, onSelect, Modifier.fillMaxWidth(), height, enabled, labelStyle, optionEnabled, columns)
        }
    }
}

@Composable
private fun <T> ChoiceGrid(
    options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier,
    height: Dp, enabled: Boolean, labelStyle: androidx.compose.ui.text.TextStyle, optionEnabled: (T) -> Boolean,
    columns: Int,
) {
    val cs = MaterialTheme.colorScheme
    val outer = 24.dp
    val inner = 6.dp
    val rows = options.chunked(columns.coerceAtLeast(1))
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { c, (value, label) ->
                    val top = r == 0
                    val bottom = r == rows.lastIndex
                    val left = c == 0
                    val right = c == row.lastIndex
                    val shape = RoundedCornerShape(
                        topStart = if (top && left) outer else inner, bottomStart = if (bottom && left) outer else inner,
                        topEnd = if (top && right) outer else inner, bottomEnd = if (bottom && right) outer else inner,
                    )
                    val shapes = ToggleButtonShapes(shape = shape, pressedShape = shape, checkedShape = CircleShape)
                    ToggleButton(
                        checked = value == selected,
                        onCheckedChange = { onSelect(value) },
                        enabled = enabled && optionEnabled(value),
                        modifier = Modifier.weight(1f).height(height).semantics { role = Role.RadioButton },
                        shapes = shapes,
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface,
                            checkedContainerColor = cs.primary,
                            checkedContentColor = cs.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle)
                    }
                }
            }
        }
    }
}

/** The search box of the game list and the import list, with a clear button while it holds text. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = query, onValueChange = onQuery, singleLine = true, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_games)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onQuery("") }, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
            }
        }) else null,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
        ),
    )
}

/** A row with a title, a description and a trailing switch. */
@Composable
fun SwitchRow(
    title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, minHeight: Dp = if (description == null) 56.dp else 72.dp,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            if (description != null) Muted(description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A row that opens a picker or a sub screen. */
@Composable
fun NavRow(
    title: String, description: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
    value: String? = null, icon: ImageVector? = null, enabled: Boolean = true, minHeight: Dp = if (description == null) 56.dp else 72.dp,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clickable(enabled = enabled, onClick = onClick).padding(padding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Muted(description, style = MaterialTheme.typography.bodyMedium)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Plain value slider that commits when the finger is lifted. */
@Composable
fun SliderField(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    format: (Float) -> String, onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier, description: String? = null, minLabel: String? = null,
    maxLabel: String? = null, enabled: Boolean = true, padding: PaddingValues = PaddingValues(0.dp),
    showValue: Boolean = true, trailing: @Composable () -> Unit = {},
) {
    var drag by remember(value) { mutableFloatStateOf(value) }
    // A switched-off slider is one flat track in onSurface at 12 percent.
    val flat = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(modifier.padding(padding)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (showValue) Text(format(drag), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            trailing()
        }
        Slider(
            value = drag, onValueChange = { drag = it }, valueRange = range, steps = steps, enabled = enabled,
            onValueChangeFinished = { onCommit(drag) },
            colors = SliderDefaults.colors(
                disabledActiveTrackColor = flat, disabledInactiveTrackColor = flat,
                disabledActiveTickColor = flat, disabledInactiveTickColor = flat,
            ),
            modifier = Modifier.semantics { contentDescription = label },
        )
        if (minLabel != null || maxLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted(minLabel.orEmpty()); Muted(maxLabel.orEmpty())
            }
        }
        if (description != null) Muted(description, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Range slider over a ladder of device speeds. The two handles are ladder indices, each with its own
 * value under it; the value text opens the exact-step picker and the reset icon puts the row back to
 * the built-in values.
 */
@Composable
fun SpeedRange(
    label: String, top: Int, lo: Int, hi: Int, valueText: (Int, Int) -> String, pointText: (Int) -> String,
    onCommit: (Int, Int) -> Unit, onPick: () -> Unit, onReset: (() -> Unit)?, note: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    var dragLo by remember(lo, hi) { mutableFloatStateOf(lo.toFloat()) }
    var dragHi by remember(lo, hi) { mutableFloatStateOf(hi.toFloat()) }
    val thumbSize = DpSize(20.dp, 20.dp)
    val trackHeight = 4.dp
    val startSource = remember { MutableInteractionSource() }
    val endSource = remember { MutableInteractionSource() }
    val span = top.toFloat().coerceAtLeast(1f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = label
            }) {
                Text(valueText(dragLo.toInt(), dragHi.toInt()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (onReset != null) {
                IconButton(onClick = onReset, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Restore, stringResource(R.string.cd_reset_row, label))
                }
            } else {
                Spacer(Modifier.width(0.dp))
            }
        }
        RangeSlider(
            value = dragLo..dragHi,
            onValueChange = { dragLo = it.start; dragHi = it.endInclusive },
            modifier = Modifier.requiredSizeIn(minWidth = thumbSize.width, minHeight = trackHeight),
            enabled = enabled && top > 0,
            valueRange = 0f..span,
            onValueChangeFinished = { onCommit(dragLo.toInt(), dragHi.toInt()) },
            startInteractionSource = startSource,
            endInteractionSource = endSource,
            startThumb = {
                SliderDefaults.Thumb(
                    interactionSource = startSource, enabled = enabled && top > 0,
                    modifier = Modifier.size(thumbSize).shadow(1.dp, CircleShape, clip = false),
                )
            },
            endThumb = {
                SliderDefaults.Thumb(
                    interactionSource = endSource, enabled = enabled && top > 0,
                    modifier = Modifier.size(thumbSize).shadow(1.dp, CircleShape, clip = false),
                )
            },
            track = {
                SliderDefaults.Track(
                    rangeSliderState = it, enabled = enabled && top > 0,
                    modifier = Modifier.height(trackHeight),
                    thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp,
                    drawStopIndicator = null, drawTick = { _, _ -> },
                )
            },
            steps = (top - 1).coerceAtLeast(0),
        )
        HandleLabels(dragLo / span, dragHi / span, thumbSize.width / 2, Modifier.padding(bottom = 4.dp),
            { Muted(pointText(dragLo.toInt())) },
            { Muted(pointText(dragHi.toInt())) },
        )
        note()
    }
}

/** Places [start] and [end] under their slider handles; [inset] is half a handle. */
@Composable
private fun HandleLabels(
    startFraction: Float, endFraction: Float, inset: Dp, modifier: Modifier = Modifier,
    start: @Composable () -> Unit, end: @Composable () -> Unit,
) {
    Layout(content = { Box { start() }; Box { end() } }, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val s = measurables[0].measure(loose)
        val e = measurables[1].measure(loose)
        val w = constraints.maxWidth
        val insetPx = inset.roundToPx()
        val gap = 8.dp.roundToPx()
        fun center(f: Float) = insetPx + (f.coerceIn(0f, 1f) * (w - 2 * insetPx)).roundToInt()
        var sx = (center(startFraction) - s.width / 2).coerceIn(0, (w - s.width).coerceAtLeast(0))
        var ex = (center(endFraction) - e.width / 2).coerceIn(0, (w - e.width).coerceAtLeast(0))
        if (ex < sx + s.width + gap) {
            ex = sx + s.width + gap
            if (ex + e.width > w) {
                ex = (w - e.width).coerceAtLeast(0)
                sx = (ex - gap - s.width).coerceAtLeast(0)
            }
        }
        layout(w, maxOf(s.height, e.height)) {
            s.place(sx, 0)
            e.place(ex, 0)
        }
    }
}

@Composable
fun ChoiceDialog(
    title: String, options: List<Pair<Int, String>>, selected: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit,
    text: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 340.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title) },
        text = {
            Column {
                if (text != null) Muted(text, Modifier.padding(bottom = 8.dp), MaterialTheme.typography.bodyMedium)
                // Eight rows of 48 dp.
                LazyColumn(Modifier.heightIn(max = 384.dp).selectableGroup()) {
                    itemsIndexed(options) { _, (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value); onDismiss() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Text(label, Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Exact-step picker: choose the lowest and the highest speed from the device's steps. */
@Composable
fun ExactStepDialog(
    title: String, labels: List<String>, lo: Int, hi: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit,
) {
    var newLo by remember { mutableStateOf(lo) }
    var newHi by remember { mutableStateOf(hi) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (side in 0..1) {
                    // Both lists open on the current choice.
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = ((if (side == 0) lo else hi) - 2).coerceIn(0, (labels.size - 1).coerceAtLeast(0)),
                    )
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(if (side == 0) R.string.lowest else R.string.highest))
                        LazyColumn(Modifier.heightIn(max = 320.dp).selectableGroup(), state = listState) {
                            itemsIndexed(labels) { index, text ->
                                val on = if (side == 0) index == newLo else index == newHi
                                // A highest step below the lowest choice cannot be picked.
                                val allowed = side == 0 || index >= newLo
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(selected = on, enabled = allowed, role = Role.RadioButton) {
                                            if (side == 0) { newLo = index; if (newHi < index) newHi = index }
                                            else newHi = index
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = on, onClick = null, enabled = allowed)
                                    Text(
                                        text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium,
                                        color = if (allowed) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(newLo, newHi); onDismiss() }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String, body: String?, confirm: String, destructive: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (body != null) ({ Text(body) }) else null,
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Small rounded tag such as "In use" or the "<profile> · <preset>" chip. */
@Composable
fun Tag(
    text: String, modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingDot: Boolean = false,
    outlined: Boolean = false,
) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(container)
            .then(if (outlined) Modifier.pillBorder(outline, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDot) Box(Modifier.size(8.dp).clip(CircleShape).background(content))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Square app tile: the game's icon, or its first letter. */
@Composable
fun GameAvatar(label: String, icon: ImageBitmap?, size: Dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 3.5f))
            .background(if (selected) cs.primary else cs.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize())
        } else {
            Text(
                label.take(1).uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                color = if (selected) cs.onPrimary else cs.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun outlinedPillBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

fun Modifier.pillBorder(color: Color, shape: Shape = RoundedCornerShape(24.dp)): Modifier = border(1.dp, color, shape)
