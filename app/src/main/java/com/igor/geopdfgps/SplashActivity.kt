package com.igor.geopdfgps

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageView

class SplashActivity : Activity() {
    private var opened = false
    private val handler = Handler(Looper.getMainLooper())

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
        root.addView(
            splash,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
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
