# shop 도메인 — 공유 용어 (Ubiquitous Language)

| 용어 | 정의 |
| ---- | ---- |
| **강화재료(ENHANCE)** | Phase 1 상점 카테고리. 진화석/진화석×5/확률 부적/보호권/강화 패키지 5종. `COSMETIC`/`VOUCHER`는 Phase 1 비활성. |
| **grant** | 한 아이템 구매 시 실제 지급되는 아이템·수량(`shop_item_grant`). 단건 아이템도 자기 자신 grant 1행으로 일관 처리. `itemCode`는 식별자일 뿐 수량을 의미하지 않으며 실제 지급 수량은 `grantQty`가 단일 source of truth. |
| **인벤토리(user_inventory)** | 사용자 보유 아이템·수량. 본 도메인은 적재(read/grant)만 책임. **소비(consume)** 는 Evolution 도메인 범위. |
| **멱등성 스코프** | `(userId, idempotencyKey)` 복합 유니크. 같은 사용자의 같은 키 = 이중 차감 방지, 다른 itemCode/qty 재사용 = `IDEMPOTENCY_KEY_CONFLICT`. 다른 사용자의 같은 키 = 별개 주문. |
| **전역 락 순서** | `point`(FOR UPDATE) → `user_inventory`(UPSERT) → `purchase_order`(INSERT). 교차 도메인 데드락 방지를 위해 모든 트랜잭션이 준수. |
| **코인** | reward 도메인과 동일 재화. 공통 정의는 [reward/_glossary.md](../reward/_glossary.md) 참조. |
