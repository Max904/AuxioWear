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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.TabKind
import org.oxycblt.auxio.companion.wear.WatchViewModel

/** Square cover with a rounded corner, or an icon on a dark tile when there is no cover. */
@Composable
fun ArtTile(
    art: ImageBitmap?,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    corner: Dp = 6.dp,
    background: Brush? = null,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(background ?: Brush.linearGradient(listOf(Palette.SurfaceHigh, Palette.Surface))),
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
 * One list row: cover on the left, big bold title, grey subtitle. This is the shape every list in
 * the Spotify watch app has (Liked Songs, Indie Mix, queue items...).
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
        ArtTile(art, fallback, background = tileBackground)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                size = 17.sp,
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
) {
    val art by rememberArt(vm, entry.art)
    MediaRow(
        title = entry.title,
        subtitle = entry.subtitle,
        art = art,
        fallback = iconFor(entry),
        onClick = onClick,
        modifier = modifier,
        highlight = highlight,
    )
}

fun iconFor(entry: MediaEntry): ImageVector =
    when (entry.tab) {
        TabKind.SONGS -> Icons.Rounded.MusicNote
        TabKind.ALBUMS -> Icons.Rounded.Album
        TabKind.ARTISTS -> Icons.Rounded.Person
        TabKind.GENRES -> Icons.Rounded.Style
        TabKind.PLAYLISTS -> Icons.AutoMirrored.Rounded.PlaylistPlay
        else -> if (entry.browsable) Icons.Rounded.Album else Icons.Rounded.MusicNote
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
        Ico(icon, size = 22.dp)
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

/** Round icon button. [active] turns the icon green, like shuffle/repeat do in Spotify. */
@Composable
fun IconToggle(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ico(icon, tint = if (active) Palette.Accent else Palette.White, size = 28.dp)
    }
}

/**
 * The vertical dots on the right edge of the now-playing screens. The current page is a short
 * white pill, the others are dim dots.
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
                Modifier.width(5.dp)
                    .height(if (selected) 14.dp else 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Palette.White else Palette.Dim)
            )
        }
    }
}

/** Gradient for the "Shuffle all" tile (Spotify's Liked Songs tile), built from the accent. */
@Composable
fun shuffleTileBrush(): Brush =
    Brush.linearGradient(listOf(lerp(Palette.Accent, Palette.Black, 0.55f), Palette.Accent))
