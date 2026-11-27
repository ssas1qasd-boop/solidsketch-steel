package com.solidsketchapp.steel.comp

import com.solidsketchapp.steel.model.FakeDoc
import com.solidsketchapp.steel.data.SteelData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S7 acceptance (catalog): the sheet has v69's 18 groups and 255 items (246 Tekla components, the 4 dialogs v49 adds —
 * Tcl, Ttg, Ttgx, Tsag —, v70's Eave haunch Teh and 2 applications); search, recently used and the pick prompts equal v69; every dialog spec,
 * accessor, icon, picture and pictogram loads.
 */
class S7CatalogTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val gold by lazy { Json.parseToJsonElement(File(root, "tests/steel/golden/s7/dialog.json").readText()).jsonObject }

    @Test fun catalogHasEveryComponent() {
        assertEquals(18, ui.catalog.size)
        val items = ui.catalog.flatMap { it.second }.distinct()
        assertEquals(256, items.size)   // v70 adds the Eave haunch (Teh, C02h), the Stair end plate (Tsep) and Stair treads (C18), the Column cap plate (Tcolcap, X02)
        assertEquals(254, ui.conn.size)
        // the 246 TK components (S4 asset) are all on the sheet, in their Tekla groups, with v69's CONN label and pick prompts
        assertEquals(246, data.tk.components.size)
        for (c in data.tk.components) {
            val d = ui.conn.getValue(c.id)
            // v69 moves three tube components to other families after building CONN (L15458, L15461)
            assertEquals(c.name, d.label); assertEquals(mapOf("T102t" to "GU", "T103t" to "GU", "Ttc" to "WF")[c.id] ?: c.family, d.fam); assertEquals(c.group, d.grp)
            assertEquals(data.tk.pickOrders[c.pick] ?: data.tk.pickOrders.getValue("ms"), d.parts)
            assertTrue(c.id, ui.catalog.first { it.first == c.group }.second.contains(c.id))
        }
        assertEquals(setOf("Tcl", "Ttg", "Ttgx", "Tsag", "Teh", "Tsep", "stairtreads", "Tcolcap"), ui.conn.keys - data.tk.components.map { it.id }.toSet())
        assertEquals(listOf("@numbering", "@checks"), ui.catalog.last().second)
        assertEquals("End plate (144)", ui.conn.getValue("T144").tekla)
    }

    @Test fun searchEqualsV69() {
        val comps = Components(ui, FakeDoc(), null, MemoryStore(), null)
        for ((f, exp) in gold.getValue("search").jsonObject) assertEquals("search '$f'", exp.jsonArray.map { it.jsonPrimitive.content }, comps.search(f))
    }

    @Test fun recentlyUsedAndPrompts() {
        val comps = Components(ui, FakeDoc(), null, MemoryStore(), null)
        assertEquals(ui.catalog, comps.groups())
        for (k in listOf("T144", "T146", "T1042", "T39", "Tcl", "Ttg", "T144")) comps.noteRecent(k)
        assertEquals(listOf("T144", "Ttg", "Tcl", "T39", "T1042"), comps.recent())
        assertEquals("Recently used" to listOf("T144", "Ttg", "Tcl", "T39", "T1042"), comps.groups().first())
        assertEquals("End plate (144): pick the main part", comps.prompt("T144", 0))
        assertEquals("End plate (144): pick the secondary part", comps.prompt("T144", 1))
        assertEquals("End plate (144): pick the secondary part", comps.prompt("T144", 5))
        val one = ui.conn.values.first { it.parts.size == 1 }
        assertEquals("${one.tekla}: pick the part", comps.prompt(one.key, 3))
    }

    @Test fun presetsAndDefaults() {
        val store = MemoryStore(); val comps = Components(ui, FakeDoc(), null, store, null)
        assertEquals(ui.conn.getValue("T144").def, comps.cq("T144"))
        val d = CompDialog(comps, "T144", null)
        d.inputs.first { it.f.k == "tp" }.let { d.setText(it, "15") }
        d.save("standard"); assertEquals("15", Js.str(comps.cq("T144")["tp"]))
        d.inputs.first { it.f.k == "tp" }.let { d.setText(it, "12") }
        comps.savePreset("T144", "heavy", d.read(true)); assertEquals(listOf("heavy"), comps.presets("T144"))
        val d2 = CompDialog(comps, "T144", null); assertEquals("15", d2.text(d2.inputs.first { it.f.k == "tp" }))
        assertTrue(d2.load("heavy")); assertEquals("12", d2.text(d2.inputs.first { it.f.k == "tp" }))
        // the store is v69's localStorage JSON: ss.connDef / ss.connSets / ss.connRecent
        assertTrue(store.get("ss.connSets")!!.startsWith("{\"T144\":{\"heavy\":{"))
    }

    @Test fun everySpecAndPictureLoads() {
        assertEquals(setOf("acc0", "acc1", "acc2"), ui.acc.keys)
        for ((_, src) in ui.acc) assertTrue(src, Acc.KNOWN.containsKey(src))
        val comps = Components(ui, FakeDoc(), null, MemoryStore(), null)
        var nIn = 0
        for (k in ui.conn.keys) { val d = CompDialog(comps, k, null); nIn += d.inputs.size; d.read(true); d.read(false) }
        assertTrue(nIn > 5000)
        for ((k, s) in ui.icons) assertTrue("icon $k", Svg.parse(s, floatArrayOf(0f, 0f, 120f, 80f)).items.size >= 3)
        for ((k, s) in ui.pics) if (s.isNotEmpty()) assertTrue("pic $k", Svg.parse(s).items.isNotEmpty())
        for ((k, s) in ui.pg) assertTrue("pictogram $k", Svg.parse(s, floatArrayOf(0f, 0f, 24f, 24f)).items.isNotEmpty())
        val pics = ui.specs.values.flatMap { it.tabs }.mapNotNull { it.pic }.toSet()
        assertTrue(pics.toString(), (pics - "icon" - "bolts").all { it in ui.pics })
        for (t in ui.thumbs.values) for (p in listOfNotNull(t.t, t.p)) assertTrue(p, File(root, "steel/src/main/assets/steel/$p").length() > 100)
    }
}
