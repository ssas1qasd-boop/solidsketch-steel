package com.solidsketchapp.steel.conn

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * JO family: joists (58j, 160-164). Live definition: v69 FAM.JO (L15166): `cap` → FAM.CP with FAM_DEF.CP and the joist's
 * bolt; otherwise FAM.SE with FAM_DEF.SE, a seat angle (the joist's L, t, bolt) and a top angle.
 */
object FamJO {
    const val PORTED = true
    private val DEF_CP = Fam.obj("tp" to 20, "ext" to 25, "gx" to 0, "gy" to 0, "bolt" to 20, "grade" to "8.8", "stiff" to 0, "ts" to 10, "cross" to 0, "a" to 6)
    private val DEF_SE = Fam.obj("seat" to "angle", "L" to 100, "t" to 12, "w" to 160, "gap" to 10, "bolt" to 20, "grade" to "8.8", "top" to 0, "ts" to 12, "a" to 6)

    private fun with(base: JsonObject, vararg kv: Pair<String, JsonElement?>) = JsonObject(LinkedHashMap(base).apply { for ((k, v) in kv) put(k, v ?: JsonNull) })

    fun gen(x: FamCtx) {
        if (x.t("cap")) { FamCP.gen(x, with(DEF_CP, "bolt" to x.q["bolt"])); return }
        FamSE.gen(x, with(DEF_SE, "seat" to JsonPrimitive("angle"), "L" to x.q["L"], "t" to x.q["t"], "bolt" to x.q["bolt"], "top" to JsonPrimitive(1)))
    }
}
