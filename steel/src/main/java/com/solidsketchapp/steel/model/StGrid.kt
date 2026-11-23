package com.solidsketchapp.steel.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The steel grid and levels in Tekla syntax (web v69 `stParse`, `stLetters`, `stGridData`, `stGridDraw`; v32 L13650):
 * the first value is where the grid starts, then the spacings; `n*d` repeats a spacing n times (as the first
 * token it places n lines d apart, starting at d). X lines are numbered 1, 2, 3…, Y lines lettered A, B, C…
 * Stored in the document as `stGrid` = {x, y, z, lx?, ly?} (strings, millimetres).
 */
data class StGrid(val x: String, val y: String, val z: String, val lx: String = "", val ly: String = "") {
    val xs: List<Double> get() = parse(x)
    val ys: List<Double> get() = parse(y)
    val zs: List<Double> get() = parse(z)

    fun toJson(): JsonObject = buildJsonObject {
        put("x", JsonPrimitive(x)); put("y", JsonPrimitive(y)); put("z", JsonPrimitive(z))
        if (lx.isNotEmpty()) put("lx", JsonPrimitive(lx)); if (ly.isNotEmpty()) put("ly", JsonPrimitive(ly))
    }

    /** Line labels: `lx` / `ly` when given, else 1, 2, 3… and A, B, C… (v69 stGridDraw). */
    fun xLabel(i: Int): String = words(lx).getOrNull(i) ?: (i + 1).toString()
    fun yLabel(i: Int): String = words(ly).getOrNull(i) ?: letters(i)

    /** Work-level labels: "+7500", "-300" (v69: sign, then Math.round). */
    fun levelLabel(i: Int): String = zs[i].let { (if (it >= 0) "+" else "") + jsRound(it).toLong() }

    companion object {
        /** v69 default when Steel mode starts on a document without a grid. */
        val DEFAULT = StGrid("0 6*6000", "0 20000", "0 7000")

        fun fromJson(o: JsonObject?): StGrid? {
            if (o == null) return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""
            return StGrid(s("x"), s("y"), s("z"), s("lx"), s("ly"))
        }

        private val rep = Regex("""^(\d+)\*(-?\d+(?:\.\d+)?)$""")

        /** v69 stParse, exactly: tokens split on whitespace; numbers as JavaScript reads them; non-finite results dropped. */
        fun parse(txt: String): List<Double> {
            val out = mutableListOf<Double>(); var x: Double? = null
            for (tok in jsTrim(txt).split(Regex("""\s+"""))) {
                if (tok.isEmpty()) continue
                val m = rep.find(tok)
                if (x == null) {
                    x = jsNumber(if (m != null) m.groupValues[2] else tok); out += x
                    if (m != null) for (i in 1 until m.groupValues[1].toInt()) { x += m.groupValues[2].toDouble(); out += x }
                    continue
                }
                if (m != null) for (i in 0 until m.groupValues[1].toInt()) { x = x!! + m.groupValues[2].toDouble(); out += x!! }
                else { x = x!! + jsNumber(tok); out += x!! }
            }
            return out.filter { it.isFinite() }
        }

        /** v69 stLetters: 0 -> A, 25 -> Z, 26 -> AA, 701 -> ZZ, 702 -> AAA. */
        fun letters(i: Int): String {
            var n = i + 1; val sb = StringBuilder()
            while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
            return sb.toString()
        }

        private fun words(s: String) = jsTrim(s).split(Regex("""\s+""")).filter { it.isNotEmpty() }

        /** JavaScript Math.round (halves go up). */
        fun jsRound(v: Double): Double = Math.floor(v + 0.5)

        private fun jsTrim(s: String) = s.trim { it.isWhitespace() || it == '﻿' }

        private val decimal = Regex("""^[+-]?(?:Infinity|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)$""")

        /** JavaScript ToNumber for a string token (unary +): decimal / exponent / Infinity / 0x 0o 0b; otherwise NaN. */
        fun jsNumber(tok: String): Double {
            val s = jsTrim(tok)
            if (s.isEmpty()) return 0.0
            if (decimal.matches(s)) return if (s.endsWith("Infinity")) (if (s.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY) else s.toDouble()
            val radix = when { s.startsWith("0x", true) -> 16; s.startsWith("0o", true) -> 8; s.startsWith("0b", true) -> 2; else -> 0 }
            if (radix != 0) return s.substring(2).toBigIntegerOrNull(radix)?.toDouble() ?: Double.NaN
            return Double.NaN
        }
    }
}

/** The grid drawn as line overlays (v69 stGridDraw): all levels faint, the work level strong; 1500 mm beyond the outer lines. */
object StGridLines {
    /** v69 colours: all levels 0x4a5a70 at opacity 0.55 (dashed in v69, solid here); the work level 0x2f6fb3 at 0.85. */
    const val ALL_ARGB = 0x8C4A5A70.toInt()
    const val WORK_ARGB = 0xD92F6FB3.toInt()
    private const val EXT = 1500.0

    data class Lines(val all: FloatArray, val work: FloatArray)

    fun build(g: StGrid, level: Int): Lines? {
        val xs = g.xs; val ys = g.ys
        if (xs.isEmpty() || ys.isEmpty()) return null
        val zs = g.zs.ifEmpty { listOf(0.0) }
        val x0 = xs.min() - EXT; val x1 = xs.max() + EXT; val y0 = ys.min() - EXT; val y1 = ys.max() + EXT
        fun levelLines(z: Double, out: MutableList<Float>) {
            for (x in xs) out.addAll(listOf(x, y0, z, x, y1, z).map { it.toFloat() })
            for (y in ys) out.addAll(listOf(x0, y, z, x1, y, z).map { it.toFloat() })
        }
        val all = mutableListOf<Float>(); for (z in zs) levelLines(z, all)
        val work = mutableListOf<Float>(); levelLines(zs[minOf(level, zs.size - 1)], work)
        return Lines(all.toFloatArray(), work.toFloatArray())
    }
}
