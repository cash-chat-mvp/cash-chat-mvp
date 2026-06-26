# Google Ad SSV Dual Signature Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SSV 서명 검증이 raw·decoded 페이로드 두 형태로 시도(하나라도 유효하면 통과)하도록 바꿔, Google 공식 샘플(raw)과 실제 확인 핑(decoded) 양쪽을 모두 만족시킨다.

**Architecture:** `GoogleAdSsvSignatureVerifier` 가 raw 페이로드로 ECDSA 검증 → 실패 시 URL 디코딩 페이로드로 재검증 → 둘 다 실패 시에만 거절. `GoogleAdSsvQueryParser` 는 `signedPayload` 를 raw(원문)로 환원(`ca729ef` 의 디코딩 되돌림) — 디코딩은 검증기가 담당.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Kotest(FunSpec), JDK 21, java.security(SHA256withECDSA).

## Global Constraints

- 커밋: Conventional Commits, **subject 소문자**(husky/commitlint), 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. `--no-verify` 금지.
- 작업 디렉터리/브랜치: 워크트리 `C:\laptop-workspace\cash-chat-mvp\.claude\worktrees\hot-fix-google-ad-ssv`, 브랜치 `hotfix/cc-368-google-ad-ssv`. 테스트는 `apps/backend`.
- 디코딩 규칙(검증기·파서 동일): `payload.replace("+", "%2B")` 후 `URLDecoder.decode(UTF-8)` — `+` 리터럴 보존, `%XX` 만 디코딩.
- 보안 불변: raw·decoded 모두 동일 콜백에서 파생 → 둘 다 시도해도 서명 위조 불가, 검증 경계 유지.
- 범위 밖: 콜백 DTO, 서비스, 엔티티, 마이그레이션, custom_data 작업.

---

### Task 1: 검증기 dual-verify + 파서 signedPayload raw 환원

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifierTest.kt`, `GoogleAdSsvQueryParserTest.kt`

**Interfaces:**
- `GoogleAdSsvSignatureVerifier.verify(signedPayload: String, signature: String, keyId: Long)` — 시그니처 유지, 동작만 dual-verify 로 변경.
- `GoogleAdSsvCallback.signedPayload` 는 raw(원문)로 환원.

- [ ] **Step 1: 실패 테스트 작성**

`GoogleAdSsvSignatureVerifierTest.kt` 에 추가(파일 끝 `})` 직전, 마지막 test 다음):

```kotlin
    test("signature over the url-decoded payload passes even when raw payload is percent-encoded") {
        val keyPair = ecKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(7L)).thenReturn(keyPair.public)
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)
        // 검증기에 전달되는 raw 페이로드(인코딩됨)와, Google 이 서명한 디코딩 페이로드
        val rawPayload = "ad_unit=au&reward_item=%EC%97%90&user_id=1"
        val decodedPayload = "ad_unit=au&reward_item=에&user_id=1"
        val signature = webSafeBase64Signature(decodedPayload, keyPair)

        // raw 로는 서명이 안 맞지만 decoded 로 재시도해 통과해야 한다.
        verifier.verify(rawPayload, signature, 7L)
    }
```

`GoogleAdSsvQueryParserTest.kt` 에서 기존 두 테스트를 교체한다.

기존 `test("valid parse decodes the signed payload to match Google's signed content") { ... }` 전체를 다음으로 교체:

```kotlin
    test("valid parse keeps the signed payload raw (undecoded)") {
        val rawSignedPayload =
            "ad_unit=ca-app-pub-3940256099942544%2F5224354917" +
                "&reward_amount=10" +
                "&reward_item=coin%20pack" +
                "&timestamp=1710000000123" +
                "&transaction_id=txn-123" +
                "&user_id=user%2B42"
        val rawQuery = "$rawSignedPayload&signature=MEUCIQDabc%2Bdef&key_id=12345"

        val callback = parser.parse(rawQuery)

        callback.adUnit shouldBe "ca-app-pub-3940256099942544/5224354917"
        callback.rewardAmount shouldBe 10
        callback.rewardItem shouldBe "coin pack"
        callback.timestamp shouldBe 1710000000123L
        callback.transactionId shouldBe "txn-123"
        callback.userId shouldBe "user+42"
        callback.signature shouldBe "MEUCIQDabc+def"
        callback.keyId shouldBe 12345L
        callback.rawQueryString shouldBe rawQuery
        // 검증기가 raw·decoded 둘 다 시도하므로 파서는 원문(raw)을 그대로 보존한다.
        callback.signedPayload shouldBe rawSignedPayload
    }
```

기존 `test("signed payload decodes non-ascii reward_item (real AdMob verification ping case)") { ... }` 전체를 다음으로 교체:

```kotlin
    test("signed payload keeps non-ascii reward_item percent-encoded (raw)") {
        val rawQuery =
            "ad_network=5450213213286189855" +
                "&ad_unit=1234567890" +
                "&reward_amount=1" +
                "&reward_item=%EC%97%90%EB%84%88%EC%A7%80" +
                "&timestamp=1782250214931" +
                "&transaction_id=123456789" +
                "&user_id=1" +
                "&signature=sig" +
                "&key_id=3335741209"

        val callback = parser.parse(rawQuery)

        callback.rewardItem shouldBe "에너지"
        // 파서는 디코딩하지 않고 raw 를 보존한다(검증기가 raw·decoded 둘 다 시도).
        callback.signedPayload shouldBe
            "ad_network=5450213213286189855" +
                "&ad_unit=1234567890" +
                "&reward_amount=1" +
                "&reward_item=%EC%97%90%EB%84%88%EC%A7%80" +
                "&timestamp=1782250214931" +
                "&transaction_id=123456789" +
                "&user_id=1"
    }
```

- [ ] **Step 2: 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvSignatureVerifierTest" --tests "com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvQueryParserTest"`
Expected: FAIL — 새 verifier 테스트는 현재 단일(decoded만 받는) 검증이라 raw 페이로드+decoded 서명이 불일치로 throw; 파서 테스트는 현재 `signedPayload` 가 decoded 라 raw 기대와 불일치.

- [ ] **Step 3: 구현 — 검증기 dual-verify**

`GoogleAdSsvSignatureVerifier.kt` 전체를 다음으로 교체:

```kotlin
package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

@Component
class GoogleAdSsvSignatureVerifier(
    private val publicKeyClient: GoogleAdPublicKeyClient,
) {
    /**
     * Google 이 raw(percent-encoded) 콘텐츠에 서명하는지 URL 디코딩된 콘텐츠에 서명하는지는 자료마다 엇갈린다
     * (공식 Java 샘플=raw, 실제 AdMob '확인' 핑 캡처=decoded). 두 형태 모두 동일 콜백에서 파생되어 어느 쪽으로도
     * 서명 위조가 불가능하므로, raw → decoded 순으로 시도해 하나라도 유효하면 통과시킨다(검증 경계 유지).
     */
    fun verify(
        signedPayload: String,
        signature: String,
        keyId: Long,
    ) {
        val decodedSignature = decodeSignature(signature)
        val publicKey = publicKeyClient.getPublicKey(keyId)

        val candidates = buildList {
            add(signedPayload)
            runCatching { urlDecode(signedPayload) }.getOrNull()?.let { decoded ->
                if (decoded != signedPayload) add(decoded)
            }
        }

        try {
            if (candidates.any { verifyOne(publicKey, it, decodedSignature) }) {
                return
            }
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")
        } catch (e: InvalidGoogleAdSsvCallbackException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw InvalidGoogleAdSsvCallbackException("Failed to verify Google AdMob SSV signature", e)
        }
    }

    private fun verifyOne(publicKey: PublicKey, payload: String, signature: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        return verifier.verify(signature)
    }

    // 파서와 동일 규칙: '+' 는 리터럴 보존(Google 은 공백을 %20 으로 인코딩), '%XX' 만 디코딩.
    private fun urlDecode(payload: String): String =
        URLDecoder.decode(payload.replace("+", "%2B"), StandardCharsets.UTF_8)

    private fun decodeSignature(signature: String): ByteArray =
        try {
            Base64.getUrlDecoder().decode(signature)
        } catch (e: IllegalArgumentException) {
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature encoding", e)
        }
}
```

- [ ] **Step 4: 구현 — 파서 signedPayload raw 환원**

`GoogleAdSsvQueryParser.kt` 에서 현재 블록

```kotlin
        val rawSignedPayload = rawQuery.substringBefore("&signature=")
        if (rawSignedPayload == rawQuery) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string must include signature")
        }
        // Google 은 percent-encoding 된 전송 문자열이 아니라 URL 디코딩된 콘텐츠에 서명한다
        // (예: reward_item 이 한글 '에너지' 면 %EC%97%90.. 가 아니라 '에너지' 에 서명). 따라서
        // 서명 검증용 페이로드는 raw 가 아니라 디코딩한 값으로 재구성한다. 구조 구분자(&, =)는
        // percent-encoding 대상이 아니므로 그대로 유지되고, 값의 %XX 만 디코딩된다.
        val signedPayload = decode(rawSignedPayload)
```

를 다음으로 교체:

```kotlin
        // 검증기가 raw·decoded 두 형태로 서명을 시도하므로 파서는 원문(raw)을 그대로 보존한다.
        val signedPayload = rawQuery.substringBefore("&signature=")
        if (signedPayload == rawQuery) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV query string must include signature")
        }
```

(콜백 생성의 `signedPayload = signedPayload` 는 그대로. `decode()` 함수는 파라미터 값 디코딩에 여전히 쓰이므로 삭제하지 않는다.)

- [ ] **Step 5: 통과 확인 (ad 도메인 전체)**

Run: `cd apps/backend && ./gradlew test --tests "com.wnl.cashchat.api.domain.ad.*"`
Expected: PASS. 새 dual-verify 테스트, raw signedPayload 파서 테스트 포함 전부 그린. (서비스/적립/영속성 테스트는 signedPayload 형태에 의존하지 않으므로 영향 없음.)

- [ ] **Step 6: 커밋**

```bash
cd "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv"
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifierTest.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParserTest.kt
git commit -m "fix(ad): cc-368 ssv 서명을 raw·decoded 두 형태로 검증

google 이 raw·decoded 중 어느 콘텐츠에 서명하는지 자료가 엇갈려(공식 샘플 raw,
실제 확인 핑 decoded), 검증기가 raw 로 먼저 시도하고 실패 시 decoded 로 재검증한다.
파서의 signedPayload 는 원문(raw)으로 환원한다. 두 형태 모두 동일 콜백 파생이라 위조 불가.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 전체 백엔드 테스트 그린 확인

**Files:** (검증 전용)

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd apps/backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. 실패 시 본 변경 관련이면 수정, 무관한 선존재 실패면 보고 후 사용자 판단.

- [ ] **Step 2: 범위 확인**

Run: `git --no-pager -C "C:/laptop-workspace/cash-chat-mvp/.claude/worktrees/hot-fix-google-ad-ssv" diff --stat upstream/dev HEAD -- apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/`
Expected: 이번 변경은 `GoogleAdSsvSignatureVerifier.kt` 와 `GoogleAdSsvQueryParser.kt`(signedPayload 환원)에 한정.

---

## 배포 후 수동 검증 (코드 작업 아님)

머지 → CD 배포 후 AdMob `URL 확인` 재시도 → 서명 검증이 dual-verify 로 통과해야 한다. 실제 운영 광고 시청 콜백이 들어오면 로그로 raw/decoded 어느 형태가 통과했는지 확인(후속, 선택).
