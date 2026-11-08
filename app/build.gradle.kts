import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Our own signing key for updates; it sits next to the project (don't lose it: without this key an
// installed version can no longer be updated).
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "nl.iptvplayer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.iptvplayer.app"
        minSdk = 26
        targetSdk = 35
        // Every build gets a higher number (minutes since 2026), so a new version always installs over the old one.
        versionCode = ((System.currentTimeMillis() / 60_000) - 29_455_000).toInt()
        // The Publish workflow passes the release number (-PappVersionName=1.4); local builds are "dev (date)".
        versionName = (findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() }
            ?: ("dev (" + SimpleDateFormat("d-M-yyyy HH:mm").format(Date()) + ")")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // ./gradlew assembleRelease -PcheckRelease: install the release build next to the test app to check it.
            if (project.hasProperty("checkRelease")) applicationIdSuffix = ".check"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        // Robolectric: the app resources (theme, fonts) in the unit tests too.
        unitTests.isIncludeAndroidResources = true
        // ScrollSimulationTest: -Pframes=<dir> saves every frame as an image.
        unitTests.all { it.systemProperty("scrollFrames", project.findProperty("frames")?.toString() ?: "") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    testImplementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // QR code on the activation screen.
    implementation("com.google.zxing:core:3.5.3")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    testImplementation("junit:junit:4.13.2")
    // Simulate scrolling without a TV: Compose UI in Robolectric, with the remote control as key presses.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
