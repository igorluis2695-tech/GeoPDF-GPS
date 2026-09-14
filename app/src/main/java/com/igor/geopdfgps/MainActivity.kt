package com.igor.geopdfgps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var root: FrameLayout
    private lateinit var map: MapView
    private lateinit var status: TextView
    private lateinit var mapTitle: TextView
    private var geo: GeoReference? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentMap: File? = null
    private val mapsDir by lazy { File(filesDir, "maps").apply { mkdirs() } }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importMap(uri)
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startGps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeoPdfParser.init(this)
        root = FrameLayout(this)
        setContentView(root)
        showLibrary()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startGps()
        } else {
            permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun showLibrary() {
        currentMap = null
        root.removeAllViews()
        root.setBackgroundColor(Color.rgb(245, 247, 245))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(12))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "GeoPDF Maps"
            textSize = 27f
            setTextColor(Color.rgb(24, 54, 31))
            setTypeface(typeface, Typeface.BOLD)
        })
        titles.addView(TextView(this).apply {
            text = "Seus mapas disponíveis offline"
            textSize = 14f
            setTextColor(Color.rgb(95, 108, 98))
        })
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(actionButton("+  ADICIONAR MAPA") { picker.launch(arrayOf("application/pdf")) })
        page.addView(header)

        val files = mapsDir.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()

        if (files.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(70), dp(24), dp(40))
            }
            empty.addView(TextView(this).apply {
                text = "🗺️"
                textSize = 52f
                gravity = Gravity.CENTER
            })
            empty.addView(TextView(this).apply {
                text = "Nenhum mapa salvo"
                textSize = 20f
                setTextColor(Color.rgb(35, 55, 40))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(5))
            })
            empty.addView(TextView(this).apply {
                text = "Toque em “Adicionar mapa” para importar seus GeoPDFs.\nEles ficarão guardados aqui para abrir quando precisar."
                textSize = 14f
                setTextColor(Color.rgb(100, 112, 103))
                gravity = Gravity.CENTER
            })
            page.addView(empty, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            page.addView(TextView(this).apply {
                text = "MEUS MAPAS  •  ${files.size}"
                textSize = 12f
                setTextColor(Color.rgb(91, 110, 95))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(4), dp(10), 0, dp(10))
            })

            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(20))
            }
            files.forEach { list.addView(mapCard(it)) }
            scroll.addView(list)
            page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun mapCard(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(10), dp(14))
            background = rounded(Color.WHITE, 18f, Color.rgb(222, 228, 223), 1)
            isClickable = true
            setOnClickListener { openSavedMap(file) }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, dp(10))
        card.layoutParams = lp

        card.addView(TextView(this).apply {
            text = "🗺"
            textSize = 31f
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(232, 244, 234), 14f)
        }, LinearLayout.LayoutParams(dp(58), dp(58)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(8), 0)
        }
        info.addView(TextView(this).apply {
            text = file.nameWithoutExtension
            maxLines = 2
            textSize = 16f
            setTextColor(Color.rgb(30, 48, 34))
            setTypeface(typeface, Typeface.BOLD)
        })
        info.addView(TextView(this).apply {
            text = "GeoPDF • ${(file.length() / 1024.0 / 1024.0).let { String.format(Locale.US, "%.1f MB", it) }}"
            textSize = 12f
            setTextColor(Color.rgb(110, 122, 112))
            setPadding(0, dp(4), 0, 0)
        })
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val menu = TextView(this).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(65, 82, 69))
            setOnClickListener {
                PopupMenu(this@MainActivity, this).apply {
                    menu.add("Abrir")
                    menu.add("Excluir mapa")
                    setOnMenuItemClickListener { item ->
                        when (item.title.toString()) {
                            "Abrir" -> openSavedMap(file)
                            "Excluir mapa" -> confirmDelete(file)
                        }
                        true
                    }
                    show()
                }
            }
        }
        card.addView(menu, LinearLayout.LayoutParams(dp(44), dp(54)))
        return card
    }

    private fun importMap(uri: Uri) {
        val original = queryName(uri)?.ifBlank { "Mapa.pdf" } ?: "Mapa.pdf"
        val safeName = original.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var dest = File(mapsDir, safeName)
        var n = 2
        while (dest.exists()) {
            val base = dest.nameWithoutExtension.substringBeforeLast(" (")
            dest = File(mapsDir, "$base ($n).pdf")
            n++
        }
        try {
            contentResolver.openInputStream(uri)!!.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Toast.makeText(this, "Mapa salvo no aplicativo", Toast.LENGTH_SHORT).show()
            showLibrary()
        } catch (e: Exception) {
            dest.delete()
            Toast.makeText(this, "Não foi possível importar este PDF", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun confirmDelete(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Excluir mapa?")
            .setMessage(file.nameWithoutExtension)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ -> file.delete(); showLibrary() }
            .show()
    }

    private fun openSavedMap(file: File) {
        currentMap = file
        showMapScreen(file.nameWithoutExtension)
        openPdf(Uri.fromFile(file))
    }

    private fun showMapScreen(title: String) {
        root.removeAllViews()
        root.setBackgroundColor(Color.rgb(18, 20, 18))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(25, 55, 32))
        }
        bar.addView(TextView(this).apply {
            text = "‹"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { showLibrary() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))

        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mapTitle = TextView(this).apply {
            text = title
            textSize = 17f
            maxLines = 1
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        status = TextView(this).apply {
            text = "Lendo mapa..."
            textSize = 12f
            setTextColor(Color.rgb(194, 220, 199))
            maxLines = 1
        }
        t.addView(mapTitle); t.addView(status)
        bar.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(bar)

        val mapFrame = FrameLayout(this)
        map = MapView(this)
        mapFrame.addView(map, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        controls.addView(roundMapButton("+") { map.zoomIn() })
        controls.addView(roundMapButton("−") { map.zoomOut() })
        controls.addView(roundMapButton("◎") { map.centerOnGps() })
        val clp = FrameLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
        clp.setMargins(0, 0, dp(14), 0)
        mapFrame.addView(controls, clp)

        column.addView(mapFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(25, 29, 26))
        }
        bottom.addView(TextView(this).apply {
            text = "Arraste para mover • Pinça para zoom"
            textSize = 12f
            setTextColor(Color.rgb(188, 196, 190))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(actionButton("CENTRALIZAR") { map.centerOnGps() })
        column.addView(bottom)

        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun openPdf(uri: Uri) {
        status.text = "Lendo georreferência..."
        Thread {
            val parsed = runCatching { GeoPdfParser.parse(this, uri) }.getOrNull()
            runOnUiThread {
                geo = parsed
                if (parsed == null) {
                    status.text = "GeoPDF não reconhecido"
                    Toast.makeText(this, "Este PDF não usa o padrão GPTS/LPTS suportado.", Toast.LENGTH_LONG).show()
                } else {
                    status.text = "Mapa pronto • aguardando GPS"
                }
                render(uri)
            }
        }.start()
    }

    private fun render(uri: Uri) {
        renderer?.close(); pfd?.close()
        pfd = if (uri.scheme == "file") {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            contentResolver.openFileDescriptor(uri, "r")
        } ?: return
        renderer = PdfRenderer(pfd!!)
        val page = renderer!!.openPage(0)
        val maxDim = 2400f
        val factor = minOf(maxDim / page.width, maxDim / page.height, 3f)
        val bmp = Bitmap.createBitmap(
            (page.width * factor).toInt().coerceAtLeast(1),
            (page.height * factor).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        map.bitmap = bmp
    }

    private fun startGps() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (::status.isInitialized) status.text = "Ative o GPS do celular"
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 0f, this)
            }
        } catch (_: SecurityException) { }
    }

    override fun onLocationChanged(loc: Location) {
        if (!::map.isInitialized || currentMap == null) return
        val p = geo?.geoToPage(loc.latitude, loc.longitude)
        map.gpsNormalized = p
        map.accuracyMeters = loc.accuracy
        if (::status.isInitialized) {
            status.text = String.format(Locale.US, "GPS %.0f m • %.6f, %.6f", loc.accuracy, loc.latitude, loc.longitude)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentMap != null) showLibrary() else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.close(); pfd?.close()
        (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this)
    }

    private fun actionButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(14), dp(11), dp(14), dp(11))
        background = rounded(Color.rgb(42, 112, 58), 12f)
        setOnClickListener { click() }
    }

    private fun roundMapButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 25f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(30, 60, 36))
        background = rounded(Color.argb(238, 255, 255, 255), 15f)
        setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(dp(48), dp(48))
        lp.setMargins(0, dp(4), 0, dp(4))
        layoutParams = lp
        elevation = dp(5).toFloat()
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null, strokeDp: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (stroke != null) setStroke(dp(strokeDp), stroke)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
