-- V5: 진화(evolution) 도메인 — 유저 레벨 상태 + 진화 시도 원장 (Phase 1 가설값)

CREATE TABLE user_evolution (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    level      INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_evolution_user UNIQUE (user_id),
    CONSTRAINT fk_user_evolution_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE evolution_attempt (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    from_level      INT          NOT NULL,
    cost            BIGINT       NOT NULL,
    success         BOOLEAN      NOT NULL,
    result_level    INT          NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_evolution_attempt_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_evolution_attempt_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_evolution_attempt_user_id ON evolution_attempt (user_id);