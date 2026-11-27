package com.rigan.flashlightprojector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rigan.flashlightprojector.databinding.ActivityGalleryVideoBinding

class GalleryVideoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryVideoBinding
    private var zoom = 1f
    private lateinit var scaleDetector: ScaleGestureDetector

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Playback can still proceed for providers that do not support persistable grants.
        }
        playVideo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 3f)
                binding.videoView.scaleX = zoom
                binding.videoView.scaleY = zoom
                return true
            }
        })
        binding.videoView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            event.pointerCount > 1
        }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnResetZoom.setOnClickListener {
            zoom = 1f
            binding.videoView.scaleX = 1f
            binding.videoView.scaleY = 1f
        }
        videoPicker.launch(arrayOf("video/*"))
    }

    private fun playVideo(uri: Uri) {
        binding.videoView.setVideoURI(uri)
        binding.videoView.setMediaController(MediaController(this).apply { setAnchorView(binding.videoView) })
        binding.videoView.setOnPreparedListener {
            binding.tvHint.text = "PINCH TO ZOOM • USE PLAYER CONTROLS TO SEEK"
            binding.videoView.start()
        }
        binding.videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "This video could not be played on this device.", Toast.LENGTH_LONG).show()
            finish()
            true
        }
    }

    override fun onStop() {
        if (::binding.isInitialized) binding.videoView.stopPlayback()
        super.onStop()
    }
}
