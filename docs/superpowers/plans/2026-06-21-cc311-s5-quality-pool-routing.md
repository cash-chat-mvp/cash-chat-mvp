# CC-311 S5 — Shared Quality Pool & Model Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. (NOTE: this session's subagents are blocked by a monthly spend limit → lead implements directly, still TDD per task.)

**Goal:** S3가 적립하는 `sharedQualityPool`을 모델 라우팅 게이트에 연결한다 — 풀 잔액이 `premiumDelta` 이상이면 상위(premium) 모델을 쓰고 그만큼 풀에서 차감, 부족하면 기본(nano) 모델로 자동 강등(I8). 풀은 음수 불가(I9).

**Architecture:** 전역 싱글톤 `shared_quality_pool` 행에 대해 **조건부 차감**(`UPDATE ... WHERE balance >= delta`, 영향행 1일 때만 premium). 라우팅 결정은 서버 전용(클라이언트 선택 불가, §10.1). 결정·차감은 채팅 진입 트랜잭션 안에서 1회 수행하고, 선택된 티어의 모델명(설정값)을 `LlmProvider`에 전달한다. 모델명 기본값은 비어 있어 **기존 단일 모델 동작과 동일**(prod에서 env로 nano/premium 모델명 주입).

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring AI(OpenAI 호환), JPA+Flyway, Kotest + TestContainers(MySQL) + mockito-kotlin + Reactor.

## Global Constraints

- **로그인/인증 코드 절대 불변**(`domain/auth`, JWT, SecurityConfig). **광고 SSV 로직 절대 불변**(`domain/ad`).
- **내부 원가/마진 수치(`NANO_COST_PT=0.68`, `SHARED_POOL_MARGIN_PT=0.32`, `ENERGY_BACKING_PT=2.00`, premiumDelta) 소스·커밋 문서에 하드코딩 금지.** `premiumDelta`는 `EconomyProperties.premiumDeltaPt` 기본 `BigDecimal.ZERO` + env(기존 `sharedPoolMarginPerChat`와 동일 패턴).
- **확정 결정(유저):** ① premium 사용 시 풀에서 `premiumDelta` **조건부 차감**(`WHERE balance >= delta`, 영향행 1일 때만 premium; I9 보장). ② **결정+풀차감 로직**만 구현, 실제 호출 모델명은 **설정값**(`nanoModelName`/`premiumModelName`, 기본 빈 문자열 → override 없음 → 현재 모델 그대로). ③ 비용 상한(MAX_*_TOKENS, throttle)은 **P2로 분리**(이번 범위 제외).
- I8: premium 모델 사용 ⇒ `sharedQualityPool ≥ premiumDelta`. I9: `sharedQualityPool`은 0 미만 불가.
- `ddl-auto: validate` — 본 슬라이스는 **신규 테이블/컬럼 없음**(shared_quality_pool은 V7에 이미 존재). 마이그레이션 불필요.
- 통합 테스트는 **Docker Desktop 필수**(TestContainers MySQL).

## 비범위(명시)

- 실제 두 모델 물리 전환의 prod 모델 선정·튜닝(설정만 제공), 비용 상한·throttle·요약/절단(P2), 공용 풀 대사·잠재부채 대시보드(P2), 풀 차감 원장/감사 테이블(P2). premium 선택 후 채팅 실패 시 풀 환불은 하지 않는다(운영손실로 간주, P2).

---

## File Structure

- `domain/economy/persistence/repository/SharedQualityPoolRepository.kt` — `tryDebit` 추가(수정).
- `domain/economy/service/SharedQualityPoolService.kt` — `tryConsumePremium` 추가(수정).
- `domain/economy/properties/EconomyProperties.kt` — `premiumDeltaPt`, `nanoModelName`, `premiumModelName` 추가(수정).
- `domain/economy/service/ModelTier.kt`, `ModelRoutingService.kt`(+`RoutingDecision`) — 신규.
- `domain/chat/service/llm/LlmProvider.kt` — `stream(messages, modelOverride)` 시그니처(수정), `GeminiLlmProvider`/`OpenAiLlmProvider` 모델 override 적용(수정).
- `domain/chat/service/ChatService.kt` — 진입 트랜잭션에서 라우팅 결정·차감, 모델 override 전달(수정).

---

### Task 1: SharedQualityPool 조건부 차감 + premiumDeltaPt

**Files:**
- Modify: `domain/economy/persistence/repository/SharedQualityPoolRepository.kt`
- Modify: `domain/economy/service/SharedQualityPoolService.kt`
- Modify: `domain/economy/properties/EconomyProperties.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/service/SharedQualityPoolDebitIntegrationTest.kt`

**Interfaces:**
- Produces:
  - `SharedQualityPoolRepository.tryDebit(amount: BigDecimal): Int` — `UPDATE ... SET balance=balance-:amount WHERE id=1 AND balance >= :amount`, 영향행 수 반환.
  - `SharedQualityPoolService.tryConsumePremium(premiumDelta: BigDecimal): Boolean` — `@Transactional(MANDATORY)`. 싱글톤 보장 후 조건부 차감, 성공(영향행 1)이면 true.
  - `EconomyProperties.premiumDeltaPt: BigDecimal = BigDecimal.ZERO`.

- [ ] **Step 1: Write the failing test**

`SharedQualityPoolDebitIntegrationTest.kt` (기존 `SharedQualityPoolServiceIntegrationTest` 컨테이너/`@DynamicPropertySource`/`TransactionTemplate` 컨벤션을 그대로 모방):
```kotlin
package com.wnl.cashchat.api.domain.economy.service

// 기존 SharedQualityPoolServiceIntegrationTest 와 동일한 @SpringBootTest + TestContainers + TransactionTemplate 셋업
class SharedQualityPoolDebitIntegrationTest : FunSpec() {
    // @Autowired service, repository(SharedQualityPoolRepository), transactionTemplate
    // beforeTest: repository.deleteAll()

    init {
        test("tryConsumePremium debits when balance >= delta and returns true") {
            transactionTemplate.execute { service.accrue(BigDecimal("10.0000")) }
            val ok = transactionTemplate.execute { service.tryConsumePremium(BigDecimal("3.0000")) }!!
            ok shouldBe true
            repository.findById(1L).get().balance.compareTo(BigDecimal("7.0000")) shouldBe 0
        }

        test("tryConsumePremium returns false and does not go negative when balance < delta (I9)") {
            transactionTemplate.execute { service.accrue(BigDecimal("2.0000")) }
            val ok = transactionTemplate.execute { service.tryConsumePremium(BigDecimal("5.0000")) }!!
            ok shouldBe false
            repository.findById(1L).get().balance.compareTo(BigDecimal("2.0000")) shouldBe 0
        }

        test("tryConsumePremium with zero delta allows premium without reducing balance") {
            transactionTemplate.execute { service.accrue(BigDecimal("1.0000")) }
            val ok = transactionTemplate.execute { service.tryConsumePremium(BigDecimal.ZERO) }!!
            ok shouldBe true
            repository.findById(1L).get().balance.compareTo(BigDecimal("1.0000")) shouldBe 0
        }
    }
    // companion object: MySQLContainer + @DynamicPropertySource (기존과 동일)
}
```
구현 노트: zero-delta 케이스는 `balance >= 0` 항상 참 + `balance - 0` 무변동이므로 영향행 1 → true(현재 모델 비용 0 기준 = premium 허용). 싱글톤 행이 없을 때도 `insertSingletonIfAbsent`로 보장.

- [ ] **Step 2: Run test (FAIL — tryDebit/tryConsumePremium 미존재)**

`cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.economy.service.SharedQualityPoolDebitIntegrationTest"` (FOREGROUND)

- [ ] **Step 3: Implement repository.tryDebit**

`SharedQualityPoolRepository.kt` — `accrue` 아래 추가:
```kotlin
    @Modifying
    @Query(
        value = "UPDATE shared_quality_pool SET balance = balance - :amount, updated_at = CURRENT_TIMESTAMP(6) " +
            "WHERE id = 1 AND balance >= :amount",
        nativeQuery = true,
    )
    fun tryDebit(@Param("amount") amount: BigDecimal): Int
```

- [ ] **Step 4: Implement service.tryConsumePremium + property**

`EconomyProperties.kt` — `sharedPoolMarginPerChat` 아래에 필드 추가:
```kotlin
    @field:DecimalMin("0.0") val premiumDeltaPt: BigDecimal = BigDecimal.ZERO,
    val nanoModelName: String = "",
    val premiumModelName: String = "",
```

`SharedQualityPoolService.kt` — `accrue` 아래 추가:
```kotlin
    /** premiumDelta 만큼 조건부 차감(잔액 충분할 때만). I9: WHERE balance >= delta 로 음수 불가. 성공 시 true. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun tryConsumePremium(premiumDelta: BigDecimal): Boolean {
        sharedQualityPoolRepository.insertSingletonIfAbsent()
        return sharedQualityPoolRepository.tryDebit(premiumDelta) == 1
    }
```

- [ ] **Step 5: Run test (PASS)** — same command as Step 2.

- [ ] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(economy): add shared quality pool conditional debit (premiumDelta)"
```

---

### Task 2: ModelTier + ModelRoutingService (라우팅 게이트 I8/I9)

**Files:**
- Create: `domain/economy/service/ModelTier.kt`
- Create: `domain/economy/service/ModelRoutingService.kt` (`RoutingDecision` 포함)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/service/ModelRoutingServiceTest.kt`

**Interfaces:**
- Consumes: `SharedQualityPoolService.tryConsumePremium`, `EconomyProperties`(premiumRoutingEnabled, premiumDeltaPt, nanoModelName, premiumModelName).
- Produces:
  - `enum class ModelTier { NANO, PREMIUM }`.
  - `data class RoutingDecision(val tier: ModelTier, val modelOverride: String?)`.
  - `ModelRoutingService.selectAndConsume(): RoutingDecision` — `@Transactional(MANDATORY)`. premiumRoutingEnabled=false → NANO; true & 차감 성공 → PREMIUM; 차감 실패 → NANO. `modelOverride`는 해당 티어의 설정 모델명(빈 문자열이면 null).

- [ ] **Step 1: Write the failing test**

`ModelRoutingServiceTest.kt` — 순수 단위 테스트(스프링 컨텍스트 불필요): `EconomyProperties`를 직접 생성, `SharedQualityPoolService`는 mockito mock:
```kotlin
package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ModelRoutingServiceTest : FunSpec({
    test("routing disabled -> NANO, no pool consumption") {
        val pool = mock<SharedQualityPoolService>()
        val props = EconomyProperties(premiumRoutingEnabled = false, premiumDeltaPt = BigDecimal("2.0"),
            nanoModelName = "nano-1", premiumModelName = "pro-1")
        val d = ModelRoutingService(pool, props).selectAndConsume()
        d.tier shouldBe ModelTier.NANO
        d.modelOverride shouldBe "nano-1"
        verifyNoInteractions(pool)
    }

    test("routing enabled + pool sufficient -> PREMIUM") {
        val pool = mock<SharedQualityPoolService>()
        whenever(pool.tryConsumePremium(any())).thenReturn(true)
        val props = EconomyProperties(premiumRoutingEnabled = true, premiumDeltaPt = BigDecimal("2.0"),
            nanoModelName = "nano-1", premiumModelName = "pro-1")
        val d = ModelRoutingService(pool, props).selectAndConsume()
        d.tier shouldBe ModelTier.PREMIUM
        d.modelOverride shouldBe "pro-1"
    }

    test("routing enabled + pool insufficient -> NANO downgrade") {
        val pool = mock<SharedQualityPoolService>()
        whenever(pool.tryConsumePremium(any())).thenReturn(false)
        val props = EconomyProperties(premiumRoutingEnabled = true, premiumDeltaPt = BigDecimal("2.0"),
            nanoModelName = "nano-1", premiumModelName = "pro-1")
        val d = ModelRoutingService(pool, props).selectAndConsume()
        d.tier shouldBe ModelTier.NANO
        d.modelOverride shouldBe "nano-1"
    }

    test("blank model name maps to null override") {
        val pool = mock<SharedQualityPoolService>()
        val d = ModelRoutingService(pool, EconomyProperties(premiumRoutingEnabled = false)).selectAndConsume()
        d.modelOverride shouldBe null
    }
})
```
구현 노트: `@Transactional(MANDATORY)`인 메서드들을 mock으로 대체하므로 트랜잭션 불필요(순수 객체 호출). `EconomyProperties`는 data class라 명명인자로 일부만 지정 가능.

- [ ] **Step 2: Run test (FAIL).** `./gradlew test --tests "*ModelRoutingServiceTest"`

- [ ] **Step 3: Implement ModelTier + ModelRoutingService**

`ModelTier.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.service

enum class ModelTier { NANO, PREMIUM }
```

`ModelRoutingService.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

data class RoutingDecision(val tier: ModelTier, val modelOverride: String?)

@Service
class ModelRoutingService(
    private val sharedQualityPoolService: SharedQualityPoolService,
    private val economyProperties: EconomyProperties,
) {
    /**
     * 서버 전용 모델 라우팅 결정(클라이언트 선택 불가, §10.1).
     * 긴급중지(premiumRoutingEnabled=false)면 항상 NANO. 그 외엔 풀에서 premiumDelta 조건부 차감 성공 시 PREMIUM, 실패 시 NANO 강등(I8).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun selectAndConsume(): RoutingDecision {
        if (!economyProperties.premiumRoutingEnabled) return decision(ModelTier.NANO)
        return if (sharedQualityPoolService.tryConsumePremium(economyProperties.premiumDeltaPt)) {
            decision(ModelTier.PREMIUM)
        } else {
            decision(ModelTier.NANO)
        }
    }

    private fun decision(tier: ModelTier): RoutingDecision {
        val name = when (tier) {
            ModelTier.NANO -> economyProperties.nanoModelName
            ModelTier.PREMIUM -> economyProperties.premiumModelName
        }
        return RoutingDecision(tier, name.ifBlank { null })
    }
}
```

- [ ] **Step 4: Run test (PASS).**

- [ ] **Step 5: Commit**
```bash
git add -A && git commit -m "feat(economy): add model routing service (premium gate + nano downgrade)"
```

---

### Task 3: LlmProvider 모델 override + ChatService 라우팅 결합

**Files:**
- Modify: `domain/chat/service/llm/LlmProvider.kt`
- Modify: `domain/chat/service/llm/GeminiLlmProvider.kt`
- Modify: `domain/chat/service/llm/OpenAiLlmProvider.kt`
- Modify: `domain/chat/service/ChatService.kt`
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatServiceTest.kt`

**Interfaces:**
- Consumes: `ModelRoutingService.selectAndConsume(): RoutingDecision`.
- Produces:
  - `LlmProvider.stream(messages: List<LlmMessage>, modelOverride: String? = null): Flux<String>` (기본 인자 null → 기존 호출부 호환).
  - 진입 트랜잭션에서 `routingDecision = modelRoutingService.selectAndConsume()` 호출, `StreamContext`에 `modelOverride` 추가, `llmProvider.stream(ctx.providerMessages, ctx.modelOverride)`.

- [ ] **Step 1: Update ChatServiceTest (failing) — inject ModelRoutingService mock + assert override pass-through**

`ChatServiceTest.kt`:
- 생성자/필드에 `private lateinit var modelRoutingService: ModelRoutingService` 추가, `setup`에서 `modelRoutingService = mock()` + `ChatService(... modelRoutingService = modelRoutingService ...)`.
- 기본 스텁: `whenever(modelRoutingService.selectAndConsume()).thenReturn(RoutingDecision(ModelTier.NANO, null))`.
- 기존 성공 스트림 테스트의 `whenever(llmProvider.stream(any())).thenReturn(...)`를 `whenever(llmProvider.stream(any(), anyOrNull())).thenReturn(...)`로, `verify(llmProvider).stream(...)`를 2-인자 형태로 갱신.
- 신규 테스트: premium 결정 시 모델 override 전달:
```kotlin
test("passes premium model override from routing to provider") {
    whenever(modelRoutingService.selectAndConsume())
        .thenReturn(RoutingDecision(ModelTier.PREMIUM, "pro-1"))
    whenever(llmProvider.stream(any(), eq("pro-1"))).thenReturn(Flux.just("hi"))
    // conversation/owner 시드 스텁(기존 성공 테스트와 동일)
    StepVerifier.create(chatService.stream(1L, 1L, "msg-pro", "hello")).thenConsumeWhile { true }.verifyComplete()
    verify(llmProvider).stream(any(), eq("pro-1"))
}
```
구현 노트: 기존 테스트의 conversation/repo 스텁 구성을 그대로 따른다. `anyOrNull()`은 mockito-kotlin import.

- [ ] **Step 2: Run test (FAIL — 시그니처/의존성 불일치).** `./gradlew test --tests "*chat.service.ChatServiceTest"`

- [ ] **Step 3: Update LlmProvider interface + providers**

`LlmProvider.kt`:
```kotlin
    /** Streams response chunks. modelOverride 가 non-null 이면 해당 모델로 호출(라우팅), null 이면 기본 모델. */
    fun stream(messages: List<LlmMessage>, modelOverride: String? = null): Flux<String>
```
(`generate`는 변경 없음.)

`GeminiLlmProvider.kt`/`OpenAiLlmProvider.kt` — `stream` 시그니처에 `modelOverride: String? = null` 추가, override 있을 때만 `OpenAiChatOptions` 로 모델 지정:
```kotlin
import org.springframework.ai.openai.OpenAiChatOptions
...
    override fun stream(messages: List<LlmMessage>, modelOverride: String?): Flux<String> {
        val springMessages = messages.map { it.toSpringAiMessage() }
        val prompt = if (modelOverride.isNullOrBlank()) Prompt(springMessages)
            else Prompt(springMessages, OpenAiChatOptions.builder().model(modelOverride).build())
        return streamingChatModel.stream(prompt)
            .map { it.results.firstOrNull()?.output?.text.orEmpty() }
            .filter { it.isNotEmpty() }
    }
```
구현 노트: 구현 시 `org.springframework.ai.openai.OpenAiChatOptions` 임포트 가용성 확인(두 프로바이더 모두 OpenAI 호환 스타터 사용). 빌더 API가 다르면 해당 버전의 `OpenAiChatOptions` 생성 방식으로 맞춘다. override가 null/blank면 기존 동작과 100% 동일.

- [ ] **Step 4: Wire ChatService**

`ChatService.kt`:
- 생성자에 `private val modelRoutingService: ModelRoutingService` 추가.
- 진입 트랜잭션(`transactionTemplate.execute { ... }`) 안, `beginReservation` 다음 줄에:
```kotlin
    val routing = modelRoutingService.selectAndConsume()
    StreamContext(assistant.id, settlementId, providerMessages, routing.modelOverride)
```
- `StreamContext` data class에 `val modelOverride: String?` 필드 추가.
- 스트림 빌드부의 `llmProvider.stream(ctx.providerMessages)` → `llmProvider.stream(ctx.providerMessages, ctx.modelOverride)`.

- [ ] **Step 5: Run ChatServiceTest (PASS), then full suite**
```bash
./gradlew test --tests "com.wnl.cashchat.api.domain.chat.service.ChatServiceTest"
./gradlew test
```
Expected: 전체 그린.

- [ ] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(chat): route chat through model tier with shared quality pool gate"
```

---

## Self-Review (작성자 점검 완료)

- **Spec coverage:** §9 모델 품질 공용 풀(pool ≥ premiumDelta 게이트, 부족 시 nano 강등), I8/I9, §10.1(서버 전용 라우팅), 긴급중지 토글(premiumRoutingEnabled 재사용) — 모두 Task로 매핑. 비용 상한·throttle은 비범위로 명시(P2).
- **확정 결정 반영:** 조건부 차감(WHERE balance>=delta), 결정+차감 로직 + 모델명 설정값(기본 빈→override 없음), 비용 상한 제외.
- **Type consistency:** `ModelTier`/`RoutingDecision`/`tryConsumePremium`/`tryDebit`/`selectAndConsume`/`modelOverride` Task 간 일치. `LlmProvider.stream` 기본 인자로 기존 호출부 호환.
- **No placeholders:** 모든 코드 단계 실제 코드 포함. 테스트 보일러플레이트는 기존 컨벤션 참조로 위임. provider `OpenAiChatOptions` API는 구현 시 버전 확인.
- **불변 제약:** auth/ad 미변경, premiumDelta 비공개(EconomyProperties ZERO+env), shared_quality_pool은 V7 기존 테이블(마이그레이션 불필요).
