---
id: US-REWARD-002
domain: reward
slug: rewarded-ad
status: implemented
jira: CC-242            # BE Google 광고 SSV (관련: CC-365/CC-368 오류 수정)
source: docs/features/reward/spec.md, docs/features/google-ad-ssv/
related-domains: [ad, point, energy, ledger]
---

# 리워드 광고 시청 (AdMob SSV)

## 스토리

사용자로서, 나는 혜택존의 [지금 시청] 버튼으로 리워드 광고를 끝까지 보고 보상(코인/밥)을 받고 싶다.
운영자로서, 나는 사용자당 하루 N회로 시청을 제한하고, 위조·중복 적립을 막고 싶다.

## 수용 조건 (Acceptance Criteria)

- [ ] **AC-01 nonce 발급 (시청 직전)**
  Given 인증된 사용자가 광고 시청 직전이다
  When `POST /api/ads/reward/issue-nonce`를 호출한다
  Then 단일 사용·단기 TTL nonce 1건을 저장하고 `{ nonce, expiresAt }`를 반환한다
  And 이 nonce는 AdMob `custom_data`의 `nonce` 필드에만 실린다 (`userId` 등 식별값은 클라이언트가 직접 넣지 않는다).

- [ ] **AC-02 한도 내 정상 시청**
  Given 미사용·미만료 nonce가 있고 일일 quota가 한도 미만이다
  When AdMob SSV 콜백이 도착하고 서명 검증을 통과한다
  Then **단일 트랜잭션 + 행 락(`SELECT … FOR UPDATE`)** 안에서 한도 재확인 → `usedCount += 1` → `nonce.used=true` → 멱등 적립(`admob:reward:{nonce}`) → ledger `GRANTED`를 수행한다
  And 동시 도착 콜백 간 TOCTOU 경합이 발생하지 않는다.

- [ ] **AC-03 위조·만료·사용된 nonce 거부**
  Given 서명은 통과했으나 nonce가 없거나/만료/이미 used다
  When 콜백을 처리한다
  Then 적립을 거부하고 ledger에 `REJECTED / INVALID_NONCE`를 기록하며, AdMob에는 200을 반환한다(재시도 폭주 방지).

- [ ] **AC-04 일일 한도 초과**
  Given quota가 한도에 도달했다
  When 검증 통과 콜백이 도착한다
  Then 행 락으로 `usedCount >= 한도`를 확인 후 거부하고 ledger에 `REJECTED / OVER_QUOTA`를 기록, nonce.used는 변경하지 않으며 AdMob에 200을 반환한다.

- [ ] **AC-05 서명 검증 실패**
  Given 콜백 `signature`가 공개키 검증에 실패한다
  Then 401로 응답하고 ledger에 `REJECTED / BAD_SIGNATURE`를 기록, 적립하지 않는다.

- [ ] **AC-06 콜백 중복 (동일 nonce 재전송)**
  Given 동일 nonce로 콜백이 두 번 도착한다
  Then 두 번째는 `INVALID_NONCE`로 거부되고, 설령 적립 단계에 도달해도 멱등 키 충돌로 중복 적립되지 않는다(이중 방어). 200 반환.

- [ ] **AC-07 quota 조회**
  When `GET /api/ads/reward/quota` → Then `{ usedToday, dailyLimit, remaining, resetAtKst }`를 반환한다.

## FE 관통 인수 기준 (Acceptance Criteria)

- [ ] **AC-FE-01 광고 시청 완료 → 보상 반영**
  Given mock 플레이버 앱이 혜택존에 있고 일일 한도가 남아 있다
  When 리워드 광고 카드의 [광고 보기]를 눌러 (Fake) 광고를 완료한다
  Then 보상 반영 피드백(`에너지를 충전했어요!`)이 표시된다.

- [ ] **AC-FE-02 일일 한도 소진**
  Given `ad_quota_exceeded` 시나리오다
  When 혜택존에 진입한다
  Then 리워드 광고 카드가 시청 불가(`내일 다시 만나요`)로 표시된다.

## 검증 매핑 (Verification)

- BE 단위: 파서/서명/공개키/검증·적립 (`GoogleAdSsvServiceTest`, `AdRewardServiceTest`, 컨트롤러)
- BE 통합(TestContainers): 행 락 동시성·한도·멱등 (`AdRewardIntegrationTest`)
- 실단말 E2E: DB `reward_status=GRANTED` 확인
- FE(Maestro): apps/frontend/maestro/flows/rewarded-ad/watch-reward.yaml, quota-exceeded.yaml (AC-FE-01/02)
- 절차 상세: [manual.md](../../features/google-ad-ssv/manual.md) §4.

## 관련

- 아키텍처: `docs/features/google-ad-ssv/architecture.md` · 연동/테스트/운영: `docs/features/google-ad-ssv/manual.md`
- AC 원문·시퀀스: `docs/features/reward/spec.md` (Story 3~5)
- 용어: [_glossary.md](./_glossary.md)
