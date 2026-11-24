package com.rigan.flashlightprojector

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

enum class FlashMode {
    NORMAL,
    SOS,
    STROBE,
    EMERGENCY
}

class FlashlightController(
    private val context: Context,
    private val onTorchStateChanged: (isOn: Boolean) -> Unit,
    private val onEmergencyPhaseChanged: (isRedPhase: Boolean) -> Unit,
    private val onError: (message: String) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handler = Handler(Looper.getMainLooper())

    private var cameraIdWithFlash: String? = null
    private var isFlashAvailable: Boolean = false
    private var isTorchCurrentlyOn: Boolean = false
    private var isPatternRunning: Boolean = false
    private var currentMode: FlashMode = FlashMode.NORMAL
    private var strobeIntervalMs: Long = 180L
    private var emergencyPhaseRed: Boolean = true

    // Morse code SOS durations (true = ON, false = OFF)
    private val sosSteps = listOf(
        true to 200L, false to 200L, true to 200L, false to 200L, true to 200L, false to 500L, // S
        true to 600L, false to 200L, true to 600L, false to 200L, true to 600L, false to 500L, // O
        true to 200L, false to 200L, true to 200L, false to 200L, true to 200L, false to 1200L // S
    )

    // Rapid emergency triple-beacon
    private val emergencySteps = listOf(
        true to 85L, false to 65L, true to 85L, false to 65L, true to 85L, false to 320L
    )

    private var patternStepIndex = 0
    private var callbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == cameraIdWithFlash) {
                isTorchCurrentlyOn = enabled
                onTorchStateChanged(enabled)
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == cameraIdWithFlash && isPatternRunning) {
                stopPatternOnly()
                onError("Camera flash is currently in use by another app.")
            }
        }
    }

    init {
        detectFlashHardware()
        registerCallback()
    }

    private fun detectFlashHardware() {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasFeature) {
            isFlashAvailable = false
            return
        }

        try {
            var fallbackId: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (flashSupported) {
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraIdWithFlash = id
                        isFlashAvailable = true
                        return
                    }
                    if (fallbackId == null) fallbackId = id
                }
            }
            if (fallbackId != null) {
                cameraIdWithFlash = fallbackId
                isFlashAvailable = true
            }
        } catch (_: CameraAccessException) {
            isFlashAvailable = false
        }
    }

    fun hasFlash(): Boolean = isFlashAvailable && cameraIdWithFlash != null

    fun isActive(): Boolean = isTorchCurrentlyOn || isPatternRunning

    fun getCurrentMode(): FlashMode = currentMode

    fun setMode(mode: FlashMode) {
        val wasActive = isActive()
        currentMode = mode
        if (wasActive) {
            stopAll()
            startActiveMode()
        }
    }

    fun setStrobeSpeedPercent(percent: Int) {
        // 0% -> 500ms slow strobe, 100% -> 50ms rapid strobe
        val clamped = percent.coerceIn(0, 100)
        strobeIntervalMs = (500 - (clamped * 4.5)).toLong().coerceIn(50L, 500L)
    }

    fun toggle() {
        if (!hasFlash()) {
            onError("No camera flash detected on this device.")
            return
        }
        if (isActive()) {
            stopAll()
        } else {
            startActiveMode()
        }
    }

    fun startActiveMode() {
        if (!hasFlash()) {
            onError("No camera flash detected on this device.")
            return
        }
        stopPatternOnly()
        when (currentMode) {
            FlashMode.NORMAL -> setHardwareTorch(true)
            FlashMode.SOS -> startSequencePattern(sosSteps, isEmergency = false)
            FlashMode.STROBE -> startStrobePattern()
            FlashMode.EMERGENCY -> startSequencePattern(emergencySteps, isEmergency = true)
        }
    }

    fun stopAll() {
        stopPatternOnly()
        setHardwareTorch(false)
    }

    private fun stopPatternOnly() {
        isPatternRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startStrobePattern() {
        isPatternRunning = true
        var nextOn = true
        val runnable = object : Runnable {
            override fun run() {
                if (!isPatternRunning) return
                setHardwareTorch(nextOn)
                nextOn = !nextOn
                handler.postDelayed(this, strobeIntervalMs)
            }
        }
        handler.post(runnable)
    }

    private fun startSequencePattern(steps: List<Pair<Boolean, Long>>, isEmergency: Boolean) {
        isPatternRunning = true
        patternStepIndex = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!isPatternRunning) return
                val (turnOn, duration) = steps[patternStepIndex]
                setHardwareTorch(turnOn)

                if (isEmergency && patternStepIndex == 0) {
                    emergencyPhaseRed = !emergencyPhaseRed
                    onEmergencyPhaseChanged(emergencyPhaseRed)
                }

                patternStepIndex = (patternStepIndex + 1) % steps.size
                handler.postDelayed(this, duration)
            }
        }
        handler.post(runnable)
    }

    private fun setHardwareTorch(enabled: Boolean) {
        val id = cameraIdWithFlash ?: return
        try {
            cameraManager.setTorchMode(id, enabled)
            isTorchCurrentlyOn = enabled
        } catch (e: CameraAccessException) {
            stopPatternOnly()
            onError("Unable to access flashlight: ${e.localizedMessage ?: "Camera busy"}")
        } catch (e: SecurityException) {
            stopPatternOnly()
            onError("Camera permission required to control flashlight.")
        } catch (e: IllegalArgumentException) {
            stopPatternOnly()
            onError("Flashlight hardware unavailable.")
        }
    }

    private fun registerCallback() {
        if (!callbackRegistered) {
            try {
                cameraManager.registerTorchCallback(torchCallback, handler)
                callbackRegistered = true
            } catch (_: Exception) {
                // Safe fallback if system refuses registration
            }
        }
    }

    fun release() {
        stopAll()
        if (callbackRegistered) {
            try {
                cameraManager.unregisterTorchCallback(torchCallback)
            } catch (_: Exception) {
            }
            callbackRegistered = false
        }
    }
}
