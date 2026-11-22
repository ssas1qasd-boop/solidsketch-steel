package com.solidsketchapp.core.io

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * `.ss` load/save, compatible with web v69 both ways (ssEncode / ssDecode / docObject).
 *
 * Streaming: the file is never held as one string. `shape.vp` / `shape.tv` (93% of R10) are
 * parsed straight into float / int arrays; every other value is kept as its exact JSON text,
 * so unknown fields round-trip unchanged. On save `vp` is written the way JavaScript prints
 * Float32 values (see [JsNumber]), so an unchanged document saves byte-identical to the
 * web build's file except `savedAt`.
 */
object SsCodec {
    const val FORMAT = "solidsketch"
    const val APP_NAME = "SolidSketch Steel Android"

    class SsFormatException(message: String) : IOException(message)

    // ------------------------------------------------------------------ read

    /** Reads gzipped (as the web saves) or plain JSON into a new document. */
    fun read(input: InputStream): MemoryDocument = MemoryDocument().also { read(input, it) }

    fun read(input: InputStream, into: MemoryDocument) {
        val bin = BufferedInputStream(input, 1 shl 16)
        bin.mark(2)
        val gz = bin.read() == 0x1f && bin.read() == 0x8b
        bin.reset()
        val src = if (gz) GZIPInputStream(bin, 1 shl 16) else bin
        Parser(InputStreamReader(src, Charsets.UTF_8)).parseFile(into)
    }

    private class Parser(private val r: Reader) {
        private val buf = CharArray(1 shl 16)
        private var pos = 0
        private var lim = 0
        private val tmp = StringBuilder()

        private fun fill(): Boolean { if (pos < lim) return true; lim = r.read(buf); pos = 0; return lim > 0 }
        private fun peek(): Char { if (!fill()) throw SsFormatException("This is not a SolidSketch file (unexpected end)"); return buf[pos] }
        private fun next(): Char { val c = peek(); pos++; return c }
        private fun ws() { while (fill() && buf[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++ }
        private fun expect(c: Char) { ws(); val n = next(); if (n != c) throw SsFormatException("This is not a SolidSketch file (expected '$c', got '$n')") }

        /** Reads a JSON string (after ws) and returns its decoded value. */
        private fun string(): String {
            ws(); if (next() != '"') throw SsFormatException("This is not a SolidSketch file (expected a key)")
            val sb = StringBuilder()
            while (true) {
                val c = next()
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = next()) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> sb.append(String(CharArray(4) { next() }).toInt(16).toChar())
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }

        /** Copies the raw text of the next JSON value into [out]. */
        private fun raw(out: StringBuilder) {
            ws()
            var depth = 0
            var inStr = false
            do {
                val c = next()
                out.append(c)
                if (inStr) {
                    if (c == '\\') out.append(next()) else if (c == '"') inStr = false
                    continue
                }
                when (c) {
                    '"' -> inStr = true
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                    else -> if (depth == 0) {
                        // scalar: read until a delimiter
                        while (fill() && buf[pos].let { it != ',' && it != '}' && it != ']' && it != ' ' && it != '\n' && it != '\r' && it != '\t' }) out.append(next())
                        return
                    }
                }
            } while (depth > 0 || inStr)
        }

        private fun rawString(): String { tmp.setLength(0); raw(tmp); return tmp.toString() }

        /** Iterates the members of an object: [onKey] must consume the value. */
        private inline fun obj(onKey: (String) -> Unit) {
            expect('{'); ws()
            if (peek() == '}') { pos++; return }
            while (true) {
                val k = string(); expect(':'); onKey(k); ws()
                when (next()) { ',' -> continue; '}' -> return; else -> throw SsFormatException("This is not a SolidSketch file (bad object)") }
            }
        }

        private inline fun arr(onItem: () -> Unit) {
            expect('['); ws()
            if (peek() == ']') { pos++; return }
            while (true) {
                onItem(); ws()
                when (next()) { ',' -> continue; ']' -> return; else -> throw SsFormatException("This is not a SolidSketch file (bad array)") }
            }
        }

        /** A JSON number as double; integers are parsed exactly without allocating. */
        private fun number(): Double {
            ws()
            var neg = false
            if (peek() == '-') { neg = true; pos++ }
            var v = 0L; var nd = 0; var simple = true
            tmp.setLength(0)
            while (fill()) {
                val c = buf[pos]
                if (c in '0'..'9') { if (nd < 18) v = v * 10 + (c - '0') else simple = false; nd++; tmp.append(c); pos++ }
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { simple = false; tmp.append(c); pos++ }
                else break
            }
            if (tmp.isEmpty()) throw SsFormatException("This is not a SolidSketch file (expected a number)")
            return if (simple) (if (neg) -v.toDouble() else v.toDouble()) else (if (neg) -tmp.toString().toDouble() else tmp.toString().toDouble())
        }

        fun parseFile(doc: MemoryDocument) {
            val header = ArrayList<Pair<String, String>>()
            var headerDocIndex = -1
            val fields = ArrayList<Pair<String, String>>()
            var bodiesIndex = -1
            val bodies = ArrayList<Triple<BodyId, String, Mesh?>>()
            var maxId = 0L
            obj { k ->
                if (k == "doc") {
                    headerDocIndex = header.size
                    obj { dk ->
                        if (dk == "bodies") {
                            bodiesIndex = fields.size
                            arr { bodies += body().also { b -> b.first.value.toLongOrNull()?.let { maxId = maxOf(maxId, it) } } }
                        } else fields += dk to rawString()
                    }
                } else header += k to rawString()
            }
            val format = header.firstOrNull { it.first == "format" }?.second
            if (format != "\"$FORMAT\"" || headerDocIndex < 0) throw SsFormatException("This is not a SolidSketch file")
            val version = header.firstOrNull { it.first == "version" }?.second?.toDoubleOrNull() ?: 1.0
            if (version > 1) throw SsFormatException("This file was saved by a newer SolidSketch — update the app to open it")
            if (bodiesIndex < 0) bodiesIndex = fields.size
            val fileNext = fields.firstOrNull { it.first == "nextId" }?.second?.toDoubleOrNull()?.toLong() ?: 1L
            doc.loadFrom(header, headerDocIndex, fields, bodiesIndex, bodies, maxOf(fileNext, maxId + 1))
        }

        /** One body: raw JSON without `shape`, and the mesh from `shape`. */
        private fun body(): Triple<BodyId, String, Mesh?> {
            val sb = StringBuilder(256).append('{')
            var id: String? = null
            var mesh: Mesh? = null
            obj { k ->
                if (k == "shape") {
                    mesh = shape()
                } else {
                    if (sb.length > 1) sb.append(',')
                    sb.append('"').append(escapeKey(k)).append("\":")
                    val start = sb.length
                    raw(sb)
                    if (k == "id") id = sb.substring(start)
                }
            }
            sb.append('}')
            val bid = BodyId((id ?: "").trim().removeSurrounding("\""))
            return Triple(bid, sb.toString(), mesh)
        }

        private fun shape(): Mesh? {
            ws()
            if (peek() == 'n') { rawString(); return null }   // "shape":null
            var vp = FloatArray(0); var tv = IntArray(0)
            obj { k ->
                when (k) {
                    "vp" -> { var a = FloatArray(1024); var n = 0; arr { if (n == a.size) a = a.copyOf(n * 2); a[n++] = number().toFloat() }; vp = a.copyOf(n) }
                    "tv" -> { var a = IntArray(1024); var n = 0; arr { if (n == a.size) a = a.copyOf(n * 2); a[n++] = number().toLong().toInt() }; tv = a.copyOf(n) }
                    else -> rawString()   // not written by v69; dropped
                }
            }
            return Mesh(vp, tv)
        }
    }

    private fun escapeKey(k: String): String = buildString {
        for (c in k) when {
            c == '"' -> append("\\\""); c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(java.util.Locale.ROOT, c.code))
            else -> append(c)
        }
    }

    // ------------------------------------------------------------------ write

    /** ISO time as JavaScript's toISOString(): milliseconds, Z. */
    fun nowIso(): String = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString().let { if (it.length == 20) it.dropLast(1) + ".000Z" else it }

    /** Writes [doc] gzipped (as the web build does). [savedAt] replaces the header's savedAt. */
    fun write(doc: MemoryDocument, out: OutputStream, savedAt: String = nowIso()) {
        val gz = GZIPOutputStream(out, 1 shl 16)
        val w = OutputStreamWriter(gz, Charsets.UTF_8).buffered(1 shl 16)
        writeJson(doc, w, savedAt)
        w.flush()
        gz.finish()
    }

    /** Writes the plain JSON text (no gzip). */
    fun writeJson(doc: MemoryDocument, w: Writer, savedAt: String = nowIso()) {
        val header = doc.header.ifEmpty {
            listOf("format" to "\"$FORMAT\"", "version" to "1", "app" to "\"$APP_NAME\"", "unit" to "\"mm\"", "name" to "\"Untitled\"", "rev" to "0", "savedAt" to "\"\"")
        }
        val docIndex = if (doc.header.isEmpty()) header.size else doc.headerDocIndex
        w.write("{")
        var first = true
        fun sep() { if (!first) w.write(","); first = false }
        for ((i, kv) in header.withIndex()) {
            if (i == docIndex) { sep(); w.write("\"doc\":"); writeDoc(doc, w) }
            sep(); w.write("\""); w.write(escapeKey(kv.first)); w.write("\":")
            w.write(if (kv.first == "savedAt") "\"$savedAt\"" else kv.second)
        }
        if (docIndex >= header.size) { sep(); w.write("\"doc\":"); writeDoc(doc, w) }
        w.write("}")
    }

    private fun writeDoc(doc: MemoryDocument, w: Writer) {
        val names = doc.fieldNames()
        val bodiesAt = if (doc.header.isEmpty() && names.isEmpty()) 0 else doc.bodiesIndex.coerceIn(0, names.size)
        w.write("{")
        var first = true
        fun sep() { if (!first) w.write(","); first = false }
        for ((i, k) in names.withIndex()) {
            if (i == bodiesAt) { sep(); writeBodies(doc, w) }
            sep(); w.write("\""); w.write(escapeKey(k)); w.write("\":")
            // nextId must stay ahead of every body id (new bodies made in this app).
            w.write(if (k == "nextId") nextIdValue(doc, doc.field(k)!!) else doc.field(k)!!)
        }
        if (bodiesAt >= names.size) { sep(); writeBodies(doc, w) }
        w.write("}")
    }

    private fun nextIdValue(doc: MemoryDocument, raw: String): String {
        val v = raw.toDoubleOrNull() ?: return raw
        return if (doc.nextBodyId > v) doc.nextBodyId.toString() else raw
    }

    private fun writeBodies(doc: MemoryDocument, w: Writer) {
        w.write("\"bodies\":[")
        for ((i, id) in doc.bodyIds().withIndex()) {
            if (i > 0) w.write(",")
            var json = doc.bodyJson(id)!!.trim()
            if (!json.startsWith("{\"id\":")) {
                // A body made in this app: the web build needs its numeric id first.
                val rest = json.removePrefix("{").trimStart()
                json = "{\"id\":${id.value}" + (if (rest.startsWith("}")) "" else ",") + rest
            }
            val mesh = doc.bodyMesh(id)
            if (mesh == null) { w.write(json); continue }
            val body = json.substring(0, json.lastIndexOf('}')).trimEnd()
            w.write(body)
            if (!body.endsWith("{")) w.write(",")
            w.write("\"shape\":{\"vp\":[")
            val p = mesh.positions
            for (k in p.indices) { if (k > 0) w.write(","); w.write(JsNumber.format(p[k])) }
            w.write("],\"tv\":[")
            val t = mesh.indices
            for (k in t.indices) { if (k > 0) w.write(","); w.write((t[k].toLong() and 0xffffffffL).toString()) }
            w.write("]}}")
        }
        w.write("]")
    }
}
