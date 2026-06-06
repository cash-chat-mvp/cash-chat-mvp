-- V6: 상점 Phase 1 — 강화재료 카탈로그 / 구매 주문 / 인벤토리

-- 카탈로그(참조 데이터): BaseEntity 미상속 → created_at/updated_at 없음 (attendance_reward 와 동일)
CREATE TABLE shop_item (
    item_code      VARCHAR(50)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    price_coin     BIGINT       NOT NULL,
    effect_summary VARCHAR(255) NOT NULL,
    is_active      BOOLEAN      NOT NULL,
    display_order  INT          NOT NULL,
    PRIMARY KEY (item_code)
);

-- 다건 grant 조인(참조 데이터)
CREATE TABLE shop_item_grant (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    item_code       VARCHAR(50) NOT NULL,
    grant_item_code VARCHAR(50) NOT NULL,
    grant_qty       INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shop_item_grant_item_grant UNIQUE (item_code, grant_item_code),
    CONSTRAINT fk_shop_item_grant_item FOREIGN KEY (item_code) REFERENCES shop_item (item_code),
    CONSTRAINT ck_shop_item_grant_qty CHECK (grant_qty >= 1)
);

-- 구매 주문(트랜잭션 데이터): (user_id, idempotency_key) 복합 유니크 = 멱등성 스코프
CREATE TABLE purchase_order (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    item_code       VARCHAR(50)  NOT NULL,
    qty             INT          NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    snapshot_price  BIGINT       NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_purchase_order_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_purchase_order_user_id ON purchase_order (user_id);

-- 사용자 인벤토리(트랜잭션 데이터): (user_id, item_code) 복합 유니크 = UPSERT 키
CREATE TABLE user_inventory (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    item_code  VARCHAR(50)  NOT NULL,
    qty        INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_inventory_user_item UNIQUE (user_id, item_code),
    CONSTRAINT fk_user_inventory_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_inventory_qty CHECK (qty >= 0)
);

-- 시드: 강화재료 5종 (spec 부록 표)
INSERT INTO shop_item (item_code, name, category, price_coin, effect_summary, is_active, display_order) VALUES
    ('ENHANCE_PACK',     '강화 패키지', 'ENHANCE', 1200, '진화석 5 + 확률 부적 1 (묶음)',              TRUE, 5),
    ('EVO_STONE',        '진화석',      'ENHANCE', 200,  '진화 시도 1회 필요 재료',                    TRUE, 10),
    ('EVO_STONE_BUNDLE', '진화석 ×5',   'ENHANCE', 900,  '묶음 구매 (10% 할인)',                       TRUE, 20),
    ('LUCK_CHARM',       '확률 부적',   'ENHANCE', 500,  '다음 진화 시도 성공 확률 +10%p (1회용)',     TRUE, 30),
    ('PROTECT_TICKET',   '보호권',      'ENHANCE', 800,  '실패 시 소비 코인 50% 반환 (1회용)',         TRUE, 40);

-- 시드: shop_item_grant 6행 (단건도 자기 자신 grant 1행 — 일관 처리 경로)
INSERT INTO shop_item_grant (item_code, grant_item_code, grant_qty) VALUES
    ('EVO_STONE',        'EVO_STONE',      1),
    ('EVO_STONE_BUNDLE', 'EVO_STONE',      5),
    ('LUCK_CHARM',       'LUCK_CHARM',     1),
    ('PROTECT_TICKET',   'PROTECT_TICKET', 1),
    ('ENHANCE_PACK',     'EVO_STONE',      5),
    ('ENHANCE_PACK',     'LUCK_CHARM',     1);
