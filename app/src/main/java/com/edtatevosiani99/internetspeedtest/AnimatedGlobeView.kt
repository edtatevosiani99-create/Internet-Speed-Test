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

/** Animated neon globe rendered with an orthographic spherical projection. */
class AnimatedGlobeView(context: Context) : View(context) {
    private val cyan = Color.rgb(55, 226, 255)
    private val ice = Color.rgb(174, 247, 255)
    private val blue = Color.rgb(57, 125, 255)
    private val globeFill = Color.rgb(5, 22, 49)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAA28BFE0.toInt()
    }
    private val coastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.25f
        color = 0xEE70F2FF.toInt()
    }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // Coordinates are real-world longitude/latitude degrees, not screen-space shapes.
    private data class Geo(val lon: Float, val lat: Float)
    private data class Point3(val x: Float, val y: Float, val z: Float)
    private data class ScreenPoint(val x: Float, val y: Float, val depth: Float)

    private val continents = listOf(
        // North America, from Alaska across Canada to Central America
        listOf(Geo(-168f, 65f), Geo(-145f, 70f), Geo(-125f, 58f), Geo(-112f, 52f),
            Geo(-96f, 50f), Geo(-82f, 45f), Geo(-60f, 52f), Geo(-52f, 43f),
            Geo(-66f, 25f), Geo(-82f, 8f), Geo(-96f, 17f), Geo(-108f, 28f),
            Geo(-117f, 33f), Geo(-130f, 48f), Geo(-153f, 58f)),
        // South America
        listOf(Geo(-81f, 12f), Geo(-70f, 8f), Geo(-50f, 1f), Geo(-35f, -8f), Geo(-40f, -22f), Geo(-52f, -55f), Geo(-70f, -17f), Geo(-81f, 0f)),
        // Europe and Asia
        listOf(Geo(-10f, 36f), Geo(-10f, 58f), Geo(5f, 71f), Geo(32f, 70f),
            Geo(45f, 58f), Geo(65f, 60f), Geo(90f, 72f), Geo(125f, 58f),
            Geo(150f, 48f), Geo(138f, 35f), Geo(121f, 22f), Geo(105f, 8f),
            Geo(80f, 7f), Geo(65f, 25f), Geo(48f, 30f), Geo(35f, 42f),
            Geo(25f, 35f), Geo(12f, 42f), Geo(-10f, 36f)),
        // Africa
        listOf(Geo(-17f, 35f), Geo(5f, 37f), Geo(33f, 30f), Geo(51f, 12f),
            Geo(42f, -12f), Geo(30f, -34f), Geo(18f, -35f), Geo(5f, -25f),
            Geo(-4f, -5f), Geo(-17f, 14f)),
        // Australia
        listOf(Geo(113f, -11f), Geo(130f, -10f), Geo(153f, -25f),
            Geo(145f, -39f), Geo(124f, -35f), Geo(113f, -22f)),
        // Greenland
        listOf(Geo(-52f, 82f), Geo(-22f, 76f), Geo(-40f, 59f), Geo(-60f, 64f))
    )

    private var phase = 0f
    private var globeRotation = 0f
    @Volatile private var testing = false

    fun setTesting(active: Boolean) {
        testing = active
        postInvalidateOnAnimation()
    }

    init {
        contentDescription = "Animated glowing 3D Earth globe"
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun insideGeoPolygon(lon: Float, lat: Float, polygon: List<Geo>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val crosses = (a.lat > lat) != (b.lat > lat) &&
                lon < (b.lon - a.lon) * (lat - a.lat) / ((b.lat - a.lat).takeIf { abs(it) > 0.0001f } ?: 0.0001f) + a.lon
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun project(geo: Geo, cx: Float, cy: Float, r: Float): ScreenPoint {
        val lon = Math.toRadians((geo.lon + globeRotation).toDouble())
        val lat = Math.toRadians(geo.lat.toDouble())
        val cosLat = cos(lat)
        val x = (cosLat * sin(lon)).toFloat()
        val y = (-sin(lat)).toFloat()
        val z = (cosLat * cos(lon)).toFloat()
        return ScreenPoint(cx + x * r, cy + y * r, z)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width * 0.265f, height * 0.335f)
        if (r <= 0f) return

        glowPaint.shader = RadialGradient(cx, cy, r * 1.82f,
            intArrayOf(0x6633DFFF, 0x33245DFF, 0x142F7DFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.46f, 0.72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.82f, glowPaint)
        glowPaint.shader = null
        glowPaint.color = 0x5535DFFF
        glowPaint.setShadowLayer(dp(18f), 0f, 0f, cyan)
        canvas.drawCircle(cx, cy, r * 0.96f, glowPaint)
        glowPaint.clearShadowLayer()

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

        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r * 0.99f, Path.Direction.CW) })

        // Longitude and latitude are projected onto a sphere. The polar axis stays upright;
        // only longitude advances, so the Earth rotates horizontally like a real globe.
        for (latDeg in listOf(-60f, -30f, 0f, 30f, 60f)) {
            val lat = Math.toRadians(latDeg.toDouble())
            val y = cy - sin(lat).toFloat() * r
            val rx = cos(lat).toFloat() * r
            linePaint.color = if (latDeg == 0f) 0xAA53E9FF.toInt() else 0x5546CFFF
            linePaint.strokeWidth = dp(if (latDeg == 0f) 1.2f else 0.7f)
            canvas.drawOval(cx - rx, y - r * 0.10f, cx + rx, y + r * 0.10f, linePaint)
        }
        for (lonDeg in -150..180 step 30) {
            val lon = Math.toRadians((lonDeg + globeRotation).toDouble())
            val rx = abs(sin(lon)).toFloat() * r
            linePaint.color = if (lonDeg == 0) 0xAA46DFFF.toInt() else 0x5546CFFF
            linePaint.strokeWidth = dp(if (lonDeg == 0) 1.2f else 0.7f)
            canvas.drawOval(cx - rx, cy - r, cx + rx, cy + r, linePaint)
        }

        // Rasterize each geographic land polygon into small overlapping surface patches.
        // Unlike drawing a whole continent only when every corner is visible, this clips each
        // patch against the horizon independently, so land remains attached to the globe and
        // cannot blink out when a continent crosses the edge of the visible hemisphere.
        landPaint.setShadowLayer(dp(3f), 0f, 0f, cyan)
        coastPaint.setShadowLayer(dp(3f), 0f, 0f, cyan)
        for (continent in continents) {
            val minLon = continent.minOf { it.lon }
            val maxLon = continent.maxOf { it.lon }
            val minLat = continent.minOf { it.lat }
            val maxLat = continent.maxOf { it.lat }
            var lat = minLat
            while (lat <= maxLat) {
                var lon = minLon
                while (lon <= maxLon) {
                    if (insideGeoPolygon(lon, lat, continent)) {
                        val p = project(Geo(lon, lat), cx, cy, r)
                        if (p.depth > 0.015f) {
                            // Overlapping small patches make a continuous filled land mass.
                            canvas.drawCircle(p.x, p.y, r * 0.027f, landPaint)
                        }
                    }
                    lon += 2f
                }
                lat += 2f
            }

            // Densify coast edges so the visible part of a coast never vanishes as its
            // endpoints rotate behind the horizon.
            for (i in continent.indices) {
                val a = continent[i]
                val b = continent[(i + 1) % continent.size]
                val steps = (maxOf(abs(b.lon - a.lon), abs(b.lat - a.lat)) / 2f).toInt().coerceAtLeast(1)
                var previous: ScreenPoint? = null
                for (step in 0..steps) {
                    val t = step.toFloat() / steps
                    val p = project(Geo(a.lon + (b.lon - a.lon) * t, a.lat + (b.lat - a.lat) * t), cx, cy, r)
                    val prev = previous
                    if (prev != null && prev.depth > 0.015f && p.depth > 0.015f) {
                        canvas.drawLine(prev.x, prev.y, p.x, p.y, coastPaint)
                    }
                    previous = p
                }
            }
        }
        landPaint.clearShadowLayer()
        coastPaint.clearShadowLayer()

        // Network hubs are also geographic points, so they rotate with the surface.
        val cities = listOf(
            Geo(-74f, 40f), Geo(-118f, 34f), Geo(-99f, 19f), Geo(-46f, -23f),
            Geo(-3f, 52f), Geo(2f, 48f), Geo(31f, 30f), Geo(77f, 28f),
            Geo(116f, 40f), Geo(121f, 14f), Geo(151f, -34f)
        )
        val cityPoints = cities.map { project(it, cx, cy, r) }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.9f)
            color = 0xBB4FEAFF.toInt()
            setShadowLayer(dp(3f), 0f, 0f, cyan)
        }
        for (i in 0 until cityPoints.size - 1) {
            val a = cityPoints[i]
            val b = cityPoints[i + 1]
            if (a.depth > 0.05f && b.depth > 0.05f) {
                val path = Path()
                path.moveTo(a.x, a.y)
                path.quadTo((a.x + b.x) / 2f, (a.y + b.y) / 2f - r * 0.12f, b.x, b.y)
                canvas.drawPath(path, routePaint)
            }
        }
        routePaint.clearShadowLayer()
        cityPoints.forEachIndexed { index, p ->
            if (p.depth > 0.04f) {
                val pulse = 1.4f + (1f + sin(phase * 2.2f + index)) * 1.1f
                glowPaint.color = cyan
                glowPaint.setShadowLayer(dp(6f), 0f, 0f, cyan)
                canvas.drawCircle(p.x, p.y, dp(pulse + 1f), glowPaint)
                glowPaint.clearShadowLayer()
                dotPaint.color = Color.WHITE
                canvas.drawCircle(p.x, p.y, dp(pulse * 0.55f), dotPaint)
            }
        }

        // A luminous scan band follows the curved latitude of the sphere.
        val scanLat = -1f + ((phase * 0.72f) % 2f)
        val scanY = cy + scanLat * r
        val scanWidth = r * sqrt((1f - scanLat * scanLat).coerceAtLeast(0f))
        linePaint.color = 0x554FEAFF
        linePaint.strokeWidth = dp(2f)
        linePaint.setShadowLayer(dp(8f), 0f, 0f, cyan)
        canvas.drawLine(cx - scanWidth, scanY, cx + scanWidth, scanY, linePaint)
        linePaint.clearShadowLayer()
        canvas.restore()

        // Orbital rings and satellites remain independent of the Earth's polar rotation.
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

        phase = (phase + if (testing) 0.075f else 0.025f) % (2f * PI.toFloat())
        globeRotation = (globeRotation + if (testing) 1.05f else 0.35f) % 360f
        postInvalidateDelayed(if (testing) 16L else 32L)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
