plugins {
    id("com.android.application")
}

android {
    namespace = "com.enmar.kioskbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enmar.kioskbrowser"
        minSdk = 14
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
