package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.num
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * GU family: gusset bracing (bolted 11 / 196 / 169 / 110 / 62, bent 12d / 140, welded 10, tube 20 / 67 / 105, corner 56 / 57 / 58 /
 * 59 / 60w / 63 / 165, direct 5, cross 19 / 22 / 61, central 18, rod 1w / 16, fork 7t, strips 52, squeeze 102t / 103t, tee 1050 / gt,
 * angle 1051). Live chain: v69 FAM.GU (L15067, FAM_GU14) wrapped by L15429 (squeeze / fork / rod / strips, with stBraceFrame L15420).
 */
object FamGU {
    const val PORTED = true
    private const val BIG = 60000.0   // v69 v49Big

    /** v69 stFaceAt (L14891) / stFaceToward (L15536) with the face's plate thickness t. */
    private class Face(val n: D3, val off: Double, val flange: Boolean, val t: Double, val pc: D3)
    private fun faceAt(m: MemberFrame, p0: D3): Face = FamCL.faceAt(m, p0).let { Face(it.n, it.off, it.flange, if (it.flange) m.tf else m.tw, it.pc) }
    private fun faceToward(m: MemberFrame, p0: D3, d: D3): Face {
        val al = (p0 - m.c1) dot m.a; val pc = m.c1.add(m.a, al); var u = d.add(m.a, -(d dot m.a)); if (u.length() < 1e-6) return faceAt(m, p0); u = u.normalize()
        val w = p0 - pc; val y0 = w dot m.eh; val z0 = w dot m.es; val uy = u dot m.eh; val uz = u dot m.es; val tube = m.kind == "O" || m.kind == "R"
        val fl = abs(uy) >= abs(uz)
        fun sg(a: Double, b: Double) = if (a != 0.0) Math.signum(a) else FamCL.sgn1(b)
        val s = if (fl) sg(uy, y0) else sg(uz, z0)
        return Face(if (fl) m.eh * s else m.es * s, if (fl) m.h / 2 else (if (tube) m.b / 2 else m.tw / 2), fl, if (fl) m.tf else m.tw, pc)
    }

    /** v69 stEnvelope (L14883, grow 0): the profile's outline (no hollows) along the part. */
    private fun envelope(x: FamCtx, st: JsonObject): Mesh? {
        val sec = Sections.of(x.profiles.lookup(st.str("prof") ?: "")) ?: return null; val m = Joints.mf(st, x.profiles)
        val l = m.c2.distanceTo(m.c1) + st.num("dx1") + st.num("dx2")
        return Prism.extrude(Region(sec.outer), l, Prism.Frame(m.c1.add(m.a, -st.num("dx1")), m.es, m.eh, m.a))
    }

    /** v69 hull (2D monotone chain, as stHull2). */
    private fun hull(p: List<DoubleArray>): List<DoubleArray> {
        val s = p.sortedWith { a, b -> val d = a[0] - b[0]; if (d != 0.0) d.compareTo(0.0) else (a[1] - b[1]).compareTo(0.0) }
        fun cr(o: DoubleArray, a: DoubleArray, b: DoubleArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        val lo = ArrayList<DoubleArray>(); val up = ArrayList<DoubleArray>()
        for (q in s) { while (lo.size >= 2 && cr(lo[lo.size - 2], lo[lo.size - 1], q) <= 0) lo.removeAt(lo.size - 1); lo += q }
        for (q in s.reversed()) { while (up.size >= 2 && cr(up[up.size - 2], up[up.size - 1], q) <= 0) up.removeAt(up.size - 1); up += q }
        return lo.dropLast(1) + up.dropLast(1)
    }

    /** v69 stCyl's / stBox3's vertices (exact doubles), for [hull3]. */
    private fun cylPts(p: D3, axis: D3, r: Double, len: Double, seg: Int): List<D3> {
        val n = axis.normalize(); val t = if (abs(n.z) < 0.9) D3.Z else D3(1.0, 0.0, 0.0); val u = (t cross n).normalize(); val v = n cross u
        return (0 until seg).flatMap { i -> val a = (i + 0.5) * 2 * PI / seg; val q = p.add(u, r * kotlin.math.cos(a)).add(v, r * kotlin.math.sin(a)); listOf(q, q.add(n, len)) }
    }
    private fun boxPts(o: D3, x: D3, y: D3, x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double): List<D3> {
        val n = (x cross y).normalize(); return listOf(x0, x1).flatMap { a -> listOf(y0, y1).flatMap { b -> listOf(z0, z1).map { c -> o.add(x, a).add(y, b).add(n, c) } } }
    }
    /** Manifold hull(): the convex hull of a few dozen points (brute-force facets, coplanar points merged into one polygon per face). */
    private fun hull3(p0: List<D3>): Mesh {
        val c = p0.fold(D3.ZERO) { a, p -> a + p } * (1.0 / p0.size); val pts = ArrayList<D3>()
        for (q in p0) { val p = q - c; if (pts.none { it.distanceTo(p) < 1e-6 }) pts += p }
        val eps = 1e-6; val planes = ArrayList<Pair<D3, Double>>(); val n = pts.size
        for (i in 0 until n) for (j in i + 1 until n) for (k in j + 1 until n) {
            var nn = (pts[j] - pts[i]) cross (pts[k] - pts[i]); if (nn.length() < 1e-6) continue; nn = nn.normalize()
            var above = false; var below = false
            for (p in pts) { val s = (p - pts[i]) dot nn; if (s > eps) above = true else if (s < -eps) below = true; if (above && below) break }
            if (above && below) continue; if (above) nn = -nn
            val dd = nn dot pts[i]; if (planes.none { (it.first dot nn) > 1 - 1e-9 && abs(it.second - dd) < 1e-5 }) planes += nn to dd
        }
        val idx = ArrayList<Int>()
        for ((nn, dd) in planes) {
            val on = pts.indices.filter { abs((pts[it] dot nn) - dd) < 1e-5 }; if (on.size < 3) continue
            val u = (if (abs(nn.x) < 0.9) D3(1.0, 0.0, 0.0) else D3(0.0, 1.0, 0.0)).let { (nn cross it) cross nn }.normalize(); val v = nn cross u
            val h = hull(on.map { doubleArrayOf(pts[it] dot u, pts[it] dot v, it.toDouble()) }).map { it[2].toInt() }
            for (t in 1 until h.size - 1) { idx += h[0]; idx += h[t]; idx += h[t + 1] }
        }
        val pos = FloatArray(pts.size * 3); pts.forEachIndexed { i, p -> val q = p + c; pos[3 * i] = q.x.toFloat(); pos[3 * i + 1] = q.y.toFloat(); pos[3 * i + 2] = q.z.toFloat() }
        return Mesh(pos, idx.toIntArray())
    }

    private fun half(m: MemberFrame, dir: D3) = (abs(dir dot m.eh) * m.h + abs(dir dot m.es) * m.b) / 2
    private fun d0Of(d: Double) = d + (if (d <= 24) 2 else 3)
    private fun round(v: Double) = floor(v + 0.5)
    /** `q.k || d` for a number. */
    private fun or(x: FamCtx, k: String, d: Double) = if (x.t(k)) x.n(k) else d
    private fun ctx(x: FamCtx, q: JsonObject) = FamCtx(q, x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt, x.conn, x.conns, x.stOf)

    /** v69 FAM.GU (L15429): squeeze / fork / rod / strips on the general gusset built as 'welded'; the others go to [gu14]. */
    fun gen(x: FamCtx) {
        val kind = x.s("kind"); if (kind !in listOf("squeeze", "fork", "rod", "strips")) return gu14(x)
        val brId = x.b2Id ?: x.bId ?: throw IllegalStateException("not built"); val br = x.b2 ?: x.b!!
        val nb = max(1, jsInt(x.n("nb"))); val l0 = 2 * x.n("e") + (nb - 1) * x.n("pb"); val lap = or(x, "lap", l0)
        val extra = if (kind == "fork") max(0.0, 100 - l0) else 0.0
        val q2 = JsonObject(LinkedHashMap<String, JsonElement>(x.q).apply { put("kind", JsonPrimitive("welded")); put("lap", jsNum(or(x, "lap", l0) + extra)) })
        val y = ctx(x, q2); gu14(y)
        x.parts += y.parts; for ((id, l) in y.cuts) x.cuts.getOrPut(id) { ArrayList() } += l; x.groups += y.groups; x.welds += y.welds.filter { it.name != "Brace to gusset" }
        // stBraceFrame (L15420)
        val mb = Joints.mf(br, x.profiles); val ma = Joints.mf(x.a, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val near = dl(mb.c1) <= dl(mb.c2); val e = if (near) mb.c1 else mb.c2; var d = ((if (near) mb.c2 else mb.c1) - e).normalize()
        val fa = faceAt(ma, e); val nF = fa.n; val f0 = fa.pc.add(nF, fa.off); if ((d dot nF) < 0.05) d = d.add(nF, 0.06).normalize()
        val w = e.add(d, ((f0 - e) dot nF) / ((d dot nF).takeIf { it != 0.0 } ?: 1.0))
        var g = d cross nF; if (g.length() < 1e-6) g = ma.a; g = g.normalize(); val pIn = (g cross d).normalize()
        val w2 = half(mb, pIn); val sinT = max(0.15, abs(d dot nF)); val cosT = sqrt(max(0.0, 1 - sinT * sinT)); val te = (w2 * cosT + x.n("setback")) / sinT
        val t = x.n("t"); val dD = max(mb.h, mb.b)
        fun at(s: Double, p: Double, gg: Double) = w.add(d, s).add(pIn, p).add(g, gg)
        fun brEnd(s: Double) = x.cut(brId, Joints.box3(w, d, pIn, -BIG, s, -5000.0, 5000.0, -5000.0, 5000.0))
        fun gussets(hs: List<Mesh>) { for (i in x.parts.indices) { val p = x.parts[i]; if ("GUSSET" in p.name) x.parts[i] = ConnPart(p.name, p.pieces, p.bolts, p.prof, p.minus + hs, p.group) } }
        val prof = x.profiles.lookup(br.str("prof") ?: "")
        when (kind) {
            "squeeze" -> {   // flat end on the gusset (bolted), then the transition back to the round tube
                val tt = 2 * (prof?.t?.takeIf { it != 0.0 && !it.isNaN() } ?: 4.0); val wf = min(PI * dD / 2, 1.6 * dD); val ltr = 1.2 * dD; val gf = t / 2 + tt / 2
                val s1 = te + lap; val s2 = s1 + ltr; brEnd(s2)
                val disc = cylPts(at(s2 - 1, 0.0, 0.0), d, dD / 2, 1.0, 24); val flat0 = boxPts(at(s1 - 1, 0.0, gf), d, pIn, 0.0, 1.0, -wf / 2, wf / 2, -tt / 2, tt / 2)
                val sq = listOf(hull3(disc + flat0), Joints.box3(at(te, 0.0, gf), d, pIn, 0.0, lap, -wf / 2, wf / 2, -tt / 2, tt / 2))
                val bl = (0 until nb).map { at(te + x.n("e") + it * x.n("pb"), 0.0, gf + tt / 2 + 0.1) }; val grip = tt + t + 2
                val bb = x.group(bl, -g, x.n("bolt"), grip); gussets(bb.holes)
                x.parts += ConnPart("SQUEEZED END", sq, prof = prof?.key ?: br.str("prof"), minus = bb.holes); x.bolts(bb, nb, grip + 2 * x.n("bolt"))
                x.weld("Squeezed end to tube (shop)", 4.0, PI * dD)
            }
            "fork" -> {   // lugs straddle the gusset (pin through), the fork body further out
                val tl = 10.0; val gap = 1.0; val l = 90.0; val wl = max(70.0, 2.5 * dD); val s1 = te + l; val s2 = s1 + 75; brEnd(s2 - 10)
                val body = stCyl(at(s1, 0.0, 0.0), d, max(dD * 0.9, 18.0), 75.0, 16)
                val pd = max(16.0, round(dD * 0.8)); val bb = x.group(listOf(at(te + 35, 0.0, -(t / 2 + gap + tl) - 0.1)), g, pd, t + 2 * (gap + tl) + 2); gussets(bb.holes)
                // lugs.subtract(holes).add(body): the pin holes run along g, square to the lugs, so they are cut into the lugs' outline (prism holes);
                // the union with the body first and one subtraction after gives a wrong Manifold result (the body touches the crossbar)
                val lugs = listOf(-1, 1).map { s -> val o = at(te, 0.0, s * (t / 2 + gap + tl / 2) - tl / 2)
                    val hs = bb.holes.map { h -> hull((0 until h.vertexCount).map { i -> val v = D3(h.positions[3 * i].toDouble(), h.positions[3 * i + 1].toDouble(), h.positions[3 * i + 2].toDouble()) - o; doubleArrayOf(v dot d, v dot pIn) }.distinctBy { Math.round(it[0] * 100) to Math.round(it[1] * 100) }) }
                    Prism.extrude(Region(listOf(doubleArrayOf(0.0, -wl / 2), doubleArrayOf(l, -wl / 2), doubleArrayOf(l, wl / 2), doubleArrayOf(0.0, wl / 2)), hs), tl, Prism.Frame(o, d, pIn, g)) } +
                    Joints.box3(at(s1 - 12, 0.0, 0.0), d, pIn, 0.0, 12.0, -wl / 2, wl / 2, -(t / 2 + gap + tl), t / 2 + gap + tl)
                x.parts += ConnPart("FORK", lugs + body, prof = "FORK M" + JsNumber.format(round(dD)))
                x.bolts(bb, 1, t + 2 * (gap + tl) + 30, pd, "8.8"); x.weld("Fork to rod", 6.0, PI * dD)
            }
            "rod" -> {   // bearing plate square to the rod, welded to the gusset; washer + two nuts behind
                val tp = 15.0; val s = 120.0; val sb = te + lap * 0.6
                val bp = Joints.box3(at(sb, 0.0, 0.0), d, pIn, 0.0, tp, -s / 2, s / 2, -s / 2, s / 2); val hole = stCyl(at(sb - 5, 0.0, 0.0), d, dD / 2 + 2, tp + 10, 16)
                gussets(listOf(Joints.box3(at(sb - 5, 0.0, 0.0), d, pIn, 0.0, 1000.0, -dD, dD, -5000.0, 5000.0)))
                brEnd(sb - 4 - 1.6 * dD - 25); x.plate("BEARING PLATE", bp, 15.0, minus = listOf(hole))
                val nut = listOf(stCyl(at(sb - 4, 0.0, 0.0), d, 1.1 * dD, 4.0, 24), stCyl(at(sb - 4 - 0.8 * dD, 0.0, 0.0), d, 0.95 * dD, 0.8 * dD, 6), stCyl(at(sb - 4 - 1.6 * dD, 0.0, 0.0), d, 0.95 * dD, 0.8 * dD, 6))
                val p0 = at(sb, 0.0, 0.0)
                // v58 wrapper: no bolt group behind it (G empty), so only std / thr / sw / cmt / blen are added
                val info = linkedMapOf<String, JsonElement>("pts" to JsonArray(listOf(jsArr(p0.x, p0.y, p0.z))), "ax" to jsArr(d.x, d.y, d.z), "d" to jsNum(round(dD)), "n" to jsNum(1.0), "grade" to JsonPrimitive("4.6"), "L" to jsNum(0.0))
                if (x.s("grade").isNotEmpty()) info["std"] = JsonPrimitive(x.s("grade") + "XOX")
                info["thr"] = JsonPrimitive(if ((x.q["thr"] as? JsonPrimitive)?.content == "0") "No" else "Yes"); info["sw"] = JsonPrimitive(x.s("sw").ifEmpty { "Site" })
                if (x.s("bcmt").isNotEmpty()) info["cmt"] = JsonPrimitive(x.s("bcmt")); StBolts.tkNum(x.q["blen"], 0.0).let { if (it != 0.0) info["blen"] = jsNum(it) }
                x.parts += ConnPart("NUTS", nut, JsonObject(info)); x.weld("Bearing plate to gusset", 6.0, 2.0 * 120)
            }
            else -> {   // strips: two flat strips either side of the gusset, welded to the tube end (52)
                val ts = 8.0; val ws = min(60.0, 0.6 * dD); brEnd(te + lap + 30)
                for (s in listOf(-1, 1)) x.plate("STRIP", Joints.box3(at(te, 0.0, s * (t / 2 + ts / 2)), d, pIn, 0.0, lap + 60, -ws / 2, ws / 2, -ts / 2, ts / 2), 8.0)
                x.weld("Strips", 6.0, 4 * lap)
            }
        }
    }

    /** v69 FAM.GU (L15067, FAM_GU14): one general gusset; cross / central bolt where two braces cross; direct bolting. */
    private fun gu14(x: FamCtx) {
        var a2: JsonObject? = null; var brSt = x.b; var brId = x.bId; if (x.b2 != null) { a2 = x.b; brSt = x.b2; brId = x.b2Id }
        if (brSt == null || brId == null) throw IllegalStateException("not built")
        val kind = x.s("kind"); val mb = Joints.mf(brSt, x.profiles); val ma = Joints.mf(x.a, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val near = dl(mb.c1) <= dl(mb.c2); val e0 = if (near) mb.c1 else mb.c2; var d = ((if (near) mb.c2 else mb.c1) - e0).normalize()
        val bolt = x.n("bolt")
        if ((kind == "cross" || kind == "central") && x.b2 == null) {   // two braces crossing: a bolt (or a centre gusset) where they cross
            val a1 = ma.a; val a2v = mb.a; var n = a1 cross a2v; if (n.length() < 1e-6) throw IllegalStateException("not built"); n = n.normalize()
            val w0 = ma.c1 - mb.c1; val b = a1 dot a2v; val dd = a1 dot w0; val ee = a2v dot w0; val den = 1 - b * b
            val pa = ma.c1.add(a1, (b * ee - dd) / den); val pb = mb.c1.add(a2v, (ee - b * dd) / den)
            val sgn = FamCL.sgn1((pb - pa) dot n); val gap = abs((pb - pa) dot n); val ha = half(ma, n); val hb = half(mb, n); val clear = gap - ha - hb
            if (clear > 0.5) { val s = if (kind == "central") 160.0 else 60.0; val f = Joints.Frame3(pa.add(n, sgn * ha), a1, (n cross a1).normalize())
                x.plate(if (kind == "central") "CENTRE GUSSET" else "PACKING", f.box(-s, s, -s, s, 0.0, sgn * clear), clear) }
            val grip = max(gap, 0.0) + ha + hb + 2; val bb = x.group(listOf(pa.add(n, -sgn * (ha + 0.1))), n * sgn, bolt, grip)
            for (hc in bb.holes) { x.cut(x.aId, hc); x.cut(brId, hc) }; x.bolts(bb, 1, grip + 2 * bolt); return
        }
        val fa = faceToward(ma, e0, d); val nF = fa.n; val f0 = fa.pc.add(nF, fa.off); val dn = d dot nF; if (dn < 0.05) d = d.add(nF, 0.06).normalize()
        val w = e0.add(d, ((f0 - e0) dot nF) / ((d dot nF).takeIf { it != 0.0 } ?: 1.0))   // the work point on the face
        var g = d cross nF; if (g.length() < 1e-6) g = ma.a; g = g.normalize(); val uF = (nF cross g).normalize(); val pIn = (g cross d).normalize()
        val kindB = mb.kind; val tube = kindB == "O" || kindB == "R"; val w2 = half(mb, pIn); val tb = half(mb, g); val t = x.n("t")
        val off = if (tube || kind == "tube" || kind == "rod" || kind == "fork") 0.0 else (if (kindB == "I") mb.tw / 2 + t / 2 else tb + t / 2) * or(x, "side", 1.0)
        val e = x.n("e"); val pb = x.n("pb")
        if (kind == "direct") {
            val s0 = w2 / max(0.2, abs(dn)) + e; val bl = listOf(w.add(d, s0), w.add(d, s0 + pb)).map { it.add(g, tb + 0.1) }
            val bb = x.group(bl, -g, bolt, 2 * tb + fa.t + 2); for (hc in bb.holes) { x.cut(x.aId, hc); x.cut(brId, hc) }; x.bolts(bb, 2, 2 * tb + fa.t + 2 * bolt); return
        }
        val sinT = max(0.15, abs(d dot nF)); val cosT = sqrt(max(0.0, 1 - sinT * sinT))
        val te = (w2 * cosT + max(x.n("setback"), if (x.t("clip") || kind == "tee" || kind == "angle") 90.0 else 0.0) + (if (!fa.flange && ma.kind == "I") ma.b / 2 - ma.tw / 2 else 0.0)) / sinT   // brace end, clear of the face
        val nb = max(1, jsInt(x.n("nb"))); val lap = or(x, "lap", if (tube) 2 * e + (nb - 1) * pb + 40 else 2 * e + (nb - 1) * pb); val tEnd = te + lap
        val go = w.add(g, off); val gf = Joints.Frame3(go, uF, nF); fun toUV(p: D3): DoubleArray { val v = p - go; return doubleArrayOf(v dot uF, v dot nF) }
        val cw = max(w2 + x.n("clear"), 1.5 * d0Of(bolt))
        val corners = listOf(te - 10, tEnd + (if (tube) 20.0 else 0.0)).flatMap { s -> listOf(-cw, cw).map { p -> go.add(d, s).add(pIn, p) } }
        val pts = ArrayList(corners.map(::toUV)); val faceU = pts.map { it[0] }; val uMin = faceU.min(); val uMax = faceU.max()
        pts += doubleArrayOf(uMin - 15, 0.0); pts += doubleArrayOf(uMax + 15, 0.0)
        if (a2 != null) { val m2 = Joints.mf(a2, x.profiles); val fa2 = faceAt(m2, e0); val n2 = fa2.n; val f2 = fa2.pc.add(n2, fa2.off)
            for (p in corners) pts += toUV(p.add(n2, -((p - f2) dot n2))); pts += toUV(go.add(n2, -((go - f2) dot n2))) }
        val gus = gf.poly(hull(pts), -t / 2, t / 2)
        // try { gus − envelope(A) − envelope(A2) } catch { keep }: a missing envelope skips both
        val minus = ArrayList<Mesh>(); run { val ea = envelope(x, x.a) ?: return@run; if (a2 == null) { minus += ea; return@run }; val eb = envelope(x, a2) ?: return@run; minus += ea; minus += eb }
        x.cut(brId, Joints.box3(w, d, pIn, -BIG, te, -5000.0, 5000.0, -5000.0, 5000.0))
        var bolted: Triple<BoltGroup, Int, Double>? = null
        if (tube || kind == "tube") { val sl = x.n("slot"); x.cut(brId, Joints.box3(go, d, pIn, te - 1, te + lap + 25, -cw - 50, cw + 50, -(t / 2 + sl / 2), t / 2 + sl / 2)); x.weld("Brace to gusset", x.n("a"), 4 * lap) }
        else if (kind == "welded" || kind == "rod" || kind == "fork" || kind == "strips") { x.weld("Brace to gusset", x.n("a"), 2 * lap)
            if (kind == "rod" || kind == "fork") x.plate("NUT", stCyl(go.add(d, te - 30), d, mb.h * 0.9, 25.0, 6), 0.0, "NUT M" + JsNumber.format(round(mb.h))) }
        else { val hc = min(mb.h / 2 - mb.tf - 35, 45.0); val cols = if ((kindB == "P" || kindB == "L") && mb.b < 110) listOf(0.0) else if (kindB == "I") listOf(-hc, hc) else listOf(0.0)
            val sg0 = if (off == 0.0) 1.0 else Math.signum(off); val thB = if (kindB == "I") mb.tw / 2 else tb; val bl = ArrayList<D3>()
            for (i in 0 until nb) for (c in cols) bl += w.add(d, te + e + i * pb).add(pIn, c).add(g, -sg0 * (thB + 0.1))
            val grip = t + 2 * thB + 2; val bb = x.group(bl, g * sg0, bolt, grip); for (h in bb.holes) { minus += h; x.cut(brId, h) }; bolted = Triple(bb, bl.size, grip + 2 * bolt) }
        // v69 pushes BOLTS before the gusset plate
        bolted?.let { x.bolts(it.first, it.second, it.third) }
        x.plate(if (a2 != null) "CORNER GUSSET" else "GUSSET", gus, t, minus = minus); x.weld("Gusset to main part", x.n("a"), 2 * (uMax - uMin))
        if (x.t("clip")) for (sg in listOf(-1.0, 1.0)) x.plate("CLIP ANGLE", Fam.lAngle(Joints.Frame3(go.add(g, sg * t / 2), g * sg, nF), 0.0, 0.0, 75.0, 75.0, 8.0, 1.0, 1.0, uMin + 10, uMax - 10), 8.0, "L75*75*8")
        if (kind == "tee" || kind == "angle") x.plate(if (kind == "tee") "TEE FLANGE" else "ANGLE CLEAT", gf.box(uMin, uMax, 0.0, 12.0, -60.0, 60.0), 12.0)
    }
}
