package com.edtatevosiani99.internetspeedtest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight animated neon globe. It is drawn natively so it stays sharp on all
 * screen densities and needs no downloaded images or extra libraries.
 */
class AnimatedGlobeView(context: Context) : View(context) {
    private val cyan = Color.rgb(48, 220, 255)
    private val paleBlue = Color.rgb(100, 164, 255)
    private val globeFill = Color.rgb(8, 27, 54)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.85f)
        color = cyan
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = paleBlue
    }
    private var phase = 0f

    init {
        contentDescription = "Animated glowing network globe"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width * 0.31f, height * 0.39f)
        if (r <= 0f) return

        // Soft halo behind the planet.
        glowPaint.shader = RadialGradient(cx, cy, r * 1.65f,
            intArrayOf(0x5531DFFF, 0x22316FFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.65f, glowPaint)
        glowPaint.shader = null

        // Dark blue sphere with a luminous edge.
        fillPaint.shader = RadialGradient(cx - r * 0.30f, cy - r * 0.34f, r * 1.5f,
            intArrayOf(Color.rgb(24, 67, 112), globeFill, Color.rgb(3, 12, 29)),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.shader = null

        linePaint.color = cyan
        linePaint.strokeWidth = dp(1.15f)
        linePaint.setShadowLayer(dp(5f), 0f, 0f, cyan)
        canvas.drawCircle(cx, cy, r, linePaint)
        linePaint.clearShadowLayer()

        // Longitudes rotate across the sphere, creating a clear sense of motion.
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r * 0.985f, Path.Direction.CW) })
        for (i in -3..3) {
            val longitude = i * 0.38f + phase
            val horizontalScale = kotlin.math.abs(cos(longitude)).coerceAtLeast(0.10f)
            linePaint.color = if (i == 0) 0xAA58E8FF.toInt() else 0x7746BDEB
            linePaint.strokeWidth = if (i == 0) dp(1.05f) else dp(0.65f)
            canvas.drawOval(cx - r * horizontalScale, cy - r, cx + r * horizontalScale, cy + r, linePaint)
        }
        // Latitude arcs become flatter toward the poles.
        for (i in -2..2) {
            val latitude = i * 0.31f
            val y = cy + sin(latitude) * r
            val rx = r * kotlin.math.sqrt((1f - sin(latitude) * sin(latitude)).coerceAtLeast(0.05f))
            linePaint.color = 0x7746D7FF
            linePaint.strokeWidth = dp(0.65f)
            canvas.drawOval(cx - rx, y - r * 0.16f, cx + rx, y + r * 0.16f, linePaint)
        }
        canvas.restore()

        // Orbit rings pass around and behind the globe.
        canvas.save()
        canvas.rotate(-22f, cx, cy)
        orbitPaint.color = 0x9947DFFF.toInt()
        orbitPaint.setShadowLayer(dp(4f), 0f, 0f, cyan)
        canvas.drawOval(cx - r * 1.55f, cy - r * 0.43f, cx + r * 1.55f, cy + r * 0.43f, orbitPaint)
        orbitPaint.clearShadowLayer()
        canvas.restore()

        // Bright nodes travel around the orbit and twinkle at different positions.
        for (i in 0..3) {
            val a = phase * (1.0 + i * 0.14) + i * PI / 2.0
            val orbitX = r * 1.52f * cos(a).toFloat()
            val orbitY = r * 0.40f * sin(a).toFloat()
            val x = cx + orbitX * 0.93f - orbitY * 0.37f
            val y = cy + orbitX * 0.37f + orbitY * 0.93f
            glowPaint.color = cyan
            glowPaint.setShadowLayer(dp(7f), 0f, 0f, cyan)
            canvas.drawCircle(x, y, dp(if (i == 0) 3.2f else 2.1f), glowPaint)
            glowPaint.clearShadowLayer()
            dotPaint.color = Color.WHITE
            canvas.drawCircle(x, y, dp(if (i == 0) 1.5f else 1.0f), dotPaint)
        }

        // Small, restrained data sparks around the globe.
        for (i in 0..5) {
            val a = phase * 0.45f + i * (PI.toFloat() / 3f)
            val x = cx + (cos(a) * r * (1.25f + (i % 2) * 0.20f)).toFloat()
            val y = cy + (sin(a) * r * (0.90f + (i % 3) * 0.12f)).toFloat()
            dotPaint.color = if (i % 2 == 0) cyan else paleBlue
            canvas.drawCircle(x, y, dp(1.25f), dotPaint)
        }

        phase = (phase + 0.035f) % (2f * PI.toFloat())
        postInvalidateDelayed(32L)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
