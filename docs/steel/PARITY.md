# Tekla parity: web v70 chapter and app, against Tekla (R13)

Each section covers one connection where v69 differs from Tekla (shown by `tests/steel/models/Frame_28x53m_R10.ss` and Tekla's help). It gives the rule, the proof, the change and the tests. The web change is the chapter **v70 · Tekla parity** (`web/v70_tekla_parity.js`). It goes into the reference copy just before the `v28 · BIM layers` chapter: `python3 tools/web/build_v70.py` writes `reference/solidsketch_v70.html`, and the golden harness (`tools/golden/lib/v69.js`) adds the same text in memory, so every golden made from now on comes from v69 + v70 (`SS_ORACLE=v69` runs plain v69). The app port follows the same steps in the same order. Models saved before v70 keep their v69 result unless the new option is set.

## Reference model: R13 (owner, 2026-11-28)
`tests/steel/models/Frame_28x53m_R13.ss` is the reference for everything: golden tests (`tests/steel/golden/r13/`), `docs/steel/R13-REPORT.md` (the rebuild against R13), rule B and the weight target. It is built by `tools/golden/r12_build.py R13` from Tekla's IFC export of the fixed model (Package_2 `out.ifc` = `tests/steel/tekla/Frame_28x53m_tekla_v3.ifc`). `docs/steel/R13.md` says how; `docs/steel/R13-PACKAGE.md` compares it with R12 and checks it against Package_2's lists. R10, R11 and R12 stay unchanged, for history (their tools run with `SS_MODEL=R12` etc.).

**Weight target:** Tekla's Part List total, **64,023.4 kg** (Package_2 `SingleList.xsr`). Tekla's per-part weights (`st.tkW`) sum to 64,017.49 kg (the same 5.9 kg rounding gap as R12).

**What R13 changed (owner, 2026-11-28):** the 3 mezzanine girders at C/D/E 5-6 have their web FLT8*300 (mark 4518, EB4007, 103.89 kg each), 6 new bolt groups 8 x M16 L45 into the column shear plates (now drilled), 6 new welds; the bottom flanges joined EB4007. The C20 stiffeners and C12 shear plates were already in Tekla. Nothing else changed.

## Open (owner decisions, 2026-11-27 / 28)
- **C06:** the gable / end-frame nodes re-done on R13 (agent TUBES, section C06 "Gable / end-frame nodes"): 11 of the 17 gussets and every plate, end plate and bolt there now match at 0.1 mm; open: the main part edge of the 6 strut-led gussets (0.126 mm), and 8 lead plates' bolts with 1.8-2.3 mm air (Tekla's own geometry, rule B).
- **C07:** the 18 PL5 end plates at the upper ends of VL6011-VL6014 match (they were the gable nodes' turned plates, see C06 "Gable / end-frame nodes"; not IFC rounding).
- **C12 / C20 / C14 on R13:** agent R13 (branch r13-conn).
- **Row 18 counter plates, SGR9001 / SR9001–SR9004 assembly weights:** agent C18 (branch c18).
- **Data quirk (R12 and R13):** one monitor rafter end plate (R12 body 1985, PLATE FLT12*120, assembly mark '14', class 99) is not in a PR assembly, so its 144 is not rebuilt and PR7001 / one PC7001 keep a square end (box 36.8 mm, +0.4 kg).
- **GA drawings (S12):** Package_2 has 16 GA sheets, the web build's fixed list makes 13. Accepted for now (owner 2026-11-28); the 3 extra sheets are open for S12.
- **Assembly weights per mark:** 1001, SGR9001 and SR9001–SR9004 differ from Package_2's assembly list (the same differences as R12 vs Package_1): the known C01 / C18 / C19 gaps.
- **Rule B on the app's R13 rebuild (baseline `tools/ci/ruleB_r13_baseline.txt`, 68 after merging agent/tubes):** railing / stanchion overlaps 26 and stanchion / stringer 22 (rows 18 / 19: the members placed as Tekla has them; R13 itself has the same railing overlaps); 20 bolts at the 18 gable / end-frame tube nodes with 2.3–3.3 mm air between the turned plates on the bolt line (C06: the plates match R13 within 0.1 mm, so the gap is Tekla's; R13's own check flags 16 bolts there, grip 26.7 vs stack 24.0). The golden case `t20g_r13_gable_vl_lead` (R13 node 3419) has 2 bolts with 2.3 mm air (rule B fails); R13's own bolts there have 1.8 mm (grip 25.8 vs stack 24, within its own +-2 mm), so the case is 0.5 mm more open than Tekla. Not on `ruleB_known.txt` (no owner OK; the proof did not hold): open.
- **Rule B on R13 itself (Tekla's model), new since R12:** each new EB4007 web runs 3.94 mm into the column web of C2021 / C2024 (3 overlaps, 18,176 mm³ each), and the 3 new bolt groups' grip is 18 against 8 mm of material on the bolt line (+3 grip failures). Both are in Tekla's model as exported; shown in `docs/steel/parity/r13/` (emulator images).

## Integration rule for parallel agents (owner, 2026-11-28)
1. **Own worktree, own branch, own section.** Each agent works only in its own worktree and branch (`agent/<code>`). It writes its v70 fix in its own section of `web/v70_tekla_parity.js`, headed `// == <code> ==`, or in a separate file `web/v70/<code>.js`. `tools/web/build_v70.py` and the golden harness (`tools/golden/lib/v69.js`) append `web/v70/*.js` after the chapter, in name order. An agent never edits another agent's section. Shared code changes (core, catalog, rule checks) go to the main session first.
2. **Report to the main session, not the owner.** When an agent finishes, it reports to the main session: branch, commit, what changed, and which goldens it added. All goldens must be green on its branch.
3. **The main session merges one branch at a time.** After EACH merge it runs the FULL test suite: all component goldens, the two-video goldens, R13 (weights, assemblies, drawings), and rule B. Anything broken is fixed before the next merge. Merge conflicts are resolved by keeping both fixes, never by dropping one.
4. **One combined delivery, from one commit on main,** all sent as files:
   - ONE APK (apk-phone, unzipped);
   - ONE `solidsketch_v70.html`;
   - each connection's files (.ss × 2, recordings × 2, PNG × 2, VIDEO.txt × 2) in one zip, `parity_<date>.zip`, with one folder per connection;
   - ONE report, `docs/steel/parity/DELIVERY_<date>.md`: a table with one row per connection (status, loops, rule B, goldens), plus the R13 totals before → after.
5. **When to deliver:** when at least 2 connections are ready, or once a day, whichever comes first. No separate APKs from individual agents.

6. **main is merge-only (owner, 2026-11-28).** No rebase, reset --hard or force push on main, by the main session or any agent. Agents rebase only their own branches. If a git command stops halfway, it is aborted and the owner is told.
7. **`tools/ci/ruleB_known.txt` needs the owner's OK (owner, 2026-11-28).** No new entry without it. Each proposed entry is shown to the owner with the case, the count and the proof that R13 has the same finding, and waits.

**Parked for later (owner, 2026-11-28; not worked on now):** the 6 C06 gussets at 0.126 mm (the 0.1 mm tolerance is NOT widened); EB4007 (a known Tekla error, fixed later in Tekla with an R14); the phone checks.

## Group 1 Tekla parity: the two-video method (owner standing rule, 2026-11-28)
**Scope:** Group 1 is End plates (23), Shear tabs (32) and Clip angles (10): 65 components (`tk_catalog.json`).
- **Order:** End plates first, then Shear tabs, then Clip angles. Within a group the most used components go first. T144, T146 and T14 already match R13, but still go through the two-video test.
- **One component at a time.** Groups 2–4 come later, the same way.

For EACH component:
1. **Two reference videos.**
   - Find 2 different Tekla videos for the component number: different channels or different examples. Search the Tekla support site first, then the YouTube Data API.
   - Analyse each video with Gemini in two passes, at high resolution on the dialog frames. Use Apify only as a fallback.
   - Write `VIDEO_1.txt` and `VIDEO_2.txt`: URL, timestamps, the profiles and their positions, EVERY dialog value tab by tab, and the result (plates and their sizes, bolt size, count and positions, holes, cuts, welds, dimensions).
   - If fewer than 2 usable videos exist, use the example on the Tekla help page as the second reference and say so in the report. Never invent values.
2. **Settings check (video 1).** Compare the app's dialog with the video's dialog, tab by tab: options, defaults, labels, units. Fix every difference in BOTH `web/v70_tekla_parity.js` (`reference/solidsketch_v70.html`) and the app.
3. **Rebuild video 1's example in the app.** Use the same profiles, angles, offsets and dialog values, and compare the result with the video part by part.
   - Rule B: contact ≤ 1 mm; no floating part; an overlap 0.01 mm deep or more fails; every bolt goes through a hole; grip = plate stack ± 2 mm.
   - If anything differs, fix it and rebuild; repeat until it matches.
   - If it still differs after 5 loops, stop and report the cause to the owner.
4. **Video 2.** Repeat steps 2–3 on the second video; fixing video 2 must not break video 1. Both examples become golden regression tests, alongside the existing std / odd / skew goldens and the R13 golden. All goldens stay green.
5. **Final check on the emulator** (GitHub Actions CI): build each example through the real dialog, and orbit front / end / top / 3D.
6. **Deliver in ONE message, everything as files in the chat.** The APK and the HTML come from the same commit; the HTML is never inside the APK.
   - the APK (apk-phone artifact, unzipped) and `solidsketch_v70.html`;
   - `<code>_v1.ss` and `<code>_v2.ss` (the SolidSketch models each example was built in);
   - `<code>_v1_recording.mp4` and `<code>_v2_recording.mp4` (emulator, 30–60 s each);
   - `<code>_v1.png` and `<code>_v2.png`: video frame | app, the same view, labelled, with dimensions;
   - `VIDEO_1.txt` and `VIDEO_2.txt`;
   - a short report: the settings differences found and fixed, the number of loops, rule-B results, golden status, before → after.
7. **Acceptance.** A component is "accepted (pending phone OK)" when it matches both videos. After delivery, go straight to the next component without waiting for a reply. The owner gives the ✓ in R12-REPORT.md from the phone. If the owner reports a problem, that component is fixed first.

**Time tracking (owner, 2026-11-28).**
1. **One row per component** in `docs/steel/parity/TIMES.md`: code, family, agent, start (UTC), end (UTC), hours, wait hours, videos found (0/1/2), loops, blocked by. "Blocked by" is what cost the most time (no video, engine change, CI queue, rule B...).
2. **Only real work counts:** from picking the videos to the component being "accepted (pending phone OK)". Waiting (session paused, usage limits, CI queue) goes in "wait hours", not in "hours".
3. **Every combined delivery report** carries the TIMES.md rows of the components it delivers.
4. **Forecast, after the first 5 accepted components:** the delivery report adds average hours per component (first-of-family vs. rest-of-family), components left, agents running, and the estimated finish date for Group 1. It is updated in every delivery after that.

**How this runs with the integration rule:** one agent per component, in its own worktree and branch `agent/g1-<code>`. It reports to the main session (integration rule step 2). The main session merges it, runs `tools/ci/full_suite.sh`, runs CI on main, and sends that component's delivery (step 6) from the merge commit. Then the next component's agent starts.

## Weights (owner, 2026-11-26)
**Target:** Tekla's per-part weights (`st.tkW`, 63,705.8 kg; Part List 63,711.7).

**Proof** (Tekla's IFC, `docs/steel/WEIGHTS-R12.md`): every part's Tekla weight is its faceted solid × density, with bolt holes not subtracted.
- Tekla's circles are inscribed polygons. ROD12 / ROD16 / ROD20 are 8-gons (101.82 / 181.02 / 282.84 mm²). CHS48*3 and PD40*10 are 12-gons (405.0 / 900.0 mm²). PD50*5 is a 16-gon (688.83 mm²).
- SHS100*3 has square corners (1,164 mm²).
- Grating density is 1,333 kg/m³; grout is 0.
- The CC purlin is weighed by its section (704 mm² → 5.5264 kg/m).

**Rule (web v70 section "Weights", app `steel/.../model/TeklaWeight.kt`, the same steps):**
- Base: the part's solid without bolt holes.
- Round and square-hollow parametric members: × Tekla's section area ÷ the part's own section area. The own area is measured on the solid: the median of five 10 mm slabs at 20–80 % of the length, which skips a crossing slot.
- Catalog profiles: catalog kg/m × length (v66).

**What v66 got wrong:** it scaled round sections by a fixed table taken from R10 (ROD16 188.4, PD40*10 942.5), so an imported part with Tekla's polygons was scaled twice.

**Result on R12:** web 63,710.3 kg, app 63,711.1 kg, every part web = app within 0.01 kg (`R12WeightTest`). The segment counts are proven only for the sizes in this model.

### Owner's hypothesis tested (2026-11-26): "Tekla's weight is the gross weight (kg/m × length, or the plate's bounding size), with no bolt holes and no chamfers subtracted"
Tested on all 4,945 R12 parts with `st.tkW` (plate bounding size from each part's NC header: length × width × t). **It does not hold:**
- the gross rule matches 2,160 of the 3,405 plates it can be computed for, and none of the 357 parts the current rule misses; it would make the total 65,098 kg against Tekla's 63,706;
- Tekla **does subtract** chamfers, clips and cuts. For example, the C13 Stiff. plate FLT10*50 is 0.22 gross against Tekla's 0.21, and C09's haunch plate is 0.22 / 0.21 gross against Tekla's 0.17. Tekla does **not** subtract bolt holes: the C13 end plate is 6.48 without holes and 6.24 with them.

**The current rule (the solid without bolt holes):**
- 4,588 parts match exactly;
- 353 more are within ±0.01 kg, in both directions: 192 washers PD40*10, C09's haunch plates 0.18 vs 0.17, C13's Stiff. plates 0.20 vs 0.21, gussets. This is Tekla's internal rounding of the same solid, and gross does not explain it;
- the real misses are 4 stair stringers UPN240 (+0.40 / +0.43 kg): our UPN240 kg/m against Tekla's section (open).

**Real gap found (the cause of C13's 6.24 vs 6.48):** the rule fills bolt holes back only for parts drilled by imported TKBOLT groups (v66 `solidNoHoles`). A part drilled by a component's own bolts (the end plate a 144 makes, a column flange a 144 drills) is weighed with its holes, so a model built in the app is weighed light by the holes' weight. **Proposed fix (owner's OK first):** a component records each part's solid before its bolt holes (web `plate()` / app `FamCtx.plate(minus = holes)` already keep them apart), and the weight uses that solid.

### Gross rule (owner, 2026-11-27): no bolt holes taken off, everywhere
- **Rule** (web v70 "Weights" `holeFill70`, app `TeklaWeight.holeFill`, the same steps): imported TKBOLT holes as before (the part rebuilt without them). A component's own bolt holes (its plates and the parts it drills) are filled back: per bolt, from the head point along the shank, if the core (r0 − 0.5) finds no material the part has the hole; its thickness t is measured in a ring r0 + 0.3 … r0 + 1.3 round it (32-gons), and the fill is t × the outer circle's area less the material inside that circle (the hole as drilled). Catalog members weighed as kg/m × length are unchanged (holes never counted); a catalog part with no length (the C04 cleat angle) is weighed by its solid and filled.
- **Grout weighs 0** (Tekla's material GROUT): components name it GROUT on S275, which weighed 1,131.76 kg in the rebuild (`docs/steel/WEIGHT-ANALYSIS.md` D1, agent 2's analysis).
- **Not the bounding size:** chamfers, clips and cuts stay subtracted, as Tekla does (the hypothesis test above; WEIGHT-ANALYSIS.md: a full gross would put R12 1,445 kg over Tekla).
- **Result on R12's rebuild:** every one of the 1,061 R12 parts the rebuild matches now weighs what Tekla says (3,559.64 vs 3,559.28 kg, per-part rounding; before: 15 groups light by their holes, −83.4 kg). Total 62,516.10 kg (web) / 62,517.22 kg (app, every part within 0.01 kg of web, `R12RebuildTest`) against Tekla's parts 63,705.8 kg (Part List 63,711.7): −1,190 kg, all of it parts the rebuild does not make yet or makes differently (anchor rods, EB4006 / EB4008 flanges, tube gussets C05–C07, built-ups X01, stairs C18; WEIGHT-ANALYSIS.md). Before the change: 63,495.66 kg, which looked close only because the grout's +1,132 kg hid the missing parts.
- R12 itself (imported, TKBOLT): unchanged, `R12WeightTest`.

## Reference order (owner, 2026-11-26)
When sources disagree, the higher one wins:
1. **Tekla itself:** R12 (Tekla's IFC) and Tekla's dialog settings.
2. **The Tekla package:** drawings, NC files, bolt list and reports (`tests/steel/tekla/Frame_28mx53m_Package_1.zip`).
3. **A YouTube video of the component:**
   - frames via the Apify actor `emttn/frameskim`;
   - timestamps and dialog values via the Gemini API, given the YouTube URL;
   - Gemini's values are cross-checked with the frames before use.

   `APIFY_TOKEN` and `GEMINI_API_KEY` are set in the environment. Never print them, never write them to a file or a log.

## Reverse-engineering method, per connection (owner, 2026-11-26; step 0 added 2026-11-26 with C13)
0. **Tekla's own site first:** search support.tekla.com / help.trimble.com for the component and the tab in question (help page, picture captions, tips, release notes, the Tekla Discussion forum, Tekla User Assistance videos or animations). Cite every page used in the connection's section. Tekla's own material, including its videos, comes before YouTube. Also check which Tekla component can make the parts at all: a part name can belong to several components (C13: Top / Bottom NS / FS are both 144's and 186's names).
1. **Starting settings:** the video's dialog values.
2. **Target:** every instance of the connection in R12.
3. **Search** the dialog inputs in code (headless web oracle / app over JNI) until the build reproduces **all** instances within 0.1 mm. That covers connection parts AND member bodies, with 0 rule-B failures.
4. **Several settings fit:** prefer the video's or Tekla's default.
5. **Check** the settings on 2–3 other member sizes or slopes.
6. **Fix** in BOTH web v70 (`web/v70_tekla_parity.js` → `reference/solidsketch_v70.html`) and the app. Old models keep their saved result.
7. **Verify on the emulator through the real dialog**, as a user would: place the case, add the component through its dialog, change an option, Modify. The images and the recording sent to the owner come from that final emulator run.

## Delivery after each connection (owner, 2026-11-25 / 2026-11-26)
Sent as files in the chat, in one message:
- the APK (CI artifact `apk-phone`, unzipped; version and sha256);
- `reference/solidsketch_v70.html` from the same commit (the owner's DEV copy, never inside the APK);
- `Frame_28x53m_R12.ss`, if it changed;
- the comparison images (orthographic, labelled, R12 | Tekla drawing | app);
- the emulator recording `<code>_recording.mp4`: 30–60 s, made by `<Code>RecordingTest` in its own pass of `tools/ci/emulator_run.sh`.

Mark ✓ in `docs/steel/R12-REPORT.md` only after the owner's "OK" from the phone. One connection at a time.

## Standing rule B: measured checks, never images (owner, 2026-11-25; rule 3 amended 2026-11-26)
**Never mark a connection OK from looking at images.** Images are for the owner; the checks are measured. Every connection case gets automatic geometry checks (`python3 tools/golden/geom_checks.py <model.ss | case dump>`), and the case fails on any of them:
1. **Contact:** parts that should bear on each other (purlin on rafter, plate on flange, cleat on web) have a gap ≤ 1 mm. The gap is measured perpendicular to the contact face (Manifold `min_gap` between the solids; the per-type pairs are in `DESIGNED`).
2. **No floating part:** every part touches (≤ 1 mm) or is bolted / welded to another part of its connection. In an .ss model: of its own assembly, its main part included.
3. **No overlap:** two parts must not intersect.
   - An intersection with a **mean depth under 0.01 mm** counts as contact.
   - Mean depth = 2 × volume ÷ surface area of the intersection solid, i.e. the slab's thickness.
   - Such shallow overlaps come from float32 storage of faces in contact 20–50 m from the origin.
   - Any deeper intersection fails, whatever its volume.
   - The check runs within a connection, between plates of two connections at one joint, and in an .ss model between any two parts of the model.
4. **Bolts:** each bolt passes through a hole in every part it joins, and its grip equals the plate stack ± 2 mm.
   - For an .ss TKBOLT: `q.grip` against the plate thicknesses measured beside the hole.
   - For a component bolt group: no air along the shank between the plates it passes.
5. **Images:** orthographic views (not perspective), large, one view per image, with the key dimensions written on them. The app has an orthographic camera (`OrbitCamera.orthographic`).

The checks run on the connection's own case(s) and on all R12 connections; failures are reported in a table. A check tool that finds nothing has to be shown failing on a known-bad case first (C04: plain v69's upright purlins, 26 failures).

## Earlier rule (2026-11-25, replaced by the sections above)
The first version of the standing rule (send 2–3 video links and wait for the owner's .mp4; R10 / R11 as reference) is replaced by the reference order, the method and the delivery above.

## R11 = R10 with Tekla-proven data fixes (owner 2026-11-25)
`tests/steel/models/Frame_28x53m_R11.ss` is built by `python3 tools/golden/r11_build.py` from R10 (R10 stays unchanged). The source is Tekla's own package `tests/steel/tekla/Frame_28mx53m_Package_1.zip` (drawings, NC, reports, `Tekla Model.db1`), which is reference data only and never goes in the APK. Each fix is one function in the script and one commit. From now on R11 is the reference (golden tests, `R11-REPORT.md`, weight target).

### Fix 1 · C04 purlin cleats (264 × 8503 L140*60*6, 264 × 8504 PL6*44, 440 bolt groups = 1,056 M12)
- **Tekla bolt list** (`Reports/Bolt List (+Weight).xsr`): `4.6XOX Site BOLT 12X30 1056`. An M12×30 clamps about 8–15 mm, which fits the cleat (6) against the purlin web (2). R10 had L144 and 58–72 mm of air inside the C.
- **R3022 assembly drawing** (front view, sections B-B … F-F): the short leg lies on the rafter flange (weld 5) and the tall leg stands against the purlin web (2 bolt rows, axis along the rafter). The stiffener 8504 sits in the cleat's corner, at right angles to the purlin, on the rafter web line. The holes are 50 / 60 from the flange's inner face (= 40 / 100 above the rafter top) and 40 / 70 from the flange edge (= ±35 from the rafter web). R3023 / R3001 give the cleat ends as 57 / 63 from the stiffener's faces, so the cleat is centred on the stiffener (= on the rafter web).
- **Which way the short leg points:** away from the purlin web. On R3022 (purlin toes downhill) that is toward the apex; on R3001 (toes uphill) it is toward the eave. Both drawings match.
- **Single parts and NC:** 8503 `DSTV_Profiles/8503.nc1`: L140*60*6 L120, holes 4 × Ø14 in the 140 leg at x 25 / 95, y 40 / 100. 8504 `DSTV_Plates/8504.nc1`: PL6, outline (0,0) (114,0) (124,10) (124,44) (114,44) (0,10), i.e. 124 up the tall leg, 44 along the short leg, a 10 × 10 snip at the heel and 10 wide at the top.
- **R10 → R11:** the angle moved from the toe line (60 mm from the web, upside down) to the web's back face, with the heel on the rafter top. The stiffener was in the web's plane, turned 90°, and is now across the purlin in the corner with the NC outline. The bolts start at the cleat's outer face and point toward the web: M12 4.6 L30, grip 8. The bolt lines (heights, positions along the purlin), the purlins and their web holes, and the cleats' positions along the purlins are R10's, which already agree with the drawings.
- **Check:** `python3 tools/golden/bolt_hole_check.py --model tests/steel/models/Frame_28x53m_R11.ss`. All 1,056 C04 bolts pass through holes in the cleat and the purlin web with no air (R10: 58–72 mm). The only air left is at the stair brackets (130 mm, fix 2).

### Fix 2 · Stair stringers and tread brackets (12 × UPN240 9507–9518, 46 × L60*6 9501 / 9502, 46 bolt groups = 92 M12)
- **Tekla bolt list:** `8.8XOX Site BOLT 12X35 92`, which clamps about 15 mm: bracket leg 6 + UPN240 web 9.5. R10 had L169, grip 145 and 130 mm of air.
- **SGR9001 assembly drawing (the tread):** tread 9503 PL10*336 L990 with brackets 9501 / 9502 at its ends. 5 + 995 = **1000 between the outer faces of the brackets' bolted legs**, so those faces sit on the stringer webs, and the brackets' other legs run under the tread (end view). NC 9501 / 9502: holes Ø14 in one leg (y 30); NC 9507: holes in the UPN web.
- **R10:** the two stringers' inner edges are exactly 1000 apart (x 608 / 1608), but those edges are the **flange tips**: the webs are on the far side, 85 mm out. The brackets' bolted legs are inboard (54 mm from the stringer edge). With R10's stringers, no bracket position gives both the 1000 and a 15.5 grip. So R10 mirrored both the stringers and the brackets.
- **R11:** each stringer (and the 8 short UPN240 end pieces of the same assemblies SR9001–SR9004) is reflected about its own centre plane across the bolt axis, so the web faces the treads and the flanges point out. Each bracket is reflected about its own centre, so the bolted leg lies on the web. Holes, ends and lengths move with the meshes. Bolts: head at the bracket leg's inner face, axis toward the web, M12 8.8 L35, grip 15.5.
- **Check:** bolt check on R11: no bolt with air along its shank (R10: 130 mm at the stairs); hole results unchanged.

### Checked with the package, not fixed (yet)
- **Node gussets** (176 bolt × gusset failures: marks 6507, 6514, 6515, 6521, 6522, 6523, 6525, 6533, 6534, 6536, 6550, 6554). Proven wrong in R10: on every failing instance R10's gusset outline equals its NC outline in the plate's own frame, but the brace bolts do not pass the NC holes. There is an exact in-plane rigid move (some mirrored) that puts all NC holes on the bolts with 0.0 mm residual, so the gusset, not the brace, is misplaced in its plane. **Not fixed:** the 2 × 2 hole groups are symmetric, giving 4–8 exact moves per plate, and the weld / contact test could not pick one (these gussets have no part of their own assembly next to them). The node drawings (GA "Frame Connections", the HZ / VL / C / R assemblies) are needed to choose each move. Next step after C04, with the owner's go.
- **Stair plate 9505** (2 holes-only anchor bolts, connections 316 / 317): the plate has its NC holes (40 / 110 × 70 / 190); 2 of its 4 anchor points are 124 mm off the plate along the stair. Not decided which (plate or anchors) is wrong; not fixed.

## C17 · Shear plate (146) as a hanger under a sloped rafter: R12's gable column tops (owner 2026-11-27)

### Step 0: Tekla's own site
- Shear plate (146) (conn_146_help, as C14): the tab is welded to the main part and bolted to the secondary's web; Bolts tab vertical measurement (Top / Middle / Below), horizontal edge distance; Plates tab tab size. R12's gable column tops are 146 with the rafter as the main part and the gable column as the secondary: the tab hangs from the rafter's underside, bolted to the column web.
- Tekla's own videos: none for 146 as a hanger (C14's video MrhM4X9opLo shows the Bolts tab only).

### Step 1: videos — C14's (tests/steel/videos/C14), the Bolts tab ("Middle" / "Top"); no hanger values from it (VIDEO.txt).

### Step 2: the Tekla package
- Assembly drawings EG3501 / EG3502: part 3001 FLT10*120 x 192 (Tekla's part length is the tab's longest edge: 190 from the uphill face + the slope over the tab and web); the rafter drawings list the same plate in the rafter's assembly. The bolts (4 M16 8.8 L40 to the gable column web) are R12's.

### R12 (16 gable column tops: 14 tabs in the rafters' assemblies, 2 as their own assemblies EG3501 / EG3502)
- Tab FLT10*120 against the gable column's web, **centred on the rafter** (Vertical measurement Middle, 35 from the column's middle), **190 long**, its top **cut along the rafter's sloped underside**.
- Everything along the column is measured from the rafter's underside **at the column web's uphill face** (both sides of the web: 7 tabs uphill, 7 downhill): tab length 190, column top 49.056 below (the gap), first bolt row 49.056 + 40.949 below.
- 4 M16 8.8 L40 (Tekla's length: grip 16), heads on the tab, 2 x 2 at 60 / 60.
- v69's 146 measured from the face at the column's axis with a flat tab top, centred the tab on the column (35 off) and used L50.

### Change, in web v70 (section "C17") and the app (`Fam.st`)
- 146 Plates tab: **Main part face** = the secondary web's uphill face (a hanger under a sloped part): the tab and bolts measured from there, the tab ends on the main part's face plane. 'Tab plate b' sets the tab length. Old models (v69) unchanged.

### Search result: the R12 inputs (`r10_inputs.py`)
- hang face, refV Middle, posV 34.994, tb 190, gap 49.056, eh 40.949, blrule tekla at all 16 (the two EG hangers find their rafter right above them).

### Checks
| Check | Before (C03, 907edf2) | After |
|---|---|---|
| Row 17 R12 parts (web rebuild, 0.1 mm) | 0 / 16 (14 at 35 mm, 2 missing) | 16 / 16 |
| R12 bolts matched by position (web rebuild) | 4,390 / 4,548 | 4,454 / 4,548 (+64) |
| App vs R12 (`R12C17Test`, 0.1 mm) | — | 16 / 16 hangers; 28.56 kg = Tekla 28.56 |
| Rule B | — | 0 failures: R12 uphill (R3002), downhill (R3008), EG3502, IPE300 on HEA200 at 15 deg |
| Weight (rebuild, web) | 63,102.54 kg | 63,105.97 kg (Tekla 63,705.8): +3.4 (the two EG hangers) |

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C03 · Apex haunch as Tekla 106: haunch dimensions, plumb flange ends, end plate stiffeners above the rafter (owner 2026-11-27)

### Step 0: Tekla's own site
- Apex haunch (106) (conn_106_help): an apex end plate connection with haunches under both rafters and stiffeners. As in C02h's eaves haunch (102), Tekla's haunch length is horizontal and its depth plumb.
- Tekla's own videos: none found.

### Step 1: videos — none read (no 106 video found that shows the Haunch / Stiffeners tabs; noted in VIDEO.txt).

### Step 2: the Tekla package
- Assembly drawing R3008: 3501 FLT20*400 (end plate), 3515 FLT8*130 L2029 (the haunch web, its length along the rafter = 2000 horizontal + the plate), 3516 FLT10*150 L2016 (the haunch flange), 2504 Stiff. x 4.
- Single-part drawing 2504: **FLT10*60, length 88.0**, 48 pieces, 0.3 kg: the stiffener's 88 is Tekla's own dimension (the height at its far end).

### R12 (24 apexes, 192 parts)
- **Haunch web:** the triangle under the rafter from the end plate's back face to **2000 horizontal from the apex joint**, **83.373 deep plumb** at the plate.
- **Haunch flange** 10 x 150 under the web's lower edge, **both ends plumb** (v54's ends were square to the flange).
- **End plate stiffeners FLT10*60** (2 per apex): in the rafter's web plane, behind each end plate where it stands 70 above the rafter top: 60 long (horizontal), from the rafter top to the plate top, the far top corner chamfered 30 x 30; at the far end 88 high (0.24 above the rafter's line: Tekla's stiffener height).
- v54's apex (len along the rafter, depth square to it, from R10's rounded 2029 / 82) put the haunch 1.5 mm off and made no stiffeners.

### Change, in web v70 (section "C03") and the app (`ClassicV34.apex54`, `FamHA`)
- apex options: **Haunch dimensions** = Tekla (length horizontal from the apex, depth plumb at the plate; flange ends plumb); **end plate stiffeners** (length, chamfer, thickness, height at the far end). Old models (v69 rule, no stiffeners) unchanged.
- **Apex haunch (106)** from the catalog now has a Tekla dialog (Picture, Plates, Haunch, Stiffeners, Bolts, General, Welds); with a plate height it builds the same apex (`t106_r12_r3008_catalog` = `t106_r12_r3008`).

### Search result: the R12 inputs (`r10_inputs.py`)
- hrule tekla, len 2000, depth 83.373 (measured on each haunch web at the plate face), ast 1, asl 60, ach 30, att 10, ash 87.988 (measured on each stiffener): the same at all 24 apexes.

### Checks
| Check | Before (C18, c5d9df5) | After |
|---|---|---|
| Row 3 R12 parts (web rebuild, 0.1 mm) | 48 / 192 (end plates; 96 haunch parts 1.5 mm off, 48 stiffeners missing) | 192 / 192 |
| App vs R12 (`R12C03Test`, 0.1 mm) | — | end plates 48 / 48, webs 48 / 48, flanges 48 / 48, stiffeners 48 / 48; 1,885.92 kg = Tekla 1,885.92 |
| Rule B | — | 0 failures: R12 R3008, IPE400 10 deg defaults, HEA300 5 deg without stiffeners, and the catalog route |
| Weight (rebuild, web) | 63,086.22 kg | 63,102.54 kg (Tekla 63,705.8): +16.3 (the stiffeners) |

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C18 · Stairs as Tekla: U-pan grating treads on bolted brackets, stringer mitre fits, stair end plates (owner 2026-11-27)

### Step 0: Tekla's own site
- [Stairs (S71)](https://support.tekla.com/doc/tekla-structures/2025/macro_s71_help): "U pan profile steps with brackets"; the steps are placed from the nosing points of the first and last step; **Bracket tab**: bracket length, "the bracket offset dimension from the center line of the step", bracket-to-step connection (bolted by default, welded, no connection, plate bracket, holes only) and **bracket-to-stringer connection** (bolt size, standard, tolerance, group dimensions, bolt direction, exact length). Step type Catalogue step ignores the U-pan / bracket tabs.
- So R12's treads are S71's U-pan steps (a bent PL10 channel, material Grating) on L60*6 brackets bolted to the stringer webs (no bracket-to-step bolts in R12). The stringer pieces' end plates are not S71's: no Tekla component for them is in the IFC.

### Step 1: videos — none read for C18 (no Tekla video found for S71's U-pan steps; noted in VIDEO.txt).

### R12 (2 flights, 23 treads, 46 brackets, 10 stair plates, 12 stringer pieces)
- **Treads:** PL10 bent channel 300 x 40 (legs down, inner bend radius 5 in 3 chords; developed 335.7), 990 long = 5 clear of each web, material Grating (1,333 kg/m3, 4.62 kg); on the stringer's slope (going = rise x run / height): 12 at rise 156.667 (going 309.92) and 11 at rise 170 (going 290.57); top 47.43 / 39.68 above the stringer centre line.
- **Brackets:** L60*6 x 140 under the tread's top plate, the vertical leg on the stringer web; 2 M12 8.8 L35 per bracket, 90 apart, 30 below the bracket top, 5 up the run from the bracket centre, head on the web's outer face (grip 15.5). Tekla weighs them by catalog kg/m x length (5.42 x 0.14 = 0.76).
- **Stringer pieces:** each sloped stringer mitred to the piece it meets (foot / landing / top): one plane through the centre lines' crossing, square to the sum of the two directions, cutting both pieces.
- **Stair plates FLT12*150:** at the feet 260 x 150 (2 x 2 M16 L12 outward), at the landings 320 x 150 through the posts' flanges (2 x 4 M16 L45, pitch 85, gauge 70), at the top 320 x 150 bolted outward to a counter plate on EB4002 (2 x 4 M16 L50, grip 24).

### Change, in web v70 (section "C18") and the app (`ClassicApps.stairs70`, `FamSEP`)
- Stair treads, **Treads = Tekla**: number of treads, rise, first tread's centre along the run, its top above the stringer line, tread / bracket sizes, bracket bolts (spacing, offset, depth, Tekla length); optional parts 3–6 = the pieces each stringer meets (mitre fit). Old models (Treads = v69) unchanged. Stair treads is now in the catalog (Other) with a Tekla-style dialog.
- **Stair end plate** (SolidSketch, our own; catalog Other): plate t x b x h with its centre offsets, a bolt grid through the main part (head on its far side) or outward, an optional counter plate on the main part.
- Weight: a component's catalog angle part carries its length (st.len70) and is weighed kg/m x length (the brackets).

### Search result: the R12 inputs (`r10_inputs.py`)
- Per flight, measured on R12's treads: ntr, rise, s0, dz; the 4 adjoining pieces (same assembly mark, nearest end). Per stair plate: its stringer end, size and offsets, bolt grid, main part (the post the bolts pass) or outward, explicit bolt length where R12's is not Tekla's rule (the feet's L12).

### Checks
| Check | Before (C01, d7d5374) | After |
|---|---|---|
| Row 18 R12 parts (web rebuild, 0.1 mm) | 4 / 83 (treads, brackets, stair plates missing) + 8 / 12 stringer pieces (4 sloped ones not mitred) | 81 / 83 (all but the 2 counter plates on EB4002) + 12 / 12 stringer pieces |
| R12 bolts matched by position (web rebuild, 0.1 mm) | 4,250 / 4,548 | 4,390 / 4,548 (+140: the 92 bracket bolts and the stair plates' 48) |
| App vs R12 (`R12C18Test`, 0.1 mm) | — | treads 23 / 23, brackets 46 / 46, stair plates 8 / 8, stringer pieces 12 / 12, bolts 148 / 148; treads + brackets 141.22 kg = Tekla 141.22 |
| Rule B | — | 0 failures: R12 flight 1 and 2, UPN200 defaults, UPN240 at 40 deg in plan |
| Weight (rebuild, web) | 63,323.22 kg | 63,086.22 kg (Tekla 63,705.8): -237.0 (v69's PL6 steel treads and L50 supports replaced by R12's grating and brackets) |

### Open
- (Closed on R13, below.) The 2 counter plates FLT12*150 on EB4002.

### R13 (agent C18, 2026-11-28): counter plates, Tekla's stair assemblies (web `web/v70/c18.js`, app `FamSEP`, `StRegen` owner)
- **Counter plates:** R13's 885 / 887 (FLT12*150, assembly EB4002, welded to the girder's flange edges, 4.52 kg each) sit right behind the top plates 884 / 886 (y 16713–16725 against 16701–16713, same x / z). The top Stair end plates now take EB4002 as main part with **Counter plate on the main part = Yes** (`r10_inputs.py`: the plate's TKBOLT partner is an EB Stair Plate; grip 24 = 2 x 12, extra grip 0). Row 18: 83 / 83 MATCHED (before 81 / 83); bolts unchanged (the 16 top bolts already matched).
- **The top plates are loose:** 884 / 886 have no weld and no owner in R13 (Tekla's assembly PLSTR9001, qty 2, 9.0 kg in the Assembly list). New Stair end plate field **Plate welded to the member** (`pweld`, default Yes = old models unchanged): No = no weld, the plate its own assembly. `r10_inputs.py` sets it for a plate with no weld and no assembly.
- **Treads are their own assemblies:** Tekla's SGR9001 x 23 = THREAD (main part) + its two L60*6 brackets. v69 put every tread and bracket in the first stringer's assembly (SR9002 / SR9004 were 251.40 / 226.59 kg). Treads = Tekla now: the tread has no owner, its brackets belong to it.
- **Assembly weights, ours (rebuild, per assembly as the app numbers them) vs Tekla's assembly drawings (Package_2 `Assembley.PDF`, material list total) vs Tekla's Assembly list (`AssembleyList.xsr`):**

| Mark | Parts | Ours | Tekla drawing | Tekla Assembly list | Sum of R13 st.tkW |
|---|---|---|---|---|---|
| SGR9001 (x 23) | THREAD 4.62 + 2 L60*6 0.76 | 141.22 (6.14 each) | 6.1 each | 140.8 | 141.22 |
| SR9001 / SR9002 | UPN240 139.41 + 5.78 + 5.32, plates 4.52 + 3.67, 6 CHS48*3 3.18 | 177.69 | 177.8 | 169.5 | 177.78 |
| SR9003 / SR9004 | UPN240 128.41 + 4.91 + 5.34, plate 4.52, 5 CHS48*3 3.18 | 159.03 | 159.1 | 153.3 | 159.08 |
| PLSTR9001 (x 2) | FLT12*150 4.52 | 9.04 | — | 9.0 | 9.04 |
| EB4002 | girder + 2 counter plates | 530.85 | — | 530.9 | 530.90 |

- **Why Tekla's Assembly list is lower (Tekla's own, not ours):** it weighs each part by its solid without bolt holes, cuts subtracted, catalog sections as Tekla's own solid; the Part List and the assembly drawings weigh a catalog profile by kg/m x length. Proof from R13's solids (IFC mesh volume, holes filled back): SGR9001 4.6188 (tread, 3,500 mm² x 990 x 1,333) + 2 x 0.7517 (L60*6 at 684 mm², the IFC's sharp-cornered angle, x 140) = 6.1222 x 23 = **140.81** (list 140.8; by kg/m 0.7588: 141.14). SR9001: IFC solids 168.91 + the holes (24 x Ø14 through the 9.5 web, 12 x Ø18 through the plates) 0.56 = **169.47** (list 169.5): the mitred stringer weighs 134.58 as a solid against 139.41 by kg/m x length. SR9003: 152.91 + 0.44 = **153.35** (list 153.3). Over the whole model the Assembly list totals 63,995.0 against the Part List's 64,023.4.
- **Left, not C18's:** the stanchions CHS48*3 weigh 3.16–3.21 against Tekla's 3.18 (SR9001 −0.06, SR9003 −0.03): row 19's weld-only connection cuts each stanchion's top by the hand rail; Tekla's top is square and its foot is cut on the stringer slope (R13's mesh: 13 z-levels 1285.9–1310.2 at the foot, one at the top; 404,985 mm³ = 999.96 x 405). The stringers' −0.03 (SR9001) / −0.02 (SR9003): the shared kg/m table (below).
- **Shared changes proposed to the main session (not made, integration rule):** (1) `TK_KGM.UPN240` / `TeklaWeight.KGM` 33.2 → **33.2055** (Tekla's 4,230 mm² x 7,850: every one of R13's 12 stringer pieces then equals st.tkW, e.g. 4198.366 x 33.2055 = 139.41, 173.929 x 33.2055 = 5.78; 33.2 gives 139.39 / 5.77): rebuild +0.10 kg. (2) For an **imported catalog member drilled by TKBOLT**, v66 rebuilds the solid without holes *and without its cuts* and v69 takes max(gross, net): R13's four long UPN240 stringers weigh 139.84 / 128.81 instead of 139.41 / 128.41 (WEIGHTS-R13 UPN240 +1.64 kg). Fix: when `holesKg > 0`, a catalog member is kg/m x length (web `partWeight70`, app `TeklaWeight.part`: skip `max(gross, net)`); checked: only these 4 parts change on R13 (+0.00 then), no rebuild or golden case changes.

| Check | Before (main 5f16e60) | After |
|---|---|---|
| Row 18 R13 parts (web rebuild, 0.1 mm) | 81 / 83 (2 counter plates MISSING) | 83 / 83 |
| App vs R13 (`R13C18Test`, 0.1 mm) | treads, brackets, 8 stair plates, 12 stringers, 148 bolts | + the 2 counter plates (10 / 10 stair plates); every tread, bracket, stair and counter plate weighs st.tkW (0.01 kg); stringers within 0.03 (kg/m 33.2, see the proposal) |
| Assemblies (ours vs Tekla's drawings) | SR9002 251.40, SR9004 226.59, no SGR / PLSTR | SGR9001 141.22, SR9001–4 177.69 / 159.03, PLSTR 9.04, EB4002 530.85 |
| Weight (rebuild, web) | 64,005.59 kg | 64,014.63 kg (+9.04, the counter plates; Tekla's Part List 64,023.4) |

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C01 / C16 · Base plate 1042 / 1004 as Tekla: anchor rods as parts, nut and washer, grout with slopes, cast plate under the grout (owner 2026-11-27)

### Step 0: Tekla's own site
- [Base plate (1042)](https://support.tekla.com/doc/tekla-structures/2025/detail_1042_help) and [Base plate (1004)](https://support.tekla.com/doc/tekla-structures/2025/detail_1004_help), **Anchor rods tab**: "Base plate with: bolts, anchor rods, or a custom component" (default bolts). Anchor rod dimensions: 1 nut size (default the rod diameter), 2 washer thickness (half the nut size), 3 gap between the plate and the nut or washer, 4 rod length (500), 5 rod length above the base plate (50), 6 distance between the cast plate and the base plate (60). Hook type 1: a = radius of the hook (2 x diameter), b = width of the hook (1/5 of the rod length). Hook direction types 1–4. The washer has no hole by default. Grout: thickness, "created with or without slopes"; cast unit type Cast in place.
- Tekla's own videos: none on the page.

### Step 1: videos (`tests/steel/videos/C01/`)
- `find.py`: Tekla's pages (no media), then YouTube. `gemini_read.py` on two 1042 videos, Anchor rods tab: see `VIDEO.txt`.

### Step 2: the Tekla package
- Assembly 1001 (192 pieces): 1501 ROD20 St-52 L677.1 1.5 kg + 4 PD40*10 (washer, 10) + 5 PD40*10 (nut, 20). Single-part 1501: 600 overall, hooked, the bend drawn in chords. 1002 / 1502: ROD16 L283.7 0.4 kg, 250 overall.

### R12 (52 bases: 48 columns on 1042, 4 stair posts on 1004)
- **Rod:** an 8-gon (Tekla's ROD20 / ROD16, vertices on the bend plane and across it) from 50 above the plate top down to 600 (250) below it, bent outward along the plate X (the column depth) with a = 2 d (40 / 32) in two chords, the hook's end face b = 120 (68) from the axis. Volume 195,490 (53,399) mm³ = Tekla's 1.53 (0.42) kg.
- **Washer** PD40*10 (12-gon ring 20 / 10) on the plate, 10 thick; **nut** PD40*10 on it, 20 thick. None on the stair posts.
- **Holes** in the base and cast plates: 24-gons of radius d / 2 + 1 (11 / 9).
- **Grout:** directly under the plate, 30 thick, **with slopes**: the plate's size at the top, the margin (30) at the bottom (R12's FLT30*300 / PL30*260 / PL30*230 solids are frustums), no holes, weight 0.
- **Cast plate** PL4, the plate's size, **under the grout** (Tekla 6 = 30). v69 put it directly under the plate, the grout under it (30 mm off).
- The earlier report's "grout 130–150 mm off centre" was R10's; R12's grout is centred on the plate.

### Change, in web v70 (section "C01 / C16") and the app (`FamBP.bp70`)
- 1042 / 1004 Anchor rods tab: **Base plate with** = Anchor rods; group "Anchor rod (Tekla)": 4 length, 5 above the plate, a, b, hook direction (outward / inward along X or Y, or straight), nuts (0 / 1 / 2), 1 nut size, washer, 2 washer thickness; group "Grout and cast plate (Tekla)": order (grout under the plate, cast plate under the grout), 6 cast plate distance, grouting with / without slopes. Old models (Bolts, v69 order) are v69's unchanged.
- Nut and washer: PD(2d)*(d/2) (PD40*10 on M20).
- Weight: a rod part carries its hole (st.hole, a 24-gon) so the base and cast plates are weighed gross; PD rings are weighed by their solid (their bore is the profile's).
- Rule B (`geom_checks.py`): an anchor rod in the grout is embedded (Tekla's grout has no holes); a nut / washer on its own rod touching the rod's corners (< 0.5 mm) is the thread; each rod must pass a hole in the base and cast plate (no grip check: concrete between them).

### Search result: the R12 inputs (`r10_inputs.py`)
- Per base, measured on R12's first rod and the parts under the plate: awith rods, aL, aup, ahr, ahw, ahdir (all x), anut, awash, gorder tekla, castd, gslope slope; the anchor gauges from the rods' own axes (R12's rod reference lines are 55 mm off the rods).

### Checks
| Check | Before (C07, ee9267c) | After |
|---|---|---|
| Row 1 R12 parts (web rebuild, 0.1 mm) | 48 / 720 (the base plates; 192 rods as a bolt group, 384 nuts and washers missing, 48 cast plates 30 mm off, 48 grouts near) | 720 / 720 |
| Row 16 | 4 / 24 (16 rods as bolts, grout near) | 24 / 24 |
| App vs R12 (`R12C01Test`, 0.1 mm, rods also by volume) | — | 744 / 744 parts; their weight 923.41 kg = Tekla 923.41 |
| Rule B | — | 0 failures: R12 C2020, R12 EGC2001, R12 STR9001 (1004), HEA300 defaults Y-hooks 2 nuts, IPE360 turned 30° with 6 rods hooked inward |
| App = web | — | `R12RebuildTest` 0 differences; `S7T144Test` + 5 t1042 cases; host tests 126 / 126 |
| Weight (rebuild, web) | 62,982.42 kg | 63,323.22 kg (app 63,323.36; Tekla 63,705.8): +340.8 kg (rods, nuts, washers as parts) |

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C07 · Tube crossing (Tekla 20 crossing): continuous brace, two halves, crossing gusset (owner 2026-11-27)

### Step 0: Tekla's own site
- [Tube gusset (20), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_20_help): 20's help also covers crossing braces (its Cross plates tab); the halves are connected like any brace (connection plate, end plates, Picture 5 = gusset to end plate, default 20) and use the same Brace conn / Bolts tabs as C05.
- So R12's crossings are Tube gusset (20) with three parts (continuous, half 1, half 2); our Ttgx (v69 "Tube crossing (tongues)") is the place for it. Tekla's own videos: none.

### Step 1: videos — the same as C05 (`tests/steel/videos/C05/VIDEO.txt`); neither shows a crossing (lighter model); nothing from the videos is used.

### Step 2: the Tekla package
- NC 6534 (16 pieces, HZ roof crossings): PL12 572.73 × 330.06, contour (0, 130.82) (49.85, 0) (425.18, 0) (572.74, 199.23) (522.89, 330.06) (147.55, 330.06), 8 holes d18 (two 2 × 2 groups, gauge 60, pitch 60): the hexagon of the rule below. Single-part drawings 6534, 6536, 6554 (HZ), 6514, 6507, 6522, 6523 (VL).

### R12 (52 crossings: 28 HZ roof, 24 VL wall; 52 crossing gussets PL12*318–334, 104 halves)
- The continuous brace runs through; each half ends at its own end point (as C05's tube end) with a 315 × 140 × 12 connection plate on its axis (150 inside, 165 out), two 100 × 44 × 5 end plates and 4 M16 (e 40, p 60, g 60, Tekla bolt length).
- **The crossing gusset**: each half has an edge square to it 20 before its end plate (half-length 70 each side); the outline is the hull of those corners and of each corner brought, along its half, onto the continuous brace's far face (−side × half its width). It lies beside the connection plates (12 thick) and passes through the continuous brace, which is slotted by it.

### Change, in web v70 (section "C07") and the app (`FamTG.cross70`)
- Ttgx Gusset tab, group "Crossing (Tekla 20)": **Tube end** = at the brace end (Tekla's rule above; v69's cut-back tongues for old models), **Gusset side**, **5 · Gusset to the end plate** (20); Bolts tab **Bolt length** = Tekla. Old models unchanged (tend '').

### Search result: the R12 inputs (`c07_q`)
- L, ins, e, p, g measured on the first half (as `c05_q`); tend 'end', blrule 'tekla', gshape 'tekla', side 1 at all 52.

### Checks
| Check | Before (C06, 7b1ec0e) | After |
|---|---|---|
| Row 7 R12 parts (web rebuild, 0.1 mm boxes) | 32 / 118: crossing gussets 0 / 52 (ROUGH), caps 32 / 66 (18 NEAR, 16 ROUGH) | 100 / 118: gussets 52 / 52, caps 48 / 66 (the same 18 NEAR at 0.1–1.7 mm, below) |
| Row 5 (the halves' plates and end plates) | 943 / 1,294 | 1,239 / 1,294 (the rest: the C06 gable nodes) |
| App vs R12 (`R12C07Test`, 0.1 mm) | — | parts 364 / 364, bolts 416 / 416, member bodies 156 / 156 |
| Rule B | — | 0 failures: R12 HZ6004, R12 VL crossing on line 6, SHS80*4 X at 60° (halves 330 from the crossing: at 230 the connection plate would clash with the continuous tube, also in Tekla), SHS120*5 X at 90° side 2 with 285 plates |
| App = web | — | `R12RebuildTest` 0 differences; `S7T144Test` + 4 t20x cases |
| Weight (rebuild, web) | 62,742.75 kg | 62,982.42 kg (app 62,982.56; Tekla 63,705.8) |

### Open (closed on R13, agent TUBES 2026-11-28)
- The 18 PL5 "crossing caps" (Gusset Plate PL5*32-93) are the end plates of VL6011-VL6014 at their upper ends, i.e. at the gable / end-frame nodes, not at a crossing: their 0.3 deg turn is the connection plate turned square to the gusset normal (C06 "Gable / end-frame nodes"), not IFC rounding. With gplane 'perp' all 18 match (0.002 mm), row 7 118 / 118.

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C06 · Tube gusset (20), the gusset: node gussets, single-brace gussets, fins (owner 2026-11-27)

### Step 0: Tekla's own site
- [Tube gusset (20), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_20_help): the gusset is 20's own part (part key 1). **Picture tab:** 1 / 10 the gusset's upper / lower edge from the connection plates; 2 corner angle; 3 / 7 "length of the edge of the gusset plate that is perpendicular to the uppermost / lowest brace"; 6 "length of the edges of the gusset plate perpendicular to the braces"; 5 "distance between the gusset plate and the brace; if the braces are sealed with end plates, between the gusset plate and the end plate", default 20; 8 / 9 main part to the first brace, between braces. **Gusset tab:** welded directly to the main part (default) / clip angles / connection plate; "Gusset plate shape: optimize the weight"; "Gusset plate position on the brace: in the middle of the brace (default) / on the top flange".
- So the node gusset is Tube gusset (20)'s own gusset (one component for 1–10 braces); no separate "node gusset" component is needed. Tekla's own videos: none for 20.

### Step 1: videos — the same as C05 (`tests/steel/videos/C05/VIDEO.txt`): the Gusset tab is not read in either video (lighter model); nothing from the videos is used.

### Step 2: the Tekla package
- NC 6532 (R3019's node gusset, 5 pieces): PL12 549.34 × 280.39, contour (0, 0) (549.34, 0) (549.34, 190.21) (426.53, 257.42) (339.32, 280.39) (199.32, 280.39) …, 12 holes d18 — the same outline as R12's IFC solid and as the rule below.

### R12 (250 gussets of row 6: 168 node gussets, 82 fins)
- Every brace has an edge square to it at **Picture 5 = 20 before its end plate** (plate tip + 140 for the 315 plates, + 110 for the 285 ones: 166 / 166 brace edges), its half-lengths 70 (half the plate width; 22 brace edges at 55 on one side: Picture 3 / 7).
- **The outline is the convex hull of those edges' corners and of each corner brought back onto the main part's web face, square to the face and along its brace.** It fits all 62 multi-brace node gussets (51 within 0.1 mm, 11 within 0.4) and all 104 single-brace PL12 gussets (within 0.1 mm); the 82 FLT12*140 fins already matched after C05.
- At the 6 gable / end-frame nodes (gussets that are their own assembly, marks 11 / 12 / 13) no plate is on its own axis: the gusset is centred on the braces (Gusset tab "middle of the brace", Tekla's default) and the plates lie beside it. Their vertical braces are skewed 4–60 mm out of the gusset's plane, and those 17 gussets stay within 0.2–4.3 mm (open).

### Change, in web v70 (section "C06") and the app (`FamTG.teklaOutline`)
- Ttg Gusset tab, group "Gusset plate shape": **Gusset shape** = Tekla (the rule above; v69's hull of the tongues for old models), **5 · Gusset to the end plate** (20), the brace edge's half-lengths **toward / away from the main part end** (default half the plate width), **Gusset plate position on the brace** = middle.
- One brace with Gusset shape Tekla: Tekla's gusset instead of v69's fin.

### Search result: the R12 inputs (`c05_q`, `c05_order`)
- gshape 'tekla' at every node and every PL12 single-brace gusset; gwa / gwb where R12's edge is shorter than half the plate width (22 ends: 55); gpos 'middle' where no plate of the node is on its axis (12 ends).

### Checks
| Check | Before (C05, bccb1e7) | After |
|---|---|---|
| Row 6 R12 parts (web rebuild, 0.1 mm boxes) | 82 / 250 (fins only; node gussets 15 NEAR, 153 ROUGH) | 233 / 250 (17 NEAR: the gable nodes) |
| App vs R12 (`R12C05Test`, gussets) | — | 230 / 230 (gable nodes apart: 3 / 18) |
| Row 5 plates at the C06 nodes | 27 / 82 | the same 27 / 82 (the gable nodes' skewed braces) |
| Rule B | — | 0 failures: R12 node R3019, R12 single gusset, HEA240 two braces 'middle', IPE360 V node with a shortened edge |
| App = web | — | `R12RebuildTest` 0 differences; `S7T144Test` + 4 t20g cases |
| Weight (rebuild, web) | 62,591.88 kg | 62,742.75 kg (Tekla 63,705.8); the 233 matched gussets 1,107.25 kg vs Tekla's 1,109.77 (e.g. the 32 PL12*213.4: 108.8 = 108.8) |

### Gable / end-frame nodes (R13, agent TUBES, 2026-11-28)
The 18 own-assembly gussets (marks 11 / 12 / 13) at the gable and end-frame columns: a strut (ST5005 / ST5006 / ST5007) and a wall brace (VL6009-VL6014) on one gusset, the strut skewed 0.82-9.0 mrad out of the wall (its Tekla reference line starts on the far column's centre, IFC placement), so the two braces are not in one plane. Measured on R13 with the IFC placements (Package_2 `out.ifc` = `Frame_28x53m_tekla_v3.ifc`: each plate's origin = its tip centre, X along its brace):
- **Gusset plane:** square to the strut (the brace most square to the main part): normal = strut × column axis, to 1e-6 at all 18 nodes.
- **Plates turned to the gusset:** every connection plate keeps its brace's direction and has its width in the gusset plane (normal = the gusset normal made square to the brace; e.g. VL6012 at 3419: plate N (0.99999, -0.00251, 0.00403) = (0.99996, -0.00897, 0) less its component along the brace). The end plates follow (they are boxes in the plate's frame, to the tube wall at the nearer corner; R13's 0.3 deg "turn" of the C07 caps is this, not IFC rounding).
- **The plates' plane (gusset ± 12) passes through the middle of the first brace's Tekla reference line** (its IFC start to its far end): 12.0000 mm from the gusset mid-plane at all 10 nodes whose first brace is a VL (VL6010 / VL6011 / VL6012, reference start 280.09 / 421.50 mm before the tube end). At the 8 nodes whose first brace is the strut (plate on its axis) this is the strut's own plane, as before.
- **A plate whose brace is off that plane crosses it at its own gusset edge** (Picture 5: plate tip + 140): VL6009 / VL6013 / VL6014 at 139.88-140.0 from the tip (all 8 strut-led nodes).
- **The first brace's plate (VL, 10 nodes) is 1.10-12.02 mm off its axis**, proportional to the strut's skew (1339 x the skew at the y = 5645 columns, 1296 x at y = 27925), toward no point of the model found: entered as a measured input (pofs), not a rule.
- **The gusset's brace edges:** Picture 5 is the least gap to the end plate, at the gusset face nearest it: the edge sits tf/2 x |sin tilt| nearer the tip (139.954 for 7.6 mrad, 139.995 for 0.8 mrad: all 36 VL edges in the plate's frame, ±70 across), its corners moved along the plate normal onto the gusset mid-plane.
- **Open (6 gussets, 0.126 mm):** at the strut-led nodes with the 9 mrad strut (3425, 3487, 3601, 3674, 3747, 4057) Tekla's main part edge (square to the strut) is 0.234 mm off the column web at the mid-plane (ours 0.108); at the VL-led nodes it is 0.101 inside (ours 0.000 from the plates' line, within 0.1). No point of the model was found that puts it there (the edge's plane meets the web 14 mm beyond the plates' plane at the strut-led nodes, 12 mm before the VL plane at the VL-led ones).
- **Rule B:** the 8 VL lead plates at the 9 mrad VL-led nodes stand 1.45-2.5 mm off the gusset along their bolts (R13: TKBOLT grip 25.8 / 26.7 against a 24 mm stack): Tekla's own geometry, reproduced (case t20g_r13_gable_vl_lead: 2 bolt-grip findings "air 2.3"). Every other t20 / t20g / t20x case: 0 failures.

**Change (web v70 sections "C05" / "C06", app `FamTG.gen` / `teklaOutline` / `nodeToff`):** Ttg Gusset tab, group "Gusset plate shape": **Gusset plane at a node** = Tekla (gplane 'perp': the rules above), **First brace modelled from (mm before its tube end)** (gmid), **Connection plate off the brace axis** (pofs, measured). Old models unchanged (gplane '').
**R13 inputs (`r10_inputs.py` `c05_gable`):** a node whose braces are not all in the gusset's plane gets gplane 'perp' (36 ends at 18 nodes); where no plate is on its axis the VL goes first with gmid (from R13: 273.3-283.7 / 421.9, Tekla's 280.09 / 421.50 within the 0.001 mm rounding) and pofs.

| Check (R13 rebuild, web, 0.1 mm boxes) | Before (main 5f16e60) | After |
|---|---|---|
| Row 5 (tube ends) | 1,239 / 1,294 (55 NEAR) | 1,294 / 1,294 |
| Row 6 (gussets) | 233 / 250 (17 NEAR, 0.2-4.3 mm) | 244 / 250 (6 NEAR, 0.126 mm) |
| Row 7 (crossings) | 100 / 118 (18 NEAR) | 118 / 118 |
| Bolts at the same position (R13 4,596) | 4,524 (groups 1,276) | 4,548 (groups 1,282) |
| App (`R13C05Test`) | gable nodes not asserted | plates / end plates 82 / 82, bolts 144 / 144, bodies 72 / 72; gable gussets 12 / 18 (6 open) |
| Weight (rebuild, web) | 64,005.59 kg | 64,005.36 kg (rows 5-7: 4,041.92 -> 4,041.83; Tekla's parts 4,044.29) |

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C05 · Tube gusset (20), the tube end: connection plate, end plates, slot, bolts (owner 2026-11-27)

### Step 0: Tekla's own site
- [Tube gusset (20), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_20_help) (also [2024](https://support.tekla.com/doc/tekla-structures/2024/conn_20_help)): connects 1–10 hollow braces to a beam or column with a gusset plate; "bolts the braces to the gusset plate using a connection plate and an optional tongue plate"; "the hollow braces can be sealed with end plates". Part key: 1 gusset, 3 tongue plate, 6 connection plate, 7 end plates (seal plates).
  - **Picture tab:** 4 "Brace length on the connection plate", default 150 mm; 5 "Distance between the gusset plate and the brace (the end plate)", default 20 mm; 1 / 10 gusset edges; 2, 3, 6, 7 gusset shape.
  - **Brace conn tab:** Connection plate t / b / h (default t 20); End plates (default t 5); brace connection types (welded / bolted / notched); "Cut in bracing": width "where t is the thickness of the connection plate" and length; connection plate cut or brace notched; one or two connection plates; end plate shape (square / round) and edge distance.
  - **Selection order:** main part, then the braces (first, second, …).
- So R12's names are Tekla's: "Connection Plate" FLT12*140 = 20's connection plate (no tongue plate); "Gusset Plate" FLT5*100 = its end plates (the IFC names them after the gusset part type).
- Tekla's own videos: none found for 20 (the "Tekla Software" hit is Multiple stiffeners 1064, C20).

### Step 1: videos (`tests/steel/videos/C05/`)
- `find.py`: Tekla's page above, then YouTube "TUBE GUSSET 20" (Z5SVpvwDdv8) and "[Tekla Structures 2020] Tube Gusset" (igIjArVFNn0); `gemini_read.py` on both, Brace conn tab. Values in `VIDEO.txt`.

### Step 2: the Tekla package
- NC 6508 (380 pieces): connection plate 315 × 140 × 12, holes d18 at 40 / 100 along × 40 / 100 across (gauge 60). NC 6512 (42): 285 × 140 × 12, holes at 30 / 80 × 45 / 95 (gauge 50). NC 1 (794): end plate 44 × 100 × 5. Single-part drawings 6508, 6512, 1.

### R12 (456 tube ends; row 5 = 352 Ttg ends + 104 crossing halves of row 7)
- Every brace solid ends exactly at its own end points (Tekla cut it there) and lies on its reference line; SHS100*3 with square corners.
- Connection plate on the tube axis, 150 inside, 165 (or 135) out; slot = the plate (12 wide, 150 long). Two end plates 5 thick on the tube's end face, each from the plate to the tube wall (100 × 44).
- 4 M16 8.8 L50 (grip 24 = plate + gusset): 40 + 60 (30 + 50) from the plate's tip, gauge 60 (50); head point on the plate face.
- The plate plane: v69's automatic choice at 272 ends; the rafter's plane at 80 strut ends (single struts on a rafter top: Tekla's plate is in the rafter slope, v69 stood it vertical).
- At a node (several braces on one gusset) the first picked brace's plate is on its own axis; the others lie against the same gusset, off their axes by 9.4 / 11.3 / 46.9 mm (R12's lead = the brace whose plate is on its axis).

### Change, in web v70 (section "C05") and the app (`FamTG`, `StProf`, `TeklaWeight`)
- Ttg options (old models unchanged): **Tube end** = at the brace end (as Tekla); **Connection plate position** = against the gusset (at a node); **Gusset side**; **Gusset plane** adds Vertical; Bolts tab **Bolt length** = Tekla. In 'at the brace end' mode the slot is Tekla's (t × the length inside) and the head point is on the plate face.
- Parametric SHS / RHS written in Tekla's `*` notation have square corners (r 0), as Tekla's IFC; v69's own names (SHS80x5) keep r = 2 t.
- Weight: the hole fill now measures on the part's own mesh (rays; the drilled hole is stBolts' 12-gon), so a page that builds many models leaks no Manifold objects (the S10 portal golden crashed); same numbers as before (tab 2.47, cleat 1.10); app = web.

### Search result: the R12 inputs (`tools/golden/r10_inputs.py` `c05_q`, `c05_order`)
- Per end, measured on R12: L, ins, e, p, gauge, plane, side; tend 'end', tpos 'gusset', blrule 'tekla'. The node's first brace (plate on its axis) goes first in the list (Tekla's pick order).

### Checks
| Check | Before (ce01af4) | After |
|---|---|---|
| Row 5 R12 parts matched (web rebuild, 0.1 mm boxes) | 0 / 1,294 (584 MISSING) | 943 / 1,294 (0 MISSING); the rest: 104 crossing-half plates + their end plates (row 7, C07) and the ends at 6 own-assembly gusset nodes (C06) |
| App vs R12 (`R12C05Test`, 0.1 mm) | — | plates 916 / 916, bolts 1,264 / 1,264, member bodies 632 / 632 (the C06-node ends counted apart: plates 27 / 82) |
| Brace bodies (web) | HZ 96 / ST 68 / VL 56 SOLID DIFFERS | 228 / 228 MATCHED (box 0.0, volume 0.999–1.001) |
| R12 bolts on the same line | 2,450 of 4,548 (2,096 same length / grade) | 3,834 (3,480) |
| Rule B | — | 0 failures on 4 cases (R12 node R3019, R12 single strut, HEA200 + SHS80*4 at 45°, IPE300 V node with SHS120*5) and on every earlier case |
| App = web | — | `R12RebuildTest` 0 differences; `S7T144Test` + 4 t20 cases; host tests 124 / 124 |
| Weight (rebuild, web) | 62,516.10 kg | 62,591.88 kg (Tekla 63,705.8): +75.8 kg from the square-cornered SHS braces and the row-5 parts now made as Tekla's |

### R13 (agent TUBES, 2026-11-28)
- The 55 row-5 parts left NEAR on R13 (plates and end plates at the 18 gable / end-frame nodes) all match with C06's gable-node rules (plates turned square to the gusset normal; the plates' plane through the first brace's middle): row 5 1,294 / 1,294; app `R13C05Test` asserts them (82 / 82 plates, 144 / 144 bolts, 72 / 72 member bodies).

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C20 · Column with stiffeners (186) at the mezzanine column webs, and row 12 (owner 2026-11-26)

**Scope (owner):** the column stiffeners FLT10*70 / FLT10*90 (Top / Bot, FS / NS) at the mezzanine columns, and the shear tab 186 makes on the column web; where R12 has 186 (tab + column stiffeners together), 186 replaces 146 in row 12. No other connection touched.

### Step 0: Tekla's own site
- [Column with stiffeners (186), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_186_help) (also [2024](https://support.tekla.com/doc/tekla-structures/2024/conn_186_help), the same text):
  - **Picture tab:** 1 "Cut of the secondary part … creates a gap between the main part and the secondary part", default 20; 2 "Size of the strip made to the secondary part flange. The cut of the flange is defined from the shear tab edge. The flange is automatically stripped when the shear tab crosses the flange", default 20; Beam end cut (Automatic / Square / Bevel, default Bevel); Beam flange cut (Bevel / Square, default Bevel).
  - **Plates tab:** Tab plate t, b, h; shear tab chamfers 1 / 2 and type; position.
  - **Stiffeners tab:** Top NS / Top FS / Bottom NS / Bottom FS (t, b, h); orientation (default parallel to the secondary part); creation (default: created; Automatic; not created); shape (default line chamfered; square; gap for the web rounding); **Stiffener gap** 1 "distance from the edge of the flange to the edge of the stiffener", 2 "size of the gap between the flanges and the stiffener"; positions 1–5; chamfer 1 / 2, **default 10 mm**; chamfer type (none / line / convex / concave / line and arc).
  - Bolts tab (as 146: horizontal position, edge distance, number, spacing, vertical position Top / Middle / Below, delete, staggering, orientation), Haunch, Notch, Doubler plate, General, Welds.
- [Shear plate simple (146), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_146_help) and [End plate (144), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_144_help): the same Bolts tab and the same stiffener names (C13).
- Tekla's own videos: `find.py` found "Stiffener and Gusset Plate Width Tolerance" (channel Tekla Software, [qvQZWnkhhas](https://www.youtube.com/watch?v=qvQZWnkhhas)); its frames show **Multiple stiffeners (1064)**, not 186, so Tekla has no 186 video that was found.

### Step 1: videos (`tests/steel/videos/C20/VIDEO.txt`)
- `find.py` → Tekla's page above, then YouTube: [lYG_CPzBR40](https://www.youtube.com/watch?v=lYG_CPzBR40), [XdvAYaGJ5OY](https://www.youtube.com/watch?v=XdvAYaGJ5OY), [ebPk5Jc3ENI](https://www.youtube.com/watch?v=ebPk5Jc3ENI) and Tekla's qvQZWnkhhas.
- > **Gemini model used (2026-11-26):** the three YouTube videos were read with the main model (gemini-flash-latest). Tekla's qvQZWnkhhas was read with the **lighter model** (gemini-flash-lite-latest; the main model's daily quota was spent, 429); the lite model called it a 186 dialog, and the Apify frames show Multiple stiffeners (1064) instead: not used, no re-read needed.
- Apify frames of a real 186 Stiffeners tab: lYG_CPzBR40 01:14–03:14 (`frames_lYG_CPzBR40`); 02:14 is the video panel of C20.png.
- Result: every video leaves the stiffener t / b / h blank (computed by Tekla); none shows the gaps, the chamfers or the strip. The only two-source agreement is "Bottom NS blank". So the starting values are Tekla's site defaults (Picture 1 = 20, 2 = 20, chamfer 10 × 10 line) and the targets are R12's parts.

### Step 2: the Tekla package
- NC 4511 (C20 tab): 210 × 150 × 10, holes d18 at 55 / 115 from the welded edge, rows 50 apart; NC 4501 (EGC2002 tab): 210 × 100 × 10, one column at 55.
- NC 4509 / 4510 (stiffeners FLT10*70) and the FLT10*90 ones at EGC2002: 296 / 196 long, corner chamfers 5.6 / 4.2 on the web side, weld bevels on three edges.
- Assembly drawings C2013, C2020, EGC2002: tab and stiffener positions, beam gap 20.

### R12 (all 68 stiffeners, 24 column-web shear tabs)
| | R12 | Fits |
|---|---|---|
| Stiffeners | 60 FLT10*70 on 15 C20 columns, 8 FLT10*90 on EGC2002 (2 sets) | t 10; width 70 / 90 (set; the outstand is 71 / 97); length = clear depth − 2 × 2 (**Stiffener gap 2 = 2**); flush with the secondary's flange outer faces; NS = the secondary's side; web-side corner chamfers 5.6 / 4.2 (0.7 × the column web, on the root face); the web edge and both flange edges prepared for a K weld (45°, t / 3 from each face, root t / 3) |
| Shear tabs | 20 FLT10*150 on C20 webs, 4 FLT10*100 on EGC2002 | tab from the web face, b 150 / 100, h 210; M16 8.8 L45, 2 × 4 (55 / 115, rows 50) and 1 × 4 (55), centred (Middle 0), grip 18 |
| Beams | BI324-8-250-12 and BI320-8-120-10 | web 20 from the column web (Picture 1 = 20); **both flanges stripped to the near side stiffener's edge + 20** (90 / 110 from the web face, Picture 2 = 20), also at the second beam of a column, which shares the first one's stiffeners |
| Not reproducible | 3 stiffener sets (12 plates) at C2019 and the two C2023 joints; 6 tabs (3615, 3620, 3688, 3693, 3761, 3766) | the beams there, EB4006 / EB4008, have flanges only and no web in R12: no member, so no secondary part to pick (R12 data) |
| Bolt heads | 16 of 18 joints on the beam's Y × X side (C14's rule) | joints C2014 / EB4009 and C2016 / EB4009 have them on the other side: Bolts tab **Bolting direction 2** there |

### Change, in web v70 (section "C20") and the app (`Fam.st` → `colStiff186`, `strip186`)
- New catalog component **T186 Column with stiffeners (186)** with Tekla's tabs: Picture (gap, t, **2 = strip**), Plates (Tab plate t, **b**), Stiffeners (Tekla's groups, including Stiffener gap 1 / 2 and "Welds (stiffeners): K preparation"), Haunch / Notch off, Bolts, **Doubler plate** (off), General, Welds.
- v69's T186 stays for old models: the v70 behaviour applies when Stiffener creation is set.
- Stiffeners: between the main part's flanges at the secondary's flange levels; t default max(8, secondary tf); b default = outstand − gap 1; length = clear depth − 2 × gap 2; chamfer default Tekla's 10, never less than a rolled main part's root radius (rule B: a 10 chamfer cuts into an HEA300's r 27); NS on the secondary's side; K preparation when set.
- Flange strip: both secondary flanges cut square to the beam, the full width, to Picture 2 (default 20) past the near side stiffener's outer edge, also when that 186 creates no stiffeners (the second beam at a column).
- A skewed secondary's tab ends on the main part's web face.

### Search result: the R12 inputs (`tools/golden/r10_inputs.py` `c20_q`)
- 18 joints → T186 (row 20): t 10, tb 150 / 100, bolts as measured (eh 35 / 45, gap 20 / 10 in v69's tab rule), refV Middle 0; Stiffeners: created at the first beam of a column, "not created" at the second (with the same sizes, for the strip), line shape, gap 2 = 2, t 10, b 70 / 90, chamfer 5.6 / 4.2 line, K; bdir 2 at the 2 joints above.
- Every input is measured from R12's parts (no fitting constants).

### Checks
| Check | C20 (row 20) before → after | C12 (row 12) before → after |
|---|---|---|
| Components | 0 → 18 T186 | 22 T146 → 4 T146 (R12 9–12: stair posts STR9001–9004 to the stair beam STB9002; inputs unchanged) |
| R12 parts, web rebuild | stiffeners 0 / 68 → 56 / 68 at box 0.0 mm, volume 1.000–1.002 (12 = R12 data, above); tabs (in row 12 before) 18 / 18 → 18 / 18 | 22 / 28 → 4 / 10 (the same 4 146 tabs at box 0.0; the 6 un-bolted tabs MISSING as before) |
| App, `R12C20Test` (vs R12 itself, 0.1 mm) | plates 74 / 74, bolts 128 / 128 (point, axis, length, grade), member bodies 36 / 36 | plates 4 / 4 as before; their 16 bolts and 2 of 8 member bodies were not reproduced before C20 either (bolts 5 mm off, L 50 vs Tekla 40: a row 12 item of its own) |
| Member bodies, web rebuild | EB BI324 beams 7 INPUT EQUAL, SOLID DIFFERS (volume 1.015–1.019) → 7 MATCHED (1.000); EB BI320 3 + 2 → 5 MATCHED (1.000–1.001) | — |
| Bolts, web rebuild | M16 on the same line 674 → 802 of R12's | — |
| Rule B (`build/c09/case_t186_*-web.json`) | 5 cases (R12 C2020, R12 EGC2002, HEA300 / IPE300, HEB240 / IPE240 Tekla defaults, HEA260 / IPE270 skewed 15°): 0 failures; the 13 C02 / C09 / C13 / C02h cases still 0 | — |
| Golden (app = web) | `S7T144Test`: the 5 t186 cases (every body's volume and box, bolts, welds) + the 16 earlier cases; `R12RebuildTest`: app = web on the whole R12, 0 differences | |
| Weights vs Tekla (`st.tkW`) | stiffeners 1.59 / 1.35 kg each = Tekla (56 plates, K preparation included); tabs 2.32 / 1.57 vs Tekla 2.47 / 1.65 kg: ours are weighed with their bolt holes (the open component-hole item in "Weights") | — |

Checker fix on the way (`geom_checks.py`, rule 4): a part wholly past the shank's end (EGC2002's column flange, 45 mm beyond a 45 mm bolt) was counted as drilled ("air 61 along the shank"); now only parts the shank reaches count.

### Follow-up (owner, 2026-11-27: flange cut-back as R12 and chamfer max(10, r) approved)
- **The 12 stiffeners not reproduced** (C2019, C2023 × 2; `docs/steel/parity/C20_missing.png`): R12 and the drawings agree. Assembly EB4006 is one FLT12*250 top flange and EB4008 one bottom flange (3 Nos. each, drawings EB4006 / EB4008), with no web: the VL6005 / VL6006 bracing gussets PL12*214 sit where the web would be. C2019's drawing still lists tab 4506 and stiffeners 4507–4510, and the tabs have no bolts. So 186 was applied while the web existed and the web was deleted afterwards. 186 cannot be made again there (it needs the secondary beam); not fixed. A fix belongs in the Tekla model (put the webs back, or delete the 186s there).
- **Stair posts (row 12, the 4 remaining 146 joints):** Tekla's Bolts tab as C14 (Middle 0, horizontal edge 35, beam gap 20, tab b 150) and Tekla's bolt length in 146 (grip + nut + washer + 5, up to 5; the earlier approximation ceil((stack + 1.5 d + 2) / 5) × 5 gave 45 for grip 16): plates 4 / 4, bolts 16 / 16 (L40), member bodies 8 / 8 (`R12C20Test`). R12's C14 and C20 joints are unchanged (45 by both rules); C14's other-size golden cases with an IPE240 secondary now get L40 (grip 16.2) instead of 45.
- **Weights:** the tabs now weigh like Tekla (2.47 / 1.65 kg; gross rule above).

### Status
Delivered for the owner's phone check; ✓ only after the owner's OK.

## C12 / C20 / C14 on R13 · the fixed girders EB4007 at C/D/E 5-6 (owner 2026-11-28; agent R13, branch r13-conn)

**Scope:** R13's change (see "Reference model: R13"): the 3 mezzanine girders EB4007 at x 26,498 / 39,693 / 52,838 between the columns at y 22,355 (C2022 / C2022 / C2017) and y 27,925 (C2024 / C2024 / C2021) now have their web FLT8*300 (4518), bolted to the column shear plates (C12's 6 tabs, now drilled: 4515 at y 22,355, 4514 at y 27,925) with 8 × M16 8.8 L45 each. So the 186 (C20) at those 6 joints has its secondary again, and the 12 stiffeners R12 could not place (C2021, C2024 × 2: Top / Bot NS / FS FLT10*70) are made. C12 here = row 20 (the 186 tab is Tekla's 146-style shear plate); row 12 keeps the 4 stair-post 146 joints.

### Step 0 / 1: Tekla's site and videos
As "C20" and "C14" (186 / 146 help pages: Picture 1 = gap between the main part and the secondary, Bolts tab horizontal edge distance, Plates tab b). No new video: nothing here depends on a dialog value that R13 does not show itself.

### Step 2: R13 and the package (what Tekla did)
- **Tekla's web is 30 mm along y.** EB4007's web runs y 22,409 – 27,931; EB4006's (the same girder on the other side, and every R12 186 beam) runs 22,379 – 27,901: 20 from each column web face (Picture 1 = 20). Same length 5,522 (the assembly drawing EB4007: web 5,522, flanges 5,382). The flanges are unchanged (22,449 – 27,831, the 186 strip 70 + 20 from each web face). So the web, with its bolt group, was placed 30 mm too far in +y: Picture 1 = **50** at C2017 / C2022 and **-10** at C2021 / C2024.
- **The bolts follow the web, not the tab:** 35 from each web end (22,444 / 22,504 and 27,896 / 27,836), 60 apart. The tab (150 from the column web face, unchanged) gets them at **85 / 145** (NC 4515, a 150 plate: the 145 hole, d18, breaks out of the tab's end by 4 mm) and **25 / 85** (NC 4514) from its welded edge, against 55 / 115 on every other tab (4506). This is why R13 reads these tabs as two new marks.
- **R13's own rule B** (`geom_checks.py` on R13's mezzanine, 107 connections): 6 failures, all here.
  - 3 × OVERLAP: the web runs 10 mm past the C2021 / C2024 column web face, through the whole 8 mm web (18,176 mm³ = 8 × 8 × 284, mean depth 3.94 mm; 2 mm stick out on the far side between the FS stiffeners).
  - 3 × BOLTS grip 18 vs 8: the groups at C2017 / C2022; the checker reads the tab beside the hole on the bolt line, and at the 145 hole the tab is not there (the hole is open at the tab's end), so only the 8 mm web counts.
  - Both are in Tekla's model as exported (a web copied 30 mm off). In Tekla: move the 3 webs 30 mm in -y (Picture 1 = 20 both ends); the tabs then get 55 / 115 holes (4506) and the clash goes. Owner's decision; nothing in R13 is changed.

### Change, in web v70 (`web/v70/c20.js`, section `// == C20 ==`) and the app (`Fam.st`)
- **Inputs** (`r10_inputs.py c20_q`): Picture 1 = the secondary's own end from the main part's web face, and the Bolts tab's horizontal edge distance from that end (Tekla's 146 / 186 dimension). R12 could not tell this from "the tab end less the edge" (gap 20 + eh 35 = tab 150 - eh 35 at every R12 joint), so v69's tab-based reading gave the EB4007 joints gap 80 / eh 5 and gap -40 / eh 65: our 186 then cut 30 mm off Tekla's web at C2017 / C2022 (`R13C20Test`: 72,431 mm³). Now gap 50 / -10, eh 35 at the six; every other joint keeps gap 20, eh 35 (EGC2002's 10 / 45 becomes 20 / 35: the same bolts, the beam end where it is).
- **186 with Picture 1 < 0** (web `web/v70/c20.js`: a FAM.ST wrapper that runs the 186 with gap 0, edge + gap and the tab length kept; app `Fam.st`: the end cut at max(gap, 0); only T186 with Stiffener creation set): the secondary is cut at the main part's web face, never into it (rule B); the bolts stay at Picture 1 + the edge distance. So our rebuild is Tekla's EB4007 less its clash.
- Not reproduced on purpose: the clash itself. Reproduced as Tekla: the open 145 hole (it is Tekla's NC; our rule B does not flag it for a component bolt, whose axis is 5 mm inside the tab).

### Checks
| Check | before → after |
|---|---|
| `R13C20Test` (app vs R13, 0.1 mm) row 20 | plates 92 / 92, bolts 176 / 176, member bodies **45 / 48 → 48 / 48**; the reference at a joint where Tekla's secondary runs into the main part is Tekla's part cut at the main part's face, and the test asserts those clashes are exactly the 3 EB4007 / C2021-C2024 joints at 18,176 mm³ each |
| `R13C20Test` row 12 (stair posts, 146) | 4 / 4, 16 / 16, 8 / 8 (unchanged) |
| `R13C14Test` (row 14, 68 joist tabs) | tabs 68 / 68, bolts 272 / 272, joists 68 / 68, girders 66 / 68 as on R12 (the 2 EB4005 ends stripped by EGC2002's 186, not built in the C14-only run): **nothing changed on R13** |
| Web rebuild (`R13-REPORT.md`) | EB BI324 members 7 + 3 INPUT EQUAL, SOLID DIFFERS (box 30.0) → **10 / 10 MATCHED** (all 839 members now MATCHED) (the 3 EB4007 against R13 less its clash, box 0.0; `r10_compare.py` `tekla_clash`); rows 12 / 14 / 20 reproduced as before |
| App = web (`R13RebuildTest`) | 6,864 bodies, 1,369 connections, 0 differences |
| Rule B, our rebuild (`build/R13_app.ss`, the mezzanine y ≥ 16,000: 504 bodies, 169 connections) | **0 failures** at C12 / C20 / C14 (4 others: stair railings / stanchions, row 18, also in R13 itself); R13 itself, same region: the 6 above + those 4 |
| Rule B, golden cases (`build/c09/case_t186_*`) | 6 cases 0 failures, new **`t186_r13_c2021_eb4007`** (Picture 1 = -10: the beam ends on the column web face, bolts 25 / 85); `S7T144Test` app = web on it |
| Weight (web rebuild, on main with C08 / C10) | 64,005.59 → **64,005.41 kg** (-0.18: Tekla's 10 mm clash taken off the 3 webs); Tekla 64,023.4 kg (list) / 64,017.49 kg (`st.tkW`) |

### Emulator
`C20RecordingTest` now places R13's C2021 / EB4007 (Tekla's web 22,409 – 27,931), the 186 through its dialog with R13's values (Picture 1 = -10, strip 20; tab 10 × 150; stiffeners created, 10 × 70, gap 2, chamfer 5.6, K; Bolts M16 4 × 2, e 30, pitch 50 / 60, edge 35, Middle 0), checks the beam ends on the column web face (27,921), orbits, orthographic plan, saves; then Top NS b 50 + Modify. `parity_png.py C20`: R13 | Tekla's EB4007 drawing (Package_2) | video | app.

### Status
Waiting for the owner's phone OK; not marked.

## C13 · End plate (144) at the mezzanine, girder → column flange (owner 2026-11-26)

**Owner's decision (2026-11-26):** C13 is the 144 only. The column stiffeners FLT10*70 / FLT10*90 are Column with stiffeners (186)'s and come next, as their own connection (replacing row 12's 146 there).

### Step 0: Tekla's own site
- [End plate (144), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_144_help), Stiffeners tab:
  - stiffeners in the main part: Top NS, Top FS, Bottom NS, Bottom FS (t, b, h);
  - orientation; creation (Automatic / created / not created);
  - shape (line chamfered / square / gap for the web rounding);
  - positions 1–5 (gaps to the beam web and flange edges);
  - chamfer 1 / 2, whose default is "the radius of the profile", and chamfer type.
- [End plate (144), 2024](https://support.tekla.com/doc/tekla-structures/2024/conn_144_help): the same tab, with chamfer defaults of 10 mm.
- [What's new in components, 2025](https://support.tekla.com/doc/tekla-structures/2025/rel_improvements_in_components): the "radius of the profile" default chamfer, which this note gives for Stiffener (1003).
- [Shear plate simple (146), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_146_help): its Stiffeners tab makes only an "opposite web stiffener".
- [Column with stiffeners (186), 2025](https://support.tekla.com/doc/tekla-structures/2025/conn_186_help):
  - a square shear tab "welded to the main part web and stiffeners"; stiffeners Top / Bottom NS / FS;
  - **Stiffener gap**: "distance from the edge of the flange to the edge of the stiffener" and "gap between the flanges and the stiffener";
  - positions 1–5 as in 144.
- Videos: none of Tekla's own videos found for 144's or 186's Stiffeners tab (search of support.tekla.com / help.trimble.com).

### R12 (all 84 stiffeners; R10's grouping was a hint only)
| Where | Joints | Parts | Fits |
|---|---|---|---|
| Mezzanine girder EB (BI320) → column C20 flange: End Plate PL16*120 (Tekla name "End Plate " with a trailing space), 16 × 120 × 430, ext 55 / 55; M16 8.8 L50, 2 × 4 at 60, rows 30 / 100 / 330 / 400 from the top | 6 | girder side "Top Stiff." / "Bot Stiff." FLT10*50: 2 per joint, against the extended end plate in the girder web plane, 50 × 55, clip 15 × 15, chamfer 12.5 | **144**: Haunch tab plates (as C09) |
| The same 6 columns and 9 more C20 columns (all 15) | 15 | "Top FS / Top NS / Bot FS / Bot Ns" FLT10*70: 296 × 70 × 10, square, 2 mm from each column flange, 1 mm in from the flange edge, flush with the **BI312 web-side beam's** flanges (bottom at 3676: 4 mm below the 144 girder's bottom flange) | **186** at the BI312 beam on the column web (its shear tab FLT10*150, stiffener gaps 1 and 2): one rule for all 15; 144 would need a non-default 4 mm bottom gap at 6 of them and could not explain the other 9 |
| Gable columns EGC2002 (2) | 2 | FS / NS FLT10*90 at shear-plate joints | 186 (or 146) at those joints |
| Mezzanine column MBC2001 standing on the girders: End Plate PL16*120 | 2 | "Top / Bot Stiff." FLT10*50 | 144, column on beam (R04: row 15 "Cap 39") |

**Input bug found and fixed:** `r10_inputs.py` took the name "End Plate " (trailing space) as Cap 39, so all 8 PL16*120 end plates were rebuilt as T39 (row 15) and row 13 had no component. Now the 6 EB girder end plates are T144 (row 13); the 2 MBC2001 column end plates stay with row 15.

### Step 1: videos (tools/video, its first real use)
> **Gemini model used (2026-11-26):** gemini-flash-lite-latest read all three videos. gemini-flash's free tier (20 requests a day) was used up by the first attempts. When the quota resets, re-read the values marked one-source or unread with gemini-flash (`GEMINI_MODEL=gemini-flash-latest`).
- `find.py`: Tekla's own pages first (conn_144_help 2025 / 2024: the Stiffeners tab text, no embedded video or animation), then YouTube (Tekla's own channel "Tekla Software" has no 144 Stiffeners video). Gemini's Google Search grounding has no quota on this key, so `find.py` reads the pages directly.
- `gemini_read.py` on the 3 best candidates:
  - pass 1 at low resolution for the time spans;
  - pass 2 at high resolution on those spans, in 60 s pieces;
  - the free tier's 20 requests a day on gemini-flash were used up, so the lite model read them; the result records the model.
- What was read, and what two sources agree on: `tests/steel/videos/C13/VIDEO.txt`.

### Step 2: the Tekla package
- **NC 4512 (end plate):** 430 × 120 × 16, holes d18 at 30 / 100 / 330 / 400 × 30 / 90.
- **NC 4513 (Stiff.):** 55 × 50 × 10, clip 15 × 15, chamfer 12.5 × 12.5.
- **NC 4509 (186's column stiffener):** 296 × 70 × 10, with 5.6 corner chamfers and weld bevels, which R12's IFC solid does not show.
- Assembly drawings EB4002 and C2015.

### Step 3: the R12 inputs (row 13, 6 joints)
- **End plate:** t 16, ext 55 / 55 (1 = −55, 2 = −55), gap 0.
- **Bolts:** M16 8.8, 2 × 4, gauge 60, side 30, e 30, s = 70 230 70; Tekla bolt length (L50); bolting direction 2.
- **Haunch tab:** Top / Bottom plate 10 × 55 × 50 (b along the end plate, h along the girder), chamfer 12.5 line, created. These are R12's girder-side "Top Stiff." / "Bot Stiff.".
- **Stiffeners tab:** not created (186's, above).

### Step 4: 144 Stiffeners tab with Tekla's fields (web v70 section "C13", app `Fam.stiffeners`)
- **Fields:** Top NS / Top FS / Bottom NS / Bottom FS (t, b, h); orientation (parallel to the secondary / perpendicular to the main part); creation (Not created (v69) / Automatic / created / not created); shape (line chamfered / square / gap for the web rounding); positions 1–5; chamfer 1 / 2 (default the root radius) and chamfer type.
- **Geometry:**
  - a stiffener's outer face is flush with the secondary's flange outer face, less its gap 2–5;
  - it spans the main part's clear depth (h, blank = all, centred) and runs from the web face + gap 1 (+ the root radius for "gap") to the flange edge (b);
  - t defaults to the secondary's flange thickness;
  - it is trimmed at the flanges' inner faces (a tilted "parallel" stiffener);
  - near side = the main part's +es side.
- The default "Not created (v69)" keeps every existing 144; v69's own `stiff` stays for old models.

### Checks
| Check | Result |
|---|---|
| Web rebuild vs R12 (R12-REPORT rows 13 / 15) | 6 / 6 end plates and 12 / 12 Stiff. plates at box 0.0; +48 bolts with the same point, length and grade (M16 8.8 L50). Before: 0 end plates as 144 (2 of 8 matched as Cap 39), 12 Stiff. MISSING |
| App `R12C09Test` row 13 | plates 18 / 18, bolts 48 / 48, member bodies 12 / 12 (≤ 0.001 mm) |
| App vs web, whole R12 (`R12RebuildTest`) | 0 differences; the Cap 39 bolt difference seen with C02 was these end plates |
| Cases (`tools/golden/cases/t144.js` t144_c13_*) | R12's joint; HEA300 + IPE360 with the default stiffeners; HEM300 + IPE450 at 8°, parallel, "gap" shape, gaps 5 / 4 / 4, t 15, b 120; HEB400 + IPE500 at 5°, perpendicular, concave 20 × 15, automatic, h 300: 0 rule-B failures; app = web (`S7T144Test`) |
| Weights vs Tekla (st.tkW) | Stiff. plates 0.20 kg vs 0.21 (12 plates: 2.40 vs 2.52; the chamfer weighting differs, as C09); end plate 6.24 vs 6.48: Tekla does not take bolt holes off, and our rule only skips holes made by imported TKBOLT groups (open item for the Weights rule) |

**Emulator** (`C13RecordingTest`, CI 45875510138; image `docs/steel/parity/C13.png`):
- R12's joint through the dialog (Picture, Haunch, Stiffeners "not created", Bolts), Pick parts, orbit, saved `c13_case.ss`;
- then the Stiffeners tab created (square, t 10, b 70), Modify, orbit, saved `c13_stiff.ss`; then Top NS b 50, Modify, orbit;
- landscape orthographic stills. The activity now keeps its model on rotation (`configChanges`): before, turning the phone recreated it and lost the model.

### Status
Delivered; waiting for the owner's phone OK; not marked.

## C02h · Eave haunch (our own component), at each eave with End plate 144 (owner 2026-11-26)

### References
- **The owner:** the eave haunch is exploded in Tekla (plain plates, no component symbol). Tekla itself cannot say which component made it, so R12's geometry is the truth.
- **R12, all 48 eaves** (24 at the BI320 frame columns, 24 at the BI220 gable-end columns):
  - the web's far corner is **2,000 from the column axis** at both column sizes (1,820 / 1,870 from the end plate);
  - the web's plumb depth at the end plate (at the column) is 82.926 (BI320) / 82.900 (BI220): **one setting, 82.91**, reproduces both within 0.02;
  - the web is t 8, a triangle under the rafter's underside;
  - the flange is 10 × 150 along the web's lower edge, with plumb ends at the end plate and at the far corner;
  - welds (a 6): web to rafter flange, both sides, 1,866.4; web to end plate, both sides, 82.9; flange to web, all round, 3,789.1; flange to end plate, 2 × 71 on top and 150 underneath; flange end to rafter flange, both sides, 150;
  - no cut of the rafter.

### Step 1: Tekla components tried
- **Eaves haunch (102)** (help `conn_102_help_eaves_haunch`). Objects: end plate, compression / Morris / rib stiffeners, cap plate, vertical and horizontal haunch plates, bolts.
  - Its **Haunch length** ("horizontal distance from the center line of the column to the intersection between the haunch flange and the bottom flange of the rafter") fits R12 exactly: 2,000 at both column sizes.
  - None of its depth settings is one value over R12:
    - Picture 5 ("vertical dimension of the haunch plate from the rafter flange"; the picture draws it as the haunch's whole height, from the far corner down to the bottom at the end plate) is 496.72 / 508.06;
    - Parameters 2 ("from the intersection between the rafter and column center line to the bottom of the haunch plate flange") is 154.81 / 166.15 (219.48 / 217.01 extended to the column axis).
  - 102 also always makes its own end plate and stiffeners. R12's eaves have 144's end plate and none of 102's stiffeners.
  - So 102 reproduces R12 only with a different depth per column size, plus parts R12 does not have.
- **Haunch (40)** (help `conn_40_help`, read): a haunch made from a profile or plates under a beam. It is not an eave (column-to-rafter) component, and its settings are not from the column axis.
- **Videos:** the two eave videos (2FK1f5_Nzj4, MjAJSPlILsY) could not be read (Gemini dropped the connection or answered 503; YouTube blocked Apify). This is noted, not hidden.

### Step 2: our own component, "Eave haunch" (key Teh, code C02h)
The fewest fields that reproduce all 48:
- **L, Haunch length**: from the column axis, horizontal, to where the haunch flange meets the rafter's bottom flange (Tekla 102's own definition);
- **D, Depth at the column**: the web's plumb depth at the end plate, below the rafter's underside;
- **Haunch web t**;
- **Haunch flange t and width**.

R12's values are L 2,000, D 82.91, web 8 and flange 10 × 150; they are also the defaults.

### Input fix found on the way (all R12 built-up members)
`r10_inputs.py` read each plate's frame from Tekla's NC data (`ncF`), whose directions are rounded to 3 decimals (0.975, −0.222). A 6.3 m rafter's axis was therefore tilted 0.00033 rad against its own flanges (the "webGap −1.04"), so its underside at the eave was 1.07 mm low.

R12 frames now come from the plate outline (exact plane, and the outline edge along ncF's X). Every built-up web now sits on its flanges (webGap 0), and the rafter's underside at the eave equals Tekla's (7,326.68). No R12-REPORT row lost a match, and app C04 / C14 / C09 stay 528 / 528, 68 / 68 and 72 / 72.

### Change, in web v70 (section "C02h") and the app (`FamEH`)
- **New catalog component:** "Eave haunch" (key Teh, code C02h), in group Haunch and moment next to Eaves haunch (102). Main part = column, secondary = rafter.
- **Dialog:** Picture (L, D), Parts (Haunch web t; Haunch flange t, width; names Web / Bottom flange as R12), General, Welds.
- **Where the haunch starts:** at the back face of the End plate (144) joining the same column and rafter (column face + gap + shim / front + plate), else at the column face.
- **Parts:** web triangle (rafter underside at the start, the far corner on the underside at L, depth D plumb below the start); flange under its lower edge with plumb ends. Parts belong to the rafter's assembly. Welds as R12.

| Check | Result |
|---|---|
| Web rebuild vs R12 (`docs/steel/R12-REPORT.md` row 2) | 144 / 144 MATCHED: 48 end plates, 48 haunch webs, 48 haunch flanges (worst box 0.016 mm); 384 / 384 bolts |
| App `R12C09Test` (row "2h") | 96 / 96 haunch parts: box within 0.1 mm and the same solid (symmetric difference ≤ 0.1 mm × its surface) |
| Other sizes and slopes (`tools/golden/cases/teh.js`) | R12's eave; HEA300 / IPE360 at 10° (length 1,500, depth 200, flange 12 × 170); HEA240 / IPE300 at 15° with tp 25; IPE400 / IPE330 at 5° with no end plate: 0 rule-B failures each (`c09_cases.js` + `geom_checks.py`); app = web on all (`S7T144Test`, 13 cases) |
| Weights (`compare_v69.json`, v70 weight rule) | 48 webs 230.64 kg (Tekla st.tkW 230.64); 48 flanges 1,080.72 kg (Tekla 1,080.72): equal |

**Emulator** (`C02C09RecordingTest.c02Recording`, CI 45833553102): End plate 144 through its dialog (Picture, Haunch tab "not created", Bolts: Tekla length, direction 2), Pick parts; Eave haunch through its dialog (L 2000, D 82.91, web 8, flange 10 × 150), Pick parts; orbit, orthographic stills, model saved (`c02_case.ss`); then L 1500, Modify, orbit. Measured on the saved model: L 2000.00, D 82.91, start 180.0, flange 150, M20 8.8 L60, rule B 0 failures. Image: `docs/steel/parity/C02.png`.

### Status
Delivered; waiting for the owner's phone OK; not marked.

## Exploded connections in R12 (no component in Tekla, so the dialog cannot be proven by Tekla)
Tekla's IFC carries no component information. Which connections were components in the owner's model, and which were exploded, cannot be read from R12. Known so far:

| Connection | Status in Tekla | Evidence | Consequence |
|---|---|---|---|
| C02h eave haunch (row 2: Web FLT8*80 + Bottom flange FLT10*150, 48 + 48) | **exploded** | the owner, 2026-11-26 | our own Eave haunch: L as Tekla 102, D from R12 geometry only |
| Apex haunch (row 3: Web FLT8*130 + Bottom flange FLT10*150 + End Plate FLT20*400 + Stiff. FLT10*60) | **likely exploded** (to confirm) | same naming as the built-up rafter's own plates (Web / Bottom flange) and the eave haunch, in the rafter's assembly | as C02h |
| Built-up members (BI columns, rafters, beams, monitor posts: Web / Top flange / Bottom flange plates) | **likely exploded** (to confirm) | plain FLT / PL plates named Web / Top flange / Bottom flange, no profile | modelled as one BI member each |
| C09 monitor post haunch plates (HAUNCH_PLATE) | likely a component (144) | Tekla 144's default part name style (HAUNCH_PLATE) | 144 Haunch tab |

The owner can see the component symbols in Tekla. Rows marked "to confirm" wait for that.

## C08 · Sag rod at purlin: no nut or washer parts (owner 2026-11-28)

### Target: R13
- **R13 (and R12):** the 140 tie rods (ROD12, "Tie Rod THREAD100MM") are plain members. Tekla has no connection, bolt, nut or washer on them: no TKBOLT joins a rod, and Package_2's BOLT-LIST has no M12 nuts.
- **Purlins:** the purlins are drilled where the rods pass. Our Sag rod component already cuts that hole, and 220 / 220 purlins match R13.
- **Weight:** v69's Sag rod (Tsag) made a nut and a washer M12 as steel parts at each purlin web face: 560 + 560 parts, +22.40 kg that Tekla doesn't have.

### Change
- **Settings, not code:** the Sag rod component already has the Parts tab switches "Nuts" / "Washers" in web v70 and the app (FamSR), so no generator changes. v69's default (on) is kept for new models.
- **R13 inputs:** `tools/golden/r10_inputs.py` sets them off for models built from Tekla's IFC (R12, R13).
- **Dialog test tag:** dialog checkboxes now carry the test tag `tk_<key>` (CompUi.kt), so the emulator tests can set them.

### Result (R13 rebuild, web = app)
- **Extra parts:** v69 parts Tekla doesn't have went from 1,120 to 0.
- **Weight:** 64,025.41 → 64,003.01 kg (web; app 64,003.12), so −22.40 kg.
- **vs Tekla:** against `st.tkW` 64,017.49 kg: +7.92 → −14.48 kg (the parts still missing: C18 counter plates −9.04, C10 / C13 −3.44, tubes −2.33). Against the Part List 64,023.4: −20.39 kg.
- **Recording:** C08RecordingTest (Sag rod at purlin through its dialog: Nuts / Washers off, then on + Modify).

## C10 / C13 on R13: the last plates of rows 10 and 13 (owner 2026-11-28)

- **C10, the data quirk:** R13's monitor rafter end plate F7501 (PLATE FLT12*120) has the assembly mark '14' with no letters, so the inputs found no group (PR) and no member for it. Its 144 was never made, and PR7001 kept a square end. For a model built from Tekla's IFC, a plate whose mark has no letters now takes the nearest member within 200 mm as its own (`r10_inputs.py`). Result: the plate is matched, and PR7001 is matched, so 836 / 839 members match; the 3 left are agent R13's EB4007.
- **C13, MBC2001's column-top plates:** the two 'End Plate ' PL16*120 on top of MBC2001, under girders EB4001 / EB4002, were rebuilt as Seating (39). Their 4 'Top / Bot Stiff.' FLT10*50 were missing. The names prove they are Tekla's 144: the same 'End Plate ' (with Tekla's trailing space) and 'Top / Bot Stiff.' haunch tab plates as the 6 girder end plates of C13. They are now 144 (main part = the girder, secondary = the column) with the Haunch tab read from R13: 10 x 55 x 50, chamfer 12.5. Result: both plates and all 4 stiffeners match within 0.0 mm. No code change; the 144 Haunch tab is C09's.
- **R13 rebuild after C08, C10 and C13:** 3,615 detail parts matched, 90 near, 2 missing (agent C18's counter plates); web 64,005.59 kg, app 64,005.70 kg.

## X01 · Built-ups as three plates with end cuts, and Tekla's fittings (owner 2026-11-27)

### Target: R12
Every built-up member is three Tekla parts: Web, Top flange, Bottom flange (for example R3011: FLT8*200 + 2 FLT10*150, pos 3503 / 3502 / 3504). Tekla weighs each part as its own solid, without bolt holes. Across the 198 built-ups:
- **Weights.** Tekla's parts are lighter than the catalog kg/m × length that v69 used, because v69 counted none of the end cuts, copes or fits. That made the built-ups +257 kg too heavy.
- **Rafter apex ends.** The 96 rafters and monitor rafters are fitted to their end plates. Their plumb end reaches the plate face over the whole depth: R3011's top flange ends 4.5 mm past the rafter's end point and its bottom flange 45.4 mm short of it. v69 cut the member at the plate but kept its square end, so the top corner stopped short.
- **Monitor rafter eave ends.** The monitor rafters' other end is fitted to its 144 end plate the same way (the top flange reaches 2.1 mm past).

### Change, in web v70 (sections "Weights" → X01, "X01 · Fittings", "X02") and the app (`TeklaWeight.builtUp`, `Fits`, `StRegen.cutPart`, `ClassicV34.apex54`)
- **The three plates.** A BI member is weighed as its three plates. Each plate is the member's own solid (with every cut, fit, cope and hole) inside that plate's slab of the section, with its bolt holes filled. The slabs are sized to the member: a 1e6 mm box in float32 tilts its faces, which cost 0.05 kg per rafter.
- **Where the plates show.** The plates are also what the drawings and part lists show: `tkSubs` gives the plates as cut, not plain boxes, and now covers 4-number BI profiles as well. The weight report lists them as part rows.
- **Fittings.** A component can fit a member end to a plane. The member is built longer at that end, then cut by the plane, before the component's own cuts.
- **Apex fits.** Every Tekla apex (106 with a plate height) fits both rafters to their end plate faces.
- **End plate (144) fits.** 144 fits the secondary part to the end plate face. The face direction is taken toward the secondary's middle, because its end may lie on the main part's axis (crane brackets).
- **Golden case change.** The golden case `teh_hea240_ipe300_15deg_tp25` now puts its top bolt row 60 mm below the plate top. Once the rafter reaches the plate over its full depth, the default 35 mm put two bolts through its top flange (rule B).

### Result (R12 rebuild, web = app)
- **Members:** 835 / 836 match (every R, PR, PC and C built-up).
- **Built-up weight vs Tekla:** +257 kg → −2.3 kg.
- **Open:** PR7001 (the R12 data quirk above).

## X02 · Column tops and cap plates (owner 2026-11-27)

### Target: R12
- **Column tops.** At the 12 eave columns and the 24 monitor posts, the column top is cut along the rafter's top face: the plane of its top flange's upper side, extended over the column. For example, C2017 is at 7,547.7 at the face toward the rafter and 7,475.0 at the far face.
- **Cap plates.** There are 30 cap plates FLT8*150, which no Tekla component in the IFC made:
  - 18 sit on the interior columns: flat, centred, h − 2 tf long (200 / 300).
  - 12 sit on the eave columns, on the sloped top: 320 / 215 long, with the far end 5.64 mm (along the slope) inside the column's far face.

### Change
- **144 Plates tab.** New field "Main part end" = "Fitted to the secondary's top face" (`cfit: 'top'`).
- **New component: Column cap plate** (`Tcolcap`, group Other, family CAP). It places t × b × L on the column's end face, or on the beam's top face when a beam is picked. Defaults: L = h − 2 tf, b = flange width. e = the far end's distance inside the far face; empty means centred.
- **R12 inputs.** `cfit` is set where R12's column top slopes. Each R12 cap gives one Column cap plate, with L, b, t and e measured from R12.

### Result
- **Cap plates:** 30 / 30 match.
- **Column weights:** columns −0.21 kg, monitor posts +0.19 kg vs Tekla.

## C09 · End plate (144) Haunch tab at the monitor post, and C02 · 144 at the eave (owner 2026-11-26)

### References
- **Tekla's 144 help** (`conn_144_help`, Haunch tab): *Haunch plates*: Top plate and Bottom plate, each t, b, h; *Haunch plate chamfers* 1 (width, top), 2 (height, top), 3 (height, bottom), 4 (width, bottom) and *Chamfer type* (No chamfer / Line / Convex arc / Concave arc); *Haunch plate creation* (Automatic / Top and bottom haunch plates are created / Not created; t = 0 leaves one plate out). Part 4 of the picture is "Haunch plate".
- **Video** rf0oBDvnNjE 10:53–12:22 (Gemini; the Apify frames were blocked by YouTube that day): the Haunch tab with all boxes blank, then t set (plates created, their length = the end plate's reach past the flange), then b = 5" and 4". Notes in `tests/steel/videos/C09/`.
- **R12**: the 24 monitor-post joints (PC7001) each have two HAUNCH_PLATE t 8 (marks 7503 FLT8*60 and 7502 PL8*56.7) in the post's assembly. All 24 are the same:
  - in the post's web plane, standing on the end plate against each post flange;
  - bottom edge on the end plate, **b = 60 measured along the end plate**;
  - outer edge along the post; top edge square to the post at **h = 60** above the lower bottom corner;
  - outer top corner **chamfered 15 × 15** (line); inner corner (flange / end plate weld) **cut 15 × 15**.

### C02's tapered eave haunch is not a 144 feature: it is C02h (below)
Tekla's 144 Haunch tab makes only the haunch plates above. R12's eave haunch (48 × Web FLT8*80 + 48 × Bottom flange FLT10*150) is a tapered haunch under the rafter. In the owner's Tekla model it is **exploded** (plain plates, no component symbol; owner 2026-11-26), so it is the separate component C02h.

### Change, in web v70 (section "C09") and the app (`Fam.ep`, `Fam.haunchPlates`, `Fam.tkBoltLen`)
- **144 Haunch tab** with Tekla's fields: Top plate / Bottom plate t, b, h (part name HAUNCH_PLATE); chamfers 1–4 and chamfer type; Haunch plate creation.
  - Blank b = the end plate's reach past the flange (the video); blank h = b.
  - Automatic = a plate where the end plate reaches more than 10 mm past the flange.
  - Across a slope (the end plate tilted square to the plate's plane) the plate is fitted to the end plate: its bottom is trimmed at the end plate's back face.
  - The new default 'Not created (v69)' keeps every existing 144 joint unchanged. v69's own 'comp' (a 250 mm FLANGE PLATE) stays for old models that set it; the dialog no longer shows it.
- **Bolts tab, Bolt assembly: 'Bolt length'** = v69 (grip + 2 + 2 d; the default and old models) / **Tekla**. Tekla's rule fits all 1,288 R12 bolt groups: grip = the plate stack, length = grip + nut (ISO 4032 m) + washer (ISO 7089) + 5, rounded up to 5 mm (M12 8 → 30, 15.5 → 35; M16 16 → 40, 18–22 → 45, 24–26.7 → 50; M20 30 → 60, 40 → 70). With 'Bolting direction 2' (Tekla's heads on the other side), C09's bolts are M16 L45 with heads under the rafter flange and C02's are M20 L60.

### Search result: the R12 inputs (`tools/golden/r10_inputs.py`)
- **Row 9:**
  - Haunch tab: both plates t 8, b 60, h 60, chamfers 15, Line, created;
  - Bolts: Tekla length, direction 2;
  - gap 0, ext 55.36, extb 60.81 (with the exact plate frames of C02h; before that fix the nominal rafter face was 0.75 mm off Tekla's flange plate and the inputs needed gap −0.75).
- **Row 2:** ext 10, extb 104.39, gap 0; Bolts: Tekla length, direction 2.

| Check | Result |
|---|---|
| Web rebuild vs R12 (`docs/steel/R12-REPORT.md`) | C09: 24 / 24 end plates and 48 / 48 haunch plates MATCHED (≤ 0.044 mm), was 24 NEAR 0.8 + 48 MISSING; C02: 48 / 48 end plates; bolts with the same point, axis, length and grade +576 (C09 192, C02 384) |
| App `R12C09Test` | C09: plates 72 / 72, bolts 192 / 192, member bodies 48 / 48; C02: plates 48 / 48, bolts 384 / 384, member bodies 96 / 96 |
| Member bodies | the post within 0.003 mm. The built-up rafters and columns differ from R12 by 0.15–0.99 mm averaged over their faces **with or without the connection**: their own section (web 1.04 mm off the flanges, flange plates not parallel to the axis; R12-REPORT "INPUT EQUAL, SOLID DIFFERS"). The test also checks that the connection's cut and holes remove no material Tekla keeps (≤ 28 mm³ of 19,000–114,000 mm³ removed) |
| Other sizes and slopes (`tools/golden/cases/t144.js`, `tools/golden/c09_cases.js` + `geom_checks.py`) | R12's post joint; HEA160 on IPE300 at 5° (b, h blank); IPE200 on IPE360 at 10°, Automatic, convex; IPE240 on a level HEB300, concave; R12's eave (C02): 0 rule-B failures each; app = web on all (`S7T144Test`, 8 cases) |
| Weights | Haunch plate 0.18 kg each (= Tekla's own IFC solid, 0.182 kg); Tekla's st.tkW says 0.17 (48 plates: 8.74 vs 8.16 kg). Not explained yet: Tekla's IFC solid itself weighs 0.18 |

**Emulator** (`C02C09RecordingTest.c09Recording`, CI 45833553102): End plate 144 through its dialog (Picture; Haunch tab 8 × 60 × 60, chamfers 15 line, created; Bolts: Tekla length, direction 2), Pick parts; orbit, orthographic stills, model saved (`c09_case.ss`); then Top plate b 40, Modify, orbit. Measured on the saved model: haunch plates 58.5 × 60.0 and 58.5 × 56.7 (as R12), end plate 265.9 × 71.6, M16 8.8 L45, rule B 0 failures. Image: `docs/steel/parity/C09.png`.

### Status
C09: owner OK on the work (2026-11-26); delivered, waiting for the phone OK; not marked. C02: the end plate and bolts with C02h (above).

## C14 · Shear plate (146), joist ← girder (reverse-engineered on R12, owner 2026-11-26)

### Starting settings: the video (reference order 3)
- Video: https://www.youtube.com/watch?v=MrhM4X9opLo (imperial units). Evidence is in `tests/steel/videos/C14/`.
- Gemini gives the dialog's timestamps: Picture 1:18, Plates 2:21, Notch 9:37, Bolts 13:13–15:48.
- Apify frames (`emttn/frameskim`, 12:50–16:10) show the Bolts tab: the bolt group's vertical position is a dimension with a reference list set to **Top**.
- Cross-check: frame 14:50 reads 3"1/2 with Top. Gemini gave 1 1/2", so its value is not used; the reference "Top" agrees in both.

### Target: R12 (68 joist tabs FLT10*130, mark 4515, on 59 girders 430 deep and 9 girders 320 deep)
**Fixed relative to the joist** (the same on both girder depths, so any rule measured from the girder is ruled out):
- top bolt row 77.2 below the joist top;
- bottom row 72.8 above the joist bottom;
- rows 70 apart;
- tab 130 tall, top 47.2 below the joist top (vertical edge 30).

**Along the joist:**
- the joist end is 20 from the girder face;
- the bolt columns are 35 from the joist end and 60 apart;
- the tab runs 35 beyond the last column (horizontal edge 35);
- the tab is 150 long from the girder face.

**Bolts:**
- M16 8.8 L45, grip 18 (tab 10 + joist web 8);
- the length follows ⌈(grip + 1.5·d + 2) / 5⌉ × 5, the same rule as C04 (M12, 8 → 30);
- the heads are on the left looking along the joist from the girder: on the tab for 34 tabs, on the joist web for the other 34.

**What R12 cannot decide:** every joist has the same depth (220), so "Top 77.2" and "Middle, 2.2 below the centre" both reproduce R12. The video's setting, Top, is used.

### Change, in web v70 (section "C14") and the app (`Fam.st`)
- **146 Bolts tab:**
  - 'Vertical measurement': Centred (v69, the default) / Top / Middle / Below;
  - 'Vertical position' (dimension 1);
  - 'Horizontal edge distance' (dimension 5).
- **Where they are measured:** on the secondary's own section, not its coped zone. Measured from the cope, all 68 tabs were 20 mm off.
- **When a vertical measurement is set:** the grip is the plate stack, Tekla's bolt length applies, and the heads go on the Y × X side.
- **Old models and the default:** they keep v69's centred group.

### Search result: the R12 inputs
Row 14 now has refV Top, posV 77.2, e 30, eh 35, gap 20, M16, 2 × 2 at 70 / 60.

| Check | Result |
|---|---|
| Web rebuild vs R12 (`docs/steel/R12-REPORT.md`) | 68 / 68 tabs, 272 / 272 bolts (point, axis, length, grade) |
| App `R12C14Test` | the same, within 0.1 mm |
| Member bodies within 300 mm of each tab (`tools/golden/c14_check.py`, `R12C14Test`) | joists 68 / 68; girders 66 / 68 |
| Other sizes (`tools/golden/c14_cases.js`): R12's built-up pair, IPE450 + IPE240, HEA300 + IPE300 (joist 40 below) | top bolt 77.2 below the joist top, tab 47.2–177.2, L45, 0 rule-B failures; app = web on these and on Middle / Below cases (`CompGoldenTest`, 83 cases) |

The two girders that differ are girder ends cut by the column's own shear plate (row 12), which is not built in the C14-only run.

**Emulator:** `C14RecordingTest` goes through the real dialog (Picture, Plates, Bolts tabs), picks the girder and joist, orbits, takes orthographic stills, saves the model, then changes the reference to Middle and runs Modify.

### Status
Waiting for the owner's phone OK; not marked.

## C04 · Purlin cleat (Tcl, Tekla "Cold rolled overlap (1)")

### C04 on R12 (owner 2026-11-26)
**R11 had the purlins turned 180°.** R11's 220 purlins (from R10) are turned 180° about their axis compared with Tekla (R12: the web is on the other side, 220 / 220). That put R11's cleats on the wrong side.

**Tekla's rule, measured on R12** (264 joints):
- The cleat is on the web's back (Back).
- Its underside is the purlin's underside, within 0.02 mm at every joint.
- Tekla's built-up rafter flange is not quite parallel to the rafter axis, so a cleat placed from the rafter axis drifts up to 2 mm along a rafter.

**Change, in web v70 and the app (`FamCL.back`):**
- **Cleat level:** the cleat's underside is the purlin section's lowest point along the rafter normal.
- **One cleat per joint:** adding a purlin cleat L on a rafter and purlin(s) removes the purlin generator's own plate cleat (Tpc) on that rafter and those purlins (web `stConnAdd`, app `Components.add`).
- **Purlin application dialog:** the Profile field is text, so a typed CC200-2-20-60 is used. In v69 it was a number box, and a typed name was dropped.

**Checks:**
- Web rebuild vs R12 (`docs/steel/R12-REPORT.md`): 528 / 528 parts, 1,056 / 1,056 bolts (position, length, grade).
- App `R12C04Test`: 528 / 528 parts and 1,056 / 1,056 bolts within 0.1 mm.
- Workflow case (default RAFTER set, purlin application with CC200-2-20-60, C04): 0 rule-B failures in web and app; web = app 0.0 mm; purlin underside − rafter top 0.00 mm.
- Emulator: `C04RecordingTest.c04Recording` follows the same workflow through the dialogs and saves the model it built (`c04/c04_workflow.ss`), which is measured again.

> **Superseded 2026-11-25 by Tekla's package (R11 fix 1 above).** The "Front = toe side" rule below was read from R10, and R10 was wrong there (an importer error: Tekla's bolt list has M12×30, and its drawings show the cleat against the web). **v70 now:** Cleat location **Back** is the default for new cleats and is Tekla's cleat: v69's angle and side, NC 8504's stiffener, bolts from the cleat face, M12 4.6 L30 (`web/v70_tekla_parity.js`, `FamCL.kt` `back()`). **v69** (or no `loc` = an old model) keeps the result from before v70. The Front placement is removed. Result against R11: 528 / 528 parts, 1,056 / 1,056 bolts within 0.1 mm (`R11C04Test`); with v69's placement 264 / 528 (the angles match, the stiffeners don't), 960 / 1,056 bolts. The text below is kept as the record of the R10 analysis.

**Rule (one line):** Tekla's *Cleat location* is **Front** by default. On a C / U purlin, Front means the cleat stands on the purlin's **toe (open) side, whatever the slope**: its long leg sits on the toe line and its short leg is at the top, pointing away. The angle is placed like a Tekla L member at Middle / Middle, so on a slope it shifts a few mm. **Back** = v69: behind the web.

### Tekla help (text)
The Cold rolled overlap (1) page (Tekla Structures 2026; support.tekla.com redirects to help.trimble.com), read as text:
- Objects created: "Angle cleat, Stiffener, Stays, Bolts, Welds". Selection order: "Select the main part (rafter or stanchion). Select the secondary parts (one or two purlins)."
- Cleat tab, **Cleat location**, Default: "The cleat is attached to the front of the purlin. Tekla Structures positions the cleat according to the purlin profile that is used. AutoDefaults can change this option." Options: "The cleat is attached to the front of the purlin." / "The cleat is attached to the back of the purlin."
- Before you start: "Create the purlins using alternate rotations, first top, then bottom, and so on."

The page **does not say in words which side "front" is**; that is in its pictures, which I did not use (text only). The page also gives no rule based on the slope. Neither "uphill" nor "downhill" appears on it. So which side Front is comes from R10, below.

### R10 (264 cleats = 528 parts, 1,056 bolts)
Measured with R10's own meshes: the purlin's web is the side of the section with no lip vertices, and positions are taken in each purlin's frame. Script: `python3 tools/golden/r10_c04_proof.py` (prints this table, the stiffener outlines and the bolt groups). The golden data is `tests/steel/golden/r10/c04_r10.json` (`tools/golden/r10_c04.py`). Every purlin is `CC200-2-20-60` (b = 60).

| Support | purlin rAng | toes point | cleats | cleat rAng (R10) | long leg, from the web back | cleat height, from the support face | shift predicted by the L rule (toe, down) |
|---|---|---|---|---|---|---|---|
| R rafter, 12.81° | 347.19 | downhill | 120 (80 with 2 purlins) | −167.19 | 61.3 … 121.3 | −5.8 … 134.2 | 1.3, 5.8 |
| R rafter, 12.81° | 12.81 | uphill | 96 (64 with 2 purlins) | 167.19 | 74.5 … 134.5 | −3.3 … 136.7 | 14.5, 3.3 |
| PR rafter, 15.03° | 344.97 | downhill | 24 (16 with 2 purlins) | −164.97 | 61.8 … 121.8 | −6.8 … 133.2 | 1.8, 6.8 |
| PC column side | 270 | down | 24 (16 with 2 purlins) | −90 | 60.0 … 120.0 | 0.0 … 140.0 | 0, 0 |

- **Side:** all 264 cleats are on the toe side, at 60 mm (= b) or more from the web back, toward the lips. That holds for toes uphill (96) and toes downhill (168). So the side follows the profile (C toes) and not the slope or the purlin direction, which matches "according to the purlin profile". Not one R10 cleat is behind the web (v69's side).
- **Shape:** the 140 leg stands on the toe line (6 thick), and the 60 leg is at its top (heel up), pointing away from the purlin. The L's box is 60 × 140 × 120 (w), centred on the frame line (rafter web ± 60 along the purlin).
- **Where Tekla puts the L:** every R10 cleat has the same member axis relative to its purlin (`st.p1`: 90 mm from the web back toward the toes, 30 mm along the depth), i.e. the L's own box centre on the toe line + 30 / rafter top + 70. The meshes are moved off that by the four shifts above. Each shift equals, **to 0.1 mm**, what you get when the L 60 × 140 × 6 is turned by its rAng and then its **bounding box in the part's Top frame** is centred on the axis: (1.3, 5.8), (14.5, 3.3), (1.8, 6.8), (0, 0). Tekla's Middle / Middle uses the box of the turned profile, not the L's own box.
- **Stiffener `Stiff.` PL6*44 (264):** the same in all four groups. It lies in the plane of the long leg's outer face (toe line + 6, depth Middle, so it spans 3 … 9 from the toe line). Outline (height above the support face, along the purlin from the cleat centre): (135, 22) (21, 22) (11, 12) (11, −22) (21, −22) (135, 12). It is not shifted (it is a plate).
- **Bolts:** 528 groups of 2 × M12 8.8 **L144**. They start on the purlin's web back face and point toward the toes; heights 40 / 100 above the support (h1 / h2); ±35 from the frame line (e). Each purlin gets its own group on its side.

### Change
- **Web (v70 chapter):** `CONN.Tcl.def.loc = 'front'`, so new cleats get Front (`stCQ` copies the default into `c.q`). The Tekla dialog's Parts tab, Cleat group, gets **Cleat location** (Front / Back), and `ST_LBL.loc` is added. `FAM.CL` is wrapped: when the **stored** `c.q.loc` is `'front'` and the purlin is C / U, the new placement runs; otherwise the v69 chain runs. `famGeom` merges `def` into `q` again at regen, so the wrapper reads `c.q` and not `q`: a model saved before v70 has no `loc` and stays v69.
- **New placement:** frame at the rafter top on the toe line (x = toward the toes, y = up from the support face, z along the purlin). L outline `[0,0] [t,0] [t,L1−t] [L2,L1−t] [L2,L1] [0,L1]`, z ±w/2, moved by (own box centre − bounding box centre in the Top frame: y = global Z made square to the purlin, z = x × y). The stiffener uses the R10 outline. Bolts start at the web back (x = −b) and point toward the toes, with grip = b + L2, so L = b + L2 + 2·d = 144 as R10. Holes go in the cleat and in each purlin. The weld is as in v69.
- **App:** `steel/.../conn/FamCL.kt` `front()` makes the same steps in the same order, and reads `conn.q["loc"]`. `tk_ui.json` / `tk_catalog.json` are re-exported from the v70 page (`tools/golden/s7_catalog.js`, `data_export.js`): Tcl `def.loc = "front"` and the dialog field, so the app's dialog shows the row with no Kotlin UI code. Z and I purlins are unchanged (q.side).

### Tests
- **`R10C04Test` (app vs R10, -PhostJni):** the 835 members, then only the 264 Tcl components with R10's inputs and the new default. Result: **528 / 528 parts (box 0.1 mm on all six numbers, volume within 2 %) and 1,056 / 1,056 bolt positions within 0.1 mm**. With `loc: 'back'`, and with settings stored without `loc` (an old model), 0 / 528 and 0 / 1,056, i.e. v69.
- **Web vs R10:** `docs/steel/R10-REPORT.md` row 4: **528 matched (was 0)**, M12 8.8 L144 1,056 / 1,056 (the v69 rebuild had M12 L34). All other rows are unchanged. Weight 62,143.16 → 62,145.80 kg (the holes).
- **App = web:** `R10RebuildTest` (all 6,327 bodies, 0 differences). `CompGoldenTest` CL 15 / 15: `tests/steel/golden/comp/s8_cp_cl_sr_tg.json` was regenerated through v70. `tcl_odd_profile` (C200 purlin) is now Front. New cases: `tcl_odd_profile_back` (= the v69 golden of `tcl_odd_profile`, box for box), and four R10 cleats as they are in the model (toes down, toes up, column, 15° slope), plus one purlin and Back. `S7DialogTest`: `tcl_modify_loc_back` and `tcl_apply_loc_front` through the web dialog's DOM and the app's CompDialog. `EmulatorFlowTest` step `cleat_dialog`: the Parts tab shows "Cleat location" with "Front".

### Video reference (standing rule step 1/3)
Links (found by search; none could be played here): [a5N_x5zrTYk "Tekla Structures Create C Purlin & Connected with Rafter"](https://www.youtube.com/watch?v=a5N_x5zrTYk) (kept: the only frame with a readable cleat), [CbOBUXL_4EI "Correctly modelling Cold Rolled Overlap"](https://www.youtube.com/watch?v=CbOBUXL_4EI), [froi0jRFbCM "C Purlins connection to rafter … cold rolled overlap"](https://www.youtube.com/watch?v=froi0jRFbCM).
- Access: yt-dlp → "Sign in to confirm you're not a bot" / HTTP 403 on the video data; Playwright on the watch page → reCAPTCHA "unusual traffic" page (not solved); signed storyboards → 403. Only the public thumbnails load (cover + YouTube's frames at ≈25 / 50 / 75 % of the video, 480×360). Exact times are unknown (no player, no captions).
- Frames kept in `tests/steel/videos/C04/`: `a5N_x5zrTYk_~50pct_hq2.jpg` (+ `…_cleat_zoom4x.png`), `a5N_x5zrTYk_cover.jpg`, `CbOBUXL_4EI_~50pct_hq2.jpg`, `froi0jRFbCM_~50pct_hq2.jpg`.
- **What the readable frame shows (a5N ≈50 %):** an L cleat with its **short leg flat on the rafter's top flange** and its **tall leg against the purlin with 2 bolts**. That is the shape of v69's placement (Back in the app: bolts through the purlin web), **not** the R10-mesh shape used for Front (long leg upright on the C toe line, 60 mm from the web, short leg at the top, bolts crossing the open C). Which side of the purlin (toes or web back) cannot be read at 480×360.
- `docs/steel/parity/C04.png`: video frames | app (Front 3D, Back 3D, Front end view). Verdict: **the video contradicts R10's cleat meshes on the cleat shape**; owner decision needed (PROGRESS.md, Decisions). Purlin on the rafter: in the app the purlin bottom is 1.4 mm from the rafter top at the cleat (sits on it).

### Purlin body and bolts vs R10 (owner question 2026-11-25: is the app's C mirrored?)
- **Not mirrored.** All 220 R10 C purlins: the web is on the same side in R10's Tekla mesh, in the web build's solid (`tools/golden/r10_purlin_web.js` → `tests/steel/golden/r10/purlin_web.json`: 0 / 220 mirrored, web faces within 0.003 mm) and in the app's solid (`R10PurlinBodyTest`, -PhostJni: **220 / 220**, web side, web face 0.1 mm, box 0.1 mm).
- **So R10 itself has the C04 cleat on the open side of the C**, 60 mm from the web: its 1,056 C04 bolts pass through holes in the purlin web and in the cleat, with **58–72 mm of air** between them inside the C (`tools/golden/bolt_hole_check.py`). Front copies that (528 / 528 parts, 1,056 / 1,056 bolts). The video (a5N_x5zrTYk) shows the cleat against the web, as v69 / Back. R10 and the video disagree; the app does not mirror anything.
- **Bolt check** (new, `python3 tools/golden/bolt_hole_check.py --report` → `docs/steel/BOLT-HOLES.md`): every bolt of every R10 TKBOLT connection must pass through a hole in each part it joins. R10: 3,035 connections, 17,248 bolt × part checks: 16,685 HOLE, 221 NO HOLE (bolt through solid steel), 342 MISSES (bolt does not reach the part); all failures are node gusset plates (Gusset Plate PL12*2xx / 3xx) and one stair plate (2). Air along the shank: C04 cleats 58–72 mm (1,056 bolts), stair brackets 130 mm (92).

### Is R10 wrong at C04? (owner 2026-11-25: fix the data as R11 only if proven from Tekla's own data)
**Not provable with the data here; nothing changed (no R11).**
- R10's bolt data is not Tekla's: in 1,679 of 1,693 bolt groups L = grip + 2·d exactly (the other 14 within 0.5 mm), with lengths no Tekla bolt catalogue gives (M12 L144, M16 L56 / L59, M12 L169). The grip is the span of the joined parts along the bolt on R10's own geometry (C04: 120 = web back face to the far side of the cleat; stair brackets: 145). So L144 / grip 120 repeat the importer's geometry and cannot show what Tekla had.
- The only Tekla figures in the repo (`tests/steel/models/Frame_28x53m_R04_vs_Tekla.md`) are counts: Tekla M12 = 1,148 = R10's 1,056 C04 bolts + 92 stair bracket bolts; parts "8503 L140*60*6 L120, 8504 PL6 124×44, 4 M12". No bolt lengths, no drawings, no NC.
- NC would not decide it either: the holes in the purlin web and in the cleat are the same in both placements; only the bolt length (bolt list) or an assembly drawing / section of the cleat shows the gap.
- Pointer (not proof): the two places where bolts cross air are both L-angle members the importer had to place by solving their rotation (`tk_how: 'solved'`): the C04 cleats (58–72 mm) and the stair BRACKET L60*6 (130 mm). That pattern fits an importer error on L members, but it is not Tekla data.
- **Needed to decide:** Tekla's bolt list (M12 lengths for the purlin cleats), or the assembly drawing of a rafter (R3022 / PR / PC) with the cleat section, or the `.db1`. With an M12 length of about 30–40 mm the cleat sits against the web (R11 then moves the 264 cleats, stiffeners and 528 bolt groups, and Front = v69's side); with about 140–150 mm Tekla really had the gap and R10 stays.

### C04 follow-up: "the purlin floats above the cleat" (owner 2026-11-25)
- **Cause: the test case's input, not the placement rule and not the component.** C04RecordingTest (and the S7 dialog case `r10cl`) built the rafter from the RAFTER property set with only prof / p1 / p2. That set has **depth Behind** (the beam below its line), while R11's rafter has depth Middle, so the rafter sat 110 mm (half of BI220) lower. The dialog case's purlin also took the PURLIN set's depth Front. The cleat followed the rafter, so the purlin looked lifted by about a cleat height.
- **Measured** (`C04MemberGapTest`, perpendicular to the rafter at cleat 3185's joint): recording inputs **+110.00 mm** without C04 and **+110.00 mm** with C04. R11 inputs **0.00 mm** without and with C04. R11 file **0.00 mm**. C04 moves no member, and the app places members exactly as R11 when given R11's fields. End views: `docs/steel/parity/C04_gap.png` (`tools/golden/c04_gap_view.py`).
- **Fix:** both cases now use R11's position fields (rot / rAng / plane / depth). The S7 dialog golden was regenerated.
- **New check:** `R11MemberBodyTest`. At all 264 C04 joints, the app's member bodies vs R11 within 0.1 mm: purlin boxes, rafter top and purlin underside perpendicular to the support. Result: **264 / 264**.

### C04 workflow (owner 2026-11-25, part A): the user's own way, not R11's fields
Case (`tools/golden/c04_workflow.js` in web v70, `C04WorkflowTest` in the app): two rafters from the **default RAFTER set** (IPE360, **depth Behind**) on a 12.8° slope, purlins by **Generation of purlins (50)** (T50p, profile CC200-2-20-60 chosen in its dialog), then C04 (Tcl) on rafter A and the middle purlin. Measured perpendicular to rafter A (`tools/golden/c04_measure.py`; end views `docs/steel/parity/C04_workflow.png`):
- **plain v69:** the purlins are **IPE160** (the PURLIN property set overrode the profile chosen in the dialog), upright, and **cut into the rafter by 13.56 mm** (lowest bottom corner) and 1.00 mm (the other). They don't float: the depth Behind rafter is placed right (v69's member frame uses the real section centre), but the generator put the purlin line at centre + h/2 straight up and kept the purlins vertical.
- **fix (web v70 chapter "Generation of purlins" + app `GenRun`):** the profile is the dialog's (the PURLIN set's when blank); each purlin's section centre is at rafter top + h/2 perpendicular to the rafter, and the section is turned so its web stands perpendicular to the rafters (rAng −12.80° here, as R11's purlins). Purlins between vertical members keep v69's purlins.
- **after:** web v70 and app **0.00 / 0.00 mm** (both bottom corners) on every purlin; profile CC200-2-20-60; the C04 bolts pass the purlin web (22 bolts through holes, no air).
- **B checks** (geom_checks): plain v69 26 failures (rafter / purlin and purlin / cleat overlaps); web v70 and app **2**: at the C04 joint the generator's own **Purlin cleat (Tpc, PL8)** overlaps the C04 cleat and stiffener. T50p puts a Tpc cleat on every purlin end, so adding C04 there doubles the cleat. **Owner decision needed** (see PROGRESS).

### Status (standing rule)
1 links sent (YouTube blocks downloads from the container: owner sends the .mp4) · 2 done (00318c0) · 3 R10 golden ✓, video frames vs emulator: waiting for the video · 4 delivery: see PROGRESS.md · 5 ✓: waiting for the owner's phone OK.

### Unclear / not proven
- **Z and I purlins:** R10 has only C purlins, and the Tekla text does not say where Front is for Z or I profiles. v70 leaves them as v69 (`q.side`). Front / Back has no effect on them.
- **Stiffener outline:** this is R10's, for L140*60*6 with ts = 6 only. The rule for other leg lengths is not shown (the outline is kept in mm and does not scale with L1 / L2).
- **Bolt length:** R10's L144 fits both b + L2 + 2·d and 2·b + 2·d (b = L2 = 60 in R10). v70 uses b + L2 (the bolt then always reaches the shifted leg). R10 cannot tell the two apart.
- **The 3 mm overlap:** in R10 the stiffener is half inside the long leg (Tekla's depth Middle on the leg face), and on a slope the shifted leg goes up to 6.8 mm into the rafter flange. v70 copies both as they are.
