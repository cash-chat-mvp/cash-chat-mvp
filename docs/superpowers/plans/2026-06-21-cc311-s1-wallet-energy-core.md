# CC-311 S1 — Wallet / Energy Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신규 `economy` 도메인에 Wallet(에너지·pending/confirmed 포인트·진화 상태), EnergyGrant(FIFO 만료 추적), 통합 원장(WalletLedger), 멱등 Energy 발행, 그리고 `economy/me`·`economy/policy`·`wallet` 조회 API를 구축한다.

**Architecture:** 기존 `domain/point`의 검증된 패턴(비관적 락 → 멱등 키 조회 → 가감 → 원장 INSERT, `@ConfigurationProperties`, Kotest+TestContainers)을 그대로 따르되, 단일 `balance`인 `UserPoint`를 대체하는 `UserWallet`을 신설한다. 모든 재화는 `userId` 귀속. 인증/로그인 코드는 손대지 않는다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway, Java 21, Kotest(FunSpec), mockito-kotlin, TestContainers(MySQL 8.4).

## Global Constraints

- 패키지 루트: `com.wnl.cashchat.api.domain.economy`
- 모든 재화는 `userId`(Long) 귀속 — 비로그인/`X-App-Id` 미구현.
- `domain/auth`·JWT 필터·`SecurityConfig` 인증 로직 변경 금지. 새 GET 엔드포인트는 기존 인증된 `Authentication` principal(Long userId)을 사용.
- **내부 원가·마진 수치(nano 원가, 공용 풀 마진, Energy backing)는 소스·문서에 넣지 않는다.** S1은 게임/운영 파라미터(Energy 수량·상한·만료일·진화 EXP·기능 토글)만 다룬다. 원가/마진은 S5(비공개).
- **스키마는 Flyway 가 관리한다(`ddl-auto: validate`).** Hibernate 는 테이블을 만들지 않고 검증만 한다 → 신규 엔티티는 반드시 대응 Flyway 마이그레이션이 먼저 존재해야 앱·테스트가 부팅된다. 마이그레이션 컬럼명/타입/nullable 은 엔티티 `@Column` 과 정확히 일치해야 한다.
- 빌드/테스트: `cd apps/backend && ./gradlew test` (단일 클래스: `./gradlew test --tests '*ClassName*'`).
- 커밋 메시지: Conventional Commits. 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` 추가.
- `@ConfigurationPropertiesScan`·`@EnableJpaAuditing` 이미 활성 — 신규 properties/엔티티는 자동 등록·감사된다.
- 신규 엔티티는 `BaseEntity`(createdAt/updatedAt: `TIMESTAMP(6) NOT NULL`) 상속, `@GeneratedValue(IDENTITY)` id.

---

## File Structure

```
domain/economy/
  persistence/entity/
    UserWallet.kt              # 지갑 집계 엔티티 + 도메인 규칙(require 가드)
    EnergyGrant.kt             # 발행 출처·remaining·만료
    EnergySourceType.kt        # enum: REWARDED_AD, ATTENDANCE_AD, EVENT, SIGNUP, ADMIN
    WalletLedger.kt            # 통합 원장(idempotencyKey unique)
    WalletTxType.kt            # enum: ENERGY_*, POINT_PENDING_GRANTED, POINT_CONFIRMED, EXP_GRANTED
  persistence/repository/
    UserWalletRepository.kt
    EnergyGrantRepository.kt
    WalletLedgerRepository.kt
  properties/EconomyProperties.kt
  exception/EnergyCapExceededException.kt
  exception/WalletNotInitializedException.kt
  service/WalletService.kt     # ensureInitialized, 락 조회, snapshot
  service/EnergyService.kt     # grant() (S1). reserve/consume/refund 는 S3.
  web/controller/EconomyController.kt   # GET /economy/me, /economy/policy
  web/controller/WalletController.kt    # GET /wallet
  web/response/{EconomySnapshotResponse,EconomyPolicyResponse,WalletResponse}.kt
  web/exception/EconomyExceptionHandler.kt

src/main/resources/db/migration/V6__economy_wallet.sql   # user_wallet, energy_grant, wallet_ledger
```

테스트(미러 경로, `src/test`): `UserWalletTest`(단위), `EconomyPropertiesTest`(단위), `EnergyGrantTest`(단위), `WalletPersistenceIntegrationTest`·`EnergyGrantLedgerIntegrationTest`·`EnergyServiceIntegrationTest`(TestContainers), `EconomyControllerTest`(@SpringBootTest).

**태스크 순서 핵심:** 엔티티(T2·T3)는 순수 단위 테스트만 → **T4 에서 Flyway V6(세 테이블 전부) 생성** → 이후 모든 통합 테스트(T5~T8)는 부팅 시 validate 통과.

---

## Task 1: Economy enums + EconomyProperties

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/EnergySourceType.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/WalletTxType.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/properties/EconomyProperties.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/properties/EconomyPropertiesTest.kt`

**Interfaces:**
- Produces: `enum EnergySourceType { REWARDED_AD, ATTENDANCE_AD, EVENT, SIGNUP, ADMIN }`; `enum WalletTxType { ENERGY_GRANTED, ENERGY_RESERVED, ENERGY_CONSUMED, ENERGY_REFUNDED, ENERGY_EXPIRED, POINT_PENDING_GRANTED, POINT_CONFIRMED, EXP_GRANTED }`; `data class EconomyProperties(...)` with the fields below.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.properties

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation

class EconomyPropertiesTest : FunSpec({
    test("defaults match spec operating parameters") {
        val p = EconomyProperties()
        p.maxEnergy shouldBe 50L
        p.energyCostPerChat shouldBe 1L
        p.chatRewardPt shouldBe 1L
        p.evolutionExpPerChat shouldBe 1L
        p.rewardedEnergyPerAd shouldBe 3L
        p.attendanceEnergyReward shouldBe 4L
        p.adEnergyExpirationDays shouldBe 30L
        p.attendanceEnergyExpirationDays shouldBe 7L
        p.rewardChatEnabled shouldBe true
        p.evolutionEnabled shouldBe true
    }

    test("rejects non-positive maxEnergy") {
        val validator = Validation.buildDefaultValidatorFactory().validator
        val violations = validator.validate(EconomyProperties(maxEnergy = 0L))
        violations.map { it.propertyPath.toString() } shouldContain "maxEnergy"
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*EconomyPropertiesTest*'`
Expected: FAIL — `EconomyProperties` unresolved.

- [ ] **Step 3: Write minimal implementation**

`EnergySourceType.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

enum class EnergySourceType { REWARDED_AD, ATTENDANCE_AD, EVENT, SIGNUP, ADMIN }
```

`WalletTxType.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

enum class WalletTxType {
    ENERGY_GRANTED, ENERGY_RESERVED, ENERGY_CONSUMED, ENERGY_REFUNDED, ENERGY_EXPIRED,
    POINT_PENDING_GRANTED, POINT_CONFIRMED, EXP_GRANTED,
}
```

`EconomyProperties.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.properties

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.economy")
data class EconomyProperties(
    @field:Positive val maxEnergy: Long = 50,
    @field:Positive val energyCostPerChat: Long = 1,
    @field:PositiveOrZero val chatRewardPt: Long = 1,
    @field:PositiveOrZero val evolutionExpPerChat: Long = 1,
    @field:Positive val rewardedEnergyPerAd: Long = 3,
    @field:Positive val attendanceEnergyReward: Long = 4,
    @field:Positive val adEnergyExpirationDays: Long = 30,
    @field:Positive val attendanceEnergyExpirationDays: Long = 7,
    @field:Positive val energyExpirationNoticeDays: Long = 3,
    val rewardChatEnabled: Boolean = true,
    val rewardedAdEnabled: Boolean = true,
    val attendanceRewardEnabled: Boolean = true,
    val evolutionEnabled: Boolean = true,
    val cashoutEnabled: Boolean = true,
    val premiumRoutingEnabled: Boolean = true,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*EconomyPropertiesTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/EnergySourceType.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/WalletTxType.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/properties/EconomyProperties.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/properties/EconomyPropertiesTest.kt
git commit -m "feat(economy): add wallet enums and EconomyProperties

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: UserWallet entity domain rules

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/UserWallet.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/exception/EnergyCapExceededException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/exception/WalletNotInitializedException.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/UserWalletTest.kt`

**Interfaces:**
- Consumes: `User` (`domain.user.persistence.entity.User`).
- Produces: `class UserWallet(id: Long = 0, user: User)` with read-only-from-outside `var energyAvailable/energyReserved/pendingCashablePt/confirmedCashablePt: Long`, `var evolutionLevel/evolutionFailStack: Int`, `var evolutionExp: Long`; methods `grantEnergy(amount: Long, maxEnergy: Long)`, `reserveEnergy(amount: Long = 1)`, `consumeReserved(amount: Long = 1)`, `refundReserved(amount: Long = 1)`, `addPendingPt(amount: Long)`, `confirmPending(amount: Long)`, `addExp(amount: Long)`. `class EnergyCapExceededException(message): RuntimeException`, `class WalletNotInitializedException(userId: Long): RuntimeException`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserWalletTest : FunSpec({
    fun wallet() = UserWallet(
        user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "w")
    )

    test("grantEnergy increases available up to max") {
        val w = wallet(); w.grantEnergy(3, maxEnergy = 50); w.energyAvailable shouldBe 3L
    }
    test("grantEnergy beyond max is rejected") {
        val w = wallet(); w.grantEnergy(48, maxEnergy = 50)
        shouldThrow<EnergyCapExceededException> { w.grantEnergy(3, maxEnergy = 50) }
        w.energyAvailable shouldBe 48L
    }
    test("reserve then consume moves energy out of the wallet") {
        val w = wallet(); w.grantEnergy(2, maxEnergy = 50)
        w.reserveEnergy(); w.energyAvailable shouldBe 1L; w.energyReserved shouldBe 1L
        w.consumeReserved(); w.energyReserved shouldBe 0L; w.energyAvailable shouldBe 1L
    }
    test("reserve fails when no available energy") {
        shouldThrow<IllegalArgumentException> { wallet().reserveEnergy() }
    }
    test("refund returns reserved energy to available") {
        val w = wallet(); w.grantEnergy(1, maxEnergy = 50); w.reserveEnergy(); w.refundReserved()
        w.energyAvailable shouldBe 1L; w.energyReserved shouldBe 0L
    }
    test("pending points can be confirmed") {
        val w = wallet(); w.addPendingPt(5); w.confirmPending(5)
        w.pendingCashablePt shouldBe 0L; w.confirmedCashablePt shouldBe 5L
    }
    test("addExp accumulates evolution exp") {
        val w = wallet(); w.addExp(1); w.addExp(1); w.evolutionExp shouldBe 2L
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*UserWalletTest*'`
Expected: FAIL — `UserWallet` unresolved.

- [ ] **Step 3: Write minimal implementation**

`exception/EnergyCapExceededException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.exception

class EnergyCapExceededException(message: String = "Energy 보유 상한을 초과했습니다.") : RuntimeException(message)
```

`exception/WalletNotInitializedException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.exception

class WalletNotInitializedException(userId: Long) :
    RuntimeException("Wallet not initialized for userId=$userId")
```

`persistence/entity/UserWallet.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "user_wallet", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])])
class UserWallet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,
) : BaseEntity() {
    @Column(name = "energy_available", nullable = false) var energyAvailable: Long = 0; private set
    @Column(name = "energy_reserved", nullable = false) var energyReserved: Long = 0; private set
    @Column(name = "pending_cashable_pt", nullable = false) var pendingCashablePt: Long = 0; private set
    @Column(name = "confirmed_cashable_pt", nullable = false) var confirmedCashablePt: Long = 0; private set
    @Column(name = "evolution_level", nullable = false) var evolutionLevel: Int = 1; private set
    @Column(name = "evolution_exp", nullable = false) var evolutionExp: Long = 0; private set
    @Column(name = "evolution_fail_stack", nullable = false) var evolutionFailStack: Int = 0; private set

    fun grantEnergy(amount: Long, maxEnergy: Long) {
        require(amount >= 0) { "Energy amount must be non-negative" }
        if (Math.addExact(energyAvailable, amount) > maxEnergy) throw EnergyCapExceededException()
        energyAvailable += amount
    }
    fun reserveEnergy(amount: Long = 1) {
        require(amount >= 0) { "Reserve amount must be non-negative" }
        require(energyAvailable >= amount) { "Insufficient available energy" }
        energyAvailable -= amount; energyReserved += amount
    }
    fun consumeReserved(amount: Long = 1) {
        require(amount >= 0) { "Consume amount must be non-negative" }
        require(energyReserved >= amount) { "Insufficient reserved energy" }
        energyReserved -= amount
    }
    fun refundReserved(amount: Long = 1) {
        require(amount >= 0) { "Refund amount must be non-negative" }
        require(energyReserved >= amount) { "Insufficient reserved energy" }
        energyReserved -= amount; energyAvailable += amount
    }
    fun addPendingPt(amount: Long) {
        require(amount >= 0) { "Pending point amount must be non-negative" }
        pendingCashablePt = Math.addExact(pendingCashablePt, amount)
    }
    fun confirmPending(amount: Long) {
        require(amount >= 0) { "Confirm amount must be non-negative" }
        require(pendingCashablePt >= amount) { "Insufficient pending points" }
        pendingCashablePt -= amount; confirmedCashablePt = Math.addExact(confirmedCashablePt, amount)
    }
    fun addExp(amount: Long) {
        require(amount >= 0) { "Exp amount must be non-negative" }
        evolutionExp = Math.addExact(evolutionExp, amount)
    }
}
```

> Kotlin 주의: `var x: Long = 0; private set` 한 줄 표기가 안 되면 표준 형식으로 분리한다:
> ```kotlin
> @Column(name = "energy_available", nullable = false)
> var energyAvailable: Long = 0
>     private set
> ```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*UserWalletTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/UserWallet.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/exception/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/UserWalletTest.kt
git commit -m "feat(economy): add UserWallet entity with energy/point/exp rules

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: EnergyGrant + WalletLedger entities

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/EnergyGrant.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/WalletLedger.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/EnergyGrantTest.kt`

**Interfaces:**
- Produces:
  - `class EnergyGrant(userId: Long, sourceType: EnergySourceType, grantedAmount: Long, grantedAt: Instant, expiresAt: Instant, id: Long = 0)` with `var remainingAmount: Long` (private set, init = grantedAmount) and `fun consume(amount: Long): Long` (실제 차감량 반환, FIFO).
  - `class WalletLedger(userId: Long, type: WalletTxType, delta: Long, balanceAfter: Long, referenceId: String?, idempotencyKey: String, id: Long = 0)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class EnergyGrantTest : FunSpec({
    test("consume takes up to remaining and reports the taken amount") {
        val g = EnergyGrant(1L, EnergySourceType.REWARDED_AD, 3, Instant.now(), Instant.now())
        g.consume(2) shouldBe 2L
        g.remainingAmount shouldBe 1L
        g.consume(5) shouldBe 1L   // only 1 left
        g.remainingAmount shouldBe 0L
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*EnergyGrantTest*'`
Expected: FAIL — entities unresolved.

- [ ] **Step 3: Write minimal implementation**

`EnergyGrant.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "energy_grant", indexes = [Index(name = "idx_energy_grant_user", columnList = "user_id, expires_at")])
class EnergyGrant(
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 30)
    val sourceType: EnergySourceType,
    @Column(name = "granted_amount", nullable = false) val grantedAmount: Long,
    @Column(name = "granted_at", nullable = false) val grantedAt: Instant,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
) : BaseEntity() {
    @Column(name = "remaining_amount", nullable = false)
    var remainingAmount: Long = grantedAmount
        private set

    fun consume(amount: Long): Long {
        require(amount >= 0) { "Consume amount must be non-negative" }
        val taken = minOf(amount, remainingAmount)
        remainingAmount -= taken
        return taken
    }
}
```

`WalletLedger.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "wallet_ledger",
    uniqueConstraints = [UniqueConstraint(name = "uq_wallet_ledger_idempotency_key", columnNames = ["idempotency_key"])],
    indexes = [Index(name = "idx_wallet_ledger_user", columnList = "user_id")]
)
class WalletLedger(
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Enumerated(EnumType.STRING) @Column(name = "tx_type", nullable = false, length = 40) val type: WalletTxType,
    @Column(nullable = false) val delta: Long,
    @Column(name = "balance_after", nullable = false) val balanceAfter: Long,
    @Column(name = "reference_id", length = 255) val referenceId: String?,
    @Column(name = "idempotency_key", nullable = false, length = 255) val idempotencyKey: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
) : BaseEntity()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*EnergyGrantTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/EnergyGrant.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/WalletLedger.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/entity/EnergyGrantTest.kt
git commit -m "feat(economy): add EnergyGrant and WalletLedger entities

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Flyway V6 migration (economy tables)

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V6__economy_wallet.sql`
- Test: (none new — verified by booting the first integration test in Task 5; also run app context here)

**Interfaces:**
- Produces DB tables `user_wallet`, `energy_grant`, `wallet_ledger` whose columns exactly match the entities from Tasks 2–3. (`validate` 가 컬럼명/타입/nullable 불일치 시 부팅 실패.)

- [ ] **Step 1: Write the migration**

`V6__economy_wallet.sql`:
```sql
-- V6: 경제·성장 시스템 — 지갑(에너지/포인트/진화), Energy 발행, 통합 원장

CREATE TABLE user_wallet (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    energy_available      BIGINT       NOT NULL,
    energy_reserved       BIGINT       NOT NULL,
    pending_cashable_pt   BIGINT       NOT NULL,
    confirmed_cashable_pt BIGINT       NOT NULL,
    evolution_level       INT          NOT NULL,
    evolution_exp         BIGINT       NOT NULL,
    evolution_fail_stack  INT          NOT NULL,
    created_at            TIMESTAMP(6) NOT NULL,
    updated_at            TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_wallet_user UNIQUE (user_id),
    CONSTRAINT fk_user_wallet_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE energy_grant (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    source_type      VARCHAR(30)  NOT NULL,
    granted_amount   BIGINT       NOT NULL,
    remaining_amount BIGINT       NOT NULL,
    granted_at       TIMESTAMP(6) NOT NULL,
    expires_at       TIMESTAMP(6) NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_energy_grant_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_energy_grant_user ON energy_grant (user_id, expires_at);

CREATE TABLE wallet_ledger (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    tx_type         VARCHAR(40)  NOT NULL,
    delta           BIGINT       NOT NULL,
    balance_after   BIGINT       NOT NULL,
    reference_id    VARCHAR(255) NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_wallet_ledger_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_wallet_ledger_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_wallet_ledger_user ON wallet_ledger (user_id);
```

- [ ] **Step 2: Verify schema validates against entities**

Run: `cd apps/backend && ./gradlew test --tests '*CashChatApiApplicationTests*'`
Expected: PASS — Spring context loads; Flyway applies V6; Hibernate `validate` finds all three entity↔table mappings consistent. (불일치 시 `SchemaManagementException` 으로 실패 → 컬럼/타입 수정.)

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V6__economy_wallet.sql
git commit -m "feat(economy): add Flyway V6 migration for wallet, energy grant, ledger

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: UserWallet repository + WalletService.ensureInitialized

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/UserWalletRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/service/WalletService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/WalletPersistenceIntegrationTest.kt`

**Interfaces:**
- Consumes: `UserWallet`, `User`, `UserRepository`, `WalletNotInitializedException`.
- Produces: `interface UserWalletRepository : JpaRepository<UserWallet, Long> { findByUserId(userId): UserWallet?; findByUserIdForUpdate(userId): UserWallet? }`; `class WalletService { ensureInitialized(user: User): UserWallet; getForUpdate(userId: Long): UserWallet }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class WalletPersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var walletService: WalletService

    init {
        beforeTest { userWalletRepository.deleteAll(); userRepository.deleteAll() }

        test("ensureInitialized creates exactly one wallet and is idempotent") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "w"))
            val first = walletService.ensureInitialized(user)
            val second = walletService.ensureInitialized(user)
            second.id shouldBe first.id
            userWalletRepository.count() shouldBe 1L
            userWalletRepository.findByUserId(user.id)!!.energyAvailable shouldBe 0L
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*WalletPersistenceIntegrationTest*'`
Expected: FAIL — `UserWalletRepository`/`WalletService` unresolved.

- [ ] **Step 3: Write minimal implementation**

`UserWalletRepository.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserWalletRepository : JpaRepository<UserWallet, Long> {
    fun findByUserId(userId: Long): UserWallet?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from UserWallet w where w.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserWallet?
}
```

`WalletService.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.exception.WalletNotInitializedException
import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletService(
    private val userWalletRepository: UserWalletRepository,
) {
    @Transactional
    fun ensureInitialized(user: User): UserWallet =
        userWalletRepository.findByUserId(user.id) ?: create(user)

    fun getForUpdate(userId: Long): UserWallet =
        userWalletRepository.findByUserIdForUpdate(userId)
            ?: throw WalletNotInitializedException(userId)

    @Transactional(readOnly = true)
    fun snapshot(userId: Long): UserWallet =
        userWalletRepository.findByUserId(userId) ?: throw WalletNotInitializedException(userId)

    private fun create(user: User): UserWallet =
        try {
            userWalletRepository.saveAndFlush(UserWallet(user = user))
        } catch (e: DataIntegrityViolationException) {
            userWalletRepository.findByUserId(user.id) ?: throw e
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*WalletPersistenceIntegrationTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/UserWalletRepository.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/service/WalletService.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/WalletPersistenceIntegrationTest.kt
git commit -m "feat(economy): add UserWalletRepository and WalletService

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: EnergyGrant + WalletLedger repositories

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/EnergyGrantRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/WalletLedgerRepository.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/EnergyGrantLedgerIntegrationTest.kt`

**Interfaces:**
- Produces: `EnergyGrantRepository { findUsableOrderByExpiry(userId: Long, now: Instant): List<EnergyGrant> }`; `WalletLedgerRepository { findByIdempotencyKey(key: String): WalletLedger? }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.persistence

import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergyGrant
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EnergyGrantLedgerIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var energyGrantRepository: EnergyGrantRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository

    init {
        beforeTest { energyGrantRepository.deleteAll(); walletLedgerRepository.deleteAll() }

        test("findUsableOrderByExpiry returns non-expired positive grants ordered by expiry") {
            val now = Instant.now()
            energyGrantRepository.save(EnergyGrant(1L, EnergySourceType.REWARDED_AD, 3, now, now.plus(30, ChronoUnit.DAYS)))
            energyGrantRepository.save(EnergyGrant(1L, EnergySourceType.ATTENDANCE_AD, 4, now, now.plus(7, ChronoUnit.DAYS)))
            energyGrantRepository.save(EnergyGrant(1L, EnergySourceType.EVENT, 5, now, now.minus(1, ChronoUnit.DAYS)))
            energyGrantRepository.findUsableOrderByExpiry(1L, now).map { it.sourceType } shouldBe
                listOf(EnergySourceType.ATTENDANCE_AD, EnergySourceType.REWARDED_AD)
        }

        test("duplicate ledger idempotency key is rejected by unique constraint") {
            walletLedgerRepository.saveAndFlush(WalletLedger(1L, WalletTxType.ENERGY_GRANTED, 3, 3, "ads_1", "admob:reward:tx1"))
            shouldThrow<DataIntegrityViolationException> {
                walletLedgerRepository.saveAndFlush(WalletLedger(1L, WalletTxType.ENERGY_GRANTED, 3, 6, "ads_2", "admob:reward:tx1"))
            }
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*EnergyGrantLedgerIntegrationTest*'`
Expected: FAIL — repositories unresolved.

- [ ] **Step 3: Write minimal implementation**

`EnergyGrantRepository.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergyGrant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface EnergyGrantRepository : JpaRepository<EnergyGrant, Long> {
    @Query(
        """
        select g from EnergyGrant g
        where g.userId = :userId and g.remainingAmount > 0 and g.expiresAt > :now
        order by g.expiresAt asc
        """
    )
    fun findUsableOrderByExpiry(@Param("userId") userId: Long, @Param("now") now: Instant): List<EnergyGrant>
}
```

`WalletLedgerRepository.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import org.springframework.data.jpa.repository.JpaRepository

interface WalletLedgerRepository : JpaRepository<WalletLedger, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): WalletLedger?
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*EnergyGrantLedgerIntegrationTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/EnergyGrantRepository.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/persistence/repository/WalletLedgerRepository.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/persistence/EnergyGrantLedgerIntegrationTest.kt
git commit -m "feat(economy): add EnergyGrant and WalletLedger repositories

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: EnergyService.grant() — idempotent issuance with cap and ledger

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/service/EnergyService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/service/EnergyServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `WalletService.getForUpdate`, `EnergyGrantRepository`, `WalletLedgerRepository`, `EconomyProperties`, `UserWallet.grantEnergy`, `EnergyGrant`, `WalletLedger`.
- Produces: `class EnergyService { grant(userId: Long, amount: Long, sourceType: EnergySourceType, expiresAt: Instant, idempotencyKey: String): WalletLedger }`. 단일 `@Transactional`; 지갑 락 → 멱등 키 존재 시 기존 원장 반환 → `wallet.grantEnergy(amount, maxEnergy)` → `EnergyGrant` 저장 → `WalletLedger(ENERGY_GRANTED, delta=amount, balanceAfter=wallet.energyAvailable, referenceId=null, key)` 저장.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EnergyServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var energyGrantRepository: EnergyGrantRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository
    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var energyService: EnergyService

    init {
        beforeTest {
            walletLedgerRepository.deleteAll(); energyGrantRepository.deleteAll()
            userWalletRepository.deleteAll(); userRepository.deleteAll()
        }
        fun newUser(): Long {
            val u = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "g"))
            walletService.ensureInitialized(u); return u.id
        }
        val exp = Instant.now().plus(30, ChronoUnit.DAYS)

        test("grant increases energy, writes a grant row and a ledger entry") {
            val userId = newUser()
            energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx1")
            userWalletRepository.findByUserId(userId)!!.energyAvailable shouldBe 3L
            energyGrantRepository.count() shouldBe 1L
            walletLedgerRepository.count() shouldBe 1L
        }
        test("duplicate idempotency key does not double-grant") {
            val userId = newUser()
            energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx1")
            energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx1")
            userWalletRepository.findByUserId(userId)!!.energyAvailable shouldBe 3L
            energyGrantRepository.count() shouldBe 1L
            walletLedgerRepository.count() shouldBe 1L
        }
        test("grant beyond max energy is rejected") {
            val userId = newUser()
            energyService.grant(userId, 49, EnergySourceType.ADMIN, exp, "seed:1")
            shouldThrow<EnergyCapExceededException> {
                energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx2")
            }
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*EnergyServiceIntegrationTest*'`
Expected: FAIL — `EnergyService` unresolved.

- [ ] **Step 3: Write minimal implementation**

`EnergyService.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergyGrant
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class EnergyService(
    private val walletService: WalletService,
    private val energyGrantRepository: EnergyGrantRepository,
    private val walletLedgerRepository: WalletLedgerRepository,
    private val economyProperties: EconomyProperties,
) {
    /**
     * 멱등 Energy 발행. 단일 트랜잭션에서 지갑 행 락 → 멱등 키 조회 → 상한 검사 후 가산 →
     * EnergyGrant·WalletLedger INSERT. 잠금-먼저 순서로 동일 키 동시 호출을 직렬화하고,
     * 원장 unique 가 최종 방어선이다.
     */
    @Transactional
    fun grant(
        userId: Long,
        amount: Long,
        sourceType: EnergySourceType,
        expiresAt: Instant,
        idempotencyKey: String,
    ): WalletLedger {
        val wallet = walletService.getForUpdate(userId)
        walletLedgerRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        wallet.grantEnergy(amount, economyProperties.maxEnergy)
        energyGrantRepository.save(
            EnergyGrant(
                userId = userId,
                sourceType = sourceType,
                grantedAmount = amount,
                grantedAt = Instant.now(),
                expiresAt = expiresAt,
            )
        )
        return walletLedgerRepository.save(
            WalletLedger(
                userId = userId,
                type = WalletTxType.ENERGY_GRANTED,
                delta = amount,
                balanceAfter = wallet.energyAvailable,
                referenceId = null,
                idempotencyKey = idempotencyKey,
            )
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*EnergyServiceIntegrationTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/service/EnergyService.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/service/EnergyServiceIntegrationTest.kt
git commit -m "feat(economy): add idempotent EnergyService.grant with cap and ledger

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Read APIs — economy/me, economy/policy, wallet

**Files:**
- Create: `web/response/EconomySnapshotResponse.kt`, `web/response/EconomyPolicyResponse.kt`, `web/response/WalletResponse.kt`
- Create: `web/controller/EconomyController.kt`, `web/controller/WalletController.kt`
- Create: `web/exception/EconomyExceptionHandler.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/web/controller/EconomyControllerTest.kt`

**Interfaces:**
- Consumes: `WalletService.snapshot`, `EconomyProperties`, `Authentication.principal as Long` (기존 `ChatController` 패턴), `ErrorResponse`.
- Produces: `EconomySnapshotResponse(serverTime, energy, point, evolution, features)`; `EconomyPolicyResponse(...)`; `WalletResponse(...)`.

> **범위 메모:** `economy/me` 의 `ad`/`attendance` 하위 객체는 광고가 Energy 를 지급하도록 바뀌는 **S2 에서 추가**한다. S1 의 `economy/me` 는 energy/point/evolution/features 만 채운다(스펙 2.1 의 부분 집합). 의도된 슬라이스 경계.

- [ ] **Step 1: Confirm helper signatures (구현 전 확인)**

Read: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/jwt/JwtTokenHandler.kt` 에서 access-token 발급 메서드 시그니처, `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/web/response/ErrorResponse.kt` 에서 생성자 시그니처를 확인한다. 아래 테스트/핸들러의 토큰 발급·`ErrorResponse(...)` 호출부를 실제 시그니처에 맞춘다. (기존 `AuthControllerTest`/도메인 ExceptionHandler 가 참고 예시.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.wnl.cashchat.api.domain.economy.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EconomyControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var jwtTokenHandler: JwtTokenHandler

    init {
        test("GET /economy/me returns the authenticated user's energy snapshot") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "me"))
            walletService.ensureInitialized(user)
            energyService.grant(user.id, 3, EnergySourceType.REWARDED_AD,
                Instant.now().plus(30, ChronoUnit.DAYS), "seed:${user.id}")
            // NOTE: confirm token-creation API in Step 1 and adjust this call accordingly.
            val token = jwtTokenHandler.createAccessToken(user.id, user.role.name)

            val body = WebClient.create("http://localhost:$port").get()
                .uri("/api/v1/economy/me")
                .header("Authorization", "Bearer $token")
                .retrieve().bodyToMono<Map<String, Any>>().block()!!

            @Suppress("UNCHECKED_CAST")
            val energy = body["energy"] as Map<String, Any>
            (energy["available"] as Number).toLong() shouldBe 3L
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests '*EconomyControllerTest*'`
Expected: FAIL — controller/response types unresolved.

- [ ] **Step 4: Write minimal implementation**

`web/response/EconomySnapshotResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.web.response

import java.time.Instant

data class EconomySnapshotResponse(
    val serverTime: Instant,
    val energy: EnergyView,
    val point: PointView,
    val evolution: EvolutionView,
    val features: FeaturesView,
) {
    data class EnergyView(val available: Long, val reserved: Long, val max: Long)
    data class PointView(val pending: Long, val confirmed: Long)
    data class EvolutionView(val level: Int, val exp: Long, val failStack: Int)
    data class FeaturesView(
        val rewardChatEnabled: Boolean,
        val rewardedAdEnabled: Boolean,
        val attendanceRewardEnabled: Boolean,
        val evolutionEnabled: Boolean,
        val cashoutEnabled: Boolean,
    )
}
```

`web/response/WalletResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.web.response

data class WalletResponse(
    val energyAvailable: Long,
    val energyReserved: Long,
    val maxEnergy: Long,
    val pendingCashablePt: Long,
    val confirmedCashablePt: Long,
    val evolutionExp: Long,
)
```

`web/response/EconomyPolicyResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.web.response

data class EconomyPolicyResponse(
    val energyCostPerChat: Long,
    val chatRewardPt: Long,
    val evolutionExpPerChat: Long,
    val maxEnergy: Long,
    val rewardedEnergyPerAd: Long,
    val attendanceEnergyReward: Long,
    val energyExpirationNoticeDays: Long,
)
```

`web/controller/EconomyController.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.web.controller

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.economy.web.response.EconomyPolicyResponse
import com.wnl.cashchat.api.domain.economy.web.response.EconomySnapshotResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/economy")
class EconomyController(
    private val walletService: WalletService,
    private val economyProperties: EconomyProperties,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): EconomySnapshotResponse {
        val w = walletService.snapshot(authentication.userId())
        return EconomySnapshotResponse(
            serverTime = Instant.now(),
            energy = EconomySnapshotResponse.EnergyView(w.energyAvailable, w.energyReserved, economyProperties.maxEnergy),
            point = EconomySnapshotResponse.PointView(w.pendingCashablePt, w.confirmedCashablePt),
            evolution = EconomySnapshotResponse.EvolutionView(w.evolutionLevel, w.evolutionExp, w.evolutionFailStack),
            features = EconomySnapshotResponse.FeaturesView(
                rewardChatEnabled = economyProperties.rewardChatEnabled,
                rewardedAdEnabled = economyProperties.rewardedAdEnabled,
                attendanceRewardEnabled = economyProperties.attendanceRewardEnabled,
                evolutionEnabled = economyProperties.evolutionEnabled,
                cashoutEnabled = economyProperties.cashoutEnabled,
            ),
        )
    }

    @GetMapping("/policy")
    fun policy(): EconomyPolicyResponse = EconomyPolicyResponse(
        energyCostPerChat = economyProperties.energyCostPerChat,
        chatRewardPt = economyProperties.chatRewardPt,
        evolutionExpPerChat = economyProperties.evolutionExpPerChat,
        maxEnergy = economyProperties.maxEnergy,
        rewardedEnergyPerAd = economyProperties.rewardedEnergyPerAd,
        attendanceEnergyReward = economyProperties.attendanceEnergyReward,
        energyExpirationNoticeDays = economyProperties.energyExpirationNoticeDays,
    )

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")
}
```

`web/controller/WalletController.kt`:
```kotlin
package com.wnl.cashchat.api.domain.economy.web.controller

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.economy.web.response.WalletResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(
    private val walletService: WalletService,
    private val economyProperties: EconomyProperties,
) {
    @GetMapping
    fun wallet(authentication: Authentication): WalletResponse {
        val w = walletService.snapshot(authentication.principal as Long)
        return WalletResponse(
            energyAvailable = w.energyAvailable,
            energyReserved = w.energyReserved,
            maxEnergy = economyProperties.maxEnergy,
            pendingCashablePt = w.pendingCashablePt,
            confirmedCashablePt = w.confirmedCashablePt,
            evolutionExp = w.evolutionExp,
        )
    }
}
```

`web/exception/EconomyExceptionHandler.kt` (ErrorResponse 시그니처는 Step 1 에서 확인한 것으로 맞춤):
```kotlin
package com.wnl.cashchat.api.domain.economy.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.economy.exception.WalletNotInitializedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.economy"])
class EconomyExceptionHandler {
    @ExceptionHandler(EnergyCapExceededException::class)
    fun handleCap(e: EnergyCapExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("ENERGY_CAP_EXCEEDED", e.message ?: "Energy 상한 초과"))

    @ExceptionHandler(WalletNotInitializedException::class)
    fun handleNotInit(e: WalletNotInitializedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("WALLET_NOT_FOUND", e.message ?: "지갑이 초기화되지 않았습니다."))
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests '*EconomyControllerTest*'`
Expected: PASS.

- [ ] **Step 6: Run the full economy suite + whole build**

Run: `cd apps/backend && ./gradlew test --tests 'com.wnl.cashchat.api.domain.economy.*'`
Then: `cd apps/backend && ./gradlew test`
Expected: PASS (신규 엔티티가 기존 통합 테스트 스키마/validate 에 영향 없음 확인).

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/economy/web/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/economy/web/controller/EconomyControllerTest.kt
git commit -m "feat(economy): add economy/me, economy/policy and wallet read APIs

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## After S1

- [ ] `cd apps/backend && ./gradlew test` 전체 녹색 확인.
- [ ] `/code-review` (로컬 diff) 실행 — S1 변경분 리뷰 후 다음 슬라이스로.
- [ ] **S2 plan 작성:** `AdRewardService.grantFromCallback`·출석 지급부의 적립을 `EnergyService.grant` 로 교체(SSV 검증 로직 불변), `economy/me` 에 `ad`/`attendance` 객체 추가, 만료일 properties(`adEnergyExpirationDays`/`attendanceEnergyExpirationDays`) 적용.

## Self-Review 결과

- **Spec 커버리지(S1 범위):** user_wallet(T2/T4/T5)·energy_grant(T3/T4/T6)·wallet_ledger(T3/T4/T6)·EnergyService.grant 멱등·상한(T7)·economy/me·policy·wallet(T8)·FIFO 만료 조회(T6)·동시성 락(T5/T7)·Flyway 스키마(T4). `shared_quality_pool` 엔티티는 적립 로직과 함께 도입하는 게 응집도 높아 **S5 로 명시 연기**(S1 미생성) — 의도된 경계.
- **Placeholder 스캔:** 코드 단계는 모두 실제 코드 포함. 외부 의존 2건(`JwtTokenHandler` 토큰 발급, `ErrorResponse` 생성자)은 T8 Step1 에서 "구현 전 확인" 절차로 명시 — 추측 금지, 기존 코드 확인 후 시그니처 일치.
- **타입 일관성:** `UserWallet` 메서드명(grantEnergy/reserveEnergy/consumeReserved/refundReserved/addPendingPt/confirmPending/addExp), `EnergyService.grant(userId, amount, sourceType, expiresAt, idempotencyKey)`, repo 메서드명(findByUserId/findByUserIdForUpdate/findUsableOrderByExpiry/findByIdempotencyKey)이 T2~T8 전반 + V6 컬럼명과 일치.
- **Flyway/validate 정합:** V6 컬럼명·타입·nullable 이 엔티티 `@Column` 과 1:1 매핑(T4 검증 단계 포함).
