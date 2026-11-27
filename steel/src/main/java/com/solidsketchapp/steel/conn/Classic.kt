package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import kotlinx.serialization.json.JsonObject

/**
 * S10: v69's own connection types (not catalog components) that the applications make:
 *  - v34 (L13883): base (base plate), knee (eaves haunch), apex (apex haunch; v54 L15817: Tekla 106 plates when ph > 0)
 *  - v35 (L14042): cleat (purlin cleat), wgusset (bolted gusset), tgusset (tube gusset), tieend (tie end plate)
 *  - v42 (L15251): gabletop; v43 (L15324): craneseat, railclip; v44 (L15374): stairtreads, weldonly
 * Their geometry is v69 stConnGeom of the live chain (each type's branch, inside the v58 wrapper L15991, which [Fam.geom]
 * already ports: bolt info d0 / std / thr / sw, the Holes tab, the Welds tab). Each type runs on a [FamCtx] like a family
 * generator; the generators are in ClassicV34.kt, ClassicV35.kt and ClassicApps.kt.
 */
object Classic {
    /** v69 CONN[type].def after every chapter (v54 apex options, v58 L16116: apex a 5, base bolt 24 → 20); tests/steel/golden/app/conn_defs.json. */
    val DEFS: Map<String, JsonObject> = mapOf(
        "base" to Fam.obj("t" to 20, "ext" to 75, "side" to 25, "bolt" to 20, "grade" to "8.8", "anch" to 450, "a" to 6),
        "knee" to Fam.obj("tp" to 20, "len" to 0, "depth" to 0, "rows" to 4, "bolt" to 20, "grade" to "8.8", "gauge" to 100, "cap" to 15, "stiff" to 12, "a" to 6),
        "apex" to Fam.obj("ph" to 0, "pw" to 0, "top" to "", "bot" to "", "rowsAt" to "", "hf" to 10, "hb" to 0, "hw" to 8, "tp" to 20, "len" to 900, "depth" to 250, "rows" to 3, "bolt" to 20, "grade" to "8.8", "gauge" to 100, "a" to 5),
        "cleat" to Fam.obj("t" to 8, "h" to 0, "w" to 90, "bolt" to 12, "grade" to "8.8", "a" to 4),
        "wgusset" to Fam.obj("t" to 10, "len" to 260, "w" to 160, "bolt" to 16, "grade" to "8.8", "a" to 5),
        "tgusset" to Fam.obj("t" to 10, "gap" to 100, "lap" to 120, "w" to 160, "a" to 5),
        "tieend" to Fam.obj("t" to 12, "size" to 150, "bolt" to 16, "grade" to "8.8", "a" to 5),
        "gabletop" to Fam.obj("tp" to 15, "ext" to 60, "side" to 10, "bolt" to 16, "grade" to "8.8", "a" to 6),
        "craneseat" to Fam.obj("tp" to 20, "bolt" to 20, "grade" to "8.8", "ts" to 12, "gx" to 60, "a" to 6),
        "railclip" to Fam.obj("s" to 750, "t" to 12, "w" to 80, "l" to 60, "bolt" to 16, "grade" to "8.8", "stop" to 1),
        "stairtreads" to Fam.obj("sl" to 0, "tread" to 6, "riserMax" to 180, "nosing" to 25, "leg" to 50, "ta" to 5, "bolt" to 12, "grade" to "8.8", "foot" to 1, "tf" to 12,
            // v70 C18 (web CONN.stairtreads.def additions)
            "tmode" to "", "ntr" to 12, "rise" to 156.667, "s0" to 145.863, "dz" to 47.43, "tw" to 300, "th" to 40, "tt" to 10, "tri" to 5, "tgap" to 5, "tmat" to "Grating", "bl" to 140, "bleg" to 60, "bta" to 6, "bbolt" to 12, "bp" to 90, "bo" to 5, "bz" to 30, "bgrade" to "8.8", "blrule" to "tekla"),
        "weldonly" to Fam.obj("a" to 4),
    )
    /** v69 CONN[type].label, as the toasts and lists show them. */
    val LABEL = mapOf("base" to "Base plate", "knee" to "Eaves haunch", "apex" to "Apex haunch", "cleat" to "Purlin cleat", "wgusset" to "Bolted gusset", "tgusset" to "Tube gusset",
        "tieend" to "Tie end plate", "gabletop" to "Gable column top", "craneseat" to "Crane beam seat", "railclip" to "Rail clips", "stairtreads" to "Stair treads", "weldonly" to "Weld")

    /** The generators (filled by ClassicV34 / ClassicV35 / ClassicApps); a type is ported when its generator is here. */
    val GEN: Map<String, (FamCtx) -> Unit> by lazy { ClassicV34.GEN + ClassicV35.GEN + ClassicApps.GEN }
    fun ported(type: String) = type in GEN
    /** The pseudo family name [Fam.geom] runs a classic type under. */
    fun fam(type: String) = "C:$type"

    /** v69 stOwner for these types (v36 L14113, v42 L15267, v43 L15352, v44 L15398): the part a connection plate is welded to. */
    fun owner(type: String, p: List<String>, name: String, ix: Int): String? = when (type) {
        "knee" -> if (Regex("CAP|STIFF").containsMatchIn(name)) p[0] else p.getOrNull(1)
        "apex" -> if (ix == 0) p[0] else if (ix == 1) p.getOrNull(1) else if (ix - 2 < 2) p[0] else p.getOrNull(1)
        "tieend" -> p.getOrNull(1)
        "gabletop" -> p.getOrNull(1)
        "craneseat" -> if ("STIFFENER" in name) p.getOrNull(1) else p[0]
        else -> p.firstOrNull()
    }
}

/**
 * v69 v34 working frame (L13915): s along the horizontal direction h, z up, y across (y = Z × h).
 * [lp] = stLP, [prism] = stPrism (a polygon in (s, z), thickness from y0 to y1), [box] = stBoxL.
 */
class LF(val o: D3, val h: D3, val y: D3) {
    val Z = D3(0.0, 0.0, 1.0)
    fun lp(s: Double, z: Double, yy: Double = 0.0): D3 = o.add(h, s).add(Z, z).add(y, yy)
    /** stPrism: extrudeRegions(poly) placed with u = h, v = Z, n = −y at the origin lp(0, 0, y1): the solid between y0 and y1. */
    // h × Z = −y, so poly3 extrudes from y1 toward y0 (|y1 − y0|, as v69 extrudeRegions takes |height|)
    fun prism(poly: List<DoubleArray>, y0: Double, y1: Double): Mesh = Joints.poly3(lp(0.0, 0.0, y1), h, Z, poly, 0.0, y1 - y0)
    fun box(s0: Double, s1: Double, z0: Double, z1: Double, y0: Double, y1: Double): Mesh =
        prism(listOf(doubleArrayOf(s0, z0), doubleArrayOf(s1, z0), doubleArrayOf(s1, z1), doubleArrayOf(s0, z1)), y0, y1)

    companion object {
        /** stLF(o, hd): o on the ground plane of the connection, hd a horizontal direction. */
        fun of(o: D3, hd: D3): LF { val Z = D3(0.0, 0.0, 1.0); val h = D3(hd.x, hd.y, 0.0).normalize(); val y = Z.cross(h).normalize(); return LF(o, h, y) }
    }
}
