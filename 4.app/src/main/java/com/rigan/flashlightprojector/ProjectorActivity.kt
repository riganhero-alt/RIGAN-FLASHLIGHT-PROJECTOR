package com.rigan.flashlightprojector

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rigan.flashlightprojector.databinding.ActivityProjectorBinding
import com.rigan.flashlightprojector.ui.ProjectionPattern

class ProjectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectorBinding
    private var hudVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityProjectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableFullscreenImmersive()
        setWindowBrightness(100)
        setupControls()
    }

    private fun enableFullscreenImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }

        // Tap background visual to toggle HUD for distraction-free projection
        binding.projectorVisualView.setOnClickListener {
            hudVisible = !hudVisible
            binding.hudContainer.animate()
                .alpha(if (hudVisible) 1f else 0f)
                .setDuration(220L)
                .withStartAction {
                    if (hudVisible) binding.hudContainer.visibility = View.VISIBLE
                }
                .withEndAction {
                    if (!hudVisible) binding.hudContainer.visibility = View.GONE
                }
                .start()
        }

        binding.btnToggleHud.setOnClickListener {
            binding.projectorVisualView.performClick()
        }

        // Brightness slider
        binding.seekProjectorBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val safeVal = progress.coerceAtLeast(10)
                setWindowBrightness(safeVal)
                binding.tvProjectorBrightness.text = "$safeVal%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Pattern buttons
        binding.btnPatternBeam.setOnClickListener {
            binding.projectorVisualView.setProjectionPattern(ProjectionPattern.BEAM_SWEEP)
            highlightPatternButton(binding.btnPatternBeam)
        }
        binding.btnPatternPulse.setOnClickListener {
            binding.projectorVisualView.setProjectionPattern(ProjectionPattern.PULSE_AURA)
            highlightPatternButton(binding.btnPatternPulse)
        }
        binding.btnPatternWave.setOnClickListener {
            binding.projectorVisualView.setProjectionPattern(ProjectionPattern.AURORA_WAVE)
            highlightPatternButton(binding.btnPatternWave)
        }
        binding.btnPatternSolid.setOnClickListener {
            binding.projectorVisualView.setProjectionPattern(ProjectionPattern.SOLID_FLOOD)
            highlightPatternButton(binding.btnPatternSolid)
        }

        // Color swatches
        binding.colorBlue.setOnClickListener { applyColor(R.color.rigan_electric_blue) }
        binding.colorPurple.setOnClickListener { applyColor(R.color.proj_violet) }
        binding.colorRed.setOnClickListener { applyColor(R.color.proj_red) }
        binding.colorGreen.setOnClickListener { applyColor(R.color.proj_green) }
        binding.colorGold.setOnClickListener { applyColor(R.color.proj_gold) }
        binding.colorWhite.setOnClickListener { applyColor(R.color.rigan_white) }
    }

    private fun applyColor(colorRes: Int) {
        binding.projectorVisualView.setProjectionColor(ContextCompat.getColor(this, colorRes))
    }

    private fun highlightPatternButton(selected: View) {
        listOf(
            binding.btnPatternBeam,
            binding.btnPatternPulse,
            binding.btnPatternWave,
            binding.btnPatternSolid
        ).forEach { btn ->
            btn.setBackgroundResource(
                if (btn == selected) R.drawable.bg_chip_selected else R.drawable.bg_glass_card
            )
        }
    }

    private fun setWindowBrightness(percent: Int) {
        val params = window.attributes
        params.screenBrightness = percent.coerceIn(10, 100) / 100f
        window.attributes = params
    }
}
