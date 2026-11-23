# Steel progress

History: S0–S4 in `docs/steel/PROGRESS-S0-S4.md` (incl. the postponed license gate rules); S5–S6 in `docs/steel/PROGRESS-S5-S6.md` (incl. the S5 and S6 phone check lists).

## Current milestone
**S8 done; S9 in progress** (owner plan 2026-11-23: S7, S8 and S9 without waiting for phone checks, push after every step, stop before S10; Claude does all of S8 and S9). S8 APK: `dist/solidsketch-steel-s8-debug.apk` (0.8.0-s8, phone check below). S7 APK: `dist/solidsketch-steel-s7-debug.apk` (0.7.0-s7). S5 and S6 APKs: `dist/solidsketch-steel-s5-debug.apk`, `dist/solidsketch-steel-s6-debug.apk` (phone checks in `PROGRESS-S5-S6.md`).

## S7 plan (component framework)
1. Component engine: v69 `famGeom` context, the v58 geometry wrapper (bolt options reach `stBolts`, bolt info d0 / std / thr / sw, Welds tab), the live EP family (v48), component plates and bolts in regen (`tkKidSt` part fields, class colours, assembly owner), welds kept on the connection. **Done.**
2. Catalog sheet (18 groups / 252 items, search by name or number, recently used), pick order (TK_PICK prompts), component dialog with Modify / Get / on-off / Apply, saved presets. **Done.**
3. Symbols (cones green / yellow / red, lines to the joined parts) and the rule checks per component (`tkStatus`). **Done.**
Then the S7 APK and phone check list. **Done.**

## S8 plan (the 10 full Tekla dialogs and their families)
The dialogs themselves (Picture with input boxes, every tab and field, presets, on/off) are data from v69 since S7 step 2 (`tkSpec` → tk_ui.json) and the same dialog code shows all 10. S8 is the geometry behind them, compared with v69 per component (`tools/golden/comp_cases.js` + `CompGoldenTest`; at least 3 cases each: standard, odd profile, skewed or sloped):
1. ST family (Shear plate 146), Holes tab, the live owner chain, the generic component golden; v58 bolt options through End plate 144. **Done.**
2. BP (base plates 1042, 1004, 1014 and the other 10), CP (Seating 39 and the other 8), CL (purlin cleat), SR (sag rod), TG (tube gusset, tube crossing): one file per family (`conn/Fam<XX>.kt`), ported in parallel. **Done.**
3. Marks: Tekla marks come from numbering (S11); S8 checks the part fields marks are made from (Pos_No prefix / start, name, material, profile). **Done** (marks themselves: NOT VERIFIED until S11).
Then the S8 APK and phone check list. **Done.**

## S9 table (families; golden = `CompGoldenTest`, cases equal v69 / total; "known" = NOT VERIFIED)
| Family | Components done / total | Golden cases passed |
|---|---|---|
| EP | 16 / 16 | 80 / 80 (incl. 16 bolt-option cases) |
| ST | 35 / 35 | 122 / 122 |
| BP | 13 / 13 | 73 / 73 |
| CP | 9 / 9 | 41 / 41 |
| CL | 1 / 1 | 8 / 8 |
| SR | 1 / 1 | 7 / 7 |
| TG | 2 / 2 | 24 / 24 (incl. 9 node gusset) |
| CA | 11 / 11 | 45 / 48 (3 known) |
| BE | 2 / 2 | 10 / 10 |
| SE | 11 / 11 | 42 / 42 |
| JO | 6 / 6 | 27 / 27 |
| GU | 32 / 32 | 109 / 109 |
| SD | 26 / 26 | 136 / 136 |
| WF | 20 / 20 | 66 / 66 |
| SP | 19 / 19 | 64 / 64 |
| GEN | 0 / 17 | in progress |
| PU | 9 / 9 | 31 / 31 |
| TU | 7 / 7 | 22 / 22 |
| OP | 5 / 5 | 18 / 18 |
| MF | 0 / 4 | in progress |
| HA | 0 / 4 | in progress |
(Family counts are v69's live CONN: 250 components incl. the 4 S8 dialogs; the 3 tube components v69 moves to GU / WF are counted there.)

## S8 summary
- The 10 full Tekla dialogs (144, 146, 1042, 1004, 1014, 39, purlin cleat, sag rod, tube gusset, tube crossing): picture with input boxes, every tab and field from v69's own dialog specs (S7), and the geometry behind them. **Golden: 174/174 component cases equal v69** (End plate 144: 3 + 16 bolt options; Shear plate 146: 5; base plates 1042 18, 1004 7, 1014 9; Seating 39 10; purlin cleat 8; sag rod 7; tube gusset 9 + node gusset 9; tube crossing 6; and the other components of these families). Plates, holes, bolts (points in order, n, d, d0, L), welds, cuts, the part fields marks are made from, and the rule checks equal v69.
- The v58 bolt options and welds are verified through components (moved out of NOT VERIFIED).

## S8 phone check (owner) — `dist/solidsketch-steel-s8-debug.apk`
1. Start: the bottom line says `Steel 0.8.0-s8`. Everything in the S7 check still works.
2. Column HEA300 + beam IPE360 into it (as in S7) → **Components** → search `146` → Shear plate (146) → pick the column, then the beam: a shear tab with 3 bolts, the beam's end cut back and its flanges notched over the tab; green cone.
3. A second column alone → **Components** → `1042` → Base plate → pick the column: base plate PL25, 4 anchor bolts (M20, 4.6), grout. Open its dialog (tap the cone): change anchors to 8 and Bolts tab Oversized → Modify; the anchors and holes change as in v69.
4. `1014` Stiffened base plate on another column: base plate with flange stiffeners. `1004`: the same base plate dialog.
5. Two beams crossing on a column top → `39` Seating: cap / seating plate with bolts (dialog: plate size Lp / Wp, bolt positions).
6. A rafter (IPE360, sloped) and a purlin (C or Z) across it → `Purlin cleat L` (catalog group Purlins and cold rolled): an L cleat welded to the rafter, bolted to the purlin. Then `Sag rod at purlin` on a purlin and a rod (e.g. D16): nuts and washers both sides.
7. A column and a CHS brace ending at it → `Tongue plate tube end`: fin plate on the column, tongue plate slotted into the tube, end cap, 4 bolts. Add a second brace ending at the same place, same component: both share one gusset plate (v69's node gusset). `Tube crossing (tongues)`: a continuous brace and two halves.
8. For any of them: the dialog's tabs and fields are v69's; Modify / Get / on-off / presets work as in S7; the rule checks colour the cone.

## S8 step 1 (2026-11-23): ST family, Holes tab, owner chain, component golden, bolt options
- `conn/Fam.kt`: **ST** (v69 FAM.ST L14939, its only definition): shear tab(s) on the main part (one side, other side or two sided; shaped corners; through a tube / column), bolt rows fitted between the secondary's flanges (or full depth / up to the top flange), several bolt columns, the secondary's end cut back and its flanges notched over the tab, welded tee, column stiffeners / web stiffeners; v58 **Holes tab** (1, 2 or 4 sinkholes in the first plate from the first bolt group's centre); bolt groups recorded in order (v69 TKB_G). One file per family for the rest (`conn/Fam<XX>.kt`, a stub until ported: regen leaves components of unported families as the file has them).
- `conn/StRegen.kt`: the live `stOwner` chain (v40 family rules, v49 TG / CL and T133 / T135 stubs, v58 TG gussets) for every family.
- **Generic component golden**: `tools/golden/comp_cases.js <cases.js> <out.json>` runs cases `{component, members, q}` through v69 (`__st.connect`) and dumps every body (members after their cuts, plates, bolt groups: volume, box), plate fields, bolt groups, welds and the rule-check status; `CompGoldenTest` rebuilds each case through the app's framework and compares body by body in v69's order (`COMP_ONLY=<file>` runs one file; run with `--rerun`, Gradle does not see the JSON as an input); `known*.json` lists cases that cannot be matched (they are the NOT VERIFIED list); report `build/comp-golden-report.txt`.
- **Acceptance:** Shear plate 146 equals v69 in 5/5 cases (standard; HEM300 turned with IPE240, two sided; skewed 20° into a main beam IPE500; full depth with column stiffeners and 2 bolt columns; shaped with tee and gap 20). **v58 bolt options**: 16/16 cases through End plate 144 equal v69 (staggering 1–4, deleted bolts `2 5`, slotted, slotted rotated, oversized, tapped, no hole, no washers, no nuts, no bolt, bolting direction 2, length increase, tolerance 3 with M24).

## S8 step 2 (2026-11-23): BP, CP, CL, SR, TG families
- `conn/FamBP.kt`: the live FAM.BP chain (v54 L15797 → v46 L15466 bearing plate → v40 L15012): rectangular / round / box / tapered column base plates, anchors (2 / 4 / 6 / 8, gauges gx / gy), grout, cast plate, plate washers, stiffeners (flange / web / box, ts / hs), the bearing plate for a beam end (at the picked end, v69 `c.pt`), sloped and steep columns. **73/73 golden cases equal v69** (all 13 BP components; T1042 18, T1004 7, T1014 9 cases with their dialog options; Holes tab 1 / 2 / 4 holes; bolt options through the anchors: oversized, no washer / nut, length increase, slotted rotated, deleted anchors, tapped, bolting direction 2, thread / site / comment).
- `conn/FamCP.kt`, `FamCL.kt`, `FamSR.kt`, `FamTG.kt`: CP (v40 L14990 wrapped by L15890: cap plates, seating, crossing beams, tube seating, wind column; T39 plate size / bolt positions), CL purlin cleat (L15582), SR sag rod with washers and nuts (L15645), TG tongue plate tube end and tube crossing (L15612; stEnvelope, stHull2, stFaceToward). **71/71 golden cases equal v69** (T39 10, Tcl 8, Tsag 7, Ttg 9, Ttgx 6, the other CP components 3–6 each).
- Components now get the point picked on the first part (v69 `c.pt`; golden with picked points 2/2) and can see the other components in the document (v69 `S.stConns`, for the TG node gusset).
- v69 behaviours matched (noted for the owner): with an anchor gauge / grout / cast plate / plate washers set, v69's base plate makes no stiffeners; plate washers follow the full anchor layout even when anchors are deleted; Seating 39 with an empty bolt-position list puts 2 bolts on the centre line (JavaScript `String('').split → [0]`).

## S7 phone check (owner) — `dist/solidsketch-steel-s7-debug.apk`
1. Start: the bottom line says `Steel 0.7.0-s7`. Everything in the S6 check still works.
2. **Steel** → place a column (HEA300, grid point 1/A) and a beam into it (Beam, set Beam, from the column's side at +7000 to the next grid line), as in v69.
3. **Components**: the sheet "Applications & components" with a search box and 18 groups (Base plates 13, End plates 23, Shear tabs 32, …, Generators 5, Applications 2); each line has a picture, the name and the Tekla name (e.g. "End plate (144)") and a **Properties** button. Type `144`: End plate (144) and the others whose name or number contains 144 (same list as v69 with the same search).
4. Tap **End plate (144)**: the panel shows `End plate · Properties · Catalog · Done` and the hint `End plate (144): pick the main part`. Tap the column, then the beam: the end plate, its 8 bolts and the welds are made (toast "End plate created"; one Undo removes it). A green cone appears at the joint.
5. Tap the cone (no tool): the component is selected (yellow halo, dashed yellow lines to the column and the beam, green status line "End plate (144) #1: OK"), and its dialog opens: header with OK, tabs Picture · Plates · Stiffeners · Haunch · Notch · Bolts · Holes · Angle box · General · Design · Welds; red input boxes on the picture (1, 2, 3, t); the Bolts tab draws the real bolt pattern, numbered 1–8.
6. In the dialog: t = 15, Bolts tab box 4 = 4 columns, box 5 = `60 100 60` → **Modify**: the plate is PL15 and has 12 bolts (toast "1 component rebuilt"). Same values in v69's dialog give the same plate and bolts (checked on the build machine).
7. Bolts tab box 3 (side) = 15 → Modify: the cone turns yellow, the status says "Bolt edge distance of the bolts 1, … (M20) in END PLATE is too small: 15 mm < 30 mm (1.5·d)" — v69 says the same.
8. Presets: type a name next to **Save as**, Save as; close the app, open it again: the preset is in the list (Load brings the values back). **Recently used** at the top of the catalog shows the last 5 components picked.
9. A component whose family comes in S9 (e.g. Shear plate (146) before S8 is done) can be picked but is answered "could not be built for these parts" with a red cone (v69 makes it; the app does once its family is ported).

## S7 step 3 (2026-11-23): symbols and rule checks
- `steel/comp/TkCheck.kt`: v69 `tkStatus` (v58 L16034) exactly: for each bolt group, where each bolt passes through the component's plates (`tkPierce`: mesh vertices within the hole radius + 1.2 of the bolt line), the plate's outline in that plane (`tkSlice`), the edge distance to the nearest edge that is not a hole (rule 1.5·d), bolts that pass through no plate (error unless they all pass through a joined part), the smallest bolt spacing across the axis (2.2·d0); the first plate's thickness against every weld (a ≤ 0.7·t); and (deep) each plate's overlap with the joined parts by Manifold intersection (> 3 % of the plate, at least 125 mm³). Messages and rounding as v69 ("Bolt spacing is too small: 40 mm < 48 mm (2.2·d0, M20)"). Results are cached per component until the document changes (v69 `TK_ST`). `stSymPos` (at the joint: the secondary part's end on the main part, halfway when closer than 600 mm; a detail at its part's end) and `tkFanDraw` lines.
- Steel mode: v58 symbols — v69's cone texture in green / yellow / red, constant size (three.js sprite 0.026, fov 45°), one per 24 px cell with errors then warnings first, the selected one always; checks run 1.5 s after a change, 30 ms at a time (`tkCheckStep`), and recolour the cones. A tap on a cone (no tool) selects the component (yellow halo, dashed yellow lines to its parts, the status line `#tk-status` in green / yellow / red; tap it to clear) and opens its dialog with v69's toast "End plate (144) · connection 1"; a tap elsewhere clears the selection. The dialog header shows OK / Warning / Error and the messages.
- **Golden** `tests/steel/golden/s7/status.json` (`tools/golden/s7_status.js`): 8 cases on End plate 144: OK; side 15 (edge distance); pitch 40 (spacing); a = 10 (weld); e = −100 (4 bolts outside the plates, error); gap −5 (END PLATE overlaps COLUMN HEA300, 240 cm³); sloped rafter OK; sloped with side 20 and a = 9 (two warnings).
- **Acceptance:** `S7StatusTest` (Manifold): **all 8 equal v69: status, every message word for word, symbol position (0.1 mm)**. `S7PickTest`: pick order, prompts, "Pick a steel part", "Already picked", the component made on the last pick in one undo step named after it, "created" + toast, Done, then a tap on its symbol selects it, asks for its dialog and draws its 2 lines. App 0.7.0-s7 (versionCode 9). Unit tests: 164 runs, all pass.

## S7 step 1 (2026-11-23): component engine, End plate 144
- `steel/conn/Fam.kt`: `FamCtx` (v69 `famGeom`: q = CONN def + c.q; cut / plate / bolts / weld collectors), `Fam.DEF` (v69 FAM_DEF with the v58 EP additions), helpers `tkRows`, `v49Pitches`, `v58Span`, `stStiffPair`, `stLAngle`, and **EP** (live v48 definition: plate flush on the main part's face, rows / pitches / posV / refV, cols 2 or 4, gauge2, posH / refH, cope limits, shim, front / stub plate, compensating flange plates, seat angle, column stiffeners, two-sided with shared grip), then the v58 wrapper (bolt info `d0`, `std`, `thr`, `sw`, `cmt`, `blen`; Welds tab `q.W`). The component's settings reach `stBolts` here (v58 TKB_Q), unlike TKBOLT.
- `steel/conn/Joint.kt`: `J.u`, `J.Pc`, `tPrim`, `yHi` / `yLo`, `P`; `stPoly3`, `stFrame3`.
- `StRegen`: catalog components (`T<n>`) go to their family with v69's CONN def (FAM_DEF + the component's TK options); component plates are made as bodies with v69's `tkKidSt` fields (name / role / prof / mat / cls / asm / pp / ps / fin), colour 0x9aa0a6 or the General tab class colour (`TK_CLS`), assembly owner by v69's family rule (`stOwner`, v40); holes subtracted with Manifold; the connection keeps its welds (`len` rounded) as v69's `stRegen` does.
- **Acceptance:** `S7T144Test` (Manifold on the build machine): **End plate 144 equals the S1 golden cases 3/3** (standard; HEM300 turned with an IPE450, 3 rows, 12 mm stiffeners; sloped R10 rafter): every body's volume (1e-3) and box (0.1 mm), the bolt group (points in v69's order, n, L, d), the welds (name, a, len), the plates' name / prof / mat / cls / asm. Unit tests: 74, all pass.

## S7 step 2 (2026-11-23): catalog sheet, pick order, component dialog, presets
- **Data from the live v69 page** (`tools/golden/s7_catalog.js`, `--check` compares): `steel/src/main/assets/steel/tk_ui.json` = ST_CATALOG after every chapter (18 groups, 252 items: the 246 Tekla components, the 4 dialogs v49 adds — purlin cleat `Tcl`, tube gusset `Ttg`, tube crossing `Ttgx`, sag rod `Tsag` — and Numbering / Checks), the live CONN of each component (label, Tekla name, pick prompts, family, group, def), the live dialog spec `tkSpec(k)` (tabs, groups, fields; the 3 field accessors v69 uses — `neg`, `gaugeList`, `pitchList` — are matched by their source text and ported), the catalog icons (SS_ICONS), SVG_PIC, TKPG; and `thumbs/*.webp` (v58 SS_THUMBS: 536 pictures).
- **Found in v69:** three tube components change family after the catalog is built (`T102t`, `T103t` → GU; `Ttc` → WF with a new def, L15458/L15461). Regen now uses the live CONN (family and def) from tk_ui.json, not the raw TK table.
- `steel/comp/Components.kt`: v69 `stCatalog` groups with "Recently used" (5, newest first), search (`label tekla key` contains the text, sheet order), `stCatPrompt`, `stCQ` (CONN def + saved defaults), presets (`ss.connSets`), `stConnAdd` + regen as one undo step (label = the component name; `stNextConn`; `pt` = first picked point), dialog Modify (one or all of this type, locked ones skipped), Delete (plates, bolts, cuts), `stConnOK`. Storage = v69's localStorage keys and JSON (`ss.connDef`, `ss.connSets`, `ss.connRecent`) in the app's SharedPreferences, so presets and defaults survive a restart.
- `CompDialog`: the live v58 dialog's inputs in v69's order (picture boxes, parts table, field rows with Tekla on/off, welds table), `tkGet` / `tkSet` (incl. `P.<role>.<x>`, `W.<weld>.<x>`), `tkInput` rules (number with comma, `auto` fields), read / fill / Load / Save / Save as / Apply / Get / on-off / Modify exactly as v69 (Modify applies each ticked field over CONN def + the component's own q, `_` keys dropped).
- `steel/comp/CompUi.kt` + `SvgLite.kt` + `BoltPic.kt`: catalog sheet (v38 look: search box, groups, icon or thumbnail, name, Tekla name, Properties), the v58 dialog (header with status, Save / Load / preset / Save as / ignore other types / Welds… / Help, Tekla tabs, picture with red input boxes placed as v69, Bolts tab picture = `tkBoltPic` with the real bolt pattern, numbered, dimension chains), OK / Apply / Modify / Get / on/off / all of this type / Pick parts / Cancel / Delete. A small SVG renderer draws v69's icons, pictures and pictograms (all of them parse in the test).
- Steel panel: **Components** (v69 `stConnSheet`: a selected component plate opens its dialog, else the catalog); while picking: `<name> · Properties · Catalog · Done`; the hint line shows `End plate (144): pick the main part` …; taps on a member are picked (`Already picked`, `Pick a steel part`); toasts `End plate created` / `could not be built for these parts`. Generators (family GEN) and the Numbering / Checks entries say which later milestone brings them.
- **Golden** `tests/steel/golden/s7/dialog.json` (`tools/golden/s7_dialog.js`): v69's live dialog driven through its own DOM: 4 cases on End plate 144 (Modify with nothing changed; Modify with t, the negated boxes 1/2, 4 bolt columns with the gauge list `60 100 60`, edge distance, tolerance, an unticked row, the Welds table size, class; Modify with 4 rows and pitches `80 70 90`, the Parts table material / name, Holes; Apply then a new component), plus 10 catalog searches.
- **Acceptance:** `S7CatalogTest` (5): 18 groups, 252 items, every one of the 246 TK components with v69's label / family / group / pick prompts, search equals v69 for all 10 filters, recently used, presets, every dialog spec / accessor / icon / picture / pictogram / thumbnail loads. `S7DialogTest` (Manifold): **the app's dialog gives v69's settings (c.q, compared key by key), welds, plates and bolt group in all 4 cases** (mutation check: with the `neg` accessor broken the test fails). Unit tests: 160 runs (debug and release variants), all pass.

## S7 summary
- Catalog sheet (18 groups, 252 items: the 246 Tekla components + 4 dialogs + 2 applications), search by name or number, recently used, pick order, symbols (green / yellow / red cones, lines to the joined parts), the v58 dialog with Modify / Get / on-off / Apply / presets, rule checks per component — each compared with v69 (search 10/10, dialog 4/4, checks 8/8).
- Acceptance: 246 components in the catalog (`S7CatalogTest`); End plate 144 through the framework equals golden (`S7T144Test` 3/3, `S7DialogTest` 4/4).
- Only the EP family builds geometry so far; the other families come with S8 (the 10 dialogs' families) and S9. Until then those components are added and answered "could not be built" with a red cone.

## Large generated files (owner bundle)
Owner rule: big generated files do not go through the GitHub tool; they come in a bundle for the owner to push. Until they are on GitHub, the tests that read them fail there.
- From S4 (bundle `steel-s4-data.bundle`, sent 2026-11-22): `steel/src/main/assets/steel/profiles.json` (f822a133…76e5), `steel/src/main/assets/steel/tk_catalog.json` (dd1ec06b…ffe0), `tests/steel/golden/data/profile_cases.json` (94e2dba7…9205).
- From S8 (bundle `steel-s8-data.bundle`, branch `s8-data`): `tests/steel/golden/comp/s8_bp.json` (fe215e25…), `s8_cp_cl_sr_tg.json` (95cf9d84…), `s8_tgnode.json` (d2de31f1…) (v69 golden for the base plates, CP / CL / SR / TG and the node gusset; `CompGoldenTest` reads them).
- From S7 (bundle `steel-s7-data.bundle`, 841 KB, branch `s7-data`; fetch it and copy the files to main): `steel/src/main/assets/steel/tk_ui.json` (5d88a4c5…3b9b3f), `steel/src/main/assets/steel/thumbs/` (536 webp, tree aac3ba8e…b7f067). The app's catalog sheet and dialogs need them; `S7CatalogTest` / `S7DialogTest` read them.
- From S5 and S6 (bundle `steel-s5-s6-data.bundle`): `tests/steel/golden/s5/members.json` (c0f8bf65…44e9), `tests/steel/golden/s6/r10_regen.json` (9c2ef222…9e1c), `tests/steel/golden/s6/bolts.json` (4a223bcc…88d4), `tests/steel/golden/s6/fittings.json` (42d9dc7d…e227).

## How to build
Golden data: `node tools/golden/r10_totals.js` and `node tools/golden/run_cases.js` (needs the reference HTML and Playwright; see `tools/golden/README.md`).

Android SDK with platform 35, build-tools 35.0.0, NDK 30.0.16248370, CMake 4.1.2; `local.properties` with `sdk.dir`.
`./gradlew test assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
Manifold source: `./gradlew fetchManifold` (runs automatically before native builds; needs git and network once).
Unit tests with the real native code on the host: `./gradlew test -PhostJni` (needs cmake, ninja, a C++ compiler and a JDK).
Host native test: `tools/native/fetch_manifold.sh && cmake -S native/tests -B build/native-tests -G Ninja && cmake --build build/native-tests && ctest --test-dir build/native-tests`.
Shader check: `node tools/native/check_shaders.js`.
.ss both ways with v69: three steps in `tools/golden/README.md` (".ss both ways").
Data assets (S4): `node tools/golden/data_export.js` (write) and `--check` (compare with v69).
S5 golden (grid, member solids): `node tools/golden/s5_geometry.js` → `tests/steel/golden/s5/`.
S7 data and golden: `node tools/golden/s7_catalog.js` (tk_ui.json + thumbs; `--check`), `node tools/golden/s7_dialog.js` → `tests/steel/golden/s7/dialog.json`, `node tools/golden/s7_status.js` → `status.json`.
S6 golden: `node tools/golden/s6_cuts_bolts.js` (bolt / cut cases and R10 regenerated by v69, about 1 min) and `node tools/golden/s6_fittings.js` → `tests/steel/golden/s6/`. R10 acceptance: `./gradlew :app:testDebugUnitTest --tests '*S6R10RegenTest*' -PhostJni`.
Android modules: run one test class with `./gradlew :steel:testDebugUnitTest --tests '*StGridTest*'` (`:steel:test --tests` is refused by Gradle).
Note: Maven Central answered 429 (Too Many Requests) to parallel downloads in the cloud session; `--no-parallel` on the first build helped.

## NOT VERIFIED
- Clip angles two sided, skewed (T25, T33, T117: 3 cases): every body, bolt and weld equals v69; the rule check gives 2 extra "Bolt edge distance" warnings because the newer Manifold leaves more vertices on the hole edges, and v69's check (tkPierce) works on vertices.
- Marks (assembly / part marks) of component parts: they come from numbering (S11); S8 / S9 compare the fields they are made from (Pos_No prefix / start, name, material, profile), not the marks.
- Steel mode screen on a phone (built and unit-tested, not yet tried by the owner): tap targets, the sheets' scrolling, the labels' positions.
- Two-finger pan on the phone (not shown in the recordings; Latin digits and LTR were confirmed in the S3 check).
- S5 look on a phone: edge lines and v69's lights are written to v69's numbers and checked with one pixel in WebGL2, not compared side by side with v69 on the owner's phone. Edge cost on R10 on a phone (time until they appear, GPU memory) not measured.
- S6 on a phone: Modify on R10 parts with their cuts and bolt groups (built and tested on the build machine with Manifold, not tried on a phone).
- Welds: End plate welds (name, a, length, loop, Welds tab size / around) equal v69 (S7T144Test 3/3, S7DialogTest 4/4, the weld rule check S7StatusTest). The other families' welds, the weld list and `weldonly` are checked with their families (S8–S9).
- Fittings with copes (a beam into a main beam's web) are ported (`stJoint` cope) but none of the 3 golden cases has a cope.
- Edges in v69 also go through `smoothEdgeLines` (drops seams of smooth surfaces); not ported (the 12° rule already keeps smooth round tubes and bolts without side lines).

## Decisions for the owner to review
- Tube slotted hole (Ttsl) shows a red symbol in v69 every time (it only cuts, and v69's built check exempts welded / opening families but not tubes); the app matches v69 (status "could not be created").
- `Joints.box3` / `poly3` with the bounds reversed now extrude upward from the first bound as v69's extrudeRegions does (|height| from z0), found by a two-sided shear tab on an SHS stanchion (T87); every earlier case still equals v69.
- Tube gusset node: the shared gusset reads the other tongue braces' settings with a copy of v69's Ttg defaults in FamTG.kt (the family code has no catalog access); same values as v69 today.
Chosen while the owner was away (owner rule 2026-11-23: pick what matches v69, note it here, continue).
- Saved property sets ("Save as column", …) last for the session only; v69 keeps them in the browser's localStorage. S7 component presets and defaults are kept in the app's SharedPreferences with v69's keys and JSON (`ss.connDef`, `ss.connSets`, `ss.connRecent`); the part property sets can move there too.
- Component symbols are drawn over the model (v69 draws them in the scene with depth test, lifted 320 mm towards the camera, so steel in front can hide them). On a phone the cones stay visible behind steel; chosen so they can always be tapped.
- Component checks run for the components that have symbols (not for R10's TKBOLT / TKCUT, which v69 also checks in the background for its Checks sheet; that sheet is S11).
- Component picking uses the app's normal body pick: a component plate in front of a member is hit first and answered "Pick a steel part" (v69 casts its ray against members only). Rarely matters; noted.
- Grid level lines are solid (v69 dashes them); our overlay lines have no dash pattern yet.
- **Sloped fitting:** v69 builds the right cut box (equal to ours), but its Manifold leaves the sloped rafter uncut (the 120 m cut box loses precision in v69's older Manifold; the volume is unchanged to 12 digits). The native app makes the intended cut (9.2 mm). Matching v69 here would mean copying a precision failure, so the app cuts; the straight cases equal v69.
- After Modify, connection parts are rebuilt only for the types ported in S6 (TKCUT, TKBOLT, fitting); other connection types keep their bodies as the file has them until S7–S9.
- `clean()` on load (S3 question): not done. v69 cleans every body it loads; the app keeps the file's geometry, and Modify rebuilds a part fresh from its st (as v69's regen does), so the result equals v69 either way (R10 acceptance above).
- The edges of a big model are computed in the background and appear a few seconds after the parts (v69 computes them when the body is made). Chosen so that opening R10 stays fast.

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
