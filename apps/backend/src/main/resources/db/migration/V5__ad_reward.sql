-- V5: 리워드 광고 적립 — 서버 발급 nonce + 일일 시청 한도

CREATE TABLE ad_reward_nonce (
    nonce      VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used       BOOLEAN      NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (nonce),
    CONSTRAINT fk_ad_reward_nonce_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_ad_reward_nonce_user_id ON ad_reward_nonce (user_id);

CREATE TABLE ad_reward_daily_quota (
    user_id    BIGINT       NOT NULL,
    kst_date   DATE         NOT NULL,
    used_count INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, kst_date),
    CONSTRAINT fk_ad_reward_daily_quota_user FOREIGN KEY (user_id) REFERENCES users (id)
);
