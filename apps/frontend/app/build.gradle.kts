import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Google services Gradle plugin
    id("com.google.gms.google-services")
}

// key.properties (릴리즈 서명)
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

// local.properties (AdMob ID, Sentry DSN 등 민감 키 관리)
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

/** local.properties → 환경변수 → fallback 순서로 설정값 조회 */
fun getConfigValue(key: String, fallback: String = ""): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: fallback

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
        // CI: VERSION_CODE=github.run_number, VERSION_NAME=브랜치에서 추출 (release-fe/1.2.0 → 1.2.0)
        // 로컬: 환경변수 없으면 fallback 값 사용
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // dev / prod 환경 분리
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"

            // dev 환경: Google 공식 테스트 광고 ID 사용 (실제 수익 발생 안 함)
            val appId = getConfigValue("DEV_ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
            manifestPlaceholders["admobAppId"] = appId
            buildConfigField("String", "ADMOB_APP_ID", "\"$appId\"")
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID",
                "\"${getConfigValue("DEV_ADMOB_BANNER_AD_UNIT_ID", "ca-app-pub-3940256099942544/6300978111")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "\"${getConfigValue("DEV_ADMOB_INTERSTITIAL_AD_UNIT_ID", "ca-app-pub-3940256099942544/1033173712")}\"")
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID",
                "\"${getConfigValue("DEV_ADMOB_NATIVE_AD_UNIT_ID", "ca-app-pub-3940256099942544/2247696110")}\"")
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID",
                "\"${getConfigValue("DEV_ADMOB_REWARDED_AD_UNIT_ID", "ca-app-pub-3940256099942544/5224354917")}\"")
            buildConfigField("String", "SENTRY_DSN",
                "\"${getConfigValue("DEV_SENTRY_DSN")}\"")
        }
        create("prod") {
            dimension = "env"

            // prod 환경: local.properties 또는 CI 환경변수에서 주입 (하드코딩 금지)
            val appId = getConfigValue("ADMOB_APP_ID")
            manifestPlaceholders["admobAppId"] = appId
            buildConfigField("String", "ADMOB_APP_ID", "\"$appId\"")
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID",
                "\"${getConfigValue("ADMOB_BANNER_AD_UNIT_ID")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "\"${getConfigValue("ADMOB_INTERSTITIAL_AD_UNIT_ID")}\"")
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID",
                "\"${getConfigValue("ADMOB_NATIVE_AD_UNIT_ID")}\"")
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID",
                "\"${getConfigValue("ADMOB_REWARDED_AD_UNIT_ID")}\"")
            buildConfigField("String", "SENTRY_DSN",
                "\"${getConfigValue("SENTRY_DSN")}\"")
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = (keystoreProperties["keyAlias"] as String?) ?: System.getenv("KEYSTORE_KEY_ALIAS")
            keyPassword = (keystoreProperties["keyPassword"] as String?) ?: System.getenv("KEYSTORE_KEY_PASSWORD")
            storeFile = (keystoreProperties["storeFile"] as String?)?.let { file(it) }
                ?: System.getenv("KEYSTORE_PATH")?.let { file(it) }
            storePassword = (keystoreProperties["storePassword"] as String?) ?: System.getenv("KEYSTORE_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))

    // AndroidX / Compose
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

    // Firebase (BOM으로 버전 관리)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)

    // AdMob
    implementation(libs.play.services.ads)

    // Koin (Android + Compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
