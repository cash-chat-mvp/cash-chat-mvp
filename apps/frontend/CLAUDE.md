# CLAUDE.md

Claude Code가 이 저장소에서 작업할 때 참고하는 가이드 문서입니다.

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

Gradle 모듈 2개: `:app` (Android 전용 UI), `:shared` (KMM 크로스플랫폼 로직)

```
app/src/main/java/com/nomadclub/cashchat/
├── MainActivity.kt          # 단일 액티비티: NavHost 루트, 전역 상태 (points, messageCount)
├── ui/theme/                # Material3 테마 — Color.kt, Type.kt, Theme.kt
├── feature/                 # 화면별 패키지
│   ├── auth/                # LoginScreen + LoginViewModel
│   ├── chat/                # ChatScreen + ChatViewModel
│   ├── main/                # MainScreen (하단 네비) + MainTab enum
│   ├── onboarding/          # OnboardingScreen + OnboardingViewModel
│   ├── rewards/             # RewardsScreen (stateless)
│   ├── shop/                # ShopScreen (stateless)
│   └── mypage/              # MyPageScreen (stateless)
└── data/                    # 플레이스홀더 — 네트워크 레이어 미구현

shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/
├── chat/ChatStore.kt        # 핵심 채팅 로직 (messages StateFlow, sendMessage)
├── auth/LoginStore.kt       # 이메일 상태 (login() 미구현)
├── points/PointsStore.kt    # 포인트 + 메시지 카운트 상태
└── platform/TimeProvider.kt # expect/actual — currentTimeMillis()
```

### 상태 및 데이터 흐름

패턴: MVVM + `StateFlow`. 각 화면의 ViewModel이 shared `*Store`를 감싸는 구조.

```
Composable → ViewModel (collectAsStateWithLifecycle) → Store (StateFlow) → Composable 리컴포즈
```

전역 상태(`points`, `messageCount`)는 `MainActivity`에서 `rememberSaveable`로 관리하며 파라미터로 전달 — 공유 ViewModel 사용 안 함.

DI 프레임워크 미사용. Store는 ViewModel 내부에서 직접 인스턴스화 (`private val store = ChatStore(viewModelScope)`).

> ⚠️ **MVP 작업에서 변경 예정**: Koin 도입으로 DI 구조 전환 (Epic A-1 참고)

### 네비게이션

2단계 중첩 NavHost:
- **루트** (`MainActivity`): `"onboarding"` → `"main?firstEntry={Boolean}"`
- **탭** (`MainScreen`): `Chat | Rewards | Shop | MyPage` — `MainTab` enum 기반

### KMM Shared 모듈

`:shared` 모듈 타겟: `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`
iOS용 `CashChatShared.framework` (static) 생성.
현재 공유 의존성은 `kotlinx.coroutines.core`만 사용.
플랫폼별 코드는 `expect`/`actual` 패턴 사용 (현재는 `TimeProvider`만 해당).

### 채팅 도메인 (현재 구조)

`ChatStore.sendMessage()`는 1.5초 딜레이로 AI 응답을 시뮬레이션.
3번째 메시지마다 `RewardPrompt` 삽입, 400ms 후 `InlineAd` 추가.
광고 데이터는 전부 하드코딩. 실제 네트워크 호출 없음.

`ChatMessage` sealed class: `Text`, `InlineAd`, `RewardPrompt`

### 현재 미구현 항목 (MVP에서 구현 예정)

- 네트워크 레이어 (`core/network/`, `data/repository/`, `data/model/` 플레이스홀더만 존재)
- `LoginStore.login()` — TODO 마킹됨
- 실제 API 연동 (현재 전체 목 데이터)
- 테스트 커버리지

---

## MVP 목표 및 우선순위 (3/15 ~ 3/31)

**목표**: 광고 기반 채팅 MVP를 2026-03-31까지 배포 가능한 상태로 완성.

### 우선순위 원칙

- **Ads FE First**: 광고 수익화 구조를 먼저 확보하고, 채팅은 백엔드 준비 이후 병행
- **Chat BE First**: 채팅 API는 백엔드 선행 개발, FE는 Stub 기반으로 구조 선확정
- **Remote Config 기반 운영**: 코드 수정 없이 광고/리워드 정책 조정 가능해야 함

---

## MVP 도입 라이브러리

| 라이브러리 | 용도 |
|---|---|
| **AdMob** | 배너/전면/네이티브/리워드 광고 |
| **Firebase Analytics** | 광고/채팅 핵심 이벤트 계측 |
| **Firebase Remote Config** | 광고/리워드 정책 런타임 조정 |
| **Sentry KMP** | 런타임 에러 추적 (스트리밍/광고 실패 등) |
| **Ktor** | KMP 네트워크/SSE/WS 통합 클라이언트 |
| **kotlinx.serialization** | API 모델 직렬화 |
| **SQLDelight** | 대화 기록/정책 카운트 로컬 저장 + 마이그레이션 |
| **Koin** | KMP 공통 DI (환경별/플랫폼별 구성 분리) |
| **Coroutines/Flow** | 스트리밍/광고/저장 비동기 처리 (기존 사용 중) |
| **Navigation Compose** | 화면 전환 유지 (Voyager 전환은 MVP 기간 리스크로 보류) |

---

## MVP Epic 구성 및 일정

| 기간 | Epic | 내용 |
|---|---|---|
| 3/15 – 3/17 | **Epic A** | FE Core / 의존성 정리 |
| 3/15 – 3/21 | **Epic B** | Ads FE (배너/전면/네이티브/리워드/실패 UX) |
| 3/18 – 3/21 | **Epic C** | Analytics + Remote Config + Feature Flag |
| 3/18 – 3/21 | **Epic D** | Error Tracking (Sentry) |
| 3/22 – 3/26 | **Epic E** | Chat API 계약/Stub/Ktor 클라이언트 |
| 3/22 – 3/26 | **Epic F** | 광고 정책 엔진 |
| 3/27 – 3/31 | **Epic G** | QA & Release |

---

## Epic 상세

### Epic A. FE Core / 의존성 정리 (3/15~3/17)

**Task A-1. 의존성 추가 및 버전 정리**

수정 파일: `app/build.gradle.kts`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`

| 라이브러리 | Android | iOS |
|---|---|---|
| AdMob | `play-services-ads` | CocoaPods `Google-Mobile-Ads-SDK` |
| Firebase Analytics | `firebase-analytics` | CocoaPods `FirebaseAnalytics` |
| Firebase Remote Config | `firebase-config` | CocoaPods `FirebaseRemoteConfig` |
| Sentry KMP | `sentry-kotlin-multiplatform` | (KMP 공통) |
| Ktor | `ktor-client-core`, `ktor-client-okhttp` | `ktor-client-darwin` |
| kotlinx.serialization | `kotlinx-serialization-json` | (KMP 공통) |
| SQLDelight | `android-driver` | `native-driver` |
| Koin | `koin-core`, `koin-android`, `koin-compose` | (KMP 공통) |

AC: 빌드 성공, 충돌 없음, 모든 버전 `libs.versions.toml` 일원화

**Task A-2. 공통 설정 / 키 관리 구조화**

- `google-services.json` / `GoogleService-Info.plist` 환경별 분리 (dev/prod)
- `BuildConfig` 또는 `local.properties`로 AdMob App ID, Ad Unit ID 분리
- Sentry DSN dev/prod 분리
- `AppConfig` object 또는 Koin module로 키 주입 구조 구현

AC: dev/prod 설정 분기 가능, 키 하드코딩 없음

---

### Epic B. Ads FE First (3/15~3/21) ← 최우선

**Task B-1. AdMob SDK 통합 (Android + iOS)**
- Android: `AndroidManifest.xml`에 `GADApplicationIdentifier` 추가, `MobileAds.initialize()` 호출
- iOS: `Info.plist`에 `GADApplicationIdentifier` 추가, CocoaPods `Google-Mobile-Ads-SDK` 연동
- KMP `expect/actual` 구조로 `AdManager` 인터페이스 정의 (commonMain → androidMain / iosMain)
- 테스트 광고 단위 ID 사용 (Google 공식 테스트 ID)

**Task B-2. 배너 광고 슬롯 UI 연동**
- 채팅 화면 하단 고정 슬롯에 배너 Composable 배치
- `AdView` (Android) / `GADBannerView` (iOS) `expect/actual`로 래핑
- 로딩 중 shimmer 또는 placeholder 처리

**Task B-3. 전면 광고 (Interstitial) 로딩/노출**
- `InterstitialAd` 미리 로딩(pre-load) 구조 구현
- 지정 액션(채팅 입장, 새 대화 시작 등) 시점에 노출
- 로딩 실패 시 graceful fallback (광고 없이 액션 진행)

**Task B-4. 네이티브 광고 UI 컴포넌트**
- 메시지 리스트 아이템으로 삽입 가능한 Composable 컴포넌트
- 헤드라인, 광고주명, 이미지, CTA 버튼 포함 UI
- 기존 `InlineAd` 하드코딩 구조를 실제 네이티브 광고로 교체

**Task B-5. 리워드 광고 플로우**
- `RewardedAd` 미리 로딩 구조
- `onUserEarnedReward` 콜백 수신 시 채팅 차단 해제
- 광고 이탈 시 보상 미지급, 재시청 요구
- 기존 `RewardPrompt` 하드코딩 로직을 실제 리워드 광고로 교체

**Task B-6. 광고 실패 / 재시도 UX**
- 각 광고 유형별 `onAdFailedToLoad` 핸들러 구현
- 실패 사유 로깅 (Sentry + Firebase Analytics)
- 재시도 버튼 또는 자동 재시도 (최대 N회)

---

### Epic C. Analytics + Remote Config + Feature Flag (3/18~3/21)

**Task C-1. Firebase Analytics 이벤트 연동**

| 이벤트 | 파라미터 |
|---|---|
| `chat_start` | `session_id`, `model` |
| `chat_end` | `session_id`, `message_count` |
| `ad_view` | `ad_type`, `ad_unit_id` |
| `ad_failed` | `ad_type`, `error_code` |
| `reward_earned` | `reward_type`, `amount` |
| `chat_blocked` | `reason` |

**Task C-2. Remote Config 연동**

| 키 | 기본값 | 설명 |
|---|---|---|
| `ad_chat_interval` | `1` | 채팅 N회마다 네이티브 광고 삽입 |
| `reward_chat_interval` | `3` | 채팅 N회마다 리워드 광고 요구 |
| `reward_required` | `true` | 리워드 광고 필수 여부 |
| `interstitial_trigger_action` | `new_chat` | 전면 광고 트리거 액션 |

**Task C-3. Feature Flag 구조**
- RC 값 기반 `FeatureFlag` sealed class 또는 enum 정의
- `FeatureFlagManager`로 flag 상태 단일 진입점 관리

---

### Epic D. Error Tracking (3/18~3/21)

**Task D-1. Sentry 연동**
- KMP Sentry SDK 초기화 (Android / iOS 각각)
- 광고 실패/스트리밍 에러 수동 캡처 (`Sentry.captureException`)
- 사용자 식별 정보 최소화 (익명 ID 또는 세션 ID만)
- 환경별 DSN 분리 (dev/prod)

---

### Epic E. Chat 통합 (3/22~3/30) ← BE 선행 개발 이후

**Task E-1. Chat API 계약서 반영 및 Stub 인터페이스**
- `ChatRepository` 인터페이스 정의
- `FakeChatRepository` 구현 (기존 `ChatStore` 목 로직 활용)
- Koin 모듈에서 Fake ↔ Real 구현 교체 가능하도록 구성

**Task E-2. Ktor 기반 채팅 API 클라이언트**
- Ktor HttpClient 설정 (ContentNegotiation, kotlinx.serialization, Logging)
- 메시지 전송 API (`POST /chat/message`)
- SSE 스트리밍 수신

**Task E-3. 스트리밍 렌더링 및 응답 중단**
- `Flow<String>`으로 토큰 단위 수신 후 UI 상태에 누적
- 기존 `sendMessage()` 1.5초 딜레이 시뮬레이션 → 실제 스트리밍으로 교체
- "응답 중단" 버튼: `Job.cancel()` 또는 `CancellationToken`으로 즉시 중단
- 중단 시 부분 응답 보존

**Task E-4. 대화 내역 저장 / 재개 / 목록**
- SQLDelight 스키마: `conversations`, `messages` 테이블
- 메시지 수신 시 자동 저장 (스트리밍 완료 후)
- 대화 목록 화면, 과거 대화 재개 기능

---

### Epic F. 정책 로직 통합 (3/22~3/26)

**Task F-1. 광고 정책 엔진**

```
채팅 카운트 증가
  └→ 카운트 % ad_chat_interval == 0 → 네이티브 광고 삽입
  └→ 카운트 % reward_chat_interval == 0 && reward_required == true
       └→ 리워드 광고 요구
          ├→ 시청 완료 → 채팅 계속
          └→ 미시청/이탈 → 채팅 차단 + 재시청 요구
```

- `AdPolicyEngine` (또는 `ChatAdCoordinator`) 클래스 구현
- RC 값으로 interval/required 여부 실시간 반영
- SQLDelight로 카운트 영속화 (앱 재실행 후에도 유지)
- 기존 `ChatStore`의 하드코딩 정책 로직을 이 엔진으로 교체

---

### Epic G. QA & Release (3/27~3/31)

**통합 시나리오 테스트**

| 시나리오 | 기대 동작 |
|---|---|
| 네트워크 끊김 상태에서 채팅 시도 | 에러 안내 및 재시도 버튼 |
| 광고 로딩 실패 | Graceful fallback, Sentry 로깅 |
| 스트리밍 중 앱 백그라운드 전환 | 재진입 시 응답 상태 보존 또는 재시도 안내 |
| 리워드 광고 이탈 | 채팅 차단 유지, 재시청 요구 |
| RC 값 변경 후 fetch | 정책 즉시 반영 |
| 대화 목록 50건 이상 | 스크롤 성능 이상 없음 |

**릴리즈 체크리스트**
- [ ] 테스트 광고 ID → 실제 광고 ID 교체
- [ ] Firebase prod 환경 설정 적용
- [ ] Sentry prod DSN 적용
- [ ] ProGuard / R8 난독화 규칙 확인
- [ ] Android: AAB 빌드 및 Play Store 내부 테스트 업로드
- [ ] iOS: Archive 빌드 및 TestFlight 업로드
- [ ] 릴리즈 노트 작성

---

## 참고 링크

- Confluence 전체 설계 문서: https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/2130006/FE+MVP+TASK
- AdMob 테스트 광고 ID: https://developers.google.com/admob/android/test-ads
- Koin KMP 가이드: https://insert-koin.io/docs/reference/koin-mp/kmp
- SQLDelight KMP 가이드: https://cashapp.github.io/sqldelight/
