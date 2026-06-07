# 혜택존(Benefit Zone) — 설계 문서

- 작성일: 2026-06-07
- 관련 Jira: CC-312, CC-287 / 작업 브랜치 컨벤션: `feature/*`
- 참고: Confluence "혜택존 — TNK 오퍼월 · 출석체크 · 부가 위젯" (FCTC/14909530), `docs/design-preview/index.html` (혜택존 탭, line 920~)

## 0. 요약

기존 `feature/rewards`는 순수 UI 목업이다. 이를 **「혜택존」 탭**으로 확장하여 4개 컴포넌트(출석체크 · 데일리 미션 · 리워드 광고 · TNK 오퍼월)를 실제 API와 연동한다.

- **담당 범위: FE 전용** (Android Compose + iOS SwiftUI 동시). 미구현 BE(미션 · 포인트 잔액 · TNK webhook)는 본 문서가 **API 계약만 명세**하고 구현은 별도 BE 티켓.
- 범위가 커서 **단계(Phase) 단위로 분해**한다. 각 Phase는 독립 spec/plan/구현 사이클을 가진다.
- **본 문서가 상세화하는 범위: Phase F(기반 인프라) + Phase 1(출석체크).** 나머지 Phase 2~4는 §6에 개요만 기록하고, 각 Phase 착수 시 별도 spec으로 확장한다.

## 1. 전체 분해 (Master Decomposition)

| 레이어 | Android | iOS | BE 의존 |
|---|---|---|---|
| **F. 기반 인프라** | shared 인증 Ktor 클라이언트 | iOS 메인 탭/네비 셸 + 인증 클라이언트 | — |
| 1. 출석체크 | Compose 화면 | SwiftUI 화면 | ✅ Ready |
| 2. 리워드 광고 | AdMob SDK | AdMob SDK | ✅ Ready |
| 3. 데일리 미션 | Compose 화면 | SwiftUI 화면 | ❌ 계약만 |
| 4. TNK 오퍼월 | Native SDK | Native SDK | ❌ (FE 선행) |

**권장 빌드 순서:** F → 1 → 2 → 3 → 4. (단순·BE Ready·리텐션 효과 순)

## 2. 현황 분석 (코드 기준)

### 2.1 백엔드 준비 현황
- `POST /api/attendance/check-in`, `GET /api/attendance/me` — 출석 ✅ (응답 DTO: `CheckInResponse`, `MonthlyAttendanceResponse`)
- `POST /api/ads/reward/issue-nonce`, `GET /api/ads/reward/quota`, `GET /api/ads/google/ssv` — 광고 ✅
- `GET /api/users/me` — 유저 ✅
- **부재:** 미션 도메인, 포인트 잔액 조회 엔드포인트(`UserPointService`에 컨트롤러 없음), TNK webhook.

### 2.2 프론트엔드 현황
- `app/.../feature/rewards/RewardsScreen.kt` — 순수 UI 목업. `MainScreen.kt:95`에서 `points/messageCount/addPoints` 파라미터로 호출.
- `shared/.../points/PointsStore.kt` — 인메모리 목업(`initialPoints = 1250`), 실 API 없음.
- 인증 네트워킹은 **Android 앱 레이어 전용**: `core/network/ApiService`(Retrofit) + `AuthInterceptor`(Bearer 주입) + `TokenAuthenticator`(401 리프레시) + `core/data/TokenDataStore`.
- shared `auth/AuthApiService`(Ktor)는 **인증 없는** 별도 스택(게스트/콜백/리프레시 전용).
- iOS 앱(`CashChatIOS/`)은 `ContentView`, `SettingsView`, 인증 코디네이터만 존재. **메인 탭·기능 화면 없음.**

### 2.3 Confluence 대비 수정 사항
1. **iOS TNK는 WebView가 아니라 네이티브 뷰** (`AdOfferwallViewController` / `AdOfferwallView`, SwiftUI는 `UIViewControllerRepresentable`로 임베드). "iOS WebView 폴백" 가정 폐기.
2. **코인 잔액 조회 API 부재** → 헤더의 코인 표시(1,250)에 소스가 없음. 신규 계약 필요(블로커).
3. **미션 BE 미존재** → "미션 완료"라던 진행사항과 불일치. 계약부터 정의.

## 3. 아키텍처 결정

### 3.1 네트워킹: shared KMM 인증 Ktor 클라이언트 (A안 채택)
- `shared/commonMain`에 `AuthenticatedHttpClientFactory`를 두고, Ktor `Auth`(Bearer) 플러그인으로 access 토큰 주입 + 401 시 refresh 토큰으로 갱신(rotation). 기존 `TokenAuthenticator`의 정책(refresh 엔드포인트 자체 401 시 무한루프 방지, 게스트/멤버 분기)을 Ktor로 이식.
- 토큰 저장소를 KMM에서 공유 가능하도록 `expect`/`actual` 또는 `multiplatform-settings`로 추상화: Android는 기존 `TokenDataStore`(DataStore) 위임, iOS는 Keychain(`KeychainHelper`) 위임.
- **B안(플랫폼별 중복)/C안(하이브리드) 기각:** iOS+Android 동시 요구에서 DTO·엔드포인트 드리프트 위험과 토큰 저장소 이원화 문제가 큼.

> 이행(migration) 주의: 기존 Android Retrofit 인증 경로는 즉시 제거하지 않는다. 신규 혜택존 API만 shared Ktor로 시작하고, auth 흐름 이관은 별도 작업으로 분리해 회귀 위험을 격리한다.

### 3.2 데이터/상태 계층
- 화면 상태는 **shared Store**로 KMM 공유(Android `collectAsState`, iOS `@Published` 브리지).
- Store는 도메인별 API Service를 호출하고 `StateFlow`로 노출. 기존 `ChatStore`/`PointsStore` 패턴 계승.
- iOS suspend 함수는 `@Throws(CancellationException::class, Exception::class)` 필수(미준수 시 예외 발생하면 앱 크래시 — [[reference-kmm-suspend-throws]]).

### 3.3 UI 계층
- Android: Jetpack Compose(Material3), 디자인 컬러 `#5C6BFA`(primary), `#FFB800`(accent). 레이아웃은 `docs/design-preview/index.html` 혜택존 탭 준수.
- iOS: SwiftUI. 메인 탭 셸(채팅/혜택존/상점/MY) 신규.
- TNK 오퍼월만 네이티브 SDK 뷰를 임베드(Phase 4).

## 4. Phase F — 기반 인프라 (상세)

### 4.1 목표
출석체크(Phase 1) 이후 모든 Phase가 의존하는 인증 네트워킹과 iOS 셸을 구축한다.

### 4.2 구성 요소
1. **shared 인증 Ktor 클라이언트** (`shared/commonMain/.../core/network/`)
   - `AuthenticatedApiClient`: baseUrl + 토큰 공급자 주입. `ContentNegotiation(Json{ ignoreUnknownKeys=true })`, `Auth { bearer { loadTokens / refreshTokens } }`.
   - `TokenProvider` (expect/actual 또는 인터페이스): `getAccessToken()`, `getRefreshToken()`, `getRole()`, `saveTokens()`. Android→DataStore, iOS→Keychain 위임.
   - 401 정책 이식: refresh/guest/callback 경로 재시도 제외, 동시 401 단일 갱신.
2. **iOS 메인 탭 셸** (`CashChatIOS/`)
   - `TabView`: 채팅 · 혜택존 · 상점 · MY 4탭. 혜택존 탭에 Phase 1 화면 장착 지점 마련.
   - 인증 가드: 미로그인 시 기존 auth 플로우로 라우팅.

### 4.3 검증
- Android/iOS 각각에서 `GET /api/users/me`를 신규 shared 인증 클라이언트로 호출해 200 확인(토큰 주입·리프레시 동작 확인). 만료 토큰 시 자동 refresh 후 재요청 성공.

## 5. Phase 1 — 출석체크 (상세)

### 5.1 API 연동
- **조회:** `GET /api/attendance/me?year&month` → `MonthlyAttendanceResponse { year, month, checkedDays:List<Int>, currentStreak, todayChecked, nextRewardPreview }`.
- **출석:** `POST /api/attendance/check-in` → `CheckInResponse { awardedCoin, streakDayCount, bonusItems:[{itemCode,quantity}], nextRewardPreview }`.
- **잔액(신규 계약, BE 별도 티켓):** `GET /api/points/me` → `{ balance: Long }`. 헤더 코인 표시 및 출석 후 갱신에 사용. **BE 부재 동안은 어댑터로 격리**(아래 5.4).

### 5.2 상태 (shared `AttendanceStore`)
- `state: StateFlow<AttendanceUiState>` — `{ year, month, checkedDays, currentStreak, todayChecked, nextReward, isCheckingIn, coinBalance }`.
- `loadMonthly()`, `checkIn()` (성공 시 도장 추가 + 토스트용 보상 이벤트 emit + 잔액 갱신). 중복 출석(이미 todayChecked)·서버 reject 에러 처리.
- 출석 결과 토스트/애니메이션을 위한 일회성 이벤트 채널(`Flow` 또는 `Channel`).

### 5.3 UI (양 플랫폼)
- 상단 고정 `AttendanceWidget`: 월 라벨, 일자 도트 그리드(완료 `#5C6BFA` / 오늘 `#FFB800` / 미출석 `#E0DCEF`), "오늘 보상" 프리뷰, `출석 도장 찍기` 버튼(48dp, `todayChecked`면 비활성).
- 출석 성공: 도장 애니메이션 + 보상 토스트(`awardedCoin`, `bonusItems`).
- 헤더 우측 코인 잔액 표시.
- 기존 `RewardsScreen.kt`를 혜택존 컨테이너로 교체하고 목업 미션/룰렛 카드는 후속 Phase 자리로 정리(Phase 1에선 출석 위젯 + 나머지 영역 placeholder).

### 5.4 BE 미구현 격리
- `PointsRepository` 인터페이스 뒤에 `GET /api/points/me` 호출을 두고, BE 준비 전에는 `CheckInResponse.awardedCoin` 누적 + 로컬 캐시로 잠정 동작하는 어댑터 구현. BE 준비 시 실제 호출 구현으로 교체(인터페이스 불변).

### 5.5 검증
- 신규 출석: 도트 추가·streak 증가·코인 토스트·버튼 비활성 확인.
- 중복 출석 차단(이미 todayChecked면 버튼 비활성, 서버 reject도 graceful).
- 월 경계/연속 끊김: 다른 달 조회 시 `checkedDays` 반영.
- Android `assembleDebug` + iOS 빌드 통과(JAVA_HOME JDK 21).

## 6. 후속 Phase 개요 (별도 spec에서 확장)

- **Phase 2 — 리워드 광고:** AdMob Rewarded SDK(Android/iOS) + `issue-nonce`→광고 시청→SSV 콜백→`quota` 갱신. 코인 지급은 **반드시 서버 SSV 경로**로만(클라 직접 지급 금지). 일일 캡 `AdRewardQuotaResponse{usedToday,dailyLimit,remaining,resetAtKst}` 표시. **광고 호출당 비용/적립 코인(30~50)·일일 캡을 화면·문서에 명시** ([[feedback-ai-feature-cost]] 정신 준용).
- **Phase 3 — 데일리 미션:** 계약 `GET /api/missions/me`, `POST /api/missions/{id}/claim`, `POST /api/missions/refresh`(광고 1회/일 소모). 미션 풀(채팅 N회·친구초대·진화 시도·상점 방문·광고 시청·쿠팡 클릭) 중 랜덤 3개. KST 자정 갱신. BE 부재 동안 어댑터/목.
- **Phase 4 — TNK 오퍼월:** `expect/actual OfferwallProvider { init(appId); setUserName(userId); showOfferwall() }`. Android `com.tnkfactory:rwd`(`TnkSession.applicationStarted`, `offerwall.startOfferwallActivity`/`getAdListView`), iOS xcframework(`TnkSession.initInstance(appId)`, `AdOfferwallView`). 적립은 TNK→BE webhook→코인(별도 BE 티켓). **TNK 앱 등록·키 발급은 선행 필요** — 미확보 시 SDK 호출부는 스텁, 구조(인터페이스·임베드 지점)만 완성.

## 7. 미결정 / 위험

- `GET /api/points/me` 응답 스펙 BE와 합의 필요(잔액만 vs 거래내역 포함).
- 토큰 저장소 KMM 공유 방식: `multiplatform-settings` 도입 vs expect/actual 수동 위임 — Phase F 착수 시 결정.
- iOS 메인 셸 신규 구축이 Phase F에 포함되어 Phase F 비용이 큼(예상보다 길어질 수 있음).
- TNK 앱 등록 미완 → Phase 4는 키 확보 전까지 스텁 한도.
- 미션 BE "완료" 보고와 실제 부재 불일치 → 착수 전 BE 담당과 확인.

## 8. 본 spec의 구현 단위

**Phase F + Phase 1**을 하나의 implementation plan으로 진행한다. Phase 2~4는 각자 착수 시 본 문서 §6을 기반으로 별도 spec을 작성한다.

## 9. 실행 방식: 서브에이전트 + 진행 로그

- 구현 plan의 각 task는 **서브에이전트**로 실행한다(태스크 단위 격리).
- **진행 로그 파일:** `docs/superpowers/specs/2026-06-07-benefit-zone-progress.md`.
- **규약:** 각 task를 담당한 서브에이전트는 **완료 시점에 진행 로그에 항목을 append**한다. 한 항목은 다음을 포함한다:
  - Task 번호/제목, 상태(✅ 완료 / ⚠️ 부분 / ❌ 막힘)
  - 변경/추가 파일 목록
  - 검증 결과(빌드·테스트·수동 확인 무엇을 어떻게 통과했는지, 미통과면 사유)
  - 다음 task로의 인계 메모(주의점·미결)
- 로그는 append-only(기존 항목 수정 금지), 한국어로 작성. plan 실행 오케스트레이터는 각 서브에이전트 디스패치 프롬프트에 "완료 후 진행 로그 append" 지시를 포함한다.
