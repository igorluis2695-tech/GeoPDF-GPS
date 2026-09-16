package com.igor.geopdfgps

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

class MapView(context: Context) : View(context) {
    var bitmap: Bitmap? = null
        set(v) { field = v; reset(); invalidate() }
    var gpsNormalized: Pair<Double, Double>? = null
        set(v) { field = v; invalidate() }
    var accuracyMeters: Float? = null
    var bearingDegrees: Float? = null
        set(v) { field = v; invalidate() }
    private val trail = mutableListOf<Pair<Double, Double>>()

    fun clearTrail() { trail.clear(); invalidate() }
    fun hasTrail(): Boolean = trail.isNotEmpty()
    fun addTrailPoint(p: Pair<Double, Double>) {
        if (p.first in -0.05..1.05 && p.second in -0.05..1.05) {
            trail.add(p)
            if (trail.size > 10000) trail.removeAt(0)
            invalidate()
        }
    }

    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            scale = (scale * d.scaleFactor).coerceIn(1f, 15f)
            invalidate()
            return true
        }
    })

    fun reset() { scale = 1f; tx = 0f; ty = 0f; invalidate() }
    fun zoomIn() { scale = (scale * 1.45f).coerceAtMost(15f); invalidate() }
    fun zoomOut() { scale = (scale / 1.45f).coerceAtLeast(1f); invalidate() }

    fun centerOnGps() {
        val b = bitmap ?: return
        val p = gpsNormalized ?: return
        if (width <= 0 || height <= 0) return
        if (scale < 3f) scale = 3f
        val fit = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val dw = b.width * fit
        val dh = b.height * fit
        val ox = (width - dw) / 2f
        val oy = (height - dh) / 2f
        val x = ox + (p.first * dw).toFloat()
        val y = oy + ((1.0 - p.second) * dh).toFloat()
        tx = -scale * (x - width / 2f)
        ty = -scale * (y - height / 2f)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        if (e.pointerCount == 1 && !detector.isInProgress) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y }
                MotionEvent.ACTION_MOVE -> {
                    tx += e.x - lastX
                    ty += e.y - lastY
                    lastX = e.x
                    lastY = e.y
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val b = bitmap ?: return
        val fit = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val dw = b.width * fit
        val dh = b.height * fit
        val ox = (width - dw) / 2f
        val oy = (height - dh) / 2f

        c.save()
        c.translate(width / 2f + tx, height / 2f + ty)
        c.scale(scale, scale)
        c.translate(-width / 2f, -height / 2f)
        c.drawBitmap(b, null, RectF(ox, oy, ox + dw, oy + dh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        // Rastro da sessão atual.
        if (trail.size > 1) {
            val path = Path()
            trail.forEachIndexed { index, p ->
                val x = ox + (p.first * dw).toFloat()
                val y = oy + ((1.0 - p.second) * dh).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f / scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.rgb(20, 196, 92)
            }
            c.drawPath(path, trailPaint)
        }

        gpsNormalized?.let { p ->
            val x = ox + (p.first * dw).toFloat()
            val y = oy + ((1.0 - p.second) * dh).toFloat()
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(45, 33, 150, 243) }
            c.drawCircle(x, y, 34f / scale, halo)

            // Seta aponta para o rumo informado pelo GPS. Se ainda não houver rumo, mantém o ponto azul.
            val bearing = bearingDegrees
            if (bearing != null) {
                c.save()
                c.rotate(bearing, x, y)
                val arrow = Path().apply {
                    moveTo(x, y - 20f / scale)
                    lineTo(x - 12f / scale, y + 14f / scale)
                    lineTo(x, y + 9f / scale)
                    lineTo(x + 12f / scale, y + 14f / scale)
                    close()
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f / scale; strokeJoin = Paint.Join.ROUND; color = Color.WHITE }
                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(25, 118, 210) }
                c.drawPath(arrow, borderPaint)
                c.drawPath(arrow, arrowPaint)
                c.restore()
            } else {
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
                c.drawCircle(x, y, 11f / scale, border)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(25, 118, 210) }
                c.drawCircle(x, y, 7f / scale, dot)
            }
        }
        c.restore()
    }
}
