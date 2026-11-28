package com.solidsketchapp.steel

import com.solidsketchapp.core.api.Vec3
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * T142 · Two sided end plate (142), the Group 1 two-video test (docs/steel/PARITY.md "T142 · Two sided end plate (142): the two-video test"):
 * the shared steps of T142V1RecordingTest / T142V2RecordingTest on top of [T144RecordingBase] (members, the real dialog, orbit, orthographic
 * stills, save, one option + Modify), plus Pick parts for a three-part component (main part, secondary 1, secondary 2): the pattern for
 * the other two-sided End plates (T115, T24, T34, T112).
 */
abstract class T142RecordingBase : T144RecordingBase() {
    /** Pick parts: the three parts at the given points (another triple if a tap missed), until a component of [type] is added. */
    protected fun pickParts3(type: String, cam: () -> Unit, triples: List<Triple<Vec3, Vec3, Vec3>>) {
        press("Pick parts"); pause(800); cam(); pause(1200)
        val n0 = mode().comps!!.conns().count { it.type == type }
        for ((a, b, c) in triples) { if (mode().comps!!.conns().count { it.type == type } > n0) break
            act.runOnUiThread { mode().catPick(mode().catK ?: type) }; pause(400); shot("pick_${type}")
            tapAt(screen(a)); pause(700); tapAt(screen(b)); pause(700); tapAt(screen(c)); pause(1500) }
        waitFor("the $type") { mode().comps!!.conns().count { it.type == type } == n0 + 1 }
        act.runOnUiThread { mode().catDone() }; pause(800)
    }
    /** How many bodies carry this part name. */
    protected fun count(name: String) = act.testDoc.bodyIds().count { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.content == name }
    /** The last bolt group's st (n, L, d, pts). */
    protected fun boltSt(): JsonObject = act.testDoc.bodyIds().last { (mode().stOf(it)?.get("kind") as? JsonPrimitive)?.content == "bolts" }.let { mode().stOf(it)!! }
    /** Orbit front / end / top / 3D and the three orthographic stills around the joint [c]. */
    protected fun views(c: Vec3, tag: String, d: Double) {
        orbit(c, d, tag)
        landscape(true)
        orthoShot(c, Vec3(0.0, -1.0, 0.0), d * 0.8, "ortho_front_$tag")   // along the frame line: both end plates, the main part between, the bolts across
        orthoShot(c, Vec3(1.0, 0.0, 0.0), d * 0.6, "ortho_end_$tag")      // onto end plate 1 from the beam side: b / h, the bolt group
        orthoShot(c, Vec3(0.0, 0.0, 1.0), d * 0.8, "ortho_top_$tag")
        landscape(false)
        look(c, Vec3(0.9, -1.2, 0.7), d); pause(600)
    }
}
