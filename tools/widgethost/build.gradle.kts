plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// A widget host for testing only (tools/devicetest/widgets.sh, .github/workflows/widgets.yml): a separate, tiny app that
// shows Minhad's home-screen widgets as a launcher does. It is its own package on purpose: Android inflates a widget in
// its own restricted context only when the host is another app (RemoteViews: a host of the same package gets the app's
// own, unrestricted context, where fonts and other resources a launcher ignores would load). Never given to users and
// never part of the phone app.
android {
    namespace = "com.noor.widgethost"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noor.widgethost"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }
