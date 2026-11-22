# Licenses (SolidSketch Steel)

**Hard rule: no GPL / LGPL / AGPL code or libraries, and no code copied from GPL projects.** The app is closed-source and commercial. If a task seems to need such a library, stop and report to the owner.

## Not allowed, found in the web reference
| Item in web v69 | License | Decision |
|---|---|---|
| LibreDWG (WebAssembly DWG writer) | GPL-3.0 | Not ported, and its source is not read. Own clean-room DWG writer instead (`native/ssdwg`, R2000, from the public ODA specification). Owner decision 2026-11-21. Note: the paid web build ships LibreDWG too. |
| OpenCASCADE (WebAssembly) | LGPL-2.1 with exception | Not needed for Steel; not used. |

## Dependencies
Add a row before adding any dependency.

| Dependency | Version | License | Where | Ships in APK |
|---|---|---|---|---|
| Kotlin stdlib / Kotlin Gradle plugin | 2.0.21 | Apache-2.0 | all modules | yes (stdlib) |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | build only | no |
| Gradle (wrapper) | 8.10.2 | Apache-2.0 | build only | no |
| AndroidX core-ktx | 1.15.0 | Apache-2.0 | app | yes |
| AndroidX lifecycle-runtime-ktx | 2.8.7 | Apache-2.0 | app | yes |
| AndroidX activity-compose | 1.9.3 | Apache-2.0 | app | yes |
| Jetpack Compose (BOM: ui, material3, ui-tooling debug only) | 2024.12.01 | Apache-2.0 | app, steel | yes |
| kotlinx-coroutines (transitive via Compose/lifecycle) | per BOM | Apache-2.0 | app | yes |
| JUnit | 4.13.2 | EPL-1.0 | unit tests only | no |
| Android NDK (libc++ runtime) | 30.0.16248370 | Apache-2.0 / Apache-2.0 with LLVM exception | native | yes (libc++ static) |
| Manifold | (pin at S2) | Apache-2.0 | `native/sscore-lite` | yes |
| Playwright (+ Chromium it drives) | 1.63.0 | Apache-2.0 (Chromium: BSD-3-Clause) | `tools/golden` (tests only) | no |
| Node.js (runs the harness) | 22 | MIT | `tools/golden` (tests only) | no |
