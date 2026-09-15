plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.darn.spatiallauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.darn.spatiallauncher"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { buildConfig = false }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true; checkReleaseBuilds = true }
}

dependencies { testImplementation("junit:junit:4.13.2") }
