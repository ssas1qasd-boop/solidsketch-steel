# SolidSketch Steel: native Android app (Claude Code instructions)

This is the **Steel Edition plan**. It is separate from the main native-port plan (`CLAUDE.md`) and from the BIM plan. Never move features, code or documents between plans, and never merge this file into another plan's file.

## Goal
Build **SolidSketch Steel** as its own native Android app, with no dependency on the main native port. It brings the steel detailing of web build **v69** to Kotlin + Jetpack Compose, OpenGL ES and a C++ core over JNI. When the main native port is finished, the owner merges the two apps. Every structural choice below is made so that the merge is easy.

## Built for the later merge
- Same stack and versions as the main port (`CLAUDE.md`): Kotlin + Compose, OpenGL ES 3, C++ over JNI, minSdk 24, compile/target 35, AGP 8.7.3, Gradle 8.10.2, NDK 30.0.16248370, CMake 4.1.2, arm64-v8a + x86_64.
- Split the code into modules whose names say what they are:
  - `:core-view` (viewport, camera, free orbit, picking, undo/redo)
  - `:core-io` (`.ss` load/save)
  - `native/sscore-lite` (Manifold booleans and meshing behind a small C API with the same shape as the main port's `sscore`)
  - `:steel` (everything steel)
  - `native/sssteel` (steel geometry, which links `sscore-lite`)
- At merge time the `core-*` modules and `sscore-lite` get replaced by the main port's versions, and `:steel` moves across unchanged. So `:steel` may talk to the core modules **only through interfaces** (`ViewportApi`, `SelectionApi`, `UndoApi`, `DocumentApi`, `BooleanEngine`) defined in a small `:core-api` module. Write `docs/steel/MERGE.md` and keep it current: every interface, and what the main port must provide to satisfy it.
- Keep the core modules small. Build only what Steel needs (no sketch tools, no fillet/shell, no OpenCASCADE).
- `.ss` stays the same format as the web build (gzipped JSON, `format: 'solidsketch', version: 1`). Unknown fields are kept and written back unchanged.
- Package and applicationId: **owner decision**. Proposed: `com.solidsketchapp.steel` for now (a separate app); the merge then decides the final id.

## Sources of truth
- `reference/solidsketch_v69.html` (20,495,043 bytes, 17,999 lines). Do NOT read it whole. Search it with grep or Python.
  - **This copy is the owner's DEV copy:** the license check is skipped and the "DEV · license skipped" badge is shown. Use it only as a reference. Never put it in an APK.
- The steel code runs from `// ===================== v32 · Steel: Tekla-style steel modelling` to `ncZip`. It is organised in chapters with version headers: v32 modelling, v33 portal frame, v34–v35 connections, v36 numbering, v37 checks, v38 catalog, v39 drawings, v40 the Tekla catalog as family generators, v41 profiles, v42 girts/gables and the batch API, v43 crane runway, v44 stairs and railings, v45–v48 brace hardware, purlin laps and stub splices, v49–v58 review fixes, symbols, Tekla dialogs, bolt options and rule checks, v63 drawings + Document manager + NC/DSTV, v64 Tekla-import connection types TKCUT/TKBOLT, v65–v66 Tekla weights.
- Many functions are patched in place: a later chapter wraps an earlier one (`const prev = stConnGeom; stConnGeom = function…`), and there are numbered copies (`stConnGeom3…22`, `stOwner5…22`). Port the behaviour of the **final wrapped chain**, not the first definition. Record each chain in `docs/steel/LIVE-FUNCTIONS.md`.
- Key tables:
  - `ST_CAT` (profiles; v41 adds flats, parametric hollows, channels, cold-formed C/Z, built-up and tapered I)
  - `ST_SETS`, `ST_TYPE`, `ST_CLASS`
  - `TK`: **246 Tekla components in 17 groups**, built by 18 family generators (ST, GU, SD, WF, SP, GEN, EP, BP, CA, SE, TU, CP, PU, JO, OP, MF, HA, BE)
  - `TKD`: **10 components with full Tekla dialogs**: T144, Tcl, Tsag, Ttg, Ttgx, T146, T1042, T1004, T1014, T39
  - `CONN`, `ST_APPS`, `ST_LBL`, `ST_TAB`, `ST_CHK`
- UI look and wording: copy the exact CSS, text and icons from the web file. Do not make them up.
- Test data:
  - `tests/steel/models/Frame_28x53m_R10.ss` (6,638 bodies, 3,680 connections; came from a Tekla import, so its connections are `TKBOLT`/`TKCUT`)
  - `Frame_28x53m_R04_vs_Tekla.md`
  - the owner's workflow video

## Target level (from the owner's video and R10, web v68)
- Workflow: Steel mode, Grid in Tekla syntax (X: `0 4*13195`, Y: `0 5*5570`, Z: `0 7500 1500`), columns, rafters (eave +7500 → apex +9000), End plate 144 through the Tekla dialog (tabs Picture, Plates, Stiffeners, Haunch, Notch, Bolts, Holes, Angle box, General, Design, Welds; Save/Load presets; OK/Apply/Modify/Get/on-off/Pick parts), Modify, apex haunch, then the full frame with purlins, bracing and monitor.
- Document manager: 418 drawings (13 GA, 162 assembly, 243 single part). Actions: Create/update, Open, Print → PDF, PDF (one file), Export DWG/DXF, NC files (DSTV) → `model_NC_DSTV.zip` (DSTV_Profiles, DSTV_Plates, NC_dxf, with Tekla names), Full package, Drawing properties. Drawing viewer with Fit/PDF/DWG/Print/SVG/DXF.
- Result against the Tekla package: 162/162 assemblies, 243/243 parts, weight 63,722 kg (Tekla 63,711.7), NC/DSTV 99.6 %, GA 100 %.

## Licensing (hard rule)
- No GPL/LGPL/AGPL code. List every dependency with its license in `docs/LICENSES.md`.
- **The web build's DWG export uses LibreDWG (GPLv3) as WebAssembly. Do NOT port or bundle it, and do not read its source.** **Owner decision (2026-11-21): we write our own DWG writer.** It is a clean-room writer (`native/ssdwg`, C++) built only from the public Open Design Alliance DWG specification and our own tests. It writes DWG R2000 (AC1015), with only the entities our drawings use: LINE, LWPOLYLINE, CIRCLE, ARC, TEXT/MTEXT, DIMENSION as plain geometry, layers, linetypes and one model plus paper space. Each file must open in AutoCAD on the owner's PC, which is the acceptance check, and in a free viewer, and must match the DXF of the same sheet. Until it passes, DWG export stays behind a debug flag, and DXF (R12, as in `tkDXF`), PDF and SVG are the release outputs. The web build has the same GPL problem; it can switch to this writer later (compiled to WebAssembly).
- A test-only headless browser (Playwright, Apache-2.0) lives in `tools/` and never ships in the APK.
- License gate: the Steel app gets the same Lemon Squeezy gate as `CLAUDE.md` (`LicenseManager.kt`, same rules, same debug-only bypass rule). **Owner decision:** which Lemon Squeezy variants unlock Steel (the same Pro variants or a new Steel product).

## How we verify: golden files from the web build
The web build is the oracle. Every ported feature must give the same output.
1. `tools/golden/` runs v69 in headless Chromium (Playwright). Scripted cases use the steel batch API (v42) to place members and add components, then dump JSON: parts, plate outlines, holes, bolts, welds, cuts, marks, weights. They also dump drawing data (dimensions, tables) and the NC zip.
2. Dumps go to `tests/steel/golden/`. Native tests rebuild the same case and compare: 0.1 mm for positions, exact for counts, marks and NC text lines.
3. Each component gets at least three golden cases: a standard case, an odd profile, and a skewed or sloped one.

## Milestones (one at a time; working rules, session discipline and recovery exactly as in CLAUDE.md)
S0. New repo and app skeleton with the module split above; the build works; the license gate is in place; `docs/steel/FEATURES.md`, `LIVE-FUNCTIONS.md`, `MERGE.md`, `LICENSES.md`, `PROGRESS.md`.
S1. Golden harness, plus a dump of R10 totals (assemblies, parts, weights, bolts per size, drawing list).
S2. `core-view` + `sscore-lite`: viewport, free orbit, picking, undo/redo, Manifold booleans. Only what Steel needs.
S3. `core-io`: `.ss` load/save, both ways with web v69, every field kept. R10 must open and render (6,638 bodies at a usable frame rate on a mid-range phone; add a debug overlay for frame time and memory).
S4. Data: `ST_CAT` (with the v41 profiles), `ST_SETS`, `ST_CLASS`, materials and the `TK` catalog as JSON assets, with a test that they match the HTML.
S5. Steel mode: grid and levels (Tekla syntax), member placement with property sets (`stPropSheet`), profile solids following the Tekla position rules (plane, rotation, depth, offsets), class colours, Exit Steel.
S6. Fittings and cuts, `TKCUT`/`TKBOLT`, bolt groups with every Tekla bolt option (v58), holes, welds.
S7. Component framework: catalog sheet (17 groups, search by name or number, recently used), pick order, symbols (green/yellow/red cones, lines to the joined parts), Modify/Get/on-off/Apply, saved presets, rule checks per component.
S8. The 10 full Tekla dialogs (TKD), with the picture and input boxes: 144, 146, 1042, 1004, 1014, 39, purlin cleat, sag rod, tube gusset, tube crossing.
S9. Family generators for the other 236 catalog components (18 families), one family at a time.
S10. Applications: portal frame, girts/gables, purlins (laps, sleeves), bracing hardware, stub splices, crane runway, stairs and railings.
S11. Numbering (Tekla style, keeping existing marks), model checks, Tekla weights (net/gross, bolts excluded), steel list CSV, IFC4 export.
S12. Drawings: GA, assembly and single part sheets; Document manager; PDF (one file and per sheet), SVG, DXF; Full package; Drawing properties.
S12b. Own DWG writer (`native/ssdwg`, see Licensing): R2000, a byte-level structure test, an AutoCAD open check by the owner, and the same geometry as the DXF of each sheet.
S13. NC/DSTV: `ncExport`, DSTV_Profiles, DSTV_Plates, NC_dxf, with Tekla names. Byte-compare against the web build's zip.
S14. Acceptance on R10: open → save gives the same data; 162 assemblies and 243 parts; 63,722 kg; the same 418 drawings; NC equal to the web build (or every difference listed). Replay the video workflow in the app. Everything not verified on a device goes under NOT VERIFIED.

## Tekla parity fixes (standing owner rule, 2026-11-25)
Every connection fixed for Tekla parity follows `docs/steel/PARITY.md` → "Standing rule": (1) send 2–3 video links before coding and wait for the owner's .mp4; (2) fix in BOTH the web build (`web/v70_tekla_parity.js` chapter → `reference/solidsketch_v70.html`) and the app; (3) R10 golden 0.1 mm + emulator screenshots beside the video frames in `docs/steel/parity/<code>.png`; (4) deliver in one message: the v70 HTML and the APK from the same commit (`apk-phone` artifact, unzipped), both sent as files in the chat (the HTML never inside the APK), an emulator screen recording `<code>_recording.mp4` (30–60 s: place the case, add the component through its dialog, orbit front / end / top / 3D, change the parity option, Modify, orbit again) as a file, `<code>.png`, matched before → after; (5) ✓ in R10-REPORT.md only after the owner's "OK" from the phone. One connection at a time.
**Reference model since 2026-11-26: `tests/steel/models/Frame_28x53m_R12.ss`** = built from Tekla's own IFC export (`tests/steel/tekla/Frame_28x53m_tekla_v2.ifc`) by `tools/golden/r12_build.py` (docs/steel/R12.md). Reports (`docs/steel/R12-REPORT.md`), golden tests, rule B and the weight target (Tekla's per-part weights, 63,705.8 kg; Part List 63,711.7) use R12. R10 and R11 stay unchanged for history. Reference order, reverse-engineering method and delivery: docs/steel/PARITY.md.

## Who does what (Claude Code vs the local model)
- **Claude Code:** S0–S3, S5–S7, the first two TKD dialogs in S8 and the first family in S9 (so that the rest follow a proven pattern), the drawing engine in S12, and any golden failure whose cause is unclear.
- **Local model** (looping until the tests pass): S4 data export, the remaining TKD dialogs and families, CSV/IFC/NC writers against golden files, and icons. Each task gets one function or family from the HTML, its golden tests, and nothing from other plans. If it fails the same test after N loops, it goes back to Claude Code.

## Open items for the owner
1. applicationId for the separate Steel app (proposed `com.solidsketchapp.steel`), and which license variants unlock it.
2. (Decided 2026-11-21: own DWG writer.)
3. (Decided 2026-11-21: code lives in the private repo `ssas1qasd-boop/solidsketch-steel`.)
4. Tekla import from `.db1`: it is not in the HTML (R10 was made outside it). Keep it out of this plan, or add it later?
