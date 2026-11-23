package com.solidsketchapp.steel.model

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A document in memory (ids 1, 2, 3…), enough for :steel tests. */
class FakeDoc : DocumentApi {
    val bodies = LinkedHashMap<BodyId, Pair<String, Mesh?>>(); val fields = LinkedHashMap<String, String>(); var next = 1
    override fun bodyIds() = bodies.keys.toList()
    override fun bodyJson(id: BodyId) = bodies[id]?.first
    override fun bodyMesh(id: BodyId) = bodies[id]?.second
    override fun addBody(json: String, mesh: Mesh?) = BodyId((next++).toString()).also { bodies[it] = json to mesh }
    override fun replaceBody(id: BodyId, json: String, mesh: Mesh?) { bodies[id] = json to mesh }
    override fun removeBody(id: BodyId) { bodies.remove(id) }
    override fun field(name: String) = fields[name]
    override fun setField(name: String, json: String?) { if (json == null) fields.remove(name) else fields[name] = json }
    override fun addListener(listener: DocumentApi.Listener) {}
    override fun removeListener(listener: DocumentApi.Listener) {}
}

class FakeUndo : UndoApi {
    val steps = mutableListOf<String>()
    override fun <T> group(label: String, block: () -> T): T { steps += label; return block() }
    override fun canUndo() = steps.isNotEmpty(); override fun canRedo() = false; override fun undo() {}; override fun redo() {}
}

class StPartsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }
    private fun st(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun addMakesV69Records() {
        val doc = FakeDoc(); val undo = FakeUndo(); val parts = StParts(data, doc, undo, StBuild(data.profiles))
        val id = parts.add(st("""{"kind":"member","name":"COLUMN","prof":"HEA300","mat":"S275","cls":2,"p1":[0,0,0],"p2":[0,0,7500]}"""), "Column HEA300")
        val b = Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject
        assertEquals("COLUMN 1", b["name"]!!.jsonPrimitive.content)
        assertEquals(0xC0392B, b["color"]!!.jsonPrimitive.content.toInt())        // ST_CLASS[2]
        assertEquals("stColumn", b["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("name", "color", "hidden", "type", "st"), b.keys.toList())
        assertTrue(doc.bodyJson(id)!!.contains("\"p2\":[0,0,7500]"))            // JavaScript number text
        assertEquals(listOf("Column HEA300"), undo.steps)
        assertEquals("Steel column", Json.parseToJsonElement(doc.fields["folders"]!!).jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(84833926.67, Prism.signedVolume(doc.bodyMesh(id)!!), 84833926.67 * 1e-4)
    }

    @Test
    fun typesAndColoursLikeV69() {
        val parts = StParts(data, FakeDoc(), null, StBuild(data.profiles))
        fun t(name: String, kind: String = "member") = parts.typeOf(st("""{"kind":"$kind","name":"$name"}"""))
        assertEquals("stColumn", t("COLUMN")); assertEquals("stBeam", t("RAFTER")); assertEquals("stPurlin", t("PURLIN"))
        assertEquals("stBrace", t("WIND BRACING")); assertEquals("stGirt", t("SIDE GIRT")); assertEquals("stColumn", t("GABLE COL"))
        assertEquals("stPlate", t("BASE", "plate")); assertEquals("stBeam", t("HAND RAIL"))
        assertEquals(0x8E8E93, parts.colorOf(st("""{"cls":99}"""))); assertEquals(0x2E8B57, parts.colorOf(st("""{"cls":"3"}""")))
    }

    @Test
    fun modifyRebuildsAndKeepsOtherFields() {
        val doc = FakeDoc(); val parts = StParts(data, doc, FakeUndo(), StBuild(data.profiles))
        val id = parts.add(st("""{"kind":"member","name":"BEAM","prof":"IPE300","cls":3,"p1":[0,0,7500],"p2":[13195,0,7500],"pos":"B1"}"""), "Beam")
        val v0 = Prism.signedVolume(doc.bodyMesh(id)!!)
        parts.modify(listOf(id), mapOf("prof" to JsonPrimitive("IPE360"), "cls" to jsNum(4.0)))
        val b = Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject
        assertEquals("IPE360", b["st"]!!.jsonObject["prof"]!!.jsonPrimitive.content)
        assertEquals("B1", b["st"]!!.jsonObject["pos"]!!.jsonPrimitive.content)       // unknown fields kept
        assertEquals(0x2F7A4D.let { data.classColor(4) }, b["color"]!!.jsonPrimitive.content.toInt())
        assertTrue(Prism.signedVolume(doc.bodyMesh(id)!!) > v0)
        val pl = parts.add(st("""{"kind":"plate","name":"PLATE","prof":"PL20","pts":[[0,0],[100,0],[100,100]],"z":0,"p1":[1,2,3]}"""), "Plate")
        parts.modify(listOf(pl), mapOf("prof" to JsonPrimitive("PL10")))
        assertFalse((Json.parseToJsonElement(doc.bodyJson(pl)!!).jsonObject["st"] as JsonObject).containsKey("p1"))
    }
}
