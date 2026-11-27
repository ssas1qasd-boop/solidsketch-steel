package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.num
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * TG family: tongue plate tube ends (Ttg) and tube crossings (Ttgx). Live chain: v69 FAM.TG (L15612, FAM_TG22) wrapped by
 * the v54 node gusset (L15867, v54tgSet L15850, v54group L15862): several Ttg on one node share one GUSSET PLATE.
 */
object FamTG {
    const val PORTED = true
    private const val BIG = 60000.0   // v69 v49Big

    private fun tubeHalf(m: MemberFrame, dir: D3) = (abs(dir dot m.eh) * m.h + abs(dir dot m.es) * m.b) / 2

    /** v69 stFaceToward (L15536): the face of a member a brace comes from, by the brace direction. */
    private fun faceToward(m: MemberFrame, p0: D3, d: D3): FamCL.Face {
        val al = (p0 - m.c1) dot m.a; val pc = m.c1.add(m.a, al); var u = d.add(m.a, -(d dot m.a)); if (u.length() < 1e-6) return FamCL.faceAt(m, p0); u = u.normalize()
        val w = p0 - pc; val y0 = w dot m.eh; val z0 = w dot m.es; val uy = u dot m.eh; val uz = u dot m.es; val tube = m.kind == "O" || m.kind == "R"
        val fl = abs(uy) >= abs(uz)
        fun sg(a: Double, b: Double) = if (a != 0.0) Math.signum(a) else FamCL.sgn1(b)
        val s = if (fl) sg(uy, y0) else sg(uz, z0)
        return FamCL.Face(if (fl) m.eh * s else m.es * s, if (fl) m.h / 2 else (if (tube) m.b / 2 else m.tw / 2), fl, pc)
    }

    /** v69 stEnvelope (L14883): the profile's outline (no hollows) along the part. */
    private fun envelope(x: FamCtx, st: JsonObject): Mesh? {
        val sec = Sections.of(x.profiles.lookup(st.str("prof") ?: "")) ?: return null; val m = Joints.mf(st, x.profiles)
        val l = m.c2.distanceTo(m.c1) + st.num("dx1") + st.num("dx2")
        return Prism.extrude(Region(sec.outer), l, Prism.Frame(m.c1.add(m.a, -st.num("dx1")), m.es, m.eh, m.a))
    }

    /** v69 stHull2 (L15542): monotone-chain convex hull. */
    private fun hull(p: List<DoubleArray>): List<DoubleArray> {
        val s = p.sortedWith { a, b -> val d = a[0] - b[0]; if (d != 0.0) d.compareTo(0.0) else (a[1] - b[1]).compareTo(0.0) }
        fun cr(o: DoubleArray, a: DoubleArray, b: DoubleArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        val lo = ArrayList<DoubleArray>(); val up = ArrayList<DoubleArray>()
        for (q in s) { while (lo.size >= 2 && cr(lo[lo.size - 2], lo[lo.size - 1], q) <= 0) lo.removeAt(lo.size - 1); lo += q }
        for (q in s.reversed()) { while (up.size >= 2 && cr(up[up.size - 2], up[up.size - 1], q) <= 0) up.removeAt(up.size - 1); up += q }
        return lo.dropLast(1) + up.dropLast(1)
    }

    private class End(val tg: Mesh, val bl: List<D3>, val f: Joints.Frame3)
    /** v69 tgEnd (L15599): one brace end on a tongue, in the brace plane (normal g); s along d from S0. */
    private fun tgEnd(x: FamCtx, brSt: JsonObject, brId: String, s0: D3, d: D3, g: D3, sTip: Double, toff: Double, out: Double): End {
        val mb = Joints.mf(brSt, x.profiles); val pIn = (g cross d).normalize(); val t = x.n("t"); val w2 = x.n("w") / 2; val e = x.n("e"); val p = x.n("p")
        val sTube = sTip + (x.n("L") - x.n("ins")); val f = Joints.Frame3(s0, d, pIn)
        val tg = f.box(sTip, sTip + x.n("L"), -w2, w2, toff - t / 2, toff + t / 2); val bl = ArrayList<D3>()
        val v70 = x.s("tend") == "end"   // v70 C05: Tekla's slot (t wide, the length inside the tube) and head point on the plate face
        for (s in listOf(sTip + e, sTip + e + p)) for (z in listOf(-x.n("g") / 2, x.n("g") / 2)) bl += f.p(s, z, toff - out * (t / 2 + (if (v70 && x.s("blrule") == "tekla") 0.0 else 0.1)))
        x.cut(brId, f.box(-BIG, sTube, -BIG / 10, BIG / 10, -BIG / 10, BIG / 10)); val hw = tubeHalf(mb, pIn); val hg = tubeHalf(mb, g)
        if (v70) x.cut(brId, f.box(sTube - 1, sTube + x.n("ins"), -hw - 5, hw + 5, toff - t / 2, toff + t / 2))
        else x.cut(brId, f.box(sTube - 1, sTube + x.n("ins") + 10, -hw - 5, hw + 5, toff - t / 2 - 1, toff + t / 2 + 1))
        if (x.t("cap")) for (sg in listOf(1.0, -1.0)) { val z0 = toff + sg * t / 2; val z1 = sg * hg
            if (abs(z1 - z0) > 1 && Math.signum(z1 - z0) == sg) x.plate("END CAP", f.box(sTube - x.n("cap"), sTube, -hw, hw, z0, z1), x.n("cap"), "FLT" + FamCL.jsStr(x.q["cap"]) + "*" + JsNumber.format(floor(2 * hw + 0.5))) }
        x.weld("Tongue in tube", x.n("a"), 4 * x.n("ins")); return End(tg, bl, f)
    }

    private fun flt(x: FamCtx) = "FLT" + FamCL.jsStr(x.q["t"]) + "*" + FamCL.jsStr(x.q["w"])
    /** v70 C05: Tekla's bolt length (grip = plate + gusset) when blrule is 'tekla', else v69's [len]. */
    private fun blen(x: FamCtx, len: Double) = if (x.s("blrule") == "tekla") Fam.tkBoltLen(x.n("bolt"), x.n("t") + x.n("tf")) else len
    /** v70 C05 'Tube end' = 'brace end': the tip that puts the tube end at the brace's own end (the end nearer the main part). */
    private fun tipAtEnd(x: FamCtx, q: QR, br: JsonObject, w: D3, d: D3): Double {
        val mb = Joints.mf(br, x.profiles); val ma = Joints.mf(x.a, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val e = if (dl(mb.c1) <= dl(mb.c2)) mb.c1 else mb.c2; return ((e - w) dot d) - (q.n("L") - q.n("ins"))
    }

    /** v69 CONN.Ttg.def (L15608; node / nodeR added at L15849): a neighbour's q in the node is this def + its c.q. */
    private val TTG_DEF = Fam.obj("node" to 1, "nodeR" to 450, "t" to 12, "w" to 140, "L" to 315, "ins" to 150, "tf" to 12, "fw" to 140, "e" to 40, "p" to 60, "g" to 60,
        "bolt" to 16, "grade" to "8.8", "clear" to 15, "cap" to 5, "side" to 1, "plane" to "auto", "a" to 3.5)
    /** A q read the JavaScript way (`+q.k`, truthiness, String). */
    private class QR(val o: JsonObject) {
        fun n(k: String): Double { val e = o[k] ?: return 0.0; return if (e is JsonNull) 0.0 else StBuild.jsNum(e) }
        fun t(k: String) = jsTruthy(o[k]); fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""
    }
    private class TgSet(val d: D3, val w: D3, val g: D3, val pIn: D3, val nF: D3, val f0: D3, val sTip: Double, val fin: List<D3>, val bl: List<D3>, val sg: Double, val steep: Boolean)
    /** v69 v54tgSet (L15850): one brace's tongue set-up at the node (plane forced to [plane], g turned toward [gRef]). */
    private fun tgSet(x: FamCtx, q: QR, br: JsonObject, plane: String?, gRef: D3?): TgSet {
        val t = q.n("t"); val w2 = q.n("w") / 2; val mb = Joints.mf(br, x.profiles); val ma = Joints.mf(x.a, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val near = dl(mb.c1) <= dl(mb.c2); val e = if (near) mb.c1 else mb.c2; val far = if (near) mb.c2 else mb.c1; val d = (far - e).normalize()
        val fa = faceToward(ma, e, d); val nF = fa.n; val f0 = fa.pc.add(nF, fa.off); val dn = d dot nF
        val w = if (abs(dn) > 0.05) e.add(d, ((f0 - e) dot nF) / dn) else e
        val pl = plane ?: q.s("plane"); var v2 = if (pl == "main") ma.a else if (pl == "vert") D3.Z else (if (abs(d dot ma.a) < 0.3) D3.Z else ma.a)
        if ((d cross v2).length() < 0.1) v2 = ma.es
        var g = (d cross v2).normalize(); if (gRef != null && (g dot gRef) < 0) g = -g; val pIn = (g cross d).normalize()
        val sinT = max(0.2, abs(dn)); val cosT = sqrt(max(0.0, 1 - sinT * sinT))
        val outst = if (!fa.flange && ma.kind == "I" && !(abs(g dot ma.eh) > 0.9 && abs(ma.a.z) < 0.8)) ma.b / 2 - ma.tw / 2 else 0.0
        val sTip = if (q.s("tend") == "end") tipAtEnd(x, q, br, w, d) else (outst + w2 * cosT + q.n("clear")) / sinT
        val sEnd = sTip + (2 * q.n("e") + q.n("p")); val fw2 = (if (q.t("fw")) q.n("fw") else q.n("w")) / 2; val s0 = -80 - outst / sinT
        fun p3(s: Double, y: Double) = w.add(d, s).add(pIn, y)
        val fin = listOf(p3(s0, -fw2), p3(sEnd, -fw2), p3(sEnd, fw2 - 25), p3(sEnd - 25, fw2), p3(s0, fw2))
        val sg = if (q.t("side")) q.n("side") else 1.0; val bl = ArrayList<D3>()
        for (s in listOf(sTip + q.n("e"), sTip + q.n("e") + q.n("p"))) for (z in listOf(-q.n("g") / 2, q.n("g") / 2)) bl += p3(s, z).add(g, -sg * (t / 2 + 0.1))
        return TgSet(d, w, g, pIn, nF, f0, sTip, fin, bl, sg, abs(d dot ma.a) >= 0.3)
    }
    private class Mem(val id: String, val q: QR, val brId: String, val br: JsonObject) { var s: TgSet? = null }

    /**
     * v70 C06 · Tekla 20's gusset outline (web gusset70): per brace an edge square to it, Picture 5 (gd, default 20) before its end plate,
     * half-lengths gwa / gwb on the sides toward + / - the main part's axis (default half the plate width); the points are those corners
     * and each corner brought back onto the main part's face, square to the face and along its brace; the gusset is their convex hull.
     */
    private fun teklaOutline(x: FamCtx, set: List<Pair<QR, TgSet>>, nF: D3, f0: D3, gN: D3, o: D3, a1: D3, a2: D3): List<DoubleArray> {
        val ma = Joints.mf(x.a, x.profiles); val k = nF.add(gN, -(nF dot gN)).normalize(); val pts = ArrayList<DoubleArray>()
        fun num(q: QR, key: String, d: Double): Double { val e = q.o[key] as? JsonPrimitive ?: return d; if (e.isString && e.content.isBlank()) return d; val v = StBuild.jsNum(e); return if (v.isFinite()) v else d }
        for ((q, st) in set) {
            val sE = st.sTip + (num(q, "L", 315.0) - num(q, "ins", 150.0) - num(q, "cap", 5.0) - num(q, "gd", 20.0)); val w2 = num(q, "w", 140.0) / 2
            val pa = if ((st.pIn dot ma.a) >= 0) 1 else -1; val hp = num(q, if (pa > 0) "gwa" else "gwb", w2); val hn = num(q, if (pa > 0) "gwb" else "gwa", w2)
            for (y in listOf(hp, -hn)) { val c = st.w.add(st.d, sE).add(st.pIn, y); val kn = (k dot nF).let { if (it == 0.0) 1.0 else it }
                val cp = c.add(k, -((c - f0) dot nF) / kn); val dn = st.d dot nF; val ca = if (abs(dn) > 1e-6) c.add(st.d, -((c - f0) dot nF) / dn) else cp
                for (p in listOf(c, cp, ca)) { val v = p - o; pts += doubleArrayOf(v dot a1, v dot a2) } }
        }
        return pts
    }

    /** v69 FAM.TG (L15867): several Ttg on one node of the main part (within nodeR, same face) share one GUSSET PLATE, built by the first. */
    fun gen(x: FamCtx) {
        val b = x.b; if (x.s("kind") == "cross" || !x.t("node") || b == null) return tg22(x)
        val me = runCatching { tgSet(x, QR(x.q), b, null, null) }.getOrElse { return tg22(x) }; val r0 = if (x.t("nodeR")) x.n("nodeR") else 450.0
        // v54group (L15862): this component and every Ttg on the same main part with node on
        val seen = HashSet<String>(); val grp = ArrayList<Mem>(); val mineId = x.conn?.id
        for (k in listOfNotNull(x.conn) + x.conns) { if (!seen.add(k.id)) continue
            if (k.type != "Ttg" || k.parts.getOrNull(0) != x.aId) continue; val brId = k.parts.getOrNull(1) ?: continue; val br = x.stOf(brId) ?: continue
            val q = if (k.id == mineId) x.q else JsonObject(LinkedHashMap(TTG_DEF).apply { putAll(k.q) }); if (!jsTruthy(q["node"])) continue; grp += Mem(k.id, QR(q), brId, br) }
        val all = grp.filter { it.id == mineId || it.brId != x.bId }.mapNotNull { o -> runCatching { o.s = tgSet(x, o.q, o.br, null, null); o }.getOrNull() }
            .filter { it.s!!.w.distanceTo(me.w) < r0 && (it.s!!.nF dot me.nF) > 0.9 }
        if (all.size < 2) return tg22(x)
        val plane = if (all.any { it.s!!.steep }) "main" else null; val s2 = all.sortedBy { it.id.toDoubleOrNull() ?: -1.0 }; val lead = s2[0]
        val l0 = tgSet(x, lead.q, lead.br, plane, null); val gN = l0.g; for (o in s2) o.s = tgSet(x, o.q, o.br, plane, gN)
        // v70 C05 'Connection plate position' = 'against the gusset': every plate lies on the first brace's plane (web: NODE)
        val ag = x.s("tpos") == "gusset"
        // v70 C06 'Gusset plate position on the brace' = middle: the gusset centred on the braces, every plate beside it
        val w0 = if (ag && x.s("gpos") == "middle") l0.w.add(l0.g, -(if (x.t("side")) x.n("side") else 1.0) * (x.n("t") + x.n("tf")) / 2) else l0.w
        fun shift(st: TgSet) = if (ag && abs(st.g dot l0.g) > 0.99) -((st.w - w0) dot st.g) else 0.0
        if (ag) for (o in s2) { val st = o.s!!; val sh = shift(st); if (sh != 0.0) o.s = TgSet(st.d, st.w, st.g, st.pIn, st.nF, st.f0, st.sTip, st.fin, st.bl.map { it.add(st.g, sh) }, st.sg, st.steep) }
        val mine = s2.find { it.id == mineId } ?: s2.first { it.brId == x.bId }; val sm = mine.s!!; val t = x.n("t"); val tf = x.n("tf"); val sg = sm.sg
        val r = tgEnd(x, b, x.bId!!, sm.w, sm.d, sm.g, sm.sTip, shift(sm), sg); val bb = x.group(r.bl, sm.g * sg, x.n("bolt"), t + tf + 2)
        x.plate("TONGUE PLATE", r.tg, t, flt(x), minus = bb.holes); x.bolts(bb, r.bl.size, blen(x, x.n("t") + x.n("tf") + 2 * x.n("bolt")))
        if (mine !== s2[0]) return   // the first connection of the node builds the shared gusset
        val a1 = l0.d.add(gN, -(l0.d dot gN)).normalize(); val a2 = (gN cross a1).normalize(); val o = w0; val gf = Joints.Frame3(o, a1, a2)
        val pts = ArrayList<DoubleArray>()
        if (x.s("gshape") == "tekla") pts += teklaOutline(x, s2.map { it.q to it.s!! }, l0.nF, l0.f0, gN, o, a1, a2)
        else for (m in s2) for (p in m.s!!.fin) { val v = p - o; pts += doubleArrayOf(v dot a1, v dot a2) }
        val z0 = sg * t / 2; val z1 = sg * (t / 2 + tf); val gus = gf.poly(hull(pts), min(z0, z1), max(z0, z1))
        // gus.intersect(the half space in front of the face) = gus minus the half space behind it; then minus the main part's envelope and every brace's holes
        val behind = Joints.box3(l0.f0, l0.nF, l0.pIn.add(l0.nF, -(l0.pIn dot l0.nF)).normalize(), -BIG, -0.01, -BIG, BIG, -BIG, BIG)
        val holes = ArrayList<Mesh>(); for (m in s2) holes += x.group(m.s!!.bl, m.s!!.g * m.s!!.sg, m.q.n("bolt"), t + tf + 2).holes
        x.plate("GUSSET PLATE", gus, tf, minus = listOfNotNull(behind, runCatching { envelope(x, x.a) }.getOrNull()) + holes)
        x.weld("Gusset plate to main part", x.n("a"), 2.0 * 400)
    }

    /** v69 FAM.TG (L15612, FAM_TG22): tongue plate tube end on a fin plate, or a tube crossing (kind 'cross'). */
    private fun tg22(x: FamCtx) {
        val t = x.n("t"); val w2 = x.n("w") / 2
        if (x.s("kind") == "cross") {
            val h1 = x.b ?: throw IllegalStateException("not built"); val mc = Joints.mf(x.a, x.profiles)
            val halves = listOfNotNull(h1 to x.bId!!, x.b2?.let { it to x.b2Id!! }); val m1 = Joints.mf(h1, x.profiles)
            var g = mc.a cross m1.a; if (g.length() < 1e-6) throw IllegalStateException("not built"); g = g.normalize()
            val w0 = mc.c1 - m1.c1; val b = mc.a dot m1.a; val dd = mc.a dot w0; val ee = m1.a dot w0; val den = (1 - b * b).let { if (it == 0.0) 1e-9 else it }
            val p0 = mc.c1.add(mc.a, (b * ee - dd) / den)
            val pC = (g cross mc.a).normalize(); val gf = Joints.Frame3(p0, mc.a, pC); val toff = t / 2 + x.n("t") / 2
            val hwC = tubeHalf(mc, pC)
            val pts = arrayListOf(doubleArrayOf(-hwC - 60, -hwC - 10), doubleArrayOf(hwC + 60, -hwC - 10), doubleArrayOf(hwC + 60, hwC + 10), doubleArrayOf(-hwC - 60, hwC + 10))
            val ends = ArrayList<End>()
            for ((hst, hid) in halves) {
                val mh = Joints.mf(hst, x.profiles); val near = mh.c1.distanceTo(p0) <= mh.c2.distanceTo(p0); val d = ((if (near) mh.c2 else mh.c1) - (if (near) mh.c1 else mh.c2)).normalize()
                val sinA = max(0.2, (d cross mc.a).length()); val cosA = abs(d dot mc.a)
                val sTip = (hwC + x.n("clear") + w2 * cosA) / sinA; ends += tgEnd(x, hst, hid, p0, d, g, sTip, toff, 1.0); val pIn = (g cross d).normalize()
                for (s in listOf(sTip - 5, sTip + (2 * x.n("e") + x.n("p")))) for (z in listOf(-w2, w2)) { val v = p0.add(d, s).add(pIn, z) - p0; pts += doubleArrayOf(v dot mc.a, v dot pC) }
            }
            val gus = gf.poly(hull(pts), -t / 2, t / 2)
            x.cut(x.aId, gf.box(pts.minOf { it[0] } - 1, pts.maxOf { it[0] } + 1, -hwC - 10, hwC + 10, -t / 2 - 1, t / 2 + 1))   // slot the continuous brace for the gusset
            val gh = ArrayList<Mesh>()
            for (r in ends) { val bb = x.group(r.bl, g, x.n("bolt"), 2 * t + 2); gh += bb.holes
                x.plate("TONGUE PLATE", r.tg, t, flt(x), minus = bb.holes); x.bolts(bb, r.bl.size, blen(x, 2 * x.n("t") + 2 * x.n("bolt"))) }
            x.plate("CROSSING GUSSET", gus, t, minus = gh); x.weld("Crossing gusset to brace", x.n("a"), 4 * 2 * hwC); return
        }
        val br = x.b ?: throw IllegalStateException("not built"); val mb = Joints.mf(br, x.profiles); val ma = Joints.mf(x.a, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val near = dl(mb.c1) <= dl(mb.c2); val e = if (near) mb.c1 else mb.c2; val far = if (near) mb.c2 else mb.c1; val d = (far - e).normalize()
        val fa = faceToward(ma, e, d); val nF = fa.n; val f0 = fa.pc.add(nF, fa.off); val dn = d dot nF
        val w = if (abs(dn) > 0.05) e.add(d, ((f0 - e) dot nF) / dn) else e
        val pl = x.s("plane"); var v2 = if (pl == "main") ma.a else if (pl == "vert") D3.Z else (if (abs(d dot ma.a) < 0.3) D3.Z else ma.a)
        if ((d cross v2).length() < 0.1) v2 = ma.es
        val g = (d cross v2).normalize(); val pIn = (g cross d).normalize(); val sinT = max(0.2, abs(dn)); val cosT = sqrt(max(0.0, 1 - sinT * sinT))
        val outst = if (!fa.flange && ma.kind == "I" && !(abs(g dot ma.eh) > 0.9 && abs(ma.a.z) < 0.8)) ma.b / 2 - ma.tw / 2 else 0.0
        val sTip = if (x.s("tend") == "end") tipAtEnd(x, QR(x.q), br, w, d) else (outst + w2 * cosT + x.n("clear")) / sinT; val sg = if (x.t("side")) x.n("side") else 1.0; val toff = 0.0
        val r = tgEnd(x, br, x.bId!!, w, d, g, sTip, toff, sg)
        val tf = x.n("tf"); val fw2 = (if (x.t("fw")) x.n("fw") else x.n("w")) / 2; val z0 = toff + sg * t / 2; val z1 = toff + sg * (t / 2 + tf); val sEnd = sTip + (2 * x.n("e") + x.n("p"))
        val s0 = -80 - outst / sinT
        val teklaG = x.s("gshape") == "tekla"   // v70 C06: one brace's gusset by Tekla 20's outline rule (not a fin)
        val fin = if (teklaG) { val st = tgSet(x, QR(x.q), br, null, null); val a1 = d; val a2 = (g cross d).normalize()
                r.f.poly(hull(teklaOutline(x, listOf(QR(x.q) to st), nF, f0, g, w, a1, a2)), min(z0, z1), max(z0, z1)) }
            else r.f.poly(listOf(doubleArrayOf(s0, -fw2), doubleArrayOf(sEnd, -fw2), doubleArrayOf(sEnd, fw2 - 25), doubleArrayOf(sEnd - 25, fw2), doubleArrayOf(s0, fw2)), min(z0, z1), max(z0, z1))
        // fin.intersect(the half space in front of the face) = fin minus the half space behind it; then minus the main part's envelope
        val behind = Joints.box3(f0, nF, pIn.add(nF, -(pIn dot nF)).normalize(), -BIG, -0.01, -BIG, BIG, -BIG, BIG)
        val bb = x.group(r.bl, g * sg, x.n("bolt"), t + tf + 2)
        x.plate("TONGUE PLATE", r.tg, t, flt(x), minus = bb.holes)
        x.plate(if (teklaG) "GUSSET PLATE" else "FIN PLATE", fin, tf, minus = listOfNotNull(behind, runCatching { envelope(x, x.a) }.getOrNull()) + bb.holes)
        x.bolts(bb, r.bl.size, blen(x, x.n("t") + x.n("tf") + 2 * x.n("bolt"))); x.weld("Fin plate to main part", x.n("a"), 2 * (sEnd + outst / sinT + 80))
    }
}
