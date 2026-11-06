plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.noor.wear"
    compileSdk = 36

    defaultConfig {
        // Must equal the phone app's id: the Wear OS Data Layer only connects apps with the same id and signature.
        applicationId = "com.noor.smartwatch"
        minSdk = 33 // Wear OS 4+ (Galaxy Watch 4 and later are on Wear OS 5 or 6)
        targetSdk = 35
        versionCode = 13
        versionName = "0.9.4"
    }

    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("signing/noor-dev.keystore")
            storePassword = "noor-dev-key"
            keyAlias = "noor-dev"
            keyPassword = "noor-dev-key"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("dev") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("dev")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { generateLocaleConfig = true }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/watchface/assets"))
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/watchface/res"))
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all { t ->
                providers.gradleProperty("robolectric.repo").orNull?.let { t.systemProperty("robolectric.dependency.repo.url", it) }
                // Each test class in its own JVM: DataStore keeps one instance per file for the whole process.
                t.forkEvery = 1
            }
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:prayer"))
    implementation(project(":alerts"))
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)
    implementation(libs.play.services.location)
    implementation(libs.wear.remote.interactions)
    implementation(libs.wear.ongoing)
    implementation(libs.wear.input)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.wear.complications.source)
    implementation(libs.wear.watchfacepush)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.wear.tiles.renderer) // only to render tile previews in tests
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// ---- Noor watch face, carried by this app and installed with Watch Face Push (Wear OS 6) ----
val faceValidator: Configuration by configurations.creating
dependencies { faceValidator("com.google.android.wearable.watchface.validator:validator-push-cli:1.1.0-alpha01") }

val buildPushedFace by tasks.registering(Exec::class) {
    description = "Packages the Noor watch face for Watch Face Push and validates it with Google's validator."
    val out = layout.buildDirectory.dir("generated/watchface")
    inputs.dir(rootProject.file("legacy/watchface-v1.3.2/NoorWatchFace/watchface/src/main/res"))
    inputs.file(rootProject.file("tools/watchface/build_pushed_face.py"))
    inputs.file(rootProject.file("signing/noor-watchface-dev.keystore"))
    inputs.files(faceValidator)
    outputs.dir(out)
    val sdk = android.sdkDirectory.absolutePath
    val script = rootProject.file("tools/watchface/build_pushed_face.py").absolutePath
    doFirst { out.get().asFile.deleteRecursively() }
    commandLine("python3", script, sdk, out.get().asFile.absolutePath, faceValidator.singleFile.absolutePath)
}
tasks.named("preBuild") { dependsOn(buildPushedFace) }
