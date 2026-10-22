import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing: read from a properties file OUTSIDE the repo (never committed). First found wins:
 *   1. env MASARIFI_SIGNING_PROPERTIES (path)   2. ~/.gradle/masarifi-signing.properties
 *   3. ../../masarifi-keystore/signing.properties (next to the project folder)
 * Keys: storeFile, storePassword, keyAlias, keyPassword. Absent → release builds are left unsigned (CI / other machines).
 */
val signingProps: Properties? = listOfNotNull(
    System.getenv("MASARIFI_SIGNING_PROPERTIES")?.let { file(it) },
    file("${System.getProperty("user.home")}/.gradle/masarifi-signing.properties"),
    rootProject.file("../masarifi-keystore/signing.properties"),
).firstOrNull { it.isFile }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    ?.takeIf { p -> listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !p.getProperty(it).isNullOrBlank() } && file(p.getProperty("storeFile")).isFile }

android {
    namespace = "com.midroid.masarifi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.midroid.masarifi"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "2.1.2"
    }

    signingConfigs {
        if (signingProps != null) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            if (signingProps == null) logger.warn("Masarifi: no signing.properties found — release APK will be UNSIGNED")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1536m"
            // AppDatabase is a process-wide singleton: a fresh JVM per test class keeps Robolectric UI tests independent
            it.forkEvery = 1
            it.maxParallelForks = 4
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Exported Room schemas (schemas/<db>/<version>.json) are committed and used by the migration tests.
    arg("room.schemaLocation", "$projectDir/schemas")
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
}
