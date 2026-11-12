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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
import org.oxycblt.auxio.companion.wear.Snapshot
import org.oxycblt.auxio.companion.wear.WatchViewModel

private const val PAGE_COVER = 0
private const val PAGE_CONTROLS = 1
private const val PAGE_QUEUE = 2
private const val PAGE_COUNT = 3

// Bottom row of the controls page (shuffle / queue / repeat), sized like Spotify's icons.
private val BOTTOM_BOX = 48.dp
private val BOTTOM_ICON = 34.dp

/**
 * Spotify's "shadow" over the cover: a radial gradient centred on the screen that reaches [outer]
 * at the bezel (radius = half the screen), instead of a vertical top/bottom fade.
 */
private fun Modifier.radialScrim(inner: Color, outer: Color): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            colors = listOf(inner, outer),
            center = center,
            radius = size.minDimension / 2f,
        )
    )
}

/** Playback position that keeps ticking between the phone's updates. */
@Composable
private fun rememberPositionMs(snap: Snapshot): State<Long> =
    produceState(snap.positionAt(SystemClock.elapsedRealtime()), snap) {
        while (true) {
            value = snap.positionAt(SystemClock.elapsedRealtime())
            delay(250)
        }
    }

private fun Snapshot.progressAt(position: Long): Float =
    if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

/**
 * The now-playing experience. Three vertical pages, like Spotify's: full-bleed cover, transport
 * controls, and the queue (with shuffle / repeat / sleep timer). The crown changes the phone's
 * volume on the first two pages.
 */
@Composable
fun PlayerScreen(vm: WatchViewModel, onSleep: () -> Unit) {
    val snap by vm.player.collectAsState()
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

    val scope = rememberCoroutineScope()
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

    // Spotify hides the page dots on the cover page after a moment, so the art stays clean.
    var dotsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(pager.currentPage) {
        dotsVisible = true
        if (pager.currentPage == PAGE_COVER) {
            delay(2500)
            dotsVisible = false
        }
    }
    val dotsAlpha by animateFloatAsState(if (dotsVisible) 1f else 0f, label = "dots")

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
                PAGE_CONTROLS ->
                    ControlsPage(
                        vm,
                        snap,
                        onQueue = { scope.launch { pager.animateScrollToPage(PAGE_QUEUE) } },
                    )
                else -> QueuePage(vm, snap, onSleep)
            }
        }

        PageDots(
            count = PAGE_COUNT,
            current = pager.currentPage,
            modifier =
                Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).graphicsLayer {
                    alpha = dotsAlpha
                },
        )

        if (showVolume && state.maxVolume > 0) {
            VolumeOverlay(state.volume, state.maxVolume, Modifier.align(Alignment.Center))
        }
    }
}

/** Page 1: just the cover, the title and the artist. Tap to pause / resume. */
@Composable
private fun CoverPage(vm: WatchViewModel, snap: Snapshot) {
    val state = snap.state
    val art by vm.npArt.collectAsState()

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

        // Spotify: untouched cover in the middle, fading to 60% black at the bezel.
        Box(Modifier.fillMaxSize().radialScrim(Color.Transparent, Color(0x99000000)))

        Clock(
            Modifier.align(Alignment.TopCenter).padding(top = 7.dp),
            size = 19.sp,
            letterSpacing = 4.sp,
        )

        // Paused: the pause glyph appears over the cover (and disappears again on resume).
        if (!state.playing) {
            Box(
                Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Ico(Icons.Rounded.Pause, size = 52.dp)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 29.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Long titles scroll across the whole width, short ones sit centred.
            Txt(state.title, size = 17.5.sp, align = TextAlign.Center, marquee = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stands in for Spotify's logo next to the artist.
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(Palette.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Ico(Icons.Rounded.MusicNote, tint = Palette.Black, size = 12.dp)
                }
                Spacer(Modifier.width(5.dp))
                Txt(state.artist, size = 14.sp, weight = FontWeight.Medium, color = Palette.Accent)
            }
        }
    }
}

/** Page 2: dimmed cover behind transport controls with a progress ring around play/pause. */
@Composable
private fun ControlsPage(vm: WatchViewModel, snap: Snapshot, onQueue: () -> Unit) {
    val state = snap.state
    val art by vm.npArt.collectAsState()
    val position by rememberPositionMs(snap)
    val progress = snap.progressAt(position)
    val atStart = state.queueIndex <= 0 && state.repeat == 0

    Box(Modifier.fillMaxSize()) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Spotify: 40% black over the cover in the middle, solid black at the bezel.
        Box(Modifier.fillMaxSize().radialScrim(Color(0x66000000), Color.Black))

        // The clock sits in a small black pill, as in Spotify's controls page.
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.Black)
                .padding(horizontal = 10.dp, vertical = 1.dp)
        ) {
            Clock(color = Palette.White, size = 15.sp)
        }

        // Title and artist under the clock.
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(state.title, size = 17.5.sp, align = TextAlign.Center, marquee = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Txt(
                state.artist,
                size = 14.sp,
                weight = FontWeight.Medium,
                color = Palette.Accent,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            )
        }

        // Previous / play-pause in a progress ring / next.
        Row(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(Icons.Rounded.SkipPrevious, enabled = !atStart, onClick = { vm.prev() })
            PlayRing(progress = progress, playing = state.playing, onClick = { vm.toggle() })
            SkipButton(Icons.Rounded.SkipNext, enabled = true, onClick = { vm.next() })
        }

        // Shuffle and repeat flank the queue button, which sits lower, following the bezel.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.padding(bottom = 24.dp)) {
                IconToggle(
                    Icons.Rounded.Shuffle,
                    active = state.shuffle,
                    onClick = { vm.setShuffle(!state.shuffle) },
                    boxSize = BOTTOM_BOX,
                    iconSize = BOTTOM_ICON,
                )
            }
            IconToggle(
                Icons.AutoMirrored.Rounded.QueueMusic,
                active = false,
                onClick = onQueue,
                boxSize = BOTTOM_BOX,
                iconSize = BOTTOM_ICON,
            )
            Box(Modifier.padding(bottom = 24.dp)) {
                IconToggle(
                    if (state.repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    active = state.repeat != 0,
                    onClick = { vm.setRepeat((state.repeat + 1) % 3) },
                    boxSize = BOTTOM_BOX,
                    iconSize = BOTTOM_ICON,
                )
            }
        }
    }
}

@Composable
private fun SkipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ico(icon, tint = if (enabled) Palette.White else Color(0x55FFFFFF), size = 60.dp)
    }
}

/** Play/pause glyph inside a ring that fills with the accent colour as the track plays. */
@Composable
private fun PlayRing(progress: Float, playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(70.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color(0x33FFFFFF),
                startAngle = 0f,
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
        Ico(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, size = 54.dp)
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
                bold = true,
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
