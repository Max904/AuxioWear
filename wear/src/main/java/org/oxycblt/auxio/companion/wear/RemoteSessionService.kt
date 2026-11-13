/*
 * Copyright (c) 2026 Auxio Project
 * RemoteSessionService.kt is part of Auxio.
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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.oxycblt.auxio.companion.protocol.PlayerState

/**
 * Makes Auxio show up in Wear OS's own media controls, the way Spotify's watch app does.
 *
 * The phone plays the music, but on the watch this service publishes a [MediaSessionCompat]
 * mirroring it. Wear OS picks that session up for its media controls (tile / smart stack /
 * notification), play, pause, skip and seek go back to the phone through [RemoteHub], and the
 * session is a *remote* one, so the watch's crown and volume keys change the phone's volume.
 *
 * It stops itself once Auxio has had no session for a minute, or has been paused for half an hour.
 */
class RemoteSessionService : Service() {
    private lateinit var hub: RemoteHub
    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var volumeProvider: VolumeProviderCompat? = null
    private var volumeMax = 0
    private var lastActiveAt = SystemClock.elapsedRealtime()
    private var lastPlayingAt = SystemClock.elapsedRealtime()
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        hub = (application as WatchApp).hub
        createChannel()

        session =
            MediaSessionCompat(this, "AuxioWear").apply {
                setCallback(
                    object : MediaSessionCompat.Callback() {
                        override fun onPlay() = hub.play()

                        override fun onPause() = hub.pause()

                        override fun onSkipToNext() = hub.next()

                        override fun onSkipToPrevious() = hub.prev()

                        override fun onSeekTo(pos: Long) = hub.seekTo(pos)

                        override fun onStop() = hub.pause()

                        override fun onSetShuffleMode(shuffleMode: Int) =
                            hub.setShuffle(shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE)

                        override fun onSetRepeatMode(repeatMode: Int) =
                            hub.setRepeat(
                                when (repeatMode) {
                                    PlaybackStateCompat.REPEAT_MODE_ALL,
                                    PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                                    PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                                    else -> 0
                                }
                            )
                    }
                )
                setSessionActivity(openAppIntent())
                isActive = true
            }

        hub.setServiceRunning(true)

        // Android wants a foreground notification right away after startForegroundService().
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(hub.player.value.state, hub.npBitmap.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (e: Exception) {
            // Not allowed to run in the foreground from here (e.g. started from the background).
            shutdown()
            return
        }

        scope.launch {
            combine(hub.player, hub.npBitmap) { snap, art -> snap to art }
                .collect { (snap, art) -> render(snap, art) }
        }
        scope.launch {
            while (true) {
                delay(10_000)
                val now = SystemClock.elapsedRealtime()
                if (now - lastActiveAt > 60_000 || now - lastPlayingAt > 30 * 60_000) {
                    shutdown()
                    break
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> hub.toggle()
            ACTION_NEXT -> hub.next()
            ACTION_PREV -> hub.prev()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    /** Mirror the phone's playback into the session, the notification and the volume provider. */
    private fun render(snap: Snapshot, art: Bitmap?) {
        val s = snap.state
        val now = SystemClock.elapsedRealtime()
        if (s.active) lastActiveAt = now
        if (s.playing) lastPlayingAt = now

        session.isActive = s.active
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, s.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, s.durationMs)
                .apply { art?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )

        val actions =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        val playbackState =
            when {
                !s.active -> PlaybackStateCompat.STATE_NONE
                s.playing -> PlaybackStateCompat.STATE_PLAYING
                else -> PlaybackStateCompat.STATE_PAUSED
            }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(playbackState, snap.positionAt(now), if (s.playing) s.speed else 0f)
                .build()
        )
        session.setShuffleMode(
            if (s.shuffle) PlaybackStateCompat.SHUFFLE_MODE_ALL
            else PlaybackStateCompat.SHUFFLE_MODE_NONE
        )
        session.setRepeatMode(
            when (s.repeat) {
                1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
        )

        updateVolume(s)

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(s, art))
        } catch (e: SecurityException) {
            // Notification permission not granted: the media session itself still works.
        }
    }

    /**
     * Make this a remote-playback session, so the crown / volume keys control the *phone's* media
     * volume instead of the watch's.
     */
    private fun updateVolume(s: PlayerState) {
        if (s.maxVolume <= 0) return
        val existing = volumeProvider
        if (existing == null || volumeMax != s.maxVolume) {
            volumeMax = s.maxVolume
            val provider =
                object :
                    VolumeProviderCompat(
                        VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
                        s.maxVolume,
                        s.volume,
                    ) {
                    override fun onSetVolumeTo(volume: Int) = hub.volumeTo(volume)

                    override fun onAdjustVolume(direction: Int) = hub.volume(direction)
                }
            volumeProvider = provider
            session.setPlaybackToRemote(provider)
        } else {
            existing.currentVolume = s.volume
        }
    }

    private fun buildNotification(s: PlayerState, art: Bitmap?): Notification {
        fun action(icon: Int, title: String, what: String) =
            NotificationCompat.Action(
                icon,
                title,
                PendingIntent.getService(
                    this,
                    what.hashCode(),
                    Intent(this, RemoteSessionService::class.java).setAction(what),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(s.title.ifEmpty { "Auxio" })
            .setContentText(s.artist)
            .setLargeIcon(art)
            .setContentIntent(openAppIntent())
            .setOngoing(s.playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
            .addAction(
                action(
                    if (s.playing) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play,
                    if (s.playing) "Pause" else "Play",
                    ACTION_TOGGLE,
                )
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun shutdown() {
        if (stopped) return
        stopped = true
        scope.cancel()
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        hub.setServiceRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "auxio_playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "org.oxycblt.auxio.companion.TOGGLE"
        private const val ACTION_NEXT = "org.oxycblt.auxio.companion.NEXT"
        private const val ACTION_PREV = "org.oxycblt.auxio.companion.PREV"

        /** Start (or keep alive) the media session. Quietly does nothing if Android says no. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RemoteSessionService::class.java),
                )
            } catch (e: Exception) {
                // Background start refused; we will try again when the app is opened.
            }
        }
    }
}
