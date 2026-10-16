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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Native animated neon globe with luminous network arcs and moving data particles. */
class AnimatedGlobeView(context: Context) : View(context) {
    private val cyan = Color.rgb(55, 226, 255)
    private val ice = Color.rgb(174, 247, 255)
    private val blue = Color.rgb(57, 125, 255)
    private val globeFill = Color.rgb(5, 22, 49)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var phase = 0f

    init {
        contentDescription = "Animated glowing digital network globe"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width * 0.265f, height * 0.335f)
        if (r <= 0f) return

        // Wide atmospheric halo and a second, brighter aura.
        glowPaint.shader = RadialGradient(cx, cy, r * 1.82f,
            intArrayOf(0x6633DFFF, 0x33245DFF, 0x142F7DFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.46f, 0.72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.82f, glowPaint)
        glowPaint.shader = null
        glowPaint.color = 0x5535DFFF
        glowPaint.setShadowLayer(dp(18f), 0f, 0f, cyan)
        canvas.drawCircle(cx, cy, r * 0.96f, glowPaint)
        glowPaint.clearShadowLayer()

        // Deep glossy sphere with a bright upper-left highlight.
        fillPaint.shader = RadialGradient(cx - r * 0.36f, cy - r * 0.40f, r * 1.48f,
            intArrayOf(Color.rgb(40, 104, 169), Color.rgb(9, 38, 77), globeFill, Color.rgb(1, 7, 20)),
            floatArrayOf(0f, 0.34f, 0.70f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.shader = null

        linePaint.color = cyan
        linePaint.strokeWidth = dp(1.8f)
        linePaint.setShadowLayer(dp(9f), 0f, 0f, cyan)
        canvas.drawCircle(cx, cy, r, linePaint)
        linePaint.clearShadowLayer()
        linePaint.color = 0xAAE5FCFF.toInt()
        linePaint.strokeWidth = dp(0.8f)
        canvas.drawArc(cx-r*0.96f, cy-r*0.96f, cx+r*0.96f, cy+r*0.96f, 205f, 115f, false, linePaint)

        // Rotating longitude mesh and pulsing latitude bands.
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r * 0.99f, Path.Direction.CW) })
        for (i in -4..4) {
            val longitude = i * 0.31f + phase
            val widthScale = abs(cos(longitude)).coerceAtLeast(0.055f)
            val alpha = if (i == 0) 220 else 95
            linePaint.color = (alpha shl 24) or 0x0046DFFF
            linePaint.strokeWidth = dp(if (i == 0) 1.6f else 0.9f)
            linePaint.setShadowLayer(dp(if (i == 0) 5f else 2f), 0f, 0f, cyan)
            canvas.drawOval(cx-r*widthScale, cy-r, cx+r*widthScale, cy+r, linePaint)
            linePaint.clearShadowLayer()
        }
        for (i in -3..3) {
            val latitude = i * 0.255f
            val sy = sin(latitude)
            val y = cy + sy * r
            val rx = r * sqrt((1f - sy * sy).coerceAtLeast(0.04f))
            linePaint.color = if (i == 0) 0xAA53E9FF.toInt() else 0x6646CFFF
            linePaint.strokeWidth = dp(if (i == 0) 1.25f else 0.75f)
            canvas.drawOval(cx-rx, y-r*0.19f, cx+rx, y+r*0.19f, linePaint)
        }

        // Recognizable stylized continents, drawn as luminous filled landmasses.
        // Shapes are intentionally simplified for a crisp digital-globe look.
        val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xAA28BFE0.toInt()
            setShadowLayer(dp(5f), 0f, 0f, cyan)
        }
        val coastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.25f)
            color = 0xEE70F2FF.toInt()
            setShadowLayer(dp(4f), 0f, 0f, cyan)
        }
        fun continent(points: List<Pair<Float, Float>>) {
            val p = Path()
            points.forEachIndexed { index, point ->
                val px = cx + point.first * r
                val py = cy + point.second * r
                if (index == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            canvas.drawPath(p, landPaint)
            canvas.drawPath(p, coastPaint)
        }
        // Europe and Asia
        continent(listOf(-0.12f to -0.48f, 0.02f to -0.55f, 0.14f to -0.48f,
            0.27f to -0.50f, 0.39f to -0.39f, 0.52f to -0.31f, 0.47f to -0.17f,
            0.32f to -0.12f, 0.25f to -0.02f, 0.12f to -0.10f, 0.02f to -0.03f,
            -0.10f to -0.14f, -0.22f to -0.19f, -0.18f to -0.34f))
        // Africa
        continent(listOf(-0.16f to -0.08f, -0.02f to -0.12f, 0.11f to -0.04f,
            0.13f to 0.12f, 0.04f to 0.38f, -0.05f to 0.49f, -0.14f to 0.28f,
            -0.21f to 0.08f))
        // North America
        continent(listOf(-0.76f to -0.43f, -0.61f to -0.56f, -0.43f to -0.50f,
            -0.33f to -0.37f, -0.41f to -0.23f, -0.55f to -0.17f,
            -0.62f to -0.02f, -0.72f to -0.13f, -0.80f to -0.30f))
        // South America
        continent(listOf(-0.48f to 0.02f, -0.34f to 0.09f, -0.31f to 0.23f,
            -0.37f to 0.40f, -0.42f to 0.58f, -0.49f to 0.43f, -0.53f to 0.21f))
        // Australia
        continent(listOf(0.44f to 0.28f, 0.58f to 0.25f, 0.65f to 0.34f,
            0.58f to 0.43f, 0.46f to 0.40f))
        landPaint.clearShadowLayer()
        coastPaint.clearShadowLayer()

        // City lights and network routes over the continents.
        val cities = listOf(
            -0.61f to -0.31f, -0.49f to -0.23f, -0.43f to 0.17f,
            -0.08f to -0.28f, 0.02f to -0.08f, 0.19f to -0.31f,
            0.32f to -0.22f, 0.42f to -0.34f, 0.51f to 0.34f
        )
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.85f)
            color = 0xBB4FEAFF.toInt()
            setShadowLayer(dp(3f), 0f, 0f, cyan)
        }
        for (i in 0 until cities.size - 1) {
            val a = cities[i]
            val b = cities[i + 1]
            val path = Path().apply {
                moveTo(cx + a.first * r, cy + a.second * r)
                quadTo(cx + (a.first + b.first) * r * 0.58f,
                    cy + (a.second + b.second) * r * 0.40f - r * 0.10f,
                    cx + b.first * r, cy + b.second * r)
            }
            canvas.drawPath(path, routePaint)
        }
        routePaint.clearShadowLayer()
        cities.forEachIndexed { index, point ->
            val x = cx + point.first * r
            val y = cy + point.second * r
            val pulse = 1.4f + (1f + sin(phase * 2.2f + index)) * 1.1f
            glowPaint.color = cyan
            glowPaint.setShadowLayer(dp(6f), 0f, 0f, cyan)
            canvas.drawCircle(x, y, dp(pulse + 1f), glowPaint)
            glowPaint.clearShadowLayer()
            dotPaint.color = Color.WHITE
            canvas.drawCircle(x, y, dp(pulse * 0.55f), dotPaint)
        }

        // Animated diagonal data trails on the globe surface.
        for (i in 0..5) {
            val yy = cy + ((i - 2.5f) * r * 0.24f)
            val drift = ((phase * 24f + i * 31f) % 90f) - 45f
            linePaint.color = 0x6644DFFF
            linePaint.strokeWidth = dp(0.8f)
            canvas.drawLine(cx-r*0.60f+drift, yy+r*0.13f, cx+r*0.60f+drift, yy-r*0.13f, linePaint)
        }
        canvas.restore()

        // Three tilted orbital rings, each with a different rotation.
        canvas.save()
        canvas.rotate(-24f, cx, cy)
        orbitPaint.color = 0xCC4DE6FF.toInt()
        orbitPaint.strokeWidth = dp(1.35f)
        orbitPaint.setShadowLayer(dp(7f), 0f, 0f, cyan)
        canvas.drawOval(cx-r*1.72f, cy-r*0.43f, cx+r*1.72f, cy+r*0.43f, orbitPaint)
        orbitPaint.clearShadowLayer()
        canvas.restore()

        canvas.save()
        canvas.rotate(56f + sin(phase * 0.42f) * 8f, cx, cy)
        orbitPaint.color = 0x885B9FFF.toInt()
        orbitPaint.strokeWidth = dp(1.05f)
        orbitPaint.setShadowLayer(dp(5f), 0f, 0f, blue)
        canvas.drawOval(cx-r*1.48f, cy-r*0.29f, cx+r*1.48f, cy+r*0.29f, orbitPaint)
        orbitPaint.clearShadowLayer()
        canvas.restore()

        canvas.save()
        canvas.rotate(phase * 18f, cx, cy)
        orbitPaint.color = 0x775DEEFF
        orbitPaint.strokeWidth = dp(0.8f)
        canvas.drawOval(cx-r*1.62f, cy-r*0.58f, cx+r*1.62f, cy+r*0.58f, orbitPaint)
        canvas.restore()

        // Bright travelling satellites with layered bloom and white-hot cores.
        for (i in 0..5) {
            val a = phase * (0.72 + i * 0.095) + i * PI / 3.0
            val ox = r * (1.52f + (i % 2) * 0.10f) * cos(a).toFloat()
            val oy = r * (0.37f + (i % 3) * 0.025f) * sin(a).toFloat()
            val x = cx + ox * 0.91f - oy * 0.41f
            val y = cy + ox * 0.41f + oy * 0.91f
            glowPaint.color = cyan
            glowPaint.setShadowLayer(dp(if (i == 0) 13f else 7f), 0f, 0f, cyan)
            canvas.drawCircle(x, y, dp(if (i == 0) 4.1f else 2.6f), glowPaint)
            glowPaint.clearShadowLayer()
            dotPaint.color = if (i == 0) Color.WHITE else ice
            canvas.drawCircle(x, y, dp(if (i == 0) 2f else 1.3f), dotPaint)
        }

        // Orbiting sparks and pulsing network points.
        for (i in 0..11) {
            val a = phase * (if (i % 2 == 0) 0.55f else -0.34f) + i * (PI.toFloat() / 6f)
            val orbitRx = r * (1.18f + (i % 3) * 0.19f)
            val orbitRy = r * (0.82f + (i % 4) * 0.10f)
            val x = cx + cos(a) * orbitRx
            val y = cy + sin(a) * orbitRy
            val pulse = 1.2f + (1f + sin(phase * 2.8f + i)) * 0.75f
            dotPaint.color = if (i % 3 == 0) Color.WHITE else if (i % 2 == 0) cyan else blue
            dotPaint.setShadowLayer(dp(4f), 0f, 0f, cyan)
            canvas.drawCircle(x, y, dp(pulse), dotPaint)
            dotPaint.clearShadowLayer()
        }

        // A soft scanning beam sweeps down the sphere.
        val scanY = cy - r + ((phase * 22f) % (2f * r))
        val beamWidth = r * sqrt((1f - ((scanY-cy)/r).let { it*it }).coerceAtLeast(0f))
        linePaint.color = 0x554FEAFF
        linePaint.strokeWidth = dp(2f)
        linePaint.setShadowLayer(dp(8f), 0f, 0f, cyan)
        canvas.drawLine(cx-beamWidth, scanY, cx+beamWidth, scanY, linePaint)
        linePaint.clearShadowLayer()

        phase = (phase + 0.025f) % (2f * PI.toFloat())
        postInvalidateDelayed(32L)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
