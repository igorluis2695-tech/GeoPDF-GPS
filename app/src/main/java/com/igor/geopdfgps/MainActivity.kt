package com.igor.geopdfgps

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var map: MapView
    private lateinit var status: TextView
    private var geo: GeoReference?=null
    private var pfd: ParcelFileDescriptor?=null
    private var renderer: PdfRenderer?=null
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){ uri -> if(uri!=null) openPdf(uri) }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ if(it) startGps() }

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState); GeoPdfParser.init(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(12,12,12,12)}
        val open=Button(this).apply{text="ABRIR GEOPDF";setOnClickListener{picker.launch(arrayOf("application/pdf"))}}
        val center=Button(this).apply{text="CENTRALIZAR";setOnClickListener{map.reset();map.invalidate()}}
        status=TextView(this).apply{text="Abra um PDF georreferenciado";setPadding(12,0,0,0)}
        bar.addView(open);bar.addView(center);bar.addView(status,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        map=MapView(this)
        root.addView(bar);root.addView(map,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));setContentView(root)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startGps() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun openPdf(uri: Uri){
        status.text="Lendo georreferência..."
        Thread{
            val parsed=runCatching{GeoPdfParser.parse(this,uri)}.getOrNull()
            runOnUiThread{
                geo=parsed
                if(parsed==null){status.text="GeoPDF não reconhecido (precisa GPTS/LPTS)"; Toast.makeText(this,"Este PDF não usa o padrão GPTS/LPTS suportado nesta versão.",Toast.LENGTH_LONG).show()}
                else status.text="GeoPDF carregado • aguardando GPS"
                render(uri)
            }
        }.start()
    }

    private fun render(uri: Uri){
        renderer?.close();pfd?.close()
        pfd=contentResolver.openFileDescriptor(uri,"r") ?: return
        renderer=PdfRenderer(pfd!!)
        val page=renderer!!.openPage(0)
        val maxDim=2200f; val factor=minOf(maxDim/page.width,maxDim/page.height,3f)
        val bmp=Bitmap.createBitmap((page.width*factor).toInt().coerceAtLeast(1),(page.height*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close();map.bitmap=bmp
    }

    private fun startGps(){
        val lm=getSystemService(LOCATION_SERVICE) as LocationManager
        try{
            if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){status.text="Ative o GPS do celular"; startActivity(android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return}
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,this)
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1500L,0f,this)
        }catch(_:SecurityException){}
    }

    override fun onLocationChanged(loc: Location){
        val p=geo?.geoToPage(loc.latitude,loc.longitude)
        map.gpsNormalized=p;map.accuracyMeters=loc.accuracy
        status.text=String.format(Locale.US,"GPS %.1f m • %.6f, %.6f",loc.accuracy,loc.latitude,loc.longitude)
    }
    override fun onDestroy(){super.onDestroy();renderer?.close();pfd?.close();(getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this)}
}
