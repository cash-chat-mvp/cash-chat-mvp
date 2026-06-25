# [CC-352] 진화/채팅 보상 — 백엔드 API 요청

> 작성일 2026-06-25 · 요청자 FE · 관련 설계 [`2026-06-25-cc-352-revised-economy-frontend-design.md`](../superpowers/specs/2026-06-25-cc-352-revised-economy-frontend-design.md)
>
> 개정 경제모델(CC-283 R1/R2)에 맞춰 프론트(Android/iOS)를 대응하면서 발견한 **백엔드 보강 요청**이다.
> 프론트는 전방 호환(nullable 필드 / 게이팅)으로 미리 대응했으므로, 아래가 배포되면 **FE 코드 수정 없이** 자동 활성화된다.

---

## 1. (필수) `EvolutionStateResponse`에 보유 경험치(`currentExp`) 추가

### 배경
R2로 진화 시도 비용이 **진화 경험치(exp)** 차감으로 바뀌었으나(`EvolutionService.attempt` → `UserEvolution.spendExp`), 조회 API `GET /api/evolution/me`(`EvolutionStateResponse`)는 현재 **보유 경험치를 노출하지 않는다.** 프론트는 사용자의 현재 경험치·진화 가능 여부(잔액 ≥ 비용)·경험치 진행바를 그릴 수 없다.

### 요청
`EvolutionStateResponse`에 현재 보유 경험치 필드 추가.

```kotlin
// domain/evolution/web/response/EvolutionStateResponse.kt
data class EvolutionStateResponse(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
    val currentExp: Long,        // ← 추가: UserEvolution.exp
)
```
- 출처: `UserEvolution.exp`(`evolution_exp` 컬럼). `EvolutionStateResult`에도 동일 필드 추가 후 `getState`에서 매핑.
- 인증 사용자 본인 기준.

### FE 대응 상태
`EvolutionStateDto.currentExp: Long? = null`로 이미 선언. 필드가 오면 진화 화면의 "보유 경험치", 진화 버튼 활성 조건, 채팅 완료 연출의 경험치 바가 자동 활성화된다.

---

## 2. (필수) 진화 시도 기록 조회 `GET /api/evolution/attempts`

### 배경
프론트는 진화 시도 타임라인 UI(`FeatureFlags.EVOLUTION_HISTORY`)를 위해 `GET /api/evolution/attempts?limit=N`을 호출하도록 이미 구현돼 있으나, 백엔드에 해당 엔드포인트가 없다. 플래그는 현재 `false`로 막혀 있다.

### 요청
`EvolutionController`에 기록 조회 엔드포인트 추가.

```
GET /api/evolution/attempts?limit={1..100, default 20}
```

응답(프론트 `EvolutionAttemptsDto`와 일치해야 함):
```json
{
  "attempts": [
    {
      "success": true,
      "fromLevel": 2,
      "resultLevel": 3,
      "cost": 1200,
      "attemptedAt": "2026-06-25T12:34:56Z"
    }
  ]
}
```
- 정렬: 최신순(`attemptedAt` desc).
- 출처: `EvolutionAttempt` 엔티티(`userId, fromLevel, resultLevel, cost, success` + 생성시각). `attemptedAt`은 `BaseEntity`의 생성시각(ISO-8601).
- 인증 사용자 본인 기록만.

### FE 대응 상태
`EvolutionApi.getAttempts(limit)` / `EvolutionStore.refreshHistory()` 구현 완료. 배포 후 `FeatureFlags.EVOLUTION_HISTORY=true`로 전환하면 활성화.

---

## 3. (선택) 채팅 `done` SSE 이벤트에 보상 페이로드 포함

### 배경
R1로 채팅 정상 완료 시 포인트 +1 / 진화 경험치 +1이 적립된다(`ChatRewardService`, 기본값 `app.chat-reward.chat-reward-pt=1`, `evolution-exp-per-chat=1`). 프론트는 완료 시 "보상 획득 연출"을 표시하는데, 현재 `done` 이벤트가 `[DONE]` 문자열만 전달하므로 **금액을 정책 기본값(+1/+1)으로 하드코딩**하고 있다.

### 요청 (보상액이 가변/구성형이 될 경우에만)
`done` 이벤트 데이터에 실제 적립 금액을 JSON으로 포함:
```json
{ "pointDelta": 1, "expDelta": 1 }
```
- 현재처럼 항상 +1/+1 고정이면 **불필요**. 추후 보상액을 레벨·이벤트별로 가변화할 때 검토.

### FE 대응 상태
done 이벤트에 페이로드가 있으면 그 값을, 없으면 기본값을 표시하도록 설계(전방 호환). 현 시점 FE는 기본값 표시로 충분.

---

## 4. (필수) 길게 누르기 타이밍 보너스 서버 판정

### 배경

진화 화면을 Evolution Lab UI로 개편하면서 길게 누른 뒤 특정 타이밍에 손을 떼면 성공 확률 보너스를 주는 인터랙션을 추가한다. 성공 확률은 경제 모델과 직접 연결되므로 클라이언트 계산만 신뢰할 수 없다.

타이밍을 놓쳐도 진화는 취소하지 않고 기존 기본 확률로 시도한다. 누른 뒤 0.6초 이전에 손을 떼는 경우에만 요청 없이 취소한다.

### 판정 정책

| 등급 | 성공 확률 보너스 |
|---|---:|
| `PERFECT` | +10%p |
| `GREAT` | +5%p |
| `NORMAL` | +0%p |

- Perfect: 서버가 정의한 중앙 10% 구간
- Great: 중앙 24% 구간에서 Perfect 제외
- Normal: 나머지 유효 구간
- 최종 성공 확률 상한: 100%

### 요청 계약 제안

기존 `POST /api/evolution/attempt` 요청에 타이밍 증빙을 추가한다.

```json
{
  "idempotencyKey": "uuid",
  "timing": {
    "sessionId": "server-issued-session-id",
    "releasedAtMs": 1432
  }
}
```

클라이언트가 임의의 `PERFECT` 등급이나 최종 확률을 직접 보내는 방식은 사용하지 않는다. 서버가 발급한 세션과 시작 시각을 기준으로 해제 시점을 검증한다.

권장 사전 API:

```http
POST /api/evolution/timing-sessions
```

```json
{
  "sessionId": "opaque-id",
  "serverStartedAt": "2026-06-26T00:00:00Z",
  "minimumHoldMs": 600,
  "cycleDurationMs": 1800
}
```

### 응답 계약 제안

```json
{
  "success": true,
  "fromLevel": 2,
  "resultLevel": 3,
  "cost": 1200,
  "timingGrade": "PERFECT",
  "timingBonusRate": 0.10,
  "baseSuccessRate": 0.65,
  "finalSuccessRate": 0.75
}
```

- 등급, 보너스와 최종 확률은 서버 응답을 최종값으로 사용한다.
- 동일 idempotency key 재시도는 최초 판정 결과를 반환해야 한다.
- 만료·변조된 timing session은 진화 비용을 차감하지 않고 명시적 에러를 반환한다.

### FE 대응 방침

- 백엔드 지원 전에는 타이밍 보너스를 기능 플래그로 숨긴다.
- 기존 기본 확률 진화 시도는 유지한다.
- 백엔드 배포 후 Android/iOS에서 같은 등급 구간과 문구를 활성화한다.

---

## 우선순위

| 순위 | 항목 | 영향 |
|---|---|---|
| P0 | #1 `currentExp` | 진화 UX 핵심 — 보유 경험치/가능 여부/경험치 바 |
| P0 | #4 타이밍 보너스 서버 판정 | 실제 성공 확률과 경제 모델 무결성 |
| P1 | #2 `/attempts` | 진화 기록 타임라인 기능 활성 |
| P2 | #3 done 보상 페이로드 | 보상액 가변화 시에만 |
