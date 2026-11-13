/*
 * Copyright (c) 2026 Auxio Project
 * Theme.kt is part of Auxio.
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

@file:OptIn(ExperimentalFoundationApi::class)

package org.oxycblt.auxio.companion.wear.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The look: pure black canvas, one loud accent, heavy white type and muted grey secondary text,
 * the way Spotify's Wear OS app is styled. The accent follows the colour picked in the phone app.
 */
object Palette {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    /**
     * The accent, kept in sync with the phone's Auxio colour scheme. It is Compose state, so
     * everything that reads it recolours when the phone reports a new one. Starts as Auxio blue.
     */
    var Accent: Color by mutableStateOf(Color(0xFFA1C9FD))
    val Surface = Color(0xFF1F1F1F)
    val SurfaceHigh = Color(0xFF2C2C2C)
    val Muted = Color(0xFFB3B3B3)
    val Dim = Color(0xFF6A6A6A)
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Palette.White,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    marquee: Boolean = false,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val style =
        TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            letterSpacing = letterSpacing,
        )
    if (marquee) {
        // Short text stays put (centred if asked), long text scrolls forever like Spotify's.
        Box(
            modifier,
            contentAlignment =
                when (align) {
                    TextAlign.Center -> Alignment.Center
                    TextAlign.End -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
        ) {
            BasicText(
                text = text,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    } else {
        BasicText(
            text = text,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Ico(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Palette.White,
    size: Dp = 24.dp,
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** A ticking wall clock, like the one Spotify draws at the top of its now-playing screen. */
@Composable
fun Clock(
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
    size: TextUnit = 20.sp,
    letterSpacing: TextUnit = 0.sp,
) {
    val context = LocalContext.current
    fun now(): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
    var text by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = now()
            val secondsLeft = 60 - Calendar.getInstance().get(Calendar.SECOND)
            delay(secondsLeft.coerceAtLeast(1) * 1000L)
        }
    }
    // Letter spacing is also added after the last digit; the start padding keeps the text centred.
    Txt(
        text,
        modifier.padding(start = letterSpacing.value.dp),
        size = size,
        color = color,
        letterSpacing = letterSpacing,
    )
}

/** m:ss */
fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
