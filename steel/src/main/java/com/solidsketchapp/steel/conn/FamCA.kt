package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max

/**
 * CA family · clip angles (v69 FAM.CA, v40 L14961, the live definition): angles either side of the secondary's web
 * (one angle when `double` is off), bolted through the web and (unless `weldMain`, or the shared second side) to the main part.
 */
object FamCA {
    const val PORTED = true

    fun gen(x: FamCtx) {
        fun one(bm: kotlinx.serialization.json.JsonObject, bmId: String, shared: Boolean) {
            val j = Joints.joint(x.a, bm, x.profiles); val bp = j.b; val t = x.n("t"); val l = x.n("leg"); val e = x.n("e"); val pitch = x.n("pitch")
            var n = if (x.t("rows")) jsInt(x.n("rows")) else max(2, Fam.tkRows(bp.h))
            val room = j.yHi - j.yLo - 2 * (bp.tf + (bp.r.takeIf { it != 0.0 } ?: 10.0) + 5)
            while (n > 1 && ((n - 1) * pitch + 2 * e) > room) n--
            val hp = (n - 1) * pitch + 2 * e; val yc = (j.yHi + j.yLo) / 2; val w = bp.tw / 2; val x0 = j.xFace
            val sides = if (x.t("double")) listOf(1, -1) else listOf(1); val f = Joints.Frame3(j.p(0.0, yc + hp / 2, 0.0), j.x, j.zb)
            val angs = sides.map { sg -> f.poly(listOf(doubleArrayOf(x0, w), doubleArrayOf(x0 + l, w), doubleArrayOf(x0 + l, w + t), doubleArrayOf(x0 + t, w + t), doubleArrayOf(x0 + t, w + l), doubleArrayOf(x0, w + l)).map { doubleArrayOf(it[0], sg * it[1]) }, 0.0, hp) }
            val bw = ArrayList<D3>(); for (i in 0 until n) bw += j.p(x0 + l - e, yc - hp / 2 + e + i * pitch, sides[0] * (w + t + 0.1))
            val b1 = x.group(bw, j.zb * (-sides[0].toDouble()), x.n("bolt"), sides.size * t + bp.tw + 2)
            val minus = ArrayList<Mesh>(b1.holes); for (hc in b1.holes) x.cut(bmId, hc)
            if (!x.t("weldMain") && !shared) {
                val gz = w + t + (l - t) * 0.55; val bpts = ArrayList<D3>()
                for (sg in sides) for (i in 0 until n) bpts += j.p(x0 + t + 0.1, yc - hp / 2 + e + i * pitch, sg * gz)
                val grip = t + j.tPrim + (if (x.b2 != null && x.t("shared")) t else 0.0) + 2
                val b2 = x.group(bpts, -j.x, x.n("bolt"), grip); minus += b2.holes; for (hc in b2.holes) x.cut(x.aId, hc)
                x.bolts(b2, bpts.size, grip + 2 * x.n("bolt"))
            }
            for (m in j.cutEnd(j.xFace + x.n("gap"))) x.cut(bmId, m)
            val prof = "L${jsStr(x.q["leg"])}*${jsStr(x.q["leg"])}*${jsStr(x.q["t"])}"
            angs.forEach { x.plate("CLIP ANGLE", it, t, prof, minus) }
            x.bolts(b1, bw.size, sides.size * t + bp.tw + 2 * x.n("bolt"))
        }
        one(x.b!!, x.bId!!, false); if (x.b2 != null) one(x.b2, x.b2Id!!, x.t("shared"))
    }

    /** JavaScript String(v) of a q value (template literals, 'PL' + q.t). */
    internal fun jsStr(e: JsonElement?): String { val p = e as? JsonPrimitive ?: return "undefined"; return if (p.isString) p.content else JsNumber.format(StBuild.jsNum(p)) }
}
