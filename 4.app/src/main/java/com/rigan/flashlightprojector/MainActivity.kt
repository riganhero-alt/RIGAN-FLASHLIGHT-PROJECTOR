package com.rigan.flashlightprojector

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rigan.flashlightprojector.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var flashlightController: FlashlightController

    private var pulseAnimator: ObjectAnimator? = null
    private var batteryReceiverRegistered = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            flashlightController.toggle()
            updateDashboardUi()
        } else {
            // Attempt torch directly because Android 6+ CameraManager.setTorchMode
            // works without camera capture permission on most AOSP devices, or inform user cleanly.
            flashlightController.toggle()
            updateDashboardUi()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                binding.tvBattery.text = if (isCharging) "$pct% • CHG" else "$pct%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        flashlightController = FlashlightController(
            context = this,
            onTorchStateChanged = { isOn ->
                runOnUiThread {
                    updateGlowVisual(isOn || flashlightController.isActive())
                    updateStatusText()
                }
            },
            onEmergencyPhaseChanged = { isRedPhase ->
                runOnUiThread {
                    if (flashlightController.isActive() &&
                        flashlightController.getCurrentMode() == FlashMode.EMERGENCY
                    ) {
                        binding.emergencyOverlay.visibility = View.VISIBLE
                        binding.emergencyOverlay.setBackgroundColor(
                            ContextCompat.getColor(
                                this,
                                if (isRedPhase) R.color.emergency_red_tint else R.color.emergency_blue_tint
                            )
                        )
                    }
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    updateDashboardUi()
                }
            }
        )

        setupControls()
        checkHardwareAvailability()
        updateDashboardUi()
    }

    private fun setupControls() {
        // Center Power Button
        binding.btnPower.setOnClickListener {
            if (!flashlightController.hasFlash()) {
                Toast.makeText(this, R.string.no_flash_message, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                flashlightController.toggle()
                updateDashboardUi()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Mode Buttons
        binding.btnModeNormal.setOnClickListener { selectMode(FlashMode.NORMAL) }
        binding.btnModeSos.setOnClickListener { selectMode(FlashMode.SOS) }
        binding.btnModeStrobe.setOnClickListener { selectMode(FlashMode.STROBE) }
        binding.btnModeEmergency.setOnClickListener { selectMode(FlashMode.EMERGENCY) }

        // Strobe Speed SeekBar
        binding.seekStrobeSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                flashlightController.setStrobeSpeedPercent(progress)
                val hz = 1.0f + (progress / 100f) * 9.0f
                binding.tvStrobeSpeed.text = String.format("%.1f Hz", hz)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Screen Brightness SeekBar (App window level - requires zero special permissions)
        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(5)
                val params = window.attributes
                params.screenBrightness = clamped / 100f
                window.attributes = params
                binding.tvBrightnessValue.text = "$clamped%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Open Full-Screen Visual Projector Experience
        binding.btnOpenProjector.setOnClickListener {
            stopHardwareFlash()
            startActivity(Intent(this, ProjectorActivity::class.java))
        }
        binding.btnOpenLiveCamera.setOnClickListener {
            stopHardwareFlash()
            startActivity(Intent(this, LiveCameraActivity::class.java))
        }
        binding.btnOpenGalleryVideo.setOnClickListener {
            stopHardwareFlash()
            startActivity(Intent(this, GalleryVideoActivity::class.java))
        }
    }

    private fun stopHardwareFlash() {
        if (flashlightController.isActive()) {
            flashlightController.stopAll()
            updateDashboardUi()
        }
    }

    private fun selectMode(mode: FlashMode) {
        flashlightController.setMode(mode)
        updateDashboardUi()
    }

    private fun checkHardwareAvailability() {
        if (!flashlightController.hasFlash()) {
            binding.tvFlashWarning.visibility = View.VISIBLE
            binding.btnPower.alpha = 0.45f
        } else {
            binding.tvFlashWarning.visibility = View.GONE
            binding.btnPower.alpha = 1.0f
        }
    }

    private fun updateDashboardUi() {
        val active = flashlightController.isActive()
        val mode = flashlightController.getCurrentMode()

        updateGlowVisual(active)
        updateStatusText()

        // Highlight active mode button
        val modeButtons = mapOf(
            FlashMode.NORMAL to binding.btnModeNormal,
            FlashMode.SOS to binding.btnModeSos,
            FlashMode.STROBE to binding.btnModeStrobe,
            FlashMode.EMERGENCY to binding.btnModeEmergency
        )
        modeButtons.forEach { (m, btn) ->
            val selected = m == mode
            btn.setBackgroundResource(
                if (selected) R.drawable.bg_chip_selected else R.drawable.bg_glass_card
            )
            btn.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (selected) R.color.rigan_electric_blue else R.color.rigan_text_secondary
                )
            )
        }

        // Show strobe speed slider when Strobe is selected
        binding.layoutStrobeControl.visibility =
            if (mode == FlashMode.STROBE) View.VISIBLE else View.GONE

        // Hide emergency overlay when not actively running Emergency mode
        if (!active || mode != FlashMode.EMERGENCY) {
            binding.emergencyOverlay.visibility = View.GONE
        }
    }

    private fun updateStatusText() {
        if (!flashlightController.hasFlash()) {
            binding.tvStatus.text = getString(R.string.status_no_flash)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.rigan_danger))
            return
        }

        val active = flashlightController.isActive()
        val label = if (!active) {
            getString(R.string.status_standby)
        } else {
            when (flashlightController.getCurrentMode()) {
                FlashMode.NORMAL -> getString(R.string.status_torch_on)
                FlashMode.SOS -> getString(R.string.status_sos_active)
                FlashMode.STROBE -> getString(R.string.status_strobe_active)
                FlashMode.EMERGENCY -> getString(R.string.status_emergency_active)
            }
        }
        binding.tvStatus.text = label
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (active) R.color.rigan_electric_blue else R.color.rigan_text_secondary
            )
        )
    }

    private fun updateGlowVisual(isActive: Boolean) {
        binding.btnPower.setBackgroundResource(
            if (isActive) R.drawable.bg_power_button_on else R.drawable.bg_power_button_off
        )
        binding.ivPowerIcon.setColorFilter(
            ContextCompat.getColor(
                this,
                if (isActive) R.color.rigan_white else R.color.rigan_electric_blue
            )
        )

        if (isActive) {
            binding.viewGlowRing.animate().alpha(1f).scaleX(1.08f).scaleY(1.08f).setDuration(250).start()
            startPulseAnimation()
        } else {
            stopPulseAnimation()
            binding.viewGlowRing.animate().alpha(0.25f).scaleX(1f).scaleY(1f).setDuration(250).start()
        }
    }

    private fun startPulseAnimation() {
        if (pulseAnimator?.isRunning == true) return
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.28f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.28f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0.0f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.viewOuterPulse, scaleX, scaleY, alpha
        ).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.viewOuterPulse.alpha = 0f
        binding.viewOuterPulse.scaleX = 1f
        binding.viewOuterPulse.scaleY = 1f
    }

    override fun onStart() {
        super.onStart()
        if (!batteryReceiverRegistered) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        }
        // If user is leaving/closing the activity, turn off flashlight cleanly
        if (isFinishing) {
            flashlightController.stopAll()
        }
    }

    override fun onDestroy() {
        stopPulseAnimation()
        flashlightController.release()
        super.onDestroy()
    }
}
