package com.igor.geopdfgps

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

class SplashActivity : Activity() {
    private var animator: ValueAnimator? = null
    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(0, 20, 16)
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(0, 20, 16))
        }

        val splash = ImageView(this).apply {
            setImageResource(R.drawable.splash_geotrack)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        root.addView(splash, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Uma única barra real, alinhada exatamente sobre a barra desenhada na arte.
        val track = View(this).apply {
            background = rounded(Color.rgb(67, 86, 82), 8f)
        }
        val fill = View(this).apply {
            background = rounded(Color.rgb(0, 226, 139), 8f)
            pivotX = 0f
            scaleX = 0f
        }
        root.addView(track, FrameLayout.LayoutParams(1, 1))
        root.addView(fill, FrameLayout.LayoutParams(1, 1))

        setContentView(root)

        root.post {
            // Proporções medidas na arte aprovada: barra curta centralizada.
            val barWidth = (root.width * 0.378f).toInt()
            val barHeight = (root.height * 0.008f).toInt().coerceAtLeast(dp(5))
            val left = (root.width - barWidth) / 2
            val top = (root.height * 0.6075f).toInt()

            track.layoutParams = FrameLayout.LayoutParams(barWidth, barHeight).apply {
                leftMargin = left
                topMargin = top
            }
            fill.layoutParams = FrameLayout.LayoutParams(barWidth, barHeight).apply {
                leftMargin = left
                topMargin = top
            }
            fill.pivotX = 0f

            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3400L
                interpolator = LinearInterpolator()
                addUpdateListener { fill.scaleX = it.animatedValue as Float }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        openMain()
                    }
                })
                start()
            }
        }
    }

    private fun openMain() {
        if (opened || isFinishing || isDestroyed) return
        opened = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        super.onDestroy()
    }
}
