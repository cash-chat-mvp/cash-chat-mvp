-- V8: 밥(energy) 도메인 — 유저별 채팅 연료 지갑

CREATE TABLE user_energy (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    energy     INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_energy_user UNIQUE (user_id),
    CONSTRAINT fk_user_energy_user FOREIGN KEY (user_id) REFERENCES users (id)
);
