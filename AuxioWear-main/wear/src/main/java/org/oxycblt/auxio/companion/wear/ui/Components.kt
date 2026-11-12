/*
 * Copyright (c) 2026 Auxio Project
 * Components.kt is part of Auxio.
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import org.oxycblt.auxio.companion.protocol.ItemKind
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.TabKind
import org.oxycblt.auxio.companion.wear.WatchViewModel

/** Square cover with a rounded corner (round for artists), or an icon on a dark tile. */
@Composable
fun ArtTile(
    art: ImageBitmap?,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    corner: Dp = 6.dp,
    round: Boolean = false,
    background: Brush? = null,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(if (round) CircleShape else RoundedCornerShape(corner))
                .background(
                    background ?: Brush.linearGradient(listOf(Palette.SurfaceHigh, Palette.Surface))
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Ico(fallback, tint = Palette.White, size = size / 2)
        }
    }
}

/** Loads (and caches) the cover for [key] through the phone. */
@Composable
fun rememberArt(vm: WatchViewModel, key: String, sizePx: Int = 128): State<ImageBitmap?> =
    produceState(initialValue = vm.cachedArt(key), key1 = key) {
        if (key.isNotEmpty() && value == null) {
            value = vm.art(key, sizePx)
        }
    }

/**
 * One list row, as in Spotify's watch app: cover on the left, a large plain title, and an
 * optional smaller grey line under it.
 */
@Composable
fun MediaRow(
    title: String,
    subtitle: String,
    art: ImageBitmap?,
    fallback: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    bold: Boolean = false,
    round: Boolean = false,
    tileBackground: Brush? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtTile(art, fallback, round = round, background = tileBackground)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                size = if (bold) 17.sp else 19.sp,
                weight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = if (highlight) Palette.Accent else Palette.White,
            )
            if (subtitle.isNotEmpty()) {
                Txt(subtitle, size = 13.sp, color = Palette.Muted, weight = FontWeight.Medium)
            }
        }
    }
}

/** A [MediaRow] for a [MediaEntry] that loads its own cover. */
@Composable
fun EntryRow(
    vm: WatchViewModel,
    entry: MediaEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    /** Just the title, like Spotify's home shelf. */
    titleOnly: Boolean = false,
    /** Bold title, like Spotify's queue. */
    bold: Boolean = false,
) {
    val art by rememberArt(vm, entry.art)
    MediaRow(
        title = entry.title,
        subtitle = if (titleOnly) "" else entry.subtitle,
        art = art,
        fallback = iconFor(entry),
        onClick = onClick,
        modifier = modifier,
        highlight = highlight,
        bold = bold,
        round = entry.kind == ItemKind.ARTIST,
    )
}

/** Icon + plain text, no cover: the look of Spotify's "Your library" rows. */
@Composable
fun PlainRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Ico(icon, size = 30.dp)
        }
        Spacer(Modifier.width(12.dp))
        Txt(title, size = 20.sp, weight = FontWeight.Normal)
    }
}

fun iconFor(entry: MediaEntry): ImageVector =
    when (entry.tab) {
        TabKind.SONGS -> Icons.Rounded.MusicNote
        TabKind.ALBUMS -> Icons.Rounded.Album
        TabKind.ARTISTS -> Icons.Rounded.Person
        TabKind.GENRES -> Icons.Rounded.Style
        TabKind.PLAYLISTS -> Icons.AutoMirrored.Rounded.PlaylistPlay
        else ->
            when (entry.kind) {
                ItemKind.ARTIST -> Icons.Rounded.Person
                ItemKind.ALBUM -> Icons.Rounded.Album
                ItemKind.GENRE -> Icons.Rounded.Style
                ItemKind.PLAYLIST -> Icons.AutoMirrored.Rounded.PlaylistPlay
                else -> if (entry.browsable) Icons.Rounded.Album else Icons.Rounded.MusicNote
            }
    }

/** The dark rounded buttons at the top of the Spotify home screen (search, library). */
@Composable
fun Pill(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.Surface)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Spotify draws these icons in a dim grey, not white.
        Ico(icon, tint = Color(0xFF9A9A9A), size = 24.dp)
    }
}

/** A wide labelled pill, used for the "Play" button on album and playlist screens. */
@Composable
fun LabelPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(50))
                .background(if (filled) Palette.Accent else Palette.Surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (filled) Palette.Black else Palette.White, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Txt(label, size = 16.sp, color = if (filled) Palette.Black else Palette.White)
    }
}

/** Round icon button. [active] turns the icon into the accent colour. */
@Composable
fun IconToggle(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 44.dp,
    iconSize: Dp = 28.dp,
) {
    Box(
        modifier = modifier.size(boxSize).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ico(icon, tint = if (active) Palette.Accent else Palette.White, size = iconSize)
    }
}

/**
 * The vertical dots on the right edge of the now-playing screens. Like Spotify's they are all
 * plain 5dp circles: the current page is white, the others dim.
 */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(count) { i ->
            val selected = i == current
            Box(
                Modifier.size(5.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.White else Palette.Dim)
            )
        }
    }
}

/**
 * The curved scroll indicator hugging the right edge of the round screen: a dim track with a
 * lighter thumb showing which part of the list is on screen. Hidden when everything fits.
 */
@Composable
fun ScrollBar(state: ScalingLazyListState, modifier: Modifier = Modifier) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total < 2 || visible.isEmpty()) return
    val first = visible.first().index
    val last = visible.last().index
    if (first == 0 && last >= total - 1) return

    val from = first.toFloat() / total
    val to = (last + 1).toFloat() / total
    Canvas(modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2 + 3.dp.toPx()
        val topLeft = Offset(inset, inset)
        val arc = Size(size.width - inset * 2, size.height - inset * 2)
        val start = -26f
        val span = 52f
        drawArc(
            color = Color(0x33FFFFFF),
            startAngle = start,
            sweepAngle = span,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color(0xCCFFFFFF),
            startAngle = start + span * from,
            sweepAngle = (span * (to - from)).coerceAtLeast(6f),
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Gradient for the "Shuffle all" tile (Spotify's Liked Songs tile), built from the accent. */
@Composable
fun shuffleTileBrush(): Brush =
    Brush.linearGradient(listOf(lerp(Palette.Accent, Palette.Black, 0.55f), Palette.Accent))
