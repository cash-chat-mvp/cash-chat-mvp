-- V9: quality(공용 풀) 도메인 — 프리미엄 재원 공용 풀 + 유저별 일일 사용량

-- 전체 유저 공용 프리미엄 재원 풀 (singleton row, id=1 고정)
CREATE TABLE shared_quality_pool (
    id               BIGINT       NOT NULL,
    balance_centi_pt BIGINT       NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

-- 싱글톤 행 시드 (id=1, 초기 잔액 0)
INSERT INTO shared_quality_pool (id, balance_centi_pt, created_at, updated_at)
VALUES (1, 0, NOW(6), NOW(6));

-- 유저별 일일 프리미엄 사용 횟수
CREATE TABLE daily_premium_usage (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    usage_date DATE         NOT NULL,
    count      INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_daily_premium_usage_user_date UNIQUE (user_id, usage_date),
    CONSTRAINT fk_daily_premium_usage_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_daily_premium_usage_user ON daily_premium_usage (user_id);
