package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BP family · base plates (v69 FAM.BP, the live chain): v54 L15798 (Tekla 1042 options: anchor gauges, grout, cast plate,
 * plate washers) → v46 L15467 (bearing plate under a beam end) → v40 L15012 (rectangular / round plate, anchors, stiffeners).
 */
object FamBP {
    const val PORTED = true

    /** v54 L15798: the live definition. */
    fun gen(x: FamCtx) {
        val m = Joints.mf(x.a, x.profiles); val vert = abs(m.a.z) > 0.8; val round = x.s("shape") == "round" || m.kind == "O"
        if ((x.s("awith") == "rods" || x.s("gorder") == "tekla") && vert && !round && !x.t("bearing")) return bp70(x, m)   // v70 C01 / C16
        if (!vert || round || x.t("bearing") || !(x.n("gx") > 0 || x.n("gy") > 0 || x.n("gt") > 0 || x.n("cast") > 0 || x.n("pw") > 0)) return bp22(x)
        val low = if (m.c1.z <= m.c2.z) m.c1 else m.c2; val n = D3.Z; val xd = m.eh.add(n, -(m.eh dot n)).normalize(); val yd = n cross xd
        val t = x.n("t"); val l = m.h / 2 + x.n("ext"); val w = m.b / 2 + x.n("side"); val o = low
        x.cut(x.aId, Joints.box3(o, xd, yd, -l * 4, l * 4, -w * 4, w * 4, -1000.0, t))
        val na = max(2, jsInt(x.n("anchors"))); val ax = if (x.n("gx") > 0) x.n("gx") / 2 else m.h / 2 + x.n("ext") * 0.55; val ay = if (x.n("gy") > 0) x.n("gy") / 2 else max(m.b / 2 - 40, 50.0)
        val pts = ArrayList<DoubleArray>()
        if (na == 2) { pts += doubleArrayOf(0.0, -ay); pts += doubleArrayOf(0.0, ay) } else {
            for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) pts += doubleArrayOf(sx * ax, sy * ay)
            if (na >= 6) { pts += doubleArrayOf(0.0, -ay); pts += doubleArrayOf(0.0, ay) }; if (na >= 8) { pts += doubleArrayOf(-ax, 0.0); pts += doubleArrayOf(ax, 0.0) } }
        val tw = if (x.t("pw")) 10.0 else 0.0; fun at(p: DoubleArray) = o.add(xd, p[0]).add(yd, p[1])
        val bolt = x.n("bolt"); val bl = pts.map { at(it).add(n, t + tw + 1.2 * bolt) }
        val bb = x.group(bl, D3(0.0, 0.0, -1.0), bolt, t + tw + x.n("anch"))
        x.plate("BASE PLATE", Joints.box3(o, xd, yd, -l, l, -w, w, 0.0, t), x.n("t"), minus = bb.holes); anchors(x, bb, bl.size)
        x.weld("Column to base plate", x.n("a"), perim(x) * 1000)
        if (x.n("cast") > 0) x.plate("CAST PLATE", Joints.box3(o, xd, yd, -l, l, -w, w, -x.n("cast"), 0.0), x.n("cast"), minus = bb.holes)
        if (x.n("gt") > 0) { val z1 = -(if (x.t("cast")) x.n("cast") else 0.0); val gm = x.n("gm")
            x.parts += ConnPart("GROUT", listOf(Joints.box3(o, xd, yd, -l - gm, l + gm, -w - gm, w + gm, z1 - x.n("gt"), z1)), prof = "PL" + jsStr(x.q["gt"]), minus = bb.holes) }
        if (x.t("pw")) for (p in pts) { val pp = at(p); val r0 = bolt / 2 + 1
            x.parts += ring("PLATE WASHER", pp.add(n, t), 20.0, r0, 10.0)
            val zf = t + tw + 1.2 * bolt - (t + tw + x.n("anch")); x.parts += ring("ANCHOR WASHER", pp.add(n, zf), 20.0, r0, 20.0) }
    }

    /** v46 L15467: a beam end on a bearing plate (q.bearing, not a column); else the v40 plate. */
    private fun bp22(x: FamCtx) {
        val m = Joints.mf(x.a, x.profiles); if (!x.t("bearing") || abs(m.a.z) > 0.8) return bp15(x)
        // v69: the beam end nearest the picked point (c.pt; c1 when there is none)
        val pt = x.pt ?: m.c1; val atC1 = pt.distanceTo(m.c1) <= pt.distanceTo(m.c2)
        val z = D3.Z; val end = if (atC1) m.c1 else m.c2; val inw = if (atC1) m.a else -m.a; val side = (z cross m.a).normalize()
        val zb = end.z - m.h / 2; val t = x.n("t"); val lp = 200.0; val wp = m.b / 2 + 50; val o = D3(end.x, end.y, zb)
        val bolt = x.n("bolt"); val bl = listOf(-1, 1).map { s -> o.add(inw, lp / 2).add(side, s * (wp - 30)).add(z, 1.2 * bolt) }
        val b = x.group(bl, D3(0.0, 0.0, -1.0), bolt, t + x.n("anch"))
        x.plate("BEARING PLATE", Joints.box3(o, inw, side, 0.0, lp, -wp, wp, -t, 0.0), x.n("t"), minus = b.holes); anchors(x, b, 2)
        val hw = m.h / 2 - m.tf; val ww = m.b / 2 - 5; val s0 = end.add(inw, lp / 2); val ts = x.n("ts")
        for (sg in listOf(1, -1)) x.plate("STIFFENER", Joints.box3(s0, side, z, if (sg > 0) m.tw / 2 else -ww, if (sg > 0) ww else -m.tw / 2, -hw, hw, -ts / 2, ts / 2), ts)
        x.weld("Beam to bearing plate", x.n("a"), 2 * m.b)
    }

    /** v40 L15012: rectangular (or round) base plate at the lower end, anchors, stiffeners (flange / web / box / round). */
    private fun bp15(x: FamCtx) {
        val m = Joints.mf(x.a, x.profiles); val c1Low = m.c1.z <= m.c2.z; val low = if (c1Low) m.c1 else m.c2; val up = m.a * (if (c1Low) 1.0 else -1.0); val vert = abs(m.a.z) > 0.8
        val n = if (vert) D3.Z else up; val xd = m.eh.add(n, -(m.eh dot n)).normalize(); val yd = n cross xd; val t = x.n("t"); val o = low
        val round = x.s("shape") == "round" || m.kind == "O"; val l = m.h / 2 + x.n("ext"); val w = m.b / 2 + (if (round) x.n("ext") else x.n("side"))
        val bp = if (round) Joints.poly3(o, xd, yd, List(40) { i -> doubleArrayOf(l * cos(i * PI / 20), l * sin(i * PI / 20)) }, 0.0, t) else Joints.box3(o, xd, yd, -l, l, -w, w, 0.0, t)
        x.cut(x.aId, Joints.box3(o, xd, yd, -l * 4, l * 4, -w * 4, w * 4, -1000.0, t))
        val pts = ArrayList<DoubleArray>(); val na = max(2, jsInt(x.n("anchors")))
        if (round) { val r = m.h / 2 + x.n("ext") * 0.5; for (i in 0 until na) { val a = (i + 0.5) * 2 * PI / na; pts += doubleArrayOf(r * cos(a), r * sin(a)) } }
        else { val ax = m.h / 2 + x.n("ext") * 0.55; val ay = max(m.b / 2 - 40, 50.0)
            if (na == 2) { pts += doubleArrayOf(0.0, -ay); pts += doubleArrayOf(0.0, ay) } else {
                for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) pts += doubleArrayOf(sx * ax, sy * ay)
                if (na >= 6) { pts += doubleArrayOf(0.0, -w + 45); pts += doubleArrayOf(0.0, w - 45) }; if (na >= 8) { pts += doubleArrayOf(-ax, 0.0); pts += doubleArrayOf(ax, 0.0) } } }
        val bolt = x.n("bolt"); val bl = pts.map { o.add(xd, it[0]).add(yd, it[1]).add(n, t + 1.2 * bolt) }
        val bb = x.group(bl, -n, bolt, t + x.n("anch"))
        x.plate("BASE PLATE", bp, x.n("t"), minus = bb.holes); anchors(x, bb, bl.size)
        x.weld("Column to base plate", x.n("a"), perim(x) * 1000)
        val hs = x.n("hs"); val ts = x.n("ts")
        fun stf(x0: Double, y0: Double, dx: Double, dy: Double, len: Double) {
            val f = Joints.Frame3(o.add(xd, x0).add(yd, y0).add(n, t), (xd * dx).add(yd, dy).normalize(), n)
            x.plate("STIFFENER", f.poly(listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(len, 0.0), doubleArrayOf(len, 25.0), doubleArrayOf(0.0, hs)), -ts / 2, ts / 2), x.n("ts"))
        }
        val st = x.s("stiff")
        if (!round && (st == "flange" || st == "box")) for (sx in listOf(-1.0, 1.0)) stf(sx * m.h / 2, 0.0, sx, 0.0, l - m.h / 2)
        if (!round && (st == "web" || st == "box")) for (sy in listOf(-1.0, 1.0)) stf(0.0, sy * m.tw / 2, 0.0, sy, w - m.tw / 2)
        if (round && st != "none") for (i in 0 until na) { val a = i * 2 * PI / na; stf(cos(a) * m.h / 2, sin(a) * m.h / 2, cos(a), sin(a), l - m.h / 2) }
    }

    // ---------- v70 C01 / C16 (web v70 section "C01 / C16"): Base plate 1042 / 1004 as Tekla ----------
    private fun tk(x: FamCtx, k: String, d: Double) = StBolts.tkNum(x.q[k], d)
    private fun poly(nv: Int, r: Double) = List(nv) { k -> doubleArrayOf(r * cos(k * 2 * PI / nv), r * sin(k * 2 * PI / nv)) }
    private fun perpTo(nn: D3): D3 = ((if (abs(nn.z) < 0.9) D3.Z else D3(1.0, 0.0, 0.0)) cross nn).normalize()
    /** A convex solid between two rings of the same size (k-th vertex to k-th vertex): the caps and the side quads. */
    private fun loft(a: List<D3>, b: List<D3>): Mesh {
        val nv = a.size; val pos = FloatArray(nv * 6); (a + b).forEachIndexed { i, p -> pos[3 * i] = p.x.toFloat(); pos[3 * i + 1] = p.y.toFloat(); pos[3 * i + 2] = p.z.toFloat() }
        val idx = ArrayList<Int>()
        for (k in 1 until nv - 1) { idx += listOf(0, k + 1, k); idx += listOf(nv, nv + k, nv + k + 1) }
        for (k in 0 until nv) { val k1 = (k + 1) % nv; idx += listOf(k, k1, nv + k1); idx += listOf(k, nv + k1, nv + k) }
        val mesh = Mesh(pos, idx.toIntArray())
        if (com.solidsketchapp.steel.geom.Prism.signedVolume(mesh) >= 0) return mesh
        val r = idx.toIntArray(); for (t in 0 until r.size / 3) { val tmp = r[3 * t + 1]; r[3 * t + 1] = r[3 * t + 2]; r[3 * t + 2] = tmp }; return Mesh(pos, r)
    }
    /** Tekla hook type 1 (web bpRod70): an 8-gon (vertices on the bend plane and across it) down from [top], bent by [a] in two chords, the hook's end face [b] from the axis. */
    private fun rod(top: D3, hd: D3?, n: D3, d: Double, len: Double, a: Double, b: Double): List<Mesh> {
        val r = d / 2; val w = hd?.let { (n cross it).normalize() }; val zl = top.add(n, -(len - r)); val c = 1 - sqrt(0.5)
        val p = if (hd == null) listOf(top, top.add(n, -len)) else listOf(top, zl.add(n, a), zl.add(hd, a * c).add(n, a * c), zl.add(hd, a), zl.add(hd, b))
        val segs = (0 until p.size - 1).map { Triple(p[it], p[it + 1], (p[it + 1] - p[it]).normalize()) }.filter { it.first.distanceTo(it.second) > 1e-9 }
        val pg = poly(8, r)
        return segs.mapIndexed { i, (p0, p1, dv) ->
            val u = if (w != null) (w cross dv).normalize() else perpTo(dv); val v = dv cross u
            val mA = if (i == 0) dv else (segs[i - 1].third + dv).normalize(); val mB = if (i == segs.size - 1) dv else (dv + segs[i + 1].third).normalize()
            fun ring(o: D3, mm: D3) = pg.map { q -> val rad = u * q[0] + v * q[1]; o + rad + dv * (-(rad dot mm) / (dv dot mm)) }
            loft(ring(p0, mA), ring(p1, mB))
        }
    }
    private fun ringPart(name: String, p: D3, n: D3, ro: Double, ri: Double, h: Double, prof: String): ConnPart {
        val u = perpTo(n); val v = n cross u
        return ConnPart(name, listOf(com.solidsketchapp.steel.geom.Prism.extrude(com.solidsketchapp.steel.geom.Region(poly(12, ro), if (ri > 0) listOf(poly(12, ri).reversed()) else emptyList()), h, com.solidsketchapp.steel.geom.Prism.Frame(p, u, v, n))), prof = prof)
    }
    private fun hole24(p: D3, n: D3, r: Double): Mesh { val u = perpTo(n); val v = n cross u
        return com.solidsketchapp.steel.geom.Prism.extrude(com.solidsketchapp.steel.geom.Region(poly(24, r)), 4000.0, com.solidsketchapp.steel.geom.Prism.Frame(p.add(n, -2000.0), u, v, n)) }

    private fun bp70(x: FamCtx, m: MemberFrame) {
        val low = if (m.c1.z <= m.c2.z) m.c1 else m.c2; val n = D3.Z; val xd = m.eh.add(n, -(m.eh dot n)).normalize(); val yd = n cross xd
        val t = x.n("t"); val l = m.h / 2 + x.n("ext"); val w = m.b / 2 + x.n("side"); val o = low
        x.cut(x.aId, Joints.box3(o, xd, yd, -l * 4, l * 4, -w * 4, w * 4, -1000.0, t))
        val na = max(2, jsInt(x.n("anchors"))); val ax = if (x.n("gx") > 0) x.n("gx") / 2 else m.h / 2 + x.n("ext") * 0.55; val ay = if (x.n("gy") > 0) x.n("gy") / 2 else max(m.b / 2 - 40, 50.0)
        val pts = ArrayList<DoubleArray>()
        if (na == 2) { pts += doubleArrayOf(0.0, -ay); pts += doubleArrayOf(0.0, ay) } else {
            for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) pts += doubleArrayOf(sx * ax, sy * ay)
            if (na >= 6) { pts += doubleArrayOf(0.0, -ay); pts += doubleArrayOf(0.0, ay) }; if (na >= 8) { pts += doubleArrayOf(-ax, 0.0); pts += doubleArrayOf(ax, 0.0) } }
        fun at(p: DoubleArray) = o.add(xd, p[0]).add(yd, p[1])
        val d = x.n("bolt").takeIf { it != 0.0 && !it.isNaN() } ?: 20.0; val rods = x.s("awith") == "rods"
        val tol = tk(x, "tol", if (d <= 24) 2.0 else 3.0); val hr = (d + tol) / 2
        var bb: BoltGroup? = null
        val holes: List<Mesh> = if (rods) pts.map { hole24(at(it), n, hr) } else { val tw = if (x.t("pw")) 10.0 else 0.0
            val bl = pts.map { at(it).add(n, t + tw + 1.2 * d) }; bb = x.group(bl, D3(0.0, 0.0, -1.0), d, t + tw + x.n("anch")); bb!!.holes }
        x.plate("BASE PLATE", Joints.box3(o, xd, yd, -l, l, -w, w, 0.0, t), x.n("t"), minus = holes)
        if (!rods) anchors(x, bb!!, pts.size)
        x.weld("Column to base plate", x.n("a"), perim(x) * 1000)
        val gt = x.n("gt").takeIf { !it.isNaN() } ?: 0.0; val tek = x.s("gorder") == "tekla"; val cz = if (tek) -tk(x, "castd", gt) else 0.0
        if (x.n("cast") > 0) x.plate("CAST PLATE", Joints.box3(o, xd, yd, -l, l, -w, w, cz - x.n("cast"), cz), x.n("cast"), minus = holes)
        if (gt > 0) { val z1 = if (tek) 0.0 else -(if (x.t("cast")) x.n("cast") else 0.0); val gm = x.n("gm")
            val g = if (x.s("gslope") == "slope" && gm > 0) { fun rect(hl: Double, hw: Double, z: Double) = listOf(o.add(xd, -hl).add(yd, -hw).add(n, z), o.add(xd, hl).add(yd, -hw).add(n, z), o.add(xd, hl).add(yd, hw).add(n, z), o.add(xd, -hl).add(yd, hw).add(n, z))
                loft(rect(l + gm, w + gm, z1 - gt), rect(l, w, z1)) } else Joints.box3(o, xd, yd, -l - gm, l + gm, -w - gm, w + gm, z1 - gt, z1)
            x.parts += ConnPart("GROUT", listOf(g), prof = "PL" + jsStr(x.q["gt"]), minus = if (!tek) holes else emptyList()) }
        if (!rods) { if (x.t("pw")) for (p in pts) { val pp = at(p); val r0 = d / 2 + 1
                x.parts += ring("PLATE WASHER", pp.add(n, t), 20.0, r0, 10.0)
                val zf = t + 10.0 + 1.2 * d - (t + 10.0 + x.n("anch")); x.parts += ring("ANCHOR WASHER", pp.add(n, zf), 20.0, r0, 20.0) }
            return }
        val len = tk(x, "aL", 500.0); val up = tk(x, "aup", 50.0); val a = tk(x, "ahr", 2 * d); val b = tk(x, "ahw", len / 5); val nutL = tk(x, "nutL", d); val wT = tk(x, "washT", nutL / 2); val nn = jsInt(x.n("anut"))
        val pd = "PD" + JsNumber.format(2 * d) + "*" + JsNumber.format(d / 2)
        for (p in pts) { val pp = at(p); val top = pp.add(n, t + up); val sd = x.s("ahdir").ifEmpty { "x" }
            val hd = if (sd == "none") null else { val e = if (sd.endsWith("x")) xd else yd; val k = if (sd.endsWith("x")) p[0] else p[1]; e * ((if (sd.startsWith("-")) -1.0 else 1.0) * (if (k == 0.0) 1.0 else kotlin.math.sign(k))) }
            val hole = JsonObject(linkedMapOf("pts" to JsonArray(listOf(jsArr(top.x, top.y, top.z))), "ax" to jsArr(0.0, 0.0, -1.0), "d" to jsNum(d), "d0" to jsNum(d + tol), "L" to jsNum(len), "poly" to jsNum(24.0)))
            x.parts += ConnPart("ANCHOR ROD", rod(top, hd, n, d, len, a, b), prof = "ROD" + JsNumber.format(d), hole = hole)
            var z = t; if (jsInt(x.n("awash")) != 0) { x.parts += ringPart("WASHER", pp.add(n, z), n, d, d / 2, wT, pd); z += wT }
            for (k in 0 until minOf(2, nn)) { x.parts += ringPart("NUT", pp.add(n, z), n, d, d / 2, nutL, pd); z += nutL } }
    }

    /** v69 `X.out.parts.push({name: 'ANCHOR BOLTS', bolts: {pts, ax, d: q.bolt, n, grade, L: round(anch + t + 2.6·bolt)}})`. */
    private fun anchors(x: FamCtx, b: BoltGroup, nb: Int) {
        val info = linkedMapOf<String, JsonElement>("pts" to JsonArray(b.pts.map { jsArr(it.x, it.y, it.z) }), "ax" to jsArr(b.ax.x, b.ax.y, b.ax.z),
            "d" to jsNum(x.n("bolt")), "n" to jsNum(nb.toDouble()), "grade" to JsonPrimitive(x.s("grade")), "L" to jsNum(floor(x.n("anch") + x.n("t") + 2.6 * x.n("bolt") + 0.5)))
        x.parts += ConnPart("ANCHOR BOLTS", b.pieces, JsonObject(info), group = b)
    }
    /** `M.p.P || 1.5` (paint surface m²/m of the column profile). */
    private fun perim(x: FamCtx): Double = x.profiles.lookup((x.a["prof"] as? JsonPrimitive)?.contentOrNull ?: "")?.P?.takeIf { it != 0.0 && !it.isNaN() } ?: 1.5
    /** v54ring: a washer (cylinder ro, length L, along +Z) with its bore (ri) still to subtract. */
    private fun ring(name: String, p: D3, ro: Double, ri: Double, len: Double): ConnPart {
        val ax = D3.Z; val outer: Mesh = stCyl(p, ax, ro, len, 16)
        return ConnPart(name, listOf(outer), prof = "PD40*10", minus = listOf(stCyl(p.add(ax, -1.0), ax, ri, len + 2, 12)))
    }
    /** JavaScript String(v) of a q number ('PL' + q.gt). */
    private fun jsStr(e: JsonElement?): String { val p = e as? JsonPrimitive ?: return "undefined"; return if (p.isString) p.content else JsNumber.format(com.solidsketchapp.steel.geom.StBuild.jsNum(p)) }
}
