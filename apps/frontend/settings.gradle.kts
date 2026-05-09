pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cashchat"
include(":app")

// TODO(iOS 연동 시점): :shared 모듈 포함 활성화
//   KMM shared 모듈은 iOS 연동 전까지 의도적으로 빌드에서 제외.
//   iOS 연동 시점에 아래 줄을 추가하고 다음 작업을 함께 진행:
//
//   include(":shared")
//
//   함께 처리할 항목:
//   1. app/build.gradle.kts dependencies 블록에 implementation(project(":shared")) 추가
//   2. app/data/model/AuthResponse.kt 삭제 → shared/auth/model/AuthResponse.kt로 통합
//   3. ApiService, AuthRepository 등의 import를 shared 모델로 교체
//   4. Xcode: Build Phases > Embed Shared Framework 스크립트 확인
//      (gradlew :shared:embedAndSignAppleFrameworkForXcode, JAVA_HOME=JDK21)
 