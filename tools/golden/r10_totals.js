// S1: dump the R10 totals from web v69 (the oracle) to tests/steel/golden/r10/.
// Usage: node tools/golden/r10_totals.js [path/to/model.ss]
'use strict';
const crypto = require('crypto');
const path = require('path');
const { REPO, openV69, openSS, writeGolden } = require('./lib/v69');

const MODEL = process.argv[2] || path.join(REPO, 'tests', 'steel', 'models', 'Frame_28x53m_R10.ss');
// Targets from CLAUDE.md ("Target level", web v68 against the Tekla package).
const TARGET = { assemblies: 162, partMarks: 243, weightKg: 63722, drawings: 418, ga: 13 };

const sha = s => crypto.createHash('sha256').update(s).digest('hex');

(async () => {
  const { browser, page, errors } = await openV69();
  try {
    const nBodies = await openSS(page, MODEL);
    const d = await page.evaluate(() => {
      const S = __ss.S, st = __st;
      const r2 = v => Math.round(v * 100) / 100;
      const kinds = {}; for (const b of S.bodies) { const k = b.st ? b.st.kind : '(none)'; kinds[k] = (kinds[k] || 0) + 1; }
      const connTypes = {}; for (const c of S.stConns || []) connTypes[c.type] = (connTypes[c.type] || 0) + 1;

      // Weights: Tekla rules (v66), bolts excluded.
      const w = st.weights();

      // Bolts: every bolt body is a group of n bolts of one size.
      const bySize = {}, byDia = {}; let boltGroups = 0, bolts = 0;
      for (const b of S.bodies) { if (!b.st || b.st.kind !== 'bolts') continue;
        const q = b.st; const n = +q.n || 0; boltGroups++; bolts += n;
        const k = `M${q.d} ${q.grade || ''} L${q.L}`.replace(/\s+/g, ' ');
        (bySize[k] = bySize[k] || { d: +q.d, grade: q.grade || '', L: +q.L, groups: 0, bolts: 0 }); bySize[k].groups++; bySize[k].bolts += n;
        const kd = 'M' + q.d; (byDia[kd] = byDia[kd] || { groups: 0, bolts: 0 }); byDia[kd].groups++; byDia[kd].bolts += n; }

      // Drawing list (Document manager, Tekla style).
      const dw = st.tkd.all();
      const dwCount = { G: 0, A: 0, W: 0 }; for (const x of dw) dwCount[x.type] = (dwCount[x.type] || 0) + 1;
      const X = st.tkd.idx();
      const asmList = [...X.asm.keys()].map(k => ({ mark: k, n: X.asm.get(k).length }));
      const posList = [...X.pos.keys()].map(k => ({ mark: k, n: X.pos.get(k).items.length }));

      // NC / DSTV (same call as the Document manager's "NC files (DSTV)").
      let nc = null, ncErr = null;
      try { const r = st.ncExport({}); nc = { files: (r.files || []).map(f => ({ path: f.path || f.name, text: f.text })), warnings: r.warnings || [] }; }
      catch (e) { ncErr = String(e && e.message || e); }

      return {
        counts: { bodies: S.bodies.length, kinds, connections: (S.stConns || []).length, connTypes },
        weight: { weight_t: w.weight_t, net_t: w.net_t, gross_t: w.gross_t, parts: w.parts, boltBodiesExcluded: w.boltBodiesExcluded, note: w.note },
        weightPerAsm: w.asm, weightPerPart: w.part,
        bolts: { groups: boltGroups, bolts, bySize, byDia },
        drawings: { total: dw.length, byType: dwCount, list: dw.map(x => ({ key: x.key, type: x.type, mark: x.mark, title: x.title, size: x.size || '', n: x.n == null ? null : x.n, file: x.file })) },
        marks: { assemblies: asmList.length, partMarks: posList.length, asm: asmList, pos: posList },
        nc, ncErr,
      };
    });

    // NC: keep every file (S13 byte-compares against them) plus a manifest with line counts and hashes.
    let ncManifest = null;
    if (d.nc) {
      ncManifest = { files: d.nc.files.map(f => ({ path: f.path, lines: f.text.split('\n').length, bytes: Buffer.byteLength(f.text), sha256: sha(f.text) })), warnings: d.nc.warnings };
      for (const f of d.nc.files) writeRaw(path.join('r10', 'nc', f.path), f.text);
      const dirs = {}; for (const f of d.nc.files) { const k = f.path.split('/')[0]; dirs[k] = (dirs[k] || 0) + 1; }
      ncManifest.byDir = dirs;
    }
    delete d.nc;

    const summary = {
      model: path.basename(MODEL), modelSha256: sha(require('fs').readFileSync(MODEL)),
      oracle: 'web v69 (reference/solidsketch_v69.html, 20,495,043 bytes), headless Chromium',
      bodies: nBodies, assemblies: d.marks.assemblies, partMarks: d.marks.partMarks,
      weightKg: Math.round(d.weight.weight_t * 1000), netKg: Math.round(d.weight.net_t * 1000), grossKg: Math.round(d.weight.gross_t * 1000),
      bolts: d.bolts.bolts, boltGroups: d.bolts.groups,
      drawings: d.drawings.total, drawingsByType: d.drawings.byType,
      ncFiles: ncManifest ? ncManifest.files.length : 0, ncByDir: ncManifest ? ncManifest.byDir : null, ncError: d.ncErr,
      pageErrors: errors,
    };
    summary.vsTarget = {
      assemblies: [summary.assemblies, TARGET.assemblies], partMarks: [summary.partMarks, TARGET.partMarks],
      weightKg: [summary.weightKg, TARGET.weightKg], drawings: [summary.drawings, TARGET.drawings], ga: [d.drawings.byType.G, TARGET.ga],
    };

    writeGolden('r10/summary.json', summary);
    writeGolden('r10/counts.json', d.counts);
    writeGolden('r10/weights.json', { total: d.weight, perAssembly: d.weightPerAsm, perPartMark: d.weightPerPart });
    writeGolden('r10/bolts.json', d.bolts);
    writeGolden('r10/drawings.json', d.drawings);
    writeGolden('r10/marks.json', d.marks);
    if (ncManifest) writeGolden('r10/nc_manifest.json', ncManifest);
    console.log(JSON.stringify(summary, null, 1));
  } finally {
    await browser.close();
  }
})().catch(e => { console.error(e); process.exit(1); });

function writeRaw(rel, text) {
  const fs = require('fs'); const { GOLDEN } = require('./lib/v69');
  const out = path.join(GOLDEN, rel); fs.mkdirSync(path.dirname(out), { recursive: true }); fs.writeFileSync(out, text);
}
