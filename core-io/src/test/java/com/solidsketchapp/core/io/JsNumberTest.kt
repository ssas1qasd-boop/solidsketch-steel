package com.solidsketchapp.core.io

import org.junit.Assert.assertEquals
import org.junit.Test

class JsNumberTest {
    // Expected strings are what node prints for String(x) / JSON.stringify(x).
    @Test fun integersAndZero() {
        assertEquals("0", JsNumber.format(0.0)); assertEquals("0", JsNumber.format(-0.0))
        assertEquals("13299", JsNumber.format(13299.0)); assertEquals("-3688", JsNumber.format(-3688.0))
        assertEquals("123456789012", JsNumber.format(123456789012.0))
        assertEquals("1e+21", JsNumber.format(1e21)); assertEquals("100000000000000000000", JsNumber.format(1e20))
    }

    @Test fun decimalsAndExponents() {
        assertEquals("0.1", JsNumber.format(0.1)); assertEquals("1.5", JsNumber.format(1.5))
        assertEquals("0.000001", JsNumber.format(1e-6)); assertEquals("1e-7", JsNumber.format(1e-7))
        assertEquals("1.5e-7", JsNumber.format(1.5e-7)); assertEquals("1.2345e+25", JsNumber.format(1.2345e25))
        assertEquals("-2.5e-8", JsNumber.format(-2.5e-8)); assertEquals("123.456", JsNumber.format(123.456))
    }

    @Test fun float32ValuesLikeTheWebWrites() {
        // Array.from(new Float32Array([22416.33, 0.1, 7.3])) in node:
        assertEquals("22416.330078125", JsNumber.format(22416.33f))
        assertEquals("0.10000000149011612", JsNumber.format(0.1f))
        assertEquals("7.300000190734863", JsNumber.format(7.3f))
        assertEquals("2.802596928649634e-45", JsNumber.format(java.lang.Float.intBitsToFloat(2)))
    }
}
