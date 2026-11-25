# Tekla parity: web v70 chapter and app, against Tekla and R10

Each section covers one connection where v69 differs from Tekla (shown by `tests/steel/models/Frame_28x53m_R10.ss` and Tekla's help). It gives the rule, the proof, the change and the tests. The web change is the chapter **v70 · Tekla parity** (`web/v70_tekla_parity.js`). It goes into the reference copy just before the `v28 · BIM layers` chapter: `python3 tools/web/build_v70.py` writes `reference/solidsketch_v70.html`, and the golden harness (`tools/golden/lib/v69.js`) adds the same text in memory, so every golden made from now on comes from v69 + v70 (`SS_ORACLE=v69` runs plain v69). The app port follows the same steps in the same order. Models saved before v70 keep their v69 result unless the new option is set.

## C04 · Purlin cleat (Tcl, Tekla "Cold rolled overlap (1)")

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

### Unclear / not proven
- **Z and I purlins:** R10 has only C purlins, and the Tekla text does not say where Front is for Z or I profiles. v70 leaves them as v69 (`q.side`). Front / Back has no effect on them.
- **Stiffener outline:** this is R10's, for L140*60*6 with ts = 6 only. The rule for other leg lengths is not shown (the outline is kept in mm and does not scale with L1 / L2).
- **Bolt length:** R10's L144 fits both b + L2 + 2·d and 2·b + 2·d (b = L2 = 60 in R10). v70 uses b + L2 (the bolt then always reaches the shifted leg). R10 cannot tell the two apart.
- **The 3 mm overlap:** in R10 the stiffener is half inside the long leg (Tekla's depth Middle on the leg face), and on a slope the shifted leg goes up to 6.8 mm into the rafter flange. v70 copies both as they are.
