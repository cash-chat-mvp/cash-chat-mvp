---
id: US-SHOP-003
domain: shop
slug: inventory
status: implemented
jira: CC-292            # BE 상점 리뉴얼 API
source: docs/features/shop/spec.md
related-domains: [inventory, evolution]
---

# 인벤토리 조회

## 스토리

사용자로서(그리고 후속 진화 시스템·마이페이지에서), 나는 보유 아이템과 수량을 조회할 수 있어야 한다.

## 수용 조건 (Acceptance Criteria)

- **AC-01 보유 수량 조회**
  Given 진화석 2개, 보호권 1개를 보유한다
  When `GET /api/inventory/me`
  Then `{ items: [{itemCode:"EVO_STONE", qty:2}, {itemCode:"PROTECT_TICKET", qty:1}] }` 형태로 반환된다. 도메인 에러 없음(인증 실패 시 401만).

## 검증 매핑 (Verification)

- BE: 보유 수량 직렬화 테스트
- 후속: 진화 시스템이 본 인벤토리 모델을 read/consume (소비는 Evolution spec 범위)

## 관련

- 기술 상세: `docs/features/shop/spec.md`
- Inventory **소비(consume)** 는 본 도메인 범위 외 → Evolution 도메인.
- 용어: [_glossary.md](./_glossary.md)
