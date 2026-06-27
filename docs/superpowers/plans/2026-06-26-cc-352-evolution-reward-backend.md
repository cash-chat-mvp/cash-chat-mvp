# CC-352 진화/채팅 보상 백엔드 보강 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CC-352 FE가 요청한 백엔드 보강 4건(보유 경험치 노출, 진화 기록 조회, 길게누르기 타이밍 보너스 서버 판정, done SSE 보상 페이로드)을 구현한다.

**Architecture:** 기존 `domain/evolution` 레이어드 구조(Controller → Service → Repository/Entity, `*Result`/`*Response` 분리, `@WebMvcTest` 웹슬라이스 + 순수 `FunSpec` 서비스 테스트)를 그대로 따른다. 타이밍 세션은 인메모리 1회용 스토어로 관리하고, 판정 수치는 FE 하드코딩 상수(0.45/0.55/0.38/0.62, +10/+5%p)와 일치시킨다. done SSE는 event 이름 `done`을 유지하고 data만 `[DONE]`→보상 JSON으로 바꾼다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Jakarta Validation, Kotest(FunSpec) + mockito-kotlin, `@WebMvcTest`(MockMvc), Reactor(SSE), Jackson.

## Global Constraints

- 패키지 루트: `com.wnl.cashchat.api`. 대상 도메인: `domain/evolution`, `domain/chat`.
- 진화 타이밍 판정 수치는 **FE와 정확히 일치**: PERFECT ∈ `[0.45, 0.55]`(+0.10), GREAT ∈ `[0.38, 0.62]`(+0.05), 그 외 NORMAL(+0.0). `position = (releasedAtMs % cycleDurationMs) / cycleDurationMs`. 세션 기본값 `minimumHoldMs=600`, `cycleDurationMs=1800`.
- `finalSuccessRate = min(1.0, baseSuccessRate + bonus)`.
- 모든 신규 응답 필드는 FE DTO(`EvolutionStateDto`, `EvolutionAttemptDto`, `EvolutionAttemptsDto`, `TimingSessionDto`)와 필드명·타입 일치.
- 시간 직렬화: `Instant` → ISO-8601 UTC(`...Z`). 기존 Jackson 설정 사용(별도 포맷터 추가 금지, 기본 `WRITE_DATES_AS_TIMESTAMPS=false` 전제 — Task 2 Step 9에서 검증).
- 레거시 호환: `timing` 미포함 진화 시도(`POST /api/evolution/attempt`)는 기존 동작 그대로. 타이밍 응답 필드는 nullable.
- 테스트 실행: `cd apps/backend && ./gradlew test`. 단일 클래스: `./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.*"`.
- 커밋: Conventional Commits, scope `evolution` 또는 `chat`. 각 Task 끝에서 커밋.

---

## File Structure

**신규 파일:**
- `domain/evolution/service/TimingGrade.kt` — BE 도메인 등급 enum(bonusRate 포함).
- `domain/evolution/service/EvolutionTimingJudge.kt` — position·grade·bonus 계산 + 변조 검증.
- `domain/evolution/service/TimingSessionStore.kt` — 인메모리 1회용 세션 스토어.
- `domain/evolution/exception/InvalidTimingSessionException.kt`
- `domain/evolution/web/response/EvolutionAttemptsResponse.kt` — `{attempts:[...]}`.
- `domain/evolution/web/response/TimingSessionResponse.kt`
- `domain/evolution/web/request/TimingAttemptRequest.kt`
- `domain/evolution/properties/EvolutionTimingConfiguration.kt` — `TimingConfig` 빈 노출.
- 테스트: `EvolutionTimingJudgeTest.kt`, `TimingSessionStoreTest.kt`, `EvolutionControllerTest.kt`(신규), `ChatSseEventsTest.kt`(신규), 기존 `EvolutionServiceTest.kt`에 케이스 추가.

**수정 파일:**
- `domain/evolution/service/EvolutionResults.kt` — `currentExp`, 타이밍 필드, attempts record result, `TimingAttemptCommand`.
- `domain/evolution/service/EvolutionService.kt` — getState 매핑, getAttempts, attempt timing 통합.
- `domain/evolution/web/response/EvolutionStateResponse.kt` — `currentExp`.
- `domain/evolution/web/response/EvolutionAttemptResponse.kt` — 타이밍 필드.
- `domain/evolution/web/request/EvolutionAttemptRequest.kt` — `timing` 중첩.
- `domain/evolution/web/controller/EvolutionController.kt` — `/attempts`, `/timing-sessions`, attempt에 timing 전달.
- `domain/evolution/web/exception/EvolutionExceptionHandler.kt` — `InvalidTimingSessionException`.
- `domain/evolution/persistence/entity/EvolutionAttempt.kt` — 타이밍 컬럼 4개.
- `domain/evolution/persistence/repository/EvolutionAttemptRepository.kt` — 최신순 조회.
- `domain/evolution/properties/EvolutionProperties.kt` — `timing` 블록.
- `domain/chat/web/controller/ChatSseEvents.kt` — done 페이로드.
- `domain/chat/web/controller/ChatController.kt` — done 페이로드 주입.

---

## Task 1: `currentExp` 노출 (#1, P0)

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionResults.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionService.kt:37-47`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/response/EvolutionStateResponse.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionServiceTest.kt`

**Interfaces:**
- Produces: `EvolutionStateResult(level, isMaxLevel, nextAttemptCost, nextSuccessRate, currentExp: Long)`; `EvolutionStateResponse.currentExp: Long`.

- [ ] **Step 1: Write the failing test** — `EvolutionServiceTest.kt`에 신규 테스트 추가. `UserEvolution`은 `addExp(exp)`로 경험치를 세팅한다.

```kotlin
test("getState exposes current evolution exp") {
    val evolution = UserEvolution(user = user(), level = 1).apply { addExp(750L) }
    whenever(userEvolutionRepository.findByUserId(userId)).thenReturn(evolution)

    val state = service.getState(userId)

    state.currentExp shouldBe 750L
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionServiceTest"`
Expected: 컴파일 실패 — `EvolutionStateResult`에 `currentExp` 없음 / `state.currentExp` 미해결.

- [ ] **Step 3: Add `currentExp` to result + map in service**

`EvolutionResults.kt`:
```kotlin
data class EvolutionStateResult(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
    val currentExp: Long,
)
```
`EvolutionService.getState` 의 `return EvolutionStateResult(...)` 에 `currentExp = evo.exp` 추가:
```kotlin
return EvolutionStateResult(
    level = evo.level,
    isMaxLevel = rule == null,
    nextAttemptCost = rule?.attemptCost,
    nextSuccessRate = rule?.successRate,
    currentExp = evo.exp,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionServiceTest"`
Expected: PASS.

- [ ] **Step 5: Add field to response DTO**

`EvolutionStateResponse.kt`:
```kotlin
data class EvolutionStateResponse(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
    val currentExp: Long,
) {
    companion object {
        fun from(result: EvolutionStateResult) = EvolutionStateResponse(
            level = result.level,
            isMaxLevel = result.isMaxLevel,
            nextAttemptCost = result.nextAttemptCost,
            nextSuccessRate = result.nextSuccessRate,
            currentExp = result.currentExp,
        )
    }
}
```

- [ ] **Step 6: Compile + full evolution tests**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.*"`
Expected: PASS (기존 테스트가 `EvolutionStateResult` 생성자를 직접 호출하면 컴파일 에러 → 해당 호출에 `currentExp = ...` 추가).

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionResults.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionService.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/response/EvolutionStateResponse.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionServiceTest.kt
git commit -m "feat(evolution): expose currentExp in evolution state"
```

---

## Task 2: 진화 기록 조회 `GET /api/evolution/attempts` (#2, P1)

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/persistence/repository/EvolutionAttemptRepository.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionResults.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionService.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/response/EvolutionAttemptsResponse.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/controller/EvolutionController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionServiceTest.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/web/controller/EvolutionControllerTest.kt` (신규)

**Interfaces:**
- Produces:
  - `EvolutionAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<EvolutionAttempt>`
  - `EvolutionService.getAttempts(userId: Long, limit: Int): List<EvolutionAttemptRecordResult>`
  - `data class EvolutionAttemptRecordResult(success, fromLevel, resultLevel, cost, attemptedAt: Instant)`
  - `EvolutionAttemptsResponse(attempts: List<EvolutionAttemptRecordResponse>)`, `EvolutionAttemptRecordResponse(success, fromLevel, resultLevel, cost, attemptedAt: Instant)`
  - `GET /api/evolution/attempts?limit={1..100, default 20}`

- [ ] **Step 1: Write the failing service test**

`EvolutionServiceTest.kt`에 추가(import `org.mockito.kotlin.eq`는 기존 존재):
```kotlin
test("getAttempts returns own records newest-first limited by limit") {
    val now = java.time.Instant.parse("2026-06-25T12:34:56Z")
    val a1 = EvolutionAttempt(userId = userId, fromLevel = 2, cost = 1200, success = true, resultLevel = 3, idempotencyKey = "k1")
        .apply { createdAt = now }
    whenever(evolutionAttemptRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
        .thenReturn(listOf(a1))

    val records = service.getAttempts(userId, 20)

    records.size shouldBe 1
    records[0].success shouldBe true
    records[0].fromLevel shouldBe 2
    records[0].resultLevel shouldBe 3
    records[0].cost shouldBe 1200L
    records[0].attemptedAt shouldBe now
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionServiceTest"`
Expected: 컴파일 실패 — `findByUserIdOrderByCreatedAtDesc`, `getAttempts`, `EvolutionAttemptRecordResult` 미정의.

- [ ] **Step 3: Add repository method**

`EvolutionAttemptRepository.kt`:
```kotlin
import org.springframework.data.domain.Pageable

interface EvolutionAttemptRepository : JpaRepository<EvolutionAttempt, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): EvolutionAttempt?
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<EvolutionAttempt>
}
```

- [ ] **Step 4: Add result type + service method**

`EvolutionResults.kt`에 추가:
```kotlin
import java.time.Instant

data class EvolutionAttemptRecordResult(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val attemptedAt: Instant,
)
```
`EvolutionService.kt`에 추가(클래스 본문, import `org.springframework.data.domain.PageRequest`):
```kotlin
@Transactional(readOnly = true)
fun getAttempts(userId: Long, limit: Int): List<EvolutionAttemptRecordResult> =
    evolutionAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
        .map {
            EvolutionAttemptRecordResult(
                success = it.success,
                fromLevel = it.fromLevel,
                resultLevel = it.resultLevel,
                cost = it.cost,
                attemptedAt = it.createdAt,
            )
        }
```

- [ ] **Step 5: Run service test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionServiceTest"`
Expected: PASS.

- [ ] **Step 6: Add response DTO**

Create `EvolutionAttemptsResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptRecordResult
import java.time.Instant

data class EvolutionAttemptsResponse(
    val attempts: List<EvolutionAttemptRecordResponse>,
) {
    companion object {
        fun from(records: List<EvolutionAttemptRecordResult>) =
            EvolutionAttemptsResponse(records.map { EvolutionAttemptRecordResponse.from(it) })
    }
}

data class EvolutionAttemptRecordResponse(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val attemptedAt: Instant,
) {
    companion object {
        fun from(r: EvolutionAttemptRecordResult) = EvolutionAttemptRecordResponse(
            success = r.success,
            fromLevel = r.fromLevel,
            resultLevel = r.resultLevel,
            cost = r.cost,
            attemptedAt = r.attemptedAt,
        )
    }
}
```

- [ ] **Step 7: Add controller endpoint**

`EvolutionController.kt` — import 추가 후 클래스에 `@Validated` 부착, 메서드 추가:
```kotlin
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionAttemptsResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestParam

@GetMapping("/attempts")
fun getAttempts(
    authentication: Authentication,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
): EvolutionAttemptsResponse =
    EvolutionAttemptsResponse.from(evolutionService.getAttempts(authentication.userId(), limit))
```

- [ ] **Step 8: Write the failing controller test (new file)**

Create `EvolutionControllerTest.kt` (패턴: `AttendanceControllerTest`):
```kotlin
package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptRecordResult
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.web.exception.EvolutionExceptionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(EvolutionController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(EvolutionExceptionHandler::class)
class EvolutionControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var evolutionService: EvolutionService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /attempts returns records with ISO-8601 UTC attemptedAt") {
            whenever(evolutionService.getAttempts(eq(1L), any())).thenReturn(
                listOf(
                    EvolutionAttemptRecordResult(
                        success = true, fromLevel = 2, resultLevel = 3, cost = 1200,
                        attemptedAt = Instant.parse("2026-06-25T12:34:56Z"),
                    )
                )
            )

            mockMvc.perform(get("/api/evolution/attempts").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.attempts[0].success").value(true))
                .andExpect(jsonPath("$.attempts[0].fromLevel").value(2))
                .andExpect(jsonPath("$.attempts[0].resultLevel").value(3))
                .andExpect(jsonPath("$.attempts[0].cost").value(1200))
                .andExpect(jsonPath("$.attempts[0].attemptedAt").value("2026-06-25T12:34:56Z"))
        }

        test("GET /attempts with limit over 100 returns 400") {
            mockMvc.perform(get("/api/evolution/attempts").param("limit", "101").principal(principal))
                .andExpect(status().isBadRequest)
        }

        test("GET /attempts with limit 0 returns 400") {
            mockMvc.perform(get("/api/evolution/attempts").param("limit", "0").principal(principal))
                .andExpect(status().isBadRequest)
        }
    }
}
```

- [ ] **Step 9: Run controller test**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.web.controller.EvolutionControllerTest"`
Expected: PASS. `attemptedAt`가 `"2026-06-25T12:34:56Z"`(타임스탬프 숫자가 아님)로 직렬화되는지 확인. 숫자로 나오면 `application.yml`에 `spring.jackson.serialization.write-dates-as-timestamps: false` 추가.

- [ ] **Step 10: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/
git commit -m "feat(evolution): add GET /api/evolution/attempts history endpoint"
```

---

## Task 3: 타이밍 도메인 — `TimingGrade` enum + `EvolutionTimingJudge` (#4 핵심 판정)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/TimingGrade.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/properties/EvolutionProperties.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/properties/EvolutionTimingConfiguration.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionTimingJudge.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/exception/InvalidTimingSessionException.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionTimingJudgeTest.kt` (신규)

**Interfaces:**
- Produces:
  - `enum class TimingGrade(val bonusRate: Double) { NORMAL(0.0), GREAT(0.05), PERFECT(0.10) }`
  - `EvolutionProperties.timing: TimingConfig` with `TimingConfig(minimumHoldMs=600, cycleDurationMs=1800, perfectStart=0.45, perfectEnd=0.55, greatStart=0.38, greatEnd=0.62, sessionTtl=Duration.ofMinutes(2), clockSkewToleranceMs=2000)`
  - `data class TimingJudgement(val grade: TimingGrade, val bonusRate: Double, val baseSuccessRate: Double, val finalSuccessRate: Double)`
  - `EvolutionTimingJudge(config: EvolutionProperties.TimingConfig).judge(releasedAtMs: Long, elapsedSinceStartMs: Long, baseSuccessRate: Double): TimingJudgement` — 변조 시 `InvalidTimingSessionException`.
  - `class InvalidTimingSessionException(message): RuntimeException`

- [ ] **Step 1: Write the failing judge test**

Create `EvolutionTimingJudgeTest.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvolutionTimingJudgeTest : FunSpec({
    // cycleDurationMs = 1800. position = (releasedAtMs % 1800) / 1800.
    val judge = EvolutionTimingJudge(EvolutionProperties().timing)

    test("center release is PERFECT and adds 0.10") {
        // position 0.5 → releasedAtMs = 900 (in [0.45,0.55])
        val j = judge.judge(releasedAtMs = 900, elapsedSinceStartMs = 1000, baseSuccessRate = 0.65)
        j.grade shouldBe TimingGrade.PERFECT
        j.bonusRate shouldBe 0.10
        j.baseSuccessRate shouldBe 0.65
        j.finalSuccessRate shouldBe 0.75
    }

    test("great band release is GREAT (+0.05)") {
        // position 0.40 → releasedAtMs = 720 (in [0.38,0.62] but not perfect)
        val j = judge.judge(releasedAtMs = 720, elapsedSinceStartMs = 1000, baseSuccessRate = 0.5)
        j.grade shouldBe TimingGrade.GREAT
        j.finalSuccessRate shouldBe 0.55
    }

    test("outside band is NORMAL (+0.0)") {
        // position 0.10 → releasedAtMs = 180
        val j = judge.judge(releasedAtMs = 180, elapsedSinceStartMs = 1000, baseSuccessRate = 0.5)
        j.grade shouldBe TimingGrade.NORMAL
        j.finalSuccessRate shouldBe 0.5
    }

    test("final success rate is capped at 1.0") {
        val j = judge.judge(releasedAtMs = 900, elapsedSinceStartMs = 1000, baseSuccessRate = 0.95)
        j.finalSuccessRate shouldBe 1.0
    }

    test("releasedAtMs beyond elapsed + tolerance is rejected (tamper)") {
        // elapsed 500, tolerance 2000 → max 2500; 3000 exceeds
        shouldThrow<InvalidTimingSessionException> {
            judge.judge(releasedAtMs = 3000, elapsedSinceStartMs = 500, baseSuccessRate = 0.5)
        }
    }

    test("negative releasedAtMs is rejected") {
        shouldThrow<InvalidTimingSessionException> {
            judge.judge(releasedAtMs = -1, elapsedSinceStartMs = 1000, baseSuccessRate = 0.5)
        }
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionTimingJudgeTest"`
Expected: 컴파일 실패 — `TimingGrade`, `EvolutionTimingJudge`, `EvolutionProperties().timing`, `InvalidTimingSessionException` 미정의.

- [ ] **Step 3: Create `TimingGrade` enum**

`TimingGrade.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.service

/** 길게누르기 타이밍 등급. bonusRate 는 성공 확률 가산치(%p). FE TimingGrade 와 동일 의미. */
enum class TimingGrade(val bonusRate: Double) {
    NORMAL(0.0),
    GREAT(0.05),
    PERFECT(0.10),
}
```

- [ ] **Step 4: Create `InvalidTimingSessionException`**

`InvalidTimingSessionException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.exception

/** 타이밍 세션이 없거나 만료/타 사용자/변조(releasedAtMs 상한 초과)일 때. 비용 차감 전에 던진다. */
class InvalidTimingSessionException(
    message: String = "Invalid or expired timing session",
) : RuntimeException(message)
```

- [ ] **Step 5: Add `timing` config block to `EvolutionProperties`**

`EvolutionProperties.kt` — `rules` 옆에 `timing` 필드 + `TimingConfig` 추가:
```kotlin
import java.time.Duration

data class EvolutionProperties(
    val rules: List<LevelRule> = emptyList(),
    val timing: TimingConfig = TimingConfig(),
) {
    data class LevelRule(
        val fromLevel: Int,
        @field:Positive val attemptCost: Long,
        @field:DecimalMin("0.0") @field:DecimalMax("1.0") val successRate: Double,
    )

    /** 길게누르기 타이밍 보너스 설정. 기본값은 FE 하드코딩 상수와 일치해야 한다. */
    data class TimingConfig(
        val minimumHoldMs: Long = 600,
        val cycleDurationMs: Long = 1800,
        val perfectStart: Double = 0.45,
        val perfectEnd: Double = 0.55,
        val greatStart: Double = 0.38,
        val greatEnd: Double = 0.62,
        val sessionTtl: Duration = Duration.ofMinutes(2),
        val clockSkewToleranceMs: Long = 2000,
    )

    fun ruleFor(level: Int): LevelRule? = rules.firstOrNull { it.fromLevel == level }
}
```

- [ ] **Step 6: Create `EvolutionTimingJudge`**

`EvolutionTimingJudge.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import org.springframework.stereotype.Component
import kotlin.math.min

data class TimingJudgement(
    val grade: TimingGrade,
    val bonusRate: Double,
    val baseSuccessRate: Double,
    val finalSuccessRate: Double,
)

/**
 * 길게누르기 타이밍 판정. position = (releasedAtMs % cycle) / cycle 로 등급을 정하고
 * baseSuccessRate 에 보너스를 더해 최종 확률(상한 1.0)을 만든다.
 * releasedAtMs 가 세션 경과시간 + 허용오차를 넘으면 변조로 보고 거부한다.
 */
@Component
class EvolutionTimingJudge(
    private val config: EvolutionProperties.TimingConfig,
) {
    fun judge(releasedAtMs: Long, elapsedSinceStartMs: Long, baseSuccessRate: Double): TimingJudgement {
        if (releasedAtMs < 0) throw InvalidTimingSessionException("releasedAtMs must be non-negative")
        if (releasedAtMs > elapsedSinceStartMs + config.clockSkewToleranceMs) {
            throw InvalidTimingSessionException("releasedAtMs exceeds elapsed time")
        }
        val position = (releasedAtMs % config.cycleDurationMs).toDouble() / config.cycleDurationMs
        val grade = when {
            position in config.perfectStart..config.perfectEnd -> TimingGrade.PERFECT
            position in config.greatStart..config.greatEnd -> TimingGrade.GREAT
            else -> TimingGrade.NORMAL
        }
        val finalRate = min(1.0, baseSuccessRate + grade.bonusRate)
        return TimingJudgement(grade, grade.bonusRate, baseSuccessRate, finalRate)
    }
}
```

- [ ] **Step 7: Expose `TimingConfig` as a bean**

`EvolutionTimingJudge`/`TimingSessionStore`가 `TimingConfig`를 직접 주입받으므로 빈으로 노출한다.
`EvolutionTimingConfiguration.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.properties

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EvolutionTimingConfiguration {
    @Bean
    fun evolutionTimingConfig(properties: EvolutionProperties): EvolutionProperties.TimingConfig =
        properties.timing
}
```
> `EvolutionProperties`가 이미 `@ConfigurationProperties` 빈으로 등록돼 있다고 전제. 아니라면 등록 위치(`@EnableConfigurationProperties`/`@ConfigurationPropertiesScan`)를 확인해 동일하게 처리.

- [ ] **Step 8: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionTimingJudgeTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/TimingGrade.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionTimingJudge.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/exception/InvalidTimingSessionException.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/properties/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionTimingJudgeTest.kt
git commit -m "feat(evolution): add timing grade judge with tamper guard"
```

---

## Task 4: 타이밍 세션 스토어 + 발급 엔드포인트 (#4)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/TimingSessionStore.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/response/TimingSessionResponse.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/controller/EvolutionController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/service/TimingSessionStoreTest.kt` (신규)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/web/controller/EvolutionControllerTest.kt`

**Interfaces:**
- Produces:
  - `data class TimingSession(val sessionId: String, val userId: Long, val serverStartedAt: Instant, val expiresAt: Instant)`
  - `TimingSessionStore(config: EvolutionProperties.TimingConfig).issue(userId: Long): TimingSession`
  - `TimingSessionStore.consume(sessionId: String, userId: Long, now: Instant): TimingSession` — 없음/만료/타 사용자 시 `InvalidTimingSessionException`; 성공 시 제거(1회용).
  - `TimingSessionResponse(sessionId, serverStartedAt: Instant, minimumHoldMs: Long, cycleDurationMs: Long)`
  - `POST /api/evolution/timing-sessions`

- [ ] **Step 1: Write the failing store test**

Create `TimingSessionStoreTest.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class TimingSessionStoreTest : FunSpec({
    val config = EvolutionProperties().timing // sessionTtl 2m

    test("issued session can be consumed once by the same user") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)

        val consumed = store.consume(session.sessionId, userId = 1L, now = session.serverStartedAt.plusSeconds(1))
        consumed.sessionId shouldBe session.sessionId

        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 1L, now = session.serverStartedAt.plusSeconds(1))
        }
    }

    test("consuming another user's session is rejected") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)
        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 2L, now = session.serverStartedAt.plusSeconds(1))
        }
    }

    test("expired session is rejected") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)
        val afterTtl = session.serverStartedAt.plus(Duration.ofMinutes(5))
        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 1L, now = afterTtl)
        }
    }

    test("unknown session id is rejected") {
        val store = TimingSessionStore(config)
        shouldThrow<InvalidTimingSessionException> {
            store.consume("nope", userId = 1L, now = Instant.now())
        }
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.TimingSessionStoreTest"`
Expected: 컴파일 실패 — `TimingSessionStore` 미정의.

- [ ] **Step 3: Create `TimingSessionStore`**

`TimingSessionStore.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TimingSession(
    val sessionId: String,
    val userId: Long,
    val serverStartedAt: Instant,
    val expiresAt: Instant,
)

/**
 * 인메모리 1회용 타이밍 세션 스토어. 단일 인스턴스 배포 전제.
 * consume 은 같은 사용자·미만료일 때만 성공하고 즉시 제거한다(중복 사용 차단).
 */
@Component
class TimingSessionStore(
    private val config: EvolutionProperties.TimingConfig,
) {
    private val sessions = ConcurrentHashMap<String, TimingSession>()

    fun issue(userId: Long): TimingSession {
        val now = Instant.now()
        val session = TimingSession(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            serverStartedAt = now,
            expiresAt = now.plus(config.sessionTtl),
        )
        sessions[session.sessionId] = session
        return session
    }

    fun consume(sessionId: String, userId: Long, now: Instant): TimingSession {
        val session = sessions.remove(sessionId)
            ?: throw InvalidTimingSessionException("Unknown timing session")
        if (session.userId != userId) throw InvalidTimingSessionException("Timing session owner mismatch")
        if (now.isAfter(session.expiresAt)) throw InvalidTimingSessionException("Timing session expired")
        return session
    }
}
```

- [ ] **Step 4: Run store test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.TimingSessionStoreTest"`
Expected: PASS.

- [ ] **Step 5: Add response DTO + controller endpoint**

`TimingSessionResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.evolution.service.TimingSession
import java.time.Instant

data class TimingSessionResponse(
    val sessionId: String,
    val serverStartedAt: Instant,
    val minimumHoldMs: Long,
    val cycleDurationMs: Long,
) {
    companion object {
        fun from(session: TimingSession, config: EvolutionProperties.TimingConfig) = TimingSessionResponse(
            sessionId = session.sessionId,
            serverStartedAt = session.serverStartedAt,
            minimumHoldMs = config.minimumHoldMs,
            cycleDurationMs = config.cycleDurationMs,
        )
    }
}
```
`EvolutionController.kt` — 생성자에 의존성 추가(`private val timingSessionStore: TimingSessionStore, private val timingConfig: EvolutionProperties.TimingConfig`) + 메서드 추가:
```kotlin
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.evolution.service.TimingSessionStore
import com.wnl.cashchat.api.domain.evolution.web.response.TimingSessionResponse

@PostMapping("/timing-sessions")
fun createTimingSession(authentication: Authentication): TimingSessionResponse =
    TimingSessionResponse.from(timingSessionStore.issue(authentication.userId()), timingConfig)
```

- [ ] **Step 6: Add controller test for session issue**

`EvolutionControllerTest.kt` — 컨트롤러 생성자가 늘었으므로 `@MockBean` 추가 + 테스트(import `org.mockito.kotlin.eq`, `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post`):
```kotlin
// 클래스 필드 추가
@MockBean lateinit var timingSessionStore: com.wnl.cashchat.api.domain.evolution.service.TimingSessionStore
@MockBean lateinit var timingConfig: com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties.TimingConfig

// init 블록 테스트
test("POST /timing-sessions returns session window params") {
    val started = java.time.Instant.parse("2026-06-26T00:00:00Z")
    whenever(timingSessionStore.issue(eq(1L))).thenReturn(
        com.wnl.cashchat.api.domain.evolution.service.TimingSession(
            sessionId = "sess-1", userId = 1L,
            serverStartedAt = started, expiresAt = started.plusSeconds(120),
        )
    )
    whenever(timingConfig.minimumHoldMs).thenReturn(600)
    whenever(timingConfig.cycleDurationMs).thenReturn(1800)

    mockMvc.perform(post("/api/evolution/timing-sessions").principal(principal))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.sessionId").value("sess-1"))
        .andExpect(jsonPath("$.serverStartedAt").value("2026-06-26T00:00:00Z"))
        .andExpect(jsonPath("$.minimumHoldMs").value(600))
        .andExpect(jsonPath("$.cycleDurationMs").value(1800))
}
```

- [ ] **Step 7: Run controller test**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.web.controller.EvolutionControllerTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/
git commit -m "feat(evolution): add one-time in-memory timing session issue endpoint"
```

---

## Task 5: 진화 시도에 타이밍 통합 + 멱등 컬럼 + 응답 필드 (#4)

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/persistence/entity/EvolutionAttempt.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionResults.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/service/EvolutionService.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/request/TimingAttemptRequest.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/request/EvolutionAttemptRequest.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/response/EvolutionAttemptResponse.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/controller/EvolutionController.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/web/exception/EvolutionExceptionHandler.kt`
- Test: `EvolutionServiceTest.kt`, `EvolutionControllerTest.kt`

**Interfaces:**
- Consumes: `TimingSessionStore.consume`, `EvolutionTimingJudge.judge`, `TimingJudgement`, `TimingGrade`, `TimingSession`.
- Produces:
  - `data class TimingAttemptCommand(val sessionId: String, val releasedAtMs: Long)` (in `EvolutionResults.kt`)
  - `EvolutionService.attempt(userId: Long, idempotencyKey: String, timing: TimingAttemptCommand? = null): EvolutionAttemptResult`
  - `EvolutionAttemptResult(success, fromLevel, resultLevel, cost, timingGrade: TimingGrade?=null, timingBonusRate: Double?=null, baseSuccessRate: Double?=null, finalSuccessRate: Double?=null)`
  - `EvolutionAttempt` 추가 컬럼: `timingGrade, timingBonusRate, baseSuccessRate, finalSuccessRate` (nullable, default null)
  - `TimingAttemptRequest(sessionId, releasedAtMs)`, `EvolutionAttemptRequest.timing: TimingAttemptRequest?`
  - `EvolutionAttemptResponse` 타이밍 필드 4개.

- [ ] **Step 1: Add nullable timing columns to entity**

`EvolutionAttempt.kt` — import + 생성자 파라미터 추가(기존 `idempotencyKey` 뒤, default null로 레거시 생성 호출 호환):
```kotlin
import com.wnl.cashchat.api.domain.evolution.service.TimingGrade
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

// 생성자 끝(idempotencyKey 뒤)에 추가:
    @Enumerated(EnumType.STRING)
    @Column(name = "timing_grade")
    val timingGrade: TimingGrade? = null,

    @Column(name = "timing_bonus_rate")
    val timingBonusRate: Double? = null,

    @Column(name = "base_success_rate")
    val baseSuccessRate: Double? = null,

    @Column(name = "final_success_rate")
    val finalSuccessRate: Double? = null,
```
> `ddl-auto`가 dev/test에서 update/create면 마이그레이션 SQL 불필요(기존 관례 확인). prod 스키마 반영은 운영 절차에 따름.

- [ ] **Step 2: Extend result types + add command type**

`EvolutionResults.kt` — `EvolutionAttemptResult`에 타이밍 필드 추가 + `TimingAttemptCommand` 추가. (`TimingGrade`는 동일 `service` 패키지이므로 import 불필요.)
```kotlin
data class EvolutionAttemptResult(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val timingGrade: TimingGrade? = null,
    val timingBonusRate: Double? = null,
    val baseSuccessRate: Double? = null,
    val finalSuccessRate: Double? = null,
)

data class TimingAttemptCommand(
    val sessionId: String,
    val releasedAtMs: Long,
)
```

- [ ] **Step 3: Write failing service tests for timing path**

`EvolutionServiceTest.kt` — `lateinit`에 `timingSessionStore`, `evolutionTimingJudge` 추가, `beforeTest`에서 mock 생성 + `service = EvolutionService(..., timingSessionStore, evolutionTimingJudge)`로 생성자 갱신, 신규 테스트 추가:
```kotlin
// lateinit 추가
lateinit var timingSessionStore: TimingSessionStore
lateinit var evolutionTimingJudge: EvolutionTimingJudge
// beforeTest 내부: timingSessionStore = mock(); evolutionTimingJudge = mock()
//   service 생성자 마지막에 timingSessionStore, evolutionTimingJudge 추가

test("timing attempt consumes session, judges, and uses final success rate") {
    val evolution = evo(level = 1, exp = 1000L)
    whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
    val started = java.time.Instant.parse("2026-06-26T00:00:00Z")
    whenever(timingSessionStore.consume(eq("sess-1"), eq(userId), any())).thenReturn(
        TimingSession("sess-1", userId, started, started.plusSeconds(120))
    )
    whenever(evolutionTimingJudge.judge(eq(900L), any(), eq(0.7))).thenReturn(
        TimingJudgement(TimingGrade.PERFECT, 0.10, 0.7, 0.8)
    )
    whenever(probabilityRoller.succeeds(0.8)).thenReturn(true)

    val result = service.attempt(userId, "key-t1", TimingAttemptCommand("sess-1", 900L))

    result.success shouldBe true
    result.timingGrade shouldBe TimingGrade.PERFECT
    result.timingBonusRate shouldBe 0.10
    result.baseSuccessRate shouldBe 0.7
    result.finalSuccessRate shouldBe 0.8
    verify(probabilityRoller).succeeds(0.8)
}

test("legacy attempt without timing uses base rule rate and null timing fields") {
    val evolution = evo(level = 1, exp = 1000L)
    whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
    whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

    val result = service.attempt(userId, "key-l1", null)

    result.timingGrade shouldBe null
    result.finalSuccessRate shouldBe null
    verify(timingSessionStore, never()).consume(any(), any(), any())
}

test("duplicate timing key returns stored judgement without consuming session again") {
    val evolution = evo(level = 1, exp = 1000L)
    whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
    whenever(evolutionAttemptRepository.findByUserIdAndIdempotencyKey(userId, "key-t2")).thenReturn(
        EvolutionAttempt(
            userId = userId, fromLevel = 1, cost = 500, success = true, resultLevel = 2, idempotencyKey = "key-t2",
            timingGrade = TimingGrade.GREAT, timingBonusRate = 0.05, baseSuccessRate = 0.7, finalSuccessRate = 0.75,
        )
    )

    val result = service.attempt(userId, "key-t2", TimingAttemptCommand("sess-x", 720L))

    result.timingGrade shouldBe TimingGrade.GREAT
    result.finalSuccessRate shouldBe 0.75
    verify(timingSessionStore, never()).consume(any(), any(), any())
}
```

- [ ] **Step 4: Run to verify fail**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionServiceTest"`
Expected: 컴파일 실패 — `TimingAttemptCommand`, 3-인자 `attempt`, 새 생성자 의존 미정의.

- [ ] **Step 5: Implement timing integration in `EvolutionService`**

`EvolutionService.kt`:
1) 생성자에 의존 추가: `private val timingSessionStore: TimingSessionStore, private val evolutionTimingJudge: EvolutionTimingJudge`.
2) `attempt` 시그니처/본문 교체:
```kotlin
@Transactional
fun attempt(userId: Long, idempotencyKey: String, timing: TimingAttemptCommand? = null): EvolutionAttemptResult {
    val evo = userEvolutionRepository.findByUserIdForUpdate(userId)
        ?: throw IllegalStateException("UserEvolution not initialized for userId=$userId")

    evolutionAttemptRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { return it.toResult() }

    val rule = evolutionProperties.ruleFor(evo.level) ?: throw AlreadyMaxLevelException()
    val fromLevel = evo.level

    // 타이밍 판정(있으면): 세션 소비(1회용) → 변조검증/등급 → 최종 확률. 비용 차감 전에 수행.
    val judgement = timing?.let {
        val now = java.time.Instant.now()
        val session = timingSessionStore.consume(it.sessionId, userId, now)
        val elapsed = java.time.Duration.between(session.serverStartedAt, now).toMillis()
        evolutionTimingJudge.judge(it.releasedAtMs, elapsed, rule.successRate)
    }
    val effectiveRate = judgement?.finalSuccessRate ?: rule.successRate

    evo.spendExp(rule.attemptCost)

    val success = probabilityRoller.succeeds(effectiveRate)
    if (success) {
        evo.levelUp()
        energyService.applyPostEvolutionBoost(userId)
    }

    evolutionAttemptRepository.save(
        EvolutionAttempt(
            userId = userId,
            fromLevel = fromLevel,
            cost = rule.attemptCost,
            success = success,
            resultLevel = evo.level,
            idempotencyKey = idempotencyKey,
            timingGrade = judgement?.grade,
            timingBonusRate = judgement?.bonusRate,
            baseSuccessRate = judgement?.baseSuccessRate,
            finalSuccessRate = judgement?.finalSuccessRate,
        )
    )
    return EvolutionAttemptResult(
        success = success,
        fromLevel = fromLevel,
        resultLevel = evo.level,
        cost = rule.attemptCost,
        timingGrade = judgement?.grade,
        timingBonusRate = judgement?.bonusRate,
        baseSuccessRate = judgement?.baseSuccessRate,
        finalSuccessRate = judgement?.finalSuccessRate,
    )
}
```
3) `toResult()` 매핑 확장:
```kotlin
private fun EvolutionAttempt.toResult() =
    EvolutionAttemptResult(
        success, fromLevel, resultLevel, cost,
        timingGrade, timingBonusRate, baseSuccessRate, finalSuccessRate,
    )
```

- [ ] **Step 6: Run service test to verify pass**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.service.EvolutionServiceTest"`
Expected: PASS (기존 2-인자 `attempt(userId, "key")` 호출은 `timing` default null로 그대로 통과).

- [ ] **Step 7: Add request DTO (nested timing) + response fields**

`TimingAttemptRequest.kt`:
```kotlin
package com.wnl.cashchat.api.domain.evolution.web.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class TimingAttemptRequest(
    @field:NotBlank @field:Size(max = 255) val sessionId: String,
    @field:PositiveOrZero val releasedAtMs: Long,
)
```
`EvolutionAttemptRequest.kt` — `timing` 필드 추가:
```kotlin
import jakarta.validation.Valid

data class EvolutionAttemptRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val idempotencyKey: String,
    @field:Valid
    val timing: TimingAttemptRequest? = null,
)
```
`EvolutionAttemptResponse.kt` — 타이밍 필드 + 매핑:
```kotlin
import com.wnl.cashchat.api.domain.evolution.service.TimingGrade

data class EvolutionAttemptResponse(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val timingGrade: TimingGrade? = null,
    val timingBonusRate: Double? = null,
    val baseSuccessRate: Double? = null,
    val finalSuccessRate: Double? = null,
) {
    companion object {
        fun from(result: EvolutionAttemptResult) = EvolutionAttemptResponse(
            success = result.success,
            fromLevel = result.fromLevel,
            resultLevel = result.resultLevel,
            cost = result.cost,
            timingGrade = result.timingGrade,
            timingBonusRate = result.timingBonusRate,
            baseSuccessRate = result.baseSuccessRate,
            finalSuccessRate = result.finalSuccessRate,
        )
    }
}
```

- [ ] **Step 8: Wire controller `attempt` to pass timing + map exception**

`EvolutionController.kt` `attempt` 메서드:
```kotlin
import com.wnl.cashchat.api.domain.evolution.service.TimingAttemptCommand

@PostMapping("/attempt")
fun attempt(
    authentication: Authentication,
    @Valid @RequestBody request: EvolutionAttemptRequest,
): EvolutionAttemptResponse =
    EvolutionAttemptResponse.from(
        evolutionService.attempt(
            authentication.userId(),
            request.idempotencyKey,
            request.timing?.let { TimingAttemptCommand(it.sessionId, it.releasedAtMs) },
        )
    )
```
`EvolutionExceptionHandler.kt` — 핸들러 추가:
```kotlin
import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException

@ExceptionHandler(InvalidTimingSessionException::class)
fun handleInvalidTimingSession(e: InvalidTimingSessionException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ErrorResponse("INVALID_TIMING_SESSION", e.message ?: "Invalid or expired timing session"))
```

- [ ] **Step 9: Add controller tests for timing attempt + invalid session**

`EvolutionControllerTest.kt`(import `org.springframework.http.MediaType`, `...MockMvcRequestBuilders.post`):
```kotlin
test("POST /attempt with timing returns judged rates") {
    whenever(evolutionService.attempt(eq(1L), eq("key-1"), any())).thenReturn(
        com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptResult(
            success = true, fromLevel = 2, resultLevel = 3, cost = 1200,
            timingGrade = com.wnl.cashchat.api.domain.evolution.service.TimingGrade.PERFECT,
            timingBonusRate = 0.10, baseSuccessRate = 0.65, finalSuccessRate = 0.75,
        )
    )
    val body = """{"idempotencyKey":"key-1","timing":{"sessionId":"s1","releasedAtMs":900}}"""
    mockMvc.perform(
        post("/api/evolution/attempt").principal(principal)
            .contentType(MediaType.APPLICATION_JSON).content(body)
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.timingGrade").value("PERFECT"))
        .andExpect(jsonPath("$.finalSuccessRate").value(0.75))
}

test("POST /attempt with invalid timing session returns 422 INVALID_TIMING_SESSION") {
    whenever(evolutionService.attempt(eq(1L), eq("key-2"), any()))
        .thenThrow(com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException())
    val body = """{"idempotencyKey":"key-2","timing":{"sessionId":"bad","releasedAtMs":900}}"""
    mockMvc.perform(
        post("/api/evolution/attempt").principal(principal)
            .contentType(MediaType.APPLICATION_JSON).content(body)
    )
        .andExpect(status().isUnprocessableEntity)
        .andExpect(jsonPath("$.code").value("INVALID_TIMING_SESSION"))
}
```

- [ ] **Step 10: Run full evolution suite**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.evolution.*"`
Expected: PASS. 통합 테스트(`EvolutionIntegrationTest`)가 `attempt`를 2-인자로 부르면 default null로 통과. 컴파일 에러 시 호출은 그대로 두고(default) 확인만.

- [ ] **Step 11: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/evolution/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/evolution/
git commit -m "feat(evolution): server-side timing bonus judgement on attempt"
```

---

## Task 6: done SSE 보상 페이로드 (#3, P2)

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatSseEvents.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt:274`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatSseEventsTest.kt` (신규)

**Interfaces:**
- Consumes: `ChatRewardProperties.chatRewardPt`, `evolutionExpPerChat`.
- Produces: `Flux<String>.asChatSseEvents(donePayload: String): Flux<ServerSentEvent<String>>` — done 이벤트 data가 `donePayload`(JSON), event 이름은 `done` 유지.

- [ ] **Step 1: Write failing test for done payload**

기존 SSE 테스트 존재 확인: `ls apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/`. 없으면 신규 `ChatSseEventsTest.kt`:
```kotlin
package com.wnl.cashchat.api.domain.chat.web.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class ChatSseEventsTest : FunSpec({
    test("successful stream ends with done event carrying reward payload, name stays 'done'") {
        val payload = """{"pointDelta":1,"expDelta":1}"""
        val events = Flux.just("hello", " world").asChatSseEvents(payload)

        StepVerifier.create(events)
            .assertNext { it.event() shouldBe "message"; it.data() shouldBe "hello" }
            .assertNext { it.event() shouldBe "message"; it.data() shouldBe " world" }
            .assertNext { it.event() shouldBe "done"; it.data() shouldBe payload }
            .verifyComplete()
    }

    test("failed stream emits error event and no done") {
        val events = Flux.concat(Flux.just("partial"), Flux.error<String>(RuntimeException("boom")))
            .asChatSseEvents("""{"pointDelta":1,"expDelta":1}""")

        StepVerifier.create(events)
            .assertNext { it.event() shouldBe "message" }
            .assertNext { it.event() shouldBe "error" }
            .verifyComplete()
    }
})
```
> `reactor-test`(`StepVerifier`)가 testImplementation에 있는지 `build.gradle.kts`에서 확인. 없으면 기존 SSE 검증 방식(있으면 그 패턴)을 따르고, 정 없으면 `events.collectList().block()`으로 검증.

- [ ] **Step 2: Run to verify fail**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.chat.web.controller.ChatSseEventsTest"`
Expected: 컴파일 실패 — `asChatSseEvents(payload)` 인자 미지원.

- [ ] **Step 3: Change `asChatSseEvents` to take done payload**

`ChatSseEvents.kt`:
```kotlin
internal fun Flux<String>.asChatSseEvents(donePayload: String): Flux<ServerSentEvent<String>> =
    map { chunk -> ServerSentEvent.builder<String>(chunk).event(MESSAGE_EVENT).build() }
        .concatWith(Flux.just(ServerSentEvent.builder<String>(donePayload).event(DONE_EVENT).build()))
        .onErrorResume {
            Flux.just(ServerSentEvent.builder<String>(STREAM_FAILED_MESSAGE).event(ERROR_EVENT).build())
        }
```
`STREAM_DONE_DATA` 상수: 다른 참조 있는지 grep 후 미사용이면 제거.

- [ ] **Step 4: Run test to verify pass**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.chat.web.controller.ChatSseEventsTest"`
Expected: PASS.

- [ ] **Step 5: Wire ChatController to build the payload**

`ChatController.kt` — `ObjectMapper`/`ChatRewardProperties` 주입 확인(없으면 생성자에 추가) 후 line 274 호출 변경:
```kotlin
// 보조 타입(파일 하단 또는 별도 파일):
//   data class DoneRewardPayload(val pointDelta: Long, val expDelta: Long)

.asChatSseEvents(
    objectMapper.writeValueAsString(
        DoneRewardPayload(
            pointDelta = chatRewardProperties.chatRewardPt,
            expDelta = chatRewardProperties.evolutionExpPerChat,
        )
    )
)
```
> 컨트롤러에 `ObjectMapper`가 없으면 생성자에 `private val objectMapper: ObjectMapper` 추가(스프링 기본 빈). `ChatRewardProperties`도 동일하게 주입.

- [ ] **Step 6: Run chat tests**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.chat.*"`
Expected: PASS. 기존 `ChatControllerTest`가 done data를 `[DONE]`로 단언하면 새 payload(`{"pointDelta":1,"expDelta":1}`)로 갱신.

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/
git commit -m "feat(chat): include reward delta payload in done SSE event"
```

---

## Task 7: 전체 빌드·검증 + 마무리

- [ ] **Step 1: Full build + test**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL. 실패 시 해당 테스트만 좁혀 디버그.

- [ ] **Step 2: 항목별 /code-review (CC-311 워크플로 관례)**

각 기능 커밋 범위로 `/code-review`(특히 Task 5 타이밍 통합, Task 6 SSE 변경). 지적 반영 후 재커밋.

- [ ] **Step 3: 스모크 — 라우팅 확인(선택)**

`./gradlew bootRun`(H2) 후 인증 토큰으로:
- `GET /api/evolution/me` → `currentExp` 포함
- `POST /api/evolution/timing-sessions` → 세션 발급
- `POST /api/evolution/attempt`(timing 포함) → `timingGrade`/`finalSuccessRate`
- `GET /api/evolution/attempts` → 기록 목록

- [ ] **Step 4: PR + Confluence 작업로그**

`feature/cc-352-evolution-reward-backend` → upstream `dev` PR(CLAUDE.md Fork PR 절차). Confluence CC-352 페이지/작업로그에 구현 완료 갱신.

---

## Self-Review 메모

- **Spec 커버리지:** #1→Task1, #2→Task2, #4(세션·판정·통합·멱등컬럼)→Task3·4·5, #3→Task6. 전 항목 매핑됨.
- **타입 일관성:** `TimingGrade`(service 패키지) 단일 정의를 entity/response/result가 공유. `TimingJudgement`/`TimingSession`/`TimingAttemptCommand` 이름 일관. `attempt` 3-인자(default null)로 레거시 호출 호환. `asChatSseEvents(donePayload)` 단일 시그니처.
- **실행 중 확인 지점:** (a) `Instant` ISO 직렬화 설정(Task2 S9), (b) `EvolutionProperties.TimingConfig` 빈 노출(Task3 S7), (c) `EvolutionService`/`EvolutionController` 생성자 변경에 따른 기존 통합테스트/호출부 컴파일(Task5 S10), (d) `reactor-test` 의존 유무(Task6 S1).
