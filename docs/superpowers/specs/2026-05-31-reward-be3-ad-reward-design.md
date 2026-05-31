# 혜택존 BE-3 — 리워드 광고 적립 레이어 Design (cc-242 SSV 위 통합)

> 상태: In Review (PR #164)
> 범위: CC-288 백엔드 PR3 (BE-3) — AdMob 리워드 광고 시청 → 코인 적립
> 관련: cc-242(#146, dev 머지됨 — Google AdMob SSV 검증/로깅), `docs/features/reward/spec.md`(BE-3 인수 기준), RFC CC-287, BE-1 `UserPointService.recordTransaction`

## 1. 목표 (Goal)

AdMob 리워드 광고 시청을 **서버 SSV 검증 후 코인으로 적립**한다. 적립은 위조·중복·동시성(TOCTOU)·일일 한도를 모두 방어한다. SSV 서명 검증·콜백 수신·이벤트 로깅은 **이미 cc-242가 구현**(`domain/ad/`)했으므로, 본 작업은 그 위에 **리워드 적립 레이어**(서버 발급 nonce → userId 해석, 일일 한도, 코인 적립, 결과 기록)만 추가한다.

## 2. 맥락 (Context) — 이미 있는 것 vs 추가할 것

**cc-242가 이미 구현(dev):**
- `GET /api/ads/google/ssv` (`GoogleAdSsvController`) → `GoogleAdSsvService.verifyAndStore(rawQueryString)`
- 표준 SSV 파라미터 파싱(`GoogleAdSsvQueryParser`: `ad_unit, reward_amount, reward_item, timestamp, transaction_id, user_id, signature, key_id`), ECDSA 서명 검증(`GoogleAdSsvSignatureVerifier` + `GoogleAdPublicKeyClient`), `google_ad_ssv_events` 저장(`transaction_id` 유니크로 dedup, `rewardStatus` enum 현재 `VERIFIED`만).
- 설정 `app.ads.google.*`(공개키 URI/캐시 TTL/광고 단위 ID). V4 마이그레이션.

**본 작업(BE-3)이 추가:**
- 서버 발급 nonce(단일 사용·단기 TTL) ↔ 내부 userId 매핑 + 발급 API
- 일일 시청 한도(per-user-per-day, 행 락) + 조회 API
- 코인 적립(BE-1 `recordTransaction`) + 적립 결과 기록(이벤트 `rewardStatus` 확장)

## 3. 핵심 설계 결정 (Decisions)

- **D1 — nonce 전송**: 클라이언트는 `issue-nonce`로 받은 nonce를 AdMob SSV의 **`user_id` 필드**에 싣는다. 백엔드는 `callback.userId`를 실제 userId가 아닌 **opaque nonce 토큰**으로 취급해 `nonce → 내부 userId`로 해석한다(클라이언트가 채운 식별값 미신뢰). cc-242 파서 변경 불필요.
- **D2 — 멱등성 키**: `admob:reward:{transactionId}`. Google이 제공하는 고유 SSV id이며 cc-242도 이 값으로 이벤트를 dedup하므로 재사용.
- **D3 — 적립 결과 기록**: 별도 ledger 테이블을 만들지 않고 `google_ad_ssv_events.rewardStatus` enum을 확장한다: `VERIFIED`(초기) → `GRANTED` / `REJECTED_INVALID_NONCE` / `REJECTED_OVER_QUOTA`. 컬럼은 `VARCHAR(32)`라 **스키마 변경 없이 값만 추가**.
- **D4 — 통합 방식**: `GoogleAdSsvService.verifyAndStore`가 파싱·검증·저장 결과(파싱된 `GoogleAdSsvCallback` + "신규 저장 여부")를 **반환**하도록 소폭 리팩터(현재 `void`). 컨트롤러가 검증 성공 시 그 결과를 `AdRewardService.grantFromCallback(...)`에 전달. SSV 검증은 기존대로 무트랜잭션, **코인 적립만 별도 `@Transactional`**.
- **D5 — 마이그레이션 V5**: `ad_reward_nonce`, `ad_reward_daily_quota` 신규. (적립 결과는 D3로 기존 테이블 재사용.)
- **코인 값**: 적립 코인은 **서버 설정값**(Phase 1 고정 40코인). 콜백의 `reward_amount`(AdMob 콘솔 설정값)는 신뢰·사용하지 않는다(코인 이코노미는 서버 정책).

## 4. 컴포넌트 (Architecture)

**엔티티/리포지토리 (`domain/ad/persistence/`)**
- `AdRewardNonce`(`nonce` PK String, `userId` Long, `expiresAt` Instant, `used` Boolean) + `AdRewardNonceRepository`
- `AdRewardDailyQuota`(`(userId, kstDate)` 복합 PK, `usedCount` Int) + `AdRewardDailyQuotaRepository`(`@Lock(PESSIMISTIC_WRITE)` 조회)

**서비스 (`domain/ad/service/`)**
- `AdRewardNonceService.issueFor(userId): AdRewardNonce` — UUID nonce, TTL 적용 저장
- `AdRewardService.grantFromCallback(callback: GoogleAdSsvCallback)` — **단일 `@Transactional`**
- `AdRewardQuotaView` 조회 로직(또는 `AdRewardService.quotaOf(userId, todayKst)`)

**웹 (`domain/ad/web/`)**
- `AdRewardController`: `POST /api/ads/reward/issue-nonce`, `GET /api/ads/reward/quota`
- `GoogleAdSsvController` 수정: `verifyAndStore` 결과를 `adRewardService.grantFromCallback`으로 연결

**설정 (`app.ads.reward.*`)**: `coin-amount`(40), `daily-limit`(10), `nonce-ttl`(10m)

**마이그레이션 V5**: `ad_reward_nonce`, `ad_reward_daily_quota`

## 5. 데이터 흐름 (Data Flow)

### 5.1 nonce 발급 (광고 시청 직전)
1. 인증 사용자가 `POST /api/ads/reward/issue-nonce` 호출.
2. `AdRewardNonceService.issueFor(userId)` → `ad_reward_nonce`에 (`nonce`=UUID, `userId`, `expiresAt`=now+TTL, `used`=false) INSERT.
3. 응답 `{ nonce, expiresAt }`. 클라이언트는 이 nonce를 AdMob SSV `user_id` 필드에 싣는다.

### 5.2 SSV 콜백 적립
1. AdMob → `GET /api/ads/google/ssv?...`
2. cc-242 `verifyAndStore`: 파싱 → ad_unit 검증 → 서명 검증 → `google_ad_ssv_events` 저장(신규/중복 판별). **결과(callback, isNew) 반환**(D4).
   - 서명 실패: cc-242가 기존대로 4xx/503 + 이벤트 미저장(BE-3 범위 외, 기존 동작 유지).
   - 중복(transactionId 기존 존재): `isNew=false` → 적립 건너뜀(이미 처리). recordTransaction 멱등 키가 2차 방어.
3. `isNew=true`면 컨트롤러가 `adRewardService.grantFromCallback(callback)` 호출 — **단일 `@Transactional`**:
   a. `callback.userId`(=nonce)로 `ad_reward_nonce` 조회. 없음/`expiresAt` 경과/`used=true` → 이벤트 `rewardStatus=REJECTED_INVALID_NONCE` UPDATE, 코인 없음, 종료.
   b. 해석된 `userId`로 `ad_reward_daily_quota` 행 UPSERT 후 `SELECT … FOR UPDATE`(per-user-per-day 행 락).
   c. `usedCount >= daily-limit` → 이벤트 `rewardStatus=REJECTED_OVER_QUOTA`, 코인 없음, 종료.
   d. 한도 내 → `usedCount += 1`; `ad_reward_nonce.used = true`; `recordTransaction(userId, coin-amount, AD_REWARD, "admob:reward:{transactionId}")`(BE-1); 이벤트 `rewardStatus=GRANTED`.
4. 컨트롤러는 (서명만 통과했다면) 거부 케이스라도 AdMob에 **200**을 반환(재시도 폭주 방지). 서명 실패는 cc-242의 4xx/503 그대로.

### 5.3 quota 조회
- `GET /api/ads/reward/quota` → `{ usedToday, dailyLimit, remaining, resetAtKst }`. KST 자정 리셋.

## 6. API 계약 (요약)

| Method | Path | 설명 |
| ------ | ---- | ---- |
| `POST` | `/api/ads/reward/issue-nonce` | 인증 사용자에게 단일 사용·단기 nonce 발급 → `{ nonce, expiresAt }` |
| `GET`  | `/api/ads/reward/quota` | 오늘 남은 시청 횟수 → `{ usedToday, dailyLimit, remaining, resetAtKst }` |
| `GET`  | `/api/ads/google/ssv` | **(cc-242 기존)** SSV 콜백 — 서명 검증 후 적립 연동 추가 |

## 7. 인수 기준 (Acceptance Criteria)

### nonce 발급
Given 인증 사용자가 광고 시청 직전 When `POST /api/ads/reward/issue-nonce` Then `ad_reward_nonce`에 단일 사용·미만료 nonce 1건 저장(`used=false`), 응답 `{ nonce, expiresAt }`.

### 한도 내 정상 적립
Given 미사용·미만료 nonce가 SSV `user_id`에 실려 있고 `(userId, kstDate)` usedCount가 한도 미만 When 서명 검증을 통과한 SSV 콜백이 신규 도착 Then 단일 트랜잭션에서 quota 행 락 → usedCount+1 → nonce.used=true → `recordTransaction("admob:reward:{transactionId}")`로 코인(설정값) 적립 → 이벤트 `GRANTED`. `GET /api/ads/reward/quota`에 remaining 감소 반영.

### 위조/만료/사용된 nonce
Given 서명은 통과했으나 `user_id`의 nonce가 없음/만료/이미 used When 콜백 처리 Then 이벤트 `REJECTED_INVALID_NONCE`, 코인 미적립, AdMob에 200.

### 일일 한도 초과
Given usedCount가 한도 도달 When 서명·신규 콜백 도착 Then 행 락으로 한도 재확인 후 이벤트 `REJECTED_OVER_QUOTA`, 코인 미적립, nonce.used 변경 없음, AdMob에 200. 동시 도착 두 콜백은 행 락 경쟁으로 정확히 한쪽만 GRANT(TOCTOU 방지).

### 중복 SSV 콜백
Given 동일 `transaction_id`로 두 번 도착 When 두 번째 처리 Then cc-242 dedup으로 `isNew=false` → 적립 건너뜀. 설령 적립 단계 도달해도 멱등 키 `admob:reward:{transactionId}` 충돌로 중복 적립 없음(이중 방어).

### quota 조회
Given 오늘 3회 시청·한도 10 When `GET /api/ads/reward/quota` Then `{ usedToday:3, dailyLimit:10, remaining:7, resetAtKst:"…" }`.

## 8. 테스트 전략

- **단위(mock)**: `AdRewardService.grantFromCallback` — 정상 적립 / invalid nonce / over quota / 중복(isNew=false). `AdRewardNonceService.issueFor`. 컨트롤러 `@WebMvcTest`(issue-nonce·quota).
- **통합(Testcontainers MySQL)**: 실제 V5 + 행 락 동시성(한도-1에서 서로 다른 nonce 두 콜백 동시 → 정확히 한쪽 GRANT, 다른 쪽 OVER_QUOTA), 멱등(동일 transactionId 두 번 → 코인 1회). `recordTransaction` 연동·원자성.
- cc-242 기존 테스트는 회귀 없이 통과(verifyAndStore 시그니처 변경 반영).

## 9. 범위 외 / 의존성 (Out Of Scope / Dependencies)

- **FE**: `issue-nonce` 호출 후 nonce를 AdMob SSV `user_id`에 주입(FE 티켓). 직접 userId/식별값 주입 금지.
- **INF-1**: AdMob 콘솔 SSV URL은 cc-242가 이미 등록·사용(`/api/ads/google/ssv`). 광고 단위 ID는 `app.ads.google.rewarded-ad-unit-id`.
- 서명 실패 응답 정책(현재 cc-242의 4xx/503)은 변경하지 않음(spec의 401+BAD_SIGNATURE ledger와 다르나 cc-242 소관).
- TNK 오퍼월·데일리 미션·도장 회복권 → Phase 2.
- 적립 코인 동적 조정(관리자 UI), 환수(revoke) → 범위 외.

## 10. Phase 1 설정값 (가설)

- 일일 시청 한도: 10 (`app.ads.reward.daily-limit`)
- 시청당 적립 코인: 40 (`app.ads.reward.coin-amount`)
- nonce TTL: 10분 (`app.ads.reward.nonce-ttl`)
- 일자·리셋·nonce 만료 판정: `Asia/Seoul`(KST)
