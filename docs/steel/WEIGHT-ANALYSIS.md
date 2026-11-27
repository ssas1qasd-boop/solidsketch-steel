# Weight analysis: the R12 rebuild vs Tekla, part by part

Written 2026-11-27. This is a read-only investigation: no code and no other file was changed.

## History
- **v1** (C20 snapshot, before ce01af4): app 63,495.66 kg, Tekla 63,705.82, **Δ −210.16**. Bolt holes were taken off component parts, and the grout was weighed as S275.
- **v2** (ce01af4: gross rule `holeFill70` / `TeklaWeight.holeFill`, grout weighs 0, stair-post 146 bolts L40): app 62,516.10 kg (web; the app reports 62,517.22), Tekla 63,705.82, **Δ −1,189.72**. This is the current state. All the tables below are for v2 unless they say v1.

**Compared:**
- **Tekla:** each R12 part's own Tekla weight (`st.tkW` in `tests/steel/models/Frame_28x53m_R12.ss`). There are 4,945 parts and they sum to **63,705.82 kg**. Tekla's Part List total is 63,711.7 kg.
- **App:** the web rebuild in the v2 snapshot (`rebuild_v69.json`, `compare_v69.json`, `rebuild_inputs.json`, `R12_app.ss` after ce01af4). The app and the web build give the same rebuild body by body (`R11RebuildTest`). The total is **62,516.10 kg**, bolts excluded. The app's own total is 62,517.22, 1.12 kg above the web; I did not look into that gap.
- **Difference:** app − Tekla = **−1,189.72 kg**. The groups below add up to exactly this figure (residual 0.00 kg).

## Summary (v2)

The gross rule and the grout fix did what was expected. **D1 (grout) is now 0.00, and D2 (the same component part) is +0.98 kg on 1,219 parts**, which is Tekla's rounding and two outline differences, not a weight-rule problem. What is left is the real gap: missing parts and parts made differently.

The predicted Δ was −1,181.72 and the actual is −1,189.72. The 8.00 kg between them is mostly **one gap in the hole fill**: Ttg node gussets (`GUSSET PLATE` PL12) get back only the holes of one tongue. Of 62 gussets, 56 still have their other tongues' holes taken off, about **8.1 kg** in total. For example, gusset 2851 (connection 612) has 12 holes and gains only 0.09 kg, while its estimated holes weigh 0.275 kg. The rest (+1.1 kg) is the error of my v1 hole estimate.

| # | Group | R12 parts | App parts | kg R12 (Tekla) | kg app | Δ kg (v2) | Δ v1 | Fix / code |
|---|---|---|---|---|---|---|---|---|
| A1 | (a) Anchor rods ROD20 made as a bolt group (bolts are not weighed) | 192 | 0 | 293.76 | 0.00 | −293.76 | −293.76 | C01: 1042 "anchor rods as parts" |
| A2 | (a) Anchor nuts and washers PD40*10 not made | 384 | 0 | 40.32 | 0.00 | −40.32 | −40.32 | C01: nut and washer parts per anchor |
| A3 | (a) Anchor rods ROD16 made as a bolt group | 16 | 0 | 6.72 | 0.00 | −6.72 | −6.72 | C16: 1004 anchor rods as parts |
| A4 | (a) Apex stiffener FLT10*60 not made | 48 | 0 | 16.80 | 0.00 | −16.80 | −16.80 | C03: apex stiffener option |
| A5 | (a) Girder flanges EB4006 / EB4008 (R12 has flanges but no web, so no member was placed) | 6 | 0 | 760.50 | 0.00 | −760.50 | −760.50 | X01 / R12 data: place them as built-ups without a web |
| A6 | (a) Column top cap plates FLT8*150 (no component makes them) | 30 | 0 | 72.63 | 0.00 | −72.63 | −72.63 | Not in C01–C20: needs a cap-plate option |
| A7 | (a) Parts with no secondary part to pick (R12 data) | 25 | 0 | 40.91 | 0.00 | −40.91 | −40.91 | C10, C12, C13, C17, C20 (follows A5) |
| B1 | (a)+(b) Tube gusset and crossing: other parts and outlines | 1,662 | 1,668 | 4,044.25 | 3,649.59 | −394.66 | −459.74 | C05, C06, C07 (+ the node-gusset hole fill) |
| B2 | (a)+(b) Stairs: gratings, brackets and stringer plates vs the app's treads | 79 | 64 | 184.72 | 412.56 | +227.84 | +227.68 | C18 |
| C1 | (b) Sag-rod nuts and washers M12 (Tekla has none) | 0 | 1,120 | 0.00 | 22.40 | +22.40 | +22.40 | C08: nut / washer off by default |
| D1 | (c) Grout | 52 | 52 | 0.00 | 0.00 | **0.00** | +1,131.76 | Done (ce01af4) |
| D2 | (c) The same component part: rounding and outline | 1,219 | 1,219 | 5,065.22 | 5,066.20 | **+0.98** | −85.98 | Done (ce01af4); the rest is Tekla's rounding |
| D4 | (c) Built-up members: one BI catalog member vs three Tekla plates | 594 | 198 | 36,820.20 | 37,077.56 | +257.36 | +257.36 | X01 |
| D5 | (c) Members placed as given, cut differently | 638 | 638 | 16,359.79 | 16,287.79 | −72.00 | −72.00 | C05 / C07 (SHS slots and cut-backs) |
| | **Total** | **4,945** | **4,959** | **63,705.82** | **62,516.10** | **−1,189.72** | **−210.16** | |
| | Snapshot difference (app 62,516.10 − Tekla 63,705.82) | | | | | −1,189.72 | | residual **0.00** |

**The parts that matter, in order:** A5 girder flanges −760.5, B1 tube gusset / crossing −394.7, A1–A3 anchor rods, nuts and washers −340.8, D4 built-ups +257.4, B2 stairs +227.8, A6 cap plates −72.6, D5 SHS cuts −72.0. Everything else adds up to less than 80 kg.

## Method

1. **Pairing.** Every R12 part with `st.tkW` is paired once, using the matches in `compare_v69.json` (the `classify()` / greedy-box logic in `tools/golden/r10_compare.py`):
   - Members placed as given: the rebuilt member with that input (`rebuild_inputs.members[i].r10`).
   - Built-ups: one BI member against its three R12 plates.
   - Detail parts: the component part matched as MATCHED, NEAR or ROUGH. Parts with no match are MISSING. Anchor rods matched to bolts are AS BOLT.
   - App parts that no R12 part matched are EXTRA.

   That gives 4,945 R12 parts against 4,959 app parts (bolt bodies excluded on both sides).
2. **Grouping.** Each pairing goes into exactly one group, using the R12-REPORT row (row N = C<NN>) or, for an extra part, the row of the app connection that made it.
3. **Bolt holes in the app, estimated per part.** I cast rays along every app bolt axis through the app's meshes (`R12_app.ss`, 4,650 bolts). The ray on the axis meets no steel where there is a hole. Eight rays just outside the hole radius measure the material around it, which gives the hole length in each part. Hole weight = the app's hole section (a 12-gon, `stCyl(…, d0/2, …, 12)`, area 3·r²) × length × 7,850. It counts only where the part is weighed by its solid: every component part, and members that are not catalog.

   Check: on the 1,061 MATCHED component parts, app weight + estimated holes − Tekla weight leaves −0.11 kg in total. For example, the 48 eave end plates differ by −22.08 kg and the holes come to 21.88 kg. So the estimate is accurate to about 1 %.
4. **Gross check on R12.** Plates: NC header (DSTV_Plates in `tests/steel/tekla/Frame_28mx53m_Package_1.zip`) L × W × t × density, and the NC outline (AK contour) area × t. Members: Tekla's section area × NC length. Tekla's areas are the polygons proven in `WEIGHTS-R12.md`: rods are 8-gons, tubes 12/16-gons, SHS has square corners, CC is 704 mm², UPN240 is 33.2 kg/m and L60*6 is 5.42 kg/m. Also used: the R12 solid (mesh volume × density). Equal means equal after rounding to 0.01 kg.

Scratch scripts and outputs are outside the repo: `decomp.py`, `holes.py`, `grosscheck.py`, `gross2.py`, `groups.py`, and `v2_*.py` for the v2 snapshot. Step 3 (the hole estimate) was used for v1. For v2 it is used only to find holes that are still taken off: it compares each part's v2 − v1 weight with its estimated holes.

## (a) Parts missing in the app

| Group | Code | Parts | Tekla kg | Examples (mark, assembly: Tekla kg → app) | Why / fix |
|---|---|---|---|---|---|
| A1 | C01 | 192 ROD20 | 293.76 | 1501 in assembly 1001: 1.53 → in a bolt group (not weighed); same for all 192 | 1042 makes the anchors as one bolt group (M20 L677, kind `bolts`), and bolts are excluded from the weight. **Fix:** the 1042 option "anchor rods as parts" (ROD members in the base-plate assembly). |
| A2 | C01 | 192 NUT + 192 WASHER PD40*10 | 26.88 + 13.44 | 5 / 1001 NUT 0.14 → –; 4 / 1001 WASHER 0.07 → – | No nut or washer part per anchor. **Fix:** 1042 Anchor rods tab, nut and washer as parts. |
| A3 | C16 | 16 ROD16 | 6.72 | 1502 / 1002: 0.42 → bolt group | As A1, for the 1004 stair-post base plates. |
| A4 | C03 | 48 Stiff. FLT10*60 | 16.80 | 2504 / R3025: 0.35 → –; same for every apex | The v54 apex makes no stiffener. **Fix:** apex stiffener option (Tekla 106 Stiffeners tab). |
| A5 | X01 / R12 data | 3 × top flange + 3 × bottom flange FLT12*250 | 760.50 | 4516 / EB4006 top flange 126.75 → –; 4518 / EB4008 bottom flange 126.75 → – | Two girders, EB4006 and EB4008, have flanges but no web in Tekla's IFC. The built-up detection finds no web, so no member is placed. **Fix:** place them as built-ups without a web, or as two plates. This also fixes A7's C20 / C12 parts. |
| A6 | none yet | 30 Cap Plate FLT8*150 | 72.63 | 2506 / A2: 3.01 → –; also in the C2 column assemblies | Loose cap plates on the column tops under the rafters. No component in C01–C20 makes them. **Fix:** a column cap-plate option (End plate 144, or a Tekla 1002-style cap). |
| A7 | C20 | 12 stiffeners FLT10*70 at C2019 / C2023 | 19.08 | 4510 / C2019 Bot FS 1.59 → – | The beam on the web is EB4006 / EB4008 (A5), so there is no secondary part to pick. |
| A7 | C12 | 6 Shear Plate FLT10*150 | 14.82 | 4506 / C2017: 2.47 → – | Not bolted to any beam in R12 (EB4006 / EB4008 again). |
| A7 | C17 | 2 PLATE FLT10*120 | 3.57 | 3002 / EG3502: 1.77 → – | Hangers with no rafter partner found. |
| A7 | C10 | 1 PLATE FLT12*120 | 2.60 | F7501 / 14: 2.60 → – | Its own assembly, not bolted (R12 data). |
| A7 | C13 | 4 Stiff. FLT10*50 | 0.84 | at MBC2001 | Row 15 joints. |

## (a)+(b) Parts made differently: B1 tube gusset / crossing (C05–C07), B2 stairs (C18)

Here the app makes about as many parts as Tekla, but at other places and with other outlines. Many of them do not match within 50 mm, so they count as MISSING on one side and EXTRA on the other. Splitting them into missing and extra would only make two large numbers that cancel, so each family is compared as a whole.

**B1, by family (v2).** "Δ v1" is the same family before the hole fill.

| Family | Code | R12 parts | Tekla kg | App parts | App kg | Δ kg | Δ v1 | Examples (mark, assembly: Tekla → app) | Fix |
|---|---|---|---|---|---|---|---|---|---|
| Crossing gussets PL12 | C07 | 52 | 749.32 | 52 | 511.60 | −237.72 | −239.33 | 6534 / HZ6004 PL12*330.1 14.42 → 9.84; 6536 / HZ6013 PL12*330.5 14.53 → 9.87 | Ttgx crossing gusset is about ⅔ of Tekla's area: Tekla tube crossing size and position |
| Node gussets and fins PL12 / FLT12 | C06 | 248 | 1,263.38 | 248 (186 fin + 62 gusset) | 1,081.83 | −181.55 | −203.98 | 6533 / R3019 PL12*213.4 3.40 → FIN 2.55; 6532 / R3019 PL12*280.4 12.74 → none within 50 mm; app gusset 2851 7.58 (still about 0.18 of holes taken off) | Ttg node = 1 hull vs Tekla's outlines (margins, corner trim, fin length); also fill every tongue's holes in the shared node gusset (about 8.1 kg) |
| Tongue plates FLT12*140 | C05 | 456 | 1,876.02 | 456 | 1,892.40 | +16.38 | −24.66 | 6508 / HZ6002 4.15 → 4.15 | Same size; the rest is tongue length / insertion and position along the brace |
| Tongue end caps FLT5 | C05 | 840 | 143.17 | 816 | 139.76 | −3.41 | −3.41 | 1 / HZ6014 0.17 → 0.17; 17 / ST5008 0.14 → none | Tekla: two plates per tongue, one each side. App: one cap around the tongue. Tekla 20 cap plates |
| Crossing caps PL5 / FLT5*120 | C07 | 66 | 12.36 | 96 | 24.00 | +11.64 | +11.64 | 1 / HZ6016 PL5*44.1 0.17 → END CAP 0.17; app END CAP FLT5*120 0.25 each | Tekla cap plates at the crossing halves |
| **B1 total** | | **1,662** | **4,044.25** | **1,668** | **3,649.59** | **−394.66** | **−459.74** | | |

**B2 stairs (C18):**
- Tekla: 23 gratings THREAD PL10*335.7 at 1,333 kg/m³ (106.26 kg, e.g. 9503 / SGR9001 4.62), 46 brackets L60*6 (34.96, e.g. 9502 0.76) and 10 stringer end plates FLT12*150 (43.50, e.g. 9505 / SR9002 3.67).
- App: 20 steel treads PL6 (325.00, e.g. body 6091 15.38 kg), 40 supports L50*5 (48.80, 1.15 each) and 4 foot plates PL12 (38.60, 9.21 each).
- Δ +227.84 in v2 (+227.68 in v1; the foot plates' holes, 0.16 kg, are now filled).
- **Fix (C18):** grating treads (material Grating, 1,333 kg/m³), L60*6 brackets, Tekla's tread count (23) and stringer end plates.

## (b) Extra parts the app makes and Tekla does not

| Group | Code | Parts | App kg | Examples | Fix |
|---|---|---|---|---|---|
| C1 | C08 | 560 NUT M12 + 560 WASHER M12 | 16.80 + 5.60 | body 4972 NUT 0.03; body 4971 WASHER 0.01 | R12's sag rods are plain ROD12 members with no nut or washer parts (they are placed as given, and the 140 rods weigh the same: 191.80 = 191.80). Tsag with nut / washer off (or as bolt items, not parts) for Tekla parity. |

The other extra parts (tongues, caps, gussets, treads) belong to the families in B1 and B2.

## (c) The same part with a different weight

### D1 Grout (C01, C16): 0.00 kg in v2 (+1,131.76 in v1)
All 52 grout plates (paired, NEAR within 5 mm) now weigh 0, as in Tekla. In v1 they had material S275 and were weighed as steel, for example 6 / assembly 3 PL30*260 at 25.37 kg and 8 / assembly 5 FLT30*300 at 22.27 kg. **Done.**

### D2 The same component part: +0.98 kg in v2 (−85.98 in v1, of which bolt holes −85.87)
These are the 1,219 component parts that match Tekla's part. Since ce01af4 every part in this group equals Tekla to 0.01 kg, apart from four outline or rounding cases:
- C03 apex haunch (NEAR, outline about 26 mm off): +0.48
- C09 HAUNCH_PLATE (Tekla 0.17 against a solid of 0.182): +0.48
- C17 gable hanger (ROUGH, other outline): +0.14
- C13 Stiff. FLT10*50 (Tekla 0.21 against a solid of 0.201): −0.12

In particular, every one of these is now equal: the end plates (C02 384.48 = 384.48, C03 482.40 = 482.40), the base plates (471.30 = 471.30), the cleats L140*60*6 (290.40 = 290.40), the C14 shear tabs (104.04 = 104.04) and the C13 end plate 4512 (6.48 = 6.48).

The table below is the **v1** state, kept because it shows what the hole fill changed:

| Code | Part | Parts | Tekla kg | App kg | Δ | App holes kg | Rest (rounding / outline) | Examples (mark, assembly: Tekla → app) |
|---|---|---|---|---|---|---|---|---|
| C02 | Eave end plate FLT20*150 | 48 | 384.48 | 362.40 | −22.08 | 21.88 | −0.20 | F3501 / R3023: 8.01 → 7.55 (holes 0.46) |
| C03 | Apex end plate FLT20*400 | 48 | 482.40 | 460.32 | −22.08 | 21.88 | −0.20 | 3501 / R3023: 10.05 → 9.59 (0.46) |
| C01 | Base plates PL20*240 / FLT20*200 | 48 | 471.30 | 460.58 | −10.72 | 10.94 | +0.22 | 2503 / EGC2001: 9.80 → 9.57; 2502 / C2020: 11.30 → 11.08 |
| C01 | Cast plates PL4 (ROUGH: 30 mm lower) | 48 | 94.23 | 92.15 | −2.08 | 2.19 | +0.11 | |
| C04 | Cleat angle L140*60*6 | 264 | 290.40 | 282.48 | −7.92 | 7.31 | −0.61 | 8503: 1.10 → 1.07 (solid 1.096 without holes, 1.069 with 4 × M12) |
| C14 | Joist shear plate FLT10*130 | 68 | 104.04 | 98.60 | −5.44 | 5.19 | −0.25 | |
| C09 | End plate post PL12*160 | 24 | 97.68 | 93.36 | −4.32 | 4.39 | +0.07 | |
| C10 | Monitor rafter end plate | 23 | 59.80 | 56.58 | −3.22 | 3.16 | −0.06 | |
| C20 | Shear tabs FLT10*100 / *150 | 18 | 41.18 | 38.76 | −2.42 | 2.44 | +0.02 | |
| C11 | Monitor apex joining plate | 24 | 91.20 | 88.80 | −2.40 | 2.20 | −0.20 | |
| C13 / C15 | Cap / end plates PL16*120 | 8 | 51.84 | 49.92 | −1.92 | 1.95 | +0.03 | 4512 / EB4002: 6.48 → 6.24 (the C13 case in PARITY.md) |
| C17 | Gable hanger FLT10*120 (ROUGH) | 14 | 24.99 | 24.01 | −0.98 | 1.07 | +0.09 | |
| C16, C12, C18 | Small plates | 12 | 33.56 | 32.32 | −1.24 | 1.26 | +0.02 | |
| C03 | Apex haunch web / flange (NEAR) | 96 | 1,386.72 | 1,387.20 | +0.48 | 0 | +0.48 | Outline about 26 mm off (R12-REPORT row 3) |
| C09 | HAUNCH_PLATE FLT8*60 / PL8*56.7 | 48 | 8.16 | 8.64 | +0.48 | 0 | +0.48 | 7503: Tekla 0.17, app 0.18; the solid is 0.182 on both sides (Tekla's own rounding) |
| C13 | Stiff. FLT10*50 | 12 | 2.52 | 2.40 | −0.12 | 0 | −0.12 | 4513: Tekla 0.21, app 0.20; the solid is 0.201 (Tekla's own rounding) |
| C02, C04, C20 | Haunch flange / web, cleat stiffener, 186 stiffeners | 416 | 1,440.72 | 1,440.72 | 0.00 | 0 | 0.00 | |
| | **D2 total** | **1,219** | **5,065.22** | **4,979.24** | **−85.98** | **85.87** | **−0.11** | |

**(d) Rounding.** Both sides round each part to 0.01 kg. Most of the −0.11 kg left in D2 is that rounding, plus the error of the hole estimate. In Tekla's own data, 78 plates (C09 haunch plates, C13 Stiff., some FLT5 caps) differ from their own solid by up to 0.015 kg. That is Tekla's internal rounding of the same solid (see the check below), and it accounts for +0.48 / −0.12 kg here.

### D4 Built-up members (X01): +257.36 kg
The app places one BI member, weighed as a catalog section × length (area × L × 7,850; checked on all 198). Tekla weighs three plates as solids. The difference splits into two parts:

| Part of Δ | kg | What it is |
|---|---|---|
| Tekla's end cuts, copes and sloped ends | +138.57 | Σ (NC gross of the three plates) − Tekla kg. For example, EB4009 BI324-8-250-12: the flanges are coped 70 mm at each end (NC 5,522 → Tekla length 5,382), giving 364.12 gross vs 357.39 Tekla. The MB girders (+36.04) and rafters R (+29.92) work the same way. |
| The app member is longer than the plates | +118.79 | App kg − Σ plate gross. The BI ends are square at the outermost plate extent. For example, C2005 is 7,295 mm long, but its plates are 7,273 / 7,227 / 7,275 (a sloped column top), giving 263.42 vs 262.11. The C columns +32.3, rafters R +48.7, EGC +13.0 and PC / PR +21.7 behave the same way. |

Per group (Tekla → app): R 48 members 11,405.04 → 11,483.70 (+78.66); EB 12 members 3,937.46 → 3,991.50 (+54.04); C 30 members 8,560.26 → 8,598.36 (+38.10); MB 34 members 6,448.78 → 6,484.82 (+36.04); PC + PR 48 members 839.76 → 873.60 (+33.84); EGC 16 members 5,090.24 → 5,103.36 (+13.12); others +3.56.

Examples: EB4009: 357.39 → 364.12; C2017: 307.81 → 310.20; R3019: 236.90 → 238.55.

**Fix (X01):** model built-ups as Tekla does, as three plates (Web, Top flange, Bottom flange) in one assembly with end fitting / copes to the connected part. A cheaper alternative is to weigh a BI member by its solid (not catalog kg/m × L) and to give it fittings and copes. The gross rule does not change this group: Tekla does take copes and end cuts off (see below).

### D5 Members placed as given: −72.00 kg
- **SHS100*3 (HZ / VL bracing, struts): 228 members, 8,533.25 → 8,461.53 (−71.72).** These are almost all the INPUT EQUAL, SOLID DIFFERS members. The app's Ttg / Ttgx cut the tube (tongue slots, cut-backs at crossings) and take more off than Tekla's own cuts.
  - Examples: 6501 / VL6009 54.84 → 53.59 (−1.25); 24 members are −1.08 each, 20 are about −0.63.
  - A few go the other way: 5504 / ST5006 42.88 → 43.05.
  - The weight rule itself is right for these (the R12 SHS parts are weighed within 0.03 kg, `WEIGHTS-R12.md`).
  - **Fix (C05 / C07):** tongue slot length and width and the crossing cut-back as Tekla 20 / tube crossing.
- **CHS48*3 stanchions: −0.30.** For example, 25 / SR9002 3.18 → 3.16, from the stanchion end cut (C19).
- **UPN240 stringers: +0.02.**
- Purlins (220), sag rods (140), railings and hand rails: equal.

## Is Tekla's weight the gross weight? Checked on all 4,945 R12 parts

Hypothesis: "Tekla weight = gross, with no holes and no chamfers taken off" (plate = NC length × width × t × density; member = Tekla kg/m × length). **It holds for bolt holes and fails for chamfers, clips, copes and cuts.**

**Plates (3,405):**

| Tekla weight equals … | Plates | Σ (gross − Tekla) kg | Examples (mark, assembly: Tekla / gross / other) |
|---|---|---|---|
| Gross (NC L × W × t) | 2,059 | +3.64 (rounding) | Rectangular plates: end plates, base plates, shear tabs, tongues |
| The NC outline (chamfers and clips taken off, holes not) | 837 | +1,377.53 | 3515 apex haunch web FLT8*130: 5.18 / gross 16.64 / outline 5.18. 8504 cleat Stiff. PL6*44: 0.16 / 0.26 / 0.16. 6533 gusset PL12*213.4: 3.40 / 5.04 / 3.40 |
| The 3D solid, whose bevelled or skewed ends are not in the NC outline | 344 | +4.01 | 3516 / R bottom flange FLT10*150: 23.71 / 23.73 / solid 23.71. 4507–4510 / C 186 stiffeners FLT10*70: 1.59 / 1.63 / 1.59. 9503 grating (1,333 kg/m³): 4.62 / 4.43 / 4.62 |
| The 3D solid with its holes filled | 78 | +2.17 | 3505 / R top flange FLT10*150: 77.25 / 77.28 / solid 77.09 + holes 0.16. 2516 bottom flange: 85.92 / 85.93 / 85.52 + 0.40 |
| The 3D solid within 0.015 kg (Tekla's rounding) | 78 | +2.36 | 7503 HAUNCH_PLATE: 0.17 / 0.22 / solid 0.182. 4513 Top Stiff. FLT10*50: 0.21 / 0.22 / 0.201 |
| None of these (solid + holes within 0.02) | 3 | +5.32 | 6556 / R3015 gusset PL12*280.4: 12.74 / 14.51 / outline 12.75 / solid + holes 12.76 |
| No NC file in the package | 6 | – | EB4001 / EB4002 plates 4522–4524 (solid = Tekla) |

**Bolt holes:**
- 1,318 R12 plates have holes in their NC file, 201.19 kg of holes in all.
- For 1,237 of them, the Tekla weight equals the outline **without** taking the holes off. The other 81 are the bevelled ones above, and they too match only with the holes filled.
- For **0** of them does the Tekla weight equal outline − holes.
- Examples: F3501 eave end plate: Tekla 8.01, outline 8.007, outline − holes 7.53. 3501 apex end plate: 10.05 / 10.048 / 9.571. 2520 / C2022 top flange: 85.77 / 85.77 / 85.37.

**Members (1,540):**

| Tekla weight equals … | Members | Σ (gross − Tekla) kg | Examples |
|---|---|---|---|
| Gross (Tekla area × NC length) | 1,076 | −0.04 | CC200 purlins, ROD12 sag rods, PD / CHS rails, L140*60*6 cleats, L60*6 brackets, nuts and washers |
| The 3D solid (cuts and notches taken off) | 427 | +44.06 | 6530 HZ SHS100*3: 23.59 / gross 23.76 / solid 23.59. 5507 strut: 45.08 / 45.25 / 45.08. 25 stanchion CHS48*3: 3.18 / 3.22 / 3.18. 1501 anchor ROD20: 1.53 / 1.50 / 1.535 (the solid is longer than the NC length) |
| The 3D solid within 0.015 | 30 | +6.01 | 5512 strut 45.50 / 45.67 / 45.51 |
| None of these | 7 | +0.34 | 6 UPN240 stringers equal **gross** at 33.2055 kg/m (9514: 128.41 / 33.2 kg/m gross 128.39 / cut solid 124.68), not their cut solid. 6546 / VL6008 SHS 52.96 / 53.40 / 52.94 |

**Results of the gross check:**
- Gross for every part would make R12 **65,151.22 kg** against Tekla's 63,705.82 (+1,445.40). Almost all of that is the 837 chamfered or clipped plates: haunch webs, gussets, cleat stiffeners, coped flanges.
- **Tekla's rule is: the solid with every cut, chamfer, clip and cope taken off, and with the bolt holes not taken off.** The one exception seen is the UPN240 stringers, which are weighed as kg/m × length even though they are cut.
- **For the app:** the approved "gross" change should mean "no bolt holes taken off". It should not mean "bounding size": that would add about 1.4 t on a model like R12. Weighed that way, the app's component parts match Tekla within rounding (D2: −0.11 kg on 1,219 parts).

## Not verified
- In v1, app hole weights were an estimate from ray casting. The v2 hole fill confirms them to about 1 % on every component family except the node gussets (above).
- B1 and B2 compare part families as a whole, because many of their parts are more than 50 mm from Tekla's and cannot be paired one to one.
- The app's own total (62,517.22) is 1.12 kg above the web's (62,516.10). This report uses the web per-part weights; that 1.12 kg was not split into parts.
