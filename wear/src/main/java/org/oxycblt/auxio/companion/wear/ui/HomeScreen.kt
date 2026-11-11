/*
 * Copyright (c) 2026 Auxio Project
 * HomeScreen.kt is part of Auxio.
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

package org.oxycblt.auxio.companion.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.wear.Load
import org.oxycblt.auxio.companion.wear.WatchViewModel

/**
 * Home, laid out like Spotify's: a home glyph, two pill shortcuts (search, library) and then a
 * shelf of things to play. Here the shelf is "Shuffle all", whatever is playing, and the user's
 * playlists (or albums when there are no playlists).
 */
@Composable
fun HomeScreen(
    vm: WatchViewModel,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onPlayer: () -> Unit,
    onOpenEntry: (MediaEntry) -> Unit,
) {
    val home by vm.home.collectAsState()
    val snap by vm.player.collectAsState()
    val npArt by vm.npArt.collectAsState()

    LaunchedEffect(Unit) { if (home !is Load.Ready) vm.loadHome() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(initialCenterItemIndex = 0),
        autoCentering = null,
        contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Ico(Icons.Rounded.Home, size = 28.dp) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(Icons.Rounded.Search, onSearch, Modifier.weight(1f))
                Pill(Icons.Rounded.LibraryMusic, onLibrary, Modifier.weight(1f))
            }
        }

        if (snap.state.active) {
            item {
                MediaRow(
                    title = snap.state.title,
                    subtitle = snap.state.artist,
                    art = npArt,
                    fallback = Icons.Rounded.MusicNote,
                    onClick = onPlayer,
                    highlight = true,
                )
            }
        }

        item {
            MediaRow(
                title = "Shuffle all",
                subtitle = "Your whole library",
                art = null,
                fallback = Icons.Rounded.Shuffle,
                onClick = {
                    vm.shuffleAll()
                    onPlayer()
                },
                tileBackground = ShuffleTileBrush,
            )
        }

        when (val h = home) {
            is Load.Loading ->
                item {
                    Txt(
                        "Loading…",
                        color = Palette.Muted,
                        weight = FontWeight.Medium,
                        size = 14.sp,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            is Load.Failed ->
                item {
                    Txt(
                        "${h.message}\nTap to retry",
                        color = Palette.Muted,
                        weight = FontWeight.Medium,
                        size = 14.sp,
                        maxLines = 3,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { vm.loadHome() },
                    )
                }
            is Load.Ready -> {
                val entries = h.value.items
                items(entries.size, key = { "${entries[it].id}#$it" }) { i ->
                    val entry = entries[i]
                    EntryRow(vm, entry, onClick = { onOpenEntry(entry) })
                }
            }
        }
    }
}
