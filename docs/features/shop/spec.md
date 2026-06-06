# 상점(Shop) Phase 1 — 강화재료 카탈로그 · 구매 · 인벤토리 Spec

> 상태: Draft
> 범위: Phase 1 (강화재료 카탈로그 + 구매 트랜잭션 + Inventory 적재)
> 관련 기획: [Confluence — 상점](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/15007848), [Confluence — overview](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14975052/Cash+Chat+-+overview), `docs/planning/03-shop.md`
> 선결 조건: `docs/features/reward/spec.md`의 `BE-1 포인트 멱등성 확장` 완료

## 목표 (Goal)

상점 탭의 Phase 1로 강화재료 카탈로그를 노출하고, 코인 차감과 Inventory 적재가 원자적으로 묶인 구매 트랜잭션을 제공한다.

- 강화재료 5종(진화석×1 / 진화석×5 / 확률 부적 / 보호권 / 강화 패키지)을 시드 데이터로 노출
- 구매 시 코인 차감과 Inventory 적재가 같은 DB 트랜잭션 안에서 수행됨
- 동일 클라이언트가 동일 `idempotencyKey`로 재시도해도 중복 차감/적재가 없음
- Inventory의 **소비(consume)** 는 본 spec의 범위 외이며, 후속 진화 시스템 spec이 본 spec이 정의한 Inventory 모델을 read/consume 한다

## 유저 스토리 (User Story)

### Story 1: 강화재료 목록 조회

사용자는 상점 탭에서 강화재료 5종의 가격·효과·보유 수량을 한 화면에서 보고 싶다.

### Story 2: 코인으로 아이템 구매

사용자는 보유 코인이 가격 이상일 때 아이템을 구매하고, 차감된 코인과 증가한 Inventory를 즉시 반영해서 보고 싶다.

### Story 3: 코인 부족 안내

사용자는 보유 코인이 가격에 미치지 못할 때 명확한 거부 메시지를 받고, 혜택존으로 이동해 코인을 모을 수 있는 동선을 제공받고 싶다.

### Story 4: 구매 무결성

백엔드는 네트워크 재시도/이중 탭으로 동일 구매가 두 번 도착해도 코인이 두 번 차감되지 않아야 한다.

### Story 5: 인벤토리 조회

후속 진화 시스템·마이페이지 등에서 보유 아이템과 수량을 조회할 수 있어야 한다.

## 인수 기준 (Acceptance Criteria)

### 카탈로그 조회

Given Phase 1 시드(5종 ENHANCE 아이템)가 적재되어 있다
When 사용자가 `GET /api/shop/items?category=ENHANCE`를 호출한다
Then 응답에는 `isActive=true`인 5개 아이템이 `displayOrder` 오름차순으로 포함된다
And 각 아이템은 `itemCode`, `name`, `priceCoin`, `effectSummary`를 포함한다.

### 보유 수량 동시 조회

Given 사용자가 진화석 2개, 보호권 1개를 보유한다
When 사용자가 `GET /api/inventory/me`를 호출한다
Then 응답은 `{ "items": [{itemCode:"EVO_STONE", qty:2}, {itemCode:"PROTECT_TICKET", qty:1}] }` 형태로 반환된다 (정확한 스키마는 "API 계약 — `GET /api/inventory/me`" 섹션 참조).

### 정상 구매

Given 사용자 코인 잔액 = 1,250, 진화석 가격 = 200
When 사용자가 `POST /api/shop/purchase`를 `{itemCode:"EVO_STONE", qty:1, idempotencyKey:"k1"}`로 호출한다
Then 단일 DB 트랜잭션 안에서 코인이 200 차감되어 1,050이 된다
And `user_inventory.EVO_STONE.qty`가 1 증가한다
And `purchase_order`에 `status=COMPLETED`, `idempotencyKey="k1"` 행이 1개 생성된다
And `point_transaction`에 멱등성 키 `shop:purchase:k1`로 차감 트랜잭션이 기록된다
And 응답에는 갱신된 코인 잔액과 갱신된 보유 수량이 포함된다.

### 패키지 구매 (다건 grant)

Given 사용자 코인 잔액 = 2,000, `ENHANCE_PACK` 가격 = 1,200 (grant: `EVO_STONE`×5, `LUCK_CHARM`×1)
When 사용자가 `POST /api/shop/purchase`를 `{itemCode:"ENHANCE_PACK", qty:1, idempotencyKey:"k2"}`로 호출한다
Then 코인이 1,200 차감되어 800이 된다
And `user_inventory.EVO_STONE.qty`가 5 증가한다
And `user_inventory.LUCK_CHARM.qty`가 1 증가한다
And 두 grant는 같은 DB 트랜잭션 안에서 처리된다.

### 코인 부족

Given 사용자 코인 잔액 = 100, 진화석 가격 = 200
When 사용자가 `POST /api/shop/purchase`를 호출한다
Then 백엔드는 도메인 에러 `INSUFFICIENT_COIN`으로 거부한다
And 코인 잔액, Inventory, `purchase_order` 어느 것도 변하지 않는다.

### 멱등성 — 동일 키 재호출

Given 사용자가 직전에 `idempotencyKey="k1"`로 진화석 1개 구매에 성공했다
When 사용자가 동일 페이로드를 다시 호출한다
Then 추가 차감/적재가 발생하지 않는다
And 응답은 추가 차감 없이 **현재 시점**의 잔액·Inventory를 반환한다 (이중 차감만 방지하며, 첫 구매 이후 다른 거래가 있었다면 그 결과가 반영된 최신 값이다).

### 멱등성 — 키 재사용 충돌

Given 사용자가 `idempotencyKey="k1"`로 진화석 1개 구매에 성공했다
When 동일 `idempotencyKey="k1"`로 **다른 `itemCode`/`qty`(또는 다른 사용자)**로 호출한다
Then 백엔드는 409 `IDEMPOTENCY_KEY_CONFLICT`로 거부한다
And 어떤 상태도 변하지 않는다.

### 잘못된 itemCode

Given 요청 페이로드의 `itemCode`가 시드에 없는 값이다
When 사용자가 `POST /api/shop/purchase`를 호출한다
Then 백엔드는 400 `ITEM_NOT_FOUND`로 거부한다
And 어떤 상태도 변하지 않는다.

### 비활성화된 아이템

Given 운영자가 `shop_item.isActive=false`로 표시한 아이템이 있다
When 사용자가 해당 itemCode로 `POST /api/shop/purchase`를 호출한다
Then 백엔드는 도메인 에러 `ITEM_INACTIVE`로 거부한다
And `GET /api/shop/items?category=ENHANCE` 응답에도 해당 아이템이 포함되지 않는다.

### Phase 1 비대상 카테고리 호출

Given 사용자가 Phase 1 비대상 카테고리(`COSMETIC`, `VOUCHER`)를 요청한다
When `GET /api/shop/items?category=COSMETIC`을 호출한다
Then 응답 본문은 `{ "category": "<요청값>", "phase1Active": false, "items": [] }` 형태로 빈 카탈로그와 `phase1Active=false` 플래그를 함께 반환한다 (정확한 스키마는 "API 계약" 섹션 참조)
And Phase 1 동안 해당 카테고리 아이템은 카탈로그에 노출되지 않는다.

## API 계약

### 공통 규칙

- **인증**: 모든 endpoint는 `Authorization: Bearer <jwt>` 필수 (기존 `domain/auth` 발급 access token; 추가 scope 요구 없음). 누락·만료 시 `401 UNAUTHORIZED` (기존 Security 필터 응답 형식 그대로).
- **에러 응답 공통 본문**: `{ "code": "<DOMAIN_ENUM>", "message": "<설명>" }`. 도메인 enum은 인수 기준에 명시된 식별자(`INSUFFICIENT_COIN` 등)와 1:1 일치.
- **idempotencyKey**: UUID v4 권장 (서버는 형식만 검증). 클라이언트가 같은 요청에 같은 키를 재사용하면 멱등 처리.

### 엔드포인트 요약

| Method | Path | 설명 |
| ------ | ---- | ---- |
| `GET`  | `/api/shop/items?category=ENHANCE` | 카탈로그 (Phase 1은 ENHANCE만 활성) |
| `POST` | `/api/shop/purchase` | `{itemCode, qty, idempotencyKey}` → 트랜잭션 구매 |
| `GET`  | `/api/inventory/me` | 보유 아이템 수량 리스트 |

### `GET /api/shop/items?category=<CATEGORY>`

ENHANCE (Phase 1 활성):

```json
{
  "category": "ENHANCE",
  "phase1Active": true,
  "items": [
    { "itemCode": "ENHANCE_PACK",    "name": "강화 패키지", "priceCoin": 1200, "effectSummary": "진화석 5 + 확률 부적 1 (묶음)",     "displayOrder": 5 },
    { "itemCode": "EVO_STONE",       "name": "진화석",      "priceCoin": 200,  "effectSummary": "진화 시도 1회 필요 재료",          "displayOrder": 10 },
    { "itemCode": "EVO_STONE_BUNDLE", "name": "진화석 ×5",  "priceCoin": 900,  "effectSummary": "묶음 구매 (10% 할인)",             "displayOrder": 20 },
    { "itemCode": "LUCK_CHARM",      "name": "확률 부적",   "priceCoin": 500,  "effectSummary": "다음 진화 시도 성공 확률 +10%p (1회용)", "displayOrder": 30 },
    { "itemCode": "PROTECT_TICKET",  "name": "보호권",      "priceCoin": 800,  "effectSummary": "실패 시 소비 코인 50% 반환 (1회용)",   "displayOrder": 40 }
  ]
}
```

COSMETIC / VOUCHER (Phase 1 비활성, "Phase 1 비대상 카테고리 호출" criterion 참조):

```json
{
  "category": "COSMETIC",
  "phase1Active": false,
  "items": []
}
```

| 에러 | HTTP | 비고 |
| ---- | ---- | ---- |
| `INVALID_CATEGORY` | 400 | enum 범위 밖 `category` 값 |

### `POST /api/shop/purchase`

Request: `{ "itemCode": "...", "qty": 1, "idempotencyKey": "<uuid>" }`

성공 응답:

```json
{
  "purchaseOrderId": 123,
  "status": "COMPLETED",
  "coinBalance": 1050,
  "inventory": [
    { "itemCode": "EVO_STONE", "qty": 3 }
  ]
}
```

> 멱등성 재호출은 추가 차감 없이 **현재 시점의 잔액·인벤토리를 재조회**해서 반환한다 — `idempotencyKey`는 *이중 차감 방지*만 보장하며, 첫 구매 이후 다른 거래(출석·광고 보상 등)가 있었다면 그 결과가 반영된 최신 값이 반환된다(stale 스냅샷으로 클라이언트 상태를 덮어쓰지 않기 위함). 또한 같은 키가 **다른 `userId`/`itemCode`/`qty`로 재사용**되면 `IDEMPOTENCY_KEY_CONFLICT`로 거부한다. 자세한 시맨틱은 인수 기준 "멱등성 — 동일 키 재호출" / "멱등성 — 키 재사용 충돌" 참조.

| 에러 | HTTP | 발생 조건 |
| ---- | ---- | -------- |
| `INSUFFICIENT_COIN` | 400 | 잔액 < `priceCoin * qty` |
| `ITEM_NOT_FOUND` | 400 | 시드에 없는 `itemCode` |
| `ITEM_INACTIVE` | 400 | `shop_item.isActive=false` |
| `VALIDATION` | 400 | `qty < 1`, `idempotencyKey` 형식 위반 등 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 `idempotencyKey`가 다른 `userId`/`itemCode`/`qty`로 재사용됨 |

### `GET /api/inventory/me`

```json
{
  "items": [
    { "itemCode": "EVO_STONE",      "qty": 2 },
    { "itemCode": "PROTECT_TICKET", "qty": 1 }
  ]
}
```

에러 없음 (인증 실패만 401).

## 사용자 흐름 (User Flow)

1. 사용자가 상점 탭(세그먼트: [강화재료] / [외형](disabled) / [교환권](disabled))을 열고 [강화재료]를 선택한다.
2. 프론트가 `GET /api/shop/items?category=ENHANCE`와 `GET /api/inventory/me`를 병렬 호출한다.
3. 카드 리스트로 5종 아이템과 보유 수량, 코인 잔액을 보여준다.
4. 사용자가 [구매]를 누르면 확인 다이얼로그가 뜬다 ("현재: 🪙1,250 → 구매 후: 🪙1,050").
5. 사용자가 [구매 확정]을 누르면 프론트가 UUID `idempotencyKey`를 생성하여 `POST /api/shop/purchase`를 호출한다.
6. 백엔드가 단일 트랜잭션으로 코인 차감 + Inventory 적재를 수행하고 결과를 응답한다.
7. 프론트가 코인 잔액과 보유 수량을 갱신한다. 코인 부족 시 [🎁 혜택존에서 코인 벌기 →] 배너로 딥링크한다.

### 순차 흐름도 (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant FE as 상점 화면
    participant API as CashChat 백엔드
    participant DB as DB

    User->>FE: 상점 탭 진입
    par 카탈로그/인벤토리 병렬 로드
        FE->>API: GET /api/shop/items?category=ENHANCE
        API->>DB: shop_item WHERE category=ENHANCE AND isActive=true
        DB-->>API: 5종
        API-->>FE: 카탈로그
    and
        FE->>API: GET /api/inventory/me
        API->>DB: user_inventory WHERE userId=?
        DB-->>API: 보유 수량 리스트
        API-->>FE: Inventory
    end

    User->>FE: 구매 탭
    FE->>User: 확인 다이얼로그 (현 잔액 → 구매 후 잔액)
    User->>FE: 구매 확정
    FE->>FE: idempotencyKey = UUID
    FE->>API: POST /api/shop/purchase {itemCode, qty, idempotencyKey}

    API->>DB: BEGIN TRANSACTION
    API->>DB: purchase_order SELECT WHERE idempotencyKey=?
    alt 기존 키 존재
        DB-->>API: 기존 COMPLETED 주문 (재차감 없음)
        alt 저장된 userId/itemCode/qty 불일치
            API->>DB: ROLLBACK
            API-->>FE: 409 IDEMPOTENCY_KEY_CONFLICT
        else 일치
            API->>DB: 현재 코인 잔액 + user_inventory 재조회
            API->>DB: COMMIT (no-op)
            API-->>FE: 현재 시점 잔액 + Inventory
        end
    else 신규
        API->>DB: shop_item SELECT (가격/isActive 확인)
        alt itemCode 없음 또는 inactive
            API->>DB: ROLLBACK
            API-->>FE: 400 ITEM_NOT_FOUND or ITEM_INACTIVE
        else 유효 아이템
            API->>API: recordTransaction(delta=-price*qty, key="shop:purchase:{idem}")
            alt 잔액 부족
                API->>DB: ROLLBACK
                API-->>FE: 400 INSUFFICIENT_COIN
            else 잔액 충분
                API->>DB: user_inventory UPSERT (grant 다건 처리)
                API->>DB: purchase_order INSERT (userId, itemCode, qty, status=COMPLETED, snapshotPrice)
                API->>DB: COMMIT
                API-->>FE: 200 {coinBalance, inventory}
            end
        end
    end

    FE->>User: 잔액/보유 수량 갱신
```

## 도메인 enum 정의

### `PurchaseOrder.status`

| 값 | 의미 |
| -- | ---- |
| `COMPLETED` | 트랜잭션 커밋 성공. **Phase 1에서 `purchase_order` 행이 가지는 유일한 값.** 도메인/잔액/inactive 거부는 `purchase_order` 행이 만들어지기 전 `ROLLBACK` 되므로 행이 남지 않는다. |
| `FAILED` | 예약값. 사후 보상 트랜잭션(예: 다운스트림 grant 적재 실패 → 코인 환불) 실패 시 마킹용. Phase 1 미사용 (별도 spec). 모니터링 훅(`tasks: INF-2`)은 이 값을 0이 정상으로 가정한다. |

> 운영 알람은 이 enum을 단일 source of truth로 참조한다. 신규 status 값 도입은 별도 spec 결정 후 본 표를 갱신한 뒤에만 추가한다.

## 범위 외 (Out Of Scope)

- **외형 아이템 탭**: 스킨/액세서리/배경, IAP 결제 — Phase 2
- **네이버페이 교환권 탭**: `domain/voucher`, `domain/kyc`, `domain/abuse` — 별도 spec
- **강화 패키지 24h 한정 노출(FOMO) 정책**: `validUntil` 같은 시간 제한 노출 룰은 별도 spec. 본 spec은 `ENHANCE_PACK`을 상시 노출.
- **진화/강화 시스템**: `domain/evolution`, `Inventory.consume(itemCode, qty)` — Evolution spec이 본 spec의 Inventory 모델을 read/consume
- **운영자 상품 관리 UI**: `shop_item`은 Flyway 마이그레이션 또는 별도 DB 작업으로만 관리
- **묶음 할인 / 정가 표시 / 할인율 UI 메타**: Phase 1은 단일 `priceCoin`만 노출 (진화석×5는 `priceCoin=900`으로 시드)
- **환불 / 결제 취소**: 구매 후 코인 환불 흐름은 미지원
- **결제 한도 / 어뷰징 방지**: 본 spec은 멱등성과 잔액 검증만 책임 (한도/어뷰징은 교환권 spec과 함께)
- **재료 효과 감산 로직**: 기획안의 "Lv4 이상 시 확률 부적 +5%p로 감산" 같은 사용 시점 효과 조정은 Inventory consume 단계의 정책이며 Evolution spec이 다룬다. 본 spec의 `effectSummary`는 기본 효과만 기재한다.

## 부록: Phase 1 시드값

### `shop_item` 테이블 시드 (Phase 1)

| itemCode         | name        | category | priceCoin | effectSummary                              | isActive | displayOrder |
| ---------------- | ----------- | -------- | --------- | ------------------------------------------ | -------- | ------------ |
| `ENHANCE_PACK`   | 강화 패키지 | ENHANCE  | 1,200     | 진화석 5 + 확률 부적 1 (묶음)              | true     | 5            |
| `EVO_STONE`      | 진화석      | ENHANCE  | 200       | 진화 시도 1회 필요 재료                    | true     | 10           |
| `EVO_STONE_BUNDLE`    | 진화석 ×5   | ENHANCE  | 900       | 묶음 구매 (10% 할인)                        | true     | 20           |
| `LUCK_CHARM`     | 확률 부적   | ENHANCE  | 500       | 다음 진화 시도 성공 확률 +10%p (1회용)      | true     | 30           |
| `PROTECT_TICKET` | 보호권      | ENHANCE  | 800       | 실패 시 소비 코인 50% 반환 (1회용)          | true     | 40           |

### `shop_item_grant` 시드 (다건 grant join 테이블)

| itemCode         | grantItemCode    | grantQty |
| ---------------- | ---------------- | -------- |
| `EVO_STONE`      | `EVO_STONE`      | 1        |
| `EVO_STONE_BUNDLE`    | `EVO_STONE`      | 5        |
| `LUCK_CHARM`     | `LUCK_CHARM`     | 1        |
| `PROTECT_TICKET` | `PROTECT_TICKET` | 1        |
| `ENHANCE_PACK`   | `EVO_STONE`      | 5        |
| `ENHANCE_PACK`   | `LUCK_CHARM`     | 1        |

- 단건 아이템도 일관된 처리 경로를 위해 `shop_item_grant`에 자기 자신 grant 1행을 시드한다.
- `displayOrder`는 작을수록 상단 노출 — 패키지를 최상단에 배치.
- **itemCode 명명 규칙**: `itemCode`는 식별자일 뿐 수량을 의미하지 않는다 (`EVO_STONE_BUNDLE`은 "묶음"을 뜻하는 라벨이며 실제 지급 수량은 항상 `shop_item_grant.grantQty`가 단일 source of truth). 향후 묶음 수량 조정이 발생하면 시드 행만 수정하고 `itemCode`는 유지한다.
