package com.solidsketchapp.steel.mode

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.FakeDoc
import com.solidsketchapp.steel.model.FakeUndo
import com.solidsketchapp.steel.model.StGrid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** S5: Steel mode taps like v69 stTap / stPick, with a top view where 1 px = 10 mm (x right, y up). */
class SteelModeTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }

    private class Sel : SelectionApi {
        var sel = listOf<BodyId>()
        override fun pick(x: Float, y: Float) = null
        override fun selected() = sel
        override fun setSelected(ids: Collection<BodyId>) { sel = ids.toList() }
        override fun setHighlighted(ids: Collection<BodyId>) {}
        override fun addListener(listener: SelectionApi.Listener) {}
        override fun removeListener(listener: SelectionApi.Listener) {}
    }

    // Top view: screen (x, y) = (X / 10, -Y / 10); z ignored, so every level's grid point lands on the same pixel.
    private fun mode(doc: FakeDoc, sel: Sel, undo: FakeUndo) = SteelMode(data, doc, undo, sel, StBuild(data.profiles),
        project = { p -> (p.x / 10).toFloat() to (-p.y / 10).toFloat() },
        ray = { x, y -> Vec3(x * 10.0, -y * 10.0, 100000.0) to Vec3(0.0, 0.0, -1.0) })

    private fun body(doc: FakeDoc, i: Int) = Json.parseToJsonElement(doc.bodies.values.toList()[i].first).jsonObject

    @Test
    fun columnFromAGridPointToTheNextLevel() {
        val doc = FakeDoc(); val sel = Sel(); val undo = FakeUndo(); val m = mode(doc, sel, undo)
        m.toggle(); assertTrue(m.on)
        assertEquals(StGrid.DEFAULT, m.grid)                                    // v69 grid when the document has none
        assertNull(m.setGrid(StGrid("0 4*13195", "0 5*5570", "0 7500 1500")))
        assertEquals(listOf("+0", "+7500", "+9000"), m.levelLabels())
        m.pickTool(SteelMode.Tool.COLUMN); assertEquals("COLUMN", m.set)
        assertTrue(m.tap(1319.5f + 3f, -557f))                                  // near grid point 2/B (snaps within 26 px)
        val st = body(doc, 0)["st"]!!.jsonObject
        assertEquals("HEA300", st["prof"]!!.jsonPrimitive.content)
        assertEquals(listOf(13195.0, 5570.0, 0.0), st["p1"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() })
        assertEquals(listOf(13195.0, 5570.0, 7500.0), st["p2"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() })   // next level up
        assertEquals("COLUMN 1", body(doc, 0)["name"]!!.jsonPrimitive.content)
        assertEquals(listOf("Grid", "Column HEA300"), undo.steps)
        assertEquals(listOf(BodyId("1")), sel.sel)                              // v69 selects the new part
        assertEquals(listOf("kind", "name", "prof", "mat", "cls", "fin", "pp", "ps", "ap", "as", "plane", "pOff", "rot", "rAng", "depth", "dOff", "dx1", "dx2", "p1", "p2"), st.keys.toList())
    }

    @Test
    fun beamBetweenTwoPicksAndHandlesMoveAnEnd() {
        val doc = FakeDoc(); val sel = Sel(); val undo = FakeUndo(); val m = mode(doc, sel, undo)
        m.toggle(); m.setGrid(StGrid("0 4*13195", "0 5*5570", "0 7500 1500")); m.setLevel(1)
        m.pickTool(SteelMode.Tool.BEAM); assertEquals("BEAM", m.set); m.useSet("RAFTER")
        assertTrue(m.tap(0f, 0f)); assertEquals(1, m.pts.size)                  // grid point 1/A (snaps to the first level listed: +0)
        assertTrue(m.tap(1319.5f, 0f))
        assertEquals("Rafter IPE360", undo.steps.last())
        val st = body(doc, 0)["st"]!!.jsonObject
        assertEquals("RAFTER", st["name"]!!.jsonPrimitive.content); assertEquals("IPE360", st["prof"]!!.jsonPrimitive.content)
        // Handles: no tool, the beam selected; tap its end handle, then a new point.
        m.pickTool(SteelMode.Tool.BEAM)                                          // tapping the active tool turns it off
        assertNull(m.tool)
        assertTrue(m.tap(1319.5f, 0f)); assertTrue(m.arm != null)
        assertTrue(m.tap(2639f, 0f))
        val st2 = body(doc, 0)["st"]!!.jsonObject
        assertEquals(26390.0, st2["p2"]!!.jsonArray[0].jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("Move part end", undo.steps.last())
    }

    @Test
    fun plateAndTypedPointsAndExit() {
        val doc = FakeDoc(); val sel = Sel(); val undo = FakeUndo(); val m = mode(doc, sel, undo)
        m.toggle(); m.setGrid(StGrid("0 6000", "0 6000", "0"))
        m.pickTool(SteelMode.Tool.PLATE); assertEquals("PLATE", m.set)
        m.tap(200f, -200f); assertEquals(2000.0, m.pts[0].x, 0.0)                // off the grid: the work plane, 10 mm raster
        assertNull(m.typedPoint("500 0 0")); assertNull(m.typedPoint("0 300"))
        assertEquals(3, m.pts.size)
        m.tap(200f, -200f)                                                       // the first corner again: finish
        val st = body(doc, 0)["st"]!!.jsonObject
        assertEquals("plate", st["kind"]!!.jsonPrimitive.content); assertEquals(3, st["pts"]!!.jsonArray.size)
        assertEquals("Contour plate PL20", undo.steps.last())
        m.toggle(); assertFalse(m.on); assertFalse(m.tap(0f, 0f))               // Exit Steel: taps go back to the viewport
    }
}
