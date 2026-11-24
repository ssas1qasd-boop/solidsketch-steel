// UI golden (owner, 2026-11-23): v69 on the owner's phone (412 × 891 CSS px, DPR 2.625 = 1081.5 × 2339 px, dark, touch),
// scripted like the app's UI test: Steel mode, a grid, a column + beam with End plate 144 (a component cone), the beam
// selected (its two end handles), fixed camera states (orbit, pan, zoom, a new grid applied), then the grid sheet, the
// catalog and the End plate 144 dialog. Recorded: screenshots, and numbers in phone px — the screen position of every grid
// label, handle and component cone per camera state, and the box of every button and sheet.
//   python3 tools/golden/ui_fonts.py (once: the phone's font), then node tools/golden/ui_golden.js -> tests/steel/ui/golden/ui_golden.json + tests/steel/ui/golden/v69/*.png
'use strict';
const fs = require('fs');
const path = require('path');
const { REPO, openV69 } = require('./lib/v69');
const DPR = 2.625, W = 412, H = 891;
const OUT = path.join(REPO, 'tests', 'steel', 'ui', 'golden');
// camera states in mm and radians (v69 cam: eye = target + dist · (cos pitch cos yaw, cos pitch sin yaw, sin pitch), up Z, fov 45°)
const STATES = [
  { name: 'cam1_overview', grid: null, target: [18000, 10000, 0], dist: 60000, yaw: -0.9, pitch: 0.5 },
  { name: 'cam2_orbit', grid: null, target: [18000, 10000, 0], dist: 60000, yaw: -0.3, pitch: 0.85 },
  { name: 'cam3_pan', grid: null, target: [6000, 3000, 2000], dist: 45000, yaw: -0.3, pitch: 0.85 },
  { name: 'cam4_zoom', grid: null, target: [3000, 0, 3500], dist: 14000, yaw: -1.35, pitch: 0.3 },
  { name: 'cam5_apply', grid: { x: '0 4*13195', y: '0 5*5570', z: '0 7500 1500' }, target: [26000, 14000, 0], dist: 90000, yaw: -0.9, pitch: 0.5 },
];

(async () => {
  process.env.FONTCONFIG_FILE = path.join(__dirname, 'phone-fonts.conf');   // the phone's fonts (Roboto for every UI family)
  if (!require('child_process').execSync('fc-list').toString().includes('Roboto')) throw new Error('Roboto is not installed: run python3 tools/golden/ui_fonts.py (the phone renders v69 in Roboto)');
  const { browser, page, errors } = await openV69({ log: false, pageOptions: { viewport: { width: W, height: H }, deviceScaleFactor: DPR, isMobile: true, hasTouch: true, colorScheme: 'dark' } });
  fs.mkdirSync(path.join(OUT, 'v69'), { recursive: true });
  const res = { source: `web v69 at ${W} × ${H} CSS px, DPR ${DPR}, dark (tools/golden/ui_golden.js); positions and boxes in phone px (CSS px × ${DPR})`, dpr: DPR, canvas: [W * DPR, H * DPR], states: {}, boxes: {} };
  const settle = () => page.waitForTimeout(500);
  const boxes = (sel) => page.evaluate(({ sel, DPR }) => [...document.querySelectorAll(sel)].filter(e => e.offsetParent !== null || getComputedStyle(e).position === 'fixed')
    .map(e => { const r = e.getBoundingClientRect(); return { text: (e.innerText || e.value || e.placeholder || '').trim().split('\n')[0].slice(0, 40), x: +(r.x * DPR).toFixed(1), y: +(r.y * DPR).toFixed(1), w: +(r.width * DPR).toFixed(1), h: +(r.height * DPR).toFixed(1) }; }), { sel, DPR });
  try {
    await page.evaluate(() => { try { localStorage.clear(); } catch (e) { /* none */ } const b = [...document.querySelectorAll('#tools button')].find(x => /Steel/.test(x.textContent)); b.click(); });
    // parts: a column and a beam into it with End plate 144 (a cone); the beam selected (its handles)
    await page.evaluate(() => { const st = __st; const S = __ss.S;
      const a = st.add(Object.assign({ kind: 'member' }, st.ST_SETS.COLUMN, { prof: 'HEA300', p1: [0, 0, 0], p2: [0, 0, 7000] }), 'golden');
      const b = st.add(Object.assign({ kind: 'member' }, st.ST_SETS.BEAM, { prof: 'IPE360', p1: [0, 0, 6500], p2: [6000, 0, 6500] }), 'golden');
      st.connect('T144', [a.id, b.id], {}); window.__goldenBeam = b.id; __ss.renderUI(); });
    await settle();
    // the panel with nothing selected (with a part selected v69 adds the weight line, which comes with S11)
    res.panel = await boxes('#st-panel button');
    await page.evaluate(() => { __ss.S.selectedId = window.__goldenBeam; __ss.renderUI(); __st.ui.handles(); }); await settle();
    for (const s of STATES) {
      if (s.grid) await page.evaluate((g) => { const b = [...document.querySelectorAll('#st-panel button')].find(x => x.textContent.trim() === 'Grid'); b.click();
        const ins = document.querySelectorAll('#st-sheet input'); ins[0].value = g.x; ins[1].value = g.y; ins[2].value = g.z;
        [...document.querySelectorAll('#st-sheet button')].find(x => x.textContent.trim() === 'Apply').click(); }, s.grid);
      const r = await page.evaluate(({ s, DPR }) => {
        const cam = __ss.cam; cam.target.set(...s.target); cam.dist = s.dist; cam.yaw = s.yaw; cam.pitch = s.pitch; __st.ui.updateCamera(); __ss.requestRender();
        const camera = __ss.camera; camera.updateMatrixWorld(); const cr = document.getElementById('c').getBoundingClientRect(), cw = cr.width, ch = cr.height;
        // screen px (the canvas may not start at the top left of the page)
        const px = v => { const p = v.clone().project(camera); return [+((cr.x + (p.x + 1) / 2 * cw) * DPR).toFixed(2), +((cr.y + (1 - p.y) / 2 * ch) * DPR).toFixed(2), p.z < 1]; };
        const ST = __st.ui.ST();
        const labels = ST.grid.children.filter(o => o.isSprite).map(o => px(o.position));
        // each label's size on screen (a sprite o.scale.x mm across at its depth), phone px
        const fy = ch * DPR / (2 * Math.tan(camera.fov * Math.PI / 360));
        const labelSizes = ST.grid.children.filter(o => o.isSprite).map(o => { const v = o.position.clone().applyMatrix4(camera.matrixWorldInverse); return +(o.scale.x * fy / -v.z).toFixed(2); });
        const handles = ST.handles.children.map(o => px(o.position));
        const cones = __st.ui.sym().children.map(o => px(o.userData.base || o.position));
        return { labels, labelSizes, handles, cones, grid: __ss.S.stGrid, canvasRect: [cr.x * DPR, cr.y * DPR, cw * DPR, ch * DPR] };
      }, { s, DPR });
      await settle(); await page.screenshot({ path: path.join(OUT, 'v69', s.name + '.png') });
      res.states[s.name] = Object.assign({}, s, r);
      console.log(s.name, 'labels', r.labels.length, 'handles', r.handles.length, 'cones', r.cones.length);
    }
    // sheets: grid, catalog, End plate 144 dialog (boxes of the sheet and its buttons / inputs)
    await page.evaluate(() => { [...document.querySelectorAll('#st-panel button')].find(x => x.textContent.trim() === 'Grid').click(); }); await settle();
    res.boxes.grid_sheet = { sheet: await boxes('#st-sheet'), buttons: await boxes('#st-sheet button'), inputs: await boxes('#st-sheet input') };
    await page.screenshot({ path: path.join(OUT, 'v69', 'sheet_grid.png') });
    await page.evaluate(() => __st.catalog()); await settle();
    res.boxes.catalog = { sheet: await boxes('#st-sheet'), buttons: (await boxes('#st-sheet button')).slice(0, 12), search: await boxes('#st-sheet .srch') };
    await page.screenshot({ path: path.join(OUT, 'v69', 'sheet_catalog.png') });
    await page.evaluate(() => __st.compDialog('T144')); await settle();
    res.boxes.dialog_t144 = { sheet: await boxes('#st-sheet'), tabs: await boxes('#st-sheet .tktabs button'), bar: await boxes('#st-sheet .tkbar button'), top: await boxes('#st-sheet .tktop button') };
    await page.screenshot({ path: path.join(OUT, 'v69', 'sheet_dialog_t144.png') });
    // S10: the Portal frame sheet (from the panel)
    await page.evaluate(() => { const c = [...document.querySelectorAll('#st-sheet button')].find(x => x.textContent.trim() === 'Cancel'); if (c) c.click(); const s = document.getElementById('st-sheet'); if (s) s.hidden = true; }); await settle();
    await page.evaluate(() => { [...document.querySelectorAll('#st-panel button')].find(x => x.textContent.trim() === 'Portal frame').click(); }); await settle();
    res.boxes.portal_sheet = { sheet: await boxes('#st-sheet'), buttons: await boxes('#st-sheet button'), inputs: await boxes('#st-sheet input:not([type=checkbox])') };
    await page.screenshot({ path: path.join(OUT, 'v69', 'sheet_portal.png') });
  } finally { await browser.close(); }
  if (errors.length) console.log('page errors:', errors.slice(0, 3));
  fs.writeFileSync(path.join(OUT, 'ui_golden.json'), JSON.stringify(res, null, 1) + '\n');
  console.log('wrote', path.join(OUT, 'ui_golden.json'));
})().catch(e => { console.error(e); process.exit(1); });
