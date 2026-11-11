/*
 * Copyright (c) 2026 Auxio Project
 * StatusActivity.kt is part of Auxio.
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

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.oxycblt.auxio.companion.protocol.Wire

/** A tiny diagnostics screen: is Auxio there, can we talk to it, is a watch with the app around. */
class StatusActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var report: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val title =
            TextView(this).apply {
                text = "Auxio Companion"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
            }
        val intro =
            TextView(this).apply {
                text =
                    "This app relays Auxio to your Wear OS watch. Leave it installed, open " +
                        "Auxio once, then open the Auxio watch app."
                setPadding(0, pad / 2, 0, pad)
            }
        report = TextView(this).apply { textSize = 16f }
        val refresh =
            Button(this).apply {
                text = "Check again"
                setOnClickListener { refresh() }
            }
        val openAuxio =
            Button(this).apply {
                text = "Open Auxio"
                setOnClickListener {
                    val pkg = companionApp.link.installedPackage()
                    packageManager.getLaunchIntentForPackage(pkg ?: return@setOnClickListener)?.let {
                        startActivity(it)
                    }
                }
            }

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                setPadding(pad, pad, pad, pad)
                addView(title)
                addView(intro)
                addView(report)
                addView(refresh)
                addView(openAuxio)
            }
        val root = ScrollView(this).apply { addView(content) }
        setContentView(root)

        // Edge-to-edge is forced on newer Android versions, keep our content out of the bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v: View, insets: WindowInsetsCompat ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun refresh() {
        report.text = "Checking…"
        scope.launch {
            val link = companionApp.link
            val lines = mutableListOf<String>()

            val installed = link.installedPackage()
            lines += if (installed != null) "✓ Auxio found ($installed)" else "✗ Auxio isn't installed"

            if (installed != null) {
                val connected = link.connect()
                lines +=
                    if (connected) "✓ Connected to Auxio's media service"
                    else "✗ Couldn't connect to Auxio's media service"
            }

            try {
                val nodes = Wearable.getNodeClient(this@StatusActivity).connectedNodes.await()
                lines +=
                    if (nodes.isEmpty()) "✗ No watch connected"
                    else "✓ Watch connected: " + nodes.joinToString { it.displayName }

                val watchApp =
                    Wearable.getCapabilityClient(this@StatusActivity)
                        .getCapability(Wire.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                        .await()
                lines +=
                    if (watchApp.nodes.isEmpty()) "✗ Auxio watch app not found on the watch"
                    else "✓ Auxio watch app found"
            } catch (e: Exception) {
                lines += "✗ Couldn't query Wear OS (is Wear OS / Google Play Services set up?)"
            }

            report.text = lines.joinToString("\n\n")
        }
    }
}
