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
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(splash, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Cobre a barra estática da arte e cria uma barra realmente animada.
        val track = View(this).apply {
            background = rounded(Color.rgb(67, 86, 82), 8f)
        }
        val trackParams = FrameLayout.LayoutParams(dp(320), dp(7)).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (resources.displayMetrics.heightPixels * 0.646f).toInt()
        }
        root.addView(track, trackParams)

        val fill = View(this).apply {
            background = rounded(Color.rgb(0, 226, 139), 8f)
            pivotX = 0f
            scaleX = 0f
        }
        val fillParams = FrameLayout.LayoutParams(dp(320), dp(7)).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (resources.displayMetrics.heightPixels * 0.646f).toInt()
        }
        root.addView(fill, fillParams)

        setContentView(root)

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
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
