/*
 * Copyright (c) 2026 Auxio Project
 * PhoneLink.kt is part of Auxio.
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

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.oxycblt.auxio.companion.protocol.Art
import org.oxycblt.auxio.companion.protocol.Event
import org.oxycblt.auxio.companion.protocol.Request
import org.oxycblt.auxio.companion.protocol.Wire

/**
 * The watch's end of the Data Layer conversation with the phone bridge.
 *
 * Requests go out as messages. Anything the phone says comes back as an [Event]; events that
 * answer a specific request complete that request's [call], everything is also published on
 * [events] so unsolicited pushes (playback changes) reach the UI.
 */
class PhoneLink(context: Context) :
    MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private val messages = Wearable.getMessageClient(context)
    private val capabilities = Wearable.getCapabilityClient(context)

    private val _phoneNode = MutableStateFlow<String?>(null)
    /** Node id of the phone running the bridge, or null when none is reachable. */
    val phoneNode: StateFlow<String?> = _phoneNode.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _arts = MutableSharedFlow<Art>(extraBufferCapacity = 64)
    val arts: SharedFlow<Art> = _arts.asSharedFlow()

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Event>>()
    private val nextId = AtomicInteger(1)

    fun start() {
        messages.addListener(this)
        capabilities.addListener(this, Wire.CAPABILITY_PHONE)
    }

    fun stop() {
        messages.removeListener(this)
        capabilities.removeListener(this)
    }

    suspend fun refreshNode() {
        try {
            val info =
                capabilities
                    .getCapability(Wire.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await()
            _phoneNode.value = pick(info.nodes)
        } catch (e: Exception) {
            _phoneNode.value = null
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        _phoneNode.value = pick(info.nodes)
    }

    private fun pick(nodes: Set<Node>): String? = (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Wire.PATH_EVENT -> {
                val decoded = Event.decode(event.data) ?: return
                if (decoded.reqId != 0) {
                    pending.remove(decoded.reqId)?.complete(decoded)
                }
                _events.tryEmit(decoded)
            }
            Wire.PATH_ART -> {
                val art = Art.decode(event.data) ?: return
                _arts.tryEmit(art)
            }
        }
    }

    /** Fire and forget. Returns false if there is no phone or the send failed. */
    suspend fun send(request: Request): Boolean {
        val node = _phoneNode.value ?: return false
        return try {
            messages.sendMessage(node, Wire.PATH_REQUEST, request.encode()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Send [request] and wait for the phone's answer. Null on timeout or send failure. */
    suspend fun call(request: Request, timeoutMs: Long = 14_000): Event? {
        val id = nextId.getAndUpdate { if (it >= Int.MAX_VALUE - 1) 1 else it + 1 }
        val waiter = CompletableDeferred<Event>()
        pending[id] = waiter
        if (!send(request.copy(reqId = id))) {
            pending.remove(id)
            return null
        }
        val result = withTimeoutOrNull(timeoutMs) { waiter.await() }
        pending.remove(id)
        return result
    }
}
