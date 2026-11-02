// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.miner7222.gametuning.R

@Composable
fun GraphicsSection(vm: AppViewModel, st: TuningState) {
    val options = st.display.graphics
    val default = stringResource(R.string.graphics_default)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GraphicsChoice(
            R.string.graphics_texture_filtering, R.string.graphics_texture_filtering_desc, options.textureFiltering,
            listOf("" to default, "Balanced" to stringResource(R.string.mode_balanced), "Fastest" to stringResource(R.string.mode_performance)),
            enabled = !vm.busy,
        ) { value -> vm.editGraphics(R.string.graphics_texture_filtering) { it.copy(textureFiltering = value) } }
        GraphicsChoice(
            R.string.graphics_anisotropic_filtering, R.string.graphics_anisotropic_filtering_desc, options.anisotropicFiltering,
            listOf("" to default) + listOf(1, 2, 4, 8, 16).map { "$it.0" to "${it}x" },
            enabled = !vm.busy,
        ) { value -> vm.editGraphics(R.string.graphics_anisotropic_filtering) { it.copy(anisotropicFiltering = value) } }
        GraphicsChoice(
            R.string.graphics_mipmap_lod_bias, R.string.graphics_mipmap_lod_bias_desc, options.mipmapLodBias,
            listOf(-4, -2, -1, 0, 1, 2, 4).map { if (it == 0) "" to default else "$it.0" to "$it" },
            enabled = !vm.busy,
        ) { value -> vm.editGraphics(R.string.graphics_mipmap_lod_bias) { it.copy(mipmapLodBias = value) } }
        GraphicsChoice(
            R.string.graphics_msaa_limit, R.string.graphics_msaa_limit_desc, options.msaaLimit,
            listOf("" to default, "1" to "1x", "2" to "2x", "4" to "4x"),
            enabled = !vm.busy,
        ) { value -> vm.editGraphics(R.string.graphics_msaa_limit) { it.copy(msaaLimit = value) } }
        Muted(stringResource(R.string.graphics_restart_note), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GraphicsChoice(
    label: Int, desc: Int, selected: String, choices: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Muted(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
        }
        ConnectedChoice(
            choices, selected, onSelect, Modifier.fillMaxWidth(), enabled = enabled,
            wrapColumns = if (choices.size > 4 && LocalDensity.current.fontScale < 1.3f) 3 else 2,
            shortOf = { value -> choices.first { it.first == value }.second },
        )
    }
}
