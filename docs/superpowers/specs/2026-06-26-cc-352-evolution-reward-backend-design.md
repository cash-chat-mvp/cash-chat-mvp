# CC-352 — 진화/채팅 보상 백엔드 보강 설계

> 작성일 2026-06-26 · 담당 BE · 관련 요청 [Confluence CC-352 API](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/26017793/CC-352+API)
>
> CC-352 FE(PR #217, `e54604c`)가 개정 경제모델(CC-283 R1/R2) 대응 중 발견한 **백엔드 API 보강 4건**을 구현한다.
> 계약(요청/응답 형태·타이밍 판정 수치)은 **이미 FE가 확정**했으므로, 백엔드는 FE의 기존 구현과 **수치까지 일치**하도록 맞춘다.

---

## 0. 계약 소스 오브 트루스 (FE)

머지된 FE 코드가 계약의 정본이다. 백엔드는 아래와 정확히 일치해야 한다.

| 계약 요소 | FE 정의 위치 |
| --- | --- |
| `EvolutionStateDto.currentExp: Long?` | `shared/.../evolution/EvolutionApi.kt` |
| `EvolutionAttemptDto`(timingGrade/bonus/base/final) | 동 |
| `EvolutionAttemptsDto` / `EvolutionAttemptRecordDto` | 동 |
| `TimingSessionDto{sessionId, serverStartedAt, minimumHoldMs, cycleDurationMs}` | 동 |
| `TimingAttempt{sessionId, releasedAtMs}` | 동 |
| 등급 경계·보너스(`localTimingGrade`, `TimingWindow`, `TimingGrade`) | `shared/.../evolution/EvolutionTiming.kt` |
| `position = (releasedAtMs % cycleDurationMs) / cycleDurationMs` | `feature/chat/evolution/EvolutionViewModel.kt` |
| done 완료 판정 `event=="done" \|\| data=="[DONE]"` | `shared/.../chat/ChatApi.kt` |

**FE가 하드코딩한 타이밍 수치(서버가 반드시 맞춰야 함):**

```kotlin
// EvolutionTiming.kt
enum class TimingGrade(bonusRate) { NORMAL(0.0), GREAT(0.05), PERFECT(0.10) }
TimingWindow(perfectStart=0.45f, perfectEnd=0.55f, greatStart=0.38f, greatEnd=0.62f)
fun localTimingGrade(position) =
    position in 0.45..0.55 -> PERFECT
    position in 0.38..0.62 -> GREAT
    else                   -> NORMAL
```

`timing-sessions` 응답은 `minimumHoldMs`/`cycleDurationMs`만 내려주고 **경계값은 내려주지 않는다.**
따라서 경계(0.45/0.55/0.38/0.62)는 FE·BE가 공유하는 상수이며, 서버는 이 값으로 판정해야 FE 예측과 결과가 일치한다.

---

## 1. (P0) `EvolutionStateResponse`에 `currentExp` 추가

### 변경

- `EvolutionStateResult`에 `currentExp: Long` 추가.
- `EvolutionService.getState`에서 `evo.exp` 매핑.
- `EvolutionStateResponse`에 `currentExp: Long` 추가 + `from()` 매핑.

```kotlin
data class EvolutionStateResponse(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
    val currentExp: Long,   // ← UserEvolution.exp
)
```

- 출처: `UserEvolution.exp`(`evolution_exp` 컬럼). 인증 사용자 본인.
- FE는 `currentExp: Long? = null`이라 non-null Long 반환 시 자동 노출(전방 호환).

### 테스트

- `getState`가 `currentExp = evo.exp`를 반환한다.
- 컨트롤러 응답 JSON에 `currentExp` 필드가 포함된다.

---

## 2. (P1) `GET /api/evolution/attempts?limit={1..100, default 20}`

### 변경

- `EvolutionAttemptRepository`에 최신순 페이징 조회 추가:
  `fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<EvolutionAttempt>`
- `EvolutionService.getAttempts(userId, limit): List<EvolutionAttemptRecordResult>` 추가.
- `EvolutionController`에 엔드포인트 추가. `limit`은 `@Min(1) @Max(100)`, 기본 20.
- 응답 DTO `EvolutionAttemptsResponse(attempts: List<EvolutionAttemptRecordResponse>)`.

```json
{ "attempts": [
  { "success": true, "fromLevel": 2, "resultLevel": 3, "cost": 1200,
    "attemptedAt": "2026-06-25T12:34:56Z" }
]}
```

- 정렬: `createdAt` desc(최신순).
- `attemptedAt`: `BaseEntity` 생성시각을 **ISO-8601 UTC(`...Z`)** 로 직렬화.
- 인증 사용자 본인 기록만.

### 테스트

- 본인 기록만 최신순으로 limit 개수만큼 반환.
- limit 경계(0, 101)는 400.
- `attemptedAt` 포맷이 ISO-8601 UTC.

---

## 3. (P2, forward-prep) `done` SSE 이벤트에 보상 페이로드

### 결정 — 진행하되 깨짐 없는 방식

`done` 이벤트의 **data를 `[DONE]` → JSON으로 전환**하고 **event 이름 `done`은 유지**한다.

- 모든 실 클라이언트(PR #189 이후)는 `event.event == "done"`(이름)으로 완료를 판정하므로
  data가 JSON이어도 OR 단락평가로 **이름 매칭이 먼저** → 토큰으로 새지 않고 완료로 소비된다.
- `[DONE]` 리터럴만 의존하는 구버전 클라는 존재하지 않는다(둘은 함께 도입됨).
- 별도 `event: reward`는 **금지** — 현재/구 FE 파서의 `else` 분기에서 JSON이 말풍선 텍스트로 렌더링된다.

### 변경

- `STREAM_DONE_DATA = "[DONE]"` 대신 정산된 보상 델타 JSON을 done 이벤트 data로 방출:
  `{ "pointDelta": <chatRewardPt>, "expDelta": <evolutionExpPerChat> }`
- 값 출처: `ChatRewardProperties.chatRewardPt`, `evolutionExpPerChat`(현재 +1/+1 고정).
- `asChatSseEvents`가 보상 델타를 인자로 받도록 시그니처 확장(보상 컨텍스트 주입). 호출부(`ChatService`)에서 정산 결과/설정값을 전달.

### 비고

- 현재 FE는 done 페이로드를 **아직 파싱하지 않고** `RewardEarned`를 +1/+1로 하드코딩한다.
  따라서 본 변경은 **forward-prep**이며, FE 후속(파싱 1줄)으로 실제 활성화된다.
- 보상이 가변화(레벨/이벤트별)될 때 비로소 사용자에게 정확한 값이 표시된다.

### 테스트

- 정상 완료 시 done 이벤트 data가 `{"pointDelta":1,"expDelta":1}` JSON.
- event 이름은 여전히 `done`. 실패 시엔 done 미방출(기존 `error` 동작 유지).

---

## 4. (P0) 길게 누르기 타이밍 보너스 서버 판정

### 4.1 타이밍 세션 발급 — `POST /api/evolution/timing-sessions`

응답(`TimingSessionDto` 일치):

```json
{ "sessionId": "opaque-uuid",
  "serverStartedAt": "2026-06-26T00:00:00Z",
  "minimumHoldMs": 600,
  "cycleDurationMs": 1800 }
```

- `TimingSessionStore`(인메모리, `ConcurrentHashMap<String, Session>`):
  `Session(userId, serverStartedAt: Instant, expiresAt: Instant)`.
- **1회용**: attempt에서 consume 시 즉시 제거(성공/실패 무관 소비).
- **만료**: 발급 시각 + `sessionTtl` 후 무효. 조회·소비 시 만료 검사. (단명 데이터 — 백그라운드 청소는 선택, 우선 lazy 만료로 충분.)
- 인증 사용자 본인 세션. `sessionId`는 UUID 등 추측 불가한 opaque 값.
- 운영: 단일 인스턴스 배포 전제. 재시작 시 진행 중 세션 유실 → FE가 "미지원→기본 확률"로 폴백(이미 구현).

### 4.2 진화 시도 확장 — `POST /api/evolution/attempt`

요청에 `timing` 선택 필드 추가(nullable → 레거시 호환):

```json
{ "idempotencyKey": "uuid",
  "timing": { "sessionId": "server-issued-id", "releasedAtMs": 1432 } }
```

- `EvolutionAttemptRequest`에 `timing: TimingAttemptRequest?`(`@Valid` 중첩). null이면 기존 기본 확률 경로.

### 4.3 판정 로직 — `EvolutionTimingJudge`

1. 세션 조회 + 소비. 없거나 만료·타 사용자면 **`InvalidTimingSessionException`**(비용 차감 없이 명시적 에러).
2. **변조 검증**: `releasedAtMs ≤ (now − serverStartedAt).toMillis() + clockSkewTolerance`. 초과 시 거부(차감 없음).
   - 음수 `releasedAtMs` 거부. `< minimumHoldMs`는 FE에서 이미 취소하지만 서버도 NORMAL 처리(보너스 0)로 방어.
3. `position = (releasedAtMs % cycleDurationMs) / cycleDurationMs.toDouble()`
4. 등급: `[0.45,0.55] → PERFECT(+0.10)`, `[0.38,0.62] → GREAT(+0.05)`, else `NORMAL(+0.0)`.
5. `finalSuccessRate = min(1.0, baseSuccessRate + bonus)`.
6. `probabilityRoller.succeeds(finalSuccessRate)`로 판정.

경계·보너스·`minimumHoldMs`·`cycleDurationMs`·`sessionTtl`·`clockSkewTolerance`는 `EvolutionProperties.timing` 설정 블록으로 분리(기본값은 FE 하드코딩과 동일).

### 4.4 서비스 통합 — `EvolutionService.attempt`

- 시그니처를 `attempt(userId, idempotencyKey, timing: TimingAttemptCommand?)`로 확장.
- timing이 있으면 판정 결과(grade/bonus/base/final)를 산출해 `succeeds(finalSuccessRate)` 사용,
  없으면 기존처럼 `succeeds(rule.successRate)`.
- 락 순서(user_evolution → user_energy) 및 멱등 키 선조회 로직은 유지.
- 세션 consume은 **비용 차감/판정 전에** 수행(1회용 보장). 단, 멱등 재시도(동일 키 기존 결과 반환) 시에는 세션을 다시 소비하지 않는다.

### 4.5 멱등 재시도 시 타이밍 결과 보존 — 엔티티 컬럼 추가

`EvolutionAttempt`에 판정 필드 컬럼 추가(전부 nullable — 레거시/타이밍 미사용 시 null):

```kotlin
@Column(name = "timing_grade") @Enumerated(EnumType.STRING) val timingGrade: TimingGrade? = null,
@Column(name = "timing_bonus_rate") val timingBonusRate: Double? = null,
@Column(name = "base_success_rate") val baseSuccessRate: Double? = null,
@Column(name = "final_success_rate") val finalSuccessRate: Double? = null,
```

- 동일 idempotencyKey 재요청은 저장된 판정 필드까지 그대로 반환(최초 판정 100% 재현).
- 백엔드 도메인용 `TimingGrade` enum 신설(FE와 동일 의미: NORMAL/GREAT/PERFECT + bonusRate).

### 4.6 응답 — `EvolutionAttemptResponse`

`EvolutionAttemptResult` / `EvolutionAttemptResponse`에 타이밍 필드 추가(전부 nullable):

```json
{ "success": true, "fromLevel": 2, "resultLevel": 3, "cost": 1200,
  "timingGrade": "PERFECT", "timingBonusRate": 0.10,
  "baseSuccessRate": 0.65, "finalSuccessRate": 0.75 }
```

- 레거시(timing 없는) 시도는 타이밍 필드 null.

### 4.7 에러 처리

- `InvalidTimingSessionException`(만료/없음/타 사용자/변조) → `EvolutionExceptionHandler`에서 명시적 에러 코드로 매핑. **비용 차감 없음**(판정/차감 이전에 실패).
- FE 폴백: 세션 미지원(404/405)·5xx·네트워크 오류 시 기본 확률 시도(이미 구현). 본 에러는 4xx 명시 코드.

### 테스트

- position→grade 매핑(경계 포함: 0.45, 0.55, 0.38, 0.62, 0.0, 0.999) FE `localTimingGrade`와 일치.
- `finalSuccessRate` 상한 1.0 클램프.
- 변조(`releasedAtMs` > 경과시간) 거부 + 비용 미차감.
- 세션 1회용: 같은 세션 2회 소비 시 두 번째 거부.
- 세션 만료 거부. 타 사용자 세션 거부.
- 멱등 재시도가 timing 필드까지 동일 반환.
- 레거시 attempt(timing=null) 기존 동작 유지.

---

## 5. 영향 범위 / 비범위

**변경 파일(예상):**
- evolution: `EvolutionStateResponse`, `EvolutionResults`, `EvolutionService`, `EvolutionController`,
  `EvolutionAttemptRequest`, `EvolutionAttemptResponse`, `EvolutionAttempt`(entity),
  `EvolutionAttemptRepository`, `EvolutionProperties`, `EvolutionExceptionHandler`,
  신규: `TimingSessionStore`, `EvolutionTimingJudge`, `TimingGrade`(domain enum),
  `TimingSessionResponse`, exception.
- chat: `ChatSseEvents`(done 페이로드), 호출부 `ChatService`.

**비범위:**
- FE 변경(별도 PR). 본 작업은 BE 계약 충족까지.
- 다중 인스턴스 세션 공유(현재 단일 인스턴스 전제). 추후 필요 시 Redis 등으로 교체.
- 보상 가변화 로직 자체(#3은 전달 통로만 마련).

---

## 6. 작업 순서 (TDD, 항목별 /code-review)

1. **#1 currentExp** — 가장 작고 독립적.
2. **#2 /attempts** — 조회 API.
3. **#4 타이밍 보너스** — 세션 스토어 → 판정 → 서비스/응답 통합 → 멱등 컬럼.
4. **#3 done 페이로드** — SSE 보상 전달.

각 항목 완료 시 Kotest 테스트 green 확인 + `/code-review`. 전체 완료 후 Confluence 작업로그 갱신.
