# 혜택존(Reward) Phase 1 — 출석체크 · 리워드 광고 Spec

> 상태: Draft
> 범위: Phase 1 (출석체크 + AdMob 리워드 광고)
> 관련 기획: [Confluence — 혜택존](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14909530), [Confluence — overview](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14975052/Cash+Chat+-+overview), `docs/planning/02-rewards-zone.md`

## 목표 (Goal)

혜택존 탭의 Phase 1로 두 가지 코인 적립 채널을 제공한다.

1. **일일 출석체크**: 하루 1회 도장 → 보상 코인(+옵션 부가 보상). 연속 출석 카운트와 누적 일차별 보상 차등.
2. **AdMob 리워드 광고 시청**: 1일 N회까지 광고 시청 → 서버 SSV 검증 후 코인 적립.

두 채널 모두 `domain/point/UserPointService`의 멱등성 보장 트랜잭션을 통해 코인을 적립한다. 본 spec은 `UserPointService.recordTransaction(idempotencyKey)` 확장을 함께 다룬다 (Shop spec과 공유되는 선결 조건).

## 유저 스토리 (User Story)

### Story 1: 신규 출석 도장

신규/기존 사용자는 혜택존 탭에서 오늘자 도장을 1회 찍어 코인을 받고 싶다. 어제 도장을 찍었다면 연속 일차가 1 증가하고, 끊겼다면 1일차로 리셋된다.

### Story 2: 누적 일차 보너스

연속 출석 7일/14일/30일 시점에는 코인 외 부가 보상(진화석, 확률 부적, 보호권)을 함께 받고 싶다. 보너스는 해당 누적 일차 도달 시 1회만 지급된다.

### Story 3: 리워드 광고 시청

사용자는 혜택존의 [지금 시청] 버튼으로 AdMob 리워드 광고를 보고 코인을 받고 싶다. 광고를 끝까지 보지 않으면 코인이 지급되지 않는다.

### Story 4: 광고 일일 한도

운영 정책상 사용자당 하루 N회로 광고 시청을 제한하고 싶다. 한도를 초과하면 시청 버튼은 비활성화되고, KST 자정에 리셋된다.

### Story 5: 적립 무결성

백엔드는 AdMob이 동일한 reward callback을 재전송하거나 동일 nonce가 동시에 도착할 때 코인을 중복 적립하지 않아야 한다. 출석도 마찬가지로 동일 일자 중복 도장을 거부해야 한다.

## 인수 기준 (Acceptance Criteria)

### 첫 출석

Given 사용자가 가입 직후이고 오늘 출석 기록이 없다
When 사용자가 `POST /api/attendance/check-in`을 호출한다
Then 백엔드는 `attendance_log`에 오늘자 1행을 기록한다
And 연속 출석 일차는 1로 저장된다
And 1일차 보상 시드값(예: +20 코인)이 `UserPointService.recordTransaction`을 통해 멱등성 키 `attendance:{userId}:{yyyy-MM-dd}`로 적립된다
And 응답에는 적립된 코인, 연속 일차, 다음 보상 미리보기가 포함된다.

### 같은 날 중복 출석

Given 사용자가 오늘 이미 출석을 찍었다
When 같은 사용자가 같은 날 `POST /api/attendance/check-in`을 다시 호출한다
Then 백엔드는 409 도메인 에러(`ALREADY_CHECKED_IN`)로 거부한다
And `attendance_log`에 추가 행이 생기지 않는다
And 코인이 추가로 적립되지 않는다.

### 연속 출석 카운트 증가

Given 사용자의 가장 최근 출석일이 어제(KST 기준)이다
When 오늘 출석을 찍는다
Then 연속 일차는 어제 일차 + 1로 저장된다.

### 연속 출석 끊김

Given 사용자의 가장 최근 출석일이 2일 이상 전(KST 기준)이다
When 오늘 출석을 찍는다
Then 연속 일차는 1로 리셋된다.

### 7일/14일/30일 누적 일차 보너스

Given 사용자의 누적 출석 일차가 시드 테이블의 보너스 지급 일차(7/14/30)에 도달한다
When 해당 회차 출석을 찍는다
Then 코인 외 부가 보상(진화석/확률 부적/보호권 등) 시드값이 추가로 지급된다
And 본 인수 기준은 누적 1~30일 범위만 정의한다 — 31일 이후 사이클 정책은 Phase 1 범위 외(부록 참고).

### 캘린더 조회

Given 사용자가 이번 달 출석을 7일 동안 찍었다
When 사용자가 `GET /api/attendance/me`를 호출한다
Then 응답은 `{ year, month, checkedDays:[1..7], currentStreak:7, todayChecked:true, nextRewardPreview:{...} }` 형태로 반환된다.

### 광고 일일 한도 내 시청

Given 사용자의 오늘 광고 시청 횟수가 일일 한도 미만이다
When AdMob이 SSV 콜백을 `POST /api/ads/ssv/admob`으로 보낸다
And 백엔드가 query string의 `signature`를 AdMob 공개키로 검증한다
And `custom_data` 안의 `userId`, `nonce`가 유효하다
Then `UserPointService.recordTransaction`이 멱등성 키 `admob:reward:{nonce}`로 코인을 적립한다
And `ad_reward_ledger`에 `status=GRANTED`로 콜백 메타가 저장된다
And 같은 사용자의 `GET /api/ads/reward/quota` 응답에 남은 횟수가 감소된 값으로 반영된다.

### 광고 일일 한도 초과

Given 사용자의 오늘 광고 시청 횟수가 일일 한도에 도달했다
When 새 SSV 콜백이 도착한다
Then 백엔드는 적립을 거부한다
And `ad_reward_ledger`에 `status=REJECTED`, `reason=OVER_QUOTA`로 기록한다
And 코인이 적립되지 않는다
And AdMob에는 200을 반환한다 (재시도 폭주 방지).

### SSV 서명 검증 실패

Given AdMob 콜백 query string의 `signature`가 공개키 검증에 실패한다
When 백엔드가 콜백을 처리한다
Then 백엔드는 401로 응답한다
And `ad_reward_ledger`에 `status=REJECTED`, `reason=BAD_SIGNATURE`로 기록한다
And 코인을 적립하지 않는다.

### SSV 콜백 중복

Given 동일한 `nonce`로 두 번 SSV 콜백이 도착한다
When 백엔드가 두 번째 콜백을 처리한다
Then 멱등성 키 `admob:reward:{nonce}` 충돌로 한 번만 적립된다
And 두 번째 콜백에도 200을 반환한다.

### Quota 조회

Given 사용자가 오늘 광고를 3회 시청했고 한도는 10회다
When 사용자가 `GET /api/ads/reward/quota`를 호출한다
Then 응답은 `{ usedToday:3, dailyLimit:10, remaining:7, resetAtKst:"..." }` 형태로 반환된다.

## API 계약 (요약)

| Method | Path | 설명 |
| ------ | ---- | ---- |
| `POST` | `/api/attendance/check-in` | 오늘 도장 + 보상 응답 |
| `GET`  | `/api/attendance/me?year&month` | 월간 캘린더 + 연속일 + 오늘 여부 |
| `POST` | `/api/ads/ssv/admob` | AdMob 서버 SSV 콜백 (서명 검증, 멱등) |
| `GET`  | `/api/ads/reward/quota` | 오늘 남은 시청 횟수 |

## 사용자 흐름 (User Flow)

### 출석체크

1. 사용자가 혜택존 탭에 진입한다.
2. 프론트가 `GET /api/attendance/me`로 이번 달 캘린더와 오늘 도장 여부를 조회한다.
3. 사용자가 [출석 도장 찍기]를 누른다.
4. 프론트가 `POST /api/attendance/check-in`을 호출한다.
5. 백엔드가 멱등성 키로 코인을 적립하고 적립 결과를 응답한다.
6. 프론트가 도장 애니메이션과 보상 토스트를 표시한다.

### 리워드 광고

1. 사용자가 혜택존 탭의 [지금 시청] 버튼을 누른다.
2. 프론트가 `GET /api/ads/reward/quota`로 잔여 횟수를 확인한다.
3. 프론트가 AdMob SDK로 리워드 광고를 로드하고 노출하며, `custom_data`에 `{userId, nonce}` JSON을 실어 보낸다.
4. 사용자가 광고를 끝까지 시청한다.
5. AdMob이 백엔드로 SSV 콜백을 전송한다.
6. 백엔드가 서명을 검증하고 멱등성 키로 코인을 적립한다.
7. 프론트가 광고 종료 직후 `GET /api/ads/reward/quota`를 재호출해 적립 반영을 확인한다.

### 순차 흐름도 (Sequence Diagram)

#### 출석체크

```mermaid
sequenceDiagram
    participant User as 사용자
    participant FE as 혜택존 화면
    participant API as CashChat 백엔드
    participant DB as DB

    User->>FE: 혜택존 탭 진입
    FE->>API: GET /api/attendance/me
    API->>DB: 이번 달 attendance_log 조회
    DB-->>API: 캘린더 + 오늘 도장 여부
    API-->>FE: 캘린더 + 연속 일차
    User->>FE: 출석 도장 찍기
    FE->>API: POST /api/attendance/check-in
    API->>DB: 오늘자 중복 체크
    alt 이미 찍음
        API-->>FE: 409 ALREADY_CHECKED_IN
    else 미찍음
        API->>DB: attendance_log INSERT
        API->>DB: 연속 일차 갱신
        API->>API: recordTransaction(key="attendance:{userId}:{date}")
        API->>DB: point_transaction INSERT (코인 + 옵션 부가 보상)
        API-->>FE: 적립 결과 (코인, 연속 일차, 보너스)
        FE->>User: 도장 애니메이션 + 보상 토스트
    end
```

#### 리워드 광고

```mermaid
sequenceDiagram
    participant User as 사용자
    participant FE as 혜택존 화면
    participant SDK as AdMob SDK
    participant AdMob as AdMob 서버
    participant API as CashChat 백엔드
    participant DB as DB

    User->>FE: 지금 시청 탭
    FE->>API: GET /api/ads/reward/quota
    API-->>FE: usedToday, dailyLimit, remaining
    FE->>FE: nonce 생성 (UUID)
    FE->>SDK: loadRewardedAd(customData={userId, nonce})
    SDK->>AdMob: 광고 요청
    AdMob-->>SDK: 광고 응답
    SDK-->>FE: 광고 노출
    User->>SDK: 광고 끝까지 시청
    SDK-->>FE: onUserEarnedReward
    AdMob->>API: GET /api/ads/ssv/admob?...&signature=...
    API->>API: 공개키 캐시 조회 → 서명 검증
    alt 서명 실패
        API->>DB: ad_reward_ledger INSERT (REJECTED, BAD_SIGNATURE)
        API-->>AdMob: 401
    else 서명 성공
        API->>DB: 오늘 시청 횟수 조회
        alt 한도 초과
            API->>DB: ad_reward_ledger INSERT (REJECTED, OVER_QUOTA)
            API-->>AdMob: 200
        else 한도 내
            API->>API: recordTransaction(key="admob:reward:{nonce}")
            API->>DB: ad_reward_ledger INSERT (GRANTED)
            API->>DB: point_transaction INSERT
            API-->>AdMob: 200
        end
    end
    FE->>API: GET /api/ads/reward/quota (재조회)
    API-->>FE: 갱신된 quota
```

## 범위 외 (Out Of Scope)

- TNK Factory 오퍼월 연동 (`domain/offerwall/`) — Phase 2
- 데일리 미션 (`domain/mission/`) — Phase 2
- 미션 새로고침권, 도장 회복권 (광고 시청으로 사용)
- 친구 초대 보상 및 디바이스 핑거프린팅 어뷰징 방지
- AdMob 네이티브/인터스티셜 광고 (Chat 탭 광고는 Chat 도메인 별도 spec)
- 광고 노출 빈도 제어(Frequency Cap)의 서버측 정책
- 출석/광고 보상 코인 환산비의 동적 조정 (관리자 UI)
- 추가 오퍼월(Buzzvil, AdiSON 등) 통합
- 진화/강화 시스템에서의 부가 보상 아이템 소비 (Inventory consume은 Evolution spec)
- 적립 후 환수(revoke) / 운영자 수동 조정 도메인

## 부록: Phase 1 시드값

### 출석 보상 (`attendance_reward` 테이블 시드)

| 누적 일차 | 코인 | 부가 보상 (`itemCode × qty`) |
| --------- | ---- | ----------------------------- |
| 1~6일     | +20  | -                             |
| 7일       | +50  | `EVO_STONE` × 1               |
| 14일      | +100 | `EVO_STONE` × 2, `LUCK_CHARM` × 1 |
| 30일      | +300 | `PROTECT_TICKET` × 1          |

- 31일+ 처리는 Phase 1 범위 외 — 가설은 "누적 일차 % 30 기준 사이클 재진입"이지만, 구체 규칙(streak 카운터 리셋 여부, 14/30일 부가 보상 재지급 시점)은 별도 의사결정 후 후속 spec에서 확정한다.
- 모든 일자 판정은 `Asia/Seoul` 기준이며, 멱등성 키 `attendance:{userId}:{yyyy-MM-dd}`의 `yyyy-MM-dd`도 KST 자정 기준으로 계산한다.
- 부가 보상의 `itemCode`는 Shop spec의 `shop_item.itemCode`와 동일 키 사용

### 광고 한도 (설정값)

- 일일 시청 한도: 10회 (`application.yml`의 `reward.admob.daily-limit`)
- 시청당 적립 코인: 40코인 (Phase 1 고정값; 후속 DB 이관 가능)
- KST 자정 리셋
