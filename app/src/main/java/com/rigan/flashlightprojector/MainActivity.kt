package com.rigan.flashlightprojector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var zoomLabel: TextView
    private var zoom = 1f
    private var cameraId: String? = null
    private var torchOn = false

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { playVideo(it) }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) applyTorchToggle()
        else Toast.makeText(this, "Flashlight ব্যবহারের জন্য Camera permission প্রয়োজন", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        setupTorch()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18)
            setBackgroundColor(0xFF07111F.toInt())
        }
        val title = TextView(this).apply {
            text = "RIGAN  •  FLASHLIGHT PROJECTOR"
            textSize = 20f
            setTextColor(0xFF19D7FF.toInt())
            setPadding(4)
        }
        val subtitle = TextView(this).apply {
            text = "ভিডিও চালাও • দুই আঙুলে Zoom করো"
            textSize = 14f
            setTextColor(0xFFE8F4FF.toInt())
            setPadding(4)
        }
        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(0xFF000000.toInt())
            setVideoTextureView(TextureView(this@MainActivity))
        }
        val playerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            .apply { topMargin = 12; bottomMargin = 12 }
        zoomLabel = TextView(this).apply {
            text = "Zoom: 1.0×"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(4)
        }
        val open = makeButton("গ্যালারি থেকে ভিডিও চালাও") { pickVideo.launch(arrayOf("video/*")) }
        val zoomIn = makeButton("＋ Zoom In") { setZoom(zoom + 0.2f) }
        val zoomOut = makeButton("－ Zoom Out") { setZoom(zoom - 0.2f) }
        val reset = makeButton("Reset Zoom") { setZoom(1f) }
        val torch = makeButton("Flashlight ON / OFF") { toggleTorch() }
        val youtube = makeButton("YouTube ভিডিও খুলুন") {
            Toast.makeText(this, "YouTube-এর নীতিমালা ও DRM-এর কারণে ভিডিওটি এখানে এনে Zoom করা যাবে না। YouTube আলাদাভাবে খুলবে।", Toast.LENGTH_LONG).show()
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))) } catch (_: Exception) { }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(playerView, playerParams)
        root.addView(zoomLabel)
        root.addView(open)
        val zoomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(zoomOut, zoomIn, reset).forEach { zoomRow.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(zoomRow)
        root.addView(torch)
        root.addView(youtube)
        setContentView(root)
        val detector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setZoom(zoom * detector.scaleFactor)
                return true
            }
        })
        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            event.pointerCount > 1
        }
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { action() }
        }

    private fun setZoom(value: Float) {
        zoom = max(1f, min(3f, value))
        playerView.videoSurfaceView?.apply { scaleX = zoom; scaleY = zoom }
        zoomLabel.text = "Zoom: %.1f×  (1.0×–3.0×)".format(zoom)
    }

    private fun playVideo(uri: Uri) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                playerView.player = player
            }
            player?.setMediaItem(MediaItem.fromUri(uri))
            player?.prepare()
            player?.playWhenReady = true
            setZoom(1f)
        } catch (e: Exception) {
            Toast.makeText(this, "ভিডিও চালানো যায়নি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTorch() {
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { cameraId = null }
    }

    private fun toggleTorch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        applyTorchToggle()
    }

    private fun applyTorchToggle() {
        val id = cameraId
        if (id == null) {
            Toast.makeText(this, "এই ফোনে Flashlight পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            torchOn = !torchOn
            (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(id, torchOn)
        } catch (_: Exception) {
            torchOn = false
            Toast.makeText(this, "Flashlight চালু করা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        try {
            if (torchOn && cameraId != null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(cameraId!!, false)
            }
        } catch (_: Exception) { }
        super.onDestroy()
    }
}
