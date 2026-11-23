# UI golden: the app against web v69 (owner, 2026-11-23)

Like the geometry golden, v69 is the oracle for the interface too. v69 runs scripted sessions on the owner's phone size; the
app runs the same script; numbers and screenshots are compared. **It runs before every APK**: `assemble*` / `bundle*` of the
app depend on `testDebugUnitTest`, which includes the UI golden (`app/build.gradle.kts`; `-PskipUiGolden` only for local
experiments, never for an APK that goes to the owner).

## v69 side — `node tools/golden/ui_golden.js`
- Chromium (Playwright) at 412 × 891 CSS px, DPR 2.625 (= 1081.5 × 2339 px), dark, touch: the owner's phone.
- Fonts as on the phone: Android Chrome draws v69 in Roboto (its font stack `-apple-system, "Segoe UI", Roboto, …`; on Android
  `system-ui` is Roboto too). `python3 tools/golden/ui_fonts.py` installs the Roboto files Robolectric ships (the font the app's
  tests draw with) into `~/.fonts`, fixing their family names (needs `pip install fonttools`); `tools/golden/phone-fonts.conf`
  maps every UI family to Roboto (fontconfig). Without this Chromium uses another font and every text width is off.
- Script: Steel mode; a column HEA300 (0,0,0 → 0,0,7000) and a beam IPE360 (0,0,6500 → 6000,0,6500) with End plate 144 (one
  component cone); the panel's boxes with nothing selected; the beam selected (its two end handles); five camera states
  (`cam` target / dist / yaw / pitch: overview, orbit, pan, zoom, and a new grid `0 4*13195` / `0 5*5570` / `0 7500 1500`
  applied); then the grid sheet, the catalog and the End plate 144 dialog.
- Recorded (`tests/steel/ui/golden/ui_golden.json`, phone px): per camera state the screen position of every grid label (X,
  Y, levels, in that order), its size on screen (the sprite is 900 mm across, levels 1440 mm), every handle and every cone;
  the boxes of every panel button, of the sheets, their buttons, inputs, the dialog's tabs and bars. Screenshots:
  `tests/steel/ui/golden/v69/*.png` (owner bundle).
- The page gets one extra line (`tools/golden/lib/v69.js`, after the existing harness line): `__st.ui` gives the harness
  v69's scene, ST, the symbol group, `updateCamera` and `stHandlesDraw`. Nothing else changes.

## App side — `UiGoldenTest` (`./gradlew :app:testDebugUnitTest --tests '*UiGoldenTest*'`)
No emulator in the cloud session (no KVM), so the Compose UI runs in Robolectric (native graphics) at 412 × 891 dp, 420 dpi,
night, with Roborazzi screenshots (`tests/steel/ui/compare/app_*.png`):
- the same parts, component, selection and grid; the camera put where v69's is (`OrbitCamera.lookAt` from v69's yaw / pitch /
  distance / target); every state changes the camera the way the app does (camera moves, view revision up, a frame is drawn)
  and reads what the overlay actually drew (test hooks of `SteelOverlay`);
- **labels, handles, cones: within 2 px** of v69 (points v69 shows on screen; a point off screen in v69 must be off screen in
  the app); **label size within 2 px**;
- **button / sheet boxes: within 4 px** (whole boxes, as `getBoundingClientRect`; controls scrolled out of view in v69 are
  not compared); accepted differences are listed in `tests/steel/ui/differences.json` under `boxes`, each with its reason;
- **visible area**: with a phone's status bar (63 px) and 3-button navigation bar (126 px) (`LocalSysInsets`), every control
  of the main screen, grid sheet, catalog and dialog is inside the visible area.
Reports: `overlay_report.txt`, `boxes_<screen>.txt`, `nodes_<screen>.txt` (every text with its box) in `tests/steel/ui/compare/`.

## Side by side — `python3 tools/golden/ui_sidebyside.py`
`tests/steel/ui/compare/sbs_<state>.png`: v69 left, app right, v69's numbers drawn on both halves (labels green rings,
handles yellow, cones red, boxes cyan), so a difference shows on the app half at a glance.

## The grid-label bug, caught
With the S9b fix removed (the overlay no longer reads the view revision in its draw step), `overlayFollowsEveryCameraState`
fails in every state after the first: "the overlay was not redrawn after the camera change", labels 347–870 px from v69's
(`tests/steel/ui/compare/overlay_report_with_s9b_bug.txt`). With the fix it passes.

## Order
1. `python3 tools/golden/ui_fonts.py` (once per machine), 2. `node tools/golden/ui_golden.js` (when v69 or the script
changes), 3. `./gradlew :app:testDebugUnitTest --tests '*UiGoldenTest*'` (runs with every APK anyway),
4. `python3 tools/golden/ui_sidebyside.py`.
