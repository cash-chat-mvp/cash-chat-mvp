# reward 도메인 — 유저 스토리 인덱스

혜택존(코인/밥 적립 채널)의 유저 스토리 카탈로그. 각 파일은 **직군 중립 계약**(스토리 + 인수 조건)이며, 완료 판정·검증의 기준선이다.
기술 구현 상세(API 계약·데이터 모델·시퀀스)는 각 파일이 링크하는 `docs/features/*` 원본 spec에 있다.

> 파일명 = 불변 ID(`US-REWARD-NNN`) + slug. 번호는 **생성 순서일 뿐 의미 없음**(우선순위/순서 아님, 재사용·재배치 안 함). 사람이 보는 순서·우선순위는 아래 표에서 관리한다.

| ID | 스토리 | 상태 | Jira | 원본 spec |
| -- | ------ | ---- | ---- | --------- |
| [US-REWARD-001](./US-REWARD-001-daily-attendance.md) | 일일 출석체크 | implemented | CC-288 | features/reward |
| [US-REWARD-002](./US-REWARD-002-rewarded-ad.md) | 리워드 광고 시청(AdMob SSV) | implemented | CC-242 | features/reward, features/google-ad-ssv |
| [US-REWARD-003](./US-REWARD-003-tnk-offerwall.md) | TNK 오퍼월 적립 | implemented | CC-288 | features/offerwall |
| [US-REWARD-004](./US-REWARD-004-friend-invite.md) | 친구 초대(추천 코드) | implemented | CC-355 | features/invite-friend |
| [US-REWARD-005](./US-REWARD-005-lucky-roulette.md) | 행운 룰렛 | agreed | CC-376 | Confluence CC-355, superpowers/specs |

- **상태**는 스펙 합의 기준의 응결값이다. 실제 배포/진척 추적은 Jira를 참조.
- 공유 용어: [_glossary.md](./_glossary.md)
