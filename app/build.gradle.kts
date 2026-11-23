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
        versionCode = 8
        versionName = "0.6.0-s6"
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
}

// R10 is loaded in unit tests.
tasks.withType<Test>().configureEach { maxHeapSize = "2g" }

// S6: R10 regenerated with the real Manifold booleans on this machine (same host library as :core-view).
//   ./gradlew :app:testDebugUnitTest -PhostJni
if (providers.gradleProperty("hostJni").isPresent) tasks.withType<Test>().configureEach {
    dependsOn(":core-view:buildHostJni")
    systemProperty("java.library.path", project(":core-view").layout.buildDirectory.dir("host-jni").get().asFile.absolutePath)
    systemProperty("sscore.hostJni", "1")
    maxHeapSize = "4g"
}
