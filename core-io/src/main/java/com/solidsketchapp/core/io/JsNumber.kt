package com.solidsketchapp.core.io

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Number -> text exactly as JavaScript's JSON.stringify / Number.prototype.toString writes it
 * (ECMA-262 Number::toString): shortest digits that round-trip to the same double, plain
 * notation for 1e-7 < |x| < 1e21, otherwise exponent form like "1e+21" or "1.5e-7"; -0 is "0".
 * Uses BigDecimal and Double.parseDouble only, so the result does not depend on the
 * platform's Double.toString (which differs between JDKs and Android versions).
 */
object JsNumber {
    fun format(d: Double): String {
        require(!d.isNaN() && !d.isInfinite()) { "JSON has no NaN/Infinity" }
        if (d == 0.0) return "0"
        // Fast path: integers up to 2^53 print as plain digits.
        if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) return d.toLong().toString()
        val exact = BigDecimal(d)
        var digits = ""; var n = 0
        for (p in 1..17) {
            val r = exact.round(MathContext(p, RoundingMode.HALF_EVEN))
            if (r.toDouble() == d) {
                val u = r.unscaledValue().abs().toString().trimEnd('0')
                digits = u.ifEmpty { "0" }
                // value = 0.digits * 10^n
                n = r.precision() - r.scale()
                break
            }
        }
        val sign = if (d < 0) "-" else ""
        val k = digits.length
        return sign + when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val e = n - 1
                val es = if (e >= 0) "+$e" else "$e"
                if (k == 1) digits + "e" + es else digits[0] + "." + digits.substring(1) + "e" + es
            }
        }
    }

    /** Float32 value as JavaScript prints Array.from(new Float32Array(...)) entries. */
    fun format(f: Float): String = format(f.toDouble())
}
