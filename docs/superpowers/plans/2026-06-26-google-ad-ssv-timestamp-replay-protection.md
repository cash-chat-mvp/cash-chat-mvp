# Google Ad SSV timestamp 재생공격 방어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SSV 콜백의 `timestamp`가 허용 시간 윈도우 밖이면 적립하지 않게 하여 재생공격(replay) 창을 닫는다.

**Architecture:** 신선도 윈도우(과거 1h / 미래 5m)를 `GoogleAdSsvProperties`에 설정으로 캡슐화하고, `GoogleAdSsvService.verifyAndStore`가 서명·ad_unit 검증에 이어 timestamp 신선도를 검사한다. 윈도우 밖이면 기존 ad_unit 불일치와 동일하게 저장 없이 200을 반환한다(WARN 로그). 현재 시각은 컨트롤러가 단일 `Instant`로 만들어 두 서비스 호출에 전달한다.

**Tech Stack:** Kotlin, Spring Boot, Kotest(FunSpec), mockito-kotlin, Jakarta Validation.

## Global Constraints

- 패키지: `com.wnl.cashchat.api.domain.ad`
- 설정 prefix: `app.ads.google`
- 윈도우 초과 콜백은 **저장하지 않고** 200 반환(`newlyStored=false`) + WARN 로그.
- 검증 순서: 서명(보안 경계) → ad_unit 허용목록 → timestamp 신선도.
- 기본값: `timestamp-tolerance=1h`(과거), `timestamp-future-skew=5m`(미래). 둘 다 `@PositiveDuration`.
- 경계값 포함(`isBefore`/`isAfter` 사용 → 경계 시각은 신선).
- 테스트 timestamp 기준값: `1710000000123L` (기존 테스트와 동일).
- 커밋 메시지: Conventional Commits, scope `ad`, 본문에 `cc-368`. 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

### Task 1: Properties — 신선도 윈도우 설정 + 판정 헬퍼

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties/GoogleAdSsvProperties.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/GoogleAdSsvPropertiesTest.kt`

**Interfaces:**
- Produces:
  - `GoogleAdSsvProperties.timestampTolerance: Duration` (기본 `Duration.ofHours(1)`)
  - `GoogleAdSsvProperties.timestampFutureSkew: Duration` (기본 `Duration.ofMinutes(5)`)
  - `GoogleAdSsvProperties.isTimestampFresh(timestampMillis: Long, now: Instant): Boolean`

- [ ] **Step 1: Write the failing tests**

`GoogleAdSsvPropertiesTest.kt`에 import 추가(파일 상단 import 블록):

```kotlin
import java.time.Instant
```

그리고 `test("uses Google SSV defaults")` 블록 안 마지막 줄 다음에 기본값 단언을 추가한다:

```kotlin
        properties.timestampTolerance shouldBe Duration.ofHours(1)
        properties.timestampFutureSkew shouldBe Duration.ofMinutes(5)
```

스펙(`{ ... })`) 닫기 직전에 새 테스트들을 추가한다:

```kotlin
    test("accepts a timestamp inside the freshness window") {
        val properties = GoogleAdSsvProperties()
        val now = Instant.ofEpochMilli(1710000000123L)

        properties.isTimestampFresh(now.toEpochMilli(), now) shouldBe true
        // 과거 경계(정확히 tolerance 만큼 이전) 포함
        properties.isTimestampFresh(now.minus(Duration.ofHours(1)).toEpochMilli(), now) shouldBe true
        // 미래 경계(정확히 future-skew 만큼 이후) 포함
        properties.isTimestampFresh(now.plus(Duration.ofMinutes(5)).toEpochMilli(), now) shouldBe true
    }

    test("rejects a timestamp older than the tolerance") {
        val properties = GoogleAdSsvProperties()
        val now = Instant.ofEpochMilli(1710000000123L)

        properties.isTimestampFresh(now.minus(Duration.ofHours(1).plusMillis(1)).toEpochMilli(), now) shouldBe false
    }

    test("rejects a timestamp further in the future than the skew allowance") {
        val properties = GoogleAdSsvProperties()
        val now = Instant.ofEpochMilli(1710000000123L)

        properties.isTimestampFresh(now.plus(Duration.ofMinutes(5).plusMillis(1)).toEpochMilli(), now) shouldBe false
    }

    test("rejects non-positive freshness window durations") {
        val validator = Validation.buildDefaultValidatorFactory().validator

        val violations = validator.validate(
            GoogleAdSsvProperties(
                timestampTolerance = Duration.ZERO,
                timestampFutureSkew = Duration.ofMinutes(-1),
            ),
        )

        violations.map { it.propertyPath.toString() } shouldContain "timestampTolerance"
        violations.map { it.propertyPath.toString() } shouldContain "timestampFutureSkew"
    }
```

> 참고: 위 테스트는 `shouldBe`/`shouldContain`만 쓴다(둘 다 기존 파일에 이미 import 됨). 새로 추가할 import 는 `java.time.Instant` 뿐이다.

- [ ] **Step 2: Run tests to verify they fail**

Run: `apps/backend/gradlew.bat -p apps/backend test --tests "com.wnl.cashchat.api.config.GoogleAdSsvPropertiesTest"`
Expected: 컴파일 실패 또는 FAIL — `timestampTolerance`/`isTimestampFresh` 미정의.

- [ ] **Step 3: Implement the properties**

`GoogleAdSsvProperties.kt` 전체를 아래로 교체:

```kotlin
package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.time.Instant

@Validated
@ConfigurationProperties(prefix = "app.ads.google")
data class GoogleAdSsvProperties(
    @field:NotBlank
    val ssvPublicKeysUri: String = "https://www.gstatic.com/admob/reward/verifier-keys.json",

    @field:PositiveDuration
    @field:MaxDuration(hours = 24)
    val publicKeyCacheTtl: Duration = Duration.ofHours(24),

    // Android·iOS 는 각각 별도의 보상형 광고 단위를 사용하므로 복수 ID 를 허용한다(콤마 구분 바인딩).
    // 비어 있으면 ad_unit 검증을 건너뛴다.
    val rewardedAdUnitIds: List<String> = emptyList(),

    // 콜백 timestamp 가 현재 시각 기준 이 만큼 과거보다 더 오래면 재생(replay)으로 보고 미적립한다.
    @field:PositiveDuration
    val timestampTolerance: Duration = Duration.ofHours(1),

    // 서버-구글 시계 오차 허용. 콜백 timestamp 가 이 만큼보다 더 미래면 미적립한다.
    @field:PositiveDuration
    val timestampFutureSkew: Duration = Duration.ofMinutes(5),
) {
    private val allowedAdUnitIds: Set<String> = rewardedAdUnitIds.filter { it.isNotBlank() }.toSet()

    fun isRewardedAdUnitValidationEnabled(): Boolean = allowedAdUnitIds.isNotEmpty()

    fun isAllowedAdUnit(adUnit: String): Boolean = adUnit in allowedAdUnitIds

    fun isTimestampFresh(timestampMillis: Long, now: Instant): Boolean {
        val eventTime = Instant.ofEpochMilli(timestampMillis)
        return !eventTime.isBefore(now.minus(timestampTolerance)) &&
            !eventTime.isAfter(now.plus(timestampFutureSkew))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `apps/backend/gradlew.bat -p apps/backend test --tests "com.wnl.cashchat.api.config.GoogleAdSsvPropertiesTest"`
Expected: PASS (모든 properties 테스트).

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties/GoogleAdSsvProperties.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/config/GoogleAdSsvPropertiesTest.kt
git commit -m "feat(ad): cc-368 ssv timestamp 신선도 윈도우 설정 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Service + Controller — 신선도 게이트 & 현재 시각 주입

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvControllerTest.kt`

**Interfaces:**
- Consumes: `GoogleAdSsvProperties.isTimestampFresh(Long, Instant)` (Task 1).
- Produces:
  - `GoogleAdSsvService.verifyAndStore(rawQueryString: String?, now: Instant): GoogleAdSsvVerificationResult` (시그니처 변경 — `now` 파라미터 신규).

- [ ] **Step 1: Update the failing tests**

**(a) `GoogleAdSsvServiceTest.kt`** — 스펙 람다 본문 최상단(`val nonceUserId = ...` 위)에 import 와 고정 시각을 추가한다.

import 블록에 추가:
```kotlin
import java.time.Duration
import java.time.Instant
```

`class GoogleAdSsvServiceTest : FunSpec({` 바로 다음 줄에 추가:
```kotlin
    // 콜백 timestamp(1710000000123L)와 같은 시각 → 기본 윈도우 안에서 신선.
    val now = Instant.ofEpochMilli(1710000000123L)
```

그리고 이 파일의 **모든** `service.verifyAndStore(rawQuery)` 호출을 `service.verifyAndStore(rawQuery, now)` 로,
`service.verifyAndStore(null)` 을 `service.verifyAndStore(null, now)` 로,
`service.verifyAndStore("   ")` 을 `service.verifyAndStore("   ", now)` 로 바꾼다.

스펙 닫기 직전에 신선도 테스트 2개를 추가한다:
```kotlin
    test("stale timestamp is verified but accepted without storing") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        val service = service(parser, signatureVerifier, repository)
        // 이벤트 시각보다 2시간 뒤 → 과거 tolerance(1h) 초과.
        val later = Instant.ofEpochMilli(callback.timestamp).plus(Duration.ofHours(2))

        val result = service.verifyAndStore(rawQuery, later)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(repository, never()).findByTransactionId(any())
    }

    test("timestamp too far in the future is accepted without storing") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        val service = service(parser, signatureVerifier, repository)
        // 현재 시각이 이벤트 시각보다 10분 전 → 이벤트가 미래 skew(5m) 초과.
        val earlier = Instant.ofEpochMilli(callback.timestamp).minus(Duration.ofMinutes(10))

        val result = service.verifyAndStore(rawQuery, earlier)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }
```

**(b) `GoogleAdSsvControllerTest.kt`** — mock 스텁/검증을 새 시그니처로 바꾼다.

import 블록에 추가:
```kotlin
import org.mockito.kotlin.argumentCaptor
import io.kotest.matchers.shouldBe
import java.time.Instant
```

`test("google ssv callback is public and passes raw query string to service")` 안:
- `whenever(googleAdSsvService.verifyAndStore(rawQuery))` → `whenever(googleAdSsvService.verifyAndStore(eq(rawQuery), any()))`
- `verify(googleAdSsvService).verifyAndStore(rawQuery)` → `verify(googleAdSsvService).verifyAndStore(eq(rawQuery), any())`

`test("newly stored ssv callback triggers reward granting")` 안:
- `whenever(googleAdSsvService.verifyAndStore(rawQuery))` → `whenever(googleAdSsvService.verifyAndStore(eq(rawQuery), any()))`
- `verify(adRewardService).grantFromCallback(eq(callback), any())` 직후에 "동일 now" 단언을 추가:
```kotlin
            val verifyNow = argumentCaptor<Instant>()
            val grantNow = argumentCaptor<Instant>()
            verify(googleAdSsvService).verifyAndStore(eq(rawQuery), verifyNow.capture())
            verify(adRewardService).grantFromCallback(eq(callback), grantNow.capture())
            grantNow.firstValue shouldBe verifyNow.firstValue
```
(기존 `verify(adRewardService).grantFromCallback(eq(callback), any())` 줄은 위 블록으로 대체한다.)

`test("google ssv callback maps invalid callback to bad request")` 와
`test("google ssv callback maps transient verification failure to service unavailable")` 안:
- `.verifyAndStore(rawQuery)` → `.verifyAndStore(eq(rawQuery), any())`

- [ ] **Step 2: Run tests to verify they fail**

Run: `apps/backend/gradlew.bat -p apps/backend test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvServiceTest" --tests "com.wnl.cashchat.api.domain.ad.web.controller.GoogleAdSsvControllerTest"`
Expected: 컴파일 실패 — `verifyAndStore`가 아직 `now` 파라미터를 받지 않음.

- [ ] **Step 3: Implement service + controller**

`GoogleAdSsvService.kt` — import 에 `import java.time.Instant` 추가. `verifyAndStore` 시그니처와 본문을 아래처럼 변경(서명 검증 → ad_unit → timestamp 순서):

```kotlin
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun verifyAndStore(rawQueryString: String?, now: Instant): GoogleAdSsvVerificationResult {
        // 공개 진입점에서 fail-fast: null/blank 는 파서 구현에 의존하지 않고 여기서 명확히 거절한다.
        if (rawQueryString.isNullOrBlank()) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV raw query string is null or blank")
        }
        val callback = parser.parse(rawQueryString)
        // 서명 검증을 가장 먼저 수행한다(보안 경계).
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        // ad_unit 불일치는 '수신하되 적립하지 않음(200)'(미저장).
        if (!isAdUnitMatched(callback)) {
            logger.warn(
                "Google Ad SSV ad_unit mismatch — accepted without crediting (callback ad_unit={}, configured={})",
                callback.adUnit,
                properties.rewardedAdUnitIds,
            )
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

        // timestamp 신선도 — 윈도우 밖이면 재생/지연 콜백으로 보고 '수신하되 적립하지 않음(200)'(미저장).
        if (!properties.isTimestampFresh(callback.timestamp, now)) {
            logger.warn(
                "Google Ad SSV timestamp outside freshness window — accepted without crediting " +
                    "(callback timestamp={}, now={}, tolerance={}, futureSkew={})",
                callback.timestamp,
                now.toEpochMilli(),
                properties.timestampTolerance,
                properties.timestampFutureSkew,
            )
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

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
                logger.error(
                    "Unexpected DataIntegrityViolationException for Google Ad SSV transaction {}",
                    callback.transactionId,
                    exception,
                )
                throw exception
            }
        }
    }
```

`GoogleAdSsvController.kt` — import 에 `import java.time.Instant`가 이미 있다(기존 사용). `verify` 메서드를 변경:

```kotlin
    fun verify(request: HttpServletRequest): ResponseEntity<Void> {
        // 검증과 적립이 동일한 '현재 시각'을 보도록 한 번만 만들어 두 호출에 전달한다.
        val now = Instant.now()
        val result = googleAdSsvService.verifyAndStore(request.queryString, now)
        // 모든 검증된 콜백에 대해 적립을 시도한다. grantFromCallback 은 이미 GRANTED 된 이벤트를 멱등하게 건너뛴다.
        adRewardService.grantFromCallback(result.callback, now)
        return ResponseEntity.ok().build()
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `apps/backend/gradlew.bat -p apps/backend test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvServiceTest" --tests "com.wnl.cashchat.api.domain.ad.web.controller.GoogleAdSsvControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvControllerTest.kt
git commit -m "feat(ad): cc-368 ssv timestamp 신선도 검증으로 재생공격 방어

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 설정 노출 + 운영자 문서

**Files:**
- Modify: `apps/backend/src/main/resources/application.yaml`
- Modify: `apps/backend/src/main/resources/application-prod.yaml`
- Modify: `apps/backend/.env.example`
- Modify: `docs/features/google-ad-ssv/manual.md`
- Modify: `docs/features/google-ad-ssv/architecture.md`

> 기본값이 코드에 있으므로 prod 필수 아님 — 가시성 목적으로만 노출. `infra`·CI 는 기본값으로 동작하므로 본 작업에서 건드리지 않는다(추가 시크릿 불필요).

- [ ] **Step 1: application.yaml 에 노출**

`app.ads.google` 블록의 `rewarded-ad-unit-ids:` 줄 아래에 추가:

```yaml
      # 콜백 timestamp 신선도 윈도우. 과거 tolerance/미래 skew 밖이면 미적립(재생공격 방어).
      timestamp-tolerance: ${APP_ADS_GOOGLE_TIMESTAMP_TOLERANCE:1h}
      timestamp-future-skew: ${APP_ADS_GOOGLE_TIMESTAMP_FUTURE_SKEW:5m}
```

- [ ] **Step 2: application-prod.yaml 에 노출(선택, 기본값 유지)**

`app.ads.google` 블록의 `rewarded-ad-unit-ids:` 줄 아래에 추가:

```yaml
      timestamp-tolerance: ${APP_ADS_GOOGLE_TIMESTAMP_TOLERANCE:1h}
      timestamp-future-skew: ${APP_ADS_GOOGLE_TIMESTAMP_FUTURE_SKEW:5m}
```

- [ ] **Step 3: `.env.example` 에 문서화**

`APP_ADS_GOOGLE_REWARDED_AD_UNIT_IDS=` 줄 아래에 추가:

```bash
# 콜백 timestamp 신선도 윈도우(미설정 시 과거 1h / 미래 5m)
APP_ADS_GOOGLE_TIMESTAMP_TOLERANCE=1h
APP_ADS_GOOGLE_TIMESTAMP_FUTURE_SKEW=5m
```

- [ ] **Step 4: manual.md 설정표에 행 추가**

설정 표(`| app.ads.google.public-key-cache-ttl | ... |` 행이 있는 표)의 `public-key-cache-ttl` 행 아래에 추가:

```markdown
| `app.ads.google.timestamp-tolerance` | `APP_ADS_GOOGLE_TIMESTAMP_TOLERANCE` | `1h` | 콜백 timestamp 과거 허용폭(초과 시 미적립) |
| `app.ads.google.timestamp-future-skew` | `APP_ADS_GOOGLE_TIMESTAMP_FUTURE_SKEW` | `5m` | 콜백 timestamp 미래 허용폭(시계 오차) |
```

manual.md 트러블슈팅 표의 "200인데 미적립" 행 아래에 추가:

```markdown
| 콜백 **200**인데 미적립 | `timestamp`가 신선도 윈도우(과거 1h/미래 5m) 밖 | 서버 시계(NTP) 동기화 확인, 필요 시 윈도우 조정 |
```

- [ ] **Step 5: architecture.md 보안 설계에 한 줄 추가**

"광고단위 검증(선택)" 불릿 아래에 추가:

```markdown
- **timestamp 신선도** — 콜백 `timestamp`가 `app.ads.google.timestamp-tolerance`(과거)/`timestamp-future-skew`(미래) 윈도우 밖이면 적립하지 않는다(200, 미저장). transaction_id 멱등성을 보완하는 재생공격 심층 방어.
```

- [ ] **Step 6: 빌드로 바인딩 확인 후 commit**

Run: `apps/backend/gradlew.bat -p apps/backend test --tests "com.wnl.cashchat.api.config.GoogleAdSsvPropertiesTest"`
Expected: PASS (yaml 변경이 바인딩을 깨지 않음).

```bash
git add apps/backend/src/main/resources/application.yaml \
        apps/backend/src/main/resources/application-prod.yaml \
        apps/backend/.env.example \
        docs/features/google-ad-ssv/manual.md \
        docs/features/google-ad-ssv/architecture.md
git commit -m "docs(ad): cc-368 ssv timestamp 윈도우 설정 노출 및 운영 문서 갱신

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 최종 검증

- [ ] 전체 ad 도메인 테스트: `apps/backend/gradlew.bat -p apps/backend test --tests "com.wnl.cashchat.api.domain.ad.*" --tests "com.wnl.cashchat.api.config.GoogleAdSsvPropertiesTest"`
- [ ] 남은 작업(코드 밖): 운영에서 윈도우를 바꾸려면 `APP_ADS_GOOGLE_TIMESTAMP_*` env 설정. 미설정 시 기본값 동작.
