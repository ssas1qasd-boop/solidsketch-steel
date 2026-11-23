pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Optional local copy of Robolectric's big jars when Maven Central answers 429 (cloud session): -PlocalM2=/path/to/m2
        providers.gradleProperty("localM2").orNull?.let { dir -> maven { url = uri(dir); content { includeGroup("org.robolectric") } } }
        google()
        mavenCentral()
    }
}

rootProject.name = "solidsketch-steel"
include(":app", ":core-api", ":core-view", ":core-io", ":steel")
