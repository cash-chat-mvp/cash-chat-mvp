# 혜택존(Invite) — 친구 초대(추천 코드) 백엔드 Spec

> 상태: Draft
> 범위: 백엔드 (`domain/invite/` 추천 코드 적립 채널)
> 관련 기획: [Confluence — 혜택존 TNK 오퍼월·출석·부가 위젯](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14909530/TNK), `docs/superpowers/specs/2026-06-21-benefit-zone-friend-invite-design.md`(FE 설계), `docs/planning/be-api-requests-cc355.md` §5(FE→BE API 요청)
> 선행 인프라: `domain/point/UserPointService.recordTransaction(idempotencyKey)`(코인 멱등 적립), `domain/energy/EnergyService.charge`(에너지 충전)
> Jira: CC-355 §H

## 목표 (Goal)

혜택존에 **추천 코드** 기반 친구 초대 적립 채널을 백엔드로 추가한다. 사용자가 친구의 추천 코드를 혜택존 '친구 초대' 화면에서 입력(redeem)하면, 백엔드는 코드를 검증하고 다음을 **단일 트랜잭션**으로 수행한다.

1. **코드 발급**: 각 사용자에게 고유 추천 코드를 부여한다. `GET /api/invite/me` 최초 호출 시 get-or-create(멱등)로 생성한다.
2. **redeem 검증·지급**: 코드 유효성·자기코드 금지·1인 1회·적격 기간을 검증한 뒤, **가입자(입력자)에게 에너지**를, **초대자(코드 소유자)에게 코인**을 지급한다.
3. **원장 기록**: 모든 성공 redeem을 `invite_redemptions`에 1행으로 기록한다(누가 누구를 초대했는지, 지급 결과 포함).

코인 적립은 기존 `UserPointService.recordTransaction(idempotencyKey)`의 멱등성 트랜잭션을 통해 수행하며, 사유로 `REFERRAL`을 추가한다. 에너지 충전은 `EnergyService.charge(userId, amount)`를 사용한다 — 이 메서드는 멱등성 키가 없으므로, **에너지 중복 지급 방어는 `invite_redemptions.invitee_user_id` UNIQUE 제약**이 1차 방어선이 된다.

## 핵심 설계 결정 (Decisions)

| # | 결정 | 내용 |
| - | ---- | ---- |
| D1 | 식별 방식 | 사용자당 고유 **추천 코드**(길이 `code-length`, 기본 6자, 혼동 문자 `O/0/I/1/L` 제외한 대문자+숫자). `GET /api/invite/me` 최초 호출 시 **get-or-create**(멱등). 딥링크/초대 링크는 미사용(범위 외). |
| D2 | redeem 적격 | redeem 가능 조건은 **(a) 본인이 아직 추천 코드를 입력한 적이 없음** AND **(b) 가입 후 `redeem-window-days`일(KST 기준) 이내**. 둘 다 충족 시 `GET /api/invite/me`의 `redeemAvailable=true`. |
| D3 | 초대자 보상 상한 | 초대자(코드 소유자)의 코인 보상은 **최대 `inviter-cap`명**까지만 지급한다. 상한 도달 이후 친구가 redeem하면 **친구 에너지는 정상 지급하되 초대자 코인은 지급하지 않는다** — 친구의 가입 보상은 초대자의 인기와 독립적이어야 하기 때문이다. |
| D4 | 멱등·원자성 | redeem은 **단일 `@Transactional`**. `invite_redemptions.invitee_user_id` **UNIQUE** 제약이 "1인 1회 + 에너지 중복 지급"의 1차 방어선이다(동시 redeem 2건 중 1건만 INSERT 성공). 초대자 코인은 멱등성 키 `referral:{inviteeUserId}`로 이중 방어한다. |
| D5 | 어뷰징 방지 | 디바이스 핑거프린팅/IP 기반 다계정 차단은 **이번 범위 외**(후속 phase). 자기 코드 금지·1인 1회·적격 기간·코드 유효성만 검증한다. |
| D6 | 보상값 출처 | `rewardCoin`(초대자 코인)·`rewardEnergy`(가입자 에너지)·`redeem-window-days`·`inviter-cap`은 전부 **서버 설정값**(`app.invite.*`). FE는 표시·입력만 한다. |

## 유저 스토리 (User Story)

### Story 1: 내 추천 코드 확인·공유

사용자는 혜택존 '친구 초대' 화면에서 자신의 고유 추천 코드와 지금까지 초대한 친구 수, 초대 성공 시 받는 보상을 확인하고 싶다. 같은 사용자는 항상 같은 코드를 받아야 한다.

### Story 2: 친구의 추천 코드 입력(redeem)

신규 사용자는 가입 후 혜택존 '친구 초대' 화면에서 친구에게 받은 추천 코드를 입력해, 본인은 에너지(밥)를 받고 코드 소유자(초대자)에게는 코인 보상이 가도록 하고 싶다.

### Story 3: 적립 무결성 (중복·자기참조·위조 방어)

백엔드는 다음을 만족해야 한다.

1. 한 사용자는 추천 코드를 **단 1회만** redeem할 수 있다(재호출·동시 호출 모두 1회만 적립).
2. 자기 자신의 코드는 입력할 수 없다.
3. 적격 기간(가입 후 N일)을 벗어난 사용자는 redeem할 수 없다.
4. 존재하지 않는 코드는 거부한다.

### Story 4: 초대자 보상 상한

운영 정책상 한 초대자가 코인 보상을 받을 수 있는 초대 성공 횟수를 `inviter-cap`명으로 제한하고 싶다. 상한 초과분에 대해서는 초대자에게 코인을 지급하지 않되, 입력한 친구의 에너지 지급은 막지 않는다.

## 인수 기준 (Acceptance Criteria)

### 코드 발급 (최초)

Given 인증된 사용자가 추천 코드를 발급받은 적이 없다
When 사용자가 `GET /api/invite/me`를 호출한다
Then 백엔드는 `invite_codes`에 `(userId, code)` 1행을 생성한다 — `code`는 혼동 문자를 제외한 `code-length`자 난수이며 `code` UNIQUE 충돌 시 재생성한다
And 응답은 `{ myCode, invitedCount:0, redeemAvailable, rewardCoin, rewardEnergy }` 형태로 반환된다.

### 코드 발급 (재호출 멱등)

Given 사용자가 이미 추천 코드를 발급받았다
When 같은 사용자가 `GET /api/invite/me`를 다시 호출한다
Then 백엔드는 새 행을 만들지 않고 기존과 동일한 `myCode`를 반환한다
And 동시 최초 호출 2건이 와도 (`user_id` PK / `code` UNIQUE 제약으로) 하나의 코드만 생성되고 양쪽 모두 같은 값을 받는다.

### 내 초대 정보 조회

Given 사용자의 코드로 친구 3명이 redeem을 완료했다
When 사용자가 `GET /api/invite/me`를 호출한다
Then `invitedCount`는 내 코드로 redeem한 친구 수(여기선 3)를 반환한다
And `redeemAvailable`은 "본인이 아직 미입력" AND "가입 후 `redeem-window-days`일 이내"가 모두 참일 때만 `true`이다
And `rewardCoin`/`rewardEnergy`는 서버 설정값을 그대로 표시값으로 반환한다.

### 정상 redeem (상한 내)

Given 친구 B가 아직 추천 코드를 입력한 적이 없고, 가입 후 적격 기간 이내이다
And 입력한 코드가 초대자 A의 유효한 코드이며 A ≠ B 이다
And A의 코인 보상 누적 횟수가 `inviter-cap` 미만이다
When B가 `POST /api/invite/redeem` 바디 `{ code }`로 호출한다
Then 백엔드는 **단일 `@Transactional`** 안에서 — (a) `invite_redemptions`에 `(inviteeUserId=B, inviterUserId=A, code, status=GRANTED)` INSERT(`invitee_user_id` UNIQUE), (b) `EnergyService.charge(B, invitee-reward-energy)`로 B 에너지 충전, (c) `UserPointService.recordTransaction(A, +inviter-reward-coin, reason=REFERRAL, idempotencyKey="referral:{B}")`로 A 코인 적립, (d) redemption 행의 `awardedEnergy`/`awardedCoin` 확정 — 을 수행한다
And 응답은 `{ success:true, awardedEnergy:<invitee-reward-energy>, message:null }`이다
And B의 에너지 잔액과 A의 코인 잔액에 각각 반영된다
And 어느 한 단계라도 실패하면 트랜잭션 전체가 롤백되어 부분 지급이 발생하지 않는다.

### 이미 redeem함 (재호출/동시)

Given 친구 B가 이미 추천 코드를 1회 입력했다
When B가 `POST /api/invite/redeem`을 다시 호출한다
Then 백엔드는 `409 ALREADY_REDEEMED`로 거부한다
And `invite_redemptions`에 추가 행이 생기지 않는다(`invitee_user_id` UNIQUE)
And 에너지·코인이 추가로 지급되지 않는다
And 동시 도착한 동일 사용자 redeem 2건 중 정확히 1건만 INSERT에 성공하고 나머지는 UNIQUE 위반으로 `ALREADY_REDEEMED` 처리된다.

### 자기 코드 입력

Given 사용자 B가 입력한 코드가 자신의 코드이다
When B가 `POST /api/invite/redeem`을 호출한다
Then 백엔드는 `409 SELF_REFERRAL`로 거부한다
And 어떤 행도 생성되지 않고 보상이 지급되지 않는다.

### 미존재 코드

Given 입력한 코드가 `invite_codes`에 존재하지 않는다
When B가 `POST /api/invite/redeem`을 호출한다
Then 백엔드는 `404 INVALID_CODE`로 거부한다
And 보상이 지급되지 않는다.

### 적격 기간 초과

Given 사용자 B의 가입 시각(`users.created_at`)이 현재로부터 `redeem-window-days`일을 초과했다(KST 기준)
When B가 미사용 상태로 `POST /api/invite/redeem`을 호출한다
Then 백엔드는 `403 NOT_ELIGIBLE`로 거부한다
And `GET /api/invite/me`의 `redeemAvailable`도 `false`로 일관된다
And 보상이 지급되지 않는다.

### 초대자 보상 상한 초과

Given 초대자 A의 코인 보상 누적 횟수가 이미 `inviter-cap`에 도달했다
And 친구 B는 적격이며 A의 코드를 처음 입력한다
When B가 `POST /api/invite/redeem`을 호출한다
Then 백엔드는 단일 트랜잭션 안에서 `invite_redemptions`를 `status=GRANTED_INVITER_CAPPED`, `awardedCoin=0`으로 INSERT하고 **B 에너지는 정상 충전**한다
And A에게는 코인을 지급하지 않는다(`recordTransaction` 미호출)
And 응답은 `{ success:true, awardedEnergy:<invitee-reward-energy>, message:null }`이다 — 친구 관점에서 redeem은 성공이다.

## API 계약 (요약)

| Method | Path | 인증 | 설명 |
| ------ | ---- | ---- | ---- |
| `GET` | `/api/invite/me` | 사용자 | 내 추천 코드(get-or-create)·초대 성공 수·redeem 가능 여부·보상 표시값 |
| `POST` | `/api/invite/redeem` | 사용자 | 바디 `{ code }`. 코드 검증 후 친구 에너지 + 초대자 코인 지급 |

### `GET /api/invite/me` 응답 (200)

```json
{
  "myCode": "ABC23X",
  "invitedCount": 3,
  "redeemAvailable": true,
  "rewardCoin": 500,
  "rewardEnergy": 10
}
```

| 필드 | 타입 | 설명 |
| ---- | ---- | ---- |
| `myCode` | String | 내 추천 코드(공유용, get-or-create) |
| `invitedCount` | Int | 내 코드로 redeem한 친구 수 |
| `redeemAvailable` | Boolean | 내가 추천 코드를 입력할 수 있는지(미사용 AND 적격 기간 내) |
| `rewardCoin` | Int | 초대 성공 시 초대자 코인(표시용, 서버 설정값) |
| `rewardEnergy` | Int | 추천 코드 입력 시 가입자 에너지(표시용, 서버 설정값) |

### `POST /api/invite/redeem` 요청·응답

```json
// 요청
{ "code": "XYZ29K" }

// 응답 (200)
{ "success": true, "awardedEnergy": 10, "message": null }
```

| 필드 | 타입 | 설명 |
| ---- | ---- | ---- |
| `success` | Boolean | 적립 성공 여부 |
| `awardedEnergy` | Int | 입력자(가입자)에게 지급된 에너지 |
| `message` | String? | 실패 사유(표시용, 성공 시 null) |

에러:

| HTTP | 코드 | 사유 |
| ---- | ---- | ---- |
| 409 | `ALREADY_REDEEMED` | 이미 추천 코드를 사용함 |
| 404 | `INVALID_CODE` | 존재하지 않는 코드 |
| 409 | `SELF_REFERRAL` | 자기 코드 입력 |
| 403 | `NOT_ELIGIBLE` | 적격 아님(가입 후 기간 초과) |

## 사용자 흐름 (User Flow)

1. 사용자가 혜택존 '친구 초대' 화면에 진입한다.
2. 프론트가 `GET /api/invite/me`로 내 코드·초대 수·redeem 가능 여부·보상값을 조회한다(최초 호출 시 코드 생성).
3. (공유) 사용자가 OS 공유시트로 코드를 친구에게 공유한다(FE 책임).
4. (입력) 친구가 자신의 혜택존 '친구 초대' 화면에서 받은 코드를 입력하고 `POST /api/invite/redeem`을 호출한다.
5. 백엔드가 코드·자기참조·1인1회·적격을 검증하고, 단일 트랜잭션으로 친구 에너지와 (상한 내라면) 초대자 코인을 지급한 뒤 redemption을 기록한다.
6. 프론트가 적립 결과 토스트를 표시하고 `GET /api/invite/me`를 재조회한다.

### 순차 흐름도 (Sequence Diagram)

```mermaid
sequenceDiagram
    participant Friend as 친구 B(가입자)
    participant FE as 혜택존 화면
    participant API as CashChat 백엔드
    participant DB as DB

    Friend->>FE: '친구 초대' 진입
    FE->>API: GET /api/invite/me
    API->>DB: invite_codes get-or-create(B)
    API-->>FE: { myCode, invitedCount, redeemAvailable, rewardCoin, rewardEnergy }
    Friend->>FE: 추천 코드 입력(A의 코드)
    FE->>API: POST /api/invite/redeem { code }
    API->>DB: invite_codes 조회 (code → 초대자 A)
    alt 코드 없음
        API-->>FE: 404 INVALID_CODE
    else code = 본인 코드
        API-->>FE: 409 SELF_REFERRAL
    else B가 이미 redeem함
        API-->>FE: 409 ALREADY_REDEEMED
    else B가 적격 기간 초과
        API-->>FE: 403 NOT_ELIGIBLE
    else 유효
        Note over API,DB: 단일 @Transactional
        API->>DB: BEGIN
        API->>DB: invite_redemptions INSERT (invitee_user_id UNIQUE)
        alt UNIQUE 위반(동시/중복)
            API->>DB: ROLLBACK
            API-->>FE: 409 ALREADY_REDEEMED
        else INSERT 성공
            API->>DB: EnergyService.charge(B, invitee-reward-energy)
            alt 초대자 A 누적 < inviter-cap
                API->>API: recordTransaction(A, +coin, REFERRAL, key="referral:{B}")
                API->>DB: point_transaction INSERT (A 코인)
                API->>DB: redemption status=GRANTED, awardedCoin=coin
            else 상한 초과
                API->>DB: redemption status=GRANTED_INVITER_CAPPED, awardedCoin=0
            end
            API->>DB: COMMIT
            API-->>FE: { success:true, awardedEnergy }
        end
    end
```

## 데이터 모델 (Flyway V13)

### `invite_codes`

| 컬럼 | 타입 | 비고 |
| ---- | ---- | ---- |
| `user_id` | BIGINT | PK, 사용자당 1행 |
| `code` | VARCHAR | UNIQUE, 혼동 문자 제외 대문자+숫자 |
| `created_at` / `updated_at` | (BaseEntity) | |

### `invite_redemptions` (원장)

| 컬럼 | 타입 | 비고 |
| ---- | ---- | ---- |
| `id` | BIGINT | PK |
| `invitee_user_id` | BIGINT | **UNIQUE** (1인 1회 + 에너지 중복 지급 방어) |
| `inviter_user_id` | BIGINT | 코드 소유자(초대자) |
| `code` | VARCHAR | 입력된 코드 원본 |
| `awarded_energy` | INT | 가입자에게 지급한 에너지 |
| `awarded_coin` | BIGINT | 초대자에게 지급한 코인(상한 초과 시 0) |
| `status` | ENUM | `GRANTED` / `GRANTED_INVITER_CAPPED` |
| `created_at` / `updated_at` | (BaseEntity) | |

- `inviter_user_id`에 인덱스(초대자별 누적 카운트 / `invitedCount` 조회용).
- `invitee_user_id` UNIQUE는 멱등성 없는 `EnergyService.charge`의 중복 지급을 막는 핵심 제약이다.

## 설정 (`app.invite.*`)

| 키 | 기본값(예시) | 설명 |
| -- | ------ | ---- |
| `code-length` | `6` | 추천 코드 길이 |
| `inviter-reward-coin` | `500` | 초대 성공 시 초대자 코인 |
| `invitee-reward-energy` | `10` | 코드 입력 시 가입자 에너지 |
| `redeem-window-days` | `7` | 가입 후 redeem 적격 기간(일, KST) |
| `inviter-cap` | `20` | 초대자가 코인 보상받을 수 있는 최대 초대 성공 수 |

> 코드 문자 집합은 혼동 방지를 위해 `O/0/I/1/L`을 제외한다(예: `ABCDEFGHJKMNPQRSTUVWXYZ23456789`).

## 검증 필요 항목

- [ ] `PointTransactionReason`에 `REFERRAL` enum 추가 — 적립 사유 분류.
- [ ] 보상값(`inviter-reward-coin`, `invitee-reward-energy`)·상한(`inviter-cap`)·적격 기간(`redeem-window-days`) 운영 확정값은 기획/운영과 협의 후 `application.yml`에 주입.
- [ ] `invitedCount` 정의 확정 — 본 spec은 "내 코드로 redeem한 친구 수(상한 초과분 포함)". 상한 초과분 표시 정책은 FE와 동기화.

## 범위 외 (Out Of Scope)

- 딥링크/초대 링크 기반 초대 — 추천 코드 입력만(추후 별도).
- 디바이스 핑거프린팅/IP 기반 다계정 어뷰징 차단 — 후속 phase (D5).
- 온보딩/가입 단계에서의 코드 입력 — 가입 후 혜택존에서만 redeem.
- 적립 후 환수(claw-back)/운영자 수동 조정.
- 초대 보상 동적 환산·관리자 UI.
- 프론트엔드(KMM `shared/`, Android/iOS) 구현 — `2026-06-21-benefit-zone-friend-invite-design.md` 참조.
