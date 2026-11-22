package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.Mesh

/** Pure-Kotlin box mesh for tests (outward-facing, counter-clockwise). */
fun boxMesh(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Mesh = Mesh(
    floatArrayOf(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1),
    intArrayOf(0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7),
)
