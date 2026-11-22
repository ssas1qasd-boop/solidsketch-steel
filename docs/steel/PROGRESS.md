# Steel progress

## Current milestone
S0 done (2026-11-21) and verified on a phone (2026-11-22), apart from the postponed license gate. Next: S1.

## Done (2026-11-21)
- Repo created; `CLAUDE.md` (the plan) pushed.
- `docs/steel/FEATURES.md`: inventory of v69 (31 chapters, 500 profiles, 246 Tekla components in 17 groups, 10 full dialogs, outputs).
- `docs/steel/LIVE-FUNCTIONS.md`: 24 patched function chains with line numbers (`stConnGeom` has 23 steps, `stOwner` 17, `stChecks` 9…).
- `docs/steel/MERGE.md`: the interfaces `:steel` may use, so that the later merge is a swap of core modules.
- `docs/LICENSES.md`: hard rule; LibreDWG and OpenCASCADE excluded; dependency table filled for S0.
- `tools/inventory/`: scripts that regenerate the two inventories from the HTML.
- `reference/solidsketch_v69.html` placed locally (20,495,043 bytes, 17,999 lines, checked). `reference/` is git-ignored.
- **App skeleton**, toolchain as in the main port (AGP 8.7.3, Gradle 8.10.2 wrapper, Kotlin 2.0.21, Compose BOM 2024.12.01, compile/target 35, minSdk 24, NDK 30.0.16248370, CMake 4.1.2, arm64-v8a + x86_64):
  - `:core-api` (pure Kotlin): `DocumentApi`, `ViewportApi`, `SelectionApi`, `UndoApi`, `BooleanEngine`, `FileApi`, `LicenseApi`, plus `Vec3`, `Ray`, `BodyId`, `Mesh`.
  - `:core-view`: `UndoStack` (snapshot undo, nested groups merge; 3 unit tests), `NativeCore` JNI → `libsscore_lite_jni.so`.
  - `:core-io`: `SsFile` gzip layer of `.ss` (1 unit test). Document model is S3.
  - `:steel`: depends on `:core-api` only; `SteelHost`, placeholder `SteelScreen`, `NativeSteel` JNI → `libsssteel_jni.so`.
  - `:app`: `com.solidsketchapp.steel` (temporary id), `MainActivity` wiring, `LicenseGate` (always allows).
  - `native/sscore-lite` (static; C API `sscore_lite.h`: box, volume work; extrude/booleans return `SS_ERR_NOT_IMPLEMENTED` until S2), `native/sssteel` (static, links sscore-lite via `SSCORE_DIR`), `native/ssdwg` (placeholder README for S12b), `native/tests` (host smoke test, passes).
- `./gradlew test assembleDebug` passes; APK copied to `dist/solidsketch-steel-s0-debug.apk` (dist/ is git-ignored). `assembleRelease` also built (unsigned) before the gate was postponed.

## Verified on a phone (owner, 2026-11-22)
- `dist/solidsketch-steel-s0-debug.apk` installed on the owner's phone: the app starts without a crash, both native libraries load (`sssteel 0.0.1` links `sscore-lite 0.0.1`), and the JNI call returns box 10×20×30 volume = 6000.0.

## Notes for S5
- The owner's phone is set to Arabic, so the layout flipped to RTL (text right-aligned, "Plan:" shown as "- :Plan"). Force LTR for the whole app, like the web build (for example `android:supportsRtl="false"` or `LocalLayoutDirection provides LayoutDirection.Ltr` at the root).
- "Plan:" on the placeholder screen shows "-" (empty value).

## Postponed: license gate (owner decision, 2026-11-21)
`:app` has only `LicenseGate` (`app/src/main/java/com/solidsketchapp/steel/license/LicenseGate.kt`), an interface that always allows, and `OpenLicenseGate`. No network permission, no key UI.

When it comes back, the rules given by the owner (from the main port's CLAUDE.md), checked against `<script id="ss-license-gate">` in v69 (line 79 of the HTML; values match):
- API `https://api.lemonsqueezy.com/v1/licenses/`, POST form-encoded, `Accept: application/json`, 12 s timeout, no API key. `activate` (license_key, instance_name), `validate` (license_key, instance_id), `deactivate` (license_key, instance_id).
- Allowed `meta.variant_id`: 2224132 (Yearly), 2224187 (Monthly), in ONE list. **TODO(owner): which variants unlock Steel.** Valid key of another product → deactivate the new instance, refuse "This key is not for SolidSketch Pro."
- Record (key, instance id, last successful check, plan) in encrypted storage. Device name "Android " + 6 random chars.
- Start: no record → lock screen. Record → "Checking your license…", validate. Valid + allowed → update time, start. Not valid → delete record, lock with: expired "Your subscription has ended. Renew it to keep using SolidSketch."; disabled "This license key has been disabled. Contact support if you think this is a mistake."; else API error or "This license is no longer active." Network failure → start if last check < 7 days, else "Could not reach the license server. Connect to the internet and try again."
- While open: re-validate if last check > 6 h (look every 30 min and on return to foreground). Invalid → delete, lock. Offline → keep working until 7-day grace ends.
- Lock screen buy links (from the v69 gate script): Monthly $4.99 `https://solidsketch.lemonsqueezy.com/checkout/buy/900d7ea4-d7a7-4e41-b821-799d6c94393f?enabled=2224187`, Yearly $34.99 `https://solidsketch.lemonsqueezy.com/checkout/buy/0c0cde4e-72e9-40f8-854a-c4d752364db7?enabled=2224132`, `https://solidsketchapp.com/#pricing`. Lock-screen CSS is `#ss-lock` in the same script. The web text "The browser version is for Pro members…" needs app wording (TODO(owner)).
- Debug-only bypass in the debug source set, `BuildConfig.DEBUG` only, red "DEBUG – license skipped" banner, a way to run the real gate; `scripts/verify_release_no_bypass.sh`; unit tests with a fake HTTP layer (activate, validate, grace, wrong variant).
- A started (untested, not compiled) port of these rules is on the branch `license-gate-draft` under `drafts/license/` (pushed 2026-11-22).

## Next step
S1: golden harness in `tools/golden/` (Playwright, pin version in LICENSES.md), dump R10 totals. Needs `tests/steel/models/Frame_28x53m_R10.ss` from the owner (not in the repo yet).

## How to build
Android SDK with platform 35, build-tools 35.0.0, NDK 30.0.16248370, CMake 4.1.2; `local.properties` with `sdk.dir`.
`./gradlew test assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
Host native test: `cmake -S native/tests -B build/native-tests && cmake --build build/native-tests && ctest --test-dir build/native-tests`.
Note: Maven Central answered 429 (Too Many Requests) to parallel downloads in the cloud session; `--no-parallel` on the first build helped.

## NOT VERIFIED
- UI look: the placeholder screens are not the web build's UI (that starts in S5).

## Decisions
- Separate app until the merge (owner, 2026-11-21).
- Own DWG writer, clean-room, R2000 (owner, 2026-11-21).
- Repo: `ssas1qasd-boop/solidsketch-steel` (owner, 2026-11-21).
- License gate postponed; always-allow `LicenseGate` for now (owner, 2026-11-21).
- `sscore-lite` and `sssteel` are static libraries; each JNI .so links its own copy, so modules never clash on shared-library names (S0).

## Open (owner)
- applicationId (proposed `com.solidsketchapp.steel`).
- License gate: when to bring it back, and which variants unlock Steel (TODO(owner)).
- Tekla `.db1` import: in or out of this plan.
- R10 test model and comparison file into `tests/steel/models/`.
