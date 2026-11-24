package com.solidsketchapp.steel.apps

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.steel.conn.Joints
import com.solidsketchapp.steel.conn.LF
import com.solidsketchapp.steel.data.Profile
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tan

/**
 * S10: v69's portal frame generator, the live chain:
 *  - parts: v33 stPortalParts (L13831) wrapped by v42 (L15269: side girts, wall bracing on the column webs, gable columns
 *    and gable girts) and v43 (L15354: crane brackets, crane beams, crane rails);
 *  - connections: v34 stPortalConns (L13988: base, knee, apex) → v35 stPortalConns2 (L14100: purlin cleats, wall / roof
 *    gussets, tie end plates) → v42 (L15293: T11 for braces on a web, Tpc girt cleats, gable column bases and tops) →
 *    v43 (L15366: T144 bracket end plates, crane seats, rail clips with their end stops);
 *  - the step: v33 stPortal (L13859): the generated parts (st.gen 1) and their connections are replaced, S.stGrid and
 *    S.stGen are set, the connections are built.
 * q = the sheet's values over [DEF] (v69 PF_DEF after v42 / v43).
 */
object PortalFrame {
    /** v69 PF_DEF (L13830) + v42 (L15250) + v43 (L15323). */
    val DEF: JsonObject = JsonObject(linkedMapOf<String, JsonElement>(
        "span" to jsNum(25000.0), "bays" to jsNum(6.0), "bay" to jsNum(6000.0), "eave" to jsNum(7450.0), "pitch" to jsNum(12.0), "col" to JsonPrimitive("HEA260"), "raf" to JsonPrimitive("IPE400"),
        "pur" to JsonPrimitive("IPE160"), "purS" to jsNum(1500.0), "tie" to JsonPrimitive("CHS114.3x3.6"), "rb" to JsonPrimitive("CHS114.3x3.6"), "wb" to JsonPrimitive("PL80*10"),
        "rbOn" to JsonPrimitive(true), "wbOn" to JsonPrimitive(true), "purOn" to JsonPrimitive(true), "tieOn" to JsonPrimitive(true),
        "girtOn" to JsonPrimitive(true), "girt" to JsonPrimitive("CC200-2-20-60"), "girtS" to jsNum(1500.0), "gableOn" to JsonPrimitive(true), "gcol" to JsonPrimitive("HEA200"), "gcolS" to jsNum(6000.0),
        "craneOn" to JsonPrimitive(false), "crH" to jsNum(5500.0), "crBeam" to JsonPrimitive("HEB400"), "crBr" to JsonPrimitive("HEA300"), "crRail" to JsonPrimitive("PL50*60"), "crE" to jsNum(350.0), "crL" to jsNum(600.0)))

    /** The parts (members' st, v69 order) and the grid (S.stGrid) the frame gets. */
    class Parts(val parts: List<LinkedHashMap<String, JsonElement>>, val grid: JsonObject)
    /** One connection the frame makes: v69 stConnAdd(type, parts, q) (q as given, the defaults apply when it is built) + extra fields (railclip ends). */
    class Conn(val type: String, val parts: List<String>, val q: JsonObject = JsonObject(emptyMap()), val extra: Map<String, JsonElement> = emptyMap())

    private fun n(q: JsonObject, k: String): Double = (q[k] as? JsonPrimitive)?.let { p -> p.doubleOrNull ?: if (p.isString) p.content.toDoubleOrNull() ?: Double.NaN else if (p.booleanOrNull == true) 1.0 else 0.0 } ?: Double.NaN
    private fun s(q: JsonObject, k: String): String = (q[k] as? JsonPrimitive)?.contentOrNull ?: ""
    /** JS truthiness of q[k] (the sheet's checkboxes; numbers and strings as JS sees them). */
    private fun on(q: JsonObject, k: String): Boolean { val p = q[k] as? JsonPrimitive ?: return false; if (p.isString) return p.content.isNotEmpty(); p.booleanOrNull?.let { return it }; val d = p.doubleOrNull ?: return false; return d != 0.0 && !d.isNaN() }
    /** JS `a || d` for numbers (0, NaN and undefined are falsy). */
    private fun or(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v
    private fun pt(x: Double, y: Double, z: Double) = jsArr(x, y, z)
    private fun v3(e: JsonElement?): D3 = (e as JsonArray).let { D3(num(it[0]), num(it[1]), num(it[2])) }
    private fun num(e: JsonElement): Double = (e as JsonPrimitive).doubleOrNull ?: Double.NaN
    private fun fmt(d: Double) = JsNumber.format(d)

    /** v69 stPortalParts, the live chain (v33 + v42 + v43). Units mm (stU() = 1). */
    fun parts(q0: JsonObject, data: SteelData): Parts {
        val q = JsonObject(LinkedHashMap(DEF).apply { putAll(q0) })
        val prof = { k: String -> data.profiles.lookup(s(q, k)) }
        val span = n(q, "span"); val bay = n(q, "bay"); val H = n(q, "eave"); val p = n(q, "pitch") * Math.PI / 180; val bays = n(q, "bays").toInt()
        val rise = span / 2 * tan(p); val Za = H + rise
        val ys = (0..bays).map { it * bay }; val zRaf = { x: Double -> if (x <= span / 2) H + x * tan(p) else H + (span - x) * tan(p) }
        val out = ArrayList<LinkedHashMap<String, JsonElement>>()
        fun base(set: String, extra: Map<String, JsonElement>): LinkedHashMap<String, JsonElement> {
            val m = linkedMapOf<String, JsonElement>("kind" to JsonPrimitive("member"), "gen" to jsNum(1.0)); m.putAll(data.propertySet(set)!!.raw)
            for (k in listOf("pOff", "dOff", "dx1", "dx2", "rAng")) m[k] = jsNum(0.0); m.putAll(extra); return m
        }
        fun ex(vararg kv: Pair<String, Any>): Map<String, JsonElement> = linkedMapOf<String, JsonElement>().apply { for ((k, v) in kv) put(k, when (v) { is JsonElement -> v; is String -> JsonPrimitive(v); is Number -> jsNum(v.toDouble()); else -> JsonPrimitive(v.toString()) }) }
        val cp = prof("col"); val rp = prof("raf")
        // ---- v33 (L13831)
        for (y in ys) {
            out += base("COLUMN", ex("prof" to s(q, "col"), "rot" to "Top", "plane" to "Middle", "depth" to "Middle", "p1" to pt(0.0, y, 0.0), "p2" to pt(0.0, y, H)))
            out += base("COLUMN", ex("prof" to s(q, "col"), "rot" to "Top", "plane" to "Middle", "depth" to "Middle", "p1" to pt(span, y, 0.0), "p2" to pt(span, y, H)))
            out += base("RAFTER", ex("prof" to s(q, "raf"), "p1" to pt(0.0, y, H), "p2" to pt(span / 2, y, Za)))
            out += base("RAFTER", ex("prof" to s(q, "raf"), "p1" to pt(span, y, H), "p2" to pt(span / 2, y, Za)))
        }
        if (on(q, "purOn")) {   // purlins on the rafters' top, square to the slope, from the eaves to the ridge on both sides
            val Ls = span / 2 / cos(p); val a = 200.0; val b = 150.0; val nn = max(1.0, ceil((Ls - a - b) / n(q, "purS"))).toInt()
            for (i in 0..nn) { val ss = a + (Ls - a - b) * i / nn; val x = ss * cos(p)
                out += base("PURLIN", ex("prof" to s(q, "pur"), "p1" to pt(x, 0.0, zRaf(x)), "p2" to pt(x, ys.last(), zRaf(x)), "rAng" to -n(q, "pitch")))
                val x2 = span - x; out += base("PURLIN", ex("prof" to s(q, "pur"), "p1" to pt(x2, 0.0, zRaf(x2)), "p2" to pt(x2, ys.last(), zRaf(x2)), "rAng" to n(q, "pitch"))) }
        }
        val rh = or(rp?.h, 400.0)
        if (on(q, "tieOn")) for (i in 0 until bays) for (x in listOf(0.0, span)) {
            val dx = -(or(cp?.tw, 7.5) / 2 + 12)
            out += base("BEAM", ex("name" to "EAVES TIE", "prof" to s(q, "tie"), "depth" to "Middle", "p1" to pt(x, ys[i], H - (rh + 150)), "p2" to pt(x, ys[i + 1], H - (rh + 150)), "dx1" to dx, "dx2" to dx, "cls" to 13))
        }
        val ends = (if (bays >= 2) listOf(0, bays - 1) else listOf(0)).distinct()
        if (on(q, "rbOn")) for (i in ends) for (side in listOf(0, 1)) {   // roof X bracing: two panels per slope, under the rafter top
            val xs = if (side == 1) listOf(span - 700, span * 0.75, span / 2 + 700) else listOf(700.0, span * 0.25, span / 2 - 700)
            val dz = -(rh / 2); val dd = data.profiles.lookup(s(q, "rb"))?.d.let { or(it, 114.0) }
            for (k in 0 until 2) { val a = pt(xs[k], ys[i], zRaf(xs[k]) + dz); val b = pt(xs[k + 1], ys[i + 1], zRaf(xs[k + 1]) + dz)
                val c = pt(xs[k + 1], ys[i], zRaf(xs[k + 1]) + dz - dd); val d = pt(xs[k], ys[i + 1], zRaf(xs[k]) + dz - dd)
                out += base("BRACING", ex("name" to "ROOF BRACING", "prof" to s(q, "rb"), "cls" to 13, "depth" to "Middle", "p1" to a, "p2" to b))
                out += base("BRACING", ex("name" to "ROOF BRACING", "prof" to s(q, "rb"), "cls" to 13, "depth" to "Middle", "p1" to c, "p2" to d)) }
        }
        val chh = or(cp?.h, 250.0)
        if (on(q, "wbOn")) for (i in ends) for (x in listOf(0.0, span)) {   // wall X bracing, flat bars outside the columns
            val xo = if (x == 0.0) -(chh / 2 + 15) else span + (chh / 2 + 15); val z0 = 300.0; val z1 = H - (rh + 300)
            val wbb = or(data.profiles.lookup(s(q, "wb"))?.b, 10.0); val x2 = xo + (if (x == 0.0) -1 else 1) * wbb
            out += base("BRACING", ex("name" to "WALL BRACING", "prof" to s(q, "wb"), "cls" to 14, "depth" to "Middle", "p1" to pt(xo, ys[i], z0), "p2" to pt(xo, ys[i + 1], z1)))
            out += base("BRACING", ex("name" to "WALL BRACING", "prof" to s(q, "wb"), "cls" to 14, "depth" to "Middle", "p1" to pt(x2, ys[i + 1], z0), "p2" to pt(x2, ys[i], z1)))
        }
        val grid = JsonObject(linkedMapOf("x" to JsonPrimitive("0 ${fmt(span / 2)} ${fmt(span / 2)}"), "y" to JsonPrimitive("0 ${bays}*${fmt(bay)}"),
            "z" to JsonPrimitive("0 ${fmt(H)} ${fmt(jsRound(span / 2 * tan(p)))}"), "lx" to JsonPrimitive("A B C"),
            "ly" to JsonPrimitive((1..bays + 1).joinToString(" "))))
        // ---- v42 (L15269): girts, wall bracing on the column webs, gable columns and gable girts
        val L = bays * bay
        if (on(q, "girtOn") || on(q, "gableOn")) {
            val kp = prof("gcol"); val kpH = kp?.h ?: 190.0; val ch = or(cp?.h, 250.0); val cb = or(cp?.b, 250.0)
            if (on(q, "girtOn")) {
                for (st in out) if ((st["name"] as? JsonPrimitive)?.content == "WALL BRACING") { val p1 = v3(st["p1"]); val p2 = v3(st["p2"]); val left = p1.x < span / 2
                    val nom = if (left) -(ch / 2 + 15) else span + (ch / 2 + 15); val d = p1.x - nom; val x0 = (if (left) 0.0 else span) + d
                    st["p1"] = pt(x0, p1.y, p1.z); st["p2"] = pt(x0, p2.y, p2.z); st["onWeb"] = jsNum(1.0) }
                val zs = ArrayList<Double>(); var z = 1200.0; while (z <= H - 650) { zs += z; z += n(q, "girtS") }
                for (left in listOf(true, false)) for (zz in zs) out += base("GIRT", ex("name" to "GIRT", "prof" to s(q, "girt"), "cls" to 5, "rot" to (if (left) "Back" else "Front"), "plane" to (if (left) "Left" else "Right"), "depth" to "Middle",
                    "p1" to pt(if (left) -ch / 2 else span + ch / 2, 0.0, zz), "p2" to pt(if (left) -ch / 2 else span + ch / 2, L, zz)))
            }
            if (on(q, "gableOn")) {
                val nd = max(2.0, jsRound(n(q, "span") / or(n(q, "gcolS"), 6000.0))).toInt(); val xs = ArrayList<Double>()
                for (i in 1 until nd) { val x = span * i / nd; if (abs(x - span / 2) > 1200) xs += x }
                val yOut = max(kpH / 2, cb / 2)
                for ((y, sg) in listOf(0.0 to -1, L to 1)) {
                    for (x in xs) out += base("COLUMN", ex("name" to "GABLE COLUMN", "prof" to s(q, "gcol"), "ap" to "GC", "pp" to "gc", "rot" to "Front", "plane" to "Middle", "depth" to "Middle",
                        "p1" to pt(x, y, 0.0), "p2" to pt(x, y, zRaf(x) - rh / cos(p))))
                    if (on(q, "girtOn")) { var z = 1200.0
                        while (z <= H + (span / 2) * tan(p) - 900) {
                            var x0 = 0.0; var x1 = span; if (z > H - 650) { val dx = (z - (H - 650)) / tan(p); x0 = dx; x1 = span - dx }
                            val inside = xs.filter { it > x0 + 300 && it < x1 - 300 }
                            if (inside.isNotEmpty()) { val a0 = max(x0, inside.min() - 1500); val a1 = min(x1, inside.max() + 1500)
                                val xa = if (z > H - 650) a0 else 0.0; val xb = if (z > H - 650) a1 else span
                                out += base("GIRT", ex("name" to "GABLE GIRT", "prof" to s(q, "girt"), "cls" to 5, "rot" to (if (sg < 0) "Front" else "Back"), "plane" to (if (sg < 0) "Right" else "Left"), "depth" to "Middle",
                                    "p1" to pt(xa, y + sg * yOut, z), "p2" to pt(xb, y + sg * yOut, z))) }
                            z += n(q, "girtS") } }
                }
            }
        }
        // ---- v43 (L15354): crane brackets, crane beams, crane rails
        if (on(q, "craneOn")) {
            val bp = prof("crBeam"); val railP = prof("crRail"); val ch = or(cp?.h, 250.0)
            val zRailTop = n(q, "crH"); val zBeamTop = zRailTop - or(railP?.h, 50.0); val zBr = zBeamTop - or(bp?.h, 400.0) - 20
            for (left in listOf(true, false)) { val xF = if (left) ch / 2 else span - ch / 2; val dir = if (left) 1 else -1; val xc = xF + dir * n(q, "crE")
                for (i in 0..bays) out += base("BEAM", ex("name" to "CRANE BRACKET", "prof" to s(q, "crBr"), "ap" to "CB", "pp" to "cb", "cls" to 7, "depth" to "Behind",
                    "p1" to pt(if (left) 0.0 else span, i * bay, zBr), "p2" to pt(xF + dir * n(q, "crL"), i * bay, zBr), "dx1" to -or(cp?.h, 250.0) / 2))
                for (i in 0 until bays) out += base("BEAM", ex("name" to "CRANE BEAM", "prof" to s(q, "crBeam"), "ap" to "CR", "pp" to "cr", "cls" to 8, "depth" to "Behind",
                    "p1" to pt(xc, i * bay + 5, zBeamTop), "p2" to pt(xc, (i + 1) * bay - 5, zBeamTop)))
                out += base("BEAM", ex("name" to "CRANE RAIL", "prof" to s(q, "crRail"), "ap" to "RL", "pp" to "rl", "cls" to 9, "depth" to "Front", "p1" to pt(xc, 150.0, zBeamTop), "p2" to pt(xc, L - 150, zBeamTop))) }
        }
        return Parts(out, grid)
    }

    /** JS Math.round. */
    fun jsRound(v: Double) = Math.floor(v + 0.5)

    private class Rec(val id: String, val st: JsonObject) { fun name() = (st["name"] as? JsonPrimitive)?.contentOrNull ?: ""; fun p(k: String) = v3(st[k]) }

    /** v69 stRafterLF (L14049): the rafter's working frame (its plan line from p1). */
    private fun rafterLF(st: JsonObject): LF { val p1 = v3(st["p1"]); val p2 = v3(st["p2"]); return LF.of(D3(p1.x, p1.y, 0.0), D3(p2.x - p1.x, p2.y - p1.y, 0.0)) }

    /** The connections v69 makes for the frame (stPortalConns → v35 → v42 → v43), in v69's order. [recs] = (id, st) of the new parts in order. */
    fun conns(recs: List<Pair<String, JsonObject>>, data: SteelData): List<Conn> {
        val rs = recs.map { Rec(it.first, it.second) }; val out = ArrayList<Conn>()
        fun q(vararg kv: Pair<String, Any>) = JsonObject(linkedMapOf<String, JsonElement>().apply { for ((k, v) in kv) put(k, when (v) { is String -> JsonPrimitive(v); is Number -> jsNum(v.toDouble()); else -> JsonPrimitive(v.toString()) }) })
        // ---- v34 stPortalConns (L13988)
        val cols = rs.filter { it.name() == "COLUMN" }; val rafs = rs.filter { it.name() == "RAFTER" }
        for (c in cols) { out += Conn("base", listOf(c.id)); val top = c.p("p2")
            val r = rafs.firstOrNull { it.p("p1").distanceTo(top) < 1e-6 || it.p("p2").distanceTo(top) < 1e-6 }; if (r != null) out += Conn("knee", listOf(c.id, r.id)) }
        val used = HashSet<String>(); for (a in rafs) { if (a.id in used) continue
            val b = rafs.firstOrNull { it !== a && it.id !in used && it.p("p2").distanceTo(a.p("p2")) < 1e-6 }; if (b != null) { used += a.id; used += b.id; out += Conn("apex", listOf(a.id, b.id)) } }
        // ---- v35 stPortalConns2 (L14100)
        val purs = rs.filter { it.name() == "PURLIN" }
        val nearCol = { P: D3 -> var best: Rec? = null; var bd = Double.POSITIVE_INFINITY; for (c in cols) { val p1 = c.p("p1"); val d = hypot(p1.x - P.x, p1.y - P.y); if (d < bd) { bd = d; best = c } }; if (bd < 600) best else null }
        for (pu in purs) for (r in rafs) { val F = rafterLF(r.st); val pa = pu.p("p1"); val dp = pu.p("p2") - pa; val den = dp.dot(F.y); if (abs(den) < 1e-9) continue
            val t = -(pa - F.o).dot(F.y) / den; if (t < -1e-6 || t > 1 + 1e-6) continue
            val X = pa.add(dp, t); val s0 = (X - F.o).dot(F.h); val r1 = r.p("p1"); val r2 = r.p("p2"); val run = hypot(r2.x - r1.x, r2.y - r1.y); if (s0 < -1e-6 || s0 > run + 1e-6) continue
            out += Conn("cleat", listOf(r.id, pu.id)) }
        for (b in rs) {
            if (b.name() == "WALL BRACING") for (P in listOf(b.p("p1"), b.p("p2"))) { val c = nearCol(P); if (c != null) out += Conn("wgusset", listOf(c.id, b.id)) }
            if (b.name() == "ROOF BRACING") for (P in listOf(b.p("p1"), b.p("p2"))) { var best: Rec? = null; var bd = Double.POSITIVE_INFINITY
                for (r in rafs) { val F = rafterLF(r.st); val dy = abs((P - F.o).dot(F.y)); val s = (P - F.o).dot(F.h); val r1 = r.p("p1"); val r2 = r.p("p2"); val run = hypot(r2.x - r1.x, r2.y - r1.y)
                    if (s < -1e-6 || s > run + 1e-6) continue; if (dy < bd) { bd = dy; best = r } }
                if (best != null && bd < 50) out += Conn("tgusset", listOf(best.id, b.id)) }
            if (b.name() == "EAVES TIE") for (P in listOf(b.p("p1"), b.p("p2"))) { val c = nearCol(P); if (c != null) out += Conn("tieend", listOf(c.id, b.id)) }
        }
        // ---- v42 (L15293): T11 for braces on a column web, girt cleats (Tpc), gable column bases and tops
        val cols2 = rs.filter { it.name() == "COLUMN" || it.name() == "GABLE COLUMN" }
        for (i in out.indices) { val c = out[i]; if (c.type != "wgusset") continue; val br = rs.firstOrNull { it.id == c.parts[1] } ?: continue
            if (br.st["onWeb"] == null) continue; val x1 = br.p("p1").x
            out[i] = Conn("T11", c.parts, q("t" to 10, "nb" to 2, "bolt" to 16, "side" to (sign(x1 - jsRound(x1 / 1000) * 1000).takeIf { it != 0.0 } ?: 1.0))) }
        for (g in rs.filter { it.name() == "GIRT" || it.name() == "GABLE GIRT" }) { val P1 = g.p("p1"); val P2 = g.p("p2"); val d0 = P2 - P1; val Lg = d0.length(); val d = d0.normalize()
            for (c in cols2) { if (g.name() == "GIRT" && c.name() != "COLUMN") continue; val C0 = c.p("p1"); val t = (C0 - P1).dot(d)
                if (g.name() == "GABLE GIRT" && c.name() != "GABLE COLUMN" && !(t > -700 && t < 700) && !(t > Lg - 700 && t < Lg + 700)) continue
                if (t < -700 || t > Lg + 700) continue
                val Q = P1.add(d, t); if (hypot(Q.x - C0.x, Q.y - C0.y) > 600) continue
                val zc0 = min(c.p("p1").z, c.p("p2").z); val zc1 = max(c.p("p1").z, c.p("p2").z); if (Q.z < zc0 || Q.z > zc1) continue
                out += Conn("Tpc", listOf(c.id, g.id), q("t" to 8, "bolt" to 12)) } }
        for (c in cols2.filter { it.name() == "GABLE COLUMN" }) { out += Conn("base", listOf(c.id), q("t" to 15, "ext" to 50, "side" to 25, "bolt" to 20, "anch" to 400))
            val T = c.p("p2"); var best: Rec? = null; var bd = Double.POSITIVE_INFINITY
            for (r in rafs) { val R = Joints.mf(r.st, data.profiles); val v = T - R.c1; val s = v.dot(R.a); if (s < 0 || s > R.c2.distanceTo(R.c1)) continue; val dd = v.add(R.a, -s).length(); if (dd < bd) { bd = dd; best = r } }
            if (best != null) out += Conn("gabletop", listOf(best.id, c.id)) }
        // ---- v43 (L15366): bracket end plates (T144), crane seats, rail clips with end stops
        val xsC = cols.map { it.p("p1").x }; val midX = if (xsC.isEmpty()) Double.NaN else (xsC.min() + xsC.max()) / 2
        val brs = rs.filter { it.name() == "CRANE BRACKET" }; val cbs = rs.filter { it.name() == "CRANE BEAM" }; val rails = rs.filter { it.name() == "CRANE RAIL" }
        for (br in brs) { val bp1 = br.p("p1"); val bp2 = br.p("p2")
            val c = cols.firstOrNull { hypot(it.p("p1").x - bp1.x, it.p("p1").y - bp1.y) < 50 }
            if (c != null) out += Conn("T144", listOf(c.id, br.id), q("tp" to 20, "rows" to 3, "pitch" to 80, "gauge" to 140, "side" to 40, "bolt" to 20, "grade" to "8.8", "stiff" to 1, "ext" to 10, "extb" to 10))
            for (cb in cbs) { val cp1 = cb.p("p1"); if (abs(cp1.x - bp2.x) > 2000 || (cp1.x < midX) != (bp2.x < midX)) continue
                for (P in listOf(cp1, cb.p("p2"))) if (abs(P.y - bp1.y) < 50) out += Conn("craneseat", listOf(br.id, cb.id)) } }
        for (rl in rails) { val mine = cbs.filter { abs(it.p("p1").x - rl.p("p1").x) < 1 }
            mine.forEachIndexed { k, cb -> val last = mine.size - 1
                out += Conn("railclip", listOf(cb.id, rl.id), q("stop" to (if (k == 0 || k == last) 1 else 0)), mapOf("ends" to JsonArray(if (k == 0) listOf(jsNum(1.0)) else if (k == last) listOf(jsNum(2.0)) else emptyList()))) } }
        return out
    }

    /** What [run] did: the parts made (null when nothing changed) and v69's toast. */
    class Result(val parts: Int?, val message: String)

    /**
     * v69 stPortal (L13859): one undo step "Portal frame <span> × <length> m": the generated parts (st.gen 1) go with their
     * connections (and those connections' plates and bolts), the new parts come in, S.stGrid and S.stGen are set, the
     * connections are added (q as given) and built in one regen. A profile that cannot be built leaves the model as it was.
     */
    fun run(q0: JsonObject, data: SteelData, doc: com.solidsketchapp.core.api.DocumentApi, parts: com.solidsketchapp.steel.model.StParts,
            comps: com.solidsketchapp.steel.comp.Components, undo: com.solidsketchapp.core.api.UndoApi?): Result {
        val q = JsonObject(LinkedHashMap(DEF).apply { putAll(q0) }); val t0 = System.nanoTime()
        val P = parts(q, data)
        val built = ArrayList<Pair<JsonObject, com.solidsketchapp.core.api.Mesh>>()
        for (m in P.parts) { val st = JsonObject(m); val mesh = runCatching { parts.mesh(st) }.getOrElse { return Result(null, it.message ?: it.toString()) }; built += st to mesh }
        val label = "Portal frame ${fmt(n(q, "span") / 1000)} × ${fmt(n(q, "bays") * n(q, "bay") / 1000)} m"
        var made = 0
        val step = {
            val stOf = { id: BodyId -> doc.bodyJson(id)?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) as JsonObject }.getOrNull()?.get("st") as? JsonObject } }
            val gone = doc.bodyIds().filter { (stOf(it)?.get("gen") as? JsonPrimitive)?.doubleOrNull == 1.0 }.map { it.value }.toSet()
            val keep = comps.conns().filter { c -> c.parts.none { it in gone } }; val keepIds = keep.map { it.id }.toSet()
            doc.setField("stConns", JsonArray(keep.map { it.raw }).toString())
            for (b in doc.bodyIds()) { val st = stOf(b) ?: continue; val kind = (st["kind"] as? JsonPrimitive)?.contentOrNull
                // v69 L13861 removes the old connections' plates (kind 'conn') but not their bolt groups (kind 'bolts'), which stay
                // behind when Create runs again: copied, and asked in PROGRESS.md (Decisions for the owner to review)
                if (b.value in gone || (kind == "conn" && (st["conn"] as? JsonPrimitive)?.content !in keepIds)) doc.removeBody(b) }
            val ids = built.map { (st, mesh) -> parts.addBuilt(st, mesh) }; made = ids.size
            doc.setField("stGrid", P.grid.toString()); doc.setField("stGen", q.toString())
            val cs = conns(ids.map { it.value }.zip(built.map { it.first }), data)
            if (cs.isNotEmpty()) comps.addMany(cs.map { Triple(it.type, it.parts.map(::BodyId), it.q) }, merge = false, extras = cs.map { it.extra })
        }
        undo?.group(label, step) ?: step()
        return Result(made, "Portal frame: $made parts · ${(System.nanoTime() - t0) / 1_000_000} ms")
    }
}
