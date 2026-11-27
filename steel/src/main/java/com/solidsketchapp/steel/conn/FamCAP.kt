package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.ProfileCatalog
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * v70 X01 / X02 fittings (web v70 sections "X01 · Fittings" and "X02 · Column tops"): Tekla fits a member end to a plane, so the end
 * reaches it over the whole section; the member is built longer at that end and cut by the plane before the component cuts.
 */
object Fits {
    /** A beam's top face: a point on it and its upward normal (web topFace70). */
    fun topFace(st: JsonObject, profiles: ProfileCatalog): Pair<D3, D3> { val m = Joints.mf(st, profiles); val n = if (m.eh.z >= 0) m.eh else -m.eh; return m.c1.add(n, m.h / 2) to n }
    private fun seg(mb: MemberFrame, p: D3): Double { val d = mb.c2 - mb.c1; val t = (((p - mb.c1) dot d) / (d dot d)).coerceIn(0.0, 1.0); return mb.c1.add(d, t).distanceTo(p) }

    /** web endFit70: the column end nearest the beam, fitted to the beam's top face. */
    fun endFit(col: JsonObject, beam: JsonObject, profiles: ProfileCatalog): Fit? {
        val m = Joints.mf(col, profiles); val (p, n) = topFace(beam, profiles); val mb = Joints.mf(beam, profiles)
        val e2 = seg(mb, m.c2) <= seg(mb, m.c1); val endP = if (e2) m.c2 else m.c1; val out = if (e2) m.a else -m.a; val an = out dot n; if (abs(an) < 0.2) return null
        var ext = 0.0; for (sv in listOf(-1.0, 1.0)) for (sw in listOf(-1.0, 1.0)) { val c = endP.add(m.eh, sv * m.h / 2).add(m.es, sw * m.b / 2); ext = max(ext, ((p - c) dot n) / an) }
        return Fit(if (e2) 2 else 1, p, if (an > 0) n else -n, max(0.0, ext) + 10)
    }

    /** web v70 T144 wrapper: cfit 'top' fits the main part's end to the secondary's top face; the secondary is fitted to the end plate's face. */
    fun t144(x: FamCtx) {
        val b = x.b ?: return; val bId = x.bId ?: return
        if ((x.a["kind"] as? JsonPrimitive)?.contentOrNull != "member" || (b["kind"] as? JsonPrimitive)?.contentOrNull != "member") return
        if (x.s("cfit") == "top") endFit(x.a, b, x.profiles)?.let { x.fit(x.aId, it) }
        val ep = x.parts.firstOrNull { it.name == "END PLATE" && it.pieces.isNotEmpty() } ?: return
        val m = Joints.mf(x.a, x.profiles); val mb = Joints.mf(b, x.profiles); val mid = (m.c1 + m.c2) * 0.5
        val near = if (mb.c1.distanceTo(mid) <= mb.c2.distanceTo(mid)) 1 else 2
        var w = (mb.c1 + mb.c2) * 0.5 - m.c1; w = w.add(m.a, -(w dot m.a))   // toward the secondary's middle (its end may lie on the main part's axis: crane brackets)
        var n = m.eh; for (v in listOf(m.es, -m.eh, -m.es)) if ((v dot w) > (n dot w)) n = v
        var best = Double.NEGATIVE_INFINITY; var p: D3? = null
        for (msh in ep.pieces) for (i in 0 until msh.vertexCount) { val v = D3(msh.positions[i * 3].toDouble(), msh.positions[i * 3 + 1].toDouble(), msh.positions[i * 3 + 2].toDouble()); val d = v dot n; if (d > best) { best = d; p = v } }
        val out2 = if (near == 1) -mb.a else mb.a; val an = abs(out2 dot n)
        if (p != null && an > 0.2) x.fit(bId, Fit(near, p, -n, mb.h / 2 * sqrt(max(0.0, 1 - an * an)) / an + 20))
    }

    /** A half-space starting on the plane through [p], running to the side [n] points to (web half70). */
    fun half(p: D3, n: D3): Mesh { val nn = n.normalize(); val x = (nn cross (if (abs(nn.z) < 0.9) D3.Z else D3(1.0, 0.0, 0.0))).normalize(); val y = (nn cross x).normalize(); val big = 1e5
        return Joints.box3(p, x, y, -big, big, -big, big, 0.0, big) }

    /** The member's solid with its fits: built longer at each fitted end, cut by each plane (web stApplyCuts wrapper). */
    fun fitted(st: JsonObject, fits: List<Fit>, build: StBuild, booleans: BooleanEngine): Mesh? {
        if (fits.isEmpty() || (st["kind"] as? JsonPrimitive)?.contentOrNull != "member") return null
        val o = LinkedHashMap(st); for (f in fits) { val k = if (f.end == 1) "dx1" else "dx2"; o[k] = JsonPrimitive(((o[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0) + f.ext) }
        return booleans.subtract(build.build(JsonObject(o)), fits.map { half(it.p, it.n) })
    }
}

/**
 * CAP family · Column cap plate (SolidSketch, v70 X02; web FAM.CAP): t x b x L on the column's end face, or on the beam's top face
 * when a beam is picked (R12: the eave columns' sloped caps); e = its far end inside the column's far face (empty: centred).
 * Defaults: L = h - 2 tf, b = the flange width.
 */
object FamCAP {
    const val PORTED = true
    private fun blank(x: FamCtx, k: String) = (x.q[k] as? JsonPrimitive)?.contentOrNull?.trim().let { it == null || it.isEmpty() }
    fun gen(x: FamCtx) {
        val m = Joints.mf(x.a, x.profiles); val t = StBolts.tkNum(x.q["t"], 8.0).let { if (it == 0.0) 8.0 else it }; val b = x.b
        fun seg(mb: MemberFrame, p: D3): Double { val d = mb.c2 - mb.c1; val k = (((p - mb.c1) dot d) / (d dot d)).coerceIn(0.0, 1.0); return mb.c1.add(d, k).distanceTo(p) }
        val e2 = if (b != null) { val mb = Joints.mf(b, x.profiles); seg(mb, m.c2) <= seg(mb, m.c1) } else m.c2.z >= m.c1.z
        val endP = if (e2) m.c2 else m.c1; val out = if (e2) m.a else -m.a
        var p = endP; var n = out
        if (b != null) { val (tp, tn) = Fits.topFace(b, x.profiles); p = tp; n = if ((tn dot out) >= 0) tn else -tn }
        val an = out dot n; if (abs(an) < 0.2) { x.nullResult = true; return }
        val pc = endP.add(out, ((p - endP) dot n) / an)
        val dd = m.eh.add(n, -(m.eh dot n)).normalize(); val ww = (n cross dd).normalize()
        val l = if (blank(x, "L")) m.h - 2 * m.tf else x.n("L"); val bw = if (blank(x, "b")) m.b else x.n("b")
        var s0 = -l / 2; var s1 = l / 2
        if (!blank(x, "e")) { val sf = m.h / 2 / abs(dd dot m.eh)
            val so = if (b != null) { val mb = Joints.mf(b, x.profiles); if ((((mb.c1 + mb.c2) * 0.5 - pc) dot dd) > 0) -1 else 1 } else 1
            val a = sf - x.n("e"); if (so > 0) { s0 = a - l; s1 = a } else { s0 = -a; s1 = -a + l } }
        val name = (x.q["cname"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "Cap Plate"
        x.plate(name, Joints.box3(pc, dd, ww, s0, s1, -bw / 2, bw / 2, 0.0, t), t, "FLT" + com.solidsketchapp.core.api.JsNumber.format(t) + "*" + com.solidsketchapp.core.api.JsNumber.format(Math.round(bw * 10) / 10.0))
        x.welds += Weld("Cap to column", x.n("a").let { if (it == 0.0) 6.0 else it }, 2 * (m.b * 2 + m.h))
    }
}
