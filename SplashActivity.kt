package com.igor.geopdfgps

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

class SplashActivity : Activity() {
    private var opened = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(0, 20, 16)
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(0, 20, 16)) }
        val splash = ImageView(this).apply {
            setImageResource(R.drawable.splash_geotrack)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))

        val progress = SplashProgressView(this)
        root.addView(progress, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        progress.start(3400L)
        handler.postDelayed({ openMain() }, 3500L)
    }

    private fun openMain() {
        if (opened || isFinishing || isDestroyed) return
        opened = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

private class SplashProgressView(context: android.content.Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 150, 180, 175) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 238, 180) }
    private var fraction = 0f

    fun start(duration: Long) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = width * 0.305f
        val right = width * 0.695f
        val centerY = height * 0.616f
        val h = resources.displayMetrics.density * 8f
        val radius = h / 2f
        canvas.drawRoundRect(left, centerY - h/2, right, centerY + h/2, radius, radius, track)
        if (fraction > 0f) {
            val end = left + (right-left) * fraction
            canvas.drawRoundRect(left, centerY - h/2, end, centerY + h/2, radius, radius, fill)
        }
    }
}
