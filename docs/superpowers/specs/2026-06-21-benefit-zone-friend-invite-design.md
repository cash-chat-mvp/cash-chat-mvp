# 친구 초대 설계 (슬라이스 4, FE-first 스텁)

- **날짜**: 2026-06-21
- **브랜치**: `feature/benefit-invite` (← `feature/CC-355`에서 분기, 룰렛·리워드 카드와 동일 방식, 로컬 머지 회수)
- **관련**: 룰렛 `2026-06-21-benefit-zone-roulette-design.md`, BE API 요청 `docs/planning/be-api-requests-cc355.md` §5

## 1. 배경 & 접근

혜택존에 "친구 초대"(추천 코드)를 신규 추가한다. FE·BE·딥링크 어디에도 기존 자산이 없다. 룰렛과 동일하게 **FE 먼저 스텁**으로 구현하고, BE API는 요청 문서로 계약화한다. `InviteRepository` 인터페이스 뒤 `FakeInviteRepository`로 잠정 동작하고, BE 준비 시 `RemoteInviteRepository`로 교체(인터페이스 불변, `PointsRepository` 격리 패턴).

## 2. 메커니즘 (서버가 진실)

- **방식**: 추천 코드. 각 사용자에게 고유 코드(예: `ABC123`) 부여. 딥링크 미사용.
- **공유**: 혜택존 '친구 초대' 화면에서 내 코드 + **OS 공유시트**(Android `Intent.ACTION_SEND` / iOS `ShareLink`)로 초대 메시지 공유.
- **입력**: 친구가 추천 코드를 입력 — **온보딩(가입 시) + 혜택존** 양쪽.
- **보상**(추천 성공 = 친구가 코드 입력·가입 완료 시, 서버가 결정·지급): **초대자 +코인**, **가입자 +에너지**. 금액·한도(초대 최대 N명, 1회 redeem)는 **서버 설정값**.
- **검증(서버)**: 자기 코드 입력 금지, 1인 1회만 redeem, 신규/적격 계정만, 코드 유효성. FE는 표시·입력만.

## 3. 화면 디자인 — "그라데이션 히어로" (선택됨)

(비주얼 브레인스토밍 3안 중 A안 채택) 혜택존 '친구 초대' 화면:
- 상단 **그라데이션 히어로**(보라→핑크 `#7C6CFF → #FF5E8A`): "친구 초대하고 코인 받기" + "친구가 가입하면 나는 🪙+N, 친구는 ⚡+N!"(서버값 표시)
- **내 코드 카드**: 큰 코드(`ABC123`) + 복사 칩 + `친구에게 공유하기`(공유시트) + "지금까지 N명 초대" 통계
- **추천 코드 입력 카드**: 입력란 + `에너지 받기` 버튼(적격 기간 안내)
- 혜택존 리스트의 '친구 초대' 진입 카드(`BenefitInfoCard`) 탭 → 이 화면(다이얼로그/시트 또는 라우트)

Mockup: `.superpowers/brainstorm/.../invite-screen-designs.html`(A안).

상태 매핑:
- 코드 미입력 가능: 입력 카드 노출, `에너지 받기` 활성
- 이미 redeem함/적격 아님: 입력 카드 dim + 안내("이미 추천 코드를 사용했어요" 등 서버 사유)
- 공유/입력 진행 중: 버튼 스피너

## 4. 아키텍처

### 4.1 공유 (KMM commonMain)
- `InviteStatus`: `myCode: String`, `invitedCount: Int`, `redeemAvailable: Boolean`(본인이 추천코드 입력 가능 여부), `rewardCoin: Int`(초대자 보상, 표시용), `rewardEnergy: Int`(가입자 보상, 표시용)
- `RedeemResult`: `success: Boolean`, `awardedEnergy: Int`, `message: String?`(실패 사유)
- **`InviteRepository`** 인터페이스: `getInviteStatus()`, `redeemCode(code: String): RedeemResult`. iOS 호출 → `@Throws`
  - `FakeInviteRepository`(스텁): 고정 코드(`ABC123`)·카운트 보유, `redeemCode`는 형식 검증 후 성공/실패 모사(예: 자기 코드면 실패). 실제 적립 없음.
  - `RemoteInviteRepository`(후속): BE API 호출. 범위 밖(스텁만).
- `InviteStore`: 상태 보유 + `redeem` 오케스트레이션(성공 시 `onRewardChanged`로 잔액/HUD 동기화). Koin 등록, iOS `KoinHelper`/`FlowCollector` 노출.

### 4.2 Android
- `InviteViewModel` + `InviteScreen`(다이얼로그 또는 라우트, §3 디자인). 공유는 `Intent.ACTION_SEND`. 입력→`store.redeemCode`.
- 혜택존 진입 카드(`BenefitZoneScreen.kt`)에 '친구 초대' 카드 추가.
- **온보딩**: `OnboardingScreen`에 선택적 "추천 코드 입력" 필드. 가입 전이므로 코드를 **pending 저장 → 가입 완료 후 적용**(스텁은 즉시 성공 모사). (별도 task, 분리)

### 4.3 iOS (파리티)
- `InviteView` + VM(SwiftUI). 공유는 `ShareLink`. 혜택존 진입 카드 + 온보딩 필드.
- `import CashChatShared`/`import Combine`. Swift 빌드는 **에이전트가 `xcodebuild`로 직접 검증**.

## 5. BE API 계약 (요약 — 상세는 BE 요청 문서 §5)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/invite/me` | 내 추천 코드·초대 성공 수·redeem 가능 여부·보상값 |
| POST | `/api/invite/redeem` | `{code}` → 검증(자기/중복/적격) → 초대자 코인·본인 에너지 지급 |

- 온보딩 입력은 가입 토큰 발급 후 `redeem` 호출 또는 가입 페이로드에 코드 동봉 — BE 협의 항목으로 문서화.

## 6. 테스트
- 공유: `FakeInviteRepository`/`InviteStore` — `getInviteStatus` 반환, `redeemCode` 성공/형식오류/자기코드 실패 전이, 성공 시 `onRewardChanged` 호출.
- Android: `:app:assembleDebug`. iOS: shared 빌드 + **에이전트 `xcodebuild`**.

## 7. 범위 밖 / 인지사항
- 온보딩 코드 입력은 가입 전이라 "pending 후 적용" — 스텁 단순화(즉시 성공), 실제 타이밍은 BE 계약 명시.
- 보상 금액·한도·적격 규칙은 전부 서버. FE는 표시·입력만.
- 딥링크/초대 링크는 범위 밖(추후 필요 시 별도).

## 8. 산출물
1. 본 설계 spec.
2. `be-api-requests-cc355.md` **§5 친구 초대** 채워 넣기(현재 "후속 예고").
3. 구현 계획(writing-plans) → FE 스텁 구현.
