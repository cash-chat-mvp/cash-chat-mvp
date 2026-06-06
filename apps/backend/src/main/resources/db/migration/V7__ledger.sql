-- V7: 통합 회계(ledger) 도메인 — 수익 분배 감사 원장

CREATE TABLE ledger_entry (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    source              VARCHAR(30)  NOT NULL,
    gross_revenue       BIGINT       NOT NULL,
    risk_reserve        BIGINT       NOT NULL,
    service_reserve     BIGINT       NOT NULL,
    company_profit      BIGINT       NOT NULL,
    cashable_pt_awarded BIGINT       NOT NULL,
    energy_awarded      INT          NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ledger_entry_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_ledger_entry_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_ledger_entry_user_id ON ledger_entry (user_id);
