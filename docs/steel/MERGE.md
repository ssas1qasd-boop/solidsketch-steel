# Merge contract: Steel app → main native port

Steel is built as its own app now and merged into the main native port when that port is finished. This file lists what `:steel` needs from the core. The main port must provide each item (or an adapter) at merge time. Keep this file current: every time `:steel` starts using something new from the core, add it here first.

## Rule
`:steel` and `native/sssteel` depend only on `:core-api` (Kotlin interfaces) and on the C API of `sscore-lite` (`sscore_lite.h`). They never import a class from `:core-view` or `:core-io` directly.

## Kotlin interfaces (`:core-api`)
| Interface | What Steel uses it for | Expected from the main port |
|---|---|---|
| `DocumentApi` | bodies list, add/remove/replace body, folders, extra document fields (`stGrid`, `stConns`, `stNum`, `stDwg`, `stProject`…) kept as raw JSON | its document model + a map for unknown fields |
| `ViewportApi` | add/remove overlay groups (grid, preview, handles, welds, marks, issues, symbols), camera fit, screen↔world rays, request redraw | its GL renderer with named overlay layers |
| `SelectionApi` | pick body / face / point under a touch, listen to selection changes, highlight | its picking |
| `UndoApi` | group several changes into one undo step | its undo stack |
| `BooleanEngine` | subtract cut solids from parts, union for assemblies | Manifold first, CGAL fallback (main-port rule) |
| `FileApi` | save exports (PDF, DXF, DWG, CSV, IFC, NC zip) to Downloads/SolidSketch | its MediaStore / file picker code |
| `LicenseApi` | is the app unlocked; which plan | its `LicenseManager` (Steel's gate is postponed: `:app` uses an always-allow `LicenseGate`) |

## C API (`native/sscore-lite/include/sscore_lite.h`)
Mesh in/out (float positions, uint32 indices), box/prism/extrude from a 2D outline, boolean subtract/union/intersect, closed-solid check, volume. Same function names and types as the main port's `sscore` where they overlap. At merge time `sscore-lite` is deleted and `sssteel` links `sscore` (set the CMake cache variable `SSCORE_DIR` in `native/sssteel/CMakeLists.txt`).

S0 functions: `sscore_lite_version`, `ss_box`, `ss_extrude`, `ss_subtract`, `ss_union`, `ss_intersect`, `ss_is_closed_solid`, `ss_volume`, `ss_mesh_free`; types `ss_mesh`, `ss_status`. Check these names against the main port's `sscore.h` before S2 and rename to match.

## Where Steel gets them
`:steel` receives the interfaces in one object, `SteelHost` (`steel/src/main/java/com/solidsketchapp/steel/mode/SteelHost.kt`). The host app builds it. At merge the main app builds the same object from its own implementations.

## Data
`.ss` format exactly as in the web build. Steel part data lives in `body.st`; connections in `doc.stConns`. Unknown fields round-trip unchanged.

## Open at merge
- applicationId: Steel uses a temporary id (owner decision) until the merge.
- UI entry: Steel becomes a mode inside the main app ("Steel" / "Exit Steel"), as in the web build.
