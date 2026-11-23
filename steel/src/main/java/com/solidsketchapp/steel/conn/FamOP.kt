package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * OP family: openings (hole around a part 92, duct sleeve 1029, diaphragms 1058 / 1059, rebar hole). Live definition:
 * v69 FAM.OP (L15157).
 */
object FamOP {
    const val PORTED = true

    private fun ring(r: Double) = List(32) { i -> doubleArrayOf(r * cos(i * PI / 16), r * sin(i * PI / 16)) }

    fun gen(x: FamCtx) {
        val k = x.s("kind")
        if (k == "hole" && x.b != null) {
            val mb = Joints.mf(x.b, x.profiles); val l = mb.c2.distanceTo(mb.c1); val g = x.n("gap")
            x.cut(x.aId, Joints.box3(mb.c1.lerp(mb.c2, 0.5), mb.a, mb.eh, -l / 2 - 1, l / 2 + 1, -mb.h / 2 - g, mb.h / 2 + g, -mb.b / 2 - g, mb.b / 2 + g)); return
        }
        val m = Joints.mf(x.a, x.profiles); val pt = x.pt ?: m.c1.lerp(m.c2, 0.5); val p0 = FamCL.faceAt(m, pt).pc   // M.c1 + a·FA.along
        if (k == "sleeve" || k == "hole") {
            val r = (if (x.t("d")) x.n("d") else m.h * 0.3) / 2
            x.cut(x.aId, stCyl(p0.add(m.es, -m.b), m.es, r + (if (k == "sleeve") 8.5 else 0.0), 2 * m.b, 24))
            if (k == "sleeve") x.plate("SLEEVE", Prism.extrude(Region(ring(r + 8), listOf(ring(r))), m.b, Prism.Frame(p0.add(m.es, -m.b / 2), m.a, m.eh, m.es)), 8.0, "PIPE")
            return
        }
        if (k == "diaphragm") {
            val sec = Sections.of(x.profiles.lookup(x.a.str("prof") ?: "")) ?: throw IllegalStateException("no section")
            val t1058 = x.conn?.type == "T1058"; val t = x.n("t")
            val poly = if (t1058) sec.outer.map { doubleArrayOf(it[0] * 1.15, it[1] * 1.15) } else (sec.holes.firstOrNull() ?: sec.outer)
            val fr = Prism.Frame(p0, m.es, m.eh, m.a)
            x.plate("DIAPHRAGM", Prism.extrude(Region(poly), t, fr), t)
            if (t1058) x.cut(x.aId, Prism.extrude(Region(sec.outer.map { doubleArrayOf(it[0] * 1.2, it[1] * 1.2) }), t, fr))
            return
        }
        throw IllegalStateException("not built")
    }
}
