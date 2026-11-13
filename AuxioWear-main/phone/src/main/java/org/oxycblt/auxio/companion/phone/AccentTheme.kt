/*
 * Copyright (c) 2026 Auxio Project
 * Pusher.kt is part of Auxio.
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
import android.os.Build

/**
 * Auxio's colour accents, so the watch can match the one used in Auxio.
 *
 * Auxio keeps its accent in its own private preferences, which no other app can read, so the user
 * picks the same accent here once. The hex values are Auxio's dark-theme `*_primary` colours
 * (`res/values-night/colors_ui.xml`), the right ones for the black watch UI.
 */
object AccentTheme {
    class Option(val name: String, val color: Int)

    /** Same order as Auxio's own accent list, with "Dynamic" last. */
    val OPTIONS =
        listOf(
            Option("Red", 0xFFFFB4A9.toInt()),
            Option("Pink", 0xFFFFB2BD.toInt()),
            Option("Purple", 0xFFEBB5ED.toInt()),
            Option("Deep purple", 0xFFD3BCFD.toInt()),
            Option("Indigo", 0xFFBAC3FF.toInt()),
            Option("Blue", 0xFFA1C9FD.toInt()),
            Option("Deep blue", 0xFF8BD0F0.toInt()),
            Option("Cyan", 0xFF83D2E4.toInt()),
            Option("Teal", 0xFF82D5C7.toInt()),
            Option("Green", 0xFFA2D399.toInt()),
            Option("Deep green", 0xFFB0D18B.toInt()),
            Option("Lime", 0xFFC3CD7B.toInt()),
            Option("Yellow", 0xFFF0BE6D.toInt()),
            Option("Orange", 0xFFFFB77B.toInt()),
            Option("Brown", 0xFFE7BDB0.toInt()),
            Option("Grey", 0xFFC7C6C6.toInt()),
            Option("Dynamic", 0),
        )

    const val DYNAMIC = 16
    private const val BLUE = 5
    private const val PREFS = "theme"
    private const val KEY = "accent"

    /** Mirrors Auxio: dynamic colours where the OS has them, blue elsewhere. */
    private val default =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DYNAMIC else BLUE

    fun selected(context: Context): Int {
        val i = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, default)
        return if (i in OPTIONS.indices) i else default
    }

    fun select(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, index).apply()
    }

    /** ARGB colour for [index]. "Dynamic" is read live from the phone's Material You palette. */
    fun colorOf(context: Context, index: Int = selected(context)): Int {
        if (index == DYNAMIC) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Tone 200 of the system accent is the equivalent of Auxio's "primary80".
                context.getColor(android.R.color.system_accent1_200)
            } else {
                OPTIONS[BLUE].color
            }
        }
        return OPTIONS[index].color
    }
}
