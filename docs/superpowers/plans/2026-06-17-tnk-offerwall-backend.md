# TNK 오퍼월 백엔드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 혜택존 TNK Factory 오퍼월의 백엔드 적립 채널(사용자 토큰 발급 + TNK 서버 포스트백 검증·멱등 적립 + 콜백 원장)을 `domain/offerwall/`에 구현한다.

**Architecture:** 기존 `domain/ad`(Google SSV)·`domain/point`(멱등 트랜잭션) 패턴을 준용한다. 프론트는 `POST /api/offerwall/tnk/user-token`으로 불투명 UUID 토큰을 받아 TNK SDK `setUserName`에 넣고, TNK 서버는 오퍼 완료 시 `POST /api/offerwall/tnk/callback`으로 포스트백을 보낸다. 콜백은 `md_chk` MD5 검증 → 토큰→userId 해석 → 환산비 적용 → `UserPointService.recordTransaction` 멱등 적립을 단일 트랜잭션에서 수행하며, 모든 콜백을 `tnk_offerwall_callbacks` 원장에 기록한다. 동일 `seq_id` 동시/중복 콜백은 "멱등 INSERT(ON DUPLICATE KEY no-op) + `SELECT ... FOR UPDATE` 행 락 + 멱등키" 이중 방어로 직렬화한다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway, Kotest + TestContainers(MySQL), Gradle (Kotlin DSL). Package `com.wnl.cashchat.api.domain.offerwall`.

## Global Constraints

- Java 21, Kotlin 1.9.25, Spring Boot 3.5.11. 패키지 루트 `com.wnl.cashchat.api`.
- 적립/차감은 반드시 `UserPointService.recordTransaction(userId, delta, reason, idempotencyKey)`를 통해서만 한다.
- 모든 일자 판정은 `Asia/Seoul`(KST) 기준. (본 플랜은 일자 의존 로직 없음 — 참고만.)
- DB는 dev=H2(MySQL 호환 모드), prod=MySQL 8. 네이티브 SQL은 MySQL `ON DUPLICATE KEY UPDATE` 사용(H2 MySQL 모드 호환, `insertIfAbsent` 선례 있음).
- `ddl-auto=validate` — 엔티티와 Flyway 테이블 스키마가 정확히 일치해야 함.
- 커밋 메시지: Conventional Commits, **설명은 한글, type/scope는 영문**. 예: `feat(offerwall): TNK 콜백 적립 서비스 추가`.
- 테스트: 단위는 mock, 통합은 `@SpringBootTest` + `MySQLContainer("mysql:8.4.0")`. 컨트롤러는 `@WebMvcTest` + `@Import(SecurityConfig::class, ...)`.
- gradle 빌드 동시 실행 금지 — 한 번에 하나의 `./gradlew` 명령만 실행.
- 환산: `coinAmount = floor(payPnt × ratio)` = `Math.floor(payPnt.toDouble() * ratio).toLong()`.
- `md_chk` 검증식(가정): `MD5(appKey + md_user_nm + seq_id)` lowercase hex, 비교는 대소문자 무시. 정확한 산식/ack/HTTP 메서드는 spec "검증 필요 항목"의 TNK 확인 TODO (구현은 합리적 기본값).

---

### Task 1: 설정 · Flyway 마이그레이션 · OFFERWALL 사유

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/properties/TnkOfferwallProperties.kt`
- Create: `apps/backend/src/main/resources/db/migration/V11__tnk_offerwall.sql`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt`
- Modify: `apps/backend/src/main/resources/application.yaml` (under `app:`)
- Modify: `apps/backend/src/main/resources/application-prod.yaml` (under `app:`)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/OfferwallMigrationIntegrationTest.kt`

**Interfaces:**
- Produces:
  - `TnkOfferwallProperties(appKey: String, pointToCoinRatio: Double, ack: Ack)` with `Ack(successBody: String)`, prefix `app.offerwall.tnk`.
  - Tables `offerwall_user_tokens(user_id PK, token UNIQUE, created_at, updated_at)` and `tnk_offerwall_callbacks(id PK, seq_id UNIQUE, md_user_nm, pay_pnt, coin_amount, user_id NULL, status, raw_query, created_at, updated_at)`.
  - `PointTransactionReason.OFFERWALL`.

- [ ] **Step 1: Write the failing migration test**

Create `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/OfferwallMigrationIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class OfferwallMigrationIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    init {
        test("V11 creates offerwall_user_tokens table") {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM offerwall_user_tokens", Int::class.java) shouldBe 0
        }

        test("V11 creates tnk_offerwall_callbacks table") {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tnk_offerwall_callbacks", Int::class.java) shouldBe 0
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
            registry.add("app.offerwall.tnk.app-key") { "test-app-key" }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.persistence.OfferwallMigrationIntegrationTest"`
Expected: FAIL — tables `offerwall_user_tokens` / `tnk_offerwall_callbacks` do not exist (and/or `TnkOfferwallProperties` bean missing).

- [ ] **Step 3: Create the Flyway migration**

Create `apps/backend/src/main/resources/db/migration/V11__tnk_offerwall.sql`:

```sql
-- V11: TNK 오퍼월 — 사용자 토큰 매핑 + 콜백 원장

CREATE TABLE offerwall_user_tokens (
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(64)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_offerwall_user_tokens_token UNIQUE (token),
    CONSTRAINT fk_offerwall_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE tnk_offerwall_callbacks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    seq_id      VARCHAR(128) NOT NULL,
    md_user_nm  VARCHAR(64)  NOT NULL,
    pay_pnt     BIGINT       NOT NULL,
    coin_amount BIGINT       NOT NULL,
    user_id     BIGINT       NULL,
    status      VARCHAR(32)  NOT NULL,
    raw_query   TEXT         NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    updated_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tnk_offerwall_callbacks_seq_id UNIQUE (seq_id)
);
CREATE INDEX idx_tnk_offerwall_callbacks_user_id ON tnk_offerwall_callbacks (user_id);
```

- [ ] **Step 4: Create the properties class**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/properties/TnkOfferwallProperties.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.properties

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.offerwall.tnk")
data class TnkOfferwallProperties(
    /** md_chk 검증용 공유 시크릿. prod 는 반드시 주입, 미설정 시 빈 값이라 모든 콜백이 서명 실패로 거절된다. */
    val appKey: String = "",

    @field:Positive
    val pointToCoinRatio: Double = 1.0,

    val ack: Ack = Ack(),
) {
    data class Ack(
        val successBody: String = "SUCCESS",
    )
}
```

- [ ] **Step 5: Add the OFFERWALL point reason**

Modify `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt` — add `OFFERWALL`:

```kotlin
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
    OFFERWALL,
    EVOLUTION_ATTEMPT,
    LEDGER_REWARD,
    SHOP_PURCHASE,
}
```

- [ ] **Step 6: Wire config into application yaml**

In `apps/backend/src/main/resources/application.yaml`, under the existing `app:` block (sibling of `ads:`), add:

```yaml
  offerwall:
    tnk:
      app-key: ${APP_OFFERWALL_TNK_APP_KEY:}
      point-to-coin-ratio: ${APP_OFFERWALL_TNK_POINT_TO_COIN_RATIO:1.0}
      ack:
        success-body: ${APP_OFFERWALL_TNK_ACK_SUCCESS_BODY:SUCCESS}
```

In `apps/backend/src/main/resources/application-prod.yaml`, under `app:` (sibling of `ads:`), add the required secret:

```yaml
  offerwall:
    tnk:
      app-key: ${APP_OFFERWALL_TNK_APP_KEY}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.persistence.OfferwallMigrationIntegrationTest"`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V11__tnk_offerwall.sql \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/properties/TnkOfferwallProperties.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt \
  apps/backend/src/main/resources/application.yaml \
  apps/backend/src/main/resources/application-prod.yaml \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/OfferwallMigrationIntegrationTest.kt
git commit -m "feat(offerwall): TNK 오퍼월 설정·마이그레이션·OFFERWALL 사유 추가"
```

---

### Task 2: 사용자 토큰 도메인 (엔티티 · 리포지토리 · 서비스)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallUserToken.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/OfferwallUserTokenRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/OfferwallUserTokenService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/OfferwallUserTokenServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `users` 테이블(`User`, `UserRepository`).
- Produces:
  - `OfferwallUserToken(userId: Long, token: String)` 엔티티.
  - `OfferwallUserTokenRepository : JpaRepository<OfferwallUserToken, Long>` with `findByToken(token: String): OfferwallUserToken?`.
  - `OfferwallUserTokenService.tokenFor(userId: Long): String` (get-or-create, 멱등) / `resolveUserId(token: String): Long?`.

- [ ] **Step 1: Write the failing service test**

Create `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/OfferwallUserTokenServiceIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class OfferwallUserTokenServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tokenRepository: OfferwallUserTokenRepository
    @Autowired lateinit var tokenService: OfferwallUserTokenService

    init {
        beforeTest {
            tokenRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("tokenFor creates a token on first call") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t1"))

            val token = tokenService.tokenFor(user.id)

            token.shouldNotBeNull()
            tokenRepository.findByToken(token)!!.userId shouldBe user.id
        }

        test("tokenFor returns the same token on repeated calls") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t2"))

            val first = tokenService.tokenFor(user.id)
            val second = tokenService.tokenFor(user.id)

            second shouldBe first
            tokenRepository.count() shouldBe 1L
        }

        test("resolveUserId maps a known token back to its user") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t3"))
            val token = tokenService.tokenFor(user.id)

            tokenService.resolveUserId(token) shouldBe user.id
        }

        test("resolveUserId returns null for an unknown token") {
            tokenService.resolveUserId("does-not-exist") shouldBe null
        }

        test("concurrent first calls create exactly one token") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "race"))
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val tokens = ConcurrentLinkedQueue<String>()
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) {
                pool.submit {
                    ready.countDown(); go.await()
                    try { tokens.add(tokenService.tokenFor(user.id)) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            tokenRepository.count() shouldBe 1L
            tokens.toSet().size shouldBe 1
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
            registry.add("app.offerwall.tnk.app-key") { "test-app-key" }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenServiceIntegrationTest"`
Expected: FAIL — `OfferwallUserToken`, `OfferwallUserTokenRepository`, `OfferwallUserTokenService` do not exist (compile error).

- [ ] **Step 3: Create the entity**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallUserToken.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * TNK 오퍼월 사용자 식별용 불투명 토큰. 사용자당 1행(안정적·재사용).
 * 프론트가 TNK SDK setUserName 에 이 token 을 넣고, 콜백의 md_user_nm 으로 되돌아온다.
 */
@Entity
@Table(name = "offerwall_user_tokens")
class OfferwallUserToken(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "token", nullable = false, length = 64)
    val token: String,
) : BaseEntity()
```

- [ ] **Step 4: Create the repository**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/OfferwallUserTokenRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallUserToken
import org.springframework.data.jpa.repository.JpaRepository

interface OfferwallUserTokenRepository : JpaRepository<OfferwallUserToken, Long> {
    fun findByUserId(userId: Long): OfferwallUserToken?
    fun findByToken(token: String): OfferwallUserToken?
}
```

- [ ] **Step 5: Create the service**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/OfferwallUserTokenService.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallUserToken
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OfferwallUserTokenService(
    private val offerwallUserTokenRepository: OfferwallUserTokenRepository,
) {
    /**
     * 사용자당 안정적 토큰을 get-or-create 한다. 동시 최초 호출이 와도 PK(user_id) 충돌을
     * DataIntegrityViolationException 으로 흡수하고 기존 토큰을 다시 읽어 반환한다(단일 생성 보장).
     */
    @Transactional
    fun tokenFor(userId: Long): String {
        offerwallUserTokenRepository.findByUserId(userId)?.let { return it.token }
        return try {
            offerwallUserTokenRepository.saveAndFlush(
                OfferwallUserToken(
                    userId = userId,
                    token = UUID.randomUUID().toString().replace("-", ""),
                )
            ).token
        } catch (e: DataIntegrityViolationException) {
            offerwallUserTokenRepository.findByUserId(userId)?.token ?: throw e
        }
    }

    @Transactional(readOnly = true)
    fun resolveUserId(token: String): Long? =
        offerwallUserTokenRepository.findByToken(token)?.userId
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenServiceIntegrationTest"`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallUserToken.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/OfferwallUserTokenRepository.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/OfferwallUserTokenService.kt \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/OfferwallUserTokenServiceIntegrationTest.kt
git commit -m "feat(offerwall): 사용자 불투명 토큰 발급·해석 서비스 추가"
```

---

### Task 3: 토큰 발급 컨트롤러

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/response/UserTokenResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallTokenControllerTest.kt`

**Interfaces:**
- Consumes: `OfferwallUserTokenService.tokenFor(userId)`.
- Produces: `POST /api/offerwall/tnk/user-token` (인증) → `{ "token": "<uuid>" }`. `UserTokenResponse(token: String)`.
- Note: `OfferwallController` 는 Task 6 에서 콜백 핸들러가 추가된다. 본 태스크는 토큰 엔드포인트만 구현한다.

- [ ] **Step 1: Write the failing controller test**

Create `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallTokenControllerTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(OfferwallController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OfferwallTokenControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var offerwallUserTokenService: OfferwallUserTokenService
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    // 인증 principal 을 Long userId 로 주입 (컨트롤러가 principal as? Long 으로 읽음)
    private fun principal(userId: Long): RequestPostProcessor = RequestPostProcessor { request ->
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userId, null, emptyList())
        request
    }

    init {
        test("issue user-token returns token for authenticated user") {
            whenever(offerwallUserTokenService.tokenFor(42L)).thenReturn("tok-42")

            mockMvc.perform(post("/api/offerwall/tnk/user-token").with(principal(42L)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").value("tok-42"))
        }

        test("user-token requires authentication") {
            mockMvc.perform(post("/api/offerwall/tnk/user-token"))
                .andExpect(status().isUnauthorized)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.web.controller.OfferwallTokenControllerTest"`
Expected: FAIL — `OfferwallController` / `UserTokenResponse` do not exist (compile error).

- [ ] **Step 3: Create the response DTO**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/response/UserTokenResponse.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.response

data class UserTokenResponse(
    val token: String,
)
```

- [ ] **Step 4: Create the controller**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.web.response.UserTokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/offerwall/tnk")
@Tag(name = "Offerwall", description = "TNK offerwall endpoints")
class OfferwallController(
    private val offerwallUserTokenService: OfferwallUserTokenService,
) {
    @PostMapping("/user-token")
    @Operation(summary = "Issue TNK offerwall user token", description = "Returns a stable opaque token for TNK setUserName (get-or-create).")
    fun issueUserToken(authentication: Authentication): UserTokenResponse =
        UserTokenResponse(offerwallUserTokenService.tokenFor(authentication.userId()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.web.controller.OfferwallTokenControllerTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/response/UserTokenResponse.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallTokenControllerTest.kt
git commit -m "feat(offerwall): TNK 사용자 토큰 발급 API 추가"
```

---

### Task 4: TNK md_chk 서명 검증기

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallCallbackParams.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifier.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifierTest.kt`

**Interfaces:**
- Consumes: `TnkOfferwallProperties.appKey`.
- Produces:
  - `TnkOfferwallCallbackParams(seqId: String, payPnt: Long, mdUserNm: String, mdChk: String, rawQuery: String)`.
  - `TnkMdChecksumVerifier.isValid(params: TnkOfferwallCallbackParams): Boolean` — `MD5(appKey + mdUserNm + seqId)` lowercase hex, 대소문자 무시 비교.

- [ ] **Step 1: Write the failing unit test**

Create `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifierTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.security.MessageDigest

class TnkMdChecksumVerifierTest : FunSpec({
    val appKey = "secret-key"
    val verifier = TnkMdChecksumVerifier(TnkOfferwallProperties(appKey = appKey))

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    fun params(seqId: String, mdUserNm: String, mdChk: String) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = 100, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = "raw")

    test("valid md_chk passes") {
        val expected = md5Hex(appKey + "user-token" + "seq-1")
        verifier.isValid(params("seq-1", "user-token", expected)) shouldBe true
    }

    test("valid md_chk passes regardless of case") {
        val expected = md5Hex(appKey + "user-token" + "seq-1").uppercase()
        verifier.isValid(params("seq-1", "user-token", expected)) shouldBe true
    }

    test("wrong md_chk fails") {
        verifier.isValid(params("seq-1", "user-token", "deadbeef")) shouldBe false
    }

    test("md_chk computed with a different appKey fails") {
        val forged = md5Hex("other-key" + "user-token" + "seq-1")
        verifier.isValid(params("seq-1", "user-token", forged)) shouldBe false
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.service.TnkMdChecksumVerifierTest"`
Expected: FAIL — `TnkOfferwallCallbackParams` / `TnkMdChecksumVerifier` do not exist (compile error).

- [ ] **Step 3: Create the params data class**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallCallbackParams.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

/**
 * TNK 서버 포스트백 파라미터. md_chk = MD5(appKey + mdUserNm + seqId) (가정, spec 검증 TODO).
 * rawQuery 는 원장 기록용 콜백 원본 표현.
 */
data class TnkOfferwallCallbackParams(
    val seqId: String,
    val payPnt: Long,
    val mdUserNm: String,
    val mdChk: String,
    val rawQuery: String,
)
```

- [ ] **Step 4: Create the verifier**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifier.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * TNK 콜백의 md_chk 를 검증한다. md_chk == MD5(appKey + md_user_nm + seq_id) (lowercase hex).
 * appKey 는 공유 시크릿이므로, 이를 모르면 md_user_nm(토큰)·seq_id 를 위조해도 유효한 md_chk 를 만들 수 없다.
 * 정확한 연결 순서/인코딩은 TNK 확인 후 확정(spec "검증 필요 항목").
 */
@Component
class TnkMdChecksumVerifier(
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    fun isValid(params: TnkOfferwallCallbackParams): Boolean {
        val expected = md5Hex(tnkOfferwallProperties.appKey + params.mdUserNm + params.seqId)
        return expected.equals(params.mdChk, ignoreCase = true)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.service.TnkMdChecksumVerifierTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallCallbackParams.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifier.kt \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifierTest.kt
git commit -m "feat(offerwall): TNK md_chk 서명 검증기 추가"
```

---

### Task 5: 콜백 원장 엔티티 · 리포지토리 · 적립 서비스

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/TnkOfferwallCallback.kt` (엔티티 + `TnkOfferwallStatus` enum)
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/TnkOfferwallCallbackRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallServiceIntegrationTest.kt`

**Interfaces:**
- Consumes: `TnkMdChecksumVerifier.isValid(params)`, `OfferwallUserTokenService.resolveUserId(token)`, `UserPointService.recordTransaction(...)`, `TnkOfferwallProperties.pointToCoinRatio`, `TnkOfferwallCallbackParams`.
- Produces:
  - `TnkOfferwallStatus { PENDING, GRANTED, REJECTED_BAD_SIGNATURE, REJECTED_UNKNOWN_USER }`.
  - `TnkOfferwallCallback` 엔티티 + `markGranted(userId, coinAmount)` / `markRejected(status)`.
  - `TnkOfferwallCallbackRepository` with `insertIfAbsent(...)`, `findForUpdate(seqId)`, `findBySeqId(seqId)`.
  - `TnkOfferwallService.handleCallback(params, now): TnkOfferwallStatus`.

- [ ] **Step 1: Write the failing service test**

Create `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallServiceIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
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
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class TnkOfferwallServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var callbackRepository: TnkOfferwallCallbackRepository
    @Autowired lateinit var tokenService: OfferwallUserTokenService
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var service: TnkOfferwallService

    private val now = Instant.parse("2026-06-17T00:00:00Z")
    private val appKey = "test-app-key"

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun params(seqId: String, token: String, payPnt: Long, mdChk: String = md5Hex(appKey + token + seqId)) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = token, mdChk = mdChk, rawQuery = "seq_id=$seqId")

    private fun newUserWithToken(name: String): Pair<Long, String> {
        val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
        userPointService.ensureInitialized(user)
        return user.id to tokenService.tokenFor(user.id)
    }

    init {
        beforeTest {
            callbackRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            // 토큰은 user FK 를 가지므로 user 삭제 전에 비운다
            com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository::class
            userRepository.deleteAll()
        }

        test("valid callback credits floor(payPnt * ratio) coins and records GRANTED") {
            val (userId, token) = newUserWithToken("grant")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            // ratio=0.5 (아래 DynamicPropertySource) → 1500 * 0.5 = 750
            val status = service.handleCallback(params("s1", token, 1500), now)

            status shouldBe TnkOfferwallStatus.GRANTED
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 750L
            val row = callbackRepository.findBySeqId("s1")!!
            row.status shouldBe TnkOfferwallStatus.GRANTED
            row.userId shouldBe userId
            row.coinAmount shouldBe 750L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("conversion floors fractional results") {
            val (_, token) = newUserWithToken("floor")
            // 1501 * 0.5 = 750.5 → floor 750
            service.handleCallback(params("s2", token, 1501), now)
            callbackRepository.findBySeqId("s2")!!.coinAmount shouldBe 750L
        }

        test("bad signature records REJECTED_BAD_SIGNATURE and credits nothing") {
            val (userId, token) = newUserWithToken("badsig")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            val status = service.handleCallback(params("s3", token, 1000, mdChk = "wrong"), now)

            status shouldBe TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline
            callbackRepository.findBySeqId("s3")!!.status shouldBe TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
            pointTransactionRepository.count() shouldBe 0L
        }

        test("unknown token records REJECTED_UNKNOWN_USER and credits nothing") {
            val status = service.handleCallback(params("s4", "ghost-token", 1000), now)

            status shouldBe TnkOfferwallStatus.REJECTED_UNKNOWN_USER
            val row = callbackRepository.findBySeqId("s4")!!
            row.status shouldBe TnkOfferwallStatus.REJECTED_UNKNOWN_USER
            row.userId shouldBe null
            pointTransactionRepository.count() shouldBe 0L
        }

        test("duplicate seq_id does not double-credit") {
            val (userId, token) = newUserWithToken("dup")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            service.handleCallback(params("s5", token, 1000), now)
            val second = service.handleCallback(params("s5", token, 1000), now)

            second shouldBe TnkOfferwallStatus.GRANTED // 이미 GRANTED 상태를 멱등 반환
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 500L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("concurrent identical seq_id credits exactly once") {
            val (userId, token) = newUserWithToken("race")
            val baseline = userPointRepository.findByUserId(userId)!!.balance
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) {
                pool.submit {
                    ready.countDown(); go.await()
                    try { service.handleCallback(params("s6", token, 1000), now) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 500L
            pointTransactionRepository.count() shouldBe 1L
            callbackRepository.count() shouldBe 1L
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
            registry.add("app.offerwall.tnk.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.point-to-coin-ratio") { "0.5" }
        }
    }
}
```

> Note: `beforeTest` 안의 `OfferwallUserTokenRepository::class` 줄은 삭제하고, 대신 토큰 테이블을 비우도록 `@Autowired offerwallUserTokenRepository` 를 추가해 `deleteAll()` 을 호출하라 — Step 3 에서 정정한다. (여기서는 토큰 FK 정리 순서를 상기시키는 표시다.)

- [ ] **Step 2: Fix the test's cleanup to actually clear tokens**

Replace the `beforeTest { ... }` block and add the repository autowire so cleanup respects the FK (`offerwall_user_tokens.user_id → users.id`):

Add field:
```kotlin
    @Autowired lateinit var offerwallUserTokenRepository:
        com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
```
Replace block:
```kotlin
        beforeTest {
            callbackRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            offerwallUserTokenRepository.deleteAll()
            userRepository.deleteAll()
        }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallServiceIntegrationTest"`
Expected: FAIL — `TnkOfferwallStatus`, `TnkOfferwallCallback`, `TnkOfferwallCallbackRepository`, `TnkOfferwallService` do not exist (compile error).

- [ ] **Step 4: Create the entity and status enum**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/TnkOfferwallCallback.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.entity

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
 * TNK 서버 포스트백 원장. seq_id 당 1행(UNIQUE). 멱등 INSERT 로 PENDING 상태로 먼저 생성한 뒤
 * 행 락(SELECT ... FOR UPDATE)을 잡고 검증·적립을 진행해 동일 seq_id 동시/중복 콜백을 직렬화한다.
 * status 는 향후 CANCELED 등 환수 상태로 확장 가능(현재 자동 환수는 범위 외).
 */
@Entity
@Table(
    name = "tnk_offerwall_callbacks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tnk_offerwall_callbacks_seq_id", columnNames = ["seq_id"])
    ]
)
class TnkOfferwallCallback(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "seq_id", nullable = false, length = 128)
    val seqId: String,

    @Column(name = "md_user_nm", nullable = false, length = 64)
    val mdUserNm: String,

    @Column(name = "pay_pnt", nullable = false)
    val payPnt: Long,

    @Column(name = "raw_query", nullable = false, columnDefinition = "TEXT")
    val rawQuery: String,
) : BaseEntity() {
    @Column(name = "coin_amount", nullable = false)
    var coinAmount: Long = 0
        private set

    @Column(name = "user_id")
    var userId: Long? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TnkOfferwallStatus = TnkOfferwallStatus.PENDING
        private set

    fun markGranted(userId: Long, coinAmount: Long) {
        this.userId = userId
        this.coinAmount = coinAmount
        this.status = TnkOfferwallStatus.GRANTED
    }

    fun markRejected(status: TnkOfferwallStatus) {
        require(status == TnkOfferwallStatus.REJECTED_BAD_SIGNATURE || status == TnkOfferwallStatus.REJECTED_UNKNOWN_USER) {
            "status must be a REJECTED_* value"
        }
        this.status = status
    }
}

enum class TnkOfferwallStatus {
    PENDING,
    GRANTED,
    REJECTED_BAD_SIGNATURE,
    REJECTED_UNKNOWN_USER,
}
```

- [ ] **Step 5: Create the repository**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/TnkOfferwallCallbackRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallCallback
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TnkOfferwallCallbackRepository : JpaRepository<TnkOfferwallCallback, Long> {
    fun findBySeqId(seqId: String): TnkOfferwallCallback?

    /**
     * seq_id 행을 PENDING 으로 멱등 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로 예외를 던지지 않아
     * 메인 트랜잭션이 오염되지 않고, 엔티티를 로드하지 않으므로 findForUpdate 가 행을 락과 함께 최신 상태로 로드한다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO tnk_offerwall_callbacks " +
            "(seq_id, md_user_nm, pay_pnt, coin_amount, user_id, status, raw_query, created_at, updated_at) " +
            "VALUES (:seqId, :mdUserNm, :payPnt, 0, NULL, 'PENDING', :rawQuery, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE seq_id = seq_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("seqId") seqId: String,
        @Param("mdUserNm") mdUserNm: String,
        @Param("payPnt") payPnt: Long,
        @Param("rawQuery") rawQuery: String,
    ): Int

    /**
     * seq_id 행을 비관적 쓰기 락으로 조회한다. 동일 seq_id 동시 콜백을 직렬화해, 뒤 트랜잭션이 최신 status 를
     * 읽도록 보장 → PENDING 1건만 적립하고 나머지는 GRANTED/REJECTED 를 그대로 멱등 반환한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TnkOfferwallCallback c where c.seqId = :seqId")
    fun findForUpdate(@Param("seqId") seqId: String): TnkOfferwallCallback?
}
```

- [ ] **Step 6: Create the service**

Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallService.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.floor

/**
 * TNK 서버 포스트백을 검증·적립한다. 단일 @Transactional 안에서
 * 멱등 INSERT(PENDING) → 행 락 → 서명 검증 → 토큰 해석 → 환산 적립(멱등키) → status 갱신을 원자적으로 수행한다.
 * 모든 콜백(거절 포함)은 원장에 기록된다(자동 환수는 범위 외, status 확장으로 후속 대응).
 */
@Service
class TnkOfferwallService(
    private val tnkOfferwallCallbackRepository: TnkOfferwallCallbackRepository,
    private val tnkMdChecksumVerifier: TnkMdChecksumVerifier,
    private val offerwallUserTokenService: OfferwallUserTokenService,
    private val userPointService: UserPointService,
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    @Transactional
    fun handleCallback(params: TnkOfferwallCallbackParams, now: Instant): TnkOfferwallStatus {
        tnkOfferwallCallbackRepository.insertIfAbsent(
            seqId = params.seqId,
            mdUserNm = params.mdUserNm,
            payPnt = params.payPnt,
            rawQuery = params.rawQuery,
        )
        val callback = tnkOfferwallCallbackRepository.findForUpdate(params.seqId)
            ?: throw IllegalStateException("tnk_offerwall_callbacks row must exist for seqId=${params.seqId}")

        // PENDING 만 처리한다. 이미 GRANTED/REJECTED 인 행은 중복/동시 콜백이므로 상태를 그대로 멱등 반환.
        if (callback.status != TnkOfferwallStatus.PENDING) {
            return callback.status
        }

        if (!tnkMdChecksumVerifier.isValid(params)) {
            callback.markRejected(TnkOfferwallStatus.REJECTED_BAD_SIGNATURE)
            return TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
        }

        val userId = offerwallUserTokenService.resolveUserId(params.mdUserNm)
        if (userId == null) {
            callback.markRejected(TnkOfferwallStatus.REJECTED_UNKNOWN_USER)
            return TnkOfferwallStatus.REJECTED_UNKNOWN_USER
        }

        val coinAmount = floor(params.payPnt.toDouble() * tnkOfferwallProperties.pointToCoinRatio).toLong()
        userPointService.recordTransaction(
            userId = userId,
            delta = coinAmount,
            reason = PointTransactionReason.OFFERWALL,
            idempotencyKey = "tnk:offerwall:${params.seqId}",
        )
        callback.markGranted(userId = userId, coinAmount = coinAmount)
        return TnkOfferwallStatus.GRANTED
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallServiceIntegrationTest"`
Expected: PASS (6 tests).

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/TnkOfferwallCallback.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/TnkOfferwallCallbackRepository.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallService.kt \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallServiceIntegrationTest.kt
git commit -m "feat(offerwall): TNK 콜백 검증·멱등 적립 서비스와 원장 추가"
```

---

### Task 6: 콜백 컨트롤러 + 시큐리티 공개 경로

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt` (콜백 핸들러 추가)
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt` (콜백 경로 permitAll)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallCallbackControllerTest.kt`

**Interfaces:**
- Consumes: `TnkOfferwallService.handleCallback(params, now)`, `TnkOfferwallProperties.ack.successBody`.
- Produces: `POST /api/offerwall/tnk/callback` (비인증) — `@RequestParam` `seq_id`, `pay_pnt`, `md_user_nm`, `md_chk` → 처리 후 `200 OK` + `successBody` 본문.

- [ ] **Step 1: Write the failing controller test**

Create `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallCallbackControllerTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallCallbackParams
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(OfferwallController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OfferwallCallbackControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var offerwallUserTokenService: OfferwallUserTokenService
    @MockitoBean private lateinit var tnkOfferwallService: TnkOfferwallService
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    init {
        test("callback is public, passes params to service, returns SUCCESS ack") {
            whenever(tnkOfferwallService.handleCallback(any(), any())).thenReturn(TnkOfferwallStatus.GRANTED)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback")
                    .param("seq_id", "seq-1")
                    .param("pay_pnt", "1500")
                    .param("md_user_nm", "tok-1")
                    .param("md_chk", "hash-1")
            )
                .andExpect(status().isOk)
                .andExpect(content().string("SUCCESS"))

            verify(tnkOfferwallService).handleCallback(
                argThat<TnkOfferwallCallbackParams> {
                    seqId == "seq-1" && payPnt == 1500L && mdUserNm == "tok-1" && mdChk == "hash-1"
                },
                any(),
            )
        }

        test("callback returns SUCCESS ack even when rejected (no retry storm)") {
            whenever(tnkOfferwallService.handleCallback(any(), any()))
                .thenReturn(TnkOfferwallStatus.REJECTED_BAD_SIGNATURE)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback")
                    .param("seq_id", "seq-2")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-2")
                    .param("md_chk", "bad")
            )
                .andExpect(status().isOk)
                .andExpect(content().string("SUCCESS"))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.web.controller.OfferwallCallbackControllerTest"`
Expected: FAIL — callback mapping does not exist (404 / unauthorized) and `OfferwallController` lacks the handler / extra constructor dependency.

- [ ] **Step 3: Add the callback handler to the controller**

Modify `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt` — add the new constructor dependencies and the callback endpoint. Full updated file:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallCallbackParams
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallService
import com.wnl.cashchat.api.domain.offerwall.web.response.UserTokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/offerwall/tnk")
@Tag(name = "Offerwall", description = "TNK offerwall endpoints")
class OfferwallController(
    private val offerwallUserTokenService: OfferwallUserTokenService,
    private val tnkOfferwallService: TnkOfferwallService,
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    @PostMapping("/user-token")
    @Operation(summary = "Issue TNK offerwall user token", description = "Returns a stable opaque token for TNK setUserName (get-or-create).")
    fun issueUserToken(authentication: Authentication): UserTokenResponse =
        UserTokenResponse(offerwallUserTokenService.tokenFor(authentication.userId()))

    @PostMapping("/callback")
    @Operation(summary = "Handle TNK offerwall server postback", description = "Verifies md_chk, resolves user, credits coins idempotently, records ledger.")
    fun handleCallback(
        @RequestParam("seq_id") seqId: String,
        @RequestParam("pay_pnt") payPnt: Long,
        @RequestParam("md_user_nm") mdUserNm: String,
        @RequestParam("md_chk") mdChk: String,
    ): ResponseEntity<String> {
        // 원장 기록용 원본 표현(파라미터 재구성). 정확한 전송 방식/ack 규격은 TNK 확인 후 확정(spec 검증 TODO).
        val rawQuery = "seq_id=$seqId&pay_pnt=$payPnt&md_user_nm=$mdUserNm&md_chk=$mdChk"
        tnkOfferwallService.handleCallback(
            TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = rawQuery),
            Instant.now(),
        )
        // 처리된 콜백(적립·거절·중복)에는 성공 ack 를 반환해 재전송 폭주를 막는다. 미처리 예외는 500 으로 재시도 유도.
        return ResponseEntity.ok(tnkOfferwallProperties.ack.successBody)
    }

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

- [ ] **Step 4: Open the callback path in SecurityConfig**

Modify `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt` — add a `permitAll` matcher for the TNK callback, next to the existing SSV matcher (inside `authorizeHttpRequests`, before `.anyRequest().authenticated()`):

```kotlin
                it.requestMatchers("/api/auth/logout").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/ads/google/ssv").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/offerwall/tnk/callback").permitAll()
                    .requestMatchers(*publicPaths.toTypedArray()).permitAll()
                    .anyRequest().authenticated()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.web.controller.OfferwallCallbackControllerTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Re-run the token controller test (regression — constructor changed)**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.web.controller.OfferwallTokenControllerTest"`
Expected: PASS (2 tests). The `@WebMvcTest` provides `@MockitoBean` for the new `TnkOfferwallService`; add it if the test fails to load context:

```kotlin
    @MockitoBean private lateinit var tnkOfferwallService:
        com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallService
    @MockitoBean private lateinit var tnkOfferwallProperties:
        com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
```

> If you add `tnkOfferwallProperties` as a `@MockitoBean`, its `ack.successBody` returns null in the token test — that's fine because the token test never calls the callback endpoint. Only add these mocks if the context fails to start.

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt \
  apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallCallbackControllerTest.kt \
  apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallTokenControllerTest.kt
git commit -m "feat(offerwall): TNK 포스트백 콜백 API와 공개 경로 추가"
```

---

### Task 7: 전체 빌드 검증

**Files:** (없음 — 검증 전용)

- [ ] **Step 1: Run the full build + test suite**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL. 모든 신규 테스트(마이그레이션, 토큰 서비스/컨트롤러, 검증기, 콜백 서비스/컨트롤러)와 기존 테스트가 통과한다.

- [ ] **Step 2: If anything fails, fix and re-run**

회귀가 있으면 해당 테스트를 개별 실행해 원인을 좁힌다 (`./gradlew test --tests "<FQCN>"`). gradle 명령은 한 번에 하나만 실행한다(파일 락 충돌 방지).

- [ ] **Step 3: Final commit (if fixes were needed)**

```bash
git add -A
git commit -m "test(offerwall): 전체 빌드 검증 및 회귀 수정"
```

---

## Self-Review

**1. Spec coverage:**

| spec 요구 | 구현 태스크 |
| --------- | ----------- |
| D1 불투명 토큰 매핑 | Task 2 (엔티티/서비스), Task 3 (발급 API) |
| D2 설정 환산비 | Task 1 (`point-to-coin-ratio`), Task 5 (`floor(payPnt×ratio)`) |
| D3 취소/환수 범위 외·ledger 환수-대응 | Task 5 (`status` 확장형 enum, 모든 콜백 기록) |
| D4 토큰 API POST | Task 3 |
| D5 ACK 기본값·상수 분리·검증 TODO | Task 1 (`ack.success-body`), Task 6 (성공 ack 반환) |
| AC 토큰 발급(최초/재호출 멱등) | Task 2 테스트 |
| AC 정상 적립 | Task 5 테스트 |
| AC 중복 seq_id 멱등 | Task 5 테스트 |
| AC 서명 실패 | Task 4 + Task 5 테스트 |
| AC 미지 토큰 | Task 5 테스트 |
| 동시성(동일 seq_id 1회 적립) | Task 5 테스트 |
| 데이터 모델 V11 | Task 1 |
| `/callback` 비인증 경로 | Task 6 |

모든 spec 섹션이 태스크에 매핑됨 — 갭 없음. (자동 환수·프론트·추가 오퍼월은 spec "범위 외".)

**2. Placeholder scan:** 모든 step 에 실제 코드/명령/기대값 포함. "TBD"·"적절히 처리" 류 없음. spec 의 "검증 필요 항목"은 의도된 TNK 확인 TODO(코드는 합리적 기본값으로 동작).

**3. Type consistency:**
- `TnkOfferwallCallbackParams(seqId, payPnt: Long, mdUserNm, mdChk, rawQuery)` — Task 4 정의, Task 5·6 에서 동일 사용. ✓
- `TnkOfferwallService.handleCallback(params, now): TnkOfferwallStatus` — Task 5 정의, Task 6 컨트롤러 호출. ✓
- `TnkOfferwallStatus { PENDING, GRANTED, REJECTED_BAD_SIGNATURE, REJECTED_UNKNOWN_USER }` — Task 5 정의, Task 6 테스트 사용. ✓
- `OfferwallUserTokenService.tokenFor / resolveUserId` — Task 2 정의, Task 3·5 사용. ✓
- `TnkOfferwallProperties(appKey, pointToCoinRatio, ack.successBody)` — Task 1 정의, Task 4·5·6 사용. ✓
- `PointTransactionReason.OFFERWALL` — Task 1 정의, Task 5 사용. ✓
- `recordTransaction(userId, delta, reason, idempotencyKey)` — 기존 시그니처, Task 5 호출 일치. ✓
- 멱등키 `tnk:offerwall:{seqId}` — Task 5 일관. ✓

이슈 없음.
