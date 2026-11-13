/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimer.kt is part of Auxio.
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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.oxycblt.auxio.companion.protocol.Transport

/**
 * Sleep timer. Auxio doesn't have one, so the bridge implements it.
 *
 * It uses an inexact-while-idle alarm so it needs no special permission and still fires in Doze,
 * at the cost of firing a little late (typically within a few minutes) on an idle phone.
 */
object SleepTimer {
    private const val PREFS = "sleep_timer"
    private const val KEY_END = "end_at"

    fun set(context: Context, minutes: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pending(context)
        alarms.cancel(pending)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (minutes <= 0) {
            prefs.edit().remove(KEY_END).apply()
            return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        prefs.edit().putLong(KEY_END, end).apply()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pending)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_END).apply()
    }

    fun remainingMs(context: Context): Long {
        val end = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_END, 0L)
        val left = end - System.currentTimeMillis()
        return if (end == 0L || left <= 0L) 0L else left
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, SleepReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.companionApp
        val result = goAsync()
        app.scope.launch {
            try {
                SleepTimer.clear(context)
                if (app.link.connect()) {
                    app.link.transport(Transport.PAUSE, 0)
                }
                app.pusher.pushNow()
            } finally {
                result.finish()
            }
        }
    }
}
