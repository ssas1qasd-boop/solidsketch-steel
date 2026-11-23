plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    // applicationId: temporary, owner decision pending (CLAUDE.md "Open items" 1).
    namespace = "com.solidsketchapp.steel"
    compileSdk = 35
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.solidsketchapp.steel"
        minSdk = 24
        targetSdk = 35
        versionCode = 14
        versionName = "0.9.3-s9b"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-view"))
    implementation(project(":core-io"))
    implementation(project(":steel"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)   // S6 golden JSON in tests
    // S9b: app screenshots without a device (Robolectric, MIT; Roborazzi, Apache-2.0; test only, never in the APK)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// S9b: with -PlocalM2=<dir>, Robolectric runs offline from <dir>/robolectric-deps (Maven Central answered 429 in the cloud session).
providers.gradleProperty("localM2").orNull?.let { dir -> tasks.withType<Test>().configureEach {
    systemProperty("robolectric.offline", "true"); systemProperty("robolectric.dependency.dir", "$dir/robolectric-deps") } }

// R10 is loaded in unit tests. S9b: Roborazzi writes the app screenshots (tests/steel/ui/app) on every run.
tasks.withType<Test>().configureEach { maxHeapSize = "2g"; systemProperty("roborazzi.test.record", "true") }

// S6: R10 regenerated with the real Manifold booleans on this machine (same host library as :core-view).
//   ./gradlew :app:testDebugUnitTest -PhostJni
if (providers.gradleProperty("hostJni").isPresent) tasks.withType<Test>().configureEach {
    dependsOn(":core-view:buildHostJni")
    systemProperty("java.library.path", project(":core-view").layout.buildDirectory.dir("host-jni").get().asFile.absolutePath)
    systemProperty("sscore.hostJni", "1")
    maxHeapSize = "4g"
}

// S9b: the screenshots and the grid-label test need the Compose test activity from ui-test-manifest, which only the debug build has.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { (this as Test).filter { excludeTestsMatching("*UiScreensTest*"); excludeTestsMatching("*GridLabelsFollowViewTest*"); excludeTestsMatching("*UiGoldenTest*") } }

// UI golden (owner, 2026-11-23): no APK without the UI check against web v69. Every assemble / bundle of the app first runs the
// debug unit tests, which include UiGoldenTest (labels, handles and cones within 2 px of v69 in every camera state; button and
// sheet boxes within 4 px; every control above the navigation bar), GridLabelsFollowViewTest, UiRulesTest and UiScreensTest.
// v69's side is recorded by tools/golden/ui_golden.js into tests/steel/ui/golden/ui_golden.json (see docs/steel/UI-GOLDEN.md).
// -PskipUiGolden builds without it (never for an APK that goes to the owner).
if (!providers.gradleProperty("skipUiGolden").isPresent) tasks.matching { it.name.matches(Regex("(assemble|bundle)(Debug|Release)")) }.configureEach { dependsOn("testDebugUnitTest") }
// the UI golden reads v69's numbers from the repo: a new recording must rerun the tests (not "up to date")
tasks.matching { it.name == "testDebugUnitTest" }.configureEach { inputs.dir(rootProject.file("tests/steel/ui/golden")).withPropertyName("uiGolden").optional() }
