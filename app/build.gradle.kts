import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Eigen sleutel voor updates; staat naast het project (niet kwijtraken: zonder deze sleutel kan
// een geïnstalleerde versie niet meer bijgewerkt worden).
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
        // Elke build een hoger nummer (minuten sinds 2026), zodat een nieuwe versie altijd over de oude heen installeert.
        versionCode = ((System.currentTimeMillis() / 60_000) - 29_455_000).toInt()
        versionName = "1.0 (" + SimpleDateFormat("d-M-yyyy HH:mm").format(Date()) + ")"
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
            // ./gradlew assembleRelease -PcheckRelease: release-build naast de test-app installeren om te controleren.
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
        // Robolectric: de app-resources (thema, lettertypes) ook in de unit tests.
        unitTests.isIncludeAndroidResources = true
        // ScrollSimulationTest: -Pframes=<map> bewaart elk frame als afbeelding.
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

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    testImplementation("junit:junit:4.13.2")
    // Scrollgedrag nabootsen zonder tv: Compose-UI in Robolectric, met de afstandsbediening als toetsen.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
