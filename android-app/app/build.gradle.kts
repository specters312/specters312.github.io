plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.specters312.intentlab"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.specters312.intentlab"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
