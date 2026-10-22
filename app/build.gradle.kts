import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is optional at the Gradle-config level: CI and machines
// without keystore/tidy-upload.properties (gitignored -- never commit a
// signing key) can still run assembleDebug/lintDebug; only assembleRelease
// needs it.
val keystorePropertiesFile = rootProject.file("keystore/tidy-upload.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "app.tidy.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tidy.android"
        minSdk = 29
        targetSdk = 35
        // Bump both for every release: versionName becomes the GitHub
        // release tag (v<versionName>) the in-app updater compares against,
        // and Android refuses to install a lower versionCode over a higher one.
        versionCode = 1
        versionName = "0.1.0"

        // GitHub "owner/repo" whose latest release the in-app updater
        // offers. Forks signing with their own key must change (or blank,
        // to disable) this -- the installer rejects APKs signed by a
        // different key anyway.
        buildConfigField("String", "UPDATE_REPO", "\"seolcu/tidy\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // values/ is English (also the fallback for unsupported languages),
    // values-ko/ Korean; this exposes both in Android 13+'s per-app
    // language setting.
    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so there is no
// kotlin-android plugin; the Kotlin version comes from the Compose compiler
// plugin (`kotlin` in gradle/libs.versions.toml).
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}
