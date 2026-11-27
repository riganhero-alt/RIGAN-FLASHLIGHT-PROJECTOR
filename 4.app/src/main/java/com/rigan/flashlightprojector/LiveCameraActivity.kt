package com.rigan.flashlightprojector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.rigan.flashlightprojector.databinding.ActivityLiveCameraBinding

class LiveCameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLiveCameraBinding
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var torchOn = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "Camera permission is needed for Live Camera mode.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener { finish() }
        binding.btnTorch.setOnClickListener { setTorch(!torchOn) }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                binding.btnTorch.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
                binding.btnTorch.text = if (binding.btnTorch.isEnabled) "LED OFF" else "NO LED"
            } catch (error: Exception) {
                Toast.makeText(this, "Could not start camera preview: " + (error.localizedMessage ?: "camera unavailable"), Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setTorch(enabled: Boolean) {
        val activeCamera = camera
        if (activeCamera == null || !activeCamera.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Rear LED flash is not available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        activeCamera.cameraControl.enableTorch(enabled).addListener({
            runOnUiThread {
                torchOn = enabled
                binding.btnTorch.text = if (torchOn) "LED ON" else "LED OFF"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        camera?.cameraControl?.enableTorch(false)
        torchOn = false
        cameraProvider?.unbindAll()
        camera = null
        super.onStop()
    }
}
