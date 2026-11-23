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
        var evao = 0; var evbo = 0; var edgeVertices = 0
        /** Bumped by every rebuild, so edges computed for an older content are dropped. */
        var version = 0
    }

    /** Edges of a chunk, computed off the GL thread (R10: about 1.3 M segments). */
    private class EdgesReady(val chunk: GpuChunk, val version: Int, val data: ByteBuffer, val vertices: Int) : Op

    private class Overlay(val lines: FloatArray, val argb: Int) { var vao = 0; var vbo = 0 }

    private val ops = ConcurrentLinkedQueue<Op>()
    @Volatile var frame: Frame? = null
    /** Asks the view for a frame (set by ViewportView; used when background edges are ready). */
    @Volatile var requestRender: (() -> Unit)? = null
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
    /** Feature edges per mesh (GL thread only): an edit rebuilds a chunk without recomputing the other bodies' edges. */
    private val edgeCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Mesh, FloatArray>())
    private val edgeWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "edges").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private var fpsAvg = 0.0

    fun post(op: Op) { ops.add(op) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // New context: every GPU object is gone. Keep the CPU data and upload again.
        for (c in chunks) { c.vao = 0; c.vbo = 0; c.ibo = 0; c.evao = 0; c.evbo = 0; c.edgeVertices = 0; c.dirty = true }
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
        val eye = eyeOf(f.view)
        glUniform3f(glGetUniformLocation(solidProg, "uEye"), eye[0], eye[1], eye[2])
        val uEm = glGetUniformLocation(solidProg, "uEmissive")
        glUniform3f(uEm, 0f, 0f, 0f)
        // v69 bodyMaterial: polygon offset 1/1 keeps the feature edges from fighting with the faces.
        glEnable(GL_POLYGON_OFFSET_FILL); glPolygonOffset(1f, 1f)
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
            glUniform3f(uEm, srgbToLinear(0x6b), srgbToLinear(0x4a), srgbToLinear(0x10))
            for (id in hi) {
                val c = chunkOf[id] ?: continue
                val r = c.ranges[id] ?: continue
                glBindVertexArray(c.vao)
                glDrawElements(GL_TRIANGLES, r.count, GL_UNSIGNED_INT, r.firstIndex * 4)
                calls++
            }
        }
        glDisable(GL_POLYGON_OFFSET_FILL)
        // v69 edge lines: 0x262b34, drawn a hair in front of their faces in depth only (gl_Position.z -= 6e-5 * w).
        glUseProgram(lineProg)
        glUniformMatrix4fv(glGetUniformLocation(lineProg, "uVP"), 1, false, f.viewProj, 0)
        val uBias = glGetUniformLocation(lineProg, "uBias")
        glUniform1f(uBias, 6e-5f)
        glUniform4f(glGetUniformLocation(lineProg, "uColor"), 0x26 / 255f, 0x2b / 255f, 0x34 / 255f, 1f)
        for (c in chunks) { if (c.edgeVertices == 0) continue; glBindVertexArray(c.evao); glDrawArrays(GL_LINES, 0, c.edgeVertices); calls++ }
        glUniform1f(uBias, 0f)
        if (overlays.isNotEmpty()) {
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
                is EdgesReady -> if (op.version == op.chunk.version && op.chunk.vao != 0) uploadEdges(op.chunk, op.data, op.vertices)
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
        val version = ++c.version
        edgeWorker.execute {
            val e = BatchBuilder.build(items) { m -> edgeCache[m] ?: FeatureEdges.of(m).also { edgeCache[m] = it } }
            post(EdgesReady(c, version, e.edges, e.edgeVertexCount))
            requestRender?.invoke()
        }
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
        c.edgeVertices = 0   // until the worker's edges for this content arrive
        c.indexCount = data.indexCount
        c.ranges = data.ranges
        c.bytes = data.vertices.capacity().toLong() + data.indices.capacity()
    }

    private fun uploadEdges(c: GpuChunk, buf: ByteBuffer, vertices: Int) {
        if (c.evao == 0) { val e = IntArray(2); glGenVertexArrays(1, e, 0); glGenBuffers(1, e, 1); c.evao = e[0]; c.evbo = e[1] }
        glBindVertexArray(c.evao)
        glBindBuffer(GL_ARRAY_BUFFER, c.evbo)
        glBufferData(GL_ARRAY_BUFFER, buf.remaining(), buf, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0)
        glBindVertexArray(0)
        c.edgeVertices = vertices
        c.bytes += buf.capacity()
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
        if (c.evao != 0) { glDeleteVertexArrays(1, intArrayOf(c.evao), 0); glDeleteBuffers(1, intArrayOf(c.evbo), 0) }
        c.vao = 0; c.evao = 0
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
out vec3 vWorld;
out vec3 vColor;
void main() { vWorld = aPos; vColor = aColor.rgb; gl_Position = uVP * vec4(aPos, 1.0); }"""
        /**
         * v69's scene (three.js r15x, physical light units, sRGB output, no tone mapping): HemisphereLight(0xffffff, 0x3a4250, 2.4)
         * from +Y (three's default up), DirectionalLights 2.2 at (6,-9,14), 0.8 at (-8,6,4), 0.55 at (-3,9,7); MeshPhongMaterial
         * shininess 48, specular 0x3c3c3c. Flat normals from screen-space derivatives (v69's meshes are flat too).
         */
        const val SOLID_FS = """#version 300 es
precision highp float;
in vec3 vWorld;
in vec3 vColor;
uniform vec3 uEye;
uniform vec3 uEmissive;
out vec4 outColor;
const float PI = 3.141592653589793;
vec3 toLinear(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c)); }
vec3 toSrgb(vec3 c) { return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c)); }
vec3 blinnPhong(vec3 l, vec3 v, vec3 n, vec3 spec, float shin) {
  vec3 h = normalize(l + v); float nh = max(dot(n, h), 0.0); float vh = max(dot(v, h), 0.0);
  vec3 F = spec + (1.0 - spec) * pow(1.0 - vh, 5.0);
  return F * (0.25 * (1.0 / PI) * (shin * 0.5 + 1.0) * pow(nh, shin));
}
void main() {
  vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
  vec3 v = normalize(uEye - vWorld);
  if (dot(n, v) < 0.0) n = -n;
  vec3 albedo = toLinear(vColor);
  vec3 spec = toLinear(vec3(60.0 / 255.0));
  vec3 ground = toLinear(vec3(58.0, 66.0, 80.0) / 255.0);
  vec3 hemi = mix(ground, vec3(1.0), 0.5 * n.y + 0.5) * 2.4;
  vec3 diffuse = hemi * albedo / PI; vec3 specular = vec3(0.0);
  vec3 L[3]; L[0] = normalize(vec3(6.0, -9.0, 14.0)); L[1] = normalize(vec3(-8.0, 6.0, 4.0)); L[2] = normalize(vec3(-3.0, 9.0, 7.0));
  float I[3]; I[0] = 2.2; I[1] = 0.8; I[2] = 0.55;
  for (int i = 0; i < 3; i++) {
    float nl = max(dot(n, L[i]), 0.0); vec3 irr = vec3(nl * I[i]);
    diffuse += irr * albedo / PI; specular += irr * blinnPhong(L[i], v, n, spec, 48.0);
  }
  outColor = vec4(toSrgb(clamp(diffuse + specular + uEmissive, 0.0, 1.0)), 1.0);
}"""
        const val LINE_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uVP;
uniform float uBias;
void main() { gl_Position = uVP * vec4(aPos, 1.0); gl_Position.z -= uBias * gl_Position.w; }"""
        const val LINE_FS = """#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 outColor;
void main() { outColor = uColor; }"""

        fun srgbToLinear(c: Int): Float { val x = c / 255.0; return (if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)).toFloat() }

        /** Camera position from a view matrix (column-major, rigid): eye = -R^T t. */
        fun eyeOf(v: FloatArray): FloatArray {
            val tx = v[12]; val ty = v[13]; val tz = v[14]
            return floatArrayOf(-(v[0] * tx + v[1] * ty + v[2] * tz), -(v[4] * tx + v[5] * ty + v[6] * tz), -(v[8] * tx + v[9] * ty + v[10] * tz))
        }
    }
}
