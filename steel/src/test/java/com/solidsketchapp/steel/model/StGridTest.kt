package com.solidsketchapp.steel.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** S5: grid and levels in Tekla syntax equal web v69 (golden: tests/steel/golden/s5/grid.json, from the live stParse). */
class StGridTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/s5/grid.json").readText()).jsonObject

    @Test
    fun parseEqualsV69() {
        val cases = gold.getValue("parse").jsonArray
        assertEquals(16, cases.size)
        for (c in cases) {
            val txt = c.jsonArray[0].jsonPrimitive.content
            assertEquals("'$txt'", c.jsonArray[1].jsonArray.map { it.jsonPrimitive.double }, StGrid.parse(txt))
        }
    }

    @Test
    fun acceptanceGridEqualsV69() {
        // CLAUDE.md target workflow: X "0 4*13195", Y "0 5*5570", Z "0 7500 1500".
        val g = StGrid("0 4*13195", "0 5*5570", "0 7500 1500")
        assertEquals(listOf(0.0, 13195.0, 26390.0, 39585.0, 52780.0), g.xs)
        assertEquals(listOf(0.0, 5570.0, 11140.0, 16710.0, 22280.0, 27850.0), g.ys)
        assertEquals(listOf(0.0, 7500.0, 9000.0), g.zs)
        assertEquals(listOf("1", "2", "3", "4", "5"), g.xs.indices.map(g::xLabel))
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), g.ys.indices.map(g::yLabel))
        assertEquals(listOf("+0", "+7500", "+9000"), g.zs.indices.map(g::levelLabel))
        // Every v69 grid point: 5 x 6 lines on 3 levels.
        val lines = StGridLines.build(g, level = 1)
        assertNotNull(lines)
        assertEquals(3 * (5 + 6) * 6, lines!!.all.size); assertEquals((5 + 6) * 6, lines.work.size)
        assertEquals(7500f, lines.work[2]); assertEquals(-1500f, lines.work[1]); assertEquals(27850f + 1500f, lines.work[4])
    }

    @Test
    fun lettersEqualV69() {
        for (c in gold.getValue("letters").jsonArray)
            assertEquals(c.jsonArray[1].jsonPrimitive.content, StGrid.letters(c.jsonArray[0].jsonPrimitive.int))
    }

    @Test
    fun jsonRoundTripAndLabels() {
        val g = StGrid("0 2*6000", "0 5000", "0 3000", lx = "A1 A2", ly = "")
        assertEquals(g, StGrid.fromJson(g.toJson()))
        assertEquals(listOf("A1", "A2", "3"), (0..2).map(g::xLabel))
        assertEquals("-300", StGrid("0", "0", "-300").levelLabel(0))
        assertEquals(StGrid("0 6*6000", "0 20000", "0 7000"), StGrid.DEFAULT)
    }
}
