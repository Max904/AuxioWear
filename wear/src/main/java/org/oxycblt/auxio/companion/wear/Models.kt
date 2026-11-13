/*
 * Copyright (c) 2026 Auxio Project
 * MainActivity.kt is part of Auxio.
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

import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.PlayerState

/** A [PlayerState] plus when we received it, so the UI can extrapolate the playback position. */
data class Snapshot(val state: PlayerState = PlayerState(), val receivedAt: Long = 0L) {
    fun positionAt(now: Long): Long {
        if (!state.playing) return state.positionMs
        val elapsed = (now - receivedAt).coerceAtLeast(0)
        val pos = state.positionMs + (elapsed * state.speed).toLong()
        return if (state.durationMs > 0) pos.coerceAtMost(state.durationMs) else pos
    }
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Failed(val message: String) : Load<Nothing>

    data class Ready<T>(val value: T) : Load<T>
}

/** What the home screen shows below the shortcuts. */
data class HomeData(val source: MediaEntry?, val items: List<MediaEntry>)

