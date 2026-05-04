import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.nomadclub.cashchat"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nomadclub.cashchat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
        buildConfigField("String", "BASE_URL", "\"${localProperties.getProperty("BASE_URL", "https://cashchat.duckdns.org/")}\"")

        buildConfigField("String", "ADMOB_APP_ID", "\"${localProperties.getProperty("ADMOB_APP_ID", "")}\"")
        manifestPlaceholders["admobAppId"] = localProperties.getProperty("ADMOB_APP_ID", "")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_BANNER_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_INTERSTITIAL_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_NATIVE_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_REWARDED_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${localProperties.getProperty("SENTRY_DSN", "")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.datastore.preferences)

    // Google Sign-In
    implementation(libs.google.play.services.auth)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // AdMob
    implementation(libs.play.services.ads)

    // Encrypted SharedPreferences
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
