package com.igor.geopdfgps

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.app.Activity

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openApp = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(0, 27, 22)
        window.navigationBarColor = Color.BLACK
        val splash = ImageView(this).apply {
            setImageResource(R.drawable.splash_geotrack)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(0, 27, 22))
        }
        setContentView(splash)
        handler.postDelayed(openApp, 1800L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openApp)
        super.onDestroy()
    }
}
