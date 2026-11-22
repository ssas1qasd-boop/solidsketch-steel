plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Manifold (Apache-2.0) source for native/sscore-lite, pinned here and in
// tools/native/fetch_manifold.sh. Cloned once into third_party/manifold (git-ignored).
val manifoldTag = "v3.5.4"
val manifoldCommit = "ce50d78021d64507f89e8c9fc2c2e51018117857"
tasks.register("fetchManifold") {
    description = "Clones Manifold $manifoldTag into third_party/manifold"
    val dir = layout.projectDirectory.dir("third_party/manifold").asFile
    outputs.upToDateWhen { false }
    doLast {
        fun head(): String? = runCatching {
            val p = ProcessBuilder("git", "-C", dir.path, "rev-parse", "HEAD").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().readText().trim().also { p.waitFor() }
        }.getOrNull()
        if (File(dir, ".git").exists() && head() == manifoldCommit) return@doLast
        dir.deleteRecursively()
        dir.parentFile.mkdirs()
        var ok = false
        for (attempt in 1..4) {
            val p = ProcessBuilder("git", "-c", "advice.detachedHead=false", "clone", "-q", "--depth", "1", "--branch", manifoldTag,
                "https://github.com/elalish/manifold.git", dir.path).inheritIO().start()
            if (p.waitFor() == 0) { ok = true; break }
            dir.deleteRecursively(); Thread.sleep(2000L * attempt)
        }
        check(ok) { "Could not clone Manifold $manifoldTag" }
        check(head() == manifoldCommit) { "Manifold $manifoldTag is ${head()}, expected $manifoldCommit" }
    }
}
