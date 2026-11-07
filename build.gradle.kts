plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

// Het project staat in Google Drive; build-uitvoer houden we lokaal zodat Drive niet duizenden
// tijdelijke bestanden gaat synchroniseren.
val localBuildRoot = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "IPTVPlayer-build")
allprojects {
    layout.buildDirectory.set(File(localBuildRoot, project.name))
}
