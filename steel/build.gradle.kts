plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.solidsketchapp.steel.mode"
    compileSdk = 35
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 24
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Rule (MERGE.md): :steel depends on :core-api only, never on :core-view or :core-io.
    api(project(":core-api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // S4 data assets: only the JSON tree API (no serialization plugin), so it also runs in JVM tests.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// Native builds need the Manifold source (see :fetchManifold in the root build).
tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }.configureEach {
    dependsOn(":fetchManifold")
}
