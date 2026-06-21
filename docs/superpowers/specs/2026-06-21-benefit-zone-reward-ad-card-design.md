# 혜택존 리워드 광고 카드 설계 (슬라이스 2)

- **날짜**: 2026-06-21
- **브랜치**: `feature/benefit-reward-card` (← `feature/CC-355`에서 분기, 로컬 머지로 회수)
- **관련**: 슬라이스 1(배너) `2026-06-20-benefit-zone-banner-ad-design.md`, 리워드 BE `2026-05-31-reward-be3-ad-reward-design.md`, AdMob SSV `2026-05-17-google-admob-ssv-backend-design.md`

## 1. 배경 & 목표

혜택존(BenefitZone)의 "리워드 광고" 카드를 placeholder에서 실제 동작으로 교체한다.

핵심 전제: **리워드 광고 → 에너지 적립 흐름은 이미 완성되어 있다.** 채팅에서 에너지(밥)가 0이 될 때 뜨는 `EnergyGateBottomSheet`이 바로 그 흐름이며, 백엔드 `/api/ads/reward/quota`·`/issue-nonce` + AdMob SSV 검증, FE의 `AdRewardStore`/`RewardedAdManager`가 모두 존재한다.

따라서 이 슬라이스는 **새 기능이 아니라 기존 보상 흐름의 두 번째 진입점**이다. 새 API나 백엔드 변경은 없다.

### 보상 정책 (제품 결정)
- 광고 1회 시청 → 에너지 +5, 하루 3회 (제품 의도값)
- **단, 보상량·한도는 서버가 결정한다.** SSV 콜백 시 서버가 에너지를 지급하고 `usedToday`를 올리며 `dailyLimit`도 서버 응답값이다. FE는 표시만 한다.
- 혜택존 카드와 채팅 에너지게이트는 **같은 `/api/ads/reward` 흐름과 하루 한도를 공유**한다. FE에서 카드 전용 한도를 별도 지정하지 않는다. 추후 백엔드가 진입점별 한도를 분리하면 그때 대응한다.

## 2. 제약 / 발견사항

1. `AdRewardQuotaDto`에는 `usedToday`/`dailyLimit`/`remaining`/`resetAtKst`는 있으나 **보상량(에너지 개수) 필드가 없다.** → 카드에 보상 숫자를 표시하려면 FE 하드코딩이 되어 서버 실제 지급량과 어긋날 수 있다. **결정: 숫자 없이 표시.**
2. 보상 오케스트레이션(phase 머신: `SHOWING_AD→POLLING→IDLE/FAILED`, `usedToday` baseline 추적)이 현재 `ChatViewModel`에 채팅 전용 로직(`chatStore.retryBlocked`, energyGate, `lastRewardBaseline` 기반 retry)과 엉켜 있다. 카드는 같은 시퀀스가 필요하지만 완료 동작이 다르다(차단 해제 대신 → 에너지/잔액 새로고침 + 토스트).
3. 혜택존 화면은 코인 잔액(🪙)만 표시하고 **에너지(밥)는 표시하지 않는다.** 보상은 에너지로 지급되므로 카드의 피드백은 토스트뿐이고, 충전된 에너지는 다음 채팅 진입 시 HUD에 반영된다(의도된 동작).

## 3. 아키텍처

선택된 접근: **혜택존 전용으로 독립 구현 + KMM 공유 헬퍼로 중복 제거 (격리, 채팅 경로 무손상).**

대안으로 검토했으나 채택하지 않은 것:
- 오케스트레이션을 `ChatViewModel`에서 즉시 추출(공유): 동작 중인 채팅 보상 경로에 회귀 위험. 별도 정리 슬라이스로 미룸.
- 카드가 `EnergyGateBottomSheet` 재사용: 시트가 "밥 떨어졌어요"·포인트 충전 등 채팅 맥락에 묶여 부적합.

### 3.1 공유 헬퍼 (KMM `commonMain`) — `AdRewardStore.runRewardFlow`

`usedToday` baseline 판정 시퀀스를 한 곳에 캡슐화하여 카드가 호출한다.

```kotlin
enum class RewardOutcome { APPLIED, PENDING, NOT_WATCHED }

@Throws(Exception::class)
suspend fun runRewardFlow(showAd: suspend (nonce: String) -> Boolean): RewardOutcome
// 1. baseline = refreshQuota().usedToday
// 2. nonce = requestNonce()
// 3. watched = showAd(nonce)
//      false → NOT_WATCHED
// 4. awaitRewardApplied(baseline) → true ? APPLIED : PENDING
```

- 미묘한 `usedToday` baseline 판정(에너지 자동회복과 광고 보상 격리)이 단일 출처가 된다.
- **`ChatViewModel`은 변경하지 않는다.** 채팅은 `lastRewardBaseline`을 유지해 FAILED 후 광고 재시청 없이 폴링만 재시도(`retryRewardPolling`)하는 추가 요구가 있어, 인라인 구현을 그대로 둔다. 채팅의 헬퍼 채택은 추후 별도 정리 슬라이스 후보.
- 결과적으로 채팅 인라인 시퀀스와 헬퍼가 잠시 공존하나, 무손상을 우선한 의도적 선택이다.

### 3.2 카드 비주얼 디자인 — "그라데이션 히어로" (선택됨)

리워드 카드는 기존 `BenefitInfoCard`(미구현 소개 카드, "곧 출시" 배지) 재사용이 아니라 **전용 컴포넌트**로 새로 만든다. 동작하는 핵심 보상 동선이므로 혜택존에서 시각적으로 두드러지는 "주인공" 카드로 디자인한다. (비주얼 브레인스토밍에서 4안 중 A안 채택)

- 따뜻한 그라데이션 배경(주황→핑크, `#FF8A4C → #FF5E8A` 135°), 부드러운 그림자
- 좌상단: 프로스트 원형 배경의 ⚡ 아이콘
- 타이틀 `"리워드 광고"`(흰색, 굵게), 서브 `"광고 보고 에너지 충전하기"`
- 흰색 솔리드 CTA 필 버튼 `"▶ 광고 보기"`
- 우상단: 프로스트 필 배지 `"오늘 N회 남음"` (N = `quota.remaining`, 서버값)

상태 매핑:
- `remaining > 0`: 그라데이션 활성, CTA 활성, 배지 `"오늘 N회 남음"`
- `remaining == 0`: 그라데이션 desaturate/dim, CTA 비활성, 배지 `"오늘 한도 도달 · 자정 리셋"`
- 광고 표시/폴링 중: CTA에 스피너 + `"보상 확인 중..."`

Mockup: `.superpowers/brainstorm/.../reward-card-designs.html`(A안).

### 3.3 Android 카드 연동

- 작은 상태 홀더(`BenefitRewardViewModel` 또는 화면 패턴에 맞춘 holder): `AdRewardStore` + `RewardedAdManager` + `HudStore`(모두 Koin 등록됨)를 주입, `quota: StateFlow<AdRewardQuotaDto?>` + 진행 phase 노출.
- 새 `RewardAdCard` Composable(§3.2 디자인)을 `BenefitZoneScreen.kt`의 리워드 placeholder(현재 `BenefitInfoCard` "광고 1회 시청 → 🪙+40 코인...") 자리에 배치:
  - 진입/노출 시 `refreshQuota()` → 배지 `"오늘 N회 남음"` (N = `quota.remaining`)
  - 탭(remaining>0): `runRewardFlow { nonce -> adManager.show(activity, nonce, onRewarded, onDismissed, onNotReady) }` (EnergyGate와 동일한 `suspendCancellableCoroutine` 패턴)
  - `APPLIED` → `hudStore.refreshEnergyOnly()` + `refreshQuota()` + 토스트 `"에너지를 충전했어요!"`
  - `PENDING` → `refreshQuota()` + 토스트 `"보상 확인 중이에요. 잠시 후 다시 확인해주세요"`
  - `NOT_WATCHED` → 광고 미준비 토스트 또는 조용히 복귀
  - `remaining == 0` → 카드 dim, CTA 비활성, 배지 `"오늘 한도 도달 · 자정 리셋"`

### 3.4 iOS 카드 연동 (파리티)

- `BenefitZoneScreen.swift`의 리워드 카드(현재 `"광고 1회 시청 → +40 코인 · 하루 10회까지"`)를 §3.2 그라데이션 히어로 디자인의 SwiftUI 카드로 교체하고, 공유 `AdRewardStore.runRewardFlow` + iOS `RewardedAdManager.swift` + 공유 `HudStore`로 동일 동작 연동.
- Swift 빌드(⌘B)는 사용자가 Xcode에서 확인한다(에이전트 환경에서 불가).

## 4. 카피

- 타이틀 `"리워드 광고"`, 서브 `"광고 보고 에너지 충전하기"`, CTA `"▶ 광고 보기"`
- 한도 배지 `"오늘 N회 남음"` (N = 서버 `quota.remaining`)
- 보상량 숫자 미표시 → 서버 지급량이 바뀌어도 문구 불일치 없음. 한도 N도 서버값으로 동적 표시.

## 5. 테스트

- `AdRewardStoreTest`(commonTest)에 `runRewardFlow` 케이스 추가:
  - 미시청(`showAd`가 false) → `NOT_WATCHED`, 폴링 호출 없음
  - 시청 + `usedToday` 증가 관측 → `APPLIED`
  - 시청 + 폴링 끝까지 변동 없음 → `PENDING`
- Android: `:app:assembleDebug` 컴파일 성공.
- iOS: shared 빌드 + 사용자 Xcode ⌘B 확인.

## 6. 범위 밖 / 인지사항

- 혜택존 화면엔 에너지 표시가 없어 보상 피드백은 토스트뿐(에너지는 다음 채팅에서 HUD 반영). 의도된 동작.
- 보상량/한도 변경은 백엔드 설정 영역. FE는 표시만.
- 채팅 보상 경로(`ChatViewModel` 인라인 시퀀스)는 이번 슬라이스에서 변경하지 않음.
- 실광고 전환(실 단위 ID·prod 주입·스토어 심사)은 기존 릴리즈 체크리스트 따름.

## 7. 후속 슬라이스 (대기)

3️⃣ 행운 룰렛 → 4️⃣ 친구 초대. 각각 별도 spec→plan.
