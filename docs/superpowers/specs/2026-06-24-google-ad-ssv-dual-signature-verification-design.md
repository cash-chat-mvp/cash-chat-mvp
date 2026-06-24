# Google Ad SSV Dual Signature Verification (CC-368)

## Summary

AdMob SSV 서명 검증에서 "Google 이 raw(percent-encoded) 쿼리스트링에 서명하는가, URL 디코딩된 콘텐츠에 서명하는가"가 자료마다 엇갈린다:

- Google 공식 Java SSV 샘플은 `request.getQueryString()` 의 **raw** 부분 문자열을 그대로 검증한다(디코딩 안 함). 문서도 "content should not be modified"라고 명시.
- 그러나 실제 AdMob "URL 확인" 핑을 캡처해 실제 공개키(`key_id=3335741209`)로 수동 ECDSA 검증한 결과, **raw → INVALID, URL 디코딩 → VALID** 였다(이 콜백의 `reward_item=에너지` 가 한글이라 두 형태가 다름). 즉 적어도 확인 핑은 디코딩된 콘텐츠에 서명한다.

ASCII 값만 있으면 raw == decoded 라 문제가 없지만, reward_item 등에 비ASCII/특수문자가 있으면 두 형태가 갈린다. 어느 쪽이 운영 콜백의 정답인지 실제 운영 콜백 없이는 단정할 수 없다.

이 작업은 검증기가 **raw 와 decoded 두 형태 모두로 서명을 시도**해(둘 중 하나라도 유효하면 통과) 양쪽 자료/증거를 모두 만족시킨다.

## Scope

포함:
- `GoogleAdSsvSignatureVerifier`: raw 페이로드로 검증 → 실패 시 decoded 로 재검증 → 둘 다 실패 시에만 거절(dual-verify).
- `GoogleAdSsvQueryParser`: `signedPayload` 를 raw 로 환원(`ca729ef` 의 `decode(...)` 되돌림) — 검증기가 디코딩을 담당하므로 콜백은 원문(raw)을 들고 있는다.
- 관련 테스트(`GoogleAdSsvSignatureVerifierTest`, `GoogleAdSsvQueryParserTest`).

제외:
- `GoogleAdSsvCallback` DTO, 서비스, 엔티티, 마이그레이션, custom_data 작업(별개로 완료됨).

## Background: 확인된 사실

- 검증기 현재: `verify(signedPayload, signature, keyId)` 가 SHA256withECDSA 로 `signedPayload` 바이트를 검증, 실패 시 `InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")`.
- `ca729ef` 가 파서의 `signedPayload` 를 raw → decoded 로 바꿔, 현재 콜백은 디코딩된 페이로드를 들고 있음.
- 컨트롤러는 `request.queryString`(raw)을 그대로 서비스에 전달.
- 파서의 `decode()` 규칙: `value.replace("+", "%2B")` 후 `URLDecoder.decode(UTF-8)` — 즉 `+` 는 리터럴 보존, `%XX` 만 디코딩(공백을 `%20` 으로 보내는 Google 인코딩과 일치).
- 보안: raw·decoded 모두 동일 콜백에서 파생되므로, 둘 중 하나로 Google 서명을 위조할 수 없다 → 검증 경계 유지. 두 형태를 허용해도 약화 없음.

## Components

### GoogleAdSsvQueryParser
- `signedPayload = rawQuery.substringBefore("&signature=")` 로 환원(raw). `ca729ef` 가 추가한 `decode(rawSignedPayload)` 제거.
- `signature` 누락 검증(`rawSignedPayload == rawQuery` 시 예외)은 raw 기준 그대로 유지.
- 파라미터 맵의 값 디코딩(필드 추출용)과 custom_data 추출은 변경 없음.

### GoogleAdSsvSignatureVerifier
- `verify(signedPayload, signature, keyId)` 시그니처 유지.
- 동작: 후보 페이로드 = `[signedPayload(raw), decode(signedPayload)]` 중복 제거. 각 후보에 대해 SHA256withECDSA 검증을 시도. 하나라도 `true` 면 성공 반환. 모두 실패면 `InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")`.
- `GeneralSecurityException`(키/서명 디코딩 등 비-검증 오류)은 기존처럼 `InvalidGoogleAdSsvCallbackException("Failed to verify Google AdMob SSV signature", e)` 로 매핑.
- 디코딩 헬퍼: 검증기 내부 private 함수로 파서와 동일 규칙(`+`→리터럴, `%XX` 디코딩) 적용. 디코딩이 raw 와 동일하면 후보가 1개로 합쳐져 한 번만 검증.

## Data Flow

콜백 도착 → 컨트롤러 raw queryString → 파서가 raw `signedPayload` 생성 → 검증기:
1. raw 페이로드로 서명 검증. ASCII 만이면 통과(raw == decoded).
2. 실패하면 decoded 페이로드로 재검증(인코딩된 값 포함 시 여기서 통과).
3. 둘 다 실패하면 거절(400, 서명 무효).

## Error Handling

- 두 후보 모두 서명 불일치 → `InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")`(400). 기존과 동일.
- 키 조회/서명 디코딩 실패 등 → 기존 매핑 유지.

## Test Plan

`GoogleAdSsvSignatureVerifierTest`(자체 생성 키쌍으로 서명):
- raw 페이로드로 서명한 콜백 → `verify` 통과(raw 시도 성공).
- 인코딩된 값을 포함한 페이로드를 **디코딩 형태로 서명**한 콜백 → raw 시도 실패 후 decoded 시도에서 통과.
- 어떤 후보로도 서명되지 않은(무효) 서명 → `InvalidGoogleAdSsvCallbackException`.

`GoogleAdSsvQueryParserTest`:
- `signedPayload` 가 raw(원문)임을 확인 — `ca729ef` 가 바꾼 "decoded 기대" 단언을 raw 기대로 환원.

## Assumptions

- Google 가 서명하는 형태는 raw 또는 decoded 중 하나이며, 둘 다 시도하면 운영 콜백·확인 핑 모두 통과한다.
- 실제 운영 콜백이 들어오면 로그로 어느 형태가 통과했는지 사후 확인 가능(후속, 선택).
