/*
 * Copyright (c) 2026 Auxio Project
 * ListScreen.kt is part of Auxio.
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.TabKind
import org.oxycblt.auxio.companion.wear.WatchViewModel

const val SRC_BROWSE = "b"
const val SRC_SEARCH = "s"

/**
 * One screen for every list: the library tabs, an album's songs, a playlist, search results.
 *
 * @param source [SRC_BROWSE] to list the children of [arg] (empty = library root) or
 *   [SRC_SEARCH] to list search results for [arg].
 * @param showPlay Show a "Play" button first, for containers such as albums and playlists.
 */
@Composable
fun ListScreen(
    vm: WatchViewModel,
    source: String,
    arg: String,
    title: String,
    showPlay: Boolean,
    onOpenEntry: (MediaEntry) -> Unit,
    onPlayed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val paged =
        remember(source, arg) {
            PagedList(scope) { offset ->
                if (source == SRC_SEARCH) vm.search(arg, offset) else vm.browse(arg, offset)
            }
        }
    LaunchedEffect(paged) { paged.loadMore() }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    LaunchedEffect(listState, paged) {
        snapshotFlow { listState.centerItemIndex }
            .collect { center -> if (center >= paged.items.size - 2) paged.loadMore() }
    }

    val isLibraryRoot = source == SRC_BROWSE && arg.isEmpty()

    Box(Modifier.fillMaxSize()) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        autoCentering = null,
        contentPadding = PaddingValues(top = 30.dp, bottom = 44.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isLibraryRoot) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Icons.Rounded.LibraryMusic, size = 28.dp)
                }
            }
        }
        item {
            // Spotify's list titles are small, bold and slightly grey.
            Txt(
                if (isLibraryRoot) "Your library" else title,
                size = 16.sp,
                color = Color(0xFFE0E0E0),
                maxLines = 2,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        if (showPlay) {
            item {
                LabelPill(
                    label = "Play",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = {
                        vm.play(arg, "")
                        onPlayed()
                    },
                )
            }
        }

        val entries = paged.items
        items(entries.size, key = { "${entries[it].id}#$it" }) { i ->
            val entry = entries[i]
            val previousGroup = if (i > 0) entries[i - 1].group else ""
            Column {
                // Search results and multi-disc albums arrive grouped; label each new group.
                if (entry.group.isNotEmpty() && entry.group != previousGroup) {
                    Txt(
                        entry.group,
                        size = 13.sp,
                        weight = FontWeight.Bold,
                        color = Palette.Accent,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                val open: () -> Unit = {
                    when {
                        entry.browsable -> onOpenEntry(entry)
                        entry.playable -> {
                            vm.play(entry.id, entry.childOf)
                            onPlayed()
                        }
                    }
                }
                if (entry.tab != TabKind.NONE) {
                    // Library tabs are plain icon + text rows, no cover.
                    PlainRow(entry.title, iconFor(entry), onClick = { open() })
                } else {
                    EntryRow(vm, entry, onClick = { open() })
                }
            }
        }

        paged.error?.let { message ->
            item {
                Txt(
                    "$message\nTap to retry",
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    color = Palette.Muted,
                    maxLines = 3,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { paged.retry() },
                )
            }
        }

        if (paged.loading) {
            item {
                Txt(
                    "Loading…",
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    color = Palette.Muted,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (paged.isEmpty) {
            item {
                Txt(
                    "Nothing here",
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    color = Palette.Muted,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    ScrollBar(listState)
    }
}
