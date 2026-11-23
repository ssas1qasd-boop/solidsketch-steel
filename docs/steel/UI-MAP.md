# UI map: web v69 elements used by SolidSketch Steel

Source: `reference/solidsketch_v69.html` (line numbers below are 1-based lines of that file).
Rendered reference: `tests/steel/ui/v69/ui_dom.json` + `tests/steel/ui/v69/*.png` (412 × 891 CSS px, DPR 2.625, dark).

## How to read

- All sizes are **CSS px**. On the owner's phone (1080 × 2340, DPR 2.625): **phone px = CSS px × 2.625**.
- **Compose dp = CSS px** (DPR 2.625 ≈ density 2.625, so 1 CSS px ≈ 1 dp). Font sizes: CSS px → `sp` 1:1 (at font scale 1.0).
- "eff." = the **effective** value after the cascade (several v69 rules fight; the ID selector `#st-sheet …` often wins over the class rules written later). When CSS source and effective value differ, port the effective value: that is what the screenshots show.
- Colours: `D` = dark theme, `L` = light theme. CSS variables are resolved in §1.
- Fonts: main UI uses `var(--font)` = `-apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif`; Steel uses `system-ui, sans-serif`. Both resolve to **Roboto** on Android. `<b>` = weight 700, `h3` = 700.
- The 412 px viewport is ≤ 420 / ≤ 560 / ≤ 700 px, so **all three phone media queries apply** (L8346, L16398, L16842).

---

## 1. Theme

### 1.1 CSS variables (L12–17)

| Variable | Light (default, L12–14) | Dark (L16 media, L17 data-theme) |
|---|---|---|
| `--bg` | `#eef1f5` | `#1c1e23` |
| `--panel` | `rgba(255,255,255,0.92)` | `rgba(34,37,44,0.94)` |
| `--panel2` | `rgba(255,255,255,0.8)` | `rgba(34,37,44,0.82)` |
| `--fg` | `#1a1d24` | `#eef0f4` |
| `--muted` | `#5b6270` | `#a2a9b6` |
| `--chip` | `#e2e6ec` | `#2f333c` |
| `--chip-fg` | `#1a1d24` | `#eef0f4` |
| `--chip-sel` | `#2f6fed` | `#5c9eed` |
| `--chip-sel-fg` | `#fff` | `#0e1420` |
| `--accent` | `#2f6fed` | `#5c9eed` |
| `--border` | `rgba(0,0,0,0.08)` | `rgba(255,255,255,0.08)` |
| `--toast` | `#1a1d24` | `#eef0f4` |
| `--toast-fg` | `#fff` | `#1a1d24` |
| `--font` | `-apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif` | same |
| `--sheet-solid` (L8357) | `#ffffff` | `#22252c` (used only by `#hist-sheet`) |

### 1.2 How v69 picks the theme

- Dark when `@media (prefers-color-scheme: dark)` AND `<html>` has no `data-theme="light"` (L16); forced dark by `:root[data-theme="dark"]` (L17). Light is the default.
- **Nothing in v69 sets `data-theme`** (grep: no `dataset.theme =` / `setAttribute('data-theme')`). So in practice v69 **follows the system theme**. Android port: follow `isSystemInDarkTheme()`.
- JS twin: `dark()` at L7801 = `data-theme !== 'light' && (data-theme === 'dark' || matchMedia('(prefers-color-scheme: dark)'))`. A `change` listener on the media query rebuilds the grid (L7967).
- Inconsistencies to copy as-is (or note): `--sheet-solid` (L8357) and the `.tkdlg` dark override (L16368) use only `prefers-color-scheme`, ignoring `data-theme`.

### 1.3 three.js scene colours (`buildGrid`, L7933–7940; `edgeMaterial` L7844)

| Item | Dark | Light |
|---|---|---|
| Clear colour (`renderer.setClearColor`) | `0x1c1e23` | `0xeef1f5` |
| Grid minor lines (`uMinor`) | `0x2e333c` | `0xcfd4dc` |
| Grid major lines (`uMajor`) | `0x434a56` | `0xa3abb8` |
| Body edge lines | `0x0d0f13` | `0x262b34` |
| Axis X / Y / Z (both themes) | `0xd9605f` / `0x67bd6b` / `0x5c9eed` | same |
| Lights (L7809–7812) | Hemisphere `0xffffff`/`0x3a4250` 2.4; sun `0xffffff` 2.2 at (6,−9,14); fill 0.8 at (−8,6,4); rim 0.55 at (−3,9,7) | same |

---

## 2. Top bar `#top` (DOM L251–275, CSS L22–34, phone override L8346–8347)

### 2.1 Containers

| Element | Lines | CSS (eff. at 412 px) | Rendered (json) |
|---|---|---|---|
| `#top` | L251, L22 | `position:fixed; top:0; left:0; right:0; padding: calc(env(safe-area-inset-top,0px) + 8px) 10px 0; display:flex; justify-content:center; pointer-events:none` | 0,0 412×62 (= 162.75 phone px) |
| `.bar` (inside #top) | L252, L23 + L8346 | `display:flex; align-items:center; gap:0` (4px >420px); `width:100%; max-width:720px; background:var(--panel); border:1px solid var(--border); border-radius:16px; padding:6px 4px 6px 10px` (6 6 6 14 >420px); `backdrop-filter:blur(8px); box-shadow:0 4px 18px rgba(0,0,0,0.12)` | x10 y8 392×54 |
| `.spacer` | L255, L27 | `flex:1` | |

Bar height = 40 (icon) + 6 + 6 padding + 2 border = **54**; with the 8 px top padding the top zone is **62** (+ status-bar inset).

### 2.2 Items, left to right (final DOM order)

| # | Item | Source | CSS (eff.) | D colour | L colour | Behaviour |
|---|---|---|---|---|---|---|
| 1 | Title **"SolidSketch"** (`.title b`) | L253, L25, L8346 | `.title{display:flex;flex-direction:column;line-height:1.15}`; b **14px** 700 (15px >420px) | `--fg` #eef0f4 | #1a1d24 | static |
| – | `.title small#engine` (engine name) | L253, L25 | 11px `--muted`; **`display:none` at ≤420px** | | | hidden on phone |
| 2 | `#count` "0 bodies" / "1 body" | L254, L26, L8347 | `margin-left:6px; font-size:11px` (10px / 12px >420px); `--muted` | #a2a9b6 | #5b6270 | text set in `renderUIInner` L11861; wraps to 2 lines at 412 px (screenshot 01) |
| 3 | **◫ Items** (`#xt-items-btn`, `.btn.icon`) | L12326–12327 (inserted before Undo) | `.icon{width:40px→34px at ≤420; height:40px; display:grid; place-items:center; font-size:18px; color:var(--fg)}` + `.btn` (below), padding 0. Rendered **26.7×40** (flex-shrunk) | #eef0f4 | #1a1d24 | toggles the **All Items** list `#xt-items` (sketch planes, profiles, bodies; eye = show/hide). |
| 4 | **◧ Section View** (`#x24-sec-btn`) | L12781–12782 (inserted before Undo, after ◫) | same as ◫ | #eef0f4 | #1a1d24 | toggles section view `x24Section(!SEC)` |
| 5 | **Undo** `#undo` | L256, L28–29, L8347 | `.btn{border:0; background:transparent; color:var(--accent); font:inherit; font-size:14px→**13px**; font-weight:600; padding:8px 10px→**8px 5px**; border-radius:10px}` → 43.6×32 | enabled #5c9eed | #2f6fed | disabled when `!H.past.length` (L11862) |
| 6 | **Redo** `#redo` | L257 | same → 42×32 | | | disabled when nothing to redo |
| – | `.btn:disabled` | L29 | `color:var(--muted); opacity:0.5` | #a2a9b6 @50 % | #5b6270 @50 % | applies to Undo/Redo/🗑 |
| 7 | **History (clock)** `#hist` | L8348–8350 (inserted after Redo) | `.btn` + `width:34px; padding:0` (≤420); inline SVG 24-viewBox **20×20**, `stroke=currentColor stroke-width=2`, round caps/joins: paths `M3 12a9 9 0 1 0 3-6.7`, `M3 4v4h4`, `M12 7v5l3 2`. Rendered 26.7×24 | `--accent` #5c9eed (never disabled) | #2f6fed | opens `#hist-sheet` (§2.4) |
| 8 | **🗑 Delete** `#del` (`.btn.icon`) | L258 | as ◫ | disabled look (muted, 50 %) | | disabled when nothing selected |
| 9 | **🚶 Walkthrough** `#walk` (`.btn.icon`) | L259 | as ◫ (emoji keeps its own colours) | | | opens walkthrough |
| 10 | **⋮ More** `#more` in `#menuwrap` | L260–261, L31 | as ◫; `#menuwrap{position:relative}` | #eef0f4 | #1a1d24 | toggles `#menu` (L12885); any click outside closes it (L12886) |

### 2.3 `⋮` menu `#menu` (DOM L262–273, CSS L32–34)

| Property | Value |
|---|---|
| Box | `position:absolute; right:0; top:44px` (below the ⋮ wrapper); `min-width:190px; padding:6px; border-radius:12px; border:1px solid var(--border); background:var(--panel); box-shadow:0 8px 30px rgba(0,0,0,0.25); z-index:5` |
| Rendered (07_menu) | x207 y59, **190×782** — no max-height, no scroll: the last items run under the Steel panel |
| Item `#menu button` | `display:block; width:100%; text-align:left; border:0; background:transparent; color:var(--fg); font:inherit; font-size:14px; padding:10px 12px; border-radius:8px` |
| Item hover | `background:var(--chip)` |
| Font | body font 16px base → items **14px**, weight 400 |
| Quirk | `#menu button{display:block}` overrides the `hidden` attribute, so **"Remove imported models" is always shown** (screenshot 07) |

Items in final order (L264–272 + JS inserts):
1. Open model (.ss)…
2. Walkthrough
3. Pick faces only: off
4. Save model as .ss…
5. Furniture…
6. Import 3D model (glTF, OBJ, DAE, FBX)…
7. Remove imported models (`#m-refclear`, meant hidden until a ref model is loaded, L13348–13349)
8. Import DXF / DWG…
9. Paste DXF text…
10. Export STL + OBJ (zip)
11. Export DXF drawing (zip)
12. Exact engine (beta): off/on
13. Reset view
14. Gestures & help
15. Keep sketch when leaving: on/off (`#m-keepsketch`, L17280, inserted before Clear all)
16. Auto view turn: on/off (`#m-autoturn`, L17283, inserted before Clear all)
17. Clear all
18. Units: mm / cm / m (`#m-units`, L17826–17828, appended last; cycles mm → cm → m)

### 2.4 History sheet `#hist-sheet` (L8351–8358)

`position:fixed; left:8px; right:8px; top:64px; bottom:150px; z-index:40; background: var(--panel) over var(--sheet-solid); color:var(--fg); border:1px solid #2c3240; border-radius:16px; flex column; box-shadow:0 8px 30px rgba(0,0,0,.45)`.
Head `.hist-head` 17px, padding 12 14 4, gap 10; count 13px opacity .6; note `.hist-note` 12px opacity .6, padding 0 14 8; list scrolls (`overflow-y:auto`, padding 0 8 10).
Row `.hist-item` 15px, padding 10, radius 10, gap 10; number `.n` opacity .5, min-width 28, right-aligned tabular; current row bg `#2f6fed` text `#fff`; undone rows opacity .38; pressed bg `#2a3040`.

---

## 3. Bottom area `#bottom` (DOM L277–281, CSS L35–49)

| Element | Lines | CSS | D | L | Rendered (01_main) |
|---|---|---|---|---|---|
| `#bottom` | L35–36 | `position:fixed; left:0; right:0; bottom:0; padding:0 10px calc(env(safe-area-inset-bottom,0px) + 10px); display:flex; flex-direction:column; align-items:center; gap:8px; pointer-events:none` (children `pointer-events:auto`) | – | – | y801 412×90 |
| `#hint` pill | L278, L37 | `background:var(--panel2); border:1px solid var(--border); border-radius:999px; padding:6px 14px; font-size:12.5px; color:var(--fg); max-width:92vw; text-align:center; backdrop-filter:blur(8px)` | bg rgba(34,37,44,.82), fg #eef0f4 | rgba(255,255,255,.8), #1a1d24 | x42.3 y801 327.4×**29** |
| `#panel` (command panel) | L279, L38–39 | `width:100%; max-width:720px; background:var(--panel); border:1px solid var(--border); border-radius:16px; padding:12px 14px; display:flex; flex-direction:column; gap:8px; backdrop-filter:blur(8px); box-shadow:0 4px 18px rgba(0,0,0,0.12)`; `[hidden]{display:none}` | | | hidden by default; filled by `renderUIInner` (L11864+) |
| `.row` / `.row.scroll` | L40–42 | `display:flex; gap:6px; flex-wrap:wrap; align-items:center`; `.scroll`: `nowrap; overflow-x:auto`, no scrollbar | | | |
| `.note` | L43 | `font-size:12px; color:var(--muted)` | | | |
| `#tools` | L280, L44–45 | `width:100%; max-width:720px; display:flex; gap:6px; overflow-x:auto; padding:2px 2px 4px; scrollbar-width:none` (horizontal scroll, no scrollbar) | | | x10 y838 392×43 |
| `.chip` | L46 | `flex:0 0 auto; border:1px solid var(--border); background:var(--chip); color:var(--chip-fg); font:inherit; font-size:13.5px; font-weight:500; padding:9px 14px; border-radius:999px; white-space:nowrap` | bg #2f333c, fg #eef0f4, border rgba(255,255,255,.08) | bg #e2e6ec, fg #1a1d24, border rgba(0,0,0,.08) | height **37** |
| `.chip.sel` | L47 | `background:var(--chip-sel); color:var(--chip-sel-fg); border-color:transparent` | bg #5c9eed, fg #0e1420 | bg #2f6fed, fg #fff | |
| `#panel-more` (More ▾ / Less ▴ handle) | L11840–11858 | `align-self:stretch; flex; center; gap:8px; border:0; background:transparent; color:var(--muted); 12px 600; padding:0 0 2px; margin:-6px 0 -2px; min-height:22px`; `::before` grab bar 36×4, radius 2, bg var(--border) (not when folded); span colour `--accent`; em (folded: the hint text) `--fg` opacity .75 500, ellipsis | | | when folded, `#hint` is hidden (`.pc-gone`) and its text shows in the handle |

### 3.1 Tool chips (`#tools`), in order (L11706, L11863–11864)

| # | Label | Action | Selected (`.sel`) when |
|---|---|---|---|
| 1 | Steel | `stToggle()` | Steel mode on |
| 2 | **Select** | `setTool('select')` | `S.tool==='select'` — **default** (L8020 `tool:'select'`) |
| 3 | Move/Rotate | `setTool('move')` | |
| 4 | Line | `setTool('line')` | |
| 5 | Polyline | `setTool('polyline')` | |
| 6 | Rectangle | `setTool('rect')` | |
| 7 | Circle | `setTool('circle')` | |
| 8 | Edit sketch | `setTool('edit')` | |
| 9 | Box | `setTool('box')` | |
| 10 | Cylinder | `setTool('cylinder')` | |
| 11 | Sphere | `setTool('sphere')` | |
| 12 | Grid snap | toggles `S.snap` | `S.snap` — **on by default** (L8020) |
| 13 | Finger: draw / Finger: orbit | only while a sketch tool is active | `S.fingerDraws` |

Default `#hint` text in Select: "Tap a body to select · drag to orbit · pinch to zoom".
In Steel mode `body.st-on #tools, body.st-on #panel, body.st-on #hint {display:none!important}` (L13728); `#bottom` keeps only its padding (json: y881 412×10).

---

## 4. Toast (`toast()` L7766–7767, `#toast` DOM L303, CSS L69–70)

| Property | Value |
|---|---|
| Position | `position:fixed; left:50%; top:calc(env(safe-area-inset-top,0px) + 72px); transform:translateX(-50%) translateY(-10px)`; `z-index:9` |
| Box | `padding:10px 16px; border-radius:12px; max-width:90vw; text-align:center; pointer-events:none` |
| Colours | bg `var(--toast)`, text `var(--toast-fg)` — inverted: **D** bg #eef0f4 / text #1a1d24; **L** bg #1a1d24 / text #fff |
| Font | 13.5px, 400 |
| Show | `.show` → `opacity:1; transform:translateX(-50%) translateY(0)`; hidden: `opacity:0`. Transition `opacity .2s, transform .2s` (slides down 10 px while fading in) |
| Timing | `toast(msg, ms = 2600)`: shows at once, hides after **2600 ms** (callers pass e.g. 5000); a new toast replaces the text and restarts the timer |
| Rendered width | **206 px** = 50 % of 412 (with `left:50%` the shrink-to-fit width is capped at half the viewport, not 90vw); json y71–72, h52 (2 lines) / h68 (3 lines) |
| Stacking | z 9 is **below** `#st-panel` (55), `#tk-status` (57), `#st-sheet` (58): over a sheet it only shows faintly through the 0.94-alpha panel (screenshots 03, 04) |

---

## 5. Steel

Base Steel CSS: `stCss` L13726–13741. Helpers: `stE(tag, cls, txt)` L13742, `stBtn(label, fn, on)` L13743 (button, class `on` when `on`), `stSheet(title)` L13778.

### 5.1 Steel panel `#st-panel` (CSS L13727–13733; built by `stPanel` L13754–13764, wrapped at L14399–14400 and L17237–17241)

| Part | CSS / content | D | L | Rendered |
|---|---|---|---|---|
| `#st-panel` | `position:fixed; left:8px; right:8px; bottom:8px; z-index:55; max-width:640px; margin:0 auto; padding:8px 10px; border:1px solid var(--border,#ddd); border-radius:16px; box-shadow:0 6px 28px rgba(0,0,0,.22); background:var(--panel,#fff); color:var(--fg,#111); font:14px system-ui,sans-serif`. **No safe-area inset.** | bg rgba(34,37,44,.94) fg #eef0f4 | rgba(255,255,255,.92) / #1a1d24 | x8 y770 396×113 |
| Row `.r` | `display:flex; gap:6px; overflow-x:auto; align-items:center; padding:3px 0` (each row scrolls sideways) | | | |
| Pill `button` | `border:1px solid rgba(127,127,127,.35); background:transparent; color:inherit; border-radius:999px; padding:7px 12px; white-space:nowrap; font:inherit` (14px 400) | | | h **33** |
| Pill `.on` | `background:#c0392b; color:#fff; border-color:#c0392b` (both themes) | | | |
| Row 1 (tools) | Grid · Portal frame · Column* · Beam* · Plate* · Components · Numbering · Checks · Drawings · Marks / Marks ✓ (on when marks shown) · Properties · Steel list · Exit Steel. (*tool pills: `.on` while that tool is armed; tap again to disarm) | | | y782 |
| Row 2 | span "Work level", then one pill per Z level: `+0`, `+7000` … (`.on` = current level `ST.lev`). With Beam/Column armed: " · Set" + Beam / Rafter / Purlin / Girt / Bracing (Column: Column) | | | |
| Row 3 (only while placing a beam/plate with ≥1 point) | `input#st-typed` placeholder "dx dy dz (mm)" + Place + Cancel (+ **Finish** `.on` for a plate with ≥3 corners). Input: `font:inherit; padding:6px 8px; border-radius:8px; border:1px solid var(--border,#ccc); background:transparent; color:inherit; width:150px` | | | |
| Component row (L14400, while picking parts of a component, `ST.tool==='cat'`) | span = component label, Properties, Catalog, **Done** (`.on`); inserted before `.say` | | | |
| `.say` hint line | `font-size:12.5px; opacity:.8; padding:2px 2px 0; min-height:16px`. Default "Pick a tool, or tap a steel part to see its properties"; texts from `stHint` L13745–13753 | | | x19 y857 374×17 |
| Weight line (L17238–17241) | extra `.say` when a steel part is selected: "Weight 123.4 kg · Net … kg · Gross … kg (catalog X kg/m) · Assembly A1"; for bolts: "Bolts are not counted in part or assembly weights (Tekla bolt list)." | | | |
| Shown | when Steel is on (`stToggle` L13766); turning Steel on also selects Select, creates the default grid `0 6*6000 / 0 20000 / 0 7000`, toasts "Steel: grid, columns, beams and plates the Tekla way" | | | |

### 5.2 Generic sheet `#st-sheet` (CSS L13734–13740, `stSheet` L13778)

| Part | CSS (eff.) | Rendered (03) |
|---|---|---|
| `#st-sheet` | `position:fixed; left:8px; right:8px; top:64px; bottom:8px; z-index:58; max-width:640px; margin:0 auto; padding:12px; overflow:auto (whole sheet scrolls); border:1px solid var(--border,#ddd); border-radius:16px; box-shadow:0 6px 28px rgba(0,0,0,.28); background:var(--panel,#fff) (D rgba(34,37,44,.94) — translucent!); color:var(--fg,#111); font:14px system-ui,sans-serif`. Covers the Steel panel; no safe-area inset | x8 y64 396×819 |
| `h3` (title) | `margin:4px 0 10px; font-size:16px` 700 | x21 y81 370×20 |
| `h4` | `margin:14px 0 6px; font-size:13px; opacity:.75; text-transform:uppercase; letter-spacing:.04em` | |
| `.g` (label/field grid) | `display:grid; grid-template-columns:120px 1fr; gap:6px 10px; align-items:center` | labels x21, inputs x151 w240 |
| `input`, `select` | `font:inherit; padding:6px 8px; border-radius:8px; border:1px solid var(--border,#ccc); background:transparent; color:inherit; min-width:0` | h **31** |
| `.two` | `display:flex; gap:6px`; children `flex:1` | |
| `table` | `border-collapse:collapse; width:100%; font-size:13px`; td/th `padding:5px 6px; border-bottom:1px solid var(--border,#e5e5e5); text-align:left`; `.n` right-aligned, tabular numbers | |
| `button` / `.on` | same pills as §5.1 (`.on` = red #c0392b) | h 33 |
| `.bar` (button row) | **eff.** = main `.bar` (L23) + `#st-sheet .bar` (L13740): `display:flex; gap:6px; justify-content:flex-end; flex-wrap:wrap; margin-top:12px; width:100%; max-width:720px; align-items:center; background:var(--panel); border:1px solid var(--border); border-radius:16px; padding:6px 4px 6px 10px; box-shadow:0 4px 18px rgba(0,0,0,.12); backdrop-filter:blur(8px)` — the button row is a **rounded framed strip** (screenshot 03) because it reuses the top bar's `.bar` class | |
| `p` | browser default: margin 14px 0 (1em) | |
| Close | `stClose` removes the sheet; tapping a new sheet replaces the old one (only one `#st-sheet`) | |

**Grid sheet** (`stGridSheet` L13784–13789, screenshot 03): h3 "Grid"; rows "X (mm)", "Y (mm)", "Z levels (mm)" (defaults `0 6*6000`, `0 20000`, `0 7000`); p "Tekla syntax: the first value is where the grid starts, then the spacings; 4*6000 = four bays of 6000. X lines are numbered 1, 2, 3…, Y lines lettered A, B, C…"; `.bar`: Cancel, **Apply** (`.on`, red). Apply errors: toast "X and Y need at least one line each"; success toast "Grid N × M lines, K levels".

### 5.3 Catalog sheet `#st-sheet.cat` (CSS `stCss7` L14376–14382; `stCatalog` L14383–14394; thumbnails L16400)

| Part | CSS / content | Rendered (04) |
|---|---|---|
| Title | h3 "Applications & components" | |
| Search `.srch` | `input type=search`, `width:100%; margin:0 0 8px` + sheet input style; placeholder "Search: name or number, e.g. end plate, 144"; filters by label + Tekla name + key; none → p "Nothing matches." | x21 y111 370×31 |
| Group `details` | `margin:2px 0 6px`; all open; "Recently used" group first when any (last 5, `ss.connRecent`) | |
| `summary` | `font-weight:600; padding:6px 2px; font-size:13.5px`; text "Base plates (13)" with the default ▼/▶ marker | |
| Row `.ci` | `display:flex; align-items:center; gap:10px; padding:5px 6px; border-radius:10px; border:1px solid transparent`; hover border `rgba(127,127,127,.35)`. Tap = pick the component (or run the application) | |
| Thumbnail | `<img>` **72×54** (big: 160×120), `object-fit:cover; border-radius:6px` (webp from `SS_THUMBS`, white background in the image). Fallback SVG icon: `background:#f3f5f8; border-radius:8px` | |
| Text `.tx` | `flex:1; min-width:0`; `b` block 600 (14px) = label; `span` 12px opacity .7 = Tekla name "Simple base plate (55)" | |
| Properties button | pill with `padding:5px 9px` → 88×29; opens the component dialog | x296 |
| Footer | p "Tap a component, then pick its parts in the model. N components in the model." + `.bar`: Symbols / Symbols ✓ (`.on`), Close | |
| Other parts (same chapter) | `.tabs{display:flex;gap:4px;flex-wrap:wrap;margin:8px 0}`, `.pic{display:flex;justify-content:center;background:#f3f5f8;border-radius:12px;padding:6px;margin:6px 0}`, `.sv{display:flex;gap:6px;align-items:center;flex-wrap:wrap;margin:4px 0}` (select/input `flex:1; min-width:90px`) | |

### 5.4 Component dialog `#st-sheet.tkdlg` (v58; builder `stCompDialog` L16304–16365; CSS L16366–16398)

Layout, top to bottom: header `.tkhd` → (optional `.tkmsg`) → top bar `.tktop` → tabs `.tktabs` → body `.tkbody` (scrolls; holds one `.tkpane` per tab) → bottom bar `.bar.tkbar`.

| Part | CSS (eff. at 412 px) | Light / default | Dark (L16368) | Rendered (05/06) |
|---|---|---|---|---|
| `#st-sheet.tkdlg` | ≤560px: `left:0; right:0; bottom:0; top:28px; border-radius:12px 12px 0 0`; `max-width:760px; padding:0; display:flex; flex-direction:column; overflow:hidden; font:13px system-ui,"Segoe UI",sans-serif`; border 1px var(--border); z 58 | bg `#eef0f3`, text `#1d2330` | bg `#23272e`, text `#e8eaed` | x0 y28 412×863 |
| Header `.tkhd` | `display:flex; gap:10px; align-items:center; padding:8px 10px; background:linear-gradient(#fdfdfd,#e3e6ea); border-bottom:1px solid #c5cad1; color:#1d2330` | same | same (stays light) | ~64 high |
| Header image | `img{width:64px;height:48px;object-fit:cover;border-radius:4px;border:1px solid #c5cad1}`; fallback `.tkic svg` 44×44 | | | |
| Header text | `div{flex:1;min-width:0}`; `b{display:block;font-size:14px}` = Tekla name "End plate (144)"; `small{display:block;opacity:.7;nowrap;ellipsis}` = "defaults for new components" or "#id · part · part" | | | |
| Status pill `.tkst` (only for an existing component) | `padding:3px 9px; border-radius:999px; font-weight:600; font-size:12px`; s1 bg #2f9e44 / #fff; s2 #f2b705 / #3b2c00; s3 #c92a2a / #fff | | | |
| Message `.tkmsg` | `padding:6px 10px; font-size:12px`; s2 bg #fff8db text #5c4400; s3 #ffe3e3 / #7a1010 | | | |
| Top bar `.tktop` | `display:flex; gap:5px; align-items:center; padding:6px 8px; overflow-x:auto (scrolls); border-bottom:1px solid #c5cad1; background:#f6f7f9; color:#1d2330; flex:none` | same | same | buttons y100 |
| Top bar content | Save · Load · preset `select` ("standard" + saved) · Save as · preset-name `input` (placeholder "preset name") · `label.tkchk` ☑ " ignore other types" (checked) · Welds… · Help (opens support.tekla.com). Select/input: eff. `width:110px` but `min-width:0` lets the flex row squash them (rendered 18 px wide: only the ⌄ shows) | | | |
| `.tkchk` | `display:flex; align-items:center; gap:4px; white-space:nowrap; font-size:12px; color:#1d2330` | | | |
| Checkbox | `input[type=checkbox]{width:18px;height:18px;accent-color:#2a6fd1}` | | | 18×18 |
| Dialog button (all) | `!important`: `border-radius:4px; padding:6px 10px; background:linear-gradient(#fff,#e6e9ed); color:#1d2330; border:1px solid #aab2bd`; `min-height:32px`; font inherit 13px | same | same | h **32** |
| Button `.on` (active tab, OK) | `background:#fff!important; border-bottom-color:#fff!important; font-weight:600` | | | |
| Tabs `.tktabs` | `display:flex; gap:2px; overflow-x:auto (scrolls; active tab scrolled to centre); padding:6px 8px 0; background:#dfe3e8; border-bottom:1px solid #aab2bd; flex:none` | same | same | tabs y145 |
| Tab button | `border-radius:5px 5px 0 0!important; border-bottom:none!important; white-space:nowrap; background:linear-gradient(#f4f5f7,#d9dde3)!important` | | | |
| T144 tabs | Picture, Plates, Stiffeners, Haunch, Notch, Bolts, Holes, Angle box, General, Design, Welds (from `tkSpec`) | | | |
| Body `.tkbody` | `flex:1; overflow:auto; padding:8px` (the only vertical scroller) | | | |
| Pane `.tkpane` | `background:#fff; border:1px solid #c5cad1; border-radius:0 4px 4px 4px; padding:8px; color:#1d2330` | bg #fff | bg **#2b3038** (text stays #1d2330 → legend and labels are dark-on-dark in v69, screenshots 05/06) | |
| 3D picture `.tk3d` (Picture tab) | `display:block; margin:0 auto 6px; width:100%; height:min(26vh,240px)` (= 231.7 at 891 px) `; object-fit:contain; background:#fff; border:1px solid #dde1e6; border-radius:4px` | | | |
| Dimension picture `.tkpic` | `position:relative; width:100%; max-width:min(100%, calc(46vh*400/260)); margin:0 auto; aspect-ratio:400/260; background:#fff; border:1px solid #dde1e6; border-radius:4px`; SVG `viewBox 0 0 400 260` fills it. `.tk3dpic`: aspect 520/360, img fill. `.tkicon`: flex centre, min-height 120, svg 160×120 | #fff | #fff (L16368) | |
| Dimension box `.tkdim` | `position:absolute; left/top = anchor % ; transform:translate(-50%,-50%); display:flex; align-items:center; gap:2px; background:rgba(255,255,255,.92); border:1px solid #c0392b; border-radius:4px; padding:1px 2px`; `.off` opacity .45 | | | |
| `.tkdim b` (number/letter) | `color:#c0392b; font-size:11px; min-width:10px; text-align:center` | | | |
| `.tkdim input` | **eff.** (ID rule wins): `width:46px; font:inherit (13px); padding:6px 8px; border-radius:8px; border:1px solid var(--border); background:transparent; color:#1d2330; text-align:right`. (Written but overridden: `52px, 4px 3px, #9aa3ad, radius 3, #fffef2, 14px at ≤560`) | | | 46×**30** |
| Legend `.tklegend` | `display:flex; flex-wrap:wrap; gap:3px 12px; font-size:11.5px; opacity:.8; margin:6px 2px`; `b` #c0392b | | | |
| `h4` in dialog | **eff.**: `margin:14px 0 6px; font-size:13px; opacity:.75; text-transform:uppercase; letter-spacing:.04em; border-bottom:1px solid #dde1e6; padding-bottom:2px; color:#2a3a52` ("BOLT GROUP") | | | |
| Field grid `.tkg` | `display:grid; grid-template-columns:1fr; gap:4px` | | | |
| Row `.tkrow` | ≤560: `grid-template-columns:22px 1fr` (3rd child moves to column 2 → label above input); wide: `22px minmax(120px,1.2fr) minmax(110px,1fr)`; `align-items:center; gap:6px`; `.off` opacity .5; `small` col 2/4, opacity .6, 11px, margin-top −3px. Col 1 = on/off checkbox `.tkon` | | | |
| Row text input | **eff.**: `width:100%; padding:6px 8px; border-radius:8px; border:1px solid var(--border); background:transparent; color:inherit; font:inherit; box-sizing:border-box` | | | |
| Picker `.tkpkb` (custom select with icon) | `display:flex; align-items:center; gap:6px; width:100%; text-align:left; padding:3px 6px; background:#fff` (+ dialog button style); `span` ellipsis; `i` (▾) opacity .6. List `.tkpkl`: `position:absolute; z-index:5; left:0; right:0; top:100%; background:#fff; border:1px solid #9aa3ad; border-radius:4px; box-shadow:0 6px 18px rgba(0,0,0,.2); max-height:260px; overflow:auto; min-width:180px`; item `.tkpki` min-height 38, white, hover #e7f0fd | | | "Middle", "Top" |
| Parts table `table.tkparts` (in `.tkscroll{overflow-x:auto}`) | `border-collapse:collapse; min-width:640px; font-size:12px`; th `background:#e9ecf0; 600; padding:4px 5px; border:1px solid #d3d8de`; td `padding:2px 3px; border:1px solid #e3e6ea`; first td nowrap 500; inputs eff. sheet input style (written: 66px, #fffef2); `.tkauto` "auto" opacity .5 11px. Columns: Part, t, b, h, Pos_No prefix, start, Material, Name, Finish | | | |
| Welds table | same class; columns Weld, Size a, Type, Shop/Site, Around, Length; note `.tknote` 11.5px opacity .7 margin 6px 2px | | | |
| Bottom bar `.bar.tkbar` | **eff.**: `display:flex; flex-wrap:wrap; gap:6px (row-gap 6); justify-content:flex-end; margin:12px 0 0; padding:8px 8px 14px; background:#f6f7f9; border:1px solid var(--border); border-top:1px solid #c5cad1; border-radius:16px; box-shadow:0 4px 18px rgba(0,0,0,.12); flex:none; width:100%` (stays light in dark; screenshot shows rounded top corners) | #f6f7f9 | #f6f7f9 | ~2 rows |
| Bottom bar content | **OK** (`.on`: OK on an existing component = Modify + close, else Apply + close) · Apply (save as defaults for new) · Modify · Get · on/off (toggles all field checkboxes) · ☐ "all of this type" · Pick parts · Cancel · (Delete, only for an existing component) | | | |

### 5.5 Status line `#tk-status` (DOM L16105, CSS L16113–16114, text L16107)

| Property | Value |
|---|---|
| Box | `position:fixed; left:8px; right:8px; bottom:84px; z-index:57; max-width:760px; margin:0 auto; padding:7px 12px; border-radius:10px; font:12.5px system-ui,sans-serif; box-shadow:0 3px 14px rgba(0,0,0,.25); white-space:normal; max-height:30vh; overflow:auto` |
| `.s1` OK | bg `#e9f8ee`, text `#14532d`, border `1px solid #2f9e44` |
| `.s2` warning | bg `#fff8db`, text `#5c4400`, border `1px solid #f2b705` |
| `.s3` error | bg `#ffe3e3`, text `#7a1010`, border `1px solid #c92a2a` |
| Text | messages joined with "  ·  ", or "<Tekla name> #<id>: OK" |
| Shown | when a component symbol is selected (`tkSelect(id)`); hidden for `null`; tapping it deselects. Same in both themes |

### 5.6 Symbols and grid in the 3D view

| Item | Source | Values |
|---|---|---|
| Component symbol | `tkSymTex` L16062–16068, `stSymDraw` L16082–16086 | Sprite, `sizeAttenuation:false`, `scale 0.026` (≈ 0.026 × H/2 / tan 22.5° ≈ **28 CSS px** at H 891), renderOrder 40, depthTest on. 64×64 canvas cone: apex (32,8), base ellipse at (32,48) rx 20 ry 7; gradient x 14→50 with stops 0 / .55 / 1; green `#69db7c #2f9e44 #14532d`, yellow `#ffe066 #f2b705 #8a6500`, red `#ff6b6b #c92a2a #7a1010`; outline `rgba(0,0,0,.55)` 2px; selected: halo `rgba(255,230,0,.35)` r30 + outline `#ffe600` 4px. Not drawn for TKCUT/TKBOLT. De-clutter: one per 24 px cell, errors first (L16093+) |
| Lines to joined parts | `tkFanDraw` L16088–16092 | `LineDashedMaterial` `0xffd400`, dash 60 mm, gap 30 mm, depthTest off, renderOrder 41 |
| Steel grid lines (all levels) | `stGridDraw` L13651–13663 | dashed `0x4a5a70`, opacity .55, dash 600 mm, gap 300 mm; extend 1500 mm past the outer lines |
| Work-level grid | same | solid `0x2f6fb3`, opacity .85 |
| Grid labels (1, 2, …; A, B, …) | `stLabelSprite` L13645–13650 | 128 canvas: white disc r58, ring `#3a4250` 6px; text `#1d2330` 600 54px (34px if >3 chars), sprite size 900 mm, depthTest off, placed 0.6 × size outside the first line |
| Level labels (+0, +7000) | same | no disc, size 1.6 × 900 mm, at (x1 + size, y1, z) |

### 5.7 Other overlays used around Steel

| Element | Lines | CSS |
|---|---|---|
| `#cam-2d` round "2D" camera button | L8489–8496 | `position:fixed; z-index:8; right:max(12px, env(safe-area-inset-right,0px)); 52×52; border-radius:50%; border:1px solid var(--border); background:var(--panel); color:var(--accent); box-shadow:0 4px 14px rgba(0,0,0,.18); font:700 10px/1 var(--font)`; SVG camera 22×22 + "2D"; pressed scale .94; hidden by default, tap does nothing without a sketch plane (`S.plane`) |
| `#xt-items` (◫ list) | L12299–12302 | `fixed; left:10px; top:calc(env(safe-area-inset-top)+104px); width:min(300px,82vw); max-height:60vh; overflow:auto; z 7; background:var(--panel); border 1px var(--border); radius 16; padding 8; shadow 0 8px 30px rgba(0,0,0,.22)`; head 700 15px; row padding 9 8 radius 10 gap 10; selected bg rgba(56,189,240,.18); name 14px 500, sub 11px muted; hidden rows opacity .45 |
| `#xt-status` | L12297 | `fixed; left:50%; translateX(-50%); top:calc(env(safe-area-inset-top)+68px); z 4; var(--panel); border var(--border); radius 999; padding 4px 12px; font:600 12px ui-monospace…; max-width:94vw; ellipsis` |
| DEV badge (dev copy only, never ship) | L247 | `fixed; right:8px; bottom:calc(env(safe-area-inset-bottom)+4px); 600 11px; #fff on rgba(192,57,43,.75); padding 2px 8px; radius 8` |

### 5.8 z-index ladder

`#xt-status` 4 · `#menu` 5 (inside #top) · `#snaptip`/`#dim`/labels 6 · `#xt-items` 7 · `#cam-2d` 8 · `#toast` 9 · cards `#help`/`#paste`/`#ss-save` 10 · `#loading` 20 · `#hist-sheet`, `#keep-prompt` 40 · **`#st-panel` 55** · **`#tk-status` 57** · **`#st-sheet` 58**.

---

## 6. Safe areas, insets and viewport units

- `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">` (L5): the page draws edge to edge, under the status and navigation bars.
- `env(safe-area-inset-*)` uses:
  - `:root{padding-top/bottom: env(safe-area-inset-top/bottom, 0px)}` (L10–11) and `html{scroll-padding-top: env(safe-area-inset-top)}` (L18). These do not move the fixed UI.
  - `#top` padding-top `inset-top + 8px` (L22); `#bottom` padding-bottom `inset-bottom + 10px` (L35); `#toast` top `inset-top + 72px` (L69).
  - `#cam-2d` right `max(12px, inset-right)` (L8493); `#xt-status` top `inset-top + 68px`; `#xt-items` top `inset-top + 104px` (L12297–12299); walkthrough bar/hint/sheet/map (L12908–12932); license lock (L125); DEV badge (L247).
  - **Steel elements use no insets**: `#st-panel` bottom 8px, `#st-sheet` top 64px / bottom 8px, `.tkdlg` top 28px / bottom 0, `#tk-status` bottom 84px, `#hist-sheet` top 64px. On Android edge-to-edge they would sit under the gesture bar / status bar. The port should add the system-bar insets (the browser capture had insets = 0).
- Viewport units: `#hint` max-width 92vw; `#toast` 90vw; `.card` max-height 80vh; `#keep-prompt` 92vw; `#xt-items` 82vw / 60vh; `#xt-status` 94vw; `.tkpic` `46vh`; `.tk3d` `min(26vh,240px)`; `#tk-status` max-height 30vh; Document manager list 58vh (50vh ≤700px). No `dvh`/`svh` and no `visualViewport` handling.
- Phone media queries: `max-width:420px` (top bar, L8346), `max-width:560px` (`.tkdlg` full-width bottom sheet, L16398), `max-width:700px` (Document manager, L16842). All apply at 412 px.

## 7. v69 quirks visible in the reference (decide per item: copy or fix)

1. `#st-sheet .bar` and `.tkbar` pick up the top bar's `.bar` style (rounded framed strip with shadow).
2. Toast width is capped at 50 % of the viewport (206 px) by `left:50%`, and it sits under Steel sheets (z 9 < 58).
3. `.tkdlg` dark mode: pane `#2b3038` but text stays `#1d2330`, so legends and row labels are nearly invisible; header, top bar, tabs and bottom bar stay light.
4. The ID rule `#st-sheet input` overrides the dialog's own input styles (46 px transparent boxes, radius 8, instead of 52 px cream `#fffef2`, radius 3).
5. In `.tktop` the preset select and the preset-name input shrink to ~18 px.
6. `#menu` has no max-height; items below ~y 770 sit under the Steel panel.
7. "Remove imported models" is always shown (`display:block` beats `hidden`).
8. Steel grid level labels are dark text (`#1d2330`) without a disc, so they are hard to read on the dark background.
