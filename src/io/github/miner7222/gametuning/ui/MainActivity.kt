// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.miner7222.gametuning.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miner7222.gametuning.R
import io.github.miner7222.gametuning.data.Modes
import io.github.miner7222.gametuning.service.SessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens of the app. Encoded as strings so the back stack survives rotation and process death. */
sealed class Route {
    object Home : Route()
    data class Profile(val pkg: String) : Route()
    data class Presets(val pkg: String, val mode: Int) : Route()
    data class Tuning(val pkg: String, val mode: Int) : Route()
    data class Expert(val pkg: String, val mode: Int) : Route()
    data class Steps(val pkg: String, val mode: Int) : Route()
    data class Import(val pkg: String, val mode: Int) : Route()
    data class Copy(val pkg: String, val mode: Int, val presetId: String) : Route()
    data class Records(val pkg: String) : Route()
    data class Result(val pkg: String, val recordId: String) : Route()
    data class Suggest(val pkg: String, val recordId: String) : Route()
    data class AutoTune(val pkg: String, val recordId: String) : Route()

    fun encode(): String = when (this) {
        Home -> "home"
        is Profile -> "profile|$pkg"
        is Presets -> "presets|$pkg|$mode"
        is Tuning -> "tuning|$pkg|$mode"
        is Expert -> "expert|$pkg|$mode"
        is Steps -> "steps|$pkg|$mode"
        is Import -> "import|$pkg|$mode"
        is Copy -> "copy|$pkg|$mode|$presetId"
        is Records -> "records|$pkg"
        is Result -> "result|$pkg|$recordId"
        is Suggest -> "suggest|$pkg|$recordId"
        is AutoTune -> "autotune|$pkg|$recordId"
    }

    val packageName: String?
        get() = when (this) {
            Home -> null
            is Profile -> pkg
            is Presets -> pkg
            is Tuning -> pkg
            is Expert -> pkg
            is Steps -> pkg
            is Import -> pkg
            is Copy -> pkg
            is Records -> pkg
            is Result -> pkg
            is Suggest -> pkg
            is AutoTune -> pkg
        }

    companion object {
        fun decode(text: String): Route {
            val p = text.split("|")
            fun mode() = p.getOrNull(2)?.toIntOrNull() ?: Modes.BALANCED
            return when (p[0]) {
                "profile" -> Profile(p[1])
                "presets" -> Presets(p[1], mode())
                "tuning" -> Tuning(p[1], mode())
                "expert" -> Expert(p[1], mode())
                "steps" -> Steps(p[1], mode())
                "import" -> Import(p[1], mode())
                "copy" -> Copy(p[1], mode(), p.getOrElse(3) { "" })
                "records" -> Records(p[1])
                "result" -> Result(p[1], p.getOrElse(2) { "" })
                "suggest" -> Suggest(p[1], p.getOrElse(2) { "" })
                "autotune" -> AutoTune(p[1], p.getOrElse(2) { "" })
                else -> Home
            }
        }
    }
}

class Nav(private val stack: SnapshotStateList<String>) {
    val top: Route get() = Route.decode(stack.last())
    val below: Route? get() = if (stack.size > 1) Route.decode(stack[stack.size - 2]) else null
    val canPop: Boolean get() = stack.size > 1
    fun push(route: Route) { stack.add(route.encode()) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(route: Route) { stack[stack.lastIndex] = route.encode() }
    fun popToHome() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        SessionService.start(this)
        if (savedInstanceState == null) deepLink = packageFrom(intent)
        setContent {
            GameTuningTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm, deepLink) { deepLink = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        packageFrom(intent)?.let { deepLink = it }
    }

    private fun packageFrom(intent: Intent?): String? =
        if (intent?.action == ACTION_GAME_TUNING) intent.getStringExtra(EXTRA_PACKAGE_NAME) else null

    companion object {
        const val ACTION_GAME_TUNING = "io.github.miner7222.gametuning.action.GAME_TUNING"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}

@Composable
fun AppRoot(vm: AppViewModel, deepLink: String?, onDeepLinkConsumed: () -> Unit) {
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf("home") }
    val nav = remember { Nav(stack) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<UiMessage?>(null) }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.messages.collect { shown = it } }
    // The stock snackbar host animates with a graphics class this build's UI library lacks, so a
    // plain bar is shown for eight seconds instead.
    LaunchedEffect(shown) {
        if (shown != null) {
            kotlinx.coroutines.delay(8000)
            shown = null
        }
    }

    LaunchedEffect(deepLink, vm.cards.size) {
        val pkg = deepLink ?: return@LaunchedEffect
        selected = pkg
        nav.popToHome()
        nav.push(Route.Profile(pkg))
        onDeepLinkConsumed()
    }

    BackHandler(enabled = nav.canPop) { nav.pop() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 840.dp
            val top = nav.top
            when (vm.available) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                false -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.not_available), style = MaterialTheme.typography.titleMedium)
                }
                else -> {
                    val wideSelected = selected?.takeIf { p -> vm.cards.any { it.pkg == p } } ?: vm.cards.firstOrNull()?.pkg
                    // Which game and which profile the loaded state should describe.
                    val activePkg = when {
                        top is Route.Home -> if (wide) wideSelected else null
                        else -> top.packageName
                    }
                    LaunchedEffect(activePkg) { vm.showProfile(activePkg) }
                    val tuningKey = when (top) {
                        is Route.Tuning -> top.pkg to top.mode
                        is Route.Expert -> top.pkg to top.mode
                        is Route.Steps -> top.pkg to top.mode
                        else -> null
                    }
                    LaunchedEffect(tuningKey) { vm.showTuning(tuningKey?.first, tuningKey?.second ?: 0) }
                    if (top is Route.Profile) LaunchedEffect(top.pkg) { selected = top.pkg }

                    Box(Modifier.fillMaxSize()) {
                        val dialogOver = top is Route.Copy && wide
                        Screen(vm, nav, if (dialogOver) (nav.below ?: Route.Home) else top, wide, wideSelected) { selected = it }
                        if (dialogOver) CopyDialog(vm, nav, top as Route.Copy)
                    }
                }
            }
            shown?.let { m ->
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 560.dp),
                    action = {
                        if (m.undo != null) {
                            androidx.compose.material3.TextButton(onClick = {
                                shown = null
                                m.undo.let { u -> scope.launch(Dispatchers.IO) { u() } }
                            }) { Text(undoLabel, color = MaterialTheme.colorScheme.inversePrimary) }
                        }
                    },
                ) { Text(m.text) }
            }
        }
    }
}

@Composable
private fun Screen(
    vm: AppViewModel, nav: Nav, route: Route, wide: Boolean, wideSelected: String?, select: (String) -> Unit,
) {
    val pick: (String) -> Unit = { pkg ->
        select(pkg)
        if (wide) {
            if (nav.top is Route.Profile) nav.replaceTop(Route.Profile(pkg)) else nav.push(Route.Profile(pkg))
        } else {
            nav.push(Route.Profile(pkg))
        }
    }
    when (route) {
        Route.Home, is Route.Profile -> {
            if (wide) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    GameListPane(vm, true, wideSelected, pick, Modifier.width(360.dp).fillMaxSize())
                    Surface(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerLow) {
                        if (wideSelected != null) ProfilePane(vm, wideSelected, true, nav) else Box(Modifier.fillMaxSize())
                    }
                }
            } else if (route is Route.Profile) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(title = "", onBack = { nav.pop() })
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        ProfilePane(vm, route.pkg, false, nav, Modifier.widthIn(max = 640.dp))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    GameListPane(vm, false, null, pick, Modifier.widthIn(max = 640.dp).fillMaxSize())
                }
            }
        }
        is Route.Presets -> PresetsScreen(vm, nav, route.pkg, route.mode, wide)
        is Route.Copy -> CopyScreen(vm, nav, route)
        is Route.Tuning -> TuningScreen(vm, nav, route.pkg, route.mode, wide)
        is Route.Expert -> ExpertScreen(vm, nav, route.pkg, route.mode, wide)
        is Route.Steps -> StepsScreen(vm, nav, route.pkg, route.mode, wide)
        is Route.Import -> ImportScreen(vm, nav, route.pkg, route.mode, wide)
        is Route.Records -> RecordsScreen(vm, nav, route.pkg, wide)
        is Route.Result -> if (wide) RecordsScreen(vm, nav, route.pkg, true, route.recordId) else ResultScreen(vm, nav, route.pkg, route.recordId)
        is Route.Suggest -> SuggestScreen(vm, nav, route.pkg, route.recordId, wide)
        is Route.AutoTune -> AutoTuneScreen(vm, nav, route.pkg, route.recordId, wide)
    }
}
