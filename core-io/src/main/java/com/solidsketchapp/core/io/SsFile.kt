package com.solidsketchapp.core.io

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * `.ss` container: gzipped JSON, `format: 'solidsketch', version: 1` (same as web v69).
 * S0 handles the gzip layer only; the document model with unknown-field round-trip is S3.
 */
object SsFile {
    const val FORMAT = "solidsketch"
    const val VERSION = 1

    fun readJsonText(input: InputStream): String =
        GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun writeJsonText(output: OutputStream, json: String) {
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }
}
