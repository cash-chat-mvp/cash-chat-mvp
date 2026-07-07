---
id: US-REWARD-004
domain: reward
slug: friend-invite
status: implemented
jira: CC-355
source: docs/features/invite-friend/spec.md
related-domains: [invite, point, energy, ledger]
---

# 친구 초대 (추천 코드)

## 스토리

사용자로서, 나는 내 고유 추천 코드와 지금까지 초대한 친구 수·보상을 확인하고 공유하고 싶다(같은 사용자는 항상 같은 코드).
신규 사용자로서, 나는 친구의 추천 코드를 입력(redeem)해 나는 에너지(밥)를 받고 코드 소유자(초대자)에게는 코인이 가도록 하고 싶다.
운영자로서, 나는 초대자 코인 보상을 최대 인원까지만 지급되도록 제한하고 싶다.

## 수용 조건 (Acceptance Criteria)

- [ ] **AC-01 코드 발급 (최초)**
  Given 코드를 발급받은 적이 없다 → When `GET /api/invite/me` → Then 혼동 문자 제외 난수 코드 1행 생성(UNIQUE 충돌 시 재생성), `{ myCode, invitedCount:0, redeemAvailable, rewardCoin, rewardEnergy }` 반환.

- [ ] **AC-02 코드 발급 (재호출 멱등)**
  Given 이미 코드가 있다 → When 다시 호출 → Then 동일 `myCode` 반환, 동시 최초 2건도 하나만 생성.

- [ ] **AC-03 내 초대 정보 조회**
  Then `invitedCount` = 내 코드로 redeem한 친구 수, `redeemAvailable` = "본인 미입력" AND "가입 후 `redeem-window-days`일 이내"가 모두 참일 때만 true.

- [ ] **AC-04 정상 redeem (상한 내)**
  Given B가 미입력·적격이고 코드가 A의 유효 코드이며 A≠B, A의 누적이 `inviter-cap` 미만이다
  When B가 `POST /api/invite/redeem {code}`
  Then **단일 트랜잭션**으로 `invite_redemptions` INSERT(`invitee_user_id` UNIQUE) → B 에너지 충전 → A 코인 멱등 적립(`referral:{B}`)을 수행하고 `{ success:true, awardedEnergy }`를 반환한다. 한 단계라도 실패 시 전체 롤백.

- [ ] **AC-05 이미 redeem함 (재호출/동시)**
  Then `409 ALREADY_REDEEMED`, 추가 행·지급 없음. 동시 2건 중 1건만 INSERT 성공(UNIQUE).

- [ ] **AC-06 자기 코드 입력** → `409 SELF_REFERRAL`, 지급 없음.
- [ ] **AC-07 미존재 코드** → `404 INVALID_CODE`, 지급 없음.
- [ ] **AC-08 적격 기간 초과** → `403 NOT_ELIGIBLE`, `redeemAvailable=false`와 일관, 지급 없음.

- [ ] **AC-09 초대자 보상 상한 초과**
  Given A의 누적이 `inviter-cap`에 도달했다
  Then redemption을 `GRANTED_INVITER_CAPPED`, `awardedCoin=0`으로 INSERT하고 **B 에너지는 정상 충전**, A 코인은 미지급. 친구 관점에서 redeem은 성공(`success:true`).

## 검증 매핑 (Verification)

- BE: `invitee_user_id` UNIQUE(1인 1회 + 에너지 중복 방어), 상한 분기, 멱등 키, 동시 redeem 테스트
- 미확정: 보상값/상한/적격기간 운영 확정값, `REFERRAL` enum 추가 (spec §검증 필요 항목)

## 관련

- 기술 상세(데이터 모델 V13·설정·시퀀스): `docs/features/invite-friend/spec.md`
- FE 설계: `docs/superpowers/specs/2026-06-21-benefit-zone-friend-invite-design.md`
- 딥링크/어뷰징 차단은 범위 외(후속 phase).
- 용어: [_glossary.md](./_glossary.md)
