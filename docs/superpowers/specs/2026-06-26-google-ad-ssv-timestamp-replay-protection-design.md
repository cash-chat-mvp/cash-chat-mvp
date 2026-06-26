# Google Ad SSV — timestamp 재생공격(replay) 방어 설계

- 작성일: 2026-06-26
- 대상: 백엔드 `apps/backend` — `com.wnl.cashchat.api.domain.ad`
- 관련: [google-ad-ssv 기능 문서](../../features/google-ad-ssv/architecture.md)

## 1. 배경 / 문제

Google AdMob 보상형 광고 SSV 콜백은 `timestamp`(epoch milliseconds, 사용자가 보상받은 시각)를
포함한다. 현재 `GoogleAdSsvQueryParser`는 이 값을 파싱·저장만 하고 **현재 시각과 비교 검증하지 않는다**.

`transaction_id` 유니크 제약이 *동일* 콜백의 중복 적립은 막지만, SSV 이벤트 레코드를 향후
프루닝(오래된 행 삭제)하면 그 시점 이후 동일 콜백을 재전송해 적립을 노리는 재생공격 창이 열린다.
`timestamp` 신선도 검증은 이 창을 시간 윈도우로 닫는 보조 방어다.

> 보안의 1차 경계는 ECDSA 서명 검증이다. timestamp 신선도는 transaction_id 멱등성을 보완하는
> 심층 방어(defense-in-depth)이며, 그 자체가 위조를 막는 수단은 아니다.

## 2. 목표 / 비목표

**목표**
- SSV 콜백의 `timestamp`가 허용 시간 윈도우 밖이면 적립하지 않는다.
- 윈도우 크기를 운영 중 코드 수정 없이 조정할 수 있게 설정으로 노출한다.

**비목표**
- 서명 검증·ad_unit 검증·nonce/한도/적립 로직 변경 없음.
- transaction_id 프루닝 정책 자체는 본 작업 범위 밖(향후 별도).

## 3. 설계 결정 (확정)

| 항목 | 결정 |
| --- | --- |
| 허용 윈도우 | 과거 **1시간** / 미래 **5분** (기본값, 설정 가능) |
| 윈도우 초과 시 | 기존 `ad_unit` 불일치와 동일하게 **저장하지 않고 200 반환 + WARN 로그** (`newlyStored=false`) |
| 윈도우 값 | 설정 프로퍼티로 노출 |

윈도우 초과 콜백을 **저장하지 않는** 이유: ad_unit 불일치 처리와 일관성을 유지하고, 적립되지 않는
이벤트로 `google_ad_ssv_events` 테이블이 오염되는 것을 막는다. 관측은 WARN 로그(타임스탬프 델타 포함)로 대신한다.

윈도우 초과 시 **400이 아닌 200**을 반환하는 이유: 서명이 유효한 콜백에 4xx/5xx를 돌려주면 Google이
재전송을 반복한다. 정상 콜백이 (네트워크/시계 사유로) 경계를 살짝 넘은 경우의 재시도 폭주를 피한다.

## 4. 컴포넌트 변경

### 4.1 `GoogleAdSsvProperties` (prefix `app.ads.google`)

새 프로퍼티 2개 추가:

| 프로퍼티 | env | 기본값 |
| --- | --- | --- |
| `timestamp-tolerance` | `APP_ADS_GOOGLE_TIMESTAMP_TOLERANCE` | `1h` |
| `timestamp-future-skew` | `APP_ADS_GOOGLE_TIMESTAMP_FUTURE_SKEW` | `5m` |

- 둘 다 `@PositiveDuration` 검증(기존 애너테이션 재사용).
- 신선도 판정을 프로퍼티에 캡슐화한다(ad_unit의 `isAllowedAdUnit`과 동형):

```kotlin
fun isTimestampFresh(timestampMillis: Long, now: Instant): Boolean {
    val eventTime = Instant.ofEpochMilli(timestampMillis)
    val lowerBound = now.minus(timestampTolerance)
    val upperBound = now.plus(timestampFutureSkew)
    return !eventTime.isBefore(lowerBound) && !eventTime.isAfter(upperBound)
}
```

### 4.2 `GoogleAdSsvService.verifyAndStore`

- 시그니처를 `verifyAndStore(rawQueryString: String?, now: Instant)`로 변경.
- 검증 순서: **서명(보안 경계)** → ad_unit 허용목록 → **timestamp 신선도**.
  ad_unit·timestamp 게이트는 모두 "수신하되 적립하지 않음(미저장, 200)" 으로 처리.
- 신선도 실패 시 WARN 로그(콜백 timestamp, 현재 시각/델타, 윈도우) 후 `GoogleAdSsvVerificationResult(callback, newlyStored = false, eligibleForGranting = false)` 반환.
  (PR 리뷰 반영: 결과에 `eligibleForGranting` 플래그를 두어 ad_unit 불일치·timestamp 윈도우 밖 같은 하드 거절 게이트는 `false` →
  컨트롤러가 `grantFromCallback` 의 무의미한 행 락 조회를 건너뛴다. 신규 저장·기존 이벤트 재시도는 `true`.)

### 4.3 `GoogleAdSsvController`

- `Instant.now()`를 **한 번** 만들어 `verifyAndStore(query, now)`와 `grantFromCallback(callback, now)`에
  **동일 시각**으로 전달한다(두 호출의 시각 일관 + 테스트 가능).

## 5. 데이터 흐름

```
GET /api/ads/google/ssv
  → controller: now = Instant.now()
  → service.verifyAndStore(query, now)
       parse
       verify signature            (실패 → 400/503)
       ad_unit in allowlist?        (불일치 → 200, 미저장, WARN)
       timestamp fresh?(now)        (초과 → 200, 미저장, WARN)   ← 신규
       store (idempotent)           → newlyStored
  → service.grantFromCallback(callback, now)   (기존)
  → 200 OK
```

## 6. 에러 / 엣지 케이스

- 미래 timestamp(서버-구글 시계 오차): `future-skew(5m)` 이내는 허용, 초과는 미적립.
- 경계값: lower/upper bound 정확히 같은 시각은 신선으로 인정(`isBefore`/`isAfter` 사용 → 경계 포함).
- 설정값이 음수/0: `@PositiveDuration`으로 기동 시 거절.

## 7. 테스트 (TDD)

**`GoogleAdSsvPropertiesTest`**
- 기본값 `timestamp-tolerance=1h`, `timestamp-future-skew=5m`.
- `isTimestampFresh`: 윈도우 내 true / 과거 초과 false / 미래 초과 false / 경계값 true.
- 음수 duration `@PositiveDuration` 위반.

**`GoogleAdSsvServiceTest`**
- 신선한 timestamp 콜백: 서명 검증 + 저장.
- 과거 초과 콜백: 서명은 검증되나 미저장(`saveAndFlush` never), `newlyStored=false`.
- 미래 초과 콜백: 미저장.
- 기존 케이스: `verifyAndStore(raw, now)` 시그니처 반영(테스트 helper에 고정 `now` 추가).

**`GoogleAdSsvControllerTest`**
- `verifyAndStore`가 (query, now)로 호출되고, `grantFromCallback`에 동일 now가 전달됨을 확인.

## 8. 영향 파일

- `domain/ad/properties/GoogleAdSsvProperties.kt`
- `domain/ad/service/GoogleAdSsvService.kt`
- `domain/ad/web/controller/GoogleAdSsvController.kt`
- `resources/application.yaml`, `resources/application-prod.yaml`
- `apps/backend/.env.example`, `infra/deploy/backend/.env.example`, `docker-compose.yml`, `.github/workflows/backend-cicd.yml`
  (기본값이 있으므로 prod 필수는 아님 — 노출만; CI/compose 추가는 선택)
- 테스트 3종, 운영자 문서(`docs/features/google-ad-ssv/manual.md`, `architecture.md`).
