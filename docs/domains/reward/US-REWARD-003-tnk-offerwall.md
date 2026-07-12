---
id: US-REWARD-003
domain: reward
slug: tnk-offerwall
status: implemented
jira: CC-288
source: docs/features/offerwall/spec.md
related-domains: [offerwall, point, ledger]
---

# TNK Factory 오퍼월 적립

## 스토리

프론트로서, 나는 오퍼월 진입 시 TNK SDK에 넘길 안정적 사용자 토큰을 서버에서 받고 싶다(같은 사용자는 항상 같은 토큰).
사용자로서, 나는 오퍼(앱 설치·가입·설문)를 완료하면 자동으로 코인을 적립받고 싶다.
운영자로서, 나는 수신된 모든 콜백(성공·거절)을 원장에서 조회해 정산·디버깅에 쓰고 싶다.

## 수용 조건 (Acceptance Criteria)

- [ ] **AC-01 토큰 발급 (최초)**
  Given 인증된 사용자가 토큰을 발급받은 적이 없다
  When `POST /api/offerwall/tnk/user-token`을 호출한다
  Then `(userId, token=UUID)` 1행을 생성하고 `{ token }`을 반환한다 (내부 `userId` 비노출).

- [ ] **AC-02 토큰 발급 (재호출 멱등)**
  Given 이미 토큰이 있다 → When 다시 호출 → Then 새 행 없이 동일 `token`을 반환하고, 동시 최초 호출 2건도 유니크 제약으로 하나만 생성된다.

- [ ] **AC-03 정상 적립**
  Given 콜백이 `seq_id, pay_pnt, md_user_nm, md_chk`를 포함하고 `md_chk == MD5(appKey + md_user_nm + seq_id)` 검증을 통과하며 `seq_id`가 미존재다
  When `POST /api/offerwall/tnk/callback`을 처리한다
  Then **단일 트랜잭션**으로 토큰→`userId` 해석, `coin = floor(pay_pnt × ratio)` 산출, 멱등 적립(`tnk:offerwall:{seq_id}`), ledger `GRANTED` 기록을 수행하고 TNK에 성공 ack를 반환한다.

- [ ] **AC-04 중복 seq_id (멱등)**
  Given 동일 `seq_id`로 두 번 도착한다
  Then 추가 적립 없이 멱등 종료하고 두 번째에도 성공 ack를 반환한다(재전송 중단). 동시 2건 중 1건만 적립.

- [ ] **AC-05 서명 검증 실패**
  Given `md_chk`가 불일치한다(또는 `appKey` 미설정 → fail-closed)
  Then **DB 쓰기 전에 검증하므로 원장 행을 만들지 않고** warn 로그만 남긴다(public 엔드포인트 원장 오염 방지). 적립하지 않는다.

- [ ] **AC-06 미지의 토큰**
  Given 서명은 통과했으나 `md_user_nm`이 매핑에 없다
  Then 적립하지 않고 ledger에 `REJECTED_UNKNOWN_USER`(user_id=null)로 기록한다.

## 검증 매핑 (Verification)

- BE: `md_chk` 서명 검증, 멱등(`seq_id` UNIQUE + 멱등 키), fail-closed 순서 테스트
- 규격 확인: TNK Android SDK "자체 서버 포인트 관리" 콜백 규격과 일치 확인 완료 (spec §검증 항목)

## 관련

- 기술 상세(데이터 모델 V11·설정·시퀀스): `docs/features/offerwall/spec.md`, `docs/features/offerwall/architecture.md`
- 취소/환수(claw-back)는 범위 외(D3) — 원장 기록만.
- 용어: [_glossary.md](./_glossary.md)
