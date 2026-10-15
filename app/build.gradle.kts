plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.edtatevosiani99.internetspeedtest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edtatevosiani99.internetspeedtest"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
