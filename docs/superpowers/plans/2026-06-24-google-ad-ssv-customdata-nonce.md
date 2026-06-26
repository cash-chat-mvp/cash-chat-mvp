# Google Ad SSV custom_data Nonce Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드 SSV nonce 출처를 `user_id` → `custom_data` 로 전환하고 `user_id` 를 옵셔널화해, FE(`setCustomData(nonce)`) 계약과 일치시켜 실제 광고 적립이 동작하게 한다.

**Architecture:** 파서가 `custom_data` 를 추출(퍼센트 디코딩)하고 `user_id` 를 옵셔널로 받는다. `GoogleAdSsvEvent` 는 `custom_data` 컬럼을 갖고 `user_id` 는 nullable 이 된다. `AdRewardService.grantFromCallback` 은 `callback.customData` 로 nonce 를 조회한다. 두 Flyway 마이그레이션으로 스키마를 점진 변경한다(V13 컬럼 추가, V14 user_id nullable).

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, JPA/Hibernate, Flyway, Kotest(FunSpec) + mockito-kotlin, TestContainers MySQL, JDK 21.

## Global Constraints

- 커밋: Conventional Commits, **subject 소문자**(husky/commitlint `subject-case`), 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. `--no-verify` 금지.
- 작업 디렉터리/브랜치: 워크트리 `C:\laptop-workspace\cash-chat-mvp\.claude\worktrees\hot-fix-google-ad-ssv`, 브랜치 `hotfix/cc-368-google-ad-ssv`. 테스트는 `apps/backend`.
- 마이그레이션: 최신은 `V12` → 신규 `V13`, `V14`. dev H2 는 `MODE=MySQL`, 테스트는 TestContainers MySQL → MySQL DDL(`ADD COLUMN`, `MODIFY COLUMN`) 사용.
- `custom_data` 값은 퍼센트 인코딩되어 오므로 파서의 기존 `decode()` 로 디코딩한다(이미 파라미터 맵은 디코딩됨).
- 스펙의 단일 V13 을 구현 안전을 위해 V13(custom_data 추가)+V14(user_id nullable) 두 개로 분리한다 — 동일 의도.
- 범위 밖: 프론트엔드, 서명/ad_unit/200 정책(이미 반영됨, 이 브랜치 `ca729ef` 서명-디코딩 수정 포함).

---

### Task 1: custom_data 컬럼 추가 및 파싱·저장 (additive)

`custom_data` 를 파싱하고 저장한다. `user_id` 와 적립 로직은 이 태스크에서 바꾸지 않는다(여전히 user_id 필수·non-null).

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt`
- Create: `apps/backend/src/main/resources/db/migration/V13__google_ad_ssv_custom_data.sql`
- Test: `GoogleAdSsvQueryParserTest.kt`, `GoogleAdSsvPersistenceIntegrationTest.kt`, `GoogleAdSsvServiceTest.kt`

**Interfaces:**
- Produces: `GoogleAdSsvCallback.customData: String?`; `GoogleAdSsvEvent(..., customData: String? = null)` (new `custom_data` column).

- [ ] **Step 1: 실패 테스트 작성**

`GoogleAdSsvQueryParserTest.kt` 에 추가:

```kotlin
    test("custom_data is extracted and url-decoded") {
        val rawQuery =
            "ad_unit=au&reward_amount=1&reward_item=coin&timestamp=1&transaction_id=t" +
                "&custom_data=nonce%2Babc&user_id=u&signature=sig&key_id=1"

        val callback = parser.parse(rawQuery)

        callback.customData shouldBe "nonce+abc"
    }
```

`GoogleAdSsvPersistenceIntegrationTest.kt` 의 `init { ... }` 안에 추가:

```kotlin
        test("custom_data is persisted") {
            googleAdSsvEventRepository.saveAndFlush(
                GoogleAdSsvEvent(
                    transactionId = "txn-cd",
                    userId = "user-9",
                    rewardAmount = 5,
                    rewardItem = "coin",
                    adUnit = "rewarded-ad-unit",
                    keyId = 3,
                    rawQueryString = "transaction_id=txn-cd",
                    customData = "nonce-1",
                )
            )
            entityManager.clear()

            val persisted = googleAdSsvEventRepository.findByTransactionId("txn-cd")

            persisted?.customData shouldBe "nonce-1"
        }
```

`GoogleAdSsvServiceTest.kt` 의 `callback(...)` 헬퍼에 `customData` 파라미터 추가(기존 시그니처에 한 줄 추가):

```kotlin
    fun callback(
        transactionId: String = "txn-123",
        userId: String = nonceUserId,
        rewardAmount: Int = 10,
        rewardItem: String = "coin",
        adUnit: String = "rewarded-ad-unit",
        keyId: Long = 12345L,
        customData: String? = "custom-nonce",
    ) = GoogleAdSsvCallback(
        adUnit = adUnit,
        rewardAmount = rewardAmount,
        rewardItem = rewardItem,
        timestamp = 1710000000123L,
        transactionId = transactionId,
        userId = userId,
        customData = customData,
        signature = "sig",
        keyId = keyId,
        rawQueryString = rawQuery,
        signedPayload = rawQuery.substringBefore("&signature="),
    )
```

그리고 `GoogleAdSsvServiceTest.kt` 의 "saves verified callback and calls verifier" 테스트의 이벤트 캡처 검증부에 한 줄 추가(`eventCaptor.firstValue.rawQueryString shouldBe rawQuery` 바로 다음):

```kotlin
        eventCaptor.firstValue.customData shouldBe callback.customData
```

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvQueryParserTest" --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvServiceTest"`
Expected: 컴파일 실패(`customData` 미정의) 또는 FAIL.

- [ ] **Step 3: 구현**

`GoogleAdSsvCallback.kt` 에 `customData` 추가(`userId` 다음 줄):

```kotlin
data class GoogleAdSsvCallback(
    val adUnit: String,
    val rewardAmount: Int,
    val rewardItem: String,
    val timestamp: Long,
    val transactionId: String,
    val userId: String,
    val customData: String? = null,
    val signature: String,
    val keyId: Long,
    val rawQueryString: String,
    val signedPayload: String,
)
```

`GoogleAdSsvQueryParser.kt` 의 `GoogleAdSsvCallback(...)` 생성에서 `userId = required(parameters, "user_id"),` 다음 줄에 추가:

```kotlin
            customData = parameters["custom_data"]?.takeIf { it.isNotBlank() },
```

`GoogleAdSsvEvent.kt`: 생성자에 `rawQueryString` 파라미터 다음에 컬럼 추가:

```kotlin
    @Column(name = "custom_data", nullable = true, length = 1024)
    val customData: String? = null,
```

그리고 `hasSameCoreFieldsAs` 의 마지막 비교에 `customData` 추가:

```kotlin
    private fun GoogleAdSsvEvent.hasSameCoreFieldsAs(callback: GoogleAdSsvCallback): Boolean =
        userId == callback.userId &&
            rewardAmount == callback.rewardAmount &&
            rewardItem == callback.rewardItem &&
            adUnit == callback.adUnit &&
            keyId == callback.keyId &&
            customData == callback.customData
```

`GoogleAdSsvService.kt` 의 `toEntity()` 에서 `userId = userId,` 다음 줄에 추가:

```kotlin
            customData = customData,
```

마이그레이션 생성 `V13__google_ad_ssv_custom_data.sql`:

```sql
-- V13: SSV nonce 를 custom_data 로 정렬. FE 가 setCustomData(nonce) 로 보내므로 nonce 를 담을 컬럼 추가.
ALTER TABLE google_ad_ssv_events
    ADD COLUMN custom_data VARCHAR(1024) NULL;
```

- [ ] **Step 4: 통과 확인 (ad 도메인 전체)**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.*"`
Expected: PASS (신규 custom_data 테스트 포함; 기존 user_id 기반 적립 테스트도 그대로 그린 — 이 태스크는 적립 로직 미변경).

- [ ] **Step 5: 커밋**

```bash
cd "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv"
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt \
        apps/backend/src/main/resources/db/migration/V13__google_ad_ssv_custom_data.sql \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParserTest.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/persistence/GoogleAdSsvPersistenceIntegrationTest.kt
git commit -m "feat(ad): cc-368 ssv custom_data 추출·저장 추가

fe 가 setCustomData(nonce) 로 보내는 custom_data 를 파싱·저장한다(v13 컬럼 추가).
적립 로직(user_id 기준)은 다음 커밋에서 전환한다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: nonce 출처를 custom_data 로 전환 + user_id 옵셔널화

`user_id` 를 nullable 로 만들고, `grantFromCallback` 이 `custom_data` 로 nonce 를 조회하도록 전환한다.

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/AdRewardService.kt`
- Create: `apps/backend/src/main/resources/db/migration/V14__google_ad_ssv_user_id_nullable.sql`
- Test: `GoogleAdSsvQueryParserTest.kt`, `AdRewardServiceTest.kt`, `AdRewardIntegrationTest.kt`

**Interfaces:**
- Consumes: `GoogleAdSsvCallback.customData` (Task 1).
- Produces: `GoogleAdSsvCallback.userId: String?`; `GoogleAdSsvEvent.userId: String?`; `grantFromCallback` keys nonce on `callback.customData`.

- [ ] **Step 1: 실패 테스트 작성**

`GoogleAdSsvQueryParserTest.kt` 에 추가:

```kotlin
    test("user_id is optional and absent yields null userId") {
        val rawQuery =
            "ad_unit=au&reward_amount=1&reward_item=coin&timestamp=1&transaction_id=t" +
                "&custom_data=nonce123&signature=sig&key_id=1"

        val callback = parser.parse(rawQuery)

        callback.userId shouldBe null
        callback.customData shouldBe "nonce123"
    }
```

`AdRewardServiceTest.kt` 의 `callback(...)` 헬퍼를 custom_data 기반으로 교체:

```kotlin
    fun callback(nonceCustomData: String) = GoogleAdSsvCallback(
        adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
        transactionId = txnId, userId = null, customData = nonceCustomData, signature = "sig", keyId = 1L,
        rawQueryString = "raw", signedPayload = "raw",
    )
```

그리고 `AdRewardServiceTest.kt` 에 누락 케이스 추가:

```kotlin
    test("missing custom_data marks event REJECTED_INVALID_NONCE without nonce lookup") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = null, rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw", customData = null)
        whenever(eventRepository.findForUpdateByTransactionId(txnId)).thenReturn(event)
        val noCustomData = GoogleAdSsvCallback(
            adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
            transactionId = txnId, userId = null, customData = null, signature = "sig", keyId = 1L,
            rawQueryString = "raw", signedPayload = "raw",
        )

        service.grantFromCallback(noCustomData, now)

        event.rewardStatus shouldBe RewardStatus.REJECTED_INVALID_NONCE
        verify(nonceRepository, never()).findForUpdate(any())
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }
```

`AdRewardIntegrationTest.kt` 의 `callback(...)` 과 `storeEvent(...)` 헬퍼를 custom_data 기반으로 교체:

```kotlin
    private fun callback(txnId: String, nonce: String) = GoogleAdSsvCallback(
        adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
        transactionId = txnId, userId = null, customData = nonce, signature = "sig", keyId = 1L,
        rawQueryString = "raw-$txnId", signedPayload = "raw",
    )

    private fun storeEvent(txnId: String, nonce: String) =
        eventRepository.saveAndFlush(
            GoogleAdSsvEvent(transactionId = txnId, userId = null, rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw-$txnId", customData = nonce)
        )
```

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvQueryParserTest"`
Expected: FAIL (`user_id` 가 아직 필수라 absent 시 예외 → 테스트 실패). (`AdRewardServiceTest`/`AdRewardIntegrationTest` 는 `userId = null` 로 컴파일이 안 돼 RED 가 명확하므로 파서 테스트로 RED 확인 충분.)

- [ ] **Step 3: 구현**

`GoogleAdSsvCallback.kt`: `val userId: String,` → `val userId: String?,`.

`GoogleAdSsvQueryParser.kt`: `userId = required(parameters, "user_id"),` 를 다음으로 교체:

```kotlin
            userId = parameters["user_id"]?.takeIf { it.isNotBlank() },
```

`GoogleAdSsvEvent.kt`: user_id 컬럼/필드를 nullable 로:

```kotlin
    @Column(name = "user_id", nullable = true, length = 128)
    val userId: String?,
```

그리고 `init` 블록에서 다음 한 줄을 삭제:

```kotlin
        require(userId.isNotBlank()) { "User id must not be blank" }
```

`AdRewardService.kt` 의 `grantFromCallback` 에서 기존 nonce 조회 블록

```kotlin
        val nonce = adRewardNonceRepository.findForUpdate(callback.userId)
        if (nonce == null || !nonce.isUsable(now)) {
            event.markRejected(RewardStatus.REJECTED_INVALID_NONCE)
            return
        }
```

을 다음으로 교체:

```kotlin
        // nonce 는 FE 가 SSV custom_data 로 싣는다(user_id 는 보내지 않음). 비관적 쓰기 락으로 직렬화한다.
        val nonceToken = callback.customData
        if (nonceToken.isNullOrBlank()) {
            event.markRejected(RewardStatus.REJECTED_INVALID_NONCE)
            return
        }
        val nonce = adRewardNonceRepository.findForUpdate(nonceToken)
        if (nonce == null || !nonce.isUsable(now)) {
            event.markRejected(RewardStatus.REJECTED_INVALID_NONCE)
            return
        }
```

마이그레이션 생성 `V14__google_ad_ssv_user_id_nullable.sql`:

```sql
-- V14: FE 는 SSV user_id 를 보내지 않으므로(nonce 는 custom_data) user_id 를 nullable 로 완화한다.
ALTER TABLE google_ad_ssv_events
    MODIFY COLUMN user_id VARCHAR(128) NULL;
```

- [ ] **Step 4: 통과 확인 (ad 도메인 전체)**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.*"`
Expected: PASS. 특히 `AdRewardIntegrationTest`(Flyway + validate + TestContainers MySQL)가 V13/V14 와 엔티티 일치를 검증하고, custom_data 기준 적립이 동작함을 확인.

- [ ] **Step 5: 커밋**

```bash
cd "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv"
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvCallback.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/persistence/entity/GoogleAdSsvEvent.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/AdRewardService.kt \
        apps/backend/src/main/resources/db/migration/V14__google_ad_ssv_user_id_nullable.sql \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParserTest.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/AdRewardServiceTest.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/persistence/AdRewardIntegrationTest.kt
git commit -m "fix(ad): cc-368 ssv nonce 를 custom_data 에서 조회하고 user_id 옵셔널화

fe 는 setCustomData(nonce) 로 보내고 user_id 는 보내지 않는다. grantFromCallback 이
custom_data 로 nonce 를 조회하도록 전환하고, user_id 를 nullable 로 완화한다(v14).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 전체 백엔드 테스트 그린 확인

**Files:** (검증 전용)

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd apps/backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. 실패 시 본 변경과 관련 있으면 수정, 무관한 선존재 실패면 보고 후 사용자 판단.

- [ ] **Step 2: 범위 확인**

Run: `git --no-pager -C "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv" diff --stat upstream/dev HEAD -- apps/`
Expected: 변경은 ad 도메인 파일(콜백/파서/엔티티/서비스/리워드) + V13·V14 마이그레이션 + 해당 테스트 + (이미 있는) 서명-디코딩 수정에 한정.

---

## 배포 후 수동 검증 (코드 작업 아님)

머지 → CD 배포 후, FE 가 nonce 를 custom_data 로 실어 실제 광고를 시청하면 `grantFromCallback` 이 적립한다. 서버 로그/quota 로 적립 확인. (AdMob URL 확인 통과는 ad_unit→200 + 서명-디코딩 수정으로 이미 처리됨.)
