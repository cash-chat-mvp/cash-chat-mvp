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
| H | 친구 초대 | `/api/invite/*` | JWT | ⏳ 후속(슬라이스 4, §5) |

> FE 격리: 미구현 항목은 인터페이스(`*Repository`) 뒤 Fake/스텁으로 잠정 동작, BE 준비 시 Remote 교체(인터페이스 불변).

---

## 2. [E·신규] 행운 룰렛 — `/api/roulette/*`

관련 설계: `docs/superpowers/specs/2026-06-21-benefit-zone-roulette-design.md`

### 정책 (서버가 진실)
- 하루 **총 5회** 스핀 = **무료 1 + 광고 4**. 총량·무료수는 **서버 설정값**(추후 변경 가능).
- 상품(전부 **에너지**), **가중 확률(서버 제어)**: ⚡100 **1%** / ⚡10 **10%** / ⚡3 **70%** / 꽝(0) **19%**.
- 당첨 결정·에너지 지급은 **서버에서만**. 클라이언트는 결과를 표시(휠 애니메이션)만.
- 광고 추가 스핀: 리워드 광고 1편 시청 = **스핀 크레딧 +1**(에너지 직접 지급 아님 — C의 에너지 리워드와 다른 보상 타입). SSV 검증 필수.

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
  "availableSpins": 1,
  "adSpinsRemaining": 4,
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
| `freeSpinAvailable` | Boolean | 무료 1회 미사용 여부 |
| `availableSpins` | Int | 지금 즉시 돌릴 수 있는 스핀 수(무료 미사용분 + 적립 크레딧) |
| `adSpinsRemaining` | Int | 오늘 광고로 더 얻을 수 있는 스핀 수(= dailyLimit − spinsUsedToday − availableSpins, 음수 불가) |
| `resetAtKst` | String | 자정 리셋 시각(KST) |
| `segments[]` | Array | 휠 표시용 칸. `index`는 0-based 고정, `prize`∈`JACKPOT_100·E10·E3·MISS`, `energy`는 지급 에너지 |

비고: `segments`는 표시용 고정 배치(확률과 무관). 당첨은 §2.3 `spin`이 서버 확률로 정하고 일치하는 `segmentIndex`를 돌려준다.

### 2.2 광고 추가 스핀용 nonce — `POST /api/roulette/issue-nonce`
```
POST /api/roulette/issue-nonce
Authorization: Bearer <accessToken>
```
응답 200:
```json
{ "nonce": "…", "expiresAt": "2026-06-21T12:34:56+09:00" }
```
- FE는 nonce를 AdMob `customData`(SSV)로 넣어 리워드 광고를 노출한다.
- 에러: 409 `AD_SPINS_EXHAUSTED` — 오늘 광고 스핀 한도 도달(`adSpinsRemaining == 0`).

### 2.3 스핀 — `POST /api/roulette/spin`
```
POST /api/roulette/spin
Authorization: Bearer <accessToken>
```
처리: `availableSpins ≥ 1` 확인 → 서버 가중 확률로 상품 결정 → 에너지 지급(>0일 때) → `spinsUsedToday += 1`, `availableSpins -= 1`.
응답 200:
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

에러:
- 409 `NO_SPIN_AVAILABLE` — `availableSpins == 0`(무료 소진 + 크레딧 0).
- 409 `DAILY_LIMIT_REACHED` — `spinsUsedToday >= dailyLimit`.

### 2.4 광고 SSV 적립 (서버-투-서버) — 스핀 크레딧 +1
- AdMob SSV 콜백 → 서버가 nonce 검증 후 **스핀 크레딧 +1**(에너지 아님). `transactionId`/nonce 기준 **멱등**, `dailyLimit` 초과분은 적립 거부.
- FE는 광고 종료 후 `GET /api/roulette/status`를 폴링해 `availableSpins` 증가를 확인(리워드 카드 `awaitRewardApplied`의 `usedToday` 폴링과 동일 구조, 기준만 `availableSpins`).

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

## 5. [H·후속] 친구 초대 — `/api/invite/*` (슬라이스 4 예고)

아직 설계 전. 예상 필요: 초대 코드/링크 발급, 초대 수락·검증, 초대 보상 적립(어뷰징 방지). 슬라이스 4 착수 시 본 문서에 상세 추가.

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
