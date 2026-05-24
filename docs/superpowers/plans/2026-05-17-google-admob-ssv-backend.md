# Google AdMob SSV Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend-only Google AdMob SSV callback that verifies and stores successful rewarded-ad verification events without crediting points.

**Architecture:** Add a focused `domain/ad` package that owns SSV parsing, Google public key retrieval/caching, ECDSA verification, persistence, and HTTP response mapping. Keep `domain/point` unchanged; verified rows are future point-credit candidates only.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11 MVC, Spring Data JPA, RestClient, Kotest, Mockito-Kotlin, MockMvc, MySQL Testcontainers, Java `Signature`/`KeyFactory` crypto.

---

## External Reference

Use the official Google AdMob SSV documentation during implementation:
`https://developers.google.com/admob/android/ssv`

Implementation constraints from the doc:

- SSV callbacks include `ad_network`, `ad_unit`, optional `custom_data`, `key_id`, `reward_amount`, `reward_item`, `signature`, `timestamp`, `transaction_id`, and optional `user_id`.
- Query parameters are sent alphabetically, with `signature` and `key_id` last.
- Do not modify the signed content or reorder parameters.
- Verify the UTF-8 bytes before `&signature=` using the public key selected by `key_id`.
- The public key JSON contains `keys[].keyId` and `keys[].base64`.
- Verification uses ECDSA SHA-256 with DER-encoded signatures.
- Cache AdMob public keys for no longer than 24 hours.
- Google expects `HTTP 200 OK` for successful callbacks and may retry failed delivery.

## File Structure

Create these focused backend files:

- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt`: verified-event JPA entity and `RewardStatus`.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/repository/GoogleAdSsvEventRepository.kt`: lookup by `transactionId`.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties/GoogleAdSsvProperties.kt`: SSV key URI, cache TTL, rewarded ad unit id.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt`: parsed callback data and signed payload.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt`: raw query parser that preserves signed bytes.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdPublicKeyClient.kt`: RestClient public key fetch + TTL memory cache.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt`: ECDSA verification.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt`: orchestration, ad-unit validation, idempotent persistence.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/exception/GoogleAdSsvException.kt`: invalid vs transient exceptions.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt`: public callback endpoint.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/exception/GoogleAdSsvExceptionHandler.kt`: `400`/`500` response mapping.

Modify:

- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt`: permit only `GET /api/v1/ads/google/ssv`.
- `apps/backend/src/main/resources/application.yaml`: dev defaults for Google SSV properties.
- `apps/backend/src/main/resources/application-prod.yaml`: production rewarded ad unit env binding.
- `apps/backend/.env.example`: document env vars.

Create tests:

- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/persistence/GoogleAdSsvPersistenceIntegrationTest.kt`
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParserTest.kt`
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdPublicKeyClientTest.kt`
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifierTest.kt`
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt`
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvControllerTest.kt`
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/GoogleAdSsvPropertiesTest.kt`

### Task 1: Persistence Model

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/repository/GoogleAdSsvEventRepository.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/persistence/GoogleAdSsvPersistenceIntegrationTest.kt`

- [ ] **Step 1: Write the failing persistence test**

Create `GoogleAdSsvPersistenceIntegrationTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.persistence

import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
class GoogleAdSsvPersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var repository: GoogleAdSsvEventRepository
    @Autowired lateinit var entityManager: EntityManager

    init {
        beforeTest { repository.deleteAll() }

        test("verified google ad ssv event persists core fields and raw query") {
            val event = repository.saveAndFlush(
                GoogleAdSsvEvent(
                    transactionId = "18fa792de1bca816048293fc71035638",
                    userId = "1234567",
                    rewardAmount = 5,
                    rewardItem = "coins",
                    adUnit = "ca-app-pub-3940256099942544/5224354917",
                    keyId = 1916455855,
                    rawQueryString = "ad_network=5450213213286189855&ad_unit=ca-app-pub-3940256099942544/5224354917&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=18fa792de1bca816048293fc71035638&user_id=1234567&signature=abc&key_id=1916455855",
                )
            )

            entityManager.clear()

            val found = repository.findByTransactionId(event.transactionId)
                ?: error("event should be found")

            found.userId shouldBe "1234567"
            found.rewardAmount shouldBe 5
            found.rewardItem shouldBe "coins"
            found.adUnit shouldBe "ca-app-pub-3940256099942544/5224354917"
            found.keyId shouldBe 1916455855
            found.rewardStatus shouldBe RewardStatus.VERIFIED
            found.rawQueryString shouldBe event.rawQueryString
        }

        test("transaction id is unique") {
            val first = GoogleAdSsvEvent(
                transactionId = "duplicate-transaction",
                userId = "1",
                rewardAmount = 5,
                rewardItem = "coins",
                adUnit = "ad-unit",
                keyId = 1,
                rawQueryString = "first",
            )
            val second = GoogleAdSsvEvent(
                transactionId = "duplicate-transaction",
                userId = "1",
                rewardAmount = 5,
                rewardItem = "coins",
                adUnit = "ad-unit",
                keyId = 1,
                rawQueryString = "second",
            )

            repository.saveAndFlush(first)

            shouldThrow<DataIntegrityViolationException> {
                repository.saveAndFlush(second)
            }
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

- [ ] **Step 2: Run test to verify it fails**

Run from `apps/backend`:

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvPersistenceIntegrationTest"
```

Expected: compile failure because `GoogleAdSsvEvent` and repository do not exist.

- [ ] **Step 3: Add entity and repository**

Create `GoogleAdSsvEvent.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.entity

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

@Entity
@Table(
    name = "google_ad_ssv_events",
    uniqueConstraints = [UniqueConstraint(name = "uk_google_ad_ssv_events_transaction_id", columnNames = ["transaction_id"])]
)
class GoogleAdSsvEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id", nullable = false, length = 128)
    val transactionId: String,

    @Column(name = "user_id", nullable = false, length = 128)
    val userId: String,

    @Column(name = "reward_amount", nullable = false)
    val rewardAmount: Int,

    @Column(name = "reward_item", nullable = false, length = 128)
    val rewardItem: String,

    @Column(name = "ad_unit", nullable = false, length = 255)
    val adUnit: String,

    @Column(name = "key_id", nullable = false)
    val keyId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false, length = 32)
    val rewardStatus: RewardStatus = RewardStatus.VERIFIED,

    @Column(name = "raw_query_string", nullable = false, columnDefinition = "TEXT")
    val rawQueryString: String,
) : BaseEntity() {
    init {
        require(transactionId.isNotBlank()) { "transactionId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(rewardAmount > 0) { "rewardAmount must be positive" }
        require(rewardItem.isNotBlank()) { "rewardItem must not be blank" }
        require(adUnit.isNotBlank()) { "adUnit must not be blank" }
        require(keyId >= 0) { "keyId must be non-negative" }
        require(rawQueryString.isNotBlank()) { "rawQueryString must not be blank" }
    }
}

enum class RewardStatus {
    VERIFIED,
}
```

Create `GoogleAdSsvEventRepository.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import org.springframework.data.jpa.repository.JpaRepository

interface GoogleAdSsvEventRepository : JpaRepository<GoogleAdSsvEvent, Long> {
    fun findByTransactionId(transactionId: String): GoogleAdSsvEvent?
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvPersistenceIntegrationTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/persistence
git commit -m "feat(ad): add google ssv event persistence"
```

### Task 2: Properties And Profile Configuration

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties/GoogleAdSsvProperties.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/GoogleAdSsvPropertiesTest.kt`
- Modify: `apps/backend/src/main/resources/application.yaml`
- Modify: `apps/backend/src/main/resources/application-prod.yaml`
- Modify: `apps/backend/.env.example`

- [ ] **Step 1: Write failing properties tests**

Create `GoogleAdSsvPropertiesTest.kt`:

```kotlin
package com.wnl.cashchat.api.config

import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import java.time.Duration

class GoogleAdSsvPropertiesTest : FunSpec({
    test("GoogleAdSsvProperties defaults to the AdMob key server and one day cache ttl") {
        val properties = GoogleAdSsvProperties()

        properties.ssvPublicKeysUri shouldBe "https://www.gstatic.com/admob/reward/verifier-keys.json"
        properties.publicKeyCacheTtl shouldBe Duration.ofHours(24)
        properties.rewardedAdUnitId shouldBe ""
    }

    test("GoogleAdSsvProperties rejects cache ttl longer than one day") {
        val validator = Validation.buildDefaultValidatorFactory().validator

        val violations = validator.validate(
            GoogleAdSsvProperties(publicKeyCacheTtl = Duration.ofHours(25))
        )

        violations.map { it.propertyPath.toString() } shouldContain "publicKeyCacheTtl"
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvPropertiesTest"
```

Expected: compile failure because `GoogleAdSsvProperties` does not exist.

- [ ] **Step 3: Add properties class**

Create `GoogleAdSsvProperties.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.ads.google")
data class GoogleAdSsvProperties(
    @field:NotBlank
    val ssvPublicKeysUri: String = DEFAULT_PUBLIC_KEYS_URI,
    @field:MaxDuration(hours = 24)
    val publicKeyCacheTtl: Duration = Duration.ofHours(24),
    val rewardedAdUnitId: String = "",
) {
    fun isRewardedAdUnitValidationEnabled(): Boolean = rewardedAdUnitId.isNotBlank()

    companion object {
        const val DEFAULT_PUBLIC_KEYS_URI = "https://www.gstatic.com/admob/reward/verifier-keys.json"
    }
}
```

Create `MaxDuration.kt` next to it:

```kotlin
package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import java.time.Duration

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxDurationValidator::class])
annotation class MaxDuration(
    val hours: Long,
    val message: String = "duration exceeds maximum",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MaxDurationValidator : ConstraintValidator<MaxDuration, Duration> {
    private lateinit var max: Duration

    override fun initialize(annotation: MaxDuration) {
        max = Duration.ofHours(annotation.hours)
    }

    override fun isValid(value: Duration?, context: ConstraintValidatorContext): Boolean =
        value != null && !value.isNegative && !value.isZero && value <= max
}
```

- [ ] **Step 4: Add YAML/env configuration**

Ensure `GoogleAdSsvProperties` is discovered by Spring. The current app uses
`@ConfigurationPropertiesScan` on `CashChatApiApplication`, which covers the
`com.wnl.cashchat.api.domain.ad.properties` package. If that annotation is not
present in the target branch, add `@ConfigurationPropertiesScan` to the main
Spring Boot application class or register the properties explicitly with
`@EnableConfigurationProperties(GoogleAdSsvProperties::class)`.

In `application.yaml`, under existing `app:`:

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

In `application-prod.yaml`, add:

```yaml
app:
  ads:
    google:
      rewarded-ad-unit-id: ${APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID}
```

In `.env.example`, add:

```properties
# Google AdMob SSV
APP_ADS_GOOGLE_SSV_PUBLIC_KEYS_URI=https://www.gstatic.com/admob/reward/verifier-keys.json
APP_ADS_GOOGLE_PUBLIC_KEY_CACHE_TTL=24h
APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID=ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy
```

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvPropertiesTest" --tests "*CashChatApiApplicationTests"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/GoogleAdSsvPropertiesTest.kt apps/backend/src/main/resources/application.yaml apps/backend/src/main/resources/application-prod.yaml apps/backend/.env.example
git commit -m "feat(ad): add google ssv configuration"
```

### Task 3: Raw Query Parser

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/exception/GoogleAdSsvException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParserTest.kt`

- [ ] **Step 1: Write failing parser tests**

Create `GoogleAdSsvQueryParserTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GoogleAdSsvQueryParserTest : FunSpec({
    val parser = GoogleAdSsvQueryParser()

    test("parse extracts fields and signed payload without modifying query order") {
        val raw = "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&user_id=123&signature=MEUCIQ&key_id=1916455855"

        val callback = parser.parse(raw)

        callback.adUnit shouldBe "ad-unit"
        callback.rewardAmount shouldBe 5
        callback.rewardItem shouldBe "coins"
        callback.timestamp shouldBe 1507770365237823L
        callback.transactionId shouldBe "tx-1"
        callback.userId shouldBe "123"
        callback.signature shouldBe "MEUCIQ"
        callback.keyId shouldBe 1916455855L
        callback.rawQueryString shouldBe raw
        callback.signedPayload shouldBe "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&user_id=123"
    }

    test("parse rejects missing user id because backend expects CashChat user id") {
        val raw = "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&signature=MEUCIQ&key_id=1916455855"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(raw)
        }
    }

    test("parse rejects signature not followed by key id") {
        val raw = "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&user_id=123&key_id=1916455855&signature=MEUCIQ"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(raw)
        }
    }

    test("parse rejects invalid reward amount") {
        val raw = "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=abc&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&user_id=123&signature=MEUCIQ&key_id=1916455855"

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            parser.parse(raw)
        }
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvQueryParserTest"
```

Expected: compile failure.

- [ ] **Step 3: Add exceptions, callback model, parser**

Create `GoogleAdSsvException.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.exception

sealed class GoogleAdSsvException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class InvalidGoogleAdSsvCallbackException(message: String, cause: Throwable? = null) :
    GoogleAdSsvException(message, cause)

class GoogleAdSsvTransientException(message: String, cause: Throwable? = null) :
    GoogleAdSsvException(message, cause)
```

Create `GoogleAdSsvCallback.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

data class GoogleAdSsvCallback(
    val adUnit: String,
    val rewardAmount: Int,
    val rewardItem: String,
    val timestamp: Long,
    val transactionId: String,
    val userId: String,
    val signature: String,
    val keyId: Long,
    val rawQueryString: String,
    val signedPayload: String,
)
```

Create `GoogleAdSsvQueryParser.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Component
class GoogleAdSsvQueryParser {
    fun parse(rawQueryString: String?): GoogleAdSsvCallback {
        val raw = rawQueryString?.takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleAdSsvCallbackException("Missing SSV query string")

        val signatureMarker = "&$SIGNATURE_PARAM="
        val signatureStart = raw.indexOf(signatureMarker)
        if (signatureStart <= 0) {
            throw InvalidGoogleAdSsvCallbackException("Missing signature parameter")
        }

        val signedPayload = raw.substring(0, signatureStart)
        val signatureAndKey = raw.substring(signatureStart + 1)
        if (!signatureAndKey.startsWith("$SIGNATURE_PARAM=")) {
            throw InvalidGoogleAdSsvCallbackException("Invalid signature parameter position")
        }

        val keyMarker = "&$KEY_ID_PARAM="
        val keyStart = signatureAndKey.indexOf(keyMarker)
        if (keyStart <= 0) {
            throw InvalidGoogleAdSsvCallbackException("Missing key_id parameter")
        }

        val signature = signatureAndKey
            .substring(SIGNATURE_PARAM.length + 1, keyStart)
            .takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleAdSsvCallbackException("Blank signature")

        val keyId = signatureAndKey
            .substring(keyStart + keyMarker.length)
            .takeIf { it.isNotBlank() }
            ?.toLongOrNull()
            ?: throw InvalidGoogleAdSsvCallbackException("Invalid key_id")

        val params = raw.split("&")
            .mapNotNull { part ->
                val index = part.indexOf("=")
                if (index < 0) null else part.substring(0, index) to decode(part.substring(index + 1))
            }
            .toMap()

        return GoogleAdSsvCallback(
            adUnit = required(params, "ad_unit"),
            rewardAmount = required(params, "reward_amount").toIntOrNull()
                ?: throw InvalidGoogleAdSsvCallbackException("Invalid reward_amount"),
            rewardItem = required(params, "reward_item"),
            timestamp = required(params, "timestamp").toLongOrNull()
                ?: throw InvalidGoogleAdSsvCallbackException("Invalid timestamp"),
            transactionId = required(params, "transaction_id"),
            userId = required(params, "user_id"),
            signature = signature,
            keyId = keyId,
            rawQueryString = raw,
            signedPayload = signedPayload,
        )
    }

    private fun required(params: Map<String, String>, name: String): String =
        params[name]?.takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleAdSsvCallbackException("Missing $name")

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private companion object {
        const val SIGNATURE_PARAM = "signature"
        const val KEY_ID_PARAM = "key_id"
    }
}
```

- [ ] **Step 4: Run parser tests**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvQueryParserTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/exception apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParserTest.kt
git commit -m "feat(ad): parse google ssv callbacks"
```

### Task 4: Public Key Client With TTL Cache

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdPublicKeyClient.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdPublicKeyClientTest.kt`

- [ ] **Step 0: Ensure a RestClient bean exists**

`GoogleAdPublicKeyClient` constructor-injects `RestClient`. The current backend
already provides `RestClientConfig.restClient()`. If that bean is missing in the
target branch, add a small `@Configuration` class that returns
`RestClient.builder().build()` or equivalent.

- [ ] **Step 1: Write failing key client tests**

Create `GoogleAdPublicKeyClientTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class GoogleAdPublicKeyClientTest : FunSpec({
    test("fetches and caches public keys by key id") {
        val fixture = fixture()
        val keyBase64 = base64PublicKey()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1916455855,"base64":"$keyBase64"}]}""", MediaType.APPLICATION_JSON))

        val first = fixture.client.getPublicKey(1916455855)
        val second = fixture.client.getPublicKey(1916455855)

        first shouldBe second
        fixture.server.verify()
    }

    test("refreshes keys after cache ttl expires") {
        val fixture = fixture()
        val firstKey = base64PublicKey()
        val secondKey = base64PublicKey()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1,"base64":"$firstKey"}]}""", MediaType.APPLICATION_JSON))
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withSuccess("""{"keys":[{"keyId":1,"base64":"$secondKey"}]}""", MediaType.APPLICATION_JSON))

        val first = fixture.client.getPublicKey(1)
        fixture.clock.advance(Duration.ofHours(25))
        val second = fixture.client.getPublicKey(1)

        (first == second) shouldBe false
        fixture.server.verify()
    }

    test("wraps key server errors as transient failures") {
        val fixture = fixture()
        fixture.server.expect(requestTo("https://keys.example.test"))
            .andRespond(withServerError())

        shouldThrow<GoogleAdSsvTransientException> {
            fixture.client.getPublicKey(1)
        }
    }
})

private fun fixture(): KeyClientFixture {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val clock = MutableClock(Instant.parse("2026-05-17T00:00:00Z"))
    val client = GoogleAdPublicKeyClient(
        restClient = builder.build(),
        properties = GoogleAdSsvProperties(
            ssvPublicKeysUri = "https://keys.example.test",
            publicKeyCacheTtl = Duration.ofHours(24),
        ),
        clock = clock,
    )
    return KeyClientFixture(client, server, clock)
}

private data class KeyClientFixture(
    val client: GoogleAdPublicKeyClient,
    val server: MockRestServiceServer,
    val clock: MutableClock,
)

private class MutableClock(private var now: Instant) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = now
}

private fun base64PublicKey(): String {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(256)
    val publicKey: PublicKey = generator.generateKeyPair().public
    return Base64.getEncoder().encodeToString(publicKey.encoded)
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests "*GoogleAdPublicKeyClientTest"
```

Expected: compile failure.

- [ ] **Step 3: Add key client**

Create `GoogleAdPublicKeyClient.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

@Component
class GoogleAdPublicKeyClient(
    private val restClient: RestClient,
    private val properties: GoogleAdSsvProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Volatile
    private var cachedKeys: CachedKeys? = null

    fun getPublicKey(keyId: Long): PublicKey {
        val keys = currentKeys()
        return keys[keyId] ?: throw GoogleAdSsvTransientException("Google AdMob public key not found for key_id=$keyId")
    }

    private fun currentKeys(): Map<Long, PublicKey> {
        val cached = cachedKeys
        val now = clock.instant()
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return cached.keys
        }

        synchronized(this) {
            val insideLock = cachedKeys
            if (insideLock != null && now.isBefore(insideLock.expiresAt)) {
                return insideLock.keys
            }

            val fetched = fetchKeys()
            cachedKeys = CachedKeys(fetched, now.plus(properties.publicKeyCacheTtl))
            return fetched
        }
    }

    private fun fetchKeys(): Map<Long, PublicKey> {
        val response = try {
            restClient.get()
                .uri(properties.ssvPublicKeysUri)
                .retrieve()
                .body(PublicKeysResponse::class.java)
        } catch (e: RestClientException) {
            throw GoogleAdSsvTransientException("Failed to fetch Google AdMob public keys", e)
        } ?: throw GoogleAdSsvTransientException("Google AdMob public key response was empty")

        val keys = response.keys.associate { it.keyId to decodePublicKey(it.base64) }
        if (keys.isEmpty()) {
            throw GoogleAdSsvTransientException("Google AdMob public key response contained no keys")
        }
        return keys
    }

    private fun decodePublicKey(base64: String): PublicKey {
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        } catch (e: RuntimeException) {
            throw GoogleAdSsvTransientException("Failed to decode Google AdMob public key", e)
        } catch (e: java.security.GeneralSecurityException) {
            throw GoogleAdSsvTransientException("Failed to decode Google AdMob public key", e)
        }
    }

    private data class CachedKeys(
        val keys: Map<Long, PublicKey>,
        val expiresAt: Instant,
    )

    private data class PublicKeysResponse(
        val keys: List<PublicKeyResponse> = emptyList(),
    )

    private data class PublicKeyResponse(
        @JsonProperty("keyId")
        val keyId: Long,
        val base64: String,
    )
}
```

- [ ] **Step 4: Run key client tests**

```powershell
.\gradlew.bat test --tests "*GoogleAdPublicKeyClientTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdPublicKeyClient.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdPublicKeyClientTest.kt
git commit -m "feat(ad): cache google ssv public keys"
```

### Task 5: Signature Verifier

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifierTest.kt`

- [ ] **Step 1: Write failing verifier tests**

Create `GoogleAdSsvSignatureVerifierTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class GoogleAdSsvSignatureVerifierTest : FunSpec({
    test("verify accepts a valid ECDSA SHA256 DER signature") {
        val keyPair = KeyPairGenerator.getInstance("EC").also { it.initialize(256) }.generateKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)
        val payload = "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&user_id=123"
        val signature = sign(payload, keyPair.private)

        whenever(publicKeyClient.getPublicKey(1916455855)).thenReturn(keyPair.public)

        verifier.verify(payload, signature, 1916455855)
    }

    test("verify rejects invalid signatures") {
        val keyPair = KeyPairGenerator.getInstance("EC").also { it.initialize(256) }.generateKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)

        whenever(publicKeyClient.getPublicKey(1916455855)).thenReturn(keyPair.public)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            verifier.verify("payload", "not-valid-base64", 1916455855)
        }
    }
})

private fun sign(payload: String, privateKey: java.security.PrivateKey): String {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(privateKey)
    signer.update(payload.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvSignatureVerifierTest"
```

Expected: compile failure.

- [ ] **Step 3: Add signature verifier**

Create `GoogleAdSsvSignatureVerifier.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.springframework.stereotype.Component
import java.security.Signature
import java.util.Base64

@Component
class GoogleAdSsvSignatureVerifier(
    private val publicKeyClient: GoogleAdPublicKeyClient,
) {
    fun verify(signedPayload: String, signature: String, keyId: Long) {
        val decodedSignature = try {
            Base64.getUrlDecoder().decode(padded(signature))
        } catch (e: IllegalArgumentException) {
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature encoding", e)
        }

        val publicKey = publicKeyClient.getPublicKey(keyId)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signedPayload.toByteArray(Charsets.UTF_8))

        if (!verifier.verify(decodedSignature)) {
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")
        }
    }

    private fun padded(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }
}
```

- [ ] **Step 4: Run verifier tests**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvSignatureVerifierTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifierTest.kt
git commit -m "feat(ad): verify google ssv signatures"
```

### Task 6: SSV Service Orchestration

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt`

- [ ] **Step 1: Write failing service tests**

Create `GoogleAdSsvServiceTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException

class GoogleAdSsvServiceTest : FunSpec({
    lateinit var parser: GoogleAdSsvQueryParser
    lateinit var verifier: GoogleAdSsvSignatureVerifier
    lateinit var repository: GoogleAdSsvEventRepository

    fun service(rewardedAdUnitId: String = "ad-unit") = GoogleAdSsvService(
        parser = parser,
        signatureVerifier = verifier,
        repository = repository,
        properties = GoogleAdSsvProperties(rewardedAdUnitId = rewardedAdUnitId),
    )

    beforeTest {
        parser = mock()
        verifier = mock()
        repository = mock()
    }

    test("verifyAndStore saves verified callbacks") {
        whenever(parser.parse("raw")).thenReturn(callback())
        whenever(repository.findByTransactionId("tx-1")).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.getArgument(0) }

        service().verifyAndStore("raw")

        verify(verifier).verify("signed-payload", "signature", 1916455855)
        verify(repository).saveAndFlush(
            argThat {
                transactionId == "tx-1" &&
                    userId == "123" &&
                    rewardAmount == 5 &&
                    rewardItem == "coins" &&
                    adUnit == "ad-unit" &&
                    keyId == 1916455855L &&
                    rawQueryString == "raw"
            }
        )
    }

    test("verifyAndStore treats existing transaction as idempotent success") {
        val existing = GoogleAdSsvEvent(
            transactionId = "tx-1",
            userId = "123",
            rewardAmount = 5,
            rewardItem = "coins",
            adUnit = "ad-unit",
            keyId = 1916455855,
            rawQueryString = "raw",
        )
        whenever(parser.parse("raw")).thenReturn(callback())
        whenever(repository.findByTransactionId("tx-1")).thenReturn(existing)

        service().verifyAndStore("raw")

        verify(verifier, never()).verify(any(), any(), any())
        verify(repository, never()).saveAndFlush(any())
    }

    test("verifyAndStore rejects rewarded ad unit mismatch") {
        whenever(parser.parse("raw")).thenReturn(callback(adUnit = "other-ad-unit"))
        whenever(repository.findByTransactionId("tx-1")).thenReturn(null)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service(rewardedAdUnitId = "ad-unit").verifyAndStore("raw")
        }

        verify(repository, never()).saveAndFlush(any())
    }

    test("verifyAndStore skips rewarded ad unit validation when setting is blank") {
        whenever(parser.parse("raw")).thenReturn(callback(adUnit = "any-ad-unit"))
        whenever(repository.findByTransactionId("tx-1")).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.getArgument(0) }

        service(rewardedAdUnitId = "").verifyAndStore("raw")

        verify(repository).saveAndFlush(any())
    }

    test("verifyAndStore recovers concurrent duplicate insert as idempotent success") {
        val existing = GoogleAdSsvEvent(
            transactionId = "tx-1",
            userId = "123",
            rewardAmount = 5,
            rewardItem = "coins",
            adUnit = "ad-unit",
            keyId = 1916455855,
            rawQueryString = "raw",
        )
        whenever(parser.parse("raw")).thenReturn(callback())
        whenever(repository.findByTransactionId("tx-1")).thenReturn(null, existing)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>()))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        service().verifyAndStore("raw")
    }
})

private fun callback(adUnit: String = "ad-unit") = GoogleAdSsvCallback(
    adUnit = adUnit,
    rewardAmount = 5,
    rewardItem = "coins",
    timestamp = 1507770365237823,
    transactionId = "tx-1",
    userId = "123",
    signature = "signature",
    keyId = 1916455855,
    rawQueryString = "raw",
    signedPayload = "signed-payload",
)
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvServiceTest"
```

Expected: compile failure.

- [ ] **Step 3: Add service**

Create `GoogleAdSsvService.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleAdSsvService(
    private val parser: GoogleAdSsvQueryParser,
    private val signatureVerifier: GoogleAdSsvSignatureVerifier,
    private val repository: GoogleAdSsvEventRepository,
    private val properties: GoogleAdSsvProperties,
) {
    private val log = LoggerFactory.getLogger(GoogleAdSsvService::class.java)

    @Transactional
    fun verifyAndStore(rawQueryString: String?) {
        val callback = parser.parse(rawQueryString)
        val existing = repository.findByTransactionId(callback.transactionId)
        if (existing != null) {
            logIfDuplicateDiffers(existing, callback)
            return
        }

        validateAdUnit(callback)
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        try {
            repository.saveAndFlush(
                GoogleAdSsvEvent(
                    transactionId = callback.transactionId,
                    userId = callback.userId,
                    rewardAmount = callback.rewardAmount,
                    rewardItem = callback.rewardItem,
                    adUnit = callback.adUnit,
                    keyId = callback.keyId,
                    rawQueryString = callback.rawQueryString,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            val createdByConcurrentRequest = repository.findByTransactionId(callback.transactionId)
            if (createdByConcurrentRequest != null) {
                logIfDuplicateDiffers(createdByConcurrentRequest, callback)
                return
            }
            throw e
        }
    }

    private fun validateAdUnit(callback: GoogleAdSsvCallback) {
        if (!properties.isRewardedAdUnitValidationEnabled()) return
        if (callback.adUnit != properties.rewardedAdUnitId) {
            throw InvalidGoogleAdSsvCallbackException("Google AdMob SSV ad_unit mismatch")
        }
    }

    private fun logIfDuplicateDiffers(existing: GoogleAdSsvEvent, callback: GoogleAdSsvCallback) {
        if (
            existing.userId != callback.userId ||
            existing.rewardAmount != callback.rewardAmount ||
            existing.rewardItem != callback.rewardItem ||
            existing.adUnit != callback.adUnit ||
            existing.keyId != callback.keyId
        ) {
            log.warn(
                "Duplicate Google AdMob SSV transaction_id={} has different core fields",
                callback.transactionId,
            )
        }
    }
}
```

- [ ] **Step 4: Run service tests**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt
git commit -m "feat(ad): store verified google ssv callbacks"
```

### Task 7: Controller, Exception Mapping, And Public Security Rule

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/exception/GoogleAdSsvExceptionHandler.kt`
- Create: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvControllerTest.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt`

- [ ] **Step 1: Write failing controller tests**

Create `GoogleAdSsvControllerTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvService
import com.wnl.cashchat.api.domain.ad.web.exception.GoogleAdSsvExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(GoogleAdSsvController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class, GoogleAdSsvExceptionHandler::class)
class GoogleAdSsvControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var googleAdSsvService: GoogleAdSsvService
    @MockitoBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Test
    fun `ssv endpoint is public and returns ok when callback is verified`() {
        val query = "ad_network=5450213213286189855&ad_unit=ad-unit&reward_amount=5&reward_item=coins&timestamp=1507770365237823&transaction_id=tx-1&user_id=123&signature=abc&key_id=1"

        mockMvc.perform(get("/api/v1/ads/google/ssv?$query"))
            .andExpect(status().isOk)

        verify(googleAdSsvService).verifyAndStore(query)
    }

    @Test
    fun `ssv endpoint returns bad request for invalid callbacks`() {
        whenever(googleAdSsvService.verifyAndStore("reward_amount=bad"))
            .thenThrow(InvalidGoogleAdSsvCallbackException("invalid"))

        mockMvc.perform(get("/api/v1/ads/google/ssv?reward_amount=bad"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_AD_SSV_CALLBACK"))
    }

    @Test
    fun `ssv endpoint returns server error for transient failures`() {
        whenever(googleAdSsvService.verifyAndStore("ad_unit=ad-unit"))
            .thenThrow(GoogleAdSsvTransientException("key server failed"))

        mockMvc.perform(get("/api/v1/ads/google/ssv?ad_unit=ad-unit"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("GOOGLE_AD_SSV_TEMPORARILY_UNAVAILABLE"))
    }

    @Test
    fun `other ad endpoints still require authentication`() {
        mockMvc.perform(get("/api/v1/ads/google/private"))
            .andExpect(status().isUnauthorized)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvControllerTest"
```

Expected: compile failure.

- [ ] **Step 3: Add controller and exception handler**

Create `GoogleAdSsvController.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/ads/google")
@Tag(name = "Ads", description = "Advertising callback endpoints")
class GoogleAdSsvController(
    private val googleAdSsvService: GoogleAdSsvService,
) {
    @GetMapping("/ssv")
    @Operation(
        summary = "Receive Google AdMob SSV callback",
        description = "Validates and stores Google rewarded ad server-side verification callbacks."
    )
    fun verify(request: HttpServletRequest): ResponseEntity<Void> {
        googleAdSsvService.verifyAndStore(request.queryString)
        return ResponseEntity.ok().build()
    }
}
```

Create `GoogleAdSsvExceptionHandler.kt`:

```kotlin
package com.wnl.cashchat.api.domain.ad.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.ad"])
class GoogleAdSsvExceptionHandler {
    private val log = LoggerFactory.getLogger(GoogleAdSsvExceptionHandler::class.java)

    @ExceptionHandler(InvalidGoogleAdSsvCallbackException::class)
    fun handleInvalidCallback(e: InvalidGoogleAdSsvCallbackException): ResponseEntity<ErrorResponse> {
        log.warn("Invalid Google AdMob SSV callback: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_GOOGLE_AD_SSV_CALLBACK", "Invalid Google AdMob SSV callback"))
    }

    @ExceptionHandler(GoogleAdSsvTransientException::class)
    fun handleTransientFailure(e: GoogleAdSsvTransientException): ResponseEntity<ErrorResponse> {
        log.error("Google AdMob SSV transient failure: {}", e.message, e)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("GOOGLE_AD_SSV_TEMPORARILY_UNAVAILABLE", "Google Ad SSV is temporarily unavailable."))
    }
}
```

- [ ] **Step 4: Narrowly open the callback path in SecurityConfig**

In `SecurityConfig.kt`, add this constant:

```kotlin
private const val GOOGLE_AD_SSV_PATH = "/api/v1/ads/google/ssv"
```

Then add it to `publicPaths`:

```kotlin
val publicPaths = mutableListOf(
    "/api/auth/guest",
    "/api/auth/callback/google",
    "/api/auth/refresh",
    GOOGLE_AD_SSV_PATH,
    "/favicon.ico"
)
```

Preserve all existing auth paths in the current file, including Apple paths if present on the target branch.

- [ ] **Step 5: Run controller tests**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsvControllerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvControllerTest.kt
git commit -m "feat(ad): expose google ssv callback"
```

### Task 8: Full Verification And OpenAPI Regression

**Files:**
- Modify: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/OpenApiDocumentationTest.kt`

- [ ] **Step 1: Add OpenAPI regression assertion**

Update `OpenApiDocumentationTest.kt` to assert `/api/v1/ads/google/ssv` appears in the generated OpenAPI JSON. Add this test next to existing endpoint documentation tests:

```kotlin
test("openapi docs expose google admob ssv callback") {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.paths['/api/v1/ads/google/ssv']").exists())
}
```

- [ ] **Step 2: Run focused test set**

```powershell
.\gradlew.bat test --tests "*GoogleAdSsv*Test" --tests "*GoogleAdPublicKeyClientTest" --tests "*OpenApiDocumentationTest"
```

Expected: PASS.

- [ ] **Step 3: Run full backend tests**

```powershell
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/OpenApiDocumentationTest.kt
git commit -m "test(ad): document google ssv callback"
```

## Final Acceptance Checklist

- [ ] `GET /api/v1/ads/google/ssv` is public without JWT.
- [ ] No other `/api/v1/ads/**` path is public.
- [ ] Valid Google SSV callbacks store one `google_ad_ssv_events` row.
- [ ] Duplicate `transaction_id` callbacks return `200 OK` without a second row.
- [ ] Invalid callbacks return `400` and are not stored.
- [ ] Public key or DB transient failures return `500`.
- [ ] `user_points` is not modified anywhere in this feature.
- [ ] Android code is not modified.
- [ ] The design note about future server-issued nonce/attempt security remains in the spec.
