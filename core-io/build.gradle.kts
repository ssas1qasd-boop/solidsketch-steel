plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

}

android {
    namespace = "com.solidsketchapp.core.io"
    compileSdk = 35


    defaultConfig {
        minSdk = 24

    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core-api"))
    testImplementation(libs.junit)
}

// R10 (62 MB of JSON, 1.8 M triangles) is loaded in unit tests.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    // SsInteropTest reads the file web v69 saved (tools/golden/ss_interop.js): rerun when it changes.
    inputs.files(fileTree(layout.buildDirectory.dir("ss-interop/web"))).withPropertyName("webSavedFiles").optional()
}
