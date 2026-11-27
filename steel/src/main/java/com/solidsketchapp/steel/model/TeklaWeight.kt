package com.solidsketchapp.steel.model

import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.conn.Joints
import com.solidsketchapp.steel.data.ProfileCatalog
import com.solidsketchapp.steel.geom.D3
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin

/**
 * Tekla part weights, as web v66 `tkPartWeight` (L17195) with v70 "Weights · Tekla's own rule" (web/v70_tekla_parity.js;
 * proof in docs/steel/PARITY.md "Weights"). Bolts are not weighed (Tekla's bolt list).
 *  - density: 7850 kg/m³; grating 1333; grout / concrete / antimaterial 0 (Tekla's material catalog, R12)
 *  - the solid without bolt holes: the part's mesh; if a TKBOLT drills it, [noHoles] (the part rebuilt with its other cuts) when
 *    that is between the mesh volume and 1.25 x it (v66)
 *  - catalog profiles (not parametric): max(catalog kg/m x length, solid) (v66; kg/m from [KGM] or the profile catalog)
 *  - round and square-hollow parametric members (ROD / D, PD / CHS, SHS / RHS): solid x Tekla's section area / the part's own
 *    section area (v70): Tekla's circles are inscribed polygons, 8 sides for a rod, 12 for a tube below D50, 16 from D50; SHS / RHS
 *    have square corners. The own area is measured on the mesh: 10 mm slabs at 20 / 35 / 50 / 65 / 80 % of the length, the median
 *  - other parametric members and plates: the solid
 */
class TeklaWeight(private val profiles: ProfileCatalog, private val booleans: BooleanEngine) {
    class W(val weight: Double, val net: Double, val catalog: Boolean, val kgm: Double, val len: Double, val tkArea: Double? = null, val ownArea: Double? = null, val holesFilled: Double = 0.0,
            /** v70 X01: a built-up (BI) member's three plates (Tekla's parts), each weighed as its solid without bolt holes */ val subs: List<Sub>? = null)
    /** One plate of a built-up member: [role] tf / w / bf, its Tekla name and profile, its weight (kg). */
    class Sub(val role: String, val name: String, val prof: String, val weight: Double, val holesFilled: Double, /** the plate's solid (diagnostics) */ val mesh: Mesh? = null)
    /** A component's bolt group as its body records it: head points, shank axis, hole diameter d0, bolt length L. */
    class Bolts(val pts: List<D3>, val ax: D3, val d0: Double, val len: Double, val slot: Double = 0.0, /** v70 C01: an anchor rod's hole, a [poly]-gon (0 = stBolts' 12-gon, area 3 r^2) */ val poly: Int = 0)

    /**
     * The bolt groups that may drill a part (web v70 holeFill70): those of the connections that join it, of its own connection
     * ([partConn], a component's part), and of every connection sharing a part with its own (a node gusset is drilled by the other
     * braces' tongues). [conns]: connection id -> its parts; [byConn]: connection id -> its bolt groups (not TKBOLT).
     */
    fun candidates(partId: String, partConn: String?, conns: Map<String, List<String>>, byConn: Map<String, List<Bolts>>): List<Bolts> {
        val byPart = HashMap<String, MutableList<Bolts>>(); for ((cid, ps) in conns) { val g = byConn[cid] ?: continue; for (p in ps) byPart.getOrPut(p) { ArrayList() } += g }
        val out = LinkedHashSet<Bolts>(); byPart[partId]?.let { out += it }; partConn?.let { c -> byConn[c]?.let { out += it }; conns[c]?.forEach { p -> byPart[p]?.let { out += it } } }
        return out.toList()
    }

    companion object {
        val DENS = mapOf("GROUT" to 0.0, "CONCRETE" to 0.0, "ANTIMATERIAL" to 0.0, "MISC_UNDEFINED" to 0.0, "GRATING" to 1333.0)
        /** Tekla's kg/m for this project's catalog profiles (v66 TK_KGM; v70: CC by its section 704 mm²). */
        val KGM = mapOf("UPN240" to 33.2, "CC200-2-20-60" to 5.5264, "L60*6" to 5.42, "L140*60*6" to 9.14)
        private val BI = Regex("^BI\\s*(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)(?:-(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?))?$", RegexOption.IGNORE_CASE)
        private val PARAM = Regex("^(PL|FLT|BL|ROD|RD|D\\d|PD|HI|BH|WI|HK|HQ|SPD|EPD|(?:CHS|SHS|RHS|P|EHS)\\d+(?:\\.\\d+)?\\*)", RegexOption.IGNORE_CASE)
        fun density(mat: String?): Double {
            val m = (mat ?: "").uppercase().trim(); DENS[m]?.let { return it }
            return if (Regex("^C\\d{2}/\\d{2}|CONCRETE|GROUT").containsMatchIn(m)) 0.0 else 7850.0
        }
        private fun ngon(n: Int, r: Double) = n / 2.0 * sin(2 * PI / n) * r * r
        /** Tekla's section area (mm²) of a round or square-hollow parametric profile, else null. */
        fun tkArea(p: String): Double? {
            val s = p.trim().uppercase()
            Regex("^(?:ROD|RD|D)(\\d+(?:\\.\\d+)?)$").find(s)?.let { return ngon(8, it.groupValues[1].toDouble() / 2) }
            Regex("^(?:PD|CHS)(\\d+(?:\\.\\d+)?)\\*(\\d+(?:\\.\\d+)?)$").find(s)?.let { val d = it.groupValues[1].toDouble(); val t = it.groupValues[2].toDouble(); val n = if (d < 50) 12 else 16
                return ngon(n, d / 2) - ngon(n, max(0.0, d / 2 - t)) }
            Regex("^SHS(\\d+(?:\\.\\d+)?)\\*(\\d+(?:\\.\\d+)?)$").find(s)?.let { val b = it.groupValues[1].toDouble(); val t = it.groupValues[2].toDouble(); return b * b - (b - 2 * t) * (b - 2 * t) }
            Regex("^RHS(\\d+(?:\\.\\d+)?)\\*(\\d+(?:\\.\\d+)?)\\*(\\d+(?:\\.\\d+)?)$").find(s)?.let { val h = it.groupValues[1].toDouble(); val b = it.groupValues[2].toDouble(); val t = it.groupValues[3].toDouble(); return h * b - (h - 2 * t) * (b - 2 * t) }
            return null
        }
        private fun r2(x: Double) = round(x * 100) / 100
        private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        private fun num(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        private fun pt(o: JsonObject, k: String): D3? = (o[k] as? JsonArray)?.takeIf { it.size >= 3 }?.let { a -> D3((a[0] as JsonPrimitive).doubleOrNull ?: 0.0, (a[1] as JsonPrimitive).doubleOrNull ?: 0.0, (a[2] as JsonPrimitive).doubleOrNull ?: 0.0) }
    }

    private fun prof(st: JsonObject) = (str(st, "tprof") ?: str(st, "prof") ?: "").trim()

    fun isCatalog(st: JsonObject): Boolean { if (str(st, "kind") == "plate") return false; val p = prof(st); return p.isNotEmpty() && !PARAM.containsMatchIn(p) }

    /** The section area of [mesh] square to p1 -> p2 (10 mm slabs at 20 / 35 / 50 / 65 / 80 % of the length, the median), mm². */
    fun ownArea(mesh: Mesh, p1: D3, p2: D3): Double {
        val a = (p2 - p1).normalize(); val ref = if (abs(a.z) < 0.9) D3(0.0, 0.0, 1.0) else D3(1.0, 0.0, 0.0)
        val x = (a cross ref).normalize(); val y = (a cross x).normalize(); val big = 5000.0; val l = p2.distanceTo(p1)
        val s = doubleArrayOf(0.2, 0.35, 0.5, 0.65, 0.8).map { f -> abs(booleans.volume(booleans.intersect(mesh, Joints.box3(p1.add(a, l * f), x, y, -big, big, -big, big, -5.0, 5.0)))) / 10 }.sorted()
        return s[2]
    }

    /**
     * Weight of one part: [st] its record, [mesh] its solid, [holed] = a TKBOLT lists it, [noHoles] = the part rebuilt with its cuts
     * but no bolt holes (only called when [holed]).
     */
    fun part(st: JsonObject, mesh: Mesh, holed: Boolean, noHoles: () -> Mesh?, bolts: List<Bolts> = emptyList()): W {
        val kind = str(st, "kind"); if (kind == "bolts" || kind == "bolt") return W(0.0, 0.0, false, 0.0, 0.0)
        if ((str(st, "name") ?: "").trim().equals("GROUT", true)) return W(0.0, 0.0, false, 0.0, 0.0)   // Tekla's grout weighs 0 (components name it GROUT on S275)
        if (kind == "conn" && num(st, "len70") > 0) {   // C18 (web partWeight70): a component's catalog angle (the stair brackets): Tekla's kg/m x its length
            val kg = KGM[prof(st)] ?: profiles.lookup(prof(st))?.m ?: 0.0
            if (kg > 0) { val w = r2(kg * num(st, "len70") / 1000); return W(w, w, true, r2(kg), round(num(st, "len70") * 10) / 10) } }
        val rho = density(str(st, "mat") ?: str(st, "material"))
        if (kind == "member") bi(prof(st))?.let { return builtUp(st, mesh, it, rho, bolts) }   // X01: a built-up = its three plates
        var vol = abs(booleans.volume(mesh))
        if (holed) runCatching { noHoles() }.getOrNull()?.let { m0 -> val v0 = abs(booleans.volume(m0)); if (v0 >= vol - 1e-6 && v0 < vol * 1.25 + 1e5) vol = v0 }
        val cat = isCatalog(st); var net = vol * 1e-9 * rho; var gross = net; var len = 0.0; var kgm = 0.0; var at: Double? = null; var am: Double? = null
        if (kind == "member") {
            val p1 = pt(st, "p1"); val p2 = pt(st, "p2")
            if (p1 != null && p2 != null) len = p1.distanceTo(p2) + num(st, "dx1") + num(st, "dx2")
            val t = if (cat) null else tkArea(prof(st))
            if (t != null && p1 != null && p2 != null) { val own = ownArea(mesh, p1, p2); if (own > 0) { at = t; am = own; net *= t / own; gross = net } }
            if (cat) { kgm = KGM[prof(st)] ?: profiles.lookup(prof(st))?.m ?: profiles.lookup(str(st, "prof") ?: "")?.m ?: 0.0; gross = if (kgm > 0 && len > 0) kgm * len / 1000 * rho / 7850 else net }
        }
        if (gross < net - 1e-6) gross = max(gross, net)
        var fillKg = 0.0
        if (!(cat && kgm > 0 && len > 0) && bolts.isNotEmpty() && (kind == "plate" || kind == "member" || kind == "conn") && !prof(st).uppercase().startsWith("PD")) {   // C01: a PD ring's bore is its profile's   // gross (owner 2026-11-27): a component's bolt holes filled back
            val f = holeFill(mesh, bolts); if (f > 0) { fillKg = f * 1e-9 * rho * (if (at != null && am != null) at / am else 1.0); net += fillKg; gross = max(gross, net) }   // a catalog part with no length (a cleat angle): its solid
        }
        val weight = if (cat) gross else net
        return W(r2(weight), r2(net), cat, r2(kgm), round(len * 10) / 10, at?.let(::r2), am?.let(::r2), r2(fillKg))
    }

    /** A BI profile's sizes: height, web, top flange width / thickness, bottom flange width / thickness (web v70 BI70); null: not a BI. */
    class Bi(val h: Double, val tw: Double, val b1: Double, val t1: Double, val b2: Double, val t2: Double)
    fun bi(p: String): Bi? = BI.matchEntire(p.trim())?.groupValues?.let { g -> val n = g.drop(1).map { it.toDoubleOrNull() }
        Bi(n[0]!!, n[1]!!, n[2]!!, n[3]!!, n[4] ?: n[2]!!, n[5] ?: n[3]!!) }
    /** fmt as JS: 10.0 -> "10", 8.5 -> "8.5" */
    private fun js(x: Double): String = if (x == Math.rint(x) && abs(x) < 1e15) x.toLong().toString() else x.toString()

    /**
     * Web v70 X01 (buSubs70 / buWeight70): a built-up section is three plates in Tekla, Web, Top flange and Bottom flange, each its own
     * part and weighed as its solid without bolt holes. Each plate = the member's solid (every cut, fit, cope and hole) inside its slab
     * of the section in the member frame (stMF): the flanges are the bands beyond the web's edges, the web the band between.
     */
    fun builtUp(st: JsonObject, mesh: Mesh, b: Bi, rho: Double, bolts: List<Bolts>): W {
        val f = Joints.mf(st, profiles); val big = f.c1.distanceTo(f.c2) + b.h + maxOf(b.b1, b.b2) + 2000.0   // the slabs sized to the member (a 1e6 box in float32 tilts its faces)
        val band = linkedMapOf("tf" to (b.h / 2 - b.t1 to big), "w" to (-b.h / 2 + b.t2 to b.h / 2 - b.t1), "bf" to (-big to -b.h / 2 + b.t2))
        val def = mapOf("tf" to ("Top flange" to "FLT${js(b.t1)}*${js(b.b1)}"), "w" to ("Web" to "FLT${js(b.tw)}*${js(Math.round((b.h - b.t1 - b.t2) * 1000) / 1000.0)}"), "bf" to ("Bottom flange" to "FLT${js(b.t2)}*${js(b.b2)}"))
        var total = 0.0; var fill = 0.0; val subs = ArrayList<Sub>()
        for ((role, v) in band) {
            val m = booleans.intersect(mesh, Joints.box3(f.c1, f.eh, f.es, v.first, v.second, -big, big, -big, big))
            val pn = if (role == "w") f.es else f.eh; val bs = bolts.filter { abs(it.ax.normalize() dot pn) >= 0.9 }   // a plate is drilled only by bolts square to it (web: es; flanges: eh)
            val vol = abs(booleans.volume(m)); val hf = if (bs.isNotEmpty()) holeFill(m, bs) else 0.0
            val w = (vol + hf) * 1e-9 * rho; total += w; fill += hf * 1e-9 * rho
            subs += Sub(role, def.getValue(role).first, def.getValue(role).second, r2(w), r2(hf * 1e-9 * rho), m)
        }
        return W(r2(total), r2(total), false, 0.0, 0.0, holesFilled = r2(fill), subs = subs)
    }

    /**
     * The volume of the bolt holes a part really has (web v70 holeFill70, the same steps): per bolt, the line from 2 before the head
     * point to L + 2; material on it = the part has no hole for that bolt; else the hole as drilled (stBolts: a 12-gon of radius d0 / 2,
     * area 3 (d0 / 2)^2, swept along a slot) x the part's thickness there (the median of 8 lines parallel to the bolt, 0.8 mm outside
     * the hole). Rays on the part's own mesh.
     */
    fun holeFill(mesh: Mesh, bolts: List<Bolts>): Double {
        val p = mesh.positions; val ix = mesh.indices; val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until mesh.vertexCount) for (k in 0..2) { val v = p[i * 3 + k].toDouble(); if (v < lo[k]) lo[k] = v; if (v > hi[k]) hi[k] = v }
        fun inside(P: D3, a: D3, s0: Double, s1: Double): Double {
            class H(val s: Double, val out: Boolean); val hs = ArrayList<H>()
            for (t in 0 until mesh.triangleCount) { val i0 = 3 * ix[3 * t]; val i1 = 3 * ix[3 * t + 1]; val i2 = 3 * ix[3 * t + 2]
                val e1x = (p[i1] - p[i0]).toDouble(); val e1y = (p[i1 + 1] - p[i0 + 1]).toDouble(); val e1z = (p[i1 + 2] - p[i0 + 2]).toDouble()
                val e2x = (p[i2] - p[i0]).toDouble(); val e2y = (p[i2 + 1] - p[i0 + 1]).toDouble(); val e2z = (p[i2 + 2] - p[i0 + 2]).toDouble()
                val px = a.y * e2z - a.z * e2y; val py = a.z * e2x - a.x * e2z; val pz = a.x * e2y - a.y * e2x; val det = e1x * px + e1y * py + e1z * pz; if (abs(det) < 1e-12) continue
                val inv = 1 / det; val tx = P.x - p[i0]; val ty = P.y - p[i0 + 1]; val tz = P.z - p[i0 + 2]; val u = (tx * px + ty * py + tz * pz) * inv; if (u < -1e-9 || u > 1 + 1e-9) continue
                val qx = ty * e1z - tz * e1y; val qy = tz * e1x - tx * e1z; val qz = tx * e1y - ty * e1x; val w = (a.x * qx + a.y * qy + a.z * qz) * inv; if (w < -1e-9 || u + w > 1 + 1e-9) continue
                hs += H((e2x * qx + e2y * qy + e2z * qz) * inv, det < 0) }
            hs.sortBy { it.s }; val h = ArrayList<H>(); for (x in hs) if (h.isEmpty() || abs(x.s - h.last().s) > 1e-6 || x.out != h.last().out) h += x
            var len = 0.0; var inS: Double? = null
            for (x in h) { if (!x.out) { if (inS == null) inS = x.s } else if (inS != null) { len += max(0.0, kotlin.math.min(x.s, s1) - max(inS, s0)); inS = null } }
            return len
        }
        fun c(v: D3, k: Int) = when (k) { 0 -> v.x; 1 -> v.y; else -> v.z }
        var fill = 0.0
        for (g in bolts) {
            val a = g.ax.normalize(); val x = (a cross (if (abs(a.z) < 0.9) D3(0.0, 0.0, 1.0) else D3(1.0, 0.0, 0.0))).normalize(); val y = a cross x
            val hr = g.d0 / 2; val rr = hr + 0.8; val area = (if (g.poly > 0) g.poly / 2.0 * sin(2 * PI / g.poly) * hr * hr else 3 * hr * hr) + g.slot * 2 * hr * kotlin.math.cos(PI / 12)
            for (pa in g.pts) { val pb = pa.add(a, g.len + 2); val m = hr + 2
                if ((0..2).any { k -> max(c(pa, k), c(pb, k)) + m < lo[k] || kotlin.math.min(c(pa, k), c(pb, k)) - m > hi[k] }) continue
                if (inside(pa, a, -2.0, g.len + 2) > 0.5) continue
                val ts = (0 until 8).map { i -> val f = (i + 0.37) * PI / 4; inside(pa.add(x, rr * kotlin.math.cos(f)).add(y, rr * sin(f)), a, -2.0, g.len + 2) }.sorted()
                val tm = (ts[3] + ts[4]) / 2; if (tm > 0) fill += tm * area }
        }
        return fill
    }
}
