package com.igor.geopdfgps

data class GeoReference(
    val lpts: List<Pair<Double, Double>>,
    val gpts: List<Pair<Double, Double>>
) {
    fun geoToPage(latitude: Double, longitude: Double): Pair<Double, Double>? {
        if (lpts.size < 3 || gpts.size < 3) return null
        // GPTS in ISO geospatial PDF is usually [lat, lon]. Fit affine transform geo -> normalized PDF coords.
        val n = minOf(lpts.size, gpts.size)
        val a = Array(n) { i -> doubleArrayOf(gpts[i].first, gpts[i].second, 1.0) }
        val ux = DoubleArray(n) { lpts[it].first }
        val uy = DoubleArray(n) { lpts[it].second }
        val cx = leastSquares3(a, ux) ?: return null
        val cy = leastSquares3(a, uy) ?: return null
        val x = cx[0] * latitude + cx[1] * longitude + cx[2]
        val y = cy[0] * latitude + cy[1] * longitude + cy[2]
        return x to y
    }

    private fun leastSquares3(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val ata = Array(3) { DoubleArray(3) }
        val atb = DoubleArray(3)
        for (r in a.indices) {
            for (i in 0..2) {
                atb[i] += a[r][i] * b[r]
                for (j in 0..2) ata[i][j] += a[r][i] * a[r][j]
            }
        }
        return solve3(ata, atb)
    }

    private fun solve3(m0: Array<DoubleArray>, b0: DoubleArray): DoubleArray? {
        val m = Array(3) { i -> m0[i].clone() }
        val b = b0.clone()
        for (k in 0..2) {
            var p = k
            for (i in k + 1..2) if (kotlin.math.abs(m[i][k]) > kotlin.math.abs(m[p][k])) p = i
            if (kotlin.math.abs(m[p][k]) < 1e-12) return null
            val tr = m[k]; m[k] = m[p]; m[p] = tr
            val tb = b[k]; b[k] = b[p]; b[p] = tb
            val d = m[k][k]
            for (j in k..2) m[k][j] /= d
            b[k] /= d
            for (i in 0..2) if (i != k) {
                val f = m[i][k]
                for (j in k..2) m[i][j] -= f * m[k][j]
                b[i] -= f * b[k]
            }
        }
        return b
    }
}
