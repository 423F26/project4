plugins {
    id 'kotlin-kapt'   // add alongside your existing plugins
}

android {
    buildFeatures {
        viewBinding true
    }
}

dependencies {
    // Room
    implementation "androidx.room:room-runtime:2.8.4"
    implementation "androidx.room:room-ktx:2.8.4"
    kapt "androidx.room:room-compiler:2.8.4"

    // Chart
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // ViewModel + LiveData
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.9.4"
}