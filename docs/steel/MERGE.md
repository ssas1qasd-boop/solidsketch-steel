# Merge contract: Steel app → main native port

Steel is built as its own app now and merged into the main native port when that port is finished. This file lists what `:steel` needs from the core. The main port must provide each item (or an adapter) at merge time. Keep this file current: every time `:steel` starts using something new from the core, add it here first.

## Rule
`:steel` and `native/sssteel` depend only on `:core-api` (Kotlin interfaces) and on the C API of `sscore-lite` (`sscore_lite.h`). They never import a class from `:core-view` or `:core-io` directly.

## Kotlin interfaces (`:core-api`)
| Interface | What Steel uses it for | Expected from the main port |
|---|---|---|
| `DocumentApi` | bodies list, add/remove/replace body (JSON + optional `Mesh`, S2), `bodyMesh`, folders, extra document fields (`stGrid`, `stConns`, `stNum`, `stDwg`, `stProject`…) kept as raw JSON | its document model + a map for unknown fields + the body's triangle mesh |
| `ViewportApi` | add/remove overlay groups (grid, preview, handles, welds, marks, issues, symbols), camera fit, screen↔world rays, request redraw; S5: `setTapHandler` (Steel tools get a tap first and can keep it from body picking), `addViewListener` / `removeViewListener` (called after every camera change, for screen-space labels) | its GL renderer with named overlay layers, a tap hook before its own picking, a camera-changed callback |
| `SelectionApi` | pick body / face / point under a touch, listen to selection changes, highlight | its picking |
| `UndoApi` | group several changes into one undo step | its undo stack |
| `BooleanEngine` | subtract cut solids from parts, union for assemblies | Manifold first, CGAL fallback (main-port rule) |
| `FileApi` | save exports (PDF, DXF, DWG, CSV, IFC, NC zip) to Downloads/SolidSketch | its MediaStore / file picker code |
| `LicenseApi` | is the app unlocked; which plan | its `LicenseManager` (Steel's gate is postponed: `:app` uses an always-allow `LicenseGate`) |

Plain helpers in `:core-api` (no interface; the main port keeps them or provides the same functions):
- `Fmt.f`: number formatting with `Locale.ROOT` (Latin digits whatever the phone's language).
- `JsNumber.format`: a double as JavaScript writes it (`String(x)` / `JSON.stringify`). The `.ss` writer uses it, and `:steel` uses it for profile keys such as `SHS100*5` (S5). Moved here from `:core-io` in S5 (`:core-io` keeps a typealias under the old name).

## C API (`native/sscore-lite/include/sscore_lite.h`)
Mesh in/out (float positions, uint32 indices), box/prism/extrude from a 2D outline, boolean subtract/union/intersect, closed-solid check, volume. Same function names and types as the main port's `sscore` where they overlap. At merge time `sscore-lite` is deleted and `sssteel` links `sscore` (set the CMake cache variable `SSCORE_DIR` in `native/sssteel/CMakeLists.txt`).

Functions (S2, Manifold 3.5.4 underneath): `sscore_lite_version`, `ss_box`, `ss_extrude`, `ss_cylinder`, `ss_subtract`, `ss_union`, `ss_intersect`, `ss_subtract_many` (a part minus all its cuts in one pass), `ss_union_many`, `ss_is_closed_solid`, `ss_volume`, `ss_mesh_free`; types `ss_mesh`, `ss_status`. Inputs may be flat-shaded (duplicated vertices); they are welded before a boolean. **TODO at merge:** compare these names with the main port's `sscore.h` (not available to this plan) and rename here, not in `:steel`.

Kotlin side: `BooleanEngine` is implemented by `ManifoldBooleanEngine` (`:core-view`, JNI `NativeCore`). `:steel` must only see `BooleanEngine`.

## Core behaviour Steel relies on (S2)
- Viewport overlays are named line groups (`ViewportApi.setOverlay(name, xyz pairs, argb)`); Steel uses names starting with `st.`.
- Orbit is fully free (quaternion, no clamping; owner rule). Selection draws the body with v69's emissive `0x6b4a10`. Tap picks the nearest triangle; double tap fits all.
- Undo is snapshot-based (`UndoStack` over the document snapshot): one `UndoApi.group` = one step.

## Where Steel gets them
`:steel` receives the interfaces in one object, `SteelHost` (`steel/src/main/java/com/solidsketchapp/steel/mode/SteelHost.kt`). The host app builds it. At merge the main app builds the same object from its own implementations.

## Data
`.ss` format exactly as in the web build. Steel part data lives in `body.st`; connections in `doc.stConns`. Unknown fields round-trip unchanged.

## Open at merge
- applicationId: Steel uses a temporary id (owner decision) until the merge.
- UI entry: Steel becomes a mode inside the main app ("Steel" / "Exit Steel"), as in the web build.
