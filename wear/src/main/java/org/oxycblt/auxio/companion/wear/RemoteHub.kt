/*
 * Copyright (c) 2026 Auxio Project
 * RemoteHub.kt is part of Auxio.
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

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.oxycblt.auxio.companion.protocol.Evt
import org.oxycblt.auxio.companion.protocol.Event
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.PlayerState
import org.oxycblt.auxio.companion.protocol.Req
import org.oxycblt.auxio.companion.protocol.Request
import org.oxycblt.auxio.companion.protocol.Status
import org.oxycblt.auxio.companion.protocol.TabKind
import org.oxycblt.auxio.companion.protocol.Transport
import org.oxycblt.auxio.companion.protocol.Wire
import org.oxycblt.auxio.companion.wear.ui.Palette

/**
 * Everything the watch knows about the phone and Auxio: connection, playback state, cover, and the
 * commands. It is owned by the application rather than by a screen, so the UI and the media
 * session service ([RemoteSessionService]) share one source of truth.
 */
class RemoteHub(app: Application) {
    /** Lives as long as the process, so it keeps working with no UI on screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val link = PhoneLink(app)

    val phoneNode: StateFlow<String?> = link.phoneNode

    private val _player = MutableStateFlow(Snapshot())
    val player: StateFlow<Snapshot> = _player.asStateFlow()

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    /** True once we have waited long enough for the phone that we should say something is wrong. */
    private val _stalled = MutableStateFlow(false)
    val stalled: StateFlow<Boolean> = _stalled.asStateFlow()

    private val _npArt = MutableStateFlow<ImageBitmap?>(null)
    val npArt: StateFlow<ImageBitmap?> = _npArt.asStateFlow()

    /** The same cover as a platform bitmap, for the media session and notification. */
    private val _npBitmap = MutableStateFlow<Bitmap?>(null)
    val npBitmap: StateFlow<Bitmap?> = _npBitmap.asStateFlow()

    private val _home = MutableStateFlow<Load<HomeData>>(Load.Loading)
    val home: StateFlow<Load<HomeData>> = _home.asStateFlow()

    private val uiVisible = MutableStateFlow(false)
    private val serviceRunning = MutableStateFlow(false)
    private var attemptStartedAt = SystemClock.elapsedRealtime()

    private val artCache = LruCache<String, ImageBitmap>(80)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap?>>()

    private val themePrefs = app.getSharedPreferences("theme", 0)

    init {
        // Start with the last accent the phone told us about, so there is no flash of default.
        themePrefs.getInt("accent", 0).takeIf { it != 0 }?.let { Palette.Accent = Color(it) }

        // Phone -> watch pushes.
        scope.launch {
            link.events.collect { event ->
                when (event.type) {
                    Evt.STATE ->
                        event.state?.let {
                            _player.value = Snapshot(it, SystemClock.elapsedRealtime())
                        }
                    Evt.STATUS -> {
                        _status.value = event.status
                        applyAccent(event.status?.accent ?: 0)
                    }
                }
            }
        }

        // Artwork arriving.
        scope.launch(Dispatchers.Default) {
            link.arts.collect { art ->
                val raw =
                    if (art.jpeg.isEmpty()) null
                    else BitmapFactory.decodeByteArray(art.jpeg, 0, art.jpeg.size)
                val bitmap = raw?.asImageBitmap()
                if (art.key == Wire.ART_NOW_PLAYING) {
                    _npBitmap.value = raw
                    _npArt.value = bitmap
                } else {
                    if (bitmap != null) artCache.put(art.key, bitmap)
                    inflight[art.key]?.complete(bitmap)
                }
            }
        }

        // Keep the now-playing cover in step with the track.
        scope.launch {
            _player
                .map { it.state.active to it.state.artVersion }
                .distinctUntilChanged()
                .collect { (active, version) ->
                    when {
                        !active || version == 0 -> {
                            _npArt.value = null
                            _npBitmap.value = null
                        }
                        else -> requestNowPlayingArt()
                    }
                }
        }

        // Keep-alive while the UI is visible or the media session service is running. This resyncs
        // anything we missed and keeps the phone pushing updates to us.
        scope.launch {
            combine(uiVisible, serviceRunning) { ui, service -> ui || service }
                .distinctUntilChanged()
                .collectLatest { on ->
                    if (!on) {
                        link.stop()
                        return@collectLatest
                    }
                    link.start()
                    attemptStartedAt = SystemClock.elapsedRealtime()
                    link.refreshNode()
                    var first = true
                    while (true) {
                        if (link.phoneNode.value == null) link.refreshNode()
                        link.send(Request(if (first) Req.HELLO else Req.GET_STATE))
                        first = false

                        val snap = _player.value
                        if (snap.state.active && snap.state.artVersion != 0 && _npArt.value == null) {
                            requestNowPlayingArt()
                        }
                        _stalled.value =
                            _status.value == null &&
                                SystemClock.elapsedRealtime() - attemptStartedAt > STALL_AFTER_MS
                        // Faster while someone is looking at the screen, gentle in the background.
                        delay(
                            when {
                                _status.value == null -> 2_000
                                uiVisible.value -> 4_000
                                else -> 15_000
                            }
                        )
                    }
                }
        }
    }

    /** Adopt the accent chosen on the phone (ARGB, 0 = none) and remember it. */
    private fun applyAccent(argb: Int) {
        if (argb == 0 || Palette.Accent == Color(argb)) return
        Palette.Accent = Color(argb)
        themePrefs.edit().putInt("accent", argb).apply()
    }

    /** Called by the activity when it becomes visible / hidden. */
    fun setUiVisible(visible: Boolean) {
        uiVisible.value = visible
    }

    /** Called by [RemoteSessionService] while it is running. */
    fun setServiceRunning(running: Boolean) {
        serviceRunning.value = running
    }

    /** Throw away what we think we know about the phone and ask again. */
    fun refresh() {
        scope.launch {
            _status.value = null
            _stalled.value = false
            attemptStartedAt = SystemClock.elapsedRealtime()
            link.refreshNode()
            link.send(Request(Req.HELLO))
        }
    }

    // --- HOME ---

    fun loadHome() {
        scope.launch {
            _home.value = Load.Loading
            val root = browse("", 0, 20)
            if (root == null || root.error != null) {
                _home.value = Load.Failed(root?.error ?: "No answer from your phone")
                return@launch
            }
            // The home screen mimics a "your playlists" shelf: playlists if Auxio has any,
            // otherwise albums.
            val source =
                root.entries.firstOrNull { it.tab == TabKind.PLAYLISTS }
                    ?: root.entries.firstOrNull { it.tab == TabKind.ALBUMS }
            val items = source?.let { browse(it.id, 0, 12)?.entries } ?: emptyList()
            _home.value = Load.Ready(HomeData(source, items))
        }
    }

    // --- LISTS ---

    suspend fun browse(parent: String, offset: Int, limit: Int = PAGE_SIZE): Event? =
        link.call(Request(Req.BROWSE, id = parent, offset = offset, limit = limit))

    suspend fun search(query: String, offset: Int, limit: Int = PAGE_SIZE): Event? =
        link.call(Request(Req.SEARCH, str = query, offset = offset, limit = limit))

    suspend fun queue(offset: Int, limit: Int = PAGE_SIZE): Event? =
        link.call(Request(Req.QUEUE, offset = offset, limit = limit))

    // --- ARTWORK ---

    fun cachedArt(key: String): ImageBitmap? = if (key.isEmpty()) null else artCache.get(key)

    suspend fun art(key: String, sizePx: Int): ImageBitmap? {
        if (key.isEmpty()) return null
        artCache.get(key)?.let {
            return it
        }
        val fresh = CompletableDeferred<ImageBitmap?>()
        val existing = inflight.putIfAbsent(key, fresh)
        if (existing != null) {
            return withTimeoutOrNull(ART_TIMEOUT_MS) { existing.await() }
        }
        link.send(Request(Req.ART, id = key, num = sizePx.toLong()))
        val result = withTimeoutOrNull(ART_TIMEOUT_MS) { fresh.await() }
        inflight.remove(key)
        return result
    }

    private fun requestNowPlayingArt() {
        scope.launch {
            link.send(Request(Req.ART, id = Wire.ART_NOW_PLAYING, num = NOW_PLAYING_ART_PX))
        }
    }

    // --- COMMANDS ---

    fun toggle() {
        optimistic { copy(playing = !playing) }
        transport(Transport.TOGGLE)
    }

    fun play() {
        optimistic { copy(playing = true) }
        transport(Transport.PLAY)
    }

    fun pause() {
        optimistic { copy(playing = false) }
        transport(Transport.PAUSE)
    }

    fun next() = transport(Transport.NEXT)

    fun prev() = transport(Transport.PREV)

    fun seekTo(ms: Long) {
        optimistic { copy(positionMs = ms) }
        transport(Transport.SEEK, ms)
    }

    fun setShuffle(on: Boolean) {
        optimistic { copy(shuffle = on) }
        transport(Transport.SHUFFLE, if (on) 1 else 0)
    }

    fun setRepeat(mode: Int) {
        optimistic { copy(repeat = mode) }
        transport(Transport.REPEAT, mode.toLong())
    }

    fun skipToQueue(index: Int) = transport(Transport.SKIP_TO_QUEUE, index.toLong())

    fun volume(direction: Int) = transport(Transport.VOLUME, direction.toLong())

    fun volumeTo(level: Int) = transport(Transport.VOLUME_SET, level.toLong())

    fun play(id: String, childOf: String) = send(Request(Req.PLAY, id = id, str = childOf))

    fun shuffleAll() = send(Request(Req.SHUFFLE_ALL))

    fun setSleepTimer(minutes: Int) = send(Request(Req.SLEEP, num = minutes.toLong()))

    private fun transport(action: String, num: Long = 0) =
        send(Request(Req.TRANSPORT, id = action, num = num))

    private fun send(request: Request) {
        scope.launch { link.send(request) }
    }

    /** Update the UI right away, the phone's confirmation will overwrite this a moment later. */
    private fun optimistic(change: PlayerState.() -> PlayerState) {
        _player.update { snap ->
            val now = SystemClock.elapsedRealtime()
            val rebased = snap.state.copy(positionMs = snap.positionAt(now))
            Snapshot(rebased.change(), now)
        }
    }

    companion object {
        const val PAGE_SIZE = 30
        private const val NOW_PLAYING_ART_PX = 400L
        private const val ART_TIMEOUT_MS = 10_000L
        private const val STALL_AFTER_MS = 6_000L
    }
}
