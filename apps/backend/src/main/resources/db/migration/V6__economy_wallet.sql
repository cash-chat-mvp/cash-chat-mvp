-- V6: 경제·성장 시스템 — 지갑(에너지/포인트/진화), Energy 발행, 통합 원장

CREATE TABLE user_wallet (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    energy_available      BIGINT       NOT NULL,
    energy_reserved       BIGINT       NOT NULL,
    pending_cashable_pt   BIGINT       NOT NULL,
    confirmed_cashable_pt BIGINT       NOT NULL,
    evolution_level       INT          NOT NULL,
    evolution_exp         BIGINT       NOT NULL,
    evolution_fail_stack  INT          NOT NULL,
    created_at            TIMESTAMP(6) NOT NULL,
    updated_at            TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_wallet_user UNIQUE (user_id),
    CONSTRAINT fk_user_wallet_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE energy_grant (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    source_type      VARCHAR(30)  NOT NULL,
    granted_amount   BIGINT       NOT NULL,
    remaining_amount BIGINT       NOT NULL,
    granted_at       TIMESTAMP(6) NOT NULL,
    expires_at       TIMESTAMP(6) NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_energy_grant_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_energy_grant_user ON energy_grant (user_id, expires_at);

CREATE TABLE wallet_ledger (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    tx_type         VARCHAR(40)  NOT NULL,
    delta           BIGINT       NOT NULL,
    balance_after   BIGINT       NOT NULL,
    reference_id    VARCHAR(255) NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_wallet_ledger_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_wallet_ledger_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_wallet_ledger_user ON wallet_ledger (user_id);
