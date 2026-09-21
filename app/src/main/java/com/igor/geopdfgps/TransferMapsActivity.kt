package com.igor.geopdfgps

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.*
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class TransferMapsActivity : AppCompatActivity() {
    private val mapsDir by lazy { File(filesDir, "maps").apply { mkdirs() } }
    private val executor = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    private var token: String? = null
    private lateinit var content: LinearLayout

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val value = result.contents ?: return@registerForActivityResult
        if (!value.startsWith("http://") || !value.contains("/geotrack/")) {
            Toast.makeText(this, "QR Code não pertence ao GeoTrack", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        receiveMaps(value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        server?.close(); server = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(Color.rgb(12,18,15))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text="‹"; textSize=38f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener{ finish() } }, LinearLayout.LayoutParams(dp(44),dp(52)))
        top.addView(TextView(this).apply { text="Transferir mapas"; textSize=22f; setTextColor(Color.WHITE); setTypeface(typeface,Typeface.BOLD); setPadding(dp(8),0,0,0) })
        root.addView(top)
        root.addView(TextView(this).apply { text="Copie todos os GeoPDFs e pastas de um GeoTrack para outro."; textSize=14f; setTextColor(Color.rgb(175,190,180)); setPadding(dp(4),dp(18),dp(4),dp(26)) })
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(bigButton("📤  ENVIAR MAPAS") { startSending() })
        content.addView(space(14))
        content.addView(bigButton("📷  RECEBER MAPAS") { startScanner() })
        root.addView(content)
        root.addView(TextView(this).apply { text="Os dois celulares precisam estar na mesma rede Wi‑Fi ou no mesmo ponto de acesso. A internet não é necessária."; textSize=12f; setTextColor(Color.rgb(130,150,138)); setPadding(dp(4),dp(24),dp(4),0) })
        setContentView(root)
    }

    private fun startScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Aponte para o QR Code do outro GeoTrack")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        }
        scanner.launch(options)
    }

    private fun startSending() {
        val ip = localIpv4()
        if (ip == null) {
            Toast.makeText(this, "Conecte os dois celulares à mesma rede Wi‑Fi", Toast.LENGTH_LONG).show(); return
        }
        val files = mapsDir.walkTopDown().filter { it.isFile && it.extension.equals("pdf",true) }.toList()
        if (files.isEmpty()) { Toast.makeText(this,"Não há mapas para enviar",Toast.LENGTH_LONG).show(); return }
        token = UUID.randomUUID().toString().replace("-","").take(12)
        try { server = ServerSocket(0) } catch (e: Exception) { Toast.makeText(this,"Não foi possível iniciar a transferência",Toast.LENGTH_LONG).show(); return }
        val url = "http://$ip:${server!!.localPort}/geotrack/${token}"
        content.removeAllViews()
        content.addView(TextView(this).apply { text="No outro celular, abra GeoTrack → Transferir mapas → Receber mapas"; textSize=14f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(dp(10),0,dp(10),dp(18)) })
        content.addView(ImageView(this).apply { setImageBitmap(qrBitmap(url, 720)); adjustViewBounds=true }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(310)))
        val status = TextView(this).apply { text="Aguardando o outro celular…\n${files.size} mapas prontos para enviar"; textSize=14f; setTextColor(Color.rgb(83,214,119)); gravity=Gravity.CENTER; setPadding(0,dp(18),0,dp(18)) }
        content.addView(status)
        content.addView(bigButton("CANCELAR") { showHome() })
        executor.execute {
            try {
                val socket = server?.accept() ?: return@execute
                val input = socket.getInputStream().bufferedReader()
                val request = input.readLine() ?: ""
                while (true) { val line=input.readLine() ?: break; if(line.isBlank()) break }
                if (!request.contains("/geotrack/${token}")) { socket.close(); return@execute }
                val out = BufferedOutputStream(socket.getOutputStream())
                val header = "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nX-Map-Count: ${files.size}\r\nConnection: close\r\n\r\n"
                out.write(header.toByteArray()); out.flush()
                val zip = ZipOutputStream(out)
                files.forEachIndexed { index, file ->
                    val rel = file.relativeTo(mapsDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    runOnUiThread { status.text="Enviando… ${index+1} de ${files.size} mapas" }
                }
                zip.finish(); zip.flush(); socket.close(); server?.close()
                runOnUiThread { status.text="✓ Transferência concluída\n${files.size} mapas enviados" }
            } catch (_: Exception) { if (!isFinishing) runOnUiThread { status.text="Transferência interrompida" } }
        }
    }

    private fun receiveMaps(url: String) {
        content.removeAllViews()
        val title = TextView(this).apply { text="Conectando ao outro GeoTrack…"; textSize=17f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,dp(25),0,dp(18)) }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate=true; max=100 }
        val status = TextView(this).apply { text="Aguarde"; textSize=14f; setTextColor(Color.rgb(83,214,119)); gravity=Gravity.CENTER; setPadding(0,dp(18),0,0) }
        content.addView(title); content.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(12))); content.addView(status)
        executor.execute {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout=10000; readTimeout=120000; requestMethod="GET"; connect() }
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.getHeaderField("X-Map-Count")?.toIntOrNull() ?: 0
                runOnUiThread { bar.isIndeterminate=false; bar.max=if(total>0) total else 1; title.text="Recebendo mapas…" }
                var count=0
                ZipInputStream(BufferedInputStream(conn.inputStream)).use { zip ->
                    while (true) {
                        val entry=zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".pdf")) {
                            val safeParts=entry.name.replace('\\','/').split('/').filter { it.isNotBlank() && it!="." && it!=".." }
                            if (safeParts.isNotEmpty()) {
                                var dir=mapsDir
                                safeParts.dropLast(1).forEach { part -> dir=File(dir,sanitize(part)).apply{mkdirs()} }
                                val rawName=sanitize(safeParts.last()).let { if(it.lowercase().endsWith(".pdf")) it else "$it.pdf" }
                                val dest=uniqueFile(dir,rawName)
                                dest.outputStream().use { zip.copyTo(it) }
                                count++
                                val c=count
                                runOnUiThread { bar.progress=c; status.text=if(total>0) "$c de $total mapas" else "$c mapas recebidos" }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                conn.disconnect()
                runOnUiThread {
                    bar.progress=bar.max; title.text="✓ Transferência concluída"; status.text="$count mapas recebidos"
                    content.addView(space(18)); content.addView(bigButton("VOLTAR AOS MAPAS") { finish() })
                }
            } catch (e: Exception) {
                runOnUiThread {
                    title.text="Não foi possível receber os mapas"; status.text="Confira se os dois celulares continuam na mesma rede."
                    content.addView(space(18)); content.addView(bigButton("TENTAR NOVAMENTE") { showHome() })
                }
            }
        }
    }

    private fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (_: Exception) { null }

    private fun qrBitmap(text: String, size: Int): Bitmap {
        val matrix=MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE,size,size)
        return Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565).apply {
            for(y in 0 until size) for(x in 0 until size) setPixel(x,y,if(matrix[x,y]) Color.BLACK else Color.WHITE)
        }
    }

    private fun sanitize(name:String)=name.replace(Regex("[\\\\/:*?\"<>|]"),"_").trim().ifBlank{"Mapa.pdf"}
    private fun uniqueFile(dir:File,name:String):File { var f=File(dir,name); if(!f.exists()) return f; val base=f.nameWithoutExtension; val ext=f.extension; var n=2; while(f.exists()){ f=File(dir,"$base ($n).$ext"); n++ }; return f }
    private fun bigButton(label:String, click:()->Unit)=TextView(this).apply { text=label; textSize=14f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface,Typeface.BOLD); setPadding(dp(18),dp(16),dp(18),dp(16)); background=rounded(Color.rgb(20,196,92),14f); setOnClickListener{click()} }
    private fun space(h:Int)=Space(this).apply{ layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun rounded(fill:Int,radius:Int)=GradientDrawable().apply{ shape=GradientDrawable.RECTANGLE; setColor(fill); cornerRadius=dp(radius).toFloat() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){ server?.close(); executor.shutdownNow(); super.onDestroy() }
}
