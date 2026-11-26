# Tekla parity: web v70 chapter and app, against Tekla (R12)

Each section covers one connection where v69 differs from Tekla (shown by `tests/steel/models/Frame_28x53m_R10.ss` and Tekla's help). It gives the rule, the proof, the change and the tests. The web change is the chapter **v70 · Tekla parity** (`web/v70_tekla_parity.js`). It goes into the reference copy just before the `v28 · BIM layers` chapter: `python3 tools/web/build_v70.py` writes `reference/solidsketch_v70.html`, and the golden harness (`tools/golden/lib/v69.js`) adds the same text in memory, so every golden made from now on comes from v69 + v70 (`SS_ORACLE=v69` runs plain v69). The app port follows the same steps in the same order. Models saved before v70 keep their v69 result unless the new option is set.

## Reference model: R12 (owner, 2026-11-26)
`tests/steel/models/Frame_28x53m_R12.ss`, built by `tools/golden/r12_build.py` from Tekla's own IFC export (`tests/steel/tekla/Frame_28x53m_tekla_v2.ifc`), is the reference for everything: golden tests, `docs/steel/R12-REPORT.md`, rule B and the weight target. See `docs/steel/R12.md` for how it is built and how it differs from R11. R10 and R11 stay unchanged, for history.

**Weight target:** Tekla's per-part weights (`st.tkW`). They sum to 63,705.8 kg; Tekla's Part List total is 63,711.7 kg.

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

## Reference order (owner, 2026-11-26)
When sources disagree, the higher one wins:
1. **Tekla itself:** R12 (Tekla's IFC) and Tekla's dialog settings.
2. **The Tekla package:** drawings, NC files, bolt list and reports (`tests/steel/tekla/Frame_28mx53m_Package_1.zip`).
3. **A YouTube video of the component:**
   - frames via the Apify actor `emttn/frameskim`;
   - timestamps and dialog values via the Gemini API, given the YouTube URL;
   - Gemini's values are cross-checked with the frames before use.

   `APIFY_TOKEN` and `GEMINI_API_KEY` are set in the environment. Never print them, never write them to a file or a log.

## Reverse-engineering method, per connection (owner, 2026-11-26)
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
