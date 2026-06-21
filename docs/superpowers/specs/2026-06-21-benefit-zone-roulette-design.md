# 행운 룰렛 설계 (슬라이스 3, FE-first 스텁)

- **날짜**: 2026-06-21
- **브랜치**: `feature/benefit-roulette` (← `feature/CC-355`에서 분기, 배너·리워드 카드와 동일 방식, 로컬 머지 회수)
- **관련**: 리워드 카드 `2026-06-21-benefit-zone-reward-ad-card-design.md`, BE API 요청 `docs/planning/be-api-requests-cc355.md`(본 슬라이스에서 작성), AdMob SSV `2026-05-17-google-admob-ssv-backend-design.md`

## 1. 배경 & 접근

혜택존에 "행운 룰렛" 카드/화면을 신규 추가한다. 리워드 카드와 달리 **기존 백엔드 자산이 없다** — 당첨 결정·확률·에너지 지급·일일 한도는 전부 서버가 해야 하며(클라가 당첨을 정하면 조작 가능), 신규 `/api/roulette/*` API가 필요하다.

접근(사용자 결정): **FE 먼저 스텁으로 구현**하고, BE API는 별도 요청 문서로 계약화해 커밋한다. FE는 `RouletteRepository` 인터페이스 뒤 `FakeRouletteRepository`로 잠정 동작(로컬 가중 랜덤·카운트 시뮬레이션)하고, BE 준비 시 `RemoteRouletteRepository`로 교체(인터페이스 불변). 이는 기존 `PointsRepository`(Local→Remote) 격리 패턴과 동일하다.

## 2. 메커니즘 (서버가 진실, 스텁이 모사)

- **스핀 권한**: 하루 총 **5회** — **무료 1회 + 광고 4회**(각 1회마다 리워드 광고 1편). 횟수(총량·무료수)는 **서버 설정값**으로 추후 변경 가능. FE는 status 응답값을 표시·사용만 한다.
- **상품(전부 에너지)**, 서버 **가중 확률**:
  | 상품 | 확률 |
  |---|---|
  | ⚡100 (잭팟) | 1% |
  | ⚡10 | 10% |
  | ⚡3 | 70% |
  | 꽝 (0) | 19% |
- **당첨 처리**: 서버가 확률로 당첨 상품을 결정하고 **에너지를 지급**한 뒤 결과(당첨 상품 + 표시용 세그먼트 인덱스)를 반환. FE는 받은 결과 칸으로 휠을 애니메이션만 한다(클라가 당첨을 정하지 않음).
- **스핀 정책**: 하루 **첫 1회는 무료**(광고 없이 바로 돌림). **2회차부터는 매 스핀마다 광고를 봐야** 돌릴 수 있다(광고 = 매 스핀의 게이트, "크레딧 적립" 개념 없음). 광고를 끝까지 보면 **즉시 스핀**한다. 어뷰징 방지를 위해 광고는 **AdMob SSV로 검증**(nonce 발급 → 광고 customData=nonce → 서버 검증 후 스핀).
- **에너지 반영**: 리워드 카드와 동일하게 `HudStore.refreshEnergyOnly()`로 HUD 동기화. 혜택존엔 에너지 표시가 없으므로 피드백은 **결과 모달/토스트**.

## 3. 휠 디자인 — "미니멀 2톤" (선택됨)

(비주얼 브레인스토밍 4안 중 C안 채택)
- 8칸, 크림(`#FFF6DF`)·화이트(`#FFFFFF`)·연보라(`#F4F3FA`) 2~3톤 플랫
- 칸 구분선 `#ECEAF5`, **잭팟 칸만 금색 테두리(`#FFB02E`)로 강조**
- 상단 포인터(삼각형, 인디고 `#5B5BD6`), 가운데 원형 버튼(인디고, "GO")
- CTA `"돌리기 · 오늘 N회"` (N = status의 남은 횟수)
- 칸 배치는 시각용(예: 잭팟1·⚡10×2·⚡3×3·꽝×2). **당첨은 서버가 정하고, FE는 당첨 상품과 일치하는 칸 하나를 골라 그 칸이 포인터에 멈추도록 회전**

Mockup: `.superpowers/brainstorm/.../roulette-wheel-designs.html`(C안).

상태 매핑:
- 무료 스핀 가능(첫 회): 버튼 `"돌리기 (무료)"`
- 무료 소진·한도 미도달: 버튼 `"광고 보고 돌리기"`(광고 시청 → 즉시 스핀)
- 일일 한도 도달: 버튼 비활성 `"내일 다시 · 자정 리셋"`
- 회전/적립 중: 버튼 스피너

## 4. 아키텍처

### 4.1 공유 (KMM commonMain)
- `RoulettePrize`(enum 또는 데이터): `JACKPOT_100`, `E10`, `E3`, `MISS` (+ energy 값)
- `RouletteStatus`: `dailyLimit`, `spinsUsedToday`, `freeSpinAvailable`(첫 무료 가능), `remaining`(= dailyLimit−spinsUsedToday), `resetAtKst`, `segments`(표시용)
- `RouletteSpinResult`: `prize`, `segmentIndex`, `awardedEnergy`
- **`RouletteRepository`** 인터페이스: `getStatus()`, `spin()`(무료 첫 스핀), `prepareAdSpin()`(nonce), `spinWithAd()`(광고 후 스핀)
  - `FakeRouletteRepository`(스텁): 로컬 상태 보유. `spin()`/`spinWithAd()`는 §2 확률로 로컬 가중 랜덤 → 결과 반환·카운트 증가. `spin()`은 첫 무료만, `spinWithAd()`는 remaining>0이면 동작. 에너지 실지급 없음.
  - `RemoteRouletteRepository`(후속): 위 BE API 호출. 본 슬라이스 범위 밖(스텁만).
- `RouletteStore`: 상태 보유 + `spin`/`prepareAdSpin`/`spinWithAd` 오케스트레이션(스핀 후 `onEnergyChanged`로 HUD 동기화). `@Throws`로 iOS 노출. (iOS는 suspend-lambda 미지원이라 `prepareAdSpin`+`spinWithAd`를 직접 호출)
- Koin 등록(SharedModule), iOS `KoinHelper` 노출.

### 4.2 Android
- `RouletteViewModel`(또는 store 직접) + `RouletteScreen`/모달 Compose: 휠(Canvas 또는 회전 가능한 그래픽) + 포인터 + 버튼. 회전은 `Animatable<Float>`로 N바퀴 + 목표 각도.
- 광고 게이트 스핀은 `RewardedAdManager`(기존 single)로 광고 표시 후 `store.spinWithAd()` 호출.
- 혜택존 진입점: `BenefitZoneScreen.kt`에 룰렛 카드 추가(탭 → 룰렛 화면/모달). 카드 비주얼은 별도 — 본 스펙은 휠 화면 중심, 카드는 기존 `BenefitInfoCard` 또는 간단 배너형 재사용(구현 계획에서 확정).

### 4.3 iOS (파리티)
- `RouletteView` + VM(SwiftUI): 동일 휠/회전(`rotationEffect` + `withAnimation`). iOS `RewardedAdManager` + 공유 `RouletteStore`/`HudStore`.
- `import CashChatShared` / `import Combine` 준수(KMM 인터롭). Swift 빌드는 **에이전트가 `xcodebuild`로 직접 검증**.

## 5. BE API 계약 (요약 — 상세는 BE 요청 문서)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/roulette/status` | 한도/사용수/무료가능/remaining/세그먼트/resetAtKst |
| POST | `/api/roulette/issue-nonce` | 광고 게이트 스핀용 SSV nonce |
| POST | `/api/roulette/spin` | 무료 첫 스핀 → 가중추첨 → 에너지 지급 → 결과 반환 |
| POST | `/api/roulette/spin-with-ad` | (SSV 검증된 nonce로) 광고 게이트 스핀 → 가중추첨 → 에너지 지급 → 결과 |

## 6. 테스트
- 공유: `FakeRouletteRepository`/`RouletteStore` 단위 테스트 — 확률 가중 분포(주입형 RNG), 무료 첫 스핀/광고 게이트 스핀/한도 상태 전이, `spin`/`spinWithAd` 후 카운트 증가·remaining 감소, 무료 소진·한도 초과 시 예외.
- Android: `:app:assembleDebug` 컴파일 + (선택) 에뮬레이터 휠 회전 육안.
- iOS: shared 빌드 + **에이전트 `xcodebuild` 빌드 검증**.

## 7. 범위 밖 / 인지사항
- 실제 당첨·에너지 지급·확률은 BE 구현 후 동작(스텁은 UI/애니메이션 검증용, 에너지 실지급 없음).
- 혜택존엔 에너지 표시 없음 → 보상 피드백은 결과 모달/토스트, 에너지는 다음 채팅 HUD 반영.
- 룰렛 카드의 최종 비주얼(혜택존 리스트 내 카드)은 구현 계획에서 확정.
- 친구 초대(슬라이스 4)는 별도.

## 8. 산출물
1. 본 설계 spec.
2. **CC-355 통합 BE API 요청 문서**(룰렛 + 기존 모든 BE 필요 API 정리) — `docs/`에 작성·커밋.
3. 구현 계획(writing-plans) → FE 스텁 구현.
