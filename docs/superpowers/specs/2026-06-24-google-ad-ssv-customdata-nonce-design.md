# Google Ad SSV custom_data Nonce Alignment (CC-368)

## Summary

프론트엔드(Android)는 SSV nonce 를 `ServerSideVerificationOptions.setCustomData(nonce)` 로 보낸다 — 즉 콜백의 **`custom_data`** 파라미터에 실리며, **`user_id` 는 설정하지 않는다**. 그러나 백엔드는 nonce 를 `user_id` 에서 조회하고(`AdRewardService.grantFromCallback`), 파서는 `user_id` 를 필수로 요구하며 `custom_data` 를 추출조차 하지 않는다. 결과적으로 실제 광고 시청 콜백은 (1) `user_id` 부재로 파서에서 400, (2) 설령 있어도 nonce 가 `custom_data` 에 있어 조회 실패 → **실제 보상이 절대 적립되지 않는다.**

이 작업은 백엔드를 프론트 계약에 맞춰, nonce 출처를 `custom_data` 로 전환한다. 프론트는 변경하지 않는다.

공식 문서 근거: AdMob 보상형 SSV 가이드 — "Any string value set on a rewarded ad object is passed to the `custom_data` query parameter of the SSV callback. ... The custom reward string is percent escaped and might require decoding when parsed from the SSV callback."

## Scope

포함:
- `GoogleAdSsvQueryParser`: `custom_data` 추출(퍼센트 디코딩), `user_id` 를 필수에서 옵셔널로 완화.
- `GoogleAdSsvCallback`: `userId: String?`, `customData: String?` 추가.
- `GoogleAdSsvEvent` + Flyway `V13` 마이그레이션: `user_id` nullable, `custom_data` 컬럼 추가.
- `GoogleAdSsvService.toEntity`: `customData` 저장.
- `AdRewardService.grantFromCallback`: nonce 조회 키를 `callback.userId` → `callback.customData` 로 전환.
- 관련 테스트 갱신/추가.

제외:
- 프론트엔드 변경(참고만).
- 서명 검증·ad_unit·200 정책 로직 변경(이미 반영됨 — 이 브랜치의 `ca729ef` 서명-디코딩 수정은 함께 PR 에 포함되나 본 spec 의 작업 대상은 아님).

## Background: 확인된 사실

- FE `RewardedAdManager.show()` 는 `ServerSideVerificationOptions.Builder().setCustomData(nonce).build()` 만 호출(`setUserId` 없음).
- AdMob 공식: `setCustomData` → `custom_data`(설정 시에만 존재, 퍼센트 인코딩). `setUserId` → `user_id`.
- 백엔드 dev H2 는 `MODE=MySQL`(`docker-compose.frontend-local.yml`), 테스트는 TestContainers MySQL → MySQL DDL(`MODIFY COLUMN`, `ADD COLUMN`)이 양쪽에서 동작.
- 기존 마이그레이션 최신은 `V12` → 신규는 `V13`. `google_ad_ssv_events.user_id` 는 현재 `VARCHAR(128) NOT NULL`.
- nonce 는 서버 발급 32자 hex(`AdRewardNonceService`: `UUID...replace("-","")`) — 디코딩해도 동일하지만, 임의 custom_data 대비 디코딩을 일관 적용한다.

## Components

### GoogleAdSsvQueryParser
- 파라미터 맵에서 `custom_data` 를 읽어 `customData`(없으면 null)로 전달. 값은 기존 `decode()`(퍼센트 디코딩, `+` 보존)로 디코딩됨.
- `user_id` 를 `required(...)` 에서 옵셔널 조회로 변경: 있으면 디코딩 값, 없으면 null.
- 나머지 검증(서명/key_id 위치, 중복 키, 필수 reward 필드 등)은 유지.
- `signedPayload` 디코딩(서명 검증용)은 이 브랜치에 이미 반영됨 — 변경 없음.

### GoogleAdSsvCallback
- `userId: String` → `userId: String?`.
- `customData: String?` 추가.

### GoogleAdSsvEvent + V13 마이그레이션
- 마이그레이션 `V13__google_ad_ssv_custom_data.sql`:
  - `ALTER TABLE google_ad_ssv_events MODIFY COLUMN user_id VARCHAR(128) NULL;`
  - `ALTER TABLE google_ad_ssv_events ADD COLUMN custom_data VARCHAR(1024) NULL;`
- 엔티티: `userId` 를 nullable 컬럼/필드로, `customData: String?` 필드/컬럼 추가. `init` 의 `require(userId.isNotBlank())` 제거(또는 not-null 가정 제거). 나머지 require 유지.
- `hasSameCoreFieldsAs` 비교에 `customData` 포함(idempotency audit 정확도).

### GoogleAdSsvService.toEntity
- `userId = userId`(nullable), `customData = customData` 전달.

### AdRewardService.grantFromCallback
- nonce 조회: `adRewardNonceRepository.findForUpdate(callback.customData)`.
- `callback.customData` 가 null/blank 면 nonce 없음으로 간주 → `event.markRejected(REJECTED_INVALID_NONCE)` 후 반환(HTTP 200 유지).
- 적립 대상 userId 는 기존과 동일하게 `nonce.userId`(nonce → 내부 userId 매핑) 사용.

## Data Flow (실제 콜백)

1. FE: `setCustomData(nonce)` → 광고 표시.
2. AdMob 콜백: `...&custom_data=<nonce>&...&signature=..&key_id=..` (user_id 없음).
3. 파서: 서명 페이로드 디코딩, `customData=<nonce>`, `userId=null`.
4. 서비스: 서명 검증(200 경계) → ad_unit 일치 → 이벤트 저장(custom_data 포함).
5. 컨트롤러 → `grantFromCallback`: custom_data 로 nonce 조회 → 사용 가능하면 일일 한도 확인 후 `nonce.userId` 에 코인 적립, 이벤트 `GRANTED`.

## Error Handling

- `custom_data` 없는 서명 유효 콜백: 200, 이벤트 저장되나 nonce 없음 → `REJECTED_INVALID_NONCE`(미적립). 기존 200 정책과 일관(Google 재시도 폭주 방지).
- `user_id` 부재: 정상(옵셔널). 있으면 저장만.

## Test Plan

파서(`GoogleAdSsvQueryParserTest`):
- `custom_data` 가 디코딩되어 `customData` 로 추출된다.
- `user_id` 가 없어도 파싱 성공하고 `userId == null`.
- `user_id` 가 있으면 디코딩되어 `userId` 에 담긴다.

서비스(`GoogleAdSsvServiceTest`):
- 저장 시 `custom_data` 와 (있으면) `user_id` 가 이벤트에 반영된다.

적립(`AdRewardServiceTest`):
- `custom_data` 의 nonce 로 조회·적립된다.
- `custom_data` null/blank 면 `REJECTED_INVALID_NONCE`, 예외 없이 200.

영속성(`GoogleAdSsvPersistenceIntegrationTest` / `AdRewardIntegrationTest`):
- `custom_data` 컬럼에 저장/조회되고, `user_id` null 저장이 허용된다.

## Assumptions

- FE 는 nonce 를 항상 `custom_data` 로만 보내고 `user_id` 는 보내지 않는다(현재 구현 기준).
- custom_data 최대 길이는 AdMob 한도(1024)로 충분하다.
- 기존에 저장된 이벤트(user_id NOT NULL 시절)는 nullable 완화·컬럼 추가로 영향받지 않는다.
