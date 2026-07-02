-- V18: Lucky Roulette daily state, ad nonce verification, and spin audit log

CREATE TABLE roulette_daily_state (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    kst_date        DATE         NOT NULL,
    spins_used      INT          NOT NULL,
    free_spins_used INT          NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roulette_daily_state_user_date UNIQUE (user_id, kst_date),
    CONSTRAINT fk_roulette_daily_state_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE roulette_ad_nonce (
    nonce          VARCHAR(64)  NOT NULL,
    user_id        BIGINT       NOT NULL,
    expires_at     TIMESTAMP(6) NOT NULL,
    verified       BOOLEAN      NOT NULL,
    used           BOOLEAN      NOT NULL,
    transaction_id VARCHAR(128) NULL,
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (nonce),
    CONSTRAINT fk_roulette_ad_nonce_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_roulette_ad_nonce_user_id ON roulette_ad_nonce (user_id);

CREATE TABLE roulette_spin (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    kst_date       DATE         NOT NULL,
    spin_type      VARCHAR(16)  NOT NULL,
    prize          VARCHAR(32)  NOT NULL,
    prize_energy   INT          NOT NULL,
    awarded_energy INT          NOT NULL,
    energy_after   INT          NOT NULL,
    segment_index  INT          NOT NULL,
    nonce          VARCHAR(64)  NULL,
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roulette_spin_nonce UNIQUE (nonce),
    CONSTRAINT fk_roulette_spin_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_roulette_spin_nonce FOREIGN KEY (nonce) REFERENCES roulette_ad_nonce (nonce)
);
CREATE INDEX idx_roulette_spin_user_date ON roulette_spin (user_id, kst_date);
