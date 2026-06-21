-- V7: 채팅 보상 정산 · 모델 품질 공용 풀

CREATE TABLE chat_reward_settlement (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    message_id          VARCHAR(255) NOT NULL,
    reward_type         VARCHAR(30)  NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    conversation_id     BIGINT       NOT NULL,
    assistant_message_id BIGINT      NULL,
    energy_delta        BIGINT       NOT NULL,
    pending_pt_delta    BIGINT       NOT NULL,
    evolution_exp_delta BIGINT       NOT NULL,
    settled_at          TIMESTAMP(6) NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_chat_reward_settlement_user_msg_type UNIQUE (user_id, message_id, reward_type),
    CONSTRAINT fk_chat_reward_settlement_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_chat_reward_settlement_message ON chat_reward_settlement (message_id);

CREATE TABLE shared_quality_pool (
    id         BIGINT         NOT NULL,
    balance    DECIMAL(18, 4) NOT NULL,
    created_at TIMESTAMP(6)   NOT NULL,
    updated_at TIMESTAMP(6)   NOT NULL,
    PRIMARY KEY (id)
);
