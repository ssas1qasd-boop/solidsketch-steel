package com.solidsketchapp.core.view.gl

import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3 renderer. Bodies are packed into chunks ([BatchBuilder]) so a model like R10
 * (6,638 bodies, 1.8 M triangles) draws in a few calls; an edit rebuilds only its chunk.
 * Flat shading comes from screen-space derivatives (no normal buffer). All state arrives
 * through [post] (body / overlay updates) and [frame] (camera, selection), so the GL thread
 * never touches UI-thread objects.
 */
class SceneRenderer : GLSurfaceView.Renderer {
    class Frame(val viewProj: FloatArray, val view: FloatArray, val selected: Set<BodyId>, val highlighted: Set<BodyId>)

    sealed interface Op {
        data class SetBody(val id: BodyId, val mesh: Mesh, val rgb: Int, val hidden: Boolean) : Op
        data class RemoveBody(val id: BodyId) : Op
        data class SetOverlay(val name: String, val lines: FloatArray, val argb: Int) : Op
        data class RemoveOverlay(val name: String) : Op
        data object Clear : Op
    }

    /** Numbers for the debug overlay (read from the UI thread). */
    class Stats(val cpuMs: Double, val fps: Double, val drawCalls: Int, val triangles: Long, val bodies: Int, val chunks: Int, val gpuBytes: Long)

    private class Body(val mesh: Mesh, val rgb: Int, val hidden: Boolean)

    private class GpuChunk {
        val ids = ArrayList<BodyId>()
        var vertices = 0
        var dirty = true
        var vao = 0; var vbo = 0; var ibo = 0
        var indexCount = 0
        var bytes = 0L
        var ranges: Map<BodyId, BatchBuilder.Range> = emptyMap()
    }

    private class Overlay(val lines: FloatArray, val argb: Int) { var vao = 0; var vbo = 0 }

    private val ops = ConcurrentLinkedQueue<Op>()
    @Volatile var frame: Frame? = null
    @Volatile var background = 0xEEF1F5 // v69 light theme clear colour
    @Volatile var stats = Stats(0.0, 0.0, 0, 0, 0, 0, 0)
        private set

    private val bodies = HashMap<BodyId, Body>()
    private val chunks = ArrayList<GpuChunk>()
    private val chunkOf = HashMap<BodyId, GpuChunk>()
    private val overlays = LinkedHashMap<String, Overlay>()
    private var solidProg = 0
    private var lineProg = 0
    private var lastFrameNs = 0L
    private var fpsAvg = 0.0

    fun post(op: Op) { ops.add(op) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // New context: every GPU object is gone. Keep the CPU data and upload again.
        for (c in chunks) { c.vao = 0; c.vbo = 0; c.ibo = 0; c.dirty = true }
        for (o in overlays.values) { o.vao = 0; o.vbo = 0 }
        solidProg = program(SOLID_VS, SOLID_FS)
        lineProg = program(LINE_VS, LINE_FS)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = glViewport(0, 0, width, height)

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        if (lastFrameNs != 0L) {
            val dt = (t0 - lastFrameNs) / 1e9
            if (dt < 0.25) fpsAvg = if (fpsAvg == 0.0) 1 / dt else fpsAvg * 0.9 + (1 / dt) * 0.1   // only while frames keep coming
        }
        lastFrameNs = t0
        drainOps()
        val bg = background
        glClearColor(((bg shr 16) and 255) / 255f, ((bg shr 8) and 255) / 255f, (bg and 255) / 255f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val f = frame ?: return
        var calls = 0; var tris = 0L; var bytes = 0L
        glUseProgram(solidProg)
        glUniformMatrix4fv(glGetUniformLocation(solidProg, "uVP"), 1, false, f.viewProj, 0)
        glUniformMatrix4fv(glGetUniformLocation(solidProg, "uView"), 1, false, f.view, 0)
        val uEm = glGetUniformLocation(solidProg, "uEmissive")
        glUniform3f(uEm, 0f, 0f, 0f)
        for (c in chunks) {
            if (c.dirty) rebuild(c)
            bytes += c.bytes
            if (c.indexCount == 0) continue
            glBindVertexArray(c.vao)
            glDrawElements(GL_TRIANGLES, c.indexCount, GL_UNSIGNED_INT, 0)
            calls++; tris += c.indexCount / 3
        }
        // Selection: draw those bodies again from their chunk with v69's emissive 0x6b4a10 (same depth, LEQUAL).
        val hi = if (f.highlighted.isEmpty()) f.selected else f.selected + f.highlighted
        if (hi.isNotEmpty()) {
            glUniform3f(uEm, 0x6b / 255f, 0x4a / 255f, 0x10 / 255f)
            for (id in hi) {
                val c = chunkOf[id] ?: continue
                val r = c.ranges[id] ?: continue
                glBindVertexArray(c.vao)
                glDrawElements(GL_TRIANGLES, r.count, GL_UNSIGNED_INT, r.firstIndex * 4)
                calls++
            }
        }
        if (overlays.isNotEmpty()) {
            glUseProgram(lineProg)
            glUniformMatrix4fv(glGetUniformLocation(lineProg, "uVP"), 1, false, f.viewProj, 0)
            val uc = glGetUniformLocation(lineProg, "uColor")
            for (o in overlays.values) {
                if (o.lines.size < 6) continue
                if (o.vao == 0) uploadOverlay(o)
                val c = o.argb
                glUniform4f(uc, ((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f, ((c ushr 24) and 255) / 255f)
                glBindVertexArray(o.vao)
                glDrawArrays(GL_LINES, 0, o.lines.size / 3)
                calls++
            }
        }
        glBindVertexArray(0)
        stats = Stats((System.nanoTime() - t0) / 1e6, fpsAvg, calls, tris, bodies.size, chunks.size, bytes)
    }

    private fun drainOps() {
        while (true) {
            when (val op = ops.poll() ?: break) {
                is Op.SetBody -> {
                    val old = bodies.put(op.id, Body(op.mesh, op.rgb, op.hidden))
                    val c = chunkOf[op.id]
                    if (c != null) { c.vertices += op.mesh.vertexCount - (old?.mesh?.vertexCount ?: 0); c.dirty = true }
                    else assign(op.id, op.mesh.vertexCount)
                }
                is Op.RemoveBody -> {
                    val old = bodies.remove(op.id)
                    chunkOf.remove(op.id)?.let { c -> c.ids.remove(op.id); c.vertices -= old?.mesh?.vertexCount ?: 0; c.dirty = true }
                }
                is Op.SetOverlay -> { overlays.remove(op.name)?.let(::freeOverlay); overlays[op.name] = Overlay(op.lines, op.argb) }
                is Op.RemoveOverlay -> overlays.remove(op.name)?.let(::freeOverlay)
                Op.Clear -> { chunks.forEach(::freeChunk); chunks.clear(); chunkOf.clear(); bodies.clear(); overlays.values.forEach(::freeOverlay); overlays.clear() }
            }
        }
        if (chunks.isNotEmpty() && chunks.last().ids.isEmpty() && chunks.size > 1) freeChunk(chunks.removeAt(chunks.size - 1))
    }

    private fun assign(id: BodyId, vertices: Int) {
        var c = chunks.lastOrNull()
        if (c == null || (c.ids.isNotEmpty() && c.vertices + vertices > BatchBuilder.CHUNK_VERTICES)) { c = GpuChunk(); chunks += c }
        c.ids += id; c.vertices += vertices; c.dirty = true
        chunkOf[id] = c
    }

    private fun rebuild(c: GpuChunk) {
        c.dirty = false
        val items = c.ids.mapNotNull { id -> bodies[id]?.takeIf { !it.hidden && it.mesh.triangleCount > 0 }?.let { BatchBuilder.Item(id, it.mesh, it.rgb) } }
        val data = BatchBuilder.build(items)
        if (c.vao == 0) {
            val ids = IntArray(3); glGenVertexArrays(1, ids, 0); glGenBuffers(2, ids, 1)
            c.vao = ids[0]; c.vbo = ids[1]; c.ibo = ids[2]
        }
        glBindVertexArray(c.vao)
        glBindBuffer(GL_ARRAY_BUFFER, c.vbo)
        glBufferData(GL_ARRAY_BUFFER, data.vertices.remaining(), data.vertices, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, BatchBuilder.STRIDE, 0)
        glEnableVertexAttribArray(1)
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, BatchBuilder.STRIDE, 12)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, c.ibo)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data.indices.remaining(), data.indices, GL_STATIC_DRAW)
        glBindVertexArray(0)
        c.indexCount = data.indexCount
        c.ranges = data.ranges
        c.bytes = data.vertices.capacity().toLong() + data.indices.capacity()
    }

    private fun uploadOverlay(o: Overlay) {
        val ids = IntArray(2); glGenVertexArrays(1, ids, 0); glGenBuffers(1, ids, 1)
        o.vao = ids[0]; o.vbo = ids[1]
        glBindVertexArray(o.vao)
        glBindBuffer(GL_ARRAY_BUFFER, o.vbo)
        val vb = ByteBuffer.allocateDirect(o.lines.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(o.lines).also { it.position(0) }
        glBufferData(GL_ARRAY_BUFFER, o.lines.size * 4, vb, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0)
        glBindVertexArray(0)
    }

    private fun freeChunk(c: GpuChunk) {
        if (c.vao != 0) { glDeleteVertexArrays(1, intArrayOf(c.vao), 0); glDeleteBuffers(2, intArrayOf(c.vbo, c.ibo), 0) }
        c.vao = 0
    }

    private fun freeOverlay(o: Overlay) {
        if (o.vao != 0) { glDeleteVertexArrays(1, intArrayOf(o.vao), 0); glDeleteBuffers(1, intArrayOf(o.vbo), 0) }
    }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = glCreateShader(type); glShaderSource(s, src); glCompileShader(s)
            val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + glGetShaderInfoLog(s) }
            return s
        }
        val p = glCreateProgram()
        glAttachShader(p, shader(GL_VERTEX_SHADER, vs)); glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs))
        glBindAttribLocation(p, 0, "aPos")
        glLinkProgram(p)
        val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link: " + glGetProgramInfoLog(p) }
        return p
    }

    private companion object {
        const val SOLID_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aColor;
uniform mat4 uVP;
uniform mat4 uView;
out vec3 vView;
out vec3 vColor;
void main() { vec4 w = vec4(aPos, 1.0); vView = (uView * w).xyz; vColor = aColor.rgb; gl_Position = uVP * w; }"""
        const val SOLID_FS = """#version 300 es
precision highp float;
in vec3 vView;
in vec3 vColor;
uniform vec3 uEmissive;
out vec4 outColor;
void main() {
  vec3 n = normalize(cross(dFdx(vView), dFdy(vView)));
  float d = abs(n.z);
  outColor = vec4(min(vColor * (0.35 + 0.65 * d) + uEmissive, vec3(1.0)), 1.0);
}"""
        const val LINE_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uVP;
void main() { gl_Position = uVP * vec4(aPos, 1.0); }"""
        const val LINE_FS = """#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 outColor;
void main() { outColor = uColor; }"""
    }
}
