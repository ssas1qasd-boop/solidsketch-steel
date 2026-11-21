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
| Kotlin, Jetpack Compose, AndroidX | per build | Apache-2.0 | app | yes |
| Manifold | (pin at S2) | Apache-2.0 | `native/sscore-lite` | yes |
| Playwright | (pin at S1) | Apache-2.0 | `tools/golden` (tests only) | no |
