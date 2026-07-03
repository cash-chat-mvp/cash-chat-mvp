---
id: US-REWARD-005
domain: reward
slug: lucky-roulette
status: agreed
jira: CC-376
source: Confluence CC-355, docs/superpowers/specs/2026-06-21-benefit-zone-roulette-design.md
related-domains: [ad, energy, ledger]
---

# 행운 룰렛

## 스토리

사용자로서, 나는 혜택존에서 하루 첫 1회는 무료로 룰렛을 돌리고, 이후에는 광고를 끝까지 본 뒤 추가 룰렛을 돌려 밥(에너지)을 받을 수 있기를 원한다.
프론트엔드 개발자로서, 나는 클라이언트가 확률이나 당첨을 계산하지 않고 서버가 내려준 결과와 표시용 세그먼트만 사용해 룰렛 애니메이션을 구성하고 싶다.
백엔드 개발자로서, 나는 하루 이용 횟수·광고 검증·확률·밥 지급·중복 요청 처리를 서버에서 일관되게 통제하고 싶다.
운영자로서, 나는 일일 횟수와 확률표를 코드 변경 없이 설정값으로 조정할 수 있기를 원한다.

## 수용 조건 (Acceptance Criteria)

- **AC-01 상태 조회**
  Given 인증된 사용자가 혜택존 룰렛 화면에 진입한다
  When `GET /api/roulette/status`를 호출한다
  Then KST 기준 당일 상태(`date`, `dailyLimit`, `spinsUsedToday`, `freeSpinAvailable`, `remaining`, `resetAtKst`)와 표시용 `segments`를 반환한다
  And `segments`는 룰렛 화면 배치용이며 실제 당첨 확률과 독립이다.

- **AC-02 첫 무료 스핀**
  Given 사용자가 KST 당일 무료 스핀을 아직 사용하지 않았다
  When `POST /api/roulette/spin`을 호출한다
  Then 서버가 확률표로 상품을 결정하고, 필요한 경우 밥을 지급하고, 스핀 이력을 저장한다
  And 응답은 `prize`, `segmentIndex`, `prizeEnergy`, `awardedEnergy`, `energyAfter`, 갱신된 `status`를 포함한다.

- **AC-03 무료 스핀 중복**
  Given 사용자가 KST 당일 무료 스핀을 이미 사용했다
  When `POST /api/roulette/spin`을 다시 호출한다
  Then `409 FREE_SPIN_USED`를 반환하고 추가 지급·스핀 이력을 만들지 않는다.

- **AC-04 광고 스핀은 무료 스핀 이후에만 가능**
  Given 사용자가 KST 당일 무료 스핀을 아직 사용하지 않았다
  When `POST /api/roulette/issue-nonce` 또는 `POST /api/roulette/spin-with-ad`를 호출한다
  Then `409 FREE_SPIN_AVAILABLE`을 반환한다
  And 프론트엔드는 이 상태에서 광고 버튼 대신 무료 스핀 버튼을 보여준다.

- **AC-05 광고 nonce 발급**
  Given 사용자가 무료 스핀을 사용했고 일일 한도에 도달하지 않았다
  When `POST /api/roulette/issue-nonce`를 호출한다
  Then 룰렛 광고용 단일 사용·단기 TTL nonce를 저장하고 `{ nonce, expiresAt }`를 반환한다
  And 프론트엔드는 이 nonce를 AdMob SSV `custom_data`에 실어 보상형 광고를 노출한다.

- **AC-06 AdMob SSV 라우팅**
  Given AdMob SSV 콜백이 기존 `/api/ads/google/ssv`로 도착하고 서명 검증을 통과했다
  When 콜백의 `custom_data` nonce가 룰렛 nonce 테이블에 존재한다
  Then 백엔드는 해당 nonce를 `verified=true`로 표시하고 일반 리워드 광고 보상은 지급하지 않는다
  And nonce가 일반 리워드 광고용이면 기존 리워드 광고 적립 흐름을 따른다.

- **AC-07 광고 스핀**
  Given 무료 스핀을 사용했고, 룰렛 광고 nonce가 SSV로 검증되었고, 일일 한도에 도달하지 않았다
  When `POST /api/roulette/spin-with-ad { nonce }`를 호출한다
  Then 서버가 nonce를 1회 소비하고, 확률표로 상품을 결정하고, 필요한 경우 밥을 지급하고, 스핀 이력을 저장한다.

- **AC-08 광고 검증 지연 또는 실패**
  Given 광고 종료 직후 SSV 콜백이 아직 도착하지 않았거나 검증되지 않았다
  When `POST /api/roulette/spin-with-ad { nonce }`를 호출한다
  Then `403 AD_NOT_VERIFIED`를 반환한다
  And 프론트엔드는 잠시 후 재시도하거나 보상 확인 중 상태를 보여줄 수 있다.

- **AC-09 광고 스핀 결과 재전송**
  Given 같은 nonce로 이미 성공한 광고 스핀 이력이 있다
  When 네트워크 재시도 등으로 `POST /api/roulette/spin-with-ad { nonce }`가 다시 호출된다
  Then `200`으로 기존 스핀 결과를 다시 반환한다
  And 밥 지급과 스핀 횟수 증가는 중복 수행하지 않는다.

- **AC-10 일일 한도**
  Given 사용자의 KST 당일 `spinsUsedToday`가 `dailyLimit`에 도달했다
  When 무료 스핀, nonce 발급, 광고 스핀 중 하나를 시도한다
  Then `409 DAILY_LIMIT_REACHED`를 반환한다.

- **AC-11 명목 보상과 실제 지급량**
  Given 사용자의 밥이 최대치에 가까운 상태다
  When 룰렛 상품의 명목 밥(`prizeEnergy`)이 현재 남은 수용량보다 크다
  Then `awardedEnergy`는 실제 충전된 양만 나타내고, `energyAfter`는 충전 후 밥 잔량을 나타낸다.

- **AC-12 정책 설정**
  Given 운영자가 룰렛 정책을 조정해야 한다
  Then 초기 구현은 서버 설정값으로 `dailyLimit`, `freeSpinCount`, nonce TTL, 상품별 `weight`를 관리한다
  And 기본 정책은 하루 총 5회(무료 1 + 광고 4), `JACKPOT_100` 1%, `E10` 10%, `E3` 70%, `MISS` 19%다.

## 검증 매핑 (Verification)

- FE: `RouletteRepository`는 서버 응답의 상태와 결과를 사용하고, 클라이언트에서 당첨·확률을 계산하지 않는다.
- FE: `freeSpinAvailable=true`이면 무료 스핀 CTA를, 무료 소진 후 `remaining>0`이면 광고 후 스핀 CTA를 보여준다.
- BE 단위: 확률 경계, 무료 스핀 상태 전이, 광고 nonce 차단, 밥 상한 적용, SSV 라우팅.
- BE 컨트롤러: `/api/roulette/status`, `/spin`, `/issue-nonce`, `/spin-with-ad` 응답 필드와 에러 코드.
- BE 통합(TestContainers): 룰렛 테이블 마이그레이션, nonce UNIQUE, 스핀 이력 replay, 동시 요청 시 중복 지급 방지.

## 관련

- FE 목업/스텁 설계: `docs/superpowers/specs/2026-06-21-benefit-zone-roulette-design.md`
- BE API 요청 원문: Confluence `CC-355 API FE BE` §행운 룰렛
- 기존 광고 검증 흐름: [US-REWARD-002](./US-REWARD-002-rewarded-ad.md)
- 용어: [_glossary.md](./_glossary.md)
