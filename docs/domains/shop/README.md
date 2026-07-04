# shop 도메인 — 유저 스토리 인덱스

상점(강화재료 카탈로그·구매·인벤토리)의 유저 스토리 카탈로그. 각 파일은 직군 중립 계약(스토리 + 인수 조건)이다.

> 파일명 = 불변 ID(`US-SHOP-NNN`) + slug. 번호는 생성 순서일 뿐 의미 없음. 순서·우선순위는 아래 표에서 관리한다.

| ID | 스토리 | 상태 | Jira | 원본 spec |
| -- | ------ | ---- | ---- | --------- |
| [US-SHOP-001](./US-SHOP-001-enhance-catalog.md) | 강화재료 카탈로그 조회 | implemented | CC-292 | features/shop |
| [US-SHOP-002](./US-SHOP-002-coin-purchase.md) | 코인으로 아이템 구매 | implemented | CC-292 | features/shop |
| [US-SHOP-003](./US-SHOP-003-inventory.md) | 인벤토리 조회 | implemented | CC-292 | features/shop |

- 공유 용어: [_glossary.md](./_glossary.md)
- 선결 의존: 포인트 멱등 확장(`recordTransaction`) — [reward 도메인](../reward/README.md)과 공유.
