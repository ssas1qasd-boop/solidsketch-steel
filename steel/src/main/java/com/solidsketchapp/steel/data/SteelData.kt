package com.solidsketchapp.steel.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The steel data tables of web v69 (S4), read from the JSON assets in `assets/steel/` that
 * `tools/golden/data_export.js` writes from the running v69 page. Pure Kotlin: no Android types.
 */
class SteelData(
    val profiles: ProfileCatalog,
    val propertySets: List<PropertySet>,
    /** ST_TYPE: property-set name -> body type (stColumn, stBeam, ...). */
    val types: Map<String, String>,
    /** ST_CLASS: class number -> colour 0xRRGGBB. */
    val classes: Map<Int, Int>,
    val materials: Materials,
    val tk: TkCatalog,
) {
    fun classColor(cls: Int): Int = classes[cls] ?: classes.getValue(0)
    fun propertySet(name: String): PropertySet? = propertySets.firstOrNull { it.name == name }

    companion object {
        val FILES = listOf("profiles.json", "property_sets.json", "classes.json", "materials.json", "tk_catalog.json")

        /** [read] returns the text of one asset file by name (see [FILES]). */
        fun load(read: (String) -> String): SteelData {
            val j = FILES.associateWith { Json.parseToJsonElement(read(it)).jsonObject }
            val prof = j.getValue("profiles.json")
            val sets = j.getValue("property_sets.json")
            val tk = j.getValue("tk_catalog.json")
            return SteelData(
                profiles = ProfileCatalog(
                    cat = prof.getValue("cat").jsonArray.map { e -> e.jsonArray.let { it[0].jsonPrimitive.content to it[1].jsonArray.map(::prim) } },
                    upn = sizeTable(prof.getValue("upn")),
                    upe = sizeTable(prof.getValue("upe")),
                ),
                propertySets = sets.getValue("sets").jsonArray.map { PropertySet(it.jsonObject) },
                types = sets.getValue("types").jsonObject.mapValues { it.value.jsonPrimitive.content },
                classes = j.getValue("classes.json").getValue("classes").jsonArray.associate { e -> e.jsonArray.let { it[0].jsonPrimitive.intOrNull!! to it[1].jsonPrimitive.intOrNull!! } },
                materials = Materials(j.getValue("materials.json")),
                tk = TkCatalog(tk),
            )
        }

        private fun prim(e: JsonElement): Any = (e as JsonPrimitive).let { if (it.isString) it.content else it.doubleOrNull!! }
        private fun sizeTable(e: JsonElement): Map<Int, List<Double>> =
            e.jsonArray.associate { r -> r.jsonArray.let { it[0].jsonPrimitive.intOrNull!! to it[1].jsonArray.map { v -> v.jsonPrimitive.doubleOrNull!! } } }
    }
}

/** A resolved profile, with v69's field names (stProf result). Fields a kind does not use are null. */
data class Profile(
    val key: String, val kind: String,
    val h: Double? = null, val b: Double? = null, val tw: Double? = null, val tf: Double? = null, val r: Double? = null,
    val t: Double? = null, val d: Double? = null, val m: Double, val P: Double,
)

/**
 * ST_CAT plus the v41 channel tables. [lookup] is an exact port of the table branches of v69's
 * live stProf chain: the base catalog lookup (case-insensitive key) and v41's UPN/UPE/U branch.
 * The name rules (PL, FLT, D/ROD, L, parametric SHS/RHS/CHS, C/Z, BI, PHI) are ported in S5;
 * for those names this returns null.
 */
class ProfileCatalog(
    /** In v69's order: name -> raw row (kind letter, then numbers). */
    val cat: List<Pair<String, List<Any>>>,
    val upn: Map<Int, List<Double>>,
    val upe: Map<Int, List<Double>>,
) {
    private val byUpper = cat.associate { it.first.uppercase() to it }

    val names: List<String> get() = cat.map { it.first }

    fun lookup(name: String): Profile? {
        // Base stProf: name trimmed, upper case, X -> x; find the catalog key with the same upper case.
        val n = name.trim().uppercase().replace('X', 'x')
        byUpper[n.uppercase()]?.let { (key, r) -> return fromRow(key, r) }
        // v41 wrapper: upper case, X -> *; UPN / UPE / U + size from the channel tables.
        val s = name.trim().uppercase().replace("X", "*")
        val m = Regex("""^(UPN|UPE|U)\s*(\d+)$""").find(s) ?: return null
        val table = if (m.groupValues[1] == "UPE") upe else upn
        val v = table[m.groupValues[2].toInt()] ?: return null
        val key = (if (m.groupValues[1] == "U") "UPN" else m.groupValues[1]) + m.groupValues[2]
        return Profile(key, "U", h = v[0], b = v[1], tw = v[2], tf = v[3], r = v[4], m = v[5], P = (2 * v[0] + 4 * v[1] - 2 * v[2]) / 1000)
    }

    private fun fromRow(key: String, r: List<Any>): Profile {
        fun n(i: Int) = r[i] as Double
        return when (r[0]) {
            "I" -> Profile(key, "I", h = n(1), b = n(2), tw = n(3), tf = n(4), r = n(5), m = n(6), P = n(7))
            "R" -> Profile(key, "R", h = n(1), b = n(2), t = n(3), r = n(4), m = n(5), P = n(6))
            else -> Profile(key, "O", d = n(1), t = n(2), m = n(3), P = n(4))
        }
    }
}

/** One ST_SETS entry (raw JSON kept: every field v69 has, in order). */
class PropertySet(val raw: JsonObject) {
    val name: String get() = str("name")
    val prof: String get() = str("prof")
    val mat: String get() = str("mat")
    val cls: Int get() = raw.getValue("cls").jsonPrimitive.intOrNull!!
    fun str(k: String): String = raw.getValue(k).jsonPrimitive.content
}

/** Grades and density, as v69 uses them (stPropSheet list; v66 tkDensity). */
class Materials(raw: JsonObject) {
    val grades: List<String> = raw.getValue("grades").jsonArray.map { it.jsonPrimitive.content }
    private val dens = raw.getValue("density").jsonObject
    val defaultDensity: Double = dens.getValue("defaultKgM3").jsonPrimitive.doubleOrNull!!
    val overrides: Map<String, Double> = dens.getValue("overrides").jsonObject.mapValues { it.value.jsonPrimitive.doubleOrNull!! }
    private val zero = Regex(dens.getValue("zeroIfMatches").jsonPrimitive.content, RegexOption.IGNORE_CASE)
    val teklaKgPerM: Map<String, Double> = raw.getValue("teklaKgPerM").jsonObject.mapValues { it.value.jsonPrimitive.doubleOrNull!! }
    val teklaRoundAreaMm2: Map<String, Double> = raw.getValue("teklaRoundAreaMm2").jsonObject.mapValues { it.value.jsonPrimitive.doubleOrNull!! }

    /** kg/m³ for a material name: v66 tkDensity (overrides, then 0 for concrete/grout, else 7850). */
    fun density(mat: String?): Double {
        val m = (mat ?: "").uppercase().trim()
        overrides[m]?.let { return it }
        return if (zero.containsMatchIn(m)) 0.0 else defaultDensity
    }
}

/** One Tekla catalog component (TK entry). */
class TkComponent(val n: String, val name: String, val group: String, val family: String, val pick: String, val options: JsonObject) {
    /** The catalog sheet id, as in ST_CATALOG ("T" + number). */
    val id: String get() = "T$n"
}

/** TK (246 components in 17 groups), the catalog sheet groups, family names, pick orders and icons. */
class TkCatalog(raw: JsonObject) {
    val components: List<TkComponent> = raw.getValue("components").jsonArray.map { e ->
        val o = e.jsonObject
        fun s(k: String) = o.getValue(k).jsonPrimitive.content
        TkComponent(s("n"), s("name"), s("grp"), s("fam"), s("pk"), o["o"] as? JsonObject ?: JsonObject(emptyMap()))
    }
    val groups: List<String> = raw.getValue("groups").jsonArray.map { it.jsonPrimitive.content }
    /** The catalog sheet: group name -> item ids (T-numbers and application ids such as "@portal"). */
    val catalog: List<Pair<String, List<String>>> = raw.getValue("catalog").jsonArray.map { e ->
        e.jsonObject.let { it.getValue("g").jsonPrimitive.content to it.getValue("items").jsonArray.map { i -> i.jsonPrimitive.content } }
    }
    val familyNames: Map<String, String> = raw.getValue("famNames").jsonObject.mapValues { it.value.jsonPrimitive.content }
    /** Pick order prompts per pick code (TK_PICK). */
    val pickOrders: Map<String, List<String>> = raw.getValue("pick").jsonObject.mapValues { it.value.jsonArray.map { p -> p.jsonPrimitive.content } }
    val icons: Map<String, String> = raw.getValue("icons").jsonObject.mapValues { it.value.jsonPrimitive.content }

    /** Catalog items that are CONN components, not TK entries (v69 adds them to the sheet itself). */
    val connItems: List<ConnItem> = raw.getValue("connItems").jsonArray.map { ConnItem(it.jsonObject) }
    /** Application ids on the sheet ("@numbering", ...). */
    val apps: List<String> = raw.getValue("apps").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

    private val byId = components.associateBy { it.id }
    private val connById = connItems.associateBy { it.id }
    fun byId(id: String): TkComponent? = byId[id]
    fun connItem(id: String): ConnItem? = connById[id]
    fun inGroup(group: String): List<TkComponent> = components.filter { it.group == group }

    /** Simple case-insensitive filter by name or number. S7 ports the web catalog sheet's search exactly. */
    fun search(q: String): List<TkComponent> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return components
        return components.filter { it.name.lowercase().contains(s) || it.n.lowercase() == s || it.id.lowercase() == s }
    }
}

/** A CONN component listed on the catalog sheet (raw data fields of v69's CONN entry). */
class ConnItem(val raw: JsonObject) {
    val id: String get() = raw.getValue("id").jsonPrimitive.content
    val label: String get() = raw.getValue("label").jsonPrimitive.content
    val group: String? get() = raw["grp"]?.jsonPrimitive?.content
    val parts: List<String> get() = raw["parts"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}
