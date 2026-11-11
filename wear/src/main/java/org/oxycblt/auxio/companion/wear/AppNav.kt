/*
 * Copyright (c) 2026 Auxio Project
 * AppNav.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.companion.wear

import android.app.RemoteInput
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.input.RemoteInputIntentHelper
import org.oxycblt.auxio.companion.protocol.TabKind
import org.oxycblt.auxio.companion.wear.ui.HomeScreen
import org.oxycblt.auxio.companion.wear.ui.ListScreen
import org.oxycblt.auxio.companion.wear.ui.MessageScreen
import org.oxycblt.auxio.companion.wear.ui.Palette
import org.oxycblt.auxio.companion.wear.ui.PlayerScreen
import org.oxycblt.auxio.companion.wear.ui.SRC_BROWSE
import org.oxycblt.auxio.companion.wear.ui.SRC_SEARCH
import org.oxycblt.auxio.companion.wear.ui.SleepScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_SLEEP = "sleep"
private const val ROUTE_LIST = "list/{src}/{arg}/{title}/{play}"
private const val SEARCH_KEY = "query"

// Route arguments are base64 so ids and titles can contain anything. A leading "x" keeps empty
// strings (the library root) from producing an empty path segment.
private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

private fun enc(s: String) = "x" + Base64.encodeToString(s.toByteArray(Charsets.UTF_8), B64)

private fun dec(s: String?): String =
    if (s == null || s.length < 2) "" else String(Base64.decode(s.substring(1), B64), Charsets.UTF_8)

private fun listRoute(src: String, arg: String, title: String, play: Boolean) =
    "list/$src/${enc(arg)}/${enc(title)}/${if (play) 1 else 0}"

private fun NavController.openPlayer() {
    navigate(ROUTE_PLAYER) { launchSingleTop = true }
}

/** Decides between "can't reach your phone" messages and the real app. */
@Composable
fun AuxioWatchApp(vm: WatchViewModel) {
    val node by vm.phoneNode.collectAsState()
    val status by vm.status.collectAsState()
    val stalled by vm.stalled.collectAsState()
    val s = status

    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        when {
            s != null && !s.auxioInstalled ->
                MessageScreen("Auxio not found", "Install Auxio on your phone, then retry.", "Retry", vm::refresh)
            s != null && !s.auxioConnected ->
                MessageScreen("Can't reach Auxio", "Open Auxio on your phone once, then retry.", "Retry", vm::refresh)
            s != null -> AppNav(vm)
            stalled && node == null ->
                MessageScreen(
                    "Phone not found",
                    "Install Auxio Companion on your phone and keep your watch connected.",
                    "Retry",
                    vm::refresh,
                )
            stalled ->
                MessageScreen(
                    "Phone isn't answering",
                    "Open Auxio Companion on your phone once, then retry.",
                    "Retry",
                    vm::refresh,
                )
            else -> MessageScreen("Connecting…", "")
        }
    }
}

@Composable
private fun AppNav(vm: WatchViewModel) {
    val nav = rememberSwipeDismissableNavController()

    val searchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (data != null) {
                val query =
                    RemoteInput.getResultsFromIntent(data)
                        ?.getCharSequence(SEARCH_KEY)
                        ?.toString()
                        ?.trim()
                if (!query.isNullOrEmpty()) {
                    nav.navigate(listRoute(SRC_SEARCH, query, "“$query”", false))
                }
            }
        }

    fun launchSearch() {
        try {
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            val inputs = listOf(RemoteInput.Builder(SEARCH_KEY).setLabel("Search your music").build())
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, inputs)
            searchLauncher.launch(intent)
        } catch (e: Exception) {
            // No keyboard / voice input available on this watch.
        }
    }

    SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                vm = vm,
                onSearch = { launchSearch() },
                onLibrary = { nav.navigate(listRoute(SRC_BROWSE, "", "Library", false)) },
                onPlayer = { nav.openPlayer() },
                onOpenEntry = { e ->
                    nav.navigate(listRoute(SRC_BROWSE, e.id, e.title, e.tab == TabKind.NONE))
                },
            )
        }
        composable(ROUTE_PLAYER) { PlayerScreen(vm, onSleep = { nav.navigate(ROUTE_SLEEP) }) }
        composable(ROUTE_SLEEP) { SleepScreen(vm, onDone = { nav.popBackStack() }) }
        composable(
            ROUTE_LIST,
            arguments =
                listOf("src", "arg", "title", "play").map {
                    navArgument(it) { type = NavType.StringType }
                },
        ) { entry ->
            val args = entry.arguments
            ListScreen(
                vm = vm,
                source = args?.getString("src") ?: SRC_BROWSE,
                arg = dec(args?.getString("arg")),
                title = dec(args?.getString("title")),
                showPlay = args?.getString("play") == "1",
                onOpenEntry = { e ->
                    nav.navigate(listRoute(SRC_BROWSE, e.id, e.title, e.tab == TabKind.NONE))
                },
                onPlayed = { nav.openPlayer() },
            )
        }
    }
}
