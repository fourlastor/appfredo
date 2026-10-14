plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.appfredo"
    compileSdk = 37
    // The release scripts verify with the same version. AGP 9.4 would otherwise use 36.0.0.
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "app.appfredo"
        minSdk = 26
        targetSdk = 36
        // Releases pass both from their git tag (scripts/release.sh); local builds stay at 0.1.0 (1).
        versionCode = providers.gradleProperty("appfredo.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("appfredo.versionName").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Release signing only happens when a key is supplied, as the release workflow does. Every release must be
    // signed with the same key: Android refuses updates otherwise, and patches are built from earlier releases.
    val releaseKeystore = providers.environmentVariable("APPFREDO_KEYSTORE").orNull
    if (releaseKeystore != null) {
        signingConfigs.create("release") {
            val password = providers.environmentVariable("APPFREDO_KEYSTORE_PASSWORD").get()
            storeFile = file(releaseKeystore)
            storePassword = password
            keyAlias = "appfredo"
            // PKCS12 keystores, the only kind keytool makes today, use one password for the store and the key.
            keyPassword = password
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    buildTypes.getByName("release") {
        // Appfredo's own code is what every update re-sends, so it is shrunk. The Wharf AAR brings its own keep
        // rules for the classes its native library calls by name.
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs.useLegacyPackaging = false }
    sourceSets.getByName("androidTest").assets.srcDir("../native/wharf/testdata")
    sourceSets.getByName("androidTest").assets.srcDir("../build/fixtures/assets")
    lint { abortOnError = true }
    // Lets JVM tests hold an android.content.Intent, which the screen logic only ever checks for presence.
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(files("libs/wharf.aar"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
