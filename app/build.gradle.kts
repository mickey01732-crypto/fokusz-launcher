plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "hu.miki.fokusz"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.miki.fokusz"
        minSdk = 31
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("fokusz") {
            storeFile = file("fokusz.keystore")
            storePassword = System.getenv("KS_PASS") ?: ""
            keyAlias = "fokusz"
            keyPassword = System.getenv("KS_PASS") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fokusz")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
