package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.steel.data.Profile
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.geom.StBuild.Companion.vec
import com.solidsketchapp.steel.model.StGrid
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * S10: v69's v34 connection types (stConnGeom L13912): `base` (base plate, L13916), `knee` (eaves haunch, L13927) and
 * `apex` (apex haunch, L13951; v54 L15819 `v54apex` when q.ph > 0, chosen by the wrapper stConnGeom22 L15845).
 * Each runs inside the v58 wrapper ([Fam.geom]); stBolts is the v58 one ([FamCtx.group]). U = 1 (mm).
 */
object ClassicV34 {
    val GEN: Map<String, (FamCtx) -> Unit> = mapOf("base" to ::base, "knee" to ::knee, "apex" to ::apex)

    // ---- JavaScript value helpers (q values may be numbers or strings)
    /** JavaScript String(v) of a q value ('PL' + q.t). */
    private fun jsStr(e: JsonElement?): String = when {
        e == null -> "undefined"; e is JsonNull -> "null"
        e is JsonPrimitive && e.isString -> e.content
        e is JsonPrimitive -> if (e.content == "true" || e.content == "false") e.content else JsNumber.format(StBuild.jsNum(e))
        else -> e.toString()
    }
    /** JavaScript `+v` of a q value. */
    private fun jsPlus(e: JsonElement?): Double = when {
        e == null -> Double.NaN; e is JsonNull -> 0.0
        e is JsonPrimitive && e.isString -> StGrid.jsNumber(e.content.trim())
        e is JsonPrimitive -> if (e.content == "true") 1.0 else if (e.content == "false") 0.0 else StBuild.jsNum(e)
        else -> Double.NaN
    }
    /** A JavaScript `a || b || c` chain over q values (JsonElement) and profile numbers (Double?): the first truthy one. */
    private fun or(vararg v: Any?): Any? { for (e in v) when (e) { is JsonElement -> if (jsTruthy(e)) return e; is Double -> if (e != 0.0 && !e.isNaN()) return e }; return v.last() }
    private fun num(v: Any?): Double = when (v) { is Double -> v; is JsonElement -> jsPlus(v); else -> Double.NaN }
    private fun str(v: Any?): String = when (v) { is Double -> JsNumber.format(v); is JsonElement -> jsStr(v); else -> "undefined" }
    /** `p.h || 300` for profile numbers. */
    private fun orD(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v
    private fun prof(x: FamCtx, st: JsonObject): Profile? = x.profiles.lookup(st.str("prof") ?: "")
    private fun depthOff(st: JsonObject, rhv: Double) = when (st.str("depth")) { "Front" -> rhv; "Middle" -> rhv / 2; else -> 0.0 }

    /** v34 L13916: the base plate under the lower end of a column, four anchor bolts, the column cut at the plate top. */
    private fun base(x: FamCtx) {
        val s = x.a; val q = x.q; val p1 = s.vec("p1"); val p2 = s.vec("p2"); val lo = if (p1.z <= p2.z) p1 else p2; val p = prof(x, s)
        val ch = orD(p?.h, 300.0); val cb = orD(p?.b, 300.0)
        val f = LF.of(D3(lo.x, lo.y, 0.0), D3(1.0, 0.0, 0.0)); val z0 = lo.z; val t = x.n("t"); val l = ch / 2 + x.n("ext"); val w = cb / 2 + x.n("side")
        val pl = f.box(-l, l, z0, z0 + t, -w, w)
        val bolt = x.n("bolt"); val bs = ArrayList<D3>(); val sx = ch / 2 + x.n("ext") * 0.55; val sy = max(cb / 2 - 40, 50.0)
        for (a in listOf(-1, 1)) for (b in listOf(-1, 1)) bs += f.lp(a * sx, z0 + t + 1.2 * bolt, b * sy)
        val bb = x.group(bs, D3(0.0, 0.0, -1.0), bolt, t + x.n("anch"))
        x.plate("BASE PLATE", pl, t, "PL" + jsStr(q["t"]), minus = bb.holes)
        val info = linkedMapOf<String, JsonElement>("pts" to JsonArray(bb.pts.map { jsArr(it.x, it.y, it.z) }), "ax" to jsArr(bb.ax.x, bb.ax.y, bb.ax.z),
            "d" to jsNum(bolt), "n" to jsNum(bs.size.toDouble()), "grade" to (q["grade"] ?: JsonNull), "L" to jsNum(floor(x.n("anch") + t + 2.6 * bolt + 0.5)))
        x.parts += ConnPart("ANCHOR BOLTS", bb.pieces, JsonObject(info), group = bb)
        x.cut(x.aId, f.box(-l * 4, l * 4, z0 - 1000, z0 + t, -w * 4, w * 4))
        val loop = listOf(doubleArrayOf(-ch / 2, z0 + t, -cb / 2), doubleArrayOf(ch / 2, z0 + t, -cb / 2), doubleArrayOf(ch / 2, z0 + t, cb / 2), doubleArrayOf(-ch / 2, z0 + t, cb / 2)).map { f.lp(it[0], it[1], it[2]) }
        x.welds += Weld("Column to base plate", x.n("a"), orD(p?.P, 1.5) * 1000, loop)
    }

    /** v69 S.stGen.span (the portal frame's span; 20 000 when there is none): the default haunch length is round(span / 20). */
    internal fun genSpan(x: FamCtx): Double {
        val t = x.docField("stGen") ?: return 20000.0
        val o = runCatching { Json.parseToJsonElement(t) }.getOrNull() ?: return 20000.0
        if (o is JsonNull || (o is JsonPrimitive && !jsTruthy(o))) return 20000.0
        val sp = (o as? JsonObject)?.get("span") ?: return Double.NaN
        return jsPlus(sp)
    }

    /** v34 L13927: eaves haunch: end plate on the column flange, haunch web + flange under the rafter, cap plate, four stiffeners. */
    private fun knee(x: FamCtx) {
        val cs = x.a; val rs = x.b ?: run { x.nullResult = true; return }; val rafId = x.bId!!; val q = x.q
        val cp1 = cs.vec("p1"); val cp2 = cs.vec("p2"); val top = if (cp1.z >= cp2.z) cp1 else cp2
        val rp1 = rs.vec("p1"); val rp2 = rs.vec("p2"); val near = rp1.distanceTo(top) <= rp2.distanceTo(top); val r0 = if (near) rp1 else rp2; val r1 = if (near) rp2 else rp1
        val top0 = D3(top.x, top.y, 0.0); val f = LF.of(top0, D3(r1.x - r0.x, r1.y - r0.y, 0.0))
        val run = hypot(r1.x - r0.x, r1.y - r0.y).let { if (it == 0.0) 1.0 else it }; val tanp = (r1.z - r0.z) / run; val cosp = cos(atan(tanp))
        val cp = prof(x, cs); val rp = prof(x, rs); val turned = cs.str("rot") == "Front" || cs.str("rot") == "Back"
        // v69 `cp.b * U` with no fallback: a missing profile gives NaN
        val ch = (if (turned) cp?.b else cp?.h) ?: Double.NaN; val cb = (if (turned) cp?.h else cp?.b) ?: Double.NaN; val ctf = orD(cp?.tf, 12.0); val ctw = orD(cp?.tw, 8.0)
        val rh = orD(rp?.h, 400.0); val rb = orD(rp?.b, 180.0); val rtf = orD(rp?.tf, 13.0); val rtw = orD(rp?.tw, 8.6); val rhv = rh / cosp
        val sOff = (r0 - top0) dot f.h; val dOff = depthOff(rs, rhv)
        fun zTop(s: Double) = r0.z + (s - sOff) * tanp + dOff
        fun zBot(s: Double) = zTop(s) - rhv
        val tp = x.n("tp"); val sf = ch / 2; val se = sf + tp
        val dh = num(or(q["depth"], rp?.h, 400.0)); val lh = num(or(q["len"], floor(genSpan(x) / 20 + 0.5)))
        x.cut(rafId, f.box(-50000.0, se, -50000.0, 50000.0, -2000.0, 2000.0))   // the rafter stops at the end plate
        val zc = zTop(sf); val capT = x.n("cap"); x.cut(x.aId, f.box(-2000.0, 2000.0, zc - capT, zc + 20000, -2000.0, 2000.0))   // the column stops under its cap plate
        val ep = f.box(sf, se, zBot(se) - dh - 15, zTop(sf) + 10, -rb / 2 - 10, rb / 2 + 10)
        val z1 = zTop(sf) - 60; val z2 = zBot(se) - dh + 60; val n = max(2, jsInt(x.n("rows"))); val g = min(x.n("gauge"), cb - 50) / 2
        val bs = ArrayList<D3>(); for (i in 0 until n) for (y in listOf(-g, g)) bs += f.lp(se + 0.1, z1 + (z2 - z1) * i / (n - 1), y)
        val bolt = x.n("bolt"); val bb = x.group(bs, -f.h, bolt, tp + ctf + 2)
        for (hc in bb.holes) x.cut(x.aId, hc)
        x.plate("END PLATE", ep, tp, "PL" + jsStr(q["tp"]), minus = bb.holes); x.bolts(bb, bs.size, (tp + ctf) + 2 * bolt)
        val hx = se + lh
        x.plate("HAUNCH WEB", f.prism(listOf(doubleArrayOf(se, zBot(se)), doubleArrayOf(hx, zBot(hx)), doubleArrayOf(se, zBot(se) - dh)), -rtw / 2, rtw / 2), 0.0, "PL" + str(or(rp?.tw, 9.0)))
        val ang = atan2(dh - (zBot(se) - zBot(hx)), hx - se); val nx = sin(ang); val nz = -cos(ang)   // the haunch flange under the web's lower edge
        x.plate("HAUNCH FLANGE", f.prism(listOf(doubleArrayOf(se, zBot(se) - dh), doubleArrayOf(hx, zBot(hx)), doubleArrayOf(hx + nx * rtf, zBot(hx) + nz * rtf), doubleArrayOf(se, zBot(se) - dh + nz * rtf / cos(ang))), -rb / 2, rb / 2),
            0.0, "PL" + str(or(rp?.tf, 13.0)))
        x.plate("CAP PLATE", f.box(-ch / 2 - 10, ch / 2 + 10, zc - capT, zc, -cb / 2 - 10, cb / 2 + 10), 0.0, "PL" + jsStr(q["cap"]))
        val st = x.n("stiff")
        for (zz in listOf(zBot(sf) + rtf / 2, zBot(se) - dh + rtf / 2)) for (sy in listOf(-1, 1))
            x.plate("STIFFENER", f.box(-ch / 2 + ctf, ch / 2 - ctf, zz - st / 2, zz + st / 2, if (sy > 0) ctw / 2 else -cb / 2 + 5, if (sy > 0) cb / 2 - 5 else -ctw / 2), 0.0, "PL" + jsStr(q["stiff"]))
        val a = x.n("a"); val a5 = min(5.0, num(or(q["a"], 5.0)))
        val loop = listOf(doubleArrayOf(se, zTop(se), -rb / 2), doubleArrayOf(se, zTop(se), rb / 2), doubleArrayOf(se, zBot(se) - dh, rb / 2), doubleArrayOf(se, zBot(se) - dh, -rb / 2)).map { f.lp(it[0], it[1], it[2]) }
        x.welds += Weld("Rafter + haunch to end plate", a, orD(rp?.P, 1.4) * 1000 + 2 * dh + rb, loop)
        x.welds += Weld("Haunch to rafter", a5, 2 * lh); x.welds += Weld("Cap plate to column", a5, orD(cp?.P, 1.5) * 1000)
        x.welds += Weld("Stiffeners", a5, 4 * 2 * ((ch - 2 * ctf) + (cb - ctw) / 2))
    }

    /** The apex of two rafters (v34 L13953 / v54 L15822): the end of A nearest to B, A's far end, the frame s along A outward. */
    private class Apex(val apx: D3, val f: LF, val tanp: Double)
    private fun apexFrame(a: JsonObject, b: JsonObject): Apex {
        val a1 = a.vec("p1"); val a2 = a.vec("p2"); var best: Pair<Double, D3>? = null
        for (pa in listOf(a1, a2)) for (pb in listOf(b.vec("p1"), b.vec("p2"))) { val d = pa.distanceTo(pb); if (best == null || d < best.first) best = d to pa }
        val apx = best!!.second; val far = if (apx === a1) a2 else a1
        val f = LF.of(D3(apx.x, apx.y, 0.0), D3(apx.x - far.x, apx.y - far.y, 0.0)); val run = hypot(apx.x - far.x, apx.y - far.y).let { if (it == 0.0) 1.0 else it }
        return Apex(apx, f, (apx.z - far.z) / run)
    }

    /** v34 L13951 (+ v54 L15845: q.ph > 0 → [apex54]): two end plates at the apex, bolted, a haunch under each rafter. */
    private fun apex(x: FamCtx) {
        val ast = x.a; val bst = x.b ?: run { x.nullResult = true; return }; val bId = x.bId!!; val q = x.q
        if (x.n("ph") > 0) return apex54(x, ast, bst, bId)
        val ap = apexFrame(ast, bst); val f = ap.f; val tanp = ap.tanp; val cosp = cos(atan(tanp)); val rp = prof(x, ast)
        val rh = orD(rp?.h, 400.0); val rb = orD(rp?.b, 180.0); val rtw = orD(rp?.tw, 8.6); val rtf = orD(rp?.tf, 13.0); val rhv = rh / cosp; val dOff = depthOff(ast, rhv)
        fun zTop(s: Double) = ap.apx.z - abs(s) * tanp + dOff
        fun zBot(s: Double) = zTop(s) - rhv
        val tp = x.n("tp"); val da = x.n("depth"); val la = x.n("len")
        x.cut(x.aId, f.box(-tp, 50000.0, -50000.0, 50000.0, -2000.0, 2000.0)); x.cut(bId, f.box(-50000.0, tp, -50000.0, 50000.0, -2000.0, 2000.0))
        val e1 = f.box(-tp, 0.0, zBot(tp) - da - 15, zTop(0.0) + 10, -rb / 2 - 10, rb / 2 + 10); val e2 = f.box(0.0, tp, zBot(tp) - da - 15, zTop(0.0) + 10, -rb / 2 - 10, rb / 2 + 10)
        val n = max(2, jsInt(x.n("rows"))); val z1 = zTop(tp) - 60; val z2 = zBot(tp) - da + 60; val g = x.n("gauge") / 2
        val bl = ArrayList<D3>(); for (i in 0 until n) for (y in listOf(-g, g)) bl += f.lp(-tp - 0.1, z1 + (z2 - z1) * i / (n - 1), y)
        val bolt = x.n("bolt"); val bb = x.group(bl, f.h, bolt, 2 * tp + 2)
        val ptp = "PL" + jsStr(q["tp"])
        x.plate("END PLATE", e1, tp, ptp, minus = bb.holes); x.plate("END PLATE", e2, tp, ptp, minus = bb.holes); x.bolts(bb, bl.size, 2 * tp + 2 * bolt)
        val pw = "PL" + str(or(rp?.tw, 9.0)); val pf = "PL" + str(or(rp?.tf, 13.0))
        for (sg in listOf(-1, 1)) { val s0 = sg * tp; val s1 = sg * (tp + la)
            fun p(s: Double, z: Double) = doubleArrayOf(s, z)
            x.plate("APEX HAUNCH WEB", f.prism(if (sg < 0) listOf(p(s1, zBot(s1)), p(s0, zBot(s0)), p(s0, zBot(s0) - da)) else listOf(p(s0, zBot(s0)), p(s1, zBot(s1)), p(s0, zBot(s0) - da)), -rtw / 2, rtw / 2), 0.0, pw)
            x.plate("APEX HAUNCH FLANGE", f.prism(if (sg < 0) listOf(p(s1, zBot(s1)), p(s0, zBot(s0) - da), p(s0, zBot(s0) - da - rtf), p(s1, zBot(s1) - rtf))
                else listOf(p(s0, zBot(s0) - da - rtf), p(s0, zBot(s0) - da), p(s1, zBot(s1)), p(s1, zBot(s1) - rtf)), -rb / 2, rb / 2), 0.0, pf) }
        val a = x.n("a")
        for (sg in listOf(-1, 1)) x.welds += Weld("Rafter + haunch to end plate", a, orD(rp?.P, 1.4) * 1000 + 2 * da + rb,
            listOf(doubleArrayOf(sg * tp, zTop(tp), -rb / 2), doubleArrayOf(sg * tp, zTop(tp), rb / 2), doubleArrayOf(sg * tp, zBot(tp) - da, rb / 2), doubleArrayOf(sg * tp, zBot(tp) - da, -rb / 2)).map { f.lp(it[0], it[1], it[2]) })
        x.welds += Weld("Haunches to rafters", min(5.0, num(or(q["a"], 5.0))), 4 * la)
    }

    /** v54ccw (L15793): the polygon counter-clockwise. */
    private fun ccw(p: List<DoubleArray>): List<DoubleArray> { var a = 0.0; for (i in p.indices) { val u = p[i]; val r = p[(i + 1) % p.size]; a += u[0] * r[1] - r[0] * u[1] }; return if (a < 0) p.reversed() else p }

    /** v54 L15819 `v54apex`: Tekla 106 apex: end plates of height ph (placed from the top / bottom / centred), rows at given distances, haunches square to the rafter. */
    private fun apex54(x: FamCtx, ast: JsonObject, bst: JsonObject, bId: String) {
        val q = x.q; val ap = apexFrame(ast, bst); val f = ap.f; val tanp = ap.tanp; val th = atan(tanp); val cs = cos(th); val sn = sin(th)
        val rp = prof(x, ast); val rh = orD(rp?.h, 400.0); val rb = orD(rp?.b, 180.0); val rhv = rh / cs; val dOff = depthOff(ast, rhv)
        fun zTop(s: Double) = ap.apx.z - abs(s) * tanp + dOff
        fun zBot(s: Double) = zTop(s) - rhv
        val tp = x.n("tp"); val tek = x.s("hrule") == "tekla" && jsPlus(q["len"]).let { !it.isNaN() && it > 0 }   // v70 C03: Tekla 106's haunch (horizontal from the apex, plumb at the plate)
        val lw = if (tek) (jsPlus(q["len"]) - tp) / cs else jsPlus(q["len"]).let { if (it.isNaN()) 0.0 else it }; val dp = if (lw > 0) (if (tek) x.n("depth") * cs else x.n("depth")) else 0.0; val dv = dp / cs
        x.cut(x.aId, f.box(-tp, 50000.0, -50000.0, 50000.0, -2000.0, 2000.0)); x.cut(bId, f.box(-50000.0, tp, -50000.0, 50000.0, -2000.0, 2000.0))
        val ph = x.n("ph"); val zHB = zBot(tp) - dv; val z0: Double; val z1: Double
        val topE = q["top"]; val topS = if (topE == null || topE is JsonNull) "" else jsStr(topE)
        fun plusOr0(e: JsonElement?) = jsPlus(e).let { if (it.isNaN()) 0.0 else it }
        if (topS.trim() != "") { z1 = zTop(0.0) + plusOr0(topE); z0 = z1 - ph }
        else if (jsStr(q["bot"]).trim() != "" && dp > 0) { z0 = zHB - plusOr0(q["bot"]); z1 = z0 + ph }
        else { val mid = (zTop(0.0) + zHB) / 2; z0 = mid - ph / 2; z1 = mid + ph / 2 }
        val w2 = num(or(q["pw"], orD(rp?.b, 180.0) + 10)) / 2
        val e1 = f.box(-tp, 0.0, z0, z1, -w2, w2); val e2 = f.box(0.0, tp, z0, z1, -w2, w2)
        val raS = if (jsTruthy(q["rowsAt"])) jsStr(q["rowsAt"]) else ""
        var ra = raS.trim().split(Regex("""[\s,]+""")).map { StGrid.jsNumber(it) }.filter { it > 0 }
        if (ra.isEmpty()) { val n = max(2, jsInt(x.n("rows"))); ra = List(n) { i -> 50 + (ph - 100) * i / (n - 1) } }
        val g = x.n("gauge") / 2; val bl = ArrayList<D3>(); for (r in ra) for (y in listOf(-g, g)) bl += f.lp(-tp - 0.1, z1 - r, y)
        val bolt = x.n("bolt"); val bb = x.group(bl, f.h, bolt, 2 * tp + 2)
        val pe = "FLT" + jsStr(q["tp"]) + "*" + jsStr(q["ph"])
        x.plate("END PLATE", e1, tp, pe, minus = bb.holes); x.plate("END PLATE", e2, tp, pe, minus = bb.holes); x.bolts(bb, bl.size, 2 * tp + 2 * bolt)
        if (lw > 0) for (sg in listOf(-1, 1)) {
            val s0 = sg * tp; val p0 = doubleArrayOf(s0, zBot(s0)); val dir = doubleArrayOf(sg * cs, -sn); val p1 = doubleArrayOf(p0[0] + dir[0] * lw, p0[1] + dir[1] * lw); val pd = doubleArrayOf(s0, p0[1] - dv)
            val hw = or(q["hw"], rp?.tw, 8.0); val hwn = num(hw)
            x.plate("APEX HAUNCH WEB", f.prism(ccw(listOf(p0, p1, pd)), -hwn / 2, hwn / 2), 0.0, "PL" + str(hw))
            val hx = p1[0] - pd[0]; val hz = p1[1] - pd[1]; val hl = hypot(hx, hz); var m = doubleArrayOf(hz / hl, -hx / hl); if (m[1] > 0) m = doubleArrayOf(-m[0], -m[1])
            val hf = or(q["hf"], rp?.tf, 10.0); val tf = num(hf)
            val q1 = doubleArrayOf(p1[0] + m[0] * tf, p1[1] + m[1] * tf); val k = (s0 - q1[0]) / ((pd[0] - p1[0]).let { if (it == 0.0 || it.isNaN()) 1e-9 else it }); val q0 = doubleArrayOf(s0, q1[1] + k * (pd[1] - p1[1]))
            val hb2 = num(or(q["hb"], rp?.b, 150.0)) / 2
            if (tek) { val mm = (p1[1] - pd[1]) / (p1[0] - pd[0]); val tv = tf * kotlin.math.sqrt(1 + mm * mm)   // v70 C03: both flange ends plumb
                x.plate("APEX HAUNCH FLANGE", f.prism(ccw(listOf(pd, p1, doubleArrayOf(p1[0], p1[1] - tv), doubleArrayOf(pd[0], pd[1] - tv))), -hb2, hb2), 0.0, "PL" + str(hf)) }
            else x.plate("APEX HAUNCH FLANGE", f.prism(ccw(listOf(pd, p1, q1, q0)), -hb2, hb2), 0.0, "PL" + str(hf))
        }
        if (jsInt(StBolts.tkNum(q["ast"], 0.0)) != 0 && topS.trim() != "") for (sg in listOf(-1, 1)) {   // v70 C03: end plate stiffeners above the rafter (web section "C03")
            val l = StBolts.tkNum(q["asl"], 60.0); val ch = StBolts.tkNum(q["ach"], 30.0); val att = StBolts.tkNum(q["att"], 10.0); val a0 = sg * tp; val b0 = sg * (tp + l)
            if (!(z1 - zTop(tp) > ch + 1)) continue
            val ashS = q["ash"]?.let { if (it is JsonNull) "" else jsStr(it) } ?: ""; val zb = if (ashS.trim() != "") z1 - jsPlus(q["ash"]) else zTop(tp + l)
            x.plate("APEX STIFFENER", f.prism(ccw(listOf(doubleArrayOf(a0, zTop(tp)), doubleArrayOf(b0, zb), doubleArrayOf(b0, z1 - ch), doubleArrayOf(b0 - sg * ch, z1), doubleArrayOf(a0, z1))), -att / 2, att / 2), att, "FLT" + jsStr(q["att"] ?: JsonPrimitive(10)) + "*" + jsStr(q["asl"] ?: JsonPrimitive(60)))
        }
        run {   // v70 X01: each rafter fitted to its end plate's face (web fitR: every Tekla apex, ph > 0)
            val endA = if (ast.vec("p1").distanceTo(ap.apx) < 1e-9) 1 else 2; val endB = if (bst.vec("p1").distanceTo(ap.apx) <= bst.vec("p2").distanceTo(ap.apx)) 1 else 2
            for ((id, e, sg) in listOf(Triple(x.aId, endA, -1.0), Triple(bId, endB, 1.0))) x.fit(id, Fit(e, f.lp(sg * tp, 0.0), f.h * (-sg), rhv * abs(sn) + 2 * tp + 20)) }
        for (sg in listOf(-1, 1)) x.welds += Weld("Rafter + haunch to end plate", x.n("a"), orD(rp?.P, 1.4) * 1000 + 2 * dp + rb,
            listOf(doubleArrayOf(sg * tp, zTop(tp), -rb / 2), doubleArrayOf(sg * tp, zTop(tp), rb / 2), doubleArrayOf(sg * tp, zHB, rb / 2), doubleArrayOf(sg * tp, zHB, -rb / 2)).map { f.lp(it[0], it[1], it[2]) })
        if (lw > 0) x.welds += Weld("Haunches to rafters", 5.0, 4 * lw)
    }
}
