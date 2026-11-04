plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Prayer-time alerts shared by the phone and watch apps: scheduling, the Adhan player service, notifications,
// the sound library and imported sounds. Each app supplies its own sound files (assets/sounds) and settings.
android {
    namespace = "com.noor.alerts"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all { t -> providers.gradleProperty("robolectric.repo").orNull?.let { t.systemProperty("robolectric.dependency.repo.url", it) } }
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:prayer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
