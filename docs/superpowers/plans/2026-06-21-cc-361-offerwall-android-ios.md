# TNK 오퍼월 Android/iOS 플랫폼 분리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 단일 앱 전제의 TNK 오퍼월 콜백 처리를, 안드로이드/iOS 플랫폼별 앱키·콜백 URL을 따로 다루는 platform-aware 구조로 확장한다.

**Architecture:** TNK는 앱(Android/iOS)을 별도 등록해 앱키·콜백 URL이 다르다. 콜백 경로 `/api/offerwall/tnk/callback/{platform}`로 플랫폼을 식별하고, **서명검증 이전에** 플랫폼이 확정되어 해당 플랫폼 앱키로 `md_chk`를 검증한다. 멱등성 단위와 콜백 원장을 `(platform, seq_id)`로 확장해 같은 `seq_id`가 두 플랫폼에서 와도 독립 적립한다. 기존 무플랫폼 엔드포인트·단일 키는 깔끔히 제거(하위호환 미유지).

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway(MySQL), Spring Security, Kotest + TestContainers(MySQL 8.4), Mockito-Kotlin.

## Global Constraints

- 백엔드 패키지 루트: `com.wnl.cashchat.api`. 도메인: `domain/offerwall/`.
- 서명검증은 항상 DB 쓰기보다 먼저 수행한다(미검증 public 요청이 원장 행을 만들지 못하게). 서명 실패는 로그만 남기고 미기록.
- 앱키 미설정(빈 문자열) → 해당 플랫폼 콜백 전부 서명 실패로 거절(fail-closed). 부팅은 실패하지 않는다.
- 적립은 단일 `@Transactional` 안에서 서명검증 → 멱등 INSERT(PENDING) → 행 락 → 토큰 해석 → 환산 적립(멱등키) → status 갱신을 원자적으로 수행한다.
- 처리된 콜백(적립/거절/중복)에는 ack 본문(기본 `"SUCCESS"`)을 200으로 반환해 재전송 폭주를 막는다.
- 엔티티는 `BaseEntity`를 상속하고 `@Enumerated(EnumType.STRING)`을 사용한다. 마이그레이션은 MySQL 8 기준(통합 테스트는 실제 MySQL 8.4 컨테이너로 검증).
- 커밋 메시지는 Conventional Commits. **subject는 영문 대문자/단어로 시작하면 commitlint `subject-case` 규칙에 걸린다 — 한글로 시작하거나 소문자로 시작할 것.** (예: `feat(offerwall): 오퍼월 ...`).
- 코드 주석/사용자 메시지는 기존 코드와 동일하게 한국어 사용.

## File Structure

**신규 생성:**
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatform.kt` — 플랫폼 enum + 경로값 파싱.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/exception/UnknownOfferwallPlatformException.kt` — 알 수 없는 플랫폼 경로값 도메인 예외.
- `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/exception/OfferwallExceptionHandler.kt` — 위 예외를 400으로 변환.
- `apps/backend/src/main/resources/db/migration/V12__tnk_offerwall_platform.sql` — `platform` 컬럼 추가 + 유니크 제약 교체.
- `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatformTest.kt` — enum 파싱 단위 테스트.

**수정:**
- `.../offerwall/properties/TnkOfferwallProperties.kt` — `appKey` → `android`/`ios` 중첩 + `appKeyFor(platform)`.
- `.../offerwall/service/TnkMdChecksumVerifier.kt` — `isValid(platform, params)`.
- `.../offerwall/persistence/entity/TnkOfferwallCallback.kt` — `platform` 필드 + 유니크 제약명 변경.
- `.../offerwall/persistence/repository/TnkOfferwallCallbackRepository.kt` — `insertIfAbsent(platform, ...)`, `findByPlatformAndSeqId`, `findForUpdate(platform, seqId)`.
- `.../offerwall/service/TnkOfferwallService.kt` — `handleCallback(platform, params, now)` + 플랫폼 관통 + 멱등키에 플랫폼 포함.
- `.../offerwall/web/controller/OfferwallController.kt` — `POST /callback/{platform}`.
- `.../common/security/config/SecurityConfig.kt` — `/api/offerwall/tnk/callback` → `/api/offerwall/tnk/callback/*`.
- `apps/backend/src/main/resources/application.yaml`, `application-prod.yaml` — `app.offerwall.tnk.app-key` → `android.app-key`/`ios.app-key`.
- 기존 테스트(시그니처/프로퍼티 변경 반영): `TnkMdChecksumVerifierTest.kt`, `TnkOfferwallServiceTest.kt`, `TnkOfferwallServiceIntegrationTest.kt`, `OfferwallCallbackControllerTest.kt`, `OfferwallMigrationIntegrationTest.kt`.

**참고:** `TnkOfferwallCoinConversionTest.kt`(자유 함수 `toCoinAmount` 테스트)와 `OfferwallUserTokenServiceIntegrationTest.kt`(토큰 전용)는 변경 불필요.

---

## Task 1: OfferwallPlatform enum + 예외

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/exception/UnknownOfferwallPlatformException.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatform.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatformTest.kt`

**Interfaces:**
- Produces:
  - `enum class OfferwallPlatform { ANDROID, IOS }` with `companion object { fun from(raw: String): OfferwallPlatform }` — 대소문자 무시 파싱, 미일치 시 `UnknownOfferwallPlatformException` throw.
  - `class UnknownOfferwallPlatformException(val raw: String) : RuntimeException`.

- [ ] **Step 1: 예외 클래스 작성**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/exception/UnknownOfferwallPlatformException.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.exception

/** 콜백 경로의 {platform} 값이 ANDROID/IOS 중 어느 것에도 해당하지 않을 때. 400 으로 변환된다. */
class UnknownOfferwallPlatformException(val raw: String) :
    RuntimeException("Unknown offerwall platform: $raw")
```

- [ ] **Step 2: 실패하는 enum 테스트 작성**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatformTest.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.entity

import com.wnl.cashchat.api.domain.offerwall.exception.UnknownOfferwallPlatformException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OfferwallPlatformTest : FunSpec({
    test("from parses lowercase path values") {
        OfferwallPlatform.from("android") shouldBe OfferwallPlatform.ANDROID
        OfferwallPlatform.from("ios") shouldBe OfferwallPlatform.IOS
    }

    test("from is case-insensitive") {
        OfferwallPlatform.from("Android") shouldBe OfferwallPlatform.ANDROID
        OfferwallPlatform.from("IOS") shouldBe OfferwallPlatform.IOS
    }

    test("from throws on unknown platform") {
        val ex = shouldThrow<UnknownOfferwallPlatformException> { OfferwallPlatform.from("web") }
        ex.raw shouldBe "web"
    }
})
```

- [ ] **Step 3: 테스트 실패 확인 (컴파일 실패 = OfferwallPlatform 미정의)**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatformTest"`
Expected: FAIL — `OfferwallPlatform` 미해결(unresolved reference).

- [ ] **Step 4: enum 구현**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatform.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.entity

import com.wnl.cashchat.api.domain.offerwall.exception.UnknownOfferwallPlatformException

/**
 * TNK 오퍼월 앱 플랫폼. TNK 는 안드로이드/iOS 를 별도 앱으로 등록해 앱키·콜백 URL 이 플랫폼마다 다르다.
 * 콜백 경로 /api/offerwall/tnk/callback/{platform} 의 마지막 세그먼트로 식별한다.
 */
enum class OfferwallPlatform {
    ANDROID,
    IOS;

    companion object {
        /** 경로값("android"/"ios", 대소문자 무시)을 enum 으로. 미일치 시 도메인 예외(→ 400). */
        fun from(raw: String): OfferwallPlatform =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw UnknownOfferwallPlatformException(raw)
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatformTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/exception/UnknownOfferwallPlatformException.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatform.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/OfferwallPlatformTest.kt
git commit -m "feat(offerwall): 오퍼월 플랫폼 enum 및 예외 추가"
```

---

## Task 2: 플랫폼 인지 콜백 처리 (end-to-end)

> **왜 한 태스크인가:** properties→verifier→service→entity→repository→controller→security 가 시그니처로 맞물려 있어(Kotlin 모듈 단위 컴파일), 부분 변경으로는 빌드/기존 테스트가 깨진다. 하나의 컴파일-그린 단위로 묶는다. 아래는 "기존 테스트를 새 설계로 수정(red) → 프로덕션 구현 → 신규 동작 테스트 추가 → 전체 그린" 순서다.

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/properties/TnkOfferwallProperties.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifier.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/entity/TnkOfferwallCallback.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/repository/TnkOfferwallCallbackRepository.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallService.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallController.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt`
- Modify: `apps/backend/src/main/resources/application.yaml`
- Modify: `apps/backend/src/main/resources/application-prod.yaml`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/exception/OfferwallExceptionHandler.kt`
- Create: `apps/backend/src/main/resources/db/migration/V12__tnk_offerwall_platform.sql`
- Modify (tests): `TnkMdChecksumVerifierTest.kt`, `TnkOfferwallServiceTest.kt`, `TnkOfferwallServiceIntegrationTest.kt`, `OfferwallCallbackControllerTest.kt`, `OfferwallMigrationIntegrationTest.kt`

**Interfaces:**
- Consumes (Task 1): `OfferwallPlatform`, `OfferwallPlatform.from(String)`, `UnknownOfferwallPlatformException`.
- Produces:
  - `TnkOfferwallProperties(android: Platform, ios: Platform, pointToCoinRatio: Double, ack: Ack)`, `Platform(appKey: String)`, `fun appKeyFor(platform: OfferwallPlatform): String`.
  - `TnkMdChecksumVerifier.isValid(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams): Boolean`.
  - `TnkOfferwallCallback(id, platform: OfferwallPlatform, seqId, mdUserNm, payPnt, rawQuery)` + 읽기전용 `platform`.
  - `TnkOfferwallCallbackRepository.insertIfAbsent(platform: String, seqId, mdUserNm, payPnt, rawQuery): Int`, `findByPlatformAndSeqId(platform: OfferwallPlatform, seqId: String): TnkOfferwallCallback?`, `findForUpdate(platform: OfferwallPlatform, seqId: String): TnkOfferwallCallback?`.
  - `TnkOfferwallService.handleCallback(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams, now: Instant): TnkOfferwallStatus`.
  - 멱등키 포맷: `tnk:offerwall:${platform.name.lowercase()}:${seqId}` (예: `tnk:offerwall:android:s10`).
  - 엔드포인트: `POST /api/offerwall/tnk/callback/{platform}`.

### A. 기존 테스트를 새 설계로 수정 (이 단계 후 모듈은 컴파일 실패 = red)

- [ ] **Step 1: TnkMdChecksumVerifierTest 를 플랫폼 인지로 수정**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkMdChecksumVerifierTest.kt` 전체 교체:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.security.MessageDigest

class TnkMdChecksumVerifierTest : FunSpec({
    val androidKey = "android-secret"
    val iosKey = "ios-secret"
    val verifier = TnkMdChecksumVerifier(
        TnkOfferwallProperties(
            android = TnkOfferwallProperties.Platform(appKey = androidKey),
            ios = TnkOfferwallProperties.Platform(appKey = iosKey),
        ),
    )

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    fun params(seqId: String, mdUserNm: String, mdChk: String) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = 100, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = "raw")

    test("valid android md_chk passes with android key") {
        val expected = md5Hex(androidKey + "user-token" + "seq-1")
        verifier.isValid(OfferwallPlatform.ANDROID, params("seq-1", "user-token", expected)) shouldBe true
    }

    test("valid ios md_chk passes with ios key") {
        val expected = md5Hex(iosKey + "user-token" + "seq-1")
        verifier.isValid(OfferwallPlatform.IOS, params("seq-1", "user-token", expected)) shouldBe true
    }

    test("valid md_chk passes regardless of case") {
        val expected = md5Hex(androidKey + "user-token" + "seq-1").uppercase()
        verifier.isValid(OfferwallPlatform.ANDROID, params("seq-1", "user-token", expected)) shouldBe true
    }

    test("android md_chk fails when verified against ios platform (cross-platform)") {
        // 안드로이드 키로 서명한 값을 iOS 플랫폼(=iOS 키)으로 검증하면 실패해야 한다.
        val androidSigned = md5Hex(androidKey + "user-token" + "seq-1")
        verifier.isValid(OfferwallPlatform.IOS, params("seq-1", "user-token", androidSigned)) shouldBe false
    }

    test("wrong md_chk fails") {
        verifier.isValid(OfferwallPlatform.ANDROID, params("seq-1", "user-token", "deadbeef")) shouldBe false
    }

    test("blank appKey rejects that platform (fail-closed)") {
        val verifierWithBlankIos = TnkMdChecksumVerifier(
            TnkOfferwallProperties(
                android = TnkOfferwallProperties.Platform(appKey = androidKey),
                ios = TnkOfferwallProperties.Platform(appKey = ""),
            ),
        )
        val attackerHash = md5Hex("" + "user-token" + "seq-1")
        verifierWithBlankIos.isValid(OfferwallPlatform.IOS, params("seq-1", "user-token", attackerHash)) shouldBe false
    }
})
```

- [ ] **Step 2: TnkOfferwallServiceTest 를 플랫폼 인지로 수정**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallServiceTest.kt` 변경점:
1. import 추가: `import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform`
2. `verifier.isValid(any())` → `verifier.isValid(any(), any())`
3. `callbackRepository.findForUpdate("s9")` → `callbackRepository.findForUpdate(OfferwallPlatform.ANDROID, "s9")` (s10 동일)
4. `TnkOfferwallCallback(seqId = ..., ...)` 생성 시 `platform = OfferwallPlatform.ANDROID` 추가
5. `service.handleCallback(params(...), now)` → `service.handleCallback(OfferwallPlatform.ANDROID, params(...), now)`
6. 멱등키 검증 `eq("tnk:offerwall:s10")` → `eq("tnk:offerwall:android:s10")`

수정 후 전체:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallCallback
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
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

class TnkOfferwallServiceTest : FunSpec({
    lateinit var callbackRepository: TnkOfferwallCallbackRepository
    lateinit var verifier: TnkMdChecksumVerifier
    lateinit var tokenService: OfferwallUserTokenService
    lateinit var userPointService: UserPointService
    lateinit var service: TnkOfferwallService

    val now = Instant.parse("2026-06-17T00:00:00Z")

    fun params(seqId: String, token: String, payPnt: Long) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = token, mdChk = "chk", rawQuery = "raw")

    fun build(ratio: Double) {
        callbackRepository = mock()
        verifier = mock()
        tokenService = mock()
        userPointService = mock()
        service = TnkOfferwallService(
            callbackRepository, verifier, tokenService, userPointService,
            TnkOfferwallProperties(pointToCoinRatio = ratio),
        )
    }

    test("callback converting to zero coins grants without recording a point transaction") {
        build(ratio = 0.5)
        val callback = TnkOfferwallCallback(platform = OfferwallPlatform.ANDROID, seqId = "s9", mdUserNm = "tok", payPnt = 1, rawQuery = "raw")
        whenever(verifier.isValid(any(), any())).thenReturn(true)
        whenever(callbackRepository.findForUpdate(OfferwallPlatform.ANDROID, "s9")).thenReturn(callback)
        whenever(tokenService.resolveUserId("tok")).thenReturn(7L)

        val status = service.handleCallback(OfferwallPlatform.ANDROID, params("s9", "tok", 1), now)

        status shouldBe TnkOfferwallStatus.GRANTED
        callback.status shouldBe TnkOfferwallStatus.GRANTED
        callback.coinAmount shouldBe 0L
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }

    test("callback converting to positive coins records the transaction with platform-scoped idempotency key") {
        build(ratio = 0.5)
        val callback = TnkOfferwallCallback(platform = OfferwallPlatform.ANDROID, seqId = "s10", mdUserNm = "tok", payPnt = 10, rawQuery = "raw")
        whenever(verifier.isValid(any(), any())).thenReturn(true)
        whenever(callbackRepository.findForUpdate(OfferwallPlatform.ANDROID, "s10")).thenReturn(callback)
        whenever(tokenService.resolveUserId("tok")).thenReturn(7L)

        val status = service.handleCallback(OfferwallPlatform.ANDROID, params("s10", "tok", 10), now)

        status shouldBe TnkOfferwallStatus.GRANTED
        callback.coinAmount shouldBe 5L
        verify(userPointService).recordTransaction(eq(7L), eq(5L), eq(PointTransactionReason.OFFERWALL), eq("tnk:offerwall:android:s10"))
    }
})
```

- [ ] **Step 3: OfferwallCallbackControllerTest 를 경로 기반으로 수정 + 미지 플랫폼 400 추가**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/web/controller/OfferwallCallbackControllerTest.kt` 변경점:
1. `@MockitoBean` 으로 `tnkOfferwallService.handleCallback(any(), any())` → `handleCallback(any(), any(), any())` (인자 3개)
2. POST 경로 `/api/offerwall/tnk/callback` → `/api/offerwall/tnk/callback/android`
3. `verify(...).handleCallback(argThat {...}, any())` → 플랫폼 인자 포함 `handleCallback(eq(OfferwallPlatform.ANDROID), argThat {...}, any())`
4. 미지 플랫폼 경로 → 400 테스트 추가 (`OfferwallExceptionHandler` 는 `@WebMvcTest` 의 `@RestControllerAdvice` 자동 스캔으로 적용됨)

수정 후 전체:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallCallbackParams
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
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
    @MockitoBean private lateinit var tnkOfferwallProperties: TnkOfferwallProperties
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    init {
        beforeTest {
            whenever(tnkOfferwallProperties.ack).thenReturn(TnkOfferwallProperties.Ack())
        }

        test("android callback is public, passes platform and params to service, returns SUCCESS ack") {
            whenever(tnkOfferwallService.handleCallback(any(), any(), any())).thenReturn(TnkOfferwallStatus.GRANTED)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback/android")
                    .param("seq_id", "seq-1")
                    .param("pay_pnt", "1500")
                    .param("md_user_nm", "tok-1")
                    .param("md_chk", "hash-1")
            )
                .andExpect(status().isOk)
                .andExpect(content().string("SUCCESS"))

            verify(tnkOfferwallService).handleCallback(
                eq(OfferwallPlatform.ANDROID),
                argThat<TnkOfferwallCallbackParams> {
                    seqId == "seq-1" && payPnt == 1500L && mdUserNm == "tok-1" && mdChk == "hash-1"
                },
                any(),
            )
        }

        test("ios callback routes to IOS platform") {
            whenever(tnkOfferwallService.handleCallback(any(), any(), any())).thenReturn(TnkOfferwallStatus.GRANTED)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback/ios")
                    .param("seq_id", "seq-9")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-9")
                    .param("md_chk", "hash-9")
            )
                .andExpect(status().isOk)

            verify(tnkOfferwallService).handleCallback(eq(OfferwallPlatform.IOS), any(), any())
        }

        test("callback returns SUCCESS ack even when rejected (no retry storm)") {
            whenever(tnkOfferwallService.handleCallback(any(), any(), any()))
                .thenReturn(TnkOfferwallStatus.REJECTED_BAD_SIGNATURE)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback/android")
                    .param("seq_id", "seq-2")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-2")
                    .param("md_chk", "bad")
            )
                .andExpect(status().isOk)
                .andExpect(content().string("SUCCESS"))
        }

        test("unknown platform path returns 400") {
            mockMvc.perform(
                post("/api/offerwall/tnk/callback/web")
                    .param("seq_id", "seq-3")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-3")
                    .param("md_chk", "x")
            )
                .andExpect(status().isBadRequest)
        }
    }
}
```

- [ ] **Step 4: TnkOfferwallServiceIntegrationTest 를 플랫폼 인지로 수정 + 교차 플랫폼 독립 적립 테스트 추가**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/service/TnkOfferwallServiceIntegrationTest.kt` 변경점:
1. import 추가: `import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform`
2. `params()` 헬퍼에 `platform` 파라미터를 더해 md_chk 를 플랫폼 키로 계산 — 단, DynamicPropertySource 가 android/ios 양쪽에 동일 키(`test-app-key`)를 설정하므로 기존 호출부는 그대로 두고 `handleCallback` 호출에만 플랫폼을 추가한다.
3. 모든 `service.handleCallback(params(...), now)` → `service.handleCallback(OfferwallPlatform.ANDROID, params(...), now)`
4. 모든 `callbackRepository.findBySeqId("sX")` → `callbackRepository.findByPlatformAndSeqId(OfferwallPlatform.ANDROID, "sX")`
5. DynamicPropertySource: `app.offerwall.tnk.app-key` → `app.offerwall.tnk.android.app-key` 와 `app.offerwall.tnk.ios.app-key` 둘 다 `test-app-key` 로 설정
6. 신규 테스트: 같은 `seq_id` 가 ANDROID/IOS 양쪽에서 와도 각각 독립 적립

변경 디테일 — `companion object` 의 `configureProperties`:

```kotlin
            registry.add("app.offerwall.tnk.android.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.ios.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.point-to-coin-ratio") { "0.5" }
```

신규 테스트 추가(`init { ... }` 블록 안, 기존 테스트들 뒤):

```kotlin
        test("same seq_id on different platforms credits independently") {
            val (userId, token) = newUserWithToken("multiplat")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            // 동일 seq_id "dup-seq" 를 android, ios 양쪽으로. 멱등 단위가 (platform, seq_id) 이므로 둘 다 적립.
            service.handleCallback(OfferwallPlatform.ANDROID, params("dup-seq", token, 1000), now) shouldBe TnkOfferwallStatus.GRANTED
            service.handleCallback(OfferwallPlatform.IOS, params("dup-seq", token, 1000), now) shouldBe TnkOfferwallStatus.GRANTED

            // 1000 * 0.5 = 500 씩 두 번
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 1000L
            callbackRepository.findByPlatformAndSeqId(OfferwallPlatform.ANDROID, "dup-seq")!!.platform shouldBe OfferwallPlatform.ANDROID
            callbackRepository.findByPlatformAndSeqId(OfferwallPlatform.IOS, "dup-seq")!!.platform shouldBe OfferwallPlatform.IOS
            callbackRepository.count() shouldBe 2L
            pointTransactionRepository.count() shouldBe 2L
        }
```

> 주의: `params()` 의 md_chk 는 `md5Hex(appKey + token + seqId)` 인데 android/ios 키가 동일(`test-app-key`)하므로 양 플랫폼 모두 동일 md_chk 로 검증 통과한다. 키를 플랫폼별로 다르게 두는 별도 시나리오는 단위 테스트(Step 1)에서 이미 커버한다.

- [ ] **Step 5: OfferwallMigrationIntegrationTest 에 V12 검증 추가**

`apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/offerwall/persistence/OfferwallMigrationIntegrationTest.kt` 의 `init {}` 에 테스트 추가, 그리고 DynamicPropertySource 의 `app.offerwall.tnk.app-key` 를 `android.app-key`/`ios.app-key` 로 교체:

```kotlin
        test("V12 adds platform column to tnk_offerwall_callbacks") {
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'tnk_offerwall_callbacks' AND column_name = 'platform'",
                Int::class.java,
            )
            count shouldBe 1
        }

        test("V12 replaces seq_id unique with composite (platform, seq_id)") {
            // 단독 seq_id 유니크 인덱스는 사라지고, 복합 유니크 인덱스가 존재해야 한다.
            val composite = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                    "WHERE table_name = 'tnk_offerwall_callbacks' AND index_name = 'uk_tnk_offerwall_callbacks_platform_seq_id'",
                Int::class.java,
            )
            composite shouldBe 1
        }
```

companion 의 `configureProperties` 변경:

```kotlin
            registry.add("app.offerwall.tnk.android.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.ios.app-key") { "test-app-key" }
```

### B. 프로덕션 구현 (이 단계 후 컴파일 그린)

- [ ] **Step 6: TnkOfferwallProperties 를 플랫폼별 앱키로**

`TnkOfferwallProperties.kt` 전체 교체:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.properties

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.offerwall.tnk")
data class TnkOfferwallProperties(
    /** 플랫폼별 md_chk 검증용 공유 시크릿. TNK 가 Android/iOS 앱마다 다른 앱키를 발급한다. */
    val android: Platform = Platform(),
    val ios: Platform = Platform(),

    @field:Positive
    val pointToCoinRatio: Double = 1.0,

    val ack: Ack = Ack(),
) {
    data class Platform(
        /** prod 는 반드시 주입. 미설정 시 빈 값이라 해당 플랫폼 콜백이 모두 서명 실패로 거절된다(fail-closed). */
        val appKey: String = "",
    )

    data class Ack(
        val successBody: String = "SUCCESS",
    )

    fun appKeyFor(platform: OfferwallPlatform): String = when (platform) {
        OfferwallPlatform.ANDROID -> android.appKey
        OfferwallPlatform.IOS -> ios.appKey
    }
}
```

- [ ] **Step 7: TnkMdChecksumVerifier 가 플랫폼 키 사용**

`TnkMdChecksumVerifier.kt` 의 `isValid` 교체:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * TNK 콜백의 md_chk 를 플랫폼별 앱키로 검증한다. md_chk == MD5(appKey + md_user_nm + seq_id) (lowercase hex).
 * 플랫폼은 콜백 경로로 확정되므로 서명검증 시점에 어떤 앱키를 쓸지 결정돼 있다.
 */
@Component
class TnkMdChecksumVerifier(
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    fun isValid(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams): Boolean {
        val appKey = tnkOfferwallProperties.appKeyFor(platform)
        // app_key 가 비어 있으면 공격자도 동일 해시를 계산할 수 있어 fail-open 이 된다. 미설정 시 거절(fail-closed).
        if (appKey.isBlank()) return false
        val expected = md5Hex(appKey + params.mdUserNm + params.seqId)
        return expected.equals(params.mdChk, ignoreCase = true)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 8: TnkOfferwallCallback 에 platform 필드 + 유니크 제약명 변경**

`TnkOfferwallCallback.kt` 의 `@Table` 과 생성자 머리부를 교체(나머지 본문 변경 없음):

```kotlin
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform // 동일 패키지면 import 불필요
```

`@Table` 블록:

```kotlin
@Entity
@Table(
    name = "tnk_offerwall_callbacks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tnk_offerwall_callbacks_platform_seq_id", columnNames = ["platform", "seq_id"])
    ]
)
class TnkOfferwallCallback(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    val platform: OfferwallPlatform,

    @Column(name = "seq_id", nullable = false, length = 128)
    val seqId: String,

    @Column(name = "md_user_nm", nullable = false, length = 64)
    val mdUserNm: String,

    @Column(name = "pay_pnt", nullable = false)
    val payPnt: Long,

    @Column(name = "raw_query", nullable = false, columnDefinition = "TEXT")
    val rawQuery: String,
) : BaseEntity() {
```

> `OfferwallPlatform` 은 같은 패키지(`...persistence.entity`)이므로 import 불필요.

- [ ] **Step 9: TnkOfferwallCallbackRepository 를 (platform, seq_id) 기준으로**

`TnkOfferwallCallbackRepository.kt` 전체 교체:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallCallback
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TnkOfferwallCallbackRepository : JpaRepository<TnkOfferwallCallback, Long> {
    fun findByPlatformAndSeqId(platform: OfferwallPlatform, seqId: String): TnkOfferwallCallback?

    /**
     * (platform, seq_id) 행을 PENDING 으로 멱등 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로
     * 예외를 던지지 않아 메인 트랜잭션이 오염되지 않는다. platform 은 enum name 문자열로 전달한다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO tnk_offerwall_callbacks " +
            "(platform, seq_id, md_user_nm, pay_pnt, coin_amount, user_id, status, raw_query, created_at, updated_at) " +
            "VALUES (:platform, :seqId, :mdUserNm, :payPnt, 0, NULL, 'PENDING', :rawQuery, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE seq_id = seq_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("platform") platform: String,
        @Param("seqId") seqId: String,
        @Param("mdUserNm") mdUserNm: String,
        @Param("payPnt") payPnt: Long,
        @Param("rawQuery") rawQuery: String,
    ): Int

    /**
     * (platform, seq_id) 행을 비관적 쓰기 락으로 조회한다. 동일 (platform, seq_id) 동시 콜백을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TnkOfferwallCallback c where c.platform = :platform and c.seqId = :seqId")
    fun findForUpdate(
        @Param("platform") platform: OfferwallPlatform,
        @Param("seqId") seqId: String,
    ): TnkOfferwallCallback?
}
```

- [ ] **Step 10: TnkOfferwallService 가 platform 을 관통**

`TnkOfferwallService.kt` 의 `handleCallback` 교체(나머지 파일·`toCoinAmount` 변경 없음):

```kotlin
    @Transactional
    fun handleCallback(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams, now: Instant): TnkOfferwallStatus {
        // 서명 검증을 DB 쓰기 앞에 둔다 — 미검증 public 요청이 원장 행을 무제한 생성하는 것을 차단.
        if (!tnkMdChecksumVerifier.isValid(platform, params)) {
            log.warn("TNK offerwall callback rejected: bad signature (platform={}, seqId={})", platform, params.seqId)
            return TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
        }

        tnkOfferwallCallbackRepository.insertIfAbsent(
            platform = platform.name,
            seqId = params.seqId,
            mdUserNm = params.mdUserNm,
            payPnt = params.payPnt,
            rawQuery = params.rawQuery,
        )
        val callback = tnkOfferwallCallbackRepository.findForUpdate(platform, params.seqId)
            ?: throw IllegalStateException("tnk_offerwall_callbacks row must exist for platform=$platform seqId=${params.seqId}")

        // PENDING 만 처리한다. 이미 GRANTED/REJECTED 인 행은 중복/동시 콜백이므로 상태를 그대로 멱등 반환.
        if (callback.status != TnkOfferwallStatus.PENDING) {
            return callback.status
        }

        // 적립액은 양수여야 한다. 음수/0 pay_pnt 는 기록만 하고 적립하지 않는다.
        if (params.payPnt <= 0) {
            log.warn("TNK offerwall callback rejected: non-positive pay_pnt={} (platform={}, seqId={})", params.payPnt, platform, params.seqId)
            callback.markRejected(TnkOfferwallStatus.REJECTED_NON_POSITIVE)
            return TnkOfferwallStatus.REJECTED_NON_POSITIVE
        }

        val userId = offerwallUserTokenService.resolveUserId(params.mdUserNm)
        if (userId == null) {
            log.warn("TNK offerwall callback rejected: unknown token (platform={}, seqId={})", platform, params.seqId)
            callback.markRejected(TnkOfferwallStatus.REJECTED_UNKNOWN_USER)
            return TnkOfferwallStatus.REJECTED_UNKNOWN_USER
        }

        val coinAmount = toCoinAmount(params.payPnt, tnkOfferwallProperties.pointToCoinRatio)
        if (coinAmount > 0) {
            userPointService.recordTransaction(
                userId = userId,
                delta = coinAmount,
                reason = PointTransactionReason.OFFERWALL,
                idempotencyKey = "tnk:offerwall:${platform.name.lowercase()}:${params.seqId}",
            )
        }
        callback.markGranted(userId = userId, coinAmount = coinAmount)
        return TnkOfferwallStatus.GRANTED
    }
```

`import` 추가: `import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform`.

- [ ] **Step 11: OfferwallController 가 경로에서 플랫폼 파싱**

`OfferwallController.kt` 의 `handleCallback` 교체 + import 추가:

import 추가:
```kotlin
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import org.springframework.web.bind.annotation.PathVariable
```

메서드:
```kotlin
    @PostMapping("/callback/{platform}")
    @Operation(summary = "Handle TNK offerwall server postback", description = "Verifies md_chk with the platform app key, resolves user, credits coins idempotently.")
    fun handleCallback(
        @PathVariable platform: String,
        @RequestParam("seq_id") seqId: String,
        @RequestParam("pay_pnt") payPnt: Long,
        @RequestParam("md_user_nm") mdUserNm: String,
        @RequestParam("md_chk") mdChk: String,
    ): ResponseEntity<String> {
        val resolvedPlatform = OfferwallPlatform.from(platform)
        val rawQuery = "seq_id=$seqId&pay_pnt=$payPnt&md_user_nm=$mdUserNm&md_chk=$mdChk"
        tnkOfferwallService.handleCallback(
            resolvedPlatform,
            TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = rawQuery),
            Instant.now(),
        )
        return ResponseEntity.ok(tnkOfferwallProperties.ack.successBody)
    }
```

- [ ] **Step 12: OfferwallExceptionHandler 생성**

`apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/offerwall/web/exception/OfferwallExceptionHandler.kt`:

```kotlin
package com.wnl.cashchat.api.domain.offerwall.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.offerwall.exception.UnknownOfferwallPlatformException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.offerwall"])
class OfferwallExceptionHandler {
    private val log = LoggerFactory.getLogger(OfferwallExceptionHandler::class.java)

    @ExceptionHandler(UnknownOfferwallPlatformException::class)
    fun handleUnknownPlatform(e: UnknownOfferwallPlatformException): ResponseEntity<ErrorResponse> {
        log.warn("Unknown offerwall platform in callback path: {}", e.raw)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("UNKNOWN_OFFERWALL_PLATFORM", "지원하지 않는 오퍼월 플랫폼입니다."))
    }
}
```

> 검증: `ErrorResponse` 의 정확한 경로/시그니처를 확인할 것 — `apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/web/response/ErrorResponse.kt`, `data class ErrorResponse(val code: String, val message: String)`. 다르면 import/생성자를 맞춘다.

- [ ] **Step 13: SecurityConfig 의 콜백 경로 변경**

`SecurityConfig.kt` 에서:
```kotlin
                    .requestMatchers(HttpMethod.POST, "/api/offerwall/tnk/callback").permitAll()
```
→
```kotlin
                    .requestMatchers(HttpMethod.POST, "/api/offerwall/tnk/callback/*").permitAll()
```
(`*` 는 한 세그먼트만 매칭 → `/callback/android`, `/callback/ios`, `/callback/web`(→컨트롤러에서 400) 포함)

- [ ] **Step 14: application.yaml 의 오퍼월 설정 교체**

`application.yaml` 의 `app.offerwall.tnk` 블록 교체:

```yaml
  offerwall:
    tnk:
      android:
        app-key: ${APP_OFFERWALL_TNK_ANDROID_APP_KEY:}
      ios:
        app-key: ${APP_OFFERWALL_TNK_IOS_APP_KEY:}
      point-to-coin-ratio: ${APP_OFFERWALL_TNK_POINT_TO_COIN_RATIO:1.0}
      ack:
        success-body: ${APP_OFFERWALL_TNK_ACK_SUCCESS_BODY:SUCCESS}
```

- [ ] **Step 15: application-prod.yaml 의 오퍼월 설정 교체**

`application-prod.yaml` 의 `app.offerwall.tnk` 블록 교체:

```yaml
  offerwall:
    tnk:
      # 미설정 시 빈 값으로 기동(부팅 실패 방지). 빈 키면 해당 플랫폼 콜백이 모두 서명 실패로 거절(fail-closed).
      android:
        app-key: ${APP_OFFERWALL_TNK_ANDROID_APP_KEY:}
      ios:
        app-key: ${APP_OFFERWALL_TNK_IOS_APP_KEY:}
```

- [ ] **Step 16: Flyway 마이그레이션 V12 작성**

`apps/backend/src/main/resources/db/migration/V12__tnk_offerwall_platform.sql`:

```sql
-- V12: TNK 오퍼월 콜백을 플랫폼(Android/iOS) 인지로 확장.
-- TNK 미연동 상태라 tnk_offerwall_callbacks 가 비어 있어 NOT NULL 컬럼 추가가 안전하다.

ALTER TABLE tnk_offerwall_callbacks
    ADD COLUMN platform VARCHAR(16) NOT NULL;

-- seq_id 단독 유니크 → (platform, seq_id) 복합 유니크로 교체.
-- 동일 seq_id 라도 플랫폼이 다르면 독립 콜백으로 처리한다.
ALTER TABLE tnk_offerwall_callbacks
    DROP INDEX uk_tnk_offerwall_callbacks_seq_id;

ALTER TABLE tnk_offerwall_callbacks
    ADD CONSTRAINT uk_tnk_offerwall_callbacks_platform_seq_id UNIQUE (platform, seq_id);
```

### C. 전체 검증 + 커밋

- [ ] **Step 17: 오퍼월 전체 테스트 실행 (단위 + 통합)**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.offerwall.*"`
Expected: PASS. (통합 테스트는 Docker 로 MySQL 8.4 컨테이너를 띄움 — Docker 필요. V12 마이그레이션이 실제 MySQL 에서 실행되어 platform 컬럼/복합 유니크가 검증된다.)

- [ ] **Step 18: 백엔드 전체 빌드로 회귀 확인**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL. (`ddl-auto: validate` 가 엔티티-스키마 정합을 확인 — platform 매핑과 V12 컬럼이 일치해야 통과.)

- [ ] **Step 19: 커밋**

```bash
git add apps/backend/src/main apps/backend/src/test
git commit -m "feat(offerwall): 오퍼월 콜백 안드로이드/iOS 플랫폼 분리"
```

---

## Self-Review

**1. Spec coverage** (spec: `docs/superpowers/specs/2026-06-21-cc-361-offerwall-android-ios-design.md`)
- §1 OfferwallPlatform enum → Task 1.
- §2 Properties(앱키만 분리) → Step 6, 14, 15.
- §3 엔드포인트 `/callback/{platform}` → Step 11.
- §4 SecurityConfig `/callback/*` → Step 13.
- §5 서명검증 플랫폼 키 → Step 7.
- §6 엔티티/마이그레이션/멱등성 (platform, seq_id) + 멱등키 플랫폼 포함 → Step 8, 9, 10, 16.
- §7 서비스 흐름 platform 관통 → Step 10.
- §8 예외 처리(UnknownOfferwallPlatform → 400) → Step 12 + 컨트롤러 테스트 Step 3.
- 테스트 시나리오(플랫폼별 검증/교차 거절/독립 적립/미지 플랫폼 400/fail-closed/미지 유저) → Step 1~5.
- 비목표(프론트 SDK, 환산비율 분리, user-token 변경) → 계획에 미포함(의도적). ✅

**2. Placeholder scan:** 모든 코드 스텝에 완전한 코드 포함. "확인할 것"으로 표기한 곳은 `ErrorResponse` 경로 1건뿐이며 예상 시그니처를 명시 → 실제 플랜 공백 아님.

**3. Type consistency:**
- `isValid(platform, params)` — Step 1(테스트), 7(구현), 10(호출) 일치.
- `handleCallback(platform, params, now)` — Step 2/3/4(테스트), 10(구현), 11(호출) 일치.
- `findForUpdate(platform, seqId)`/`findByPlatformAndSeqId` — Step 9(정의), 2/4(테스트), 10(호출) 일치.
- `insertIfAbsent(platform: String, ...)` — Step 9(정의)는 String, Step 10(호출)은 `platform.name` 전달 일치.
- 멱등키 `tnk:offerwall:${platform.name.lowercase()}:${seqId}` — Step 2(테스트 `android:s10`), 10(구현) 일치.
- 유니크 제약명 `uk_tnk_offerwall_callbacks_platform_seq_id` — Step 8(엔티티), 16(마이그레이션), 5(테스트) 일치.
