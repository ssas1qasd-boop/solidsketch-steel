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
 * OpenGL ES 3 renderer. Bodies are flat shaded from screen-space derivatives (no normal
 * buffer). All state arrives through [post] (body / overlay updates) and [frame]
 * (camera matrices, selection), so the GL thread never touches UI-thread objects.
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

    private class Body(val mesh: Mesh, val r: Float, val g: Float, val b: Float, val hidden: Boolean) {
        var vao = 0; var vbo = 0; var ibo = 0
    }

    private class Overlay(val lines: FloatArray, val argb: Int) { var vao = 0; var vbo = 0 }

    private val ops = ConcurrentLinkedQueue<Op>()
    @Volatile var frame: Frame? = null
    @Volatile var background = 0xEEF1F5 // v69 light theme clear colour
    @Volatile var lastFrameMs = 0.0
        private set

    private val bodies = LinkedHashMap<BodyId, Body>()
    private val overlays = LinkedHashMap<String, Overlay>()
    private var solidProg = 0
    private var lineProg = 0

    fun post(op: Op) { ops.add(op) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // New context: every GPU object is gone. Keep the CPU data and upload again lazily.
        for (b in bodies.values) { b.vao = 0; b.vbo = 0; b.ibo = 0 }
        for (o in overlays.values) { o.vao = 0; o.vbo = 0 }
        solidProg = program(SOLID_VS, SOLID_FS)
        lineProg = program(LINE_VS, LINE_FS)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = glViewport(0, 0, width, height)

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        drainOps()
        val bg = background
        glClearColor(((bg shr 16) and 255) / 255f, ((bg shr 8) and 255) / 255f, (bg and 255) / 255f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val f = frame ?: return
        glUseProgram(solidProg)
        glUniformMatrix4fv(glGetUniformLocation(solidProg, "uVP"), 1, false, f.viewProj, 0)
        glUniformMatrix4fv(glGetUniformLocation(solidProg, "uView"), 1, false, f.view, 0)
        val uColor = glGetUniformLocation(solidProg, "uColor")
        val uEm = glGetUniformLocation(solidProg, "uEmissive")
        for ((id, b) in bodies) {
            if (b.hidden || b.mesh.triangleCount == 0) continue
            if (b.vao == 0) upload(b)
            glUniform3f(uColor, b.r, b.g, b.b)
            // v69: selected body gets emissive 0x6b4a10 on top of its colour.
            if (id in f.selected || id in f.highlighted) glUniform3f(uEm, 0x6b / 255f, 0x4a / 255f, 0x10 / 255f) else glUniform3f(uEm, 0f, 0f, 0f)
            glBindVertexArray(b.vao)
            glDrawElements(GL_TRIANGLES, b.mesh.triangleCount * 3, GL_UNSIGNED_INT, 0)
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
            }
        }
        glBindVertexArray(0)
        lastFrameMs = (System.nanoTime() - t0) / 1e6
    }

    private fun drainOps() {
        while (true) {
            when (val op = ops.poll() ?: break) {
                is Op.SetBody -> { bodies.remove(op.id)?.let(::free); bodies[op.id] = Body(op.mesh, ((op.rgb shr 16) and 255) / 255f, ((op.rgb shr 8) and 255) / 255f, (op.rgb and 255) / 255f, op.hidden) }
                is Op.RemoveBody -> bodies.remove(op.id)?.let(::free)
                is Op.SetOverlay -> { overlays.remove(op.name)?.let(::freeOverlay); overlays[op.name] = Overlay(op.lines, op.argb) }
                is Op.RemoveOverlay -> overlays.remove(op.name)?.let(::freeOverlay)
                Op.Clear -> { bodies.values.forEach(::free); bodies.clear(); overlays.values.forEach(::freeOverlay); overlays.clear() }
            }
        }
    }

    private fun upload(b: Body) {
        val ids = IntArray(3); glGenVertexArrays(1, ids, 0); glGenBuffers(2, ids, 1)
        b.vao = ids[0]; b.vbo = ids[1]; b.ibo = ids[2]
        glBindVertexArray(b.vao)
        glBindBuffer(GL_ARRAY_BUFFER, b.vbo)
        val vb = ByteBuffer.allocateDirect(b.mesh.positions.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(b.mesh.positions).also { it.position(0) }
        glBufferData(GL_ARRAY_BUFFER, b.mesh.positions.size * 4, vb, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, b.ibo)
        val ib = ByteBuffer.allocateDirect(b.mesh.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(b.mesh.indices).also { it.position(0) }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, b.mesh.indices.size * 4, ib, GL_STATIC_DRAW)
        glBindVertexArray(0)
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

    private fun free(b: Body) {
        if (b.vao != 0) { glDeleteVertexArrays(1, intArrayOf(b.vao), 0); glDeleteBuffers(2, intArrayOf(b.vbo, b.ibo), 0) }
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
uniform mat4 uVP;
uniform mat4 uView;
out vec3 vView;
void main() { vec4 w = vec4(aPos, 1.0); vView = (uView * w).xyz; gl_Position = uVP * w; }"""
        const val SOLID_FS = """#version 300 es
precision highp float;
in vec3 vView;
uniform vec3 uColor;
uniform vec3 uEmissive;
out vec4 outColor;
void main() {
  vec3 n = normalize(cross(dFdx(vView), dFdy(vView)));
  float d = abs(n.z);
  outColor = vec4(min(uColor * (0.35 + 0.65 * d) + uEmissive, vec3(1.0)), 1.0);
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
