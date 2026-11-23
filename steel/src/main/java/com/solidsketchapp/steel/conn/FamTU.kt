package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * TU family: tube connections (saddles Tts / Tth / Ttm, slotted hole Ttsl, Hql 46, plate with nail 47n / 47p). Live chain:
 * v69 FAM.TU (L15149, FAM_TU15) wrapped by L15465 (`hole`: the secondary tube's inside cut out of the main part).
 */
object FamTU {
    const val PORTED = true
    private val DEF_GU = Fam.obj("kind" to "bolted", "t" to 12, "setback" to 25, "lap" to 0, "nb" to 2, "pb" to 70, "e" to 40, "bolt" to 20, "grade" to "8.8", "clear" to 15, "slot" to 3, "side" to 1, "clip" to 0, "a" to 6)
    /** v69 FAM_DEF.EP (L14858, without the v58 additions) with TU's end plate settings (L15154). */
    private fun defEP(front: Int) = Fam.obj("tp" to 15, "ext" to 25, "extb" to 25, "side" to 25, "gauge" to 60, "rows" to 2, "pitch" to 0, "e" to 35, "bolt" to 20, "grade" to "8.8", "a" to 6, "gap" to 2, "shim" to 0, "front" to front, "seat" to 0, "stiff" to 0, "comp" to 0, "two" to 0, "shared" to 0)
    /** The bolt options stBolts reads from TKB_Q (the component's own settings, not the family's q). */
    private val BOLT_KEYS = listOf("ba_b", "ba_n", "ba_w", "bdel", "bdir", "blen", "htype", "sl1", "sl2", "srot", "stag", "tol")

    /** `FAM.XX(Object.assign({}, X, { q }))`: the family runs with [q]; stBolts still sees the component's settings; outputs go to [x]. */
    private fun sub(x: FamCtx, q: JsonObject, run: (FamCtx) -> Unit): FamCtx {
        val qq = LinkedHashMap<String, JsonElement>(q); for (k in BOLT_KEYS) x.q[k]?.let { qq[k] = it }
        val y = FamCtx(JsonObject(qq), x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt, x.conn, x.conns, x.stOf); run(y)
        x.cuts.let { for ((id, l) in y.cuts) it.getOrPut(id) { ArrayList() } += l }; x.groups += y.groups; x.welds += y.welds
        return y
    }

    fun gen(x: FamCtx) {
        tu15(x)
        if (x.t("hole") && x.b != null) {   // L15465
            val t = x.profiles.lookup(x.b.str("prof") ?: "")?.t?.takeIf { it != 0.0 && !it.isNaN() } ?: 4.0
            try { FamWF.envelope(x, x.b, -t)?.let { x.cut(x.aId, it) } } catch (e: RuntimeException) { /* no hole */ }
        }
    }

    /** v69 FAM.TU (L15149). */
    private fun tu15(x: FamCtx) {
        val b = x.b ?: throw IllegalStateException("not built"); val k = x.s("kind")
        when (k) {
            "saddle", "chamfer" -> { val y = sub(x, FamWF.with(FamWF.DEF, "kind" to "saddle")) { FamWF.gen(it) }; x.parts += y.parts }
            "slot" -> { val mb = Joints.mf(b, x.profiles); val l = mb.c2.distanceTo(mb.c1); val g = x.n("gap")
                x.cut(x.aId, Joints.box3(mb.c1.lerp(mb.c2, 0.5), mb.a, mb.eh, -l / 2 - g, l / 2 + g, -mb.h / 2 - g, mb.h / 2 + g, -mb.b / 2 - g, mb.b / 2 + g)) }
            "squeeze" -> { val q = LinkedHashMap<String, JsonElement>(DEF_GU).apply { put("kind", JsonPrimitive("tube")); put("t", x.q["t"] ?: JsonNull); put("lap", x.q["lap"] ?: JsonNull) }
                val y = sub(x, JsonObject(q)) { FamGU.gen(it) }; x.parts += y.parts }
            else -> {
                val y = sub(x, defEP(if (k == "nail") 1 else 0)) { Fam.ep(it) }
                // famGeom's bolts(): d || q.bolt and grade || q.grade come from the component's q, not the family's
                for (p in y.parts) x.parts += if (p.bolts == null) p else ConnPart(p.name, p.pieces, JsonObject(LinkedHashMap(p.bolts).apply {
                    put("d", jsNum(x.n("bolt"))); put("grade", JsonPrimitive(x.s("grade"))) }), p.prof, p.minus, p.group)
            }
        }
    }
}
