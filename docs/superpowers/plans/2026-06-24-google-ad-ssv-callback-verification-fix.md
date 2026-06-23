# Google Ad SSV Callback URL Verification Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AdMob SSV 콜백 URL 확인이 통과하도록, `ad_unit` 불일치를 HTTP 400이 아닌 "수신하되 적립하지 않음(200)"으로 바꾼다.

**Architecture:** `GoogleAdSsvService.verifyAndStore`에서 서명 검증을 가장 먼저 수행(보안 경계)하고, `ad_unit` 불일치는 경고 로그 후 저장·적립 없이 200으로 조기 반환한다. 컨트롤러·예외 핸들러는 변경하지 않는다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Kotest(FunSpec) + mockito-kotlin, Gradle, JDK 21.

## Global Constraints

- 커밋: Conventional Commits. **subject는 소문자**로 시작/유지(commitlint `subject-case`가 Start-Case/PascalCase/UPPER-CASE/Sentence-case를 거부함). 예: `fix(ad): ...`.
- 커밋 메시지 끝에 트레일러: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- husky `commit-msg` 훅 활성 — `--no-verify` 사용 금지.
- 작업 디렉터리: 워크트리 `C:\laptop-workspace\cash-chat-mvp\.claude\worktrees\hot-fix-google-ad-ssv`. 브랜치 `hotfix/cc-368-google-ad-ssv`.
- 테스트/빌드는 `apps/backend`에서 실행.
- 범위 밖(이번에 건드리지 않음): `verifyAndStore`의 숫자 `user_id` 강제 및 ledger 이중 적립. nonce 적립 모순 정리.

---

### Task 1: ad_unit 불일치를 400 → 200(미적립) 으로 전환

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt`

**Interfaces:**
- Consumes: 기존 `GoogleAdSsvService(parser, signatureVerifier, repository, properties, ledgerService)` 생성자와 `GoogleAdSsvCallback`, `GoogleAdSsvVerificationResult(callback, newlyStored)`, `GoogleAdSsvProperties(rewardedAdUnitId)` — 변경 없음.
- Produces: `verifyAndStore(rawQueryString): GoogleAdSsvVerificationResult` 동작 변경 — `ad_unit` 불일치 시 예외 대신 `newlyStored = false` 결과 반환, 서명 검증은 항상 ad_unit 판정보다 먼저 수행.

- [ ] **Step 1: 실패 테스트 작성 — 기존 ad_unit 거절 테스트 2개를 새 동작으로 교체**

`GoogleAdSsvServiceTest.kt`에서 아래 **기존 두 테스트 블록을 삭제**한다:
- `test("existing transaction id with ad unit mismatch is rejected before verifier and save") { ... }`
- `test("ad unit mismatch rejects and does not save") { ... }`

그 자리에 아래 두 테스트를 삽입한다:

```kotlin
test("ad unit mismatch verifies signature then accepts (200) without storing or crediting") {
    val parser = mock<GoogleAdSsvQueryParser>()
    val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
    val repository = mock<GoogleAdSsvEventRepository>()
    val ledgerService = ledgerMock()
    val callback = callback(adUnit = "unexpected-ad-unit")
    whenever(parser.parse(rawQuery)).thenReturn(callback)
    val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

    val result = service.verifyAndStore(rawQuery)

    result.newlyStored shouldBe false
    verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
    verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    verify(repository, never()).findByTransactionId(any())
    verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
}

test("invalid signature is rejected even when ad_unit also mismatches (signature checked first)") {
    val parser = mock<GoogleAdSsvQueryParser>()
    val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
    val repository = mock<GoogleAdSsvEventRepository>()
    val ledgerService = ledgerMock()
    val callback = callback(adUnit = "unexpected-ad-unit")
    whenever(parser.parse(rawQuery)).thenReturn(callback)
    doThrow(InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature"))
        .whenever(signatureVerifier)
        .verify(callback.signedPayload, callback.signature, callback.keyId)
    val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

    shouldThrow<InvalidGoogleAdSsvCallbackException> {
        service.verifyAndStore(rawQuery)
    }

    verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvServiceTest"`
Expected: FAIL. 첫 테스트는 현재 `validateAdUnit`가 `InvalidGoogleAdSsvCallbackException`을 던져 `verifyAndStore` 호출에서 예외 발생(결과를 못 받음). 둘째 테스트는 현재 ad_unit 검증이 서명보다 먼저라 서명 검증이 호출되지 않아도 통과할 수 있으나, 첫 테스트 실패만으로 RED 확인 가능.

- [ ] **Step 3: 구현 — 서명 검증을 앞으로 이동, ad_unit 게이트를 비치명적화**

`GoogleAdSsvService.kt`의 `verifyAndStore` 함수 본문 상단을 교체한다. 기존:

```kotlin
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun verifyAndStore(rawQueryString: String?): GoogleAdSsvVerificationResult {
        val callback = parser.parse(rawQueryString)
        validateAdUnit(callback)
        val internalUserId = callback.userId.toLongOrNull()
            ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV userId is not a numeric internal id: ${callback.userId}")
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        val existingEvent = repository.findByTransactionId(callback.transactionId)
```

를 다음으로 바꾼다:

```kotlin
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun verifyAndStore(rawQueryString: String?): GoogleAdSsvVerificationResult {
        val callback = parser.parse(rawQueryString)
        // 서명 검증을 가장 먼저 수행한다. 이것이 보안 경계이며, 200 응답은 'Google 이 우리 계정용으로
        // 서명한 진짜 콜백'에만 부여된다. AdMob 콜백 URL '확인' 핑도 유효 서명을 싣고 오므로 통과한다.
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        // ad_unit 불일치는 거절(400)이 아니라 '수신하되 적립하지 않음(200)' 으로 처리한다.
        // AdMob URL '확인' 핑은 실제 광고 단위가 아닌 placeholder ad_unit 을 싣기 때문에 400 으로 막으면
        // URL 등록이 불가능하고, 잘못된 ad_unit 의 (서명 유효) 콜백을 400 으로 돌려주면 Google 이 재시도를 반복한다.
        if (!isAdUnitMatched(callback)) {
            logger.warn(
                "Google Ad SSV ad_unit mismatch — accepted without crediting (callback ad_unit={}, configured={})",
                callback.adUnit,
                properties.rewardedAdUnitId,
            )
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

        val internalUserId = callback.userId.toLongOrNull()
            ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV userId is not a numeric internal id: ${callback.userId}")

        val existingEvent = repository.findByTransactionId(callback.transactionId)
```

그리고 같은 파일의 기존 `validateAdUnit` 함수:

```kotlin
    private fun validateAdUnit(callback: GoogleAdSsvCallback) {
        if (!properties.isRewardedAdUnitValidationEnabled()) {
            return
        }
        if (callback.adUnit != properties.rewardedAdUnitId) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV ad_unit does not match configured rewarded ad unit")
        }
    }
```

를 다음으로 교체한다:

```kotlin
    private fun isAdUnitMatched(callback: GoogleAdSsvCallback): Boolean {
        if (!properties.isRewardedAdUnitValidationEnabled()) {
            return true
        }
        return callback.adUnit == properties.rewardedAdUnitId
    }
```

(나머지 함수 본문 — 기존 이벤트 분기, try/catch 중복 복구, `creditReward`, `logIfCoreFieldsDiffer`, `toEntity`, `companion object logger` — 는 변경하지 않는다.)

- [ ] **Step 4: 대상 테스트 실행 → 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvServiceTest"`
Expected: PASS (신규 2개 포함, 전체 그린).

- [ ] **Step 5: ad 도메인 + 컨트롤러 회귀 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.*"`
Expected: PASS. 특히 `GoogleAdSsvControllerTest`(서비스 mock 기반, 변경 없음)와 `non-numeric userId ... rejected` 테스트(ad_unit 일치 케이스라 여전히 400)가 그린이어야 한다.

- [ ] **Step 6: 커밋**

```bash
cd "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv"
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvServiceTest.kt
git commit -m "fix(ad): cc-368 ad_unit 불일치를 400 대신 200(미적립)으로 처리

admob ssv 콜백 url 확인 핑은 placeholder ad_unit 을 보내 400 으로 막혀 등록이 불가능했다.
서명 검증을 먼저 수행해 보안 경계를 유지하고, ad_unit 불일치는 경고 로그 후
저장·적립 없이 200 으로 반환한다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 전체 백엔드 테스트 그린 확인 후 마무리

**Files:** (없음 — 검증 전용)

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd apps/backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. 실패 시 해당 테스트를 조사하고, 본 변경과 무관한 선존재 실패면 보고 후 사용자 판단을 받는다.

- [ ] **Step 2: 변경 요약 점검**

Run: `git --no-pager -C "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv" diff --stat origin/dev`
Expected: 변경 파일은 spec/plan 문서 2개 + `GoogleAdSsvService.kt` + `GoogleAdSsvServiceTest.kt` 만.

---

## 배포 후 수동 검증 (코드 작업 아님 — 머지/배포 후)

스테이징이 없으므로 CD 배포 후 확인한다.

1. `hotfix/cc-368-google-ad-ssv` → `dev` PR 머지 → backend-cicd CD 배포.
2. AdMob 콘솔에서 콜백 URL `https://cashchat.duckdns.org/api/ads/google/ssv` + 테스트 `사용자 ID = 1` → **URL 확인**.
3. 기대: **확인됨** → `확인된 URL 사용` 활성화.
4. 동시에 서버 로그에서 AdMob 확인 핑의 실제 ad_unit 값 확인(처방 검증):
   `docker logs --since 5m cash-chat-backend 2>&1 | grep -i "ad_unit mismatch"`
5. 만약 통과되지 않고 로그에 `Invalid Google AdMob SSV signature`가 보이면, 확인 핑 서명이 우리 검증을 통과하지 못한 것이므로 공개키/서명 페이로드 추출을 별도로 재논의한다.

## 후속(별도 티켓)

`verifyAndStore`의 숫자 user_id 강제 + ledger 이중 적립과 nonce 기반 `grantFromCallback` 충돌 정리 — 실제 광고 시청 적립 동작화. 이번 핫픽스는 여기에 의존하지 않는다.
