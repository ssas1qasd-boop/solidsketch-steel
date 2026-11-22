package com.solidsketchapp.steel.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * S4: the JSON assets match web v69. `tools/golden/data_export.js` writes the assets and the golden
 * files from the running v69 page (and `--check` re-compares them with the HTML); these tests check the
 * committed assets against that export and the Kotlin readers against v69's own results.
 */
class SteelDataTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val assets = File(root, "steel/src/main/assets/steel")
    private val gold = File(root, "tests/steel/golden/data")
    private val data by lazy { SteelData.load { File(assets, it).readText() } }
    private val manifest by lazy { Json.parseToJsonElement(File(gold, "manifest.json").readText()).jsonObject }

    @Test
    fun assetsAreTheV69Export() {
        val sha = manifest.getValue("sha256").jsonObject
        assertEquals(SteelData.FILES.toSet(), sha.keys)
        for (f in SteelData.FILES) {
            val hex = MessageDigest.getInstance("SHA-256").digest(File(assets, f).readBytes()).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
            assertEquals("$f changed since the v69 export", sha.getValue(f).jsonPrimitive.content, hex)
        }
    }

    @Test
    fun countsMatchV69() {
        val c = manifest.getValue("counts").jsonObject.mapValues { it.value.jsonPrimitive.int }
        assertEquals(500, c["profiles"]); assertEquals(data.profiles.cat.size, c["profiles"])
        assertEquals(data.profiles.upn.size, c["upn"]); assertEquals(data.profiles.upe.size, c["upe"])
        assertEquals(7, data.propertySets.size); assertEquals(15, data.classes.size)
        assertEquals(246, data.tk.components.size); assertEquals(17, data.tk.groups.size)     // CLAUDE.md: 246 components in 17 groups
        assertEquals(c["catalogGroups"], data.tk.catalog.size); assertEquals(18, data.tk.familyNames.size)
        assertEquals(data.tk.groups, data.tk.components.map { it.group }.distinct())
    }

    @Test
    fun profileLookupEqualsV69() {
        val cases = Json.parseToJsonElement(File(gold, "profile_cases.json").readText()).jsonObject.getValue("cases").jsonArray
        var resolved = 0; var deferred = 0
        for (c in cases) {
            val name = c.jsonArray[0].jsonPrimitive.content
            val v69 = c.jsonArray[1]
            val ours = data.profiles.lookup(name)
            if (ours == null) { if (v69 != JsonNull) deferred++; continue }   // name rules: S5
            resolved++
            val o = v69 as JsonObject
            fun num(k: String) = o[k]?.jsonPrimitive?.doubleOrNull
            assertEquals(name, o.getValue("key").jsonPrimitive.content, ours.key)
            assertEquals(name, o.getValue("kind").jsonPrimitive.content, ours.kind)
            for ((k, v) in listOf("h" to ours.h, "b" to ours.b, "tw" to ours.tw, "tf" to ours.tf, "r" to ours.r, "t" to ours.t, "d" to ours.d, "m" to ours.m, "P" to ours.P))
                assertEquals("$name.$k", num(k), v)
            assertEquals("$name has fields we do not model", setOf("key", "kind", "h", "b", "tw", "tf", "r", "t", "d", "m", "P").containsAll(o.keys), true)
        }
        // Every catalog row and every channel size resolves.
        for (n in data.profiles.names) assertNotNull(n, data.profiles.lookup(n))
        for (s in data.profiles.upn.keys) assertNotNull(data.profiles.lookup("UPN$s"))
        for (s in data.profiles.upe.keys) assertNotNull(data.profiles.lookup("UPE$s"))
        println("profile cases: $resolved equal to v69 through the tables, $deferred need the name rules (S5)")
        assertTrue(resolved >= 530)
    }

    @Test
    fun catalogSheetItemsResolve() {
        for ((group, items) in data.tk.catalog) for (id in items) {
            if (id.startsWith("@")) { assertTrue(id, id in data.tk.apps); continue }   // applications, not components
            assertTrue("$group: $id", data.tk.byId(id) != null || data.tk.connItem(id) != null)
        }
        for (t in data.tk.components) {
            assertTrue("${t.id} family ${t.family}", t.family in data.tk.familyNames)
            assertTrue("${t.id} pick ${t.pick}", t.pick in data.tk.pickOrders)
        }
        assertEquals("End plate", data.tk.familyNames["EP"])
        assertEquals(listOf("main part", "secondary part"), data.tk.pickOrders["ms"])
        assertNotNull(data.tk.byId("T144")); assertEquals("End plates", data.tk.byId("T144")!!.group)
        // 246 TK + 4 CONN items (tube crossing, tube gusset, sag rod, purlin cleat) + 2 applications = 252 sheet items.
        assertEquals(listOf("Ttgx", "Ttg", "Tsag", "Tcl"), data.tk.connItems.map { it.id })
        assertEquals("Tube crossing (tongues)", data.tk.connItem("Ttgx")!!.label)
        assertEquals(listOf("continuous brace", "half 1", "half 2"), data.tk.connItem("Ttgx")!!.parts)
        assertEquals(252, data.tk.catalog.sumOf { it.second.size })
    }

    @Test
    fun materialsAndPropertySetsLikeV69() {
        val m = data.materials
        assertEquals(listOf("S235", "S275", "S355"), m.grades)
        assertEquals(7850.0, m.density("S275"), 0.0); assertEquals(7850.0, m.density("St-52"), 0.0); assertEquals(7850.0, m.density(null), 0.0)
        assertEquals(0.0, m.density("GROUT"), 0.0); assertEquals(0.0, m.density("C30/37"), 0.0); assertEquals(0.0, m.density("concrete"), 0.0)
        assertEquals(1383.0, m.density("grating"), 0.0)
        assertEquals(33.2, m.teklaKgPerM["UPN240"]!!, 0.0); assertEquals(287.0, m.teklaRoundAreaMm2["ROD20"]!!, 0.0)
        val col = data.propertySet("COLUMN")!!
        assertEquals("HEA300", col.prof); assertEquals("S275", col.mat); assertEquals(2, col.cls)
        assertEquals(listOf("COLUMN", "BEAM", "RAFTER", "PURLIN", "GIRT", "BRACING", "PLATE"), data.propertySets.map { it.name })
        for (s in data.propertySets) assertTrue(s.name, s.name in data.types)
        assertEquals(0xC0392B, data.classColor(2)); assertEquals(0x8E8E93, data.classColor(99))
    }
}
