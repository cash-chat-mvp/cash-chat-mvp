# 혜택존 PR1 — Flyway 도입 + 포인트 멱등성 확장 (BE-4 인프라 + BE-1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flyway 기반 스키마 관리를 도입(기존 스키마 베이스라인 포함)하고, `UserPointService`에 멱등성·동시성 안전 적립 메서드 `recordTransaction(userId, delta, reason, idempotencyKey)`와 `point_transaction` ledger를 추가한다.

**Architecture:** 기존 프로젝트는 Hibernate 자동 DDL(dev H2 `create-drop`)에 의존하고 prod 스키마 관리가 부재하다. 본 PR에서 Flyway를 도입하고 `ddl-auto=validate`로 전환한다. `V1`은 기존 전체 스키마(users·user_points·refresh_tokens·conversations·chat_messages) 베이스라인, `V2`는 신규 `point_transaction` 테이블이다. 적립은 단일 `@Transactional` 안에서 (1) 사용자 포인트 행 비관적 락 → (2) 멱등성 키 조회(중복이면 기존 ledger 반환) → (3) 잔액 가감 → (4) ledger INSERT 순으로 수행해 중복·동시성 적립을 차단한다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway 10.x(Boot 관리 버전), H2(dev, MySQL 호환 모드), MySQL 8(prod·test), Kotest 5.9.1, Mockito-Kotlin, Testcontainers MySQL.

---

## 결정 사항 / 가정 (Documented Decisions)

이 PR을 시작하기 전에 다음 결정들이 사용자와 합의되었다:

1. **CC-288 범위**: 백엔드 전체(`tasks.md`의 BE-1~BE-4). 본 계획은 그중 **PR1 = BE-4 인프라 + BE-1**만 다룬다. BE-2(출석)·BE-3(광고)는 PR1 머지 후 각각 별도 계획·PR.
2. **마이그레이션 전략**: Flyway 정식 도입 + 기존 스키마 베이스라인(V1). `ddl-auto=validate`.
3. **시드/한도 값**: spec 부록의 가설값으로 진행(본 PR에는 reward 시드 없음 — BE-2/BE-3에서 추가).
4. **테이블 분할**: `tasks.md`의 BE-4는 6개 테이블을 모두 만들지만, 도메인별 PR 분할 결정에 따라 본 PR은 `point_transaction`만 생성한다. `attendance_*`는 PR2, `ad_reward_*`는 PR3의 마이그레이션에서 추가한다(`V3`, `V4`...).
5. **dev H2 호환 모드**: 동일한 MySQL 방언 마이그레이션을 dev에서도 실행하기 위해 H2를 MySQL 호환 모드로 전환한다.
6. **에러 코드**: 잔액 부족은 기존 `InsufficientPointsException`(`INSUFFICIENT_POINTS`, HTTP 402)을 재사용한다. spec BE-1의 `INSUFFICIENT_COIN`은 신규 코드 추가 대신 기존 코드로 통일(DRY). spec/tasks 문구는 후속 정리.
7. **UUID 컬럼 리스크**: `conversations.uuid`(java.util.UUID)는 방언별 매핑이 달라 베이스라인 작성 시 검증이 필요하다. Task 2의 validate 루프로 수렴시킨다.

---

## File Structure

**신규 생성**
- `apps/backend/src/main/resources/db/migration/V1__baseline_schema.sql` — 기존 전체 스키마 베이스라인
- `apps/backend/src/main/resources/db/migration/V2__point_transaction.sql` — 신규 ledger 테이블
- `apps/backend/.../domain/point/persistence/entity/PointTransaction.kt` — ledger 엔티티
- `apps/backend/.../domain/point/persistence/entity/PointTransactionReason.kt` — 적립 사유 enum
- `apps/backend/.../domain/point/persistence/repository/PointTransactionRepository.kt`
- `apps/backend/src/test/.../domain/point/service/PointTransactionRecordTest.kt` — recordTransaction 단위 테스트(mock)
- `apps/backend/src/test/.../domain/point/persistence/PointIdempotencyIntegrationTest.kt` — 멱등성·동시성 통합 테스트(Testcontainers MySQL)

**수정**
- `apps/backend/build.gradle.kts` — Flyway 의존성 추가
- `apps/backend/src/main/resources/application.yaml` — `ddl-auto=validate`, H2 MySQL 모드, Flyway 설정
- `apps/backend/.../domain/point/persistence/repository/UserPointRepository.kt` — 비관적 락 조회 추가
- `apps/backend/.../domain/point/service/UserPointService.kt` — `recordTransaction` 추가
- `apps/backend/src/test/.../domain/chat/persistence/ChatPersistenceIntegrationTest.kt` — `ddl-auto=create-drop` 오버라이드 제거(Flyway+validate 사용)

---

## Task 1: Flyway 의존성 추가 + 기존 스키마 베이스라인(V1)

**Files:**
- Modify: `apps/backend/build.gradle.kts`
- Create: `apps/backend/src/main/resources/db/migration/V1__baseline_schema.sql`

이 시점에는 아직 `application.yaml`을 바꾸지 않는다(현재 동작 유지). 베이스라인 SQL을 먼저 만들고, Task 2에서 validate로 전환해 검증한다.

- [ ] **Step 1: Flyway 의존성 추가**

`apps/backend/build.gradle.kts`의 `dependencies { ... }` 블록 안 `// implementation` 그룹에 다음을 추가한다:

```kotlin
    // Flyway (schema migration)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
```

- [ ] **Step 2: 의존성 해석 확인**

Run: `cd apps/backend && ./gradlew dependencies --configuration runtimeClasspath -q | Select-String flyway`
(PowerShell) 또는 `./gradlew dependencies --configuration runtimeClasspath | grep flyway`
Expected: `org.flywaydb:flyway-core`와 `org.flywaydb:flyway-mysql`이 Boot 관리 버전(10.x)으로 표시됨.

- [ ] **Step 3: 베이스라인 SQL 작성**

`apps/backend/src/main/resources/db/migration/V1__baseline_schema.sql` 생성. 아래는 기존 엔티티(`User`, `RefreshToken`, `UserPoint`, `Conversation`, `ChatMessage`, `BaseEntity`)에서 도출한 MySQL 방언 DDL이다. H2 MySQL 호환 모드에서도 동작한다.

```sql
-- V1: 기존 스키마 베이스라인 (Flyway 도입 이전 Hibernate 자동 DDL과 동등)

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    role              VARCHAR(255) NOT NULL,
    device_token      VARCHAR(255),
    provider          VARCHAR(255) NOT NULL,
    provider_id       VARCHAR(255),
    email             VARCHAR(255),
    name              VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(255),
    created_at        TIMESTAMP(6) NOT NULL,
    updated_at        TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_device_token UNIQUE (device_token),
    CONSTRAINT uq_users_provider_provider_id UNIQUE (provider, provider_id)
);

CREATE TABLE user_points (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    balance    BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_points_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_points_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE conversations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    uuid       BINARY(16)   NOT NULL,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_conversations_uuid UNIQUE (uuid),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE chat_messages (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(255) NOT NULL,
    model           VARCHAR(255),
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);
CREATE INDEX idx_chat_messages_conversation_created_at ON chat_messages (conversation_id, created_at);
```

> **주의(UUID):** `conversations.uuid`는 Hibernate 6이 java.util.UUID를 MySQL에서 기본 `BINARY(16)`으로 매핑한다고 가정했다. Task 2의 validate가 실패하면, 임시로 `spring.jpa.hibernate.ddl-auto=create`로 부팅해 실제 생성된 컬럼 타입을 `SHOW CREATE TABLE conversations`로 확인하고 V1을 그 타입에 맞춘다(예: `uuid VARCHAR(36)`). 그 후 다시 `validate`로 되돌린다.

- [ ] **Step 4: 커밋**

```bash
git add apps/backend/build.gradle.kts apps/backend/src/main/resources/db/migration/V1__baseline_schema.sql
git commit -m "build(reward): add Flyway and baseline schema migration"
```

---

## Task 2: 스키마 관리 전환 (validate + H2 MySQL 모드 + Flyway 활성화)

**Files:**
- Modify: `apps/backend/src/main/resources/application.yaml`
- Modify: `apps/backend/src/test/.../domain/chat/persistence/ChatPersistenceIntegrationTest.kt`

- [ ] **Step 1: application.yaml 갱신**

`apps/backend/src/main/resources/application.yaml`의 `spring:` 하위를 다음과 같이 수정한다. (1) H2 URL을 MySQL 호환 모드로, (2) `jpa.hibernate.ddl-auto=validate` 추가, (3) `flyway` 블록 추가.

기존:
```yaml
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:test
    username: sa
```
수정 후:
```yaml
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
    username: sa

  jpa:
    hibernate:
      ddl-auto: validate

  flyway:
    enabled: true
    baseline-on-migrate: false
```

> `spring.flyway.enabled`는 classpath에 Flyway가 있으면 기본 true지만 명시한다. 기존 스키마가 없는 인메모리/신규 DB이므로 `baseline-on-migrate=false`로 두고 V1부터 적용한다.

- [ ] **Step 2: 기존 통합테스트의 ddl-auto 오버라이드 제거**

`ChatPersistenceIntegrationTest.kt`는 Flyway+validate를 사용하도록 클래스 애너테이션에서 `create-drop` 오버라이드를 제거한다.

기존(27행):
```kotlin
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
class ChatPersistenceIntegrationTest : FunSpec() {
```
수정 후:
```kotlin
@SpringBootTest
class ChatPersistenceIntegrationTest : FunSpec() {
```

> 이 테스트는 Testcontainers MySQL에 연결되므로, 이제 Flyway가 실제 MySQL 8에 V1을 실행하고 Hibernate가 validate한다 — 베이스라인의 MySQL 정합성을 검증하는 안전망이 된다.

- [ ] **Step 3: 다른 ddl-auto 오버라이드 잔존 여부 점검**

Run (Grep 도구 사용 권장): 패턴 `ddl-auto` 를 `apps/backend/src/test` 전체에서 검색.
Expected: `ChatPersistenceIntegrationTest` 외에 오버라이드가 더 있으면 동일하게 제거(또는 `validate` 유지). 없으면 다음 단계로.

- [ ] **Step 4: 전체 테스트 실행으로 validate·Flyway 정합성 확인**

Run: `cd apps/backend && ./gradlew test`
Expected: PASS. 특히 컨텍스트 로딩 테스트(`CashChatApiApplicationTests`, `OpenApiDocumentationTest`, config 테스트)와 `ChatPersistenceIntegrationTest`가 통과해야 한다.
- 만약 `SchemaManagementException`(validate 실패)이 나면, 메시지의 누락/불일치 컬럼을 V1에 반영한다. UUID 관련 실패는 Task 1 Step 3의 주의 사항대로 실제 타입을 확인해 수정한다.
- Testcontainers는 Docker 데몬이 필요하다. 미실행 시 Docker Desktop을 먼저 켠다.

- [ ] **Step 5: dev 부팅 스모크 확인**

Run: `cd apps/backend && ./gradlew bootRun` (몇 초 후 Ctrl+C)
Expected: 로그에 Flyway가 `Migrating schema ... to version 1 - baseline schema`를 수행하고 Hibernate validate가 예외 없이 통과, 애플리케이션이 정상 기동.

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/resources/application.yaml apps/backend/src/test
git commit -m "build(reward): switch to Flyway-managed schema with ddl-auto=validate"
```

---

## Task 3: PointTransaction ledger 엔티티 + 마이그레이션(V2)

**Files:**
- Create: `apps/backend/.../domain/point/persistence/entity/PointTransactionReason.kt`
- Create: `apps/backend/.../domain/point/persistence/entity/PointTransaction.kt`
- Create: `apps/backend/.../domain/point/persistence/repository/PointTransactionRepository.kt`
- Create: `apps/backend/src/main/resources/db/migration/V2__point_transaction.sql`

- [ ] **Step 1: 적립 사유 enum 생성**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.persistence.entity

/**
 * 포인트 적립/차감 사유. Phase 1 적립 채널(출석·광고) 중심으로 정의하며,
 * 소비(상점 등) 사유는 해당 도메인 구현 시 추가한다.
 */
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
}
```

- [ ] **Step 2: PointTransaction 엔티티 생성**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransaction.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.persistence.entity

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

/**
 * 포인트 적립/차감 원장(ledger). idempotencyKey 유니크 제약으로 중복 적립을 차단한다.
 */
@Entity
@Table(
    name = "point_transaction",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_point_transaction_idempotency_key", columnNames = ["idempotency_key"])
    ],
    indexes = [
        Index(name = "idx_point_transaction_user_id", columnList = "user_id")
    ]
)
class PointTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val delta: Long,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val reason: PointTransactionReason,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,
) : BaseEntity()
```

- [ ] **Step 3: Repository 생성**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/repository/PointTransactionRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.persistence.repository

import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import org.springframework.data.jpa.repository.JpaRepository

interface PointTransactionRepository : JpaRepository<PointTransaction, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): PointTransaction?
}
```

- [ ] **Step 4: V2 마이그레이션 작성**

`apps/backend/src/main/resources/db/migration/V2__point_transaction.sql`:

```sql
-- V2: 포인트 적립/차감 원장 (멱등성 키 유니크)

CREATE TABLE point_transaction (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    delta           BIGINT       NOT NULL,
    balance_after   BIGINT       NOT NULL,
    reason          VARCHAR(50)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_point_transaction_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_point_transaction_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_point_transaction_user_id ON point_transaction (user_id);
```

- [ ] **Step 5: validate로 엔티티↔스키마 정합성 확인**

Run: `cd apps/backend && ./gradlew test --tests "*ChatPersistenceIntegrationTest"`
Expected: PASS. 신규 `point_transaction` 매핑이 V2 스키마와 일치해 validate가 통과(컨텍스트가 정상 로드). 실패 시 메시지의 컬럼 타입/이름 불일치를 V2 또는 엔티티에 반영.

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence apps/backend/src/main/resources/db/migration/V2__point_transaction.sql
git commit -m "feat(point): add PointTransaction ledger entity and migration"
```

---

## Task 4: UserPointRepository 비관적 락 조회 추가

**Files:**
- Modify: `apps/backend/.../domain/point/persistence/repository/UserPointRepository.kt`

- [ ] **Step 1: 비관적 락 메서드 추가**

`UserPointRepository.kt`를 다음으로 교체한다:

```kotlin
package com.wnl.cashchat.api.domain.point.persistence.repository

import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserPointRepository : JpaRepository<UserPoint, Long> {
    fun findByUserId(userId: Long): UserPoint?

    fun existsByUserIdAndBalanceGreaterThanEqual(userId: Long, balance: Long): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select up from UserPoint up where up.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserPoint?
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/repository/UserPointRepository.kt
git commit -m "feat(point): add pessimistic-lock lookup for balance updates"
```

---

## Task 5: recordTransaction 멱등성 적립 (TDD — 단위 테스트)

**Files:**
- Test: `apps/backend/src/test/.../domain/point/service/PointTransactionRecordTest.kt`
- Modify: `apps/backend/.../domain/point/service/UserPointService.kt`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/service/PointTransactionRecordTest.kt` 생성. 기존 `UserPointServiceTest`의 mockito-kotlin 스타일을 따른다.

```kotlin
package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PointTransactionRecordTest : FunSpec({
    lateinit var userPointRepository: UserPointRepository
    lateinit var pointTransactionRepository: PointTransactionRepository
    lateinit var userPointService: UserPointService

    val user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "tester")

    beforeTest {
        userPointRepository = org.mockito.kotlin.mock()
        pointTransactionRepository = org.mockito.kotlin.mock()
        userPointService = UserPointService(
            userPointRepository = userPointRepository,
            pointTransactionRepository = pointTransactionRepository,
            pointProperties = PointProperties(initialBalance = 1L),
        )
    }

    test("records a positive accrual, charges balance, and persists a ledger row") {
        val userPoint = UserPoint(user = user, balance = 100L)
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k1")).thenReturn(null)
        whenever(pointTransactionRepository.save(any<PointTransaction>())).thenAnswer { it.arguments[0] }

        val result = userPointService.recordTransaction(
            userId = 1L, delta = 20L, reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k1",
        )

        userPoint.balance shouldBe 120L
        result.balanceAfter shouldBe 120L
        result.delta shouldBe 20L
        result.idempotencyKey shouldBe "k1"
        verify(pointTransactionRepository).save(any<PointTransaction>())
    }

    test("returns the existing ledger row on duplicate idempotency key without re-charging") {
        val userPoint = UserPoint(user = user, balance = 100L)
        val existing = PointTransaction(
            userId = 1L, delta = 20L, balanceAfter = 120L,
            reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k1",
        )
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k1")).thenReturn(existing)

        val result = userPointService.recordTransaction(
            userId = 1L, delta = 20L, reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k1",
        )

        result shouldBe existing
        userPoint.balance shouldBe 100L
        verify(pointTransactionRepository, never()).save(any<PointTransaction>())
    }

    test("deducts balance for a negative delta when sufficient") {
        val userPoint = UserPoint(user = user, balance = 100L)
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k2")).thenReturn(null)
        whenever(pointTransactionRepository.save(any<PointTransaction>())).thenAnswer { it.arguments[0] }

        userPointService.recordTransaction(
            userId = 1L, delta = -30L, reason = PointTransactionReason.AD_REWARD, idempotencyKey = "k2",
        )

        userPoint.balance shouldBe 70L
    }

    test("throws InsufficientPointsException for a negative delta exceeding balance") {
        val userPoint = UserPoint(user = user, balance = 10L)
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k3")).thenReturn(null)

        shouldThrow<InsufficientPointsException> {
            userPointService.recordTransaction(
                userId = 1L, delta = -30L, reason = PointTransactionReason.AD_REWARD, idempotencyKey = "k3",
            )
        }
        userPoint.balance shouldBe 10L
        verify(pointTransactionRepository, never()).save(any<PointTransaction>())
    }

    test("throws when the user point row is missing") {
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(null)

        shouldThrow<IllegalStateException> {
            userPointService.recordTransaction(
                userId = 1L, delta = 20L, reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k4",
            )
        }
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*PointTransactionRecordTest"`
Expected: 컴파일 실패 — `UserPointService` 생성자에 `pointTransactionRepository` 파라미터와 `recordTransaction` 메서드가 아직 없음.

- [ ] **Step 3: recordTransaction 구현**

`UserPointService.kt`를 다음으로 교체한다. 생성자에 `pointTransactionRepository`를 주입하고, 잠금-먼저(lock-first) 순서로 멱등 적립을 구현한다.

```kotlin
package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserPointService(
    private val userPointRepository: UserPointRepository,
    private val pointTransactionRepository: PointTransactionRepository,
    private val pointProperties: PointProperties,
) {
    fun hasEnoughBalance(userId: Long): Boolean =
        userPointRepository.existsByUserIdAndBalanceGreaterThanEqual(userId, REQUIRED_STREAM_POINTS)

    fun ensureInitialized(user: User): UserPoint =
        userPointRepository.findByUserId(user.id)
            ?: createInitialPoint(user)

    /**
     * 멱등성 키 기반 포인트 적립/차감.
     *
     * 단일 트랜잭션 안에서 (1) 사용자 포인트 행을 비관적 락으로 잡고 → (2) 멱등성 키를 조회해
     * 이미 처리됐으면 기존 원장을 그대로 반환(중복 적립 방지) → (3) 잔액을 가감 → (4) 원장 INSERT.
     * 잠금-먼저 순서이므로 같은 키/같은 사용자에 동시 호출이 와도 행 락으로 직렬화되어
     * 두 번째 호출은 첫 번째가 커밋한 원장을 보고 그대로 반환한다(이중 적립 없음).
     * 유니크 제약 `uq_point_transaction_idempotency_key`가 최종 방어선이다.
     *
     * @param delta 양수=적립, 음수=차감. 차감 시 잔액 부족이면 InsufficientPointsException.
     */
    @Transactional
    fun recordTransaction(
        userId: Long,
        delta: Long,
        reason: PointTransactionReason,
        idempotencyKey: String,
    ): PointTransaction {
        val userPoint = userPointRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserPoint not initialized for userId=$userId")

        pointTransactionRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        if (delta >= 0) {
            userPoint.charge(delta)
        } else {
            val cost = -delta
            if (userPoint.balance < cost) throw InsufficientPointsException()
            userPoint.deduct(cost)
        }

        return try {
            pointTransactionRepository.save(
                PointTransaction(
                    userId = userId,
                    delta = delta,
                    balanceAfter = userPoint.balance,
                    reason = reason,
                    idempotencyKey = idempotencyKey,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            // 잠금-먼저 순서상 도달하기 어려운 경합이지만, 유니크 제약 위반 시 기존 원장으로 수렴.
            throw e
        }
    }

    private fun createInitialPoint(user: User): UserPoint =
        try {
            userPointRepository.saveAndFlush(
                UserPoint(
                    user = user,
                    balance = pointProperties.initialBalance,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            userPointRepository.findByUserId(user.id) ?: throw e
        }

    private companion object {
        private const val REQUIRED_STREAM_POINTS = 1L
    }
}
```

> 위 `catch`는 잠금-먼저 순서 때문에 정상 경로에서 거의 도달하지 않는다(동일 키는 락 직후 `findByIdempotencyKey`에서 걸러짐). 유니크 위반이 발생하면 그대로 전파해 호출 트랜잭션이 롤백되도록 둔다 — 동시성 정확성은 Task 6 통합 테스트로 검증한다.

- [ ] **Step 4: 단위 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "*PointTransactionRecordTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: 기존 UserPointServiceTest 회귀 확인**

`UserPointServiceTest`는 생성자 시그니처가 바뀌었으므로 컴파일 에러가 난다. 해당 테스트의 `beforeTest`에서 서비스 생성을 다음으로 수정한다(목 추가):

```kotlin
    lateinit var pointTransactionRepository: com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository

    beforeTest {
        userPointRepository = mock()
        pointTransactionRepository = mock()
        userPointService = UserPointService(
            userPointRepository = userPointRepository,
            pointTransactionRepository = pointTransactionRepository,
            pointProperties = PointProperties(initialBalance = 3L),
        )
    }
```

Run: `cd apps/backend && ./gradlew test --tests "*UserPointServiceTest"`
Expected: PASS (기존 테스트 전부).

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointService.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/service
git commit -m "feat(point): add idempotent recordTransaction with pessimistic locking"
```

---

## Task 6: 멱등성·동시성 통합 테스트 (Testcontainers MySQL)

**Files:**
- Test: `apps/backend/src/test/.../domain/point/persistence/PointIdempotencyIntegrationTest.kt`

`ChatPersistenceIntegrationTest`의 Testcontainers MySQL 패턴을 따른다. 실제 MySQL 행 락으로 멱등성·동시성을 검증한다.

- [ ] **Step 1: 통합 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/persistence/PointIdempotencyIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class PointIdempotencyIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var userPointService: UserPointService

    init {
        beforeTest {
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("duplicate idempotency key does not double-credit") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup")
            )
            userPointService.ensureInitialized(user)

            val first = userPointService.recordTransaction(
                user.id, 50L, PointTransactionReason.ATTENDANCE, "attendance:${user.id}:2026-05-30"
            )
            val second = userPointService.recordTransaction(
                user.id, 50L, PointTransactionReason.ATTENDANCE, "attendance:${user.id}:2026-05-30"
            )

            second.id shouldBe first.id
            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 51L // initial 1 + 50
        }

        test("concurrent same-key calls credit exactly once") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "race")
            )
            userPointService.ensureInitialized(user)

            val threads = 8
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val failures = AtomicInteger(0)
            val key = "admob:reward:nonce-xyz"

            repeat(threads) {
                pool.submit {
                    ready.countDown()
                    go.await()
                    try {
                        userPointService.recordTransaction(
                            user.id, 40L, PointTransactionReason.AD_REWARD, key
                        )
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                    }
                }
            }
            ready.await()
            go.countDown()
            pool.shutdown()
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)

            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 41L // initial 1 + 40 once
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat")
            .withUsername("cashchat")
            .withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
```

> 동시성 테스트에서 일부 스레드가 유니크 제약 위반(`DataIntegrityViolationException`)으로 실패할 수 있으나(`failures` 카운트), 핵심 단언은 "원장 1행 + 잔액 1회 반영"이다. 잠금-먼저 순서가 정상 동작하면 대부분 기존 행 반환으로 수렴해 예외 없이 통과한다.

- [ ] **Step 2: 통합 테스트 실행**

Run: `cd apps/backend && ./gradlew test --tests "*PointIdempotencyIntegrationTest"`
Expected: PASS (2 tests). Docker 데몬 필요.

- [ ] **Step 3: 커밋**

```bash
git add apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/persistence/PointIdempotencyIntegrationTest.kt
git commit -m "test(point): add idempotency and concurrency integration tests"
```

---

## Task 7: 전체 빌드 검증 + 마무리

- [ ] **Step 1: 전체 빌드·테스트**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL. 모든 테스트 통과, Flyway V1·V2가 H2(MySQL 모드)와 Testcontainers MySQL 양쪽에서 정상 실행, validate 통과.

- [ ] **Step 2: tasks.md 체크리스트 갱신**

`docs/features/reward/tasks.md`에서 본 PR로 완료된 항목을 체크한다:
- BE-1: 전 항목 `[x]`
- BE-4: `application.yml` 설정 항목 중 본 PR 해당분(없음 — reward.admob.* 는 BE-3), Flyway 마이그레이션 항목 중 `point_transaction` 부분 `[x]` 처리하고 나머지 테이블은 후속 PR로 주석 표기.

> BE-4 항목은 원래 6개 테이블을 한 번에 만드는 것으로 적혀 있다. 도메인별 PR 분할 결정을 반영해 "테이블별로 해당 도메인 PR에서 생성"으로 메모를 남긴다.

- [ ] **Step 3: 커밋**

```bash
git add docs/features/reward/tasks.md
git commit -m "docs(reward): mark BE-1 and Flyway baseline complete in task checklist"
```

- [ ] **Step 4: PR 생성 준비**

`finishing-a-development-branch` 스킬로 dev 대상 PR을 만든다. PR 제목: `[CC-288] Flyway 도입 + 포인트 멱등성 적립(BE-1) 기반 마련`.

---

## Self-Review 결과

- **Spec 커버리지(BE-1):** ledger 엔티티/Repo(Task 3) ✓, `recordTransaction(userId, delta, reason, idempotencyKey)`(Task 5) ✓, 동일 키 재호출 시 기존 트랜잭션 반환(Task 5/6) ✓, 음수 잔액 거부(Task 5) ✓, Kotest 정상/중복/잔액부족/동시호출(Task 5·6) ✓.
- **Spec 커버리지(BE-4 부분):** Flyway 마이그레이션(dev H2 + prod MySQL)(Task 1·2) ✓, `point_transaction` 마이그레이션(Task 3) ✓. `attendance_*`·`ad_reward_*` 테이블, 출석 시드, `reward.admob.*` 설정은 본 PR 범위 외(BE-2/BE-3) — 결정 사항 #4에 명시.
- **타입 일관성:** `recordTransaction` 시그니처·`PointTransaction` 필드명(`userId`, `delta`, `balanceAfter`, `reason`, `idempotencyKey`)·`findByUserIdForUpdate`·`findByIdempotencyKey`가 Task 3~6에서 일관되게 사용됨 ✓.
- **미해결 리스크:** `conversations.uuid` 방언 매핑(BINARY(16) 가정) — Task 1 Step 3 주의 + Task 2 validate 루프로 수렴. 동시성 테스트의 일부 스레드 예외 허용 — 핵심 단언은 원장 1행/잔액 1회.
