# 리워드 광고 SSV — 연동 · 테스트 · 후속 작업 매뉴얼

> 구조·흐름 이해는 [`architecture.md`](./architecture.md) 먼저 읽기.
> 이 문서는 **실제로 동작시키기 위해 무엇을 해야 하는지**(프론트 구현, 인프라 설정, 테스트 절차, 트러블슈팅)를 정리한다.

## 0. 현재 상태 한눈에

| 구분 | 상태 |
| ---- | ---- |
| 백엔드 SSV 검증·저장·nonce 적립 | ✅ 구현 완료 (단, 본 변경 브랜치 **배포 필요**) |
| 프론트 — nonce를 `user_id`로 전달 | ⚠️ **미적용** (현재 `custom_data`로 보냄 → §2 필수 작업) |
| AdMob 콘솔 SSV URL 등록 | 🚧 확인/등록 필요 (§3) |
| 광고단위 ID·공개키 URI 환경변수 | 🚧 확인 필요 (§3, §5) |

> **end-to-end 적립이 동작하려면 최소: ①이 브랜치 배포 + ②프론트 `user_id` 전달 수정 + ③AdMob 콘솔 SSV URL 등록** 세 가지가 모두 필요하다.

---

## 1. 엔드포인트 요약

| Method | Path | 인증 | 설명 |
| ------ | ---- | ---- | ---- |
| `POST` | `/api/ads/reward/issue-nonce` | **JWT 필요** | 광고 시청 직전 단일 사용·단기 nonce 발급 → `{ nonce, expiresAt }` |
| `GET`  | `/api/ads/reward/quota` | **JWT 필요** | 오늘 남은 시청 횟수 → `{ usedToday, dailyLimit, remaining, resetAtKst }` |
| `GET`  | `/api/ads/google/ssv` | **public** | AdMob SSV 콜백(AdMob 서버가 호출). 앱이 직접 부르지 않음 |

---

## 2. 프론트엔드 구현 지침 (필수)

### 2.1 핵심 변경 — nonce를 `custom_data`가 아니라 `user_id`로

백엔드는 nonce를 SSV **`user_id`** 파라미터에서 읽는다(`AdRewardService`가 `callback.userId`를 nonce로 해석). 현재 클라이언트는 nonce를 `custom_data`로 싣고 있어 **위치가 어긋난다.** 아래처럼 바꿔야 한다.

**Android** — `apps/frontend/app/.../ads/RewardedAdManager.kt`
```kotlin
// 변경 전
ServerSideVerificationOptions.Builder().setCustomData(it).build()
// 변경 후
ServerSideVerificationOptions.Builder().setUserId(it).build()
```

**iOS** — `apps/frontend/CashChatIOS/.../Ads/RewardedAdManager.swift`
```swift
// 변경 전
options.customRewardText = nonce
// 변경 후
options.userIdentifier = nonce
```

> SDK API 참고: Android `ServerSideVerificationOptions.Builder.setUserId(String)`, iOS `ServerSideVerificationOptions.userIdentifier`. 둘 다 SSV 콜백의 `user_id` 파라미터로 매핑되는 표준 속성이다. (iOS는 SDK 버전에 따라 `customRewardString`/`customRewardText` 네이밍이 다를 수 있으나 `userIdentifier`는 버전 무관 안정 속성.) **변경 후 각 플랫폼 빌드로 컴파일 확인할 것.**

### 2.2 광고 시청 전체 흐름 (클라이언트가 구현해야 할 순서)

1. **(선택) quota 확인** — `GET /api/ads/reward/quota`로 `remaining`을 확인해, 0이면 [지금 시청] 버튼 비활성화.
2. **nonce 발급** — `POST /api/ads/reward/issue-nonce` 호출 → `{ nonce, expiresAt }` 수신. **광고 노출 직전에** 발급(TTL 기본 10분이므로 미리 받아두고 오래 묵히지 말 것).
3. **nonce를 `user_id`에 실어 노출** — `setUserId(nonce)`(§2.1) 후 보상형 광고 present.
4. **시청 후 재조회** — `onUserEarnedReward`/광고 종료 콜백 시점에 잔액(`GET /api/points/me` 등)과 `GET /api/ads/reward/quota`를 재조회해 화면 반영. (적립은 AdMob 서버 콜백으로 비동기 발생하므로, 종료 직후 약간의 지연이 있을 수 있음 → 필요 시 짧은 폴링/재시도.)

> nonce는 **단일 사용**이다. 한 번 시청에 한 번 발급. 같은 nonce 재사용 시 `REJECTED_INVALID_NONCE`로 적립되지 않는다.

### 2.3 KMM/공유 모듈 고려

`issue-nonce`·`quota` 호출과 잔액 재조회는 `shared/`의 리포지토리에 두고, 광고 SDK 노출(`RewardedAdManager`)만 플랫폼별로 두는 것이 현재 구조와 정합적이다. (광고 SDK는 Android/iOS 네이티브 코드.)

---

## 3. 인프라 / 운영 설정

### 3.1 AdMob 콘솔 — SSV 콜백 URL 등록

AdMob 콘솔 → 해당 **보상형 광고 단위** → **서버 측 확인(SSV)** → 콜백 URL 등록:
```
https://<도메인>/api/ads/google/ssv
```
- dev/prod 각각 도메인에 맞춰 등록(예: `https://cashchat.duckdns.org/api/ads/google/ssv`).
- HTTPS 필수. 콘솔의 "콜백 URL 설정 및 확인" 도구로 검증(§4.1).

### 3.2 광고 단위 ID 검증(선택이지만 권장)

`app.ads.google.rewarded-ad-unit-ids`(`APP_ADS_GOOGLE_REWARDED_AD_UNIT_IDS`)에 운영 광고단위 ID들을 **콤마로 구분해** 나열하면, 콜백 `ad_unit`이 목록 중 하나와 일치할 때만 적립된다(타 광고단위 콜백 차단). Android·iOS 앱은 각각 별도의 보상형 광고단위를 쓰므로 **두 ID를 모두 등록**해야 한다. **빈 값이면 검증을 건너뛴다** — 운영에서는 설정 권장.

> 주의: 콘솔의 SSV **테스트 도구**는 해당 광고단위 ID로 콜백을 보낸다. 목록에 없는 광고단위로 테스트하면 적립되지 않는다(서명이 유효하면 200 응답이되 미적립).

### 3.3 공개키 URI

기본값 `https://www.gstatic.com/admob/reward/verifier-keys.json` 사용(`APP_ADS_GOOGLE_SSV_PUBLIC_KEYS_URI`). 외부망 아웃바운드가 막힌 환경이면 이 호스트로의 egress를 허용해야 한다(못 받으면 SSV가 503).

### 3.4 보안/네트워크

- `GET /api/ads/google/ssv`는 `SecurityConfig`에서 `permitAll`(AdMob 서버가 인증 없이 호출). `POST .../issue-nonce`·`GET .../quota`는 JWT 필요.
- 리버스 프록시/로드밸런서가 쿼리스트링을 **그대로(인코딩 보존)** 전달해야 한다 — 서명 검증이 원본 쿼리스트링 바이트에 의존하므로, 중간에서 재인코딩/파라미터 재정렬이 일어나면 서명이 깨진다. (`server.forward-headers-strategy: framework` 설정됨.)

---

## 4. 테스트

### 4.1 AdMob 콘솔 "콜백 URL 설정 및 확인" 도구

이 도구는 **URL이 200을 반환하는지(도달성)만** 검증한다. 적립 로직 정확성은 보지 않는다.

- **HTTP 200 받기**: "사용자 ID" 칸에 **아무 non-blank 값**(임의 문자열 가능 — 이제 숫자 강제 없음)을 넣고 확인 → 서명만 통과하면 200("확인된 URL 사용" 활성화). **비워두면** `missing user_id`로 400.
- **실제 적립까지 검증하기**(웹 도구로 가능):
  1. JWT로 `POST /api/ads/reward/issue-nonce` 호출 → `nonce` 획득.
  2. 그 **nonce 문자열을 "사용자 ID" 칸에 붙여넣고** (TTL 10분 내) 확인.
  3. 결과: 서명 ✓ → nonce 유효 → 한도 내 → **코인 적립 + `reward_status=GRANTED`**.
  - 재시도하려면 매번 **새 nonce** 발급(단일 사용).

> 이메일/숫자 등 nonce가 아닌 값은 200은 나오지만 `REJECTED_INVALID_NONCE`로 기록되고 코인은 적립되지 않는다.

### 4.2 적립 결과 확인 (DB)

| 확인 대상 | 의미 |
| --------- | ---- |
| `google_ad_ssv_events.reward_status` | `GRANTED`(적립됨) / `REJECTED_INVALID_NONCE` / `REJECTED_OVER_QUOTA` / `VERIFIED`(적립 미결정) |
| `ad_reward_nonce.used` | 적립/소모 시 `true` |
| `point_transaction` (key=`admob:reward:{transactionId}`) | 코인 적립 1행 |
| 애플리케이션 로그 | `Invalid Google Ad SSV callback: ...`에 400 사유 명시 |

### 4.3 자동화 테스트 (백엔드)

```bash
cd apps/backend
# 단위(빠름, Docker 불필요)
./gradlew test --tests "com.wnl.cashchat.api.domain.ad.service.*" \
               --tests "com.wnl.cashchat.api.domain.ad.web.controller.GoogleAdSsvControllerTest"
# 통합(TestContainers MySQL — Docker 필요): 행 락 동시성·멱등 검증
./gradlew test --tests "com.wnl.cashchat.api.domain.ad.persistence.*"
```
- 단위: 파서/서명/공개키/검증·저장(`GoogleAdSsvServiceTest`)/적립(`AdRewardServiceTest`)/컨트롤러.
- 통합: `AdRewardIntegrationTest`(nonce·한도·멱등 동시성), `GoogleAdSsvPersistenceIntegrationTest`.

### 4.4 실단말 end-to-end

§2 수정 + §3 설정 + 배포 후: 테스트 단말에서 (구글 테스트 광고단위 또는 실 광고단위로) 광고 시청 → DB `reward_status=GRANTED` 확인. 가장 신뢰도 높은 검증.

---

## 5. 설정값 레퍼런스

`apps/backend/src/main/resources/application.yaml` (env로 오버라이드)

| 키 | 환경변수 | 기본값 | 의미 |
| --- | --- | --- | --- |
| `app.ads.google.ssv-public-keys-uri` | `APP_ADS_GOOGLE_SSV_PUBLIC_KEYS_URI` | gstatic verifier-keys.json | SSV 공개키 묶음 URI |
| `app.ads.google.public-key-cache-ttl` | `APP_ADS_GOOGLE_PUBLIC_KEY_CACHE_TTL` | `24h` | 공개키 캐시 TTL |
| `app.ads.google.timestamp-tolerance` | `APP_ADS_GOOGLE_TIMESTAMP_TOLERANCE` | `1h` | 콜백 timestamp 과거 허용폭(초과 시 미적립) |
| `app.ads.google.timestamp-future-skew` | `APP_ADS_GOOGLE_TIMESTAMP_FUTURE_SKEW` | `5m` | 콜백 timestamp 미래 허용폭(시계 오차) |
| `app.ads.google.rewarded-ad-unit-ids` | `APP_ADS_GOOGLE_REWARDED_AD_UNIT_IDS` | `` (빈값) | 콜백 `ad_unit` 허용 목록(콤마 구분, Android·iOS). 빈값이면 검증 스킵 |
| `app.ads.reward.coin-amount` | `APP_ADS_REWARD_COIN_AMOUNT` | `40` | 시청당 적립 코인(서버 고정, `reward_amount` 미신뢰) |
| `app.ads.reward.daily-limit` | `APP_ADS_REWARD_DAILY_LIMIT` | `10` | 사용자당 1일 시청 한도(KST 자정 리셋) |
| `app.ads.reward.nonce-ttl` | `APP_ADS_REWARD_NONCE_TTL` | `10m` | nonce 유효 시간 |

---

## 6. 트러블슈팅

| 증상 | 원인 | 조치 |
| ---- | ---- | ---- |
| 콘솔 테스트 **400** "Url error" | `user_id` 비움 → `missing user_id` | 사용자 ID 칸에 값 입력 |
| 콜백 **400** | 서명 검증 실패 / 쿼리스트링 변형 / percent 인코딩 깨짐 | 프록시가 쿼리스트링 원본 보존하는지 확인 |
| 콜백 **200**인데 미적립 | `ad_unit`이 `rewarded-ad-unit-ids` 목록에 없음 | 설정 목록에 Android·iOS 광고단위 ID가 모두 들어있는지 확인 |
| 콜백 **200**인데 미적립 | `timestamp`가 신선도 윈도우(과거 1h/미래 5m) 밖 | 서버 시계(NTP) 동기화 확인, 필요 시 윈도우 조정 |
| 콜백 **503** | 공개키(verifier-keys.json) 조회 실패 | gstatic egress 허용·일시 장애 재시도 |
| 200인데 적립 안 됨 | `user_id`가 실제 nonce가 아님/만료/이미 used → `REJECTED_INVALID_NONCE`, 또는 한도 초과 `REJECTED_OVER_QUOTA` | DB `reward_status` 확인. 실 nonce를 TTL 내 사용 |
| 적립이 화면에 안 뜸 | 콜백은 비동기 | 시청 후 잔액/quota 재조회 트리거 추가 |
| 실 광고 시청해도 적립 X | 프론트가 nonce를 `custom_data`로 보냄(§2 미적용) | `setUserId`/`userIdentifier`로 수정 후 재배포 |

---

## 7. 배포 전 체크리스트

- [ ] 백엔드 이 브랜치 배포 (숫자강제 제거 + ledger 적립 제거 반영)
- [ ] 프론트 nonce 전달 위치 수정: `setUserId` / `userIdentifier` (§2.1) + 빌드 확인
- [ ] 프론트 광고 흐름: 시청 전 `issue-nonce` 호출, 시청 후 잔액/quota 재조회 (§2.2)
- [ ] AdMob 콘솔 SSV 콜백 URL 등록 (dev/prod) (§3.1)
- [ ] `APP_ADS_GOOGLE_REWARDED_AD_UNIT_IDS` 에 Android·iOS 운영 광고단위 ID 콤마로 모두 등록(권장) (§3.2)
- [ ] gstatic verifier-keys.json egress 허용 확인 (§3.3)
- [ ] 콘솔 테스트 도구로 200 확인 + 실 nonce로 `GRANTED` 확인 (§4.1)
- [ ] 실단말 end-to-end 1회 검증 (§4.4)
- [ ] (정리) 휴면 `LedgerService`/`app.ledger.rewards.AD` 처리 방침 결정
