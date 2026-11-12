/*
 * Copyright (c) 2026 Auxio Project
 * CompanionApp.kt is part of Auxio.
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

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide holder for the bridge's state.
 *
 * Play Services spins [CompanionListenerService] up and down per message, so anything that has to
 * survive between messages (the connection to Auxio, which watch to push to) lives here instead.
 */
class CompanionApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val link: AuxioLink by lazy { AuxioLink(this) }
    val pusher: Pusher by lazy { Pusher(this, link, scope) }
    val handler: RequestHandler by lazy { RequestHandler(this, link, pusher) }
}

val android.content.Context.companionApp: CompanionApp
    get() = applicationContext as CompanionApp
