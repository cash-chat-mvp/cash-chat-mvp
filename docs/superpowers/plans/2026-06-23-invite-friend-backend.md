# 친구 초대(Invite Friend) 백엔드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 혜택존에 추천 코드 기반 친구 초대 적립 채널(`/api/invite/me`, `/api/invite/redeem`)을 백엔드에 추가한다 — 가입자에게 에너지, 초대자에게 코인을 멱등·원자적으로 지급한다.

**Architecture:** 신규 `domain/invite/` 도메인. 코드 발급은 `insertIfAbsent`(ON DUPLICATE KEY no-op) + `findForUpdate` get-or-create 패턴(offerwall 토큰과 동일). redeem은 단일 `@Transactional`에서 `invite_redemptions.invitee_user_id` UNIQUE를 1차 방어선으로 두고, 가입자 에너지는 `EnergyService.charge`, 초대자 코인은 `UserPointService.recordTransaction(idempotencyKey)`로 지급한다.

**Tech Stack:** Kotlin, Spring Boot 3.5.11, Spring Data JPA, Flyway, Kotest(FunSpec) + Testcontainers(MySQL 8.4), MockMvc(@WebMvcTest).

**Spec:** `docs/features/invite-friend/spec.md`

## Global Constraints

- 언어/런타임: Kotlin 1.9.25, Java 21, Spring Boot 3.5.11. 패키지 루트 `com.wnl.cashchat.api`.
- 시간대: 일자/기간 경계는 의미상 KST(`Asia/Seoul`). 적격 기간은 `createdAt + redeemWindowDays*24h`(Instant 기준 duration)으로 계산하며, `now: Instant`를 서비스 인자로 주입해 테스트 가능하게 한다.
- 인증: `Authorization: Bearer <accessToken>`. 컨트롤러는 `Authentication.principal as? Long`으로 `userId`를 읽는다(없으면 `AuthenticationCredentialsNotFoundException` → 401).
- 에러 응답: `com.wnl.cashchat.api.common.web.response.ErrorResponse(code, message)` 재사용. 도메인 예외는 `@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.invite"])`에서 매핑.
- 적립 인프라 재사용: 코인 = `UserPointService.recordTransaction(userId, delta, reason, idempotencyKey)`. 에너지 = `EnergyService.charge(userId, amount)`(멱등성 키 없음 — 중복 방어는 redemption UNIQUE가 책임).
- 설정값: `app.invite.*`(서버 권위). `@Validated @ConfigurationProperties`는 `@ConfigurationPropertiesScan`으로 자동 등록됨(별도 @EnableConfigurationProperties 불필요).
- 커밋: Conventional Commits(`feat:`/`test:` 등). 작업 단위마다 커밋.
- 빌드/테스트: `cd apps/backend && ./gradlew test`. 단일 테스트는 `./gradlew test --tests "<FQN>"`.

---

## File Structure

신규 디렉터리 `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/`:

- `service/InviteCodeGenerator.kt` — 혼동 문자 제외 난수 코드 생성(순수 로직).
- `service/InviteService.kt` — get-or-create 코드, redeem 오케스트레이션.
- `service/MyInviteView.kt`, `service/RedeemResult.kt` — 서비스 반환 DTO + `InviteRedemptionStatus`와 함께 사용.
- `properties/InviteProperties.kt` — `app.invite.*` 설정.
- `persistence/entity/InviteCode.kt`, `persistence/entity/InviteRedemption.kt`, `persistence/entity/InviteRedemptionStatus.kt`.
- `persistence/repository/InviteCodeRepository.kt`, `persistence/repository/InviteRedemptionRepository.kt`.
- `exception/AlreadyRedeemedException.kt`, `InvalidCodeException.kt`, `SelfReferralException.kt`, `NotEligibleException.kt`.
- `web/controller/InviteController.kt`, `web/request/RedeemRequest.kt`, `web/response/MyInviteResponse.kt`, `web/response/RedeemResponse.kt`, `web/exception/InviteExceptionHandler.kt`.

수정:
- `domain/point/persistence/entity/PointTransactionReason.kt` — `REFERRAL` 추가.
- `src/main/resources/db/migration/V13__invite.sql` — 신규 마이그레이션.

테스트:
- `domain/invite/service/InviteCodeGeneratorTest.kt`(순수 단위)
- `domain/invite/persistence/InvitePersistenceIntegrationTest.kt`(@SpringBootTest)
- `domain/invite/service/InviteServiceMyInviteIntegrationTest.kt`(@SpringBootTest)
- `domain/invite/service/InviteServiceRedeemIntegrationTest.kt`(@SpringBootTest)
- `domain/invite/web/controller/InviteControllerTest.kt`(@WebMvcTest)

---

## Task 1: 추천 코드 생성기 (InviteCodeGenerator)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteCodeGenerator.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteCodeGeneratorTest.kt`

**Interfaces:**
- Produces: `InviteCodeGenerator.generate(length: Int): String` — 길이 `length`의 코드, 문자 집합 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`(O/0/I/1/L 제외). `@Component`.

- [ ] **Step 1: 실패하는 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteCodeGeneratorTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.shouldBe

class InviteCodeGeneratorTest : FunSpec({
    val generator = InviteCodeGenerator()

    test("generate returns a code of the requested length") {
        generator.generate(6).length shouldBe 6
        generator.generate(10).length shouldBe 10
    }

    test("generate uses only the unambiguous alphabet") {
        val allowed = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toList()
        repeat(500) {
            generator.generate(8).forEach { c -> c shouldBeIn allowed }
        }
    }

    test("generate never emits ambiguous characters O/0/I/1/L") {
        val ambiguous = listOf('O', '0', 'I', '1', 'L')
        repeat(500) {
            generator.generate(10).forEach { c -> c shouldNotBeIn ambiguous }
        }
    }
})
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.service.InviteCodeGeneratorTest"`
Expected: FAIL — `InviteCodeGenerator` 미해결(컴파일 에러).

- [ ] **Step 3: 최소 구현**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteCodeGenerator.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.service

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 추천 코드 생성기. 혼동되기 쉬운 문자(O/0/I/1/L)를 제외한 대문자+숫자에서 균일 추출한다.
 * 충돌 회피(재시도)는 호출 측(InviteService.getOrCreateCode)이 UNIQUE 제약으로 처리한다.
 */
@Component
class InviteCodeGenerator {
    fun generate(length: Int): String {
        require(length > 0) { "length must be positive" }
        return buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.service.InviteCodeGeneratorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteCodeGenerator.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteCodeGeneratorTest.kt
git commit -m "feat(invite): add unambiguous invite code generator"
```

---

## Task 2: 영속성 + 마이그레이션 (엔티티·리포지토리·V13)

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt`
- Create: `apps/backend/src/main/resources/db/migration/V13__invite.sql`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/entity/InviteCode.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/entity/InviteRedemptionStatus.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/entity/InviteRedemption.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/repository/InviteCodeRepository.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/repository/InviteRedemptionRepository.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/persistence/InvitePersistenceIntegrationTest.kt`

**Interfaces:**
- Produces:
  - `enum InviteRedemptionStatus { GRANTED, GRANTED_INVITER_CAPPED }`
  - `InviteCode(userId: Long, code: String)` — `@Id user_id` PK.
  - `InviteRedemption(inviteeUserId: Long, inviterUserId: Long, code: String, awardedEnergy: Int, awardedCoin: Long, status: InviteRedemptionStatus)` — `id` auto PK.
  - `InviteCodeRepository`: `findByUserId(Long): InviteCode?`, `findByCode(String): InviteCode?`, `insertIfAbsent(userId, code): Int`, `findForUpdate(userId): InviteCode?`.
  - `InviteRedemptionRepository`: `existsByInviteeUserId(Long): Boolean`, `countByInviterUserId(Long): Long`, `countByInviterUserIdAndStatus(Long, InviteRedemptionStatus): Long`.
  - `PointTransactionReason.REFERRAL`.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/persistence/InvitePersistenceIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemption
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
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

@SpringBootTest
class InvitePersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var inviteCodeRepository: InviteCodeRepository
    @Autowired lateinit var inviteRedemptionRepository: InviteRedemptionRepository

    private fun newUser(name: String): User =
        userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))

    init {
        beforeTest {
            inviteRedemptionRepository.deleteAll()
            inviteCodeRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("insertIfAbsent creates a code row and is idempotent on user_id") {
            val user = newUser("u1")

            inviteCodeRepository.insertIfAbsent(user.id, "ABC23X")
            inviteCodeRepository.insertIfAbsent(user.id, "ZZZ99Y") // 같은 user_id → no-op

            inviteCodeRepository.findByUserId(user.id)!!.code shouldBe "ABC23X"
            inviteCodeRepository.findByCode("ABC23X")!!.userId shouldBe user.id
            inviteCodeRepository.count() shouldBe 1L
        }

        test("invitee_user_id is unique across redemptions") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            inviteRedemptionRepository.save(
                InviteRedemption(
                    inviteeUserId = invitee.id, inviterUserId = inviter.id, code = "ABC23X",
                    awardedEnergy = 10, awardedCoin = 500, status = InviteRedemptionStatus.GRANTED,
                )
            )

            shouldThrow<DataIntegrityViolationException> {
                inviteRedemptionRepository.saveAndFlush(
                    InviteRedemption(
                        inviteeUserId = invitee.id, inviterUserId = inviter.id, code = "ABC23X",
                        awardedEnergy = 10, awardedCoin = 0, status = InviteRedemptionStatus.GRANTED_INVITER_CAPPED,
                    )
                )
            }
        }

        test("count helpers split total referrals from coin-awarded referrals") {
            val inviter = newUser("inviter")
            val a = newUser("a"); val b = newUser("b")
            inviteRedemptionRepository.save(
                InviteRedemption(a.id, inviter.id, "ABC23X", 10, 500, InviteRedemptionStatus.GRANTED)
            )
            inviteRedemptionRepository.save(
                InviteRedemption(b.id, inviter.id, "ABC23X", 10, 0, InviteRedemptionStatus.GRANTED_INVITER_CAPPED)
            )

            inviteRedemptionRepository.countByInviterUserId(inviter.id) shouldBe 2L
            inviteRedemptionRepository.countByInviterUserIdAndStatus(inviter.id, InviteRedemptionStatus.GRANTED) shouldBe 1L
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

> 참고: `InviteRedemption`의 보조 생성자 호출은 positional(`InviteRedemption(a.id, inviter.id, ...)`)로 쓰므로, Step 3 엔티티의 생성자 파라미터 순서를 `inviteeUserId, inviterUserId, code, awardedEnergy, awardedCoin, status`로 정확히 맞춘다.

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.persistence.InvitePersistenceIntegrationTest"`
Expected: FAIL — invite 엔티티/리포지토리 미해결(컴파일 에러).

- [ ] **Step 3: 마이그레이션·엔티티·리포지토리 작성 + REFERRAL 추가**

`apps/backend/src/main/resources/db/migration/V13__invite.sql`:

```sql
-- V13: 친구 초대(추천 코드) — 코드 발급(invite_codes) + redeem 원장(invite_redemptions)

CREATE TABLE invite_codes (
    user_id    BIGINT       NOT NULL,
    code       VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_invite_codes_code UNIQUE (code),
    CONSTRAINT fk_invite_codes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE invite_redemptions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    invitee_user_id BIGINT       NOT NULL,
    inviter_user_id BIGINT       NOT NULL,
    code            VARCHAR(16)  NOT NULL,
    awarded_energy  INT          NOT NULL,
    awarded_coin    BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_invite_redemptions_invitee UNIQUE (invitee_user_id),
    CONSTRAINT fk_invite_redemptions_invitee FOREIGN KEY (invitee_user_id) REFERENCES users (id),
    CONSTRAINT fk_invite_redemptions_inviter FOREIGN KEY (inviter_user_id) REFERENCES users (id),
    INDEX idx_invite_redemptions_inviter (inviter_user_id)
);
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/entity/InviteCode.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 사용자당 고유 추천 코드(공유용). user_id 가 PK 이므로 사용자당 1행. */
@Entity
@Table(name = "invite_codes")
class InviteCode(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "code", nullable = false, length = 16)
    val code: String,
) : BaseEntity()
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/entity/InviteRedemptionStatus.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.persistence.entity

/** redeem 결과 상태. GRANTED=초대자 코인까지 지급, GRANTED_INVITER_CAPPED=초대자 상한 초과로 코인 미지급(가입자 에너지는 지급). */
enum class InviteRedemptionStatus {
    GRANTED,
    GRANTED_INVITER_CAPPED,
}
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/entity/InviteRedemption.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 추천 코드 입력(redeem) 원장. 사용자당 1회만 가능 — invitee_user_id UNIQUE 가
 * "1인 1회 + (멱등성 없는) 에너지 중복 지급"의 1차 방어선이다.
 */
@Entity
@Table(name = "invite_redemptions")
class InviteRedemption(
    @Column(name = "invitee_user_id", nullable = false)
    val inviteeUserId: Long,

    @Column(name = "inviter_user_id", nullable = false)
    val inviterUserId: Long,

    @Column(name = "code", nullable = false, length = 16)
    val code: String,

    @Column(name = "awarded_energy", nullable = false)
    val awardedEnergy: Int,

    @Column(name = "awarded_coin", nullable = false)
    val awardedCoin: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    val status: InviteRedemptionStatus,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) : BaseEntity()
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/repository/InviteCodeRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.persistence.repository

import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteCode
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InviteCodeRepository : JpaRepository<InviteCode, Long> {
    fun findByUserId(userId: Long): InviteCode?
    fun findByCode(code: String): InviteCode?

    /**
     * (user_id) 행을 멱등 생성한다. user_id PK 가 이미 있으면 no-op(동시 최초 호출 한 행만 남음).
     * code UNIQUE 가 다른 사용자와 충돌하면 그 행에 no-op 이 적용되어 우리 행은 INSERT 되지 않으므로,
     * 호출 측은 직후 findForUpdate(userId) 가 null 인지로 코드 충돌을 감지해 재시도한다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO invite_codes (user_id, code, created_at, updated_at) " +
            "VALUES (:userId, :code, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("code") code: String): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InviteCode c where c.userId = :userId")
    fun findForUpdate(@Param("userId") userId: Long): InviteCode?
}
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence/repository/InviteRedemptionRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.persistence.repository

import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemption
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import org.springframework.data.jpa.repository.JpaRepository

interface InviteRedemptionRepository : JpaRepository<InviteRedemption, Long> {
    fun existsByInviteeUserId(inviteeUserId: Long): Boolean
    fun countByInviterUserId(inviterUserId: Long): Long
    fun countByInviterUserIdAndStatus(inviterUserId: Long, status: InviteRedemptionStatus): Long
}
```

`PointTransactionReason.kt` 수정 — `REFERRAL` 추가:

```kotlin
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
    OFFERWALL,
    EVOLUTION_ATTEMPT,
    LEDGER_REWARD,
    SHOP_PURCHASE,
    REFERRAL,
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.persistence.InvitePersistenceIntegrationTest"`
Expected: PASS (3 tests). Flyway가 V13을 적용하고 제약이 동작.

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/resources/db/migration/V13__invite.sql apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/persistence apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/entity/PointTransactionReason.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/persistence/InvitePersistenceIntegrationTest.kt
git commit -m "feat(invite): add invite_codes/invite_redemptions schema and entities"
```

---

## Task 3: 설정 + 내 초대 정보 조회 (InviteProperties, getMyInvite)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/properties/InviteProperties.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/MyInviteView.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteService.kt`(getMyInvite + private getOrCreateCode/eligibility — Task 4에서 redeem 추가)
- Modify: `apps/backend/src/main/resources/application.yml`(또는 `application-*.yml`) — `app.invite.*` 기본값(선택; 미설정 시 코드 기본값 사용)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteServiceMyInviteIntegrationTest.kt`

**Interfaces:**
- Consumes: `InviteCodeGenerator`(Task 1), `InviteCodeRepository`/`InviteRedemptionRepository`(Task 2), `UserRepository`.
- Produces:
  - `InviteProperties(codeLength: Int=6, inviterRewardCoin: Long=500, inviteeRewardEnergy: Int=10, redeemWindowDays: Int=7, inviterCap: Int=20)`.
  - `data class MyInviteView(myCode: String, invitedCount: Long, redeemAvailable: Boolean, rewardCoin: Long, rewardEnergy: Int)`.
  - `InviteService.getMyInvite(userId: Long, now: Instant): MyInviteView`.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteServiceMyInviteIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
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
import java.time.Duration
import java.time.Instant

@SpringBootTest
class InviteServiceMyInviteIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var inviteCodeRepository: InviteCodeRepository
    @Autowired lateinit var inviteRedemptionRepository: InviteRedemptionRepository
    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var properties: InviteProperties

    private fun newUser(name: String): User =
        userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))

    init {
        beforeTest {
            inviteRedemptionRepository.deleteAll()
            inviteCodeRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("getMyInvite creates a code on first call and echoes reward config") {
            val user = newUser("u1")

            val view = inviteService.getMyInvite(user.id, Instant.now())

            view.myCode.length shouldBe properties.codeLength
            view.invitedCount shouldBe 0L
            view.redeemAvailable shouldBe true
            view.rewardCoin shouldBe properties.inviterRewardCoin
            view.rewardEnergy shouldBe properties.inviteeRewardEnergy
            inviteCodeRepository.count() shouldBe 1L
        }

        test("getMyInvite returns the same code on repeated calls") {
            val user = newUser("u2")

            val first = inviteService.getMyInvite(user.id, Instant.now()).myCode
            val second = inviteService.getMyInvite(user.id, Instant.now()).myCode

            second shouldBe first
            inviteCodeRepository.count() shouldBe 1L
        }

        test("redeemAvailable is false once the signup window has passed") {
            val user = newUser("u3")
            val pastWindow = Instant.now().plus(Duration.ofDays(properties.redeemWindowDays.toLong() + 1))

            inviteService.getMyInvite(user.id, pastWindow).redeemAvailable shouldBe false
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

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.service.InviteServiceMyInviteIntegrationTest"`
Expected: FAIL — `InviteService`/`InviteProperties`/`MyInviteView` 미해결.

- [ ] **Step 3: 설정·뷰·서비스 작성**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/properties/InviteProperties.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.properties

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.invite")
data class InviteProperties(
    @field:Positive val codeLength: Int = 6,
    @field:PositiveOrZero val inviterRewardCoin: Long = 500,
    @field:PositiveOrZero val inviteeRewardEnergy: Int = 10,
    @field:Positive val redeemWindowDays: Int = 7,
    @field:Positive val inviterCap: Int = 20,
)
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/MyInviteView.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.service

data class MyInviteView(
    val myCode: String,
    val invitedCount: Long,
    val redeemAvailable: Boolean,
    val rewardCoin: Long,
    val rewardEnergy: Int,
)
```

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteService.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 친구 초대 — 추천 코드 발급 및 redeem(Task 4에서 추가).
 * 코드 발급은 insertIfAbsent + findForUpdate get-or-create(offerwall 토큰과 동일 패턴)이며,
 * code UNIQUE 충돌 시 새 코드로 재시도한다.
 */
@Service
class InviteService(
    private val inviteCodeRepository: InviteCodeRepository,
    private val inviteRedemptionRepository: InviteRedemptionRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val userRepository: UserRepository,
    private val properties: InviteProperties,
) {
    @Transactional
    fun getMyInvite(userId: Long, now: Instant): MyInviteView =
        MyInviteView(
            myCode = getOrCreateCode(userId),
            invitedCount = inviteRedemptionRepository.countByInviterUserId(userId),
            redeemAvailable = isRedeemEligible(userId, now),
            rewardCoin = properties.inviterRewardCoin,
            rewardEnergy = properties.inviteeRewardEnergy,
        )

    private fun getOrCreateCode(userId: Long): String {
        inviteCodeRepository.findByUserId(userId)?.let { return it.code }
        repeat(MAX_CODE_ATTEMPTS) {
            val code = inviteCodeGenerator.generate(properties.codeLength)
            inviteCodeRepository.insertIfAbsent(userId, code)
            // null = code UNIQUE 가 다른 사용자 행과 충돌해 우리 행이 안 들어감 → 새 코드로 재시도.
            inviteCodeRepository.findForUpdate(userId)?.let { return it.code }
        }
        throw IllegalStateException("Failed to allocate invite code for userId=$userId")
    }

    private fun isRedeemEligible(userId: Long, now: Instant): Boolean =
        !inviteRedemptionRepository.existsByInviteeUserId(userId) && isWithinWindow(userId, now)

    private fun isWithinWindow(userId: Long, now: Instant): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        return now.isBefore(user.createdAt.plus(Duration.ofDays(properties.redeemWindowDays.toLong())))
    }

    private companion object {
        private const val MAX_CODE_ATTEMPTS = 10
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.service.InviteServiceMyInviteIntegrationTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/properties apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/MyInviteView.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteService.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteServiceMyInviteIntegrationTest.kt
git commit -m "feat(invite): add invite config and get-or-create code + my-invite query"
```

---

## Task 4: 추천 코드 입력 (InviteService.redeem)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/RedeemResult.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/exception/AlreadyRedeemedException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/exception/InvalidCodeException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/exception/SelfReferralException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/exception/NotEligibleException.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteService.kt`(add `redeem`)
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteServiceRedeemIntegrationTest.kt`

**Interfaces:**
- Consumes: `UserPointService.recordTransaction(userId, delta: Long, reason, idempotencyKey)`, `EnergyService.charge(userId, amount: Int)`, `EnergyService.getEnergy(userId).energy`, `UserPointService.getBalance(userId)`, `*.ensureInitialized(user)`.
- Produces:
  - `data class RedeemResult(awardedEnergy: Int, status: InviteRedemptionStatus)`.
  - `InviteService.redeem(inviteeUserId: Long, rawCode: String, now: Instant): RedeemResult`.
  - 예외: `AlreadyRedeemedException`, `InvalidCodeException`, `SelfReferralException`, `NotEligibleException`(모두 `RuntimeException`).

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteServiceRedeemIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
import com.wnl.cashchat.api.domain.point.service.UserPointService
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
import java.time.Duration
import java.time.Instant

@SpringBootTest
class InviteServiceRedeemIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var inviteCodeRepository: InviteCodeRepository
    @Autowired lateinit var inviteRedemptionRepository: InviteRedemptionRepository
    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var properties: InviteProperties
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var userPointService: UserPointService

    /** 코인·에너지 지갑까지 초기화된 사용자 생성(가입 시 ensureInitialized 와 동치). */
    private fun newUser(name: String): User {
        val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
        energyService.ensureInitialized(user)
        userPointService.ensureInitialized(user)
        return user
    }

    private fun codeOf(userId: Long): String = inviteService.getMyInvite(userId, Instant.now()).myCode

    init {
        beforeTest {
            inviteRedemptionRepository.deleteAll()
            inviteCodeRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("redeem grants invitee energy and inviter coin within cap") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)
            val inviterCoinBefore = userPointService.getBalance(inviter.id)
            val inviteeEnergyBefore = energyService.getEnergy(invitee.id).energy

            val result = inviteService.redeem(invitee.id, code, Instant.now())

            result.awardedEnergy shouldBe properties.inviteeRewardEnergy
            result.status shouldBe InviteRedemptionStatus.GRANTED
            userPointService.getBalance(inviter.id) shouldBe inviterCoinBefore + properties.inviterRewardCoin
            energyService.getEnergy(invitee.id).energy shouldBe
                minOf(inviteeEnergyBefore + properties.inviteeRewardEnergy, maxEnergy())
            inviteRedemptionRepository.existsByInviteeUserId(invitee.id) shouldBe true
        }

        test("redeem rejects the user's own code") {
            val user = newUser("self")
            val code = codeOf(user.id)

            shouldThrow<SelfReferralException> { inviteService.redeem(user.id, code, Instant.now()) }
        }

        test("redeem rejects an unknown code") {
            val invitee = newUser("invitee")

            shouldThrow<InvalidCodeException> { inviteService.redeem(invitee.id, "NOPE99", Instant.now()) }
        }

        test("redeem rejects a second attempt by the same user") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)
            inviteService.redeem(invitee.id, code, Instant.now())

            shouldThrow<AlreadyRedeemedException> { inviteService.redeem(invitee.id, code, Instant.now()) }
        }

        test("redeem rejects an invitee past the signup window") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)
            val pastWindow = Instant.now().plus(Duration.ofDays(properties.redeemWindowDays.toLong() + 1))

            shouldThrow<NotEligibleException> { inviteService.redeem(invitee.id, code, pastWindow) }
        }

        test("over-cap redeem still grants invitee energy but no inviter coin") {
            // inviter-cap 은 DynamicPropertySource 에서 1 로 강제.
            val inviter = newUser("inviter")
            val code = codeOf(inviter.id)
            val firstInvitee = newUser("first")
            inviteService.redeem(firstInvitee.id, code, Instant.now()) // cap(1) 소진

            val secondInvitee = newUser("second")
            val inviterCoinBefore = userPointService.getBalance(inviter.id)
            val secondEnergyBefore = energyService.getEnergy(secondInvitee.id).energy

            val result = inviteService.redeem(secondInvitee.id, code, Instant.now())

            result.status shouldBe InviteRedemptionStatus.GRANTED_INVITER_CAPPED
            result.awardedEnergy shouldBe properties.inviteeRewardEnergy
            userPointService.getBalance(inviter.id) shouldBe inviterCoinBefore // 코인 미증가
            energyService.getEnergy(secondInvitee.id).energy shouldBe
                minOf(secondEnergyBefore + properties.inviteeRewardEnergy, maxEnergy())
        }
    }

    private fun maxEnergy(): Int = Int.MAX_VALUE // 캡 영향 없도록: 실제 maxEnergy 미만 적립이면 합산값 그대로

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
            registry.add("app.invite.inviter-cap") { "1" }
            registry.add("app.invite.invitee-reward-energy") { "10" }
            registry.add("app.invite.inviter-reward-coin") { "500" }
        }
    }
}
```

> 에너지 합산 단언은 `minOf(before + reward, maxEnergy)` 형태로 둔다 — `EnergyService.charge`가 `energyProperties.maxEnergy`로 상한을 두기 때문. 테스트 사용자는 가입 보너스(`signupBonus`)만 가진 상태라 `before + 10`이 maxEnergy를 넘지 않으면 그대로 합산된다. `maxEnergy()` 헬퍼는 단언을 단순화하기 위한 상한 가드일 뿐이며, 실제 maxEnergy를 알 필요가 없도록 `Int.MAX_VALUE`로 둔다(합산값이 항상 이김).

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.service.InviteServiceRedeemIntegrationTest"`
Expected: FAIL — `redeem`/예외 클래스 미해결.

- [ ] **Step 3: 예외·결과 DTO·redeem 구현**

4개 예외 클래스:

`exception/AlreadyRedeemedException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.exception

class AlreadyRedeemedException : RuntimeException("Invite code already redeemed")
```
`exception/InvalidCodeException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.exception

class InvalidCodeException : RuntimeException("Invite code does not exist")
```
`exception/SelfReferralException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.exception

class SelfReferralException : RuntimeException("Cannot redeem your own invite code")
```
`exception/NotEligibleException.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.exception

class NotEligibleException : RuntimeException("Not eligible to redeem an invite code")
```

`service/RedeemResult.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus

data class RedeemResult(
    val awardedEnergy: Int,
    val status: InviteRedemptionStatus,
)
```

`InviteService.kt`에 의존성과 `redeem` 추가 — 생성자에 `EnergyService`, `UserPointService` 주입, redeem 메서드 작성:

```kotlin
// import 추가
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemption
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.dao.DataIntegrityViolationException

// 생성자에 추가:
//   private val userPointService: UserPointService,
//   private val energyService: EnergyService,

@Transactional
fun redeem(inviteeUserId: Long, rawCode: String, now: Instant): RedeemResult {
    val code = rawCode.trim().uppercase()
    val inviteCode = inviteCodeRepository.findByCode(code) ?: throw InvalidCodeException()
    val inviterUserId = inviteCode.userId
    if (inviterUserId == inviteeUserId) throw SelfReferralException()
    if (inviteRedemptionRepository.existsByInviteeUserId(inviteeUserId)) throw AlreadyRedeemedException()
    if (!isWithinWindow(inviteeUserId, now)) throw NotEligibleException()

    val grantsCoin = inviteRedemptionRepository
        .countByInviterUserIdAndStatus(inviterUserId, InviteRedemptionStatus.GRANTED) < properties.inviterCap
    val status = if (grantsCoin) InviteRedemptionStatus.GRANTED else InviteRedemptionStatus.GRANTED_INVITER_CAPPED
    val awardedCoin = if (grantsCoin) properties.inviterRewardCoin else 0L

    // invitee_user_id UNIQUE 가 1인1회 + 에너지 중복지급의 최종 방어선.
    // 동시 도착한 두 번째 redeem 은 여기서 제약 위반 → 트랜잭션 전체 롤백 → 409(ALREADY_REDEEMED).
    try {
        inviteRedemptionRepository.saveAndFlush(
            InviteRedemption(
                inviteeUserId = inviteeUserId,
                inviterUserId = inviterUserId,
                code = code,
                awardedEnergy = properties.inviteeRewardEnergy,
                awardedCoin = awardedCoin,
                status = status,
            )
        )
    } catch (e: DataIntegrityViolationException) {
        throw AlreadyRedeemedException()
    }

    energyService.charge(inviteeUserId, properties.inviteeRewardEnergy)
    if (grantsCoin) {
        userPointService.recordTransaction(
            userId = inviterUserId,
            delta = properties.inviterRewardCoin,
            reason = PointTransactionReason.REFERRAL,
            idempotencyKey = "referral:$inviteeUserId",
        )
    }
    return RedeemResult(awardedEnergy = properties.inviteeRewardEnergy, status = status)
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.service.InviteServiceRedeemIntegrationTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/exception apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/RedeemResult.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteService.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/service/InviteServiceRedeemIntegrationTest.kt
git commit -m "feat(invite): add code redeem with energy/coin grant and inviter cap"
```

---

## Task 5: Web 레이어 (컨트롤러·DTO·예외 핸들러)

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/web/request/RedeemRequest.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/web/response/MyInviteResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/web/response/RedeemResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/web/controller/InviteController.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/web/exception/InviteExceptionHandler.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/web/controller/InviteControllerTest.kt`

**Interfaces:**
- Consumes: `InviteService.getMyInvite(userId, now)`, `InviteService.redeem(inviteeUserId, rawCode, now)`, `MyInviteView`, `RedeemResult`, 4개 예외.
- Produces: `GET /api/invite/me` → `MyInviteResponse`, `POST /api/invite/redeem` → `RedeemResponse`. 에러 코드 `ALREADY_REDEEMED`(409)/`INVALID_CODE`(404)/`SELF_REFERRAL`(409)/`NOT_ELIGIBLE`(403).

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/web/controller/InviteControllerTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.invite.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.service.InviteService
import com.wnl.cashchat.api.domain.invite.service.MyInviteView
import com.wnl.cashchat.api.domain.invite.service.RedeemResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InviteController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class InviteControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var inviteService: InviteService
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    private fun principal(userId: Long): RequestPostProcessor {
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        return SecurityMockMvcRequestPostProcessors.authentication(auth)
    }

    init {
        test("GET /me returns my invite info") {
            whenever(inviteService.getMyInvite(eq(7L), any()))
                .thenReturn(MyInviteView("ABC23X", 3L, true, 500L, 10))

            mockMvc.perform(get("/api/invite/me").with(principal(7L)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.myCode").value("ABC23X"))
                .andExpect(jsonPath("$.invitedCount").value(3))
                .andExpect(jsonPath("$.redeemAvailable").value(true))
                .andExpect(jsonPath("$.rewardCoin").value(500))
                .andExpect(jsonPath("$.rewardEnergy").value(10))
        }

        test("GET /me requires authentication") {
            mockMvc.perform(get("/api/invite/me"))
                .andExpect(status().isUnauthorized)
        }

        test("POST /redeem returns success payload") {
            whenever(inviteService.redeem(eq(7L), eq("XYZ29K"), any()))
                .thenReturn(RedeemResult(10, InviteRedemptionStatus.GRANTED))

            mockMvc.perform(
                post("/api/invite/redeem").with(principal(7L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"code":"XYZ29K"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.awardedEnergy").value(10))
                .andExpect(jsonPath("$.message").doesNotExist())
        }

        test("POST /redeem maps domain errors to status codes") {
            whenever(inviteService.redeem(eq(1L), any(), any())).thenThrow(AlreadyRedeemedException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(1L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"A"}"""))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("ALREADY_REDEEMED"))

            whenever(inviteService.redeem(eq(2L), any(), any())).thenThrow(InvalidCodeException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(2L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"B"}"""))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))

            whenever(inviteService.redeem(eq(3L), any(), any())).thenThrow(SelfReferralException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(3L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"C"}"""))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("SELF_REFERRAL"))

            whenever(inviteService.redeem(eq(4L), any(), any())).thenThrow(NotEligibleException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(4L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"D"}"""))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"))
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.web.controller.InviteControllerTest"`
Expected: FAIL — `InviteController`/DTO/핸들러 미해결.

- [ ] **Step 3: DTO·컨트롤러·핸들러 구현**

`web/request/RedeemRequest.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.web.request

data class RedeemRequest(val code: String)
```

`web/response/MyInviteResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.web.response

import com.wnl.cashchat.api.domain.invite.service.MyInviteView

data class MyInviteResponse(
    val myCode: String,
    val invitedCount: Long,
    val redeemAvailable: Boolean,
    val rewardCoin: Long,
    val rewardEnergy: Int,
) {
    companion object {
        fun from(v: MyInviteView) = MyInviteResponse(
            myCode = v.myCode,
            invitedCount = v.invitedCount,
            redeemAvailable = v.redeemAvailable,
            rewardCoin = v.rewardCoin,
            rewardEnergy = v.rewardEnergy,
        )
    }
}
```

`web/response/RedeemResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.web.response

import com.wnl.cashchat.api.domain.invite.service.RedeemResult

data class RedeemResponse(
    val success: Boolean,
    val awardedEnergy: Int,
    val message: String?,
) {
    companion object {
        // 실패는 예외로 던져 핸들러가 처리하므로, 정상 반환은 항상 success=true.
        fun from(r: RedeemResult) = RedeemResponse(success = true, awardedEnergy = r.awardedEnergy, message = null)
    }
}
```

`web/controller/InviteController.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.web.controller

import com.wnl.cashchat.api.domain.invite.service.InviteService
import com.wnl.cashchat.api.domain.invite.web.request.RedeemRequest
import com.wnl.cashchat.api.domain.invite.web.response.MyInviteResponse
import com.wnl.cashchat.api.domain.invite.web.response.RedeemResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/invite")
class InviteController(
    private val inviteService: InviteService,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): MyInviteResponse =
        MyInviteResponse.from(inviteService.getMyInvite(authentication.userId(), Instant.now()))

    @PostMapping("/redeem")
    fun redeem(authentication: Authentication, @RequestBody request: RedeemRequest): RedeemResponse =
        RedeemResponse.from(inviteService.redeem(authentication.userId(), request.code, Instant.now()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

`web/exception/InviteExceptionHandler.kt`:
```kotlin
package com.wnl.cashchat.api.domain.invite.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.invite"])
class InviteExceptionHandler {

    @ExceptionHandler(AlreadyRedeemedException::class)
    fun handleAlreadyRedeemed(e: AlreadyRedeemedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("ALREADY_REDEEMED", e.message ?: "Already redeemed"))

    @ExceptionHandler(InvalidCodeException::class)
    fun handleInvalidCode(e: InvalidCodeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("INVALID_CODE", e.message ?: "Invalid code"))

    @ExceptionHandler(SelfReferralException::class)
    fun handleSelfReferral(e: SelfReferralException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("SELF_REFERRAL", e.message ?: "Self referral not allowed"))

    @ExceptionHandler(NotEligibleException::class)
    fun handleNotEligible(e: NotEligibleException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("NOT_ELIGIBLE", e.message ?: "Not eligible"))
}
```

> `ErrorResponse`의 생성자 형태(`ErrorResponse(code, message)`)와 JSON 필드명(`code`)은 기존 `AttendanceExceptionHandler`와 동일하다고 가정한다. Step 3 작성 전 `common/web/response/ErrorResponse.kt`를 열어 생성자 시그니처/필드명을 확인하고, 다르면 그에 맞춰 조정한다(테스트의 `$.code` jsonPath도 함께).

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.invite.web.controller.InviteControllerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: 전체 백엔드 테스트 + 커밋**

```bash
cd apps/backend && ./gradlew test
```
Expected: 전체 PASS(기존 + invite 신규).

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/invite/web apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/invite/web
git commit -m "feat(invite): add invite controller, DTOs, and error mapping"
```

---

## Self-Review

**1. Spec 커버리지** (`docs/features/invite-friend/spec.md` 대비)

- D1 코드 식별·get-or-create → Task 1(생성기) + Task 3(getOrCreateCode). ✅
- D2 redeem 적격(미사용 AND 가입 후 N일) → Task 3 `isRedeemEligible`/`isWithinWindow`, Task 4 redeem 가드. ✅
- D3 초대자 상한(초과 시 친구 에너지만) → Task 4 `grantsCoin`/`GRANTED_INVITER_CAPPED`. ✅
- D4 멱등·원자성(단일 트랜잭션 + invitee UNIQUE + referral 멱등키) → Task 2 스키마, Task 4 redeem. ✅
- D5 어뷰징 범위 외 → 디바이스 로직 없음. ✅
- D6 보상값 서버 설정 → Task 3 InviteProperties. ✅
- 인수기준: 코드 발급/멱등(Task 3) · 정상 redeem(Task 4) · ALREADY/SELF/INVALID/NOT_ELIGIBLE(Task 4·5) · 상한 초과(Task 4) · GET /me 의미(Task 3·5). ✅
- API 계약 GET /me·POST /redeem + 에러코드 → Task 5. ✅
- 데이터모델 V13 invite_codes·invite_redemptions → Task 2. ✅
- PointTransactionReason.REFERRAL → Task 2. ✅

**2. 플레이스홀더 스캔:** TBD/TODO 없음. 모든 코드 스텝에 실제 코드 포함. 두 곳의 `>` 주석은 "기존 파일 시그니처 확인" 지시로, 구체적 확인 대상(`ErrorResponse` 생성자/필드, 엔티티 생성자 순서)을 명시함 — 모호한 위임 아님.

**3. 타입 일관성:**
- `InviteRedemption` 생성자 순서(`inviteeUserId, inviterUserId, code, awardedEnergy, awardedCoin, status, id=0`)가 Task 2 정의 · Task 2 테스트 positional 호출 · Task 4 named 호출에서 일치. ✅
- `MyInviteView`/`RedeemResult` 필드가 Task 3·4 정의와 Task 5 `from(...)` 매핑에서 일치. ✅
- `recordTransaction(userId, delta: Long, reason, idempotencyKey)` 시그니처와 `inviterRewardCoin: Long` 일치(코인 Long). ✅
- `EnergyService.charge(userId, amount: Int)`와 `inviteeRewardEnergy: Int` 일치. ✅
- 리포지토리 메서드명(`existsByInviteeUserId`, `countByInviterUserId`, `countByInviterUserIdAndStatus`, `insertIfAbsent`, `findForUpdate`, `findByCode`, `findByUserId`)이 정의·사용처에서 일치. ✅

---

## Execution Handoff

이 계획은 BE 단일 도메인으로 분해 적절(독립 서브시스템 아님). FE/iOS는 별도 작업(`2026-06-21-benefit-zone-friend-invite-design.md`).
