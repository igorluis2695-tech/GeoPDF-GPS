package com.igor.geopdfgps

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

object GeoPdfParser {
    fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)

    fun parse(context: Context, uri: Uri): GeoReference? {
        val tmp = File.createTempFile("geopdf_", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }

        return try {
            PDDocument.load(tmp).use { doc ->
                for (page in doc.pages) {
                    // GeoPDFs exported by ArcMap usually put the Measure dictionary inside
                    // a Viewport (/VP). LPTS is normalized to the viewport, NOT to the full
                    // PDF page. Convert it to full-page normalized coordinates so the GPS
                    // marker lines up with the bitmap rendered by PdfRenderer.
                    parseViewport(page.cosObject)?.let { return@use it }

                    // Fallback for GeoPDFs that store GPTS/LPTS elsewhere.
                    findMeasure(page.cosObject, mutableSetOf())?.let { return@use it }
                }
                null
            }
        } finally {
            tmp.delete()
        }
    }

    private fun parseViewport(page: COSDictionary): GeoReference? {
        val media = numberArray(page.getDictionaryObject(COSName.getPDFName("MediaBox")))
        if (media.size < 4) return null

        val pageLeft = media[0]
        val pageBottom = media[1]
        val pageWidth = media[2] - media[0]
        val pageHeight = media[3] - media[1]
        if (pageWidth == 0.0 || pageHeight == 0.0) return null

        val vpBase = deref(page.getDictionaryObject(COSName.getPDFName("VP")))
        val viewports = when (vpBase) {
            is COSArray -> (0 until vpBase.size()).mapNotNull { deref(vpBase.getObject(it)) as? COSDictionary }
            is COSDictionary -> listOf(vpBase)
            else -> emptyList()
        }

        for (vp in viewports) {
            val bbox = numberArray(vp.getDictionaryObject(COSName.getPDFName("BBox")))
            if (bbox.size < 4) continue

            val measure = deref(vp.getDictionaryObject(COSName.getPDFName("Measure"))) as? COSDictionary ?: continue
            val l = numberArray(measure.getDictionaryObject(COSName.getPDFName("LPTS")))
            val g = numberArray(measure.getDictionaryObject(COSName.getPDFName("GPTS")))
            if (l.size < 6 || g.size < 6) continue

            // IMPORTANT: preserve the BBox axis direction exactly as stored in the PDF.
            // ArcMap may write BBox as [left, top, right, bottom]. Using min/max here
            // flips the vertical axis and puts the GPS marker on the opposite side of
            // the map. LPTS must be interpolated along the signed BBox dimensions.
            val bx0 = bbox[0]
            val by0 = bbox[1]
            val bw = bbox[2] - bbox[0]
            val bh = bbox[3] - bbox[1]
            if (bw == 0.0 || bh == 0.0) continue

            val lp = l.chunked(2).mapNotNull { pair ->
                if (pair.size < 2) null else {
                    val pageX = (bx0 + pair[0] * bw - pageLeft) / pageWidth
                    val pageY = (by0 + pair[1] * bh - pageBottom) / pageHeight
                    pageX to pageY
                }
            }
            val gp = g.chunked(2).mapNotNull { pair ->
                if (pair.size < 2) null else pair[0] to pair[1]
            }
            if (lp.size >= 3 && gp.size >= 3) return GeoReference(lp, gp)
        }
        return null
    }

    private fun findMeasure(base: COSBase?, seen: MutableSet<Int>): GeoReference? {
        val obj = deref(base) ?: return null
        val id = System.identityHashCode(obj)
        if (!seen.add(id)) return null

        when (obj) {
            is COSDictionary -> {
                val l = numberArray(obj.getDictionaryObject(COSName.getPDFName("LPTS")))
                val g = numberArray(obj.getDictionaryObject(COSName.getPDFName("GPTS")))
                if (l.size >= 6 && g.size >= 6) {
                    val lp = l.chunked(2).mapNotNull { if (it.size >= 2) it[0] to it[1] else null }
                    val gp = g.chunked(2).mapNotNull { if (it.size >= 2) it[0] to it[1] else null }
                    return GeoReference(lp, gp)
                }
                for (key in obj.keySet()) {
                    findMeasure(obj.getDictionaryObject(key), seen)?.let { return it }
                }
            }
            is COSArray -> for (i in 0 until obj.size()) {
                findMeasure(obj.getObject(i), seen)?.let { return it }
            }
        }
        return null
    }

    private fun deref(base: COSBase?): COSBase? = if (base is COSObject) base.`object` else base

    private fun numberArray(base: COSBase?): List<Double> {
        val arr = deref(base) as? COSArray ?: return emptyList()
        val out = mutableListOf<Double>()
        for (i in 0 until arr.size()) {
            val v = deref(arr.getObject(i))
            if (v is COSNumber) out += v.doubleValue()
        }
        return out
    }
}
