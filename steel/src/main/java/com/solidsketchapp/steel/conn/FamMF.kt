package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.max

/**
 * MF family: moment connections with flange plates (130, 134 bolted, 181, shear and flange plates 43). Live definition:
 * v69 FAM.MF (L15032): a shear tab (FAM.ST, L14939, with FAM_DEF.ST and the tab size / gap / bolt), flange plates on both
 * flanges (welded, or bolted when `flange` is 'bolt'), continuity plates in a column.
 */
object FamMF {
    const val PORTED = true
    /** v69 FAM_DEF.ST (L14860). */
    private val DEF_ST = Fam.obj("t" to 10, "gap" to 15, "e" to 40, "rows" to 0, "pitch" to 70, "cols" to 1, "pitchH" to 70, "bolt" to 20, "grade" to "8.8", "a" to 6, "side" to 1, "full" to 0, "top" to 0, "colstiff" to 0, "stiff" to 0, "tee" to 0, "through" to 0, "shaped" to 0, "two" to 0)
    /** The bolt options stBolts reads from TKB_Q (the component's own settings, not the q FAM.ST gets). */
    private val BOLT_KEYS = listOf("ba_b", "ba_n", "ba_w", "bdel", "bdir", "blen", "htype", "sl1", "sl2", "srot", "stag", "tol")

    fun gen(x: FamCtx) {
        val b = x.b ?: throw IllegalStateException("not built"); val bId = x.bId!!
        // FAM.ST(Object.assign({}, X, { q: Object.assign({}, FAM_DEF.ST, { t, gap, bolt: q.bolt <= 24 ? q.bolt : 20, grade: '8.8', colstiff: 0 }) }))
        run {
            val qq = LinkedHashMap<String, JsonElement>(DEF_ST)
            x.q["t"]?.let { qq["t"] = it }; x.q["gap"]?.let { qq["gap"] = it }
            qq["bolt"] = if (x.n("bolt") <= 24) (x.q["bolt"] ?: jsNum(20.0)) else jsNum(20.0); qq["grade"] = JsonPrimitive("8.8"); qq["colstiff"] = jsNum(0.0)
            for (k in BOLT_KEYS) x.q[k]?.let { qq[k] = it }
            val y = FamCtx(JsonObject(qq), x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt, x.conn, x.conns, x.stOf); Fam.st(y)
            for ((id, l) in y.cuts) x.cuts.getOrPut(id) { ArrayList() } += l; x.groups += y.groups; x.welds += y.welds
            // famGeom's bolts(): d || q.bolt and grade || q.grade are the component's (the closure's q), not FAM.ST's
            for (p in y.parts) x.parts += if (p.bolts == null) p else ConnPart(p.name, p.pieces, JsonObject(LinkedHashMap(p.bolts).apply {
                put("d", jsNum(x.n("bolt"))); put("grade", JsonPrimitive(x.s("grade"))) }), p.prof, p.minus, p.group)
        }
        val j = Joints.joint(x.a, b, x.profiles); val bp = j.b; val tf = x.n("tfp"); val n = max(1, jsInt(x.n("rows")))
        val lap = if (x.t("lap")) x.n("lap") else x.n("e") * 2 + (n - 1) * x.n("pitch") + x.n("gap") + 20
        val hb = bp.h / 2; val wb = bp.b / 2; val f = Joints.Frame3(j.e, j.x, j.y); val fl = x.s("flange")
        for (s in listOf(1, -1)) {
            val y0 = s * hb; val wpl = if (fl == "weld" && s < 0) wb + 20 else if (fl == "weld") wb - 15 else wb
            val fp = f.box(j.xFace, j.xFace + lap, y0, y0 + s * tf, -wpl, wpl); var holes: List<com.solidsketchapp.core.api.Mesh> = emptyList()
            if (fl == "bolt") {
                val bolt = x.n("bolt"); val d0 = bolt + (if (bolt <= 24) 2 else 3)
                val g = max(bp.tw / 2 + (bp.r.takeIf { it != 0.0 } ?: 10.0) + 1.3 * d0, bp.b * 0.3); val bl = ArrayList<D3>()
                for (i in 0 until n) for (z in listOf(-g, g)) bl += j.p(j.xFace + (x.n("gap") + 20 + x.n("e")) + i * x.n("pitch"), y0 + s * (tf + 0.1), z)
                val bb = x.group(bl, j.y * (-s.toDouble()), bolt, tf + bp.tf + 2); holes = bb.holes; for (hc in bb.holes) x.cut(bId, hc)
                x.bolts(bb, bl.size, x.n("tfp") + bp.tf + 2 * bolt)
            } else x.weld("Flange plate to beam", x.n("a"), 2 * lap + 2 * wpl)
            x.plate("FLANGE PLATE", fp, x.n("tfp"), minus = holes); x.weld("Flange plate to column (CJP)", x.n("tfp"), 2 * wpl)
        }
        if (x.t("colstiff") && j.flange && abs(j.m.a.z) > 0.9) {
            val nn = (j.m.eh cross j.m.es).normalize()
            for (s in listOf(1, -1)) Fam.stiffPair(x, j.m, (j.p(0.0, s * (hb + tf / 2), 0.0) - j.m.c1) dot nn, x.n("ts"), 15.0, listOf(1, -1), "CONTINUITY PLATE")
        }
    }
}
