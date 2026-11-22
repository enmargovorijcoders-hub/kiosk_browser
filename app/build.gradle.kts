plugins {
    id("com.android.application")
}

android {
    namespace = "com.enmar.kioskbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enmar.kioskbrowser"
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
}
