-- V1: 기존 스키마 베이스라인 (Flyway 도입 이전 Hibernate 자동 DDL과 동등)

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    role              VARCHAR(255) NOT NULL,
    device_token      VARCHAR(255),
    provider          VARCHAR(255) NOT NULL,
    provider_id       VARCHAR(255),
    email             VARCHAR(255),
    name              VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(255),
    created_at        TIMESTAMP(6) NOT NULL,
    updated_at        TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_device_token UNIQUE (device_token),
    CONSTRAINT uq_users_provider_provider_id UNIQUE (provider, provider_id)
);

CREATE TABLE user_points (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    balance    BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_points_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_points_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE conversations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    uuid       BINARY(16)   NOT NULL,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_conversations_uuid UNIQUE (uuid),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE chat_messages (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(255) NOT NULL,
    model           VARCHAR(255),
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);
CREATE INDEX idx_chat_messages_conversation_created_at ON chat_messages (conversation_id, created_at);
