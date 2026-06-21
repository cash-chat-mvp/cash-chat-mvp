# 혜택존(CC-355) — 백엔드 API 요구 통합 명세 (FE → BE)

- 작성일: 2026-06-21
- 작성: FE (혜택존 슬라이스: 출석·잔액·리워드광고·배너·오퍼월·**룰렛(신규)**·미션)
- 대상: 백엔드 (`com.wnl.cashchat.api`)
- 비고: 2026-06-07 `benefit-zone-be-api-requests.md`를 **갱신·통합**한 문서. CC-355에서 작업한 모든 기능의 BE API 필요/완료 상태를 한곳에 정리한다.

## 0. 공통 규약 (기존 코드 기준)
- 인증: `Authorization: Bearer <accessToken>`. 컨트롤러에서 `Authentication.userId(): Long` 패턴.
- 시간대: 일자/리셋 경계는 **KST(`Asia/Seoul`)**.
- 에러: `com.wnl.cashchat.api.common.web.response.ErrorResponse` 형식 재사용.
- 적립/차감: `UserPointService`(코인) / 에너지 서비스 트랜잭션 경유.
- 광고 검증: AdMob SSV. 기존 `domain/ad`의 nonce 발급 + SSV 콜백 멱등 적립(`grantFromCallback`) 패턴 재사용.

## 1. 현황 요약

| # | 기능 | 메서드·경로 | 인증 | 상태 |
|---|---|---|---|---|
| A | 코인 잔액 | GET `/api/points/me` | JWT | ✅ 구현됨 (CC-356) |
| B | 출석 | GET/POST `/api/attendance/*` | JWT | ✅ 구현됨 |
| C | 리워드광고→에너지 | `/api/ads/reward/quota·issue-nonce` + SSV | JWT/SSV | ✅ 구현됨 |
| D | 오퍼월 사용자 토큰 | TNK user-token API | JWT | ✅ 구현됨 |
| E | **행운 룰렛** | `/api/roulette/*` (+SSV) | JWT/SSV | 🔴 **신규 필요(본 문서 §2)** |
| F | 데일리 미션 | `/api/missions/*` | JWT | 🔴 필요 (§3) |
| G | 오퍼월 TNK 적립 | POST `/api/offerwall/tnk/callback` | TNK 서명 | 🔴 TNK 등록 후 (§4) |
| H | 친구 초대 | `/api/invite/me`·`/redeem` | JWT | 🔴 필요 (§5) |

> FE 격리: 미구현 항목은 인터페이스(`*Repository`) 뒤 Fake/스텁으로 잠정 동작, BE 준비 시 Remote 교체(인터페이스 불변).

---

## 2. [E·신규] 행운 룰렛 — `/api/roulette/*`

관련 설계: `docs/superpowers/specs/2026-06-21-benefit-zone-roulette-design.md`

### 정책 (서버가 진실)
- 하루 **총 5회** 스핀 = **무료 1 + 광고 4**. 총량·무료수는 **서버 설정값**(추후 변경 가능).
- 상품(전부 **에너지**), **가중 확률(서버 제어)**: ⚡100 **1%** / ⚡10 **10%** / ⚡3 **70%** / 꽝(0) **19%**.
- 당첨 결정·에너지 지급은 **서버에서만**. 클라이언트는 결과를 표시(휠 애니메이션)만.
- **스핀 정책**: 하루 **첫 1회는 무료**(광고 없이), **2회차부터는 매 스핀마다 광고를 봐야** 돌릴 수 있다. 광고 = 매 스핀의 게이트이며 "크레딧 적립" 개념은 없다. 광고를 끝까지 보면 그 즉시 1회 스핀.
- 광고 검증: nonce 발급 → 광고 customData=nonce → AdMob SSV로 서버가 검증 → 검증된 nonce로 스핀(spin-with-ad). 에너지 직접 지급 아님(스핀의 추첨 결과로 에너지 지급).

### 2.1 룰렛 상태 조회 — `GET /api/roulette/status`
```
GET /api/roulette/status
Authorization: Bearer <accessToken>
```
응답 200:
```json
{
  "date": "2026-06-21",
  "dailyLimit": 5,
  "spinsUsedToday": 0,
  "freeSpinAvailable": true,
  "remaining": 5,
  "resetAtKst": "2026-06-22T00:00:00+09:00",
  "segments": [
    { "index": 0, "prize": "JACKPOT_100", "energy": 100 },
    { "index": 1, "prize": "E3",  "energy": 3 },
    { "index": 2, "prize": "MISS", "energy": 0 },
    { "index": 3, "prize": "E10", "energy": 10 },
    { "index": 4, "prize": "E3",  "energy": 3 },
    { "index": 5, "prize": "MISS", "energy": 0 },
    { "index": 6, "prize": "E10", "energy": 10 },
    { "index": 7, "prize": "E3",  "energy": 3 }
  ]
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `dailyLimit` | Int | 하루 총 스핀 상한(예: 5) |
| `spinsUsedToday` | Int | 오늘 수행한 스핀 수 |
| `freeSpinAvailable` | Boolean | 하루 첫 무료 스핀 미사용 여부(true 면 `spin`, false 면 `spin-with-ad`) |
| `remaining` | Int | 오늘 더 돌릴 수 있는 횟수(= dailyLimit − spinsUsedToday, 음수 불가). 0 이면 한도 도달 |
| `resetAtKst` | String | 자정 리셋 시각(KST) |
| `segments[]` | Array | 휠 표시용 칸. `index`는 0-based 고정, `prize`∈`JACKPOT_100·E10·E3·MISS`, `energy`는 지급 에너지 |

비고: `segments`는 표시용 고정 배치(확률과 무관). 당첨은 spin 류 API가 서버 확률로 정하고 일치하는 `segmentIndex`를 돌려준다.

### 2.2 무료 첫 스핀 — `POST /api/roulette/spin`
```
POST /api/roulette/spin
Authorization: Bearer <accessToken>
```
처리: `freeSpinAvailable == true` 확인 → 서버 가중 확률로 상품 결정 → 에너지 지급(>0일 때) → `spinsUsedToday += 1`, `freeSpinAvailable = false`.
응답 200: §2.4 공통 스핀 응답.
에러:
- 409 `FREE_SPIN_USED` — 오늘 무료 스핀을 이미 사용(이후엔 `spin-with-ad` 사용).
- 409 `DAILY_LIMIT_REACHED` — `remaining == 0`.

### 2.3 광고 게이트 스핀 — `POST /api/roulette/issue-nonce` → `POST /api/roulette/spin-with-ad`
2회차부터의 스핀. nonce 발급 → 광고(customData=nonce) → SSV 검증 → 검증된 nonce로 스핀.

**(a) nonce 발급** `POST /api/roulette/issue-nonce`
응답 200: `{ "nonce": "…", "expiresAt": "2026-06-21T12:34:56+09:00" }`
- FE는 nonce를 AdMob `customData`(SSV)로 넣어 리워드 광고를 노출.
- 에러: 409 `DAILY_LIMIT_REACHED` — `remaining == 0`.

**(b) 광고 시청 후 스핀** `POST /api/roulette/spin-with-ad`
요청 바디: `{ "nonce": "…" }`
처리: 해당 nonce가 **AdMob SSV로 검증 완료**됐는지 확인 → 가중 확률 추첨 → 에너지 지급 → `spinsUsedToday += 1`. nonce는 1회용(멱등).
응답 200: §2.4 공통 스핀 응답.
에러:
- 403 `AD_NOT_VERIFIED` — 해당 nonce의 SSV 콜백 미수신/검증 실패. (SSV 지연 가능 — 서버가 짧게 대기하거나 FE가 잠시 후 재시도)
- 409 `NONCE_ALREADY_USED` — 이미 사용된 nonce.
- 409 `DAILY_LIMIT_REACHED` — `remaining == 0`.

### 2.4 공통 스핀 응답(`spin`·`spin-with-ad`)
```json
{
  "prize": "E3",
  "segmentIndex": 4,
  "awardedEnergy": 3,
  "status": { "...": "§2.1 RouletteStatus 갱신본" }
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `prize` | Enum | `JACKPOT_100·E10·E3·MISS` |
| `segmentIndex` | Int | 당첨 상품과 일치하는 표시 칸 인덱스(FE가 이 칸으로 휠 정지) |
| `awardedEnergy` | Int | 지급된 에너지(꽝=0) |
| `status` | Object | 갱신된 룰렛 상태(클라 재조회 절약) |

### 2.5 확률·구성 협의
- 확률 테이블·상품 구성은 서버 설정(Remote Config 또는 DB)로 두어 코드 수정 없이 조정 가능하게 권장.
- `segments` 배치(칸 수 8, 잭팟 1개 등)는 FE 표시 합의값 — 변경 시 FE와 동기화.

---

## 3. [F·필요] 데일리 미션 — `/api/missions/*`

> 2026-06-07 명세 유지. 백엔드에 `domain/mission` 미존재 확인. 이미 구현됐다면 실제 스키마 회신 바람.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/missions/me` | 당일 배정 3개(진행/목표/상태) + `refreshAvailable` |
| POST | `/api/missions/{id}/claim` | 완료 미션 보상 코인 수령 |
| POST | `/api/missions/refresh` | 광고 1회 소모로 미션 재배정(하루 1회) |
| POST | `/api/missions/{id}/progress` | (협의) 클라 행위 미션 진행 보고 |

- 미션 풀/스키마/에러코드 상세는 `docs/superpowers/specs/2026-06-07-benefit-zone-be-api-requests.md` §2 참조.
- 협의: 진행도 증가 주체(서버 집계 권장 vs FE 보고), refresh의 광고 시청 연동 방식.

---

## 4. [G·TNK 등록 후] 오퍼월 적립 Webhook — `POST /api/offerwall/tnk/callback`

> FE는 TNK SDK 임베드(`setUserName(userId)`)만. 코인 적립은 **TNK 서버 → BE webhook → `UserPointService`** 경로로만.

- 인증: 사용자 JWT 아님. **TNK 서명/시크릿 검증**(TNK 콘솔 발급 키).
- `transactionId` 기준 **멱등**(중복 적립 방지) — `domain/ad` SSV 멱등 패턴 참고.
- 상세(파라미터·서명): `2026-06-07-benefit-zone-be-api-requests.md` §3. 선결: TNK 앱 등록·콜백 스펙 확보.

---

## 5. [H·신규] 친구 초대 — `/api/invite/*`

관련 설계: `docs/superpowers/specs/2026-06-21-benefit-zone-friend-invite-design.md`

### 정책 (서버가 진실)
- 방식: **추천 코드**(딥링크 미사용). 각 사용자에게 고유 코드 부여.
- 추천 성공(친구가 코드 입력·가입 완료) 시: **초대자 +코인**, **가입자 +에너지**. 금액·한도(초대 최대 N명, 1인 1회 redeem)는 **서버 설정값**.
- 검증: 자기 코드 금지, 1인 1회만, 신규/적격 계정만, 코드 유효성. 적립은 `UserPointService`(코인)·에너지 서비스 트랜잭션.

### 5.1 내 초대 정보 — `GET /api/invite/me`
```
GET /api/invite/me
Authorization: Bearer <accessToken>
```
응답 200:
```json
{
  "myCode": "ABC123",
  "invitedCount": 3,
  "redeemAvailable": true,
  "rewardCoin": 500,
  "rewardEnergy": 10
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `myCode` | String | 내 추천 코드(공유용) |
| `invitedCount` | Int | 내 코드로 가입한 친구 수(보상 지급된) |
| `redeemAvailable` | Boolean | 내가 추천 코드를 입력할 수 있는지(미사용·적격 기간 내) |
| `rewardCoin` | Int | 초대 성공 시 초대자 코인(표시용) |
| `rewardEnergy` | Int | 추천 코드 입력 시 가입자 에너지(표시용) |

### 5.2 추천 코드 입력 — `POST /api/invite/redeem`
```
POST /api/invite/redeem
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "code": "XYZ789" }
```
처리: 코드 검증(존재·자기코드 아님·미사용·적격) → 입력자에게 에너지 지급, **코드 소유자(초대자)에게 코인 지급**(멱등: 1인 1회).
응답 200:
```json
{ "success": true, "awardedEnergy": 10, "message": null }
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | Boolean | 적립 성공 여부 |
| `awardedEnergy` | Int | 입력자에게 지급된 에너지 |
| `message` | String? | 실패 사유(표시용) |

에러:
- 409 `ALREADY_REDEEMED` — 이미 추천 코드를 사용함.
- 404 `INVALID_CODE` — 존재하지 않는 코드.
- 409 `SELF_REFERRAL` — 자기 코드 입력.
- 403 `NOT_ELIGIBLE` — 적격 아님(예: 가입 후 기간 초과).

### 5.3 온보딩 입력 연동 (협의)
- 온보딩(가입 전) 코드 입력은 가입 토큰 발급 후 `POST /api/invite/redeem` 호출, 또는 **가입 페이로드에 `referralCode` 동봉** 후 서버가 가입 완료 시 적립. 방식은 BE와 협의해 확정.

---

## 6. 이미 구현된 항목 (참고)

| 기능 | 경로 | 컨트롤러 |
|---|---|---|
| 코인 잔액 | `GET /api/points/me` | `point/web/controller/PointController` |
| 출석 | `/api/attendance/*` | `attendance/web/controller/AttendanceController` |
| 리워드광고 쿼터/nonce | `/api/ads/reward/quota·issue-nonce` | `ad/web/controller/AdRewardController` |
| AdMob SSV 콜백 | (SSV) | `ad/web/controller/GoogleAdSsvController` |

- **리워드 카드(슬라이스 2)**는 위 `/api/ads/reward/*`를 재사용했고 **신규 BE 불요**. 단, 추후 "진입점별(채팅/혜택존) 한도 분리"가 필요하면 별도 협의(현재는 한도 공유).

## 7. FE 격리 현황
- 잔액: `PointsRepository`(Local→Remote 교체 완료/예정).
- 룰렛: `RouletteRepository` + `FakeRouletteRepository`(스텁, 로컬 가중 랜덤). BE 준비 시 `RemoteRouletteRepository` 교체(인터페이스 불변).
- 미션/오퍼월 콜백: 동일 패턴으로 격리.
