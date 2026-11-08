plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

// Build output goes to the temp directory (/tmp/IPTVPlayer-build on Linux), outside the project folder.
val localBuildRoot = File(System.getProperty("java.io.tmpdir"), "IPTVPlayer-build")
allprojects {
    layout.buildDirectory.set(File(localBuildRoot, project.name))
}
