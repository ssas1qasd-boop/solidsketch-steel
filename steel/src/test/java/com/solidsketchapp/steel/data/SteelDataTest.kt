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
        var equal = 0; var nulls = 0
        val wrong = mutableListOf<String>()
        for (c in cases) {
            val name = c.jsonArray[0].jsonPrimitive.content
            val v69 = c.jsonArray[1]
            val ours = data.profiles.lookup(name)
            if (v69 == JsonNull) { if (ours == null) nulls++ else wrong += "$name: v69 null, ours ${ours.key}"; continue }
            if (ours == null) { wrong += "$name: v69 ${v69.jsonObject["key"]}, ours null"; continue }
            val o = v69 as JsonObject
            val v69Fields = o.filterKeys { it != "key" && it != "kind" }.mapValues { it.value.jsonPrimitive.doubleOrNull }
            val diff = buildList {
                if (o.getValue("key").jsonPrimitive.content != ours.key) add("key ${o["key"]} vs ${ours.key}")
                if (o.getValue("kind").jsonPrimitive.content != ours.kind) add("kind ${o["kind"]} vs ${ours.kind}")
                if (v69Fields != ours.fields()) add("fields $v69Fields vs ${ours.fields()}")   // exact doubles, same field set
            }
            if (diff.isEmpty()) equal++ else wrong += "$name: $diff"
        }
        // Every catalog row and every channel size resolves.
        for (n in data.profiles.names) assertNotNull(n, data.profiles.lookup(n))
        for (s in data.profiles.upn.keys) assertNotNull(data.profiles.lookup("UPN$s"))
        for (s in data.profiles.upe.keys) assertNotNull(data.profiles.lookup("UPE$s"))
        println("profile cases: $equal equal to v69, $nulls null in both, ${wrong.size} differ")
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
        assertEquals(629, equal); assertEquals(cases.size, equal + nulls)
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
        // 246 TK + 7 CONN items (tube crossing, tube gusset, sag rod, purlin cleat; v70 C02h eave haunch Teh, C18 stair end plate Tsep and stair treads, X02 column cap plate Tcolcap) + 2 applications = 256 sheet items.
        assertEquals(listOf("Teh", "Ttgx", "Ttg", "Tsag", "Tcl", "Tsep", "stairtreads", "Tcolcap"), data.tk.connItems.map { it.id })
        assertEquals("Tube crossing (tongues)", data.tk.connItem("Ttgx")!!.label)
        assertEquals(listOf("continuous brace", "half 1", "half 2"), data.tk.connItem("Ttgx")!!.parts)
        assertEquals(256, data.tk.catalog.sumOf { it.second.size })
    }

    @Test
    fun materialsAndPropertySetsLikeV69() {
        val m = data.materials
        assertEquals(listOf("S235", "S275", "S355"), m.grades)
        assertEquals(7850.0, m.density("S275"), 0.0); assertEquals(7850.0, m.density("St-52"), 0.0); assertEquals(7850.0, m.density(null), 0.0)
        assertEquals(0.0, m.density("GROUT"), 0.0); assertEquals(0.0, m.density("C30/37"), 0.0); assertEquals(0.0, m.density("concrete"), 0.0)
        assertEquals(1333.0, m.density("grating"), 0.0)   // v70: Tekla's grating density (R12)
        assertEquals(33.2055, m.teklaKgPerM["UPN240"]!!, 0.0); assertEquals(287.0, m.teklaRoundAreaMm2["ROD20"]!!, 0.0)
        val col = data.propertySet("COLUMN")!!
        assertEquals("HEA300", col.prof); assertEquals("S275", col.mat); assertEquals(2, col.cls)
        assertEquals(listOf("COLUMN", "BEAM", "RAFTER", "PURLIN", "GIRT", "BRACING", "PLATE"), data.propertySets.map { it.name })
        for (s in data.propertySets) assertTrue(s.name, s.name in data.types)
        assertEquals(0xC0392B, data.classColor(2)); assertEquals(0x8E8E93, data.classColor(99))
    }
}
