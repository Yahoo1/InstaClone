plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Use ALIAS here so it pulls the version from your TOML file
    alias(libs.plugins.google.services)

    // If you have KSP in your TOML, use alias for that too
    id("com.google.devtools.ksp") // or alias(libs.plugins.ksp) if defined
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.instaclone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.instaclone"
        minSdk = 24
        targetSdk = 35
        versionCode = 2 // Increment by 1 every time (1, 2, 3...)
        versionName = "1.1" // Your new version string

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("io.appwrite:sdk-for-android:12.0.0")

    // 1. Import the Firebase BOM (this manages versions)
//    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Add this for ViewModel, ViewModelProvider, and collectAsState
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation for Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Material icons (for the heart, home, etc.)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // 1. The actual Hilt library
    implementation("com.google.dagger:hilt-android:2.51.1")

    // 2. The code generator (KSP)
    ksp("com.google.dagger:hilt-compiler:2.51.1")

    // 3. The Compose integration
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

}