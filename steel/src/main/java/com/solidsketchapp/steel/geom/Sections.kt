package com.solidsketchapp.steel.geom

import com.solidsketchapp.steel.data.Profile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Profile cross-sections in mm, centred on the profile's box: exact port of v69's live `stSection` chain
 * (v32 L13591 + v41 wrapper L15217). Same point order and arc subdivision (4 steps per quarter, 32 for round).
 */
object Sections {
    private fun p(x: Double, y: Double) = doubleArrayOf(x, y)

    /** v69 stArc: n + 1 points from angle a0 to a1 (degrees) on a circle about (cx, cy). */
    private fun arc(out: MutableList<DoubleArray>, cx: Double, cy: Double, r: Double, a0: Double, a1: Double, n: Int = 4) {
        for (i in 0..n) { val a = (a0 + (a1 - a0) * i / n) * PI / 180; out += p(cx + r * cos(a), cy + r * sin(a)) }
    }

    /** v69 stRRect: rounded rectangle w × hh, corner radius r (square when r <= 0.01). */
    fun rrect(w: Double, hh: Double, r0: Double, n: Int = 4): List<DoubleArray> {
        val o = mutableListOf<DoubleArray>(); val x = w / 2; val y = hh / 2; val r = min(r0, min(x, y))
        if (r <= 0.01) return listOf(p(-x, -y), p(x, -y), p(x, y), p(-x, y))
        arc(o, x - r, -y + r, r, -90.0, 0.0, n); arc(o, x - r, y - r, r, 0.0, 90.0, n)
        arc(o, -x + r, y - r, r, 90.0, 180.0, n); arc(o, -x + r, -y + r, r, 180.0, 270.0, n)
        return o
    }

    private fun circle(r: Double) = List(32) { i -> p(r * cos(i * PI / 16), r * sin(i * PI / 16)) }

    /** The live stSection: null when v69 has no section for the profile (e.g. `PL20` without a width). */
    fun of(pr: Profile?): Region? {
        if (pr == null) return null
        // ---- v41 wrapper ----
        if (pr.kind == "I" && pr.built != null && pr.built != 0.0) {
            val hh = pr.h!!; val y0 = -hh / 2; val y1 = hh / 2; val bt = pr.bt ?: pr.b!!; val bb = pr.bb ?: pr.b!!
            val tf = pr.tf!!; val tf2 = pr.tf2 ?: pr.tf; val tw = pr.tw!!
            return Region(listOf(p(-bb / 2, y0), p(bb / 2, y0), p(bb / 2, y0 + tf2), p(tw / 2, y0 + tf2), p(tw / 2, y1 - tf), p(bt / 2, y1 - tf),
                p(bt / 2, y1), p(-bt / 2, y1), p(-bt / 2, y1 - tf), p(-tw / 2, y1 - tf), p(-tw / 2, y0 + tf2), p(-bb / 2, y0 + tf2)))
        }
        if (pr.kind == "U") {
            val hh = pr.h!!; val bw = pr.b!!; val tw = pr.tw!!; val tf = pr.tf!!; val x0 = -bw / 2
            return Region(listOf(p(x0, -hh / 2), p(x0 + bw, -hh / 2), p(x0 + bw, -hh / 2 + tf), p(x0 + tw, -hh / 2 + tf), p(x0 + tw, hh / 2 - tf),
                p(x0 + bw, hh / 2 - tf), p(x0 + bw, hh / 2), p(x0, hh / 2)))
        }
        if (pr.kind == "C" || pr.kind == "Z") {
            val hh = pr.h!!; val bw = pr.b!!; val t = pr.t!!; val c = pr.c!!; val x0 = -bw / 2
            if (pr.kind == "C") return Region(listOf(p(x0, -hh / 2), p(x0 + bw, -hh / 2), p(x0 + bw, -hh / 2 + c), p(x0 + bw - t, -hh / 2 + c),
                p(x0 + bw - t, -hh / 2 + t), p(x0 + t, -hh / 2 + t), p(x0 + t, hh / 2 - t), p(x0 + bw - t, hh / 2 - t), p(x0 + bw - t, hh / 2 - c),
                p(x0 + bw, hh / 2 - c), p(x0 + bw, hh / 2), p(x0, hh / 2)))
            return Region(listOf(p(-t / 2 - bw + t, -hh / 2 + c), p(-t / 2 - bw + t, -hh / 2), p(t / 2, -hh / 2), p(t / 2, hh / 2 - t),
                p(t / 2 + bw - t, hh / 2 - t), p(t / 2 + bw - t, hh / 2 - c), p(t / 2 + bw, hh / 2 - c), p(t / 2 + bw, hh / 2), p(-t / 2, hh / 2),
                p(-t / 2, -hh / 2 + t), p(-t / 2 - bw + 2 * t, -hh / 2 + t), p(-t / 2 - bw + 2 * t, -hh / 2 + c)))
        }
        // ---- v32 base ----
        when (pr.kind) {
            "I" -> {
                val hh = pr.h!!; val bw = pr.b!!; val tw = pr.tw!!; val tf = pr.tf!!; val r = pr.r!!; val y0 = -hh / 2; val y1 = hh / 2
                val o = mutableListOf(p(-bw / 2, y0), p(bw / 2, y0), p(bw / 2, y0 + tf))
                arc(o, tw / 2 + r, y0 + tf + r, r, -90.0, -180.0); arc(o, tw / 2 + r, y1 - tf - r, r, 180.0, 90.0)
                o += listOf(p(bw / 2, y1 - tf), p(bw / 2, y1), p(-bw / 2, y1), p(-bw / 2, y1 - tf))
                arc(o, -tw / 2 - r, y1 - tf - r, r, 90.0, 0.0); arc(o, -tw / 2 - r, y0 + tf + r, r, 0.0, -90.0); o += p(-bw / 2, y0 + tf)
                return Region(o)
            }
            "R" -> return Region(rrect(pr.b!!, pr.h!!, pr.r!!), listOf(rrect(pr.b - 2 * pr.t!!, pr.h - 2 * pr.t, max(0.0, pr.r - pr.t))))
            "O" -> return Region(circle(pr.d!! / 2), if (pr.t!! >= pr.d / 2 - 0.01) emptyList() else listOf(circle(pr.d / 2 - pr.t)))
            "P" -> return if (pr.b != null && pr.b != 0.0) Region(rrect(pr.b, pr.h!!, 0.0)) else null
            "L" -> { val hh = pr.h!!; val bw = pr.b!!; val t = pr.t!!
                return Region(listOf(p(-bw / 2, -hh / 2), p(bw / 2, -hh / 2), p(bw / 2, -hh / 2 + t), p(-bw / 2 + t, -hh / 2 + t), p(-bw / 2 + t, hh / 2), p(-bw / 2, hh / 2))) }
        }
        return null
    }
}
