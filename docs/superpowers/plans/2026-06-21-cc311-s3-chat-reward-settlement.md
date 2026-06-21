# CC-311 S3 — Chat Reward Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 보상형 채팅을 구현한다 — 채팅 진입 시 Energy 1개 원자적 예약, 답변 정상 저장 시 단일 트랜잭션 정산(Energy−1·pendingCashablePt+1·evolutionExp+1·sharedQualityPool 적립), `reward_settled` SSE, 실패 시 환불, messageId 멱등, 정산 복구 조회 API.

**Architecture:** 기존 `ChatService.stream`(Flux)을 리팩터한다. 진입(블로킹 트랜잭션)에서 정산 레코드 생성 + Energy 예약을 원자적으로 수행하고, 리액티브 파이프라인에서 `meta → delta* → (정산)reward_settled → done` 순으로 이벤트를 emit한다. 정산/환불은 정산 레코드 상태(ENERGY_RESERVED/GENERATING→SETTLED/REFUNDED)로 멱등 보장한다. 신규 `chat_reward_settlement`·`shared_quality_pool` 테이블(Flyway V7)을 추가한다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11(WebFlux SSE + 블로킹 JPA), Project Reactor, JPA(`ddl-auto: validate`), Flyway, Kotest FunSpec + TestContainers(MySQL 8.4) + mockito-kotlin.

## Global Constraints

모든 Task 요구사항에 암묵 포함. 값은 verbatim.

- **로그인/인증 불변** — `domain/auth/`, JWT 필터, `SecurityConfig` 손대지 않는다. 모든 재화는 `userId` 귀속.
- **광고 SSV·nonce·일일한도 불변** — `domain/ad/` 손대지 않는다(S2에서 이미 Energy 전환 완료).
- **내부 원가/마진 수치 비공개** — `NANO_COST_PT`/`SHARED_POOL_MARGIN_PT`/`ENERGY_BACKING_PT` 등 실제 수치를 소스·커밋 문서에 하드코딩 금지. `sharedPoolMarginPerChat`는 `EconomyProperties`에 **비민감 기본값(BigDecimal ZERO)** 으로 두고 실값은 비공개 env(`app.economy.shared-pool-margin-per-chat`)로 주입. 테스트는 자체 값을 주입해 검증.
- **`UserWallet`/`grantEnergy` 불변** — S1 엔티티의 기존 메서드(`reserveEnergy`/`consumeReserved`/`refundReserved`/`addPendingPt`/`addExp`)를 사용만 한다. cap 초과=throw 유지.
- **불변식(명세 13):** I1(채팅⇒available≥1), I2(완료 시 −1/+1/+1), I3(동일 messageId 최대 1회 보상), I9(sharedQualityPool ≥ 0), I10(메시지 길이·의미 무관), I11(중복·실패·위조 보상 없음).
- **보상 제외(명세 6.3, 구조적만):** 중복 messageId·서버 재시도 중복·무료 재생성·Energy 예약 실패·AI 호출 전 실패·정책 위반 차단·답변 미생성·위조 요청. **콘텐츠(길이/의미/반복/이모지)로는 제외하지 않는다.**
- **멱등 키:** 채팅 `messageId`(클라 UUID, 요청 body). DB `UNIQUE(user_id, message_id, reward_type)`.
- **기존 `EnergyService.grant(...)` 시그니처 불변**(S1). 신규 `reserve`/`refund` 추가.
- **오류 코드(명세 9):** 422 `ENERGY_INSUFFICIENT`, 409 `REWARD_ALREADY_SETTLED`, 503 `FEATURE_DISABLED`. 기존 `ErrorResponse(code,message)` + 도메인 `@RestControllerAdvice` 패턴 사용.
- **엔드포인트 최소 변경:** 기존 `POST /api/v1/chat/stream` 유지, 요청 body에 `messageId` 추가. 신규 `GET /api/v1/messages/{messageId}/settlement`.
- **범위 밖(명세대로 연기):** 응답 재생성(4.3)·취소 엔드포인트(4.4)·rate limit(429)·거래내역/Energy발행내역 조회(P1)·pending→confirmed 배치(P1)·premium 라우팅(S5)·operationalLoss 전용 테이블/대시보드(P2, S3에선 환불+로그만).

---

## File Structure

- **T1 데이터 토대:** `SettlementStatus`/`ChatRewardType` enum, `ChatRewardSettlement`·`SharedQualityPool` 엔티티 + 레포, Flyway `V7__chat_reward.sql`.
- **T2 풀·설정:** `EconomyProperties`(+`sharedPoolMarginPerChat`), `SharedQualityPoolService`(싱글톤 행 보장 + 원자적 적립).
- **T3 Energy 예약/환불:** `EnergyService`(+`reserve`/`refund`), `EnergyInsufficientException`.
- **T4 정산 서비스:** `ChatRewardSettlementService`(진입 reserve+레코드 생성 / 성공 settle / 실패 refund, 멱등), `RewardAlreadySettledException`.
- **T5 ChatService 스트림 리팩터:** `ChatStreamEvent` sealed, `ChatService.stream`(reserve→event 파이프라인→settle/refund), `ChatStreamRequest`(+messageId).
- **T6 컨트롤러 SSE + 오류:** `ChatController`(meta/delta/reward_settled/done 매핑), `ChatExceptionHandler`(+ENERGY_INSUFFICIENT 422, REWARD_ALREADY_SETTLED 409, FEATURE_DISABLED 503).
- **T7 정산 조회 API:** `GET /messages/{messageId}/settlement` — `MessageSettlementController` + 응답 + 조회.

빌드 순서 **T1→T2→T3→T4→T5→T6→T7** (각 후행이 선행 산출물에 의존).

---

### Task 1: 데이터 토대 (enum · 엔티티 · 레포 · Flyway V7)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/entity/SettlementStatus.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/entity/ChatRewardType.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/entity/ChatRewardSettlement.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/SharedQualityPool.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/persistence/repository/ChatRewardSettlementRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/SharedQualityPoolRepository.kt`
- Create: `apps/backend/src/main/resources/db/migration/V7__chat_reward.sql`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/persistence/ChatRewardPersistenceIntegrationTest.kt`

**Interfaces:**
- Produces: `ChatRewardSettlement`(id, userId, messageId:String, rewardType:ChatRewardType, status:SettlementStatus, conversationId:Long, assistantMessageId:Long?, energyDelta:Long, pendingPtDelta:Long, evolutionExpDelta:Long, settledAt:Instant?) with `markSettled(...)`/`markRefunded()`/`markFailed()`/`markGenerating()` mutators.
- Produces: `SharedQualityPool`(id, balance:BigDecimal).
- Produces repos with `findByUserIdAndMessageIdAndRewardType`, `findByIdForUpdate`(@Lock), `insertSingletonIfAbsent`, `accrue`, `findByMessageId`.

- [ ] **Step 1: enum 2개 작성**

```kotlin
// SettlementStatus.kt
package com.wnl.cashchat.api.domain.chat.persistence.entity
enum class SettlementStatus { NOT_STARTED, ENERGY_RESERVED, GENERATING, SETTLED, REFUNDED, FAILED }
```
```kotlin
// ChatRewardType.kt
package com.wnl.cashchat.api.domain.chat.persistence.entity
enum class ChatRewardType { CHAT_REWARD }
```

- [ ] **Step 2: `ChatRewardSettlement` 엔티티 작성** — S1 `UserWallet` 스타일(backing var + `private set` + 상태전이 메서드).

```kotlin
package com.wnl.cashchat.api.domain.chat.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "chat_reward_settlement",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_chat_reward_settlement_user_msg_type",
        columnNames = ["user_id", "message_id", "reward_type"],
    )],
    indexes = [Index(name = "idx_chat_reward_settlement_message", columnList = "message_id")],
)
class ChatRewardSettlement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Column(name = "message_id", nullable = false) val messageId: String,
    @Enumerated(EnumType.STRING) @Column(name = "reward_type", nullable = false, length = 30)
    val rewardType: ChatRewardType = ChatRewardType.CHAT_REWARD,
    @Column(name = "conversation_id", nullable = false) val conversationId: Long,
) : BaseEntity() {
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30)
    var status: SettlementStatus = SettlementStatus.ENERGY_RESERVED
        private set
    @Column(name = "assistant_message_id") var assistantMessageId: Long? = null
        private set
    @Column(name = "energy_delta", nullable = false) var energyDelta: Long = 0
        private set
    @Column(name = "pending_pt_delta", nullable = false) var pendingPtDelta: Long = 0
        private set
    @Column(name = "evolution_exp_delta", nullable = false) var evolutionExpDelta: Long = 0
        private set
    @Column(name = "settled_at") var settledAt: Instant? = null
        private set

    fun markGenerating() { status = SettlementStatus.GENERATING }
    fun markSettled(assistantMessageId: Long, energyDelta: Long, pendingPtDelta: Long, evolutionExpDelta: Long, settledAt: Instant) {
        this.assistantMessageId = assistantMessageId
        this.energyDelta = energyDelta; this.pendingPtDelta = pendingPtDelta; this.evolutionExpDelta = evolutionExpDelta
        this.settledAt = settledAt; status = SettlementStatus.SETTLED
    }
    fun markRefunded(assistantMessageId: Long?) {
        this.assistantMessageId = assistantMessageId; status = SettlementStatus.REFUNDED
    }
    fun markFailed(assistantMessageId: Long?) {
        this.assistantMessageId = assistantMessageId; status = SettlementStatus.FAILED
    }
}
```

- [ ] **Step 3: `SharedQualityPool` 엔티티 작성** (전역 단일 행; balance는 BigDecimal).

```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "shared_quality_pool")
class SharedQualityPool(
    @Id val id: Long = SINGLETON_ID,
    @Column(name = "balance", nullable = false, precision = 18, scale = 4)
    var balance: BigDecimal = BigDecimal.ZERO,
) : BaseEntity() {
    companion object { const val SINGLETON_ID = 1L }
}
```

- [ ] **Step 4: 레포 2개 작성**

```kotlin
// ChatRewardSettlementRepository.kt
package com.wnl.cashchat.api.domain.chat.persistence.repository
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardSettlement
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface ChatRewardSettlementRepository : JpaRepository<ChatRewardSettlement, Long> {
    fun findByUserIdAndMessageIdAndRewardType(userId: Long, messageId: String, rewardType: ChatRewardType): ChatRewardSettlement?
    fun findByMessageId(messageId: String): ChatRewardSettlement?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatRewardSettlement s where s.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ChatRewardSettlement?
}
```
```kotlin
// SharedQualityPoolRepository.kt — 싱글톤 행 멱등 생성 + 원자적 적립(I9: balance>=0 유지; S3는 가산만)
package com.wnl.cashchat.api.domain.economy.persistence.repository
import com.wnl.cashchat.api.domain.economy.persistence.entity.SharedQualityPool
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface SharedQualityPoolRepository : JpaRepository<SharedQualityPool, Long> {
    @Modifying
    @Query(
        value = "INSERT INTO shared_quality_pool (id, balance, created_at, updated_at) " +
            "VALUES (1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE id = id",
        nativeQuery = true,
    )
    fun insertSingletonIfAbsent(): Int

    @Modifying
    @Query(
        value = "UPDATE shared_quality_pool SET balance = balance + :amount, updated_at = CURRENT_TIMESTAMP(6) WHERE id = 1",
        nativeQuery = true,
    )
    fun accrue(@Param("amount") amount: BigDecimal): Int
}
```

- [ ] **Step 5: Flyway `V7__chat_reward.sql` 작성** — 엔티티와 정확히 일치(boot validate 통과). assistant_message_id는 nullable, 약결합(FK 없음).

```sql
-- V7: 채팅 보상 정산 · 모델 품질 공용 풀

CREATE TABLE chat_reward_settlement (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    message_id          VARCHAR(255) NOT NULL,
    reward_type         VARCHAR(30)  NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    conversation_id     BIGINT       NOT NULL,
    assistant_message_id BIGINT      NULL,
    energy_delta        BIGINT       NOT NULL,
    pending_pt_delta    BIGINT       NOT NULL,
    evolution_exp_delta BIGINT       NOT NULL,
    settled_at          TIMESTAMP(6) NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_chat_reward_settlement_user_msg_type UNIQUE (user_id, message_id, reward_type),
    CONSTRAINT fk_chat_reward_settlement_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_chat_reward_settlement_message ON chat_reward_settlement (message_id);

CREATE TABLE shared_quality_pool (
    id         BIGINT         NOT NULL,
    balance    DECIMAL(18, 4) NOT NULL,
    created_at TIMESTAMP(6)   NOT NULL,
    updated_at TIMESTAMP(6)   NOT NULL,
    PRIMARY KEY (id)
);
```

- [ ] **Step 6: 영속성 통합 테스트 작성** (TestContainers, S1 `WalletPersistenceIntegrationTest` 스타일). 검증: (a) settlement 저장·`findByUserIdAndMessageIdAndRewardType` 조회·상태전이 영속, (b) `uq_chat_reward_settlement_user_msg_type` 중복 INSERT 시 `DataIntegrityViolationException`, (c) `insertSingletonIfAbsent` 두 번 호출 시 행 1개, (d) `accrue(BigDecimal("0.32"))` 2회 후 balance `0.6400`. (settlement은 users FK가 있으므로 테스트는 User를 먼저 저장.)

- [ ] **Step 7: 부팅 검증 + 테스트**

Run: `cd apps/backend && ./gradlew test --tests "*.ChatRewardPersistenceIntegrationTest"`
Expected: PASS (Hibernate `validate` 가 V7 스키마와 엔티티 일치 확인).

- [ ] **Step 8: 커밋** — `feat(chat): add chat_reward_settlement and shared_quality_pool entities + V7`

---

### Task 2: 공용 풀 서비스 + 설정

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/properties/EconomyProperties.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/service/SharedQualityPoolService.kt`
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/properties/EconomyPropertiesTest.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/service/SharedQualityPoolServiceIntegrationTest.kt`

**Interfaces:**
- Produces: `EconomyProperties.sharedPoolMarginPerChat: BigDecimal`(기본 `BigDecimal.ZERO`, 비민감).
- Produces: `SharedQualityPoolService.accrue(amount: BigDecimal)`(@Transactional(propagation=MANDATORY), 싱글톤 보장 후 원자적 가산; amount ≤ 0이면 no-op).

- [ ] **Step 1: `EconomyProperties`에 margin 추가** — 기본값은 **실제 수치가 아닌** ZERO(실값은 비공개 env로 주입).

```kotlin
import java.math.BigDecimal
import jakarta.validation.constraints.DecimalMin
// data class 본문에 추가:
@field:DecimalMin("0.0") val sharedPoolMarginPerChat: BigDecimal = BigDecimal.ZERO,
```

- [ ] **Step 2: `EconomyPropertiesTest` 갱신** — 신규 필드 기본값 단언 추가: `props.sharedPoolMarginPerChat shouldBe BigDecimal.ZERO`.

- [ ] **Step 3: 실패 테스트 작성** — `SharedQualityPoolServiceIntegrationTest`(@SpringBootTest, TestContainers): `transactionTemplate.executeWithoutResult { service.accrue(BigDecimal("0.32")) }` 2회 후 풀 balance `BigDecimal("0.6400")`; `accrue(BigDecimal.ZERO)`는 no-op(행 그대로 또는 미생성). `app.economy.shared-pool-margin-per-chat`는 테스트에서 불필요(직접 amount 전달).

- [ ] **Step 4: 실패 확인** — Run: `cd apps/backend && ./gradlew test --tests "*.SharedQualityPoolServiceIntegrationTest"` → FAIL(서비스 없음).

- [ ] **Step 5: `SharedQualityPoolService` 작성**

```kotlin
package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.persistence.repository.SharedQualityPoolRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class SharedQualityPoolService(
    private val sharedQualityPoolRepository: SharedQualityPoolRepository,
) {
    /** 싱글톤 풀 행을 멱등 보장한 뒤 원자적 가산한다(I9: 가산만이므로 음수 불가). amount<=0이면 적립하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun accrue(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) return
        sharedQualityPoolRepository.insertSingletonIfAbsent()
        sharedQualityPoolRepository.accrue(amount)
    }
}
```

- [ ] **Step 6: 테스트 통과 확인** — Run 위 명령 → PASS.

- [ ] **Step 7: 커밋** — `feat(economy): add SharedQualityPoolService + non-sensitive margin property`

---

### Task 3: Energy 예약/환불 서비스

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/service/EnergyService.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/exception/EnergyInsufficientException.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/service/EnergyReserveRefundIntegrationTest.kt`

**Interfaces:**
- Produces: `EnergyService.reserve(userId: Long, idempotencyKey: String): WalletLedger` — `@Transactional`, `ensureForUpdate`(S1, lazy bootstrap) 후 `available<1`이면 `EnergyInsufficientException`, else `reserveEnergy(1)` + 원장 `ENERGY_RESERVED`(delta -1). 멱등(키 존재 시 기존 반환).
- Produces: `EnergyService.refund(userId: Long, idempotencyKey: String): WalletLedger` — `@Transactional`, `refundReserved(1)` + 원장 `ENERGY_REFUNDED`(delta +1). 멱등.
- Produces: `EnergyInsufficientException`.

- [ ] **Step 1: 실패 테스트 작성** — `EnergyReserveRefundIntegrationTest`(TestContainers, `EnergyServiceIntegrationTest` 스타일, `newUser()`로 user+`ensureInitialized`):
  - reserve 성공: 사전 `grant(userId,5,REWARDED_AD,exp,"seed")` 후 `reserve(userId,"chat:reserve:m1")` → energyAvailable 4, energyReserved 1, 원장 ENERGY_RESERVED 1건.
  - reserve 부족: grant 없이 `reserve` → `EnergyInsufficientException`.
  - reserve 멱등: 동일 키 2회 → reserved 1, ENERGY_RESERVED 원장 1건.
  - refund: reserve 후 `refund(userId,"chat:refund:m1")` → energyAvailable 5, energyReserved 0, ENERGY_REFUNDED 원장 1건.

- [ ] **Step 2: 실패 확인** — Run: `cd apps/backend && ./gradlew test --tests "*.EnergyReserveRefundIntegrationTest"` → FAIL.

- [ ] **Step 3: `EnergyInsufficientException` 작성**

```kotlin
package com.wnl.cashchat.api.domain.economy.exception
class EnergyInsufficientException : RuntimeException("Not enough energy to start a chat")
```

- [ ] **Step 4: `EnergyService`에 reserve/refund 추가** — 기존 `grant`와 동일 패턴(lock-first → 멱등 키 조회 → 변경 → 원장 INSERT). `balanceAfter`는 `energyAvailable`.

```kotlin
// import: com.wnl.cashchat.api.domain.economy.exception.EnergyInsufficientException
@Transactional
fun reserve(userId: Long, idempotencyKey: String): WalletLedger {
    val wallet = walletService.ensureForUpdate(userId)
    walletLedgerRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }
    if (wallet.energyAvailable < 1) throw EnergyInsufficientException()
    wallet.reserveEnergy(1)
    return walletLedgerRepository.save(
        WalletLedger(userId = userId, type = WalletTxType.ENERGY_RESERVED, delta = -1,
            balanceAfter = wallet.energyAvailable, referenceId = null, idempotencyKey = idempotencyKey),
    )
}

@Transactional
fun refund(userId: Long, idempotencyKey: String): WalletLedger {
    val wallet = walletService.ensureForUpdate(userId)
    walletLedgerRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }
    wallet.refundReserved(1)
    return walletLedgerRepository.save(
        WalletLedger(userId = userId, type = WalletTxType.ENERGY_REFUNDED, delta = 1,
            balanceAfter = wallet.energyAvailable, referenceId = null, idempotencyKey = idempotencyKey),
    )
}
```

- [ ] **Step 5: 테스트 통과 확인** — Run 위 명령 → PASS.

- [ ] **Step 6: 커밋** — `feat(economy): add EnergyService.reserve/refund with ledger + idempotency`

---

### Task 4: 채팅 보상 정산 서비스 (원자적·멱등)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatRewardSettlementService.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/SettlementResult.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/RewardAlreadySettledException.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatRewardSettlementServiceIntegrationTest.kt`

**Interfaces:**
- Produces: `SettlementResult`(messageId, status, energyDelta, pendingPtDelta, evolutionExpDelta, energyBalance, pendingCashablePt, evolutionExp, settledAt).
- Produces: `ChatRewardSettlementService`:
  - `beginReservation(userId, conversationId, messageId): Long`(@Transactional) — 기존 레코드(SETTLED/in-flight)면 `RewardAlreadySettledException`; 신규 레코드(ENERGY_RESERVED) INSERT + `energyService.reserve`(같은 tx). unique 충돌 시 `RewardAlreadySettledException`. settlement.id 반환.
  - `settle(userId, settlementId, assistantMessageId): SettlementResult`(@Transactional, 멱등) — 이미 SETTLED면 결과 반환; else consume+pt+exp+pool+원장 3건+markSettled.
  - `refund(userId, settlementId, assistantMessageId): Unit`(@Transactional, 멱등) — SETTLED/REFUNDED면 no-op; else energyService.refund + markRefunded.

- [ ] **Step 1: `SettlementResult`·`RewardAlreadySettledException` 작성**

```kotlin
// SettlementResult.kt
package com.wnl.cashchat.api.domain.chat.service
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import java.time.Instant
data class SettlementResult(
    val messageId: String, val status: SettlementStatus,
    val energyDelta: Long, val pendingPtDelta: Long, val evolutionExpDelta: Long,
    val energyBalance: Long, val pendingCashablePt: Long, val evolutionExp: Long,
    val settledAt: Instant?,
)
```
```kotlin
// RewardAlreadySettledException.kt
package com.wnl.cashchat.api.domain.chat.exception
class RewardAlreadySettledException(val messageId: String) :
    RuntimeException("Reward already settled or in progress for messageId=$messageId")
```

- [ ] **Step 2: 실패 테스트 작성** — `ChatRewardSettlementServiceIntegrationTest`(@SpringBootTest, TestContainers, `@DynamicPropertySource`에 `app.economy.shared-pool-margin-per-chat=0.32` 주입). 시나리오(헬퍼: user 저장 + `walletService.ensureInitialized` + conversation 저장 + assistant ChatMessage 저장):
  - 정상: `grant(userId,5,...)` → `beginReservation(userId,convId,"m1")` → reserved 1/available 4 → `settle(userId,id,assistantMsgId)` → available 4·reserved 0·pendingCashablePt 1·evolutionExp 1, settlement SETTLED, 풀 balance `0.3200`, 원장: RESERVED+CONSUMED+PENDING+EXP(4건).
  - 멱등 settle: `settle` 2회 → pendingCashablePt 1, 풀 `0.3200` 유지(중복 적립 없음).
  - 중복 messageId: settle 후 동일 messageId `beginReservation` → `RewardAlreadySettledException`.
  - 환불: `beginReservation` 후 `refund` → available 5·reserved 0, settlement REFUNDED, pendingCashablePt 0.
  - 예약 실패: energy 0(grant 안 함)에서 `beginReservation` → `EnergyInsufficientException`, settlement 레코드 0건(롤백).
  (settle/refund/beginReservation은 각자 @Transactional이므로 테스트에서 직접 호출 가능.)

- [ ] **Step 3: 실패 확인** — Run: `cd apps/backend && ./gradlew test --tests "*.ChatRewardSettlementServiceIntegrationTest"` → FAIL.

- [ ] **Step 4: `ChatRewardSettlementService` 작성**

```kotlin
package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.exception.RewardAlreadySettledException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardSettlement
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardType
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatRewardSettlementRepository
import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import com.wnl.cashchat.api.domain.economy.service.SharedQualityPoolService
import com.wnl.cashchat.api.domain.economy.service.WalletService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ChatRewardSettlementService(
    private val chatRewardSettlementRepository: ChatRewardSettlementRepository,
    private val energyService: EnergyService,
    private val walletService: WalletService,
    private val sharedQualityPoolService: SharedQualityPoolService,
    private val walletLedgerRepository: WalletLedgerRepository,
    private val economyProperties: EconomyProperties,
) {
    @Transactional
    fun beginReservation(userId: Long, conversationId: Long, messageId: String): Long {
        chatRewardSettlementRepository
            .findByUserIdAndMessageIdAndRewardType(userId, messageId, ChatRewardType.CHAT_REWARD)
            ?.let { throw RewardAlreadySettledException(messageId) }
        val settlement = try {
            chatRewardSettlementRepository.saveAndFlush(
                ChatRewardSettlement(userId = userId, messageId = messageId, conversationId = conversationId),
            )
        } catch (e: DataIntegrityViolationException) {
            throw RewardAlreadySettledException(messageId) // 동시 동일 messageId
        }
        energyService.reserve(userId, "chat:reserve:$messageId") // 부족 시 EnergyInsufficientException → 전체 롤백
        return settlement.id
    }

    @Transactional
    fun settle(userId: Long, settlementId: Long, assistantMessageId: Long): SettlementResult {
        val settlement = chatRewardSettlementRepository.findByIdForUpdate(settlementId)
            ?: error("settlement $settlementId not found")
        val wallet = walletService.getForUpdate(userId)
        if (settlement.status == SettlementStatus.SETTLED) return settlement.toResult(wallet)

        val messageId = settlement.messageId
        wallet.consumeReserved(1)
        wallet.addPendingPt(economyProperties.chatRewardPt)
        wallet.addExp(economyProperties.evolutionExpPerChat)
        sharedQualityPoolService.accrue(economyProperties.sharedPoolMarginPerChat)

        ledger(userId, WalletTxType.ENERGY_CONSUMED, -1, wallet.energyAvailable, messageId, "chat:consume:$messageId")
        ledger(userId, WalletTxType.POINT_PENDING_GRANTED, economyProperties.chatRewardPt, wallet.pendingCashablePt, messageId, "chat:pt:$messageId")
        ledger(userId, WalletTxType.EXP_GRANTED, economyProperties.evolutionExpPerChat, wallet.evolutionExp, messageId, "chat:exp:$messageId")

        settlement.markSettled(
            assistantMessageId = assistantMessageId,
            energyDelta = -1, pendingPtDelta = economyProperties.chatRewardPt, evolutionExpDelta = economyProperties.evolutionExpPerChat,
            settledAt = Instant.now(),
        )
        return settlement.toResult(wallet)
    }

    @Transactional
    fun refund(userId: Long, settlementId: Long, assistantMessageId: Long?) {
        val settlement = chatRewardSettlementRepository.findByIdForUpdate(settlementId)
            ?: error("settlement $settlementId not found")
        if (settlement.status == SettlementStatus.SETTLED || settlement.status == SettlementStatus.REFUNDED) return
        energyService.refund(userId, "chat:refund:${settlement.messageId}")
        settlement.markRefunded(assistantMessageId)
    }

    private fun ledger(userId: Long, type: WalletTxType, delta: Long, balanceAfter: Long, referenceId: String, key: String) {
        walletLedgerRepository.findByIdempotencyKey(key)?.let { return }
        walletLedgerRepository.save(WalletLedger(userId = userId, type = type, delta = delta,
            balanceAfter = balanceAfter, referenceId = referenceId, idempotencyKey = key))
    }

    private fun ChatRewardSettlement.toResult(wallet: UserWallet) =
        SettlementResult(
            messageId = messageId, status = status,
            energyDelta = energyDelta, pendingPtDelta = pendingPtDelta, evolutionExpDelta = evolutionExpDelta,
            energyBalance = wallet.energyAvailable, pendingCashablePt = wallet.pendingCashablePt, evolutionExp = wallet.evolutionExp,
            settledAt = settledAt,
        )
}
```

- [ ] **Step 5: 테스트 통과 확인** — Run 위 명령 → PASS.

- [ ] **Step 6: 커밋** — `feat(chat): add ChatRewardSettlementService (atomic settle/refund, idempotent)`

---

### Task 5: ChatService 스트림 리팩터 (예약 → 이벤트 파이프라인 → 정산/환불)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/exception/FeatureDisabledException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatStreamEvent.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatService.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/request/ChatStreamRequest.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatServiceStreamTest.kt`

**Interfaces:**
- Produces: sealed `ChatStreamEvent`: `Meta(messageId, energyReserved)`, `Delta(text)`, `RewardSettled(result: SettlementResult)`, `Done(finishReason: String)`.
- Produces: `ChatService.stream(userId, conversationId, messageId, content): Flux<ChatStreamEvent>` (시그니처 변경: `messageId` 추가).
- Produces: `ChatStreamRequest.messageId: String`(@field:NotBlank).
- Consumes: `ChatRewardSettlementService.beginReservation/settle/refund`, `EconomyProperties.rewardChatEnabled`, `FeatureDisabledException`, `llmProvider.stream`.

- [ ] **Step 1: `FeatureDisabledException` 작성**

```kotlin
package com.wnl.cashchat.api.domain.economy.exception
class FeatureDisabledException(val feature: String) : RuntimeException("Feature disabled: $feature")
```

- [ ] **Step 2: `ChatStreamEvent` 작성**

```kotlin
package com.wnl.cashchat.api.domain.chat.service
sealed interface ChatStreamEvent {
    data class Meta(val messageId: String, val energyReserved: Long) : ChatStreamEvent
    data class Delta(val text: String) : ChatStreamEvent
    data class RewardSettled(val result: SettlementResult) : ChatStreamEvent
    data class Done(val finishReason: String) : ChatStreamEvent
}
```

- [ ] **Step 3: `ChatStreamRequest`에 messageId 추가** — 생성자에 추가(`conversationId`, `message` 유지).

```kotlin
@JsonProperty("messageId")
@field:NotBlank
@field:Schema(description = "Client-generated idempotency key for this user message.", example = "msg_01H...")
val messageId: String,
```

- [ ] **Step 4: 실패 테스트 작성** — `ChatServiceStreamTest`(mockito-kotlin + Reactor `StepVerifier`). 진입 트랜잭션은 실제 `TransactionTemplate`(`PlatformTransactionManager` mock: `whenever(tm.getTransaction(any())).thenReturn(SimpleTransactionStatus())`)로 동작시키고, repo/settlementService/llmProvider를 mock한다. 핵심 검증:
  - feature OFF(`EconomyProperties(rewardChatEnabled=false)`) → `stream(...)` 구독 시 `FeatureDisabledException`(`StepVerifier ... verifyError(FeatureDisabledException::class.java)`). (beginReservation/llm 미호출.)
  - 정상: conversation mock 존재, llmProvider가 `Flux.just("A","B")`, `settlementService.settle(...)`가 `SettlementResult(...)` 반환 → 이벤트 순서 `Meta`,`Delta("A")`,`Delta("B")`,`RewardSettled`,`Done`; `settle` 1회·`refund` 0회.
  - 스트림 에러: llmProvider가 `Flux.error(...)` → `refund` 1회 호출, `RewardSettled` 없음, 스트림 error 종료.

- [ ] **Step 5: 실패 확인** — Run: `cd apps/backend && ./gradlew test --tests "*.ChatServiceStreamTest"` → FAIL.

- [ ] **Step 6: `ChatService.stream` 리팩터** — 진입 트랜잭션 확장 + 리액티브 파이프라인. `userPointService`/`hasEnoughBalance`/`InsufficientPointsException` 의존 제거. `createConversation`/`listConversations`/`getMessages`/`getHistory`/헬퍼는 보존.

```kotlin
// 생성자: userPointService 제거, settlementService: ChatRewardSettlementService + economyProperties: EconomyProperties 추가.
fun stream(userId: Long, conversationId: Long, messageId: String, content: String): Flux<ChatStreamEvent> {
    val ctx = transactionTemplate.execute {
        if (!economyProperties.rewardChatEnabled) throw FeatureDisabledException("REWARD_CHAT_ENABLED")
        val conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
            ?: throw ConversationNotFoundException(conversationId)
        val userMessage = chatMessageRepository.save(
            ChatMessage(conversation = conversation, role = MessageRole.USER, content = content, status = MessageStatus.COMPLETED))
        conversation.updatedAt = Instant.now(); conversationRepository.save(conversation)
        val history = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
        val providerMessages = history.filter { it.status == MessageStatus.COMPLETED && it.id != userMessage.id }
            .map { it.toProviderMessage() } + userMessage.toProviderMessage()
        val assistant = chatMessageRepository.save(
            ChatMessage(conversation = conversation, role = MessageRole.ASSISTANT, content = "", status = MessageStatus.STREAMING))
        require(assistant.id > 0) { "Assistant message id must be assigned" }
        val settlementId = settlementService.beginReservation(userId, conversationId, messageId)
        StreamContext(assistant.id, settlementId, providerMessages)
    } ?: error("Failed to initialize chat stream")

    val buffer = StringBuilder()
    return Flux.concat(
        Flux.just(ChatStreamEvent.Meta(messageId, 1L) as ChatStreamEvent),
        llmProvider.stream(ctx.providerMessages).doOnNext { buffer.append(it) }.map { ChatStreamEvent.Delta(it) as ChatStreamEvent },
        Flux.defer { Flux.just(ChatStreamEvent.RewardSettled(persistAndSettle(userId, ctx, buffer.toString())) as ChatStreamEvent) },
        Flux.just(ChatStreamEvent.Done("STOP") as ChatStreamEvent),
    ).onErrorResume { e ->
        failAndRefund(userId, ctx, buffer.toString())
        Flux.error(e)
    }.doFinally { signal ->
        if (signal == SignalType.CANCEL) failAndRefund(userId, ctx, buffer.toString())
    }
}

private fun persistAndSettle(userId: Long, ctx: StreamContext, text: String): SettlementResult =
    transactionTemplate.execute {
        val assistant = chatMessageRepository.findById(ctx.assistantMessageId).orElseThrow { IllegalArgumentException("Assistant message not found") }
        assistant.content = text; assistant.status = MessageStatus.COMPLETED; chatMessageRepository.save(assistant)
        settlementService.settle(userId, ctx.settlementId, ctx.assistantMessageId)
    } ?: error("settlement failed")

private fun failAndRefund(userId: Long, ctx: StreamContext, text: String) {
    transactionTemplate.executeWithoutResult {
        val assistant = chatMessageRepository.findById(ctx.assistantMessageId).orElse(null)
        if (assistant != null && assistant.status == MessageStatus.STREAMING) {
            assistant.content = text; assistant.status = MessageStatus.FAILED; chatMessageRepository.save(assistant)
        }
        settlementService.refund(userId, ctx.settlementId, ctx.assistantMessageId) // SETTLED면 no-op (멱등)
    }
}
// StreamContext: (assistantMessageId: Long, settlementId: Long, providerMessages: List<LlmMessage>)
```
주의: `persistAndSettle`/`failAndRefund`의 `settle`/`refund`는 정산 레코드 상태로 멱등이므로, 정상 종료 후 `doFinally(CANCEL이 아님)`에서는 refund가 호출되지 않고, 만약 호출돼도 SETTLED no-op이다.

- [ ] **Step 7: 테스트 통과 확인** — Run 위 명령 → PASS.

- [ ] **Step 8: 커밋** — `feat(chat): reserve energy and settle reward around the stream (Energy economy)`

---

### Task 6: 컨트롤러 SSE 이벤트 + 오류 매핑

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatController.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/exception/ChatExceptionHandler.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/ChatControllerTest.kt`

**Interfaces:**
- Consumes: `ChatService.stream(...): Flux<ChatStreamEvent>`, `ChatStreamEvent` 하위 타입, `EnergyInsufficientException`, `RewardAlreadySettledException`, `FeatureDisabledException`.
- Produces: SSE `event:` 이름 `meta`/`delta`/`reward_settled`/`done`(+`error`), JSON data.

- [ ] **Step 1: 컨트롤러 테스트 갱신(실패)** — `ChatControllerTest`. `chatService.stream(eq(1L), eq(7L), eq("msg_1"), eq("hi"))` mock이 `Flux.just(Meta("msg_1",1), Delta("A"), RewardSettled(result), Done("STOP"))` 반환 → 응답 본문에 `meta`/`delta`/`reward_settled`/`done` 이벤트, delta data `"A"`. 요청 JSON에 `messageId:"msg_1"`. 오류 매핑: 서비스가 `EnergyInsufficientException`→422 `ENERGY_INSUFFICIENT`; `RewardAlreadySettledException`→409 `REWARD_ALREADY_SETTLED`; `FeatureDisabledException`→503 `FEATURE_DISABLED`. (SSE 본문 수집은 기존 ChatControllerTest 관례(`WebTestClient`/`MockMvc` async) 따름.)

- [ ] **Step 2: 실패 확인** — Run: `cd apps/backend && ./gradlew test --tests "*.ChatControllerTest"` → FAIL.

- [ ] **Step 3: `ChatController.stream` 매핑 교체** — `request.messageId` 전달; `ObjectMapper` 주입; 각 이벤트→`ServerSentEvent`(event 이름 + JSON data).

```kotlin
// 생성자에 private val objectMapper: ObjectMapper 추가.
return chatService.stream(authentication.userId(), request.conversationId!!, request.messageId, request.message)
    .map { event -> when (event) {
        is ChatStreamEvent.Meta -> sse("meta", objectMapper.writeValueAsString(event))
        is ChatStreamEvent.Delta -> sse("delta", objectMapper.writeValueAsString(mapOf("text" to event.text)))
        is ChatStreamEvent.RewardSettled -> sse("reward_settled", objectMapper.writeValueAsString(event.result))
        is ChatStreamEvent.Done -> sse("done", objectMapper.writeValueAsString(mapOf("finishReason" to event.finishReason)))
    } }
    .onErrorResume { Flux.just(sse(ERROR_EVENT, STREAM_FAILED_MESSAGE)) }
// private fun sse(event: String, data: String) = ServerSentEvent.builder<String>(data).event(event).build()
```

- [ ] **Step 4: `ChatExceptionHandler`에 economy 예외 매핑 추가** (basePackages chat 핸들러에서 import):
  - `EnergyInsufficientException` → 422 `ENERGY_INSUFFICIENT`
  - `RewardAlreadySettledException` → 409 `REWARD_ALREADY_SETTLED`
  - `FeatureDisabledException` → 503 `FEATURE_DISABLED`

- [ ] **Step 5: 테스트 통과 확인** — Run 위 명령 → PASS.

- [ ] **Step 6: 커밋** — `feat(chat): emit meta/delta/reward_settled/done SSE + economy error mapping`

---

### Task 7: 정산 복구 조회 API

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/response/MessageSettlementResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/MessageSettlementController.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/exception/SettlementNotFoundException.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/service/ChatRewardSettlementService.kt` (+조회 메서드, +chatMessageRepository 주입)
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/chat/web/exception/ChatExceptionHandler.kt` (+404 SETTLEMENT_NOT_FOUND)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/chat/web/controller/MessageSettlementControllerTest.kt`

**Interfaces:**
- Produces: `GET /api/v1/messages/{messageId}/settlement` → `MessageSettlementResponse(messageId, chatStatus, settlementStatus, energyDelta, pendingCashablePtDelta, evolutionExpDelta, settledAt)`.
- Produces: `ChatRewardSettlementService.findForUser(userId, messageId): MessageSettlementResponse?`(@Transactional(readOnly), 본인 소유만).

- [ ] **Step 1: 응답 DTO + 예외 작성**

```kotlin
// MessageSettlementResponse.kt
package com.wnl.cashchat.api.domain.chat.web.response
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import java.time.Instant
data class MessageSettlementResponse(
    val messageId: String,
    val chatStatus: String?,
    val settlementStatus: SettlementStatus,
    val energyDelta: Long,
    val pendingCashablePtDelta: Long,
    val evolutionExpDelta: Long,
    val settledAt: Instant?,
)
```
```kotlin
// SettlementNotFoundException.kt
package com.wnl.cashchat.api.domain.chat.exception
class SettlementNotFoundException(val messageId: String) : RuntimeException("Settlement not found: $messageId")
```

- [ ] **Step 2: 실패 테스트 작성** — `MessageSettlementControllerTest`(@WebMvcTest 관례, addFilters=false, `.principal(1L)`, `@MockBean ChatRewardSettlementService`/`jwtTokenHandler`/`jpaMappingContext`, `@Import(ChatExceptionHandler)`):
  - mock `findForUser(1L,"msg_1")`가 `MessageSettlementResponse("msg_1","COMPLETED",SETTLED,-1,1,1,Instant)` → 200, `$.settlementStatus`=`SETTLED`, `$.energyDelta`=-1, `$.pendingCashablePtDelta`=1.
  - mock null → 404 `SETTLEMENT_NOT_FOUND`.

- [ ] **Step 3: 실패 확인** — Run: `cd apps/backend && ./gradlew test --tests "*.MessageSettlementControllerTest"` → FAIL.

- [ ] **Step 4: 서비스 조회 메서드 추가** — `ChatRewardSettlementService`에 `chatMessageRepository: ChatMessageRepository` 주입 + `@Transactional(readOnly=true) fun findForUser(userId, messageId): MessageSettlementResponse?`: `findByMessageId` → null이거나 `userId` 불일치면 null; assistantMessageId 있으면 `chatMessageRepository.findById(...).status.name`을 chatStatus로, 없으면 null. 매핑 반환.

- [ ] **Step 5: 컨트롤러 + 예외 매핑 작성** — `MessageSettlementController`(위 Interfaces) + `ChatExceptionHandler`에 `SettlementNotFoundException`→404 `SETTLEMENT_NOT_FOUND`.

```kotlin
package com.wnl.cashchat.api.domain.chat.web.controller
// imports...
@RestController
@RequestMapping("/api/v1/messages")
class MessageSettlementController(private val settlementService: ChatRewardSettlementService) {
    @GetMapping("/{messageId}/settlement")
    fun settlement(authentication: Authentication, @PathVariable messageId: String): MessageSettlementResponse =
        settlementService.findForUser(authentication.userId(), messageId)
            ?: throw SettlementNotFoundException(messageId)
    private fun Authentication.userId(): Long = principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")
}
```

- [ ] **Step 6: 테스트 통과 확인** — Run 위 명령 → PASS.

- [ ] **Step 7: 커밋** — `feat(chat): add GET /messages/{messageId}/settlement recovery endpoint`

---

## 완료 게이트 (전 Task 후)

- [ ] 전체 백엔드 테스트: `cd apps/backend && ./gradlew test` — BUILD SUCCESSFUL.
- [ ] 부팅 검증: V7 추가됨 → `validate` 통과 확인(전체 테스트가 컨텍스트 로드로 검증).
- [ ] 최종 전-브랜치(S3) 리뷰 → Critical/Important 수정.
- [ ] `.superpowers/sdd/progress.md` S3 완료 기록, 메모리 [[cc311-workflow]] 갱신.
- [ ] **PR/마무리 금지** — 브랜치는 CC-311 P0 전체(S1~S5). S4(Evolution)·S5(Quality pool routing) 남음.

## Self-Review 결과

- **스펙 커버리지:** 명세 4.1(보상형 채팅)·4.2(정산 조회)·6.1~6.3(보상/제외)·7.1(상태)·7.2(원자 정산)·9(오류코드)·10.3(멱등 UNIQUE)·13(I1/I2/I3/I9/I10/I11) → Task 매핑 완료. premium 라우팅(9장)·재생성/취소(4.3/4.4)·rate limit은 명시적 범위 밖.
- **비공개 수치:** margin은 EconomyProperties ZERO 기본 + env 주입, 소스에 실수치 없음. 풀은 BigDecimal DECIMAL(18,4).
- **타입 일관성:** `Flux<ChatStreamEvent>`, settlement 상태전이 메서드, `EnergyService.reserve/refund(userId, key): WalletLedger`, `beginReservation→settle/refund(settlementId)` 일관. WalletTxType 기존값(ENERGY_RESERVED/CONSUMED/REFUNDED, POINT_PENDING_GRANTED, EXP_GRANTED) 사용.
- **리스크:** T5 리액티브 정산/환불 멱등(onErrorResume + doFinally(CANCEL) 중복 호출 가능 → settle/refund가 상태로 멱등 보장). T5 단위테스트는 트랜잭션 mock 부담 → 파이프라인 조립(이벤트 순서·settle/refund 호출) 검증 중심, DB 정합은 T4 통합테스트가 담당.
