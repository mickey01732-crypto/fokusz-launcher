plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksPass = System.getenv("KS_PASS")?.takeIf { it.isNotBlank() } ?: "j1W_Bi51OFx9gPFU6Ig-TsJi"
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
            storePassword = ksPass
            keyAlias = "fokusz"
            keyPassword = ksPass
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
