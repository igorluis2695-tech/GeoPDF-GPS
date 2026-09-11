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
        context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return try {
            PDDocument.load(tmp).use { doc ->
                for (page in doc.pages) {
                    val found = findMeasure(page.cosObject, mutableSetOf())
                    if (found != null) return@use found
                }
                null
            }
        } finally { tmp.delete() }
    }

    private fun findMeasure(base: COSBase?, seen: MutableSet<Int>): GeoReference? {
        if (base == null) return null
        val id = System.identityHashCode(base)
        if (!seen.add(id)) return null
        when (base) {
            is COSObject -> return findMeasure(base.`object`, seen)
            is COSDictionary -> {
                val l = numberArray(base.getDictionaryObject(COSName.getPDFName("LPTS")))
                val g = numberArray(base.getDictionaryObject(COSName.getPDFName("GPTS")))
                if (l.size >= 6 && g.size >= 6) {
                    val lp = l.chunked(2).map { it[0] to it[1] }
                    val gp = g.chunked(2).map { it[0] to it[1] }
                    return GeoReference(lp, gp)
                }
                for (key in base.keySet()) {
                    val r = findMeasure(base.getDictionaryObject(key), seen)
                    if (r != null) return r
                }
            }
            is COSArray -> for (i in 0 until base.size()) {
                val r = findMeasure(base.getObject(i), seen)
                if (r != null) return r
            }
        }
        return null
    }

    private fun numberArray(base: COSBase?): List<Double> {
        val arr = when (base) { is COSArray -> base; is COSObject -> base.`object` as? COSArray; else -> null } ?: return emptyList()
        val out = mutableListOf<Double>()
        for (i in 0 until arr.size()) {
            val v = arr.getObject(i)
            if (v is COSNumber) out += v.doubleValue()
        }
        return out
    }
}
