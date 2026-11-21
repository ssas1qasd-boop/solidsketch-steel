plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

}

android {
    namespace = "com.solidsketchapp.core.view"
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
