plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.liftingapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.CatPack.liftingapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "0.12.0"
    }

    // One debug key for everyone on the project (keys/catpack-debug.keystore), so Google sign-in works
    // on every developer's machine with a single SHA-1 registered in Firebase. Debug builds only;
    // the Play Store build is signed with the separate upload key.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("../keys/catpack-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // Google sign-in (Credential Manager)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")   // Workouts + Leaderboard lists

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Chart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ViewModel + LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")   // lifecycleScope

    // Tests (StrengthTest, LeaderboardTest)
    testImplementation("junit:junit:4.13.2")
}
