# Weight analysis: the R12 rebuild vs Tekla, part by part

Written 2026-11-27. This is a read-only investigation: no code and no other file was changed.

## History
- **v1** (C20 snapshot, before ce01af4): app 63,495.66 kg, Tekla 63,705.82, **Δ −210.16**. Bolt holes were taken off component parts, and the grout was weighed as S275.
- **v2** (ce01af4: gross rule `holeFill70` / `TeklaWeight.holeFill`, grout weighs 0, stair-post 146 bolts L40): app 62,516.10 kg (web; the app reports 62,517.22), Tekla 63,705.82, **Δ −1,189.72**.
- **v3** (bccb1e7, C05: Ttg tube ends as Tekla, parametric SHS / RHS with square corners, hole fill by rays on the part mesh including other connections' bolts): app 62,591.88 kg (web; the app reports 62,592.04), Tekla 63,705.82, **Δ −1,113.94**.
- **v4** (7b1ec0e, C06: node gussets with Tekla 20's outline): app 62,742.75 kg (web; the app reports 62,742.91), **Δ −963.07**.
- **v5** (ee9267c, C07: tube crossing as Tekla 20): app 62,982.42 kg (web; the app reports 62,982.56), **Δ −723.40**.
- **v6** (d7d5374, C01 / C16: anchor rods, nuts and washers as parts, grout with slopes, cast plate under the grout): app 63,323.22 kg (web; the app reports 63,323.36), **Δ −382.60**.
- **v7** (4890e47, C18 stairs: U-pan grating treads PL10*335.7 at 1,333 kg/m³, L60*6 brackets, mitred stringers, 8 stair end plates): app 63,086.22 kg (web; the app reports 63,086.36), **Δ −619.60**.
- **v8** (907edf2, C03: apex haunch as Tekla 106, with the 48 apex stiffeners FLT10*60 and Tekla's haunch outline): app 63,102.54 kg (web), **Δ −603.28**.
- **v9** (acc995b, C17: gable hanger 146, the 2 hangers with no partner now made, and the other 14 with Tekla's outline): app 63,105.97 kg (web), **Δ −599.85**.
- **v10** (72de6af, X01 + X02: a built-up BI member is weighed as Tekla's three plates, with Tekla's fittings; column cap plates FLT8*150 from a new Column cap plate component): app 62,919.28 kg (web; the app reports 62,919.39), **Δ −786.54**. This is the current state. All the tables below are for v10 unless they say otherwise.

**Compared:**
- **Tekla:** each R12 part's own Tekla weight (`st.tkW` in `tests/steel/models/Frame_28x53m_R12.ss`). There are 4,945 parts and they sum to **63,705.82 kg**. Tekla's Part List total is 63,711.7 kg.
- **App:** the web rebuild in the v10 snapshot (`rebuild_v69.json`, `compare_v69.json`, `rebuild_inputs.json`, `R12_app.ss` after 72de6af). The app and the web build give the same rebuild body by body (`R11RebuildTest`). The total is **62,919.28 kg**, bolts excluded. The app's own total is 62,919.39, 0.11 kg above the web.
- **Difference:** app − Tekla = **−786.54 kg** (against the Part List, −792.42 kg). The groups below add up to exactly this figure (residual 0.00 kg) in every snapshot.

## Summary (v10, after C03, C17, X01 and X02)

**C01 / C16 (v6):** the anchor rods, nuts and washers are now parts. Rows 1 and 16 match completely: 744 parts, 923.41 kg = 923.41 kg. Groups A1–A3 (−340.80 kg) are gone, and their parts are now in D2, which is still +0.98 kg in total.

**C18 (v7):** the stairs match. Row 18 has 81 of 83 parts MATCHED, 186.04 kg = 186.04 kg. The app's extra steel treads, supports and foot plates (+227.84 kg) are gone. B2 is now only the 2 counter plates FLT12*150 on EB4002 (−9.04 kg), left for R13 by the owner's decision. The 12 UPN240 stringers now match too (D5 UPN −0.10, which is rounding).

**C03 (v8):** the apex haunch is made as Tekla 106. Row 3 matches completely: 192 of 192 parts MATCHED, 1,885.92 kg = 1,885.92 kg (48 end plates 482.40, 48 stiffeners 16.80, 96 haunch webs and flanges 1,386.72). A4 (−16.80) is gone, and the haunch outline (+0.48 in D2) too.

**C17 (v9):** the gable hangers match. Row 17: 16 of 16 parts MATCHED, 28.56 kg = 28.56 kg. The 2 hangers with no partner (−3.57 in A7) are made, and the 14 ROUGH hangers (+0.14 in D2) now have Tekla's outline.

**X01 (v10):** a built-up BI member is now weighed as Tekla's three plates (Web, Top flange, Bottom flange). Each plate is its own solid, without bolt holes. Tekla's fittings are made too: the apex rafters and the 144 secondaries are fitted to the end plate face, and the 144 option `cfit` fits the column top to the rafter's top face. D4 went from +257.36 to **−1.96 kg**. 835 of 836 members are MATCHED. PR7001 (+0.39) is left: an R12 data quirk (plate 1985, F7501, has assembly mark '14').

**X02 (v10):** the 30 column cap plates FLT8*150 are made by a new Column cap plate component. All 30 are MATCHED, 72.63 kg = 72.63 kg. A6 (−72.63) is gone.

**Why the total went down in v10:** v69's catalog rule (BI area × member length) made the built-ups 257 kg too heavy. That excess had been hiding part of the R13 gap. Now the built-ups weigh as Tekla's, so the gap shows in full.

**Owner decisions reflected here:**
- EB4006 / EB4008 (A5), and with them the C20 / C12 parts in A7 and the C18 counter plates, wait for R13.
- The 17 C06 gable gussets and the 18 C07 PL5 plates (both NEAR) are accepted for now.

The −786.54 kg left is almost all R13 work (−803.44 kg). Against that stand only C08 (+22.40), C10 / C13 (−3.44) and rounding (−2.06).

| # | Group | R12 parts | App parts (v10) | kg R12 | kg app v8 | kg app v9 | kg app v10 | Δ v8 | Δ v9 | **Δ v10** | Fix / code |
|---|---|---|---|---|---|---|---|---|---|---|---|
| A1 | (a) Anchor rods ROD20 | 192 → 0 (matched) | – | – | – | – | – | 0.00 | 0.00 | **0.00** | Done (C01, v6): parts in D2 |
| A2 | (a) Anchor nuts and washers PD40*10 | 384 → 0 (matched) | – | – | – | – | – | 0.00 | 0.00 | **0.00** | Done (C01, v6) |
| A3 | (a) Anchor rods ROD16 | 16 → 0 (matched) | – | – | – | – | – | 0.00 | 0.00 | **0.00** | Done (C16, v6) |
| A4 | (a) Apex stiffener FLT10*60 | 48 → 0 (matched) | – | – | – | – | – | 0.00 | 0.00 | **0.00** | Done (C03, v8): parts in D2 |
| A5 | (a) Girder flanges EB4006 / EB4008 (no web in R12) | 6 | 0 | 760.50 | 0 | 0 | 0 | −760.50 | −760.50 | **−760.50** | R13 (owner) |
| A6 | (a) Column top cap plates FLT8*150 | 30 → 0 (matched) | – | 72.63 → – | 0 | 0 | – | −72.63 | −72.63 | **0.00** | Done (X02, v10): parts in D2 |
| A7 | (a) Parts with no secondary part to pick | 25 → 23 | 0 | 40.91 → 37.34 | 0 | 0 | 0 | −40.91 | −37.34 | **−37.34** | R13 33.90 (C20 / C12), C10 2.60, C13 0.84; C17 done (v9) |
| B1 | (a)+(b) Tube gusset and crossing | 1,662 | 1,662 | 4,044.25 | 4,041.92 | 4,041.92 | 4,041.92 | −2.33 | −2.33 | **−2.33** | Done (C05–C07); 35 NEAR parts accepted |
| B2 | (a)+(b) Stairs: parts not yet made | 2 | 0 | 9.04 | 0 | 0 | 0 | −9.04 | −9.04 | **−9.04** | Done (C18, v7); 2 counter plates on EB4002 for R13 |
| C1 | (b) Sag-rod nuts and washers M12 (Tekla has none) | 0 | 1,120 | 0.00 | 22.40 | 22.40 | 22.40 | +22.40 | +22.40 | **+22.40** | C08: nut / washer off |
| D1 | (c) Grout | 52 | 52 | 0.00 | 0 | 0 | 0 | 0.00 | 0.00 | **0.00** | Done |
| D2 | (c) The same component part (rounding, outline) | 1,936 → 1,938 → 1,968 | same | 5,598.50 → 5,602.07 → 5,674.70 | 5,599.00 | 5,602.43 | 5,675.06 | +0.50 | +0.36 | **+0.36** | Done; Tekla's rounding (C09 +0.48, C13 −0.12) |
| D4 | (c) Built-up members: v8 / v9 one BI catalog member, v10 three plates as Tekla | 594 | 198 members (594 plates) | 36,820.20 | 37,077.56 | 37,077.56 | 36,818.24 | +257.36 | +257.36 | **−1.96** | Done (X01, v10); rounding, PR7001 +0.39 |
| D5 | (c) Members placed as given | 638 | 638 | 16,359.79 | 16,361.66 | 16,361.66 | 16,361.66 | +1.87 | +1.87 | **+1.87** | Done; rounding |
| | **Total** | **4,945** | **5,606 / 5,608 / 5,638** | **63,705.82** | **63,102.54** | **63,105.97** | **62,919.28** | **−603.28** | **−599.85** | **−786.54** | residual 0.00 in each |

How to read the table:
- Where a group's R12 parts became MATCHED, they moved into D2. That is why D2 grows from 1,219 (v2) to 1,968 parts while its Δ stays below +1.
- The app parts count one row per built-up member. With each built-up as three plates, the app has 6,034 parts in v10.
- v7 → v8 went up by 16.32 kg: the apex stiffeners (+16.80) minus the old haunch outline (−0.48).
- v8 → v9 went up by 3.43 kg: the two new hangers (+3.57) minus the old hanger outline (−0.14).
- v9 → v10 went down by 186.69 kg: the built-ups (−259.32) and the cap plates (+72.63).
- Earlier states: v1 −210.16, v2 −1,189.72, v3 −1,113.94, v4 −963.07, v5 −723.40, v6 −382.60, v7 −619.60 (see History).

### What is left, ranked by kg

| Order | Fix | Code | Δ kg it removes | Parts | What |
|---|---|---|---|---|---|
| 1 | **R13 (owner):** EB4006 / EB4008 flanges, their C20 stiffeners and C12 shear plates, and the 2 counter plates on EB4002 | R13 | −803.44 | 26 | Two girders with flanges but no web in R12 (A5 760.50, A7 33.90, B2 9.04) |
| 2 | Sag-rod nut and washer off (not in the owner's order yet) | C08 | +22.40 | 1,120 | Tekla has no nut or washer parts on the sag rods |
| 3 | Monitor plate, MBC2001 stiffeners (R12 data, no partner) | C10, C13 | −3.44 | 5 | |
| 4 | Rounding: B1 −2.33, D4 −1.96, D5 +1.87, D2 +0.36 | – | −2.06 | | Tekla's rounding, accepted NEAR parts, PR7001 |
| Done (v8) | Apex haunch and stiffeners | C03 | −16.80 (+0.48 outline in D2) → **0.00** | 48 + 96 | Row 3: 192 / 192, 1,885.92 kg = Tekla |
| Done (v9) | Gable hanger | C17 | −3.57 (+0.14 outline in D2) → **0.00** | 2 + 14 | Row 17: 16 / 16, 28.56 kg = Tekla |
| Done (v10) | Built-ups as Tekla | X01 | +257.36 → **−1.96** | 198 members | Three plates as solids, with Tekla's fittings; 835 / 836 members MATCHED |
| Done (v10) | Column top cap plates | X02 | −72.63 → **0.00** | 30 | New Column cap plate component; 30 / 30 MATCHED |

The owner's four fixes (C03, C17, X01, X02) are done. Δ is −786.54 kg. The v7 forecast was about −784 kg; the 2.58 kg more is the built-ups' rounding (D4 −1.96) and the C03 / C17 outlines (−0.62), which the forecast's rounding line still counted. After R13 and C08 as well, Δ would be −5.50 kg: C10 / C13 (−3.44) and rounding (−2.06).

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

Scratch scripts and outputs are outside the repo: `decomp.py`, `holes.py`, `grosscheck.py`, `gross2.py`, `groups.py`, and `v2_*.py` … `v10_*.py` for the later snapshots. Step 3 (the hole estimate) was used for v1. For v2 it is used only to find holes that are still taken off: it compares each part's v2 − v1 weight with its estimated holes.

## (a) Parts missing in the app

This table describes the v1 to v5 state. A1–A3 are done in v6 (C01 / C16), A4 in v8 (C03), A7's two C17 hangers in v9 and A6 in v10 (X02). A5 and A7's C20 / C12 parts wait for R13 (owner).

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

**B1, by family: v3 → v4 → v5.** The match column counts R12 parts in v5.

| Family | Code | R12 parts | Tekla kg | App parts | App kg v3 | v4 | v5 | Δ v3 | Δ v4 | Δ v5 | Match in v5 | Examples in v5 (mark, assembly: Tekla → app) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Crossing gussets PL12 | C07 | 52 | 749.32 | 52 | 511.60 | 511.60 | 749.32 | −237.72 | −237.72 | **0.00** | 52 MATCHED | 6514 / VL6001 PL12*333.9 15.41 → 15.41 |
| Node gussets and fins PL12 / FLT12 | C06 | 248 | 1,263.38 | 248 (v5: 166 gusset + 82 fin) | 1,110.17 | 1,260.92 | 1,260.92 | −153.21 | −2.46 | **−2.46** | 233 MATCHED, 15 NEAR (gable nodes) | 6545 / R3023 FLT12*140 2.24 → 2.21 |
| Tongue plates FLT12*140 | C05 | 456 | 1,876.02 | 456 | 1,876.02 | 1,876.02 | 1,876.02 | 0.00 | 0.00 | **0.00** | 429 MATCHED, 27 NEAR | 6512 / HZ6014 3.76 → 3.76 |
| End caps FLT5 / PL5 | C05 / C07 | 906 | 155.53 | 906 | 156.58 | 156.70 | 155.66 | +1.05 | +1.17 | **+0.13** | 858 MATCHED, 48 NEAR | 15 / VL6013 PL5*91.1 0.35 → 0.37 (NEAR) |
| **B1 total** | | **1,662** | **4,044.25** | **1,662** | **3,654.37** | **3,805.24** | **4,041.92** | **−389.88** | **−239.01** | **−2.33** | 1,572 MATCHED, 90 NEAR | |

Earlier: v1 −459.74 (holes taken off, other outlines), v2 −394.66.

**B2 stairs (C18):**
- Tekla: 23 gratings THREAD PL10*335.7 at 1,333 kg/m³ (106.26 kg, e.g. 9503 / SGR9001 4.62), 46 brackets L60*6 (34.96, e.g. 9502 0.76) and 10 stringer end plates FLT12*150 (43.50, e.g. 9505 / SR9002 3.67).
- App: 20 steel treads PL6 (325.00, e.g. body 6091 15.38 kg), 40 supports L50*5 (48.80, 1.15 each) and 4 foot plates PL12 (38.60, 9.21 each).
- Δ +227.84 in v2 to v6 (+227.68 in v1; the foot plates' holes, 0.16 kg, were filled in v2).
- **Done in v7 (C18):** grating treads (Grating, 1,333 kg/m³), L60*6 brackets, Tekla's 23 treads, mitred stringers and 8 stair end plates. 81 of 83 row-18 parts are MATCHED (186.04 = 186.04 kg). The 2 counter plates FLT12*150 on EB4002 (9504, 4.52 kg each) wait for R13.

## (b) Extra parts the app makes and Tekla does not

| Group | Code | Parts | App kg | Examples | Fix |
|---|---|---|---|---|---|
| C1 | C08 | 560 NUT M12 + 560 WASHER M12 | 16.80 + 5.60 | body 4972 NUT 0.03; body 4971 WASHER 0.01 | R12's sag rods are plain ROD12 members with no nut or washer parts (they are placed as given, and the 140 rods weigh the same: 191.80 = 191.80). Tsag with nut / washer off (or as bolt items, not parts) for Tekla parity. |

The other extra parts (tongues, caps, gussets, treads) belong to the families in B1 and B2.

## (c) The same part with a different weight

### D1 Grout (C01, C16): 0.00 kg in v2 (+1,131.76 in v1)
All 52 grout plates (paired, NEAR within 5 mm) now weigh 0, as in Tekla. In v1 they had material S275 and were weighed as steel, for example 6 / assembly 3 PL30*260 at 25.37 kg and 8 / assembly 5 FLT30*300 at 22.27 kg. **Done.**

### D2 The same component part: +0.36 kg in v9 and v10 (+0.50 in v8, +0.98 in v2 to v7, −85.98 in v1, of which bolt holes −85.87)
These are the 1,219 component parts that match Tekla's part. Since ce01af4 every part in this group equals Tekla to 0.01 kg, apart from four outline or rounding cases:
- C03 apex haunch (NEAR, outline about 26 mm off): +0.48
- C09 HAUNCH_PLATE (Tekla 0.17 against a solid of 0.182): +0.48
- C17 gable hanger (ROUGH, other outline): +0.14
- C13 Stiff. FLT10*50 (Tekla 0.21 against a solid of 0.201): −0.12

The C03 outline is gone in v8 and the C17 outline in v9. In v10 only the two Tekla rounding cases are left (C09 +0.48, C13 −0.12), on 1,968 parts.

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

### D4 Built-up members (X01): −1.96 kg in v10 (+257.36 in v1 to v9)
The text and the first table below describe v1 to v9. The app places one BI member, weighed as a catalog section × length (area × L × 7,850; checked on all 198). Tekla weighs three plates as solids. The difference splits into two parts:

| Part of Δ | kg | What it is |
|---|---|---|
| Tekla's end cuts, copes and sloped ends | +138.57 | Σ (NC gross of the three plates) − Tekla kg. For example, EB4009 BI324-8-250-12: the flanges are coped 70 mm at each end (NC 5,522 → Tekla length 5,382), giving 364.12 gross vs 357.39 Tekla. The MB girders (+36.04) and rafters R (+29.92) work the same way. |
| The app member is longer than the plates | +118.79 | App kg − Σ plate gross. The BI ends are square at the outermost plate extent. For example, C2005 is 7,295 mm long, but its plates are 7,273 / 7,227 / 7,275 (a sloped column top), giving 263.42 vs 262.11. The C columns +32.3, rafters R +48.7, EGC +13.0 and PC / PR +21.7 behave the same way. |

Per group (Tekla → app): R 48 members 11,405.04 → 11,483.70 (+78.66); EB 12 members 3,937.46 → 3,991.50 (+54.04); C 30 members 8,560.26 → 8,598.36 (+38.10); MB 34 members 6,448.78 → 6,484.82 (+36.04); PC + PR 48 members 839.76 → 873.60 (+33.84); EGC 16 members 5,090.24 → 5,103.36 (+13.12); others +3.56.

Examples: EB4009: 357.39 → 364.12; C2017: 307.81 → 310.20; R3019: 236.90 → 238.55.

**Fix (X01):** model built-ups as Tekla does, as three plates (Web, Top flange, Bottom flange) in one assembly with end fitting / copes to the connected part. A cheaper alternative is to weigh a BI member by its solid (not catalog kg/m × L) and to give it fittings and copes. The gross rule does not change this group: Tekla does take copes and end cuts off (see below).

**Done in v10 (X01, 72de6af):** a BI member is weighed as Tekla's three plates, each as its own solid without bolt holes. The apex rafters and the 144 secondaries are fitted to the end plate face, and the 144 option `cfit` fits the column top to the rafter's top face. 835 of 836 members are MATCHED.

| Group | Members | Tekla kg | App kg v9 | App kg v10 | Δ v9 | **Δ v10** |
|---|---|---|---|---|---|---|
| R | 48 | 11,405.04 | 11,483.70 | 11,404.98 | +78.66 | **−0.06** |
| C | 30 | 8,560.26 | 8,598.36 | 8,560.17 | +38.10 | **−0.09** |
| MB | 34 | 6,448.78 | 6,484.82 | 6,444.36 | +36.04 | **−4.42** |
| EGC | 16 | 5,090.24 | 5,103.36 | 5,090.40 | +13.12 | **+0.16** |
| EB | 12 | 3,937.46 | 3,991.50 | 3,938.59 | +54.04 | **+1.13** |
| PC + PR | 48 | 839.76 | 873.60 | 840.58 | +33.84 | **+0.82** |
| MBC, STB, STR | 10 | 538.66 | 542.22 | 539.16 | +3.56 | **+0.50** |
| **Total** | **198** | **36,820.20** | **37,077.56** | **36,818.24** | **+257.36** | **−1.96** |

Examples in v10 (Tekla → app): EB4009 357.39 → 357.53; C2017 307.81 → 307.81; C2005 261.80 → 261.80; R3019 236.90 → 236.91. The one member not MATCHED is PR7001 (18.70 → 19.09, INPUT EQUAL, SOLID DIFFERS), an R12 data quirk: plate 1985 (F7501) has assembly mark '14'.

### D5 Members placed as given: +1.99 kg in v5 (−1.00 in v3 / v4, −72.00 in v1 / v2)
- **SHS100*3:** 228 members, all MATCHED. Tekla 8,533.25 kg, app 8,535.52 kg (+2.27). Since C07, 42 crossing-half braces are +0.04 kg each and a few are +0.07 (for example 6519 / VL6013 50.98 → 51.05). That is a slightly shorter cut where the halves meet the gusset, within the 0.1 mm box tolerance. 167 are equal.
- **CHS48*3 stanchions:** −0.30. For example, 25 / SR9002 3.18 → 3.16, from the stanchion end cut (C19).
- **UPN240 stringers:** +0.02. 4 are still INPUT EQUAL, SOLID DIFFERS (C18).
- Purlins (220), sag rods (140), railings and hand rails: equal.
- v1 / v2 for history: SHS was −71.72 kg, because Ttg slots and cut-backs took more off than Tekla's own cuts.

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
- The app's own total is 0.14–0.16 kg above the web's in v3 to v7, and 0.11 kg above in v10 (v8 and v9: the app total was not recorded). This report uses the web per-part weights.
