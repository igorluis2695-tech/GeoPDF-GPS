plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.igor.geopdfgps"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.igor.geopdfgps"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.9.0"
    }

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}
}
dependencies { 
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
