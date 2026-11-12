/*
 * Copyright (c) 2026 Auxio Project
 * MessageScreen.kt is part of Auxio.
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen status message with an optional button. Used for every "not connected" state. */
@Composable
fun MessageScreen(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Black).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ico(Icons.Rounded.MusicNote, tint = Palette.Accent, size = 30.dp)
        Spacer(Modifier.height(6.dp))
        Txt(title, size = 18.sp, align = TextAlign.Center, maxLines = 2)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Txt(
                body,
                size = 13.sp,
                weight = FontWeight.Medium,
                color = Palette.Muted,
                align = TextAlign.Center,
                maxLines = 4,
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            LabelPill(actionLabel, Icons.Rounded.MusicNote, onAction, filled = true)
        }
    }
}
