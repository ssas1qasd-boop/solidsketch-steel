package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * PU family: purlin / girt / rail cleats (61, 93, purlin cleat, angle 70 / 8, wooden shoe 15, tube rail 113) and cold-formed
 * laps and sleeves (overlap 1c, sleeve 2c). Live chain: v69 FAM.PU (L15497, overlap / sleeve) → base (L15099, FAM_PU16).
 */
object FamPU {
    const val PORTED = true

    private fun or(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v
    private fun isC(m: MemberFrame) = m.kind == "C" || m.kind == "U"
    /** v69 stFaceAt (L14891): the face of [m] nearest [p0]: outward normal, offset, axis point. */
    private class Face(val n: D3, val off: Double, val pc: D3)
    private fun faceAt(m: MemberFrame, p0: D3): Face {
        val al = (p0 - m.c1) dot m.a; val pc = m.c1.add(m.a, al); val w = p0 - pc; val y = w dot m.eh; val z = w dot m.es
        fun sg(v: Double) = if (v > 0) 1.0 else if (v < 0) -1.0 else 1.0
        val fl = abs(y) / (if (m.h != 0.0) m.h else 1.0) >= abs(z) / (if (m.b != 0.0) m.b else 1.0)
        return Face(if (fl) m.eh * sg(y) else m.es * sg(z), if (fl) m.h / 2 else (if (m.kind == "O" || m.kind == "R") m.b / 2 else m.tw / 2), pc)
    }
    /** The purlin's point nearest the main part's axis (v69 PP). */
    private fun pp(ma: MemberFrame, mp: MemberFrame, den0: Boolean): D3 {
        val w0 = ma.c1 - mp.c1; val b = ma.a dot mp.a; val dd = ma.a dot w0; val ee = mp.a dot w0; var den = 1 - b * b; if (den0 && den == 0.0) den = 1e-9
        return mp.c1.add(mp.a, (ee - b * dd) / den)
    }

    fun gen(x: FamCtx) {
        val k = x.s("kind")
        if (k != "overlap" && k != "sleeve") { base(x, k, listOfNotNull(x.b?.let { it to x.bId!! }, x.b2?.let { it to x.b2Id!! })); return }
        // v69 L15497: the cleat first (kind 'cleat', the first purlin only), then the lap / sleeve
        base(x, "cleat", listOfNotNull(x.b?.let { it to x.bId!! }))
        val p = x.b ?: return; val pId = x.bId!!; val mp = Joints.mf(p, x.profiles); val ma = Joints.mf(x.a, x.profiles); val bolt = x.n("bolt")
        val es = if (isC(mp)) -mp.es else mp.es; val pp = pp(ma, mp, true)
        val web = pp.add(es, if (isC(mp)) mp.b / 2 else 0.0); val t = or(mp.tw, 2.0); val lap = x.n("lap"); val hw = mp.h / 2 - 20 - 15; val up = mp.eh
        if (k == "sleeve") {
            val ts = or(x.n("ts"), max(2.0, 2.0 * 1.5)); val inner = -es   // (MP.t || 2) · 1.5: stMF has no t
            val sp = Joints.box3(web.add(inner, t + ts / 2), mp.a, up, -lap, lap, -hw, hw, -ts / 2, ts / 2)
            val bl = listOf(-lap + 40, -lap + 110, lap - 110, lap - 40).map { s -> web.add(mp.a, s).add(es, 0.1) }; val grip = t + ts + 2
            val bg = x.group(bl, inner, bolt, grip); for (hc in bg.holes) x.cut(pId, hc)
            x.plate("SLEEVE", sp, floor(ts * 10 + 0.5) / 10, minus = bg.holes); x.bolts(bg, 4, grip + 2 * bolt); return
        }
        val p2 = x.b2 ?: return; val p2Id = x.b2Id!!; val m2 = Joints.mf(p2, x.profiles); val es2 = if (isC(m2)) -m2.es else m2.es
        val w2 = pp.add(es2, if (isC(m2)) m2.b / 2 else 0.0); val gapW = abs((w2 - web) dot es); val t2 = or(m2.tw, 2.0)
        val outer = if (((web - w2) dot es) >= 0) es else -es; val start = if (((web - w2) dot outer) >= 0) web else w2
        val bl = ArrayList<D3>(); for (s in listOf(-lap + 40, lap - 40)) for (z in listOf(-hw * 0.4, hw * 0.4)) bl += start.add(mp.a, s).add(up, z).add(outer, 0.1)
        val grip = gapW + t + t2 + 2; val bg = x.group(bl, -outer, bolt, grip); for (hc in bg.holes) { x.cut(pId, hc); x.cut(p2Id, hc) }
        x.bolts(bg, bl.size, grip + 2 * bolt)
    }

    /** v69 FAM.PU base (L15099): a cleat on the main part's face per purlin (with a leg for angle / tube / shoe, two plates for sleeve / shoe). */
    private fun base(x: FamCtx, kind: String, list: List<Pair<JsonObject, String>>) {
        val two = kind == "sleeve" || kind == "shoe"; val bolt = x.n("bolt")
        for ((p, pId) in list) {
            val mp = Joints.mf(p, x.profiles); val ma = Joints.mf(x.a, x.profiles); if ((ma.a cross mp.a).length() < 1e-6) continue
            val pp = pp(ma, mp, false); val fa = faceAt(ma, pp); val base = fa.pc.add(fa.n, fa.off); val c = isC(mp)
            val es = mp.es * (if (c) -1.0 else or(x.n("side"), 1.0)); val tw = if (c) mp.b / 2 else mp.tw / 2; val t = x.n("t")
            val hc = or(x.n("h"), floor(mp.h * 0.6 + 0.5)); val w = x.n("w") / 2
            val zN = if (((fa.n cross mp.a) dot es) >= 0) 1 else -1
            fun pl1(o: D3, dir: Int): Mesh = Joints.box3(o, fa.n, mp.a, 0.0, hc, -w, w, if (zN * dir > 0) 0.0 else -t, if (zN * dir > 0) t else 0.0)
            val cl = arrayListOf(pl1(base.add(es, tw), 1)); if (two) cl += pl1(base.add(es, -tw), -1)
            if (kind == "angle" || kind == "tube" || kind == "shoe") { val zL = if (((es cross mp.a) dot fa.n) >= 0) 1 else -1
                val x0 = if (kind == "shoe") -tw - t else tw; val x1 = if (kind == "shoe") tw + t else tw + x.n("leg")
                x.plate("CLEAT LEG", Joints.box3(base, es, mp.a, x0, x1, -w, w, if (zL > 0) 0.0 else -t, if (zL > 0) t else 0.0), t) }
            val bl = listOf(0.35, 0.75).map { f -> base.add(fa.n, hc * f).add(es, tw + t + 0.1) }.toMutableList()
            if (c) { val lo = 20.0 + 25; for (i in bl.indices) { val d = (bl[i] - base) dot fa.n; if (d < lo) bl[i] = bl[i].add(fa.n, lo - d) } }   // (MP.c || 20): stMF has no c
            val grip = (if (two) 2 * t else t) + mp.tw + 2; val bg = x.group(bl, -es, bolt, grip); for (hc2 in bg.holes) x.cut(pId, hc2)
            x.parts += ConnPart(if (kind == "shoe") "PURLIN SHOE" else "CLEAT", cl, prof = "PL" + com.solidsketchapp.core.api.JsNumber.format(floor(t * 10 + 0.5) / 10), minus = bg.holes)
            if (mp.kind == "I") for (sg in (if (two) listOf(1, -1) else listOf(1))) {
                val pos = ((fa.n cross mp.a) dot es) * sg >= 0
                x.cut(pId, Joints.box3(base, fa.n, mp.a, -1.0, mp.tf + mp.r + 2, -w - 5, w + 5, if (pos) tw - 0.5 else -mp.b, if (pos) mp.b else -tw + 0.5))
            }
            x.bolts(bg, 2, grip + 2 * bolt); x.weld("Cleat to main part", x.n("a"), 2 * x.n("w"))
        }
    }
}
