# Google Ad SSV Callback URL Verification Fix (CC-368)

## Summary

AdMob 콘솔에서 리워드 광고 단위의 SSV 콜백 URL을 등록할 때, "URL 확인" 단계가 우리 서버에서 **HTTP 400**을 받아 실패한다. 서버 로그로 원인을 확정했다:

```
Invalid Google Ad SSV callback: Google Ad SSV ad_unit does not match configured rewarded ad unit
```

AdMob의 "URL 확인" 핑이 싣는 `ad_unit` 값이 운영에 설정된 리워드 광고 단위 ID(`6961908443`)와 일치하지 않기 때문이다(확인 핑은 실제 광고 단위가 아닌 placeholder/다른 형식을 보냄). 따라서 **어떤 config 값을 넣어도** `validateAdUnit`의 완전 일치 검증을 통과할 수 없다.

이 핫픽스는 SSV 엔드포인트가 **서명이 유효한 콜백에 대해 200을 반환**하도록 바꿔, ad_unit 불일치를 HTTP 400이 아닌 "수신하되 적립하지 않음(200)" 게이트로 전환한다. 이로써 AdMob URL 확인이 통과한다.

## Scope

포함:
- `GoogleAdSsvService.verifyAndStore`에서 검증 순서를 재배치하고, `ad_unit` 불일치를 비치명적(200) 처리로 변경.
- ad_unit 불일치 시 실제 콜백의 `ad_unit` 값을 WARN 로그에 기록(진단 가시성).
- 관련 테스트 갱신.

제외(이번 핫픽스 범위 밖, 후속 티켓 권장):
- `verifyAndStore`의 숫자 `user_id` 강제(`toLongOrNull` → 400)와 `LedgerService` 기반 적립.
- nonce 기반 `AdRewardService.grantFromCallback` 적립과 ledger 적립의 이중/모순 정리.
- 이 둘은 *실제* 광고 시청 적립에서만 문제가 되며, AdMob URL 확인 핑(테스트 `user_id=1`, 숫자)에는 영향이 없다.

## Background: 확인된 동작 사실

- 운영 컨테이너 환경변수 `APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID=6961908443` 적용 확인됨(컨테이너 재시작 후 실패 로그 발생).
- 실패 사유는 항상 `ad_unit does not match configured rewarded ad unit` (서버 WARN 로그).
- Google 공식 SSV 콜백 파라미터: `signature`, `key_id`는 항상 마지막 두 개, 나머지는 알파벳순. `ad_unit`은 짧은 숫자 ID(예: `ad_unit=12345678`).
- AdMob URL 확인 핑은 위 형식의 서명된 콜백이며, 우리 엔드포인트가 **200을 반환하면 "확인됨"** 처리된다.

## Current Flow (문제)

`GoogleAdSsvService.verifyAndStore(rawQueryString)`:

1. `parser.parse` — 필수값/형식 검증 (실패 시 400)
2. `validateAdUnit` — **ad_unit 불일치 시 400** ← URL 확인을 막는 지점
3. `callback.userId.toLongOrNull()` — 비숫자 시 400
4. `signatureVerifier.verify` — 서명 무효 시 400
5. 이벤트 저장 + `creditReward`(ledger)

이후 컨트롤러가 `adRewardService.grantFromCallback`(nonce) 호출.

문제: 검증이 서명 확인 *전에* ad_unit 일치를 강제하고, 불일치를 400으로 처리해 확인 핑을 거절한다.

## Proposed Flow (수정)

`verifyAndStore`를 다음 순서로 재배치한다:

1. `parser.parse` — 변경 없음 (malformed → 400)
2. **`signatureVerifier.verify`를 앞으로 이동** — 서명 무효 → 400. 이것이 보안 경계: 200은 "진짜 Google이 우리 계정용으로 서명한 콜백"에만 부여된다.
3. **`validateAdUnit`를 비치명적 게이트로 변경**:
   - `rewardedAdUnitId`가 설정되어 있고 `callback.adUnit`과 불일치하면:
     - `logger.warn`에 실제 `callback.adUnit`와 설정값을 함께 기록.
     - 저장·적립 없이 즉시 반환(=200). 즉 `GoogleAdSsvVerificationResult(callback, newlyStored = false)` 반환하되, **`creditReward`는 호출하지 않는다**.
   - 일치(또는 검증 비활성)면 기존 흐름 진행.
4. `callback.userId.toLongOrNull()` — **변경 없음**(범위 밖). 확인 핑은 숫자 `user_id=1`이라 통과.
5. 기존 저장 + `creditReward`(ledger) — 변경 없음.

컨트롤러는 변경하지 않는다. ad_unit 불일치로 이벤트가 저장되지 않으면, 후속 `grantFromCallback`은 `findForUpdateByTransactionId(...) ?: return`에서 no-op이 되어 안전하다.

### HTTP 상태 매핑(변경 후)

| 상황 | 상태 |
|---|---|
| 파싱 실패/필수값 누락/형식 오류 | 400 |
| 서명 무효 | 400 |
| ad_unit 불일치 | **200** (저장·적립 skip, WARN 로그) |
| 숫자 아닌 user_id (ad_unit 일치 시) | 400 (범위 밖, 유지) |
| ad_unit 일치 + 정상 | 200 + 적립(유지) |
| Google 공개키 조회 실패 등 일시 오류 | 503 (유지) |

## 부수 효과 / 이점

- AdMob 확인 핑(ad_unit=placeholder) → 서명 통과 → ad_unit 불일치 → **200** → URL 확인 통과.
- 그동안 로그에 안 찍히던 **AdMob 확인 핑의 실제 `ad_unit` 값**이 WARN에 기록되어, placeholder인지 전체 형식인지 사후 확인 가능.
- 잘못된 광고 단위(타 퍼블리셔)의 서명 유효 콜백도 200을 받지만 적립되지 않음 — Google 재시도 폭주를 피하면서 안전.

## 변경 파일

- `apps/backend/src/main/kotlin/.../domain/ad/service/GoogleAdSsvService.kt` — `verifyAndStore` 순서 재배치 및 `validateAdUnit` 비치명적화.
- `apps/backend/src/test/kotlin/.../domain/ad/service/GoogleAdSsvServiceTest.kt` — 동작 변경 반영.

## Test Plan

서비스 테스트(`GoogleAdSsvServiceTest`):

- **(변경)** ad_unit 불일치 콜백: 예외/400이 아니라 → 저장·적립 없이 정상 반환(200 동작). 이벤트 미저장, `ledgerService.recordRevenue` 미호출 검증.
- 서명 무효 콜백: 여전히 `InvalidGoogleAdSsvCallbackException`(400). 서명 검증이 ad_unit보다 먼저 수행됨도 함께 확인.
- ad_unit 일치 + 유효 서명: 기존대로 저장 + 적립.
- 숫자 아닌 user_id(ad_unit 일치): 여전히 400 (범위 밖, 회귀 방지).
- 검증 비활성(`rewardedAdUnitId` 빈 값): ad_unit 무시하고 진행(기존 유지).

컨트롤러 테스트(`GoogleAdSsvControllerTest`):

- ad_unit 불일치 시 엔드포인트가 **200**을 반환(기존에 400을 기대하던 케이스가 있으면 갱신).

## 검증(배포 후)

스테이징 부재로, 머지 → CD 배포 후 AdMob 콘솔에서 `URL 확인` 클릭으로 최종 확인한다.
- 통과: URL 확인됨 → 콜백 등록 완료.
- 만약 확인 핑 서명이 우리 검증을 통과하지 못하면 로그에 `Invalid Google AdMob SSV signature`가 남으며, 그 경우 별도 재논의(공개키/서명 페이로드 추출 점검).

## 후속(별도 티켓 권장)

`verifyAndStore`의 숫자 user_id 강제 + ledger 이중 적립과 nonce 기반 `grantFromCallback`의 충돌을 정리해야 *실제* 광고 시청 적립이 동작한다. 이번 핫픽스는 여기에 의존하지 않는다.
