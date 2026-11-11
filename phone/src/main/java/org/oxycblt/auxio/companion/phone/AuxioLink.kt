/*
 * Copyright (c) 2026 Auxio Project
 * AuxioLink.kt is part of Auxio.
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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.utils.MediaConstants
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.oxycblt.auxio.companion.protocol.ItemKind
import org.oxycblt.auxio.companion.protocol.MediaEntry
import org.oxycblt.auxio.companion.protocol.PlayerState
import org.oxycblt.auxio.companion.protocol.TabKind
import org.oxycblt.auxio.companion.protocol.Transport
import org.oxycblt.auxio.companion.protocol.Wire

/**
 * The bridge's connection to Auxio.
 *
 * This talks to Auxio exactly the way Android Auto does: it binds to `AuxioService` as a
 * `MediaBrowserService` client, browses Auxio's media tree, and drives the playback through the
 * `MediaSession` that service publishes. Nothing here needs any change to Auxio itself.
 *
 * What Auxio's tree looks like (see `MusicBrowser.kt` / `TabNode.kt` in Auxio):
 * - root (`mctt`) -> one browsable child per enabled home tab (`mcts`, `mcta`, `mctr`, `mctg`,
 *   `mctp`) unless the client's root-children limit forces a "More" tab (`mctm`)
 * - a tab -> its items. Albums/artists/genres/playlists are browsable, songs are playable.
 * - an album/artist/genre/playlist (`mi<uid>`) -> its songs, with a `CHILD_OF` extra so that
 *   playing a song plays it in the context of that parent.
 */
class AuxioLink(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val browseMutex = Mutex()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile private var browser: MediaBrowserCompat? = null
    @Volatile private var controller: MediaControllerCompat? = null
    private var connecting: CompletableDeferred<Boolean>? = null

    /** Package of the Auxio build we are currently connected to, or null. */
    @Volatile
    var connectedPackage: String? = null
        private set

    /** Cover URIs we have handed out keys for. The watch only ever sends back keys. */
    private val artIndex =
        object : LinkedHashMap<String, Uri>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Uri>?) =
                size > MAX_ART_KEYS
        }

    private val controllerCallback =
        object : MediaControllerCompat.Callback() {
            override fun onSessionDestroyed() = notifyChanged()

            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = notifyChanged()

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) = notifyChanged()

            override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) =
                notifyChanged()

            override fun onRepeatModeChanged(repeatMode: Int) = notifyChanged()

            override fun onShuffleModeChanged(shuffleMode: Int) = notifyChanged()
        }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it() }
    }

    // --- CONNECTION ---

    /** The first installed Auxio build (release first, then the debug build), or null. */
    fun installedPackage(): String? =
        AUXIO_PACKAGES.firstOrNull { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    /** Ensure we are bound to Auxio. Safe to call constantly, returns fast when connected. */
    suspend fun connect(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (controller != null && browser?.isConnected == true) {
                return@withContext true
            }
            connecting?.let {
                return@withContext it.await()
            }
            val pkg = installedPackage() ?: return@withContext false

            val result = CompletableDeferred<Boolean>()
            connecting = result

            val callbacks =
                object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() {
                        try {
                            val b = checkNotNull(browser)
                            val c = MediaControllerCompat(context, b.sessionToken)
                            c.registerCallback(controllerCallback, main)
                            controller = c
                            connectedPackage = pkg
                            result.complete(true)
                            notifyChanged()
                        } catch (e: Exception) {
                            tearDown()
                            result.complete(false)
                        }
                        connecting = null
                    }

                    override fun onConnectionFailed() {
                        tearDown()
                        connecting = null
                        result.complete(false)
                    }

                    override fun onConnectionSuspended() {
                        tearDown()
                        notifyChanged()
                    }
                }

            // Auxio hides tabs beyond the client's root-children limit behind a "More" folder.
            // We have a scrollable list, so ask for plenty of room.
            val hints =
                Bundle().apply {
                    putInt(MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT, 12)
                }
            browser =
                MediaBrowserCompat(context, ComponentName(pkg, AUXIO_SERVICE), callbacks, hints)
            browser?.connect()

            val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { result.await() } ?: false
            if (!ok && connecting === result) {
                tearDown()
                connecting = null
            }
            ok
        }

    private fun tearDown() {
        controller?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        controller = null
        browser?.let { runCatching { it.disconnect() } }
        browser = null
        connectedPackage = null
    }

    // --- STATE ---

    fun snapshot(sleepRemainingMs: Long): PlayerState {
        val audio = context.getSystemService(AudioManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val c = controller ?: return PlayerState(volume = volume, maxVolume = maxVolume)
        val metadata = c.metadata
        val playback = c.playbackState
        if (metadata == null || playback == null || playback.state == PlaybackStateCompat.STATE_NONE) {
            return PlayerState(volume = volume, maxVolume = maxVolume)
        }

        val playing = playback.state == PlaybackStateCompat.STATE_PLAYING
        val speed = if (playback.playbackSpeed > 0f) playback.playbackSpeed else 1f
        val position =
            if (playing) {
                val elapsed = SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime
                playback.position + (elapsed * speed).toLong()
            } else {
                playback.position
            }
        val art =
            metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        val activeQueueId = playback.activeQueueItemId
        val title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        // Auxio doesn't publish a media id with its metadata, derive something that changes with
        // the track.
        val trackId =
            metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                ?: "$title|$artist|$album|$duration"

        return PlayerState(
            active = true,
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            positionMs = position.coerceAtLeast(0),
            playing = playing,
            speed = speed,
            shuffle = c.shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE,
            repeat =
                when (c.repeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_ALL,
                    PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                    PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                    else -> 0
                },
            queueTitle = c.queueTitle?.toString() ?: "",
            queueIndex =
                if (activeQueueId == MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()) -1
                else activeQueueId.toInt(),
            queueSize = c.queue?.size ?: 0,
            artVersion = art?.let { System.identityHashCode(it) } ?: 0,
            volume = volume,
            maxVolume = maxVolume,
            sleepRemainingMs = sleepRemainingMs,
        )
    }

    // --- BROWSING ---

    /** Children of [parentId], or of the library root when it is empty. Null on failure. */
    suspend fun children(parentId: String): List<MediaBrowserCompat.MediaItem>? =
        browseMutex.withLock {
            withTimeoutOrNull(BROWSE_TIMEOUT_MS) {
                withContext(Dispatchers.Main.immediate) {
                    val b = browser?.takeIf { it.isConnected } ?: return@withContext null
                    val id = if (parentId.isEmpty()) b.root else parentId
                    suspendCancellableCoroutine<List<MediaBrowserCompat.MediaItem>?> { cont ->
                        val callback =
                            object : MediaBrowserCompat.SubscriptionCallback() {
                                override fun onChildrenLoaded(
                                    parentId: String,
                                    children: MutableList<MediaBrowserCompat.MediaItem>,
                                ) {
                                    b.unsubscribe(id, this)
                                    if (cont.isActive) cont.resume(children)
                                }

                                override fun onError(parentId: String) {
                                    b.unsubscribe(id, this)
                                    if (cont.isActive) cont.resume(null)
                                }
                            }
                        b.subscribe(id, callback)
                        cont.invokeOnCancellation { main.post { b.unsubscribe(id, callback) } }
                    }
                }
            }
        }

    suspend fun search(query: String): List<MediaBrowserCompat.MediaItem>? =
        browseMutex.withLock {
            withTimeoutOrNull(BROWSE_TIMEOUT_MS) {
                withContext(Dispatchers.Main.immediate) {
                    val b = browser?.takeIf { it.isConnected } ?: return@withContext null
                    suspendCancellableCoroutine<List<MediaBrowserCompat.MediaItem>?> { cont ->
                        b.search(
                            query,
                            null,
                            object : MediaBrowserCompat.SearchCallback() {
                                override fun onSearchResult(
                                    query: String,
                                    extras: Bundle?,
                                    items: MutableList<MediaBrowserCompat.MediaItem>,
                                ) {
                                    if (cont.isActive) cont.resume(items)
                                }

                                override fun onError(query: String, extras: Bundle?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            },
                        )
                    }
                }
            }
        }

    /** The slice of the live playback queue that starts at [offset]. */
    fun queuePage(offset: Int, limit: Int): Pair<List<MediaEntry>, Int> {
        val queue = controller?.queue ?: return emptyList<MediaEntry>() to 0
        val from = offset.coerceIn(0, queue.size)
        val to = (from + limit).coerceAtMost(queue.size)
        val entries =
            queue.subList(from, to).map { item ->
                val d = item.description
                MediaEntry(
                    id = d.mediaId ?: "",
                    title = d.title?.toString() ?: "",
                    subtitle = d.subtitle?.toString() ?: "",
                    art = artKey(d.iconUri),
                    playable = true,
                    kind = ItemKind.SONG,
                    index = item.queueId.toInt(),
                )
            }
        return entries to queue.size
    }

    /** Translate one of Auxio's [MediaBrowserCompat.MediaItem]s into a watch row. */
    fun entryOf(item: MediaBrowserCompat.MediaItem): MediaEntry {
        val d = item.description
        val id = d.mediaId ?: ""
        val extras = d.extras
        return MediaEntry(
            id = id,
            title = d.title?.toString() ?: "",
            subtitle = d.subtitle?.toString() ?: "",
            art = artKey(d.iconUri),
            playable = item.isPlayable,
            browsable = item.isBrowsable,
            group =
                extras?.getString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE)
                    ?: "",
            childOf = extras?.getString("$connectedPackage.key.CHILD_OF") ?: "",
            tab = tabKindOf(id),
            kind = itemKindOf(id),
        )
    }

    /** Auxio's item ids look like `mi` + `u` + format char + item char + uuid, see `Music.UID`. */
    private fun itemKindOf(id: String): String {
        if (!id.startsWith("miu") || id.length < 5) return ItemKind.NONE
        return when (id[4]) {
            's' -> ItemKind.SONG
            'a' -> ItemKind.ALBUM
            'r' -> ItemKind.ARTIST
            'g' -> ItemKind.GENRE
            'p' -> ItemKind.PLAYLIST
            else -> ItemKind.NONE
        }
    }

    /** Auxio's compressed tab ids, see `TabNode.microId`. */
    private fun tabKindOf(id: String): String {
        if (!id.startsWith("mct") || id.length != 4) return TabKind.NONE
        return when (id[3]) {
            's' -> TabKind.SONGS
            'a' -> TabKind.ALBUMS
            'r' -> TabKind.ARTISTS
            'g' -> TabKind.GENRES
            'p' -> TabKind.PLAYLISTS
            'm' -> TabKind.MORE
            else -> TabKind.NONE
        }
    }

    private fun artKey(uri: Uri?): String {
        if (uri == null) return ""
        val key = uri.lastPathSegment ?: return ""
        synchronized(artIndex) { artIndex[key] = uri }
        return key
    }

    // --- CONTROL ---

    fun transport(action: String, num: Long) {
        main.post {
            val c = controller ?: return@post
            val tc = c.transportControls
            when (action) {
                Transport.PLAY -> tc.play()
                Transport.PAUSE -> tc.pause()
                Transport.TOGGLE ->
                    if (c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) tc.pause()
                    else tc.play()
                Transport.NEXT -> tc.skipToNext()
                Transport.PREV -> tc.skipToPrevious()
                Transport.SEEK -> tc.seekTo(num)
                Transport.SHUFFLE ->
                    tc.setShuffleMode(
                        if (num == 1L) PlaybackStateCompat.SHUFFLE_MODE_ALL
                        else PlaybackStateCompat.SHUFFLE_MODE_NONE
                    )
                Transport.REPEAT ->
                    tc.setRepeatMode(
                        when (num) {
                            1L -> PlaybackStateCompat.REPEAT_MODE_ALL
                            2L -> PlaybackStateCompat.REPEAT_MODE_ONE
                            else -> PlaybackStateCompat.REPEAT_MODE_NONE
                        }
                    )
                Transport.SKIP_TO_QUEUE -> tc.skipToQueueItem(num)
                Transport.VOLUME -> adjustVolume(num)
            }
        }
    }

    private fun adjustVolume(direction: Long) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
        notifyChanged()
    }

    fun playMedia(mediaId: String, childOf: String) {
        main.post {
            val tc = controller?.transportControls ?: return@post
            val extras = Bundle()
            if (childOf.isNotEmpty()) {
                // Mirrors MusicBrowser.KEY_CHILD_OF in Auxio. It is namespaced by the app id, so
                // it differs between release and debug builds.
                extras.putString("$connectedPackage.key.CHILD_OF", childOf)
            }
            tc.playFromMediaId(mediaId, extras)
        }
    }

    /** Auxio treats an empty search as "play some music": shuffle the whole library. */
    fun shuffleAll() {
        main.post { controller?.transportControls?.playFromSearch("", Bundle()) }
    }

    // --- ARTWORK ---

    /** A square JPEG for [key], at most [size] pixels wide, small enough for one message. */
    suspend fun artJpeg(key: String, size: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap =
                    if (key == Wire.ART_NOW_PLAYING) {
                        val md = controller?.metadata
                        md?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                            ?: md?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
                    } else {
                        val uri = synchronized(artIndex) { artIndex[key] }
                        uri?.let { decodeUri(it, size) }
                    }
                bitmap?.let { encodeSquareJpeg(it, size) }
            } catch (e: Exception) {
                null
            }
        }

    private fun decodeUri(uri: Uri, target: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun encodeSquareJpeg(source: Bitmap, size: Int): ByteArray {
        val src =
            if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square

        var quality = 82
        while (true) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            // Leave headroom for the key header in the art message.
            if (bytes.size <= Wire.MAX_PAYLOAD - 1024 || quality <= 30) return bytes
            quality -= 12
        }
    }

    companion object {
        /** Release build first, then the `.debug` build that Auxio's own debug variant uses. */
        val AUXIO_PACKAGES = listOf("org.oxycblt.auxio", "org.oxycblt.auxio.debug")

        /** The class name is the same for both, it comes from Auxio's namespace, not its app id. */
        const val AUXIO_SERVICE = "org.oxycblt.auxio.AuxioService"

        private const val CONNECT_TIMEOUT_MS = 6_000L
        private const val BROWSE_TIMEOUT_MS = 8_000L
        private const val MAX_ART_KEYS = 2048
    }
}
