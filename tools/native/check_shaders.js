// Compiles and links the viewport shaders (GLSL ES 3.00) in headless Chromium WebGL2,
// which uses the same shading language as OpenGL ES 3.0, and draws one triangle.
// Usage: node tools/native/check_shaders.js   (needs Playwright, see tools/golden/README.md)
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const SRC = path.resolve(__dirname, '../../core-view/src/main/java/com/solidsketchapp/core/view/gl/SceneRenderer.kt');
const kt = fs.readFileSync(SRC, 'utf8');
const get = n => { const m = new RegExp(n + ' = """([\\s\\S]*?)"""').exec(kt); if (!m) throw new Error('missing ' + n); return m[1]; };

(async () => {
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  const errs = await p.evaluate(({ sv, sf, lv, lf }) => {
    const gl = document.createElement('canvas').getContext('webgl2');
    if (!gl) return ['no WebGL2'];
    const out = [];
    const prog = (vs, fs, name, uniforms) => {
      const mk = (t, s) => { const h = gl.createShader(t); gl.shaderSource(h, s); gl.compileShader(h); if (!gl.getShaderParameter(h, gl.COMPILE_STATUS)) out.push(`${name} compile: ${gl.getShaderInfoLog(h)}`); return h; };
      const pr = gl.createProgram(); gl.attachShader(pr, mk(gl.VERTEX_SHADER, vs)); gl.attachShader(pr, mk(gl.FRAGMENT_SHADER, fs));
      gl.bindAttribLocation(pr, 0, 'aPos'); gl.linkProgram(pr);
      if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) out.push(`${name} link: ${gl.getProgramInfoLog(pr)}`);
      for (const u of uniforms) if (!gl.getUniformLocation(pr, u)) out.push(`${name}: uniform ${u} not found`);
      return pr;
    };
    const s = prog(sv, sf, 'solid', ['uVP', 'uEye', 'uEmissive']);
    prog(lv, lf, 'line', ['uVP', 'uColor', 'uBias']);
    if (out.length) return out;
    gl.useProgram(s);
    const id = new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
    gl.uniformMatrix4fv(gl.getUniformLocation(s, 'uVP'), false, id); gl.uniform3f(gl.getUniformLocation(s, 'uEye'), 0, 0, 5);
    gl.vertexAttrib4f(1, 0.75, 0.22, 0.17, 1); gl.uniform3f(gl.getUniformLocation(s, 'uEmissive'), 0, 0, 0);   // body colour as a constant attribute
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 0, 1, -1, 0, 0, 1, 0]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 12, 0);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    const px = new Uint8Array(4); gl.readPixels(gl.canvas.width / 2, gl.canvas.height / 2, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
    // Face-on (normal +Z, eye on +Z), v69's lights and Phong worked out by hand with three.js r15x formulas: (205, 68, 56).
    if (Math.abs(px[0] - 205) > 2 || Math.abs(px[1] - 68) > 2 || Math.abs(px[2] - 56) > 2) out.push('unexpected pixel ' + Array.from(px));
    if (gl.getError()) out.push('GL error ' + gl.getError());
    return out;
  }, { sv: get('SOLID_VS'), sf: get('SOLID_FS'), lv: get('LINE_VS'), lf: get('LINE_FS') });
  await b.close();
  if (errs.length) { console.error(errs.join('\n')); process.exit(1); }
  console.log('shaders OK (compile, link, uniforms, one draw)');
})().catch(e => { console.error(e); process.exit(1); });
