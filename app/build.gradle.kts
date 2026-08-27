plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

fun String.toBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.example.comiku"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.comiku"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val firebaseApiKey = providers.gradleProperty("FIREBASE_API_KEY").orNull ?: ""
        val firebaseAppId = providers.gradleProperty("FIREBASE_APP_ID").orNull ?: ""
        val firebaseAuthDomain = providers.gradleProperty("FIREBASE_AUTH_DOMAIN").orNull ?: ""
        val firebaseMessagingSenderId =
            providers.gradleProperty("FIREBASE_MESSAGING_SENDER_ID").orNull ?: ""
        val firebaseProjectId = providers.gradleProperty("FIREBASE_PROJECT_ID").orNull ?: ""
        val firebaseStorageBucket = providers.gradleProperty("FIREBASE_STORAGE_BUCKET").orNull ?: ""
        val streamApiKey = providers.gradleProperty("STREAM_API_KEY").orNull ?: ""
        val backendUrl = providers.gradleProperty("BACKEND_URL").orNull ?: "http://10.0.2.2:3000"

        buildConfigField("String", "FIREBASE_API_KEY", firebaseApiKey.toBuildConfigString())
        buildConfigField("String", "FIREBASE_APP_ID", firebaseAppId.toBuildConfigString())
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", firebaseAuthDomain.toBuildConfigString())
        buildConfigField(
            "String",
            "FIREBASE_MESSAGING_SENDER_ID",
            firebaseMessagingSenderId.toBuildConfigString()
        )
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseProjectId.toBuildConfigString())
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", firebaseStorageBucket.toBuildConfigString())
        buildConfigField("String", "STREAM_API_KEY", streamApiKey.toBuildConfigString())
        buildConfigField("String", "BACKEND_URL", backendUrl.toBuildConfigString())
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        disable.addAll(listOf("UnsafeOptInUsageError", "MissingTranslation"))
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.guava:guava:33.2.1-android")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    
    // Stream Chat
    implementation("io.getstream:stream-chat-android-client:6.11.0")
    implementation("io.getstream:stream-chat-android-ui-components:6.11.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    
    // Retrofit para API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata:2.6.2")
    
    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}