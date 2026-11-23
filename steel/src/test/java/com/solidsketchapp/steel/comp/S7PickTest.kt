package com.solidsketchapp.steel.comp

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.mode.SteelMode
import com.solidsketchapp.steel.model.FakeDoc
import com.solidsketchapp.steel.model.FakeUndo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S7: placing a component the v69 way (v38 stCatPick / stTap): prompts in pick order, "Pick a steel part",
 * "Already picked", the component made on the last pick in one undo step named after it, "created" / toast, Done;
 * then (v49 / v58) a tap on its symbol selects it and asks for its dialog. Booleans are faked (no Manifold here).
 */
class S7PickTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }

    private object NoBooleans : BooleanEngine {
        override fun subtract(a: Mesh, cutters: List<Mesh>) = a
        override fun union(parts: List<Mesh>) = Prism.concat(parts)
        override fun intersect(a: Mesh, b: Mesh) = a
        override fun isClosedSolid(m: Mesh) = true
        override fun volume(m: Mesh) = 0.0
    }
    private class Sel(val hits: Map<Int, BodyId>) : SelectionApi {
        override fun pick(x: Float, y: Float) = hits[x.toInt()]?.let { SelectionApi.Hit.Body(it, Vec3(0.0, 0.0, 7000.0)) }
        override fun selected() = emptyList<BodyId>()
        override fun setSelected(ids: Collection<BodyId>) {}
        override fun setHighlighted(ids: Collection<BodyId>) {}
        override fun addListener(listener: SelectionApi.Listener) {}
        override fun removeListener(listener: SelectionApi.Listener) {}
    }

    @Test fun pickOrderMakesTheComponent() {
        val doc = FakeDoc(); val undo = FakeUndo(); val build = StBuild(data.profiles)
        val regen = StRegen(build, NoBooleans, data.profiles, data.tk)
        val m = SteelMode(data, doc, undo, Sel(mapOf(100 to BodyId("1"), 200 to BodyId("2"))), build,
            project = { p -> (p.x / 10).toFloat() to (-p.y / 10).toFloat() }, ray = { _, _ -> null }, regen = regen)
        val comps = Components(ui, doc, undo, MemoryStore(), regen); m.comps = comps; m.checks = TkCheck(comps, doc, NoBooleans, data.profiles)
        m.toggle()
        fun mem(set: String, o: String) = m.parts.add(JsonObject(LinkedHashMap(data.propertySet(set)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(Json.parseToJsonElement(o).jsonObject) }), "m")
        mem("COLUMN", """{"prof":"HEA300","p1":[0,0,0],"p2":[0,0,7500]}"""); mem("BEAM", """{"prof":"IPE360","p1":[0,0,7000],"p2":[6000,0,7000]}""")
        val d = ui.conn.getValue("T144")
        m.catPick("T144")
        assertEquals("End plate (144): pick the main part", m.hint()); assertEquals(listOf("T144"), comps.recent())
        assertTrue(m.tap(5f, 5f)); assertEquals("Pick a steel part · End plate (144): pick the main part", m.hint())
        assertTrue(m.tap(100f, 0f)); assertEquals("End plate (144): pick the secondary part", m.hint())
        assertTrue(m.tap(100f, 0f)); assertEquals("Already picked · End plate (144): pick the secondary part", m.hint())
        assertTrue(m.tap(200f, 0f))
        val conns = Json.parseToJsonElement(doc.field("stConns")!!).jsonArray
        assertEquals(1, conns.size); val c = conns[0].jsonObject
        assertEquals("T144", c.getValue("type").jsonPrimitive.content); assertEquals(listOf("1", "2"), c.getValue("parts").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(d.def.keys, c.getValue("q").jsonObject.keys)              // q = stCQ(k)
        assertEquals(d.label, undo.steps.last())                               // one undo step named after the component
        assertEquals("${d.label} created · End plate (144): pick the main part · Done to stop", m.hint())
        assertEquals("${d.label} created", m.lastToast); m.lastToast = null
        assertTrue(comps.kids(comps.conns()[0]).size >= 2)                     // END PLATE and BOLTS bodies
        m.catDone(); assertNull(m.catK)
        // the symbol sits at the joint (0, 0, 6820 → pixel 0, 0); a tap on it selects the component and asks for its dialog
        val sy = m.symbolsOnScreen(); assertEquals(1, sy.size); assertEquals(0f, sy[0].x, 0.5f)
        assertTrue(m.tap(3f, 2f))
        assertEquals("T144" to "1", m.dialogRequest); assertEquals("1", m.selConn)
        assertEquals("End plate (144) · connection 1", m.lastToast)
        assertTrue(m.fanLines().size == 2)
        m.tap(500f, 500f); assertNull(m.selConn)                               // a tap elsewhere clears the selection
    }
}
