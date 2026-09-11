package com.igor.geopdfgps

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class MapView(context: Context) : View(context) {
    var bitmap: Bitmap? = null; set(v) { field=v; reset(); invalidate() }
    var gpsNormalized: Pair<Double, Double>? = null; set(v) { field=v; invalidate() }
    var accuracyMeters: Float? = null
    private var scale=1f; private var tx=0f; private var ty=0f
    private var lastX=0f; private var lastY=0f
    private val detector = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d: ScaleGestureDetector): Boolean { scale=(scale*d.scaleFactor).coerceIn(1f,12f); invalidate(); return true }
    })
    fun reset(){ scale=1f; tx=0f; ty=0f }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        if(e.pointerCount==1 && !detector.isInProgress){ when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y}
            MotionEvent.ACTION_MOVE->{tx+=e.x-lastX;ty+=e.y-lastY;lastX=e.x;lastY=e.y;invalidate()}
        }}
        return true
    }
    override fun onDraw(c: Canvas){
        super.onDraw(c); val b=bitmap?:return
        val fit=min(width.toFloat()/b.width,height.toFloat()/b.height)
        val dw=b.width*fit; val dh=b.height*fit
        val ox=(width-dw)/2f; val oy=(height-dh)/2f
        c.save(); c.translate(width/2f+tx,height/2f+ty); c.scale(scale,scale); c.translate(-width/2f,-height/2f)
        c.drawBitmap(b,null,RectF(ox,oy,ox+dw,oy+dh),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        gpsNormalized?.let { p ->
            val x=ox+(p.first*dw).toFloat(); val y=oy+((1.0-p.second)*dh).toFloat()
            val ring=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL;color=Color.argb(55,33,150,243)}
            c.drawCircle(x,y,28f/scale,ring)
            val border=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL;color=Color.WHITE}
            c.drawCircle(x,y,11f/scale,border)
            val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL;color=Color.rgb(25,118,210)}
            c.drawCircle(x,y,7f/scale,dot)
        }
        c.restore()
    }
}
