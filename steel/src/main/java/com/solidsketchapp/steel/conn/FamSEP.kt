package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * SEP family · Stair end plate (SolidSketch, v70 C18; web v70 section "C18", FAM.SEP): R12's FLT12*150 plates on the stringer pieces'
 * ends: a plate square to the member at its end (t x b along the width x h along the depth, centre offset de / dw), a bolt grid
 * (nr x nc, pitch bp, gauge bg, offset bx / by) through the main part (head on its far side) or outward from the member end, an
 * optional counter plate on the main part; R13: the plate welded to the member (its assembly) or loose (its own assembly, no weld: the top
 * plates PLSTR9001, bolted through the counter plate on EB4002).
 */
object FamSEP {
    const val PORTED = true
    private fun tk(x: FamCtx, k: String, d: Double) = StBolts.tkNum(x.q[k], d)

    fun gen(x: FamCtx) {
        val m = Joints.mf(x.a, x.profiles); val nN = (m.eh cross m.es).normalize(); val b = x.b
        val atEnd = when (x.s("end")) { "1" -> false; "2" -> true; else -> if (b != null) { val mb = Joints.mf(b, x.profiles)
            fun seg(p: D3): Double { val d = mb.c2 - mb.c1; val t = (((p - mb.c1) dot d) / (d dot d)).coerceIn(0.0, 1.0); return mb.c1.add(d, t).distanceTo(p) }
            seg(m.c2) < seg(m.c1) } else m.c2.z < m.c1.z }
        val endP = if (atEnd) m.c2 else m.c1; val out = if (atEnd) m.a else -m.a; val sg = if ((out dot nN) >= 0) 1.0 else -1.0; val t = x.n("t")
        val h2 = x.n("h") / 2; val b2 = x.n("b") / 2; val de = tk(x, "de", 0.0); val dw = tk(x, "dw", 0.0)
        fun box(z0: Double, z1: Double) = Joints.box3(endP, m.eh, m.es, de - h2, de + h2, dw - b2, dw + b2, if (sg > 0) z0 else -z1, if (sg > 0) z1 else -z0)
        val mg = tk(x, "mgrip", if (b != null) Joints.mf(b, x.profiles).tf.takeIf { it != 0.0 } ?: 10.0 else 0.0)
        val ct = if (jsInt(x.n("cplate")) != 0 && b != null) t else 0.0; val main = x.s("bside") != "out" && b != null
        val nr = jsInt(x.n("nr")); val nc = jsInt(x.n("nc")); val pts = ArrayList<D3>()
        for (i in 0 until nr) for (j in 0 until nc) { val e = tk(x, "by", 0.0) + (i - (nr - 1) / 2.0) * x.n("bp"); val w = tk(x, "bx", 0.0) + (j - (nc - 1) / 2.0) * x.n("bg")
            pts += endP.add(m.eh, e).add(m.es, w).add(out, if (main) t + mg else 0.0) }
        val grip = if (main) t + mg else t + ct + tk(x, "bgrip", 0.0); val d = x.n("bd").takeIf { it != 0.0 && !it.isNaN() } ?: 16.0
        val bg = if (pts.isNotEmpty()) x.group(pts, if (main) -out else out, d, grip) else null
        val prof = "FLT" + js(x, "t") + "*" + js(x, "b")
        x.plate("STAIR PLATE", box(0.0, t), x.n("t"), prof, minus = bg?.holes ?: emptyList())
        if (ct > 0) x.parts += ConnPart("COUNTER PLATE", listOf(box(t, 2 * t)), prof = prof, minus = bg?.holes ?: emptyList())
        if (bg != null) { if (main) for (hc in bg.holes) x.cut(x.bId!!, hc)
            val len = (x.q["bL"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: Fam.tkBoltLen(d, grip)
            val info = linkedMapOf<String, JsonElement>("pts" to JsonArray(bg.pts.map { jsArr(it.x, it.y, it.z) }), "ax" to jsArr(bg.ax.x, bg.ax.y, bg.ax.z), "d" to jsNum(d), "n" to jsNum(pts.size.toDouble()),
                "grade" to JsonPrimitive(x.s("bgrade").ifEmpty { "8.8" }), "L" to jsNum(len))
            x.parts += ConnPart("BOLTS", bg.pieces, JsonObject(info), group = bg) }
        if (x.s("pweld") != "0") x.weld("Plate to member", x.n("a"), (x.profiles.lookup((x.a["prof"] as? JsonPrimitive)?.contentOrNull ?: "")?.P?.takeIf { it != 0.0 && !it.isNaN() } ?: 1.0) * 1000)
    }
    private fun js(x: FamCtx, k: String): String { val p = x.q[k] as? JsonPrimitive ?: return "undefined"; return if (p.isString) p.content else JsNumber.format(com.solidsketchapp.steel.geom.StBuild.jsNum(p)) }
}
