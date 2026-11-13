/*
 * Copyright (c) 2026 Auxio Project
 * SleepScreen.kt is part of Auxio.
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import org.oxycblt.auxio.companion.wear.WatchViewModel

private val SLEEP_OPTIONS = listOf(5, 15, 30, 45, 60)

@Composable
fun SleepScreen(vm: WatchViewModel, onDone: () -> Unit) {
    val snap by vm.player.collectAsState()
    val remainingMs = snap.state.sleepRemainingMs

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(initialCenterItemIndex = 0),
        autoCentering = null,
        contentPadding = PaddingValues(top = 30.dp, bottom = 44.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Txt(
                "Sleep timer",
                size = 20.sp,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (remainingMs > 0) {
            item {
                Txt(
                    "Pausing in ${remainingMs / 60_000 + 1} min",
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    color = Palette.Accent,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LabelPill("Turn off", Icons.Rounded.Close, onClick = {
                    vm.setSleepTimer(0)
                    onDone()
                })
            }
        }
        items(SLEEP_OPTIONS.size) { i ->
            val minutes = SLEEP_OPTIONS[i]
            LabelPill(
                "$minutes min",
                Icons.Rounded.Timer,
                filled = false,
                onClick = {
                    vm.setSleepTimer(minutes)
                    onDone()
                },
            )
        }
    }
}
