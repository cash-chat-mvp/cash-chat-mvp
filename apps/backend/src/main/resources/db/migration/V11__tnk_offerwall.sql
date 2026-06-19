-- V11: TNK 오퍼월 — 사용자 토큰 매핑 + 콜백 원장

CREATE TABLE offerwall_user_tokens (
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(64)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_offerwall_user_tokens_token UNIQUE (token),
    CONSTRAINT fk_offerwall_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE tnk_offerwall_callbacks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    seq_id      VARCHAR(128) NOT NULL,
    md_user_nm  VARCHAR(64)  NOT NULL,
    pay_pnt     BIGINT       NOT NULL,
    coin_amount BIGINT       NOT NULL,
    user_id     BIGINT       NULL,
    status      VARCHAR(32)  NOT NULL,
    raw_query   TEXT         NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    updated_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tnk_offerwall_callbacks_seq_id UNIQUE (seq_id),
    -- user_id 는 미지 토큰 시 NULL 허용. 값이 있으면 반드시 유효한 사용자를 참조한다.
    CONSTRAINT fk_tnk_offerwall_callbacks_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_tnk_offerwall_callbacks_user_id ON tnk_offerwall_callbacks (user_id);
