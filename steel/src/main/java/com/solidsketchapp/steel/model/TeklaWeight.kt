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
    class W(val weight: Double, val net: Double, val catalog: Boolean, val kgm: Double, val len: Double, val tkArea: Double? = null, val ownArea: Double? = null)

    companion object {
        val DENS = mapOf("GROUT" to 0.0, "CONCRETE" to 0.0, "ANTIMATERIAL" to 0.0, "MISC_UNDEFINED" to 0.0, "GRATING" to 1333.0)
        /** Tekla's kg/m for this project's catalog profiles (v66 TK_KGM; v70: CC by its section 704 mm²). */
        val KGM = mapOf("UPN240" to 33.2, "CC200-2-20-60" to 5.5264, "L60*6" to 5.42, "L140*60*6" to 9.14)
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
    fun part(st: JsonObject, mesh: Mesh, holed: Boolean, noHoles: () -> Mesh?): W {
        val kind = str(st, "kind"); if (kind == "bolts" || kind == "bolt") return W(0.0, 0.0, false, 0.0, 0.0)
        val rho = density(str(st, "mat") ?: str(st, "material"))
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
        val weight = if (cat) gross else net
        return W(r2(weight), r2(net), cat, r2(kgm), round(len * 10) / 10, at?.let(::r2), am?.let(::r2))
    }
}
