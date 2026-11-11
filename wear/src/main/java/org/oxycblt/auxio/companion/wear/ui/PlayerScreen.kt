/*
 * Copyright (c) 2026 Auxio Project
 * PlayerScreen.kt is part of Auxio.
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

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.oxycblt.auxio.companion.wear.Snapshot
import org.oxycblt.auxio.companion.wear.WatchViewModel

private const val PAGE_COVER = 0
private const val PAGE_CONTROLS = 1
private const val PAGE_QUEUE = 2
private const val PAGE_COUNT = 3

/** Playback position that keeps ticking between the phone's updates. */
@Composable
private fun rememberPositionMs(snap: Snapshot): State<Long> =
    produceState(snap.positionAt(SystemClock.elapsedRealtime()), snap) {
        while (true) {
            value = snap.positionAt(SystemClock.elapsedRealtime())
            delay(250)
        }
    }

/**
 * The now-playing experience. Three vertical pages, like Spotify's: full-bleed cover, transport
 * controls, and the queue (with shuffle / repeat / sleep timer). The crown changes the phone's
 * volume on the first two pages.
 */
@Composable
fun PlayerScreen(vm: WatchViewModel, onSleep: () -> Unit) {
    val snap by vm.player.collectAsState()
    val npArt by vm.npArt.collectAsState()
    val state = snap.state

    if (!state.active) {
        MessageScreen(
            title = "Nothing playing",
            body = "Pick something on your phone's Auxio, or shuffle your library.",
            actionLabel = "Shuffle all",
            onAction = { vm.shuffleAll() },
        )
        return
    }

    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val focus = remember { FocusRequester() }

    // Crown -> phone volume, only where the crown isn't scrolling the queue.
    var crown by remember { mutableFloatStateOf(0f) }
    var lastVolume by remember { mutableIntStateOf(-1) }
    var showVolume by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage != PAGE_QUEUE) focus.requestFocus() }
    LaunchedEffect(state.volume) {
        if (lastVolume != -1 && lastVolume != state.volume) {
            showVolume = true
            delay(1500)
            showVolume = false
        }
        lastVolume = state.volume
    }

    Box(
        Modifier.fillMaxSize()
            .background(Palette.Black)
            .onRotaryScrollEvent { event ->
                if (pager.currentPage == PAGE_QUEUE) {
                    false
                } else {
                    crown += event.verticalScrollPixels
                    if (abs(crown) >= 40f) {
                        vm.volume(if (crown > 0) 1 else -1)
                        crown = 0f
                    }
                    true
                }
            }
            .focusRequester(focus)
            .focusable()
    ) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_COVER -> CoverPage(vm, snap)
                PAGE_CONTROLS -> ControlsPage(vm, snap)
                else -> QueuePage(vm, snap, onSleep)
            }
        }

        PageDots(
            count = PAGE_COUNT,
            current = pager.currentPage,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 7.dp),
        )

        if (showVolume && state.maxVolume > 0) {
            VolumeOverlay(state.volume, state.maxVolume, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun CoverPage(vm: WatchViewModel, snap: Snapshot) {
    val state = snap.state
    val art by vm.npArt.collectAsState()
    val position by rememberPositionMs(snap)
    val progress =
        if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize().clickable { vm.toggle() }) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Palette.Surface), Alignment.Center) {
                Ico(Icons.Rounded.MusicNote, tint = Palette.Dim, size = 64.dp)
            }
        }

        // Darken the top (clock) and bottom (title) so the text stays readable on any cover.
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xAA000000),
                        0.3f to Color.Transparent,
                        0.5f to Color(0x22000000),
                        1f to Color(0xF0000000),
                    )
                )
        )

        // Progress around the bezel.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = Color(0x40FFFFFF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(stroke),
            )
            drawArc(
                color = Palette.Accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }

        Clock(Modifier.align(Alignment.TopCenter).padding(top = 20.dp))

        if (!state.playing) {
            Ico(Icons.Rounded.PlayArrow, Modifier.align(Alignment.Center), size = 52.dp)
        }

        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter).padding(start = 30.dp, end = 30.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(
                state.title,
                size = 20.sp,
                align = TextAlign.Center,
                marquee = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(Icons.Rounded.MusicNote, tint = Palette.Accent, size = 15.dp)
                Spacer(Modifier.width(3.dp))
                Txt(
                    state.artist,
                    size = 15.sp,
                    weight = FontWeight.Medium,
                    color = Palette.Accent,
                )
            }
        }
    }
}

@Composable
private fun ControlsPage(vm: WatchViewModel, snap: Snapshot) {
    val state = snap.state
    val position by rememberPositionMs(snap)
    val progress =
        if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Txt(
            state.title,
            size = 16.sp,
            align = TextAlign.Center,
            marquee = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Txt(
            state.artist,
            size = 13.sp,
            weight = FontWeight.Medium,
            color = Palette.Muted,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconToggle(Icons.Rounded.SkipPrevious, active = false, onClick = { vm.prev() })
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Palette.White).clickable { vm.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Ico(
                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    tint = Palette.Black,
                    size = 38.dp,
                )
            }
            IconToggle(Icons.Rounded.SkipNext, active = false, onClick = { vm.next() })
        }

        Spacer(Modifier.height(8.dp))

        // Tap anywhere on the bar to seek there.
        Box(
            modifier =
                Modifier.fillMaxWidth().height(24.dp).pointerInput(state.durationMs) {
                    detectTapGestures { tap ->
                        if (state.durationMs > 0) {
                            vm.seekTo((tap.x / size.width * state.durationMs).toLong())
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.SurfaceHigh)
            )
            Box(
                Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.White)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(formatTime(position), size = 12.sp, weight = FontWeight.Medium, color = Palette.Muted)
            Txt(formatTime(state.durationMs), size = 12.sp, weight = FontWeight.Medium, color = Palette.Muted)
        }
    }
}

@Composable
private fun QueuePage(vm: WatchViewModel, snap: Snapshot, onSleep: () -> Unit) {
    val state = snap.state
    val scope = rememberCoroutineScope()
    // The queue is shown from the current track onwards, and reloads when the track changes.
    val paged =
        remember(state.trackId, state.queueIndex, state.queueSize) {
            PagedList(scope, base = state.queueIndex.coerceAtLeast(0)) { offset -> vm.queue(offset) }
        }
    LaunchedEffect(paged) { paged.loadMore() }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    LaunchedEffect(listState, paged) {
        snapshotFlow { listState.centerItemIndex }
            .collect { center -> if (center >= paged.items.size - 2) paged.loadMore() }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        autoCentering = null,
        contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 12.dp, end = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("Queue", size = 22.sp, align = TextAlign.Center)
                Txt(
                    "Playing ${state.queueIndex + 1} of ${state.queueSize}",
                    size = 14.sp,
                    weight = FontWeight.Medium,
                    color = Palette.Muted,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconToggle(Icons.Rounded.Shuffle, active = state.shuffle, onClick = { vm.setShuffle(!state.shuffle) })
                IconToggle(
                    if (state.repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    active = state.repeat != 0,
                    onClick = { vm.setRepeat((state.repeat + 1) % 3) },
                )
                IconToggle(Icons.Rounded.Timer, active = state.sleepRemainingMs > 0, onClick = onSleep)
            }
        }
        val entries = paged.items
        items(entries.size, key = { "${entries[it].index}:${entries[it].id}" }) { i ->
            val entry = entries[i]
            EntryRow(
                vm,
                entry,
                highlight = entry.index == state.queueIndex,
                onClick = { vm.skipToQueue(entry.index) },
            )
        }
    }
}

@Composable
private fun VolumeOverlay(volume: Int, max: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xDD000000)).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt("Phone volume", size = 13.sp, weight = FontWeight.Medium, color = Palette.Muted)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(90.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.SurfaceHigh)) {
            Box(
                Modifier.fillMaxWidth((volume.toFloat() / max).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(Palette.Accent)
            )
        }
    }
}
