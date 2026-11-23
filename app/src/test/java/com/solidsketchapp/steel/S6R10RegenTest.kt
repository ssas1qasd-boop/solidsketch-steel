package com.solidsketchapp.steel

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.SsCodec
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.conn.TkConns
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * S6 acceptance with the real Manifold booleans (host JNI: ./gradlew :app:testDebugUnitTest -PhostJni):
 * the TKBOLT / TKCUT golden cases with their union volumes, and R10: every member and plate rebuilt from its st and cut
 * by its 3,035 TKBOLT and 645 TKCUT connections, every bolt group made again, compared part by part with v69's own
 * regeneration (tests/steel/golden/s6/r10_regen.json, owner bundle).
 */
class S6R10RegenTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val engine by lazy { ManifoldBooleanEngine() }

    @Before fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    private fun box(m: Mesh): List<Double> {
        val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return lo.toList() + hi.toList()
    }

    @Test
    fun boltUnionVolumesEqualV69() {
        val cases = Json.parseToJsonElement(File(root, "tests/steel/golden/s6/bolts.json").readText()).jsonObject.getValue("cases").jsonObject
        val bad = mutableListOf<String>()
        for ((name, c0) in cases) {
            val c = c0.jsonObject; if (c.containsKey("cut")) continue
            val g = TkConns.geom("TKBOLT", listOf("1", "2"), c.getValue("q").jsonObject, StBuild(data.profiles), StRegen.DEFS.getValue("TKBOLT"))
            val gh = c["holes1"]
            if (gh != null && gh !is JsonNull && gh.jsonArray.isNotEmpty()) {
                val v = engine.volume(engine.union(g!!.cuts.getValue("1"))); val gv = gh.jsonArray[0].jsonObject.getValue("volume").jsonPrimitive.double
                if (abs(v - gv) > max(1.0, gv * 1e-3)) bad += "$name holes $v vs $gv"
            }
            val gp = c["part"]
            if (gp != null && gp !is JsonNull) {
                val v = engine.volume(engine.union(g!!.parts.single().pieces)); val gv = gp.jsonObject.getValue("volume").jsonPrimitive.double
                if (abs(v - gv) > max(1.0, gv * 1e-3)) bad += "$name bolts $v vs $gv"
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun r10PartsAfterCutsEqualV69() {
        val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/s6/r10_regen.json").readText()).jsonObject
        val gp = gold.getValue("parts").jsonObject
        val doc = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R10.ss").inputStream())
        val regen = StRegen(StBuild(data.profiles, union = { engine.union(it) }), engine)
        val conns = regen.conns(doc)
        assertEquals(3035, conns.count { it.type == "TKBOLT" }); assertEquals(645, conns.count { it.type == "TKCUT" })
        val t0 = System.nanoTime()
        var n = 0; val worst = ArrayList<Pair<Double, String>>(); var volBad = 0; var boxBad = 0
        for (id in doc.bodyIds()) {
            val st = Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject["st"] as? JsonObject ?: continue
            val kind = st["kind"]?.jsonPrimitive?.content; if (kind != "member" && kind != "plate") continue
            val g = gp[id.value]?.jsonArray?.map { it.jsonPrimitive.double } ?: continue
            val m = regen.cutPart(id.value, st, conns); n++
            val v = engine.volume(m); val b = box(m)
            val dv = abs(v - g[0]) / max(g[0], 1.0); val db = (0..5).maxOf { abs(b[it] - g[it + 1]) }
            if (dv > 1e-3) volBad++; if (db > 0.1) boxBad++
            worst += max(dv * 1000, db) to "${id.value} ${st["name"]?.jsonPrimitive?.content} vol ${"%.1f".format(java.util.Locale.ROOT, v)} vs ${g[0]} (${"%.2e".format(java.util.Locale.ROOT, dv)}), box Δ ${"%.3f".format(java.util.Locale.ROOT, db)} mm"
        }
        val s = (System.nanoTime() - t0) / 1e9
        worst.sortByDescending { it.first }
        println("R10 regen: $n parts in ${"%.0f".format(java.util.Locale.ROOT, s)} s; volume off by > 0.1 %: $volBad; box off by > 0.1 mm: $boxBad; worst:\n" + worst.take(8).joinToString("\n") { "  " + it.second })
        assertEquals(gold.getValue("nParts").jsonPrimitive.content.toInt(), n)
        assertTrue("parts differ from v69 (see output)", volBad == 0 && boxBad == 0)
    }

    @Test
    fun r10BoltGroupsEqualV69() {
        val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/s6/r10_regen.json").readText()).jsonObject
        val gb = gold.getValue("bolts").jsonArray.map { r -> r.jsonArray.let { it[0].jsonPrimitive.content to it.drop(1).map { v -> v.jsonPrimitive.double } } }.toMap()
        val doc = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R10.ss").inputStream())
        val regen = StRegen(StBuild(data.profiles), engine)
        var n = 0; var bad = 0; val notes = ArrayList<String>()
        for (c in regen.conns(doc)) {
            if (c.type != "TKBOLT") continue
            val p = regen.geom(c)?.parts?.singleOrNull() ?: continue
            n++
            val m = engine.union(p.pieces); val v = engine.volume(m); val b = box(m); val g = gb[c.id] ?: run { bad++; notes += "${c.id}: not in v69"; null } ?: continue
            if (abs(v - g[0]) / g[0] > 1e-3 || (0..5).any { abs(b[it] - g[it + 1]) > 0.1 }) { bad++; if (notes.size < 8) notes += "${c.id}: vol $v vs ${g[0]}" }
        }
        println("R10 bolt groups: $n made, ${n - bad} equal to v69\n" + notes.joinToString("\n"))
        assertEquals(gold.getValue("nBolts").jsonPrimitive.content.toInt(), n)
        assertEquals(0, bad)
    }
}
