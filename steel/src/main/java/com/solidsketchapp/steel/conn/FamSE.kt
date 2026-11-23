package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.min

/**
 * SE family: seats under the secondary (seat angle 60 / 72-75 / 170 / 170r with an optional top angle, bearing plate 7,
 * stiffener seating 12, stub 28, tube seat 104). Live definition: v69 FAM.SE (L14977); FAM.JO (L15166) calls it with its own q.
 */
object FamSE {
    const val PORTED = true

    /** The family's own settings (v69 `q` inside FAM.SE). */
    private class Q(val o: JsonObject) {
        fun n(k: String): Double { val e = o[k] ?: return Double.NaN; return if (e is JsonNull) 0.0 else StBuild.jsNum(e) }
        fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun t(k: String) = jsTruthy(o[k])
        /** JavaScript `${q.k}`. */
        fun js(k: String): String { val e: JsonElement = o[k] ?: return "undefined"; if (e is JsonNull) return "null"; val p = e as JsonPrimitive
            return if (p.isString) p.content else p.content.toDoubleOrNull()?.let { if (it.isFinite()) JsNumber.format(it) else p.content } ?: p.content }
    }

    fun gen(x: FamCtx) = gen(x, x.q)

    /** FAM.SE with the settings [q] (FAM.JO: `FAM.SE(Object.assign({}, X, { q }))`); parts, cuts, bolts and welds go to [x]. */
    fun gen(x: FamCtx, q0: JsonObject) {
        val q = Q(q0); val b = x.b ?: throw IllegalStateException("not built"); val bId = x.bId!!
        val j = Joints.joint(x.a, b, x.profiles); val bp = j.b; val f = Joints.Frame3(j.e, j.x, j.y)
        val yb = -bp.h / 2; val t = q.n("t"); val l = q.n("L"); val w = q.n("w") / 2; val xF = j.xFace; val gap = q.n("gap"); val bolt = q.n("bolt")
        for (m in j.cutEnd(xF + gap)) x.cut(bId, m)
        val g = min(q.n("w") / 2 - 35, 50.0)
        val b2 = listOf(j.p(xF + gap + l * 0.55, yb - t - 0.1, -g), j.p(xF + gap + l * 0.55, yb - t - 0.1, g))
        if (q.s("seat") == "angle") {
            val s = Fam.lAngle(f, xF, yb, l, l, t, 1.0, -1.0, -w, w)
            val b1 = listOf(j.p(xF + t + 0.1, yb - l * 0.6, -g), j.p(xF + t + 0.1, yb - l * 0.6, g))
            val ba = x.group(b1, -j.x, bolt, t + j.tPrim + 2); val bb = x.group(b2, j.y, bolt, t + bp.tf + 2)
            for (hc in ba.holes) x.cut(x.aId, hc); for (hc in bb.holes) x.cut(bId, hc)
            x.plate("SEAT ANGLE", s, t, "L${q.js("L")}*${q.js("L")}*${q.js("t")}", minus = ba.holes + bb.holes)
            x.bolts(ba, 2, t + j.tPrim + 2 * bolt); x.bolts(bb, 2, t + bp.tf + 2 * bolt)
            if (q.t("top") && !(abs(j.m.a.z) < 0.9 && !j.flange)) {
                val yt = bp.h / 2; val s2 = Fam.lAngle(f, xF, yt, 75.0, 75.0, 8.0, 1.0, 1.0, -w * 0.8, w * 0.8)
                val bt = x.group(listOf(j.p(xF + gap + 75 * 0.55, yt + 8 + 0.1, 0.0)), -j.y, 16.0, 8 + bp.tf + 2)
                for (hc in bt.holes) x.cut(bId, hc)
                x.plate("TOP ANGLE", s2, 8.0, "L75*75*8", minus = bt.holes); x.bolts(bt, 1, 8 + bp.tf + 32, 16.0)
            }
            return
        }
        val stub = q.s("seat") == "stub"
        val sp = f.box(xF, xF + l, yb - t, yb, -w, w); val bb = x.group(b2, j.y, bolt, t + bp.tf + 2)
        for (hc in bb.holes) x.cut(bId, hc)
        x.plate(if (stub) "BEARING PLATE" else "SEAT PLATE", sp, t, minus = bb.holes); x.bolts(bb, 2, t + bp.tf + 2 * bolt)
        x.weld("Seat to main part", q.n("a"), 2 * q.n("w"))
        val h = (if (stub) 2.0 else 1.2) * l; val ts = q.n("ts")
        for (z in (if (stub) listOf(-w * 0.7, w * 0.7) else listOf(0.0))) {
            val m: Mesh = f.poly(listOf(doubleArrayOf(xF, yb - t), doubleArrayOf(xF + l - 10, yb - t), doubleArrayOf(xF, yb - t - h)), z - ts / 2, z + ts / 2)
            x.plate("SEAT STIFFENER", m, ts)
        }
    }

}
