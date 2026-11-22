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

// Native builds need the Manifold source (see :fetchManifold in the root build).
tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }.configureEach {
    dependsOn(":fetchManifold")
}

// Optional: build the JNI library for this machine and let JVM unit tests call it.
//   ./gradlew :core-view:testDebugUnitTest -PhostJni
val hostJni = providers.gradleProperty("hostJni").isPresent
val hostJniDir = layout.buildDirectory.dir("host-jni").get().asFile
val buildHostJni = tasks.register<Exec>("buildHostJni") {
    dependsOn(":fetchManifold")
    val src = file("src/main/cpp")
    commandLine("bash", "-c", "cmake -S '$src' -B '$hostJniDir' -G Ninja -DCMAKE_BUILD_TYPE=Release > '$hostJniDir.log' 2>&1 && cmake --build '$hostJniDir' >> '$hostJniDir.log' 2>&1 || { tail -40 '$hostJniDir.log'; exit 1; }")
}
tasks.withType<Test>().configureEach {
    if (hostJni) {
        dependsOn(buildHostJni)
        systemProperty("java.library.path", hostJniDir.absolutePath)
        systemProperty("sscore.hostJni", "1")
    }
}
