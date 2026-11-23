package com.solidsketchapp.steel.data

import com.solidsketchapp.core.api.JsNumber

/**
 * Exact port of web v69's live `stProf` chain (LIVE-FUNCTIONS.md: base v32 at L13577, wrapped by
 * v41 at L15203): profile name -> [Profile] or null. Numbers in keys are written as JavaScript
 * writes them (`${a}`), and every formula keeps v69's order of operations, so the doubles are
 * bit-identical to v69's. Golden: tests/steel/golden/data/profile_cases.json.
 */
internal class StProf(private val cat: ProfileCatalog) {
    private fun js(d: Double) = JsNumber.format(d)
    private val num = """(\d+(?:\.\d+)?)"""
    private val stLen = 7.85e-3

    // ---- v32 base stProf (L13577) ----
    private val rePl = Regex("""^PL\s*$num(?:\s*\*\s*$num)?$""", RegexOption.IGNORE_CASE)
    private val reRod = Regex("""^(?:D|RD|ROD)\s*$num$""", RegexOption.IGNORE_CASE)
    private val reL3 = Regex("""^L\s*$num\s*\*\s*$num\s*\*\s*$num$""", RegexOption.IGNORE_CASE)

    fun base(name: String): Profile? {
        val n = name.trim().uppercase().replace('X', 'x')
        cat.row(n.uppercase())?.let { return it }
        val raw = name.trim()
        rePl.find(raw)?.let { mm ->
            val a = mm.groupValues[1].toDouble()
            val bs = mm.groups[2]?.value
            return if (bs != null && bs.toDouble() != 0.0) { val b = bs.toDouble()
                Profile("PL${js(a)}*${js(b)}", "P", h = a, b = b, m = a * b * stLen, P = 2 * (a + b) / 1000)
            } else Profile("PL${js(a)}", "P", t = a, m = 0.0, P = 0.0)
        }
        reRod.find(raw)?.let { mm ->
            val d = mm.groupValues[1].toDouble()
            return Profile("D${js(d)}", "O", d = d, t = d / 2, m = Math.PI * d * d / 4 * stLen, P = Math.PI * d / 1000)
        }
        reL3.find(raw)?.let { mm ->
            val (a, b, t) = mm.groupValues.drop(1).map { it.toDouble() }
            return Profile("L${js(a)}*${js(b)}*${js(t)}", "L", h = a, b = b, t = t, tw = t, tf = t, m = (a + b - t) * t * stLen, P = 2 * (a + b) / 1000)
        }
        return null
    }

    // ---- v41 wrapper (L15203) ----
    private val reFlt = Regex("""^FLT\s*$num\s*\*\s*$num$""")
    private val reHollow = Regex("""^(SHS|RHS)\s*$num\s*\*\s*$num(?:\s*\*\s*$num)?$""")
    private val reRound = Regex("""^(CHS|PD|PIPE)\s*$num\s*\*\s*$num$""")
    private val reL2 = Regex("""^L\s*$num\s*\*\s*$num$""")
    private val reChan = Regex("""^(UPN|UPE|U)\s*(\d+)$""")
    private val reCz = Regex("""^(CC|C|Z|ZZ)\s*$num-$num-$num-$num$""")
    private val reBi = Regex("""^BI\s*$num-$num-$num-$num(?:-$num-$num)?$""")
    private val rePhi = Regex("""^PHI\s*([\d.]+)-([\d.]+)-([\d.]+)-([\d.]+)-([\d.]+)(?:-([\d.]+)-([\d.]+))?$""")

    fun lookup(name: String): Profile? {
        base(name)?.let { return it }
        val s = name.trim().uppercase().replace("X", "*")
        reFlt.find(s)?.let { m -> return base("PL${m.groupValues[1]}*${m.groupValues[2]}")!!.copy(key = "FLT${m.groupValues[1]}*${m.groupValues[2]}") }
        reHollow.find(s)?.let { m ->
            val h = m.groupValues[2].toDouble(); val three = m.groups[4] != null
            val bv = if (three) m.groupValues[3].toDouble() else h
            val t = if (three) m.groupValues[4].toDouble() else m.groupValues[3].toDouble()
            val a = 2 * t * (h + bv - 2 * t)
            val key = if (three) "${m.groupValues[1]}${js(h)}*${js(bv)}*${js(t)}" else "${m.groupValues[1]}${js(h)}*${js(t)}"
            return Profile(key, "R", h = h, b = bv, t = t, r = 2 * t, m = a * stLen, P = 2 * (h + bv) / 1000)
        }
        reRound.find(s)?.let { m ->
            val d = m.groupValues[2].toDouble(); val t = m.groupValues[3].toDouble()
            return Profile("${m.groupValues[1]}${js(d)}*${js(t)}", "O", d = d, t = t, m = Math.PI * (d - t) * t * stLen, P = Math.PI * d / 1000)
        }
        reL2.find(s)?.let { m -> return base("L${m.groupValues[1]}*${m.groupValues[1]}*${m.groupValues[2]}") }
        reChan.find(s)?.let { m ->
            val table = if (m.groupValues[1] == "UPE") cat.upe else cat.upn
            val v = table[m.groupValues[2].toInt()]
            if (v != null) return Profile((if (m.groupValues[1] == "U") "UPN" else m.groupValues[1]) + m.groupValues[2], "U",
                h = v[0], b = v[1], tw = v[2], tf = v[3], r = v[4], m = v[5], P = (2 * v[0] + 4 * v[1] - 2 * v[2]) / 1000)
        }
        reCz.find(s)?.let { m ->
            val (h, t, c, b) = m.groupValues.drop(2).map { it.toDouble() }
            val z = m.groupValues[1][0] == 'Z'; val l = h + 2 * b + 2 * c - 4 * t
            return Profile("${m.groupValues[1]}${js(h)}-${js(t)}-${js(c)}-${js(b)}", if (z) "Z" else "C", h = h, b = b, t = t, c = c, tw = t, tf = t, r = 0.0, m = l * t * stLen, P = 2 * l / 1000)
        }
        reBi.find(s)?.let { m ->
            val g = m.groupValues
            val h = g[1].toDouble(); val tw = g[2].toDouble(); val b = g[3].toDouble(); val tf = g[4].toDouble()
            val b2 = if (g[5].isNotEmpty()) g[5].toDouble() else b; val tf2 = if (g[6].isNotEmpty()) g[6].toDouble() else tf
            val a = b * tf + b2 * tf2 + (h - tf - tf2) * tw
            return Profile(s.replace(Regex("""\s"""), ""), "I", h = h, b = maxOf(b, b2), bt = b, bb = b2, tw = tw, tf = tf, tf2 = tf2, r = 0.0, built = 1.0,
                m = a * stLen, P = (2 * h + 2 * b + 2 * b2 - 2 * tw) / 1000)
        }
        rePhi.find(s)?.let { m ->
            val g = m.groupValues
            val h1 = jsNum(g[1]); val h2 = jsNum(g[2]); val tw = jsNum(g[3]); val b = jsNum(g[4]); val tf = jsNum(g[5])
            val b2 = if (g[6].isNotEmpty()) jsNum(g[6]) else b; val tf2 = if (g[7].isNotEmpty()) jsNum(g[7]) else tf
            val hh = maxOf(h1, h2); val a = b * tf + b2 * tf2 + ((h1 + h2) / 2 - tf - tf2) * tw
            return Profile(s.replace(Regex("""\s"""), ""), "I", h = hh, h1 = h1, h2 = h2, b = maxOf(b, b2), bt = b, bb = b2, tw = tw, tf = tf, tf2 = tf2, r = 0.0,
                taper = 1.0, built = 1.0, m = a * stLen, P = (h1 + h2 + 2 * b + 2 * b2 - 2 * tw) / 1000)
        }
        return null
    }

    /** JavaScript unary + on a `[\d.]+` match: "1.2.3" and "." are NaN (v69 then builds a NaN profile). */
    private fun jsNum(s: String): Double = s.toDoubleOrNull() ?: Double.NaN
}
