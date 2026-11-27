# Emulator UI run 46073759881 (0d069d4)

- data files on main: true
- KVM: yes · runner cores: 4
- unit tests incl. UI golden (gate before the APK): failure
- build APK + test APK: success
- emulator tests step: success
- connectedDebugAndroidTest exit code: 0 (152 s)
- job time until this step: 618 s = 10.3 min (billed: whole minutes, rounded up per job)

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

JUnit (unit + instrumented): 63 tests, 19 failed

```
com.solidsketchapp.steel.UiScreensTest.s01_main: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiScreensTest.s05_dialog_t144: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiScreensTest.s07_menu: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiScreensTest.s06_dialog_t144_bolts: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiScreensTest.s03_grid_sheet: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiScreensTest.s02_steel_grid: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiScreensTest.s04_catalog: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.GridLabelsFollowViewTest.labelsFollowEveryViewChange: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.catalogBoxes: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.dialogT144Boxes: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.visibleGridSheet: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.visibleCatalog: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.visibleMain: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.portalSheetBoxes: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.visibleDialogT144: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.panelBoxes: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.overlayFollowsEveryCameraState: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.visiblePortalSheet: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

```
com.solidsketchapp.steel.UiGoldenTest.gridSheetBoxes: java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:14-robolectric-10818077-i7
```

| step | ok | ms | message |
|---|---|---|---|
| start | ✓ | 1456 |  |
| steel | ✓ | 2850 |  |
| grid | ✓ | 5037 |  |
| orbit | ✓ | 1072 |  |
| pan | ✓ | 1014 |  |
| zoom | ✓ | 1142 |  |
| column | ✓ | 2193 |  |
| properties_apply | ✓ | 4350 |  |
| beam | ✓ | 2203 |  |
| properties_modify | ✓ | 2926 |  |
| catalog | ✓ | 3711 |  |
| endplate_dialog | ✓ | 2018 |  |
| endplate_pick | ✓ | 2597 |  |
| cleat_dialog | ✓ | 5454 |  |
| save_open | ✓ | 1276 |  |
| portal_create | ✓ | 14170 |  |
| portal_modify | ✓ | 18925 |  |

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
Finished 2 tests on emulator-5554 - 15
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/emu-1795816998905.json

BUILD SUCCESSFUL in 2m 32s
133 actionable tasks: 6 executed, 127 up-to-date

```
