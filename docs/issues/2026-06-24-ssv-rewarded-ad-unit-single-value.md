# [이슈/BE] SSV `ad_unit` 단일값 검증 + CC-368 "조용한 미적립" → 리워드 적립 실패

- 상태: **Open** (BE 수정 + 재배포 필요. 추후 BE에서 처리)
- 작성일: 2026-06-24
- 영역: AdMob 리워드 SSV — 백엔드 `ad_unit` 검증 / 적립
- 관련: [2026-06-23-admob-rewarded-ssv-callback.md](./2026-06-23-admob-rewarded-ssv-callback.md), [google-ad-ssv/manual.md](../features/google-ad-ssv/manual.md)
- 관련 커밋: `279b50d` fix(ad): cc-368 ad_unit 불일치를 400 대신 200(미적립)으로 처리

---

## 1. 증상 (시간순)

1. **(CC-368 이전)** AdMob 콘솔 SSV URL "확인"이 Android에서 400으로 실패 → 등록 불가.
2. **(CC-368 이후, 현재)** 콘솔 "확인"은 **iOS·Android 둘 다 통과(200)**. 그러나 실광고 시청 후 **코인이 적립되지 않음**(특히 한쪽 플랫폼). 에러도 없이 조용히 미적립.

> CC-368이 "등록 실패(400)" 문제는 풀었지만, 그 대가로 **ad_unit 설정 오류를 조용한 무적립으로 바꿔** 가려버렸다. 그래서 **콘솔 "확인" 통과가 더 이상 ad_unit 일치를 보장하지 않는다.**

## 2. 원인 (코드 확정)

### 2.1 Google이 보내는 `ad_unit` 형식 = 전체 문자열

`GoogleAdSsvQueryParserTest.kt`가 실제 형식을 못박는다:
```
ad_unit=ca-app-pub-3940256099942544%2F5224354917  →  "ca-app-pub-3940256099942544/5224354917"
```
**숫자만이 아니라 `ca-app-pub-<PUBID>/<UNITID>` 전체 문자열**이다. 파서는 정규화/디코딩 외 가공을 하지 않으므로 설정값도 **전체 문자열로 정확히 일치**해야 한다.
(`.env.example`의 `ca-app-pub-xxx/yyyy`가 올바른 형식. 숫자만 넣으면 불일치.)

### 2.2 CC-368 이후: 불일치 = 저장 안 함 = 적립 안 함 (200)

```kotlin
// GoogleAdSsvService.verifyAndStore (현재)
signatureVerifier.verify(...)                       // 보안 경계(서명)는 그대로
if (!isAdUnitMatched(callback)) {
    logger.warn("Google Ad SSV ad_unit mismatch — accepted without crediting " +
                "(callback ad_unit={}, configured={})", callback.adUnit, properties.rewardedAdUnitId)
    return GoogleAdSsvVerificationResult(callback, newlyStored = false)   // ← 이벤트 미저장
}
// isAdUnitMatched: 설정이 빈값이면 true(검증 스킵), 아니면 callback.adUnit == properties.rewardedAdUnitId
```
이벤트가 저장되지 않으면 컨트롤러의 적립 호출이 무력화된다:
```kotlin
// AdRewardService.grantFromCallback
val event = repo.findForUpdateByTransactionId(callback.transactionId) ?: return  // ← null → 적립 없이 종료
```
→ **ad_unit이 설정값과 한 글자라도 다르면 200·warn 로그만 남기고 코인 미적립.**

### 2.3 단일값 한계 (구조적)

```yaml
# application-prod.yaml (prod 는 기본값 없이 필수)
app: { ads: { google: { rewarded-ad-unit-id: ${APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID} } } }
```
`rewardedAdUnitId: String` 은 **단 하나**만 담는데 리워드 단위는 **iOS·Android 둘**이다.
→ 어떤 단일값을 넣어도 **최소 한 플랫폼은 반드시 미적립**. 값이 전체 문자열이 아니거나(숫자만/test 단위/오타) iOS도 함께 실패.

## 3. 진단 (prod 로그·DB로 단계 특정)

`google_ad_ssv_events` 한 줄이면 어느 단계에서 막혔는지 특정된다:

| 관측 | 원인 | 조치 |
|---|---|---|
| **행 없음** + 로그 `ad_unit mismatch — accepted without crediting (callback ad_unit=…, configured=…)` | ad_unit 설정 불일치 (유력) | 로그의 `callback ad_unit` 값을 env에 그대로 박기 |
| **행 없음** + 로그 `Invalid Google Ad SSV callback: ... user_id` | FE가 user_id 미전송(구버전 빌드) | FE `setUserId`/`userIdentifier` 적용 빌드로 재배포 |
| 행 `reward_status=REJECTED_INVALID_NONCE` | nonce 만료(10분)/사용됨/미발급 | TTL 내 1회 시청, issue-nonce 호출 확인 |
| 행 `reward_status=REJECTED_OVER_QUOTA` | 일일 한도 초과 | 정상 동작 |
| 행 `reward_status=GRANTED` | 실제 적립됨 | FE 잔액 폴링/표시 문제 점검 |

```bash
docker logs <backend> 2>&1 | grep -iE "ad_unit mismatch|Invalid Google Ad SSV"
# SELECT transaction_id, user_id, ad_unit, reward_status, created_at
#   FROM google_ad_ssv_events ORDER BY created_at DESC LIMIT 5;
```

## 4. 해결 옵션 (BE, 재배포 필요)

### 옵션 A — 다중 광고단위 허용 (권장)
`rewardedAdUnitId: String` → `rewardedAdUnitIds`(콤마구분 문자열 또는 `List<String>`).
- `GoogleAdSsvProperties`: `rewardedAdUnitIds: List<String> = emptyList()` (또는 String을 `,`로 split·trim)
- `isRewardedAdUnitValidationEnabled()` = 리스트 비어있지 않으면 true
- `GoogleAdSsvService.isAdUnitMatched`: `callback.adUnit in configuredSet`
- prod env(**전체 문자열** 콤마구분):
  `APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID=ca-app-pub-5280178196982923/2647937531,ca-app-pub-5280178196982923/6512984753`
- 테스트: `GoogleAdSsvServiceTest`에 "목록 내 단위 적립 / 목록 외 단위 미적립" 케이스 추가

### 옵션 B — 검증 스킵 (즉시 임시 해제)
prod `APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID`를 **빈 값**으로 → ad_unit 검증 생략(서명은 그대로 강제).
**iOS·Android 둘 다 즉시 적립.** `docker-compose.yml`이 `${...:-}`(빈 기본값)이라 빈 값 주입 가능.
보안 영향은 제한적(유효 Google 서명 필수)이나 임의 광고단위 콜백을 수락하므로 **임시 조치**.

## 5. 권장 순서

1. (즉시) prod 로그 `ad_unit mismatch ... callback ad_unit=…` 확인 → 실제 값 확보.
2. (즉시 언블록) 옵션 B로 검증 스킵, 또는 옵션 A 구현까지 대기.
3. (정식) 옵션 A 구현 + env에 두 단위 **전체 문자열** 콤마 설정 + 재배포.

## 6. 참고 — 리워드 광고단위 ID (2026-06-24)

| 플랫폼 | 전체 ID (= 콜백 `ad_unit` = env에 넣을 값) |
|---|---|
| iOS | `ca-app-pub-5280178196982923/2647937531` |
| Android | `ca-app-pub-5280178196982923/6512984753` |

> ⚠️ 콜백 `ad_unit`은 **전체 문자열**이다. env에 숫자만 넣으면 불일치 → (CC-368) 조용한 미적립.

## 7. FE 측 전제 (이미 반영됨, 참고)

- FE는 nonce를 SSV **`user_id`**로 보내야 함: Android `setUserId(nonce)`, iOS `options.userIdentifier = nonce`.
  (이전 `setCustomData`/`customRewardText`는 `custom_data`로 가 user_id 누락 → 400. 본 작업 브랜치에서 수정 완료.)
