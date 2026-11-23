package com.solidsketchapp.steel.comp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The small SVG subset v69's catalog icons, dialog pictures and pictograms use (rect, line, circle, ellipse, polygon,
 * polyline, path with M/L/H/V/Q/A/Z, text, g with inherited fill / stroke, rotate), drawn on a Compose canvas with
 * `preserveAspectRatio="xMidYMid meet"`. Parsed once into [Svg] (tested on every asset).
 */
class Svg(val vbX: Float, val vbY: Float, val vbW: Float, val vbH: Float, val items: List<Item>) {
    class Style(val fill: Color?, val stroke: Color?, val sw: Float, val dash: FloatArray?, val round: Boolean, val opacity: Float, val fillOpacity: Float)
    sealed class Item { abstract val st: Style; abstract val rot: Triple<Float, Float, Float>? }
    class Shape(val ops: Ops, override val st: Style, override val rot: Triple<Float, Float, Float>?) : Item() { val path: Path by lazy { ops.toPath() } }
    /** Path commands without Android types (so parsing runs in JVM tests): 0 move, 1 line, 2 quad, 3 cubic, 4 close, 5 rect, 6 round rect, 7 oval. */
    class Ops {
        val cmd = ArrayList<Int>(); val v = ArrayList<Float>()
        fun add(c: Int, vararg f: Float) { cmd += c; for (x in f) v += x }
        fun moveTo(x: Float, y: Float) = add(0, x, y); fun lineTo(x: Float, y: Float) = add(1, x, y); fun close() = add(4)
        fun quadraticTo(a: Float, b: Float, c: Float, d: Float) = add(2, a, b, c, d); fun cubicTo(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) = add(3, a, b, c, d, e, f)
        fun toPath(): Path { val p = Path(); var i = 0
            for (c in cmd) when (c) {
                0 -> { p.moveTo(v[i], v[i + 1]); i += 2 }; 1 -> { p.lineTo(v[i], v[i + 1]); i += 2 }
                2 -> { p.quadraticTo(v[i], v[i + 1], v[i + 2], v[i + 3]); i += 4 }; 3 -> { p.cubicTo(v[i], v[i + 1], v[i + 2], v[i + 3], v[i + 4], v[i + 5]); i += 6 }
                4 -> p.close()
                5 -> { p.addRect(androidx.compose.ui.geometry.Rect(v[i], v[i + 1], v[i + 2], v[i + 3])); i += 4 }
                6 -> { p.addRoundRect(androidx.compose.ui.geometry.RoundRect(v[i], v[i + 1], v[i + 2], v[i + 3], androidx.compose.ui.geometry.CornerRadius(v[i + 4], v[i + 5]))); i += 6 }
                7 -> { p.addOval(androidx.compose.ui.geometry.Rect(v[i], v[i + 1], v[i + 2], v[i + 3])); i += 4 }
            }
            return p }
    }
    class Text(val x: Float, val y: Float, val text: String, val size: Float, val anchor: String, val bold: Boolean, override val st: Style, override val rot: Triple<Float, Float, Float>?) : Item()

    fun draw(scope: DrawScope, tm: TextMeasurer, origin: Offset = Offset.Zero, size: Size = scope.size) = with(scope) {
        val k = minOf(size.width / vbW, size.height / vbH); val ox = origin.x + (size.width - vbW * k) / 2 - vbX * k; val oy = origin.y + (size.height - vbH * k) / 2 - vbY * k
        translate(ox, oy) { scale(k, k, Offset.Zero) {
            for (it in items) {
                val body: DrawScope.() -> Unit = {
                    when (it) {
                        is Shape -> {
                            it.st.fill?.let { c -> drawPath(it.path, c.copy(alpha = c.alpha * it.st.opacity * it.st.fillOpacity)) }
                            it.st.stroke?.let { c -> drawPath(it.path, c.copy(alpha = c.alpha * it.st.opacity), style = Stroke(it.st.sw, cap = if (it.st.round) StrokeCap.Round else StrokeCap.Butt, pathEffect = it.st.dash?.let { d -> PathEffect.dashPathEffect(d) })) }
                        }
                        is Text -> {
                            // font-size is in viewBox units: measure at that size in px, then the canvas scale applies
                            val lay = tm.measure(it.text, TextStyle(fontSize = (it.size / density / fontScale).sp, fontWeight = if (it.bold) FontWeight.Bold else FontWeight.Normal, color = it.st.fill ?: Color.Black))
                            val w = lay.size.width.toFloat(); val x = when (it.anchor) { "middle" -> it.x - w / 2; "end" -> it.x - w; else -> it.x }
                            drawText(lay, topLeft = Offset(x, it.y - lay.firstBaseline))
                        }
                    }
                }
                val r = it.rot
                if (r != null) rotate(r.first, Offset(r.second, r.third), body) else body()
            }
        } }
    }

    companion object {
        private val TAG = Regex("""<(/?)(\w+)([^>]*?)(/?)>|([^<]+)""")
        private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
        private val NAMED = mapOf("white" to 0xFFFFFFFF, "black" to 0xFF000000, "none" to 0L, "red" to 0xFFFF0000)
        fun color(s: String?): Color? {
            if (s == null || s == "none" || s.isEmpty()) return null
            NAMED[s]?.let { return Color(it.toInt()) }
            if (s.startsWith("#")) { var h = s.substring(1); if (h.length == 3) h = h.map { "$it$it" }.joinToString(""); return h.toLongOrNull(16)?.let { Color((0xFF000000 or it).toInt()) } }
            Regex("""rgba?\(([^)]*)\)""").find(s)?.let { m -> val p = m.groupValues[1].split(',').map { it.trim().toFloat() }; return Color(p[0] / 255f, p[1] / 255f, p[2] / 255f, p.getOrElse(3) { 1f }) }
            return Color.Black
        }

        /** Parses an SVG fragment (with or without the outer `<svg>`; [vb] is the viewBox when the fragment has none). */
        fun parse(src: String, vb: FloatArray = floatArrayOf(0f, 0f, 400f, 260f)): Svg {
            var viewBox = vb; val items = ArrayList<Item>()
            val stack = ArrayDeque<Map<String, String>>(); stack.addLast(mapOf("fill" to "#000000"))
            var textAttrs: Map<String, String>? = null
            fun inherited(a: Map<String, String>): Map<String, String> { val m = LinkedHashMap<String, String>(); for (k in listOf("fill", "stroke", "stroke-width", "stroke-dasharray", "stroke-linecap", "opacity", "fill-opacity", "font-size", "text-anchor", "font-weight")) stack.last()[k]?.let { m[k] = it }; m.putAll(a); return m }
            fun style(a: Map<String, String>) = Style(color(a["fill"]), color(a["stroke"]), a["stroke-width"]?.toFloatOrNull() ?: 1f,
                a["stroke-dasharray"]?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }?.let { if (it.size % 2 == 1) (it + it).toFloatArray() else it.toFloatArray() },
                a["stroke-linecap"] == "round", a["opacity"]?.toFloatOrNull() ?: 1f, a["fill-opacity"]?.toFloatOrNull() ?: 1f)
            fun rot(a: Map<String, String>): Triple<Float, Float, Float>? = a["transform"]?.let { Regex("""rotate\(([^)]*)\)""").find(it) }?.groupValues?.get(1)?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.let { Triple(it[0], it.getOrElse(1) { 0f }, it.getOrElse(2) { 0f }) }
            fun f(a: Map<String, String>, k: String) = a[k]?.toFloatOrNull() ?: 0f
            for (m in TAG.findAll(src)) {
                if (m.groupValues[5].isNotEmpty()) { textAttrs?.let { a -> val t = unescape(m.groupValues[5]).trim(); if (t.isNotEmpty()) items += Text(f(a, "x"), f(a, "y"), t, a["font-size"]?.toFloatOrNull() ?: 16f, a["text-anchor"] ?: "start", (a["font-weight"] ?: "").let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 }, style(a), rot(a)) }; continue }
                val close = m.groupValues[1] == "/"; val tag = m.groupValues[2]; val self = m.groupValues[4] == "/"
                if (close) { if (tag == "g") stack.removeLastOrNull()?.let { if (stack.isEmpty()) stack.addLast(it) }; if (tag == "text") textAttrs = null; continue }
                val a0 = ATTR.findAll(m.groupValues[3]).associate { it.groupValues[1] to it.groupValues[2] }
                val a = inherited(a0)
                when (tag) {
                    "svg" -> a0["viewBox"]?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.let { viewBox = it.toFloatArray() }
                    "g" -> if (!self) stack.addLast(a)
                    "text" -> if (!self) textAttrs = a
                    else -> shape(tag, a)?.let { items += Shape(it, style(if (tag == "line" || tag == "polyline") LinkedHashMap(a).apply { if (a0["fill"] == null) put("fill", "none") } else a), rot(a)) }
                }
            }
            return Svg(viewBox[0], viewBox[1], viewBox[2], viewBox[3], items)
        }
        private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

        private fun shape(tag: String, a: Map<String, String>): Ops? {
            fun f(k: String) = a[k]?.toFloatOrNull() ?: 0f
            val p = Ops()
            when (tag) {
                "rect" -> { val x = f("x"); val y = f("y"); val w = f("width"); val h = f("height"); var rx = a["rx"]?.toFloatOrNull() ?: a["ry"]?.toFloatOrNull() ?: 0f; var ry = a["ry"]?.toFloatOrNull() ?: rx
                    rx = minOf(rx, w / 2); ry = minOf(ry, h / 2)
                    if (rx <= 0f) p.add(5, x, y, x + w, y + h) else p.add(6, x, y, x + w, y + h, rx, ry) }
                "circle" -> p.add(7, f("cx") - f("r"), f("cy") - f("r"), f("cx") + f("r"), f("cy") + f("r"))
                "ellipse" -> p.add(7, f("cx") - f("rx"), f("cy") - f("ry"), f("cx") + f("rx"), f("cy") + f("ry"))
                "line" -> { p.moveTo(f("x1"), f("y1")); p.lineTo(f("x2"), f("y2")) }
                "polygon", "polyline" -> { val v = (a["points"] ?: "").trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                    for (i in 0 until v.size / 2) if (i == 0) p.moveTo(v[0], v[1]) else p.lineTo(v[2 * i], v[2 * i + 1]); if (tag == "polygon") p.close() }
                "path" -> pathData(a["d"] ?: "", p)
                else -> return null
            }
            return p
        }

        private val NUM = Regex("""[MmLlHhVvQqCcAaZzSsTt]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
        fun pathData(d: String, p: Ops) {
            val tk = NUM.findAll(d).map { it.value }.toList(); var i = 0; var cmd = 'M'; var x = 0f; var y = 0f; var sx = 0f; var sy = 0f
            fun num() = tk[i++].toFloat()
            while (i < tk.size) {
                if (tk[i][0].isLetter()) { cmd = tk[i][0]; i++; if (cmd == 'Z' || cmd == 'z') { p.close(); x = sx; y = sy; continue } }
                if (i >= tk.size) break
                val rel = cmd.isLowerCase()
                when (cmd.uppercaseChar()) {
                    'M' -> { val nx = num() + (if (rel) x else 0f); val ny = num() + (if (rel) y else 0f); p.moveTo(nx, ny); x = nx; y = ny; sx = x; sy = y; cmd = if (rel) 'l' else 'L' }
                    'L' -> { x = num() + (if (rel) x else 0f); y = num() + (if (rel) y else 0f); p.lineTo(x, y) }
                    'H' -> { x = num() + (if (rel) x else 0f); p.lineTo(x, y) }
                    'V' -> { y = num() + (if (rel) y else 0f); p.lineTo(x, y) }
                    'Q' -> { val cx = num() + (if (rel) x else 0f); val cy = num() + (if (rel) y else 0f); val nx = num() + (if (rel) x else 0f); val ny = num() + (if (rel) y else 0f); p.quadraticTo(cx, cy, nx, ny); x = nx; y = ny }
                    'C' -> { val c1x = num() + (if (rel) x else 0f); val c1y = num() + (if (rel) y else 0f); val c2x = num() + (if (rel) x else 0f); val c2y = num() + (if (rel) y else 0f); val nx = num() + (if (rel) x else 0f); val ny = num() + (if (rel) y else 0f); p.cubicTo(c1x, c1y, c2x, c2y, nx, ny); x = nx; y = ny }
                    'A' -> { val rx = num(); val ry = num(); val phi = num(); val large = num() != 0f; val sweep = num() != 0f; val nx = num() + (if (rel) x else 0f); val ny = num() + (if (rel) y else 0f); arc(p, x, y, rx, ry, phi, large, sweep, nx, ny); x = nx; y = ny }
                    else -> i++
                }
            }
        }
        /** SVG elliptical arc (endpoint form) as line segments. */
        private fun arc(p: Ops, x1: Float, y1: Float, rx0: Float, ry0: Float, phiDeg: Float, large: Boolean, sweep: Boolean, x2: Float, y2: Float) {
            var rx = abs(rx0.toDouble()); var ry = abs(ry0.toDouble()); if (rx == 0.0 || ry == 0.0) { p.lineTo(x2, y2); return }
            val phi = phiDeg * PI / 180; val c = cos(phi); val s = sin(phi)
            val dx = (x1 - x2) / 2.0; val dy = (y1 - y2) / 2.0; val xp = c * dx + s * dy; val yp = -s * dx + c * dy
            val lam = xp * xp / (rx * rx) + yp * yp / (ry * ry); if (lam > 1) { rx *= sqrt(lam); ry *= sqrt(lam) }
            val num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp; val den = rx * rx * yp * yp + ry * ry * xp * xp
            var co = sqrt(maxOf(0.0, num / den)); if (large == sweep) co = -co
            val cxp = co * rx * yp / ry; val cyp = -co * ry * xp / rx
            val cx = c * cxp - s * cyp + (x1 + x2) / 2.0; val cy = s * cxp + c * cyp + (y1 + y2) / 2.0
            fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double { val a = acos(((ux * vx + uy * vy) / (sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy))).coerceIn(-1.0, 1.0)); return if (ux * vy - uy * vx < 0) -a else a }
            val t1 = ang(1.0, 0.0, (xp - cxp) / rx, (yp - cyp) / ry); var dt = ang((xp - cxp) / rx, (yp - cyp) / ry, (-xp - cxp) / rx, (-yp - cyp) / ry)
            if (!sweep && dt > 0) dt -= 2 * PI else if (sweep && dt < 0) dt += 2 * PI
            val n = maxOf(4, (abs(dt) / (PI / 16)).toInt())
            for (k in 1..n) { val t = t1 + dt * k / n; p.lineTo((cx + rx * cos(t) * c - ry * sin(t) * s).toFloat(), (cy + rx * cos(t) * s + ry * sin(t) * c).toFloat()) }
        }
    }
}
