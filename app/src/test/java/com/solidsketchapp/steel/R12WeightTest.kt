package com.solidsketchapp.steel

import com.solidsketchapp.core.io.SsCodec
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.TeklaWeight
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The app's own weight of R12 (owner 2026-11-26): every part weighed by [TeklaWeight] (the app's port of web v66 + v70 "Weights ·
 * Tekla's own rule"), against web v70 part by part (tests/steel/golden/r12/weights_v70.json, 0.01 kg) and against Tekla per profile
 * (st.tkW, Tekla's own weight in its IFC). Writes build/r12-weights-app.md. Needs Manifold: -PhostJni.
 */
class R12WeightTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test fun appWeighsR12LikeTekla() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }; val engine = ManifoldBooleanEngine()
        val doc = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R12.ss").inputStream())
        val regen = StRegen(StBuild(data.profiles, union = { engine.union(it) }), engine, data.profiles, data.tk).also { it.ui = TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
        val conns = regen.conns(doc); regen.allConns = conns
        val holed = conns.filter { it.type == "TKBOLT" }.flatMap { it.parts }.toSet(); val notHoles = conns.filter { it.type != "TKBOLT" }
        val tw = TeklaWeight(data.profiles, engine)
        val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/r12/weights_v70.json").readText()).jsonObject.getValue("parts").jsonObject
        class Row(var n: Int = 0, var tk: Double = 0.0, var app: Double = 0.0, var web: Double = 0.0)
        val by = sortedMapOf<String, Row>(); var bad = 0; val badList = ArrayList<String>()
        for (id in doc.bodyIds()) {
            val st = Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject["st"] as? JsonObject ?: continue
            val kind = (st["kind"] as? JsonPrimitive)?.contentOrNull; if (kind != "plate" && kind != "member") continue
            val w = tw.part(st, doc.bodyMesh(id)!!, id.value in holed, noHoles = { regen.cutPart(id.value, st, notHoles) })
            val p = (st["tprof"] as? JsonPrimitive)?.contentOrNull ?: ""; val mat = (st["mat"] as? JsonPrimitive)?.contentOrNull ?: ""
            val key = if (p.uppercase().startsWith("PL") || p.uppercase().startsWith("FLT")) "plates $mat" else p
            val web = gold[id.value]?.jsonPrimitive?.double ?: Double.NaN
            val r = by.getOrPut(key) { Row() }; r.n++; r.tk += (st["tkW"] as? JsonPrimitive)?.doubleOrNull ?: 0.0; r.app += w.weight; r.web += web
            if (!(abs(w.weight - web) <= 0.011)) { bad++; if (badList.size < 15) badList += "${id.value} $p: app ${w.weight} web $web own ${w.ownArea} net ${w.net} len ${w.len} holed ${id.value in holed}" }
        }
        val sb = StringBuilder("| Profile | Parts | Tekla (kg) | App (kg) | App − Tekla (kg) | Web v70 (kg) |\n|---|---|---|---|---|---|\n")
        var T = 0.0; var A = 0.0; var Wb = 0.0
        for ((k, r) in by.entries.sortedByDescending { it.value.tk }) { sb.append("| $k | ${r.n} | %.2f | %.2f | %+.2f | %.2f |\n".format(java.util.Locale.ROOT, r.tk, r.app, r.app - r.tk, r.web)); T += r.tk; A += r.app; Wb += r.web }
        sb.append("| **Total** | ${by.values.sumOf { it.n }} | **%.2f** | **%.2f** | %+.2f | %.2f |\n".format(java.util.Locale.ROOT, T, A, A - T, Wb))
        sb.append("\nParts where the app differs from web v70 by more than 0.01 kg: $bad\n").append(badList.joinToString("\n"))
        File(root, "build").mkdirs(); File(root, "build/r12-weights-app.md").writeText(sb.toString()); println(sb)
        assertTrue(badList.joinToString("\n"), bad == 0)
        assertTrue("total app %.2f vs Tekla %.2f".format(A, T), abs(A - T) <= 10.0)
    }
}
