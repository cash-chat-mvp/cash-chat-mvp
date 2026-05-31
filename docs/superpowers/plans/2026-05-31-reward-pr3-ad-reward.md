# 혜택존 PR3 — 리워드 광고 적립 레이어 (BE-3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** cc-242가 이미 구현한 AdMob SSV 서명 검증/이벤트 로깅 위에, 서버 발급 nonce→userId 해석·일일 한도(행 락)·코인 적립(BE-1)·결과 기록을 더해 리워드 광고 시청을 코인으로 적립한다.

**Architecture:** `domain/ad/`에 리워드 레이어를 추가한다. cc-242의 `GoogleAdSsvService.verifyAndStore`는 파싱·서명검증·이벤트저장을 그대로 하되 결과(callback + 신규저장 여부)를 반환하도록 소폭 리팩터한다(기존 동작 불변). `GoogleAdSsvController`가 신규 이벤트일 때만 `AdRewardService.grantFromCallback`을 호출해 **단일 `@Transactional`**로 nonce 해석→한도 행 락→코인 적립→이벤트 상태 갱신을 수행한다. 적립 코인은 서버 설정값, 멱등성 키는 `admob:reward:{transactionId}`.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway(V5), H2(dev, MySQL 모드)/MySQL 8(prod·test), Kotest + mockito-kotlin, Testcontainers MySQL, MockMvc.

설계 출처: `docs/superpowers/specs/2026-05-31-reward-be3-ad-reward-design.md`.

---

## 결정 사항 (설계서 D1~D5 요약)

- **D1**: nonce는 SSV `user_id` 필드로 전달. 백엔드는 `callback.userId`를 opaque nonce로 보고 `nonce→내부 userId(Long)` 해석.
- **D2**: 멱등성 키 `admob:reward:{transactionId}`.
- **D3**: 적립 결과는 `google_ad_ssv_events.rewardStatus` enum 확장으로 기록(별도 ledger 없음).
- **D4**: `verifyAndStore`가 결과를 반환하도록 리팩터, 컨트롤러가 신규일 때만 grant.
- **D5**: V5 마이그레이션 `ad_reward_nonce`, `ad_reward_daily_quota`.
- 코인=서버 설정값(40), 한도=10, nonce TTL=10분. 시간은 컨트롤러가 `Instant.now()`를 주입(테스트 결정성).

## File Structure

**수정 (cc-242 통합 seam)**
- `domain/ad/persistence/entity/GoogleAdSsvEvent.kt` — `RewardStatus` enum 값 확장, `rewardStatus` val→var + 상태 변경 메서드
- `domain/ad/service/GoogleAdSsvService.kt` — `verifyAndStore`가 `GoogleAdSsvVerificationResult` 반환
- `domain/ad/web/controller/GoogleAdSsvController.kt` — 신규 이벤트일 때 `AdRewardService.grantFromCallback` 호출
- `domain/ad/web/controller/GoogleAdSsvControllerTest.kt` (test) — `AdRewardService` mock 주입
- `apps/backend/src/main/resources/application.yaml` — `app.ads.reward.*` 설정

**신규 — 설정/마이그레이션**
- `domain/ad/properties/AdRewardProperties.kt`
- `apps/backend/src/main/resources/db/migration/V5__ad_reward.sql`

**신규 — 엔티티/리포지토리**
- `domain/ad/persistence/entity/AdRewardNonce.kt`, `AdRewardDailyQuota.kt`, `AdRewardDailyQuotaId.kt`
- `domain/ad/persistence/repository/AdRewardNonceRepository.kt`, `AdRewardDailyQuotaRepository.kt`

**신규 — 서비스**
- `domain/ad/service/GoogleAdSsvVerificationResult.kt`
- `domain/ad/service/AdRewardNonceService.kt`
- `domain/ad/service/AdRewardService.kt`
- `domain/ad/service/AdRewardQuota.kt` (조회 결과 도메인 타입)

**신규 — 웹**
- `domain/ad/web/controller/AdRewardController.kt`
- `domain/ad/web/response/IssueNonceResponse.kt`, `AdRewardQuotaResponse.kt`

**신규 — 테스트**
- `src/test/.../domain/ad/service/AdRewardServiceTest.kt`
- `src/test/.../domain/ad/web/controller/AdRewardControllerTest.kt`
- `src/test/.../domain/ad/persistence/AdRewardIntegrationTest.kt`

---

## Task 1: cc-242 통합 seam — 이벤트 상태 확장 + verifyAndStore 반환

**Files:**
- Modify: `domain/ad/persistence/entity/GoogleAdSsvEvent.kt`
- Create: `domain/ad/service/GoogleAdSsvVerificationResult.kt`
- Modify: `domain/ad/service/GoogleAdSsvService.kt`

- [ ] **Step 1: `GoogleAdSsvEvent` 의 RewardStatus 확장 + rewardStatus 가변화**

`GoogleAdSsvEvent.kt` 하단의 enum과 `rewardStatus` 필드를 수정한다.

enum 교체:
```kotlin
enum class RewardStatus {
    VERIFIED,
    GRANTED,
    REJECTED_INVALID_NONCE,
    REJECTED_OVER_QUOTA,
}
```

`rewardStatus` 필드를 `val` → `var private set` 로 바꾸고 상태 변경 메서드를 클래스 본문에 추가한다. 기존:
```kotlin
    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false)
    val rewardStatus: RewardStatus = RewardStatus.VERIFIED,

    @Column(name = "raw_query_string", nullable = false, columnDefinition = "TEXT")
    val rawQueryString: String,
) : BaseEntity() {
    init {
```
수정 후 — 생성자에서 `rewardStatus`를 제거하고(항상 VERIFIED로 시작) 본문 프로퍼티 + 메서드로:
```kotlin
    @Column(name = "raw_query_string", nullable = false, columnDefinition = "TEXT")
    val rawQueryString: String,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false)
    var rewardStatus: RewardStatus = RewardStatus.VERIFIED
        private set

    fun markGranted() { rewardStatus = RewardStatus.GRANTED }

    fun markRejected(reason: RewardStatus) {
        require(reason == RewardStatus.REJECTED_INVALID_NONCE || reason == RewardStatus.REJECTED_OVER_QUOTA) {
            "reason must be a REJECTED_* status"
        }
        rewardStatus = reason
    }

    init {
```
> 참고: `GoogleAdSsvService.toEntity()`는 `rewardStatus`를 생성자 인자로 넘기지 않으므로(기본값 사용) 영향 없음. 다른 호출부에서 `rewardStatus =` 를 생성자에 넘기는 곳이 있으면 제거한다(없을 것).

- [ ] **Step 2: 컴파일로 생성자 인자 제거 영향 확인**

Run: `cd apps/backend && ./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL. 실패 시 `GoogleAdSsvEvent(... rewardStatus = ...)`로 호출하던 곳을 찾아 인자를 제거.

- [ ] **Step 3: `GoogleAdSsvVerificationResult` 생성**

`domain/ad/service/GoogleAdSsvVerificationResult.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.service

/**
 * verifyAndStore 결과. newlyStored=true 면 이번 콜백으로 이벤트가 새로 저장된 것이며,
 * 리워드 적립 대상이다. false 면 동일 transaction_id 중복(이미 처리)으로 적립을 건너뛴다.
 */
data class GoogleAdSsvVerificationResult(
    val callback: GoogleAdSsvCallback,
    val newlyStored: Boolean,
)
```

- [ ] **Step 4: `verifyAndStore` 가 결과를 반환하도록 수정**

`GoogleAdSsvService.kt` 의 `verifyAndStore` 를 다음으로 교체한다(검증·저장 로직 동일, 반환만 추가):
```kotlin
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun verifyAndStore(rawQueryString: String?): GoogleAdSsvVerificationResult {
        val callback = parser.parse(rawQueryString)
        validateAdUnit(callback)
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        val existingEvent = repository.findByTransactionId(callback.transactionId)
        if (existingEvent != null) {
            logIfCoreFieldsDiffer(callback, existingEvent)
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

        return try {
            repository.saveAndFlush(callback.toEntity())
            GoogleAdSsvVerificationResult(callback, newlyStored = true)
        } catch (exception: DataIntegrityViolationException) {
            val duplicateEvent = repository.findByTransactionId(callback.transactionId)
            if (duplicateEvent != null) {
                logIfCoreFieldsDiffer(callback, duplicateEvent)
                GoogleAdSsvVerificationResult(callback, newlyStored = false)
            } else {
                throw exception
            }
        }
    }
```

- [ ] **Step 5: 기존 cc-242 서비스 테스트 통과 확인 (회귀)**

Run: `cd apps/backend && ./gradlew test --tests "*GoogleAdSsvServiceTest"`
Expected: PASS. 기존 테스트들은 `service.verifyAndStore(rawQuery)` 반환값을 사용하지 않으므로 그대로 통과한다(Kotlin은 반환값 무시 허용).

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvVerificationResult.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt
git commit -m "refactor(ad): SSV 이벤트 상태 확장 및 verifyAndStore 결과 반환 (리워드 연동 준비)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 설정 + V5 마이그레이션 + nonce/quota 엔티티·리포지토리

**Files:**
- Create: `domain/ad/properties/AdRewardProperties.kt`
- Modify: `apps/backend/src/main/resources/application.yaml`
- Create: `apps/backend/src/main/resources/db/migration/V5__ad_reward.sql`
- Create: `domain/ad/persistence/entity/AdRewardNonce.kt`, `AdRewardDailyQuota.kt`, `AdRewardDailyQuotaId.kt`
- Create: `domain/ad/persistence/repository/AdRewardNonceRepository.kt`, `AdRewardDailyQuotaRepository.kt`

- [ ] **Step 1: `AdRewardProperties`**

`domain/ad/properties/AdRewardProperties.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.ads.reward")
data class AdRewardProperties(
    @field:Positive
    val coinAmount: Long = 40,

    @field:Positive
    val dailyLimit: Int = 10,

    @field:PositiveDuration
    val nonceTtl: Duration = Duration.ofMinutes(10),
)
```
> 등록은 기존 `GoogleAdSsvProperties`와 동일 방식(`@ConfigurationPropertiesScan`)으로 자동 인식된다.
> `@PositiveDuration`은 표준 Jakarta Validation이 아니라 `MaxDuration.kt`에 정의된 프로젝트 커스텀 제약(`PositiveDurationValidator`)이다. `AdRewardProperties`와 동일 패키지(`...ad.properties`)라 별도 import는 필요 없다.

- [ ] **Step 2: application.yaml 에 설정 추가**

`apps/backend/src/main/resources/application.yaml` 의 `app.ads.google` 블록 아래(같은 `app.ads` 하위)에 `reward`를 추가한다. 기존:
```yaml
app:
  points:
    initial-balance: ${APP_POINTS_INITIAL_BALANCE:1}
  ads:
    google:
      ssv-public-keys-uri: ${APP_ADS_GOOGLE_SSV_PUBLIC_KEYS_URI:https://www.gstatic.com/admob/reward/verifier-keys.json}
      public-key-cache-ttl: ${APP_ADS_GOOGLE_PUBLIC_KEY_CACHE_TTL:24h}
      rewarded-ad-unit-id: ${APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID:}
```
수정 후 — `ads:` 하위에 `reward:` 형제 추가:
```yaml
app:
  points:
    initial-balance: ${APP_POINTS_INITIAL_BALANCE:1}
  ads:
    google:
      ssv-public-keys-uri: ${APP_ADS_GOOGLE_SSV_PUBLIC_KEYS_URI:https://www.gstatic.com/admob/reward/verifier-keys.json}
      public-key-cache-ttl: ${APP_ADS_GOOGLE_PUBLIC_KEY_CACHE_TTL:24h}
      rewarded-ad-unit-id: ${APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID:}
    reward:
      coin-amount: ${APP_ADS_REWARD_COIN_AMOUNT:40}
      daily-limit: ${APP_ADS_REWARD_DAILY_LIMIT:10}
      nonce-ttl: ${APP_ADS_REWARD_NONCE_TTL:10m}
```

- [ ] **Step 3: V5 마이그레이션**

`apps/backend/src/main/resources/db/migration/V5__ad_reward.sql`:
```sql
-- V5: 리워드 광고 적립 — 서버 발급 nonce + 일일 시청 한도

CREATE TABLE ad_reward_nonce (
    nonce      VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used       BOOLEAN      NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (nonce),
    CONSTRAINT fk_ad_reward_nonce_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_ad_reward_nonce_user_id ON ad_reward_nonce (user_id);

CREATE TABLE ad_reward_daily_quota (
    user_id    BIGINT       NOT NULL,
    kst_date   DATE         NOT NULL,
    used_count INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, kst_date),
    CONSTRAINT fk_ad_reward_daily_quota_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

- [ ] **Step 4: `AdRewardNonce` 엔티티**

`domain/ad/persistence/entity/AdRewardNonce.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 광고 시청 직전 서버가 발급하는 단일 사용·단기 nonce. nonce → 내부 userId 매핑.
 * 클라이언트가 SSV user_id 필드에 이 nonce 를 실어 보낸다.
 */
@Entity
@Table(name = "ad_reward_nonce")
class AdRewardNonce(
    @Id
    @Column(name = "nonce", nullable = false, length = 64)
    val nonce: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    used: Boolean = false,
) : BaseEntity() {
    @Column(name = "used", nullable = false)
    var used: Boolean = used
        private set

    fun markUsed() {
        used = true
    }

    fun isUsable(now: Instant): Boolean = !used && expiresAt.isAfter(now)
}
```

- [ ] **Step 5: `AdRewardDailyQuotaId` + `AdRewardDailyQuota`**

`domain/ad/persistence/entity/AdRewardDailyQuotaId.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.entity

import java.io.Serializable
import java.time.LocalDate

data class AdRewardDailyQuotaId(
    val userId: Long = 0,
    val kstDate: LocalDate = LocalDate.MIN,
) : Serializable
```

`domain/ad/persistence/entity/AdRewardDailyQuota.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * per-user-per-day 광고 시청 카운터. SSV 적립 트랜잭션 안에서 SELECT … FOR UPDATE 로 락을 잡는다.
 */
@Entity
@IdClass(AdRewardDailyQuotaId::class)
@Table(name = "ad_reward_daily_quota")
class AdRewardDailyQuota(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Id
    @Column(name = "kst_date", nullable = false)
    val kstDate: LocalDate,

    usedCount: Int = 0,
) : BaseEntity() {
    @Column(name = "used_count", nullable = false)
    var usedCount: Int = usedCount
        private set

    fun increment() {
        usedCount += 1
    }
}
```

- [ ] **Step 6: 리포지토리**

`domain/ad/persistence/repository/AdRewardNonceRepository.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import org.springframework.data.jpa.repository.JpaRepository

interface AdRewardNonceRepository : JpaRepository<AdRewardNonce, String>
```

`domain/ad/persistence/repository/AdRewardDailyQuotaRepository.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuotaId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface AdRewardDailyQuotaRepository : JpaRepository<AdRewardDailyQuota, AdRewardDailyQuotaId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from AdRewardDailyQuota q where q.userId = :userId and q.kstDate = :kstDate")
    fun findForUpdate(@Param("userId") userId: Long, @Param("kstDate") kstDate: LocalDate): AdRewardDailyQuota?

    fun findByUserIdAndKstDate(userId: Long, kstDate: LocalDate): AdRewardDailyQuota?
}
```

- [ ] **Step 7: validate 확인**

Run: `cd apps/backend && ./gradlew test --tests "*GoogleAdSsvPersistenceIntegrationTest"`
Expected: PASS — 전체 컨텍스트가 Testcontainers MySQL 에서 V1~V5 적용 후 Hibernate validate 통과(신규 두 테이블 매핑 일치). 실패 시 컬럼명/타입(`kst_date` DATE↔LocalDate, `expires_at` TIMESTAMP(6)↔Instant, `used` BOOLEAN↔Boolean, 복합 PK) reconcile.

- [ ] **Step 8: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties/AdRewardProperties.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence apps/backend/src/main/resources/db/migration/V5__ad_reward.sql apps/backend/src/main/resources/application.yaml
git commit -m "feat(ad): 리워드 nonce·일일 한도 엔티티/마이그레이션(V5)·설정 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: nonce 발급 서비스 + issue-nonce 엔드포인트 (TDD)

**Files:**
- Create: `domain/ad/service/AdRewardNonceService.kt`
- Create: `domain/ad/web/response/IssueNonceResponse.kt`
- Create: `domain/ad/web/controller/AdRewardController.kt`
- Test: `src/test/.../domain/ad/web/controller/AdRewardControllerTest.kt`

- [ ] **Step 1: `AdRewardNonceService`**

`domain/ad/service/AdRewardNonceService.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AdRewardNonceService(
    private val adRewardNonceRepository: AdRewardNonceRepository,
    private val adRewardProperties: AdRewardProperties,
) {
    @Transactional
    fun issueFor(userId: Long, now: Instant): AdRewardNonce =
        adRewardNonceRepository.save(
            AdRewardNonce(
                nonce = UUID.randomUUID().toString().replace("-", ""),
                userId = userId,
                expiresAt = now.plus(adRewardProperties.nonceTtl),
            )
        )
}
```

- [ ] **Step 2: `IssueNonceResponse`**

`domain/ad/web/response/IssueNonceResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.web.response

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import java.time.Instant

data class IssueNonceResponse(
    val nonce: String,
    val expiresAt: Instant,
) {
    companion object {
        fun from(entity: AdRewardNonce): IssueNonceResponse =
            IssueNonceResponse(nonce = entity.nonce, expiresAt = entity.expiresAt)
    }
}
```

- [ ] **Step 3: `AdRewardController` (issue-nonce 만 우선)**

`domain/ad/web/controller/AdRewardController.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.domain.ad.service.AdRewardNonceService
import com.wnl.cashchat.api.domain.ad.web.response.IssueNonceResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/ads/reward")
class AdRewardController(
    private val adRewardNonceService: AdRewardNonceService,
) {
    @PostMapping("/issue-nonce")
    fun issueNonce(authentication: Authentication): IssueNonceResponse =
        IssueNonceResponse.from(
            adRewardNonceService.issueFor(authentication.userId(), Instant.now())
        )

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

- [ ] **Step 4: WebMvc 테스트 작성**

`src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/AdRewardControllerTest.kt` — 기존 `GoogleAdSsvControllerTest`/`ChatControllerTest` 패턴(@WebMvcTest, addFilters=false, principal 주입). issue-nonce 만 우선 검증(quota 는 Task 5 에서 추가).
```kotlin
package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.service.AdRewardNonceService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(AdRewardController::class)
@AutoConfigureMockMvc(addFilters = false)
class AdRewardControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var adRewardNonceService: AdRewardNonceService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("issue-nonce returns nonce and expiry for the authenticated user") {
            val expiresAt = Instant.parse("2026-05-31T00:10:00Z")
            whenever(adRewardNonceService.issueFor(eq(1L), any())).thenReturn(
                AdRewardNonce(nonce = "abc123", userId = 1L, expiresAt = expiresAt)
            )

            mockMvc.perform(post("/api/ads/reward/issue-nonce").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nonce").value("abc123"))
                .andExpect(jsonPath("$.expiresAt").exists())
        }
    }
}
```

- [ ] **Step 5: 테스트 실행 → PASS**

Run: `cd apps/backend && ./gradlew test --tests "*AdRewardControllerTest"`
Expected: PASS (1 test).

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/AdRewardNonceService.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web
git commit -m "feat(ad): nonce 발급 서비스 및 issue-nonce 엔드포인트 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: AdRewardService.grantFromCallback (TDD) + 컨트롤러 연동

**Files:**
- Create: `domain/ad/service/AdRewardService.kt`
- Test: `src/test/.../domain/ad/service/AdRewardServiceTest.kt`
- Modify: `domain/ad/web/controller/GoogleAdSsvController.kt`
- Modify: `src/test/.../domain/ad/web/controller/GoogleAdSsvControllerTest.kt`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/AdRewardServiceTest.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class AdRewardServiceTest : FunSpec({
    lateinit var eventRepository: GoogleAdSsvEventRepository
    lateinit var nonceRepository: AdRewardNonceRepository
    lateinit var quotaRepository: AdRewardDailyQuotaRepository
    lateinit var userPointService: UserPointService
    lateinit var service: AdRewardService

    // KST 2026-05-31 09:00 == 2026-05-31T00:00:00Z
    val now = Instant.parse("2026-05-31T00:00:00Z")
    val kstToday = LocalDate.of(2026, 5, 31)
    val txnId = "txn-1"

    fun callback(userIdNonce: String) = GoogleAdSsvCallback(
        adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
        transactionId = txnId, userId = userIdNonce, signature = "sig", keyId = 1L,
        rawQueryString = "raw", signedPayload = "raw",
    )

    beforeTest {
        eventRepository = mock()
        nonceRepository = mock()
        quotaRepository = mock()
        userPointService = mock()
        service = AdRewardService(eventRepository, nonceRepository, quotaRepository, userPointService, AdRewardProperties())
    }

    test("invalid/used/expired nonce marks event REJECTED_INVALID_NONCE and grants nothing") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-x", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        whenever(eventRepository.findByTransactionId(txnId)).thenReturn(event)
        whenever(nonceRepository.findById("nonce-x")).thenReturn(Optional.empty())

        service.grantFromCallback(callback("nonce-x"), now)

        event.rewardStatus shouldBe RewardStatus.REJECTED_INVALID_NONCE
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }

    test("over quota marks event REJECTED_OVER_QUOTA and grants nothing") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-y", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        whenever(eventRepository.findByTransactionId(txnId)).thenReturn(event)
        whenever(nonceRepository.findById("nonce-y")).thenReturn(Optional.of(AdRewardNonce(nonce = "nonce-y", userId = 7L, expiresAt = now.plusSeconds(60))))
        whenever(quotaRepository.findForUpdate(7L, kstToday)).thenReturn(AdRewardDailyQuota(userId = 7L, kstDate = kstToday, usedCount = 10))

        service.grantFromCallback(callback("nonce-y"), now)

        event.rewardStatus shouldBe RewardStatus.REJECTED_OVER_QUOTA
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }

    test("valid nonce within quota grants coins, marks used, increments quota, event GRANTED") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-z", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        val nonce = AdRewardNonce(nonce = "nonce-z", userId = 7L, expiresAt = now.plusSeconds(60))
        val quota = AdRewardDailyQuota(userId = 7L, kstDate = kstToday, usedCount = 3)
        whenever(eventRepository.findByTransactionId(txnId)).thenReturn(event)
        whenever(nonceRepository.findById("nonce-z")).thenReturn(Optional.of(nonce))
        whenever(quotaRepository.findForUpdate(7L, kstToday)).thenReturn(quota)

        service.grantFromCallback(callback("nonce-z"), now)

        event.rewardStatus shouldBe RewardStatus.GRANTED
        nonce.used shouldBe true
        quota.usedCount shouldBe 4
        verify(userPointService).recordTransaction(eq(7L), eq(40L), eq(PointTransactionReason.AD_REWARD), eq("admob:reward:txn-1"))
    }
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*AdRewardServiceTest"`
Expected: 컴파일 실패 — `AdRewardService` 없음.

- [ ] **Step 3: `AdRewardService` 구현**

`domain/ad/service/AdRewardService.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * SSV 서명 검증 통과·신규 저장된 광고 이벤트에 대해 코인을 적립한다.
 * 단일 @Transactional 안에서 nonce 해석 → 일일 한도 행 락 → 코인 적립 → 이벤트 상태 갱신을 원자적으로 수행.
 * callback.userId 는 클라이언트가 SSV user_id 필드에 실은 서버 발급 nonce 다(직접 신뢰 금지).
 */
@Service
class AdRewardService(
    private val googleAdSsvEventRepository: GoogleAdSsvEventRepository,
    private val adRewardNonceRepository: AdRewardNonceRepository,
    private val adRewardDailyQuotaRepository: AdRewardDailyQuotaRepository,
    private val userPointService: UserPointService,
    private val adRewardProperties: AdRewardProperties,
) {
    @Transactional
    fun grantFromCallback(callback: GoogleAdSsvCallback, now: Instant) {
        // 동일 transactionId 동시 콜백을 직렬화하기 위해 이벤트를 비관적 쓰기 락으로 조회(상태 덮어쓰기 레이스 방지).
        val event = googleAdSsvEventRepository.findForUpdateByTransactionId(callback.transactionId) ?: return
        // VERIFIED(적립 미결정)만 적립을 시도한다. GRANTED·REJECTED_* 종결 이벤트는 재전송 시 멱등하게 건너뛴다.
        if (event.rewardStatus != RewardStatus.VERIFIED) {
            return
        }

        // 동일 nonce 동시 요청을 직렬화하기 위해 비관적 쓰기 락으로 조회한다(무락 시 stale 캐시로 중복 적립).
        val nonce = adRewardNonceRepository.findForUpdate(callback.userId)
        if (nonce == null || !nonce.isUsable(now)) {
            event.markRejected(RewardStatus.REJECTED_INVALID_NONCE)
            return
        }

        val kstDate = LocalDate.ofInstant(now, KST)
        val quota = lockOrCreateQuota(nonce.userId, kstDate)
        if (quota.usedCount >= adRewardProperties.dailyLimit) {
            nonce.markUsed() // 한도 초과 거절이어도 유효 nonce 는 1회 시청에 소모 처리(단일 사용 보장)
            event.markRejected(RewardStatus.REJECTED_OVER_QUOTA)
            return
        }

        quota.increment()
        nonce.markUsed()
        userPointService.recordTransaction(
            userId = nonce.userId,
            delta = adRewardProperties.coinAmount,
            reason = PointTransactionReason.AD_REWARD,
            idempotencyKey = "admob:reward:${callback.transactionId}",
        )
        event.markGranted()
    }

    // 멱등 native INSERT(ON DUPLICATE KEY UPDATE no-op)로 행을 보장 생성 → 충돌해도 예외가 없어 트랜잭션이
    // 오염되지 않고, findForUpdate 가 행을 락과 함께 최신 상태로 처음 로드한다.
    private fun lockOrCreateQuota(userId: Long, kstDate: LocalDate): AdRewardDailyQuota {
        adRewardDailyQuotaRepository.insertIfAbsent(userId, kstDate)
        return adRewardDailyQuotaRepository.findForUpdate(userId, kstDate)
            ?: throw IllegalStateException("ad_reward_daily_quota row must exist for userId=$userId on $kstDate")
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
```

> **동시성 전략 / 락 순서**: 동일 transactionId·동일 nonce 동시 요청에서도 첫 트랜잭션만 적립하고 상태 덮어쓰기를 막도록, 이벤트·nonce·일일 한도 행을 모두 비관적 쓰기 락(`SELECT … FOR UPDATE`)으로 조회한다. 데드락 방지를 위해 락 획득 순서를 **event → nonce → ad_reward_daily_quota → user_point** 로 고정한다(`GoogleAdSsvEventRepository.findForUpdateByTransactionId` → `AdRewardNonceRepository.findForUpdate` → `AdRewardDailyQuotaRepository.findForUpdate` → `UserPointService.recordTransaction` 내부 락).

- [ ] **Step 4: 단위 테스트 통과**

Run: `cd apps/backend && ./gradlew test --tests "*AdRewardServiceTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: `GoogleAdSsvController` 에 적립 연동**

`GoogleAdSsvController.kt` 를 수정 — `AdRewardService` 주입 + 신규 저장 시 grant 호출. 기존 `verify` 메서드와 생성자를 다음으로 교체:
```kotlin
@RestController
@RequestMapping("/api/ads")
@Tag(name = "Ads", description = "Advertising callback endpoints")
class GoogleAdSsvController(
    private val googleAdSsvService: GoogleAdSsvService,
    private val adRewardService: AdRewardService,
) {
    @GetMapping("/google/ssv")
    @Operation(
        summary = "Verify Google AdMob SSV callback",
        description = "Verifies a Google AdMob rewarded ad SSV callback and grants coins for newly verified events."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Callback verified and processed."),
            ApiResponse(
                responseCode = "400",
                description = "Callback is malformed or signature verification failed.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "503",
                description = "Google public keys are temporarily unavailable.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun verify(request: HttpServletRequest): ResponseEntity<Void> {
        val result = googleAdSsvService.verifyAndStore(request.queryString)
        if (result.newlyStored) {
            adRewardService.grantFromCallback(result.callback, java.time.Instant.now())
        }
        return ResponseEntity.ok().build()
    }
}
```
필요한 import 추가: `com.wnl.cashchat.api.domain.ad.service.AdRewardService`.

- [ ] **Step 6: `GoogleAdSsvControllerTest` 에 AdRewardService mock 추가**

기존 `GoogleAdSsvControllerTest` 는 `@WebMvcTest(GoogleAdSsvController::class)` 로 `googleAdSsvService` 만 mock 한다. 새 의존성 `adRewardService` 를 `@MockBean` 으로 추가하고, 기존 테스트가 `verifyAndStore` 가 `GoogleAdSsvVerificationResult` 를 반환하도록 stub 한다. 파일 상단 mock 선언부에 추가:
```kotlin
    @MockBean lateinit var adRewardService: com.wnl.cashchat.api.domain.ad.service.AdRewardService
```
그리고 `verifyAndStore` 를 stub 하던 곳(또는 기본 동작)에서 반환값을 지정한다. 기존 테스트가 `whenever(googleAdSsvService.verifyAndStore(any()))` 를 쓰지 않고 void 호출만 검증했다면, 반환 타입이 non-null 이므로 mock 기본 반환이 null → NPE 가 날 수 있다. 각 테스트에서 다음과 같이 stub 을 보장한다(예: 검증 성공 케이스):
```kotlin
whenever(googleAdSsvService.verifyAndStore(any())).thenReturn(
    com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvVerificationResult(
        callback = /* 해당 테스트의 GoogleAdSsvCallback */, newlyStored = false,
    )
)
```
> 실제 파일을 열어 기존 테스트 구조에 맞게 최소 수정한다. 목표: 컨트롤러가 결과를 받도록 stub 하고, `newlyStored=false` 로 두면 grant 가 호출되지 않아 기존 검증(200 응답 등)이 그대로 유지된다. grant 호출 검증 케이스를 원하면 `newlyStored=true` + `verify(adRewardService).grantFromCallback(any(), any())` 추가.

- [ ] **Step 7: 광고 컨트롤러 테스트 통과**

Run: `cd apps/backend && ./gradlew test --tests "*GoogleAdSsvControllerTest" --tests "*AdRewardServiceTest"`
Expected: PASS. (gradle 단독 실행 — 동시 실행 금지)

- [ ] **Step 8: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/AdRewardService.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad
git commit -m "feat(ad): SSV 콜백 코인 적립(grantFromCallback) 및 컨트롤러 연동" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: quota 조회 (AdRewardService.quotaOf + GET /quota)

**Files:**
- Create: `domain/ad/service/AdRewardQuota.kt`
- Modify: `domain/ad/service/AdRewardService.kt`
- Create: `domain/ad/web/response/AdRewardQuotaResponse.kt`
- Modify: `domain/ad/web/controller/AdRewardController.kt`
- Modify: `src/test/.../domain/ad/web/controller/AdRewardControllerTest.kt`

- [ ] **Step 1: `AdRewardQuota` 도메인 결과 타입**

`domain/ad/service/AdRewardQuota.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.service

import java.time.Instant

data class AdRewardQuota(
    val usedToday: Int,
    val dailyLimit: Int,
    val remaining: Int,
    val resetAtKst: Instant,
)
```

- [ ] **Step 2: `AdRewardService.quotaOf` 추가**

`AdRewardService.kt` 에 메서드 추가(읽기 전용). KST 자정 리셋 시각 계산 포함:
```kotlin
    @Transactional(readOnly = true)
    fun quotaOf(userId: Long, now: Instant): AdRewardQuota {
        val kstDate = LocalDate.ofInstant(now, KST)
        val usedToday = adRewardDailyQuotaRepository.findByUserIdAndKstDate(userId, kstDate)?.usedCount ?: 0
        val resetAtKst = kstDate.plusDays(1).atStartOfDay(KST).toInstant()
        return AdRewardQuota(
            usedToday = usedToday,
            dailyLimit = adRewardProperties.dailyLimit,
            remaining = (adRewardProperties.dailyLimit - usedToday).coerceAtLeast(0),
            resetAtKst = resetAtKst,
        )
    }
```

- [ ] **Step 3: `AdRewardQuotaResponse`**

`domain/ad/web/response/AdRewardQuotaResponse.kt`:
```kotlin
package com.wnl.cashchat.api.domain.ad.web.response

import com.wnl.cashchat.api.domain.ad.service.AdRewardQuota
import java.time.Instant

data class AdRewardQuotaResponse(
    val usedToday: Int,
    val dailyLimit: Int,
    val remaining: Int,
    val resetAtKst: Instant,
) {
    companion object {
        fun from(quota: AdRewardQuota): AdRewardQuotaResponse =
            AdRewardQuotaResponse(quota.usedToday, quota.dailyLimit, quota.remaining, quota.resetAtKst)
    }
}
```

- [ ] **Step 4: 컨트롤러에 GET /quota 추가**

`AdRewardController.kt` 에 `adRewardService` 주입 + 엔드포인트 추가. 생성자와 import 갱신:
```kotlin
@RestController
@RequestMapping("/api/ads/reward")
class AdRewardController(
    private val adRewardNonceService: AdRewardNonceService,
    private val adRewardService: com.wnl.cashchat.api.domain.ad.service.AdRewardService,
) {
    @PostMapping("/issue-nonce")
    fun issueNonce(authentication: Authentication): IssueNonceResponse =
        IssueNonceResponse.from(
            adRewardNonceService.issueFor(authentication.userId(), Instant.now())
        )

    @org.springframework.web.bind.annotation.GetMapping("/quota")
    fun quota(authentication: Authentication): com.wnl.cashchat.api.domain.ad.web.response.AdRewardQuotaResponse =
        com.wnl.cashchat.api.domain.ad.web.response.AdRewardQuotaResponse.from(
            adRewardService.quotaOf(authentication.userId(), Instant.now())
        )

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
```

- [ ] **Step 5: 컨트롤러 테스트에 quota 케이스 + AdRewardService mock 추가**

`AdRewardControllerTest.kt` 의 mock 선언에 추가:
```kotlin
    @MockBean lateinit var adRewardService: com.wnl.cashchat.api.domain.ad.service.AdRewardService
```
그리고 테스트 추가:
```kotlin
        test("quota returns today usage for the authenticated user") {
            whenever(adRewardService.quotaOf(eq(1L), any())).thenReturn(
                com.wnl.cashchat.api.domain.ad.service.AdRewardQuota(
                    usedToday = 3, dailyLimit = 10, remaining = 7,
                    resetAtKst = java.time.Instant.parse("2026-06-01T15:00:00Z"),
                )
            )

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ads/reward/quota").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.usedToday").value(3))
                .andExpect(jsonPath("$.dailyLimit").value(10))
                .andExpect(jsonPath("$.remaining").value(7))
        }
```

- [ ] **Step 6: 테스트 통과**

Run: `cd apps/backend && ./gradlew test --tests "*AdRewardControllerTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web
git commit -m "feat(ad): 일일 시청 한도 조회(quota) 엔드포인트 추가" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 통합 테스트 — 적립·동시성·멱등 (Testcontainers MySQL)

**Files:**
- Test: `src/test/.../domain/ad/persistence/AdRewardIntegrationTest.kt`

`GoogleAdSsvPersistenceIntegrationTest`/`PointIdempotencyIntegrationTest` 패턴(@SpringBootTest, SpringExtension, companion MySQLContainer + @DynamicPropertySource)을 따른다. 실제 V5 + 행 락으로 적립/한도/동시성/멱등을 검증한다. SSV 서명 검증(cc-242)은 우회하고 `AdRewardService.grantFromCallback` 을 직접 호출(이벤트는 미리 저장)한다.

- [ ] **Step 1: 통합 테스트 작성**

```kotlin
package com.wnl.cashchat.api.domain.ad.persistence

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.service.AdRewardService
import com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvCallback
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
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
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class AdRewardIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var eventRepository: GoogleAdSsvEventRepository
    @Autowired lateinit var nonceRepository: AdRewardNonceRepository
    @Autowired lateinit var quotaRepository: AdRewardDailyQuotaRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var adRewardService: AdRewardService

    private val now = Instant.parse("2026-05-31T00:00:00Z")

    private fun callback(txnId: String, nonce: String) = GoogleAdSsvCallback(
        adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
        transactionId = txnId, userId = nonce, signature = "sig", keyId = 1L,
        rawQueryString = "raw-$txnId", signedPayload = "raw",
    )

    private fun storeEvent(txnId: String, nonce: String) =
        eventRepository.saveAndFlush(
            GoogleAdSsvEvent(transactionId = txnId, userId = nonce, rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw-$txnId")
        )

    init {
        beforeTest {
            quotaRepository.deleteAll()
            nonceRepository.deleteAll()
            eventRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("valid nonce grants configured coins and marks event GRANTED") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "ad"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            nonceRepository.saveAndFlush(AdRewardNonce(nonce = "n1", userId = user.id, expiresAt = now.plusSeconds(600)))
            storeEvent("t1", "n1")

            adRewardService.grantFromCallback(callback("t1", "n1"), now)

            eventRepository.findByTransactionId("t1")!!.rewardStatus shouldBe RewardStatus.GRANTED
            nonceRepository.findById("n1").get().used shouldBe true
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("duplicate transaction id does not double-credit (idempotency key)") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            nonceRepository.saveAndFlush(AdRewardNonce(nonce = "n2", userId = user.id, expiresAt = now.plusSeconds(600)))
            storeEvent("t2", "n2")

            adRewardService.grantFromCallback(callback("t2", "n2"), now)
            // 동일 transactionId 재호출(이중 방어선: recordTransaction 멱등 키)
            adRewardService.grantFromCallback(callback("t2", "n2"), now)

            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
        }

        test("concurrent grants for one user at limit-1 grant exactly once more") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "race"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            // 한도 직전(9/10)으로 채워둔다
            quotaRepository.saveAndFlush(
                com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota(
                    userId = user.id, kstDate = java.time.LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Seoul")), usedCount = 9
                )
            )
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            repeat(threads) { i ->
                nonceRepository.saveAndFlush(AdRewardNonce(nonce = "rn-$i", userId = user.id, expiresAt = now.plusSeconds(600)))
                storeEvent("rt-$i", "rn-$i")
                pool.submit {
                    ready.countDown(); go.await()
                    try { adRewardService.grantFromCallback(callback("rt-$i", "rn-$i"), now) } catch (e: Exception) { }
                }
            }
            ready.await(); go.countDown(); pool.shutdown(); pool.awaitTermination(30, TimeUnit.SECONDS)

            // 한도 10 → 9에서 정확히 1회만 추가 적립
            quotaRepository.findByUserIdAndKstDate(user.id, java.time.LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Seoul")))!!.usedCount shouldBe 10
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
            pointTransactionRepository.count() shouldBe 1L
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
        }
    }
}
```

- [ ] **Step 2: 통합 테스트 실행 → PASS**

Run: `cd apps/backend && ./gradlew test --tests "*AdRewardIntegrationTest"`
Expected: PASS (3 tests). Docker 필요. 동시성 테스트는 한도-1(9)에서 6스레드 경합 → 정확히 1회만 GRANT(usedCount 10, 코인 1회). 단언이 실패하면 약화하지 말고 원인(행 락 미동작 등) 조사·보고.

- [ ] **Step 3: 커밋**

```bash
git add apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/persistence/AdRewardIntegrationTest.kt
git commit -m "test(ad): 리워드 적립·동시성·멱등 통합 테스트 추가 (TestContainers MySQL)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 전체 빌드 검증 + 체크리스트 + PR

- [ ] **Step 1: 전체 빌드**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL. 전체 테스트 통과, Flyway V1~V5 가 H2(MySQL 모드)·MySQL 8 양쪽 적용, validate 통과, cc-242 기존 테스트 회귀 없음.

- [ ] **Step 2: `docs/features/reward/tasks.md` BE-3 갱신**

BE-3 항목을 `[x]`로 갱신하되 메모: SSV 서명 검증·콜백·이벤트 로깅은 cc-242가 선제 구현, 본 PR은 그 위에 nonce→userId·일일 한도·코인 적립·결과 기록을 추가. nonce는 SSV `user_id` 필드로 전달(설계 D1). `reward.admob.*` 대신 `app.ads.reward.*` 사용.

- [ ] **Step 3: 커밋**

```bash
git add docs/features/reward/tasks.md
git commit -m "docs(reward): BE-3 광고 리워드 적립 PR3 완료 항목 체크리스트 반영" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: 푸시 + PR (finishing-a-development-branch 스킬)**

origin(seedplan005 포크)에 푸시 후 upstream `cash-chat-mvp/cash-chat-mvp` `dev` 대상으로 PR. 제목: `[CC-288] 혜택존 리워드 광고 적립 (PR3)`.

---

## Self-Review 결과

- **Spec 커버리지:** nonce 발급(Task 3) ✓; SSV 콜백 한도 내 적립(Task 4, 통합 Task 6) ✓; 위조/만료 nonce → REJECTED_INVALID_NONCE(Task 4 단위) ✓; 일일 한도 초과 → REJECTED_OVER_QUOTA + TOCTOU 행 락(Task 4 단위 · Task 6 동시성) ✓; 중복 SSV(Task 1 newlyStored=false · Task 6 멱등) ✓; quota 조회(Task 5) ✓; 멱등성 키 `admob:reward:{transactionId}`(Task 4) ✓; 적립 결과 rewardStatus 기록(Task 1·4) ✓; V5(Task 2) ✓; 설정(Task 2) ✓.
- **D1~D5 반영:** D1 nonce=user_id 필드(Task 4 서비스가 callback.userId를 nonce로 해석) ✓; D2 멱등 키 ✓; D3 rewardStatus 확장(Task 1) ✓; D4 verifyAndStore 반환 + 컨트롤러 연동(Task 1·4) ✓; D5 V5(Task 2) ✓.
- **타입 일관성:** `GoogleAdSsvVerificationResult(callback, newlyStored)`, `grantFromCallback(callback, now)`, `quotaOf(userId, now)`, `issueFor(userId, now)`, `AdRewardProperties(coinAmount, dailyLimit, nonceTtl)`, repo 메서드(`findForUpdate`, `findByUserIdAndKstDate`, `findById(nonce)`) — Task 간 일관.
- **cc-242 회귀:** Task 1은 반환 타입만 추가(기존 서비스 테스트 무영향), Task 4는 컨트롤러에 의존성 추가(컨트롤러 테스트에 mock 추가로 대응). Task 1 Step 2/5 와 Task 4 Step 7 로 회귀 확인.
- **미해결/주의:** `app.ads.reward.*` 프로퍼티는 `@ConfigurationPropertiesScan` 자동 등록 가정 — Task 2 Step 7 validate/부팅으로 확인. 서명 실패 응답(4xx/503)은 cc-242 소관으로 불변(설계 §9). FE의 nonce 주입·INF AdMob 콘솔은 범위 외.
