# Emulator UI run 45577932919 (4853e85)

- data files on main: true
- KVM: yes · runner cores: 2
- unit tests incl. UI golden (gate before the APK): success
- build APK + test APK: success
- emulator tests step: cancelled
- connectedDebugAndroidTest exit code: - (- s)
- job time until this step: 492 s = 8.2 min (billed: whole minutes, rounded up per job)

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

JUnit (unit + instrumented): 42 tests, 0 failed

connected.log (end):
```
> Task :core-io:processDebugJavaRes UP-TO-DATE
> Task :core-view:processDebugJavaRes UP-TO-DATE
> Task :steel:processDebugJavaRes UP-TO-DATE
> Task :core-view:bundleLibRuntimeToDirDebug UP-TO-DATE
> Task :core-io:bundleLibRuntimeToDirDebug UP-TO-DATE
> Task :steel:bundleLibRuntimeToDirDebug UP-TO-DATE
> Task :core-io:mergeDebugJniLibFolders UP-TO-DATE
> Task :core-io:mergeDebugNativeLibs NO-SOURCE
> Task :core-io:copyDebugJniLibsProjectOnly UP-TO-DATE
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:bundleDebugClassesToCompileJar UP-TO-DATE
> Task :app:generateDebugAndroidTestBuildConfig UP-TO-DATE
> Task :fetchManifold
> Task :app:checkDebugAndroidTestAarMetadata UP-TO-DATE
> Task :app:generateDebugAndroidTestResValues UP-TO-DATE
> Task :app:mapDebugAndroidTestSourceSetPaths UP-TO-DATE
> Task :app:generateDebugAndroidTestResources UP-TO-DATE
> Task :app:mergeDebugAndroidTestResources UP-TO-DATE
> Task :app:processDebugAndroidTestResources UP-TO-DATE
> Task :app:compileDebugAndroidTestKotlin UP-TO-DATE
> Task :app:javaPreCompileDebugAndroidTest UP-TO-DATE
> Task :app:compileDebugAndroidTestJavaWithJavac UP-TO-DATE
> Task :app:mergeDebugShaders UP-TO-DATE
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :core-view:configureCMakeDebug[x86_64]
> Task :core-view:buildCMakeDebug[x86_64]
> Task :core-view:mergeDebugJniLibFolders UP-TO-DATE
> Task :core-view:mergeDebugNativeLibs UP-TO-DATE
> Task :core-view:copyDebugJniLibsProjectOnly UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :steel:configureCMakeDebug[x86_64]
> Task :app:compressDebugAssets UP-TO-DATE
> Task :app:desugarDebugFileDependencies UP-TO-DATE
> Task :app:dexBuilderDebug UP-TO-DATE
> Task :steel:buildCMakeDebug[x86_64]
> Task :steel:mergeDebugJniLibFolders UP-TO-DATE
> Task :steel:mergeDebugNativeLibs UP-TO-DATE
> Task :steel:copyDebugJniLibsProjectOnly UP-TO-DATE

```
