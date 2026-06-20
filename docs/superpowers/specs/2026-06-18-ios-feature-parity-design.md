# iOS 기능 파리티 설계 (Android 브랜치 → iOS)

- 작성일: 2026-06-18
- 브랜치: `feature/CC-349`
- 상태: 설계 확정 (구현 플랜 대기)

## 배경

`feature/CC-349`(이하 "브랜치")는 `origin/dev`와 크게 갈라져 있고, **자체 shared 데이터
레이어**(chat·attendance·ads·energy·evolution·hud·points·shop)를 갖는다. Android 앱은 이
데이터 레이어 위에 전체 기능(채팅·혜택존·상점·마이페이지·진화 등)이 구현돼 있다.

반면 iOS(`apps/frontend/CashChatIOS`)는 **UI 골격과 4탭 네비게이션은 이미 구현돼 있으나
대부분 목업**이다. 화면들이 `shared/commonMain` 데이터 레이어에 연결돼 있지 않다.

목표: **iOS를 Android 브랜치와 기능 동등(파리티) 수준으로 끌어올린다.** 단, 아래 제약을 지킨다.

## 핵심 제약 (불변)

1. **dev 머지 금지.** `origin/dev`는 자체 혜택존+shared 레이어를 갖고 있어, 머지 시 7개
   파일(`AttendanceStore`, `SharedModule`, `TokenProvider`, `MainScreen`, `AuthRepository`,
   `CashChatApplication`, `AppModule`)에서 충돌이 예정돼 있다. **브랜치를 source of truth로
   유지**하고 dev를 머지하지 않는다.
   - 향후 dev로 PR 시 해당 충돌은 **ours(브랜치)** 로 해결. 이번에 이식하는 iOS 파일은 브랜치
     전용 신규라 새 충돌을 만들지 않는다.
2. **기존 iOS UI 골격/네비게이션 유지.** `MainTabContainer`(TabView 4탭: 채팅·리워드·상점·
   마이페이지), `MainTab` enum, `OnboardingView`, 각 화면 구조를 **현행 유지**한다. 새 네비
   구조로 갈아엎지 않는다.
3. **shared/commonMain 데이터 레이어 재사용.** 비즈니스 로직은 이미 존재하므로 **재작성 금지**.
   iOS는 SwiftUI + 브리지로 소비만 한다.
4. **혜택존(리워드) UI만 dev 기준.** 나머지 기능은 기존 iOS 화면을 유지하되 shared 연결 +
   필요한 곳 UI를 Android 수준으로 보강한다.

## 현재 iOS 구조 (유지 대상)

`ContentView.swift`(~72KB) 내 인라인 정의:

- `ContentView` → 세션 체크 → `OnboardingView` 또는 `OnboardingView.MainTabContainer`
- `MainTab` enum: `chat("채팅")`, `rewards("리워드")`, `shop("상점")`, `mypage("마이페이지")`
- `MainTabContainer`: `TabView` 4탭
- 화면(모두 목업): `ChatView`(~500줄), `RewardsView` + `RewardAdModalView`, `ShopView`
  (카테고리: 전체/카페/편의점/외식/상품권), `MyPageView`, `OnboardingView`
- 인프라(구현됨): `AppState`(세션/토큰 복원), `AuthApiService`, `AppleSignInCoordinator`,
  `KeychainHelper`, `GoogleSignIn` 연동

## 재사용할 shared 데이터 레이어 (commonMain)

| 도메인 | 타입 |
|---|---|
| chat | `ChatApi`, `ChatStore`(SSE 상태머신), `ChatGateway`/`ApiChatGateway` |
| attendance | `AttendanceApi`, `AttendanceStore`(`state`/`rewardEvents`/`loadMonthly`/`checkIn`) |
| ads | `AdsApi`, `AdRewardStore` |
| energy | `EnergyApi`, `EnergyTopupApi` |
| evolution | `EvolutionApi`, `EvolutionStore` |
| hud | `HudStore` |
| points | `PointsRepository`/`LocalPointsRepository`(`balance: StateFlow<Long>`), `PointsStore` |
| shop | `ShopApi` |
| wallet | `PointsApi` |
| auth | `LoginStore`, `TokenProvider`(인터페이스) |

DI: `sharedDataModule(rawBaseUrl)` 은 소비측이 먼저 `single<TokenProvider> { ... }` 를 등록한
다는 전제로 동작한다. HTTP 엔진은 expect/actual(`http1ClientEngine()`)로 iOS는 이미 Darwin
배선 완료(`HttpClientEngine.ios.kt`).

## 슬라이스 분해 (의존 순서)

각 슬라이스는 **자체 spec→plan→구현** 사이클을 가진다. 본 문서는 프로그램 개요 +
Slice 0·1 상세를 담는다.

| # | 슬라이스 | 비고 |
|---|---|---|
| 0 | iOS 기반 (Koin/Flow 브리지) | 모든 기능의 선행조건 |
| 1 | **채팅** | 먼저. 가장 큼. 기존 `ChatView` → `ChatStore` 연결 |
| 2 | 혜택존 | `RewardsView` → **dev iOS 혜택존 UI 교체** + 재배선 |
| 3 | 상점 + 마이페이지 | `ShopView`/`MyPageView` 연결 |
| 4 | 온보딩 | `OnboardingView` 실연동 점검 |

auth/settings는 iOS에 이미 동작 → 파리티 점검만(경미).

---

## Slice 0 — iOS 기반 (Koin/Flow 브리지)

### 목적
Swift에서 shared 데이터 레이어(Koin 그래프 + Kotlin Flow)에 접근할 수 있는 다리를 놓는다.
이후 모든 슬라이스가 이 위에서 동작한다.

### 컴포넌트

1. **`shared/iosMain/.../di/KoinIos.kt`** (신규)
   ```kotlin
   fun doInitKoin(baseUrl: String, tokenProvider: TokenProvider) {
       startKoin {
           modules(
               module { single<TokenProvider> { tokenProvider } },
               sharedDataModule(baseUrl),
           )
       }
   }
   ```
   - dev의 `sharedModule(baseUrl, tokenProvider, engineProvider)` 와 달리, 브랜치
     `sharedDataModule(baseUrl)` + 별도 `TokenProvider` 등록 방식 사용. engineProvider 불필요
     (Darwin은 actual로 배선됨).

2. **`shared/iosMain/.../di/IosBridges.kt`** (신규, dev 적응)
   - `KoinHelper : KoinComponent` — `chatStore()`, `attendanceStore()`, `pointsRepository()`,
     `evolutionStore()`, `hudStore()`, `adRewardStore()` 등 슬라이스에서 필요한 store getter.
     (슬라이스 진행하며 점진 추가. Slice 0에서는 골격 + attendance/points/chat 우선.)
   - `FlowCollector` — `StateFlow`/`SharedFlow` 를 Swift 콜백으로 브리지. `Dispatchers.Main`
     스코프에서 collect, `cancel()` 제공(Swift ViewModel `deinit`에서 호출해 누수 방지).
   - **주의(메모리):** dev의 `clearApiTokenCache()`는 브랜치에 `AuthenticatedApiClient`가
     없으므로 **제외**. 로그아웃 시 Ktor 토큰 캐시 정리는 별도 이슈(보류 사항 참조).

3. **`CashChatIOS/.../KeychainTokenProvider.swift`** (신규, dev 적응)
   - 브랜치 `TokenProvider` 인터페이스 구현: `accessToken() -> String?`, `refresh() -> Bool`.
     `@Throws` 불필요(반환형 옵셔널/불리언, 예외 전파 안 함 — KMM suspend @Throws 규칙은
     예외를 던지는 함수에만 해당). 기존 `KeychainHelper`로 토큰 저장/조회, refresh는
     `AuthApiService`의 토큰 갱신 호출 재사용.

4. **`CashChatIOSApp.swift`** (수정)
   - 앱 시작 시 `KoinIosKt.doInitKoin(baseUrl: AppConfig.apiBaseUrl, tokenProvider: KeychainTokenProvider())` 1회 호출.

### 빌드/검증
- `./gradlew :shared:assembleDebug` (KMM 컴파일 — 브리지 타입 검증)
- Xcode 빌드(`JAVA_HOME` JDK21 필수, CLAUDE.md). 앱 기동 시 Koin 초기화 크래시 없음 확인.

### 완료 기준
- iOS 앱이 기동되고 `KoinHelper().attendanceStore()` 등으로 store 인스턴스를 얻을 수 있다.
- 기존 화면/네비게이션은 그대로 동작(회귀 없음).

---

## Slice 1 — 채팅

### 목적
기존 목업 `ChatView`(및 대화목록·진화·게이트/카드)를 `ChatStore`(SSE 상태머신)에 연결해
실제 AI 채팅이 동작하게 한다. **기존 UI 디자인을 유지**하되 필요한 곳을 Android 수준으로 보강.

### 범위 (Android `feature/chat` 미러, 기존 iOS UI 위에)
- `ChatView` ↔ `ChatStore`: 메시지 전송, **SSE 스트리밍 수신**(FlowCollector로 상태 구독),
  스트리밍 중 부분 렌더링.
- 대화목록(`ConversationListScreen` 대응) — 풀스크린.
- 캐릭터 아바타(에너지별 표정), 광고 게이트 카드, 쿠팡 상품 카드(SSE product/gate 이벤트).
- 에너지 게이트 바텀시트(밥 충전), 출석 시트.
- 진화 화면(`EvolutionStore` 연동) — 풀스크린.

### 의존
Slice 0(브리지). `KoinHelper`에 `chatStore()`/`hudStore()`/`evolutionStore()`/`adRewardStore()`
getter 추가.

### 설계 메모
- SSE: iOS Darwin 엔진의 SSE 동작은 별도 검증 필요(브랜치 `HttpClientEngine.ios.kt` 주석 참조 —
  RST_STREAM 이슈는 OkHttp/HTTP-2 경로에서 관찰). 채팅 슬라이스 구현 시 실스트리밍 확인을
  완료 기준에 포함.
- `ChatStore` suspend/Flow 표면을 Swift에서 쓰므로, suspend 경계는 FlowCollector(Flow) 또는
  store 내부 `scope.launch`(fire-and-forget)로 감싸 Swift에 suspend를 노출하지 않는다.

### 빌드/검증
- `:shared:assembleDebug` + Xcode 빌드.
- 실서버 대상: 메시지 전송 → SSE 토큰 스트리밍 표시 → 응답 완료. 광고/상품/에너지 게이트
  이벤트 표시. 진화 화면 진입/동작.

### 완료 기준
- iOS에서 실제 AI 채팅(스트리밍 포함)이 동작하고, 기존 탭/네비 구조는 유지된다.

---

## 알려진 보류 사항

- **로그아웃 시 iOS Ktor 토큰 캐시 정리**: dev엔 `AuthenticatedApiClient.clearTokenCache()`로
  있으나 브랜치 구조에 대응 타입이 없다. 혜택존/파리티 범위 밖 — 별도 처리.
- **iOS SSE 실동작**: Darwin 엔진 SSE는 Slice 1에서 실검증 필요.

## 향후 슬라이스 (개요만)

- **Slice 2 — 혜택존**: 기존 `RewardsView`를 dev iOS 혜택존 UI(`BenefitZone/AttendanceViewModel`
  + ContentView 혜택존 뷰)로 교체, 브랜치 `AttendanceStore`/`PointsRepository`에 재배선.
  필드 적응: `nextReward.coin`(OK), bonus 문자열은 `bonusItems`에서 파생.
- **Slice 3 — 상점+마이페이지**: `ShopView`↔`ShopApi`, `MyPageView`↔points/사용자.
- **Slice 4 — 온보딩**: `OnboardingView` 실연동 점검.
