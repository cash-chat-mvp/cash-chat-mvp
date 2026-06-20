# iOS 채팅 파리티 설계 (2026-06-20)

`feature/CC-349` 브랜치에서 iOS 채팅 화면을 Android와 기능 동등 수준으로 끌어올린다.
shared(KMM) 비즈니스 로직은 이미 양 플랫폼 공통으로 완성돼 있고, **iOS Swift 레이어만** 그 기능을
연결하지 못한 상태다. 본 설계는 누락된 iOS UI/ViewModel/브리지/광고 연동을 채운다.

## 배경 / 현재 격차

iOS `ChatViewModel`은 `chatStore` + `chatApi.listConversations()`만 사용한다. shared가 제공하지만
iOS가 연결하지 않은 것:

- `HudStore` — 에너지/레벨/포인트 잔액(`/me`, energy, evolution).
- `AdRewardStore` — `requestNonce` / `refreshQuota` / `awaitRewardApplied` (리워드 광고 보상 폴링).
- `AttendanceApi`/`AttendanceStore` — 채팅 진입 시 자동 출석(`getMonthly`/`checkIn`).
- `EvolutionStore` — 진화 정보/화면.
- `ChatStore.gateInfo` / `streamCompletedCount` — `FlowCollector`에 구독 브리지 없음.
- `ChatItem.ProductCards` 렌더링, `AssistantMessage.gated`(블라인드 답변) 렌더링.

추가로 **iOS에는 AdMob SDK가 전혀 연동돼 있지 않다**(Android는 `RewardedAdManager.kt` 보유).

## 비목표 (Non-goals)

- shared/commonMain 데이터 레이어 재작성 — 전부 재사용한다.
- Android 코드 변경 — iOS와 shared/iosMain 브리지에 한정.
- 백엔드/인프라(nginx SSE) 변경 — 코드 외 영역.

## 접근

상태관리는 **기존 `ChatViewModel` 확장 + 광고 매니저 분리**(A안)로 한다. iOS `ChatViewModel`이
`HudStore`/`AdRewardStore`/`AttendanceStore`를 추가로 감싸 Android `ChatViewModel`과 1:1 대응시키고,
AdMob 연동은 관심사가 다르므로 `RewardedAdManager.swift`로 격리한다. 대안(화면별 다수 VM,
단일 비대 VM)은 과분할/비대 문제로 기각.

`FlowCollector`(shared/iosMain)에 다음 구독 브리지를 추가한다:
- `collectHud(HudStore)` → `HudState`
- `collectGateInfo(ChatStore)` → `GateInfo?`
- `collectStreamCompleted(ChatStore)` → `Int`
- `collectQuota(AdRewardStore)` → `AdRewardQuotaDto?`

`KoinHelper`는 이미 `hudStore()`/`adRewardStore()`/`attendanceStore()`/`evolutionStore()`를 노출한다.

## 슬라이스 분해 (기존 1c~1e 네이밍 계승, 순서대로 진행)

### Slice A — AdMob iOS 기반
- SPM으로 `Google-Mobile-Ads-SDK` 추가.
- `Info.plist`에 `GADApplicationIdentifier`(Google 공식 테스트 앱 ID), `SKAdNetworkItems`(필요 시).
- `AppConfig.admobRewardedAdUnitId` 추가 — `Secrets.swift`에 테스트 unit ID. 하드코딩 금지.
- `RewardedAdManager.swift`: `GADRewardedAd` preload/show, SSV는
  `GADServerSideVerificationOptions.customData = nonce`. `show`는 `onRewarded`/`onDismissed`/`onNotReady`
  콜백 제공(Android `RewardedAdManager`와 동형). 닫힘/실패 시 다음 광고 자동 preload.
- `CashChatIOSApp`에서 `MobileAds.shared.start()` 1회 초기화.
- 검증: 시뮬레이터에서 테스트 리워드 광고 노출 + 보상 콜백 수신.

### Slice B — HUD 헤더
- `FlowCollector.collectHud` 추가.
- `ChatViewModel`: `hud` 상태(level/energy/maxEnergy/points/nextRecoverAt/isLoaded) 노출. `load()`에서
  `hudStore.refresh()`, `streamCompletedCount` 구독으로 스트림 완료 시 `hudStore.refreshEnergyOnly()`.
- `ChatScreen` 헤더: 캐릭터 표시 + `Lv.N`, ⚡에너지 칩 + 게이지, 회복 카운트다운(ISO 파싱은 Swift),
  🪙포인트 칩(`points`가 nil이면 숨김 — `FeatureFlags.POINT_BALANCE` 의존).
- 검증: 시뮬 e2e — 에너지/레벨 표시, 스트림 후 에너지 갱신.

### Slice C — 채팅 내 출석
- 채팅 진입 시 자동 `getMonthly` + 미출석이면 `checkIn`(409 ALREADY_CHECKED_IN은 조용히 통과).
- 캘린더 시트 + 체크인 보상 토스트. 기존 `AttendanceStore`/`AttendanceWidgetView`(BenefitZone) 재사용.
- 검증: 시뮬 e2e — 진입 시 출석 처리, 캘린더 표시.

### Slice D — 에너지 게이트 + 리워드 광고 재전송
- `ChatViewModel`: `rewardPhase`(IDLE/SHOWING_AD/POLLING/FAILED), `startAdReward(showAd:)` —
  Android 미러: `refreshQuota().usedToday`(baseline) → `requestNonce` → `showAd(nonce)` →
  `awaitRewardApplied(baseline)` → 성공 시 `chatStore.retryBlocked()`, 실패 시 FAILED.
- 게이트 바텀시트 UI(밥 부족 안내 + "광고 보고 충전" CTA + 진행 단계 표시).
- 흐름: 409 `INSUFFICIENT_ENERGY`(스트림 시작 전 HTTP 에러, SSE 무관) → `energyGateVisible=true`.
- 검증: 시뮬에서 게이트 발동/광고/폴링, 재전송 스트림은 SSE 동작 시 실기기 검증.

### Slice E — Ad Gate 블라인드 카드 + 상품 카드
- `FlowCollector.collectGateInfo` 추가.
- `ChatScreen.row(for:)`에 분기 추가:
  - `ChatItemProductCards` → 상품 카드 뷰(이미지/제목/가격/CTA).
  - `ChatItemAssistantMessage.gated && !isStreaming` → AdGateCard(teaser 일부 노출 +
    "광고 보고 전체 보기" → `startGateUnlock(messageId, showAd:)` → `unlockGatedMessage`).
- `gate`/`product`는 SSE 스트림 이벤트이므로 실동작은 SSE 경로 의존. 코드/컴파일은 보장.
- 검증: SSE 동작 시 실기기 e2e.

### Slice F — 진화 화면 + 캐릭터 탭
- `EvolutionScreen.swift`: `EvolutionStore` 연동(레벨/진행/진화 액션).
- 채팅 헤더 캐릭터 탭 → 진화 화면(시트 또는 push).
- 검증: 시뮬 e2e.

### Slice G — 마감
- 빈 상태 추천 질문 칩(탭 시 `send`).
- 대화 내보내기(전체를 텍스트로 iOS 공유 시트).
- 검증: 시뮬.

## 핵심 데이터 흐름 — 게이트 재전송 (Android 미러)

```
밥 부족 → 409 INSUFFICIENT_ENERGY (스트림 시작 전 HTTP, SSE 무관)
  → energyGateVisible=true → 게이트 바텀시트
  → "광고 보고 충전" → refreshQuota(baseline=usedToday) → requestNonce
  → RewardedAdManager.show(nonce)  [SSV customData로 서버가 적립]
  → awaitRewardApplied(baseline)  [usedToday 증가를 지수 백오프 폴링]
  → 성공 시 chatStore.retryBlocked() → 막힌 user 메시지 재스트리밍
  → 실패 시 rewardPhase=FAILED (수동 재시도 안내)
```

## 에러 처리

- HUD/출석/quota 조회 실패는 조용히 무시(채팅 본 기능 영향 없음) — `runCatching`/`try?`.
- 게이트 광고 실패/이탈 → 보상 미적립 → `rewardPhase=FAILED`, 재시청 유도.
- 스트림 단절(`-1005` 등) → 부분 응답 유지 + "다시 시도" 버튼(`retryLastMessage`).
- shared suspend 호출은 모두 `@Throws` 경유 — Swift에서 `try`/`do-catch`로 크래시 방지.

## 테스트 / 검증 전략

- shared/iosMain 브리지 추가는 컴파일(프레임워크 빌드)로 1차 검증.
- REST 의존 슬라이스(B/C/F/G, D의 광고·폴링)는 iOS 시뮬레이터 e2e.
- SSE 의존(E 전체, D의 재전송 스트림)은 SSE 경로 동작 확인 후 실기기 e2e. 현재 SSE 동작 여부는
  실기기 종단 검증으로 확정(메모리상 과거 -1005 기록 있으나 nginx 수정 커밋 406ee45/d4f7078 반영 후
  상태는 재확인 필요).

## 주의점 (메모리 반영)

- KMM→Swift: suspend는 `@Throws` 필수(미적용 시 iOS 예외 크래시), Flow는 `FlowCollector` 콜백,
  non-null Bool/Int은 `KotlinBoolean`/`KotlinInt`.
- shared/iosMain 변경 후 `JAVA_HOME=$(/usr/libexec/java_home -v 21)` 설정 +
  `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 재빌드.
- SF Symbol은 `chatSFSymbol(_:fallback:)` 폴백 유지(없는 심볼은 빈 이미지). 색 이모지 시뮬 깨짐 → SF Symbol.
- 광고 ID 하드코딩 금지 — `AppConfig`/`Secrets` 경유. 릴리즈 시 실 ID 교체.
- 기존 iOS UI 골격/네비(`MainTabContainer` 4탭) 유지. 새 top-level View 파일 추가, 탭 참조만 교체.
