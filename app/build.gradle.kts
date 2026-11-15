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
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
        getByName("debug") {
            isDebuggable = true
            // CI creates an unsigned test APK; Windows applies persistent private signing.
            signingConfig = null
        }
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
