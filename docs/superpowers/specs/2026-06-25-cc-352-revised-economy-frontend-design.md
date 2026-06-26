# CC-352 개정 경제모델(R1/R2) 프론트 대응 — 설계

> 작성일 2026-06-25 · 브랜치 `feature/CC-352` · 대상 Android(`:app`) + iOS(`CashChatIOS`) + 공유(`:shared`)

## 1. 배경

브랜치 `feature/CC-352`에는 백엔드 개정 경제모델(CC-283, PR #214)이 이미 머지되어 있다.
근거 문서: [`docs/service-operation.md`](../../service-operation.md).

바뀐 백엔드 계약(as-is):

- **R1 — 채팅 완료 보상**: 채팅 정상 완료 시 밥(energy) −1 정산 + 포인트 **+1**(`CHAT_REWARD`) + **진화 경험치(exp) +1**. 실패/취소 시 예약 밥 환불, 보상 미적립.
- **R2 — 진화 시도 비용**: 진화 시도 비용을 **포인트가 아니라 진화 경험치(exp)로 차감**. 잔액 부족 시 새 에러 `INSUFFICIENT_EVOLUTION_EXP`(HTTP 409).
- **광고 보상**: 리워드 광고 1회 = 포인트가 아니라 **밥(energy) 3** 적립. (프론트는 이미 energy 재조회로 대응 중 — 본 작업 범위 아님.)

프론트는 아직 R1/R2 이전 모델(진화 비용 = 코인) 기준 문구·로직을 갖고 있어 정정이 필요하다.

### 설계 원칙

1. **전방 호환(forward-compatible)**: 백엔드가 나중에 새 필드(`currentExp`)·엔드포인트(`/api/evolution/attempts`)를 배포하면 **프론트 코드 수정 없이** 자동으로 동작하도록 한다. nullable 필드를 미리 정의하고, 값이 존재할 때만 UI를 노출한다.
2. **Android·iOS 동시 대응**, 각 플랫폼 기존 패턴을 따른다.
3. 백엔드에 필요한 변경은 코드로 손대지 않고 **`docs/issues/`에 요청 문서로 남긴다**.

## 2. 대응 범위 (요약)

| # | 항목 | 플랫폼 | BE 의존 |
|---|---|---|---|
| A | 진화 비용 통화 라벨 정정 (코인 🪙 → 경험치 ⭐) | Android·iOS | 없음 |
| B | 새 에러 `INSUFFICIENT_EVOLUTION_EXP` 처리 | shared·Android·iOS | 없음 |
| C | 현재 보유 경험치 표시 (전방 호환) | shared·Android·iOS | `currentExp` 배포 시 자동 활성 |
| D | 진화 기록(`/api/evolution/attempts`) | (변경 없음) | 엔드포인트 신설 시 자동 활성 |
| E | 채팅 완료 보상 획득 연출 (충실도 B) | shared·Android·iOS | 코인 카운트업은 즉시, exp 바는 `currentExp` 배포 시 |
| F | 채팅 화면 출석 UI + 자동 출석 제거 | Android·iOS | 없음 |
| G | 백엔드 요청 문서화 | docs | — |

## 3. 상세 설계

### A. 진화 비용 통화 라벨 정정 — *각별 대응 영역*

R2로 진화 비용 통화가 **코인 → 진화 경험치(exp)** 로 바뀌었으므로 문구·아이콘을 정정한다.

- **Android** `feature/chat/evolution/EvolutionScreen.kt`
  - `StatRow("다음 진화 비용", "🪙 …")` → `"⭐ %,d 경험치"` 형태
  - 실패 메시지 `"이번엔 실패했어요 (-%,d 코인)…"` → `"(-%,d 경험치)"`
  - 진화 기록 항목 `"🪙${record.cost} · …"` → `"⭐${record.cost} · …"`
- **iOS** `EvolutionScreen.swift`
  - `Text("비용 🪙\(cost)")` → `Text("비용 ⭐\(cost) 경험치")`

성공 시 "밥도 보너스 충전" 문구(진화 성공 시 energy 부스트)는 백엔드 `applyPostEvolutionBoost`가 유지하므로 그대로 둔다.

### B. 새 에러코드 `INSUFFICIENT_EVOLUTION_EXP` 처리

- **shared** `core/network/ApiException.kt`: 상수 추가
  ```kotlin
  const val INSUFFICIENT_EVOLUTION_EXP = "INSUFFICIENT_EVOLUTION_EXP"
  ```
  기존 `INSUFFICIENT_POINTS`는 하위호환을 위해 남겨둔다(다른 도메인에서 사용 가능).
- **Android** `EvolutionViewModel.attempt()`의 `ApiException` 분기에 추가:
  ```kotlin
  ApiException.INSUFFICIENT_EVOLUTION_EXP,
  ApiException.INSUFFICIENT_POINTS -> "경험치가 부족해요. 채팅으로 모아볼까요?"
  ```
- **iOS** `EvolutionViewModel.attempt()`의 `catch`를 `ApiException` 코드 분기로 확장해 동일 메시지 노출. (현재 일반 메시지만 표시 → 코드 분기 추가)

### C. 현재 보유 경험치 표시 (전방 호환)

백엔드 `EvolutionStateResponse`는 현재 `exp`를 노출하지 않는다(§G에서 요청). 프론트는 미리 nullable 필드를 둔다.

- **shared** `evolution/EvolutionApi.kt` `EvolutionStateDto`에 필드 추가:
  ```kotlin
  val currentExp: Long? = null   // BE 미배포 시 null → UI 미표시. 배포 시 자동 노출.
  ```
  `@Serializable` + 기본값이라 백엔드가 필드를 보내지 않아도 역직렬화 안전(전방 호환).
- **shared** `hud/HudStore.kt` `HudState`에 `exp: Long? = null` 추가, `refreshNow()`에서 `evolution.currentExp` 매핑.
- **Android/iOS 진화 화면**: `currentExp != null`이면 "보유 경험치 ⭐N" 표시 + 진화 버튼 활성 조건에 `currentExp >= nextAttemptCost`를 추가. `currentExp == null`이면 **기존 동작 유지**(cost 존재 여부로만 게이팅).
  - iOS `canAttempt`: `isLoaded && !isAttempting && !isMaxLevel && nextCost != nil && (currentExp == nil || currentExp >= nextCost)`

### D. 진화 기록 (`/api/evolution/attempts`)

프론트 코드(`EvolutionApi.getAttempts`, `EvolutionStore.refreshHistory`, `FeatureFlags.EVOLUTION_HISTORY=false`)는 이미 존재한다. 백엔드 엔드포인트만 없다.

- **프론트 변경 없음.** 플래그는 `false` 유지.
- 백엔드 엔드포인트 신설을 §G 요청 문서에 기재. 배포 후 플래그를 켜면 자동 동작(전방 호환).

### E. 채팅 완료 보상 획득 연출 (충실도 B — 디커플드)

채팅 정상 완료(`ChatStore`의 `streamCompletedCount` 증가, `ChatStreamEvent.Done`) 시점에 보상 획득 연출을 표시한다. 두 영역이 **독립적으로** 동작한다(좌표 커플링 없음).

1. **응답 버블 파티클**: 마지막 어시스턴트 버블 위로 별/코인 파티클 6~8개가 작게 튀어 **위쪽(HUD 방향)으로 흘러오르며 페이드**. (정확한 HUD 좌표 추적 없음.)
2. **상단 HUD 상승**: 같은 타이밍에 코인 칩이 **카운트업**, 경험치 바가 **fill 애니메이션**.

신호 흐름:
- `ChatStore`는 이미 `streamCompletedCount: StateFlow<Int>`를 노출한다. 이를 보상 연출 트리거로 재사용한다(별도 신호 추가 불필요).
- **Android** `ChatViewModel`: `streamCompletedCount`를 관측해 일회성 보상 이벤트(`SharedFlow<Unit>` 또는 `StateFlow<Long>` 틱)를 노출. `ChatScreen`이 이를 받아 오버레이 파티클 컴포저블을 트리거하고, HUD 코인/경험치 갱신은 기존 `hudStore.refresh()` 경로로 값을 받아 카운트업/fill 애니메이션 처리.
- **iOS** `ChatViewModel`: `streamCompletedCount` 수집 지점(`collectStreamCompleted` 류, 현재 `refreshEnergyOnly()` 호출 위치)에서 `@Published var rewardBurstTick` 증가. `ChatScreen.swift`가 `.onChange`로 파티클 오버레이를 트리거하고, HUD 칩/바가 값 변화에 애니메이션.

연출 금액 표시:
- 정책 기본값(+1 포인트 / +1 경험치)을 사용한다. 금액은 `app.chat-reward` 기본값과 일치.
- 향후 보상액이 가변/구성형이 되면 done 이벤트로 실제 금액을 받도록 §G에 요청 기재. 그 전까지는 기본값 표시(전방 호환).

경험치 바 의존성:
- 코인 카운트업은 즉시 동작(`POINT_BALANCE=true`, `GET /api/points/me` 배포됨).
- 경험치 바 fill은 `currentExp`(§C, §G)가 있어야 진행률 계산 가능. 배포 전에는 바 대신 경험치 칩 `+1` 펄스만 표시.

연출 컴포넌트는 **단일 책임**으로 분리한다:
- Android: `RewardBurstOverlay`(Composable, 파티클만), HUD 카운트업은 HUD 칩 컴포저블 내부 애니메이션.
- iOS: `RewardBurstOverlay`(View, 파티클만), HUD 애니메이션은 HUD 뷰 내부.

### F. 채팅 화면 출석 UI + 자동 출석 제거

출석은 혜택존(BenefitZone)에 완전한 체크인 UI가 있으므로(Android `AttendanceWidget`/"출석 도장 찍기", iOS `AttendanceViewModel.checkIn()`), 채팅에서 완전히 제거해도 고아가 발생하지 않는다.

- **Android**
  - `ChatScreen.kt`: 상단바 캘린더 `IconButton`(출석 캘린더), `showAttendance` 상태, 출석 `ModalBottomSheet` 제거.
  - `ChatViewModel.kt`: `attendanceApi` 의존성, `_attendance` 상태, 채팅 진입 시 자동 체크인 로직 제거. DI(`AppModule`)에서 `ChatViewModel`의 `attendanceApi` 주입 제거.
  - `feature/chat/AttendanceSheet.kt`: 채팅 외에서 참조가 없으면 파일 삭제(혜택존은 `rewards/`의 별도 위젯 사용).
- **iOS**
  - `ChatScreen.swift`: `showAttendance` 상태, `.sheet`, 출석 버튼, `AttendanceSheet` 구조체 제거.
  - `ChatViewModel.swift`: 출석 관련 `@Published`(`attendanceMonth/Streak/CheckedDays/TodayChecked`, `checkInToast`), `attendanceStore`, 자동 체크인·수집 로직 제거.
- 참조 무결성: 삭제 후 컴파일 에러가 나는 잔여 참조(import 등)를 정리한다.

### G. 백엔드 요청 문서

`docs/issues/2026-06-25-cc-352-evolution-be-api-requests.md`에 다음을 기재한다:

1. `EvolutionStateResponse`에 `currentExp: Long`(보유 진화 경험치) 추가.
2. `GET /api/evolution/attempts?limit=N` 신설 — 응답이 프론트 `EvolutionAttemptsDto`(`attempts: [{ success, fromLevel, resultLevel, cost, attemptedAt }]`)와 일치.
3. (선택) 채팅 `done` SSE 이벤트에 보상 페이로드(`pointDelta`, `expDelta`) 포함 — 보상액 가변 대비.

## 4. 테스트 전략

- **shared (commonTest, Kotest/kotlin.test)**:
  - `EvolutionStateDto` 역직렬화: `currentExp` 없는 JSON → null, 있는 JSON → 값 매핑(전방 호환 검증).
  - `ApiException`/`parseApiError`: `INSUFFICIENT_EVOLUTION_EXP` 코드 파싱.
  - `HudStore.refreshNow()`: `currentExp` → `HudState.exp` 매핑.
- **Android**: `EvolutionViewModel` 에러 분기 단위 테스트(가능 시), 빌드 `:app:assembleDebug`.
- **iOS**: `xcodebuild` 빌드 + 시뮬레이터 스모크(진화 화면 라벨, 채팅 완료 연출 육안 확인).
- 회귀: 출석 제거 후 채팅 진입/스트리밍 정상 동작, 혜택존 출석 체크인 정상.

## 5. 비고 / 영향

- **비용 관점**: 본 작업은 AI 호출량을 늘리지 않는다(연출·문구·표시 변경). 채팅 완료 보상은 백엔드가 이미 적립하며, 프론트는 표시만 한다.
- **하위호환**: `INSUFFICIENT_POINTS` 상수·문구는 제거하지 않고 새 코드와 함께 처리해 과도기 안전성 확보.
- **전방호환 트리거 정리**: `currentExp` 배포 → exp 표시·바 자동 활성 / `/attempts` 배포 → `EVOLUTION_HISTORY` 플래그 on 시 기록 활성.
