# 혜택존 — 백엔드 API 요구 명세 (FE → BE)

- 작성일: 2026-06-07
- 작성: FE (혜택존 Phase 1~4)
- 대상: 백엔드 (`com.wnl.cashchat.api`)
- 관련: `docs/superpowers/specs/2026-06-07-benefit-zone-foundation-attendance-design.md`

## 0. 배경

혜택존 FE 구현 중, **현재 백엔드에 없는 엔드포인트 3종**이 필요합니다. FE는 해당 부분을 인터페이스 뒤로 격리해 두고(예: `PointsRepository`) 잠정 동작시키되, 아래 계약대로 BE가 구현되면 실연동으로 교체합니다.

공통 규약(기존 코드 기준):
- 인증: `Authorization: Bearer <accessToken>`. 컨트롤러에서 `Authentication.userId(): Long` 패턴 사용.
- 시간대: 일자/리셋 경계는 **KST(`Asia/Seoul`)** 기준.
- 에러 응답: 기존 `com.wnl.cashchat.api.common.web.response.ErrorResponse` 형식 재사용.
- 적립/차감은 `UserPointService` 트랜잭션 경유.

우선순위: **(P0) 포인트 잔액 → (P1) 데일리 미션 → (P2) TNK 오퍼월 webhook**.

---

## 1. [P0] 코인 잔액 조회 — `GET /api/points/me`

### 필요 이유
혜택존/상점 헤더의 코인 잔액 표시 및 적립/소비 후 갱신. **현재 `UserPointService`에 컨트롤러가 없어 잔액을 가져올 경로가 없음**(블로커).

### 요청
```
GET /api/points/me
Authorization: Bearer <accessToken>
```

### 응답 200
```json
{
  "balance": 1250
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `balance` | Long | 현재 사용자 코인 잔액(음수 불가) |

### 에러
- 401: 인증 실패(토큰 없음/만료) → 기존 인증 필터 흐름.

### 비고
- 거래 내역(`PointTransaction`)은 본 엔드포인트 범위 아님. 필요 시 별도 `GET /api/points/me/transactions`로 분리 요청 예정(현재 불요).
- 신규 사용자도 `UserPointService.ensureInitialized` 기준 0 또는 초기값 반환(빈 경우 404 대신 `balance: 0`).

---

## 2. [P1] 데일리 미션

> ⚠️ FE 진행사항에는 "미션 기능 완료"로 공유되었으나, 실제 백엔드에 `domain/mission` 도메인이 없음을 확인. 본 명세로 신규 구현 필요. 이미 구현되어 있다면 실제 엔드포인트/스키마를 회신 바람.

### 도메인(제안)
`domain/mission/`: `MissionTemplate`(미션 풀), `UserDailyMission`(사용자별 당일 배정·진행·수령 상태).

### 미션 풀(타입)
| code | 설명 | 진행 기준 | 보상 코인(예시) |
|---|---|---|---|
| `CHAT_N` | 채팅 N회 보내기 | 진행 카운트/목표 | 50 |
| `INVITE_FRIEND` | 친구 1명 초대 | 0/1 | 200 |
| `EVOLVE_TRY` | 진화 시도 N회 | 카운트/목표 | 30 |
| `VISIT_SHOP` | 상점 방문 | 0/1 | 10 |
| `WATCH_AD_N` | 리워드 광고 N회 시청 | 카운트/목표 | 40 |
| `COUPANG_CLICK` | 쿠팡 카드 1회 클릭 | 0/1 | 20 |

규칙: 매일 KST 자정에 풀에서 **랜덤 3개** 배정. 진행도는 서버가 이벤트 기준으로 증가(또는 FE가 진행 이벤트 보고 — 아래 2.4 협의 필요).

### 2.1 당일 미션 조회 — `GET /api/missions/me`
요청:
```
GET /api/missions/me
Authorization: Bearer <accessToken>
```
응답 200:
```json
{
  "date": "2026-06-07",
  "refreshAvailable": true,
  "missions": [
    {
      "id": 1012,
      "code": "CHAT_N",
      "title": "채팅 10회 보내기",
      "rewardCoin": 50,
      "progress": 3,
      "target": 10,
      "status": "IN_PROGRESS"
    },
    {
      "id": 1013,
      "code": "INVITE_FRIEND",
      "title": "친구 1명 초대하기",
      "rewardCoin": 200,
      "progress": 0,
      "target": 1,
      "status": "IN_PROGRESS"
    },
    {
      "id": 1014,
      "code": "EVOLVE_TRY",
      "title": "진화 시도 1회",
      "rewardCoin": 30,
      "progress": 1,
      "target": 1,
      "status": "COMPLETED"
    }
  ]
}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `date` | String(`yyyy-MM-dd`) | KST 기준 배정 날짜 |
| `refreshAvailable` | Boolean | 오늘 새로고침권 사용 가능 여부(광고 1회/일) |
| `missions[].id` | Long | 당일 미션 인스턴스 ID(수령/진행 호출에 사용) |
| `missions[].code` | String | 미션 타입 코드 |
| `missions[].title` | String | 표시 문구 |
| `missions[].rewardCoin` | Long | 수령 시 적립 코인 |
| `missions[].progress` | Int | 현재 진행도 |
| `missions[].target` | Int | 목표치 |
| `missions[].status` | Enum | `IN_PROGRESS` \| `COMPLETED`(미수령) \| `CLAIMED`(수령완료) |

### 2.2 미션 보상 수령 — `POST /api/missions/{id}/claim`
요청:
```
POST /api/missions/1014/claim
Authorization: Bearer <accessToken>
```
응답 200:
```json
{ "missionId": 1014, "awardedCoin": 30, "newBalance": 1280, "status": "CLAIMED" }
```
에러:
- 409 `MISSION_NOT_COMPLETED` — 진행도가 목표 미달.
- 409 `MISSION_ALREADY_CLAIMED` — 이미 수령.
- 404 — 해당 미션 인스턴스가 사용자 소유 아님/존재하지 않음.

### 2.3 미션 새로고침 — `POST /api/missions/refresh`
요청:
```
POST /api/missions/refresh
Authorization: Bearer <accessToken>
```
- 정책: **광고 1회 시청 소모**(하루 1회). 광고 시청 검증과 연계 방식 협의 필요(아래 2.4).

응답 200: 2.1과 동일한 `GET /api/missions/me` 페이로드(새로 배정된 3개).
에러:
- 409 `REFRESH_NOT_AVAILABLE` — 오늘 새로고침권 이미 사용/조건 미충족.

### 2.4 협의 필요 항목
- **진행도 증가 주체:** 서버 이벤트 집계(권장) vs FE 진행 보고. 채팅/광고/진화 등은 이미 서버가 인지 가능한 이벤트이므로 서버 집계 선호. `VISIT_SHOP`, `COUPANG_CLICK`처럼 클라 행위만 있는 경우 `POST /api/missions/{id}/progress` 보고 엔드포인트가 필요할 수 있음 — 필요 여부 회신 바람.
- **새로고침의 "광고 시청 소모" 연동 방식:** 광고 nonce/SSV와 어떻게 연결할지(예: 시청 완료 후 발급된 토큰을 refresh 요청에 동봉).

---

## 3. [P2] TNK Factory 오퍼월 적립 — Webhook

> FE는 TNK SDK를 직접 임베드(`setUserName(userId)`)하고, **코인 적립은 TNK 서버 → 우리 BE webhook → `UserPointService` 적립** 경로로만 처리. FE는 적립을 직접 하지 않음.

### 3.1 적립 콜백 수신 — `POST /api/offerwall/tnk/callback`
- 인증: 사용자 JWT 아님. **TNK 발급 서명/시크릿 검증**(TNK 콘솔에서 발급되는 콜백 키). 화이트리스트 IP/서명 방식은 TNK 문서 기준으로 BE 결정.
- 호출 주체: TNK 서버(서버-투-서버).

요청(예시 — TNK 실제 파라미터 명세는 앱 등록 후 TNK 콘솔/문서 기준으로 확정):
```
POST /api/offerwall/tnk/callback
Content-Type: application/x-www-form-urlencoded  (또는 TNK 규격)

userName=<우리 userId>&transactionId=<TNK 고유 거래ID>&point=<적립 포인트>&campaignId=<...>&signature=<...>
```
| 필드 | 설명 |
|---|---|
| `userName` | FE가 `setUserName`으로 넣은 우리 사용자 식별자(= userId 또는 익명 ID) |
| `transactionId` | TNK 거래 고유 ID — **멱등 키**(중복 적립 방지) |
| `point` | 적립 포인트(코인 환산 정책 적용) |
| `signature` | TNK 서명 — 검증 필수 |

처리:
1. 서명 검증 실패 → 4xx(TNK 재전송 정책에 맞춰 결정).
2. `transactionId` 기준 **멱등 처리**(이미 적립된 거래면 200 OK로 무시) — `domain/ad`의 SSV 멱등(`grantFromCallback`) 패턴 참고.
3. 신규면 `UserPointService`로 적립.

응답: TNK가 요구하는 성공 포맷(보통 `200 OK` 또는 특정 바디) — TNK 문서 기준.

### 3.2 (선택) 적립 내역 조회 — FE 폴링용
- 오퍼월은 적립까지 지연이 있어, FE가 잔액 갱신을 위해 `GET /api/points/me`(§1)를 폴링/리프레시하는 것으로 충분. 별도 엔드포인트 불요.

### 3.3 선결 조건
- TNK 앱 등록 및 콜백 키/파라미터 스펙 확보(현재 미완). 확보 전까지 BE 구현 보류 가능, FE는 SDK 임베드 구조만 선행.

---

## 4. 요약 표

| 우선순위 | 메서드 | 경로 | 인증 | 상태 |
|---|---|---|---|---|
| P0 | GET | `/api/points/me` | JWT | 신규 필요(블로커) |
| P1 | GET | `/api/missions/me` | JWT | 신규 필요 |
| P1 | POST | `/api/missions/{id}/claim` | JWT | 신규 필요 |
| P1 | POST | `/api/missions/refresh` | JWT | 신규 필요 |
| P1? | POST | `/api/missions/{id}/progress` | JWT | 협의(클라 행위 미션 한정) |
| P2 | POST | `/api/offerwall/tnk/callback` | TNK 서명 | TNK 등록 후 |

## 5. FE 측 격리 현황(참고)
- `GET /api/points/me`: `shared/.../points/PointsRepository` 인터페이스 뒤 `LocalPointsRepository`로 잠정 동작. BE 준비 시 `RemotePointsRepository`로 교체(인터페이스 불변).
- 미션/오퍼월: 각 Phase 착수 시 동일 패턴(인터페이스 + 잠정 어댑터)으로 격리 예정.
