# Emulator UI tests on GitHub Actions (owner, 2026-11-24)

Workflow: `.github/workflows/android-emulator.yml` (on every push to `main`, and by hand: Actions → Emulator UI tests → Run
workflow).

1. **Data check**: the large data files must be on `main` (assets profiles / TK catalog / TK UI / thumbs, the S6–S9
   golden files). Without them the job stops after a few seconds and says so (owner bundle `steel-all-data`).
2. **Unit tests with the UI golden** (`testDebugUnitTest`): the gate that runs before every APK (`docs/steel/UI-GOLDEN.md`).
3. **Build**: the debug APK and the test APK, x86_64 only (`-Pabis=x86_64`); NDK 30.0.16248370, CMake 4.1.2.
4. **Emulator**: x86_64, API 35, google_apis, KVM (reactivecircus/android-emulator-runner), set up as the owner's phone by
   `tools/ci/emulator_run.sh`: `wm size 1080x2340`, `wm density 420`, dark mode (`cmd uimode night yes`), 3-button
   navigation (navbar overlay). The screen is recorded while the tests run (screenrecord, 175 s segments).
5. **Instrumented tests** (`app/src/androidTest/.../EmulatorFlowTest.kt`), the owner's workflow with real touch input:
   start → Steel → Grid (`0 4*13195` / `0 5*5570` / `0 7500 1500`, Apply) → orbit, pan (two fingers), pinch zoom, each
   followed by a check that every drawn grid label is at its projected line end (1 px) → Column on a grid point →
   Beam tool, Properties → profile IPE400 → Apply → beam between two grid points (IPE400) → the beam selected, Properties →
   IPE500 → Modify → Components (catalog, search 144) → End plate (144) dialog (tabs and buttons) → End plate 144 picked on
   the column and the beam → save to a file and open it again (the same parts and connections). Save / open use the
   activity's own code with a file (`testSave` / `testOpen`); the system file picker is not driven. Every step takes a
   screenshot (the whole screen, 3D view included) and a failed step does not stop the later ones.
6. **Artifacts**: `screenshots`, `screen-recording`, `test-report` (instrumented + unit HTML reports, logs, device info),
   `apk` (debug APK and test APK).
7. **Summary**: the job page, and the `ci-results` branch (`latest/summary.md`, `runs/<run id>/`), which shows every step,
   the failures, the device settings and how long the job took.

Caches: Gradle (setup-gradle), NDK + CMake + Manifold source, the AVD snapshot.
