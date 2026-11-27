# Emulator UI run 46000493650 (c5d9df5)

- data files on main: true
- KVM: yes · runner cores: 2
- unit tests incl. UI golden (gate before the APK): success
- build APK + test APK: success
- emulator tests step: success
- connectedDebugAndroidTest exit code: 0 (359 s)
- job time until this step: 1253 s = 20.9 min (billed: whole minutes, rounded up per job)

Device:
```
Physical size: 1080x2400
Override size: 1080x2340
Physical density: 420
Night mode: yes
[x] com.android.internal.systemui.navbar.threebutton
[ ] com.android.internal.systemui.navbar.twobutton
[ ] com.android.internal.systemui.navbar.transparent
[ ] com.android.internal.systemui.navbar.gestural
```

JUnit (unit + instrumented): 60 tests, 0 failed

| step | ok | ms | message |
|---|---|---|---|
| start | ✓ | 1703 |  |
| steel | ✓ | 8277 |  |
| grid | ✓ | 10516 |  |
| orbit | ✓ | 1661 |  |
| pan | ✓ | 1539 |  |
| zoom | ✓ | 1606 |  |
| column | ✓ | 3769 |  |
| properties_apply | ✓ | 7790 |  |
| beam | ✓ | 3240 |  |
| properties_modify | ✓ | 7538 |  |
| catalog | ✓ | 7090 |  |
| endplate_dialog | ✓ | 3335 |  |
| endplate_pick | ✓ | 4972 |  |
| cleat_dialog | ✓ | 11221 |  |
| save_open | ✓ | 2102 |  |
| portal_create | ✓ | 29799 |  |
| portal_modify | ✓ | 49795 |  |

connected.log (end):
```
> Task :app:mergeDebugNativeLibs UP-TO-DATE
> Task :app:stripDebugDebugSymbols UP-TO-DATE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:writeDebugAppMetadata UP-TO-DATE
> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
> Task :app:packageDebug UP-TO-DATE
> Task :app:createDebugApkListingFileRedirect UP-TO-DATE
> Task :app:mergeDebugAndroidTestShaders UP-TO-DATE
> Task :app:compileDebugAndroidTestShaders NO-SOURCE
> Task :app:generateDebugAndroidTestAssets UP-TO-DATE
> Task :app:mergeDebugAndroidTestAssets UP-TO-DATE
> Task :app:compressDebugAndroidTestAssets UP-TO-DATE
> Task :app:desugarDebugAndroidTestFileDependencies UP-TO-DATE
> Task :app:dexBuilderDebugAndroidTest UP-TO-DATE
> Task :app:mergeDebugAndroidTestGlobalSynthetics UP-TO-DATE
> Task :app:processDebugAndroidTestJavaRes UP-TO-DATE
> Task :app:mergeDebugAndroidTestJavaResource UP-TO-DATE
> Task :app:checkDebugAndroidTestDuplicateClasses UP-TO-DATE
> Task :app:mergeExtDexDebugAndroidTest UP-TO-DATE
> Task :app:mergeLibDexDebugAndroidTest UP-TO-DATE
> Task :app:mergeProjectDexDebugAndroidTest UP-TO-DATE
> Task :app:mergeDebugAndroidTestJniLibFolders UP-TO-DATE
> Task :app:mergeDebugAndroidTestNativeLibs NO-SOURCE
> Task :app:stripDebugAndroidTestDebugSymbols NO-SOURCE
> Task :app:validateSigningDebugAndroidTest UP-TO-DATE
> Task :app:writeDebugAndroidTestSigningConfigVersions UP-TO-DATE
> Task :app:packageDebugAndroidTest UP-TO-DATE
> Task :app:createDebugAndroidTestApkListingFileRedirect UP-TO-DATE
[EmulatorConsole]: Failed to start Emulator console for 5554
> Task :app:connectedDebugAndroidTest
Starting 2 tests on emulator-5554 - 15

emulator-5554 - 15 Tests 0/2 completed. (0 skipped) (0 failed)
emulator-5554 - 15 Tests 1/2 completed. (0 skipped) (0 failed)
emulator-5554 - 15 Tests 2/2 completed. (0 skipped) (0 failed)
Finished 2 tests on emulator-5554 - 15
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/emu-1795783774929.json

BUILD SUCCESSFUL in 5m 57s
133 actionable tasks: 6 executed, 127 up-to-date

```
