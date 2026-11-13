/*
 * Copyright (c) 2026 Auxio Project
 * MainActivity.kt is part of Auxio.
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.companion.protocol.Event
import org.oxycblt.auxio.companion.protocol.Status

/** What the screens talk to. All real state lives in [RemoteHub]. */
class WatchViewModel(app: Application) : AndroidViewModel(app) {
    private val hub = (app as WatchApp).hub

    val phoneNode: StateFlow<String?> = hub.phoneNode
    val player: StateFlow<Snapshot> = hub.player
    val status: StateFlow<Status?> = hub.status
    val stalled: StateFlow<Boolean> = hub.stalled
    val npArt: StateFlow<ImageBitmap?> = hub.npArt
    val home: StateFlow<Load<HomeData>> = hub.home

    fun onForeground(visible: Boolean) = hub.setUiVisible(visible)

    fun refresh() = hub.refresh()

    fun loadHome() = hub.loadHome()

    suspend fun browse(parent: String, offset: Int, limit: Int = RemoteHub.PAGE_SIZE): Event? =
        hub.browse(parent, offset, limit)

    suspend fun search(query: String, offset: Int, limit: Int = RemoteHub.PAGE_SIZE): Event? =
        hub.search(query, offset, limit)

    suspend fun queue(offset: Int, limit: Int = RemoteHub.PAGE_SIZE): Event? =
        hub.queue(offset, limit)

    fun cachedArt(key: String): ImageBitmap? = hub.cachedArt(key)

    suspend fun art(key: String, sizePx: Int): ImageBitmap? = hub.art(key, sizePx)

    fun toggle() = hub.toggle()

    fun next() = hub.next()

    fun prev() = hub.prev()

    fun seekTo(ms: Long) = hub.seekTo(ms)

    fun setShuffle(on: Boolean) = hub.setShuffle(on)

    fun setRepeat(mode: Int) = hub.setRepeat(mode)

    fun skipToQueue(index: Int) = hub.skipToQueue(index)

    fun volume(direction: Int) = hub.volume(direction)

    fun play(id: String, childOf: String) = hub.play(id, childOf)

    fun shuffleAll() = hub.shuffleAll()

    fun setSleepTimer(minutes: Int) = hub.setSleepTimer(minutes)
}
