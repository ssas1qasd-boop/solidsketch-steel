package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** BE family · bent plates (v69 FAM.BE, v40 L14975, the live definition): FAM.CA with double off and leg || 90, the angle renamed BENT PLATE ('PL' + q.t). */
object FamBE {
    const val PORTED = true

    fun gen(x: FamCtx) {
        val q = LinkedHashMap<String, JsonElement>(x.q); q["double"] = jsNum(0.0); if (!x.t("leg")) q["leg"] = jsNum(90.0)
        val y = FamCtx(JsonObject(q), x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt)
        FamCA.gen(y)
        for (p in y.parts) x.parts += if (p.name == "CLIP ANGLE") ConnPart("BENT PLATE", p.pieces, p.bolts, "PL" + FamCA.jsStr(x.q["t"]), p.minus, p.group) else p
        for ((id, ms) in y.cuts) x.cuts.getOrPut(id) { ArrayList() } += ms
        x.welds += y.welds; x.groups += y.groups
    }
}
