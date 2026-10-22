// A do-nothing app with a finance-sounding name, installed over adb to try the
// new-app helper end to end (see AGENTS.md). Never shipped.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tidy.testpay"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tidy.testpay"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1"
    }
}
