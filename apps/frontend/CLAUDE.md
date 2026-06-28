# CLAUDE.md

Claude Code가 이 저장소(프론트엔드)에서 작업할 때 참고하는 가이드 문서입니다.

---

## 빌드 명령어

```bash
# Android 빌드
./gradlew :app:assembleDebug          # Debug APK
./gradlew :app:assembleRelease        # Release APK (key.properties 또는 KEYSTORE_* 환경변수 필요)
./gradlew :shared:assembleDebug       # KMM shared 모듈만
./gradlew clean build                 # 전체 빌드 + 테스트

# iOS shared framework (Xcode 열기 전에 실행)
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# 테스트
./gradlew :app:test                   # 유닛 테스트
./gradlew :app:connectedAndroidTest   # 계측 테스트 (디바이스/에뮬레이터 필요)
```

릴리즈 서명: `app/` 디렉토리에 `key.properties` 파일 배치 (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) 또는 `KEYSTORE_*` 환경변수 설정.

---

## 아키텍처

### 모듈 구조

Gradle 모듈 2개: `:app` (Android 전용 UI), `:shared` (KMM 크로스플랫폼 로직).
iOS는 `CashChatShared.framework` (static) 를 통해 `:shared` 코드를 사용.

```
app/src/main/java/com/nomadclub/cashchat/
├── MainActivity.kt          # 루트 NavHost + AppRoute, AuthState 기반 startDestination
├── CashChatApplication.kt   # startKoin(appModule + sharedDataModule), MobileAds/Firebase 초기화
├── di/AppModule.kt          # 앱 레벨 Koin 모듈
├── config/                  # AppConfig, AnalyticsManager(Firebase Analytics),
│                            #   RemoteConfigManager + RemoteConfigKeys, AppGateState
├── ads/                     # BannerAd, ChatNativeAdView, NativeAdManager, RewardedAdManager
├── core/                    # network(ApiService, AuthInterceptor, TokenAuthenticator,
│                            #   TokenRefreshGate, DataStoreTokenProvider), storage, data, util
├── offerwall/               # 오퍼월 연동
├── feature/                 # 화면별 패키지 + ViewModel
│   ├── auth/                # LoginScreen + LoginViewModel
│   ├── chat/                # ChatScreen + ConversationListScreen + ChatViewModel
│   ├── gate/                # AppGateScreen (Remote Config 강제 업데이트 게이트)
│   ├── main/                # MainScreen(하단 네비) + MainTab enum
│   ├── rewards/             # 리워드/혜택존
│   ├── shop/                # ShopScreen
│   ├── settings/            # SettingsScreen
│   ├── mypage/              # MyPageScreen
│   └── onboarding/          # OnboardingScreen + OnboardingViewModel
└── ui/theme/                # Material3 테마 — Color.kt, Type.kt, Theme.kt

shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/
├── di/SharedModule.kt       # sharedDataModule(baseUrl) — Ktor 클라이언트/Store/Repository DI
├── core/                    # network(HttpClientFactory, SseParser, ApiException, TokenProvider), config
├── chat/                    # ChatApi(SSE streamMessage) + ChatStore + model
├── localllm/                # 온디바이스 Gemma 로컬 채팅 (서버↔로컬 전환, 모델 다운로드/검증)
├── auth/                    # LoginStore
├── points/, wallet/         # 포인트 잔액(PointsStore, PointsApi)
├── ads/                     # 광고 정책/모델 공통 로직
├── attendance/, energy/, evolution/, roulette/, invite/, offerwall/  # 리텐션/게이미피케이션
├── shop/, session/, hud/    # 상점 / 세션 리셋 / HUD 상태
└── platform/TimeProvider.kt # expect/actual — currentTimeMillis()
```

### 상태 및 데이터 흐름

패턴: MVVM + `StateFlow`. 화면 ViewModel이 shared `*Store`를 감싸고, Store는 `*Api`(Ktor)
또는 `*Repository`(Fake/Real 교체 가능, 예: `roulette`, `invite`)를 통해 데이터를 받습니다.

```
Composable → ViewModel (collectAsStateWithLifecycle) → Store (StateFlow) → Api/Repository → 백엔드
```

DI는 **Koin** 사용. `CashChatApplication`에서 `startKoin { modules(appModule, sharedDataModule(BuildConfig.BASE_URL)) }`로 부트스트랩.
Store/Repository/HttpClient는 Koin 모듈에서 주입 (직접 인스턴스화 지양).

### 네트워킹 / 인증

- `:shared`의 `core/network`가 KMP 공통 Ktor 클라이언트(`HttpClientFactory`), SSE 파서(`SseParser`),
  `ApiException`, `TokenProvider` 인터페이스 제공.
- `:app`의 `core/network`가 Android 토큰 갱신을 담당: `AuthInterceptor` + `TokenAuthenticator` +
  `TokenRefreshGate`(동시 갱신 single-flight) + `DataStoreTokenProvider`(DataStore Preferences에 토큰 저장).
- 채팅 응답은 `ChatApi.streamMessage()`가 `Flow<ChatStreamEvent>`로 SSE 토큰을 스트리밍 (목/딜레이 시뮬레이션 아님).

### 네비게이션

- **루트** (`MainActivity`): `AppRoute.ONBOARDING` → `LOGIN` → `MAIN?firstEntry={Boolean}`.
  `startDestination`은 `AuthState`로 결정 (role `MEMBER` → main, 그 외 → onboarding).
- **게이트**: `RemoteConfigManager.gateState`가 `None`이 아니면 `AppGateScreen`이 전체를 덮어 강제 업데이트 유도.
- **탭** (`MainScreen`): 하단 네비 `CHAT | REWARDS | SHOP | MY_PAGE` (`MainTab` enum). 탭 내부에 별도 NavHost로
  대화 목록·진화(Evolution)·설정 등 서브 화면 연결.

### 온디바이스 Gemma 로컬 채팅 (`shared/localllm`)

서버 채팅과 별개로 기기 내 Gemma 모델로 추론하는 로컬 채팅 모드.

- `ChatModeStore`의 `ChatModelMode`(`GEMMA_LOCAL` ↔ 서버)로 모드 전환. 로컬 모드는 `LocalChatStore`/`LocalChatViewModel` 사용.
- 모델은 nginx 정적 서빙에서 다운로드: `KtorModelDownloader` + `ModelDownloadStore`(진행률/재개), `Sha256Digest`로 무결성 검증.
- `DeviceCapability`로 RAM/저장공간을 검사해 미달 기기에서는 로컬 모드 비활성(게이팅).
- 플랫폼 추상화는 `LocalLlmPlatform` (`expect`/`actual`): Android(`AndroidLocalLlmContext`),
  iOS는 `SwiftBackedLocalLlmEngine` → Swift `GemmaLlmBridge`로 추론 위임. 미지원 시 `UnavailableLocalLlmEngine` 폴백.
- `GemmaModelSpec`의 URL/SHA256은 빌드 시 주입(미해소 시 `UNRESOLVED_*` 플레이스홀더).

### 광고 / 원격 설정

- AdMob: 배너/네이티브/리워드 (`app/ads`). `MobileAds.initialize()`는 `CashChatApplication`에서 호출.
- Firebase Remote Config로 광고/리워드 정책을 런타임 조정 (`config/RemoteConfigManager`, 키는 `RemoteConfigKeys`).
- Firebase Analytics 이벤트는 `config/AnalyticsManager`로 일원화.

### 주요 의존성 (`gradle/libs.versions.toml`)

AdMob(`play-services-ads`), Firebase BOM(Analytics, Remote Config), Ktor(core/okhttp/darwin + content-negotiation),
kotlinx.serialization, Koin, Sentry KMP, DataStore Preferences, Navigation Compose, Coroutines/Flow.

> ⚠️ **SQLDelight**: 카탈로그에 의존성은 선언되어 있으나 플러그인(`alias(libs.plugins.sqldelight)`)은
> 현재 주석 처리되어 **비활성** 상태이며 `.sq` 스키마도 없습니다. 로컬 저장은 현재 **DataStore Preferences** 사용.
> SQLDelight 기반 대화 영속화는 미도입 상태이므로, 관련 작업 전 플러그인 활성화 필요.

### KMM Shared 모듈

`:shared` 타겟: `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`.
플랫폼별 코드는 `expect`/`actual` 패턴 사용 (예: `platform/TimeProvider`).

---

## MVP 계획 / 작업 관리

상세 Epic·Task·일정은 코드에 두지 않고 Jira/Confluence로 관리합니다.

- Confluence FE MVP 설계 문서: https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/2130006/FE+MVP+TASK
- AdMob 테스트 광고 ID: https://developers.google.com/admob/android/test-ads
- Koin KMP 가이드: https://insert-koin.io/docs/reference/koin-mp/kmp
- SQLDelight KMP 가이드: https://cashapp.github.io/sqldelight/
