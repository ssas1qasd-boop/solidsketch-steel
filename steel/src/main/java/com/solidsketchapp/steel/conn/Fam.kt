package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.ProfileCatalog
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A weld a component makes (v69 `out.welds`): name, throat a, length (mm), optional loop points; the Welds tab sets type / ws / around. */
class Weld(val name: String, var a: Double, val len: Double, val loop: List<D3>? = null, var type: String? = null, var ws: String? = null, var around: Double? = null)

/** JavaScript truthiness of a q value (0, "", false, null, NaN are false). */
fun jsTruthy(e: JsonElement?): Boolean {
    val p = e as? JsonPrimitive ?: return e != null && e !is JsonNull
    if (p.isString) return p.content.isNotEmpty()
    if (p.content == "true") return true; if (p.content == "false") return false
    val d = p.content.toDoubleOrNull() ?: return false
    return d != 0.0 && !d.isNaN()
}
/** JavaScript `x | 0` (to int32, truncating; NaN → 0). */
fun jsInt(d: Double): Int = if (d.isNaN() || d.isInfinite()) 0 else d.toLong().toInt()

/**
 * The context v69's `famGeom` (v40 L14906) hands each family generator: the merged settings q (CONN def + c.q), the parts
 * (A main, B / B2 secondaries), and the output collectors `cut`, `plate`, `bolts`, `weld`. Units are mm (U = 1).
 */
class FamCtx(val q: JsonObject, val a: JsonObject, val aId: String, val b: JsonObject?, val bId: String?, val b2: JsonObject?, val b2Id: String?, val profiles: ProfileCatalog) {
    val parts = ArrayList<ConnPart>(); val cuts = LinkedHashMap<String, MutableList<Mesh>>(); val welds = ArrayList<Weld>()
    fun n(k: String, d: Double = 0.0): Double { val e = q[k] ?: return d; return if (e is JsonNull) d else StBuild.jsNum(e) }
    fun t(k: String) = jsTruthy(q[k])
    fun s(k: String) = (q[k] as? JsonPrimitive)?.contentOrNull ?: ""
    fun cut(id: String, m: Mesh?) { if (m != null) cuts.getOrPut(id) { ArrayList() } += m }
    /** v69 plate(name, man, t, prof): prof 'PL' + round(t·10)/10; [minus] are holes still to be subtracted. */
    fun plate(name: String, m: Mesh?, t: Double, prof: String? = null, minus: List<Mesh> = emptyList()) {
        if (m != null) parts += ConnPart(name, listOf(m), prof = prof ?: ("PL" + com.solidsketchapp.core.api.JsNumber.format(Math.floor(t * 10 + 0.5) / 10)), minus = minus)
    }
    fun bolts(b: BoltGroup, nb: Int, len: Double, d: Double? = null, grade: String? = null) {
        val info = linkedMapOf<String, JsonElement>("pts" to JsonArray(b.pts.map { jsArr(it.x, it.y, it.z) }), "ax" to jsArr(b.ax.x, b.ax.y, b.ax.z),
            "d" to jsNum(d ?: n("bolt")), "n" to jsNum(nb.toDouble()), "grade" to JsonPrimitive(grade ?: s("grade")), "L" to jsNum(floor(len + 0.5)))
        parts += ConnPart("BOLTS", b.pieces, JsonObject(info), group = b)
    }
    fun weld(name: String, a: Double?, len: Double, loop: List<D3>? = null) { welds += Weld(name, if (a != null && a != 0.0 && !a.isNaN()) a else (n("a").takeIf { it != 0.0 } ?: 5.0), max(0.0, len), loop) }
}

/** The family generators (v69 `FAM`, v40 L14918 and later redefinitions). S7: EP (live v48 definition, L15547). */
object Fam {
    /** v69 FAM_DEF (v40 L14858) with the later default additions (v58: EP cols / gauge2 / pitches). */
    val DEF: Map<String, JsonObject> = mapOf(
        "EP" to obj("cols" to 2, "gauge2" to 0, "pitches" to "", "tp" to 10, "ext" to -10, "extb" to -10, "side" to 30, "gauge" to 90, "rows" to 0, "pitch" to 70, "e" to 35, "bolt" to 20, "grade" to "8.8", "a" to 6, "gap" to 2, "shim" to 0, "front" to 0, "seat" to 0, "stiff" to 0, "comp" to 0, "two" to 0, "shared" to 0),
    )
    val PORTED = setOf("EP")

    fun obj(vararg kv: Pair<String, Any>): JsonObject = JsonObject(LinkedHashMap<String, JsonElement>().apply { for ((k, v) in kv) put(k, when (v) { is String -> JsonPrimitive(v); is Number -> jsNum(v.toDouble()); else -> JsonPrimitive(v.toString()) }) })

    /** v69 tkRows (v40 L14857). */
    fun tkRows(h: Double): Int = if (h <= 150) 1 else if (h <= 260) 2 else if (h <= 330) 3 else if (h <= 420) 4 else if (h <= 470) 5 else max(5, floor((h - 120) / 70).toInt() + 1)

    /** v69 v49Pitches: the typed pitch list, else n − 1 times the pitch. */
    fun pitches(x: FamCtx, n: Int): List<Double> {
        val s = x.s("pitches").trim()
        if (s.isNotEmpty()) return s.split(Regex("""[\s,]+""")).map { com.solidsketchapp.steel.model.StGrid.jsNumber(it) }.filter { it > 0 }
        return List(max(0, n - 1)) { x.n("pitch").let { v -> if (v.isNaN()) 0.0 else v } }
    }
    /** v69 v58Span. */
    fun span(ps: List<Double>, nr: Int, x: FamCtx): Double { var s = 0.0; for (i in 0 until nr - 1) { val v = (if (ps.isNotEmpty()) (ps.getOrNull(i)?.takeIf { it != 0.0 } ?: ps.last()) else 0.0); s += if (v != 0.0 && !v.isNaN()) v else x.n("pitch").takeIf { it != 0.0 } ?: 70.0 }; return s }

    /** v69 stStiffPair (v40): stiffener pairs across an I member at a station tz along N, both sides of the web. */
    fun stiffPair(x: FamCtx, m: MemberFrame, tz: Double, ts: Double, ch: Double, sides: List<Int>, name: String = "STIFFENER") {
        val xi = m.h / 2 - m.tf; val w = m.tw / 2; val wb = m.b / 2 - 5
        for (sg in sides) {
            val pl = listOf(doubleArrayOf(-xi, w + ch), doubleArrayOf(-xi + ch, w), doubleArrayOf(xi - ch, w), doubleArrayOf(xi, w + ch), doubleArrayOf(xi, wb), doubleArrayOf(-xi, wb)).map { doubleArrayOf(it[0], sg * it[1]) }
            x.plate(name, Joints.poly3(m.c1, m.eh, m.es, pl, tz - ts / 2, tz + ts / 2), ts)
        }
    }

    /** v69 stLAngle: an angle in frame F, corner (x0, y0), legs along sx·X and sy·Y, z0..z1. */
    fun lAngle(f: Joints.Frame3, x0: Double, y0: Double, leg1: Double, leg2: Double, t: Double, sx: Double, sy: Double, z0: Double, z1: Double): Mesh =
        f.poly(listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(leg1, 0.0), doubleArrayOf(leg1, t), doubleArrayOf(t, t), doubleArrayOf(t, leg2), doubleArrayOf(0.0, leg2)).map { doubleArrayOf(x0 + sx * it[0], y0 + sy * it[1]) }, z0, z1)

    private const val BIG = 60000.0   // v69 v49Big

    /** EP · end plates (live v48 L15547): one plate per secondary, flush on the main part's face. */
    fun ep(x: FamCtx) {
        fun one(bm: JsonObject, bmId: String, sharedGrip: Double) {
            val j = Joints.joint(x.a, bm, x.profiles); val bp = j.b; val u = j.uDir; val xu = (j.x dot u).let { if (it == 0.0) 1.0 else it }
            val o = j.e.add(j.x, j.xFace)
            var fy = bp.eh.add(u, -(bp.eh dot u)); if (fy.length() < 1e-6) fy = j.y.add(u, -(j.y dot u)); fy = fy.normalize()
            val fz = (u cross fy).normalize(); val ff = Joints.Frame3(o, u, fy)
            fun onFace(p: D3): D3 { val s = ((p - o) dot u) / xu; return p.add(j.x, -s) }
            val cs = ArrayList<Triple<Double, Double, D3>>()
            for (sy in listOf(-1, 1)) for (sz in listOf(-1, 1)) { val p = onFace(o.add(bp.eh, sy * bp.h / 2).add(bp.es, sz * bp.b / 2)); cs += Triple((p - o) dot fy, (p - o) dot fz, p) }
            val yT = cs.maxOf { it.first }; val yB = cs.minOf { it.first }; var zC = (cs.maxOf { it.second } + cs.minOf { it.second }) / 2
            val tp = x.n("tp"); val gp = x.n("gap"); val sh = x.n("shim")
            val fr = if (x.t("front") || x.t("double") || x.t("stub") || x.t("stiffT")) max(x.n("tp"), 10.0) else 0.0
            val n0 = gp + fr + sh; val n1 = n0 + tp
            var yh = yT + x.n("ext"); var yl = yB - x.n("extb")
            if (j.cope) { val k = max(0.2, abs(j.y dot fy)); yh = min(yh, (j.yTop - 10) / k); yl = max(yl, (j.yBot + 10) / k) }
            val nc = if (jsInt(x.n("cols")) == 4) 4 else 2; val g = x.n("gauge"); val g2 = x.n("gauge2").takeIf { it != 0.0 && !it.isNaN() } ?: g
            val zs = if (nc == 4) listOf(-g / 2 - g2, -g / 2, g / 2, g / 2 + g2) else listOf(-g / 2, g / 2)
            val zw = (if (nc == 4) g / 2 + g2 else g / 2) + x.n("side")
            run { val ph = x.n("posH"); val gw = zw - x.n("side")
                zC = when (x.s("refH")) { "Left" -> zC - bp.b / 2 + ph + gw; "Right" -> zC + bp.b / 2 - ph - gw; else -> zC + ph } }
            val ep = ff.box(n0, n1, yl, yh, zC - zw, zC + zw); val hh = yh - yl
            var nr = if (x.t("rows")) jsInt(x.n("rows")) else tkRows(hh); val ps = pitches(x, nr)
            if (x.s("pitches").trim().isNotEmpty()) nr = ps.size + 1
            var top = yh - x.n("e")
            run { val sp = span(ps, nr, x); val pvE = x.q["posV"]; val pv = if (pvE == null || pvE is JsonNull || (pvE as? JsonPrimitive)?.content == "") Double.NaN else StBuild.jsNum(pvE)
                if (x.s("refV") == "Middle") top = (yh + yl) / 2 + sp / 2 + (if (pv.isFinite()) pv else 0.0)
                else if (x.s("refV") == "Below") top = yl + (if (pv.isFinite()) pv else x.n("e")) + sp }
            val ys = ArrayList<Double>()
            if (x.s("pitches").trim().isEmpty() && !x.t("pitch")) { for (i in 0 until nr) ys += top - (top - (yl + x.n("e"))) * i / max(1, nr - 1) }
            else { var y = top; for (i in 0 until nr) { ys += y; y -= (ps.getOrNull(i)?.takeIf { it != 0.0 } ?: ps.lastOrNull()?.takeIf { it != 0.0 } ?: 70.0) } }
            val bl = ArrayList<D3>(); for (y in ys) for (z in zs) bl += ff.p(n1 + 0.1, y, zC + z)
            val grip = n1 + j.tPrim + sharedGrip + 2
            val bb = StBolts.build(bl, -u, x.n("bolt"), grip, x.q)   // v58: the component's settings (TKB_Q) reach stBolts here
            for (hc in bb.holes) x.cut(x.aId, hc)
            x.cut(bmId, ff.box(-BIG, n1, -BIG, BIG, -BIG, BIG))   // the secondary ends on the plate's back face
            if (j.cope) { val hb = bp.h / 2
                if (hb > j.yTop - 10) x.cut(bmId, j.box(-BIG, j.xFace + j.copeL, j.yTop - 10, BIG / 10, -BIG / 10, BIG / 10))
                if (-hb < j.yBot + 10) x.cut(bmId, j.box(-BIG, j.xFace + j.copeL, -BIG / 10, j.yBot + 10, -BIG / 10, BIG / 10)) }
            x.plate("END PLATE", ep, x.n("tp"), minus = bb.holes); x.bolts(bb, bl.size, grip + 2 * x.n("bolt"))
            if (sh != 0.0) x.plate("SHIM", ff.box(n0 - sh, n0, yl, yh, zC - zw, zC + zw), x.n("shim"), minus = bb.holes)
            if (fr != 0.0) { val mr2 = j.m.r + 1
                val f = ff.box(gp, gp + fr, max(yl - 20, if (j.yBot.isFinite()) j.yBot + mr2 else -BIG), min(yh + 20, if (j.yTop.isFinite()) j.yTop - mr2 else BIG), zC - zw - 10, zC + zw + 10)
                x.plate(if (x.t("stub")) "STUB PLATE" else "FRONT PLATE", f, fr, minus = bb.holes); x.weld("Front plate to main part", x.n("a"), 2 * (yh - yl + 2 * zw)) }
            val lp = listOf(cs[0], cs[1], cs[3], cs[2]).map { it.third.add(u, n1) }
            x.weld("Beam to end plate", x.n("a"), 2 * (yT - yB) + 4 * bp.b, lp)
            val x1J = j.xFace + n1 / xu
            if (x.t("comp")) for (s in listOf(1, -1)) { val y0 = s * bp.h / 2; x.plate("FLANGE PLATE", j.box(x1J, x1J + 250, y0, y0 + s * max(8.0, bp.tf), -bp.b / 2, bp.b / 2), max(8.0, bp.tf)) }
            if (x.t("seat") && !(abs(j.m.a.z) < 0.9 && !j.flange)) { val f = Joints.Frame3(j.e, j.x, j.y)
                x.plate("SEAT ANGLE", lAngle(f, x1J, -bp.h / 2, 90.0, 90.0, 10.0, 1.0, -1.0, -bp.b / 2, bp.b / 2), 10.0, "L90*90*10") }
            if ((x.t("stiff") || x.t("stiffT")) && j.flange && abs(j.m.a.z) > 0.9) {
                val nn = (j.m.eh cross j.m.es).normalize()
                for (c in listOf(cs.maxBy { it.first }, cs.minBy { it.first }))
                    stiffPair(x, j.m, (c.third.add(fy, -Math.signum(c.first) * bp.tf / 2) - j.m.c1) dot nn, max(10.0, bp.tf), 15.0, listOf(1, -1))
            }
        }
        one(x.b!!, x.bId!!, 0.0)
        if (x.b2 != null) one(x.b2, x.b2Id!!, if (x.t("shared")) x.n("tp") + x.n("gap") else 0.0)
    }

    /** v69 famGeom + the v58 wrapper (bolt info d0 / std / thr / sw, Welds tab). Returns null when the family is not ported or a part is missing. */
    fun geom(fam: String, q: JsonObject, parts: List<String>, stOf: (String) -> JsonObject?, profiles: ProfileCatalog): ConnGeom? {
        if (fam !in PORTED) return null
        val a = parts.getOrNull(0)?.let(stOf) ?: return null
        val x = FamCtx(q, a, parts[0], parts.getOrNull(1)?.let(stOf), parts.getOrNull(1), parts.getOrNull(2)?.let(stOf), parts.getOrNull(2), profiles)
        when (fam) { "EP" -> { if (x.b == null) return null; ep(x) } }
        // v58: bolt groups carry d0 / std / thr / sw (L15997); Welds tab (q.W) sets a / type / ws / around per weld.
        val out = x.parts.map { p ->
            val g = p.group; val bo = p.bolts
            if (g == null || bo == null) p else {
                val m = LinkedHashMap(bo); m["pts"] = JsonArray(g.pts.map { jsArr(it.x, it.y, it.z) }); m["ax"] = jsArr(g.ax.x, g.ax.y, g.ax.z); m["n"] = jsNum(g.pts.size.toDouble())
                m["d0"] = jsNum(Math.floor(g.d0 * 10 + 0.5) / 10)
                if (x.s("grade").isNotEmpty()) m["std"] = JsonPrimitive(x.s("grade") + "XOX")
                m["thr"] = JsonPrimitive(if ((q["thr"] as? JsonPrimitive)?.content == "0") "No" else "Yes"); m["sw"] = JsonPrimitive(x.s("sw").ifEmpty { "Site" })
                if (x.s("bcmt").isNotEmpty()) m["cmt"] = JsonPrimitive(x.s("bcmt")); StBolts.tkNum(q["blen"], 0.0).let { if (it != 0.0) m["blen"] = jsNum(it) }
                ConnPart(p.name, p.pieces, JsonObject(m), p.prof, p.minus, g)
            }
        }
        val w = q["W"] as? JsonObject
        if (w != null) for (wd in x.welds) { val o = (w[wd.name] ?: w["*"]) as? JsonObject ?: continue
            o["a"]?.let { if ((it as? JsonPrimitive)?.content != "") wd.a = StBuild.jsNum(it) }; (o["type"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { wd.type = it }
            (o["ws"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { wd.ws = it }; o["around"]?.let { wd.around = StBuild.jsNum(it) } }
        return ConnGeom(out, x.cuts, x.welds)
    }
}
