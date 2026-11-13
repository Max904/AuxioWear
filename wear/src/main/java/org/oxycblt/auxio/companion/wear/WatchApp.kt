/*
 * Copyright (c) 2026 Auxio Project
 * WatchApp.kt is part of Auxio.
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

/**
 * Creates the [RemoteHub] as soon as the process starts, so it is already listening when the
 * phone pushes something and no screen exists yet.
 */
class WatchApp : Application() {
    lateinit var hub: RemoteHub
        private set

    override fun onCreate() {
        super.onCreate()
        hub = RemoteHub(this)
    }
}
