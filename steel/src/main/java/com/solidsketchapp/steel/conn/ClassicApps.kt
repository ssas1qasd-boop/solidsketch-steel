package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * S10: v69's connection types of the applications chapters, see [Classic]:
 *  - v42 gabletop (gable column cap plate, stConnGeom11 L15252–15266)
 *  - v43 craneseat (crane beam seat on a bracket) and railclip (rail clips + end stops), stConnGeom12 L15326–15351
 *  - v44 stairtreads (treads, tread supports, bolts, foot plates) and weldonly (a weld, no parts), stConnGeom13 L15376–15397
 * Each runs inside the v58 wrapper (L15991, [Fam.geom]). U = 1 (mm).
 */
object ClassicApps {
    val GEN: Map<String, (FamCtx) -> Unit> = mapOf("gabletop" to ::gabletop, "craneseat" to ::craneseat, "railclip" to ::railclip, "stairtreads" to ::stairtreads, "weldonly" to ::weldonly)

    private val Z = D3(0.0, 0.0, 1.0)

    /** JavaScript `'PL' + q.k`: the value as v69 concatenates it (a string as typed, a number in JS form). */
    private fun js(x: FamCtx, k: String): String { val p = x.q[k] as? JsonPrimitive ?: return "undefined"; return if (p.isString) p.content else JsNumber.format(StBuild.jsNum(p)) }
    /** `out.parts.push({name: 'BOLTS' …, bolts: {pts, ax, d, n, grade, L}})` with an explicit name / d / grade. */
    private fun bolts(x: FamCtx, name: String, b: BoltGroup, nb: Int, d: Double, grade: String, len: Double) {
        val info = linkedMapOf<String, JsonElement>("pts" to JsonArray(b.pts.map { jsArr(it.x, it.y, it.z) }), "ax" to jsArr(b.ax.x, b.ax.y, b.ax.z),
            "d" to jsNum(d), "n" to jsNum(nb.toDouble()), "grade" to JsonPrimitive(grade), "L" to jsNum(floor(len + 0.5)))
        x.parts += ConnPart(name, b.pieces, JsonObject(info), group = b)
    }
    /** `out.welds.push({name, a, len})` as v69 writes it (no default for a). */
    private fun weld(x: FamCtx, name: String, a: Double, len: Double) { x.welds += Weld(name, a, len) }

    /** v42 L15252: a cap plate under the rafter's bottom flange on the gable column top, 4 bolts through the flange. */
    fun gabletop(x: FamCtx) {
        val r0 = x.a; val k = x.b ?: run { x.nullResult = true; return }; val kId = x.bId!!
        val r = Joints.mf(r0, x.profiles); val m = Joints.mf(k, x.profiles)
        val tt = if (m.c1.z >= m.c2.z) m.c1 else m.c2; val s = (tt - r.c1) dot r.a; val p = r.c1.add(r.a, s)
        val eh = if (r.eh.z >= 0) r.eh else -r.eh; val bf = p.add(eh, -r.h / 2); val ax = r.a; val ay = (eh cross ax).normalize()
        val tp = x.n("tp")
        val hx = (abs(m.es dot ax) * m.b + abs(m.eh dot ax) * m.h) / 2; val hy = (abs(m.es dot ay) * m.b + abs(m.eh dot ay) * m.h) / 2
        val ex = hx + x.n("ext"); val ey = max(hy + x.n("side"), r.b / 2)
        val pl = Joints.box3(bf, ax, ay, -ex, ex, -ey, ey, -tp, 0.0)   // ax × ay = eh: the plate hangs under the rafter's bottom flange
        x.cut(kId, Joints.box3(bf.add(eh, -tp), ax, ay, -2000.0, 2000.0, -2000.0, 2000.0, 0.0, 5000.0))
        val gx = hx + (ex - hx) / 2; val gy = min(r.b / 2 - 25, max(35.0, hy - 40))
        val bl = ArrayList<D3>(); for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) bl += bf.add(eh, -tp - 0.1).add(ax, sx * gx).add(ay, sy * gy)
        val grip = tp + r.tf + 2; val b = x.group(bl, eh, x.n("bolt"), grip); for (hc in b.holes) x.cut(x.aId, hc)
        x.plate("CAP PLATE", pl, tp, "PL" + js(x, "tp"), minus = b.holes)
        bolts(x, "BOLTS", b, 4, x.n("bolt"), x.s("grade"), tp + r.tf + 2 * x.n("bolt"))
        val paint = x.profiles.lookup(k.str("prof") ?: "")?.P?.takeIf { it != 0.0 && !it.isNaN() } ?: 1.0   // (M.p.P || 1)
        weld(x, "Gable column to cap plate", x.n("a"), paint * 1000)
    }

    /** v43 L15330: the crane beam end bears on the bracket: bearing plate, 4 bolts, bearing stiffeners. */
    fun craneseat(x: FamCtx) {
        val bS = x.b ?: run { x.nullResult = true; return }; val bId = x.bId!!
        val ma = Joints.mf(x.a, x.profiles); val mb = Joints.mf(bS, x.profiles)
        val ztop = max(ma.c1.z, ma.c2.z) + ma.h / 2; val e1 = mb.c1; val e2 = mb.c2; val brC = ma.c1.lerp(ma.c2, 0.5)
        val nearIs1 = e1.distanceTo(brC) <= e2.distanceTo(brC); val near = if (nearIs1) e1 else e2; val ain = if (nearIs1) mb.a else -mb.a
        val side = (Z cross mb.a).normalize()
        val zb = near.z - mb.h / 2; val gap = zb - ztop; val o = D3(near.x, near.y, ztop); val bw = mb.b / 2 + 20
        val plL = min(ma.b - 10, 220.0); val x0 = -10.0; val x1 = plL - 10
        val plates = ArrayList<Pair<Mesh, Double>>()
        if (gap > 0.5) plates += Joints.box3(o, ain, side, x0, x1, -bw, bw, 0.0, gap) to gap
        val bl = ArrayList<D3>(); for (xx in listOf(40.0, 120.0).filter { it < x1 - 25 }) for (y in listOf(-x.n("gx") - mb.tw / 2, x.n("gx") + mb.tw / 2)) bl += o.add(ain, xx).add(side, y).add(Z, -ma.tf - 0.1)
        val grip = ma.tf + max(0.0, gap) + mb.tf + 2; val bb = x.group(bl, Z, x.n("bolt"), grip)
        for (hc in bb.holes) { x.cut(x.aId, hc); x.cut(bId, hc) }
        for ((m, g) in plates) x.plate("BEARING PLATE", m, g, "PL" + JsNumber.format(floor(g + 0.5)), minus = bb.holes)   // 'PL' + Math.round(gap / U)
        bolts(x, "BOLTS", bb, bl.size, x.n("bolt"), x.s("grade"), grip + 2 * x.n("bolt"))
        val hw = mb.h / 2 - mb.tf; val ww = mb.b / 2 - 5; val fs = D3(near.x, near.y, near.z).add(ain, 80.0); val ts = x.n("ts")
        for (sg in listOf(1, -1)) x.plate("BEARING STIFFENER", Joints.box3(fs, side, Z, if (sg > 0) mb.tw / 2 else -ww, if (sg > 0) ww else -mb.tw / 2, -hw, hw, -ts / 2, ts / 2), ts, "PL" + js(x, "ts"))
        weld(x, "Bearing stiffeners", 6.0, 4 * 2 * hw)
    }

    /** v43 L15345: rail clips every s along the rail, both sides, one bolt each through the crane beam's top flange; end stops at runway ends. */
    fun railclip(x: FamCtx) {
        val bS = x.b ?: run { x.nullResult = true; return }
        val ma = Joints.mf(x.a, x.profiles); val mb = Joints.mf(bS, x.profiles)
        val l = ma.c2.distanceTo(ma.c1); val side = (Z cross ma.a).normalize(); val ztop = ma.c1.z + ma.h / 2; val rw = mb.b / 2; val t = x.n("t")
        val w = x.n("w"); val ll = x.n("l"); val step = x.n("s")
        val clips = ArrayList<Mesh>(); val pts = ArrayList<D3>()
        var s = 150.0
        while (s <= l - 150 + 1e-6) {
            for (sg in listOf(-1, 1)) { val p = ma.c1.add(ma.a, s); val o = D3(p.x, p.y, ztop)
                clips += Joints.box3(o, ma.a, side, -w / 2, w / 2, if (sg > 0) rw else -rw - ll, if (sg > 0) rw + ll else -rw, 0.0, t)
                pts += o.add(side, sg * (rw + ll * 0.55)).add(Z, t + 0.1) }
            s += step
        }
        val grip = t + ma.tf + 2; val bb = x.group(pts, D3(0.0, 0.0, -1.0), x.n("bolt"), grip)
        for (hc in bb.holes) x.cut(x.aId, hc)
        // v69 unions the clips and subtracts the holes; with no clip (a rail shorter than one step) that throws, and stRegen
        // then drops the whole connection's geometry (L13981: toast, g = null)
        if (clips.isEmpty()) { x.nullResult = true; return }
        x.parts += ConnPart("RAIL CLIP", clips, prof = "PL" + js(x, "t"), minus = bb.holes)
        bolts(x, "BOLTS", bb, pts.size, x.n("bolt"), x.s("grade"), grip + 2 * x.n("bolt"))
        if (x.t("stop")) {
            // c.ends (set by the portal's crane runway, L15370): which runway end gets a stop; undefined = both
            val ends = (x.conn?.raw?.get("ends") as? JsonArray)?.map { (it as? JsonPrimitive)?.let { p -> StBuild.jsNum(p) } }
            for ((p, dir, e) in listOf(Triple(ma.c1, ma.a, 1.0), Triple(ma.c2, -ma.a, 2.0))) {
                if (ends == null || e in ends) { val o = D3(p.x, p.y, ztop)
                    x.plate("END STOP", Joints.box3(o, dir, side, 10.0, 40.0, -ma.b / 2, ma.b / 2, 0.0, 220.0), 30.0, "PL30"); weld(x, "End stop", 8.0, 2 * ma.b) }
            }
        }
    }

    /** v44 L15377: treads between two stringers on angle supports bolted to the stringers' webs, foot plates with anchors. */
    fun stairtreads(x: FamCtx) {
        if (x.s("tmode") == "tekla") return stairs70(x)   // v70 C18
        val bS = x.b ?: run { x.nullResult = true; return }; val bId = x.bId!!
        val m1 = Joints.mf(x.a, x.profiles); val m2 = Joints.mf(bS, x.profiles)
        val lo1 = if (m1.c1.z <= m1.c2.z) m1.c1 else m1.c2; val hi1 = if (lo1 === m1.c1) m1.c2 else m1.c1; val lo2 = if (m2.c1.z <= m2.c2.z) m2.c1 else m2.c2
        val p0 = lo1.lerp(lo2, 0.5); val hH = hi1.z - lo1.z; val dh0 = (hi1 - lo1).let { D3(it.x, it.y, 0.0) }; val run = dh0.length(); val dh = dh0.normalize()
        val lat0 = (lo2 - lo1).let { D3(it.x, it.y, 0.0) }; val wW = lat0.length(); val lat = lat0.normalize()
        if (hH <= 0 || run <= 0) { x.nullResult = true; return }
        fun half(m: MemberFrame) = (abs(lat dot m.es) * m.b + abs(lat dot m.eh) * m.h) / 2
        val w1 = half(m1); val w2 = half(m2); val inner0 = -wW / 2 + w1; val inner1 = wW / 2 - w2
        val n = max(2, ceil(hH / x.n("riserMax")).toInt()); val r = hH / n; val g = run / n
        val t = x.n("tread"); val leg = x.n("leg"); val ta = x.n("ta"); val sl = x.n("sl"); val nosing = x.n("nosing")
        val treads = ArrayList<Mesh>(); val sups = ArrayList<Triple<Mesh, Mesh, Int>>(); val bl1 = ArrayList<D3>(); val bl2 = ArrayList<D3>()
        for (i in 1 until n) {
            val s0 = i * g - nosing; val s1 = (i + 1) * g; val zt = p0.z + i * r; val o = D3(p0.x, p0.y, zt).add(dh, 0.0)
            treads += Joints.box3(o, dh, lat, s0, s1, inner0 + 3, inner1 - 3, -t, 0.0)
            for ((y0, sg, bl) in listOf(Triple(inner0, 1, bl1), Triple(inner1, -1, bl2))) {
                val sa = if (sl > 0) (s0 + s1) / 2 - sl / 2 else s0 + 10; val sb = if (sl > 0) (s0 + s1) / 2 + sl / 2 else s1 - 10
                val ot = o.add(Z, -t)
                sups += Triple(Joints.box3(ot, dh, lat, sa, sb, if (sg > 0) y0 else y0 - ta, if (sg > 0) y0 + ta else y0, -leg, 0.0),
                    Joints.box3(ot, dh, lat, sa, sb, if (sg > 0) y0 else y0 - leg, if (sg > 0) y0 + leg else y0, -ta, 0.0), sg)
                val sm = (s0 + s1) / 2; bl += o.add(dh, sm).add(lat, y0 + sg * (ta + 0.1)).add(Z, -t - leg * 0.55)
            }
        }
        val grip1 = ta + m1.tw + 2   // (M1.tw || 8): stMF's tw is already p.tw || p.t || 8
        val ba = x.group(bl1, -lat, x.n("bolt"), grip1); val bb = x.group(bl2, lat, x.n("bolt"), grip1)
        for (hc in ba.holes) x.cut(x.aId, hc); for (hc in bb.holes) x.cut(bId, hc)
        for (tm in treads) x.plate("TREAD", tm, t, "PL" + js(x, "tread"))
        val lProf = "L" + js(x, "leg") + "*" + js(x, "ta")
        for ((a1, a2, sg) in sups) x.parts += ConnPart("TREAD SUPPORT", listOf(a1, a2), prof = lProf, minus = if (sg > 0) ba.holes else bb.holes)
        bolts(x, "BOLTS", ba, bl1.size, x.n("bolt"), x.s("grade"), grip1 + 2 * x.n("bolt"))
        bolts(x, "BOLTS", bb, bl2.size, x.n("bolt"), x.s("grade"), grip1 + 2 * x.n("bolt"))
        if (x.t("foot")) {
            val tf = x.n("tf")
            for ((m, lo, id) in listOf(Triple(m1, lo1, x.aId), Triple(m2, lo2, bId))) {
                val of = D3(lo.x, lo.y, lo.z); val l2 = m.h / 2 / sin(atan2(hH, run)) + 60
                x.cut(id, Joints.box3(of, dh, lat, -5000.0, 5000.0, -500.0, 500.0, -5000.0, tf))
                val fp = Joints.box3(of, dh, lat, -l2, l2, -(m.b / 2 + 40), m.b / 2 + 40, 0.0, tf)
                val ab = listOf(-1, 1).map { sx -> of.add(dh, sx * (l2 - 35)).add(Z, tf + 15) }
                val bf = x.group(ab, D3(0.0, 0.0, -1.0), 16.0, tf + 150)
                x.plate("FOOT PLATE", fp, tf, "PL" + js(x, "tf"), minus = bf.holes)
                bolts(x, "ANCHOR BOLTS", bf, 2, 16.0, "4.6", tf + 182)
            }
        }
        weld(x, "Treads to supports", 3.0, 2.0 * (n - 1) * g)
        x.connSet["nSteps"] = jsNum(n.toDouble())   // c.nSteps = n (L15397)
    }

    // ---------- v70 C18 (web v70 section "C18"): R12's treads, brackets and bolts, and the stringers' mitre fit ----------
    private fun n70(x: FamCtx, k: String, d: Double) = StBolts.tkNum(x.q[k], d)
    /** The bent tread's section (u along the going, v up from the legs' bottom), CCW (web st70Tread). */
    private fun treadSec(tt: Double, ri: Double, th: Double, tw: Double): List<DoubleArray> {
        val r = ri + tt; val a = listOf(0.0, 30.0, 60.0, 90.0).map { it * PI / 180 }; val p = ArrayList<DoubleArray>(); p += doubleArrayOf(0.0, 0.0); p += doubleArrayOf(tt, 0.0)
        for (t in a) p += doubleArrayOf(r - ri * cos(t), th - r + ri * sin(t))
        for (t in a.reversed()) p += doubleArrayOf(tw - r + ri * cos(t), th - r + ri * sin(t))
        p += doubleArrayOf(tw - tt, 0.0); p += doubleArrayOf(tw, 0.0); for (t in a) p += doubleArrayOf(tw - r + r * cos(t), th - r + r * sin(t))
        for (t in a.reversed()) p += doubleArrayOf(r - r * cos(t), th - r + r * sin(t))
        val out = p.filterIndexed { i, q -> i == 0 || kotlin.math.hypot(q[0] - p[i - 1][0], q[1] - p[i - 1][1]) > 1e-9 }
        return if (Prism.area(out) < 0) out.reversed() else out
    }
    private fun stairs70(x: FamCtx) {
        val bS = x.b ?: run { x.nullResult = true; return }; val bId = x.bId!!
        val m1 = Joints.mf(x.a, x.profiles); val m2 = Joints.mf(bS, x.profiles)
        val lo1 = if (m1.c1.z <= m1.c2.z) m1.c1 else m1.c2; val hi1 = if (lo1 === m1.c1) m1.c2 else m1.c1; val lo2 = if (m2.c1.z <= m2.c2.z) m2.c1 else m2.c2
        val hH = hi1.z - lo1.z; val dh0 = (hi1 - lo1).let { D3(it.x, it.y, 0.0) }; val run = dh0.length(); val dh = dh0.normalize(); if (hH <= 0 || run <= 0) { x.nullResult = true; return }
        val lat0 = (lo2 - lo1).let { D3(it.x, it.y, 0.0) }; val latV = lat0.add(dh, -(lat0 dot dh)); val wW = latV.length(); val lat = latV.normalize()
        fun half(m: MemberFrame) = (abs(lat dot m.es) * m.b + abs(lat dot m.eh) * m.h) / 2
        val in0 = half(m1); val in1 = wW - half(m2)
        val n = max(1, jsInt(n70(x, "ntr", 12.0))); val rise = n70(x, "rise", 156.667); val go = rise * run / hH
        val tt = n70(x, "tt", 10.0); val th = n70(x, "th", 40.0); val tw = n70(x, "tw", 300.0); val gap = n70(x, "tgap", 5.0); val ri = n70(x, "tri", 5.0)
        val sec = treadSec(tt, ri, th, tw); val dev = 2 * (th - tt - ri) + (tw - 2 * (tt + ri)) + PI * ri
        val tprof = "PL" + js(x, "tt") + "*" + JsNumber.format(Math.round(dev * 10) / 10.0)
        val bl = n70(x, "bl", 140.0); val leg = n70(x, "bleg", 60.0); val ta = n70(x, "bta", 6.0); val d = n70(x, "bbolt", 12.0).takeIf { it != 0.0 } ?: 12.0
        val lsec = listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(ta, 0.0), doubleArrayOf(ta, leg - ta), doubleArrayOf(leg, leg - ta), doubleArrayOf(leg, leg), doubleArrayOf(0.0, leg))
        val brs = ArrayList<Pair<BoltGroup, Double>>(); val mat = x.s("tmat").ifEmpty { "Grating" }
        for (k in 0 until n) { val s = n70(x, "s0", 0.0) + k * go; val zt = lo1.z + s * hH / run + n70(x, "dz", 0.0); val o0 = lo1.add(dh, s); val o = D3(o0.x, o0.y, zt)
            val ob = o.add(dh, -tw / 2).add(Z, -th).add(lat, in0 + gap); val tl = in1 - in0 - 2 * gap; val rh = ((dh cross Z) dot lat) > 0
            x.parts += ConnPart("TREAD", listOf(Prism.extrude(Region(sec), tl, Prism.Frame(if (rh) ob else ob.add(lat, tl), dh, Z, if (rh) lat else -lat))), prof = tprof, mat = mat)
            for ((y0, sg, mm) in listOf(Triple(in0, 1.0, m1), Triple(in1, -1.0, m2))) {
                val ul = lat * sg; val obr = o.add(lat, y0).add(Z, -tt - leg).add(dh, -bl / 2); val nn = ul cross Z; val fwd = (nn dot dh) > 0
                val m = Prism.extrude(Region(lsec), bl, Prism.Frame(if (fwd) obr else obr.add(dh, bl), ul, Z, if (fwd) dh else -dh))
                val pts = listOf(-1.0, 1.0).map { e -> o.add(dh, e * n70(x, "bp", 90.0) / 2 + n70(x, "bo", 0.0)).add(Z, -tt - n70(x, "bz", 30.0)).add(lat, y0 - sg * mm.tw) }
                val grip = mm.tw + ta; val bg = x.group(pts, ul, d, grip); for (hc in bg.holes) x.cut(if (sg > 0) x.aId else bId, hc)
                x.parts += ConnPart("BRACKET", listOf(m), prof = "L" + js(x, "bleg") + "*" + js(x, "bta"), minus = bg.holes, len = bl); brs += bg to grip } }
        for ((bg, grip) in brs) bolts(x, "BOLTS", bg, 2, d, x.s("bgrade").ifEmpty { "8.8" }, if (x.s("blrule") == "tekla") Fam.tkBoltLen(d, grip) else grip + 2 * d)
        // the stringers' mitre fit to the pieces they meet (parts[2..5])
        val ps = x.conn?.parts ?: emptyList()
        for ((sid, mm, lo, ix) in listOf(Quad(x.aId, m1, lo1, 2), Quad(bId, m2, lo2, 4))) for ((j, endP) in listOf(0 to lo, 1 to (if (lo === mm.c1) mm.c2 else mm.c1))) {
            val pid = ps.getOrNull(ix + j) ?: continue; val pst = x.stOf(pid) ?: continue; val mp = Joints.mf(pst, x.profiles)
            val u = if (endP === mm.c1) (mm.c1 - mm.c2).normalize() else (mm.c2 - mm.c1).normalize(); val w0 = (mp.c2 - mp.c1).normalize()
            val r = endP - mp.c1; val a = u dot u; val b = u dot w0; val cc = w0 dot w0; val d0 = u dot r; val e0 = w0 dot r; val den = a * cc - b * b; if (abs(den) < 1e-9) continue
            val x0 = endP.add(u, (b * e0 - cc * d0) / den); val far = if (mp.c1.distanceTo(x0) > mp.c2.distanceTo(x0)) mp.c1 else mp.c2; val w = (far - x0).normalize()
            val mdir = (u + w).normalize(); val big = 20000.0; val xa = ((if (abs(mdir.z) < 0.9) Z else D3(1.0, 0.0, 0.0)) cross mdir).normalize(); val ya = mdir cross xa
            x.cut(sid, Joints.box3(x0, xa, ya, -big, big, -big, big, 0.0, big)); x.cut(pid, Joints.box3(x0, xa, ya, -big, big, -big, big, -big, 0.0)) }   // both pieces on the one mitre plane (R12)
        x.connSet["nSteps"] = jsNum(n.toDouble())
    }
    private data class Quad(val a: String, val b: MemberFrame, val c: D3, val d: Int)

    /** v44 L15376: a weld with no parts; the secondary is cut with the main part's envelope (stEnvelope, v40 L14883). */
    fun weldonly(x: FamCtx) {
        // (c.q && c.q.a) || 4: c.q itself, not the def
        val cq = x.conn?.q ?: x.q
        // v70 C19 (web/v70/c19.js): q.fit 'face' = Tekla's fit of the secondary's end on the main part's face (a plane), 'none' = no cut;
        // anything else = v69 (the main part's envelope)
        val fit = (cq["fit"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val bId = x.bId
        if (bId != null && x.b != null) when (fit) {
            "face" -> runCatching { fitFace19(x) }.getOrNull()?.let { x.cut(bId, it) }
            "none" -> {}
            else -> runCatching { FamWF.envelope(x, x.a) }.getOrNull()?.let { x.cut(bId, it) }
        }
        val a = cq["a"].let { if (jsTruthy(it)) StBuild.jsNum(it!!) else 4.0 }
        weld(x, "Fillet weld all round", a, 150.0)
    }

    /** v70 C19 fitFace19: the half-space beyond the main part's face (v69 stFaceAt) at the secondary's end nearest the main part. */
    private fun fitFace19(x: FamCtx): com.solidsketchapp.core.api.Mesh? {
        val ma = Joints.mf(x.a, x.profiles); val mb = Joints.mf(x.b!!, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val near = dl(mb.c1) <= dl(mb.c2); val e = if (near) mb.c1 else mb.c2; val far = if (near) mb.c2 else mb.c1
        val f = FamCL.faceAt(ma, e.add((far - e).normalize(), 1e-3)); val n = f.n.normalize()
        if (((far - e) dot n) <= 0) return null
        val q = f.pc.add(n, f.off); val o = e.add(n, -((e - q) dot n))
        val xx = n.cross(if (abs(n.z) < 0.9) D3.Z else D3(1.0, 0.0, 0.0)).normalize(); val yy = xx.cross(n).normalize()
        val w = 2 * maxOf(mb.h, mb.b) + 100; val d = ma.h + ma.b + 100
        return Joints.box3(o, xx, yy, -w, w, -w, w, 0.0, d)
    }
}
