/*
 * Copyright (c) 2026 Auxio Project
 * Wire.kt is part of Auxio.
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

package org.oxycblt.auxio.companion.protocol

import java.nio.ByteBuffer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the phone bridge and the watch app say to each other.
 *
 * The transport is the Wear OS Data Layer *MessageClient*. Messages are capped at roughly 100KB,
 * so every list is paged and every image is downscaled before it is sent. Payloads are small JSON
 * documents (artwork is the one binary exception, see [Art]).
 *
 * Direction summary:
 * - watch -> phone: [Request] on [PATH_REQUEST]
 * - phone -> watch: [Event] on [PATH_EVENT], artwork on [PATH_ART]
 */
object Wire {
    /** Capability advertised by the phone app, used by the watch to find the bridge. */
    const val CAPABILITY_PHONE = "auxio_companion_phone"

    /** Capability advertised by the watch app, used by the phone to detect an installed watch. */
    const val CAPABILITY_WATCH = "auxio_companion_watch"

    const val PATH_REQUEST = "/auxio/w2p"
    const val PATH_EVENT = "/auxio/p2w"
    const val PATH_ART = "/auxio/p2w/art"

    /** Safe upper bound for one message body. The hard platform limit is ~100KB. */
    const val MAX_PAYLOAD = 90_000

    /** Artwork key that always refers to the cover of the track that is playing right now. */
    const val ART_NOW_PLAYING = "np"
}

/** Request types sent by the watch. */
object Req {
    /** Watch screen opened. Phone replies with a [Event.STATUS] and a [Event.STATE]. */
    const val HELLO = "hello"

    /** Keep-alive / resync. Phone replies with a [Event.STATE]. */
    const val GET_STATE = "get_state"

    /** Playback control. [Request.id] is one of [Transport], [Request.num] is its argument. */
    const val TRANSPORT = "transport"

    /** Play the media item [Request.id], optionally from the parent [Request.str]. */
    const val PLAY = "play"

    const val SHUFFLE_ALL = "shuffle_all"

    /** Page of children of [Request.id] (empty id = library root). */
    const val BROWSE = "browse"

    /** Page of search results for [Request.str]. */
    const val SEARCH = "search"

    /** Page of the playback queue starting at [Request.offset]. */
    const val QUEUE = "queue"

    /** Cover for [Request.id] (an artwork key), scaled to [Request.num] pixels. */
    const val ART = "art"

    /** Sleep timer, [Request.num] minutes, 0 cancels. */
    const val SLEEP = "sleep"
}

/** Arguments for [Req.TRANSPORT]. */
object Transport {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val TOGGLE = "toggle"
    const val NEXT = "next"
    const val PREV = "prev"

    /** num = position in ms. */
    const val SEEK = "seek"

    /** num = 1 on, 0 off. */
    const val SHUFFLE = "shuffle"

    /** num = 0 off, 1 all, 2 one. */
    const val REPEAT = "repeat"

    /** num = absolute queue index. */
    const val SKIP_TO_QUEUE = "skip_queue"

    /** num = +1 / -1, adjusts the phone's media volume. */
    const val VOLUME = "volume"
}

/** Event types sent by the phone. */
object Evt {
    const val STATE = "state"
    const val STATUS = "status"
    const val PAGE = "page"
}

data class Request(
    val type: String,
    val reqId: Int = 0,
    val id: String = "",
    val str: String = "",
    val num: Long = 0,
    val offset: Int = 0,
    val limit: Int = 0,
) {
    fun encode(): ByteArray =
        JSONObject()
            .put("t", type)
            .put("r", reqId)
            .put("i", id)
            .put("s", str)
            .put("n", num)
            .put("o", offset)
            .put("l", limit)
            .toString()
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): Request? =
            try {
                val j = JSONObject(String(bytes, Charsets.UTF_8))
                Request(
                    type = j.getString("t"),
                    reqId = j.optInt("r", 0),
                    id = j.optString("i", ""),
                    str = j.optString("s", ""),
                    num = j.optLong("n", 0),
                    offset = j.optInt("o", 0),
                    limit = j.optInt("l", 0),
                )
            } catch (e: Exception) {
                null
            }
    }
}

/** What the watch needs to know about the phone side. */
data class Status(
    val auxioInstalled: Boolean,
    val auxioConnected: Boolean,
    val auxioPackage: String = "",
    /** ARGB accent colour the watch should use, 0 = none chosen. */
    val accent: Int = 0,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("inst", auxioInstalled)
            .put("conn", auxioConnected)
            .put("pkg", auxioPackage)
            .put("acc", accent)

    companion object {
        fun fromJson(j: JSONObject) =
            Status(
                auxioInstalled = j.optBoolean("inst", false),
                auxioConnected = j.optBoolean("conn", false),
                auxioPackage = j.optString("pkg", ""),
                accent = j.optInt("acc", 0),
            )
    }
}

/** Snapshot of Auxio's media session. */
data class PlayerState(
    /** False when Auxio has no active playback session. */
    val active: Boolean = false,
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    /** Position at the moment the phone built this snapshot. The watch extrapolates. */
    val positionMs: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val shuffle: Boolean = false,
    /** 0 = off, 1 = all, 2 = one. */
    val repeat: Int = 0,
    val queueTitle: String = "",
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    /** Changes whenever the cover for [ART_NOW_PLAYING] changes. 0 = no cover yet. */
    val artVersion: Int = 0,
    val volume: Int = 0,
    val maxVolume: Int = 0,
    val sleepRemainingMs: Long = 0,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("a", active)
            .put("id", trackId)
            .put("ti", title)
            .put("ar", artist)
            .put("al", album)
            .put("du", durationMs)
            .put("po", positionMs)
            .put("pl", playing)
            .put("sp", speed.toDouble())
            .put("sh", shuffle)
            .put("rp", repeat)
            .put("qt", queueTitle)
            .put("qi", queueIndex)
            .put("qs", queueSize)
            .put("av", artVersion)
            .put("vo", volume)
            .put("vm", maxVolume)
            .put("sl", sleepRemainingMs)

    companion object {
        fun fromJson(j: JSONObject) =
            PlayerState(
                active = j.optBoolean("a", false),
                trackId = j.optString("id", ""),
                title = j.optString("ti", ""),
                artist = j.optString("ar", ""),
                album = j.optString("al", ""),
                durationMs = j.optLong("du", 0),
                positionMs = j.optLong("po", 0),
                playing = j.optBoolean("pl", false),
                speed = j.optDouble("sp", 1.0).toFloat(),
                shuffle = j.optBoolean("sh", false),
                repeat = j.optInt("rp", 0),
                queueTitle = j.optString("qt", ""),
                queueIndex = j.optInt("qi", -1),
                queueSize = j.optInt("qs", 0),
                artVersion = j.optInt("av", 0),
                volume = j.optInt("vo", 0),
                maxVolume = j.optInt("vm", 0),
                sleepRemainingMs = j.optLong("sl", 0),
            )
    }
}

/** What a library tab is. Only meaningful for the entries directly below the library root. */
object TabKind {
    const val NONE = ""
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val GENRES = "genres"
    const val PLAYLISTS = "playlists"
    const val MORE = "more"
}

/** What kind of music item an entry is (so the watch can draw artists round, like Spotify). */
object ItemKind {
    const val NONE = ""
    const val SONG = "song"
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val GENRE = "genre"
    const val PLAYLIST = "playlist"
}

/** One row in a list: a library item, a search result or a queue item. */
data class MediaEntry(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** Key for [Req.ART]; empty when the item has no cover. */
    val art: String = "",
    val playable: Boolean = false,
    val browsable: Boolean = false,
    /** Section header this entry belongs to (search groups, album discs). */
    val group: String = "",
    /** Parent to play this entry in the context of, echoed back in [Req.PLAY]. */
    val childOf: String = "",
    val tab: String = TabKind.NONE,
    /** One of [ItemKind]. */
    val kind: String = ItemKind.NONE,
    /** Absolute queue index, -1 outside of the queue. */
    val index: Int = -1,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("i", id)
            .put("t", title)
            .put("s", subtitle)
            .put("a", art)
            .put("p", playable)
            .put("b", browsable)
            .put("g", group)
            .put("c", childOf)
            .put("k", tab)
            .put("kd", kind)
            .put("x", index)

    companion object {
        fun fromJson(j: JSONObject) =
            MediaEntry(
                id = j.optString("i", ""),
                title = j.optString("t", ""),
                subtitle = j.optString("s", ""),
                art = j.optString("a", ""),
                playable = j.optBoolean("p", false),
                browsable = j.optBoolean("b", false),
                group = j.optString("g", ""),
                childOf = j.optString("c", ""),
                tab = j.optString("k", ""),
                kind = j.optString("kd", ""),
                index = j.optInt("x", -1),
            )
    }
}

data class Event(
    val type: String,
    /** Matches the [Request.reqId] this answers, 0 for unsolicited pushes. */
    val reqId: Int = 0,
    val state: PlayerState? = null,
    val status: Status? = null,
    val entries: List<MediaEntry> = emptyList(),
    /** Total number of items in the list [entries] is a page of. */
    val total: Int = 0,
    val offset: Int = 0,
    val error: String? = null,
) {
    fun encode(): ByteArray {
        val j = JSONObject().put("t", type).put("r", reqId).put("tot", total).put("o", offset)
        state?.let { j.put("st", it.toJson()) }
        status?.let { j.put("su", it.toJson()) }
        error?.let { j.put("e", it) }
        if (entries.isNotEmpty()) {
            val arr = JSONArray()
            entries.forEach { arr.put(it.toJson()) }
            j.put("en", arr)
        }
        return j.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun decode(bytes: ByteArray): Event? =
            try {
                val j = JSONObject(String(bytes, Charsets.UTF_8))
                val entries = mutableListOf<MediaEntry>()
                j.optJSONArray("en")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        entries.add(MediaEntry.fromJson(arr.getJSONObject(i)))
                    }
                }
                Event(
                    type = j.getString("t"),
                    reqId = j.optInt("r", 0),
                    state = j.optJSONObject("st")?.let { PlayerState.fromJson(it) },
                    status = j.optJSONObject("su")?.let { Status.fromJson(it) },
                    entries = entries,
                    total = j.optInt("tot", 0),
                    offset = j.optInt("o", 0),
                    error = if (j.has("e")) j.getString("e") else null,
                )
            } catch (e: Exception) {
                null
            }
    }
}

/** Binary artwork message: `[int keyLength][key utf8][jpeg bytes]`. */
class Art(val key: String, val jpeg: ByteArray) {
    fun encode(): ByteArray {
        val k = key.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + k.size + jpeg.size)
            .putInt(k.size)
            .put(k)
            .put(jpeg)
            .array()
    }

    companion object {
        fun decode(bytes: ByteArray): Art? =
            try {
                val buf = ByteBuffer.wrap(bytes)
                val keyLen = buf.int
                if (keyLen < 0 || keyLen > bytes.size - 4) {
                    null
                } else {
                    val k = ByteArray(keyLen)
                    buf.get(k)
                    val rest = ByteArray(buf.remaining())
                    buf.get(rest)
                    Art(String(k, Charsets.UTF_8), rest)
                }
            } catch (e: Exception) {
                null
            }
    }
}
