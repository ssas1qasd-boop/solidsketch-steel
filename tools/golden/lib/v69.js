// Headless v69 driver for golden dumps. The web build is the oracle (CLAUDE.md, "How we verify").
'use strict';
const fs = require('fs');
const http = require('http');
const path = require('path');
const { chromium } = require('playwright');

const REPO = path.resolve(__dirname, '..', '..', '..');
const HTML = path.join(REPO, 'reference', 'solidsketch_v69.html');
const GOLDEN = path.join(REPO, 'tests', 'steel', 'golden');
const EXPECT_SIZE = 20495043;

function checkReference() {
  if (!fs.existsSync(HTML)) throw new Error(`Missing ${HTML} (owner's DEV copy, kept out of git)`);
  const n = fs.statSync(HTML).size;
  if (n !== EXPECT_SIZE) throw new Error(`${HTML} is ${n} bytes, expected ${EXPECT_SIZE} (wrong copy of v69)`);
}

// Test-only patch: several __st exports were captured before later chapters re-wrapped the
// function (e.g. __st.connGeom is the v34 stConnGeom, not the live 23-step chain). We add ONE
// line after the export at line 14039 with wrappers that resolve the live binding at call time.
// Nothing else in the page changes. See tools/golden/README.md.
const PATCH_AFTER_LINE = 14039;
const PATCH_ANCHOR = "Object.assign(window.__st, { conns: () => S.stConns, connGeom: stConnGeom,";
const PATCH_LINE = '  Object.assign(window.__st, { live: { connGeom: c => stConnGeom(c), build: s => stBuild(s), portalParts: q => stPortalParts(q), dwHoles: (...a) => dwHoles(...a), data: () => ({ ST_CLASS, ST_TYPE, TK_FAMNAME, TK_PICK, TK_ICON, UPN_T, UPE_T }) } }); /* golden harness */';

let patched = null;
function patchedHtml() {
  if (patched) return patched;
  const lines = fs.readFileSync(HTML, 'utf8').split('\n');
  if (!lines[PATCH_AFTER_LINE - 1].includes(PATCH_ANCHOR)) throw new Error(`v69 line ${PATCH_AFTER_LINE} is not the expected __st export; cannot add the live-chain wrappers`);
  lines.splice(PATCH_AFTER_LINE, 0, PATCH_LINE);
  return (patched = Buffer.from(lines.join('\n'), 'utf8'));
}

let server = null, serverUrl = null;
async function serve() {
  if (serverUrl) return serverUrl;
  const body = patchedHtml();
  server = http.createServer((req, res) => {
    if (req.url.split('?')[0] === '/v69.html') { res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); res.end(body); }
    else { res.writeHead(404); res.end(); }
  });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  server.unref();
  return (serverUrl = `http://127.0.0.1:${server.address().port}/v69.html`);
}

/** Opens v69 with #auto (exposes window.__ss) and waits until the steel API is up. */
async function openV69({ timeoutMs = 300000, log = true } = {}) {
  checkReference();
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.setDefaultTimeout(timeoutMs);
  const errors = [];
  page.on('pageerror', e => errors.push(String(e && e.message || e)));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  const url = await serve();
  // Block every other request: the oracle must run offline and deterministically.
  await page.route(u => !u.href.startsWith(url), r => r.abort());
  const t0 = Date.now();
  await page.goto(url + '#auto', { waitUntil: 'load', timeout: timeoutMs });
  await page.waitForFunction(() => !!(window.__st && window.__ss && window.__ss.ssOpenData && window.__st.weights && window.__st.live), null, { timeout: timeoutMs });
  if (log) console.error(`v69 ready in ${((Date.now() - t0) / 1000).toFixed(1)} s`);
  return { browser, page, errors };
}

/** Loads a .ss file through the app's own Open path (ssOpenData). Returns the body count. */
async function openSS(page, file) {
  const b64 = fs.readFileSync(file).toString('base64');
  return page.evaluate(async ({ name, b64 }) => {
    const bin = atob(b64); const u8 = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
    await window.__ss.ssOpenData(name, u8.buffer);
    return window.__ss.S.bodies.length;
  }, { name: path.basename(file), b64 });
}

/** Writes JSON with sorted keys and a stable layout so diffs stay readable. */
function writeGolden(rel, data) {
  const out = path.join(GOLDEN, rel);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  const sortKeys = v => Array.isArray(v) ? v.map(sortKeys)
    : v && typeof v === 'object' ? Object.fromEntries(Object.keys(v).sort().map(k => [k, sortKeys(v[k])])) : v;
  fs.writeFileSync(out, JSON.stringify(sortKeys(data), null, 1) + '\n');
  return out;
}

module.exports = { REPO, HTML, GOLDEN, openV69, openSS, writeGolden };
