# Golden harness (S1)

Runs the web build v69 headless (Playwright + Chromium) and dumps JSON that the native tests
must reproduce. The web build is the oracle (CLAUDE.md, "How we verify"). Test-only: nothing
here ships in the APK.

## Needs
- `reference/solidsketch_v69.html` (owner's DEV copy, 20,495,043 bytes; git-ignored). The scripts refuse any other size.
- Node 22 and Playwright 1.63.0 with Chromium (`npm ci` here, or a machine where `require('playwright')` already works).

## Run
```
node tools/golden/r10_totals.js        # R10 totals → tests/steel/golden/r10/
node tools/golden/run_cases.js [name]  # scripted cases → tests/steel/golden/cases/<case>/
```
R10 takes about 25 s (opening 6,638 bodies is about 17 s); the three T144 cases take about 6 s. Both outputs are deterministic (two runs are byte-identical).

## How it drives v69
- The page is served from a local HTTP server on 127.0.0.1 and opened with `#auto`, which exposes `window.__ss` (`S`, `ssOpenData`, …). All other network requests are blocked.
- Each scripted case runs in a new browser, because v69 restores its autosave on reload.
- `.ss` files load through the app's own Open path (`__ss.ssOpenData`).
- Cases use the v42 batch API: `__st.addMany` (members from `ST_SETS` + overrides), `__st.connectMany` (components with `CONN[key].def` + overrides), then `__st.number()`.
- Totals use `__st.weights()` (v66 Tekla weights, bolts excluded), `__st.tkd.all()` / `tkd.idx()` (Document manager list, assembly and part marks), bolt bodies (`st.kind === 'bolts'`), and `__st.ncExport()` (DSTV_Profiles, DSTV_Plates, NC_dxf).

## The one patch (stale exports)
Several `window.__st` entries were captured before later chapters re-wrapped the function, so they
call an early link of the chain, not the live one:

| Export | Captured at | Last reassign |
|---|---|---|
| `__st.connGeom` | L14039 (v34 `stConnGeom`) | L17091 (23-step chain) |
| `__st.build` | L13827 | L15228 |
| `__st.portalParts` | L13880 | L15354 |
| `__st.dwg.holes` (`dwHoles`) | L14852 | L15933 |

The server adds ONE line after line 14039 of the page it serves:
`Object.assign(window.__st, { live: { connGeom, build, portalParts, dwHoles } })`. Each entry is a
wrapper that looks the name up when called, so it always reaches the end of the chain. The file
on disk is not changed. Check: R10 totals are byte-identical with and without the patch.
Use `__st.live.*`, never the stale exports. `__st.checks` and `__st.list` are fine (re-exported live, or wrappers).

## Output
- `r10/summary.json`: headline numbers and `vsTarget` against CLAUDE.md (162 assemblies, 243 part marks, 63,722 kg, 418 drawings, 13 GA).
- `r10/counts.json`, `weights.json` (total, per assembly, per part mark), `bolts.json` (per size and diameter), `drawings.json` (all 418), `marks.json`, `nc_manifest.json` + `r10/nc/**` (482 files, kept whole for S13).
- `cases/<name>/case.json` (inputs) and `dump.json`: parts (st fields, volume, bbox, Tekla weight, holes, outline), bolts (points, d, n, grade, L), connections (q, welds, cut solids per part from the live chain), weights, the drawing list and each assembly/part drawing's data (`tkd.json`), and the NC manifest; NC files are in `cases/<name>/nc/**`.

Rounding: positions and volumes to 0.01. Native tests compare positions with 0.1 mm tolerance, counts and marks exactly, NC text line by line.

## Adding a case
Add an entry to a file in `cases/` (`members: [{ set, extra }]`, `conns: [[key, [memberIndex…], q]]`). Every component needs at least three cases: a standard one, an odd profile, and a skewed or sloped one.
