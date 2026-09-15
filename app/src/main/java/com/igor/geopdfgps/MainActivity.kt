package com.igor.geopdfgps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    private lateinit var followButton: TextView
    private var followLocation = false
    private var geo: GeoReference? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentMap: File? = null
    private var currentFolder: File? = null
    private val mapsDir by lazy { File(filesDir, "maps").apply { mkdirs() } }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importMaps(uris, currentFolder ?: mapsDir)
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
        currentFolder = null
        root.removeAllViews()
        root.setBackgroundColor(Color.rgb(12, 18, 15))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        page.addView(libraryHeader("Meus Mapas", "Organize seus GeoPDFs em pastas", true))

        val folders = mapsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
        val looseMaps = mapsDir.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(20)) }

        if (folders.isEmpty() && looseMaps.isEmpty()) {
            list.addView(emptyLibraryView("Nenhuma pasta criada", "Crie uma pasta e adicione vários GeoPDFs de uma vez."))
        } else {
            if (folders.isNotEmpty()) list.addView(sectionLabel("PASTAS  •  ${folders.size}"))
            folders.forEach { list.addView(folderCard(it)) }
            if (looseMaps.isNotEmpty()) {
                list.addView(sectionLabel("MAPAS SEM PASTA  •  ${looseMaps.size}"))
                looseMaps.forEach { list.addView(mapCard(it)) }
            }
        }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun libraryHeader(title: String, subtitle: String, rootScreen: Boolean): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(10)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (!rootScreen) {
            header.addView(TextView(this).apply {
                text = "‹"; textSize = 38f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { showLibrary() }
            }, LinearLayout.LayoutParams(dp(44), dp(52)))
        } else {
            header.addView(ImageView(this).apply {
                setImageResource(com.igor.geopdfgps.R.drawable.ic_geotrack)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(58), dp(58)))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10),0,dp(8),0) }
        titles.addView(TextView(this).apply {
            text = if (rootScreen) "GeoTrack" else title; textSize = 25f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); maxLines = 1
        })
        titles.addView(TextView(this).apply {
            text = if (rootScreen) "SEUS MAPAS, SEMPRE COM VOCÊ." else subtitle; textSize = if(rootScreen) 10f else 13f; letterSpacing = if(rootScreen) .16f else 0f; setTextColor(Color.rgb(166,181,171))
        })
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (rootScreen) header.addView(actionButton("+  NOVA PASTA") { createFolderDialog() })
        else header.addView(actionButton("+  MAPAS") { picker.launch(arrayOf("application/pdf")) })
        wrap.addView(header)
        if (rootScreen) wrap.addView(TextView(this).apply {
            text = "Meus Mapas"; textSize = 18f; setTypeface(typeface,Typeface.BOLD); setTextColor(Color.WHITE); setPadding(dp(4),dp(20),0,dp(4))
        })
        return wrap
    }

    private fun sectionLabel(label: String) = TextView(this).apply {
        text = label; textSize = 12f; setTextColor(Color.rgb(145, 163, 151)); setTypeface(typeface, Typeface.BOLD); setPadding(dp(4), dp(10), 0, dp(10))
    }

    private fun emptyLibraryView(title: String, message: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(70), dp(24), dp(40))
        addView(TextView(this@MainActivity).apply { text = "📁"; textSize = 52f; gravity = Gravity.CENTER })
        addView(TextView(this@MainActivity).apply { text = title; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0,dp(10),0,dp(5)) })
        addView(TextView(this@MainActivity).apply { text = message; textSize = 14f; setTextColor(Color.rgb(150,165,155)); gravity = Gravity.CENTER })
    }

    private fun folderCard(folder: File): View {
        val count = folder.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }?.size ?: 0
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(14), dp(10), dp(14)); background = rounded(Color.rgb(20,29,24),18f,Color.rgb(42,58,48),1)
            isClickable = true; setOnClickListener { showFolder(folder) }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,0,dp(10)); card.layoutParams = lp
        card.addView(TextView(this).apply { text = "📁"; textSize = 31f; gravity = Gravity.CENTER; background = rounded(Color.rgb(20,92,55),14f) }, LinearLayout.LayoutParams(dp(58),dp(58)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13),0,dp(8),0) }
        info.addView(TextView(this).apply { text = folder.name; textSize = 16f; setTextColor(Color.WHITE); setTypeface(typeface,Typeface.BOLD); maxLines = 2 })
        info.addView(TextView(this).apply { text = if (count == 1) "1 mapa" else "$count mapas"; textSize = 12f; setTextColor(Color.rgb(158,173,163)); setPadding(0,dp(4),0,0) })
        card.addView(info, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        card.addView(TextView(this).apply {
            text="⋮"; textSize=28f; gravity=Gravity.CENTER; setTextColor(Color.rgb(205,215,208)); setOnClickListener {
                PopupMenu(this@MainActivity,this).apply {
                    menu.add("Abrir pasta"); menu.add("Renomear pasta"); menu.add("Excluir pasta")
                    setOnMenuItemClickListener { item -> when(item.title.toString()) { "Abrir pasta" -> showFolder(folder); "Renomear pasta" -> renameFolderDialog(folder); "Excluir pasta" -> confirmDeleteFolder(folder) }; true }; show()
                }
            }
        }, LinearLayout.LayoutParams(dp(44),dp(54)))
        return card
    }

    private fun showFolder(folder: File) {
        currentMap = null; currentFolder = folder; root.removeAllViews(); root.setBackgroundColor(Color.rgb(12,18,15))
        val page = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)) }
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("pdf",true) }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
        page.addView(libraryHeader(folder.name, if(files.size==1) "1 mapa" else "${files.size} mapas", false))
        val scroll=ScrollView(this); val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(8),0,dp(20)) }
        if(files.isEmpty()) list.addView(emptyLibraryView("Pasta vazia", "Toque em “+ Mapas” para selecionar vários GeoPDFs.")) else files.forEach { list.addView(mapCard(it)) }
        scroll.addView(list); page.addView(scroll,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f)); root.addView(page,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun createFolderDialog() {
        val input=EditText(this).apply { hint="Ex.: Fazendas Lambari"; setSingleLine(true) }
        val box=FrameLayout(this).apply { setPadding(dp(20),0,dp(20),0); addView(input,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT)) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Nova pasta").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Criar") { _,_ ->
            val name=safeFolderName(input.text.toString()); if(name.isBlank()) Toast.makeText(this,"Digite um nome para a pasta",Toast.LENGTH_SHORT).show() else { val f=File(mapsDir,name); if(f.exists()) Toast.makeText(this,"Já existe uma pasta com esse nome",Toast.LENGTH_SHORT).show() else { f.mkdirs(); showLibrary() } }
        }.show()
    }

    private fun renameFolderDialog(folder: File) {
        val input=EditText(this).apply { setText(folder.name); setSelection(text.length); setSingleLine(true) }
        val box=FrameLayout(this).apply { setPadding(dp(20),0,dp(20),0); addView(input,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT)) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Renomear pasta").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Salvar") { _,_ ->
            val name=safeFolderName(input.text.toString()); val dest=File(mapsDir,name); if(name.isBlank() || (dest.exists() && dest != folder)) Toast.makeText(this,"Nome inválido ou já existente",Toast.LENGTH_SHORT).show() else { folder.renameTo(dest); showLibrary() }
        }.show()
    }

    private fun confirmDeleteFolder(folder: File) {
        val count=folder.listFiles { f -> f.isFile && f.extension.equals("pdf",true) }?.size ?: 0
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Excluir pasta?").setMessage(if(count>0) "${folder.name} contém $count mapas. Todos serão excluídos do app." else folder.name).setNegativeButton("Cancelar",null).setPositiveButton("Excluir") { _,_ -> folder.deleteRecursively(); showLibrary() }.show()
    }

    private fun safeFolderName(name:String)=name.trim().replace(Regex("[\\/:*?\"<>|]"),"_")

    private fun mapCard(file: File): View {
        // Lista simples, no mesmo fundo escuro da tela: sem cartão branco.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(12))
            isClickable = true
            setOnClickListener { openSavedMap(file) }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.layoutParams = lp

        // Miniatura real da primeira página do GeoPDF, como na referência.
        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.WHITE, 9f)
            clipToOutline = true
        }
        row.addView(thumb, LinearLayout.LayoutParams(dp(58), dp(70)))
        loadPdfThumbnail(file, thumb)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        info.addView(TextView(this).apply {
            // O nome do próprio PDF é o nome da fazenda/mapa.
            text = file.nameWithoutExtension
            maxLines = 2
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.NORMAL)
        })
        info.addView(TextView(this).apply {
            text = (file.length() / 1024.0 / 1024.0).let { String.format(Locale.US, "%.1f MB", it) }
            textSize = 13f
            setTextColor(Color.rgb(158, 173, 163))
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(205, 215, 208))
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
        }, LinearLayout.LayoutParams(dp(44), dp(54)))

        // Linha divisória discreta, como no exemplo enviado.
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(43, 50, 46)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        }
        return wrap
    }


    private fun loadPdfThumbnail(file: File, imageView: ImageView) {
        val cacheDir = File(filesDir, "thumbs").apply { mkdirs() }
        val key = (file.absolutePath + "_" + file.lastModified() + "_" + file.length()).hashCode().toUInt().toString(16)
        val cached = File(cacheDir, "$key.png")

        if (cached.exists()) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { imageView.setImageBitmap(it); return }
        }

        imageView.setImageResource(R.drawable.ic_geotrack)
        Thread {
            var localPfd: ParcelFileDescriptor? = null
            var localRenderer: PdfRenderer? = null
            try {
                localPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                localRenderer = PdfRenderer(localPfd)
                if (localRenderer.pageCount > 0) {
                    val page = localRenderer.openPage(0)
                    val targetW = 180
                    val targetH = ((page.height.toFloat() / page.width.toFloat()) * targetW).toInt().coerceIn(180, 260)
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    runCatching { cached.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 88, it) } }
                    runOnUiThread {
                        if (imageView.isAttachedToWindow) imageView.setImageBitmap(bmp)
                    }
                }
            } catch (_: Exception) {
                // Mantém o logo como fallback caso algum PDF não gere miniatura.
            } finally {
                runCatching { localRenderer?.close() }
                runCatching { localPfd?.close() }
            }
        }.start()
    }

    private fun importMaps(uris: List<Uri>, destination: File) {
        val progress=android.app.ProgressDialog(this).apply { setTitle("Importando mapas"); setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL); max=uris.size; progress=0; setCancelable(false); setMessage("Preparando..."); show() }
        Thread { var imported=0; var failed=0; uris.forEachIndexed { index,uri -> val name=queryName(uri)?.ifBlank { "Mapa.pdf" } ?: "Mapa.pdf"; runOnUiThread { progress.progress=index; progress.setMessage("${index+1} de ${uris.size}: $name") }; if(copyMapToLibrary(uri,destination)) imported++ else failed++ }
            runOnUiThread { progress.progress=uris.size; progress.dismiss(); if(destination==mapsDir) showLibrary() else showFolder(destination); val msg=when { failed==0 -> "$imported mapas adicionados"; imported==0 -> "Não foi possível importar os mapas"; else -> "$imported mapas adicionados • $failed com erro" }; Toast.makeText(this,msg,Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun copyMapToLibrary(uri: Uri, destination: File): Boolean {
        destination.mkdirs(); val original=queryName(uri)?.ifBlank { "Mapa.pdf" } ?: "Mapa.pdf"; val safeName=original.replace(Regex("[\\/:*?\"<>|]"),"_"); var dest=File(destination,safeName); var n=2
        while(dest.exists()) { val base=dest.nameWithoutExtension.substringBeforeLast(" ("); dest=File(destination,"$base ($n).pdf"); n++ }
        return try { contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }; true } catch(_:Exception) { dest.delete(); false }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) return c.getString(0) }; return null
    }

    private fun confirmDelete(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Excluir mapa?").setMessage(file.nameWithoutExtension).setNegativeButton("Cancelar",null).setPositiveButton("Excluir") { _,_ -> file.delete(); file.parentFile?.let { if(it != mapsDir && it.exists()) showFolder(it) else showLibrary() } ?: showLibrary() }.show()
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
            setBackgroundColor(Color.rgb(12, 20, 16))
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
            setTextColor(Color.rgb(104, 218, 143))
            maxLines = 1
        }
        t.addView(mapTitle); t.addView(status)
        bar.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(bar)

        val mapFrame = FrameLayout(this)
        map = MapView(this)
        followLocation = false
        mapFrame.addView(map, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        controls.addView(roundMapButton("+") { map.zoomIn() })
        controls.addView(roundMapButton("−") { map.zoomOut() })
        followButton = roundMapButton("◎") {
            followLocation = !followLocation
            updateFollowButton()
            if (followLocation) {
                map.centerOnGps()
                Toast.makeText(this, "Acompanhamento ativado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Acompanhamento desativado", Toast.LENGTH_SHORT).show()
            }
        }
        controls.addView(followButton)
        updateFollowButton()
        val clp = FrameLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
        clp.setMargins(0, 0, dp(14), 0)
        mapFrame.addView(controls, clp)

        column.addView(mapFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(12, 18, 15))
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
        if (followLocation && p != null) map.centerOnGps()
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


    private fun updateFollowButton() {
        if (!::followButton.isInitialized) return
        if (followLocation) {
            followButton.setTextColor(Color.WHITE)
            followButton.background = rounded(Color.rgb(20, 196, 92), 15f)
        } else {
            followButton.setTextColor(Color.rgb(30, 60, 36))
            followButton.background = rounded(Color.argb(238, 255, 255, 255), 15f)
        }
    }

    private fun actionButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(14), dp(11), dp(14), dp(11))
        background = rounded(Color.rgb(20, 196, 92), 12f)
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
