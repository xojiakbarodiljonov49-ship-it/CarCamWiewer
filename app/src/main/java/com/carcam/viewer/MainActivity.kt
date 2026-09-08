package com.carcam.viewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView
    private lateinit var prefs: SharedPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var currentChannel = "front"

    private val defaultUrls = mapOf(
        "front" to "rtsp://192.168.1.254:554/live",
        "rear" to "rtsp://192.168.1.254:554/live2",
        "inside" to "rtsp://192.168.1.254:554/live3"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("carcam_prefs", Context.MODE_PRIVATE)
        playerView = findViewById(R.id.playerView)
        statusText = findViewById(R.id.statusText)
        val settingsButton = findViewById<View>(R.id.settingsButton)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> statusText.visibility = View.GONE
                    Player.STATE_BUFFERING -> {
                        statusText.text = "Ulanmoqda..."
                        statusText.visibility = View.VISIBLE
                    }
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                statusText.text = "Aloqa yo'q. Qayta ulanmoqda..."
                statusText.visibility = View.VISIBLE
                scheduleReconnect()
            }
        })

        settingsButton.setOnClickListener { showSettingsDialog() }
        playerView.setOnClickListener { cycleChannel() }

        startStream(currentChannel)
    }

    private fun cycleChannel() {
        currentChannel = when (currentChannel) {
            "front" -> "rear"
            "rear" -> "inside"
            else -> "front"
        }
        startStream(currentChannel)
    }

    private fun getUrlFor(channel: String): String {
        return prefs.getString("url_$channel", defaultUrls[channel]) ?: defaultUrls.getValue(channel)
    }

    private fun startStream(channel: String) {
        cancelReconnect()
        val url = getUrlFor(channel)
        statusText.text = "Ulanmoqda ($channel)..."
        statusText.visibility = View.VISIBLE

        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        reconnectRunnable = Runnable { startStream(currentChannel) }
        mainHandler.postDelayed(reconnectRunnable!!, 3000)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun showSettingsDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 32, 48, 0)

        val frontInput = EditText(this).apply {
            hint = "Old kamera RTSP manzili"
            setText(getUrlFor("front"))
        }
        val rearInput = EditText(this).apply {
            hint = "Orqa kamera RTSP manzili"
            setText(getUrlFor("rear"))
        }
        val insideInput = EditText(this).apply {
            hint = "Ichki kamera RTSP manzili"
            setText(getUrlFor("inside"))
        }

        layout.addView(TextView(this).apply { text = "Old kamera:" })
        layout.addView(frontInput)
        layout.addView(TextView(this).apply { text = "Orqa kamera:" })
        layout.addView(rearInput)
        layout.addView(TextView(this).apply { text = "Ichki kamera:" })
        layout.addView(insideInput)

        AlertDialog.Builder(this)
            .setTitle("Kamera manzillari")
            .setView(layout)
            .setPositiveButton("Saqlash") { _, _ ->
                prefs.edit()
                    .putString("url_front", frontInput.text.toString().trim())
                    .putString("url_rear", rearInput.text.toString().trim())
                    .putString("url_inside", insideInput.text.toString().trim())
                    .apply()
                startStream(currentChannel)
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        cancelReconnect()
        player.playWhenReady = false
    }

    override fun onStart() {
        super.onStart()
        startStream(currentChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelReconnect()
        player.release()
    }
}
