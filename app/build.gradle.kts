plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.asterion.writing.preview"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.asterion.writing.bench"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-autonomous-preview"
        ndk { abiFilters += "arm64-v8a" }
    }
    // The November v0.4 benchmark uses a lost CI-generated debug signing key.
    // Preserve its package and private model files; build the autonomous preview
    // as a separate, non-destructive application until a verified export exists.
    flavorDimensions += "edition"
    productFlavors {
        create("bench") {
            dimension = "edition"
            applicationId = "com.asterion.writing.bench"
            resValue("string", "writer_app_label", "Asterion Writer Studio")
        }
        create("autonomous") {
            dimension = "edition"
            applicationId = "com.asterion.writing.autonomous"
            versionCode = 1
            versionName = "0.5.1-autonomous"
            resValue("string", "writer_app_label", "Asterion Writer Autonomous")
        }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
        getByName("debug") { isDebuggable = true }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").java.srcDir("../core/src/main/kotlin")
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation(project(":brain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
