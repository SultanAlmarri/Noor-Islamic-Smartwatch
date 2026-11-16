plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Minhad Studio (0.18): the face renderer both apps share, so the phone's editor shows exactly what the watch draws.
// Plain android.graphics (no Compose), so the watch's face service draws with it directly.
android {
    namespace = "com.noor.face"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:prayer"))
}
