package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.MeshOps
import com.solidsketchapp.core.view.NativeCore
import com.solidsketchapp.core.view.math.Quat

/**
 * S2 check scene: an HEA300 column, an IPE360 beam framing into its flange, and a 20 mm end
 * plate with 8 M20 holes (d0 22) cut by Manifold. Colours are v69 ST_CLASS values
 * (column 2, beam 3, plate 5). Not Steel logic: Steel builds real parts from S5 on.
 */
object CoreDemo {
    const val COLUMN = 0xC0392B
    const val BEAM = 0x2E8B57
    const val PLATE = 0x16A2B8

    /** I-section outline (no root radii), centred: x across the flange, y along the depth. */
    fun iOutline(h: Float, b: Float, tw: Float, tf: Float): FloatArray {
        val x = b / 2; val y = h / 2; val w = tw / 2; val f = y - tf
        return floatArrayOf(-x, -y, x, -y, x, -f, w, -f, w, f, x, f, x, y, -x, y, -x, f, -w, f, -w, -f, -x, -f)
    }

    // Local Z (extrusion) -> world X, local Y (depth) -> world Z, local X -> world Y.
    private val alongX = Quat.fromAxes(Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0))
    // Local Z -> world X for cylinders (right-handed: x -> -Z, y -> Y, z -> X).
    private val cylX = Quat.fromAxes(Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0))

    class Parts(val column: Mesh, val beam: Mesh, val plate: Mesh)

    fun build(engine: BooleanEngine): Parts {
        // Column HEA300 (h 290 along world X, b 300 along Y), 3000 high.
        val colOutline = iOutline(290f, 300f, 8.5f, 14f).let { o -> FloatArray(o.size) { i -> if (i % 2 == 0) o[i + 1] else o[i - 1] } }
        val column = NativeCore.extrude(colOutline, 3000f)
        // End plate 20 x 200 x 400 on the column flange face (x = 145), top at z = 3000.
        val plate0 = MeshOps.translate(NativeCore.box(20f, 200f, 400f), Vec3(145.0, -100.0, 2600.0))
        val holes = listOf(-45.0, 45.0).flatMap { y -> listOf(2650.0, 2730.0, 2870.0, 2950.0).map { z -> y to z } }.map { (y, z) ->
            MeshOps.transform(NativeCore.cylinder(11f, 60f, 32), cylX, Vec3(125.0, y, z))
        }
        val plate = engine.subtract(plate0, holes)
        // Beam IPE360 from the plate face, 4000 long, top flush with the column top.
        val beam = MeshOps.transform(NativeCore.extrude(iOutline(360f, 170f, 8f, 12.7f), 4000f), alongX, Vec3(165.0, 0.0, 2820.0))
        return Parts(column, beam, plate)
    }

    fun json(name: String, color: Int) = """{"name":"$name","color":$color,"hidden":false}"""

    fun addTo(doc: MemoryDocument, p: Parts) {
        doc.addBody(json("COLUMN", COLUMN), p.column)
        doc.addBody(json("BEAM", BEAM), p.beam)
        doc.addBody(json("END PLATE", PLATE), p.plate)
    }

    /** Grid lines at z = 0 (v69 major grid colour 0xa3abb8). */
    fun grid(): FloatArray {
        val l = ArrayList<Float>()
        for (i in -2..6) { val x = i * 1000f; l += listOf(x, -2000f, 0f, x, 2000f, 0f) }
        for (j in -2..2) { val y = j * 1000f; l += listOf(-2000f, y, 0f, 6000f, y, 0f) }
        return l.toFloatArray()
    }
}
