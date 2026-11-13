/*
 * Copyright (c) 2026 Auxio Project
 * Paging.kt is part of Auxio.
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

package org.oxycblt.auxio.companion.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.oxycblt.auxio.companion.protocol.Event
import org.oxycblt.auxio.companion.protocol.MediaEntry

/**
 * A list that fills itself one page at a time from the phone.
 *
 * Messages between watch and phone are small, so a 2,000 song library cannot arrive at once.
 * [fetch] is asked for the rows that start at an absolute offset, beginning at [base].
 */
class PagedList(
    private val scope: CoroutineScope,
    private val base: Int = 0,
    private val fetch: suspend (offset: Int) -> Event?,
) {
    val items = mutableStateListOf<MediaEntry>()

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var total by mutableIntStateOf(Int.MAX_VALUE)

    val hasMore: Boolean
        get() = base + items.size < total

    /** True once a load finished successfully and there was nothing in it. */
    val isEmpty: Boolean
        get() = !loading && error == null && items.isEmpty() && total != Int.MAX_VALUE

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        scope.launch {
            val event = fetch(base + items.size)
            loading = false
            when {
                event == null -> error = "No answer from your phone"
                event.error != null -> error = event.error
                else -> {
                    items.addAll(event.entries)
                    // A page with nothing in it means we are done, whatever the total claimed.
                    total = if (event.entries.isEmpty()) base + items.size else event.total
                }
            }
        }
    }

    fun retry() {
        error = null
        loadMore()
    }
}
