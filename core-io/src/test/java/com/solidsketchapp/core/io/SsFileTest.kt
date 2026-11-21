package com.solidsketchapp.core.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class SsFileTest {
    @Test
    fun gzipRoundTrip() {
        val json = """{"format":"solidsketch","version":1,"bodies":[],"x":"ü€"}"""
        val out = ByteArrayOutputStream()
        SsFile.writeJsonText(out, json)
        val bytes = out.toByteArray()
        assertEquals(0x1f, bytes[0].toInt() and 0xff) // gzip magic
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
        assertEquals(json, SsFile.readJsonText(ByteArrayInputStream(bytes)))
    }
}
