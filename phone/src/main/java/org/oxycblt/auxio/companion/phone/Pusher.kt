/*
 * Copyright (c) 2026 Auxio Project
 * Pusher.kt is part of Auxio.
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

package org.oxycblt.auxio.companion.phone

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.oxycblt.auxio.companion.protocol.Evt
import org.oxycblt.auxio.companion.protocol.Event

/**
 * Pushes playback changes to the watch the moment they happen, so the watch UI doesn't have to
 * poll to notice a track change.
 *
 * The watch is only pushed to while it has talked to us recently (it sends a keep-alive every few
 * seconds while its UI is open), so a watch that has gone away never costs us battery.
 */
class Pusher(
    private val context: Context,
    private val link: AuxioLink,
    private val scope: CoroutineScope,
) {
    @Volatile private var node: String? = null
    @Volatile private var lastSeen = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val push = Runnable {
        scope.launch {
            val target = node ?: return@launch
            if (SystemClock.elapsedRealtime() - lastSeen > ACTIVE_WINDOW_MS) return@launch
            val state = link.snapshot(SleepTimer.remainingMs(context))
            context.sendEvent(target, Event(Evt.STATE, state = state))
        }
    }

    init {
        link.addListener {
            // Auxio fires several callbacks per track change; coalesce them.
            handler.removeCallbacks(push)
            handler.postDelayed(push, DEBOUNCE_MS)
        }
    }

    /** Remember that [nodeId] is looking at us right now. */
    fun touch(nodeId: String) {
        node = nodeId
        lastSeen = SystemClock.elapsedRealtime()
    }

    /** Push immediately, e.g. after a command that doesn't change Auxio's own session. */
    fun pushNow() {
        handler.removeCallbacks(push)
        handler.post(push)
    }

    private companion object {
        const val ACTIVE_WINDOW_MS = 30_000L
        const val DEBOUNCE_MS = 120L
    }
}
