/*
 * Copyright (c) 2026 Auxio Project
 * Messaging.kt is part of Auxio.
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
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.oxycblt.auxio.companion.protocol.Art
import org.oxycblt.auxio.companion.protocol.Event
import org.oxycblt.auxio.companion.protocol.Wire

private const val TAG = "AuxioCompanion"

suspend fun Context.sendEvent(node: String, event: Event) {
    try {
        Wearable.getMessageClient(this).sendMessage(node, Wire.PATH_EVENT, event.encode()).await()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to send event ${event.type}", e)
    }
}

suspend fun Context.sendArt(node: String, art: Art) {
    try {
        Wearable.getMessageClient(this).sendMessage(node, Wire.PATH_ART, art.encode()).await()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to send art ${art.key}", e)
    }
}
