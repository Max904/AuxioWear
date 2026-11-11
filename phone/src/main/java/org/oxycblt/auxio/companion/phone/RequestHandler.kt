/*
 * Copyright (c) 2026 Auxio Project
 * RequestHandler.kt is part of Auxio.
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
import android.util.LruCache
import org.oxycblt.auxio.companion.protocol.Art
import org.oxycblt.auxio.companion.protocol.Evt
import org.oxycblt.auxio.companion.protocol.Event
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.Req
import org.oxycblt.auxio.companion.protocol.Request
import org.oxycblt.auxio.companion.protocol.Status
import org.oxycblt.auxio.companion.protocol.Wire

/** Executes the watch's [Request]s against Auxio and answers them. */
class RequestHandler(
    private val context: Context,
    private val link: AuxioLink,
    private val pusher: Pusher,
) {
    /**
     * The full child list of the most recently browsed containers. Auxio hands us a whole list at
     * once, but the watch can only receive it a page at a time, so later pages come from here.
     */
    private val lists = LruCache<String, List<MediaEntry>>(8)

    suspend fun handle(node: String, req: Request) {
        when (req.type) {
            Req.HELLO,
            Req.GET_STATE -> {
                pusher.touch(node)
                val connected = link.connect()
                context.sendEvent(node, Event(Evt.STATUS, status = status(connected)))
                context.sendEvent(
                    node,
                    Event(Evt.STATE, state = link.snapshot(SleepTimer.remainingMs(context))),
                )
            }
            Req.TRANSPORT -> {
                pusher.touch(node)
                if (link.connect()) link.transport(req.id, req.num)
            }
            Req.PLAY -> {
                pusher.touch(node)
                if (link.connect()) link.playMedia(req.id, req.str)
            }
            Req.SHUFFLE_ALL -> {
                pusher.touch(node)
                if (link.connect()) link.shuffleAll()
            }
            Req.BROWSE -> page(node, req, "b:${req.id}") { link.children(req.id)?.map(link::entryOf) }
            Req.SEARCH -> page(node, req, "s:${req.str}") { link.search(req.str)?.map(link::entryOf) }
            Req.QUEUE -> {
                if (!link.connect()) return fail(node, req, "Auxio isn't reachable")
                val (entries, total) = link.queuePage(req.offset, clampLimit(req.limit))
                context.sendEvent(node, fitted(req, entries, total))
            }
            Req.ART -> {
                val size = req.num.toInt().coerceIn(48, 480)
                val jpeg = if (link.connect()) link.artJpeg(req.id, size) else null
                // An empty image tells the watch "no cover", so it stops waiting.
                context.sendArt(node, Art(req.id, jpeg ?: ByteArray(0)))
            }
            Req.SLEEP -> {
                SleepTimer.set(context, req.num.toInt())
                pusher.pushNow()
            }
        }
    }

    private fun status(connected: Boolean) =
        Status(
            auxioInstalled = link.installedPackage() != null,
            auxioConnected = connected,
            auxioPackage = link.connectedPackage ?: "",
        )

    private suspend fun page(
        node: String,
        req: Request,
        cacheKey: String,
        load: suspend () -> List<MediaEntry>?,
    ) {
        if (!link.connect()) return fail(node, req, "Auxio isn't reachable")

        // The first page always reloads so the list is never stale, later pages reuse it.
        var all = if (req.offset == 0) null else synchronized(lists) { lists.get(cacheKey) }
        if (all == null) {
            all = load() ?: return fail(node, req, "Couldn't load this list")
            synchronized(lists) { lists.put(cacheKey, all) }
        }
        val from = req.offset.coerceIn(0, all.size)
        val to = (from + clampLimit(req.limit)).coerceAtMost(all.size)
        context.sendEvent(node, fitted(req, all.subList(from, to), all.size))
    }

    /** Build a page event, dropping rows until it fits in a single message. */
    private fun fitted(req: Request, entries: List<MediaEntry>, total: Int): Event {
        var count = entries.size
        while (true) {
            val event =
                Event(
                    Evt.PAGE,
                    reqId = req.reqId,
                    entries = entries.take(count),
                    total = total,
                    offset = req.offset,
                )
            if (count <= 1 || event.encode().size <= Wire.MAX_PAYLOAD) return event
            count /= 2
        }
    }

    private suspend fun fail(node: String, req: Request, message: String) {
        context.sendEvent(node, Event(Evt.PAGE, reqId = req.reqId, error = message))
    }

    private fun clampLimit(limit: Int) = limit.coerceIn(1, 60)
}
