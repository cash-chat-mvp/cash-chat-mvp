import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
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
        // CI/CD 환경에서 VERSION_CODE/VERSION_NAME 환경변수로 주입 (android-build-distribute.yml)
        // 로컬 빌드 시에는 기본값 사용
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
        // Retrofit은 baseUrl에 끝 슬래시를 요구하므로 누락 시 자동 보정
        val rawBaseUrl = localProperties.getProperty("BASE_URL", "https://cashchat.duckdns.org/")
        val baseUrl = if (rawBaseUrl.endsWith("/")) rawBaseUrl else "$rawBaseUrl/"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")

        buildConfigField("String", "ADMOB_APP_ID", "\"${localProperties.getProperty("ADMOB_APP_ID", "")}\"")
        manifestPlaceholders["admobAppId"] = localProperties.getProperty("ADMOB_APP_ID", "")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_BANNER_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_INTERSTITIAL_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_NATIVE_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"${localProperties.getProperty("ADMOB_REWARDED_AD_UNIT_ID", "")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${localProperties.getProperty("SENTRY_DSN", "")}\"")
        buildConfigField("String", "TNK_APP_ID", "\"${localProperties.getProperty("TNK_APP_ID", "")}\"")
        manifestPlaceholders["tnkAppId"] = localProperties.getProperty("TNK_APP_ID", "")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
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

    // KMM Shared 모듈 (공통 모델/비즈니스 로직)
    implementation(project(":shared"))

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // AdMob
    implementation(libs.play.services.ads)

    // Firebase (Remote Config + Analytics) — BoM으로 버전 일괄 관리
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)

    // TNK Offerwall
    implementation("com.tnkfactory:rwd:8.09.07")

    // Encrypted SharedPreferences
    implementation(libs.security.crypto)

    // 채팅 마크다운 렌더링 (Material3 테마 연동)
    implementation(libs.markdown.renderer.m3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
