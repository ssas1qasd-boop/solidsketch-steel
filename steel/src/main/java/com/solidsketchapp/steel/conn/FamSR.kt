package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.steel.geom.D3
import kotlin.math.abs

/** SR family: sag rod through a purlin web (Tsag), v69 FAM.SR (L15645), routed to famGeom by the wrapper L15652. */
object FamSR {
    const val PORTED = true
    fun gen(x: FamCtx) {
        val b = x.b ?: throw IllegalStateException("not built")
        val mp = Joints.mf(x.a, x.profiles); val mr = Joints.mf(b, x.profiles); val w = FamCL.purlinWeb(x, x.a, mp)
        val tw = w.t.let { if (it != 0.0 && !it.isNaN()) it else 2.0 }; val nW = w.dir.normalize()
        val pw = mp.c1.add(w.dir, w.off - tw / 2); val ar = mr.a; val den = ar dot nW; if (abs(den) < 0.05) throw IllegalStateException("not built")
        val c0 = mr.c1; val s = ((pw - c0) dot nW) / den; val x0 = c0.add(ar, s)
        val d = if (mr.h != 0.0 && !mr.h.isNaN()) mr.h else 12.0; val sg = FamCL.sgn1(den)
        x.cut(x.aId, stCyl(x0.add(ar, -3 * tw - 2), ar, d / 2 + x.n("tol") / 2, 6 * tw + 4, 12))
        val a0 = x0.add(ar, -sg * tw / 2); val a1 = x0.add(ar, sg * tw / 2); val nutL = 0.8 * d; val wsh = 2.5; val ds = JsNumber.format(d)
        for ((p0, o) in listOf(a0 to -sg, a1 to sg)) { val ad: D3 = ar * o
            if (x.t("washer")) x.plate("WASHER", stCyl(p0, ad, 1.1 * d, wsh, 16), 2.5, "WASHER M$ds")
            if (x.t("nut")) x.plate("NUT", stCyl(p0.add(ad, if (x.t("washer")) wsh else 0.0), ad, 0.95 * d, nutL, 6), 0.0, "NUT M$ds") }
    }
}
