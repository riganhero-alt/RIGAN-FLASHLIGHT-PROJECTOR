package com.rigan.flashlightprojector.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

enum class ProjectionPattern {
    BEAM_SWEEP,
    PULSE_AURA,
    AURORA_WAVE,
    SOLID_FLOOD
}

class ProjectorVisualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var primaryColor: Int = Color.parseColor("#00E5FF")
    private var pattern: ProjectionPattern = ProjectionPattern.BEAM_SWEEP
    private var animPhase: Float = 0f

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
    }

    private var sweepShader: SweepGradient? = null
    private var radialShader: RadialGradient? = null
    private var waveShader: LinearGradient? = null

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setProjectionColor(color: Int) {
        primaryColor = color
        rebuildShaders()
        invalidate()
    }

    fun setProjectionPattern(newPattern: ProjectionPattern) {
        pattern = newPattern
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
    }

    private fun rebuildShaders() {
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f
        val maxR = maxOf(width, height) * 0.75f

        val brightAlpha = Color.argb(235, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        val midAlpha = Color.argb(120, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        val softAlpha = Color.argb(25, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))

        radialShader = RadialGradient(
            cx, cy, maxR,
            intArrayOf(Color.WHITE, brightAlpha, midAlpha, Color.BLACK),
            floatArrayOf(0f, 0.22f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )

        sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, brightAlpha, Color.WHITE, brightAlpha, Color.TRANSPARENT, midAlpha, Color.TRANSPARENT),
            floatArrayOf(0f, 0.12f, 0.18f, 0.24f, 0.45f, 0.72f, 1f)
        )

        waveShader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.BLACK, softAlpha, brightAlpha, Color.WHITE, brightAlpha, Color.BLACK),
            null,
            Shader.TileMode.MIRROR
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#04060B"))

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = minOf(width, height) * 0.45f

        when (pattern) {
            ProjectionPattern.SOLID_FLOOD -> {
                corePaint.shader = radialShader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), corePaint)
            }

            ProjectionPattern.BEAM_SWEEP -> {
                corePaint.shader = radialShader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), corePaint)

                canvas.save()
                canvas.rotate(animPhase * 360f, cx, cy)
                beamPaint.shader = sweepShader
                canvas.drawCircle(cx, cy, maxOf(width, height).toFloat(), beamPaint)
                canvas.restore()

                drawFuturisticRings(canvas, cx, cy, baseRadius)
            }

            ProjectionPattern.PULSE_AURA -> {
                val pulseScale = 0.75f + 0.35f * sin(animPhase * 2 * Math.PI).toFloat()
                corePaint.shader = radialShader
                canvas.save()
                canvas.scale(pulseScale, pulseScale, cx, cy)
                canvas.drawCircle(cx, cy, maxOf(width, height) * 0.7f, corePaint)
                canvas.restore()

                drawFuturisticRings(canvas, cx, cy, baseRadius * pulseScale)
            }

            ProjectionPattern.AURORA_WAVE -> {
                canvas.save()
                val shiftX = cos(animPhase * 2 * Math.PI).toFloat() * (width * 0.25f)
                val shiftY = sin(animPhase * 2 * Math.PI).toFloat() * (height * 0.25f)
                canvas.translate(shiftX, shiftY)
                beamPaint.shader = waveShader
                canvas.drawRect(-width.toFloat(), -height.toFloat(), width * 2f, height * 2f, beamPaint)
                canvas.restore()

                drawFuturisticGrid(canvas)
            }
        }
    }

    private fun drawFuturisticRings(canvas: Canvas, cx: Float, cy: Float, baseRadius: Float) {
        for (i in 1..3) {
            val progress = (animPhase + i * 0.33f) % 1f
            val r = baseRadius * (0.3f + progress * 1.1f)
            val alpha = ((1f - progress) * 180).toInt().coerceIn(0, 255)
            ringPaint.color = Color.argb(
                alpha,
                Color.red(primaryColor),
                Color.green(primaryColor),
                Color.blue(primaryColor)
            )
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
    }

    private fun drawFuturisticGrid(canvas: Canvas) {
        gridPaint.color = Color.argb(40, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        val step = 90f
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = (animPhase * step) % step
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }
}
