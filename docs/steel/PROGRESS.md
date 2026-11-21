# Steel progress

## Current milestone
S0, partly done.

## Done (2026-11-21)
- Repo created; `CLAUDE.md` (the plan) pushed.
- `docs/steel/FEATURES.md`: inventory of v69 (31 chapters, 500 profiles, 246 Tekla components in 17 groups, 10 full dialogs, outputs).
- `docs/steel/LIVE-FUNCTIONS.md`: 24 patched function chains with line numbers (`stConnGeom` has 23 steps, `stOwner` 17, `stChecks` 9…).
- `docs/steel/MERGE.md`: the interfaces `:steel` may use, so that the later merge is a swap of core modules.
- `docs/LICENSES.md`: hard rule; LibreDWG and OpenCASCADE excluded.
- `tools/inventory/`: scripts that regenerate the two inventories from the HTML.

## Half done
- App skeleton (Gradle modules, license gate, first debug APK): NOT started. The session where the docs were made has no access to the Android SDK or Google's Maven repository (network blocked), so nothing could be built there.

## Next step
In a Claude Code session that can reach dl.google.com and maven.google.com (and with the Claude GitHub App given access to this repo): create the Gradle project with modules `:app`, `:core-api`, `:core-view`, `:core-io`, `:steel` and the native folders, copy the license gate from the main port's rules, run `assembleDebug`, copy the APK to `dist/`, commit.

## Decisions
- Separate app until the merge (owner, 2026-11-21).
- Own DWG writer, clean-room, R2000 (owner, 2026-11-21).
- Repo: `ssas1qasd-boop/solidsketch-steel` (owner, 2026-11-21).

## Open (owner)
- applicationId (proposed `com.solidsketchapp.steel`) and which license variants unlock Steel.
- Tekla `.db1` import: in or out of this plan.
