plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // SQLDelight 플러그인은 Epic E-4(대화 기록 저장) 구현 시 활성화
    // alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(21)

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "CashChatShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 기존
            implementation(libs.kotlinx.coroutines.core)

            // Koin KMP 공통 DI
            implementation(libs.koin.core)

            // Ktor 네트워크 클라이언트 (KMP 공통)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // 직렬화
            implementation(libs.kotlinx.serialization.json)

            // SQLDelight 런타임 (스키마 생성은 Epic E-4에서)
            implementation(libs.sqldelight.runtime)

            // Sentry KMP 에러 추적
            implementation(libs.sentry.kmp)
        }

        androidMain.dependencies {
            // Ktor Android 엔진 (OkHttp 기반)
            implementation(libs.ktor.client.okhttp)
            // SQLDelight Android 드라이버
            implementation(libs.sqldelight.android.driver)
        }

        iosMain.dependencies {
            // Ktor iOS 엔진 (Darwin 기반)
            implementation(libs.ktor.client.darwin)
            // SQLDelight iOS Native 드라이버
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.nomadclub.cashchat.shared"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
