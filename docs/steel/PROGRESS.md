# Steel progress

## Current milestone
S3 done and accepted on the owner's phone (2026-11-22). Now: S4 (data: `ST_CAT`, `ST_SETS`, `ST_CLASS`, materials, `TK` catalog as JSON assets), started on the owner's instruction (CLAUDE.md had assigned S4 to the local model).

## S3 accepted on the phone (owner, 2026-11-22, screen recordings)
1. **Save / Open / New** work on the phone: a beam with a cut hole saved as `9d.ss` reopens with the hole; selection and Undo are cleared after Open.
2. **R10 on the phone:** opens in **5.2 s**; 6,638 bodies, 1.83 M triangles, **5 draw calls**; frame CPU 0.1–20 ms while orbiting (**about 50+ fps**); **GPU 35 MB, heap 46–73 / 512 MB, native 17 MB**; bolts, plates, holes and cuts visible up close; no crash.
3. **The app-saved file opens in v69** (Chrome on the phone): 3 bodies with the cut hole, all shown correctly.
- The owner again saw a saved `doc` with only `bodies`. That is the first S3 build's behaviour; s3b and later write all 24 v69 doc fields. Re-checked on the current code (2026-11-22): a new app file has `v`, `nextId`, `colorIdx`, `sketchLines`, `sketchPlane`, `paths`, `nextPathId`, `profiles`, `nextProfileId`, `bodies`, `folders`, `notes28`, `refModels28`, `furn28`, `stGrid`, `stGen`, `stConns`, `stNextConn`, `stNum`, `stDwg`, `curType`, `curTag`, `unit`, `kept`; the golden round trip (v69 opens the app file, adds a body, saves; the app reopens it: no error, ids 1–3 unique, all bodies) passes 3/3. To tell builds apart, the app now shows its version in the bottom line ("Steel 0.3.4-s3"; versionCode 4). APK: `dist/solidsketch-steel-s3d-debug.apk`.

## S3 (2026-11-22)
- **`.ss` codec** (`core-io/SsCodec.kt`): streaming reader (never holds the whole file as text): `shape.vp` / `shape.tv` (93 % of R10's 62 MB of JSON) go straight into float / int arrays; every other value is kept as its exact JSON text, so unknown fields round-trip at every level (top level, `doc`, body, `st`). Writer puts the raw text back and writes `vp` the way JavaScript prints Float32 values (`JsNumber`, ECMA-262 Number::toString, done with BigDecimal so it does not depend on the platform's `Double.toString`). Gzip on save (as v69); gzip or plain JSON on load; refuses non-SolidSketch files and newer versions with v69's messages.
  - Bodies made in this app get a numeric `id` first (v69 needs it) and `nextId` is kept ahead of every id.
  - `MemoryDocument` now keeps the file header and the field order (`header`, `headerDocIndex`, `bodiesIndex`), `nextBodyId`, `replaceAllFrom` (swap in a document read off the UI thread / copy one to save off it). `UndoStack.clear()` after opening a file.
- **Results on R10:** load → save is **byte-identical** to the original JSON (except `savedAt`); 6,638 bodies, 1,825,326 triangles, 910,719 vertices. JVM on the build machine: load 0.9–1.3 s, save 2.1–4.4 s.
- **Both ways with v69** (`tools/golden/ss_interop.js` + `SsInteropTest`, see `tools/golden/README.md`):
  - v69 opens the native resave of R10 with the golden totals (6,638 bodies, 162 assemblies, 243 part marks, 63,722 kg, 418 drawings, 3,680 connections).
  - v69 opens a native edit (body 1 deleted, a box added): box id 8297, `nextId` 8298, weight 63,722 → 63,618 kg (body 1 = 103.89 kg; totals are whole kg).
  - v69 opens a file made only in the app (2 bodies, exact volumes).
  - Native reads the file v69 saved: read → write gives v69's exact bytes; every body and doc field has the same JSON value as the original.
- **Found:**
  - R10 was written by Python, not by v69: 769 numbers are in Python's form (e.g. `1e-06`); v69 rewrites them as `0.000001` when it saves. Native keeps whatever text the file has.
  - v69 runs `clean()` on every body it loads ("seam scraps removed"), so a v69 save changes geometry: 668 of 1,693 bolt groups (volume up to 1.8 %, overlapping bolt solids) and 110 members re-triangulated (volume ≤ 7.4e-5); plates unchanged. Native keeps the file's geometry. Bolts are not in the weights, so totals do not move. Whether native should also clean on load: decide in S6 (bolts).
- **Renderer for big models:** bodies are packed into chunks (`gl/BatchBuilder`, 16-byte vertices with colour, up to 196,608 vertices per chunk, per-body index ranges); an edit rebuilds only its chunk; the selection is drawn again from its range with the v69 emissive. R10 = 5 chunks, 34.8 MB of GPU buffers, packed in 94 ms (JVM). `largeHeap` on.
- **Debug overlay** (debug builds, bottom left): CPU frame time, frame rate while frames keep coming, draw calls, triangles, bodies, chunks, GPU buffer MB, Java heap used/max, native heap.
- **`:app`:** Open / Save (`.ss` through the system file picker), status text with v69's wording ("Opened R10.ss · 6638 bodies" plus triangles and seconds); the S2 checks stay.
- **Tests:** 38 JVM + 3 host JNI = 41 unit tests (new: JsNumber 3, SsCodec 5 incl. the R10 byte round trip and unknown fields everywhere, SsInterop 2, BatchBuilder 2, R10 render data 1, replaceAllFrom 1, UndoStack.clear 1), shader check, native host test. All pass.

## S3 fixes from the owner's check (2026-11-22)
The owner saved a file from the phone ("Unttitled.ss"): header and body shapes matched v69, but `doc` had only `bodies` (R10 has 24 doc fields).
1. **New documents write every v69 doc field with v69's defaults** (`MemoryDocument.V69_DEFAULTS`, from v69's initial state and `docObject` + `ssDocument`): `v` 1, `nextId`, `colorIdx`, `sketchLines` [], `sketchPlane` null, `paths` [], `nextPathId` 1, `profiles` [], `nextProfileId` 1, `bodies`, `folders` [], `notes28` [], `refModels28` [], `furn28` [], `stGrid` null, `stGen` null, `stConns` [], `stNextConn` 1, `stNum` null, `stDwg` null, `curType` null, `curTag` "", `unit` "mm", `kept` [] — same order as v69. `nextId` stays above every body id; `colorIdx` goes up with every new body, like v69's `makeBodyRecord`. Opened files keep every field, known or unknown (unchanged since S3; now also tested against R10's 24 names). The header `name` is the file name without `.ss`, like v69 (`ssBase(ssFileName)`).
2. **Golden test, app file through v69 and back** (`tools/golden/ss_interop.js` step "v69 adds a body to the app-saved file" + `SsInteropTest.reopenAppFileAfterV69AddedABody`): v69 opens the app-saved file (24 doc fields), adds a column with its batch API (`COLUMN 3`, id 3, `nextId` 4) and saves; the app reopens it with no error, ids 1, 2, 3 unique, all bodies with meshes, v69's new folder kept, the next app body gets id 4, and app read → write gives v69's bytes back.
3. **Buttons stay on one line:** all toolbar buttons go through `ToolButton` (`maxLines = 1`, `softWrap = false`, tighter padding); button rows scroll sideways instead of wrapping. `UiRulesTest` fails if a screen uses a raw Button.
4. **File name spelling:** the code's default is `Untitled.ss` (spelled right). "Unttitled" was the name typed in the save dialog; the app then suggests the last saved name, and Android adds " (1)" when that file exists. Naming moved to `SsNames` (tested, including that case).
- Unit tests: 42 JVM + 3 host JNI = 45, all pass. APK: `dist/solidsketch-steel-s3b-debug.apk`.

## S3 owner phone check 2 (2026-11-22, recordings of the first S3 build)
- Works: Save through the Android file picker (default name "Untitled.ss"; "Unttitled"/"Uted" were typed); Open ("Opened Uted.ss · 3 bodies · 0.0 s"); Undo cleared after Open; Latin digits, LTR, buttons on one line; 55–60 fps with 3 bodies; Cut hole.
- "doc has only bodies" and the v69 round trip: already fixed in the s3b build (GitHub `fdba1a6`), which the recordings did not use; re-checked on the current code: the app-saved file has all 24 doc fields, v69 adds a body to it and the app reopens it (3/3 interop tests).
- **Fixed: the selection stayed after Open** ("Selected: BEAM (2)" and the highlight): the new file had a body 2 too, and the selection only dropped ids that disappeared. `DocumentSession` (app) now clears the selection and highlight, then replaces the document and clears undo, for Open and for the new **New** button (empty document with v69's default fields). `DocumentSessionTest` (2 tests): without the clear, 1 of 2 fails; with it, both pass.
- Unit tests: 44 JVM + 3 host JNI = 47, all pass. APK: `dist/solidsketch-steel-s3c-debug.apk`.

## S3 phone check (owner) — done 2026-11-22, see "S3 accepted on the phone"
Copy `tests/steel/models/Frame_28x53m_R10.ss` to the phone, install `dist/solidsketch-steel-s3c-debug.apk`, then:
1. **Open time:** Open → R10. The status line ends with the seconds it took ("Opened Frame_28x53m_R10.ss · 6638 bodies · 1.83 M triangles · N s"). Note N, and whether all 6,638 bodies show (overlay "6638 bodies · 5 chunks").
2. **Frame rate:** orbit with one finger for about 5 s and read the overlay: fps and "frame … ms cpu". Usable: about 30 fps or more while orbiting. Also pinch-zoom close to a node and pan (two fingers).
3. **Memory:** read "GPU … MB · heap used/max MB · native … MB" after opening (expect GPU about 35 MB). Open R10 a second time and check that heap and native do not keep growing.
4. **Picking:** tap a plate and a bolt: the name shows and the part lights up. Delete one, Undo.
5. **Save and reopen:** Save as a new name, Open it again: same body count. Optionally open that file in web v69.
6. Leave the app and come back (home button, then reopen): the model is still drawn (GL context restore).

## S2 (2026-11-22)
- **Booleans:** `native/sscore-lite` now runs on Manifold v3.5.4 (Apache-2.0, pinned commit `ce50d780`; CrossSection/Clipper2, TBB, tests and bindings off). C API: box, extrude (either winding), cylinder, subtract / union / intersect, `ss_subtract_many`, `ss_union_many`, closed-solid check, volume. Flat-shaded input is welded first. Manifold is fetched by the Gradle task `:fetchManifold` (or `tools/native/fetch_manifold.sh`) into `third_party/manifold` (git-ignored); both native modules depend on it.
  - v69 bundles manifold-3d ~2.3–2.4 (has `hull`/`levelSet`, no `setTolerance`/`minGap`); native uses 3.5.4. Volumes and boxes agree within the golden tolerances; triangle layouts can differ, so golden tests must not compare vertex counts.
- **`:core-api`:** `DocumentApi` carries meshes (`bodyMesh`, `addBody(json, mesh)`, `replaceBody(id, json, mesh)`). Noted in `MERGE.md`.
- **`:core-io`:** `MemoryDocument` (bodies as immutable values, cheap snapshots for undo; ids from a counter that undo restores).
- **`:core-view`:**
  - `OrbitCamera`: perspective, quaternion orbit about the camera's own axes: unlimited turns in every direction, no clamping (owner rule); pan, zoom, fit, `screenRay`, `worldToScreen`.
  - `Picker` (AABB, then Moller-Trumbore on every triangle; nearest hit), `CoreSelection` (`SelectionApi`), `GestureTracker` (1 finger orbit, 2 fingers pan + pinch, tap pick, double tap fit).
  - `ViewportController` (`ViewportApi`, document → renderer and picker sync, named line overlays), `ViewportView` (GLSurfaceView, ES 3.0), `gl/SceneRenderer` (flat shading from screen-space derivatives; clear colour `0xeef1f5` and selection emissive `0x6b4a10` from v69).
  - `NativeCore` JNI and `ManifoldBooleanEngine` (`BooleanEngine`), `MeshOps` (rigid transforms, volume).
- **`:app`:** S2 check screen: HEA300 column, IPE360 beam, 20 mm end plate with 8 M20 holes cut by Manifold; Fit / Undo / Redo / Cut hole (boolean on the selected part) / Delete; grid overlay.
- **Tests:** 20 JVM unit tests (camera incl. 10 full turns each way and the half turn upside down, picking, gestures, controller, document, undo with the document) + 3 host JNI tests that run the real JNI glue and Manifold on the build machine (`./gradlew test -PhostJni`); 1 native host test binary (`native/tests`, 15 checks: exact boolean volumes, plate with 4 holes in one pass, CW/CCW extrude, welded flat-shaded input, open mesh refused). `tools/native/check_shaders.js` compiles and links the GLSL ES 3.00 shaders in WebGL2 and draws a pixel. All pass.
- APK: 24 MB debug; each JNI library is about 3.6 MB per ABI because each statically links Manifold (`sssteel` too). Worth trimming later (one shared core library, or `--gc-sections`).

## S2 verified on a phone (owner, 2026-11-22)
Samsung, Arabic locale, two screen recordings of `solidsketch-steel-s2-debug.apk`: the scene and the plate's 8 holes render (Manifold works on ARM); free orbit and pinch zoom work; tap selects (BEAM (2), COLUMN (1), highlight shows); Cut hole cuts a real hole in the beam; Delete; Undo brings the column back; Redo; no crash. Pan was not shown.

## Locale fix (2026-11-22, from the S2 phone check)
- Found on the phone: numbers showed in Arabic-Indic digits ("Cut: ٣٧٧٤٧٦١٩ → … mm³") and the layout was still RTL.
- Fix: every number goes through `Fmt.f` (`:core-api`, `String.format(Locale.ROOT, …)`); both `.format` calls in `MainActivity` replaced. LTR forced at the app root: `android:supportsRtl="false"` in the manifest and `LocalLayoutDirection provides LayoutDirection.Ltr` around the Compose content. This also covers the two S0 "Notes for S5".
- Test `app/src/test/.../LocaleTest.kt` runs with the default locale set to `ar-EG`: `Fmt.f` gives ASCII digits (a guard first checks that plain `.format` really gives Arabic-Indic digits there), a source scan fails on any `.format(` / `String.format(` in `src/main` without `Locale.ROOT`, and the manifest / root LTR settings are checked. Against the old code 2 of the 3 tests fail; with the fix all pass.
- Rule from now on: format numbers only with `Fmt.f` (or `Locale.ROOT`); the source-scan test enforces it.
- Unit tests now: 23 JVM + 3 host JNI = 26, all pass. APK: `dist/solidsketch-steel-s2b-debug.apk`.

## S1 (2026-11-22)
- `tests/steel/models/Frame_28x53m_R10.ss` added (from the owner; 10.6 MB, sha256 16dc3dcd…ee36). The workflow video is not in the repo.
- Golden harness `tools/golden/` (Playwright 1.63.0, headless Chromium, offline): `r10_totals.js`, `run_cases.js`, `lib/v69.js` (serve + open + load .ss), `lib/dump.js` (page-side dumper), `cases/t144.js`. Details in `tools/golden/README.md`.
- **R10 totals from v69** (`tests/steel/golden/r10/`): 6,638 bodies, 3,680 connections (3,035 TKBOLT, 645 TKCUT); **162 assemblies, 243 part marks, weight 63,722 kg** (net 63,697, gross 64,750); 5,376 bolts in 1,693 groups (per size in `bolts.json`); **418 drawings (13 GA, 162 assembly, 243 single part)**; NC 482 files (81 DSTV_Profiles, 160 DSTV_Plates, 241 NC_dxf), no warnings. All headline numbers equal the CLAUDE.md targets.
- First golden cases, End plate 144: `t144_standard` (HEA300 + IPE360 level), `t144_odd_profile` (HEM300 rot Top + IPE450 into the flange, 3 rows, 12 mm stiffeners → 4 stiffeners), `t144_sloped` (R10 eave-to-apex rafter). Each dumps parts, holes, bolts, welds, cut solids, marks, weights, drawings with data, NC.
- Runs are deterministic (two runs byte-identical, for R10 and cases).
- Found: four stale `__st` exports (`connGeom`, `build`, `portalParts`, `dwg.holes`) that call early links of their chains. The harness serves the page with one added line exposing live wrappers (`__st.live.*`); R10 output is identical with and without it. Noted in `LIVE-FUNCTIONS.md`.
- Native side does not read the golden files yet; that starts with S3 (R10 load) and S5/S6 (parts).
- `tests/steel/models/Frame_28x53m_R10.ss` and `tests/steel/golden/**` (515 files) were pushed by the owner from a bundle (GitHub `e1dd149`, `tests/` tree `4fa9377`, identical to the harness output).
- `tests/steel/models/Frame_28x53m_R04_vs_Tekla.md` added (owner, 2026-11-22): connection-by-connection comparison of R04 (v56h) against the Tekla package, with model totals and open differences. Use it as the checklist for S6–S12.

## S0 (2026-11-21)
- Repo created; `CLAUDE.md` (the plan) pushed.
- `docs/steel/FEATURES.md`: inventory of v69 (31 chapters, 500 profiles, 246 Tekla components in 17 groups, 10 full dialogs, outputs).
- `docs/steel/LIVE-FUNCTIONS.md`: 24 patched function chains with line numbers (`stConnGeom` has 23 steps, `stOwner` 17, `stChecks` 9…).
- `docs/steel/MERGE.md`: the interfaces `:steel` may use, so that the later merge is a swap of core modules.
- `docs/LICENSES.md`: hard rule; LibreDWG and OpenCASCADE excluded; dependency table filled for S0.
- `tools/inventory/`: scripts that regenerate the two inventories from the HTML.
- `reference/solidsketch_v69.html` placed locally (20,495,043 bytes, 17,999 lines, checked). `reference/` is git-ignored.
- **App skeleton**, toolchain as in the main port (AGP 8.7.3, Gradle 8.10.2 wrapper, Kotlin 2.0.21, Compose BOM 2024.12.01, compile/target 35, minSdk 24, NDK 30.0.16248370, CMake 4.1.2, arm64-v8a + x86_64):
  - `:core-api` (pure Kotlin): `DocumentApi`, `ViewportApi`, `SelectionApi`, `UndoApi`, `BooleanEngine`, `FileApi`, `LicenseApi`, plus `Vec3`, `Ray`, `BodyId`, `Mesh`.
  - `:core-view`: `UndoStack` (snapshot undo, nested groups merge; 3 unit tests), `NativeCore` JNI → `libsscore_lite_jni.so`.
  - `:core-io`: `SsFile` gzip layer of `.ss` (1 unit test). Document model is S3.
  - `:steel`: depends on `:core-api` only; `SteelHost`, placeholder `SteelScreen`, `NativeSteel` JNI → `libsssteel_jni.so`.
  - `:app`: `com.solidsketchapp.steel` (temporary id), `MainActivity` wiring, `LicenseGate` (always allows).
  - `native/sscore-lite` (static; C API `sscore_lite.h`: box, volume work; extrude/booleans return `SS_ERR_NOT_IMPLEMENTED` until S2), `native/sssteel` (static, links sscore-lite via `SSCORE_DIR`), `native/ssdwg` (placeholder README for S12b), `native/tests` (host smoke test, passes).
- `./gradlew test assembleDebug` passes; APK copied to `dist/solidsketch-steel-s0-debug.apk` (dist/ is git-ignored). `assembleRelease` also built (unsigned) before the gate was postponed.

## Verified on a phone (owner, 2026-11-22)
- `dist/solidsketch-steel-s0-debug.apk` installed on the owner's phone: the app starts without a crash, both native libraries load (`sssteel 0.0.1` links `sscore-lite 0.0.1`), and the JNI call returns box 10×20×30 volume = 6000.0.

## Notes for S5
- ~~The owner's phone is set to Arabic, so the layout flipped to RTL. Force LTR for the whole app.~~ Done in the locale fix (2026-11-22).
- "Plan:" on the placeholder screen shows "-" (empty value). (The placeholder screen is no longer shown since S2.)
- The model draws over the buttons and text when zoomed in: give the top bar (buttons and status lines) a solid background (owner, S3 phone check).

## Postponed: license gate (owner decision, 2026-11-21)
`:app` has only `LicenseGate` (`app/src/main/java/com/solidsketchapp/steel/license/LicenseGate.kt`), an interface that always allows, and `OpenLicenseGate`. No network permission, no key UI.

When it comes back, the rules given by the owner (from the main port's CLAUDE.md), checked against `<script id="ss-license-gate">` in v69 (line 79 of the HTML; values match):
- API `https://api.lemonsqueezy.com/v1/licenses/`, POST form-encoded, `Accept: application/json`, 12 s timeout, no API key. `activate` (license_key, instance_name), `validate` (license_key, instance_id), `deactivate` (license_key, instance_id).
- Allowed `meta.variant_id`: 2224132 (Yearly), 2224187 (Monthly), in ONE list. **TODO(owner): which variants unlock Steel.** Valid key of another product → deactivate the new instance, refuse "This key is not for SolidSketch Pro."
- Record (key, instance id, last successful check, plan) in encrypted storage. Device name "Android " + 6 random chars.
- Start: no record → lock screen. Record → "Checking your license…", validate. Valid + allowed → update time, start. Not valid → delete record, lock with: expired "Your subscription has ended. Renew it to keep using SolidSketch."; disabled "This license key has been disabled. Contact support if you think this is a mistake."; else API error or "This license is no longer active." Network failure → start if last check < 7 days, else "Could not reach the license server. Connect to the internet and try again."
- While open: re-validate if last check > 6 h (look every 30 min and on return to foreground). Invalid → delete, lock. Offline → keep working until 7-day grace ends.
- Lock screen buy links (from the v69 gate script): Monthly $4.99 `https://solidsketch.lemonsqueezy.com/checkout/buy/900d7ea4-d7a7-4e41-b821-799d6c94393f?enabled=2224187`, Yearly $34.99 `https://solidsketch.lemonsqueezy.com/checkout/buy/0c0cde4e-72e9-40f8-854a-c4d752364db7?enabled=2224132`, `https://solidsketchapp.com/#pricing`. Lock-screen CSS is `#ss-lock` in the same script. The web text "The browser version is for Pro members…" needs app wording (TODO(owner)).
- Debug-only bypass in the debug source set, `BuildConfig.DEBUG` only, red "DEBUG – license skipped" banner, a way to run the real gate; `scripts/verify_release_no_bypass.sh`; unit tests with a fake HTTP layer (activate, validate, grace, wrong variant).
- A started (untested, not compiled) port of these rules is on the branch `license-gate-draft` under `drafts/license/` (pushed 2026-11-22).

## Next step
S4 (in progress): `ST_CAT` (with the v41 profiles), `ST_SETS`, `ST_CLASS`, materials and the `TK` catalog as JSON assets, with a test that they match the HTML.

## How to build
Golden data: `node tools/golden/r10_totals.js` and `node tools/golden/run_cases.js` (needs the reference HTML and Playwright; see `tools/golden/README.md`).

Android SDK with platform 35, build-tools 35.0.0, NDK 30.0.16248370, CMake 4.1.2; `local.properties` with `sdk.dir`.
`./gradlew test assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
Manifold source: `./gradlew fetchManifold` (runs automatically before native builds; needs git and network once).
Unit tests with the real native code on the host: `./gradlew test -PhostJni` (needs cmake, ninja, a C++ compiler and a JDK).
Host native test: `tools/native/fetch_manifold.sh && cmake -S native/tests -B build/native-tests -G Ninja && cmake --build build/native-tests && ctest --test-dir build/native-tests`.
Shader check: `node tools/native/check_shaders.js`.
.ss both ways with v69: three steps in `tools/golden/README.md` (".ss both ways").
Note: Maven Central answered 429 (Too Many Requests) to parallel downloads in the cloud session; `--no-parallel` on the first build helped.

## NOT VERIFIED
- UI look: the placeholder screens are not the web build's UI (that starts in S5).
- Two-finger pan on the phone (not shown in the recordings; Latin digits and LTR were confirmed in the S3 check).
- Shading is a simple headlight, not three.js's lighting; edge lines (`0x262b34`) are not drawn yet. Both are look items for S5.

## Decisions
- Separate app until the merge (owner, 2026-11-21).
- Own DWG writer, clean-room, R2000 (owner, 2026-11-21).
- Repo: `ssas1qasd-boop/solidsketch-steel` (owner, 2026-11-21).
- License gate postponed; always-allow `LicenseGate` for now (owner, 2026-11-21).
- `sscore-lite` and `sssteel` are static libraries; each JNI .so links its own copy, so modules never clash on shared-library names (S0).
- Manifold 3.5.4 (pinned, fetched at build time, not vendored) rather than the web's ~2.3–2.4 (S2).

## Open (owner)
- applicationId (proposed `com.solidsketchapp.steel`).
- License gate: when to bring it back, and which variants unlock Steel (TODO(owner)).
- Tekla `.db1` import: in or out of this plan.
- The workflow video (R10 and the R04 comparison arrived 2026-11-22).
