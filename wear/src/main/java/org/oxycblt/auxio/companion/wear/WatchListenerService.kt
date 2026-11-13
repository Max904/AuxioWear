/*
 * Copyright (c) 2026 Auxio Project
 * WatchListenerService.kt is part of Auxio.
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

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.oxycblt.auxio.companion.protocol.Evt

/**
 * Receives everything the phone sends, whether or not the app's UI is open. When the phone
 * reports that Auxio is playing it makes sure the media session service is running, which is
 * what puts Auxio into Wear OS's media controls.
 */
class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val hub = (application as WatchApp).hub
        val decoded = hub.link.dispatch(event.path, event.data)
        val state = decoded?.takeIf { it.type == Evt.STATE }?.state
        if (state != null && state.active && state.playing) {
            // May be refused by Android if we are in the background; the service is then simply
            // started the next time the app is opened.
            RemoteSessionService.start(this)
        }
    }
}
