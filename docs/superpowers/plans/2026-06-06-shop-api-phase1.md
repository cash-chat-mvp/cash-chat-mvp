# Shop API Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend API for Shop Phase 1 — a 5-item ENHANCE catalog (`GET /api/shop/items`), an atomic coin-debit + inventory-grant purchase transaction (`POST /api/shop/purchase`), and an inventory read endpoint (`GET /api/inventory/me`).

**Architecture:** Two new domains under `com.wnl.cashchat.api.domain`: `shop` (catalog, purchase orders, purchase service/facade) and `inventory` (user inventory entity, repository, read service). Purchase reuses the existing `UserPointService.recordTransaction` (pessimistic point-row lock + point-level idempotency) inside a single `@Transactional` boundary, exactly like `AttendanceService.checkIn`. Idempotency is `(userId, idempotencyKey)`-scoped on `purchase_order`; the same-key concurrent-INSERT race is recovered **outside** the transaction boundary in a non-transactional `ShopPurchaseFacade` (the failed transaction is `rollback-only`, so recovery must re-query in a fresh transaction). Coin debit, inventory UPSERT, and order INSERT follow a fixed global lock order: `point (FOR UPDATE) → user_inventory (UPSERT) → purchase_order (INSERT)`.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA (Hibernate, `ddl-auto: validate`), Flyway (shared migrations for dev H2-MySQL-mode + prod MySQL 8), Kotest 5.9.1 (`FunSpec`) + TestContainers MySQL 8.4.0, Mockito-Kotlin 5.4.0.

---

## Scope

This plan covers **backend only** (tasks BE-1 … BE-4 from `docs/features/shop/tasks.md`), matching branch `feature/cc-292-shop-api`. Frontend (FE-1…FE-3) and infra runbook/monitoring (INF-1, INF-2) are out of scope for this branch.

**Precondition (verified):** Reward BE-1 "포인트 멱등성 확장" is already merged — `UserPointService.recordTransaction(userId, delta, reason, idempotencyKey)` exists with pessimistic lock (`UserPointRepository.findByUserIdForUpdate`) and point-level idempotency (`point_transaction.uq_point_transaction_idempotency_key`). This plan builds on it; it does **not** reimplement point logic.

## Source-of-truth references

- Spec: `docs/features/shop/spec.md`
- Tasks: `docs/features/shop/tasks.md`
- Pattern templates already in the repo (read these for style):
  - `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/service/AttendanceService.kt` (transaction + saveAndFlush/DIVE pattern)
  - `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointService.kt` (recordTransaction signature)
  - `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/web/controller/AttendanceController.kt` (`Authentication.userId()` extension)
  - `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/attendance/web/exception/AttendanceExceptionHandler.kt` (domain-scoped `@RestControllerAdvice`)
  - `apps/backend/src/main/resources/db/migration/V3__attendance.sql` (migration + seed style)
  - `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/persistence/PointIdempotencyIntegrationTest.kt` (integration test style)
  - `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/attendance/web/controller/AttendanceControllerTest.kt` (web slice test style)

## Conventions (apply to every task)

- All commands run from `apps/backend/` (`cd apps/backend` first). On Windows use `gradlew.bat`; examples below use `./gradlew` — substitute as needed.
- **Run gradle builds one at a time** — never overlap gradle invocations (file-lock conflicts).
- Package root: `com.wnl.cashchat.api`. Source root: `src/main/kotlin/...`, test root: `src/test/kotlin/...`.
- Entities holding **transactional** data extend `BaseEntity` (adds `created_at`/`updated_at`, `Instant`, auditing). Entities holding **reference/seed** data (`ShopItem`, `ShopItemGrant`) do **not** extend `BaseEntity` — mirrors `AttendanceReward`.
- `ddl-auto: validate` is on: every entity column must exactly match the V6 migration (name snake_case, nullability). Keep them in lockstep.
- Commit after each task with a Conventional Commit (English type/scope, **Korean description** per repo convention), e.g. `feat(shop): 구매 트랜잭션 서비스 추가`.

## File structure (what gets created)

```
src/main/kotlin/com/wnl/cashchat/api/
├─ domain/point/persistence/entity/PointTransactionReason.kt   (MODIFY: add SHOP_PURCHASE)
├─ domain/inventory/
│  ├─ persistence/entity/UserInventory.kt
│  ├─ persistence/repository/UserInventoryRepository.kt         (read + native UPSERT)
│  ├─ service/InventoryService.kt
│  ├─ service/InventoryLine.kt
│  └─ web/
│     ├─ controller/InventoryController.kt
│     └─ response/InventoryResponse.kt
└─ domain/shop/
   ├─ persistence/entity/ShopItem.kt
   ├─ persistence/entity/ShopItemCategory.kt
   ├─ persistence/entity/ShopItemGrant.kt
   ├─ persistence/entity/PurchaseOrder.kt
   ├─ persistence/entity/PurchaseOrderStatus.kt
   ├─ persistence/repository/ShopItemRepository.kt
   ├─ persistence/repository/ShopItemGrantRepository.kt
   ├─ persistence/repository/PurchaseOrderRepository.kt
   ├─ exception/ItemNotFoundException.kt
   ├─ exception/ItemInactiveException.kt
   ├─ exception/InsufficientCoinException.kt
   ├─ exception/IdempotencyKeyConflictException.kt
   ├─ service/ShopCatalogService.kt
   ├─ service/ShopPurchaseService.kt
   ├─ service/ShopPurchaseFacade.kt
   ├─ service/PurchaseCommand.kt
   ├─ service/PurchaseResult.kt
   └─ web/
      ├─ controller/ShopController.kt
      ├─ request/PurchaseRequest.kt
      ├─ response/ShopCatalogResponse.kt
      ├─ response/PurchaseResponse.kt
      └─ exception/ShopExceptionHandler.kt

src/main/resources/db/migration/V6__shop.sql                   (tables + seed)

src/test/kotlin/com/wnl/cashchat/api/domain/
├─ shop/persistence/ShopMigrationIntegrationTest.kt
├─ shop/service/ShopCatalogServiceTest.kt
├─ shop/service/ShopPurchaseIntegrationTest.kt
├─ shop/web/controller/ShopControllerTest.kt
└─ inventory/web/controller/InventoryControllerTest.kt
```

---

## Task 1: Flyway migration V6 + seed (BE-4)

Create the four tables and Phase-1 seed first so `ddl-auto: validate` and TestContainers-backed tests have a schema.

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V6__shop.sql`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/persistence/ShopMigrationIntegrationTest.kt`

- [ ] **Step 1: Write the migration**

Create `V6__shop.sql` (snake_case columns, `TIMESTAMP(6)` for BaseEntity tables only, `uk_`/`fk_`/`idx_` naming matching V3–V5):

```sql
-- V6: 상점 Phase 1 — 강화재료 카탈로그 / 구매 주문 / 인벤토리

-- 카탈로그(참조 데이터): BaseEntity 미상속 → created_at/updated_at 없음 (attendance_reward 와 동일)
CREATE TABLE shop_item (
    item_code      VARCHAR(50)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    price_coin     BIGINT       NOT NULL,
    effect_summary VARCHAR(255) NOT NULL,
    is_active      BOOLEAN      NOT NULL,
    display_order  INT          NOT NULL,
    PRIMARY KEY (item_code)
);
CREATE INDEX idx_shop_item_category ON shop_item (category);

-- 다건 grant 조인(참조 데이터)
CREATE TABLE shop_item_grant (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    item_code       VARCHAR(50) NOT NULL,
    grant_item_code VARCHAR(50) NOT NULL,
    grant_qty       INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shop_item_grant_item_grant UNIQUE (item_code, grant_item_code),
    CONSTRAINT fk_shop_item_grant_item FOREIGN KEY (item_code) REFERENCES shop_item (item_code)
);

-- 구매 주문(트랜잭션 데이터): (user_id, idempotency_key) 복합 유니크 = 멱등성 스코프
CREATE TABLE purchase_order (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    item_code       VARCHAR(50)  NOT NULL,
    qty             INT          NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    snapshot_price  BIGINT       NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_purchase_order_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_purchase_order_user_id ON purchase_order (user_id);

-- 사용자 인벤토리(트랜잭션 데이터): (user_id, item_code) 복합 유니크 = UPSERT 키
CREATE TABLE user_inventory (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    item_code  VARCHAR(50)  NOT NULL,
    qty        INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_inventory_user_item UNIQUE (user_id, item_code),
    CONSTRAINT fk_user_inventory_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 시드: 강화재료 5종 (spec 부록 표)
INSERT INTO shop_item (item_code, name, category, price_coin, effect_summary, is_active, display_order) VALUES
    ('ENHANCE_PACK',     '강화 패키지', 'ENHANCE', 1200, '진화석 5 + 확률 부적 1 (묶음)',              TRUE, 5),
    ('EVO_STONE',        '진화석',      'ENHANCE', 200,  '진화 시도 1회 필요 재료',                    TRUE, 10),
    ('EVO_STONE_BUNDLE', '진화석 ×5',   'ENHANCE', 900,  '묶음 구매 (10% 할인)',                       TRUE, 20),
    ('LUCK_CHARM',       '확률 부적',   'ENHANCE', 500,  '다음 진화 시도 성공 확률 +10%p (1회용)',     TRUE, 30),
    ('PROTECT_TICKET',   '보호권',      'ENHANCE', 800,  '실패 시 소비 코인 50% 반환 (1회용)',         TRUE, 40);

-- 시드: shop_item_grant 6행 (단건도 자기 자신 grant 1행 — 일관 처리 경로)
INSERT INTO shop_item_grant (item_code, grant_item_code, grant_qty) VALUES
    ('EVO_STONE',        'EVO_STONE',      1),
    ('EVO_STONE_BUNDLE', 'EVO_STONE',      5),
    ('LUCK_CHARM',       'LUCK_CHARM',     1),
    ('PROTECT_TICKET',   'PROTECT_TICKET', 1),
    ('ENHANCE_PACK',     'EVO_STONE',      5),
    ('ENHANCE_PACK',     'LUCK_CHARM',     1);
```

- [ ] **Step 2: Write the failing migration test**

Create `ShopMigrationIntegrationTest.kt` — boots the full context (which runs Flyway on the TestContainers MySQL) and asserts the seed is present. Mirrors the companion/`@DynamicPropertySource` block from `PointIdempotencyIntegrationTest`.

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemGrantRepository
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class ShopMigrationIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var shopItemRepository: ShopItemRepository
    @Autowired lateinit var shopItemGrantRepository: ShopItemGrantRepository

    init {
        test("V6 seeds 5 active ENHANCE items ordered by displayOrder") {
            val items = shopItemRepository
                .findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(ShopItemCategory.ENHANCE)
            items.map { it.itemCode } shouldBe listOf(
                "ENHANCE_PACK", "EVO_STONE", "EVO_STONE_BUNDLE", "LUCK_CHARM", "PROTECT_TICKET"
            )
        }

        test("V6 seeds 6 grant rows including the ENHANCE_PACK bundle") {
            shopItemGrantRepository.count() shouldBe 6L
            shopItemGrantRepository.findByItemCodeOrderByGrantItemCodeAsc("ENHANCE_PACK")
                .map { it.grantItemCode to it.grantQty } shouldBe listOf("EVO_STONE" to 5, "LUCK_CHARM" to 1)
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

- [ ] **Step 3: Run the test to verify it fails to compile**

Run: `./gradlew test --tests "*ShopMigrationIntegrationTest*"`
Expected: COMPILE FAILURE — `ShopItem*`, `ShopItemCategory`, repositories don't exist yet. (This proves the test is wired; the entities/repos arrive in Tasks 2–4. We come back and green this test at the end of Task 4.)

- [ ] **Step 4: Commit the migration**

```bash
git add apps/backend/src/main/resources/db/migration/V6__shop.sql \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/persistence/ShopMigrationIntegrationTest.kt
git commit -m "feat(shop): Phase 1 Flyway 마이그레이션·시드(V6) 추가"
```

> Note: the migration test stays red until Task 4 creates the entities/repos it imports. That is expected and called out again at the end of Task 4.

---

## Task 2: Enums + `PointTransactionReason.SHOP_PURCHASE`

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/persistence/entity/ShopItemCategory.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/persistence/entity/PurchaseOrderStatus.kt`

- [ ] **Step 1: Add `SHOP_PURCHASE` to `PointTransactionReason`**

The existing file (its doc-comment explicitly says to add shop reasons here):

```kotlin
package com.wnl.cashchat.api.domain.point.persistence.entity

/**
 * 포인트 적립/차감 사유. 적립 채널(출석·광고)과 소비 채널(상점)을 함께 정의한다.
 */
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
    SHOP_PURCHASE,
}
```

- [ ] **Step 2: Create `ShopItemCategory`**

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.entity

/**
 * 상점 카테고리. Phase 1 은 ENHANCE 만 활성(phase1Active=true).
 * COSMETIC/VOUCHER 는 enum 범위에는 있으나 Phase 1 카탈로그 비노출.
 */
enum class ShopItemCategory {
    ENHANCE,
    COSMETIC,
    VOUCHER,
    ;

    val phase1Active: Boolean
        get() = this == ENHANCE
}
```

- [ ] **Step 3: Create `PurchaseOrderStatus`**

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.entity

/**
 * 구매 주문 상태.
 * - COMPLETED: 트랜잭션 커밋 성공. Phase 1 에서 purchase_order 행이 가지는 유일한 값.
 * - FAILED: 예약값(사후 보상 트랜잭션 실패 마킹용). Phase 1 미사용. 모니터링은 이 값=0 을 정상으로 가정.
 */
enum class PurchaseOrderStatus {
    COMPLETED,
    FAILED,
}
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (existing point/attendance/ad code already uses the enum by name; adding a constant is backward-compatible).

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/persistence/entity/ShopItemCategory.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/persistence/entity/PurchaseOrderStatus.kt
git commit -m "feat(shop): 도메인 enum(카테고리·주문상태·SHOP_PURCHASE 사유) 추가"
```

---

## Task 3: Inventory domain (entity, repository with UPSERT, read service)

**Files:**
- Create: `.../domain/inventory/persistence/entity/UserInventory.kt`
- Create: `.../domain/inventory/persistence/repository/UserInventoryRepository.kt`
- Create: `.../domain/inventory/service/InventoryLine.kt`
- Create: `.../domain/inventory/service/InventoryService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/inventory/persistence/UserInventoryUpsertIntegrationTest.kt`

- [ ] **Step 1: Create `UserInventory` entity**

```kotlin
package com.wnl.cashchat.api.domain.inventory.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 사용자 보유 아이템 수량. (user_id, item_code) 복합 유니크.
 * 적재(grant)는 UserInventoryRepository.upsertQty 네이티브 UPSERT 로 동시성 안전하게 처리한다.
 */
@Entity
@Table(
    name = "user_inventory",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_user_inventory_user_item", columnNames = ["user_id", "item_code"]),
    ],
)
class UserInventory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(nullable = false)
    val qty: Int,
) : BaseEntity()
```

- [ ] **Step 2: Create `UserInventoryRepository` (read + native UPSERT)**

The UPSERT references the named parameter `:qty` again in the UPDATE clause (avoids the deprecated `VALUES()` function and works in both MySQL 8 and H2 MySQL-mode). `flushAutomatically`/`clearAutomatically` keep the persistence context consistent with the native write.

```kotlin
package com.wnl.cashchat.api.domain.inventory.persistence.repository

import com.wnl.cashchat.api.domain.inventory.persistence.entity.UserInventory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserInventoryRepository : JpaRepository<UserInventory, Long> {

    fun findByUserIdOrderByItemCodeAsc(userId: Long): List<UserInventory>

    fun findByUserIdAndItemCode(userId: Long, itemCode: String): UserInventory?

    /**
     * 동시성 안전 UPSERT: (user_id, item_code) 가 있으면 qty 누적, 없으면 INSERT.
     * UPDATE 절에서 VALUES(qty) 대신 명명 파라미터 :qty 를 재사용해 MySQL 8 / H2(MySQL 모드) 모두 호환.
     * 네이티브 쓰기 후 영속성 컨텍스트를 flush+clear 해 이후 조회가 DB 최신값을 보게 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO user_inventory (user_id, item_code, qty, created_at, updated_at)
            VALUES (:userId, :itemCode, :qty, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE qty = qty + :qty, updated_at = CURRENT_TIMESTAMP(6)
        """,
        nativeQuery = true,
    )
    fun upsertQty(
        @Param("userId") userId: Long,
        @Param("itemCode") itemCode: String,
        @Param("qty") qty: Int,
    )
}
```

- [ ] **Step 3: Create `InventoryLine` (service-layer result)**

```kotlin
package com.wnl.cashchat.api.domain.inventory.service

data class InventoryLine(
    val itemCode: String,
    val qty: Int,
)
```

- [ ] **Step 4: Create `InventoryService` (read API; consume extension point lives here in future specs)**

```kotlin
package com.wnl.cashchat.api.domain.inventory.service

import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인벤토리 읽기 API. 후속 진화/소모(consume) 시스템은 이 도메인에 consume 연산을 추가해 확장한다.
 */
@Service
class InventoryService(
    private val userInventoryRepository: UserInventoryRepository,
) {
    @Transactional(readOnly = true)
    fun getMine(userId: Long): List<InventoryLine> =
        userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId)
            .map { InventoryLine(itemCode = it.itemCode, qty = it.qty) }
}
```

- [ ] **Step 5: Write the failing UPSERT integration test**

Create `UserInventoryUpsertIntegrationTest.kt`. Needs a real `users` row (FK) — create one via `UserRepository`, like `PointIdempotencyIntegrationTest`.

```kotlin
package com.wnl.cashchat.api.domain.inventory.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
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
class UserInventoryUpsertIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userInventoryRepository: UserInventoryRepository

    init {
        beforeTest {
            userInventoryRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("upsertQty inserts on first call and accumulates on second") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "inv")
            )

            userInventoryRepository.upsertQty(user.id, "EVO_STONE", 2)
            userInventoryRepository.upsertQty(user.id, "EVO_STONE", 3)

            val rows = userInventoryRepository.findByUserIdOrderByItemCodeAsc(user.id)
            rows.size shouldBe 1
            rows[0].itemCode shouldBe "EVO_STONE"
            rows[0].qty shouldBe 5
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

> Verify `User`'s constructor params (`role`, `provider`, `name`) against `domain/user/persistence/entity/User.kt` before running; copy exactly what `PointIdempotencyIntegrationTest` uses (it uses `User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "...")`).

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "*UserInventoryUpsertIntegrationTest*"`
Expected: PASS — verifies the native `ON DUPLICATE KEY UPDATE` accumulates on the real MySQL container.

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/inventory \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/inventory
git commit -m "feat(inventory): UserInventory 엔티티·UPSERT 리포지토리·읽기 서비스 추가"
```

---

## Task 4: Shop domain entities + repositories

**Files:**
- Create: `.../domain/shop/persistence/entity/ShopItem.kt`
- Create: `.../domain/shop/persistence/entity/ShopItemGrant.kt`
- Create: `.../domain/shop/persistence/entity/PurchaseOrder.kt`
- Create: `.../domain/shop/persistence/repository/ShopItemRepository.kt`
- Create: `.../domain/shop/persistence/repository/ShopItemGrantRepository.kt`
- Create: `.../domain/shop/persistence/repository/PurchaseOrderRepository.kt`

- [ ] **Step 1: Create `ShopItem` (reference data — no BaseEntity)**

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 상점 카탈로그 아이템(참조/시드 데이터). itemCode 가 자연키 PK.
 * 운영자 관리 UI 는 범위 외 — 시드/마이그레이션으로만 변경한다.
 */
@Entity
@Table(name = "shop_item")
class ShopItem(
    @Id
    @Column(name = "item_code", length = 50)
    val itemCode: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val category: ShopItemCategory,

    @Column(name = "price_coin", nullable = false)
    val priceCoin: Long,

    @Column(name = "effect_summary", nullable = false, length = 255)
    val effectSummary: String,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
)
```

- [ ] **Step 2: Create `ShopItemGrant` (reference data — no BaseEntity)**

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * itemCode 구매 시 지급할 (grantItemCode, grantQty) 목록. 패키지는 여러 행, 단건도 자기 자신 1행.
 */
@Entity
@Table(
    name = "shop_item_grant",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_shop_item_grant_item_grant", columnNames = ["item_code", "grant_item_code"]),
    ],
)
class ShopItemGrant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(name = "grant_item_code", nullable = false, length = 50)
    val grantItemCode: String,

    @Column(name = "grant_qty", nullable = false)
    val grantQty: Int,
)
```

- [ ] **Step 3: Create `PurchaseOrder` (transactional data — extends BaseEntity)**

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 구매 주문. (user_id, idempotency_key) 복합 유니크로 멱등성을 사용자별 격리.
 * snapshotPrice 는 구매 시점 총 결제 코인(priceCoin * qty).
 */
@Entity
@Table(
    name = "purchase_order",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_purchase_order_user_idem", columnNames = ["user_id", "idempotency_key"]),
    ],
)
class PurchaseOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(nullable = false)
    val qty: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PurchaseOrderStatus,

    @Column(name = "snapshot_price", nullable = false)
    val snapshotPrice: Long,
) : BaseEntity()
```

- [ ] **Step 4: Create the three repositories**

`ShopItemRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.repository

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import org.springframework.data.jpa.repository.JpaRepository

interface ShopItemRepository : JpaRepository<ShopItem, String> {
    fun findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category: ShopItemCategory): List<ShopItem>
}
```

`ShopItemGrantRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.repository

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemGrant
import org.springframework.data.jpa.repository.JpaRepository

interface ShopItemGrantRepository : JpaRepository<ShopItemGrant, Long> {
    // itemCode 오름차순 grantItemCode 정렬 → UPSERT 락 순서 고정(데드락 방지)
    fun findByItemCodeOrderByGrantItemCodeAsc(itemCode: String): List<ShopItemGrant>
}
```

`PurchaseOrderRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.persistence.repository

import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrder
import org.springframework.data.jpa.repository.JpaRepository

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PurchaseOrder?
}
```

- [ ] **Step 5: Run the migration test from Task 1 (now greenable) + UPSERT test**

Run: `./gradlew test --tests "*ShopMigrationIntegrationTest*" --tests "*UserInventoryUpsertIntegrationTest*"`
Expected: PASS — the entities/repos the migration test imports now exist, and `ddl-auto: validate` confirms the V6 schema matches every entity.

> If validate fails, the error names the mismatched table/column — reconcile the entity `@Column`/`@Table` against `V6__shop.sql` (name, nullability) and re-run. Common culprits: a BaseEntity table missing `created_at`/`updated_at`, or a snake_case mismatch.

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/persistence
git commit -m "feat(shop): ShopItem·ShopItemGrant·PurchaseOrder 엔티티·리포지토리 추가"
```

---

## Task 5: Shop domain exceptions + exception handler

**Files:**
- Create: `.../domain/shop/exception/ItemNotFoundException.kt`
- Create: `.../domain/shop/exception/ItemInactiveException.kt`
- Create: `.../domain/shop/exception/InsufficientCoinException.kt`
- Create: `.../domain/shop/exception/IdempotencyKeyConflictException.kt`
- Create: `.../domain/shop/web/exception/ShopExceptionHandler.kt`

- [ ] **Step 1: Create the four domain exceptions**

`ItemNotFoundException.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.exception

class ItemNotFoundException(
    message: String = "Shop item not found",
) : RuntimeException(message)
```

`ItemInactiveException.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.exception

class ItemInactiveException(
    message: String = "Shop item is inactive",
) : RuntimeException(message)
```

`InsufficientCoinException.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.exception

class InsufficientCoinException(
    message: String = "Insufficient coin balance",
) : RuntimeException(message)
```

`IdempotencyKeyConflictException.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.exception

class IdempotencyKeyConflictException(
    message: String = "Idempotency key reused with a different payload",
) : RuntimeException(message)
```

- [ ] **Step 2: Create `ShopExceptionHandler` (domain-scoped advice)**

Maps each domain error to its spec'd `{code, message}` + HTTP status, plus request-validation normalization (`@Valid` failure → 400 `VALIDATION`; bad `category` enum → 400 `INVALID_CATEGORY`; unparseable body → 400 `VALIDATION`). Scoped to the shop package so it does not affect other domains.

```kotlin
package com.wnl.cashchat.api.domain.shop.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.shop.exception.IdempotencyKeyConflictException
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.exception.ItemInactiveException
import com.wnl.cashchat.api.domain.shop.exception.ItemNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.shop"])
class ShopExceptionHandler {

    @ExceptionHandler(ItemNotFoundException::class)
    fun handleItemNotFound(e: ItemNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("ITEM_NOT_FOUND", e.message ?: "Shop item not found"))

    @ExceptionHandler(ItemInactiveException::class)
    fun handleItemInactive(e: ItemInactiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("ITEM_INACTIVE", e.message ?: "Shop item is inactive"))

    @ExceptionHandler(InsufficientCoinException::class)
    fun handleInsufficientCoin(e: InsufficientCoinException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INSUFFICIENT_COIN", e.message ?: "Insufficient coin balance"))

    @ExceptionHandler(IdempotencyKeyConflictException::class)
    fun handleIdempotencyConflict(e: IdempotencyKeyConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("IDEMPOTENCY_KEY_CONFLICT", e.message ?: "Idempotency key conflict"))

    // @Valid 실패(qty<1, idempotencyKey 형식 위반 등) → 400 VALIDATION
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION", e.bindingResult.fieldErrors.firstOrNull()?.let {
                "${it.field}: ${it.defaultMessage}"
            } ?: "Invalid request"))

    // 잘못된 JSON 본문 등 → 400 VALIDATION
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION", "Malformed request body"))

    // category enum 범위 밖(예: ?category=FOO) → 400 INVALID_CATEGORY
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_CATEGORY", "Invalid category: ${e.value}"))
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/exception \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/web/exception
git commit -m "feat(shop): 도메인 예외·예외 핸들러(코드/상태 매핑) 추가"
```

---

## Task 6: `ShopCatalogService` + test

**Files:**
- Create: `.../domain/shop/service/ShopCatalogService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/service/ShopCatalogServiceTest.kt`

- [ ] **Step 1: Write the failing unit test (mocked repository)**

```kotlin
package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShopCatalogServiceTest : FunSpec({
    lateinit var shopItemRepository: ShopItemRepository
    lateinit var service: ShopCatalogService

    beforeTest {
        shopItemRepository = mock()
        service = ShopCatalogService(shopItemRepository)
    }

    test("ENHANCE returns active items from repository, displayOrder-sorted by query") {
        whenever(
            shopItemRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(ShopItemCategory.ENHANCE)
        ).thenReturn(
            listOf(
                ShopItem("EVO_STONE", "진화석", ShopItemCategory.ENHANCE, 200, "재료", true, 10),
            )
        )

        val items = service.listItems(ShopItemCategory.ENHANCE)

        items.map { it.itemCode } shouldBe listOf("EVO_STONE")
    }

    test("COSMETIC returns empty list (Phase 1 inactive category)") {
        whenever(
            shopItemRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(ShopItemCategory.COSMETIC)
        ).thenReturn(emptyList())

        service.listItems(ShopItemCategory.COSMETIC) shouldBe emptyList()
    }
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*ShopCatalogServiceTest*"`
Expected: COMPILE FAILURE — `ShopCatalogService` does not exist.

- [ ] **Step 3: Implement `ShopCatalogService`**

```kotlin
package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 조회. isActive=true + displayOrder 오름차순(쿼리 정렬).
 * Phase 1 은 ENHANCE 만 시드돼 있어 COSMETIC/VOUCHER 는 빈 리스트가 반환된다.
 * phase1Active 플래그는 응답 매퍼(ShopCatalogResponse)가 category 로부터 계산한다.
 */
@Service
class ShopCatalogService(
    private val shopItemRepository: ShopItemRepository,
) {
    @Transactional(readOnly = true)
    fun listItems(category: ShopItemCategory): List<ShopItem> =
        shopItemRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "*ShopCatalogServiceTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/service/ShopCatalogService.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/service/ShopCatalogServiceTest.kt
git commit -m "feat(shop): 카탈로그 조회 서비스 추가"
```

---

## Task 7: `ShopPurchaseService` + `ShopPurchaseFacade` + integration tests

This is the core. The `@Transactional` service does the purchase; the **non-transactional** facade wraps it to recover from the same-key concurrent-INSERT race in a fresh transaction.

**Files:**
- Create: `.../domain/shop/service/PurchaseCommand.kt`
- Create: `.../domain/shop/service/PurchaseResult.kt`
- Create: `.../domain/shop/service/ShopPurchaseService.kt`
- Create: `.../domain/shop/service/ShopPurchaseFacade.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/service/ShopPurchaseIntegrationTest.kt`

- [ ] **Step 1: Create the service command/result types**

`PurchaseCommand.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.service

data class PurchaseCommand(
    val itemCode: String,
    val qty: Int,
    val idempotencyKey: String,
)
```

`PurchaseResult.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus

data class PurchaseResult(
    val purchaseOrderId: Long,
    val status: PurchaseOrderStatus,
    val coinBalance: Long,
    val inventory: List<InventoryLine>,
)
```

- [ ] **Step 2: Implement `ShopPurchaseService` (the `@Transactional` core)**

Key ordering inside `purchase`: idempotency pre-check → item validate → **point debit (FOR UPDATE lock)** → **inventory UPSERT (itemCode-ordered)** → **order INSERT via saveAndFlush (last)**. The order INSERT is last so that a same-key race loser hits the unique violation *after* its speculative inventory UPSERT, and the whole transaction rolls back together. `InsufficientPointsException` (402 from the point layer) is caught and rethrown as `InsufficientCoinException` (→ 400). `replayAfterRace` is a separate `@Transactional` method the facade calls in a fresh transaction.

```kotlin
package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.shop.exception.IdempotencyKeyConflictException
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.exception.ItemInactiveException
import com.wnl.cashchat.api.domain.shop.exception.ItemNotFoundException
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrder
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus
import com.wnl.cashchat.api.domain.shop.persistence.repository.PurchaseOrderRepository
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemGrantRepository
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 구매 트랜잭션 코어. 전역 락 순서: point(FOR UPDATE) → user_inventory(UPSERT) → purchase_order(INSERT).
 *
 * 동시 INSERT 경합(같은 (userId, key) 더블클릭)은 purchase_order 복합 유니크 위반(DataIntegrityViolationException)으로
 * 패자가 잡히지만, 그 트랜잭션은 rollback-only 로 마킹돼 내부 복구가 불가능하다.
 * 따라서 경합 복구(재조회 후 멱등 처리)는 트랜잭션 경계 바깥의 ShopPurchaseFacade 가 수행한다.
 */
@Service
class ShopPurchaseService(
    private val shopItemRepository: ShopItemRepository,
    private val shopItemGrantRepository: ShopItemGrantRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val userInventoryRepository: UserInventoryRepository,
    private val userPointService: UserPointService,
    private val userPointRepository: UserPointRepository,
) {
    @Transactional
    fun purchase(userId: Long, command: PurchaseCommand): PurchaseResult {
        // 1) 멱등성 선조회: 같은 (userId, key) 주문이 이미 있으면 재차감 없이 현재 상태 반환
        purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, command.idempotencyKey)?.let {
            return buildReplayResult(userId, it, command)
        }

        // 2) 아이템 검증
        val item = shopItemRepository.findById(command.itemCode)
            .orElseThrow { ItemNotFoundException() }
        if (!item.isActive) throw ItemInactiveException()

        val totalPrice = item.priceCoin * command.qty

        // 3) 코인 차감(point 행 FOR UPDATE → 잔액 검증 → 멱등 원장). 402(부족) → 상점 도메인 400 으로 변환.
        try {
            userPointService.recordTransaction(
                userId = userId,
                delta = -totalPrice,
                reason = PointTransactionReason.SHOP_PURCHASE,
                idempotencyKey = "shop:purchase:$userId:${command.idempotencyKey}",
            )
        } catch (e: InsufficientPointsException) {
            throw InsufficientCoinException()
        }

        // 4) 인벤토리 적재: grant 를 grantItemCode 오름차순으로 UPSERT(락 순서 고정 → 데드락 방지)
        val grants = shopItemGrantRepository.findByItemCodeOrderByGrantItemCodeAsc(command.itemCode)
        grants.forEach { grant ->
            userInventoryRepository.upsertQty(userId, grant.grantItemCode, grant.grantQty * command.qty)
        }

        // 5) 주문 INSERT(마지막). saveAndFlush 로 DIVE 를 트랜잭션 안에서 강제 → 경합 패자는 전체 롤백.
        val order = purchaseOrderRepository.saveAndFlush(
            PurchaseOrder(
                userId = userId,
                idempotencyKey = command.idempotencyKey,
                itemCode = command.itemCode,
                qty = command.qty,
                status = PurchaseOrderStatus.COMPLETED,
                snapshotPrice = totalPrice,
            )
        )

        return buildResult(userId, order)
    }

    /**
     * 동시 INSERT 경합 패자 복구: 원 트랜잭션은 이미 롤백됨. Facade(비트랜잭션)가 호출하므로
     * 이 메서드의 @Transactional 이 신규 트랜잭션을 열어 커밋된 주문을 재조회한다.
     * 주문이 없으면(= purchase_order 유니크 외의 무결성 위반) null 을 반환해 Facade 가 원 예외를 전파한다.
     */
    @Transactional
    fun replayAfterRace(userId: Long, command: PurchaseCommand): PurchaseResult? {
        val order = purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, command.idempotencyKey)
            ?: return null
        return buildReplayResult(userId, order, command)
    }

    // 저장된 주문의 itemCode/qty 가 요청과 일치하는지 검증(불일치 → 409), 일치하면 현재 상태 반환.
    private fun buildReplayResult(userId: Long, order: PurchaseOrder, command: PurchaseCommand): PurchaseResult {
        if (order.itemCode != command.itemCode || order.qty != command.qty) {
            throw IdempotencyKeyConflictException()
        }
        return buildResult(userId, order)
    }

    // 현재 시점 코인 잔액 + 인벤토리(itemCode 오름차순)를 재조회해 결과를 만든다(stale 스냅샷 미반환).
    private fun buildResult(userId: Long, order: PurchaseOrder): PurchaseResult {
        val balance = userPointRepository.findByUserId(userId)?.balance ?: 0L
        val inventory = userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId)
            .map { InventoryLine(itemCode = it.itemCode, qty = it.qty) }
        return PurchaseResult(
            purchaseOrderId = order.id,
            status = order.status,
            coinBalance = balance,
            inventory = inventory,
        )
    }
}
```

- [ ] **Step 3: Implement `ShopPurchaseFacade` (non-transactional race recovery)**

```kotlin
package com.wnl.cashchat.api.domain.shop.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * 구매 진입점. @Transactional 을 두지 않는다 — 동시 INSERT 경합 시 ShopPurchaseService.purchase 의
 * 트랜잭션은 rollback-only 로 마킹되므로, 복구(재조회)는 반드시 그 트랜잭션 바깥에서 신규 트랜잭션으로 해야 한다.
 */
@Service
class ShopPurchaseFacade(
    private val shopPurchaseService: ShopPurchaseService,
) {
    fun purchase(userId: Long, command: PurchaseCommand): PurchaseResult =
        try {
            shopPurchaseService.purchase(userId, command)
        } catch (e: DataIntegrityViolationException) {
            // 경합 패자: 커밋된 주문을 신규 트랜잭션으로 재조회해 멱등 처리.
            // purchase_order 유니크 위반이면 주문이 존재 → 현재 상태/409. 그 외 무결성 위반이면 null → 원 예외 전파.
            shopPurchaseService.replayAfterRace(userId, command) ?: throw e
        }
}
```

- [ ] **Step 4: Write the failing integration test**

Covers every BE-2 scenario. Uses real `User` + `UserPointService.ensureInitialized` (initial balance from `app.points.initial-balance`; see note below on how to set a usable starting balance).

```kotlin
package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.shop.exception.IdempotencyKeyConflictException
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.exception.ItemInactiveException
import com.wnl.cashchat.api.domain.shop.exception.ItemNotFoundException
import com.wnl.cashchat.api.domain.shop.persistence.repository.PurchaseOrderRepository
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class ShopPurchaseIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var userInventoryRepository: UserInventoryRepository
    @Autowired lateinit var purchaseOrderRepository: PurchaseOrderRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var facade: ShopPurchaseFacade

    /** user_points 행을 만든 뒤 테스트가 원하는 시작 잔액으로 직접 세팅한다(초기 시드 잔액과 무관하게). */
    private fun newUserWithBalance(name: String, balance: Long): Long {
        val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
        userPointService.ensureInitialized(user)
        // 시작 잔액을 명시값으로 맞추기 위해 차이만큼 적립(테스트 전용 셋업)
        val current = userPointRepository.findByUserId(user.id)!!.balance
        if (balance > current) {
            userPointService.recordTransaction(
                user.id, balance - current, PointTransactionReason.AD_REWARD, "setup:$name"
            )
        }
        return user.id
    }

    init {
        beforeTest {
            purchaseOrderRepository.deleteAll()
            userInventoryRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("normal purchase debits coin and grants inventory atomically") {
            val userId = newUserWithBalance("normal", 1250)

            val result = facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k1"))

            result.status.name shouldBe "COMPLETED"
            result.coinBalance shouldBe 1050L
            result.inventory shouldBe listOf(com.wnl.cashchat.api.domain.inventory.service.InventoryLine("EVO_STONE", 1))
            purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, "k1")!!.snapshotPrice shouldBe 200L
            // 포인트 원장에 사용자 스코프 멱등성 키로 차감(-200) 트랜잭션이 1건 기록됐는지 확인
            pointTransactionRepository.findByIdempotencyKey("shop:purchase:$userId:k1")!!.delta shouldBe -200L
        }

        test("package purchase grants multiple items in one transaction") {
            val userId = newUserWithBalance("pack", 2000)

            val result = facade.purchase(userId, PurchaseCommand("ENHANCE_PACK", 1, "k2"))

            result.coinBalance shouldBe 800L
            result.inventory.first { it.itemCode == "EVO_STONE" }.qty shouldBe 5
            result.inventory.first { it.itemCode == "LUCK_CHARM" }.qty shouldBe 1
        }

        test("qty>1 scales both price and grant") {
            val userId = newUserWithBalance("qty", 1000)

            val result = facade.purchase(userId, PurchaseCommand("EVO_STONE", 2, "k-qty"))

            result.coinBalance shouldBe 600L // 1000 - 200*2
            result.inventory.first { it.itemCode == "EVO_STONE" }.qty shouldBe 2
        }

        test("insufficient coin throws InsufficientCoinException and changes nothing") {
            val userId = newUserWithBalance("poor", 100)

            shouldThrow<InsufficientCoinException> {
                facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k3"))
            }

            userPointRepository.findByUserId(userId)!!.balance shouldBe 100L
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId) shouldBe emptyList()
            purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, "k3") shouldBe null
        }

        test("unknown itemCode throws ItemNotFoundException") {
            val userId = newUserWithBalance("nf", 1000)
            shouldThrow<ItemNotFoundException> {
                facade.purchase(userId, PurchaseCommand("NOPE", 1, "k4"))
            }
        }

        test("idempotent replay returns current state without double-debit") {
            val userId = newUserWithBalance("idem", 1250)

            val first = facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k5"))
            val second = facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k5"))

            second.purchaseOrderId shouldBe first.purchaseOrderId
            userPointRepository.findByUserId(userId)!!.balance shouldBe 1050L // debited once
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId).first().qty shouldBe 1
            purchaseOrderRepository.count() shouldBe 1L
        }

        test("same key with different payload throws IdempotencyKeyConflictException") {
            val userId = newUserWithBalance("conflict", 2000)
            facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k6"))

            shouldThrow<IdempotencyKeyConflictException> {
                facade.purchase(userId, PurchaseCommand("LUCK_CHARM", 1, "k6"))
            }
        }

        test("same key by different users are independent orders") {
            val a = newUserWithBalance("userA", 1000)
            val b = newUserWithBalance("userB", 1000)

            facade.purchase(a, PurchaseCommand("EVO_STONE", 1, "shared"))
            facade.purchase(b, PurchaseCommand("EVO_STONE", 1, "shared"))

            userPointRepository.findByUserId(a)!!.balance shouldBe 800L
            userPointRepository.findByUserId(b)!!.balance shouldBe 800L
            purchaseOrderRepository.count() shouldBe 2L
        }

        test("concurrent same-key purchases debit exactly once") {
            val userId = newUserWithBalance("race", 1000)
            val threads = 8
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val errors = AtomicInteger(0)

            repeat(threads) {
                pool.submit {
                    ready.countDown()
                    go.await()
                    try {
                        facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "race-key"))
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            ready.await()
            go.countDown()
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS)

            errors.get() shouldBe 0 // every caller gets a successful idempotent result
            purchaseOrderRepository.count() shouldBe 1L
            userPointRepository.findByUserId(userId)!!.balance shouldBe 800L // 1000 - 200 once
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId).first().qty shouldBe 1
        }

        test("inactive item throws ItemInactiveException") {
            // EVO_STONE 을 비활성화한 뒤 구매 시도 → ITEM_INACTIVE
            val userId = newUserWithBalance("inactive", 1000)
            val stone = shopItemRepositoryFind("EVO_STONE")
            // 비활성 토글은 네이티브 업데이트로(엔티티 val 불변) — 테스트 헬퍼
            deactivate("EVO_STONE")

            shouldThrow<ItemInactiveException> {
                facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k7"))
            }
            // 원복
            reactivate("EVO_STONE")
            stone // referenced to avoid unused warning
        }
    }

    // --- 테스트 헬퍼: is_active 토글 (엔티티가 불변이라 네이티브 업데이트 사용) ---
    @Autowired lateinit var shopItemRepository: com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
    @org.springframework.beans.factory.annotation.Autowired
    lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate
    private fun shopItemRepositoryFind(code: String) = shopItemRepository.findById(code).get()
    private fun deactivate(code: String) = jdbc.update("UPDATE shop_item SET is_active = FALSE WHERE item_code = ?", code)
    private fun reactivate(code: String) = jdbc.update("UPDATE shop_item SET is_active = TRUE WHERE item_code = ?", code)

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

> Implementation notes for the test (resolve while writing it, before first run):
> - `JdbcTemplate` is auto-configured by Spring Boot when a `DataSource` is present — `@Autowired lateinit var jdbc: JdbcTemplate` works under `@SpringBootTest`. The `deactivate/reactivate` helpers toggle `is_active` without mutating the immutable entity, then restore the seed so other tests/classes are unaffected.
> - `newUserWithBalance` tops up to the desired balance via a `recordTransaction` setup credit. This avoids coupling tests to `app.points.initial-balance` (default 1). Use a distinct `idempotencyKey` (`"setup:<name>"`) so it never collides with purchase keys.
> - If the `is_active` toggle approach feels heavy, an alternative is to assert `ITEM_INACTIVE` purely at the web layer in Task 8 by mocking the facade to throw `ItemInactiveException`; keep at least one of the two.

- [ ] **Step 5: Run to verify failure, then implement**

The service/facade from Steps 2–3 should already exist by now; run:

Run: `./gradlew test --tests "*ShopPurchaseIntegrationTest*"`
Expected first run: PASS once `ShopPurchaseService`/`ShopPurchaseFacade` compile. If the concurrent test is flaky on a slow machine, increase `awaitTermination` and re-run — it must converge to exactly 1 order and a single 200-coin debit.

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/service \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/service/ShopPurchaseIntegrationTest.kt
git commit -m "feat(shop): 구매 트랜잭션 서비스·Facade(경합 복구) 추가"
```

---

## Task 8: Controllers + DTOs + web slice tests

**Files:**
- Create: `.../domain/shop/web/request/PurchaseRequest.kt`
- Create: `.../domain/shop/web/response/ShopCatalogResponse.kt`
- Create: `.../domain/shop/web/response/PurchaseResponse.kt`
- Create: `.../domain/shop/web/controller/ShopController.kt`
- Create: `.../domain/inventory/web/response/InventoryResponse.kt`
- Create: `.../domain/inventory/web/controller/InventoryController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/web/controller/ShopControllerTest.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/inventory/web/controller/InventoryControllerTest.kt`

- [ ] **Step 1: Create `PurchaseRequest` (validation)**

```kotlin
package com.wnl.cashchat.api.domain.shop.web.request

import com.wnl.cashchat.api.domain.shop.service.PurchaseCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class PurchaseRequest(
    @field:NotBlank
    val itemCode: String = "",

    @field:Min(1)
    val qty: Int = 0,

    // UUID 형식만 검증(버전 무관) — spec: "서버는 형식만 검증"
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "idempotencyKey must be a UUID",
    )
    val idempotencyKey: String = "",
) {
    fun toCommand() = PurchaseCommand(itemCode = itemCode, qty = qty, idempotencyKey = idempotencyKey)
}
```

- [ ] **Step 2: Create response DTOs**

`ShopCatalogResponse.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.web.response

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory

data class ShopCatalogResponse(
    val category: String,
    val phase1Active: Boolean,
    val items: List<Item>,
) {
    data class Item(
        val itemCode: String,
        val name: String,
        val priceCoin: Long,
        val effectSummary: String,
        val displayOrder: Int,
    )

    companion object {
        fun of(category: ShopItemCategory, items: List<ShopItem>) = ShopCatalogResponse(
            category = category.name,
            phase1Active = category.phase1Active,
            items = items.map {
                Item(
                    itemCode = it.itemCode,
                    name = it.name,
                    priceCoin = it.priceCoin,
                    effectSummary = it.effectSummary,
                    displayOrder = it.displayOrder,
                )
            },
        )
    }
}
```

`PurchaseResponse.kt`:

```kotlin
package com.wnl.cashchat.api.domain.shop.web.response

import com.wnl.cashchat.api.domain.shop.service.PurchaseResult

data class PurchaseResponse(
    val purchaseOrderId: Long,
    val status: String,
    val coinBalance: Long,
    val inventory: List<Item>,
) {
    data class Item(val itemCode: String, val qty: Int)

    companion object {
        fun from(result: PurchaseResult) = PurchaseResponse(
            purchaseOrderId = result.purchaseOrderId,
            status = result.status.name,
            coinBalance = result.coinBalance,
            inventory = result.inventory.map { Item(it.itemCode, it.qty) },
        )
    }
}
```

`InventoryResponse.kt`:

```kotlin
package com.wnl.cashchat.api.domain.inventory.web.response

import com.wnl.cashchat.api.domain.inventory.service.InventoryLine

data class InventoryResponse(
    val items: List<Item>,
) {
    data class Item(val itemCode: String, val qty: Int)

    companion object {
        fun from(lines: List<InventoryLine>) = InventoryResponse(
            items = lines.map { Item(it.itemCode, it.qty) },
        )
    }
}
```

- [ ] **Step 3: Create `ShopController`**

```kotlin
package com.wnl.cashchat.api.domain.shop.web.controller

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.service.ShopCatalogService
import com.wnl.cashchat.api.domain.shop.service.ShopPurchaseFacade
import com.wnl.cashchat.api.domain.shop.web.request.PurchaseRequest
import com.wnl.cashchat.api.domain.shop.web.response.PurchaseResponse
import com.wnl.cashchat.api.domain.shop.web.response.ShopCatalogResponse
import jakarta.validation.Valid
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shop")
class ShopController(
    private val shopCatalogService: ShopCatalogService,
    private val shopPurchaseFacade: ShopPurchaseFacade,
) {
    @GetMapping("/items")
    fun items(@RequestParam category: ShopItemCategory): ShopCatalogResponse =
        ShopCatalogResponse.of(category, shopCatalogService.listItems(category))

    @PostMapping("/purchase")
    fun purchase(
        authentication: Authentication,
        @Valid @RequestBody request: PurchaseRequest,
    ): PurchaseResponse =
        PurchaseResponse.from(shopPurchaseFacade.purchase(authentication.userId(), request.toCommand()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

- [ ] **Step 4: Create `InventoryController`**

```kotlin
package com.wnl.cashchat.api.domain.inventory.web.controller

import com.wnl.cashchat.api.domain.inventory.service.InventoryService
import com.wnl.cashchat.api.domain.inventory.web.response.InventoryResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/inventory")
class InventoryController(
    private val inventoryService: InventoryService,
) {
    @GetMapping("/me")
    fun getMine(authentication: Authentication): InventoryResponse =
        InventoryResponse.from(inventoryService.getMine(authentication.userId()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

- [ ] **Step 5: Write the failing `ShopControllerTest` (web slice)**

Mirrors `AttendanceControllerTest`: `@WebMvcTest`, `addFilters = false`, `@Import(ShopExceptionHandler)`, mock the services, principal `UsernamePasswordAuthenticationToken(1L, null)`.

```kotlin
package com.wnl.cashchat.api.domain.shop.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.service.PurchaseResult
import com.wnl.cashchat.api.domain.shop.service.ShopCatalogService
import com.wnl.cashchat.api.domain.shop.service.ShopPurchaseFacade
import com.wnl.cashchat.api.domain.shop.web.exception.ShopExceptionHandler
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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ShopController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShopExceptionHandler::class)
class ShopControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var shopCatalogService: ShopCatalogService
    @MockBean lateinit var shopPurchaseFacade: ShopPurchaseFacade
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)
    private val validUuid = "11111111-1111-1111-1111-111111111111"

    init {
        test("GET items ENHANCE returns phase1Active true and items") {
            whenever(shopCatalogService.listItems(ShopItemCategory.ENHANCE)).thenReturn(
                listOf(ShopItem("EVO_STONE", "진화석", ShopItemCategory.ENHANCE, 200, "재료", true, 10))
            )
            mockMvc.perform(get("/api/shop/items").param("category", "ENHANCE").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.category").value("ENHANCE"))
                .andExpect(jsonPath("$.phase1Active").value(true))
                .andExpect(jsonPath("$.items[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.items[0].priceCoin").value(200))
        }

        test("GET items COSMETIC returns phase1Active false and empty items") {
            whenever(shopCatalogService.listItems(ShopItemCategory.COSMETIC)).thenReturn(emptyList())
            mockMvc.perform(get("/api/shop/items").param("category", "COSMETIC").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.phase1Active").value(false))
                .andExpect(jsonPath("$.items.length()").value(0))
        }

        test("GET items with invalid category returns 400 INVALID_CATEGORY") {
            mockMvc.perform(get("/api/shop/items").param("category", "FOO").principal(principal))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"))
        }

        test("POST purchase returns 200 with balance and inventory") {
            whenever(shopPurchaseFacade.purchase(eq(1L), any())).thenReturn(
                PurchaseResult(123L, PurchaseOrderStatus.COMPLETED, 1050L, listOf(InventoryLine("EVO_STONE", 3)))
            )
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"$validUuid"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.purchaseOrderId").value(123))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.coinBalance").value(1050))
                .andExpect(jsonPath("$.inventory[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.inventory[0].qty").value(3))
        }

        test("POST purchase with qty<1 returns 400 VALIDATION") {
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":0,"idempotencyKey":"$validUuid"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION"))
        }

        test("POST purchase with non-UUID idempotencyKey returns 400 VALIDATION") {
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"not-a-uuid"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION"))
        }

        test("POST purchase mapping INSUFFICIENT_COIN returns 400") {
            whenever(shopPurchaseFacade.purchase(eq(1L), any())).thenThrow(InsufficientCoinException())
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"$validUuid"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_COIN"))
        }
    }
}
```

- [ ] **Step 6: Write the failing `InventoryControllerTest` (web slice)**

```kotlin
package com.wnl.cashchat.api.domain.inventory.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.inventory.service.InventoryService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InventoryController::class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var inventoryService: InventoryService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /api/inventory/me returns owned items") {
            whenever(inventoryService.getMine(eq(1L))).thenReturn(
                listOf(InventoryLine("EVO_STONE", 2), InventoryLine("PROTECT_TICKET", 1))
            )
            mockMvc.perform(get("/api/inventory/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.items[0].qty").value(2))
                .andExpect(jsonPath("$.items[1].itemCode").value("PROTECT_TICKET"))
        }
    }
}
```

- [ ] **Step 7: Run web tests**

Run: `./gradlew test --tests "*ShopControllerTest*" --tests "*InventoryControllerTest*"`
Expected: PASS. (These are slice tests, fast, no container.)

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/shop/web \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/inventory/web \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/shop/web \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/inventory/web
git commit -m "feat(shop): 상점·인벤토리 컨트롤러·DTO·웹 테스트 추가"
```

---

## Task 9: Full build + final verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build with all tests**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. All shop/inventory tests + existing suite pass. `ddl-auto: validate` confirms entities match V6 on the MySQL container.

- [ ] **Step 2: Dev smoke (H2 MySQL-mode) — confirms the native UPSERT runs on H2**

Run: `./gradlew bootRun` (dev profile, H2). In a second shell (or via the `! <cmd>` session helper), exercise the flow against H2 to confirm `ON DUPLICATE KEY UPDATE` works there too. Obtain a JWT via the existing guest endpoint and call:

```bash
# 1) guest token
curl -s -X POST http://localhost:8080/api/auth/guest
# 2) catalog (replace <TOKEN>)
curl -s "http://localhost:8080/api/shop/items?category=ENHANCE" -H "Authorization: Bearer <TOKEN>"
# 3) purchase (UUID idempotencyKey)
curl -s -X POST http://localhost:8080/api/shop/purchase -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"11111111-1111-1111-1111-111111111111"}'
# 4) inventory
curl -s http://localhost:8080/api/inventory/me -H "Authorization: Bearer <TOKEN>"
```

Expected: catalog returns 5 ENHANCE items; purchase returns `coinBalance`/`inventory`; repeating step 3 with the same key does not double-debit; inventory shows the granted item. Stop `bootRun` afterward.

> If H2 rejects `ON DUPLICATE KEY UPDATE`, fall back to H2's `MERGE INTO user_inventory ...` via a DB-specific query (split dev/prod), but verify first — H2 in `MODE=MySQL` is expected to accept it.

- [ ] **Step 3: Update the tasks checklist**

Edit `docs/features/shop/tasks.md` — tick BE-1, BE-2, BE-3, BE-4 boxes (Back-End section) to reflect completion. Leave FE-* and INF-* unchecked (out of scope for this branch).

- [ ] **Step 4: Commit verification + checklist update**

```bash
git add docs/features/shop/tasks.md
git commit -m "docs(shop): Phase 1 백엔드(BE-1~4) 완료 체크"
```

- [ ] **Step 5: Finish the branch**

Use the `superpowers:finishing-a-development-branch` skill to choose merge/PR. PR title format: `[CC-292] Shop Phase 1 백엔드 API`. Base branch: `dev`.

---

## Self-Review (completed during planning)

**Spec coverage** — every spec acceptance criterion maps to a task:

| Spec criterion | Covered by |
| --- | --- |
| 카탈로그 조회 (5 ENHANCE, displayOrder asc, fields) | Task 1 seed, Task 6 service, Task 8 `GET items` test |
| 보유 수량 동시 조회 (`/api/inventory/me`) | Task 3 service, Task 8 `InventoryControllerTest` |
| 정상 구매 (200 debit, +1 inv, COMPLETED order, point key `shop:purchase:<userId>:k1`) | Task 7 `normal purchase` |
| 패키지 구매 (다건 grant 한 트랜잭션) | Task 7 `package purchase` |
| 코인 부족 → `INSUFFICIENT_COIN`, no change | Task 7 `insufficient coin`, Task 8 mapping test |
| 멱등성 동일 키 재호출 → 현재 상태, no double-debit | Task 7 `idempotent replay` |
| 멱등성 키 재사용 충돌(동일 사용자) → 409 | Task 7 `same key different payload`, handler 409 |
| 다른 사용자 같은 키 → 독립 주문 | Task 7 `same key by different users` |
| 잘못된 itemCode → `ITEM_NOT_FOUND` | Task 7 `unknown itemCode`, Task 5 handler |
| 비활성 아이템 → `ITEM_INACTIVE` + 미노출 | Task 7 `inactive item`, Task 1 `isActive` filter |
| Phase 1 비대상 카테고리 → `phase1Active:false`, `items:[]` | Task 8 `COSMETIC` test |
| `INVALID_CATEGORY` (enum 범위 밖) | Task 5 handler, Task 8 invalid category test |
| `VALIDATION` (qty<1, idempotencyKey 형식) | Task 8 (qty<1, non-UUID) tests, Task 5 handler |
| 동시 INSERT 경합 → 500 미노출, 멱등 처리 | Task 7 `concurrent same-key`, Task 7 facade |
| 단일 트랜잭션 원자성 (point→inventory→order) | Task 7 service ordering |
| 전역 락 순서 / 데드락 방지(itemCode 정렬) | Task 7 grant ordering, Task 3 UPSERT |

**Placeholder scan:** no TBD/TODO; every code step shows full code; commands have expected output. (The two test-helper alternatives in Task 7 Step 4 are explicit options, not placeholders.)

**Type consistency:** `PurchaseCommand(itemCode, qty, idempotencyKey)`, `PurchaseResult(purchaseOrderId, status: PurchaseOrderStatus, coinBalance, inventory: List<InventoryLine>)`, `InventoryLine(itemCode, qty)`, `recordTransaction(userId, delta, reason, idempotencyKey)`, `findByUserIdAndIdempotencyKey`, `findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc`, `findByItemCodeOrderByGrantItemCodeAsc`, `upsertQty(userId, itemCode, qty)` are used identically across tasks.

**Known follow-ups (out of scope, noted for INF tasks):** INF-1 (prod Flyway runbook), INF-2 (FAILED-rate alarm, INSUFFICIENT_COIN dashboard, qty CHECK) are separate from this backend-API branch.
