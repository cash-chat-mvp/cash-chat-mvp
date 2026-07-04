---
id: US-SHOP-002
domain: shop
slug: coin-purchase
status: implemented
jira: CC-292            # BE 상점 리뉴얼 API
source: docs/features/shop/spec.md
related-domains: [shop, inventory, point]
---

# 코인으로 아이템 구매

## 스토리

사용자로서, 나는 보유 코인이 가격 이상일 때 아이템을 구매하고, 차감된 코인과 증가한 인벤토리를 즉시 반영해서 보고 싶다.
코인이 부족하면 명확한 안내와 함께 혜택존으로 코인 벌러 가는 동선을 받고 싶다.
백엔드로서, 나는 네트워크 재시도·이중 탭으로 동일 구매가 두 번 도착해도 코인이 두 번 차감되지 않게 해야 한다.

## 수용 조건 (Acceptance Criteria)

- [ ] **AC-01 정상 구매 (원자성)**
  Given 잔액 ≥ 가격
  When `POST /api/shop/purchase {itemCode, qty, idempotencyKey}`
  Then **단일 트랜잭션**으로 코인 차감 + `user_inventory` 증가 + `purchase_order(COMPLETED, (userId, idempotencyKey))` 생성 + 멱등 차감(`shop:purchase:{userId}:{idem}`)을 수행하고 갱신된 잔액·보유 수량을 반환한다.

- [ ] **AC-02 패키지 구매 (다건 grant)**
  Given `ENHANCE_PACK`(grant: 진화석×5, 확률부적×1)을 구매한다
  Then 두 grant가 같은 트랜잭션 안에서 인벤토리에 반영된다 (`itemCode` 오름차순 락으로 데드락 방지).

- [ ] **AC-03 코인 부족**
  Given 잔액 < 가격 → Then `INSUFFICIENT_COIN`으로 거부, 잔액·인벤토리·`purchase_order` 무변화.

- [ ] **AC-04 멱등 — 동일 키 재호출**
  Given 직전에 `k1`로 구매 성공 → When 동일 페이로드 재호출 → Then 추가 차감 없이 **현재 시점** 잔액·인벤토리 반환(이중 차감만 방지).

- [ ] **AC-05 멱등 — 키 재사용 충돌 (동일 사용자)**
  Given `k1`로 구매 성공 → When 같은 사용자가 `k1`을 **다른 itemCode/qty**로 → Then `409 IDEMPOTENCY_KEY_CONFLICT`, 무변화. (멱등 스코프 = `(userId, idempotencyKey)`; 다른 사용자의 동일 키는 별개 주문.)

- [ ] **AC-06 잘못된 itemCode** → `400 ITEM_NOT_FOUND`, 무변화.
- [ ] **AC-07 비활성 아이템 구매** → `ITEM_INACTIVE`로 거부.

## 검증 매핑 (Verification)

- BE 통합: 원자성·멱등·동시 INSERT 경합(복합 UNIQUE)·잔액 락 직렬화 테스트
- FE: 구매 확인 다이얼로그 + 코인 부족 배너 딥링크

## 관련

- 기술 상세(락 순서·에러 매핑·시퀀스): `docs/features/shop/spec.md`
- 선결: `US-REWARD` 계열의 포인트 멱등 확장(`recordTransaction`)
- 용어: [_glossary.md](./_glossary.md)
