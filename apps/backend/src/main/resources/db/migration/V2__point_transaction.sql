-- V2: 포인트 적립/차감 원장 (멱등성 키 유니크)

CREATE TABLE point_transaction (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    delta           BIGINT       NOT NULL,
    balance_after   BIGINT       NOT NULL,
    reason          VARCHAR(50)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_point_transaction_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_point_transaction_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_point_transaction_user_id ON point_transaction (user_id);
