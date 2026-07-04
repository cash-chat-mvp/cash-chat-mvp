---
id: US-SHOP-001
domain: shop
slug: enhance-catalog
status: implemented
jira: CC-292            # BE 상점 리뉴얼 API
source: docs/features/shop/spec.md
related-domains: [shop, inventory]
---

# 강화재료 카탈로그 조회

## 스토리

사용자로서, 나는 상점 탭에서 강화재료 5종의 가격·효과·보유 수량을 한 화면에서 보고 싶다.

## 수용 조건 (Acceptance Criteria)

- [ ] **AC-01 카탈로그 조회**
  Given Phase 1 시드(5종 ENHANCE)가 적재되어 있다
  When `GET /api/shop/items?category=ENHANCE`
  Then `isActive=true`인 5개 아이템이 `displayOrder` 오름차순으로, 각 `itemCode/name/priceCoin/effectSummary`를 포함해 반환된다.

- [ ] **AC-02 Phase 1 비대상 카테고리**
  Given `COSMETIC`/`VOUCHER`를 요청한다
  When `GET /api/shop/items?category=COSMETIC`
  Then `{ category:<요청값>, phase1Active:false, items:[] }`로 빈 카탈로그를 반환한다.

- [ ] **AC-03 비활성 아이템 비노출**
  Given 운영자가 `isActive=false`로 표시한 아이템이 있다
  Then 해당 아이템은 카탈로그 응답에 포함되지 않는다.

## 검증 매핑 (Verification)

- BE: 시드 노출·정렬·`phase1Active` 플래그 테스트
- FE: 상점 카탈로그 화면 렌더링

## 관련

- 기술 상세(API 스키마·시드값): `docs/features/shop/spec.md`
- 용어: [_glossary.md](./_glossary.md)
