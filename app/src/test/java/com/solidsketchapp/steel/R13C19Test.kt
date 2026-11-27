package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.SsCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * C19 Railing welds as Tekla (docs/steel/PARITY.md "C19"): the app rebuilds every R13 stanchion CHS48*3 (26: the 22 on the four stair
 * stringers and the 4 on the landing beams) with the R13 inputs (row 19: Weld (no parts) from the part the stanchion stands on, a = 2,
 * fit = face) and checks each one against R13 within 0.1 mm:
 *  - the axis (x, y of the solid's centre) and the member line p1 / p2;
 *  - the top square at R13's top (every vertex of the upper half at one z);
 *  - the foot on R13's foot plane (the plane through R13's foot vertices; no app vertex off it by more than 0.1 mm), lowest / highest foot z;
 *  - the weight = Tekla's st.tkW (3.18 kg, TeklaWeight: CHS48*3 as Tekla's 12-gon), 0.005 kg.
 * Needs Manifold: -PhostJni. Writes build/r13-c19-report.txt.
 */
class R13C19Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }
    private fun v3(e: kotlinx.serialization.json.JsonElement?) = e!!.jsonArray.map { it.jsonPrimitive.double }

    /** Least squares z = c0 + c1 x + c2 y through the points (the foot cut plane); a level foot: c1 = c2 = 0. */
    private fun plane(p: List<DoubleArray>): DoubleArray {
        val zs = p.map { it[2] }; if (zs.max() - zs.min() < 1e-3) return doubleArrayOf(zs.average(), 0.0, 0.0)
        val n = p.size.toDouble(); val sx = p.sumOf { it[0] }; val sy = p.sumOf { it[1] }; val sz = p.sumOf { it[2] }
        val sxx = p.sumOf { it[0] * it[0] }; val syy = p.sumOf { it[1] * it[1] }; val sxy = p.sumOf { it[0] * it[1] }; val sxz = p.sumOf { it[0] * it[2] }; val syz = p.sumOf { it[1] * it[2] }
        val a = arrayOf(doubleArrayOf(n, sx, sy, sz), doubleArrayOf(sx, sxx, sxy, sxz), doubleArrayOf(sy, sxy, syy, syz))
        for (c in 0..2) { val piv = (c..2).maxBy { abs(a[it][c]) }; val t = a[c]; a[c] = a[piv]; a[piv] = t
            if (abs(a[c][c]) < 1e-9) { a[c][c] = 1.0; a[c][3] = 0.0; continue }   // a stanchion's foot points on a line in x (round tube at x = const): no x term
            for (r in 0..2) if (r != c) { val f = a[r][c] / a[c][c]; for (k in c..3) a[r][k] -= f * a[c][k] } }
        return DoubleArray(3) { a[it][3] / a[it][it] }
    }

    @Test fun c19StanchionsEqualR13() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r13/rebuild_inputs.json").readText()).jsonObject
        val r13 = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R13.ss").inputStream())
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }.filter { it[3].jsonPrimitive.content == "19" }
        val need = list.flatMap { e -> e[1].jsonArray.map { it.jsonPrimitive.int } }.toSortedSet()
        val ids = HashMap<Int, BodyId>(); for (i in need) { val st = members[i].getValue("st").jsonObject; ids[i] = m.parts.addBuilt(st, m.parts.mesh(st)) }
        m.comps.addMany(list.map { e -> Triple(e[0].jsonPrimitive.content, e[1].jsonArray.map { ids.getValue(it.jsonPrimitive.int) }, e[2].jsonObject) }, merge = true)
        assertEquals(list.size, m.comps.conns().size)
        val engine = com.solidsketchapp.core.view.ManifoldBooleanEngine(); val tw = com.solidsketchapp.steel.model.TeklaWeight(t.data.profiles, engine)
        val app = need.associateWith { i -> val id = ids.getValue(i); Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject.getValue("st").jsonObject to m.doc.bodyMesh(id)!! }
        val bad = ArrayList<String>(); var n = 0; var nOk = 0; var wR = 0.0; var wA = 0.0; val rows = ArrayList<String>()
        for (id in r13.bodyIds()) { val st = Json.parseToJsonElement(r13.bodyJson(id) ?: continue).jsonObject["st"]?.jsonObject ?: continue
            if (st["name"]?.jsonPrimitive?.content != "Stanchion") continue; n++
            val rp1 = v3(st["p1"]); val rp2 = v3(st["p2"]); val rv = pts(r13.bodyMesh(id)!!); val tk = st.getValue("tkW").jsonPrimitive.double
            val hit = app.entries.firstOrNull { (_, p) -> val a1 = v3(p.first["p1"]); val a2 = v3(p.first["p2"]); (0..2).all { abs(a1[it] - rp1[it]) <= 0.1 && abs(a2[it] - rp2[it]) <= 0.1 } }
            if (hit == null) { bad += "R13 ${id.value}: no app stanchion with p1 $rp1 / p2 $rp2"; continue }
            val (ast, mesh) = hit.value; val av = pts(mesh); val err = ArrayList<String>()
            val rTop = rv.maxOf { it[2] }; val rBot = rv.minOf { it[2] }; val mid = (rTop + rBot) / 2
            val aTop = av.maxOf { it[2] }; val aBot = av.minOf { it[2] }
            if (abs(aTop - rTop) > 0.1) err += "top %.3f vs %.3f".format(java.util.Locale.ROOT, aTop, rTop)
            val topSpread = av.filter { it[2] > mid }.let { u -> u.maxOf { it[2] } - u.minOf { it[2] } }
            if (topSpread > 0.1) err += "top not square (z spread %.3f)".format(java.util.Locale.ROOT, topSpread)
            val rf = rv.filter { it[2] < mid }; val pl = plane(rf); fun off(p: DoubleArray) = p[2] - (pl[0] + pl[1] * p[0] + pl[2] * p[1])
            val rRes = rf.maxOf { abs(off(it)) }; val aRes = av.filter { it[2] < mid }.maxOf { abs(off(it)) }
            if (aRes > 0.1 + rRes) err += "foot off R13's foot plane by %.3f (R13's own %.3f)".format(java.util.Locale.ROOT, aRes, rRes)
            if (abs(aBot - rBot) > 0.1) err += "foot low %.3f vs %.3f".format(java.util.Locale.ROOT, aBot, rBot)
            val rFootHi = rf.maxOf { it[2] }; val aFootHi = av.filter { it[2] < mid }.maxOf { it[2] }
            if (abs(aFootHi - rFootHi) > 0.1) err += "foot high %.3f vs %.3f".format(java.util.Locale.ROOT, aFootHi, rFootHi)
            for (k in 0..1) { val c = (av.minOf { it[k] } + av.maxOf { it[k] }) / 2; val rc = (rv.minOf { it[k] } + rv.maxOf { it[k] }) / 2; if (abs(c - rc) > 0.1) err += "axis ${"xy"[k]} %.3f vs %.3f".format(java.util.Locale.ROOT, c, rc) }
            val w = tw.part(ast, mesh, false, { null }).weight; wR += tk; wA += w
            if (abs(Math.round(w * 100) / 100.0 - tk) > 0.005) err += "weight %.4f vs Tekla %.2f".format(java.util.Locale.ROOT, w, tk)
            rows += "R13 %s (%s): top %.3f / %.3f, foot %.3f..%.3f / %.3f..%.3f, plane residual %.3f, %.2f kg / %.2f%s".format(java.util.Locale.ROOT, id.value, st["amark"]?.jsonPrimitive?.content,
                aTop, rTop, aBot, aFootHi, rBot, rFootHi, aRes, w, tk, if (err.isEmpty()) "" else "  FAIL " + err.joinToString("; "))
            if (err.isEmpty()) nOk++ else bad += "R13 ${id.value}: " + err.joinToString("; ") }
        val rep = "stanchions matched (0.1 mm, top square, foot on R13's plane, st.tkW): $nOk / $n\nweight: app %.2f kg, Tekla %.2f kg\n".format(java.util.Locale.ROOT, wA, wR) + rows.joinToString("\n") + (if (bad.isEmpty()) "" else "\n" + bad.joinToString("\n"))
        File(root, "build").mkdirs(); File(root, "build/r13-c19-report.txt").writeText(rep + "\n"); println(rep.lines().take(4).joinToString("\n"))
        assertEquals(rep, 26, n); assertEquals(rep, n, nOk); assertEquals(rep, wR, wA, 0.005)
    }
}
