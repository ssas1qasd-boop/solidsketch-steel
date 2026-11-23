package com.solidsketchapp.steel.comp

import com.solidsketchapp.steel.geom.D3
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * v69 tkBoltPic (v58): the component's real bolt pattern (or one from its values), numbered like Tekla, with dimension
 * chains, and where the Bolts tab's input boxes go (TK_ROLE / tkRole: ev, sv, pv, nv, eh, sh, nh, ph; % of 400 × 260).
 */
object BoltPic {
    private val ROLE = mapOf("e" to "ev", "rows" to "nv", "pitches" to "sv", "pitch" to "sv", "posV" to "pv", "cols" to "nh", "gauge" to "sh", "side" to "eh", "posH" to "ph", "anchors" to "nh", "gx" to "sh", "gy" to "sv", "xsAt" to "sh", "p" to "sv", "g" to "sh", "pitchH" to "sh", "h1" to "pv", "h2" to "sv")
    fun role(f: TkField): String? = if (f.k == "rowsAt") (if (f.lbl == "1") "ev" else if (f.lbl == "3") "nv" else "sv") else if (f.k == "e" && f.lbl == "3") "sh" else ROLE[f.k]

    private fun fx(d: Double) = com.solidsketchapp.core.api.Fmt.f("%.1f", d)
    /** v69 DIM: a red dimension line with arrow heads and end ticks. */
    fun dim(x1: Double, y1: Double, x2: Double, y2: Double): String {
        val a = atan2(y2 - y1, x2 - x1); val h = 6.0; val w = 0.45
        fun ar(x: Double, y: Double, b: Double) = "M$x $y l${fx(cos(b + w) * h)} ${fx(sin(b + w) * h)} M$x $y l${fx(cos(b - w) * h)} ${fx(sin(b - w) * h)}"
        val n = doubleArrayOf(-sin(a) * 7, cos(a) * 7)
        return "<g stroke=\"#c0392b\" fill=\"none\" stroke-width=\"1.1\"><line x1=\"$x1\" y1=\"$y1\" x2=\"$x2\" y2=\"$y2\"/><path d=\"${ar(x1, y1, a)} ${ar(x2, y2, a + Math.PI)} M${x1 - n[0]} ${y1 - n[1]} l${2 * n[0]} ${2 * n[1]} M${x2 - n[0]} ${y2 - n[1]} l${2 * n[0]} ${2 * n[1]}\"/></g>"
    }

    class Result(val svg: String, val at: Map<String, Pair<Double, Double>>)
    private fun n(d: CompDialog, k: String) = Js.num(d.q[k]).let { if (it.isNaN()) 0.0 else it }
    private fun or(vararg v: Double) = v.firstOrNull { it != 0.0 && !it.isNaN() } ?: 0.0

    fun build(d: CompDialog): Result {
        var P: List<DoubleArray>? = null
        d.conn?.let { c -> d.comps.boltSt(c)?.let { st ->
            val a = (st["ax"] as JsonArray).map { Js.num(it) }; val ax = D3(a[0], a[1], a[2]).normalize()
            var up = D3.Z.add(ax, -ax.z); if (up.length() < 0.3) up = D3(0.0, 1.0, 0.0).add(ax, -ax.y); up = up.normalize(); val rt = (up cross ax).normalize()
            P = (st["pts"] as JsonArray).map { e -> val p = e.jsonArray.map { Js.num(it) }; val v = D3(p[0], p[1], p[2]); doubleArrayOf(v dot rt, v dot up) } } }
        if (P == null) {
            val nh = or(n(d, "cols"), 2.0).toInt()
            val ra = Js.str(d.q["rowsAt"] ?: Js.EMPTY).trim()
            val nv = or(n(d, "rows"), if (n(d, "anchors") != 0.0) max(1.0, n(d, "anchors") / 2) else if (ra.isNotEmpty() && Js.truthy(d.q["rowsAt"])) ra.split(Regex("""\s+""")).size.toDouble() else 3.0).toInt()
            val sh = or(n(d, "gauge"), n(d, "pitchH"), n(d, "g"), n(d, "gx"), 90.0); val sv = or(n(d, "pitch"), n(d, "p"), n(d, "gy"), 70.0)
            P = (0 until nv).flatMap { i -> (0 until nh).map { j -> doubleArrayOf(j * sh, -i * sv) } }
        }
        val pts = P!!
        val xs = pts.map { Math.round(it[0]).toDouble() }.distinct().sorted(); val ys = pts.map { Math.round(it[1]).toDouble() }.distinct().sortedDescending()
        val ev = or(n(d, "e"), 40.0); val eh = or(n(d, "side"), ev)
        val x0 = xs[0] - eh; val x1 = xs.last() + eh; val y0 = ys[0] + ev; val y1 = ys.last() - ev
        val W = max(1.0, x1 - x0); val H = max(1.0, y0 - y1); val sc = min(190 / W, 190 / H); val ox = 205 - W * sc / 2; val oy = 22.0
        fun X(x: Double) = ox + (x - x0) * sc
        fun Y(y: Double) = oy + (y0 - y) * sc
        val pb = Y(y1)
        val sb = StringBuilder("<rect x=\"${fx(X(x0))}\" y=\"$oy\" width=\"${fx(W * sc)}\" height=\"${fx(H * sc)}\" fill=\"#e2c992\" stroke=\"#7a6a40\"/>")
        val ord = pts.sortedWith { a, b -> if (abs(b[1] - a[1]) > 2) b[1].compareTo(a[1]) else a[0].compareTo(b[0]) }
        val r = max(4.0, min(9.0, or(n(d, "bolt"), 20.0) * sc / 2 + 2))
        ord.forEachIndexed { i, p -> sb.append("<circle cx=\"${fx(X(p[0]))}\" cy=\"${fx(Y(p[1]))}\" r=\"${fx(r)}\" fill=\"#fff\" stroke=\"#333\"/><text x=\"${fx(X(p[0]) + 7)}\" y=\"${fx(Y(p[1]) - 7)}\" font-size=\"9\" fill=\"#555\">${i + 1}</text>") }
        val lx = X(x0) - 18; val by = pb + 16; val vch = listOf(y0) + ys + y1; val hch = listOf(x0) + xs + x1
        for (i in 0 until vch.size - 1) sb.append(dim(lx, Y(vch[i]), lx, Y(vch[i + 1]))); for (i in 0 until hch.size - 1) sb.append(dim(X(hch[i]), by, X(hch[i + 1]), by))
        fun pc(x: Double, y: Double) = max(7.0, min(93.0, x / 4)) to max(6.0, min(95.0, y / 2.6))
        fun mv(i: Int) = (Y(vch[i]) + Y(vch[i + 1])) / 2
        fun mh(i: Int) = (X(hch[i]) + X(hch[i + 1])) / 2
        val at = linkedMapOf("ev" to pc(lx - 34, mv(0)), "sv" to pc(lx - 34, if (vch.size > 2) mv(1) else mv(0) + 20), "pv" to pc(lx - 34, mv(vch.size - 2)), "nv" to pc(lx - 34, min(250.0, mv(vch.size - 2) + 30)),
            "eh" to pc(mh(hch.size - 2) + 18, by + 18), "sh" to pc(if (hch.size > 2) mh(floor((hch.size - 1) / 2.0).toInt()) else mh(0), by + 18), "nh" to pc(X(x0) - 40, by + 18), "ph" to pc(205.0, max(10.0, oy - 10)))
        if (abs(at.getValue("ev").second - at.getValue("sv").second) < 9) at["sv"] = at.getValue("sv").let { it.first - 14 to it.second }
        if (abs(at.getValue("pv").second - at.getValue("sv").second) < 9) at["pv"] = at.getValue("pv").let { it.first - 14 to it.second }
        if (abs(at.getValue("nv").second - at.getValue("pv").second) < 9) at["nv"] = at.getValue("nv").let { it.first to it.second + 9 }
        if (abs(at.getValue("eh").first - at.getValue("sh").first) < 16) at["eh"] = at.getValue("eh").let { it.first + 14 to it.second }
        return Result(sb.toString(), at)
    }
    fun svg(d: CompDialog) = build(d).svg
    fun at(d: CompDialog) = build(d).at
}
