/*
 * Copyright (c) 2026 Auxio Project
 * CompanionListenerService.kt is part of Auxio.
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

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import org.oxycblt.auxio.companion.protocol.Request
import org.oxycblt.auxio.companion.protocol.Wire

/** Entry point for everything the watch sends. Work is done on the app scope, not this service. */
class CompanionListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Wire.PATH_REQUEST) return
        val request = Request.decode(event.data) ?: return
        val app = companionApp
        app.scope.launch { app.handler.handle(event.sourceNodeId, request) }
    }
}
